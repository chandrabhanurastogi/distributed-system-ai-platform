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

**Two-track structure (decided 2026-09-14):** the human is actively interviewing for
Senior AI Engineer roles, which creates a real, external timing pressure that pure
sequential ordering doesn't serve. Rather than reordering the whole curriculum, phases
split into two tracks based on actual dependency, not convenience:

- **Track A (Distributed Systems):** Phases 0–5, sequential as originally designed.
- **Track B (AI Foundations):** Phase 6 (LLM API fundamentals), Phase 7
  (vector/retrieval math from scratch), and Phase 8 (RAG) are unlocked to start now, in
  parallel with Track A. Phase 8's real dependency is Phase 6/7 (LLM and embedding
  fundamentals), not the distributed backbone — RAG over `dispute-service`'s
  chargeback/scheme-rule *documents* needs a document corpus and `pgvector`, not real
  transactional data from `order-service`/`inventory-service`.

Phase 10 (Agents) is the one phase individually assessed as genuinely needing Track A:
its whole premise is tool-calling "grounded in this project's own data" — a real
transaction lookup, a real dispute-status lookup — which is meaningless against mocked
services. It stays gated on enough of Track A existing (at minimum Milestone 0.4's REST
layer) for that grounding claim to be true rather than aspirational. This avoids the
two failure modes of resequencing badly: freezing all AI work behind five phases of
distributed-systems work a real interview timeline can't wait for, or front-loading
every AI phase and quietly downgrading Agents/MCP into toy-tool demonstrations — the
weaker, more generic version of exactly the story a Senior AI Engineer interview is
going to probe for.

**Phase 12 (multi-agent, decided in scope 2026-09-14) inherits Phase 10's gating**,
not Phase 11's — its real dependency is having a single-agent baseline to prove
insufficient (Phase 10), not MCP. Sequenced directly after Phase 10; Phase 11 (MCP)
can proceed independently, in either order.

**Concrete execution order, decided 2026-09-14:** Phase 6 → Phase 7 → Phase 1 → Phase
2 → Phase 8 (RAG) → Phase 10 (Agents). Phase 10's actual gate (Milestone 0.4) is
already satisfied, so nothing here is a hard dependency — Phase 1/2 are placed before
Phase 8/10 by choice, not requirement, for two reasons: (1) Phase 1 and 2 have zero
dependency in either direction on Phase 6/7/8, so there's no technical pressure ever
forcing a return to them, which is exactly the condition under which one track quietly
never gets revisited — naming a concrete return point now is the deliberate fix for
that; (2) Phase 10's agent guardrails (tool failures, timeouts) are a stronger,
more realistic story once `order-service`/`inventory-service` already have real
timeout/retry/circuit-breaker behavior (Phase 2) underneath them, rather than wrapping
bare, unprotected REST calls. Phase 3–5 and Phase 12 are not placed in this ordering
yet — where they land is still open.

**Domain grounding for the AI-specific work:** Phase 8, 10, and 12 are all grounded in
one new fictional service, `dispute-service` (introduced at Phase 8 — see its own
section below), modeling public, standard payment-industry concepts (chargebacks,
dispute reason codes, scheme rules) — deliberately *not* modeled on any specific
employer's actual internal systems, regardless of the human's professional background
in the space. This is a conscious choice, revisited and reconfirmed once already
(2026-09-14): a first pass at expanding this domain proposed a standalone service
named after a specific employer's actual internal system, which was rejected on the
same grounds as the original decision — see Phase 8's section for detail.
`order-service`/`inventory-service`/`payment-service` are not renamed; they already
map cleanly onto transaction/authorization concepts and renaming working, tested code
for narrative flavor alone would be pure busywork.

