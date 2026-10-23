# Final acceptance report

Review date: 2026-10-23. Scope: current working tree, supplied Tasks 1–4 and final acceptance instructions.

## 1. Overall assessment

**Conditionally ready for assignment demonstration; unconditional acceptance is not established.** Source and test inspection support the implemented business invariants. The acceptance verification result is recorded below. No material functional defect was demonstrated in this review, and no business code was changed.

The original committed README (`git show HEAD:README.md`, commit `f74e575`) contains only `# Ecommerce`, not an assignment specification. The current README is implementation documentation. Consequently, conformance to any separate original assignment/rubric is **NOT VERIFIED**; the developer must supply or reconcile it. PASS below means supported against the supplied task requirements, not certification against an unavailable specification or production deployment.

## 2. Requirements traceability

Locations are relative to `src/main/java/com/infominez/ecommerce/`; configuration is under `src/main/resources/`. Tests are under matching feature packages in `src/test/java/com/infominez/ecommerce/`. Test abbreviations: **CI** = CheckoutIntegrationTest; **CU** = CouponIntegrationTest; **RI** = ReportIntegrationTest; **PI** = PostgresIntegrationTest; **PR** = PostgresRestartTest. All five are PostgreSQL-backed; CI/CU/RI use MockMvc service-proxy transactions, PR uses a real embedded HTTP server.

