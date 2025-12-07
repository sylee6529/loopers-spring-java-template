package com.loopers.domain.coupon;

import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCouponTest {

    @DisplayName("회원 쿠폰 발급 시,")
    @Nested
    class IssueMemberCoupon {

        @DisplayName("정상적으로 발급된다")
        @Test
        void shouldIssueMemberCoupon() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            Long memberId = 123L;

            MemberCoupon memberCoupon = MemberCoupon.issue(memberId, coupon);

            assertThat(memberCoupon.getMemberId()).isEqualTo(memberId);
            assertThat(memberCoupon.getCoupon()).isEqualTo(coupon);
            assertThat(memberCoupon.isUsed()).isFalse();
        }

        @DisplayName("회원 ID가 null이면 예외가 발생한다")
        @Test
        void shouldThrowException_whenMemberIdIsEmpty() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));

            assertThatThrownBy(() -> MemberCoupon.issue(null, coupon))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("회원 ID는 필수입니다");
        }

        @DisplayName("쿠폰이 null이면 예외가 발생한다")
        @Test
        void shouldThrowException_whenCouponIsNull() {
            assertThatThrownBy(() -> MemberCoupon.issue(123L, null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("쿠폰 정보는 필수입니다");
        }
    }

    @DisplayName("쿠폰 사용 시,")
    @Nested
    class UseMemberCoupon {

        @DisplayName("정상적으로 사용 처리된다")
        @Test
        void shouldMarkAsUsed() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            MemberCoupon memberCoupon = MemberCoupon.issue(123L, coupon);

            memberCoupon.use();

            assertThat(memberCoupon.isUsed()).isTrue();
        }

        @DisplayName("이미 사용된 쿠폰을 다시 사용하면 예외가 발생한다")
        @Test
        void shouldThrowException_whenAlreadyUsed() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            MemberCoupon memberCoupon = MemberCoupon.issue(123L, coupon);
            memberCoupon.use();

            assertThatThrownBy(memberCoupon::use)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("이미 사용된 쿠폰입니다");
        }
    }

    @DisplayName("쿠폰 사용 가능 여부 검증 시,")
    @Nested
    class ValidateUsable {

        @DisplayName("사용 가능한 쿠폰은 통과한다")
        @Test
        void shouldPassValidation_whenUsable() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            MemberCoupon memberCoupon = MemberCoupon.issue(123L, coupon);

            memberCoupon.validateUsable();

            assertThat(memberCoupon.isUsable()).isTrue();
        }

        @DisplayName("사용 불가능한 쿠폰은 예외가 발생한다")
        @Test
        void shouldThrowException_whenNotUsable() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            MemberCoupon memberCoupon = MemberCoupon.issue(123L, coupon);
            memberCoupon.use();

            assertThatThrownBy(memberCoupon::validateUsable)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("사용할 수 없는 쿠폰입니다");
        }
    }

    @DisplayName("쿠폰 소유권 검증 시,")
    @Nested
    class ValidateOwnership {

        @DisplayName("소유자인 경우 통과한다")
        @Test
        void shouldPassValidation_whenOwner() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            MemberCoupon memberCoupon = MemberCoupon.issue(123L, coupon);

            memberCoupon.validateOwnership(123L);
        }

        @DisplayName("소유자가 아닌 경우 예외가 발생한다")
        @Test
        void shouldThrowException_whenNotOwner() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            MemberCoupon memberCoupon = MemberCoupon.issue(123L, coupon);

            assertThatThrownBy(() -> memberCoupon.validateOwnership(999L))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("본인의 쿠폰만 사용할 수 있습니다");
        }
    }

    @DisplayName("할인 계산 시,")
    @Nested
    class CalculateDiscount {

        @DisplayName("연결된 쿠폰의 할인 금액이 계산된다")
        @Test
        void shouldDelegateToLinkedCoupon() {
            Coupon coupon = Coupon.createFixedCoupon("1000원 할인", BigDecimal.valueOf(1000));
            MemberCoupon memberCoupon = MemberCoupon.issue(123L, coupon);
            Money originalPrice = Money.of(10000);

            Money discount = memberCoupon.calculateDiscount(originalPrice);

            assertThat(discount.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        }
    }
}
