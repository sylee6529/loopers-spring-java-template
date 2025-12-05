package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByTransactionKey(String transactionKey);

    boolean existsByOrderId(String orderId);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt < :dateTime")
    List<Payment> findByStatusAndCreatedAtBefore(
            @Param("status") PaymentStatus status,
            @Param("dateTime") ZonedDateTime dateTime
    );

    List<Payment> findByRequiresRetryTrue();
}
