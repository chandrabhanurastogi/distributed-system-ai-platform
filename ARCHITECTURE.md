# ARCHITECTURE.md

This document reflects the **current, actual state** of the system, verified against the
repository — not the aspirational end state. It is updated every time a milestone changes
a service boundary, API, database, topic, or consistency guarantee (see `CLAUDE.md` Rule 7).

Last verified against repo: 2026-09-14, working tree (Milestone 0.4 changes staged,
pending commit). Phase 0 is complete as of this milestone.

---

## 1. Current State (AS-IS)

### Build

- Gradle 9.7.1, wrapper-committed, root project name `distributed-platform`.
- Java 25 (LTS) pinned via a Gradle toolchain block, resolved automatically on any
  machine via the `foojay-resolver-convention` plugin (no manual JDK install required).
- Spring Boot 4.1.1 / Spring Dependency Management plugin 1.1.7, applied once in the root
  `build.gradle`'s `subprojects {}` block so per-module `build.gradle` files stay minimal.

### Services

| Service | Package | State |
|---|---|---|
| `order-service` | `com.distributedplatform.orderservice` | `POST /orders`, `GET /orders/{id}` (404 on miss). Controller → Service → Repository. Persists `Order` (plain class, not a JPA entity — ADR-0004) via `OrderRepository` (`NamedParameterJdbcTemplate`). |
| `inventory-service` | `com.distributedplatform.inventoryservice` | `GET /inventory/{sku}` (404 on miss), `POST /inventory/{sku}/reserve` (200, 409 on insufficient stock, 400 on invalid quantity). Same layering; `InventoryService.reserve` is the first `@Transactional` method in the codebase — see Data section below for a known, deliberate limitation of that boundary. |

Both currently ship `spring-boot-starter-web`, `spring-boot-starter-actuator`,
`spring-boot-starter-jdbc`, `spring-boot-starter-flyway`, `flyway-database-postgresql`,
`org.postgresql:postgresql` (runtime), `spring-kafka`, and Lombok, but **Kafka is still
only a dependency — nothing uses it yet.** See `CLAUDE.md` → Known Existing Debt.

### Data

Database-per-service is wired *and* now actually holds data. Each service has its own
`postgres:17-alpine` container (`order-db`, `inventory-db` — ADR-0003) and its own
`DataSource` bean via a `local` Spring profile (connection details in
`application-local.yml`, kept out of the unqualified `application.yml` specifically so
Testcontainers can supply a different URL without touching it — exercised for real in
Milestone 0.3).

Schema is versioned with **Flyway** (`spring-boot-starter-flyway` +
`flyway-database-postgresql` — Boot 4 moved Flyway's Spring wiring into its own module,
and Flyway 10+ moved Postgres dialect support into its own module; both are required
together, neither alone is sufficient). Each service has one migration:
`V1__create_orders_table.sql` (`orders`: `id`, `status`, `created_at`) and
`V1__create_inventory_items_table.sql` (`inventory_items`: `id`, `sku` [unique],
`quantity`, `created_at`).

Persistence is **plain JDBC, not JPA** (ADR-0004) — `NamedParameterJdbcTemplate` +
hand-written `RowMapper`s, no ORM session. `Order` and `InventoryItemDto` are plain
Lombok-`@Data` classes, not `@Entity`-annotated. `findById`/`save` remain exactly as
Milestone 0.3 left them; `InventoryItemRepository` gained `findBySku` (the API is
keyed by SKU, not the internal id) and `updateQuantity` in Milestone 0.4.

`findById`'s `EmptyResultDataAccessException` is now resolved at the service layer:
`OrderService`/`InventoryService` catch it and rethrow domain-specific exceptions
(`OrderNotFoundException`, `InventoryItemNotFoundException`), which
`@RestControllerAdvice` maps to a real HTTP 404. Persistence-specific exception types
never reach the web layer.

