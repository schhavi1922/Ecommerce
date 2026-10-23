package com.infominez.ecommerce.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infominez.ecommerce.cart.*;
import com.infominez.ecommerce.checkout.*;
import com.infominez.ecommerce.order.*;
import com.infominez.ecommerce.order.dto.OrderResponse;
import com.infominez.ecommerce.product.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"rewards.n=2", "rewards.x=10"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CouponIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired CouponService generator;
    @Autowired CouponRepository coupons;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderReader;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    private Product product;

    @BeforeEach void isolatedFixture() {
        // Only this class's disposable container, never the developer datasource.
        for (String table : List.of("coupons", "order_items", "orders", "cart_items", "carts")) {
            jdbc.update("delete from " + table);
        }
        product = products.saveAndFlush(new Product(UUID.randomUUID().toString(), "Coupon product", new BigDecimal("1.00"), 100));
    }
    private String key() { return UUID.randomUUID().toString(); }
    private long cart(int quantity) {
        long id = carts.create().id();
        carts.add(id, product.getId(), quantity);
        return id;
    }
    private OrderResponse buy(long cart, String key, String code) {
        return checkout.checkout(cart, key, new CheckoutRequest(code));
    }
    private void placeOrders(int count) { for (int i = 0; i < count; i++) buy(cart(1), key(), null); }
    private CouponResponse earned() { placeOrders(2); return generator.generate(); }
    private Coupon stored(String code) { return coupons.findByCode(code).orElseThrow(); }
    private int stock() { return products.findById(product.getId()).orElseThrow().getAvailableQuantity(); }
    private void available(String code) {
        var coupon = stored(code);
        assertThat(coupon.getStatus()).isEqualTo(Coupon.Status.AVAILABLE);
        assertThat(coupon.getRedeemedAt()).isNull();
        assertThat(coupon.getRedeemedOrderId()).isNull();
    }
    private void noEligible() throws Exception {
        mvc.perform(post("/api/v1/admin/coupons/generate")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_ELIGIBLE_MILESTONE"));
    }

    @Test void generatesAtThresholdAndNeverDuplicatesMilestone() throws Exception {
        noEligible();
        placeOrders(1);
        noEligible();
        placeOrders(1);
        mvc.perform(post("/api/v1/admin/coupons/generate")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestone").value(2)).andExpect(jsonPath("$.discountPercentage").value(10))
                .andExpect(jsonPath("$.status").value("AVAILABLE")).andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("[A-Za-z0-9_-]{32}")));
        noEligible();
        assertThat(coupons.count()).isEqualTo(1);
        placeOrders(1);
        noEligible();
    }

    @Test void generatesEarliestUnrewardedMilestoneOnePerRequest() throws Exception {
        placeOrders(5);
        var first = generator.generate();
        var second = generator.generate();
        assertThat(first.milestone()).isEqualTo(2);
        assertThat(second.milestone()).isEqualTo(4);
        assertThat(first.code()).isNotEqualTo(second.code());
        noEligible();
        assertThat(coupons.count()).isEqualTo(2);
    }

    @Test void uncommittedAndFailedOrdersDoNotAdvanceEligibility() throws Exception {
        placeOrders(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                buy(cart(1), key(), null); // This successful inner call is not committed yet.
                try { assertThat(executor.submit(this::generationStatus).get(10, TimeUnit.SECONDS)).isEqualTo(409); }
                catch (Exception ex) { throw new IllegalStateException(ex); }
                status.setRollbackOnly();
            });
        } finally { executor.shutdownNow(); }
        assertThat(orders.countByStatus(PurchaseOrder.Status.COMPLETED)).isEqualTo(1);
        assertThat(coupons.count()).isZero();
        noEligible();
        long empty = carts.create().id();
        mvc.perform(post("/api/v1/carts/" + empty + "/checkout").header("Idempotency-Key", key()))
                .andExpect(status().isBadRequest());
        noEligible();
        placeOrders(1);
        assertThat(generator.generate().milestone()).isEqualTo(2);
    }

    @Test void discountSnapshotsAndNormalizedReplayRemainStable() throws Exception {
        var coupon = earned();
        jdbc.update("update products set unit_price = 0.05 where id = ?", product.getId());
        long cart = cart(3);
        String key = key();
        var response = mvc.perform(post("/api/v1/carts/" + cart + "/checkout").header("Idempotency-Key", key)
                .contentType("application/json").content(mapper.writeValueAsString(new CheckoutRequest("  " + coupon.code() + "  "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grossTotal").value(0.15))
                .andExpect(jsonPath("$.discountAmount").value(0.02)).andExpect(jsonPath("$.finalTotal").value(0.13))
                .andExpect(jsonPath("$.couponCode").value(coupon.code())).andReturn().getResponse();
        var original = mapper.readValue(response.getContentAsString(), OrderResponse.class);
        assertThat(stored(coupon.code()).getStatus()).isEqualTo(Coupon.Status.REDEEMED);
        assertThat(stored(coupon.code()).getRedeemedOrderId()).isEqualTo(original.id());
        assertThat(stored(coupon.code()).getRedeemedAt()).isNotNull();
        int remaining = stock();
        jdbc.update("update products set unit_price = 99.99, name = 'Changed' where id = ?", product.getId());
        assertThat(buy(cart, key, coupon.code())).isEqualTo(original);
        assertThat(buy(cart, key, " " + coupon.code() + " ")).isEqualTo(original);
        assertThat(orderReader.get(original.id())).isEqualTo(original);
        assertThat(stock()).isEqualTo(remaining);
        // Future configuration cannot change the stored percentage or monetary snapshots.
        assertThat(original.discountPercentage()).isEqualByComparingTo("10");
        assertThat(original.items().get(0).lineTotal()).isEqualByComparingTo("0.15");
    }

    @Test void differentCouponOrNoCouponWithSameKeyConflicts() throws Exception {
        placeOrders(4);
        var one = generator.generate();
        var two = generator.generate();
        long cart = cart(1);
        String key = key();
        buy(cart, key, one.code());
        for (CheckoutRequest request : List.of(new CheckoutRequest(two.code()), new CheckoutRequest(null))) {
            mvc.perform(post("/api/v1/carts/" + cart + "/checkout").header("Idempotency-Key", key)
                    .contentType("application/json").content(mapper.writeValueAsString(request)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        }
        available(two.code());
        long plainCart = cart(1);
        String plainKey = key();
        buy(plainCart, plainKey, null);
        mvc.perform(post("/api/v1/carts/" + plainCart + "/checkout").header("Idempotency-Key", plainKey)
                .contentType("application/json").content(mapper.writeValueAsString(new CheckoutRequest(two.code()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        available(two.code());
    }

    @Test void unknownOrRedeemedCouponIsRejectedWithoutMutations() throws Exception {
        var coupon = earned();
        long first = cart(1);
        buy(first, key(), coupon.code());
        long second = cart(1);
        int before = stock();
        mvc.perform(post("/api/v1/carts/" + second + "/checkout").header("Idempotency-Key", key())
                .contentType("application/json").content(mapper.writeValueAsString(new CheckoutRequest("unknown"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_COUPON"));
        mvc.perform(post("/api/v1/carts/" + second + "/checkout").header("Idempotency-Key", key())
                .contentType("application/json").content(mapper.writeValueAsString(new CheckoutRequest(coupon.code()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COUPON_REDEEMED"));
        assertThat(stock()).isEqualTo(before);
        assertThat(carts.get(second).status()).isEqualTo(Cart.Status.OPEN);
        assertThat(jdbc.queryForObject("select count(*) from orders where cart_id = ?", Long.class, second)).isZero();
    }

    @Test void inventoryFailureLeavesCouponAvailableAndRetryCanSucceed() throws Exception {
        var coupon = earned();
        long cart = cart(1);
        int before = stock();
        jdbc.update("update products set available_quantity = 0 where id = ?", product.getId());
        String key = key();
        mvc.perform(post("/api/v1/carts/" + cart + "/checkout").header("Idempotency-Key", key)
                .contentType("application/json").content(mapper.writeValueAsString(new CheckoutRequest(coupon.code()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        available(coupon.code());
        assertThat(carts.get(cart).status()).isEqualTo(Cart.Status.OPEN);
        assertThat(orders.findByIdempotencyKey(key)).isEmpty();
        jdbc.update("update products set available_quantity = ? where id = ?", before, product.getId());
        assertThat(buy(cart, key, coupon.code()).discountAmount()).isEqualByComparingTo("0.10");
    }

    @Test void commitFailureRestoresCouponOrderCartInventoryAndKey() throws Exception {
        var coupon = earned();
        long cart = cart(2);
        int before = stock();
        long ordersBefore = orders.count();
        long itemsBefore = jdbc.queryForObject("select count(*) from order_items", Long.class);
        jdbc.execute("CREATE FUNCTION test_coupon_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.idempotency_key = 'forced-coupon-rollback' THEN RAISE EXCEPTION 'coupon rollback' USING ERRCODE = '23514'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER test_coupon_failure AFTER INSERT ON orders DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION test_coupon_failure()");
        try {
            assertThatThrownBy(() -> buy(cart, "forced-coupon-rollback", coupon.code())).hasStackTraceContaining("coupon rollback");
            available(coupon.code());
            assertThat(stock()).isEqualTo(before);
            assertThat(carts.get(cart).status()).isEqualTo(Cart.Status.OPEN);
            assertThat(orders.count()).isEqualTo(ordersBefore);
            assertThat(jdbc.queryForObject("select count(*) from order_items", Long.class)).isEqualTo(itemsBefore);
            assertThat(orders.findByIdempotencyKey("forced-coupon-rollback")).isEmpty();
            noEligible();
        } finally {
            jdbc.execute("DROP TRIGGER test_coupon_failure ON orders");
            jdbc.execute("DROP FUNCTION test_coupon_failure()");
        }
        assertThat(buy(cart, "forced-coupon-rollback", coupon.code()).discountAmount()).isEqualByComparingTo("0.20");
    }

    private int generationStatus() throws Exception {
        return mvc.perform(post("/api/v1/admin/coupons/generate")).andReturn().getResponse().getStatus();
    }
    private int checkoutStatus(long cart, String key, String code) throws Exception {
        return mvc.perform(post("/api/v1/carts/" + cart + "/checkout").header("Idempotency-Key", key)
                .contentType("application/json").content(mapper.writeValueAsString(new CheckoutRequest(code))))
                .andReturn().getResponse().getStatus();
    }
    private List<Integer> competing(Callable<Integer> one, Callable<Integer> two, Runnable lock, String waitingQuery) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var futures = new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                lock.run();
                futures.add(executor.submit(one));
                futures.add(executor.submit(two));
                // Observe both independent API transactions blocked by an actual PostgreSQL lock.
                await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() ->
                        jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and pid <> pg_backend_pid() and wait_event_type = 'Lock' and query like ?", Long.class, waitingQuery) >= 2);
            });
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test void concurrentGenerationCannotDuplicateOnlyEligibleMilestone() throws Exception {
        placeOrders(2);
        var statuses = competing(this::generationStatus, this::generationStatus,
                () -> jdbc.execute("select pg_advisory_xact_lock(1732050807, 1)"), "%1732050807%");
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(coupons.count()).isEqualTo(1);
        assertThat(coupons.findAll().get(0).getMilestone()).isEqualTo(2);
    }

    @Test void concurrentGenerationCanRewardTwoDistinctEligibleMilestones() throws Exception {
        placeOrders(4);
        var statuses = competing(this::generationStatus, this::generationStatus,
                () -> jdbc.execute("select pg_advisory_xact_lock(1732050807, 1)"), "%1732050807%");
        assertThat(statuses).containsExactly(201, 201);
        assertThat(coupons.findAll()).extracting(Coupon::getMilestone).containsExactlyInAnyOrder(2L, 4L);
    }

    @Test void competingCheckoutsCannotRedeemCouponTwice() throws Exception {
        var coupon = earned();
        long first = cart(1);
        long second = cart(1);
        int before = stock();
        var statuses = competing(() -> checkoutStatus(first, key(), coupon.code()), () -> checkoutStatus(second, key(), coupon.code()),
                () -> coupons.findLockedByCode(coupon.code()).orElseThrow(), "%from coupons%");
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(stock()).isEqualTo(before - 1);
        assertThat(jdbc.queryForObject("select count(*) from orders where cart_id in (?, ?)", Long.class, first, second)).isEqualTo(1);
        var redeemed = stored(coupon.code());
        assertThat(redeemed.getStatus()).isEqualTo(Coupon.Status.REDEEMED);
        assertThat(redeemed.getRedeemedOrderId()).isNotNull();
        assertThat(List.of(carts.get(first).status(), carts.get(second).status()))
                .containsExactlyInAnyOrder(Cart.Status.OPEN, Cart.Status.CHECKED_OUT);
    }

    @Test void concurrentSameKeyCouponReplayCreatesOneDiscountedOrder() throws Exception {
        var coupon = earned();
        long cart = cart(1);
        String key = key();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        int before = stock();
        try {
            Callable<Integer> call = () -> { start.await(); return checkoutStatus(cart, key, coupon.code()); };
            var one = executor.submit(call);
            var two = executor.submit(call);
            start.countDown();
            assertThat(List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS))).containsExactly(200, 200);
        } finally { start.countDown(); executor.shutdownNow(); }
        assertThat(stock()).isEqualTo(before - 1);
        var order = orders.findByIdempotencyKey(key).orElseThrow();
        assertThat(stored(coupon.code()).getRedeemedOrderId()).isEqualTo(order.getId());
        assertThat(jdbc.queryForObject("select count(*) from orders where cart_id = ?", Long.class, cart)).isEqualTo(1);
        assertThat(buy(cart, key, coupon.code()).discountAmount()).isEqualByComparingTo("0.10");
    }
}
