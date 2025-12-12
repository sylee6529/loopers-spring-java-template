package com.loopers.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * @Async 메서드를 위한 ThreadPool 및 예외 처리 구성
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 비동기 작업용 ThreadPool 설정
     * - corePoolSize: 기본 스레드 수 (5개)
     * - maxPoolSize: 최대 스레드 수 (10개)
     * - queueCapacity: 대기 큐 크기 (100개)
     * - threadNamePrefix: 스레드 이름 접두사
     */
    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 비동기 메서드에서 발생한 예외를 처리
     * void 반환 메서드에서 발생한 예외만 처리됨 (CompletableFuture는 별도 처리)
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("[Async Exception] method: {}, params: {}", method.getName(), params, ex);
            // TODO: 실패한 이벤트에 대한 재시도 또는 Dead Letter Queue 처리 고려
        };
    }

    /**
     * taskExecutor() 메서드를 별도로 노출하여 테스트에서 접근 가능하도록 함
     */
    public ThreadPoolTaskExecutor taskExecutor() {
        return (ThreadPoolTaskExecutor) getAsyncExecutor();
    }
}
