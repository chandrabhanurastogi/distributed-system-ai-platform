# ROADMAP.md

This is the syllabus for `distributed-microservices-platform` as a Senior/Staff
distributed-systems + AI learning project. Read `CLAUDE.md` first — it defines the rules
this roadmap is executed under (one milestone at a time, Definition of Done, no
fabricated benchmarks, etc.).

**How detail is allocated:** the active phase and the next one get full milestone
breakdowns with acceptance criteria. Future phases are sketched at goal/deliverable
level only, and get expanded into real milestones when they become active — writing
detailed plans for Phase 12 today, before any Phase 3–11 experimental results exist to
inform it, would just mean rewriting it later. See `CLAUDE.md` Rule 5.

**Status legend:** `[ ]` not started · `[~]` in progress · `[x]` done (only after the
full Definition of Done checklist in `CLAUDE.md` is satisfied).

---

## Phase 0 — Engineering Foundation

**Goal:** two real, independently-testable, database-backed services with a working
local dev loop — no cross-service calls yet, no Kafka activity yet. This is the
substrate every later phase depends on.

### Milestone 0.1 — Docs baseline + verified boot

**Status: Complete — 2026-09-12**

- [x] `ROADMAP.md`, `CLAUDE.md`, `ARCHITECTURE.md`, `ADR/` created
- [x] Both services verified to boot cleanly and respond on `/actuator/health`
- [x] `README.md` added at repo root (how to build, how to run, where the docs live)

**Acceptance criteria:** `./gradlew bootRun` for each service returns HTTP 200 from
`/actuator/health` with no manual configuration beyond what's committed.

