package com.loopers.domain.points;

import java.util.Optional;

public interface PointRepository {
    Optional<Point> findByMemberId(String memberId);
    Point save(Point point);
}
