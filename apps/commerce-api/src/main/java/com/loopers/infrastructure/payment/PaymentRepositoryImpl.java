package com.loopers.infrastructure.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public Payment save(Payment payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByOrder(Order order) {
        return jpaRepository.findByOrder(order);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return jpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public boolean existsByOrder(Order order) {
        return jpaRepository.existsByOrder(order);
    }

    @Override
    public List<Payment> findPendingPaymentsOlderThan(LocalDateTime dateTime) {
        ZonedDateTime zonedDateTime = dateTime.atZone(ZoneId.systemDefault());
        return jpaRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, zonedDateTime);
    }

    @Override
    public List<Payment> findByRequiresRetryTrue() {
        return jpaRepository.findByRequiresRetryTrue();
    }
}