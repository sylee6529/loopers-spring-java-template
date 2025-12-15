package com.loopers.application.event.listener;

import com.loopers.application.event.order.OrderPlacedEvent;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.InMemoryMemberCouponRepository;
import com.loopers.domain.coupon.MemberCoupon;
import com.loopers.domain.order.InMemoryOrderRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.InMemoryPaymentRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.points.InMemoryPointRepository;
import com.loopers.domain.points.Point;
import com.loopers.domain.product.InMemoryProductRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Stock;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("2단계: 쿠폰 이벤트 리스너 테스트")
class CouponEventListenerTest {

    private CouponEventListener couponEventListener;
    private InMemoryMemberCouponRepository memberCouponRepository;
    private InMemoryOrderRepository orderRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryPaymentRepository paymentRepository;
    private InMemoryPointRepository pointRepository;

    private Order testOrder;
    private MemberCoupon testCoupon;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        memberCouponRepository = new InMemoryMemberCouponRepository();
        orderRepository = new InMemoryOrderRepository();
        productRepository = new InMemoryProductRepository();
        paymentRepository = new InMemoryPaymentRepository();
        pointRepository = new InMemoryPointRepository();

        couponEventListener = new CouponEventListener(
            memberCouponRepository,
            orderRepository,
            productRepository,
            paymentRepository,
            pointRepository
        );

        // 테스트 상품 생성
        testProduct = new Product(1L, "테스트상품", null, Money.of(10000), Stock.of(100));
        productRepository.save(testProduct);

        // 테스트 주문 생성 (상품 포함)
        OrderItem orderItem = new OrderItem(testProduct.getId(), 1, testProduct.getPrice());
        testOrder = Order.create(1L, List.of(orderItem), Money.of(9000));  // 할인 적용된 가격
        orderRepository.save(testOrder);

