package com.infominez.ecommerce.coupon;

import com.infominez.ecommerce.cart.Cart;
import com.infominez.ecommerce.order.PurchaseOrder;
import com.infominez.ecommerce.product.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CouponMoneyTest {
    private PurchaseOrder order(String price, int quantity) {
        var order = new PurchaseOrder(new Cart(), "key", "coupon");
        order.addSnapshot(new Product("SKU", "Product", new BigDecimal(price), quantity), quantity);
        return order;
    }
    @Test void roundsOnceOnFullSubtotalUsingHalfUp() {
        var order = order("0.05", 3);
        order.applyCoupon(new BigDecimal("10"));
        assertThat(order.getGrossTotal()).isEqualByComparingTo("0.15");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.02");
        assertThat(order.getFinalTotal()).isEqualByComparingTo("0.13");
        assertThat(order.getItems().get(0).getLineTotal()).isEqualByComparingTo("0.15");
    }
    @Test void fullDiscountAndFreeProductsCannotProduceNegativeTotals() {
        var order = order("19.99", 2);
        order.applyCoupon(new BigDecimal("100"));
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("39.98");
        assertThat(order.getFinalTotal()).isEqualByComparingTo("0.00");
        var free = order("0.00", 1);
        free.applyCoupon(new BigDecimal("10"));
        assertThat(free.getFinalTotal()).isEqualByComparingTo("0.00");
    }
    @Test void fractionalPercentageUsesExactDecimalArithmetic() {
        var order = order("1.00", 3);
        order.applyCoupon(new BigDecimal("12.5"));
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0.38");
        assertThat(order.getFinalTotal()).isEqualByComparingTo("2.62");
    }
}
