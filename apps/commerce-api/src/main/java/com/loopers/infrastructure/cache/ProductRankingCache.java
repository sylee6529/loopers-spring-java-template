package com.loopers.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 상품 랭킹 ZSET 캐시 (조회용 - commerce-api)
 * Key: ranking:all:v1:{yyyyMMdd}
 * Member: productId
 * Score: 가중치 적용된 점수
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProductRankingCache {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");

    private final RedisTemplate<String, Object> cacheRedisTemplate;

    /**
     * 상품의 오늘 날짜 기준 순위 조회
     * @param productId 상품 ID
     * @return 순위 (1-based), 순위권 밖이면 null
     */
    public Integer getRank(Long productId) {
        return getRank(productId, getTodayDate());
    }

    /**
     * 상품의 특정 날짜 순위 조회
     * @param productId 상품 ID
     * @param date 날짜 (yyyyMMdd 형식)
     * @return 순위 (1-based), 순위권 밖이면 null
     */
    public Integer getRank(Long productId, String date) {
        try {
            String key = CacheKeyGenerator.dailyRankingKey(date);
            Long rank = cacheRedisTemplate.opsForZSet().reverseRank(key, productId.toString());

            if (rank == null) {
                log.debug("[Ranking] Product not in ranking - productId: {}, date: {}", productId, date);
                return null;
            }

            // 0-based → 1-based 변환
            int ranking = rank.intValue() + 1;
            log.debug("[Ranking] Rank retrieved - productId: {}, date: {}, rank: {}", productId, date, ranking);
            return ranking;
        } catch (Exception e) {
            log.warn("[Ranking] Failed to get rank - productId: {}, date: {}, error: {}",
                    productId, date, e.getMessage());
            return null;
        }
    }

    /**
     * 상품의 점수 조회
     * @param productId 상품 ID
     * @return 점수, 없으면 null
     */
    public Double getScore(Long productId) {
        return getScore(productId, getTodayDate());
    }

    /**
     * 상품의 특정 날짜 점수 조회
     */
    public Double getScore(Long productId, String date) {
        try {
            String key = CacheKeyGenerator.dailyRankingKey(date);
            return cacheRedisTemplate.opsForZSet().score(key, productId.toString());
        } catch (Exception e) {
            log.warn("[Ranking] Failed to get score - productId: {}, date: {}, error: {}",
                    productId, date, e.getMessage());
            return null;
        }
    }

    /**
     * 오늘 날짜 문자열 반환 (Asia/Seoul 기준)
     */
    public String getTodayDate() {
        return LocalDate.now(ZONE_ID).format(DATE_FORMATTER);
    }

    /**
     * 랭킹 상위 목록 조회 (페이지네이션)
     * @param date 날짜 (yyyyMMdd 형식)
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 크기
     * @return 상품 ID와 점수 목록 (순위 높은 순)
     */
    public List<RankingEntry> getTopRankings(String date, int page, int size) {
        try {
            String key = CacheKeyGenerator.dailyRankingKey(date);
            long start = (long) page * size;
            long end = start + size - 1;

            Set<ZSetOperations.TypedTuple<Object>> results =
                    cacheRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

            if (results == null || results.isEmpty()) {
                log.debug("[Ranking] No rankings found - date: {}, page: {}, size: {}", date, page, size);
                return Collections.emptyList();
            }

            int rank = page * size + 1;  // 시작 순위 (1-based)
            List<RankingEntry> entries = new java.util.ArrayList<>();

            for (ZSetOperations.TypedTuple<Object> tuple : results) {
                Long productId = Long.parseLong(tuple.getValue().toString());
                Double score = tuple.getScore();
                entries.add(new RankingEntry(rank++, productId, score));
            }

            log.debug("[Ranking] Retrieved {} rankings - date: {}, page: {}", entries.size(), date, page);
            return entries;
        } catch (Exception e) {
            log.warn("[Ranking] Failed to get top rankings - date: {}, page: {}, error: {}",
                    date, page, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 전체 랭킹 수 조회
     */
    public long getTotalCount(String date) {
        try {
            String key = CacheKeyGenerator.dailyRankingKey(date);
            Long count = cacheRedisTemplate.opsForZSet().zCard(key);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("[Ranking] Failed to get total count - date: {}, error: {}", date, e.getMessage());
            return 0;
        }
    }

    /**
     * 랭킹 엔트리 (순위, 상품ID, 점수)
     */
    public record RankingEntry(int rank, Long productId, Double score) {}
}
