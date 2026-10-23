package com.infominez.ecommerce.coupon;

import com.infominez.ecommerce.common.exception.BusinessException;
import com.infominez.ecommerce.order.PurchaseOrder;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.Check;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "coupons", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_code", columnNames = "code"),
        @UniqueConstraint(name = "uk_coupon_milestone", columnNames = "milestone")})
@Check(constraints = "milestone > 0 AND discount_percentage > 0 AND discount_percentage <= 100 AND "
        + "((status = 'AVAILABLE' AND redeemed_at IS NULL AND redeemed_order_id IS NULL) OR "
        + "(status = 'REDEEMED' AND redeemed_at IS NOT NULL AND redeemed_order_id IS NOT NULL))")
public class Coupon {
    public enum Status { AVAILABLE, REDEEMED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false, length = 32)
    private String code;
    @Column(nullable = false, updatable = false)
    private long milestone;
    @Column(nullable = false, updatable = false, precision = 7, scale = 4)
    private BigDecimal discountPercentage;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.AVAILABLE;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private Instant redeemedAt;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "redeemed_order_id", unique = true)
    private PurchaseOrder redeemedOrder;

    protected Coupon() {}
    Coupon(String code, long milestone, BigDecimal percentage) {
        this.code = code;
        this.milestone = milestone;
        this.discountPercentage = percentage.setScale(4, java.math.RoundingMode.UNNECESSARY);
    }
    public void requireAvailable() {
        if (status != Status.AVAILABLE) throw new BusinessException(HttpStatus.CONFLICT, "COUPON_REDEEMED", "Coupon has already been redeemed");
    }
    public void redeem(PurchaseOrder order) {
        requireAvailable();
        redeemedOrder = order;
        redeemedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        status = Status.REDEEMED;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public long getMilestone() { return milestone; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public Long getRedeemedOrderId() { return redeemedOrder == null ? null : redeemedOrder.getId(); }
}
