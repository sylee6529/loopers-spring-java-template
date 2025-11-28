package com.loopers.domain.like.service;

import com.loopers.domain.like.repository.LikeRepository;
import com.loopers.infrastructure.cache.MemberLikesCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Component
@Transactional(readOnly = true)
public class LikeReadService {

    private final LikeRepository likeRepository;
    private final MemberLikesCache memberLikesCache;

    public long countByProductId(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    public boolean isLikedBy(Long memberId, Long productId) {
        if (memberId == null) {
            return false;
        }

        // 1. 캐시 존재 여부 확인
        if (!memberLikesCache.exists(memberId)) {
            // 캐시 miss: DB에서 전체 좋아요 목록 조회 후 캐시 워밍
            Set<Long> likedProductIds = likeRepository.findLikedProductIdsByMemberId(memberId);
            memberLikesCache.initialize(memberId, likedProductIds);
            log.debug("[LikeReadService] Cache warmed for memberId={}, count={}", memberId, likedProductIds.size());
            return likedProductIds.contains(productId);
        }

        // 2. 캐시에서 조회
        return memberLikesCache.isMember(memberId, productId);
    }

    public Set<Long> findLikedProductIds(Long memberId, List<Long> productIds) {
        if (memberId == null || productIds.isEmpty()) {
            return Set.of();
        }

        // 1. 캐시 존재 여부 확인
        if (!memberLikesCache.exists(memberId)) {
            // 캐시 miss: DB에서 전체 좋아요 목록 조회 후 캐시 워밍
            Set<Long> allLikedProductIds = likeRepository.findLikedProductIdsByMemberId(memberId);
            memberLikesCache.initialize(memberId, allLikedProductIds);
            log.debug("[LikeReadService] Cache warmed for memberId={}, count={}", memberId, allLikedProductIds.size());

            // productIds 중 좋아요한 것만 필터링
            return productIds.stream()
                    .filter(allLikedProductIds::contains)
                    .collect(java.util.stream.Collectors.toSet());
        }

        // 2. 캐시에서 조회
        return memberLikesCache.findLikedProductIds(memberId, productIds);
    }
}