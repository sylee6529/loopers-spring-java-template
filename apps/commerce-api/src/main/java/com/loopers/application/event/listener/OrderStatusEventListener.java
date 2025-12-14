package com.loopers.application.event.listener;

import com.loopers.application.event.payment.PaymentCompletedEvent;
import com.loopers.application.event.order.OrderCompletedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusEventListener {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[OrderStatusEventListener] 결제 완료 이벤트 처리 - orderNo: {}, status: {}",
                 event.orderNo(), event.status());

        Order order = orderRepository.findByOrderNo(event.orderNo())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "주문을 찾을 수 없습니다: " + event.orderNo()));

        if (event.isSuccess()) {
            // 이미 결제 완료된 경우 스킵 (멱등성)
            if (order.isPaid()) {
                log.debug("[OrderStatusEventListener] 이미 결제 완료된 주문 - orderNo: {}", event.orderNo());
                return;
            }

            order.markAsPaid();
            orderRepository.save(order);
            log.info("[OrderStatusEventListener] 주문 완료 처리 - orderNo: {}", event.orderNo());

            eventPublisher.publishEvent(new OrderCompletedEvent(
                order.getOrderNo(),
                order.getMemberId(),
                order.getTotalPrice(),
                java.time.LocalDateTime.now()
            ));

        } else if (event.isFailed()) {
            log.warn("[OrderStatusEventListener] 결제 실패 - orderNo: {}, reason: {}",
                     event.orderNo(), event.failureReason());
            // TODO: 결제 실패 처리 (알림, 주문 취소 등)
        }
    }
}