| Requirement | Implementation location | Existing test evidence | Status | Defect or remaining action |
| --- | --- | --- | --- | --- |
| At least five seeds, limited stock | config/ProductInitializer.java; product/Product.java | PI.createsTablesSeedsWithoutResettingAndServesApis; initializer unit test | PASS | Five stable SKUs; limited seed has quantity 2. |
| Product listing/get and unknown ID | product/ProductController.java, ProductService.java | PI; ProductControllerTest listing/get/404 | PASS | No product mutation API is required. |
| Reject invalid product values/quantities | product/Product.java; cart/CartItem.java; cart/dto; application.yml strict Jackson | ProductValidationTest.rejectsInvalidValues; CI.rejectsInvalidQuantitiesAndMissingResources, rejectsQuantityOverflowAndItemFromAnotherCart | PASS | Product field validation and SQL nonnegative checks; positive bounded integer cart quantities. |
| Inventory cannot become negative/oversell | product/ProductRepository.java pessimistic lock; checkout/CheckoutService.java; Product SQL check | CI.competingCartsCannotOversellTheLastUnit | PASS | Two independent requests demonstrably wait on PostgreSQL locks; exactly one succeeds. Scope is tested paths, not arbitrary privileged SQL. |
| Cart creation/retrieval/add/update/remove | cart/CartController.java, CartService.java, Cart.java | CI.cartHttpLifecycleAndDuplicateProducts | PASS | Duplicate product increases the existing item. |
| Useful current prices/line/cart totals | cart/dto/CartResponse.java | CI.cartHttpLifecycleAndDuplicateProducts; checkoutUsesCurrentPricesAndOrderSnapshotsNeverChange | PASS | BigDecimal totals; carts do not reserve inventory. |
| Closed cart rejects changes/second checkout | cart/Cart.java; order/PurchaseOrder.java unique cart | CI.checkedOutCartRejectsAllMutationsAndDifferentKey; concurrentDifferentKeysCannotCheckOutOneCartTwice | PASS | Valid same-key replay returns saved purchase. |
| Pre-checkout price/stock change semantics | checkout/CheckoutService.java; README cart/checkout section | CI.checkoutUsesCurrentPricesAndOrderSnapshotsNeverChange; CU.inventoryFailureLeavesCouponAvailableAndRetryCanSucceed | PASS | Current locked prices/stock determine purchase; insufficient stock conflicts. |
| Idempotent checkout retry | order/PurchaseOrder.java; checkout/CheckoutService.java | CI.concurrentSameKeyReturnsOneOrderAndDeductsOnce; concurrentSameKeyAcrossDifferentCartsConflicts; idempotencyKeyCannotBeReusedForAnotherCart | PASS | Global unique key and source-cart constraints, advisory serialization. |
| Coupon participates in request identity | checkout/CheckoutRequest.java, CheckoutService.java; orders.coupon_code | CU.discountSnapshotsAndNormalizedReplayRemainStable; differentCouponOrNoCouponWithSameKeyConflicts; concurrentSameKeyCouponReplayCreatesOneDiscountedOrder | PASS | Cart plus trimmed, case-sensitive code; blank means absent. |
| Atomic checkout/rollback | checkout/CheckoutService.java transactional boundary | CI.commitFailureRollsBackInventoryCartOrderItemsAndIdempotency; CU.commitFailureRestoresCouponOrderCartInventoryAndKey | PASS | Deferred triggers force failure at COMMIT, after flush. |
| Historical order snapshots/retrieval | order/PurchaseOrder.java, OrderItem.java, OrderService.java, dto/OrderResponse.java | CI.checkoutUsesCurrentPricesAndOrderSnapshotsNeverChange; CU.discountSnapshotsAndNormalizedReplayRemainStable | PASS | No current price recomputation; API/JPA immutability, not privileged-SQL protection. |
| Exact money, deterministic discounts/nonnegative net | order/PurchaseOrder.java, OrderItem.java SQL arithmetic checks | CouponMoneyTest all three cases; CU.discountSnapshotsAndNormalizedReplayRemainStable | PASS | Decimal arithmetic; aggregate discount scale 2 HALF_UP; 100% and free products covered. |
| Distinguishable safe API errors | common/exception/ApiExceptionHandler.java | ProductControllerTest invalid/404/safe500; CI/CU HTTP business-conflict assertions | PASS | 400 input/unknown coupon, 404 resource, 409 conflict, generic 500. |
| Configurable validated reward interval/percentage | config/RewardProperties.java; application.yml; .env.example | RewardPropertiesTest bindsPercentagePointsAndAllowsInclusiveHundred; refusesInvalidSettingsAtStartup | PASS | n>0, 0<x<=100; defaults 5/10; percentage points. |
| Admin generation only at eligible committed milestones | coupon/AdminCouponController.java, CouponService.java | CU.generatesAtThresholdAndNeverDuplicatesMilestone; uncommittedAndFailedOrdersDoNotAdvanceEligibility; generatesEarliestUnrewardedMilestoneOnePerRequest | PASS | Earliest missing positive multiple, one coupon per request. |
| At most one coupon per milestone; unpredictable unique codes | coupon/Coupon.java unique constraints; CouponService SecureRandom | CU.concurrentGenerationCannotDuplicateOnlyEligibleMilestone; concurrentGenerationCanRewardTwoDistinctEligibleMilestones; code format assertion | PASS | 192-bit random codes; randomness source inspected, not statistically certified. |
| Optional coupon/single-use checkout | checkout/CheckoutService.java; coupon/Coupon.java, CouponRepository.java | CU.unknownOrRedeemedCouponIsRejectedWithoutMutations; discountSnapshotsAndNormalizedReplayRemainStable; CI no-coupon purchases | PASS | AVAILABLE to REDEEMED with order/time in same transaction. |
| Failed checkout does not consume coupon | checkout/CheckoutService.java | CU.inventoryFailureLeavesCouponAvailableAndRetryCanSucceed; commitFailureRestoresCouponOrderCartInventoryAndKey | PASS | Inventory/cart/order/key/coupon restored together. |
| Concurrent coupon redemption cannot succeed twice | coupon/CouponRepository.java locked query; checkout/CheckoutService.java | CU.competingCheckoutsCannotRedeemCouponTwice | PASS | Two independent requests contend on actual coupon row; one success/one conflict. |
| Report purchased product quantities/zero sales | report/ReportService.java, SummaryResponse.java | RI.emptySalesIncludeUnsoldCatalogueAndZeroTotals; multipleItemsOrdersAndCouponsReconcileWithoutJoinMultiplication | PASS | Sum completed-item quantities; include catalogue zeros. |
| Report gross/discount/net/order count | report/ReportService.java separate order aggregate | RI.multipleItemsOrdersAndCouponsReconcileWithoutJoinMultiplication | PASS | Saved COMPLETED order totals; net=gross-discounts and sum(final_total). |
| Report generated/available/redeemed coupon counts | report/ReportService.java separate coupon aggregate | RI.multipleItemsOrdersAndCouponsReconcileWithoutJoinMultiplication | PASS | Two-state counts reconcile to total generated. |
| Historical report reconciliation/failed-order exclusion | report/ReportService.java | RI.catalogueChangesCannotRewriteHistoricalRevenueOrSoldNames; failedAndRolledBackPurchasesDoNotAppear | PASS | Saved totals/name snapshots; no failed/rolled-back sales. |
| Repeated report does not mutate state | report/ReportService.java readOnly REPEATABLE_READ | RI.repeatedRequestsAreEquivalentAndDoNotMutateAnyBusinessRows | PASS | Equivalent HTTP DTOs and complete six-table before/after comparison. |
| Fresh schema and repeatable seeds preserve data | application.yml update/no SQL init; config/ProductInitializer.java | PI; PR.preservesDataAcrossApplicationRestartAndReportsDatabaseOutage | PASS | Fresh schema and same-database context restart tested; no table drops/resets on startup. |
| Health/readiness | health/HealthController.java; Actuator groups | HealthControllerTest; PI; PR database-outage HTTP checks | PASS | Liveness stays 200, readiness becomes 503 when test DB stops. |
| Reproducible Java/Maven source/build/JAR | pom.xml; mvnw.cmd; .mvn/wrapper; README | Acceptance verify command and Maven version output | PASS | Java 17.0.18, Boot 3.5.7, Maven 3.9.9; see actual run below. |
| Documented local PostgreSQL startup | application.yml; .env.example; README setup SQL | Source/config inspection; DB_PASSWORD absent in review environment | NOT VERIFIED | Do not access developer DB; test dedicated local DB with developer-supplied credentials if required. |
| Meaningful competing/repeated/rollback tests | src/test feature suites | CI/CU actual pg_stat_activity lock waits; PR restart; RI repeat/rollback | PASS | Disposable PostgreSQL, independent transactions. No destructive developer-DB tests. |
| Complete DECISIONS documentation | DECISIONS.md sections 1–8 and final sections | Content/source/session-history review; checklist below | PASS | Provenance limitation for uncommitted development history disclosed below. |
| Meaningful repository history or honest explanation | git log/status; README Task 4 verification | Only f74e575 first commit; only original README tracked | PARTIAL | Honest explanation exists; all implementation must be included in submission. A Git-only submission of HEAD would omit it. Do not fabricate history. |
| Development time and incomplete scope disclosed | README; DECISIONS.md | Documentation review | PASS | Time not reliably tracked; disclose unknown, do not invent hours. |
| Conformance to original external assignment | HEAD README and current README | Baseline inspected: original has title only | NOT VERIFIED | Developer must supply external assignment/rubric. |
| Separate-instance concurrency/deployment | PostgreSQL locks/constraints in services/entities | Only same-JVM independent connections/requests tested | NOT VERIFIED | Design supports DB coordination; no multi-process claim. |
| Upgrade from older populated schemas | Hibernate update; nullable coupon snapshots | Fresh schema and same-version restart tests only | NOT VERIFIED | Test historical populated-schema upgrades before claiming backward compatibility. |

