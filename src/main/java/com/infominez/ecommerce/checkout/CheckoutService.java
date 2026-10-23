package com.infominez.ecommerce.checkout;

import com.infominez.ecommerce.cart.*;
import com.infominez.ecommerce.common.exception.*;
import com.infominez.ecommerce.order.*;
import com.infominez.ecommerce.order.dto.OrderResponse;
import com.infominez.ecommerce.product.*;
import com.infominez.ecommerce.coupon.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class CheckoutService {
    private final CartRepository carts;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final JdbcTemplate jdbc;
    private final CouponRepository coupons;
    public CheckoutService(CartRepository carts, ProductRepository products, OrderRepository orders, JdbcTemplate jdbc, CouponRepository coupons) {
        this.carts = carts; this.products = products; this.orders = orders; this.jdbc = jdbc; this.coupons = coupons;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse checkout(long cartId, String key, CheckoutRequest request) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain 1 to 128 characters and cannot be blank");
        }
        String couponCode = request == null ? null : request.normalizedCouponCode();
        if (couponCode != null && couponCode.length() > 128) throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COUPON", "Coupon code is too long");
        // Same-key requests, even across instances/carts, serialize before reading the result.
        // A hash collision only serializes unrelated keys; exact uniqueness uses the full key.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, key);
                statement.execute();
            }
            return null;
        });
        var previous = orders.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            if (previous.get().getCartId() != cartId || !java.util.Objects.equals(previous.get().getCouponCode(), couponCode)) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was used for a different cart or coupon");
            }
            return OrderResponse.from(previous.get());
        }
        var cart = carts.findLockedById(cartId).orElseThrow(() -> new ResourceNotFoundException("Cart " + cartId + " was not found"));
        cart.requireOpen();
        if (cart.getItems().isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_CART", "Cannot check out an empty cart");
        // Lock order: idempotency key -> cart -> optional coupon -> products in ascending ID.
        Coupon coupon = null;
        if (couponCode != null) {
            coupon = coupons.findLockedByCode(couponCode).orElseThrow(() ->
                    new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_COUPON", "Coupon code is unknown"));
            coupon.requireAvailable();
        }
        var items = cart.getItems().stream().sorted(Comparator.comparing(i -> i.getProduct().getId())).toList();
        var locked = new LinkedHashMap<Long, Product>();
        for (var item : items) {
            long id = item.getProduct().getId();
            var product = products.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Product " + id + " was not found"));
            if (product.getAvailableQuantity() < item.getQuantity()) {
                throw new BusinessException(HttpStatus.CONFLICT, "INSUFFICIENT_INVENTORY", "Insufficient inventory for product " + id);
            }
            locked.put(id, product);
        }
        var order = new PurchaseOrder(cart, key, couponCode);
        for (var item : items) {
            var product = locked.get(item.getProduct().getId());
            order.addSnapshot(product, item.getQuantity());
            product.deductInventory(item.getQuantity());
        }
        if (coupon != null) order.applyCoupon(coupon.getDiscountPercentage());
        cart.checkOut();
        // Flush inside this transaction. Any failure rolls back stock, cart, order and key.
        orders.saveAndFlush(order);
        if (coupon != null) coupon.redeem(order);
        return OrderResponse.from(order);
    }
}
