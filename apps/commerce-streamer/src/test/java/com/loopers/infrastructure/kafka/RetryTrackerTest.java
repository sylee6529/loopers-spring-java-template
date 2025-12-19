package com.loopers.infrastructure.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retry Tracker 테스트
 */
class RetryTrackerTest {

    private RetryTracker retryTracker;

    @BeforeEach
    void setUp() {
        retryTracker = new RetryTracker();
    }

    @Test
    @DisplayName("첫 번째 재시도는 허용해야 함")
    void shouldAllowFirstRetry() {
        // when
        boolean canRetry = retryTracker.canRetry("test-topic", 0, 100L);

        // then
        assertThat(canRetry).isTrue();
        assertThat(retryTracker.getRetryCount("test-topic", 0, 100L)).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 재시도 횟수(3회)까지 허용해야 함")
    void shouldAllowUpToMaxRetries() {
        // when
        boolean canRetry1 = retryTracker.canRetry("test-topic", 0, 100L);  // 1회
        boolean canRetry2 = retryTracker.canRetry("test-topic", 0, 100L);  // 2회
        boolean canRetry3 = retryTracker.canRetry("test-topic", 0, 100L);  // 3회

        // then
        assertThat(canRetry1).isTrue();
        assertThat(canRetry2).isTrue();
        assertThat(canRetry3).isTrue();
        assertThat(retryTracker.getRetryCount("test-topic", 0, 100L)).isEqualTo(3);
    }

    @Test
    @DisplayName("최대 재시도 횟수(3회) 초과 시 재시도 불가")
    void shouldRejectAfterMaxRetries() {
        // given
        retryTracker.canRetry("test-topic", 0, 100L);  // 1회
        retryTracker.canRetry("test-topic", 0, 100L);  // 2회
        retryTracker.canRetry("test-topic", 0, 100L);  // 3회

        // when
        boolean canRetry4 = retryTracker.canRetry("test-topic", 0, 100L);  // 4회

        // then
        assertThat(canRetry4).isFalse();
        assertThat(retryTracker.getRetryCount("test-topic", 0, 100L)).isEqualTo(4);
    }

    @Test
    @DisplayName("서로 다른 메시지는 독립적으로 재시도 카운트 관리")
    void shouldTrackRetriesSeparately() {
        // when
        retryTracker.canRetry("test-topic", 0, 100L);
        retryTracker.canRetry("test-topic", 0, 100L);
        retryTracker.canRetry("test-topic", 1, 200L);

        // then
        assertThat(retryTracker.getRetryCount("test-topic", 0, 100L)).isEqualTo(2);
        assertThat(retryTracker.getRetryCount("test-topic", 1, 200L)).isEqualTo(1);
    }

    @Test
    @DisplayName("성공 처리 시 재시도 카운트 제거")
    void shouldClearRetryCountOnSuccess() {
        // given
        retryTracker.canRetry("test-topic", 0, 100L);
        retryTracker.canRetry("test-topic", 0, 100L);

        // when
        retryTracker.clearRetryCount("test-topic", 0, 100L);

        // then
        assertThat(retryTracker.getRetryCount("test-topic", 0, 100L)).isEqualTo(0);
    }

    @Test
    @DisplayName("재시도 카운트 제거 후 다시 처음부터 재시도 가능")
    void shouldRestartRetryCountAfterClear() {
        // given
        retryTracker.canRetry("test-topic", 0, 100L);
        retryTracker.canRetry("test-topic", 0, 100L);
        retryTracker.clearRetryCount("test-topic", 0, 100L);

        // when
        boolean canRetry = retryTracker.canRetry("test-topic", 0, 100L);

        // then
        assertThat(canRetry).isTrue();
        assertThat(retryTracker.getRetryCount("test-topic", 0, 100L)).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 메시지의 재시도 카운트는 0")
    void shouldReturnZeroForNonExistentMessage() {
        // when
        int retryCount = retryTracker.getRetryCount("test-topic", 0, 999L);

        // then
        assertThat(retryCount).isEqualTo(0);
    }
}
