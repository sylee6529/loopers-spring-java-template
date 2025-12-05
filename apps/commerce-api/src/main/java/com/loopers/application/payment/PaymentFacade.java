package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;

    @Transactional
    public PaymentInfo requestPayment(String userId, PaymentCommand.RequestPayment command) {
        return paymentService.requestPayment(userId, command);
    }

    @Transactional
    public PaymentInfo processCallback(String userId, PaymentCommand.ProcessCallback command) {
        PaymentInfo paymentInfo = paymentService.processCallback(userId, command);

        if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.SUCCESS) {
            Order order = orderRepository.findByOrderNo(paymentInfo.orderNo())
                    .orElseThrow(() -> new com.loopers.support.error.CoreException(
                            com.loopers.support.error.ErrorType.NOT_FOUND,
                            "주문을 찾을 수 없습니다: " + paymentInfo.orderNo()));
            order.markAsPaid();
            orderRepository.save(order);
            log.info("[PaymentFacade] 결제 성공 및 주문 완료 - orderNo: {}", paymentInfo.orderNo());
        } else if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.FAILED) {
            log.warn("[PaymentFacade] 결제 실패 - orderNo: {}", paymentInfo.orderNo());
        }

        return paymentInfo;
    }

    @Transactional
    public PaymentInfo syncPaymentStatus(String userId, String transactionKey) {
        PaymentInfo paymentInfo = paymentService.syncPaymentStatus(userId, transactionKey);

        if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.SUCCESS) {
            Order order = orderRepository.findByOrderNo(paymentInfo.orderNo())
                    .orElseThrow(() -> new com.loopers.support.error.CoreException(
                            com.loopers.support.error.ErrorType.NOT_FOUND,
                            "주문을 찾을 수 없습니다: " + paymentInfo.orderNo()));
            if (!order.isPaid()) {
                order.markAsPaid();
                orderRepository.save(order);
            }
            log.info("[PaymentFacade] 동기화 완료 (성공) 및 주문 완료 - orderNo: {}", paymentInfo.orderNo());
        } else if (paymentInfo.status() == com.loopers.domain.payment.PaymentStatus.FAILED) {
            log.warn("[PaymentFacade] 동기화 완료 (실패) - orderNo: {}", paymentInfo.orderNo());
        }

        return paymentInfo;
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrderNo(String orderNo) {
        return paymentService.getPaymentByOrderNo(orderNo);
    }

    @Transactional(readOnly = true)
    public List<PaymentInfo> getPendingPaymentsOlderThan(LocalDateTime dateTime) {
        return paymentService.getPendingPaymentsOlderThan(dateTime);
    }

    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsRequiringRetry() {
        return paymentService.getPaymentsRequiringRetry();
    }
}
