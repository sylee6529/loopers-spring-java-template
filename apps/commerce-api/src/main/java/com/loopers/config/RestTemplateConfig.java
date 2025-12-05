package com.loopers.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate pgRestTemplate(RestTemplateBuilder builder) {
        // PG 전용 RestTemplate - 더 짧은 타임아웃 설정
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))  // PG 연결 타임아웃: 2초
                .setReadTimeout(Duration.ofSeconds(3))     // PG 읽기 타임아웃: 3초 (Resilience4j TimeLimiter가 2초로 제한)
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .build();
    }
}
