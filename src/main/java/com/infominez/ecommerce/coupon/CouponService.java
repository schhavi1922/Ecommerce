package com.infominez.ecommerce.coupon;

import com.infominez.ecommerce.config.RewardProperties;
import com.infominez.ecommerce.common.exception.BusinessException;
import com.infominez.ecommerce.order.*;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CouponService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RewardProperties settings;
    private final OrderRepository orders;
    private final CouponRepository coupons;
    private final JdbcTemplate jdbc;
    public CouponService(RewardProperties settings, OrderRepository orders, CouponRepository coupons, JdbcTemplate jdbc) {
        this.settings = settings; this.orders = orders; this.coupons = coupons; this.jdbc = jdbc;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CouponResponse generate() {
        // Two-integer advisory-lock namespace is separate from checkout's bigint key locks.
        // Generation takes no cart/product/coupon row locks, so cannot reverse checkout lock order.
        jdbc.execute("select pg_advisory_xact_lock(1732050807, 1)");
        long count = orders.countByStatus(PurchaseOrder.Status.COMPLETED);
        var eligible = jdbc.queryForList("select milestone from generate_series(cast(? as bigint), cast(? as bigint), cast(? as bigint)) as candidates(milestone) "
                + "where not exists (select 1 from coupons c where c.milestone = candidates.milestone) order by milestone limit 1",
                Long.class, settings.n(), count, settings.n());
        if (eligible.isEmpty()) throw new BusinessException(HttpStatus.CONFLICT, "NO_ELIGIBLE_MILESTONE", "No eligible unrewarded order milestone is available");
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return CouponResponse.from(coupons.saveAndFlush(new Coupon(code, eligible.get(0), settings.x())));
    }
}
