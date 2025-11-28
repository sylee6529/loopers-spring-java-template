package com.loopers.infrastructure.cache;

import com.loopers.application.product.ProductDetailInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 상품 상세 정보 캐시
 * isLikedByMember는 false로 저장 (실제 값은 조회 시 동적 계산)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProductDetailCache {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> cacheRedisTemplate;

    public Optional<ProductDetailInfo> get(Long productId) {
        try {
            String key = CacheKeyGenerator.productDetailKey(productId);
            Object value = cacheRedisTemplate.opsForValue().get(key);

            if (value != null) {
                log.debug("[ProductDetailCache] hit key={}", key);
                return Optional.of((ProductDetailInfo) value);
            }

            log.debug("[ProductDetailCache] miss key={}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[ProductDetailCache] get failed for productId={}, error={}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    public void set(Long productId, ProductDetailInfo info) {
        try {
            String key = CacheKeyGenerator.productDetailKey(productId);

            // isLikedByMember=false로 고정해서 저장 (유저 무관)
            ProductDetailInfo cacheValue = ProductDetailInfo.builder()
                    .id(info.getId())
                    .name(info.getName())
                    .description(info.getDescription())
                    .brandName(info.getBrandName())
                    .brandDescription(info.getBrandDescription())
                    .price(info.getPrice())
                    .stock(info.getStock())
                    .likeCount(info.getLikeCount())
                    .isLikedByMember(false)  // ⭐ false로 고정
                    .build();

            cacheRedisTemplate.opsForValue().set(key, cacheValue, TTL);
            log.debug("[ProductDetailCache] set key={}, ttl={}", key, TTL);
        } catch (Exception e) {
            log.warn("[ProductDetailCache] set failed for productId={}, error={}", productId, e.getMessage());
        }
    }

    public void delete(Long productId) {
        try {
            String key = CacheKeyGenerator.productDetailKey(productId);
            cacheRedisTemplate.delete(key);
            log.debug("[ProductDetailCache] delete key={}", key);
        } catch (Exception e) {
            log.warn("[ProductDetailCache] delete failed for productId={}, error={}", productId, e.getMessage());
        }
    }
}
