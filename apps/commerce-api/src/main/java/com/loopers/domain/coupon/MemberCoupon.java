package com.loopers.domain.coupon;

import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(nullable = false)
    private boolean used;

    private MemberCoupon(String memberId, Coupon coupon) {
        validate(memberId, coupon);
        this.memberId = memberId;
        this.coupon = coupon;
        this.used = false;
    }

    public static MemberCoupon issue(String memberId, Coupon coupon) {
        return new MemberCoupon(memberId, coupon);
    }

    public void use() {
        if (this.used) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        this.used = true;
    }

    public boolean isUsable() {
        return !this.used;
    }

    public Money calculateDiscount(Money originalPrice) {
        return coupon.calculateDiscount(originalPrice);
    }

    public void validateOwnership(String memberId) {
        if (!this.memberId.equals(memberId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 쿠폰만 사용할 수 있습니다.");
        }
    }

    public void validateUsable() {
        if (!isUsable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
    }

    private void validate(String memberId, Coupon coupon) {
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (coupon == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 정보는 필수입니다.");
        }
    }
}