**What this project is and isn't for, stated plainly (2026-09-14):** this repository
and its AI/payments-domain work are a private practice ground for building genuine
technical fluency — they are not the material the human intends to present in
interviews. The human's actual interview material is real production work on a real
payment network, done as part of a larger team; this project exists to sharpen the
ability to explain and defend that real work's underlying concepts (multi-agent
orchestration, evidence validation, human-in-the-loop approval, dependency governance
under fast-moving AI libraries) with hands-on depth, not to generate resume claims
about this codebase itself. Any resume/interview language should describe what was
actually built where it was actually built — this project earning you fluency in a
concept is not the same claim as this project having shipped that concept in
production, and the two must not be conflated in outward-facing material.

**Cross-cutting concern, not a phase: hallucination mitigation (noted 2026-09-15).**
There is deliberately no dedicated "handling hallucinations" milestone — it isn't one
technique learned once, it's a property actively designed against at every layer, and
naming it as a single phase would misrepresent how it actually works. Where it
actually lives, so this isn't lost as an unstated assumption:
- **Phase 6:** structured output constrains the model to a schema instead of
  free-form generation — less room to fabricate the *shape* of an answer.
- **Phase 8 (RAG):** grounding generation in retrieved documents — the classic
  mitigation, but only one layer among several, not the whole story.
- **Phase 10 (Agents):** the filing-deadline tool is itself a hallucination-prevention
  technique — delegate anything an LLM might guess wrong (date arithmetic) to a
  deterministic function it calls, rather than letting it reason about the answer.
- **Phase 12 (multi-agent):** the Reviewer agent independently checking the
  Investigator's conclusion, plus the human-approval gate, is a structural check
  against trusting one model's confident-but-wrong output on the first pass.
- **Phase 13 (Evals):** measuring the actual hallucination rate honestly, never
  assuming zero — the same discipline behind rejecting "eliminating hallucinations"
  as resume language earlier in this project's history. Same principle, two contexts.

The interview-ready version of this story is the whole chain (grounding + structured
output + deterministic delegation + independent review + honest measurement), not any
one technique in isolation — naming only "I did RAG" would be the weaker answer.

---

## Phase 0 — Engineering Foundation

**Status: Complete — 2026-09-14** (Milestone 0.4 was its last blocking milestone; the
CI/config-management backlog items below remain open but were never required to close
this phase — see Phase 0 backlog)

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

### Milestone 0.4 — REST API + layered structure + tests

**Status: Complete — 2026-09-14**

- [x] `order-service`: `POST /orders` (201 + `Location` header + body), `GET
      /orders/{id}` (200 or 404)
- [x] `inventory-service`: `GET /inventory/{sku}` (200 or 404), `POST
      /inventory/{sku}/reserve` (200 on success, 409 on insufficient stock)
- [x] Controller → Service → Repository layering in both services; repositories stay
      exactly as Milestone 0.3 left them (no changes to `save`/`findById`)
- [x] `findById`'s `EmptyResultDataAccessException` (Known Existing Debt from 0.3) gets
      resolved here: service layer catches it and the controller maps "not found" to a
      real HTTP 404, not a default 500
