package com.loopers.domain.like.service;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.repository.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 좋아요 도메인 서비스
 * - DB 저장/삭제만 처리
 * - Redis 캐시 업데이트는 이벤트 리스너(LikeAggregationEventListener)에서 비동기 처리
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    /**
     * 좋아요 추가
     * @return 좋아요한 상품 (이벤트 발행용 brandId 포함)
     */
    public Product like(Long memberId, Long productId) {
        // 중복 좋아요 방지 (멱등성)
        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            log.debug("[LikeService] 이미 좋아요한 상품 - memberId: {}, productId: {}", memberId, productId);
            return productRepository.findById(productId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        }

        // 1. 상품 존재 확인
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        // 2. DB 저장
        likeRepository.save(new Like(memberId, productId));
        product.increaseLikeCount();
        productRepository.save(product);

        log.info("[LikeService] 좋아요 저장 완료 - memberId: {}, productId: {}", memberId, productId);

        return product;
    }

    /**
     * 좋아요 취소
     * @return 좋아요 취소한 상품 (이벤트 발행용 brandId 포함)
     */
    public Product unlike(Long memberId, Long productId) {
        // 좋아요 없으면 스킵 (멱등성)
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            log.debug("[LikeService] 좋아요하지 않은 상품 - memberId: {}, productId: {}", memberId, productId);
            return productRepository.findById(productId)
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        }

        // 1. 상품 존재 확인
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        // 2. DB 삭제
        likeRepository.deleteByMemberIdAndProductId(memberId, productId);
        product.decreaseLikeCount();
        productRepository.save(product);

        log.info("[LikeService] 좋아요 취소 완료 - memberId: {}, productId: {}", memberId, productId);

        return product;
    }
}
