package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment 애플리케이션 Facade
 * - 트랜잭션 경계 정의
 * - 여러 도메인 서비스 조율
 * - Order와 Payment 연동
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    // TODO: OrderService 연동 필요 시 추가

    /**
     * 결제 요청
     */
    @Transactional
    public PaymentInfo requestPayment(String userId, PaymentCommand.RequestPayment command) {
        return paymentService.requestPayment(userId, command);
    }

    /**
     * PG 콜백 처리
     *
     * 1. Payment 상태 업데이트
     * 2. Order 상태 업데이트 (성공/실패)
     * 3. 실패 시 재고/포인트 복구
     */
    @Transactional
    public PaymentInfo processCallback(String userId, PaymentCommand.ProcessCallback command) {
        // 1. Payment 상태 업데이트
        PaymentInfo paymentInfo = paymentService.processCallback(userId, command);

        // 2. Order 상태 업데이트 (TODO: OrderService 연동)
        if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.SUCCESS) {
            log.info("[PaymentFacade] 결제 성공 - orderId: {}, 주문 완료 처리 필요", paymentInfo.orderId());
            // orderService.completeOrder(paymentInfo.orderId());
        } else if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.FAILED) {
            log.warn("[PaymentFacade] 결제 실패 - orderId: {}, 주문 취소 및 복구 필요", paymentInfo.orderId());
            // orderService.failOrder(paymentInfo.orderId(), paymentInfo.reason());
            // TODO: 재고 복구, 포인트 환불 등
        }

        return paymentInfo;
    }

    /**
     * 결제 상태 동기화
     */
    @Transactional
    public PaymentInfo syncPaymentStatus(String userId, String transactionKey) {
        PaymentInfo paymentInfo = paymentService.syncPaymentStatus(userId, transactionKey);

        // Order 상태도 함께 동기화
        if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.SUCCESS) {
            log.info("[PaymentFacade] 동기화 완료 (성공) - orderId: {}", paymentInfo.orderId());
            // orderService.completeOrder(paymentInfo.orderId());
        } else if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.FAILED) {
            log.warn("[PaymentFacade] 동기화 완료 (실패) - orderId: {}", paymentInfo.orderId());
            // orderService.failOrder(paymentInfo.orderId(), paymentInfo.reason());
        }

        return paymentInfo;
    }

    /**
     * orderId로 결제 정보 조회
     */
    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrderId(String orderId) {
        return paymentService.getPaymentByOrderId(orderId);
    }

    /**
     * PENDING 상태 결제 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PaymentInfo> getPendingPaymentsOlderThan(LocalDateTime dateTime) {
        return paymentService.getPendingPaymentsOlderThan(dateTime);
    }

    /**
     * 재시도 필요한 결제 목록 조회
     */
    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsRequiringRetry() {
        return paymentService.getPaymentsRequiringRetry();
    }
}
