# ARCHITECTURE.md

This document reflects the **current, actual state** of the system, verified against the
repository — not the aspirational end state. It is updated every time a milestone changes
a service boundary, API, database, topic, or consistency guarantee (see `CLAUDE.md` Rule 7).

Last verified against repo: 2026-09-12, commit `ddf65e7`.

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
| `order-service` | `com.distributedplatform.orderservice` | Boots. One `@SpringBootApplication` class. No controllers, no persistence, no config. |
| `inventory-service` | `com.distributedplatform.inventoryservice` | Same — structurally identical to `order-service` right now. |

Both currently ship `spring-boot-starter-web`, `spring-boot-starter-actuator`,
`spring-kafka`, and Lombok, but **only the dependency is present — nothing uses Kafka
yet.** See `CLAUDE.md` → Known Existing Debt.

### Data

No database is wired. No entities exist. No migration tool is configured.

### Communication

None. The two services do not call each other yet, synchronously or asynchronously.

### Infrastructure

No Docker, no Docker Compose, no CI. Everything currently runs by whatever JDK/Gradle
the developer's machine resolves (which is exactly why the toolchain pin exists — see
`ADR/0002-java-toolchain.md`).

### Observability

None configured. `spring-boot-starter-actuator` is on the classpath but has no exposed
endpoints configured beyond Spring Boot defaults (`/actuator/health` only, unconfigured).

---

## 2. Target Service Topology (PROPOSED — not yet implemented)

Services are added **only when a curriculum phase needs the concept they teach**, per
`ROADMAP.md`. This table will be updated as each service actually gets built — until
then, treat it as intent, not fact.

| Service | Responsibility | Owned data | Introduced at | Status |
|---|---|---|---|---|
| `order-service` | Create/view orders; caller in resilience experiments | `orders` (own `postgres:alpine` container — ADR-0003) | Phase 0 | Skeleton exists |
| `inventory-service` | Track/reserve stock; downstream in resilience experiments | `inventory_items` (own `postgres:alpine` container — ADR-0003) | Phase 0 | Skeleton exists |
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
