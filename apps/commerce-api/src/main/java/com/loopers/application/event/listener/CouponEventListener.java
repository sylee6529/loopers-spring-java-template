package com.loopers.application.event.listener;

import com.loopers.application.event.order.OrderPlacedEvent;
import com.loopers.domain.coupon.MemberCoupon;
import com.loopers.domain.coupon.repository.MemberCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.points.Point;
import com.loopers.domain.points.repository.PointRepository;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventListener {

    private final MemberCouponRepository memberCouponRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final PointRepository pointRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        if (!event.hasCoupon()) {
            log.debug("[CouponEventListener] 쿠폰이 없는 주문 - orderNo: {}", event.orderNo());
            return;
        }

        log.info("[CouponEventListener] 쿠폰 사용 처리 시작 - orderNo: {}, couponId: {}",
                 event.orderNo(), event.memberCouponId());

        try {
            MemberCoupon coupon = memberCouponRepository.findByIdForUpdate(event.memberCouponId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

            if (coupon.isUsed()) {
                log.debug("[CouponEventListener] 이미 사용 처리된 쿠폰 - orderNo: {}, couponId: {}",
                          event.orderNo(), event.memberCouponId());
                return;
            }

            coupon.validateOwnership(event.memberId());
            coupon.use();
            memberCouponRepository.save(coupon);

            log.info("[CouponEventListener] 쿠폰 사용 완료 - orderNo: {}", event.orderNo());

        } catch (Exception e) {
            log.error("[CouponEventListener] 쿠폰 사용 실패 - orderNo: {}, couponId: {}",
                      event.orderNo(), event.memberCouponId(), e);

            // 보상 트랜잭션: 주문 취소 + 재고 복구
            compensateOrder(event);

            // 예외를 다시 던지지 않음 (이미 보상 완료)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void compensateOrder(OrderPlacedEvent event) {
        log.info("[CouponEventListener] 보상 트랜잭션 시작 - orderNo: {}", event.orderNo());

        try {
            Order order = orderRepository.findByOrderNo(event.orderNo())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

            // 1. 주문 취소
            order.cancel();
            orderRepository.save(order);

            // 2. 재고 복구
            order.getItems().forEach(item ->
                productRepository.increaseStock(item.getProductId(), item.getQuantity())
            );

            // 3. 포인트 복구 (결제가 생성되었고 포인트를 사용한 경우)
            paymentRepository.findByOrder(order).ifPresent(payment -> {
                if (payment.getPointUsed() > 0) {
                    log.info("[CouponEventListener] 포인트 복구 시작 - orderNo: {}, pointUsed: {}",
                             event.orderNo(), payment.getPointUsed());

                    Point memberPoint = pointRepository.findByMemberIdForUpdate(order.getMemberId())
                            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다."));

                    memberPoint.addAmount(BigDecimal.valueOf(payment.getPointUsed()));
                    pointRepository.save(memberPoint);

                    log.info("[CouponEventListener] 포인트 복구 완료 - orderNo: {}, refundedPoints: {}",
                             event.orderNo(), payment.getPointUsed());
                }
            });

            log.info("[CouponEventListener] 보상 트랜잭션 완료 - orderNo: {} 취소됨", event.orderNo());

        } catch (Exception e) {
            log.error("[CouponEventListener] 보상 트랜잭션 실패 - orderNo: {}", event.orderNo(), e);
            // TODO: 알림 전송 또는 수동 처리 필요
        }
    }
}
