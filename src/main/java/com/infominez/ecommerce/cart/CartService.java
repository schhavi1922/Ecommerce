package com.infominez.ecommerce.cart;

import com.infominez.ecommerce.cart.dto.CartResponse;
import com.infominez.ecommerce.common.exception.ResourceNotFoundException;
import com.infominez.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartService {
    private final CartRepository carts;
    private final ProductRepository products;
    public CartService(CartRepository carts, ProductRepository products) { this.carts = carts; this.products = products; }
    public CartResponse create() { return CartResponse.from(carts.save(new Cart())); }
    public CartResponse get(long id) { return CartResponse.from(locked(id)); }
    public CartResponse add(long id, long productId, int quantity) {
        var cart = locked(id);
        cart.requireOpen();
        var product = products.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " was not found"));
        cart.add(product, quantity);
        carts.flush();
        return CartResponse.from(cart);
    }
    public CartResponse update(long id, long itemId, int quantity) {
        var cart = locked(id);
        cart.requireOpen();
        item(cart, itemId).setQuantity(quantity);
        cart.touch();
        return CartResponse.from(cart);
    }
    public void remove(long id, long itemId) {
        var cart = locked(id);
        cart.requireOpen();
        cart.remove(item(cart, itemId));
    }
    private Cart locked(long id) {
        return carts.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Cart " + id + " was not found"));
    }
    private CartItem item(Cart cart, long itemId) {
        return cart.getItems().stream().filter(i -> i.getId() == itemId).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Cart item " + itemId + " was not found in this cart"));
    }
}
