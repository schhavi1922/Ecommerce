package com.infominez.ecommerce.order;

import com.infominez.ecommerce.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "order_items")
@Check(constraints = "quantity > 0 AND unit_price >= 0 AND line_total = unit_price * quantity")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private PurchaseOrder order;
    @Column(nullable = false, updatable = false)
    private Long productId;
    @Column(nullable = false, updatable = false, length = 200)
    private String productName;
    @Column(nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false, updatable = false)
    private int quantity;
    @Column(nullable = false, updatable = false, precision = 30, scale = 2)
    private BigDecimal lineTotal;
    protected OrderItem() {}
    OrderItem(PurchaseOrder order, Product product, int quantity) {
        this.order = order;
        this.productId = product.getId();
        this.productName = product.getName();
        this.unitPrice = product.getUnitPrice();
        this.quantity = quantity;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
