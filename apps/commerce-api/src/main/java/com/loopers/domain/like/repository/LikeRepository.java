package com.loopers.domain.like.repository;

import com.loopers.domain.like.Like;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LikeRepository {

    Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);

    Like save(Like like);

    void deleteByMemberIdAndProductId(Long memberId, Long productId);

    Set<Long> findLikedProductIds(Long memberId, List<Long> productIds);

    Set<Long> findLikedProductIdsByMemberId(Long memberId);
}