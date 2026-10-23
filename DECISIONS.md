# Design decisions

## Invariants and scope

A cart is purchased at most once. A successful idempotency key identifies exactly one cart and normalized coupon request. Stock never becomes negative. Coupon milestones and codes are unique; one coupon can redeem at most once. Inventory, cart state, order snapshots, coupon redemption and idempotency commit or roll back together. Only committed COMPLETED orders advance rewards or contribute to reporting. Historical amounts come from immutable purchase snapshots. Reports do not mutate state and net revenue equals gross revenue minus discounts.

The original repository README contained only a title. Tasks 1–4 supplied the requirements implemented here; any separate assignment rubric still needs developer reconciliation. Alternatives below were assessed in implementation/design review, not asserted to be discussions with an external stakeholder.

## 1. Application architecture and stack

**Context:** Deliver a reliable application within an approximately three-hour assignment budget.

**Options considered:** A modular monolith or distributed services; concrete feature services or additional interface layers.

**Choice:** Java 17, Spring Boot 3.5.7, Maven 3.9.9 wrapper, feature packages, concrete services, thin controllers and DTOs.

**Why:** One process and one PostgreSQL transaction keep correctness and verification manageable. Boot supplies the Jakarta/JPA/test ecosystem.

**Consequences:** No messaging, Redis, frontend or microservices. Feature boundaries remain available for later refactoring without paying distributed transaction costs now.

## 2. Database configuration and evolution

**Context:** Start locally, create missing tables, preserve developer data and avoid committing credentials.

**Options considered:** Hibernate update for initial development or versioned Flyway migrations; unconditional SQL seeds or explicit initialization.

**Choice:** Environment-configured local PostgreSQL only; password has no default. Hibernate ddl-auto=update, no Flyway or SQL initialization. A transactional runner inserts missing stable SKUs without updating existing rows. The developer creates the database once with suitable privileges.

**Why:** This meets the requested development workflow quickly and fails startup on connection errors rather than silently substituting an in-memory database.

**Consequences:** Update is not a production migration plan: renames, backfills, rollback and upgrade history need reviewed migrations and backups. Future Flyway must own schema changes with Hibernate validate. Simultaneous seed runners are not coordinated; single-instance initialization is assumed. Reporting adds no tables or schema changes. Hibernate startup may warn about not recognizing constraints it created via update; these are false positives - PostgreSQL enforces the constraints correctly. Direct inspection confirmed all unique constraints are present (uk_cart_product, uk_coupon_code, uk_coupon_milestone, uk_order_cart, uk_order_idempotency).

## 3. Cart and order history

**Context:** Draft carts must reflect current catalogue information while completed purchases must remain explainable.

**Options considered:** Reserve stock/freeze prices when adding items, or determine both at checkout; live product references or order snapshots for history.

**Choice:** OPEN carts combine duplicate additions into one product item, reject nonpositive/overflow quantities and become CHECKED_OUT once. Cart operations lock the cart row. Carts reserve nothing. Checkout uses locked current prices and persists independent product ID/name/price/quantity/line snapshots and order totals.

**Why:** Avoids abandoned reservations and preserves purchase history despite later catalogue changes.

**Consequences:** Displayed cart prices may change before purchase. No reopen, refunds or cancellation workflow. Snapshots are non-updatable through JPA with no write API; privileged SQL administrators can still change data. Timestamps are truncated to PostgreSQL microseconds for exact replay equality.

## 4. Checkout transaction and inventory control

**Context:** Competing purchases must not oversell or leave partial changes, including when commit fails.

**Options considered:** Optimistic versions and retries, conditional atomic updates, or pessimistic row locks.

**Choice:** One public service-proxy READ COMMITTED transaction. Lock order: idempotency advisory lock, cart row, optional coupon row, then products in ascending ID. Validate every stock before deduction. Save order/items, cart, inventory and redemption in this transaction.

**Why:** PostgreSQL row locks give straightforward correctness across independent connections and application processes. Deterministic product ordering reduces deadlocks.

**Consequences:** Contention blocks callers; lock/constraint conflicts translate to 409 after the transaction exits. No catching a database failure and continuing a rollback-only transaction. Database-backed checkout success stands in for successful payment; real payment integration requires a separate design. Tests force deferred commit failures and competing independent transactions. Separate multi-process deployment has not been verified.

## 5. Request idempotency

**Context:** A lost checkout response must be safely retried, including concurrent retries on different instances.

**Options considered:** Separate pending/result records, unique constraints with conflict retries, or storing the completed result on the order with database serialization.

**Choice:** Global unique order idempotency key (nonblank, at most 128 characters, exact and case-sensitive), unique source cart, and transaction-scoped PostgreSQL advisory lock on the key hash before lookup. Identity is cart ID plus coupon code stripped of surrounding whitespace; absent/blank means no coupon and case is retained.

**Why:** No pending record can orphan; successful result and business changes share a transaction. Full key equality and uniqueness decide identity; hash collisions only serialize unrelated requests.

**Consequences:** Same identity returns the original DTO before rechecking cart/coupon state; changed identity returns 409. Different keys cannot repurchase a cart. Failed purchases consume no key. Draft cart edits are not fingerprinted: the first successful purchase freezes the result. PostgreSQL-specific locks and unbounded key retention are accepted limitations.

## 6. Rewards and coupon redemption

**Context:** Milestones must reflect committed successful purchases and coupons must not be generated or redeemed twice.

**Options considered:** Automatic generation in checkout or explicit administration; all pending rewards per call or earliest one; application counters or persisted order counts.

