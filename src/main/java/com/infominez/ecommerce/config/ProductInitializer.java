package com.infominez.ecommerce.config;

import com.infominez.ecommerce.product.Product;
import com.infominez.ecommerce.product.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProductInitializer implements CommandLineRunner {
    private final ProductRepository repository;
    public ProductInitializer(ProductRepository repository) { this.repository = repository; }

    @Override
    @Transactional
    public void run(String... args) {
        List<Product> seeds = List.of(
                new Product("SEED-KEYBOARD", "Keyboard", new BigDecimal("49.99"), 25),
                new Product("SEED-MOUSE", "Mouse", new BigDecimal("19.99"), 50),
                new Product("SEED-MONITOR", "Monitor", new BigDecimal("199.00"), 10),
                new Product("SEED-HEADPHONES", "Headphones", new BigDecimal("79.50"), 15),
                new Product("SEED-LIMITED", "Limited Edition Desk Mat", new BigDecimal("29.00"), 2));
        for (Product product : seeds) {
            if (!repository.existsBySku(product.getSku())) {
                repository.save(product);
            }
        }
    }
}
