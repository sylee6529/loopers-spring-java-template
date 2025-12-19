package com.loopers.domain.payment;

import com.loopers.domain.order.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrder(Order order);

    Optional<Payment> findByTransactionKey(String transactionKey);

    boolean existsByOrder(Order order);

    List<Payment> findPendingPaymentsOlderThan(LocalDateTime dateTime);

    List<Payment> findByRequiresRetryTrue();
}
