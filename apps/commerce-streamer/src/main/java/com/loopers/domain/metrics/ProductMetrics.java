package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 상품별 집계 메트릭
 * - 좋아요 수, 조회 수, 판매량 등을 실시간 집계
 * - Kafka Consumer가 이벤트를 받아서 업데이트
 */
@Entity
@Table(name = "product_metrics", indexes = {
    @Index(name = "idx_last_updated", columnList = "last_updated"),
    @Index(name = "idx_like_count", columnList = "like_count"),
    @Index(name = "idx_sales_count", columnList = "sales_count")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    /**
     * 상품 ID (PK)
     */
    @Id
    @Column(name = "product_id")
    private Long productId;

    /**
     * 좋아요 수
     */
    @Column(name = "like_count", nullable = false)
    private long likeCount = 0;

    /**
     * 조회 수 (상세 페이지)
     */
    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    /**
     * 판매 수량
     */
    @Column(name = "sales_count", nullable = false)
    private long salesCount = 0;

    /**
     * 판매 금액
     */
    @Column(name = "sales_amount", nullable = false)
    private long salesAmount = 0;

    /**
     * 마지막 업데이트 시각
     */
    @Column(name = "last_updated", nullable = false)
    private ZonedDateTime lastUpdated;

    /**
     * 낙관적 락 (동시성 제어)
     */
    @Version
    private int version;

    /**
     * 상품 메트릭 초기 생성
     */
    public static ProductMetrics create(Long productId) {
        ProductMetrics metrics = new ProductMetrics();
        metrics.productId = productId;
        metrics.likeCount = 0;
        metrics.viewCount = 0;
        metrics.salesCount = 0;
        metrics.salesAmount = 0;
        metrics.lastUpdated = ZonedDateTime.now();
        return metrics;
    }

    /**
     * 좋아요 수 증가 (타임스탬프 체크)
     * @param eventOccurredAt 이벤트 발생 시각
     * @return 업데이트 성공 여부 (false = 오래된 이벤트로 무시됨)
     */
    public boolean incrementLikeCount(ZonedDateTime eventOccurredAt) {
        if (isEventOutdated(eventOccurredAt)) {
            return false;
        }
        this.likeCount++;
        this.lastUpdated = eventOccurredAt;
        return true;
    }

    /**
     * 좋아요 수 감소 (타임스탬프 체크)
     * @param eventOccurredAt 이벤트 발생 시각
     * @return 업데이트 성공 여부 (false = 오래된 이벤트로 무시됨)
     */
    public boolean decrementLikeCount(ZonedDateTime eventOccurredAt) {
        if (isEventOutdated(eventOccurredAt)) {
            return false;
        }
        this.likeCount = Math.max(0, this.likeCount - 1);
        this.lastUpdated = eventOccurredAt;
        return true;
    }

    /**
     * 조회 수 증가 (타임스탬프 체크)
     * @param eventOccurredAt 이벤트 발생 시각
     * @return 업데이트 성공 여부 (false = 오래된 이벤트로 무시됨)
     */
    public boolean incrementViewCount(ZonedDateTime eventOccurredAt) {
        if (isEventOutdated(eventOccurredAt)) {
            return false;
        }
        this.viewCount++;
        this.lastUpdated = eventOccurredAt;
        return true;
    }

    /**
     * 판매 데이터 추가 (타임스탬프 체크)
     * @param quantity 판매 수량
     * @param amount 판매 금액
     * @param eventOccurredAt 이벤트 발생 시각
     * @return 업데이트 성공 여부 (false = 오래된 이벤트로 무시됨)
     */
    public boolean addSales(int quantity, long amount, ZonedDateTime eventOccurredAt) {
        if (isEventOutdated(eventOccurredAt)) {
            return false;
        }
        this.salesCount += quantity;
        this.salesAmount += amount;
        this.lastUpdated = eventOccurredAt;
        return true;
    }

    /**
     * 이벤트가 현재 상태보다 오래된 것인지 체크
     * @param eventOccurredAt 이벤트 발생 시각
     * @return true = 오래된 이벤트 (무시해야 함), false = 최신 이벤트 (처리해야 함)
     */
    private boolean isEventOutdated(ZonedDateTime eventOccurredAt) {
        return this.lastUpdated != null && eventOccurredAt.isBefore(this.lastUpdated);
    }

    /**
     * 모든 메트릭 초기화 (테스트용)
     */
    public void reset() {
        this.likeCount = 0;
        this.viewCount = 0;
        this.salesCount = 0;
        this.salesAmount = 0;
        this.lastUpdated = ZonedDateTime.now();
    }
}
