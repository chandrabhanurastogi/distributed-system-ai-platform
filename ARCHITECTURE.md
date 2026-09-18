# ARCHITECTURE.md

This document reflects the **current, actual state** of the system, verified against the
repository — not the aspirational end state. It is updated every time a milestone changes
a service boundary, API, database, topic, or consistency guarantee (see `CLAUDE.md` Rule 7).

Last verified against repo: 2026-09-18, working tree (Milestone 6.3 complete). Phase 0
is complete; Phase 6 (Track B, AI Foundations) is in progress in parallel with Track A
per the two-track decision in `ROADMAP.md`.

---

## 1. Current State (AS-IS)

### Build

- Gradle 9.7.1, wrapper-committed, root project name `distributed-platform`.
- Java 25 (LTS) pinned via a Gradle toolchain block, resolved automatically on any
  machine via the `foojay-resolver-convention` plugin (no manual JDK install required).
- Spring Boot 4.1.1 / Spring Dependency Management plugin 1.1.7, applied once in the root
  `build.gradle`'s `subprojects {}` block so per-module `build.gradle` files stay minimal.
- Four Gradle modules as of Milestone 6.1: `order-service`, `inventory-service`,
  `llm-fundamentals`, and `common`. `common` is the exception to "every subproject is a
  Spring Boot application" — it has no main class, `bootJar` is disabled and plain
  `jar` enabled instead (ADR-0006); it exists to be depended on, not run.

### Services

