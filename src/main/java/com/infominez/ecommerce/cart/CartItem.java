package com.infominez.ecommerce.cart;

import com.infominez.ecommerce.product.Product;
import com.infominez.ecommerce.common.exception.BusinessException;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import org.hibernate.annotations.Check;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uk_cart_product", columnNames = {"cart_id", "product_id"}))
@Check(constraints = "quantity > 0")
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @Positive @Column(nullable = false)
    private int quantity;
    protected CartItem() {}
    CartItem(Cart cart, Product product, int quantity) { this.cart = cart; this.product = product; setQuantity(quantity); }
    public void setQuantity(int quantity) {
        if (quantity <= 0) throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity must be positive");
        this.quantity = quantity;
    }
    void increase(int amount) {
        long sum = (long) quantity + amount;
        if (amount <= 0 || sum > Integer.MAX_VALUE) throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Combined quantity exceeds the supported positive integer range");
        setQuantity((int) sum);
    }
    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
}
