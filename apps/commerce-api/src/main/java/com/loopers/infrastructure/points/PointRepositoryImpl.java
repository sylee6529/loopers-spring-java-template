package com.loopers.infrastructure.points;

import com.loopers.domain.points.Point;
import com.loopers.domain.points.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PointRepositoryImpl implements PointRepository {
    private final PointJpaRepository pointJpaRepository;

    @Override
    public Optional<Point> findByMemberId(String memberId) {
        return pointJpaRepository.findByMemberId(memberId);
    }

    @Override
    public Point save(Point point) {
        return pointJpaRepository.save(point);
    }
}