package com.loopers.application.like;

import com.loopers.domain.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
@Transactional
public class LikeFacade {

    private final LikeService likeService;

    public void likeProduct(Long memberId, Long productId) {
        likeService.like(memberId, productId);
    }

    public void unlikeProduct(Long memberId, Long productId) {
        likeService.unlike(memberId, productId);
    }
}
