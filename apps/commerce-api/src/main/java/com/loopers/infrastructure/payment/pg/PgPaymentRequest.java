package com.loopers.infrastructure.payment.pg;

public record PgPaymentRequest(
        String orderId,
        PgCardType cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {
    public enum PgCardType {
        SAMSUNG, KB, HYUNDAI
    }
}
