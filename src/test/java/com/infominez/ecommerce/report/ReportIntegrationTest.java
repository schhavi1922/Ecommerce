package com.infominez.ecommerce.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infominez.ecommerce.cart.CartService;
import com.infominez.ecommerce.checkout.*;
import com.infominez.ecommerce.coupon.CouponService;
import com.infominez.ecommerce.product.*;
import java.math.BigDecimal;
import java.util.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"rewards.n=1", "rewards.x=10"})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReportIntegrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired CouponService coupons;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    Product first;
    Product second;

    @BeforeEach void fixture() {
        // Cleanup only the isolated Testcontainers database, never a developer database.
        for (String table : List.of("coupons", "order_items", "orders", "cart_items", "carts", "products"))
            jdbc.update("delete from " + table);
        first = products.saveAndFlush(new Product("REPORT-A", "First", new BigDecimal("10.00"), 100));
        second = products.saveAndFlush(new Product("REPORT-B", "Second", new BigDecimal("2.50"), 100));
    }
    long cart(int a, int b) {
        long id = carts.create().id();
        if (a > 0) carts.add(id, first.getId(), a);
        if (b > 0) carts.add(id, second.getId(), b);
        return id;
    }
    void buy(int a, int b, String code) {
        checkout.checkout(cart(a, b), UUID.randomUUID().toString(), new CheckoutRequest(code));
    }
    SummaryResponse report() throws Exception {
        var response = mvc.perform(get("/api/v1/admin/reports/summary"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse();
        return mapper.readValue(response.getContentAsString(), SummaryResponse.class);
    }
    @Test void emptySalesIncludeUnsoldCatalogueAndZeroTotals() throws Exception {
        var r = report();
        assertThat(r.successfulOrders()).isZero();
        assertThat(r.grossRevenue()).isEqualByComparingTo("0.00");
        assertThat(r.totalDiscounts()).isEqualByComparingTo("0.00");
        assertThat(r.netRevenue()).isEqualByComparingTo("0.00");
        assertThat(r.totalCouponsGenerated()).isZero();
        assertThat(r.availableCoupons()).isZero();
        assertThat(r.redeemedCoupons()).isZero();
        assertThat(r.products()).containsExactly(
                new SummaryResponse.ProductSales(first.getId(), "First", 0),
                new SummaryResponse.ProductSales(second.getId(), "Second", 0));
    }
    @Test void multipleItemsOrdersAndCouponsReconcileWithoutJoinMultiplication() throws Exception {
        buy(2, 3, null); // 27.50
        var coupon = coupons.generate();
        buy(1, 2, coupon.code()); // 15.00, discount 1.50
        coupons.generate(); // milestone 2 remains AVAILABLE
        var r = report();
        assertThat(r.successfulOrders()).isEqualTo(2);
        assertThat(r.grossRevenue()).isEqualByComparingTo("42.50");
        assertThat(r.totalDiscounts()).isEqualByComparingTo("1.50");
        assertThat(r.netRevenue()).isEqualByComparingTo("41.00");
        assertThat(r.netRevenue()).isEqualByComparingTo(r.grossRevenue().subtract(r.totalDiscounts()));
        assertThat(r.netRevenue()).isEqualByComparingTo(jdbc.queryForObject("select sum(final_total) from orders", BigDecimal.class));
        assertThat(r.totalCouponsGenerated()).isEqualTo(2);
        assertThat(r.availableCoupons()).isEqualTo(1);
        assertThat(r.redeemedCoupons()).isEqualTo(1);
        assertThat(r.products()).extracting(SummaryResponse.ProductSales::purchasedQuantity).containsExactly(3L, 5L);
    }
    @Test void catalogueChangesCannotRewriteHistoricalRevenueOrSoldNames() throws Exception {
        buy(2, 0, null);
        var before = report();
        jdbc.update("update products set unit_price=99.99, name='Renamed' where id=?", first.getId());
        assertThat(report()).isEqualTo(before);
    }
    @Test void failedAndRolledBackPurchasesDoNotAppear() throws Exception {
        buy(1, 0, null);
        var before = report();
        assertThatThrownBy(() -> buy(101, 1, null)).hasMessageContaining("Insufficient inventory");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            buy(2, 2, null);
            status.setRollbackOnly();
        });
        assertThat(report()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select available_quantity from products where id=?", Integer.class, first.getId())).isEqualTo(99);
    }
    @Test void repeatedRequestsAreEquivalentAndDoNotMutateAnyBusinessRows() throws Exception {
        buy(1, 1, null);
        var coupon = coupons.generate();
        buy(2, 1, coupon.code());
        coupons.generate();
        var before = state();
        var r = report();
        assertThat(report()).isEqualTo(r);
        assertThat(report()).isEqualTo(r);
        assertThat(state()).isEqualTo(before);
    }
    private Map<String, List<Map<String, Object>>> state() {
        var state = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("products", "carts", "cart_items", "orders", "order_items", "coupons"))
            state.put(table, jdbc.queryForList("select * from " + table + " order by id"));
        return state;
    }
}
