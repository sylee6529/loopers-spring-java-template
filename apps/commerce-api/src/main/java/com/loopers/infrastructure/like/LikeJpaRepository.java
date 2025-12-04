package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LikeJpaRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId);

    boolean existsByMemberIdAndProductId(Long memberId, Long productId);

    long countByProductId(Long productId);

    void deleteByMemberIdAndProductId(Long memberId, Long productId);

    List<Like> findByMemberIdAndProductIdIn(Long memberId, List<Long> productIds);

    List<Like> findByMemberId(Long memberId);
}