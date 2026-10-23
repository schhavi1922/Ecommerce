package com.infominez.ecommerce.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;
    public ReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SummaryResponse summary() {
        // Aggregate orders independently of items/coupons to prevent join multiplication.
        var totals = jdbc.queryForObject("""
                select count(*) as orders, coalesce(sum(gross_total), 0.00) as gross,
                       coalesce(sum(discount_amount), 0.00) as discounts,
                       coalesce(sum(final_total), 0.00) as net
                from orders where status = 'COMPLETED'
                """, (rs, row) -> new SummaryResponse(java.util.List.of(), rs.getBigDecimal("gross"),
                        rs.getBigDecimal("discounts"), rs.getBigDecimal("net"), 0, 0, 0, rs.getLong("orders")));
        var coupons = jdbc.queryForObject("""
                select count(*) as total, count(*) filter (where status = 'AVAILABLE') as available,
                       count(*) filter (where status = 'REDEEMED') as redeemed from coupons
                """, (rs, row) -> new long[] {rs.getLong("total"), rs.getLong("available"), rs.getLong("redeemed")});
        var products = jdbc.query("""
                with sold as (
                    select i.product_id, i.product_name, i.quantity, o.created_at, o.id as order_id, i.id
                    from order_items i join orders o on o.id = i.order_id where o.status = 'COMPLETED'
                ), quantities as (
                    select product_id, sum(quantity) as quantity from sold group by product_id
                ), names as (
                    select distinct on (product_id) product_id, product_name from sold
                    order by product_id, created_at desc, order_id desc, id desc
                )
                select coalesce(p.id, q.product_id) as product_id,
                       coalesce(n.product_name, p.name) as product_name, coalesce(q.quantity, 0) as quantity
                from products p full outer join quantities q on q.product_id = p.id
                left join names n on n.product_id = coalesce(p.id, q.product_id)
                order by product_id
                """, (rs, row) -> new SummaryResponse.ProductSales(rs.getLong("product_id"),
                        rs.getString("product_name"), rs.getLong("quantity")));
        return new SummaryResponse(products, totals.grossRevenue(), totals.totalDiscounts(), totals.netRevenue(),
                coupons[0], coupons[1], coupons[2], totals.successfulOrders());
    }
}
