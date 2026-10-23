package com.infominez.ecommerce.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infominez.ecommerce.cart.*;
import com.infominez.ecommerce.cart.dto.CartResponse;
import com.infominez.ecommerce.common.exception.BusinessException;
import com.infominez.ecommerce.order.*;
import com.infominez.ecommerce.order.dto.OrderResponse;
import com.infominez.ecommerce.product.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CheckoutIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderService orders;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;

    private String key() { return UUID.randomUUID().toString(); }
    private Product product(String price, int stock) {
        return products.saveAndFlush(new Product(key(), "Original product", new BigDecimal(price), stock));
    }
    private long cart(Product product, int quantity) {
        long id = carts.create().id();
        carts.add(id, product.getId(), quantity);
        return id;
    }
    private OrderResponse buy(long cart, String key) { return checkout.checkout(cart, key, null); }
    private void conflict(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getCode()).isEqualTo(code));
    }
    private int stock(Product product) { return products.findById(product.getId()).orElseThrow().getAvailableQuantity(); }
    private long orderCount(long cart) { return jdbc.queryForObject("select count(*) from orders where cart_id = ?", Long.class, cart); }

    @Test void cartHttpLifecycleAndDuplicateProducts() throws Exception {
        var product = product("12.30", 20);
        var created = mvc.perform(post("/api/v1/carts")).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String path = "/api/v1/carts/" + id;
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(post(path + "/items").contentType("application/json")
                .content("{\"productId\":" + product.getId() + ",\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(24.60));
        var added = carts.add(id, product.getId(), 1);
        assertThat(added.items()).hasSize(1);
        assertThat(added.items().get(0).quantity()).isEqualTo(3);
        long item = added.items().get(0).id();
        mvc.perform(put(path + "/items/" + item).contentType("application/json").content("{\"quantity\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(49.20));
        mvc.perform(delete(path + "/items/" + item)).andExpect(status().isNoContent());
        assertThat(carts.get(id).items()).isEmpty();
        assertThat(stock(product)).isEqualTo(20);
    }

    @Test void rejectsInvalidQuantitiesAndMissingResources() throws Exception {
        long id = carts.create().id();
        for (String quantity : List.of("0", "-1", "null", "2147483648", "1.5", "1e1", "\"2\"")) {
            mvc.perform(post("/api/v1/carts/" + id + "/items").contentType("application/json")
                    .content("{\"productId\":1,\"quantity\":" + quantity + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/carts/" + id + "/items").contentType("application/json")
                .content("{\"productId\":9223372036854775807,\"quantity\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/carts/9223372036854775807")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/9223372036854775807")).andExpect(status().isNotFound());
        assertThat(carts.get(id).items()).isEmpty();
    }

    @Test void rejectsQuantityOverflowAndItemFromAnotherCart() throws Exception {
        var product = product("1.00", 1);
        long first = cart(product, Integer.MAX_VALUE);
        conflict(() -> carts.add(first, product.getId(), 1), "INVALID_QUANTITY");
        long second = cart(product, 1);
        long foreignItem = carts.get(second).items().get(0).id();
        mvc.perform(delete("/api/v1/carts/" + first + "/items/" + foreignItem)).andExpect(status().isNotFound());
        assertThat(carts.get(first).items().get(0).quantity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test void checkoutUsesCurrentPricesAndOrderSnapshotsNeverChange() throws Exception {
        var a = product("1.10", 10);
        var b = product("2.25", 7);
        long id = cart(a, 3);
        carts.add(id, b.getId(), 2);
        jdbc.update("update products set unit_price = 1.20 where id = ?", a.getId());
        assertThat(carts.get(id).total()).isEqualByComparingTo("8.10");
        String key = key();
        var result = mvc.perform(post("/api/v1/carts/" + id + "/checkout").header("Idempotency-Key", key)
                .contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grossTotal").value(8.10))
                .andExpect(jsonPath("$.discountAmount").value(0)).andExpect(jsonPath("$.finalTotal").value(8.10))
                .andReturn();
        long orderId = mapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(stock(a)).isEqualTo(7);
        assertThat(stock(b)).isEqualTo(5);
        assertThat(carts.get(id).status()).isEqualTo(Cart.Status.CHECKED_OUT);
        jdbc.update("update products set unit_price = 99.99, name = 'Changed product' where id = ?", a.getId());
        var saved = orders.get(orderId);
        assertThat(saved.grossTotal()).isEqualByComparingTo("8.10");
        assertThat(saved.items().get(0).unitPrice()).isEqualByComparingTo("1.20");
        assertThat(saved.items().get(0).productName()).isEqualTo("Original product");
        mvc.perform(get("/api/v1/orders/" + orderId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lineTotal").value(3.60));
        assertThat(buy(id, key)).isEqualTo(saved);
        assertThat(stock(a)).isEqualTo(7);
    }

    @Test void checkedOutCartRejectsAllMutationsAndDifferentKey() throws Exception {
        var product = product("3.00", 10);
        long id = cart(product, 2);
        long item = carts.get(id).items().get(0).id();
        buy(id, key());
        conflict(() -> carts.add(id, product.getId(), 1), "CART_CHECKED_OUT");
        conflict(() -> carts.update(id, item, 3), "CART_CHECKED_OUT");
        conflict(() -> carts.remove(id, item), "CART_CHECKED_OUT");
        mvc.perform(post("/api/v1/carts/" + id + "/checkout").header("Idempotency-Key", key()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_CHECKED_OUT"));
        assertThat(stock(product)).isEqualTo(8);
        assertThat(orderCount(id)).isEqualTo(1);
    }

    @Test void idempotencyKeyCannotBeReusedForAnotherCart() throws Exception {
        var product = product("5.00", 5);
        long first = cart(product, 1);
        long second = cart(product, 1);
        String key = key();
        var original = buy(first, key);
        assertThat(buy(first, key)).isEqualTo(original);
        mvc.perform(post("/api/v1/carts/" + second + "/checkout").header("Idempotency-Key", key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(carts.get(second).status()).isEqualTo(Cart.Status.OPEN);
        assertThat(orderCount(second)).isZero();
        assertThat(stock(product)).isEqualTo(4);
    }

    @Test void rejectsEmptyCartAndInvalidKeys() throws Exception {
        long id = carts.create().id();
        String path = "/api/v1/carts/" + id + "/checkout";
        mvc.perform(post(path)).andExpect(status().isBadRequest());
        for (String key : List.of(" ", "x".repeat(129))) {
            mvc.perform(post(path).header("Idempotency-Key", key)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
        }
        String retryKey = key();
        mvc.perform(post(path).header("Idempotency-Key", retryKey)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"));
        var product = product("1.00", 1);
        carts.add(id, product.getId(), 1);
        assertThat(buy(id, retryKey).cartId()).isEqualTo(id);
        assertThat(checkout.checkout(id, retryKey, new CheckoutRequest("")).cartId()).isEqualTo(id);
        assertThat(checkout.checkout(id, retryKey, new CheckoutRequest(" ")).cartId()).isEqualTo(id);
    }

    @Test void insufficientInventoryLeavesAllProductsAndCartUnchanged() throws Exception {
        var a = product("1.00", 4);
        var b = product("1.00", 0);
        long id = cart(a, 2);
        carts.add(id, b.getId(), 1);
        String key = key();
        mvc.perform(post("/api/v1/carts/" + id + "/checkout").header("Idempotency-Key", key))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(stock(a)).isEqualTo(4);
        assertThat(stock(b)).isZero();
        assertThat(carts.get(id).status()).isEqualTo(Cart.Status.OPEN);
        assertThat(orderCount(id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders where idempotency_key = ?", Long.class, key)).isZero();
    }

    @Test void commitFailureRollsBackInventoryCartOrderItemsAndIdempotency() {
        var product = product("2.00", 5);
        long id = cart(product, 2);
        String key = "force-rollback";
        // Deferred trigger fails at COMMIT, after JPA has flushed deductions and all inserts.
        jdbc.execute("CREATE FUNCTION test_checkout_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.idempotency_key = 'force-rollback' THEN RAISE EXCEPTION 'test rollback' USING ERRCODE = '23514'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER test_checkout_failure AFTER INSERT ON orders DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_checkout_failure()");
        try {
            assertThatThrownBy(() -> buy(id, key)).hasStackTraceContaining("test rollback");
            assertThat(stock(product)).isEqualTo(5);
            assertThat(carts.get(id).status()).isEqualTo(Cart.Status.OPEN);
            assertThat(orderCount(id)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from orders where idempotency_key = ?", Long.class, key)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from order_items oi left join orders o on oi.order_id = o.id where o.id is null", Long.class)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER test_checkout_failure ON orders");
            jdbc.execute("DROP FUNCTION test_checkout_failure()");
        }
        assertThat(buy(id, key).cartId()).isEqualTo(id);
        assertThat(stock(product)).isEqualTo(3);
    }

    private record Attempt(OrderResponse order, String conflict) {}
    private List<Attempt> compete(long first, String firstKey, long second, String secondKey) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var one = executor.submit(() -> attempt(first, firstKey, ready, start));
            var two = executor.submit(() -> attempt(second, secondKey, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
    private Attempt attempt(long cart, String key, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
        var response = mvc.perform(post("/api/v1/carts/" + cart + "/checkout")
                .header("Idempotency-Key", key)).andReturn().getResponse();
        if (response.getStatus() == 200) {
            return new Attempt(mapper.readValue(response.getContentAsString(), OrderResponse.class), null);
        }
        assertThat(response.getStatus()).isEqualTo(409);
        return new Attempt(null, mapper.readTree(response.getContentAsString()).get("code").asText());
    }

    @Test void competingCartsCannotOversellTheLastUnit() throws Exception {
        var product = product("1.00", 1);
        long first = cart(product, 1);
        long second = cart(product, 1);
        // Hold the product row until BOTH independent checkout transactions demonstrably
        // wait on PostgreSQL locks; then release them to compete for the last unit.
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var futures = new java.util.ArrayList<Future<Attempt>>();
        List<Attempt> results;
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                products.findLockedById(product.getId()).orElseThrow();
                futures.add(executor.submit(() -> attempt(first, key(), ready, start)));
                futures.add(executor.submit(() -> attempt(second, key(), ready, start)));
                start.countDown();
                await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() -> {
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    return jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid() and wait_event_type = 'Lock' and query like '%products%'", Long.class) >= 2;
                });
            });
            results = List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(results.stream().filter(r -> r.order() != null)).hasSize(1);
        assertThat(results.stream().filter(r -> r.order() == null).map(Attempt::conflict)).containsExactly("INSUFFICIENT_INVENTORY");
        assertThat(orderCount(first) + orderCount(second)).isEqualTo(1);
        assertThat(stock(product)).isZero();
        assertThat(List.of(carts.get(first).status(), carts.get(second).status()))
                .containsExactlyInAnyOrder(Cart.Status.OPEN, Cart.Status.CHECKED_OUT);
    }

    @Test void concurrentSameKeyReturnsOneOrderAndDeductsOnce() throws Exception {
        var product = product("1.00", 3);
        long id = cart(product, 1);
        String key = key();
        var results = compete(id, key, id, key);
        assertThat(results).allSatisfy(r -> assertThat(r.order()).isNotNull());
        assertThat(results.get(0).order()).isEqualTo(results.get(1).order());
        assertThat(orderCount(id)).isEqualTo(1);
        assertThat(stock(product)).isEqualTo(2);
    }

    @Test void concurrentDifferentKeysCannotCheckOutOneCartTwice() throws Exception {
        var product = product("1.00", 3);
        long id = cart(product, 1);
        var results = compete(id, key(), id, key());
        assertThat(results.stream().filter(r -> r.order() != null)).hasSize(1);
        assertThat(results.stream().filter(r -> r.order() == null).map(Attempt::conflict)).containsExactly("CART_CHECKED_OUT");
        assertThat(orderCount(id)).isEqualTo(1);
        assertThat(stock(product)).isEqualTo(2);
    }

    @Test void concurrentSameKeyAcrossDifferentCartsConflicts() throws Exception {
        var product = product("1.00", 3);
        long first = cart(product, 1);
        long second = cart(product, 1);
        String key = key();
        var results = compete(first, key, second, key);
        assertThat(results.stream().filter(r -> r.order() != null)).hasSize(1);
        assertThat(results.stream().filter(r -> r.order() == null).map(Attempt::conflict)).containsExactly("IDEMPOTENCY_CONFLICT");
        assertThat(orderCount(first) + orderCount(second)).isEqualTo(1);
        assertThat(stock(product)).isEqualTo(2);
    }
}
