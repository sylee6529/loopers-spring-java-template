package com.loopers.application.batch.dto;

/**
 * Batch 처리용 ProductMetrics DTO
 * - JdbcItemReader가 읽어온 데이터를 담는 객체
 */
public record ProductMetricsDto(
    Long productId,
    Long likeCount,
    Long viewCount,
    Long salesCount,
    Long salesAmount
) {
}