| Service | Package | State |
|---|---|---|
| `order-service` | `com.distributedplatform.orderservice` | `POST /orders`, `GET /orders/{id}` (404 on miss). Controller → Service → Repository. Persists `Order` (plain class, not a JPA entity — ADR-0004) via `OrderRepository` (`NamedParameterJdbcTemplate`). Also ships `springdoc-openapi-starter-webmvc-ui` for interactive API exploration (not BOM-managed — a genuine third-party dependency, correctly version-pinned). |
| `inventory-service` | `com.distributedplatform.inventoryservice` | `GET /inventory/{sku}` (404 on miss), `POST /inventory/{sku}/reserve` (200, 409 on insufficient stock, 400 on invalid quantity). Same layering; `InventoryService.reserve` is the first `@Transactional` method in the codebase — see Data section below for a known, deliberate limitation of that boundary. Also ships `springdoc-openapi-starter-webmvc-ui`, same reason as `order-service`. |
| `llm-fundamentals` | `com.distributedplatform.llmfundamentals` | Phase 6, Track B. `POST /chat` (multi-turn, caller supplies full history), `POST /extract-person` (schema-constrained structured output, demonstrating the two-parse mechanic — the response envelope parses automatically, but `message.content` is itself a JSON string requiring an explicit second parse). `POST /chat/weather` (Milestone 6.2) — a full tool-calling round trip: describe a tool, receive a structured `tool_calls` request (arguments arrive as a real nested object here, *not* a string — Ollama is inconsistent between this feature and structured output), execute it (a fake implementation — no real weather API), feed the result back as a `role: "tool"` message, get a real final answer. Tool dispatch (`OllamaChatService.executeToolCall`) explicitly handles an unrecognized tool name with an error result rather than silently producing no response — a dangling, unanswered tool call would otherwise leave the model waiting indefinitely. **Milestone 6.3:** provider-agnostic `LlmClient` interface (ADR-0007 — stateless, full-history-per-call by design) with two real implementations, `OllamaLlmClient` (local) and `GeminiLlmClient` (hosted, Google's Interactions API), selected via `@ConditionalOnProperty(name = "llm.provider", ...)` so exactly one is registered in the Spring context at a time — proven by `LlmProviderConditionTest` asserting the non-selected bean is genuinely absent, not merely deprioritized. |

Both `order-service`/`inventory-service` ship `spring-boot-starter-web`,
`spring-boot-starter-actuator`, `spring-boot-starter-jdbc`, `spring-boot-starter-flyway`,
`flyway-database-postgresql`, `org.postgresql:postgresql` (runtime), `spring-kafka`,
and Lombok, but **Kafka is still only a dependency — nothing uses it yet.** See
`CLAUDE.md` → Known Existing Debt. `llm-fundamentals` does not depend on any of the
Postgres/Flyway stack — it's Track B, independent of the distributed-systems backbone.

### Shared infrastructure (`common` module, ADR-0006)

Introduced when a third module (`llm-fundamentals`) needed the same
`CorrelationIdFilter` already duplicated between `order-service` and
`inventory-service` — two duplicates were correctly judged insufficient to justify a
shared module in Milestone 0.4; a third real instance made the need concrete rather
than speculative. `common` exposes `CorrelationIdFilter` via a proper Spring Boot
auto-configuration (`LoggingAutoConfiguration`, registered through
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) —
required because `com.distributedplatform.common` sits outside every consuming
service's own package hierarchy, so default component scanning would never find it
otherwise. This is the same mechanism (Flyway's Spring wiring, `@AutoConfigureMockMvc`,
`@AutoConfigureTestRestTemplate`) this project spent Milestones 0.3/0.4/6.1 learning
about as a consumer — this is the first place it's been built, not just relied upon.
Scope is deliberately narrow: only genuinely cross-cutting, domain-free infrastructure
belongs here, gated by actual repeated need (3+ real instances), not speculation.

`common` also now holds `ErrorResponse` (`com.distributedplatform.common.web`) — a
one-field `record ErrorResponse(String error)` used by all three services'
`@RestControllerAdvice` classes, replacing what used to be plain-string error bodies.
This is the same 3x-repeated-need bar that justified `common` in the first place,
applied a second time rather than assumed to apply automatically to everything.
`llm-fundamentals` gained a `GlobalExceptionHandler` for the first time here too,
mapping `RestClientException` (Ollama unreachable) to a `503` — verified for real by
pointing the service at a genuinely unreachable address and confirming a proper JSON
`503`, not an unhandled `500`. This closes the failure-scenario gap flagged as open at
the end of Milestone 6.1's Definition of Done walkthrough.

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
| `dispute-service` | Single service hosting three phases of increasing sophistication: RAG over a combined corpus of dispute-handling policy **and** network/interchange scheme-rule documents (Phase 8, no separate "config" service — see `ROADMAP.md` Phase 8 for why); a single agent's tool-calling target, including a deterministic filing-deadline tool (Phase 10); an Investigator/Reviewer multi-agent pattern with a human-approval gate before finalizing a dispute decision (Phase 12). A fictional service modeling public payment-industry concepts, not any specific employer's actual systems (see `ROADMAP.md` two-track decision, 2026-09-14, reconfirmed 2026-09-14 after a first draft proposed a standalone service under a specific employer's actual internal system name) | reference documents + embeddings (own Postgres with `pgvector` — same database-per-service pattern as ADR-0003) | Phase 8 | Not created |
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

Phase 6 is active (`llm-fundamentals` module) — real content now exists here per Rule 7.
Multi-turn conversation (caller-managed history, no server-side session on either
provider — ADR-0007), real token/usage accounting from each provider's own response
fields (not estimated), and schema-constrained structured output against Ollama.

As of Milestone 6.3, a provider-agnostic `LlmClient` interface exists with two real,
switchable implementations — `OllamaLlmClient` (local, free) and `GeminiLlmClient`
(hosted, Google's Interactions API, real API calls verified against a live project).
Still no framework abstraction (no Spring AI) — this interface was hand-rolled
deliberately, per the Phase 6 sequencing decision, precisely so the raw mechanics were
understood before any framework's abstraction could hide them.

A real, honest latency/token comparison (not fabricated, per Rule 9 — see
`ROADMAP.md` Milestone 6.3) for the identical prompt "Explain recursion in one short
sentence.": Ollama (`llama3.2`, local) — 373ms latency, 33 input tokens, 24 output
tokens. Gemini (`gemini-3.6-flash`, hosted) — 4745ms latency, 8 input tokens, 20 output
tokens. The 12.7x latency gap is a clean local-vs-WAN comparison. The input-token gap is
**not** a clean tokenizer comparison — it's confounded by Ollama's chat template
injecting control tokens and a default system preamble into every request (verified
directly against `ollama show llama3.2 --template`), while `GeminiLlmClient`'s
single-message path sends the bare string with zero framing.

No RAG, no agents, no MCP, no multi-agent orchestration yet — those remain empty until
their phases are active, per Rule 5.

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
| 2026-09-14 | AI curriculum revised a second time. Phase 12 (multi-agent) confirmed in scope, resequenced to depend on Phase 10 directly rather than Phase 11 (MCP) — no real dependency between multi-agent orchestration and MCP exists. `dispute-service`'s scope consolidated: a proposed standalone service for scheme/interchange-rule configuration (named after a specific employer's actual internal system) was rejected — folded into `dispute-service`'s existing RAG corpus as a second document category instead, avoiding both the reused proprietary name and an architecturally-unjustified second service. Phase 8's claim-classification task now explicitly excludes filing-deadline checking (moved to Phase 10 as a deterministic tool, not an LLM/RAG reasoning task) and explicitly excludes OCR/multimodal receipt processing (out of the two chosen AI capabilities' scope). Phase 12 reframed around a concrete Investigator/Reviewer pattern on `dispute-service` with a human-approval gate. Clarified: this project is practice material for defending real production work done elsewhere, not itself the subject of any resume/interview claim. |
| 2026-09-16 | Phase 6 started (Milestone 6.1): new `llm-fundamentals` module makes direct, un-abstracted calls to a local Ollama instance — chat (single- and multi-turn, history proven via a real two-call test), real token/usage accounting, and schema-constrained structured output. New `common` module (ADR-0006) extracts `CorrelationIdFilter` — previously duplicated between `order-service`/`inventory-service` — via a proper Spring Boot auto-configuration, once a third module needed it. `springdoc-openapi-starter-webmvc-ui` added to `order-service`/`inventory-service` for interactive API exploration. Phase 14 (new) added to the roadmap: AI Governance and Compliance Observability, sequenced after Evals, before the (renumbered) Phase 15 capstone — sketched only, not detailed, pending an explicit decision on Datadog cost/tier. |
| 2026-09-16 | Milestone 6.2 complete: tool-calling mechanics added to `llm-fundamentals`, still raw against Ollama, no framework abstraction. Full round trip verified for real — describe a tool, receive a structured `tool_calls` request, execute it (fake implementation), feed the result back as a `role: "tool"` message, get a genuine final answer (`POST /chat/weather`). `llama3.2` empirically produced correctly-formed tool calls in testing, on a corrected basis after an initial, inaccurate justification ("the LLM is assumed tested") was named and rejected — Phase 13 (Evals) exists precisely because that assumption doesn't hold in real practice. A user-reported confusion led to two clarifications worth recording: this is not MCP (a separate client-server protocol, Phase 11's job — tool-calling is one of its underlying mechanisms, not the protocol itself), and a request to use Spring AI's `@Tool` annotation here was named as a direct conflict with Phase 6's raw-before-abstraction design and consciously deferred to Milestone 6.3, the same pattern as the pessimistic-locking conflict in Milestone 0.4. Tool dispatch was refactored into `executeToolCall`, closing a real gap found during the milestone's own DoD walkthrough: an unrecognized tool name now returns an explicit error result instead of silently producing no response, with a genuine unit test (no Spring context, no real Ollama call) proving it. |
| 2026-09-18 | **Milestone 6.3 complete — Phase 6 ends here for now.** Provider-agnostic `LlmClient` interface introduced (ADR-0007: deliberately stateless, full-history-per-call, sacrificing Gemini's native `previous_interaction_id` session continuation for a uniform seam both providers satisfy). `OllamaLlmClient` retrofits Milestones 6.1/6.2's raw code behind the interface; `GeminiLlmClient` is a genuine second implementation calling Google's real (2026-era "Interactions API", not the legacy `generateContent` endpoint) hosted API, selected via `@ConditionalOnProperty(name = "llm.provider", ...)` — a real bug was found and fixed here: an initial `@Primary`-based approach left both beans registered and satisfied none of the milestone's actual "switch provider via config" goal, since `@Primary` only breaks ambiguous-injection ties rather than preventing bean creation; replaced with `@ConditionalOnProperty` and proven with `LlmProviderConditionTest` asserting the non-selected bean is absent from the context entirely. Getting the real Gemini API working required two non-code root causes the human found independently after header-format guessing (`x-goog-api-key` → 401, `Authorization: Bearer` → 403) went nowhere: the Gemini API was never enabled on the Google Cloud project created via AI Studio, and the initially-chosen model (`gemini-2.5-flash`) was deprecated for new users. A real, non-fabricated latency/token comparison was run (see §4, AI Components) — Ollama 373ms/33in/24out vs. Gemini 4745ms/8in/20out for an identical prompt, with the input-token gap explicitly flagged as confounded by chat-template overhead rather than a clean tokenizer comparison. New tracked debt surfaced during this milestone's closing interview, not yet fixed: `GeminiLlmClient.serializeMessages()`'s flat-string transcript format (role-prefixed text ending in a trailing `Assistant:` prime) has no tested failure scenario for multi-turn agent-shaped content — untrusted tool-observation text containing literal `User:`/`Assistant:` substrings could be mistaken for real dialogue turns, and the model could hallucinate past its own turn with no equivalent to Ollama's hard `<\|eot_id\|>` stop token. Recorded in `CLAUDE.md` → Known Existing Debt, to be addressed before Phase 10 (Agents) routes tool-calling through this provider. Two security incidents occurred and were handled during this milestone: real, live Gemini API keys were pasted into chat twice; both were refused for use, revocation was instructed immediately regardless of stated intent to rotate afterward, and debugging proceeded only via the human running commands in their own environment. |
