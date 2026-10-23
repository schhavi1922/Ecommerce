package com.infominez.ecommerce.checkout;

import com.infominez.ecommerce.order.dto.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/carts/{cartId}/checkout")
public class CheckoutController {
    private final CheckoutService service;
    public CheckoutController(CheckoutService service) { this.service = service; }
    @PostMapping
    public OrderResponse checkout(@PathVariable @Positive long cartId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody(required = false) CheckoutRequest request) {
        return service.checkout(cartId, key, request);
    }
}
