package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Payment 상태 동기화 스케줄러
 *
 * 콜백이 오지 않은 PENDING 상태의 결제를 주기적으로 확인하여 상태 동기화
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusSyncScheduler {

    private final PaymentFacade paymentFacade;

    /**
     * PENDING 상태 결제 동기화
     *
     * - 10분 이상 PENDING 상태인 결제들을 조회
     * - PG 상태 조회 API로 최종 상태 확인
     * - Payment 및 Order 상태 업데이트
     *
     * 실행 주기: 1분마다
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void syncPendingPayments() {
        log.info("[PaymentSync] PENDING 결제 동기화 시작");

        try {
            // 10분 이상 PENDING 상태인 결제 조회
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(10);
            List<PaymentInfo> pendingPayments = paymentFacade.getPendingPaymentsOlderThan(cutoffTime);

            if (pendingPayments.isEmpty()) {
                log.debug("[PaymentSync] 동기화할 PENDING 결제 없음");
                return;
            }

            log.info("[PaymentSync] PENDING 결제 {}건 발견, 동기화 시작", pendingPayments.size());

            int successCount = 0;
            int failureCount = 0;

            for (PaymentInfo paymentInfo : pendingPayments) {
                try {
                    if (paymentInfo.transactionKey() == null || paymentInfo.transactionKey().isEmpty()) {
                        log.warn("[PaymentSync] transactionKey 없음, 스킵 - orderId: {}", paymentInfo.orderId());
                        continue;
                    }

                    // PG 상태 조회 및 동기화
                    PaymentInfo syncedPayment = paymentFacade.syncPaymentStatus(
                            "SCHEDULER",
                            paymentInfo.transactionKey()
                    );

                    log.info("[PaymentSync] 동기화 완료 - orderId: {}, status: {} -> {}",
                            paymentInfo.orderId(),
                            paymentInfo.status(),
                            syncedPayment.status());

                    successCount++;

                } catch (Exception e) {
                    log.error("[PaymentSync] 동기화 실패 - orderId: {}", paymentInfo.orderId(), e);
                    failureCount++;
                }
            }

            log.info("[PaymentSync] PENDING 결제 동기화 완료 - 성공: {}, 실패: {}", successCount, failureCount);

        } catch (Exception e) {
            log.error("[PaymentSync] PENDING 결제 동기화 오류", e);
        }
    }

    /**
     * 재시도 필요 결제 처리
     *
     * PG 장애로 인해 재시도가 필요한 결제들을 수동으로 처리
     *
     * 실행 주기: 5분마다
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 120000)
    public void processRetryPayments() {
        log.info("[PaymentRetry] 재시도 필요 결제 처리 시작");

        try {
            List<PaymentInfo> retryPayments = paymentFacade.getPaymentsRequiringRetry();

            if (retryPayments.isEmpty()) {
                log.debug("[PaymentRetry] 재시도 필요 결제 없음");
                return;
            }

            log.info("[PaymentRetry] 재시도 필요 결제 {}건 발견", retryPayments.size());

            for (PaymentInfo paymentInfo : retryPayments) {
                log.warn("[PaymentRetry] 수동 처리 필요 - orderId: {}, 관리자 확인 필요", paymentInfo.orderId());
                // TODO: 관리자 알림, 수동 재시도 API 제공 등
            }

        } catch (Exception e) {
            log.error("[PaymentRetry] 재시도 결제 처리 오류", e);
        }
    }
}
