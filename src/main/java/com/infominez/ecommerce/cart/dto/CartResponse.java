package com.infominez.ecommerce.cart.dto;

import com.infominez.ecommerce.cart.Cart;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartResponse(Long id, Cart.Status status, Instant createdAt, Instant updatedAt,
                           List<Item> items, BigDecimal total) {
    public record Item(Long id, Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {}
    public static CartResponse from(Cart cart) {
        var items = cart.getItems().stream().map(i -> new Item(i.getId(), i.getProduct().getId(),
                i.getProduct().getName(), i.getProduct().getUnitPrice(), i.getQuantity(),
                i.getProduct().getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))).toList();
        return new CartResponse(cart.getId(), cart.getStatus(), cart.getCreatedAt(), cart.getUpdatedAt(),
                items, items.stream().map(Item::lineTotal).reduce(new BigDecimal("0.00"), BigDecimal::add));
    }
}
