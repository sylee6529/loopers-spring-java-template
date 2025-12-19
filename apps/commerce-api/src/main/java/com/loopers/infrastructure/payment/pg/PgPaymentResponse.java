package com.loopers.infrastructure.payment.pg;

public record PgPaymentResponse(
        String transactionKey,
        PgTransactionStatus status,
        String reason
) {
    public enum PgTransactionStatus {
        PENDING, SUCCESS, FAILED
    }

    public boolean isPending() {
        return status == PgTransactionStatus.PENDING;
    }

    public boolean isSuccess() {
        return status == PgTransactionStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == PgTransactionStatus.FAILED;
    }
}
