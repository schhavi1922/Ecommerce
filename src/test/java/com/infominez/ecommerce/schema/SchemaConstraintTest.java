package com.infominez.ecommerce.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SchemaConstraintTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired JdbcTemplate jdbc;

    @Test void inspectActualPostgreSQLConstraints() {
        List<Map<String, Object>> constraints = jdbc.queryForList("""
            SELECT tc.table_name, tc.constraint_name, tc.constraint_type,
                   kcu.column_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            WHERE tc.table_schema = 'public'
                AND tc.constraint_type = 'UNIQUE'
            ORDER BY tc.table_name, tc.constraint_name
            """);
        
        System.out.println("=== ACTUAL UNIQUE CONSTRAINTS IN POSTGRESQL ===");
        for (var c : constraints) {
            System.out.printf("Table: %s, Constraint: %s, Column: %s%n",
                c.get("table_name"), c.get("constraint_name"), c.get("column_name"));
        }
        
        // Check for expected constraints
        List<String> constraintNames = constraints.stream()
            .map(c -> (String) c.get("constraint_name"))
            .toList();
        
        System.out.println("\n=== EXPECTED CONSTRAINTS ===");
        String[] expected = {"uk_cart_product", "uk_coupon_code", "uk_coupon_milestone", "uk_order_cart", "uk_order_idempotency"};
        for (String expectedName : expected) {
            boolean exists = constraintNames.contains(expectedName);
            System.out.printf("%s: %s%n", expectedName, exists ? "PRESENT" : "MISSING");
            assertThat(exists).withFailMessage("Constraint %s is missing from database", expectedName).isTrue();
        }
    }

    @Test void directDuplicateInsertsAreRejectedByDatabase() {
        // Setup minimal test data
        long cartId = jdbc.queryForObject("INSERT INTO carts (status, created_at, updated_at) VALUES ('OPEN', NOW(), NOW()) RETURNING id", Long.class);
        long productId = jdbc.queryForObject("INSERT INTO products (sku, name, unit_price, available_quantity) VALUES ('TEST-SKU', 'Test Product', 10.00, 100) RETURNING id", Long.class);
        
        // Test uk_cart_product: duplicate (cart_id, product_id) should fail
        jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 1)", cartId, productId);
        assertThatThrownBy(() -> 
            jdbc.update("INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 2)", cartId, productId)
        ).isInstanceOf(DataIntegrityViolationException.class);
        
        // Test uk_order_cart: duplicate cart_id should fail
        jdbc.update("INSERT INTO orders (cart_id, idempotency_key, status, created_at, gross_total, discount_amount, final_total) VALUES (?, 'key-1', 'COMPLETED', NOW(), 10.00, 0.00, 10.00)", cartId);
        assertThatThrownBy(() ->
            jdbc.update("INSERT INTO orders (cart_id, idempotency_key, status, created_at, gross_total, discount_amount, final_total) VALUES (?, 'key-2', 'COMPLETED', NOW(), 10.00, 0.00, 10.00)", cartId)
        ).isInstanceOf(DataIntegrityViolationException.class);
        
        // Test uk_order_idempotency: duplicate idempotency_key should fail
        assertThatThrownBy(() ->
            jdbc.update("INSERT INTO orders (cart_id, idempotency_key, status, created_at, gross_total, discount_amount, final_total) VALUES (?, 'key-1', 'COMPLETED', NOW(), 10.00, 0.00, 10.00)", cartId + 1)
        ).isInstanceOf(DataIntegrityViolationException.class);
        
        // Test uk_coupon_code: duplicate code should fail
        jdbc.update("INSERT INTO coupons (code, milestone, discount_percentage, status, created_at) VALUES ('CODE-1', 1, 10.0000, 'AVAILABLE', NOW())");
        assertThatThrownBy(() ->
            jdbc.update("INSERT INTO coupons (code, milestone, discount_percentage, status, created_at) VALUES ('CODE-1', 2, 10.0000, 'AVAILABLE', NOW())")
        ).isInstanceOf(DataIntegrityViolationException.class);
        
        // Test uk_coupon_milestone: duplicate milestone should fail
        assertThatThrownBy(() ->
            jdbc.update("INSERT INTO coupons (code, milestone, discount_percentage, status, created_at) VALUES ('CODE-2', 1, 10.0000, 'AVAILABLE', NOW())")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
