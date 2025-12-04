package com.loopers.infrastructure.cache;

import com.loopers.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 좋아요 카운터 동기화 스케줄러
 * Redis의 좋아요 카운트를 주기적으로 DB에 동기화
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LikeCountSyncScheduler {

    private final ProductLikeCountCache productLikeCountCache;
    private final ProductRepository productRepository;

    /**
     * 1분마다 Redis → DB 동기화
     */
    @Scheduled(fixedDelay = 60000) // 1분
    @Transactional
    public void syncLikeCountsToDatabase() {
        try {
            // 1. Redis에서 모든 카운터 조회
            Map<Long, Long> cacheCounts = productLikeCountCache.getAllCounts();

            if (cacheCounts.isEmpty()) {
                log.debug("[LikeCountSyncScheduler] No cache counts to sync");
                return;
            }

            int successCount = 0;
            int failCount = 0;

            // 2. 각 상품의 카운트를 DB에 업데이트
            for (Map.Entry<Long, Long> entry : cacheCounts.entrySet()) {
                Long productId = entry.getKey();
                Long count = entry.getValue();

                try {
                    int updated = productRepository.updateLikeCount(productId, count);

                    if (updated > 0) {
                        successCount++;
                        log.debug("[LikeCountSyncScheduler] Synced productId={}, count={}", productId, count);
                    } else {
                        failCount++;
                        log.warn("[LikeCountSyncScheduler] Product not found for sync: productId={}", productId);
                    }
                } catch (Exception e) {
                    failCount++;
                    log.error("[LikeCountSyncScheduler] Failed to sync productId={}, error={}", productId, e.getMessage());
                }
            }

            log.info("[LikeCountSyncScheduler] Sync completed. total={}, success={}, fail={}",
                    cacheCounts.size(), successCount, failCount);

        } catch (Exception e) {
            log.error("[LikeCountSyncScheduler] Sync failed with error={}", e.getMessage(), e);
        }
    }
}
