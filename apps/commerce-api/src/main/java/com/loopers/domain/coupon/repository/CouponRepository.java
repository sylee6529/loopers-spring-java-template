package com.loopers.domain.coupon.repository;

import com.loopers.domain.coupon.Coupon;

import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findById(Long id);

    Coupon save(Coupon coupon);
}
