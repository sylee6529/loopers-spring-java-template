package com.loopers.infrastructure.payment.pg;

public record PgPaymentDetailResponse(
        String transactionKey,
        String orderId,
        PgPaymentRequest.PgCardType cardType,
        String cardNo,
        Long amount,
        PgPaymentResponse.PgTransactionStatus status,
        String reason
) {
    public boolean isPending() {
        return status == PgPaymentResponse.PgTransactionStatus.PENDING;
    }

    public boolean isSuccess() {
        return status == PgPaymentResponse.PgTransactionStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PgPaymentResponse.PgTransactionStatus.FAILED;
    }
}
