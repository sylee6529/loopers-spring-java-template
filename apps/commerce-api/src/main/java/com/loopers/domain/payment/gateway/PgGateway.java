package com.loopers.domain.payment.gateway;

import com.loopers.domain.payment.CardType;

import java.util.concurrent.CompletableFuture;

public interface PgGateway {

    CompletableFuture<PgPaymentResult> requestPayment(
            String userId,
            PgPaymentCommand command
    );

    PgPaymentDetail getPaymentStatus(
            String userId,
            String transactionKey
    );

    record PgPaymentCommand(
            String orderId,
            CardType cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {}

    record PgPaymentResult(
            String transactionKey,
            PgTransactionStatus status,
            String message
    ) {}

    record PgPaymentDetail(
            String transactionKey,
            String orderId,
            CardType cardType,
            String cardNo,
            Long amount,
            PgTransactionStatus status,
            String reason
    ) {}

    enum PgTransactionStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