**Choice:** Validated rewards.n>0 and 0<rewards.x<=100 (up to four fractional digits), defaults 5 and 10. Generation counts persisted COMPLETED orders after a dedicated generation advisory lock and inserts one earliest missing eligible multiple of n. Unique code/milestone constraints back this rule. Codes use 24 SecureRandom bytes (192 bits), URL-safe Base64. Checkout locks one AVAILABLE coupon and changes it to REDEEMED with timestamp/order reference atomically.

**Why:** An explicit request has bounded output, backlog remains recoverable, committed order counts avoid sequence gaps, and row locks prevent double redemption.

**Consequences:** AVAILABLE and REDEEMED both permanently reward a milestone. Global counts include discounted orders. Keep n stable: changing it recomputes multiples against lifetime counts and can reveal historical eligibility; x changes only new coupon snapshots. Generation uses a distinct advisory namespace and takes no checkout row locks. It returns 201 or 409 NO_ELIGIBLE_MILESTONE. Admin retry can generate the next milestone rather than replay. A random-code collision rolls back and requires a fresh request. Coupon expiry, ownership, stacking, listing/recovery and policy versioning are deferred.

## 7. Money and errors

**Context:** Prices, discounts and retries must remain deterministic, and clients need safe actionable failures.

**Options considered:** Floating point or decimal arithmetic; per-line discount rounding or rounding the aggregate; ad hoc responses or centralized advice.

**Choice:** BigDecimal and PostgreSQL NUMERIC(12,2) prices, NUMERIC(30,2) totals, NUMERIC(7,4) percentage snapshots. One assumed two-decimal currency. Integer multiplication/addition are exact. Discount = gross * percentage / 100, rounded once at the complete-discount boundary to scale 2 HALF_UP, bounded to [0,gross]; final=gross-discount. Central advice returns timestamp/status/code/message/path.

**Why:** Decimal snapshots reconcile exactly without floating-point drift; one error envelope is predictable and hides SQL, credentials and stack traces.

**Consequences:** No currency conversion. Item totals stay undiscounted. Invalid input/empty cart/unknown coupon is 400, missing resource 404, stock/cart/key/coupon or persistence conflict 409, unexpected failure generic 500. Strict Jackson numeric handling rejects quoted/fractional quantities. Health uses independent liveness and database-backed readiness (503 on failure).

## 8. Administrative reporting

**Context:** Revenue, quantities and coupon counts must reconcile without joins multiplying totals or concurrent commits mixing observations.

**Options considered:** Materialize entity graphs, one large joined aggregate, separate SQL aggregates, or introduce cached analytics.

**Choice:** Read-only REPEATABLE READ service transaction; separate order, coupon and product aggregates using JdbcTemplate. Only COMPLETED orders count. Gross/discount/net sum their saved order columns; purchased quantities sum their order-item snapshots. All catalogue products appear, including zero sales, plus historical sold IDs absent from the catalogue. Sold names use the latest successful snapshot ordered by creation time/order ID/item ID; unsold names use the catalogue. Results sort by product ID.

**Why:** Separate aggregates avoid fanout; one PostgreSQL snapshot makes all response figures coherent. SQL returns summaries without loading every entity and invokes no business mutation methods.

**Consequences:** Net sums saved final totals and reconciles with summed gross minus discounts under the database arithmetic check. Total coupons counts all persisted coupons; AVAILABLE and REDEEMED count their explicit statuses. Read-only is an application transaction declaration, not a substitute for database permissions. No pagination, date filters, caching, analytics pipeline or reporting schema is added.

## AI-assisted development and correction

This implementation used AI-assisted code generation and review. A concrete correction during Task 2 was rejecting default Jackson coercion: fractional quantities could otherwise truncate to integers and quoted scalars could be accepted. Configuration now disables accept-float-as-int and allow-coercion-of-scalars, with request regression tests. Another correction truncates generated timestamps to PostgreSQL microseconds so initial responses and saved idempotent replay are fully equal. These are observed implementation corrections; no separate Claude interaction or human review is claimed.

## Verification and production evolution

Unit/MVC checks and disposable PostgreSQL Testcontainers tests cover the implemented flows. Test fixture deletion and forced-failure triggers target only isolated containers, never the developer database. Docker-less execution can skip persistence tests: inspect actual suite results. Database locks/constraints support coordination across processes by design, but tests use separate requests/connections/transactions within one JVM, not a multi-instance deployment.

Implemented: catalogue, health, carts, atomic checkout, saved orders, checkout idempotency, rewards/redemption, structured errors and administrative summary. Deferred: authentication/authorization (admin endpoints are unauthenticated), real payments, expiry/ownership/stacking, refund/cancellation accounting, coupon recovery/listing and admin idempotency, production migrations, cart size limits/pagination, idempotency retention, multi-instance seed coordination and deployment verification. Do not expose the unauthenticated assignment service as a production administrative service.

With another two hours, first test upgrades from a populated older schema and introduce a reviewed Flyway baseline/migrations; next verify two application processes against one isolated PostgreSQL instance, including startup seed coordination and lock timeouts. Then inspect query plans/indexes, catalogue/cart bounds, pool sizing, monitoring and the external submission rubric. Production scale would retain database coordination, add controlled migrations and least-privilege access, and measure contention before changing locking or adding infrastructure.

Final Task 4 evidence (2026-10-23): Java 17.0.18 / Maven 3.9.9, required verify command with Docker API 1.44: BUILD SUCCESS, 49 tests, zero failures/errors/skips, including five PostgreSQL reporting tests. Source review required no changes to existing checkout/coupon locking or transaction behavior. No production or multi-process deployment verification is claimed. Exact total development time was not tracked.
