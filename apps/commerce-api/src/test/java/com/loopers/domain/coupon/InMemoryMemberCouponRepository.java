package com.loopers.domain.coupon;

import com.loopers.domain.coupon.repository.MemberCouponRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryMemberCouponRepository implements MemberCouponRepository {

    private final Map<Long, MemberCoupon> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public Optional<MemberCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<MemberCoupon> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public List<MemberCoupon> findByMemberId(String memberId) {
        return store.values().stream()
                .filter(mc -> mc.getMemberId().equals(memberId))
                .toList();
    }

    @Override
    public MemberCoupon save(MemberCoupon memberCoupon) {
        if (memberCoupon.getId() == null) {
            try {
                java.lang.reflect.Field idField = MemberCoupon.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(memberCoupon, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException("Failed to set id", e);
            }
        }
        store.put(memberCoupon.getId(), memberCoupon);
        return memberCoupon;
    }

    public void clear() {
        store.clear();
        idGenerator.set(1);
    }
}
