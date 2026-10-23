package com.infominez.ecommerce.order.dto;

import com.infominez.ecommerce.order.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, Long cartId, PurchaseOrder.Status status, Instant createdAt, List<Item> items,
                            BigDecimal grossTotal, BigDecimal discountAmount, BigDecimal finalTotal,
                            String couponCode, BigDecimal discountPercentage) {
    public record Item(Long id, Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
    public static OrderResponse from(PurchaseOrder order) {
        return new OrderResponse(order.getId(), order.getCartId(), order.getStatus(), order.getCreatedAt(),
                order.getItems().stream().map(i -> new Item(i.getId(), i.getProductId(), i.getProductName(),
                        i.getUnitPrice(), i.getQuantity(), i.getLineTotal())).toList(),
                order.getGrossTotal(), order.getDiscountAmount(), order.getFinalTotal(),
                order.getCouponCode(), order.getDiscountPercentage());
    }
}
