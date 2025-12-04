package com.loopers.domain.like.service;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.repository.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.infrastructure.cache.CacheInvalidationService;
import com.loopers.infrastructure.cache.MemberLikesCache;
import com.loopers.infrastructure.cache.ProductLikeCountCache;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final CacheInvalidationService cacheInvalidationService;
    private final MemberLikesCache memberLikesCache;
    private final ProductLikeCountCache productLikeCountCache;

    public void like(Long memberId, Long productId) {
        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }

        // 1. 상품 존재 확인
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        // 2. DB 저장
        likeRepository.save(new Like(memberId, productId));

        // 3. like_count 캐시 증가 (Redis INCR)
        productLikeCountCache.increment(productId);

        // 4. 회원 좋아요 캐시 업데이트
        memberLikesCache.add(memberId, productId);

        // 5. 상품 캐시 무효화
        cacheInvalidationService.invalidateOnLikeChange(productId, product.getBrandId());
    }

    public void unlike(Long memberId, Long productId) {
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }

        // 1. 상품 존재 확인
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        // 2. DB 삭제
        likeRepository.deleteByMemberIdAndProductId(memberId, productId);

        // 3. like_count 캐시 감소 (Redis DECR)
        productLikeCountCache.decrement(productId);

        // 4. 회원 좋아요 캐시 업데이트
        memberLikesCache.remove(memberId, productId);

        // 5. 상품 캐시 무효화
        cacheInvalidationService.invalidateOnLikeChange(productId, product.getBrandId());
    }
}