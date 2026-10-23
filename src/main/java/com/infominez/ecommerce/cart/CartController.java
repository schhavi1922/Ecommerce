package com.infominez.ecommerce.cart;

import com.infominez.ecommerce.cart.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }
    @PostMapping public ResponseEntity<CartResponse> create() {
        var cart = service.create();
        return ResponseEntity.created(URI.create("/api/v1/carts/" + cart.id())).body(cart);
    }
    @GetMapping("/{cartId}") public CartResponse get(@PathVariable @Positive long cartId) { return service.get(cartId); }
    @PostMapping("/{cartId}/items") public CartResponse add(@PathVariable @Positive long cartId, @Valid @RequestBody AddItemRequest request) {
        return service.add(cartId, request.productId(), request.quantity());
    }
    @PutMapping("/{cartId}/items/{itemId}") public CartResponse update(@PathVariable @Positive long cartId,
            @PathVariable @Positive long itemId, @Valid @RequestBody UpdateItemRequest request) {
        return service.update(cartId, itemId, request.quantity());
    }
    @DeleteMapping("/{cartId}/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable @Positive long cartId, @PathVariable @Positive long itemId) { service.remove(cartId, itemId); }
}
