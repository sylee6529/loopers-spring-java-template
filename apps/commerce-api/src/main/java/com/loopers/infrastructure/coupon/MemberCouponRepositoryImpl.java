package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.MemberCoupon;
import com.loopers.domain.coupon.repository.MemberCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class MemberCouponRepositoryImpl implements MemberCouponRepository {

    private final MemberCouponJpaRepository memberCouponJpaRepository;

    @Override
    public Optional<MemberCoupon> findById(Long id) {
        return memberCouponJpaRepository.findById(id);
    }

    @Override
    public Optional<MemberCoupon> findByIdForUpdate(Long id) {
        return memberCouponJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<MemberCoupon> findByMemberId(String memberId) {
        return memberCouponJpaRepository.findByMemberId(memberId);
    }

    @Override
    public MemberCoupon save(MemberCoupon memberCoupon) {
        return memberCouponJpaRepository.save(memberCoupon);
    }
}
