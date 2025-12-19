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
                .setConnectTimeout(Duration.ofSeconds(1))   // PG 연결 타임아웃: 1초
                .setReadTimeout(Duration.ofMillis(1800))    // PG 읽기 타임아웃: TimeLimiter(2s)보다 짧게
                .requestFactory(SimpleClientHttpRequestFactory.class)
                .build();
    }
}
