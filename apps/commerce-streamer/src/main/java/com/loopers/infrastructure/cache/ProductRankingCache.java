package com.loopers.infrastructure.cache;

import com.loopers.config.RankingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 상품 랭킹 ZSET 캐시 (적재용 - commerce-streamer)
 * Key: ranking:all:v1:{yyyyMMdd}
 * Member: productId
 * Score: 가중치 적용된 점수
 */
@Slf4j
@Component
public class ProductRankingCache {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    private final RedisTemplate<String, Object> cacheRedisTemplate;
    private final RankingConfig rankingConfig;

    /**
     * TTL이 설정된 키를 추적 (Race Condition 방지)
     * 날짜가 바뀌면 자동으로 새 키에 대해 TTL 설정
     */
    private final AtomicReference<String> ttlInitializedKey = new AtomicReference<>();

    public ProductRankingCache(RedisTemplate<String, Object> cacheRedisTemplate, RankingConfig rankingConfig) {
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.rankingConfig = rankingConfig;
    }

    /**
     * 조회 이벤트 점수 추가
     */
    public void addViewScore(Long productId) {
        double score = rankingConfig.getWeight().getView();
        incrementScore(productId, score);
        log.debug("[Ranking] View score added - productId: {}, score: {}", productId, score);
    }

    /**
     * 좋아요 이벤트 점수 추가
     */
    public void addLikeScore(Long productId) {
        double score = rankingConfig.getWeight().getLike();
        incrementScore(productId, score);
        log.debug("[Ranking] Like score added - productId: {}, score: {}", productId, score);
    }

    /**
     * 좋아요 취소 이벤트 점수 감소
     * - 좋아요 추가와 동일한 가중치만큼 감소
     * - 점수가 0 이하가 되어도 ZSET에서 자동 제거되지 않음 (일관성 유지)
     */
    public void subtractLikeScore(Long productId) {
        double score = rankingConfig.getWeight().getLike();
        decrementScore(productId, score);
        log.debug("[Ranking] Like score subtracted - productId: {}, score: -{}", productId, score);
    }

    /**
     * 주문 이벤트 점수 추가
     * - log 정규화를 적용하여 고가 상품의 과도한 점수 편중 방지
     * - Math.log1p(x) = ln(1 + x)로 안전하게 계산
     *
     * @param productId 상품 ID
     * @param price 상품 단가
     * @param quantity 주문 수량
     */
    public void addOrderScore(Long productId, long price, int quantity) {
        double weight = rankingConfig.getWeight().getOrder();
        long orderAmount = price * quantity;
        // log 정규화: 가격 스케일 차이를 완화하여 공정한 랭킹 반영
        // 예: 100,000원 → log1p(100000) ≈ 11.5, 1,000원 → log1p(1000) ≈ 6.9
        double score = weight * Math.log1p(orderAmount);
        incrementScore(productId, score);
        log.debug("[Ranking] Order score added - productId: {}, price: {}, qty: {}, amount: {}, score: {}",
                productId, price, quantity, orderAmount, score);
    }

    /**
     * 주문 아이템 배치 점수 추가 (Pipeline 사용)
     * - 여러 아이템을 한 번의 Redis 요청으로 처리하여 네트워크 오버헤드 감소
     *
     * @param orderItems 주문 아이템 목록 (productId -> OrderItemScore)
     */
    public void addOrderScoresBatch(List<OrderItemScore> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }

        try {
            String key = getTodayKey();
            double weight = rankingConfig.getWeight().getOrder();

            // Pipeline으로 여러 ZINCRBY를 한 번에 실행
            cacheRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public Object execute(RedisOperations operations) throws DataAccessException {
                    for (OrderItemScore item : orderItems) {
                        long orderAmount = item.price() * item.quantity();
                        double score = weight * Math.log1p(orderAmount);
                        operations.opsForZSet().incrementScore(key, item.productId().toString(), score);
                    }
                    return null;
                }
            });

            // TTL 설정 (CAS로 원자적 처리)
            ensureTtl(key);

            log.debug("[Ranking] Batch order scores added - {} items", orderItems.size());
        } catch (Exception e) {
            log.warn("[Ranking] Failed to add batch order scores - error: {}", e.getMessage());
            // Fallback: 개별 처리
            for (OrderItemScore item : orderItems) {
                addOrderScore(item.productId(), item.price(), item.quantity());
            }
        }
    }

    /**
     * 주문 아이템 점수 정보
     */
    public record OrderItemScore(Long productId, long price, int quantity) {}

    /**
     * ZSET 점수 증가 (ZINCRBY)
     */
    private void incrementScore(Long productId, double score) {
        try {
            String key = getTodayKey();
            cacheRedisTemplate.opsForZSet().incrementScore(key, productId.toString(), score);
            ensureTtl(key);
        } catch (Exception e) {
            log.warn("[Ranking] Failed to increment score - productId: {}, error: {}",
                    productId, e.getMessage());
        }
    }

    /**
     * ZSET 점수 감소 (ZINCRBY with negative value)
     */
    private void decrementScore(Long productId, double score) {
        try {
            String key = getTodayKey();
            // ZINCRBY는 음수 값으로 감소 가능
            cacheRedisTemplate.opsForZSet().incrementScore(key, productId.toString(), -score);
            ensureTtl(key);
        } catch (Exception e) {
            log.warn("[Ranking] Failed to decrement score - productId: {}, error: {}",
                    productId, e.getMessage());
        }
    }

    /**
     * TTL 설정 (CAS로 원자적 처리)
     * AtomicReference를 사용하여 날짜별로 한 번만 설정 (Race Condition 방지)
     */
    private void ensureTtl(String key) {
        if (!key.equals(ttlInitializedKey.get())) {
            if (ttlInitializedKey.compareAndSet(ttlInitializedKey.get(), key)) {
                cacheRedisTemplate.expire(key, rankingConfig.getTtlDays(), TimeUnit.DAYS);
                log.info("[Ranking] TTL set for key: {} ({} days)", key, rankingConfig.getTtlDays());
            }
        }
    }

    /**
     * 오늘 날짜 키 생성 (Asia/Seoul 기준)
     * CacheKeyGenerator를 사용하여 commerce-api와 동일한 키 형식 보장
     */
    private String getTodayKey() {
        String date = LocalDate.now(ZONE_ID).format(DATE_FORMATTER);
        return CacheKeyGenerator.dailyRankingKey(date);
    }

    /**
     * 특정 날짜 키 생성
     */
    public static String getKeyForDate(String date) {
        return CacheKeyGenerator.dailyRankingKey(date);
    }
}
