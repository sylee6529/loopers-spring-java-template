package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.gateway.PgGateway;
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
public class PgPaymentClient implements PgGateway {

    private final RestTemplate pgRestTemplate;

    @Value("${payment.pg.base-url:http://localhost:8082}")
    private String pgBaseUrl;

    private static final String PG_INSTANCE = "pgSimulator";

    public PgPaymentClient(RestTemplate pgRestTemplate) {
        this.pgRestTemplate = pgRestTemplate;
    }

    @Override
    @CircuitBreaker(name = PG_INSTANCE, fallbackMethod = "requestPaymentFallback")
    @Retry(name = PG_INSTANCE)
    @TimeLimiter(name = PG_INSTANCE)
    public CompletableFuture<PgPaymentResult> requestPayment(String userId, PgPaymentCommand command) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[PG] 결제 요청 시작 - orderId: {}, amount: {}", command.orderId(), command.amount());

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-USER-ID", userId);
            headers.set("Content-Type", "application/json");

            PgPaymentRequest request = new PgPaymentRequest(
                    command.orderId(),
                    toPgCardType(command.cardType()),
                    command.cardNo(),
                    command.amount(),
                    command.callbackUrl()
            );

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
                    return new PgPaymentResult(
                            paymentResponse.transactionKey(),
                            toPgTransactionStatus(paymentResponse.status()),
                            paymentResponse.reason()
                    );
                } else {
                    throw new PgCommunicationException("PG 응답이 비어있습니다.");
                }
            } catch (RestClientException e) {
                log.error("[PG] 결제 요청 실패 - orderId: {}", command.orderId(), e);
                throw new PgCommunicationException("PG 통신 실패", e);
            }
        });
    }

    @Override
    @CircuitBreaker(name = PG_INSTANCE, fallbackMethod = "getPaymentStatusFallback")
    @Retry(name = PG_INSTANCE)
    public PgPaymentDetail getPaymentStatus(String userId, String transactionKey) {
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
                return new PgPaymentDetail(
                        detailResponse.transactionKey(),
                        detailResponse.orderId(),
                        toCardType(detailResponse.cardType()),
                        detailResponse.cardNo(),
                        detailResponse.amount(),
                        toPgTransactionStatus(detailResponse.status()),
                        detailResponse.reason()
                );
            } else {
                throw new PgCommunicationException("PG 응답이 비어있습니다.");
            }
        } catch (RestClientException e) {
            log.error("[PG] 결제 상태 조회 실패 - transactionKey: {}", transactionKey, e);
            throw new PgCommunicationException("PG 통신 실패", e);
        }
    }

    private CompletableFuture<PgPaymentResult> requestPaymentFallback(
            String userId,
            PgPaymentCommand command,
            Exception ex
    ) {
        log.error("[PG Fallback] 결제 요청 실패 - orderId: {}, error: {}",
                command.orderId(), ex.getMessage());

        PgPaymentResult fallbackResponse = new PgPaymentResult(
                null,
                PgTransactionStatus.PENDING,
                "PG 시스템 일시 장애 - 나중에 재시도가 필요합니다."
        );

        return CompletableFuture.completedFuture(fallbackResponse);
    }

    private PgPaymentDetail getPaymentStatusFallback(
            String userId,
            String transactionKey,
            Exception ex
    ) {
        log.warn("[PG Fallback] 결제 상태 조회 실패, PENDING 상태로 반환 - transactionKey: {}, error: {}",
                transactionKey, ex.getMessage());

        return new PgPaymentDetail(
                transactionKey,
                "UNKNOWN",
                null,
                "****-****-****-****",
                0L,
                PgTransactionStatus.PENDING,
                "PG 시스템 일시 장애 - 결제 상태를 확인할 수 없습니다."
        );
    }

    private PgPaymentRequest.PgCardType toPgCardType(CardType cardType) {
        return PgPaymentRequest.PgCardType.valueOf(cardType.name());
    }

    private CardType toCardType(PgPaymentRequest.PgCardType cardType) {
        return CardType.valueOf(cardType.name());
    }

    private PgTransactionStatus toPgTransactionStatus(PgPaymentResponse.PgTransactionStatus status) {
        return PgTransactionStatus.valueOf(status.name());
    }

    public record PgApiResponse<T>(
            String result,
            String message,
            T data
    ) {}

    public static class PgCommunicationException extends RuntimeException {
        public PgCommunicationException(String message) {
            super(message);
        }

        public PgCommunicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
