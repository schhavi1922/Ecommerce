package com.infominez.ecommerce.cart.dto;
import jakarta.validation.constraints.*;
public record AddItemRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) {}
