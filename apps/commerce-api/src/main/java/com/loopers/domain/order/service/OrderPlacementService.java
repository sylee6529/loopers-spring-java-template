package com.loopers.domain.order.service;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.coupon.MemberCoupon;
import com.loopers.domain.coupon.repository.MemberCouponRepository;
import com.loopers.domain.members.repository.MemberRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.command.OrderLineCommand;
import com.loopers.domain.order.command.OrderPlacementCommand;
import com.loopers.domain.order.repository.OrderRepository;
import com.loopers.domain.points.Point;
import com.loopers.domain.points.repository.PointRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.repository.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderPlacementService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final PointRepository pointRepository;
    private final MemberCouponRepository memberCouponRepository;

    public Order placeOrder(OrderPlacementCommand command) {
        validateMemberExists(command.getMemberId());

        MemberCoupon memberCoupon = null;
        if (command.hasCoupon()) {
            memberCoupon = validateAndLockCoupon(command.getMemberCouponId(), command.getMemberId());
        }

        List<OrderItem> items = processOrderLines(command.getOrderLines());
        Money totalPrice = items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(Money.zero(), Money::plus);

        Money discountAmount = Money.zero();
        if (memberCoupon != null) {
            discountAmount = memberCoupon.calculateDiscount(totalPrice);
        }

        Money finalPrice = totalPrice.minus(discountAmount);
        if (finalPrice.isLessThan(Money.zero())) {
            finalPrice = Money.zero();
        }

        if (finalPrice.getAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            payWithPoints(command.getMemberId(), finalPrice);
        }

        if (memberCoupon != null) {
            memberCoupon.use();
            memberCouponRepository.save(memberCoupon);
        }

        Order order = Order.create(command.getMemberId(), items, finalPrice);
        return orderRepository.save(order);
    }

    private MemberCoupon validateAndLockCoupon(Long memberCouponId, String memberId) {
        MemberCoupon memberCoupon = memberCouponRepository.findByIdForUpdate(memberCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

        memberCoupon.validateOwnership(memberId);
        memberCoupon.validateUsable();

        return memberCoupon;
    }

    private List<OrderItem> processOrderLines(List<OrderLineCommand> orderLines) {
        List<OrderItem> items = new ArrayList<>();
        
        for (OrderLineCommand line : orderLines) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

            int updatedRows = productRepository.decreaseStock(product.getId(), line.getQuantity());
            if (updatedRows == 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
            }

            items.add(new OrderItem(product.getId(), line.getQuantity(), product.getPrice()));
        }
        
        return items;
    }

    private void validateMemberExists(String memberId) {
        if (!memberRepository.existsByMemberId(memberId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다.");
        }
    }

    private void payWithPoints(String memberId, Money totalPrice) {
        Point point = pointRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "포인트 정보를 찾을 수 없습니다."));

        if (!point.canAfford(totalPrice.getAmount())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다.");
        }

        point.pay(totalPrice.getAmount());
        pointRepository.save(point);
    }
}
