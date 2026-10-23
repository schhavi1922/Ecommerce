package com.infominez.ecommerce.config;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rewards")
public record RewardProperties(@Min(1) int n,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100")
        @Digits(integer = 3, fraction = 4) BigDecimal x) {}
