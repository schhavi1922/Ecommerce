package com.infominez.ecommerce.coupon;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(Long id, String code, long milestone, BigDecimal discountPercentage,
                             Coupon.Status status, Instant createdAt) {
    static CouponResponse from(Coupon coupon) {
        return new CouponResponse(coupon.getId(), coupon.getCode(), coupon.getMilestone(),
                coupon.getDiscountPercentage(), coupon.getStatus(), coupon.getCreatedAt());
    }
}
