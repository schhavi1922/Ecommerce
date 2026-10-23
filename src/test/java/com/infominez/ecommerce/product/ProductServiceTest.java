package com.infominez.ecommerce.product;

import com.infominez.ecommerce.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductService service = new ProductService(repository);

    @Test void mapsProductsAndSortsById() {
        when(repository.findAll(Sort.by("id"))).thenReturn(List.of(new Product("SKU", "Mouse", new BigDecimal("19.99"), 3)));
        assertThat(service.list()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("Mouse");
            assertThat(p.unitPrice()).isEqualByComparingTo("19.99");
        });
    }
    @Test void getsProductAndHandlesMissingId() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Product("SKU", "Mouse", BigDecimal.ONE, 3)));
        assertThat(service.get(1).sku()).isEqualTo("SKU");
        assertThatThrownBy(() -> service.get(2)).isInstanceOf(ResourceNotFoundException.class);
    }
}
