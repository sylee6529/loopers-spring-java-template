package com.loopers.application.metrics;

import com.loopers.application.event.like.ProductLikedEvent;
import com.loopers.application.event.like.ProductUnlikedEvent;
import com.loopers.application.event.order.OrderCompletedEvent;
import com.loopers.application.event.product.ProductViewedEvent;
import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 메트릭 집계 서비스
 * - Kafka 이벤트를 받아서 ProductMetrics 테이블 업데이트
 * - 멱등성 보장: event_handled 테이블로 중복 처리 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MetricsAggregationService {

    private final ProductMetricsRepository metricsRepository;
    private final EventHandledRepository eventHandledRepository;

    /**
     * 상품 좋아요 이벤트 처리
     */
    public void handleProductLiked(String eventId, ProductLikedEvent payload) {
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.warn("[Metrics] Duplicate event ignored - eventId: {}, type: PRODUCT_LIKED", eventId);
            return;
        }

        // ProductMetrics 업데이트
        ProductMetrics metrics = metricsRepository.findById(payload.productId())
            .orElse(ProductMetrics.create(payload.productId()));

        ZonedDateTime eventOccurredAt = toZonedDateTime(payload.likedAt());
        boolean updated = metrics.incrementLikeCount(eventOccurredAt);

        if (!updated) {
            log.warn("[Metrics] Out-of-order event ignored - eventId: {}, productId: {}, eventTime: {}, lastUpdated: {}",
                eventId, payload.productId(), eventOccurredAt, metrics.getLastUpdated());
            // 오래된 이벤트도 처리 완료 기록 (재처리 방지)
            eventHandledRepository.save(
                EventHandled.create(eventId, "PRODUCT_LIKED", String.valueOf(payload.productId()))
            );
            return;
        }

        metricsRepository.save(metrics);

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(eventId, "PRODUCT_LIKED", String.valueOf(payload.productId()))
        );

        log.info("[Metrics] Like count incremented - productId: {}, count: {}, eventTime: {}",
            payload.productId(), metrics.getLikeCount(), eventOccurredAt);
    }

    /**
     * 상품 좋아요 취소 이벤트 처리
     */
    public void handleProductUnliked(String eventId, ProductUnlikedEvent payload) {
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.warn("[Metrics] Duplicate event ignored - eventId: {}, type: PRODUCT_UNLIKED", eventId);
            return;
        }

        // ProductMetrics 업데이트
        ProductMetrics metrics = metricsRepository.findById(payload.productId())
            .orElse(ProductMetrics.create(payload.productId()));

        ZonedDateTime eventOccurredAt = toZonedDateTime(payload.unlikedAt());
        boolean updated = metrics.decrementLikeCount(eventOccurredAt);

        if (!updated) {
            log.warn("[Metrics] Out-of-order event ignored - eventId: {}, productId: {}, eventTime: {}, lastUpdated: {}",
                eventId, payload.productId(), eventOccurredAt, metrics.getLastUpdated());
            // 오래된 이벤트도 처리 완료 기록 (재처리 방지)
            eventHandledRepository.save(
                EventHandled.create(eventId, "PRODUCT_UNLIKED", String.valueOf(payload.productId()))
            );
            return;
        }

        metricsRepository.save(metrics);

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(eventId, "PRODUCT_UNLIKED", String.valueOf(payload.productId()))
        );

        log.info("[Metrics] Like count decremented - productId: {}, count: {}, eventTime: {}",
            payload.productId(), metrics.getLikeCount(), eventOccurredAt);
    }

    /**
     * 주문 완료 이벤트 처리 (판매량 집계)
     */
    public void handleOrderCompleted(String eventId, OrderCompletedEvent payload) {
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.warn("[Metrics] Duplicate event ignored - eventId: {}, type: ORDER_COMPLETED", eventId);
            return;
        }

        ZonedDateTime eventOccurredAt = toZonedDateTime(payload.completedAt());
        int updatedCount = 0;
        int ignoredCount = 0;

        // 각 주문 아이템별로 ProductMetrics 업데이트
        for (var item : payload.items()) {
            ProductMetrics metrics = metricsRepository.findById(item.productId())
                .orElse(ProductMetrics.create(item.productId()));

            // 판매 금액 = 수량 * 단가
            long totalAmount = item.quantity() * item.price().longValue();
            boolean updated = metrics.addSales(item.quantity(), totalAmount, eventOccurredAt);

            if (updated) {
                metricsRepository.save(metrics);
                updatedCount++;
                log.debug("[Metrics] Sales updated - productId: {}, quantity: {}, amount: {}, eventTime: {}",
                    item.productId(), item.quantity(), totalAmount, eventOccurredAt);
            } else {
                ignoredCount++;
                log.warn("[Metrics] Out-of-order sales event ignored - productId: {}, eventTime: {}, lastUpdated: {}",
                    item.productId(), eventOccurredAt, metrics.getLastUpdated());
            }
        }

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(eventId, "ORDER_COMPLETED", payload.orderNo())
        );

        log.info("[Metrics] Sales aggregated for order: {} ({} items updated, {} ignored)",
            payload.orderNo(), updatedCount, ignoredCount);
    }

    /**
     * 상품 조회 이벤트 처리 (조회수 집계)
     */
    public void handleProductViewed(String eventId, ProductViewedEvent payload) {
        // 멱등성 체크
        if (eventHandledRepository.existsById(eventId)) {
            log.warn("[Metrics] Duplicate event ignored - eventId: {}, type: PRODUCT_VIEWED", eventId);
            return;
        }

        // ProductMetrics 업데이트
        ProductMetrics metrics = metricsRepository.findById(payload.productId())
            .orElse(ProductMetrics.create(payload.productId()));

        ZonedDateTime eventOccurredAt = toZonedDateTime(payload.viewedAt());
        boolean updated = metrics.incrementViewCount(eventOccurredAt);

        if (!updated) {
            log.warn("[Metrics] Out-of-order event ignored - eventId: {}, productId: {}, eventTime: {}, lastUpdated: {}",
                eventId, payload.productId(), eventOccurredAt, metrics.getLastUpdated());
            // 오래된 이벤트도 처리 완료 기록 (재처리 방지)
            eventHandledRepository.save(
                EventHandled.create(eventId, "PRODUCT_VIEWED", String.valueOf(payload.productId()))
            );
            return;
        }

        metricsRepository.save(metrics);

        // 처리 완료 기록
        eventHandledRepository.save(
            EventHandled.create(eventId, "PRODUCT_VIEWED", String.valueOf(payload.productId()))
        );

        log.info("[Metrics] View count incremented - productId: {}, count: {}, eventTime: {}",
            payload.productId(), metrics.getViewCount(), eventOccurredAt);
    }

    /**
     * LocalDateTime을 ZonedDateTime으로 변환 (Asia/Seoul)
     */
    private ZonedDateTime toZonedDateTime(java.time.LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.of("Asia/Seoul"));
    }
}
