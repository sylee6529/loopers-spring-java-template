package com.loopers.application.order;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Builder
public class OrderInfo {

    private final Long id;
    private final String orderNo;
    private final Long memberId;
    private final OrderStatus status;
    private final Money totalPrice;
    private final List<OrderItemInfo> items;
    private final ZonedDateTime orderedAt;

    public static OrderInfo from(Order order) {
        List<OrderItemInfo> itemInfos = order.getItems().stream()
                .map(OrderItemInfo::from)
                .toList();

        return OrderInfo.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .memberId(order.getMemberId())
                .status(order.getStatus())
                .totalPrice(order.getTotalPrice())
                .items(itemInfos)
                .orderedAt(order.getCreatedAt())
                .build();
    }
    
    @Getter
    @Builder
    public static class OrderItemInfo {
        
        private final Long productId;
        private final int quantity;
        private final Money unitPrice;
        private final Money totalPrice;
        
        public static OrderItemInfo from(OrderItem orderItem) {
            return OrderItemInfo.builder()
                    .productId(orderItem.getProductId())
                    .quantity(orderItem.getQuantity())
                    .unitPrice(orderItem.getUnitPrice())
                    .totalPrice(orderItem.getTotalPrice())
                    .build();
        }
    }
}