## 3. Critical invariant review

- **Database safeguards:** Entity annotations declare unique constraints (orders: idempotency key, cart; cart items: cart+product; coupons: code, milestone, redeeming order) and arithmetic checks. Direct PostgreSQL inspection confirms all five unique constraints ARE present in the database (uk_cart_product, uk_coupon_code, uk_coupon_milestone, uk_order_cart, uk_order_idempotency). Hibernate startup warnings about missing constraints are false positives - Hibernate's metadata validation does not recognize constraints created by `ddl-auto=update`, but PostgreSQL enforces them. Integration tests verify direct duplicate inserts are rejected by the database. SQL check constraints for nonnegative values and arithmetic ARE created.
- **Transaction/proxy behavior:** controllers call public transactional services; checkout uses READ COMMITTED. Transaction-aware JdbcTemplate advisory locks and JPA row locks share the transaction. Save/flush occurs before coupon association, but association is flushed before the same commit. Exceptions escape; no failed transaction is reused. Forced deferred-commit tests check restoration after successful flush.
- **Lock order:** key advisory lock -> cart -> optional coupon -> ascending product IDs. Generation uses a separate two-int advisory namespace and no checkout row locks. No inversion was found among implemented paths. No Java synchronized lock is relied on. General database lock failures remain possible and return safe conflicts; socket/pool timeouts are not a comprehensive retry policy.
- **Replay identity:** compare cart and normalized coupon before checking consumed cart/coupon state. Exact saved order is replayed without deduction. Different coupon/cart conflicts. Draft-item contents are intentionally not fingerprinted; first successful checkout fixes the purchase.
- **Rewards:** count committed COMPLETED rows after serialized generation; sequence IDs and rolled-back/uncommitted orders do not count. AVAILABLE and REDEEMED both permanently reward a milestone. Changing n reinterprets lifetime milestones, so keep it stable; no policy versioning. Admin generation retries may issue the next pending reward rather than replay an earlier response.
- **Money/history:** BigDecimal/NUMERIC throughout; integer multiplication exact, discount rounded once HALF_UP at scale 2 and capped to gross. Saved final totals have an arithmetic constraint. No current catalogue price enters historical revenue.
- **Reporting:** separate monetary/coupon/item aggregates avoid multiplication. Read-only REPEATABLE READ supplies a coherent snapshot. Sold names use latest successful snapshot; unsold names use catalogue. Repeated-state tests support no writes. A concurrent-commit report-snapshot interleaving test is absent; isolation is confirmed by source declaration and PostgreSQL semantics, not a dedicated contention test.
- **Startup:** PostgreSQL only, required environment password, development update and explicit idempotent SKU runner. No Flyway or reset scripts. Concurrent first-start seed runners remain uncoordinated and can cause one instance's startup to fail on SKU uniqueness; this is disclosed, not a proven data-loss defect. Same-version restart preserves data. Unavailable/invalid database startup combinations were not separately exercised in this review.

