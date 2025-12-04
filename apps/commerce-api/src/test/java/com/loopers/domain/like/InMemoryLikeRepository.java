package com.loopers.domain.like;

import com.loopers.domain.like.repository.LikeRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryLikeRepository implements LikeRepository {

    private final Map<String, Like> store = new HashMap<>();

    private String key(Long memberId, Long productId) {
        return memberId + ":" + productId;
    }

    @Override
    public Optional<Like> findByMemberIdAndProductId(Long memberId, Long productId) {
        return Optional.ofNullable(store.get(key(memberId, productId)));
    }

    @Override
    public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
        return store.containsKey(key(memberId, productId));
    }

    @Override
    public long countByProductId(Long productId) {
        return store.values().stream()
                .mapToLong(like -> like.getProductId().equals(productId) ? 1 : 0)
                .sum();
    }

    @Override
    public Like save(Like like) {
        store.put(key(like.getMemberId(), like.getProductId()), like);
        return like;
    }

    @Override
    public void deleteByMemberIdAndProductId(Long memberId, Long productId) {
        store.remove(key(memberId, productId));
    }

    @Override
    public Set<Long> findLikedProductIds(Long memberId, List<Long> productIds) {
        return productIds.stream()
                .filter(productId -> existsByMemberIdAndProductId(memberId, productId))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Long> findLikedProductIdsByMemberId(Long memberId) {
        return store.values().stream()
                .filter(like -> like.getMemberId().equals(memberId))
                .map(Like::getProductId)
                .collect(Collectors.toSet());
    }

    public void clear() {
        store.clear();
    }
}