package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PgGateway;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PgGateway pgGateway;

    public PaymentInfo requestPayment(String userId, PaymentCommand.RequestPayment command) {
        log.info("[Payment] 결제 요청 시작 - orderNo: {}, amount: {}", command.orderId(), command.amount());

        Order order = orderRepository.findByOrderNo(command.orderId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "주문을 찾을 수 없습니다: " + command.orderId()));

        if (!order.isPendingPayment()) {
            log.warn("[Payment] 결제 대기 상태가 아닌 주문 - orderNo: {}, status: {}",
                    order.getOrderNo(), order.getStatus());
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 대기 상태가 아닌 주문입니다: " + order.getOrderNo());
        }

        if (paymentRepository.existsByOrder(order)) {
            log.warn("[Payment] 중복된 결제 요청 - orderNo: {}", order.getOrderNo());
            throw new CoreException(ErrorType.CONFLICT, "이미 처리 중인 결제입니다: " + order.getOrderNo());
        }

        Payment payment = Payment.create(
                order,
                command.cardType(),
                command.cardNo(),
                command.amount(),
                command.callbackUrl()
        );

        try {
            payment = paymentRepository.save(payment);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("[Payment] 동시 요청으로 인한 중복 - orderNo: {}", order.getOrderNo());
            throw new CoreException(ErrorType.CONFLICT, "이미 처리 중인 결제입니다: " + order.getOrderNo());
        }
        log.info("[Payment] Payment 엔티티 생성 - id: {}, orderNo: {}", payment.getId(), order.getOrderNo());

        try {
            PgGateway.PgPaymentCommand pgCommand = new PgGateway.PgPaymentCommand(
                    order.getOrderNo(),
                    command.cardType(),
                    command.cardNo(),
                    command.amount(),
                    command.callbackUrl()
            );

            CompletableFuture<PgGateway.PgPaymentResult> future = pgGateway.requestPayment(userId, pgCommand);
            PgGateway.PgPaymentResult pgResult = future.get(3, TimeUnit.SECONDS);

            if (pgResult.transactionKey() != null) {
                payment.assignTransactionKey(pgResult.transactionKey());
                log.info("[Payment] PG 결제 요청 성공 - transactionKey: {}", pgResult.transactionKey());
            } else {
                payment.markAsRequiresRetry();
                log.warn("[Payment] PG 장애로 재시도 필요 - orderNo: {}", order.getOrderNo());
            }

        } catch (Exception e) {
            log.error("[Payment] PG 결제 요청 실패 - orderNo: {}", order.getOrderNo(), e);
            payment.markAsRequiresRetry();
        }

        return PaymentInfo.from(payment);
    }

    public PaymentInfo processCallback(String userId, PaymentCommand.ProcessCallback command) {
        log.info("[Payment] 콜백 처리 시작 - transactionKey: {}, status: {}",
                command.transactionKey(), command.status());

        Payment payment = paymentRepository.findByTransactionKey(command.transactionKey())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다: " + command.transactionKey()));

        PaymentStatus pgStatus = PaymentStatus.valueOf(command.status());
        payment.updateFromPg(pgStatus, command.reason());

        log.info("[Payment] 콜백 처리 완료 - orderNo: {}, status: {}",
                payment.getOrder().getOrderNo(), payment.getStatus());

        return PaymentInfo.from(payment);
    }

    public PaymentInfo syncPaymentStatus(String userId, String transactionKey) {
        log.info("[Payment] 상태 동기화 시작 - transactionKey: {}", transactionKey);

        Payment payment = paymentRepository.findByTransactionKey(transactionKey)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다: " + transactionKey));

        try {
            PgGateway.PgPaymentDetail pgDetail = pgGateway.getPaymentStatus(userId, transactionKey);
            PaymentStatus pgStatus = PaymentStatus.valueOf(pgDetail.status().name());
            payment.updateFromPg(pgStatus, pgDetail.reason());

            log.info("[Payment] 상태 동기화 완료 - orderNo: {}, status: {}",
                    payment.getOrder().getOrderNo(), payment.getStatus());

        } catch (Exception e) {
            log.error("[Payment] 상태 조회 실패 - transactionKey: {}", transactionKey, e);
        }

        return PaymentInfo.from(payment);
    }

    public PaymentInfo getPaymentByOrderNo(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "주문을 찾을 수 없습니다: " + orderNo));
        Payment payment = paymentRepository.findByOrder(order)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "결제 정보를 찾을 수 없습니다: " + orderNo));
        return PaymentInfo.from(payment);
    }

    public List<PaymentInfo> getPendingPaymentsOlderThan(LocalDateTime dateTime) {
        return paymentRepository.findPendingPaymentsOlderThan(dateTime)
                .stream()
                .map(PaymentInfo::from)
                .toList();
    }

    public List<PaymentInfo> getPaymentsRequiringRetry() {
        return paymentRepository.findByRequiresRetryTrue()
                .stream()
                .map(PaymentInfo::from)
                .toList();
    }
}
