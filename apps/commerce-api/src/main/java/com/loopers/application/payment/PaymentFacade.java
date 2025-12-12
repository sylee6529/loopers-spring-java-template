package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    @Transactional
    public PaymentInfo cancelPayment(String userId, String orderNo, String reason) {
        Payment payment = paymentService.findByOrderNo(orderNo);

        if (payment.getStatus() == com.loopers.domain.payment.PaymentStatus.SUCCESS) {
            throw new com.loopers.support.error.CoreException(
                    com.loopers.support.error.ErrorType.BAD_REQUEST,
                    "이미 성공한 결제는 취소할 수 없습니다.");
        }

        paymentService.cancelPayment(payment.getId(), reason);

        Order order = payment.getOrder();
        if (!order.isPaid()) {
            order.cancel();
            orderRepository.save(order);

            order.getItems().forEach(item ->
                    productRepository.increaseStock(item.getProductId(), item.getQuantity()));
        }

        log.info("[PaymentFacade] 결제 취소 완료 - orderNo: {}, reason: {}", orderNo, reason);
        return PaymentInfo.from(paymentService.findById(payment.getId()));
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