**Verified 2026-09-12:** `order-service` and `inventory-service` both returned
`HTTP 200 {"groups":["liveness","readiness"],"status":"UP"}` from `/actuator/health`
on their default port (8080, unconfigured — confirmed neither `application.yml`
overrides it) and again on distinct overridden ports when run simultaneously, with
zero config changes beyond what's already committed. `README.md` was checked against
the actual repository (commands, prerequisites, ports) before being marked complete —
nothing in it was invented. (Also surfaced and fixed an unrelated issue: a stale
Gradle daemon from the scaffolding session had been launched from a since-deleted
scratchpad path and was silently being reused, breaking the build with a
`NoSuchFileException`. Killing it let the wrapper start a fresh daemon from the
properly cached distribution. Not an architectural decision — just noted here so it
isn't a mystery if it resurfaces.)

**Interview questions:** answered in `.prompts/completed_milestone_0.1.md` (2026-09-12) and
reviewed. Q1 (database-per-service trade-offs) and Q5 (why separate deployable
services) were directionally correct but under-specified — missing, respectively, the
loss-of-cross-service-ACID-transaction point that motivates all of Phase 3, and the
process/failure-isolation mechanism (separate heap/threads/GC) rather than just
"domain boundaries." Q2 (Kafka classpath debt) overstated the current risk — no
`@KafkaListener` or `KafkaTemplate` usage exists yet, so autoconfiguration does not
attempt a broker connection at startup; the dependency is inert bloat, not an active
hazard, today. Q3 (toolchain) and Q4 (shared-module risk) were accurate. Full written
feedback delivered in-session; not reproduced here in full to avoid this document
duplicating conversation history — see session log if needed.

**Milestone 0.2 decision — resolved:** Option A confirmed: two separate local
`postgres:alpine` containers (`order-db`, `inventory-db`), one per service, matching the
original isolation rationale in `ARCHITECTURE.md` §2. This is the outcome of two
detours that were raised and rejected along the way, kept here for the record rather
than erased:

1. **Managed cloud Postgres (Neon.tech)** — considered, then rejected. It replaces
   developer-controlled process isolation with provider-managed project isolation, and
   removes the ability to literally stop the dependency to run Milestone 0.2's required
   failure scenario. It also introduces a network dependency for local dev and a
   serverless cold-start latency variable that would have confounded Phase 1–2's
   resilience measurements later.
2. **Single shared local instance, two logical databases (Option B)** — considered
   briefly for resource conservation, then walked back in favor of true per-service
   process isolation. The M4 Pro / 24GB dev machine has ample headroom for two
   lightweight `postgres:alpine` containers regardless, so there was no real resource
   constraint forcing the trade-off.

**Pattern going forward:** each new service introduced later (`payment-service`, etc.)
gets its own Postgres container, following this same model — not a shared instance.

ADR-0003 records this decision formally (drafted as part of Milestone 0.2
implementation).

---

### Milestone 0.2 — Containerized local infrastructure + database wiring

**Status: Complete — 2026-09-13** (briefly marked complete, reopened same day over an
unverified claim, then closed again once independently re-verified — see correction
note and final verification below)

**Prerequisite — resolved:** Option A confirmed (see Milestone 0.1 note above): two
separate local `postgres:alpine` containers, one per service. Formal record in
ADR-0003.

- [x] `docker-compose.yml` bringing up two Postgres containers (`order-db`,
      `inventory-db`) for local development
- [x] `order-service` and `inventory-service` each get a datasource configured in
      `application.yml` (profile-based — don't hardcode connection strings)
- [x] Actuator health check extended to report DB connectivity (`/actuator/health`
      shows a `db` component) — `management.endpoint.health.show-details: always`
      added to each service's `application-local.yml` (scoped to the `local` profile
      deliberately — `always` exposes component internals to any unauthenticated
      caller, acceptable for local dev, not something to carry into a real deployment
      profile without revisiting)
- [x] ADR-0003 recorded for the database topology decision

**Explicitly out of scope for 0.2:** no entities, no migrations, no repositories yet.
This milestone is only "can the app see the database," nothing more — kept narrow on
purpose per `CLAUDE.md` Rule 1.

**Acceptance criteria:** `docker compose up`, then `./gradlew bootRun` for both
services, then `curl localhost:PORT/actuator/health` for each shows `"status":"UP"`
with a `db` sub-component also `UP`. Stopping **only `order-db`** and re-hitting both
health endpoints must show `order-service` flip to `DOWN` while `inventory-service`
**stays `UP`** — i.e., the health check is proven to actually check something, not just
report a static value, and the per-service isolation Option A was chosen for is proven
to actually hold, not just asserted in an ADR.

**Failure scenario required (Rule 3):** stop `order-db` only, with both services
running; observe `order-service`'s health flip to `DOWN` and `inventory-service`'s
health remain `UP` and unaffected. Restart `order-db`; observe `order-service` recover
without an application restart. This is the concrete test of the isolation claim behind
choosing Option A over Option B.

**Verified 2026-09-13:** `docker compose up` brought up `order-db` (port `5433`) and
`inventory-db` (port `5434`) as independent containers with independent named volumes;
each confirmed reachable and correctly scoped via `psql` inside its own container
(`current_database`/`current_user` matched per service, no cross-talk). Both services
wired to `spring-boot-starter-jdbc` + `org.postgresql:postgresql` (versions left
unpinned, managed by the existing Spring Boot BOM — two bugs caught in review: an
explicit version pin that skewed one starter to 4.1.0 against the project's actual
4.1.1, and a stray trailing `"` character in `order-service`'s JDBC URL that had been
silently accepted as a literal character by the YAML parser). `application.yml` now
sets `spring.profiles.default: local`; `application-local.yml` per service holds the
real `spring.datasource.*` values — kept out of the unqualified file specifically so
Milestone 0.3's Testcontainers tests can override the URL without touching it.
Failure scenario confirmed exactly as specified at the aggregate level: stopping
`order-db` alone flipped `order-service`'s top-level `/actuator/health` `status` to
`DOWN`; restarting it recovered `order-service` without an application restart;
`inventory-service` was unaffected throughout — the isolation claim behind Option A
holds in practice, not just on paper.

**Correction (2026-09-13, same day):** this entry originally claimed the response
showed a `db` sub-component explicitly. That was wrong — `management.endpoint.health.
show-details` defaults to `never`, so `/actuator/health` only ever returned the
aggregate `{"status":...}`, confirmed by curling it directly:
`{"groups":["liveness","readiness"],"status":"UP"}`, no `db` key. The DB check was
genuinely influencing that aggregate status the whole time (that's why the failure
test worked), but the milestone's literal acceptance criteria — "shows a `db`
sub-component also `UP`" — was not actually satisfied and required an additional,
until-now-missing config change.

**Final verification (2026-09-13):** `show-details: always` added to both services'
`application-local.yml`. Independently confirmed by curling the live endpoint directly
(not taken on report this time) — response now includes
`"db":{"details":{"database":"PostgreSQL","validationQuery":"isValid()"},"status":"UP"}`
alongside the pre-existing `diskSpace`, `ping`, `ssl`, and liveness/readiness state
components Boot exposes by default once details are shown. Milestone genuinely
complete now.

**Interview questions:** asked and answered in-session (not pre-written in this
document, since none existed before this milestone went active). Q1 (why the health
check's `DOWN` transition wasn't instant) was initially answered as "waits for a
timeout," which is true but too vague to defend under questioning; tightened, with
correct reasoning volunteered along the way, to the actual mechanism — Actuator's
health check is pull-based (no background polling), a stopped container's closed port
fails fast at the OS level, and the observed delay is HikariCP's own pool bookkeeping
(discovering a pooled idle connection is dead, evicting it, opening a replacement).
Q2 (does `UP` guarantee the next real request succeeds) reached the correct conclusion
— no — but initially via a scattershot list of unrelated causes (memory leak, network
issues, credential expiry) rather than one reasoned mechanism; tightened to the
TOCTOU framing (health check is a snapshot at time T; pool exhaustion between T and
the next request is the general shape of the divergence, not any single named cause).
Q3 (cost of Option A at 8 services) was answered as "CPU/memory/storage," which
contradicts ADR-0003's own resource-footprint arithmetic for 2 containers (~50–100MB
combined against 24GB RAM) — that arithmetic doesn't flip direction at 8 containers
either (~200–400MB, still noise). Corrected: the real cost at scale is operational
bookkeeping (port allocation, `docker-compose.yml` duplication/typo risk, remembering
what's running), addressed with Compose YAML anchors/extension fields and a documented
port convention — not by consolidating containers, which would repeat Option B's
already-rejected isolation loss.

---

### Milestone 0.3 — First persistent domain slice

**Status: Complete — 2026-09-14**

- [x] Flyway (or Liquibase — decide and record why) added to both services
- [x] First migration + entity per service: `Order` (`order-service`), `InventoryItem`
      (`inventory-service`) — minimal fields only, no business logic yet
- [x] Repository layer (Spring Data JPA or plain JDBC — decide and record why)
- [x] Testcontainers added; a repository-layer integration test boots a real Postgres
      container, runs the migration, and proves a round-trip (save → read back)

**Explicitly out of scope for 0.3:** no REST endpoints yet. This is "can we correctly
persist and retrieve one row," isolated from any API-layer concerns.

**Acceptance criteria:** `./gradlew test` runs a Testcontainers-backed integration test
per service that fails if the migration or entity mapping is wrong, and passes
otherwise. Test must not depend on a developer having Postgres running locally — it
must stand up its own container.

**Decision — resolved (ADR-0004):** plain JDBC (`NamedParameterJdbcTemplate`), not
Spring Data JPA, and Flyway, not Liquibase. The JDBC-vs-JPA call was the substantive
one: Phase 1 requires directly observing transaction isolation and row-level locking,
which Hibernate's session cache and deferred-flush behavior would obscure. Explicitly
scoped to the learning phases, not asserted as a permanent stance — full reasoning,
including what production teams would correctly choose instead (JPA) and why, is in
`ADR/0004-plain-jdbc-and-flyway.md`.

**Verified 2026-09-14:** `./gradlew test` runs both services' Testcontainers-backed
integration tests — each boots its own ephemeral `postgres:17-alpine`, applies the
real `V1` migration from an empty schema (confirmed in logs, not assumed), and proves
a save→read-back round trip. Re-verified with `order-db`/`inventory-db` stopped
entirely — full suite still passes, proving genuine independence from local dev
infrastructure rather than accidental reliance on it. `docker exec` + `psql` used
throughout to independently confirm table structure and Flyway history rather than
trusting "it worked" reports at face value — this milestone had more of those checks
pay off than any prior one.

**Bugs found and fixed along the way (kept here, not smoothed over, since each was a
genuine root cause rather than a guess):**
- Migration filename `V1_create_orders_table.sql` (single underscore) — Flyway's
  naming convention requires a double underscore between version and description;
  with only one, Flyway silently didn't recognize the file as a migration at all (no
  error, just total silence).
