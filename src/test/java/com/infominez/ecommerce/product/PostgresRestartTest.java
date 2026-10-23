package com.infominez.ecommerce.product;

import com.infominez.ecommerce.EcommerceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class PostgresRestartTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private ServletWebServerApplicationContext start() {
        return (ServletWebServerApplicationContext) SpringApplication.run(EcommerceApplication.class,
                "--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword());
    }

    @Test void preservesDataAcrossApplicationRestartAndReportsDatabaseOutage() {
        Long id;
        try (var context = start()) {
            var repository = context.getBean(ProductRepository.class);
            id = repository.findBySku("SEED-LIMITED").orElseThrow().getId();
            context.getBean(JdbcTemplate.class).update("update products set available_quantity = 0 where id = ?", id);
        }
        try (var context = start()) {
            var repository = context.getBean(ProductRepository.class);
            assertThat(repository.count()).isEqualTo(5);
            assertThat(repository.findById(id).orElseThrow().getAvailableQuantity()).isZero();
            var client = new RestTemplate();
            String base = "http://localhost:" + context.getWebServer().getPort();
            assertThat(client.getForEntity(base + "/ready", String.class).getStatusCode().value()).isEqualTo(200);
            assertThat(client.getForObject(base + "/api/v1/products/" + id, String.class)).contains("\"availableQuantity\":0");
            postgres.stop();
            assertThat(client.getForEntity(base + "/health", String.class).getStatusCode().value()).isEqualTo(200);
            assertThatThrownBy(() -> client.getForEntity(base + "/ready", String.class))
                    .isInstanceOf(org.springframework.web.client.HttpServerErrorException.ServiceUnavailable.class);
        }
    }
}
