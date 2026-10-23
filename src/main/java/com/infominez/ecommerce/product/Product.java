package com.infominez.ecommerce.product;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "products")
@Check(constraints = "unit_price >= 0 AND available_quantity >= 0")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank @Size(max = 64)
    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String sku;

    @NotBlank @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @NotNull @PositiveOrZero
    @Column(nullable = false)
    private Integer availableQuantity;

    protected Product() {}

    public Product(String sku, String name, BigDecimal unitPrice, Integer availableQuantity) {
        this.sku = sku;
        this.name = name;
        this.unitPrice = unitPrice;
        this.availableQuantity = availableQuantity;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public Integer getAvailableQuantity() { return availableQuantity; }

    // Call only after obtaining a database write lock inside the checkout transaction.
    public void deductInventory(int quantity) {
        if (quantity <= 0 || quantity > availableQuantity) {
            throw new IllegalArgumentException("Invalid inventory deduction");
        }
        availableQuantity -= quantity;
    }
}
