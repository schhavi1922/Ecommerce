package com.infominez.ecommerce.order;

import com.infominez.ecommerce.common.exception.ResourceNotFoundException;
import com.infominez.ecommerce.order.dto.OrderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orders;
    public OrderService(OrderRepository orders) { this.orders = orders; }
    @Transactional(readOnly = true)
    public OrderResponse get(long id) {
        return OrderResponse.from(orders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Order " + id + " was not found")));
    }
}
