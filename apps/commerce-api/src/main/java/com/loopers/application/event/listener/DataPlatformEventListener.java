package com.loopers.application.event.listener;

import com.loopers.application.event.order.OrderCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문/결제 결과를 데이터 플랫폼으로 전송하는 비동기 리스너.
 * 외부 I/O 실패 시에도 본 트랜잭션에 영향을 주지 않도록 예외를 삼킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataPlatformEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            // TODO: 실제 데이터 플랫폼 API 연동. 현재는 더미 호출로 대체.
            log.info("[DataPlatform] 주문 완료 전송 - orderNo: {}, memberId: {}, amount: {}",
                    event.orderNo(), event.memberId(), event.totalPrice());
        } catch (Exception e) {
            log.error("[DataPlatform] 주문 완료 전송 실패 - orderNo: {}", event.orderNo(), e);
        }
    }
}
