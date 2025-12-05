package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.payment.CardType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Payment API 컨트롤러
 * - 결제 요청, 조회, 상태 동기화
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    /**
     * 결제 요청
     *
     * POST /api/v1/payments
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PaymentRequest request
    ) {
        log.info("[Payment API] 결제 요청 - userId: {}, orderId: {}", userId, request.orderId());

        PaymentCommand.RequestPayment command = new PaymentCommand.RequestPayment(
                request.orderId(),
                request.cardType(),
                request.cardNo(),
                request.amount(),
                request.callbackUrl()
        );

        PaymentInfo paymentInfo = paymentFacade.requestPayment(userId, command);

        return ResponseEntity.ok(PaymentResponse.from(paymentInfo));
    }

    /**
     * orderId로 결제 정보 조회
     *
     * GET /api/v1/payments/orders/{orderId}
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable String orderId
    ) {
        log.info("[Payment API] 결제 조회 - userId: {}, orderId: {}", userId, orderId);

        PaymentInfo paymentInfo = paymentFacade.getPaymentByOrderId(orderId);

        return ResponseEntity.ok(PaymentResponse.from(paymentInfo));
    }

    /**
     * transactionKey로 결제 상태 동기화
     *
     * POST /api/v1/payments/{transactionKey}/sync
     */
    @PostMapping("/{transactionKey}/sync")
    public ResponseEntity<PaymentResponse> syncPaymentStatus(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable String transactionKey
    ) {
        log.info("[Payment API] 상태 동기화 - userId: {}, transactionKey: {}", userId, transactionKey);

        PaymentInfo paymentInfo = paymentFacade.syncPaymentStatus(userId, transactionKey);

        return ResponseEntity.ok(PaymentResponse.from(paymentInfo));
    }

    // ========== DTOs ==========

    public record PaymentRequest(
            String orderId,
            CardType cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {}

    public record PaymentResponse(
            Long id,
            String orderId,
            String transactionKey,
            String status,
            String cardType,
            String cardNo,
            Long amount,
            String reason,
            boolean requiresRetry
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                    info.id(),
                    info.orderId(),
                    info.transactionKey(),
                    info.status().name(),
                    info.cardType().name(),
                    info.cardNo(),
                    info.amount(),
                    info.reason(),
                    info.requiresRetry()
            );
        }
    }
}
