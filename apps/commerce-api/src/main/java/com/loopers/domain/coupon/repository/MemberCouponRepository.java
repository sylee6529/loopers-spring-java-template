package com.loopers.domain.coupon.repository;

import com.loopers.domain.coupon.MemberCoupon;

import java.util.List;
import java.util.Optional;

public interface MemberCouponRepository {

    Optional<MemberCoupon> findById(Long id);

    Optional<MemberCoupon> findByIdForUpdate(Long id);

    List<MemberCoupon> findByMemberId(Long memberId);

    MemberCoupon save(MemberCoupon memberCoupon);
}
