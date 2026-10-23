package com.infominez.ecommerce.coupon;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Administrative operation; authentication is intentionally outside assignment scope. */
@RestController
@RequestMapping("/api/v1/admin/coupons")
public class AdminCouponController {
    private final CouponService service;
    public AdminCouponController(CouponService service) { this.service = service; }
    @PostMapping("/generate") @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse generate() { return service.generate(); }
}
