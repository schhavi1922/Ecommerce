package com.infominez.ecommerce.product;

import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProductValidationTest {
    @Test void rejectsInvalidValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new Product("", "", new BigDecimal("-1.001"), -1));
            assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                    .contains("sku", "name", "unitPrice", "availableQuantity");
            assertThat(factory.getValidator().validate(new Product("SKU", "Mouse", BigDecimal.ZERO, 0))).isEmpty();
            assertThat(factory.getValidator().validate(new Product("SKU", "Mouse", null, null)))
                    .hasSize(2);
        }
    }
}
