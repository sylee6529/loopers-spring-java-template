package com.loopers.domain.like;

import com.loopers.domain.like.repository.LikeRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryLikeRepository implements LikeRepository {

    private final Map<String, Like> store = new HashMap<>();

    private String key(String memberId, Long productId) {
        return memberId + ":" + productId;
    }

    @Override
    public Optional<Like> findByMemberIdAndProductId(String memberId, Long productId) {
        return Optional.ofNullable(store.get(key(memberId, productId)));
    }

    @Override
    public boolean existsByMemberIdAndProductId(String memberId, Long productId) {
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
    public void deleteByMemberIdAndProductId(String memberId, Long productId) {
        store.remove(key(memberId, productId));
    }

    public void clear() {
        store.clear();
    }
}