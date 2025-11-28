package com.loopers.domain.points.service;

import com.loopers.domain.points.Point;
import com.loopers.domain.points.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Component
public class PointService {

    private final PointRepository pointRepository;

    @Transactional(readOnly = true)
    public BigDecimal getMemberPoints(Long memberId) {
        return pointRepository.findByMemberId(memberId)
                .map(Point::getAmount)
                .orElse(null);
    }

    @Transactional
    public Point initializeMemberPoints(Long memberId) {
        Point point = Point.create(memberId, BigDecimal.ZERO);
        return pointRepository.save(point);
    }
}
