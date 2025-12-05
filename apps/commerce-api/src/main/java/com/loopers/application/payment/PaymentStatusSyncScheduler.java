package com.loopers.application.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStatusSyncScheduler {

    private final PaymentFacade paymentFacade;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void syncPendingPayments() {
        log.info("[PaymentSync] PENDING 결제 동기화 시작");

        try {
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
                        log.warn("[PaymentSync] transactionKey 없음, 스킵 - orderNo: {}", paymentInfo.orderNo());
                        continue;
                    }

                    PaymentInfo syncedPayment = paymentFacade.syncPaymentStatus(
                            "SCHEDULER",
                            paymentInfo.transactionKey()
                    );

                    log.info("[PaymentSync] 동기화 완료 - orderNo: {}, status: {} -> {}",
                            paymentInfo.orderNo(),
                            paymentInfo.status(),
                            syncedPayment.status());

                    successCount++;

                } catch (Exception e) {
                    log.error("[PaymentSync] 동기화 실패 - orderNo: {}", paymentInfo.orderNo(), e);
                    failureCount++;
                }
            }

            log.info("[PaymentSync] PENDING 결제 동기화 완료 - 성공: {}, 실패: {}", successCount, failureCount);

        } catch (Exception e) {
            log.error("[PaymentSync] PENDING 결제 동기화 오류", e);
        }
    }

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
                log.warn("[PaymentRetry] 수동 처리 필요 - orderNo: {}, 관리자 확인 필요", paymentInfo.orderNo());
                // TODO: 관리자 알림, 수동 재시도 API 제공 등
            }

        } catch (Exception e) {
            log.error("[PaymentRetry] 재시도 결제 처리 오류", e);
        }
    }
}
