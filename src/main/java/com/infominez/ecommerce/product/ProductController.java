package com.infominez.ecommerce.product;

import com.infominez.ecommerce.product.dto.ProductResponse;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }

    @GetMapping
    public List<ProductResponse> list() { return service.list(); }

    @GetMapping("/{productId}")
    public ProductResponse get(@PathVariable @Positive long productId) { return service.get(productId); }
}
