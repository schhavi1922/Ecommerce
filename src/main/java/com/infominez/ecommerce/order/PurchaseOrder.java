package com.infominez.ecommerce.order;

import com.infominez.ecommerce.cart.Cart;
import com.infominez.ecommerce.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_cart", columnNames = "cart_id"),
        @UniqueConstraint(name = "uk_order_idempotency", columnNames = "idempotency_key")})
@Check(constraints = "gross_total >= 0 AND discount_amount >= 0 AND final_total = gross_total - discount_amount AND final_total >= 0")
public class PurchaseOrder {
    public enum Status { COMPLETED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cart_id", nullable = false, updatable = false)
    private Cart cart;
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;
    // Nullable for pre-coupon orders, preserving their no-coupon replay identity.
    @Column(updatable = false, length = 128)
    private String couponCode;
    @Column(updatable = false, precision = 7, scale = 4)
    private BigDecimal discountPercentage;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 20)
    private Status status = Status.COMPLETED;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    @Column(nullable = false, updatable = false, precision = 30, scale = 2)
    private BigDecimal grossTotal = new BigDecimal("0.00");
    @Column(nullable = false, updatable = false, precision = 30, scale = 2)
    private BigDecimal discountAmount = new BigDecimal("0.00");
    @Column(nullable = false, updatable = false, precision = 30, scale = 2)
    private BigDecimal finalTotal = new BigDecimal("0.00");
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id asc")
    private List<OrderItem> items = new ArrayList<>();
    protected PurchaseOrder() {}
    public PurchaseOrder(Cart cart, String key) { this(cart, key, null); }
    public PurchaseOrder(Cart cart, String key, String couponCode) {
        this.cart = cart; this.idempotencyKey = key; this.couponCode = couponCode;
    }
    public void addSnapshot(Product product, int quantity) {
        var item = new OrderItem(this, product, quantity);
        items.add(item);
        grossTotal = grossTotal.add(item.getLineTotal());
        finalTotal = grossTotal.subtract(discountAmount);
    }
    public Long getId() { return id; }
    public void applyCoupon(BigDecimal percentage) {
        if (id != null || couponCode == null || percentage == null || percentage.signum() <= 0
                || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Invalid coupon discount");
        }
        discountPercentage = percentage;
        discountAmount = grossTotal.multiply(percentage).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP)
                .min(grossTotal).max(new BigDecimal("0.00"));
        finalTotal = grossTotal.subtract(discountAmount);
    }
    public String getCouponCode() { return couponCode; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public Long getCartId() { return cart.getId(); }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getGrossTotal() { return grossTotal; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getFinalTotal() { return finalTotal; }
    public List<OrderItem> getItems() { return java.util.Collections.unmodifiableList(items); }
}
