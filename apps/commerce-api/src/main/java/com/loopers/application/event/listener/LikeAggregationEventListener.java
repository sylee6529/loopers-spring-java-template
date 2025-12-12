package com.loopers.application.event.listener;

import com.loopers.application.event.like.ProductLikedEvent;
import com.loopers.application.event.like.ProductUnlikedEvent;
import com.loopers.infrastructure.cache.CacheInvalidationService;
import com.loopers.infrastructure.cache.MemberLikesCache;
import com.loopers.infrastructure.cache.ProductLikeCountCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좋아요 집계 이벤트 리스너
 *
 * - 좋아요/취소 이벤트를 수신하여 Redis 캐시를 비동기로 업데이트
 * - eventual consistency: DB 저장 후 Redis는 비동기로 업데이트
 * - Redis 실패 시에도 DB는 정상 저장됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeAggregationEventListener {

    private final ProductLikeCountCache productLikeCountCache;
    private final MemberLikesCache memberLikesCache;
    private final CacheInvalidationService cacheInvalidationService;

    /**
     * 좋아요 이벤트 처리 (비동기)
     * - Redis 좋아요 카운트 증가
     * - 회원 좋아요 목록 캐시 업데이트
     * - 상품 캐시 무효화
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        log.info("[LikeAggregationEventListener] 좋아요 이벤트 처리 - memberId: {}, productId: {}",
                 event.memberId(), event.productId());

        // 각 캐시 작업을 독립적으로 처리 (하나 실패해도 나머지 계속 진행)
        try {
            productLikeCountCache.increment(event.productId());
        } catch (Exception e) {
            log.error("[LikeAggregationEventListener] 좋아요 카운트 증가 실패 - productId: {}", event.productId(), e);
        }

        try {
            memberLikesCache.add(event.memberId(), event.productId());
        } catch (Exception e) {
            log.error("[LikeAggregationEventListener] 회원 좋아요 캐시 업데이트 실패 - memberId: {}, productId: {}",
                     event.memberId(), event.productId(), e);
        }

        try {
            cacheInvalidationService.invalidateOnLikeChange(event.productId(), event.brandId());
        } catch (Exception e) {
            log.error("[LikeAggregationEventListener] 캐시 무효화 실패 - productId: {}", event.productId(), e);
        }

        log.debug("[LikeAggregationEventListener] 좋아요 집계 완료 - productId: {}", event.productId());
    }

    /**
     * 좋아요 취소 이벤트 처리 (비동기)
     * - Redis 좋아요 카운트 감소
     * - 회원 좋아요 목록 캐시 업데이트
     * - 상품 캐시 무효화
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductUnliked(ProductUnlikedEvent event) {
        log.info("[LikeAggregationEventListener] 좋아요 취소 이벤트 처리 - memberId: {}, productId: {}",
                 event.memberId(), event.productId());

        // 각 캐시 작업을 독립적으로 처리 (하나 실패해도 나머지 계속 진행)
        try {
            productLikeCountCache.decrement(event.productId());
        } catch (Exception e) {
            log.error("[LikeAggregationEventListener] 좋아요 카운트 감소 실패 - productId: {}", event.productId(), e);
        }

        try {
            memberLikesCache.remove(event.memberId(), event.productId());
        } catch (Exception e) {
            log.error("[LikeAggregationEventListener] 회원 좋아요 캐시 업데이트 실패 - memberId: {}, productId: {}",
                     event.memberId(), event.productId(), e);
        }

        try {
            cacheInvalidationService.invalidateOnLikeChange(event.productId(), event.brandId());
        } catch (Exception e) {
            log.error("[LikeAggregationEventListener] 캐시 무효화 실패 - productId: {}", event.productId(), e);
        }

        log.debug("[LikeAggregationEventListener] 좋아요 취소 집계 완료 - productId: {}", event.productId());
    }
}