**`InventoryService.reserve` is the first method with a `@Transactional` boundary in
this codebase, and it has a known, deliberate concurrency gap (ADR-0005).** It reads
the current quantity, decides in application code, then writes — a separate `SELECT`
and `UPDATE`, not an atomic conditional statement. `@Transactional` guarantees those
two statements commit or roll back together; it does **not** prevent two concurrent
callers from both reading the same pre-update quantity and both writing back a
decremented value, silently overselling with no exception raised on either side. This
is intentional: the bug is left in place for Phase 1 to measure and fix with a real
concurrency experiment, not fixed prematurely. See ADR-0005 for the full reasoning,
including a real-time test of this plan (the human asked to fix it immediately during
Milestone 0.4's interview and consciously chose not to once the conflict was named).

Round-trip correctness is proven by a Testcontainers-backed integration test per
service (`OrderRepositoryTest`, `InventoryItemRepositoryTest`) — each spins up its own
ephemeral `postgres:17-alpine` container via `@Testcontainers`/`@ServiceConnection`,
runs the real `V1` migration against it from an empty schema, then exercises the
repository. Verified independent of local dev infrastructure: the full suite passes
with `order-db`/`inventory-db` stopped entirely.

### Communication

None. The two services do not call each other yet, synchronously or asynchronously.

### Infrastructure

`docker/docker-compose.yml` brings up two independent local containers,
`order-db` and `inventory-db` (both `postgres:17-alpine`, pinned major version), each
with its own named volume and host port (`5433`, `5434` respectively) — services run
directly on the host via `./gradlew bootRun`, not inside the Compose network, so they
reach Postgres via `localhost:<published-port>`, not by container DNS name. No CI yet.
Everything still runs by whatever JDK/Gradle the developer's machine resolves via the
toolchain pin (`ADR/0002-java-toolchain.md`).

The per-service isolation this was built for has been demonstrated, not just asserted:
stopping `order-db` alone flips `order-service`'s health to `DOWN` while
`inventory-service` remains unaffected, and restarting `order-db` recovers
`order-service` without an application restart (Milestone 0.2 failure scenario,
verified 2026-09-13).

### Observability

A `DataSource` health contributor auto-configures for both services once a
`DataSource` bean exists (no custom health indicator was written) and genuinely
influences the aggregate `/actuator/health` `status` — verified by the Milestone 0.2
failure test. The per-component breakdown is visible: `management.endpoint.health.
show-details: always` is set in each service's `application-local.yml` (deliberately
scoped to the `local` profile — `always` exposes component internals, including a
`validationQuery` string, to any unauthenticated caller; acceptable for local dev,
not something to carry into a real deployment profile unexamined). Confirmed directly
by curling the live endpoint: response includes
`"db":{"details":{"database":"PostgreSQL","validationQuery":"isValid()"},"status":"UP"}`
alongside Boot's other default-exposed components (`diskSpace`, `ping`, `ssl`,
liveness/readiness state).

This check is **pull-based** (evaluated only when `/actuator/health` is hit) and
reflects HikariCP's ability to validate or open a pooled connection *at that moment*
— a point-in-time fact about the connection pool, not a guarantee that the next real
request will succeed (pool exhaustion, among other things, can diverge from it).