        // 테스트 쿠폰 생성
        Coupon coupon = Coupon.createFixedCoupon("테스트쿠폰", BigDecimal.valueOf(1000));
        testCoupon = MemberCoupon.issue(1L, coupon);
        memberCouponRepository.save(testCoupon);
    }

    @Test
    @DisplayName("주문 생성 후 쿠폰이 사용된다")
    void 주문_생성_후_쿠폰_사용() {
        // given
        OrderPlacedEvent event = new OrderPlacedEvent(
            testOrder.getOrderNo(),
            testOrder.getMemberId(),
            testCoupon.getId(),
            testOrder.getTotalPrice(),
            LocalDateTime.now()
        );

        assertThat(testCoupon.isUsable()).isTrue();

        // when
        couponEventListener.handleOrderPlaced(event);

        // then
        MemberCoupon usedCoupon = memberCouponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(usedCoupon.isUsable()).isFalse();
        assertThat(usedCoupon.isUsed()).isTrue();
    }

    @Test
    @DisplayName("쿠폰이 없으면 리스너가 스킵한다")
    void 쿠폰_없으면_스킵() {
        // given
        OrderPlacedEvent event = new OrderPlacedEvent(
            testOrder.getOrderNo(),
            testOrder.getMemberId(),
            null,  // 쿠폰 없음
            testOrder.getTotalPrice(),
            LocalDateTime.now()
        );

        // when
        couponEventListener.handleOrderPlaced(event);

        // then - 쿠폰 상태 변화 없음
        MemberCoupon coupon = memberCouponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(coupon.isUsable()).isTrue();
        assertThat(coupon.isUsed()).isFalse();
    }

    @Test
    @DisplayName("이미 사용된 쿠폰인 경우 추가 처리 없이 종료된다")
    void 이미_사용된_쿠폰_사용시_보상_트랜잭션() {
        // given
        testCoupon.use();  // 쿠폰 미리 사용
        memberCouponRepository.save(testCoupon);

        int initialStock = productRepository.findById(testProduct.getId()).orElseThrow().getStock().getQuantity();

        OrderPlacedEvent event = new OrderPlacedEvent(
            testOrder.getOrderNo(),
            testOrder.getMemberId(),
            testCoupon.getId(),
            testOrder.getTotalPrice(),
            LocalDateTime.now()
        );

        // when
        couponEventListener.handleOrderPlaced(event);

        // then - 보상 트랜잭션 없이 상태 유지
        Order cancelledOrder = orderRepository.findByOrderNo(testOrder.getOrderNo()).orElseThrow();
        assertThat(cancelledOrder.getStatus()).isEqualTo(com.loopers.domain.order.OrderStatus.PENDING_PAYMENT);

        // 재고/쿠폰 상태 변화 없음
        Product product = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(product.getStock().getQuantity()).isEqualTo(initialStock);
        MemberCoupon coupon = memberCouponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(coupon.isUsed()).isTrue();
    }

    @Test
    @DisplayName("다른 회원의 쿠폰 사용 시 주문 취소")
    void 다른_회원_쿠폰_사용시_보상() {
        // given
        MemberCoupon otherMemberCoupon = MemberCoupon.issue(999L, testCoupon.getCoupon());
        memberCouponRepository.save(otherMemberCoupon);

        OrderPlacedEvent event = new OrderPlacedEvent(
            testOrder.getOrderNo(),
            testOrder.getMemberId(),  // memberId = 1L
            otherMemberCoupon.getId(),  // 다른 회원(999L)의 쿠폰
            testOrder.getTotalPrice(),
            LocalDateTime.now()
        );

        // when
        couponEventListener.handleOrderPlaced(event);

        // then - 보상 트랜잭션으로 주문 취소
        Order order = orderRepository.findByOrderNo(testOrder.getOrderNo()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(com.loopers.domain.order.OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("쿠폰 사용 실패 시 포인트가 복구된다")
    void 쿠폰_사용_실패시_포인트_복구() {
        // given
        Long memberId = testOrder.getMemberId();

        // 재고 차감 (주문 생성 시 차감된 상태 시뮬레이션)
        productRepository.decreaseStock(testProduct.getId(), 1);

        // 회원 포인트 생성 및 차감 (초기: 10000원 -> 3000원 사용 후 7000원)
        Point memberPoint = Point.create(memberId, BigDecimal.valueOf(10000));
        memberPoint.pay(BigDecimal.valueOf(3000));  // 결제 시 포인트 차감 시뮬레이션
        pointRepository.save(memberPoint);

        // 결제 생성 (포인트 3000원 사용)
        Payment payment = Payment.create(
            testOrder,
            CardType.SAMSUNG,
            "1234-5678-****-****",
            6000L,  // PG 금액
            3000L,  // 포인트 사용
            "http://callback.url"
        );
        paymentRepository.save(payment);

        // 다른 회원의 쿠폰 (쿠폰 사용 실패 유도)
        MemberCoupon otherMemberCoupon = MemberCoupon.issue(999L, testCoupon.getCoupon());
        memberCouponRepository.save(otherMemberCoupon);

        OrderPlacedEvent event = new OrderPlacedEvent(
            testOrder.getOrderNo(),
            memberId,
            otherMemberCoupon.getId(),
            testOrder.getTotalPrice(),
            LocalDateTime.now()
        );

        // when
        couponEventListener.handleOrderPlaced(event);

        // then
        // 1. 주문 취소됨
        Order order = orderRepository.findByOrderNo(testOrder.getOrderNo()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(com.loopers.domain.order.OrderStatus.CANCELLED);

        // 2. 재고 복구됨
        Product product = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(product.getStock().getQuantity()).isEqualTo(100);  // 원래 재고로 복구

        // 3. 포인트 복구됨 (10000 - 3000 + 3000 = 10000)
        Point restoredPoint = pointRepository.findByMemberId(memberId).orElseThrow();
        assertThat(restoredPoint.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    @DisplayName("쿠폰 사용 실패 시 결제가 없으면 포인트 복구하지 않는다")
    void 쿠폰_사용_실패시_결제_없으면_포인트_복구_안함() {
        // given
        Long memberId = testOrder.getMemberId();

        // 회원 포인트 생성
        Point memberPoint = Point.create(memberId, BigDecimal.valueOf(10000));
        pointRepository.save(memberPoint);

        // 결제 없음 (결제 전 단계에서 쿠폰 실패)

        // 다른 회원의 쿠폰
        MemberCoupon otherMemberCoupon = MemberCoupon.issue(999L, testCoupon.getCoupon());
        memberCouponRepository.save(otherMemberCoupon);

        OrderPlacedEvent event = new OrderPlacedEvent(
            testOrder.getOrderNo(),
            memberId,
            otherMemberCoupon.getId(),
            testOrder.getTotalPrice(),
            LocalDateTime.now()
        );

        // when
        couponEventListener.handleOrderPlaced(event);

        // then
        // 주문 취소됨
        Order order = orderRepository.findByOrderNo(testOrder.getOrderNo()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(com.loopers.domain.order.OrderStatus.CANCELLED);

        // 포인트는 그대로 (복구할 필요 없음)
        Point point = pointRepository.findByMemberId(memberId).orElseThrow();
        assertThat(point.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }
}
