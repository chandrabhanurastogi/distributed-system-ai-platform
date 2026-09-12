# ADR-0003: Local Postgres container per service, not shared, not cloud-hosted

Status: Accepted
Date: 2026-09-12

## Context

`order-service` and `inventory-service` each need a database. `ARCHITECTURE.md` §2
established database-per-service as the intended pattern (true failure/schema
isolation is one of the core things this project exists to teach), but the concrete
mechanism — what actually runs, and where — was left open for Milestone 0.2.

The decision took three passes before landing, and all three are recorded here rather
than only the final answer, because the reasoning that eliminated the first two options
is as instructive as the decision itself.

## Options Considered

1. **Managed cloud Postgres (Neon.tech), one project per service.** Zero local
   compute/memory footprint; forces real network hops, TLS, and 12-factor secret
   management from day one.
2. **One local `postgres:alpine` container, two logical databases** (`order_db`,
   `inventory_db`) inside it. Lighter on laptop resources than two containers — one
   process instead of two.
3. **Two separate local `postgres:alpine` containers**, one per service (`order-db`,
   `inventory-db`), each with its own volume and credentials.

## Decision

Option 3: two separate local `postgres:alpine` containers, orchestrated via
`docker-compose.yml`, run under OrbStack (see dev-environment discussion — not itself
ADR-worthy, since it's a fully portable, reversible tooling choice with no effect on
what gets committed).

## Why Options 1 and 2 were tried first and then rejected

**Option 1 (Neon.tech)** was the initial Milestone 0.1 answer, on the reasoning that a
managed service offloads compute from the laptop and forces realistic network/TLS
conditions. It was reversed once its actual consequence was worked through: Milestone
0.2's required failure scenario is "stop the database, observe `/actuator/health` flip
to `DOWN`." A managed cloud project cannot be casually stopped the way a local
container can — Neon's isolation is provider-managed project isolation, not
developer-controlled process isolation, and the two are not interchangeable for a
project whose entire purpose is to let failure be induced and observed on demand. It
also introduces a hard network dependency for local development and a serverless
cold-start latency variable that would have quietly contaminated Phase 1–2's timeout/
retry/circuit-breaker latency measurements later — the opposite of what "accurate
resilience tests" requires.

**Option 2 (shared instance, two logical databases)** was considered next, driven by a
real and legitimate concern: local resource usage (memory, CPU, background processes)
on the development machine. That concern was investigated concretely rather than
assumed away — see the resource-footprint analysis in-session, using this machine's
actual specs (Apple M4 Pro, 24GB RAM). Two idle `postgres:alpine` containers cost on
the order of 50–100MB RAM combined against 24GB available; the dominant background cost
is the container runtime's VM itself, which is paid once regardless of how many
Postgres containers run inside it. Given that, Option 2's resource savings over Option
3 are real but marginal, while its cost is not marginal: a single shared Postgres
process failing takes down both services' database connectivity simultaneously,
directly contradicting the isolation property database-per-service exists to
demonstrate. Rejected once it was clear the resource constraint that motivated it
didn't actually bind on this hardware.

## Consequences

- `docker-compose.yml` defines two services (`order-db`, `inventory-db`), each a
  pinned `postgres:17-alpine` (explicit major version — not the bare `alpine` tag,
  which floats to whatever major version is currently latest and would silently break
  reproducibility later, same reasoning as the Java toolchain pin in ADR-0002), each
  with its own named volume.
- `docker compose stop order-db` (or killing just that container) can take down
  `order-service`'s database connectivity while leaving `inventory-service` completely
  unaffected — this is now a provable claim, not an assumption, and Milestone 0.2's
  failure-injection test exists specifically to prove it.
- Two containers means two things to start, stop, and account for locally instead of
  one — accepted as the cost of the isolation guarantee actually holding.
- `docker compose down -v` fully tears down both, including volumes, for a clean slate.
- Every future service added to this platform (`payment-service`, etc.) gets its own
  container under this same pattern — not a slot inside a shared one.

## Rejected Alternatives

- **Neon.tech / managed cloud Postgres**: rejected — breaks the container-stop failure
  test this milestone requires, adds a hard network dependency for local dev, and
  introduces a serverless latency variable that would confound later resilience
  experiments. Revisit if/when this project reaches a phase that specifically wants to
  study cloud-managed-service failure modes (not currently scheduled).
- **Single shared instance, two logical databases**: rejected — the resource savings
  over two containers don't materialize as a real constraint on this hardware, while
  the isolation loss is real and directly contradicts the property database-per-service
  is meant to demonstrate.