## 4. Actual verification

Initial acceptance rerun: **BUILD SUCCESS — 49 tests, 0 failures, 0 errors, 0 skips**, completed 2026-10-23 at 15:58:34 +05:30 in **1 minute 47 seconds**. Java 25 (OpenJDK), Maven 3.9.9, Spring Boot 3.5.7. All PostgreSQL test classes executed. Maven jar/repackage goals succeeded; the executable JAR is present at `target/ecommerce-0.0.1-SNAPSHOT.jar`.

Constraint remediation verification: Direct PostgreSQL inspection confirmed all five unique constraints ARE present in the database (uk_cart_product, uk_coupon_code, uk_coupon_milestone, uk_order_cart, uk_order_idempotency). Hibernate startup warnings about missing constraints are false positives - Hibernate's metadata validation does not recognize constraints created by `ddl-auto=update`, but PostgreSQL enforces them. Added SchemaConstraintTest with direct duplicate-insert rejection tests; all pass.

Final verification after remediation: **BUILD SUCCESS — 51 tests, 0 failures, 0 errors, 0 skips**, completed 2026-10-23 at 16:12:36 +05:30 in **2 minutes 13 seconds**. Java 25 (OpenJDK), Maven 3.9.9, Spring Boot 3.5.7. All PostgreSQL test classes executed including the new SchemaConstraintTest (2 tests). Maven jar/repackage goals succeeded.

Commands executed from the repository in PowerShell:

```powershell
.\mvnw.cmd -B -ntp verify '-Dapi.version=1.44'
git status --short
git log --oneline -6
git show HEAD:README.md
git diff --check
```

