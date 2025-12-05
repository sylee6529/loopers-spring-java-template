package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.pg.PgPaymentClient;
import com.loopers.infrastructure.payment.pg.PgPaymentDetailResponse;
import com.loopers.infrastructure.payment.pg.PgPaymentRequest;
import com.loopers.infrastructure.payment.pg.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Payment 도메인 서비스
 * - 멱등성 보장
 * - PG 연동 처리
 * - 상태 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PgPaymentClient pgPaymentClient;

    /**
     * 결제 요청 (멱등성 보장)
     *
     * 1. orderId 중복 체크 (멱등성)
     * 2. Payment 엔티티 생성 및 저장 (PENDING 상태)
     * 3. PG 결제 요청
     * 4. transactionKey 저장
     *
     * PG 호출 실패 시:
     * - Payment는 PENDING 상태로 저장됨
     * - requiresRetry 플래그 설정
     * - 나중에 수동/자동 재시도 가능
     */
    @Transactional
    public PaymentInfo requestPayment(String userId, PaymentCommand.RequestPayment command) {
        log.info("[Payment] 결제 요청 시작 - orderId: {}, amount: {}", command.orderId(), command.amount());

        // 1. 멱등성 체크: 이미 존재하는 orderId면 예외
        if (paymentRepository.existsByOrderId(command.orderId())) {
            log.warn("[Payment] 중복된 결제 요청 - orderId: {}", command.orderId());
            throw new CoreException(ErrorType.CONFLICT, "이미 처리 중인 결제입니다: " + command.orderId());
        }

        // 2. Payment 엔티티 생성 및 저장 (PENDING 상태)
        Payment payment = Payment.create(
                command.orderId(),
                command.cardType(),
                command.cardNo(),
                command.amount(),
                command.callbackUrl()
        );
        payment = paymentRepository.save(payment);
        log.info("[Payment] Payment 엔티티 생성 - id: {}, orderId: {}", payment.getId(), payment.getOrderId());

        // 3. PG 결제 요청
        try {
            PgPaymentRequest pgRequest = new PgPaymentRequest(
                    command.orderId(),
                    PgPaymentRequest.PgCardType.valueOf(command.cardType().name()),
                    command.cardNo(),
                    command.amount(),
                    command.callbackUrl()
            );

            CompletableFuture<PgPaymentResponse> future = pgPaymentClient.requestPayment(userId, pgRequest);
            PgPaymentResponse pgResponse = future.get(3, TimeUnit.SECONDS);

            // 4. PG 응답 처리
            if (pgResponse.transactionKey() != null) {
                // 정상 응답: transactionKey 저장
                payment.assignTransactionKey(pgResponse.transactionKey());
                log.info("[Payment] PG 결제 요청 성공 - transactionKey: {}", pgResponse.transactionKey());
            } else {
                // Fallback 응답 (transactionKey가 null): 재시도 필요 표시
                payment.markAsRequiresRetry();
                log.warn("[Payment] PG 장애로 재시도 필요 - orderId: {}", command.orderId());
            }

        } catch (Exception e) {
            // PG 호출 실패: 재시도 필요 표시
            log.error("[Payment] PG 결제 요청 실패 - orderId: {}", command.orderId(), e);
            payment.markAsRequiresRetry();
        }

        return PaymentInfo.from(payment);
    }

    /**
     * 콜백 처리 (PG에서 결제 결과 전송)
     *
     * 1. transactionKey로 Payment 조회
     * 2. Payment 상태 업데이트
     * 3. Order 상태 업데이트 (후속 처리)
     */
    @Transactional
    public PaymentInfo processCallback(String userId, PaymentCommand.ProcessCallback command) {
        log.info("[Payment] 콜백 처리 시작 - transactionKey: {}, status: {}",
                command.transactionKey(), command.status());

        // 1. Payment 조회
        Payment payment = paymentRepository.findByTransactionKey(command.transactionKey())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다: " + command.transactionKey()));

        // 2. 상태 업데이트
        PaymentStatus pgStatus = PaymentStatus.valueOf(command.status());
        payment.updateFromPg(pgStatus, command.reason());

        log.info("[Payment] 콜백 처리 완료 - orderId: {}, status: {}",
                payment.getOrderId(), payment.getStatus());

        return PaymentInfo.from(payment);
    }

    /**
     * 결제 상태 조회 및 동기화
     *
     * PG에서 최신 상태를 조회하여 Payment 엔티티 업데이트
     */
    @Transactional
    public PaymentInfo syncPaymentStatus(String userId, String transactionKey) {
        log.info("[Payment] 상태 동기화 시작 - transactionKey: {}", transactionKey);

        // 1. Payment 조회
        Payment payment = paymentRepository.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다: " + transactionKey));

        // 2. PG 상태 조회
        try {
            PgPaymentDetailResponse pgDetail = pgPaymentClient.getPaymentStatus(userId, transactionKey);

            // 3. 상태 동기화
            PaymentStatus pgStatus = PaymentStatus.valueOf(pgDetail.status().name());
            payment.updateFromPg(pgStatus, pgDetail.reason());

            log.info("[Payment] 상태 동기화 완료 - orderId: {}, status: {}",
                    payment.getOrderId(), payment.getStatus());

        } catch (Exception e) {
            log.error("[Payment] 상태 조회 실패 - transactionKey: {}", transactionKey, e);
            // 실패해도 현재 상태 반환
        }

        return PaymentInfo.from(payment);
    }

    /**
     * orderId로 결제 정보 조회
     */
    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다: " + orderId));
        return PaymentInfo.from(payment);
    }

    /**
     * PENDING 상태 결제 목록 조회 (폴링용)
     */
    @Transactional(readOnly = true)
    public List<PaymentInfo> getPendingPaymentsOlderThan(LocalDateTime dateTime) {
        return paymentRepository.findPendingPaymentsOlderThan(dateTime)
                .stream()
                .map(PaymentInfo::from)
                .toList();
    }

    /**
     * 재시도 필요한 결제 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsRequiringRetry() {
        return paymentRepository.findByRequiresRetryTrue()
                .stream()
                .map(PaymentInfo::from)
                .toList();
    }
}
