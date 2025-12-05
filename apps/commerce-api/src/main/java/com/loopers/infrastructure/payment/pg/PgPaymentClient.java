package com.loopers.infrastructure.payment.pg;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class PgPaymentClient {

    private final RestTemplate pgRestTemplate;

    @Value("${payment.pg.base-url:http://localhost:8082}")
    private String pgBaseUrl;

    private static final String PG_INSTANCE = "pgSimulator";

    public PgPaymentClient(RestTemplate pgRestTemplate) {
        this.pgRestTemplate = pgRestTemplate;
    }

    /**
     * 결제 요청 API
     * - CircuitBreaker: PG 장애 시 빠른 실패
     * - Retry: 일시적 오류 시 재시도
     * - TimeLimiter: 2초 타임아웃
     */
    @CircuitBreaker(name = PG_INSTANCE, fallbackMethod = "requestPaymentFallback")
    @Retry(name = PG_INSTANCE)
    @TimeLimiter(name = PG_INSTANCE)
    public CompletableFuture<PgPaymentResponse> requestPayment(String userId, PgPaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[PG] 결제 요청 시작 - orderId: {}, amount: {}", request.orderId(), request.amount());

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-USER-ID", userId);
            headers.set("Content-Type", "application/json");

            HttpEntity<PgPaymentRequest> entity = new HttpEntity<>(request, headers);

            try {
                String url = pgBaseUrl + "/api/v1/payments";
                ResponseEntity<PgApiResponse<PgPaymentResponse>> response = pgRestTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        new org.springframework.core.ParameterizedTypeReference<>() {}
                );

                if (response.getBody() != null && response.getBody().data() != null) {
                    PgPaymentResponse paymentResponse = response.getBody().data();
                    log.info("[PG] 결제 요청 성공 - transactionKey: {}, status: {}",
                            paymentResponse.transactionKey(), paymentResponse.status());
                    return paymentResponse;
                } else {
                    throw new PgCommunicationException("PG 응답이 비어있습니다.");
                }
            } catch (RestClientException e) {
                log.error("[PG] 결제 요청 실패 - orderId: {}", request.orderId(), e);
                throw new PgCommunicationException("PG 통신 실패", e);
            }
        });
    }

    /**
     * 결제 상태 조회 API
     * - CircuitBreaker 적용
     * - Retry 적용
     */
    @CircuitBreaker(name = PG_INSTANCE, fallbackMethod = "getPaymentStatusFallback")
    @Retry(name = PG_INSTANCE)
    public PgPaymentDetailResponse getPaymentStatus(String userId, String transactionKey) {
        log.info("[PG] 결제 상태 조회 - transactionKey: {}", transactionKey);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", userId);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            String url = pgBaseUrl + "/api/v1/payments/" + transactionKey;
            ResponseEntity<PgApiResponse<PgPaymentDetailResponse>> response = pgRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new org.springframework.core.ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null && response.getBody().data() != null) {
                PgPaymentDetailResponse detailResponse = response.getBody().data();
                log.info("[PG] 결제 상태 조회 성공 - transactionKey: {}, status: {}",
                        transactionKey, detailResponse.status());
                return detailResponse;
            } else {
                throw new PgCommunicationException("PG 응답이 비어있습니다.");
            }
        } catch (RestClientException e) {
            log.error("[PG] 결제 상태 조회 실패 - transactionKey: {}", transactionKey, e);
            throw new PgCommunicationException("PG 통신 실패", e);
        }
    }

    // ========== Fallback Methods ==========

    /**
     * 결제 요청 Fallback
     * - Circuit Open 또는 재시도 실패 시 호출
     * - transactionKey를 null로 반환하여 재시도 필요함을 표시
     */
    private CompletableFuture<PgPaymentResponse> requestPaymentFallback(
            String userId,
            PgPaymentRequest request,
            Exception ex
    ) {
        log.error("[PG Fallback] 결제 요청 실패 - orderId: {}, error: {}",
                request.orderId(), ex.getMessage());

        // transactionKey를 null로 반환하여 PG 장애를 명확히 표시
        // Service 레이어에서 이를 감지하고 requiresRetry 플래그 설정
        PgPaymentResponse fallbackResponse = new PgPaymentResponse(
                null,  // transactionKey가 없음 = PG 호출 실패
                PgPaymentResponse.PgTransactionStatus.PENDING,
                "PG 시스템 일시 장애 - 나중에 재시도가 필요합니다."
        );

        return CompletableFuture.completedFuture(fallbackResponse);
    }

    /**
     * 결제 상태 조회 Fallback
     * - 조회 실패 시 PENDING 상태로 반환
     */
    private PgPaymentDetailResponse getPaymentStatusFallback(
            String userId,
            String transactionKey,
            Exception ex
    ) {
        log.warn("[PG Fallback] 결제 상태 조회 실패, PENDING 상태로 반환 - transactionKey: {}, error: {}",
                transactionKey, ex.getMessage());

        return new PgPaymentDetailResponse(
                transactionKey,
                "UNKNOWN",
                null,
                "****-****-****-****",
                0L,
                PgPaymentResponse.PgTransactionStatus.PENDING,
                "PG 시스템 일시 장애 - 결제 상태를 확인할 수 없습니다."
        );
    }

    // ========== Helper Classes ==========

    /**
     * PG API 응답 래퍼
     */
    public record PgApiResponse<T>(
            String result,
            String message,
            T data
    ) {}

    /**
     * PG 통신 예외
     */
    public static class PgCommunicationException extends RuntimeException {
        public PgCommunicationException(String message) {
            super(message);
        }

        public PgCommunicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