- [x] `InventoryItemRepository` gains one new method, `findBySku` (the API is keyed by
      SKU, not the internal DB id — `findById` alone can't serve `GET /inventory/{sku}`)
- [x] Unit tests for service-layer logic (plain JUnit + Mockito, no Spring context)
- [x] Integration tests for the full HTTP → service → DB path (MockMvc + the same
      Testcontainers pattern as 0.3)
- [x] Structured JSON logging with a correlation ID in the MDC (plain servlet filter —
      no OpenTelemetry yet, that's Phase 5)

**Deliberate design choice — the first real business logic, and a live bug, on
purpose:** `reserve` is a genuine read-then-conditional-write (check quantity, decide,
update) — exactly the composite operation named as a forward-looking gap in
`CLAUDE.md`'s Known Existing Debt after Milestone 0.3. It gets a `@Transactional`
service-layer boundary here (closing the "no method spans multiple statements" gap),
but is implemented as the **naive** version — a separate `SELECT` then `UPDATE`, not
an atomic conditional `UPDATE ... WHERE quantity >= :n`. This is intentional, not an
oversight: it leaves a real, reproducible lost-update race under concurrent access
sitting in the codebase for Phase 1 to actually find, measure, and fix with a genuine
experiment (fire N concurrent reservations against limited stock, measure how much
overselling actually occurs) rather than a synthetic textbook example. `@Transactional`
here guarantees the service's own two statements commit or roll back together — it
does **not** claim to prevent concurrent overselling, and that distinction must be
stated explicitly wherever this is documented, not glossed over.

**Explicitly out of scope for 0.4:** no cross-service calls yet (that's Phase 1 — this
milestone is each service's own API in isolation), no fix for the reservation race
(Phase 1), no OpenTelemetry (Phase 5).

**Acceptance criteria:** `./gradlew test` passes unit and integration tests for both
services. Manually verified via `curl`: creating an order and fetching it by id
succeeds; fetching a nonexistent order id returns 404; reserving available stock
succeeds and decrements quantity; reserving more than available stock returns 409
without decrementing; every response is logged with a correlation ID present in the
structured log output.

**Interview questions (answer before moving on):**
- Why does a `@Transactional` boundary around `reserve` not prevent two concurrent
  requests from overselling the same SKU? Walk through the actual interleaving.
- Why is `findBySku` a new repository method rather than reusing `findById` with a
  lookup translation somewhere else?
- What's the actual difference between a 404 (order not found) and a 409 (insufficient
  stock) in HTTP semantics — why isn't insufficient stock also a 404 or a 400?

**Verified 2026-09-14:** `./gradlew clean test` — 19 tests across both services, 0
failures, 0 errors, confirmed via the JUnit XML reports directly, not just the
aggregate build status. Manually verified via `curl` against real running instances
(not just the test suite): `POST /orders` returns 201 with `Location` and a body;
`GET /orders/{id}` returns 200 for an existing order and 404 with a real message for a
missing one; `GET /inventory/{sku}` returns 200/404 correctly; `POST
/inventory/{sku}/reserve` correctly decrements on success (5→2 for a reserve-3),
returns 409 with the actual requested/available counts when stock is insufficient, and
400 for a non-positive quantity — verified against actual Postgres state via `psql`
after each call, not just the HTTP response. Every response carries an
`X-Correlation-Id` header, and structured JSON logging is genuinely active (confirmed
real `@timestamp`/`logger_name`/`level` JSON fields in the running services' console
output, using Spring Boot 4's native `logging.structured.format.console: logstash`
support — no `logstash-logback-encoder` dependency needed, which avoided reintroducing
an unmanaged, hand-pinned version after Milestone 0.3 established that pattern should
be avoided).

**Bugs found and fixed (same discipline as 0.3 — root-caused via direct inspection,
not guessed):**
- A stale `bootRun` process from earlier in the session was still holding port 8080,
  causing a fresh `order-service` start to fail with "Port 8080 was already in use" —
  not a code bug, but a reminder to actually check `lsof` before assuming a failure is
  code-related.
- `spring-boot-starter-test` no longer bundles MockMvc's web test autoconfiguration in
  Spring Boot 4 — that moved into its own `spring-boot-starter-webmvc-test` module,
  and `@AutoConfigureMockMvc` itself moved package from
  `org.springframework.boot.test.autoconfigure.web.servlet` to
  `org.springframework.boot.webmvc.test.autoconfigure`. Confirmed both by inspecting
  the actual resolved jars, matching the exact pattern of Boot 4 surprises from
  Milestone 0.3.
- `JsonPath.read()` parses JSON numbers as `Integer` by default; binding its generic
  return type directly to a `long` variable causes a `ClassCastException` at
  runtime (`Integer` cannot unbox into `Long`) — fixed by reading as `Number` first
  and calling `.longValue()`.

**Interview:** three rounds. Q1 (why `@Transactional` doesn't prevent overselling)
took two passes — the first answer was correct in conclusion (pessimistic locking
needed) but described the race vaguely ("threads may commit at the same time"); the
second, prompted for a concrete step-by-step trace, correctly identified that both
transactions read the pre-update value before either writes, and additionally
surfaced an important, unprompted point: with the current unconditional `UPDATE`, the
overwrite is **silent** — no exception, both callers get `200 OK`, and the resulting
DB value can look entirely plausible (not negative) even though the underlying
business fact — who actually holds a valid reservation — is wrong. This led directly
to a real-time test of the plan itself: the human asked to implement pessimistic
locking immediately, which was named as a direct conflict with Phase 1's design (see
ADR-0005) — the human chose to stay on plan, the strongest possible confirmation that
leaving the bug in was correct. Q2 (why `findBySku` isn't unified with `findById` via
a translation layer) reached for general software-engineering virtues (coupling,
extensibility) rather than the specific mechanical reason — the two methods query
different columns entirely, and a translation layer would need to run the equivalent
of `findBySku` anyway just to convert a SKU into an `id`, making it strictly more
expensive, not more decoupled. The human also independently noticed `findById` is now
unused by any production code path — a genuinely good catch, correctly left alone
since it's still exercised by Milestone 0.3's own test and the roadmap had already
committed to not touching it. Q3 (404 vs. 409) correctly explained 404, added a nice
unprompted business angle (409 signals "needs restocking" vs. 404's "doesn't exist"),
but didn't address why not 400 without a direct explanation — resolved by explaining
RFC 9110's actual distinction: 400 means the request is intrinsically invalid
regardless of server state (checkable with zero database access, which is exactly why
the quantity validation runs before `getBySku`); 409 means a well-formed request
conflicts with current state and could succeed unchanged later.

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

**Status: In progress — started 2026-09-15**

**Track B — unlocked, no dependency on Track A.** Can start now, in parallel with
Phase 0/1, per the two-track decision above.

**Goal:** direct LLM API calls first (chat completion, system/user messages, tokens,
context windows, temperature, structured output, tool calling) *before* Spring AI
abstractions — the fundamentals must not be hidden behind a framework on day one.

**Provider decision (2026-09-15):** start against a **local Ollama instance**, not a
paid hosted provider — no API key, no per-call cost, unblocking unlimited
experimentation while concepts are still being learned. Neither a Claude Pro nor a
Gemini Advanced/Pro *subscription* covers API usage; those are separate consumer
products from the pay-per-token API accounts Spring AI would call, confirmed before
this decision was made rather than assumed. Switch to a real hosted provider
(Anthropic or OpenAI) once comfortable — tracked as its own later milestone in this
phase, not deferred indefinitely.

**Sequencing decision, the one worth not forgetting (2026-09-15):** build raw,
Ollama-specific code first — hand-constructed JSON request/response shapes, zero
abstraction, no `LlmClient` interface yet. Only *after* multi-turn history, tokens,
and structured output are genuinely understood does introducing an `LlmClient`
interface (with `OllamaLlmClient` now, `AnthropicLlmClient`/similar later, selected via
a config flag) become its own deliberate milestone. Building the interface first would
quietly defeat this phase's actual point — a home-rolled abstraction hides the same
raw mechanics a framework's abstraction would, just earlier and with extra steps. See
this same session's reasoning: the interface is worth building because it teaches
*why* Spring AI's abstraction looks the way it does, which only lands if the raw
mechanics were seen first.

**New module:** `llm-fundamentals` — kept separate from `order-service`/
`inventory-service`, since this is exploratory, pre-abstraction learning work, not
part of the payments domain those services model.

**Testing strategy decision (2026-09-15):** integration tests may hit the real local
Ollama server directly (free, no cost, no non-determinism concern beyond the model's
own sampling) — but this is a real environment prerequisite, the same category as
Testcontainers needing Docker: Ollama must actually be installed, running, and have
the model pulled, or these tests fail for infrastructure reasons, not code reasons.
Once a real hosted provider is introduced later in this phase, its calls get mocked in
tests — a paid, non-free, non-deterministic external dependency should not run on
every `./gradlew test`.

**Environment, verified 2026-09-15:** Ollama installed via `brew install --cask
ollama-app` (0.34.0), server started via `ollama serve`, `llama3.2` pulled (chosen for
speed over capability at this stage — mechanics, not quality, is the point). Verified
with a real chat completion call against `http://localhost:11434/api/chat` before any
application code was written — response included real `prompt_eval_count`,
`eval_count`, and duration fields, which is exactly the real, measurable data this
phase's "what will we measure" question was looking for, not a fabricated placeholder.

### Milestone 6.1 — Raw chat completion mechanics against Ollama

**Status: Complete — 2026-09-16**

- [x] New `llm-fundamentals` Gradle module, wired into `settings.gradle`
- [x] Hand-constructed HTTP call to Ollama's `/api/chat` endpoint (no abstraction, no
      interface) — a plain Java class building the exact JSON Ollama expects
- [x] Multi-turn conversation: prove that history is actually resent every turn (not
      assumed) — a test that inspects the outgoing request body across two calls and
      confirms the second includes the first turn's messages
- [x] Token/usage accounting: capture and assert on the real `prompt_eval_count`/
      `eval_count` fields Ollama returns — real numbers, not estimated
- [x] Structured output: a request that constrains the response to a JSON schema,
      deserialized into a typed Java object — not string-parsed
- [x] A small REST endpoint (`POST /chat`) to manually trigger a call and inspect the
      real request/response shape via `curl`, matching this project's established
      verify-via-curl pattern
- [x] Integration test(s) hitting the real local Ollama server (per the testing
      strategy decision above)

**Explicitly out of scope for 6.1:** no `LlmClient` interface, no second provider, no
tool-calling yet (tool-calling's mechanics are enough of their own topic to earn a
separate milestone within this phase, written when 6.1 is done).

**Grew beyond the original checklist, for good reason:** a third module needing
`CorrelationIdFilter` triggered ADR-0006 (shared `common` module, built via a real
Spring Boot auto-configuration — the first one this project has authored rather than
just consumed). Reviewing the new per-service `README.md` files then surfaced real
documentation drift (error responses documented as JSON but actually returned as
plain strings) which led to a genuine API-design decision: a shared `ErrorResponse`
DTO across all three services, which in turn closed Milestone 6.1's previously-open
failure-scenario gap (`llm-fundamentals` had zero handling for Ollama being
unreachable) with a real, verified test — pointing the service at a genuinely
unreachable address and confirming a proper `503` JSON body, not an unhandled `500`.

**Verified 2026-09-16:** `./gradlew clean test` green across all four modules
(`order-service`, `inventory-service`, `llm-fundamentals`, `common`). Manually verified
against real, running instances (not just the test suite): single-turn and multi-turn
`/chat` against real Ollama; `/extract-person` returning a genuinely typed,
schema-constrained object (`{"name":"Maria","age":42}` from free text); real
`prompt_eval_count`/`eval_count` values captured from actual Ollama responses (33
prompt tokens for a 7-word input, traced to Ollama's chat template injecting a default
system preamble and structural role tokens — confirmed directly via `ollama show
llama3.2 --template`, not assumed); all four `order-service`/`inventory-service` error
paths (404/409/400) now returning genuine `{"error": "..."}` JSON, verified via
`curl`, not just code review; `llm-fundamentals`'s new `503` failure path verified by
actually pointing `ollama.base-url` at an unreachable port and confirming the real
response.

**Bugs found and fixed (same discipline as every prior milestone — root-caused, not
guessed):**
- `OllamaRequest`'s `model` field was initially named `request` — serialized under the
  wrong JSON key, Ollama had no idea what `"request"` meant. A value-correct,
  key-wrong bug, same category as Milestone 0.3's `save()` parameter mismatch.
- Hardcoded model name `"llama3"` when only `llama3.2` was ever pulled — confirmed via
  `ollama list` before asserting, not from memory.
- `ChatControllerTest`'s original draft had `@SpringBootTest` without
  `webEnvironment = RANDOM_PORT` (no real server started), then — once fixed — hit two
  more Boot 4 module-split surprises: `TestRestTemplate` requires an explicit
  `@AutoConfigureTestRestTemplate` annotation now (confirmed by finding its
  autoconfiguration is only reachable through that annotation's own empty-otherwise
  `.imports` file), and `RestTemplateBuilder` moved into its own `spring-boot-restclient`
  module, separate from where `RestClient` (used by the actual application code) lives.
- Turn 1's assertion compared a `Message` object to a `String` literal (type mismatch,
  could never pass) and, separately, the test's final assertion checked `turn1Response`
  a second time instead of `turn2Response` — meaning the test, as first written, never
  actually exercised the claim it was supposed to prove.
- Mid-refactor, `OllamaChatService.chat(...)` (the simple, non-structured method) was
  accidentally deleted while adding `chatStructured(...)` — caught because the
  already-existing `OllamaChatServiceTest` still referenced it and wouldn't compile;
  restored alongside the new generic method rather than one replacing the other.
- `springdoc-openapi-starter-webmvc-ui` was added to the *global* `subprojects{}`
  block without a stated reason — moved to just `order-service`/`inventory-service`
  once the actual reason (interactive endpoint testing) was named.
- New per-service `README.md` files documented JSON error bodies
  (`{"error": "..."}`) that didn't match the actual plain-string responses being
  returned at the time — caught by curling the real endpoints rather than trusting the
  docs, and resolved by fixing the code (the `ErrorResponse` DTO) rather than just the
  docs, since the JSON shape was the better design regardless of which was "wrong."

**Interview:** two rounds, both requiring more than one pass. Q1 (why
`@AutoConfiguration` doesn't depend on package location) initially answered as "makes
it part of Spring scanning from the parent module" — a real conceptual error, since
auto-configuration and component scanning are different mechanisms, not one extending
the other; tightened across two attempts to the correct distinction: component
scanning discovers classes by walking a package tree, auto-configuration loads classes
by explicit fully-qualified name from a `.imports` manifest, so there's no tree to
walk and therefore no package boundary to be blocked by. Q2 (`@ConditionalOnMissingBean`
semantics) reached the right outcome (a user-defined bean takes precedence) but with
two imprecisions worth naming: the check is by *type* (inferred from the bean method's
return type), not "name or type" as first stated; and the auto-configured bean is
never instantiated at all when the condition fails, not "overridden" after being
created — a real distinction for reasoning about constructor side effects.

Detailed milestones for the rest of Phase 6 (tool-calling mechanics, the
`LlmClient`-interface milestone, the hosted-provider switch) written when the next
piece of Phase 6 is actually started, per `CLAUDE.md` Rule 5.

---

## Phase 7 — Vector and Retrieval Foundations

**Track B — unlocked, no dependency on Track A.** Real prerequisite is Phase 6.

**Goal:** cosine similarity implemented and tested from scratch (dot product → vector
magnitude → cosine similarity) before touching a real embedding model or a VectorStore.
Build a small retrieval system by hand before introducing a vector database.

Detailed milestones written when Phase 6 is complete.

---

## Phase 8 — RAG

**Track B — unlocked, no dependency on Track A.** Real prerequisite is Phase 6/7 (LLM
and embedding fundamentals) — this phase needs a document corpus and `pgvector`, not
real transactional data, so it does not need to wait on the distributed backbone.

**Goal:** full ingestion → chunking → embedding → indexing → retrieval → reranking →
context construction → LLM → answer pipeline, applied to `dispute-service`'s reference
corpus — chargeback/dispute-handling policy documents **and** network/interchange
scheme-rule documents (bin ranges, interchange thresholds, filing-deadline rules) in
the *same* corpus, not a separate service. (An earlier draft of this plan proposed a
standalone `cdc-service` for the scheme-rule side, named after a specific employer's
internal system — rejected on two grounds: the name itself was too close to
proprietary internal branding to reuse even genericized, and there was no
distributed-systems or AI concept a second service would teach that a richer
single-service corpus doesn't already cover. One service, two document categories.)

Chunking strategies compared experimentally
(fixed/sentence/paragraph/recursive/overlap/semantic). Dense, BM25, hybrid, reranking,
HyDE — each benchmarked, none assumed to help by default, **evaluated against a
concrete downstream task, not just retrieval-in-the-abstract**: given real,
unstructured customer claim text ("I was charged twice at this restaurant") and real,
publicly-published network reason codes (e.g. Visa 10.4 — Fraud, 13.1 — Not as
Described/Received), does retrieval quality actually change classification accuracy?
This gives chunking/retrieval-strategy comparisons a measurable outcome instead of an
abstract "did it find the right paragraph."

**Explicitly out of scope, named so it doesn't quietly creep back in:**
- **OCR / multimodal receipt processing.** Realistic claim inputs use hand-written
  synthetic "receipt summary" text (merchant, line items, timestamp) — not an actual
  OCR/vision pipeline. That's a third AI capability beyond the two chosen (RAG,
  agents/multi-agent) and was deliberately not selected when scope was narrowed.
- **Filing-deadline / date-window checking is not a RAG task.** "Is this claim within
  120 days of settlement" is deterministic date arithmetic — it belongs to Phase 10 as
  a plain tool call, not to an LLM's reasoning, grounded or otherwise. Do not let an
  LLM compute it even "with retrieved context" — that's a reliability anti-pattern a
  technical interviewer will specifically probe for, not a feature.

Introduces `dispute-service` as a new deployable service with its own Postgres
(`dispute-db`, `pgvector` extension — same database-per-service pattern as ADR-0003)
and, per the human's own architectural instinct, decoupled from any synchronous
request path via the same async/event-driven pattern Kafka is already scheduled to
introduce at Phase 3/4 — heavy AI work (embedding, retrieval) has no business blocking
a REST response.

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

**The one AI phase individually assessed as needing Track A** (see two-track note
above) — gated on at least Milestone 0.4's REST layer existing, so "grounded in this
project's own data" is a true statement rather than an aspiration.

**Goal:** single agent first, small number of real tools grounded in this project's own
data — look up a transaction (`order-service`), check dispute status
(`dispute-service`), retrieve the applicable chargeback/scheme rule via Phase 8's RAG
capability, and **check whether a claim falls within the network's filing-deadline
window** via a plain deterministic tool (date arithmetic on real timestamps — no LLM
reasoning involved in the computation itself, only in deciding to call the tool and
interpreting its boolean result). This is deliberately the strongest "real system, not
tutorial" signal in the AI curriculum, per the two-track decision — an agent calling
real services with real latency and real failure modes, and knowing which decisions to
delegate to deterministic code rather than the model, is a categorically different
interview story than one calling mocked functions and reasoning about dates in-model.
Guardrails: invalid tool arguments, unauthorized/destructive operations, excessive
tool loops, tool failures, timeouts, token budgets.

This single agent is also the deliberate baseline Phase 12 needs: multi-agent
complexity only gets justified once this phase can point at something specific this
one agent can't do — see Phase 12.

Detailed milestones written when Phase 9 is complete.

---

## Phase 11 — MCP

**Goal:** MCP from first principles (resources, tools, prompts, client/server
architecture, capability negotiation, transport, security implications). Build an MCP
server exposing real capabilities of this project; connect an agent/client to it.

Detailed milestones written when Phase 10 is complete.

---

## Phase 12 — LangGraph and Multi-Agent Systems

**Real prerequisite is Phase 10, not Phase 11.** Multi-agent orchestration and
MCP (tool exposure via a standard protocol) are independent capabilities — nothing
about graph-based multi-agent workflows requires an MCP server to exist first. This
phase can start as soon as Phase 10 has established, concretely, why a single agent
was insufficient — Phase 11 can proceed in parallel or afterward, in either order.

**Goal:** graph-based agent workflows (state, nodes, edges, routing, checkpoints,
persistence, human-in-the-loop) — concretely, an **Investigator/Reviewer** pattern
built on `dispute-service`: an Investigator agent gathers evidence (retrieves the
applicable scheme rule via Phase 8's RAG capability, checks the filing-deadline tool
from Phase 10, assembles a recommended reason-code classification), and a separate
Reviewer agent critiques that recommendation against the same retrieved evidence
before it's presented for **human approval** — a real human-in-the-loop gate before
any dispute decision is finalized, not an automated end-to-end pipeline. This is a
direct instance of the general Researcher → Writer → Critic → Revision pattern this
phase is built around, applied to a task where getting it wrong has real (if
fictional, in this project) consequences — which is exactly the property that makes
human-in-the-loop approval a genuine design requirement here rather than a checkbox.
Then generalize: orchestrator-worker and parallel-worker patterns beyond this one
example.

**Explicitly required before claiming this phase teaches "multi-agent":** a single
agent (Phase 10) must first be shown insufficient for this exact task, concretely
(e.g., a single agent conflating "gather evidence" and "critique the evidence" in one
undifferentiated pass, with no independent check before a human sees it) — not
assumed insufficient because multi-agent sounds more sophisticated.

Detailed milestones written when Phase 10 is complete.

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

## Phase 14 — AI Governance and Compliance Observability

**Added 2026-09-16, at the human's request, after Milestone 6.1 surfaced a real need**
(token/cost accounting per request) that pointed at a genuine gap: nothing in the
roadmap made AI-specific signals *continuously* observable in production, as opposed
to logged once and forgotten or measured offline in Phase 13's batch evals.

**Real prerequisite: Phase 13, not Phase 5.** This is not a duplicate of Phase 5
(Track A's distributed-systems observability — latency, circuit-breaker state,
consumer lag). It's a different signal cluster: cost/token trends, human-override rate
against AI recommendations (Phase 12's approval gate), and *continuous drift* in the
same metrics Phase 13 defines and measures offline (hallucination rate, groundedness).
You can't dashboard drift in a metric you haven't defined yet — Phase 13 has to exist
first.

**Goal:** take the request-level facts already captured in structured logs (correlation
ID, token usage, latency — starting with Milestone 6.1's own logging work) and make
them continuously monitored via a real observability backend (Datadog, per the human's
choice) rather than logged and never looked at again. Concretely: cost trends over
time; token-usage anomalies as a proxy signal for the excessive-tool-loop guardrail
named back in Phase 10; human-override/approval-rate tracking from Phase 12's gate
(did humans agree with the AI's recommendation, and how often, over time); drift
dashboards for Phase 13's eval metrics instead of one-off offline numbers; and a
correlation-ID-traceable audit trail (who/what approved which case, based on which
retrieved evidence).

**Naming honesty, consistent with earlier decisions in this project:** this phase
builds the *observability mechanics* a real compliance program would rely on — audit
trails, cost dashboards, override-rate tracking — not a claim of actual certified
regulatory compliance (SOC2/PCI-DSS or otherwise). Same principle as the earlier
correction to resume language: describe what's actually built, not what it resembles.

**Named but not yet decided, to avoid over-planning per Rule 5:** Datadog is a real,
paid, external SaaS dependency — unlike everything introduced so far in this project
(Postgres, Ollama, Testcontainers are free/local; even the eventual hosted LLM
provider is the human's own deliberate, cost-aware choice). Before this phase is
actually detailed into milestones, it needs its own explicit decision (and likely an
ADR): confirm current Datadog pricing/free-tier terms before committing rather than
assuming, and confirm the log volume this project would actually generate stays within
whatever tier is chosen.

Detailed milestones written when Phase 13 is complete.

---

## Phase 15 — Production-Grade Capstone

**Goal:** the full system — `Client → API → Order → Inventory → Payment → Kafka →
Notification`, plus the AI layer (RAG/GraphRAG/Agents/MCP/Tools), plus Phase 14's
governance/observability layer — as one coherent, production-oriented system. Final
architecture is derived from what Phases 0–14 actually showed worked, not assumed now.
Security, configuration, observability, resilience, schema evolution, idempotency,
data consistency, deployment, load testing, failure testing.

Detailed milestones written when Phase 14 is complete.

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