- Adding raw `flyway-core` was insufficient on Spring Boot 4: Boot 4 split its
  monolithic `spring-boot-autoconfigure` jar into per-feature modules, and Flyway's
  Spring wiring moved into its own `spring-boot-flyway` module (confirmed by directly
  inspecting jar contents — `spring-boot-autoconfigure-4.1.1.jar` has zero
  Flyway-related classes). Fixed via `spring-boot-starter-flyway`.
- That alone still failed with `FlywayException: Unsupported Database: PostgreSQL
  17.11` — a *second*, independent split: Flyway 10+ moved per-database dialect
  support out of `flyway-core` into `flyway-database-*` modules. Needed
  `flyway-database-postgresql` in addition to the Spring starter; the BOM managing a
  version for an artifact is not the same thing as that artifact being pulled in
  automatically — a real point of confusion surfaced and corrected in-session.
- Testcontainers 2.x renamed every module with a `testcontainers-` prefix
  (`org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`,
  same for `postgresql`) and moved `PostgreSQLContainer` out of
  `org.testcontainers.containers` into `org.testcontainers.postgresql` (the old class
  survives only as a deprecated compatibility shim). Confirmed by inspecting the
  resolved `testcontainers-bom` POM directly rather than guessing from memory.
- `inventory-service`'s first migration was a copy-paste of `order-service`'s shape
  (`id`/`status`/`created_at`) under the wrong table name (`inventory` instead of the
  already-documented `inventory_items`) and represented nothing about actual inventory
  tracking. Caught before a repository was built on top of it; fixed via
  `docker compose down -v inventory-db` (verified this scopes correctly to one
  service's container and volume, leaving `order-db` completely untouched) and a
  corrected migration (`sku` unique, `quantity`).
- `InventoryItemRepository.save()` had a parameter-name/column mismatch
  (`VALUES (:status, :sku, :createdAt)` against columns `sku, quantity, created_at`) —
  another copy-paste artifact from `Order`, would have failed the instant it executed.
- `InventoryItemRepositoryTest`'s two methods both hardcoded `sku = "testsku"`; since
  `@Container` is `static`, both tests shared one Postgres instance, and the second
  insert violated `inventory_items_sku_key`'s `UNIQUE` constraint. Fixed with a random
  SKU per test invocation. Note: the identical latent risk exists in
  `OrderRepositoryTest` too — it just hasn't surfaced because `orders` has no unique
  constraint to violate.

**Interview questions:** three rounds, all requiring a real answer before any reveal.
Q1 (why Testcontainers instead of the `docker-compose` instance) was initially
answered around data-safety/pollution risk — correct but incomplete, and included an
imprecise "thread pool" resource-contention claim that didn't fit a project with no
real user traffic. Tightened, with the human volunteering the correct reasoning once
pointed at the actual migration log line: a persistent, already-migrated database
would never re-validate that the migration file itself is correct, only that the
repository code works against whatever shape that database currently happens to have.
Q2 (transactional gap in `save`/`findById`) correctly concluded no `@Transactional`
belongs on the *existing* methods, and correctly named stock reservation
(read-then-write) as the operation that will eventually need one — but initially
justified the current lack of a boundary as "these methods don't need ACID," which is
imprecise (every single statement already gets full ACID from Postgres by default;
the real reason is that a single statement has nothing left to compose). Also
surfaced, and deliberately left as a forward pointer rather than solved now: even a
correct `@Transactional` boundary around a future `reserveStock` would **not** by
itself prevent a concurrent lost-update/overselling race — that requires explicit
locking or isolation-level reasoning, i.e., Phase 1's actual subject matter. Q3 (why
the Spring Boot BOM didn't catch the Testcontainers artifact rename) correctly
identified the mechanism unprompted — a BOM is an exact-coordinate lookup table with
no aliasing or rename history, so a renamed artifact simply isn't a key in it anymore,
which is categorically different from a version *conflict* (where the coordinate
exists but multiple sources disagree on which version).

---

### Milestone 0.4 — REST API + layered structure + tests (sketched, detailed when 0.3 is done)

- [ ] `order-service`: `POST /orders`, `GET /orders/{id}`
- [ ] `inventory-service`: `GET /inventory/{sku}`, `POST /inventory/{sku}/reserve`
- [ ] Unit tests for service-layer logic (no Spring context)
- [ ] Integration tests for the full HTTP → service → DB path (MockMvc/WebTestClient +
      Testcontainers)
- [ ] Structured JSON logging with a correlation ID in the MDC (plain servlet filter —
      no OpenTelemetry yet, that's Phase 5)

**Acceptance criteria and full task breakdown to be finalized when Milestone 0.3 is
verified done** — deliberately not over-specified this far in advance.

---

### Phase 0 backlog (not yet sequenced into a milestone)

- CI pipeline (GitHub Actions) running `./gradlew build` on every push — needs a
  decision on caching the auto-provisioned JDK (see ADR-0002 consequences)
- Config management strategy for multiple profiles (local/test/ci)

---

## Phase 1 — Distributed Systems Fundamentals

**Goal:** understand and *experience* — via `order-service` calling `inventory-service`
synchronously — partial failure, timeouts, idempotency, isolation levels, and race
conditions, before reaching for a resilience library to paper over any of it.

**Prerequisite:** Milestone 0.4 complete (a real synchronous call needs a real API on
both ends).

Key topics: CAP theorem, consistency models, availability vs. durability, network
failures, timeouts, partial failures, idempotency, transaction isolation levels,
optimistic vs. pessimistic locking, race conditions. Contract testing (deferred from
Phase 0) is introduced here, once there's an actual contract between the two services
worth pinning.

Detailed milestones written when Phase 0 is complete.

---

## Phase 2 — Resilient Spring Microservices

**Goal:** Resilience4j on the `order-service → inventory-service` call: timeout, bounded
retry with exponential backoff + jitter, circuit breaker, bulkhead, rate limiting,
fallback — each one measured (latency, success rate, attempt count, downstream load),
not just implemented. Retry-storm experiment required (what happens when many callers
retry simultaneously, and how jitter changes it).

Detailed milestones written when Phase 1 is complete.

---

## Phase 3 — Transactions and Distributed Consistency

**Goal:** `payment-service` is introduced here (see `ARCHITECTURE.md` §2) as the third
Saga participant. Implement and compare local transactions, 2PC, Saga (choreography and
orchestration), compensation, transactional outbox, idempotent consumers — against the
explicit failure scenario: order created → inventory reserved → payment fails →
inventory released → order cancelled. Requires a minimal Kafka on-ramp (one topic, one
producer, one consumer, default settings) to make choreography possible — full Kafka
mechanics are deliberately deferred to Phase 4 (see curriculum note below).

Detailed milestones written when Phase 2 is complete.

---

## Phase 4 — Kafka Event-Driven Mechanics

**Goal:** deep Kafka mechanics, using the events already flowing from Phase 3
(`OrderCreated`, `InventoryReserved`, `PaymentCompleted`, etc.) as real experimental
substrate instead of synthetic topics. `notification-service` introduced here (or at the
Phase 3/4 boundary) as a deliberately "boring" pure consumer so consumer-group scaling
experiments aren't confounded by unrelated logic.

Required experiment progression: 1 topic/1 partition/1 consumer → 3 partitions/1
consumer → 3 partitions/3 consumers → 3 partitions/5 consumers, with an explanation of
the observed behavior at each step. Partition-key design, ordering guarantees, consumer
lag measurement, dead-letter topics, poison messages, rebalancing.

Detailed milestones written when Phase 3 is complete.

---

## Phase 5 — Observability and Production Engineering

**Goal:** OpenTelemetry distributed tracing across the full chain
(`Order → Inventory → Payment → Kafka → Notification`), correlation IDs, metrics,
dashboards, health/readiness/liveness distinctions, load testing. Track p50/p95/p99
latency, throughput, error rate, consumer lag, retry count, circuit breaker state, DB
latency.

Detailed milestones written when Phase 4 is complete.

---

## Phase 6 — Spring AI and LLM Foundations

**Goal:** direct LLM API calls first (chat completion, system/user messages, tokens,
context windows, temperature, structured output, tool calling) *before* Spring AI
abstractions — the fundamentals must not be hidden behind a framework on day one.

Detailed milestones written when Phase 5 is complete.

---

## Phase 7 — Vector and Retrieval Foundations

**Goal:** cosine similarity implemented and tested from scratch (dot product → vector
magnitude → cosine similarity) before touching a real embedding model or a VectorStore.
Build a small retrieval system by hand before introducing a vector database.

Detailed milestones written when Phase 6 is complete.

---

## Phase 8 — RAG

**Goal:** full ingestion → chunking → embedding → indexing → retrieval → reranking →
context construction → LLM → answer pipeline. Chunking strategies compared
experimentally (fixed/sentence/paragraph/recursive/overlap/semantic). Dense, BM25,
hybrid, reranking, HyDE — each benchmarked, none assumed to help by default.

Detailed milestones written when Phase 7 is complete.

---

## Phase 9 — GraphRAG

**Goal:** Neo4j-backed knowledge graph (entity extraction → relationship extraction →
graph → Cypher → graph retrieval → LLM), benchmarked head-to-head against Vector RAG and
Hybrid RAG on a fixed evaluation dataset (Hit Rate, Recall, MRR, precision where
meaningful, answer correctness, groundedness, latency, token usage, cost). No claim that
GraphRAG is better without the numbers to back it.

Detailed milestones written when Phase 8 is complete.

---

## Phase 10 — Agents and Tool Calling

**Goal:** single agent first, small number of real tools grounded in this project's own
data (search orders, get inventory, lookup customer, search docs). Guardrails: invalid
tool arguments, unauthorized/destructive operations, excessive tool loops, tool
failures, timeouts, token budgets.

Detailed milestones written when Phase 9 is complete.

---

## Phase 11 — MCP

**Goal:** MCP from first principles (resources, tools, prompts, client/server
architecture, capability negotiation, transport, security implications). Build an MCP
server exposing real capabilities of this project; connect an agent/client to it.

Detailed milestones written when Phase 10 is complete.

---

## Phase 12 — LangGraph and Multi-Agent Systems

**Goal:** graph-based agent workflows (state, nodes, edges, routing, checkpoints,
persistence, human-in-the-loop). Researcher → Writer → Critic → Revision, then
orchestrator-worker and parallel-worker patterns — only after establishing, concretely,
why a single agent from Phase 10 was insufficient for the task at hand.

Detailed milestones written when Phase 11 is complete.

---

## Phase 13 — Evals

**Goal:** fixed evaluation dataset; real metrics for retrieval (Recall/Hit Rate/MRR),
classification/extraction (Precision/Recall/F1), tool usage (selection accuracy,
argument accuracy, execution success rate, unnecessary-call rate), and agents (task
success rate, correctness, groundedness, hallucination rate, latency, tokens, cost).
LLM-as-judge introduced with explicit rubrics and its own meta-evaluation (consistency,
bias, agreement with human labels, prompt sensitivity) — never trusted blindly.

Detailed milestones written when Phase 12 is complete.

---

## Phase 14 — Production-Grade Capstone

**Goal:** the full system — `Client → API → Order → Inventory → Payment → Kafka →
Notification`, plus the AI layer (RAG/GraphRAG/Agents/MCP/Tools) — as one coherent,
production-oriented system. Final architecture is derived from what Phases 0–13 actually
showed worked, not assumed now. Security, configuration, observability, resilience,
schema evolution, idempotency, data consistency, deployment, load testing, failure
testing.

Detailed milestones written when Phase 13 is complete.

---

## Quantitative Benchmarking Log

Per `CLAUDE.md` Rule 9: every entry below must be a real, reproduced measurement.
Nothing is added here until it's actually run.

| Technique | Dataset | Metric | Baseline | Result | Improvement | Latency | Token usage | Cost | Notes |
|---|---|---|---|---|---|---|---|---|---|
| _(none yet — Phase 0 has no experiments to measure)_ | | | | | | | | | |

---

## Interview Questions — Starting Architecture (answer before Milestone 0.2 begins)

1. Why does each service get its own database instead of a shared schema, and what do
   you give up by doing that?
2. `inventory-service` currently has `spring-kafka` on its classpath but nothing
   produces or consumes a message. Is that a problem? Why or why not?
3. Why is Java 25 (LTS) pinned via a Gradle toolchain block instead of just relying on
   whatever JDK happens to be on the developer's `PATH`?
4. The two services currently share zero code — no common library module. When (if
   ever) would you introduce a `common`/shared-kernel module, and what's the risk of
   introducing it too early?
5. Both services are structurally identical skeletons right now. What is the actual
   architectural reason `order-service` and `inventory-service` must be two separate
   deployable services rather than two packages in one Spring Boot app?
