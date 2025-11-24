package com.loopers.domain.coupon;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.coupon.enums.DiscountType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(nullable = false)
    private BigDecimal discountValue;

    private Coupon(String name, DiscountType discountType, BigDecimal discountValue) {
        validate(name, discountType, discountValue);
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    public static Coupon createFixedCoupon(String name, BigDecimal discountValue) {
        return new Coupon(name, DiscountType.FIXED, discountValue);
    }

    public static Coupon createPercentageCoupon(String name, BigDecimal discountValue) {
        return new Coupon(name, DiscountType.PERCENTAGE, discountValue);
    }

    public Money calculateDiscount(Money originalPrice) {
        return discountType.calculateDiscount(originalPrice, discountValue);
    }

    private void validate(String name, DiscountType discountType, BigDecimal discountValue) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
        if (discountType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 타입은 필수입니다.");
        }
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (discountType == DiscountType.PERCENTAGE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100%를 초과할 수 없습니다.");
        }
    }
}
