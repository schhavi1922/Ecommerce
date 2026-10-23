package com.infominez.ecommerce.product.dto;

import com.infominez.ecommerce.product.Product;
import java.math.BigDecimal;

public record ProductResponse(Long id, String sku, String name, BigDecimal unitPrice, int availableQuantity) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                product.getUnitPrice(), product.getAvailableQuantity());
    }
}
