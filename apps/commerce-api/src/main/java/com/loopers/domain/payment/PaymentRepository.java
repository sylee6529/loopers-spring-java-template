package com.loopers.domain.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    /**
     * orderId로 조회 (멱등성 체크용)
     */
    Optional<Payment> findByOrderId(String orderId);

    /**
     * transactionKey로 조회 (콜백 처리용)
     */
    Optional<Payment> findByTransactionKey(String transactionKey);

    /**
     * orderId 존재 여부 (중복 체크용)
     */
    boolean existsByOrderId(String orderId);

    /**
     * 특정 시간 이전에 생성된 PENDING 상태 결제 조회 (폴링용)
     */
    List<Payment> findPendingPaymentsOlderThan(LocalDateTime dateTime);

    /**
     * 재시도 필요한 결제 조회 (PG 장애 복구 후)
     */
    List<Payment> findByRequiresRetryTrue();
}
