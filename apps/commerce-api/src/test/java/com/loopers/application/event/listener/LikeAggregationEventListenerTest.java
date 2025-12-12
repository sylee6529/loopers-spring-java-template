package com.loopers.application.event.listener;

import com.loopers.application.event.like.ProductLikedEvent;
import com.loopers.application.event.like.ProductUnlikedEvent;
import com.loopers.infrastructure.cache.CacheInvalidationService;
import com.loopers.infrastructure.cache.MemberLikesCache;
import com.loopers.infrastructure.cache.ProductLikeCountCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@DisplayName("좋아요 집계 이벤트 리스너 테스트")
class LikeAggregationEventListenerTest {

    private LikeAggregationEventListener listener;
    private ProductLikeCountCache productLikeCountCache;
    private MemberLikesCache memberLikesCache;
    private CacheInvalidationService cacheInvalidationService;

    @BeforeEach
    void setUp() {
        productLikeCountCache = mock(ProductLikeCountCache.class);
        memberLikesCache = mock(MemberLikesCache.class);
        cacheInvalidationService = mock(CacheInvalidationService.class);

        listener = new LikeAggregationEventListener(
            productLikeCountCache,
            memberLikesCache,
            cacheInvalidationService
        );
    }

    @Test
    @DisplayName("좋아요 이벤트 수신 시 Redis 캐시가 업데이트된다")
    void 좋아요_이벤트_Redis_업데이트() {
        // given
        ProductLikedEvent event = new ProductLikedEvent(
            1L,  // memberId
            100L,  // productId
            10L,  // brandId
            LocalDateTime.now()
        );

        // when
        listener.handleProductLiked(event);

        // then
        verify(productLikeCountCache, times(1)).increment(100L);
        verify(memberLikesCache, times(1)).add(1L, 100L);
        verify(cacheInvalidationService, times(1)).invalidateOnLikeChange(100L, 10L);
    }

    @Test
    @DisplayName("좋아요 취소 이벤트 수신 시 Redis 캐시가 업데이트된다")
    void 좋아요_취소_이벤트_Redis_업데이트() {
        // given
        ProductUnlikedEvent event = new ProductUnlikedEvent(
            1L,  // memberId
            100L,  // productId
            10L,  // brandId
            LocalDateTime.now()
        );

        // when
        listener.handleProductUnliked(event);

        // then
        verify(productLikeCountCache, times(1)).decrement(100L);
        verify(memberLikesCache, times(1)).remove(1L, 100L);
        verify(cacheInvalidationService, times(1)).invalidateOnLikeChange(100L, 10L);
    }

    @Test
    @DisplayName("Redis 업데이트 실패 시 예외가 발생하지 않는다")
    void Redis_업데이트_실패_시_예외_처리() {
        // given
        ProductLikedEvent event = new ProductLikedEvent(
            1L, 100L, 10L, LocalDateTime.now()
        );

        // Redis 작업 실패 시뮬레이션
        doThrow(new RuntimeException("Redis connection failed"))
            .when(productLikeCountCache).increment(anyLong());

        // when & then - 예외가 전파되지 않아야 함 (로그만 남김)
        // 실제로는 listener 내부에서 try-catch로 처리하므로 예외가 발생하지 않음
        listener.handleProductLiked(event);

        // Redis 실패해도 나머지 캐시는 업데이트 시도
        verify(memberLikesCache, times(1)).add(1L, 100L);
        verify(cacheInvalidationService, times(1)).invalidateOnLikeChange(100L, 10L);
    }
}