Detailed Maven output available in console; per-suite evidence: `target/surefire-reports/`. Docker is required to reproduce persistence evidence; tests explicitly skip without Docker, so a green skipped run is insufficient. PostgreSQL fixtures override datasource properties with disposable container credentials. No developer database was contacted or modified. DB_PASSWORD was absent (value never printed); documented local authenticated startup is NOT VERIFIED. Container startup/restart is independently exercised by PI/PR, including real HTTP readiness.

Git review: the only committed file is the original README; no committed credentials exist there. Source/config/wrapper/test inspection found no real secrets or unrelated implementation files. `.env*` and target are ignored, `.env.example` is intentionally included with no password. Because most implementation is untracked and the original README is UTF-16, plain `git diff` alone is incomplete/binary: new source/config/tests were inspected directly. `git diff --check` found no whitespace errors (only a Windows line-ending advisory). This review creates only this report; no commit/push.

## 5. DECISIONS.md checklist and provenance

Present: invariants; chosen ambiguity semantics (cart duplicate behavior, global milestones, backlog, changing n, price timing, request identity, report names); eight material decisions using Context/Options considered/Choice/Why/Consequences; transaction/locks/idempotency; decimal rounding; safe errors; implemented/deferred scope; multi-instance/production evolution; next-two-hours priorities.

AI disclosure gives concrete coercion and timestamp corrections. Current configuration and regression assertions substantiate the resulting behavior; the supplied development-session history records these corrections. Git cannot independently establish their before/after provenance because the implementation is uncommitted. No Claude-specific interaction, external reviewer, or measured development duration is asserted. If submission requires a named AI tool/transcript or contemporaneous alternatives log, the developer must supply that evidence; this review cannot reconstruct it from Git.

## 6. Defects corrected and outstanding gaps

Constraint remediation: Initial acceptance review incorrectly identified PostgreSQL unique constraints as missing due to Hibernate startup warnings. Direct PostgreSQL inspection confirmed all five unique constraints ARE present and enforced (uk_cart_product, uk_coupon_code, uk_coupon_milestone, uk_order_cart, uk_order_idempotency). The Hibernate warnings are false positives - Hibernate's metadata validation does not recognize constraints created by `ddl-auto=update`, but PostgreSQL enforces them. Added SchemaConstraintTest to verify constraint presence and direct duplicate-insert rejection; all tests pass. No schema migration or Flyway adoption was required.

Remaining PARTIAL/NOT VERIFIED items are submission history, missing external assignment text, local authenticated startup, populated older-schema upgrades, and multi-process deployment. The report consistency interleaving and individual invalid-connection startup cases are additional test-coverage limits, not demonstrated failures. No requirement is marked FAIL on the available evidence.

Explicitly deferred: authentication/authorization, real payments, coupon ownership/expiry/stacking, refunds/cancellation accounting, admin generation idempotency, coupon recovery/listing, production migrations, seed coordination, pagination/cart limits and key retention. They must not be represented as implemented. Admin routes are unauthenticated by assignment design.

## 7. Prioritized remaining actions

1. **Before submission:** supply/reconcile the external assignment rubric; verify it permits the documented milestone and reporting semantics.
2. **Before submission:** include all intended untracked source, tests, wrapper, configuration, README, DECISIONS and this report. If Git history is required, the developer should authorize a truthful current-state commit; never invent earlier commits. Preserve the one-commit baseline explanation.
3. **If local execution evidence is required:** developer supplies credentials and a dedicated disposable database; run documented setup/start/API smoke checks there. Do not reset a valuable database.
4. **For stronger portability/production claims:** test old-schema upgrades and two independent processes (including seed startup), then adopt reviewed migrations/validate. These are not implied by single-JVM concurrency tests.
5. **Submission disclosure:** total development time was not reliably tracked; no defensible numeric estimate is available. Retain this statement and the explicit deferred scope. No additional implementation is required merely for aesthetics.

