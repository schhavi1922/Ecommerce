package com.infominez.ecommerce.product;

import com.infominez.ecommerce.config.ProductInitializer;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductInitializerTest {
    @Test void preservesExistingProductAndDoesNotDuplicateSeeds() {
        ProductRepository repository = mock(ProductRepository.class);
        Map<String, Product> stored = new HashMap<>();
        Product changed = new Product("SEED-LIMITED", "Existing name", BigDecimal.ONE, 0);
        stored.put(changed.getSku(), changed);
        when(repository.existsBySku(anyString())).thenAnswer(call -> stored.containsKey(call.getArgument(0)));
        when(repository.save(any(Product.class))).thenAnswer(call -> {
            Product product = call.getArgument(0);
            stored.put(product.getSku(), product);
            return product;
        });
        var initializer = new ProductInitializer(repository);
        initializer.run();
        initializer.run();
        assertThat(stored).hasSize(5);
        assertThat(stored.get("SEED-LIMITED")).isSameAs(changed);
        assertThat(changed.getAvailableQuantity()).isZero();
        verify(repository, times(4)).save(any(Product.class));
    }
}
