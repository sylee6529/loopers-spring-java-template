package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments",
    indexes = {
        @Index(name = "idx_transaction_key", columnList = "transactionKey"),
        @Index(name = "idx_status_created_at", columnList = "status,createdAt")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(length = 100)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardType cardType;

    @Column(nullable = false, length = 50)
    private String cardNo;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private Long pointUsed;

    @Column(length = 500)
    private String reason;

    @Column(length = 500)
    private String callbackUrl;

    @Column(nullable = false)
    private boolean requiresRetry = false;

    private LocalDateTime lastCheckedAt;

    public static Payment create(
            Order order,
            CardType cardType,
            String cardNo,
            Long amount,
            Long pointUsed,
            String callbackUrl
    ) {
        Payment payment = new Payment();
        payment.order = order;
        payment.cardType = cardType;
        payment.cardNo = maskCardNo(cardNo);
        payment.amount = amount;
        payment.pointUsed = pointUsed;
        payment.callbackUrl = callbackUrl;
        payment.status = PaymentStatus.PENDING;
        payment.requiresRetry = false;
        return payment;
    }

    public void assignTransactionKey(String transactionKey) {
        if (this.transactionKey != null) {
            throw new IllegalStateException("이미 transactionKey가 할당되었습니다.");
        }
        this.transactionKey = transactionKey;
    }

    public void markAsSuccess() {
        if (this.status == PaymentStatus.SUCCESS) {
            return;
        }
        this.status = PaymentStatus.SUCCESS;
        this.requiresRetry = false;
        this.lastCheckedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        if (this.status == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("이미 성공한 결제는 실패로 변경할 수 없습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
        this.requiresRetry = false;
        this.lastCheckedAt = LocalDateTime.now();
    }

    public void markAsRequiresRetry() {
        this.requiresRetry = true;
        this.lastCheckedAt = LocalDateTime.now();
    }

    public void markAsCancelled(String reason) {
        if (this.status == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("이미 성공한 결제는 취소할 수 없습니다.");
        }
        this.status = PaymentStatus.CANCELLED;
        this.reason = reason;
        this.requiresRetry = false;
        this.lastCheckedAt = LocalDateTime.now();
    }

    public void updateFromPg(PaymentStatus pgStatus, String reason) {
        this.lastCheckedAt = LocalDateTime.now();

        switch (pgStatus) {
            case SUCCESS -> markAsSuccess();
            case FAILED -> markAsFailed(reason);
            case CANCELLED -> markAsCancelled(reason);
            case PENDING -> this.status = PaymentStatus.PENDING;
        }
    }

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

    public void refundPoints(java.util.function.LongConsumer refundAction) {
        if (pointUsed != null && pointUsed > 0) {
            refundAction.accept(pointUsed);
            this.pointUsed = 0L;
        }
    }

    public boolean isOlderThan(LocalDateTime dateTime) {
        return getCreatedAt().isBefore(dateTime.atZone(getCreatedAt().getZone()));
    }

    private static String maskCardNo(String cardNo) {
        if (cardNo == null || cardNo.length() < 4) {
            return "****-****-****-****";
        }
        return "****-****-****-" + cardNo.substring(cardNo.length() - 4);
    }
}
