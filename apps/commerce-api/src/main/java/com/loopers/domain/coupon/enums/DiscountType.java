package com.loopers.domain.coupon.enums;

import com.loopers.domain.common.vo.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum DiscountType {

    FIXED {
        @Override
        public Money calculateDiscount(Money originalPrice, BigDecimal discountValue) {
            Money discount = Money.of(discountValue);
            if (discount.isGreaterThanOrEqual(originalPrice)) {
                return originalPrice;
            }
            return discount;
        }
    },

    PERCENTAGE {
        @Override
        public Money calculateDiscount(Money originalPrice, BigDecimal discountValue) {
            BigDecimal percentage = discountValue.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            BigDecimal discountAmount = originalPrice.getAmount().multiply(percentage).setScale(0, RoundingMode.DOWN);
            Money discount = Money.of(discountAmount);
            if (discount.isGreaterThanOrEqual(originalPrice)) {
                return originalPrice;
            }
            return discount;
        }
    };

    public abstract Money calculateDiscount(Money originalPrice, BigDecimal discountValue);
}
