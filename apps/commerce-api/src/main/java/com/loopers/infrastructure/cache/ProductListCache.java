package com.loopers.infrastructure.cache;

import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.domain.product.enums.ProductSortCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductListCache {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final RedisTemplate<String, Object> cacheRedisTemplate;

    @SuppressWarnings("unchecked")
    public Optional<Page<ProductSummaryInfo>> get(Long brandId, ProductSortCondition sort, int page, int size) {
        try {
            String key = CacheKeyGenerator.productListKey(brandId, sort, page, size);
            Object value = cacheRedisTemplate.opsForValue().get(key);

            if (value != null) {
                log.debug("[ProductListCache] hit key={}", key);
                return Optional.of((Page<ProductSummaryInfo>) value);
            }

            log.debug("[ProductListCache] miss key={}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[ProductListCache] get failed for brandId={}, sort={}, page={}, size={}, error={}",
                    brandId, sort, page, size, e.getMessage());
            return Optional.empty();
        }
    }

    public void set(Long brandId, ProductSortCondition sort, int page, int size, Page<ProductSummaryInfo> products) {
        try {
            String key = CacheKeyGenerator.productListKey(brandId, sort, page, size);
            cacheRedisTemplate.opsForValue().set(key, products, TTL);
            log.debug("[ProductListCache] set key={}, ttl={}, count={}", key, TTL, products.getNumberOfElements());
        } catch (Exception e) {
            log.warn("[ProductListCache] set failed for brandId={}, sort={}, page={}, size={}, error={}",
                    brandId, sort, page, size, e.getMessage());
        }
    }
}
