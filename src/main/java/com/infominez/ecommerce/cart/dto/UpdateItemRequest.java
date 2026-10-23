package com.infominez.ecommerce.cart.dto;
import jakarta.validation.constraints.*;
public record UpdateItemRequest(@NotNull @Positive Integer quantity) {}
