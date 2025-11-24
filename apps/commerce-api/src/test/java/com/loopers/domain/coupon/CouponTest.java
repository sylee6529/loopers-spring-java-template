package com.loopers.domain.coupon;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.coupon.enums.DiscountType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    @DisplayName("정액 쿠폰 생성 시,")
    @Nested
    class CreateFixedCoupon {

        @DisplayName("정상적으로 생성된다")
        @Test
        void shouldCreateFixedCoupon() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));

            assertThat(coupon.getName()).isEqualTo("1000원 할인");
            assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.FIXED);
            assertThat(coupon.getDiscountValue()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @DisplayName("이름이 비어있으면 예외가 발생한다")
        @Test
        void shouldThrowException_whenNameIsEmpty() {
            assertThatThrownBy(() -> Coupon.createFixedCoupon("", BigDecimal.valueOf(1000)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("쿠폰 이름은 필수입니다");
        }

        @DisplayName("할인 값이 0 이하면 예외가 발생한다")
        @Test
        void shouldThrowException_whenDiscountValueIsZeroOrNegative() {
            assertThatThrownBy(() -> Coupon.createFixedCoupon("테스트 쿠폰", BigDecimal.ZERO))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("할인 값은 0보다 커야 합니다");
        }
    }

    @DisplayName("정률 쿠폰 생성 시,")
    @Nested
    class CreatePercentageCoupon {

        @DisplayName("정상적으로 생성된다")
        @Test
        void shouldCreatePercentageCoupon() {
            Coupon coupon = Coupon.createPercentageCoupon("10% 할인", BigDecimal.valueOf(10));

            assertThat(coupon.getName()).isEqualTo("10% 할인");
            assertThat(coupon.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
            assertThat(coupon.getDiscountValue()).isEqualByComparingTo(BigDecimal.valueOf(10));
        }

        @DisplayName("할인율이 100%를 초과하면 예외가 발생한다")
        @Test
        void shouldThrowException_whenPercentageExceeds100() {
            assertThatThrownBy(() -> Coupon.createPercentageCoupon("초과 할인", BigDecimal.valueOf(101)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("정률 할인은 100%를 초과할 수 없습니다");
        }
    }

    @DisplayName("정액 할인 계산 시,")
    @Nested
    class CalculateFixedDiscount {

        @DisplayName("정상적으로 할인 금액이 계산된다")
        @Test
        void shouldCalculateFixedDiscount() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            Money originalPrice = Money.of(10000);

            Money discount = coupon.calculateDiscount(originalPrice);

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @DisplayName("할인 금액이 원래 금액보다 크면 원래 금액만큼만 할인된다 (0원 처리)")
        @Test
        void shouldReturnOriginalPrice_whenDiscountExceedsPrice() {
            Coupon coupon = Coupon.createFixedCoupon("5000원 할인", BigDecimal.valueOf(5000));
            Money originalPrice = Money.of(3000);

            Money discount = coupon.calculateDiscount(originalPrice);

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000));
        }
    }

    @DisplayName("정률 할인 계산 시,")
    @Nested
    class CalculatePercentageDiscount {

        @DisplayName("정상적으로 할인 금액이 계산된다")
        @Test
        void shouldCalculatePercentageDiscount() {
            Coupon coupon = Coupon.createPercentageCoupon("10% 할인", BigDecimal.valueOf(10));
            Money originalPrice = Money.of(10000);

            Money discount = coupon.calculateDiscount(originalPrice);

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }

        @DisplayName("소수점 이하는 버림 처리된다")
        @Test
        void shouldTruncateDecimalPlaces() {
            Coupon coupon = Coupon.createPercentageCoupon("15% 할인", BigDecimal.valueOf(15));
            Money originalPrice = Money.of(10001);

            Money discount = coupon.calculateDiscount(originalPrice);

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1500));
        }

        @DisplayName("100% 할인 시 원래 금액 전체가 할인된다")
        @Test
        void shouldReturnOriginalPrice_when100PercentDiscount() {
            Coupon coupon = Coupon.createPercentageCoupon("100% 할인", BigDecimal.valueOf(100));
            Money originalPrice = Money.of(10000);

            Money discount = coupon.calculateDiscount(originalPrice);

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }
    }
}
