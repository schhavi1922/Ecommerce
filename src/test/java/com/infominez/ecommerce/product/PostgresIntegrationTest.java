package com.infominez.ecommerce.product;

import com.infominez.ecommerce.config.ProductInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired ProductRepository repository;
    @Autowired ProductInitializer initializer;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void createsTablesSeedsWithoutResettingAndServesApis() throws Exception {
        assertThat(repository.count()).isEqualTo(5);
        Product limited = repository.findBySku("SEED-LIMITED").orElseThrow();
        jdbc.update("update products set available_quantity = 1 where id = ?", limited.getId());
        initializer.run();
        initializer.run();
        assertThat(repository.count()).isEqualTo(5);
        assertThat(repository.findById(limited.getId()).orElseThrow().getAvailableQuantity()).isEqualTo(1);
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(get("/ready")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/v1/products/" + limited.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(1));
    }
}
