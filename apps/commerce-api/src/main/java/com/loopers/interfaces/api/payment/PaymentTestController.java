package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.infrastructure.payment.pg.PgPaymentClient;
import com.loopers.infrastructure.payment.pg.PgPaymentDetailResponse;
import com.loopers.infrastructure.payment.pg.PgPaymentRequest;
import com.loopers.infrastructure.payment.pg.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * PG 연동 테스트용 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentTestController {

    private final PgPaymentClient pgPaymentClient;
    private final PaymentFacade paymentFacade;

    /**
     * PG 결제 요청 테스트
     */
    @PostMapping("/test/request")
    public CompletableFuture<PgPaymentResponse> testPaymentRequest(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PaymentRequestDto request
    ) {
        log.info("[TEST] PG 결제 요청 테스트 - userId: {}, orderId: {}", userId, request.orderId());

        PgPaymentRequest pgRequest = new PgPaymentRequest(
                request.orderId(),
                PgPaymentRequest.PgCardType.valueOf(request.cardType()),
                request.cardNo(),
                request.amount(),
                "http://localhost:8080/api/v1/payments/callback"
        );

        return pgPaymentClient.requestPayment(userId, pgRequest);
    }

    /**
     * PG 결제 상태 조회 테스트
     */
    @GetMapping("/test/status/{transactionKey}")
    public PgPaymentDetailResponse testPaymentStatus(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable String transactionKey
    ) {
        log.info("[TEST] PG 결제 상태 조회 테스트 - userId: {}, transactionKey: {}", userId, transactionKey);
        return pgPaymentClient.getPaymentStatus(userId, transactionKey);
    }

    /**
     * PG 콜백 엔드포인트
     * - PG Simulator가 결제 완료 시 호출
     * - Payment 상태 업데이트 및 Order 상태 변경
     */
    @PostMapping("/callback")
    public CallbackResponse handleCallback(
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "SYSTEM") String userId,
            @RequestBody Map<String, Object> callbackData
    ) {
        log.info("[Callback] PG 콜백 수신 - data: {}", callbackData);

        try {
            String transactionKey = (String) callbackData.get("transactionKey");
            String status = (String) callbackData.get("status");
            String reason = (String) callbackData.get("reason");

            // PaymentFacade를 통해 콜백 처리
            PaymentCommand.ProcessCallback command = new PaymentCommand.ProcessCallback(
                    transactionKey,
                    status,
                    reason
            );

            PaymentInfo paymentInfo = paymentFacade.processCallback(userId, command);

            log.info("[Callback] 결제 처리 완료 - orderId: {}, status: {}",
                    paymentInfo.orderId(), paymentInfo.status());

            return new CallbackResponse("SUCCESS", "콜백 처리 완료");

        } catch (Exception e) {
            log.error("[Callback] 콜백 처리 실패", e);
            return new CallbackResponse("FAILED", "콜백 처리 실패: " + e.getMessage());
        }
    }

    // ========== DTOs ==========

    public record PaymentRequestDto(
            String orderId,
            String cardType,
            String cardNo,
            Long amount
    ) {}

    public record CallbackResponse(
            String result,
            String message
    ) {}
}
