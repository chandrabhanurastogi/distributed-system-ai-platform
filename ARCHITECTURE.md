# ARCHITECTURE.md

This document reflects the **current, actual state** of the system, verified against the
repository — not the aspirational end state. It is updated every time a milestone changes
a service boundary, API, database, topic, or consistency guarantee (see `CLAUDE.md` Rule 7).

Last verified against repo: 2026-09-13, working tree (Milestone 0.2 changes staged,
pending commit).

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
| `order-service` | `com.distributedplatform.orderservice` | Boots. One `@SpringBootApplication` class. No controllers. Connects to its own Postgres via a `local`-profile datasource; no entities/persistence layer yet. |
| `inventory-service` | `com.distributedplatform.inventoryservice` | Same shape as `order-service` — datasource wired, no entities yet. |

Both currently ship `spring-boot-starter-web`, `spring-boot-starter-actuator`,
`spring-boot-starter-jdbc`, `org.postgresql:postgresql` (runtime), `spring-kafka`, and
Lombok, but **Kafka is still only a dependency — nothing uses it yet.** See
`CLAUDE.md` → Known Existing Debt.

### Data

Database-per-service is now wired, not just decided. Each service has its own
`postgres:17-alpine` container (`order-db`, `inventory-db` — see Infrastructure below
and ADR-0003) and its own `DataSource` bean, configured via a `local` Spring profile
(`spring.profiles.default: local` in the base `application.yml`; connection details in
`application-local.yml`, kept out of the unqualified file so Milestone 0.3's
Testcontainers integration tests can supply a different URL without touching it).

No entities, no migrations, no repository layer yet — the datasource exists, but
nothing is persisted through it. That's Milestone 0.3.

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

---

## 2. Target Service Topology (PROPOSED — not yet implemented)

Services are added **only when a curriculum phase needs the concept they teach**, per
`ROADMAP.md`. This table will be updated as each service actually gets built — until
then, treat it as intent, not fact.

| Service | Responsibility | Owned data | Introduced at | Status |
|---|---|---|---|---|
| `order-service` | Create/view orders; caller in resilience experiments | `orders` (own `postgres:alpine` container — ADR-0003) | Phase 0 | Datasource wired, no entities yet |
| `inventory-service` | Track/reserve stock; downstream in resilience experiments | `inventory_items` (own `postgres:alpine` container — ADR-0003) | Phase 0 | Datasource wired, no entities yet |
| `payment-service` | Third Saga participant; can succeed or fail to force compensation | `payments` (own DB) | Start of Phase 3 | Not created |
| `notification-service` | Pure Kafka consumer, no other responsibility — kept deliberately "boring" so Phase 4 consumer-group experiments aren't confounded by unrelated logic | none (stateless relay, or a minimal delivery log) | Phase 3/4 boundary | Not created |
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
