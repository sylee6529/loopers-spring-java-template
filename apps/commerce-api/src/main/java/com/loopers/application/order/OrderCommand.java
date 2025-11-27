package com.loopers.application.order;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OrderCommand {

    private final String memberId;
    private final List<OrderLineCommand> orderLines;
    private final Long memberCouponId;

    public static OrderCommand of(String memberId, List<OrderLineCommand> orderLines) {
        return OrderCommand.builder()
                .memberId(memberId)
                .orderLines(orderLines)
                .memberCouponId(null)
                .build();
    }

    public static OrderCommand of(String memberId, List<OrderLineCommand> orderLines, Long memberCouponId) {
        return OrderCommand.builder()
                .memberId(memberId)
                .orderLines(orderLines)
                .memberCouponId(memberCouponId)
                .build();
    }
}