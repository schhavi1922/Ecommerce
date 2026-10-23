package com.infominez.ecommerce.cart;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.infominez.ecommerce.product.Product;
import com.infominez.ecommerce.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "carts")
public class Cart {
    public enum Status { OPEN, CHECKED_OUT }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.OPEN;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    @Column(nullable = false)
    private Instant updatedAt = createdAt;
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<CartItem> items = new ArrayList<>();

    public Cart() {}
    public void requireOpen() {
        if (status != Status.OPEN) throw new BusinessException(HttpStatus.CONFLICT, "CART_CHECKED_OUT", "Cart is already checked out");
    }
    public void add(Product product, int quantity) {
        requireOpen();
        var existing = items.stream().filter(i -> i.getProduct().getId().equals(product.getId())).findFirst();
        if (existing.isPresent()) existing.get().increase(quantity);
        else items.add(new CartItem(this, product, quantity));
        touch();
    }
    public void remove(CartItem item) { requireOpen(); items.remove(item); touch(); }
    public void checkOut() { requireOpen(); status = Status.CHECKED_OUT; touch(); }
    public void touch() { updatedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    public Long getId() { return id; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CartItem> getItems() { return java.util.Collections.unmodifiableList(items); }
}
