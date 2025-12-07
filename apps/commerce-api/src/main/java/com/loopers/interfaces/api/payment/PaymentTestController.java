package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.gateway.PgGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments/test")
@RequiredArgsConstructor
public class PaymentTestController {

    private final PgGateway pgGateway;

    @PostMapping("/request")
    public PgGateway.PgPaymentResult requestPaymentForTest(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PaymentTestRequest request
    ) throws Exception {
        log.info("[TEST] PG 결제 요청 - userId: {}, orderId: {}", userId, request.orderId());

        PgGateway.PgPaymentCommand command = new PgGateway.PgPaymentCommand(
                request.orderId(),
                CardType.valueOf(request.cardType()),
                request.cardNo(),
                request.amount(),
                request.callbackUrl()
        );

        CompletableFuture<PgGateway.PgPaymentResult> future = pgGateway.requestPayment(userId, command);
        return future.get(3, TimeUnit.SECONDS);
    }

    @GetMapping("/status/{transactionKey}")
    public PgGateway.PgPaymentDetail getPaymentStatusForTest(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable String transactionKey
    ) {
        log.info("[TEST] PG 결제 상태 조회 - userId: {}, transactionKey: {}", userId, transactionKey);
        return pgGateway.getPaymentStatus(userId, transactionKey);
    }

    public record PaymentTestRequest(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {}
}
