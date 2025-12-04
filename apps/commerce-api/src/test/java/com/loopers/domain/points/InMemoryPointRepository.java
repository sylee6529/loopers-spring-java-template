package com.loopers.domain.points;

import com.loopers.domain.points.repository.PointRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryPointRepository implements PointRepository {

    private final Map<Long, Point> store = new HashMap<>();

    @Override
    public Optional<Point> findByMemberId(Long memberId) {
        return Optional.ofNullable(store.get(memberId));
    }

    @Override
    public Optional<Point> findByMemberIdForUpdate(Long memberId) {
        return findByMemberId(memberId);
    }

    @Override
    public Point save(Point point) {
        store.put(point.getMemberId(), point);
        return point;
    }

    public void clear() {
        store.clear();
    }
}
