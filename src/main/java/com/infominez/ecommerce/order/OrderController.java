package com.infominez.ecommerce.order;
import com.infominez.ecommerce.order.dto.OrderResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable @Positive long orderId) { return service.get(orderId); }
}
