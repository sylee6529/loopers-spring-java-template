package com.loopers.application.order;

import com.loopers.domain.order.OrderItem;

/**
 * packageName : com.loopers.application.order
 * fileName     : OrderInfo
 * author      : byeonsungmun
 * date        : 2025. 11. 13.
 * description :
 * ===========================================
 * DATE         AUTHOR       NOTE
 * -------------------------------------------
 * 2025. 11. 13.     byeonsungmun       최초 생성
 */
public record OrderItemInfo(
        Long productId,
        int quantity,
        Long unitPrice,
        Long totalPrice
) {
    public static OrderItemInfo from(OrderItem item) {
        return new OrderItemInfo(
                item.getProductId(),
                item.getQuantity(),
                item.getUnitPrice().getAmount().longValue(),
                item.getTotalPrice().getAmount().longValue()
        );
    }
}
