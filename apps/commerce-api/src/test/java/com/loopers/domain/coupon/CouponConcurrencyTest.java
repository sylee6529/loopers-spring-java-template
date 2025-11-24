package com.loopers.domain.coupon;

import com.loopers.application.members.MemberFacade;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderLineCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.coupon.repository.CouponRepository;
import com.loopers.domain.coupon.repository.MemberCouponRepository;
import com.loopers.domain.members.enums.Gender;
import com.loopers.domain.points.Point;
import com.loopers.domain.points.repository.PointRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.domain.product.vo.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("쿠폰 동시성 테스트")
class CouponConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private MemberFacade memberFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한번만 사용되어야 한다")
    void shouldUseCouponOnlyOnce_whenConcurrentOrdersWithSameCoupon() throws InterruptedException {
        // given
        String memberId = "testUser";
        memberFacade.registerMember(memberId, memberId + "@test.com", "password", "1990-01-01", Gender.MALE);

        Point existingPoint = pointRepository.findByMemberId(memberId).orElseThrow();
        existingPoint.addAmount(BigDecimal.valueOf(100000));
        pointRepository.save(existingPoint);

        Brand brand = brandRepository.save(new Brand("TestBrand", "Test Brand Description"));
        Product product = productRepository.save(new Product(
                brand.getId(),
                "Test Product",
                "Test Description",
                Money.of(BigDecimal.valueOf(10000)),
                Stock.of(100)
        ));

        Coupon coupon = couponRepository.save(Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000)));
        MemberCoupon memberCoupon = memberCouponRepository.save(MemberCoupon.issue(memberId, coupon));
        Long couponId = memberCoupon.getId();

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 10개의 스레드가 동시에 같은 쿠폰으로 주문 시도
        try (ExecutorService executorService = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        OrderCommand command = OrderCommand.of(
                                memberId,
                                List.of(OrderLineCommand.of(product.getId(), 1)),
                                couponId
                        );
                        orderFacade.placeOrder(command);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        // then: 단 1건만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        // 쿠폰은 사용됨 상태여야 함
        MemberCoupon usedCoupon = memberCouponRepository.findById(couponId).orElseThrow();
        assertThat(usedCoupon.isUsed()).isTrue();
    }

    @Test
    @DisplayName("서로 다른 쿠폰으로 여러 주문이 동시에 발생해도, 각 쿠폰이 정상적으로 사용되어야 한다")
    void shouldUseEachCouponCorrectly_whenConcurrentOrdersWithDifferentCoupons() throws InterruptedException {
        // given
        Brand brand = brandRepository.save(new Brand("TestBrand", "Test Brand Description"));
        Product product = productRepository.save(new Product(
                brand.getId(),
                "Test Product",
                "Test Description",
                Money.of(BigDecimal.valueOf(10000)),
                Stock.of(100)
        ));

        Coupon coupon = couponRepository.save(Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000)));

        int threadCount = 10;
        Long[] couponIds = new Long[threadCount];

        // 10명의 회원 생성 및 각각 쿠폰 발급
        for (int i = 0; i < threadCount; i++) {
            String memberId = "member" + i;
            memberFacade.registerMember(memberId, memberId + "@test.com", "password", "1990-01-01", Gender.MALE);

            Point existingPoint = pointRepository.findByMemberId(memberId).orElseThrow();
            existingPoint.addAmount(BigDecimal.valueOf(50000));
            pointRepository.save(existingPoint);

            MemberCoupon memberCoupon = memberCouponRepository.save(MemberCoupon.issue(memberId, coupon));
            couponIds[i] = memberCoupon.getId();
        }

        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 10명이 각자의 쿠폰으로 동시에 주문
        try (ExecutorService executorService = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                final String memberId = "member" + i;
                final Long couponId = couponIds[i];
                executorService.submit(() -> {
                    try {
                        OrderCommand command = OrderCommand.of(
                                memberId,
                                List.of(OrderLineCommand.of(product.getId(), 1)),
                                couponId
                        );
                        orderFacade.placeOrder(command);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        // then: 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 모든 쿠폰이 사용됨 상태여야 함
        for (Long couponId : couponIds) {
            MemberCoupon usedCoupon = memberCouponRepository.findById(couponId).orElseThrow();
            assertThat(usedCoupon.isUsed()).isTrue();
        }
    }

    @Test
    @DisplayName("동일한 유저가 여러 주문을 동시에 쿠폰과 함께 수행해도, 쿠폰별로 정상 처리되어야 한다")
    void shouldHandleConcurrentOrdersWithMultipleCoupons_forSameUser() throws InterruptedException {
        // given
        String memberId = "testUser";
        memberFacade.registerMember(memberId, memberId + "@test.com", "password", "1990-01-01", Gender.MALE);

        Point existingPoint = pointRepository.findByMemberId(memberId).orElseThrow();
        existingPoint.addAmount(BigDecimal.valueOf(100000));
        pointRepository.save(existingPoint);

        Brand brand = brandRepository.save(new Brand("TestBrand", "Test Brand Description"));
        Product product = productRepository.save(new Product(
                brand.getId(),
                "Test Product",
                "Test Description",
                Money.of(BigDecimal.valueOf(10000)),
                Stock.of(100)
        ));

        Coupon coupon = couponRepository.save(Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000)));

        // 동일 유저에게 5개의 쿠폰 발급
        int couponCount = 5;
        Long[] couponIds = new Long[couponCount];
        for (int i = 0; i < couponCount; i++) {
            MemberCoupon memberCoupon = memberCouponRepository.save(MemberCoupon.issue(memberId, coupon));
            couponIds[i] = memberCoupon.getId();
        }

        CountDownLatch latch = new CountDownLatch(couponCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when: 동일 유저가 5개의 쿠폰을 동시에 사용
        try (ExecutorService executorService = Executors.newFixedThreadPool(couponCount)) {
            for (int i = 0; i < couponCount; i++) {
                final Long couponId = couponIds[i];
                executorService.submit(() -> {
                    try {
                        OrderCommand command = OrderCommand.of(
                                memberId,
                                List.of(OrderLineCommand.of(product.getId(), 1)),
                                couponId
                        );
                        orderFacade.placeOrder(command);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
        }

        // then: 모두 성공해야 함 (각기 다른 쿠폰이므로)
        assertThat(successCount.get()).isEqualTo(couponCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 모든 쿠폰이 사용됨 상태여야 함
        for (Long couponId : couponIds) {
            MemberCoupon usedCoupon = memberCouponRepository.findById(couponId).orElseThrow();
            assertThat(usedCoupon.isUsed()).isTrue();
        }

        // 재고도 정확히 차감되어야 함
        Product result = productRepository.findById(product.getId()).orElseThrow();
        assertThat(result.getStock().getQuantity()).isEqualTo(95); // 100 - 5
    }
}
