package com.loopers.domain.points.repository;

import com.loopers.domain.points.Point;

import java.util.Optional;

public interface PointRepository {
    Optional<Point> findByMemberId(Long memberId);

    Optional<Point> findByMemberIdForUpdate(Long memberId);

    Point save(Point point);
}
