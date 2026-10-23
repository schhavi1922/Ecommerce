package com.infominez.ecommerce.checkout;
import jakarta.validation.constraints.Size;
public record CheckoutRequest(@Size(max = 128) String couponCode) {
    public String normalizedCouponCode() {
        if (couponCode == null || couponCode.isBlank()) return null;
        return couponCode.strip();
    }
}