**Milestone 0.4 adds structured JSON request logging with a correlation ID**, via a
plain `jakarta.servlet.Filter` (`CorrelationIdFilter`, duplicated identically in both
services — no shared module exists yet, and two small filter classes don't meet the
bar for introducing one). It honors an incoming `X-Correlation-Id` header if present
(so a caller's correlation ID propagates rather than being overwritten — relevant once
Phase 1 adds real cross-service calls), generates one otherwise, puts it in the SLF4J
MDC for the request's duration, and echoes it back as a response header. Log output is
genuine structured JSON via Spring Boot 4's **native** `logging.structured.format.
console: logstash` support (`spring.boot.logging.logback.LogstashStructuredLogFormatter`,
already on the classpath) — no `logstash-logback-encoder` or any other unmanaged
dependency was added; this is a config-only capability in current Spring Boot. No
OpenTelemetry yet (Phase 5) — this is deliberately the "plain servlet filter" version
`ROADMAP.md` calls for, not distributed tracing.

---

## 2. Target Service Topology (PROPOSED — not yet implemented)

Services are added **only when a curriculum phase needs the concept they teach**, per
`ROADMAP.md`. This table will be updated as each service actually gets built — until
then, treat it as intent, not fact.

| Service | Responsibility | Owned data | Introduced at | Status |
|---|---|---|---|---|
| `order-service` | Create/view orders; caller in resilience experiments | `orders` (own `postgres:alpine` container — ADR-0003) | Phase 0 | Persists via plain JDBC, no REST API yet |
| `inventory-service` | Track/reserve stock; downstream in resilience experiments | `inventory_items` (own `postgres:alpine` container — ADR-0003) | Phase 0 | Persists via plain JDBC, no REST API yet |
| `payment-service` | Third Saga participant; can succeed or fail to force compensation | `payments` (own DB) | Start of Phase 3 | Not created |
| `notification-service` | Pure Kafka consumer, no other responsibility — kept deliberately "boring" so Phase 4 consumer-group experiments aren't confounded by unrelated logic | none (stateless relay, or a minimal delivery log) | Phase 3/4 boundary | Not created |
| `dispute-service` | RAG over chargeback/scheme-rule reference documents (Phase 8); later, real tool-calling target for Phase 10's agent — a fictional service modeling public payment-industry concepts, not any specific employer's actual systems (see `ROADMAP.md` two-track decision, 2026-09-14) | reference documents + embeddings (own Postgres with `pgvector` — same database-per-service pattern as ADR-0003) | Phase 8 | Not created |
| `shipping-service` | TBD | TBD | **Not scheduled** — see decision note below | Not created |

**Decision note on `shipping-service`:** deliberately not committed to a phase. No
distinct distributed-systems concept has been identified yet that `notification-service`
doesn't already cover. Revisit after Phase 4; add only if a specific experiment needs a
separately-scaled 4th consumer. Do not add it "because microservices architectures
usually have shipping."

### Database-per-service — decided

Database-per-service is the intended pattern (true failure/schema isolation, one of the
core things this project exists to demonstrate). **Decision: Option A** — one local
`postgres:alpine` container per service (`order-db`, `inventory-db`), not a shared
instance. Separate process, separate credentials, separate failure domain — stopping
one container does not touch the other service's data or availability. Formal
reasoning, including two detours that were considered and rejected (a managed cloud
provider, and a shared instance with two logical databases), is recorded in
`ADR/0003-local-postgres-per-service.md`.

Each new service introduced later follows the same pattern: its own container, not a
slot in a shared one.

---

## 3. Communication Patterns (PROPOSED)

- **Phase 0–2:** `order-service → inventory-service` synchronous REST call only. This is
  intentional — partial failure, timeouts, retries, circuit breakers, and bulkheads are
  all best learned against a real synchronous network hop before any async machinery is
  introduced.
- **Phase 3+:** event-driven flows introduced via Kafka once Saga choreography needs
  them. Topics, partitions, and consumer-group topology will be documented here once
  they exist — not speculated about in advance.

---

## 4. AI Components

None yet. Phases 6–14 introduce Spring AI, RAG, GraphRAG, agents, MCP, and multi-agent
orchestration on top of this platform — this section stays empty until those phases are
active, per `CLAUDE.md` Rule 5 (don't document what doesn't exist yet).

---

## 5. Change Log

| Date | Change |
|---|---|
| 2026-09-12 | Initial version. Documents the bare two-service Gradle skeleton as scaffolded; no architecture decisions beyond build tooling have been made yet. |
| 2026-09-12 | §2 database-per-service decision resolved: Option A (one local `postgres:alpine` container per service). See ADR-0003. Two detours — managed cloud Postgres, and a shared instance with two logical databases — were considered and rejected along the way. |
| 2026-09-13 | Milestone 0.2 complete: ADR-0003's Option A implemented and proven, not just decided. `docker/docker-compose.yml` brings up `order-db`/`inventory-db`; both services wired to their own Postgres via `spring-boot-starter-jdbc` and a `local` profile; `management.endpoint.health.show-details: always` makes the `db` sub-component visible. Failure/recovery scenario verified end-to-end, including the sub-component itself: stopping `order-db` alone flips only `order-service`'s `db` status to `DOWN` (and thus its aggregate status); restarting it recovers without an app restart; `inventory-service` unaffected throughout. |
| 2026-09-14 | Milestone 0.3 complete: ADR-0004 (plain JDBC + Flyway, not JPA + Liquibase) implemented. Each service has one Flyway migration and a hand-written repository (`OrderRepository`, `InventoryItemRepository`) over `NamedParameterJdbcTemplate`. Round-trip correctness proven per service via a Testcontainers-backed integration test, verified to pass with local dev Postgres containers stopped entirely — genuine independence from `docker-compose`, not assumed. No REST API yet (Milestone 0.4); no transaction boundary exists yet since both repository operations are single-statement (tracked as a forward-looking gap for when a composite operation like stock reservation is introduced, and explicitly not solved by `@Transactional` alone — see `CLAUDE.md` → Known Existing Debt and Phase 1). |
| 2026-09-14 | §2 target topology extended: `dispute-service` added (Phase 8), driven by the human's active Senior AI Engineer interview timeline — see `ROADMAP.md`'s two-track decision. Phase 6/7/8 (AI foundations) unlocked to run in parallel with the distributed-systems track rather than waiting for Phase 5, since none of them depend on it; Phase 10 (Agents) stays gated on the real backbone since its entire premise requires genuinely real tool-calling targets. `dispute-service` models public, standard payment-industry concepts (chargebacks, scheme rules) — a deliberate choice, not modeled on any specific employer's actual internal systems despite the human's professional background in the space. |
| 2026-09-14 | Milestone 0.4 complete — **Phase 0 complete.** Both services gained a real REST API (Controller → Service → Repository), domain exceptions mapped to HTTP status (404/409/400) via `@RestControllerAdvice`, and structured JSON request logging with a propagating correlation ID (Spring Boot 4's native `logging.structured.format.console: logstash`, no new dependency). `InventoryService.reserve` is the first `@Transactional` method in the codebase and carries a known, deliberate lost-update race (ADR-0005) — left in on purpose as a real baseline for Phase 1's concurrency experiment, not fixed prematurely; this was tested for real when the human asked to fix it immediately during the milestone's interview and chose to stay on plan once the conflict was named. |
