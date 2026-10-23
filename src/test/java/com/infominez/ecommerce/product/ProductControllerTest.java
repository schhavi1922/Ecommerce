package com.infominez.ecommerce.product;

import com.infominez.ecommerce.common.exception.ResourceNotFoundException;
import com.infominez.ecommerce.product.dto.ProductResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService service;
    private final ProductResponse product = new ProductResponse(1L, "SKU", "Mouse", new BigDecimal("19.99"), 3);

    @Test void listsProducts() throws Exception {
        when(service.list()).thenReturn(List.of(product));
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Mouse"));
    }
    @Test void retrievesProduct() throws Exception {
        when(service.get(1)).thenReturn(product);
        mvc.perform(get("/api/v1/products/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(3));
    }
    @Test void unknownProductReturns404() throws Exception {
        when(service.get(99)).thenThrow(new ResourceNotFoundException("Product 99 was not found"));
        mvc.perform(get("/api/v1/products/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/products/99"));
    }
    @Test void invalidIdReturns400() throws Exception {
        mvc.perform(get("/api/v1/products/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/products/0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }
    @Test void unexpectedFailureDoesNotLeakDetails() throws Exception {
        when(service.list()).thenThrow(new IllegalStateException("secret internal detail"));
        mvc.perform(get("/api/v1/products")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
