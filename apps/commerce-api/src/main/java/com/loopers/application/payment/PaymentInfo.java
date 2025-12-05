package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

import java.time.ZonedDateTime;

/**
 * Payment 정보 전달 객체
 */
public record PaymentInfo(
        Long id,
        String orderId,
        String transactionKey,
        PaymentStatus status,
        CardType cardType,
        String cardNo,
        Long amount,
        String reason,
        boolean requiresRetry,
        ZonedDateTime createdAt
) {
    public static PaymentInfo from(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionKey(),
                payment.getStatus(),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getAmount(),
                payment.getReason(),
                payment.isRequiresRetry(),
                payment.getCreatedAt()
        );
    }
}
