package com.loopers.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Retry Tracker
 * - Consumer에서 메시지별 재시도 횟수 추적
 * - 최대 재시도 횟수 초과 시 DLQ로 전송
 */
@Slf4j
@Component
public class RetryTracker {

    // topic-partition-offset을 key로 사용
    private final ConcurrentHashMap<String, Integer> retryCountMap = new ConcurrentHashMap<>();

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 재시도 가능 여부 확인 및 카운트 증가
     *
     * @param topic 토픽
     * @param partition 파티션
     * @param offset 오프셋
     * @return true = 재시도 가능, false = 최대 재시도 횟수 초과 (DLQ로 전송)
     */
    public boolean canRetry(String topic, int partition, long offset) {
        String key = buildKey(topic, partition, offset);
        int currentCount = retryCountMap.compute(key, (k, v) -> v == null ? 1 : v + 1);

        log.debug("[RetryTracker] Retry count for {}: {}/{}", key, currentCount, MAX_RETRY_COUNT);

        return currentCount <= MAX_RETRY_COUNT;
    }

    /**
     * 현재 재시도 횟수 조회
     *
     * @param topic 토픽
     * @param partition 파티션
     * @param offset 오프셋
     * @return 재시도 횟수 (0부터 시작)
     */
    public int getRetryCount(String topic, int partition, long offset) {
        String key = buildKey(topic, partition, offset);
        return retryCountMap.getOrDefault(key, 0);
    }

    /**
     * 성공 처리 시 카운터 제거
     *
     * @param topic 토픽
     * @param partition 파티션
     * @param offset 오프셋
     */
    public void clearRetryCount(String topic, int partition, long offset) {
        String key = buildKey(topic, partition, offset);
        retryCountMap.remove(key);
        log.debug("[RetryTracker] Cleared retry count for {}", key);
    }

    /**
     * 오래된 재시도 기록 정리 (메모리 관리)
     * - 정상적으로는 성공 시 clearRetryCount()로 제거되지만,
     *   비정상 종료 등으로 남은 기록을 주기적으로 정리
     */
    public void cleanupOldEntries() {
        int sizeBefore = retryCountMap.size();
        // 실제 운영에서는 타임스탬프 기반으로 오래된 항목 삭제
        // 여기서는 간단히 전체 클리어 (재시작 시에만 사용)
        if (sizeBefore > 10000) {
            log.warn("[RetryTracker] Clearing all retry counts (size: {})", sizeBefore);
            retryCountMap.clear();
        }
    }

    /**
     * 고유 키 생성
     */
    private String buildKey(String topic, int partition, long offset) {
        return String.format("%s-%d-%d", topic, partition, offset);
    }
}
