package com.infominez.ecommerce.report;

import java.math.BigDecimal;
import java.util.List;

public record SummaryResponse(List<ProductSales> products, BigDecimal grossRevenue,
        BigDecimal totalDiscounts, BigDecimal netRevenue, long totalCouponsGenerated,
        long availableCoupons, long redeemedCoupons, long successfulOrders) {
    public record ProductSales(long productId, String productName, long purchasedQuantity) {}
}
