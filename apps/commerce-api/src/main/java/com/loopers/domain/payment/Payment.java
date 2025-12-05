package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
    indexes = {
        @Index(name = "idx_order_id", columnList = "orderId"),
        @Index(name = "idx_transaction_key", columnList = "transactionKey"),
        @Index(name = "idx_status_created_at", columnList = "status,createdAt")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    /**
     * 주문 ID (멱등성 보장을 위한 유니크 키)
     */
    @Column(nullable = false, unique = true, length = 100)
    private String orderId;

    /**
     * PG 트랜잭션 키
     * - PG가 발급한 고유 식별자
     * - 콜백 수신 시 매칭용
     */
    @Column(length = 100)
    private String transactionKey;

    /**
     * 결제 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * 카드 타입
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardType cardType;

    /**
     * 카드 번호 (마스킹)
     */
    @Column(nullable = false, length = 50)
    private String cardNo;

    /**
     * 결제 금액
     */
    @Column(nullable = false)
    private Long amount;

    /**
     * 실패/한도초과 사유
     */
    @Column(length = 500)
    private String reason;

    /**
     * PG 콜백 URL
     */
    @Column(length = 500)
    private String callbackUrl;

    /**
     * PG 장애 시 재시도 필요 여부
     */
    @Column(nullable = false)
    private boolean requiresRetry = false;

    /**
     * 최종 상태 확인 시각
     */
    private LocalDateTime lastCheckedAt;

    // ========== 팩토리 메서드 ==========

    public static Payment create(
            String orderId,
            CardType cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.cardType = cardType;
        payment.cardNo = maskCardNo(cardNo);
        payment.amount = amount;
        payment.callbackUrl = callbackUrl;
        payment.status = PaymentStatus.PENDING;
        payment.requiresRetry = false;
        return payment;
    }

    // ========== 비즈니스 로직 ==========

    /**
     * PG 트랜잭션 키 저장
     */
    public void assignTransactionKey(String transactionKey) {
        if (this.transactionKey != null) {
            throw new IllegalStateException("이미 transactionKey가 할당되었습니다.");
        }
        this.transactionKey = transactionKey;
    }

    /**
     * 결제 성공 처리
     */
    public void markAsSuccess() {
        if (this.status == PaymentStatus.SUCCESS) {
            return; // 멱등성: 이미 성공이면 무시
        }
        this.status = PaymentStatus.SUCCESS;
        this.requiresRetry = false;
        this.lastCheckedAt = LocalDateTime.now();
    }

    /**
     * 결제 실패 처리
     */
    public void markAsFailed(String reason) {
        if (this.status == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("이미 성공한 결제는 실패로 변경할 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
        this.requiresRetry = false;
        this.lastCheckedAt = LocalDateTime.now();
    }

    /**
     * PG 장애로 재시도 필요 표시
     */
    public void markAsRequiresRetry() {
        this.requiresRetry = true;
        this.lastCheckedAt = LocalDateTime.now();
    }

    /**
     * 상태 업데이트 (콜백/폴링)
     */
    public void updateFromPg(PaymentStatus pgStatus, String reason) {
        this.lastCheckedAt = LocalDateTime.now();

        switch (pgStatus) {
            case SUCCESS -> markAsSuccess();
            case FAILED -> markAsFailed(reason);
            case PENDING -> {
                // PENDING 상태 유지
                this.status = PaymentStatus.PENDING;
            }
        }
    }

    // ========== 조회 메서드 ==========

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    public boolean hasTransactionKey() {
        return transactionKey != null && !transactionKey.isEmpty();
    }

    public boolean isOlderThan(LocalDateTime dateTime) {
        return getCreatedAt().isBefore(dateTime.atZone(getCreatedAt().getZone()));
    }

    // ========== 헬퍼 메서드 ==========

    private static String maskCardNo(String cardNo) {
        if (cardNo == null || cardNo.length() < 4) {
            return "****-****-****-****";
        }
        // 마지막 4자리만 보여주기
        return "****-****-****-" + cardNo.substring(cardNo.length() - 4);
    }
}
