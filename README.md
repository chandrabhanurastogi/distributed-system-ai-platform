# Distributed Microservices Platform

A multi-week, hands-on learning project for building, operating, and defending the
architecture of a distributed system from first principles — Spring Boot
microservices, resilience patterns, Kafka, distributed transactions, observability,
and (later) an AI/RAG/agents layer on top. This is a learning project, not a product:
see `ROADMAP.md` for the curriculum and `CLAUDE.md` for how work on this repository is
paced and reviewed.

## Current Services

- **`order-service`** — will own order data. Currently a bare Spring Boot skeleton: no
  persistence, no API, no cross-service calls yet.
- **`inventory-service`** — will own inventory data. Same current state as
  `order-service`.

Both services are structurally identical at this stage. See `ARCHITECTURE.md` for the
current and target system state.

## Prerequisites

- No local JDK or Gradle install required. The committed Gradle wrapper (9.7.1)
  auto-provisions a Java 25 (LTS) toolchain on first run via the Foojay resolver
  plugin.
- Internet access on first build, to download the Gradle distribution and JDK
  toolchain if they aren't already cached locally.

## Local Infrastructure (Postgres via Docker Compose)

Both services need their database running before `bootRun` — start it first:

```bash
docker compose -f docker/docker-compose.yml up -d
```

This brings up two independent Postgres containers, `order-db` (port `5433`) and
`inventory-db` (port `5434`), each with its own named volume (see ADR-0003 for why
they're separate rather than shared).

To fully reset local state — drops both containers **and their volumes**, so all data
is gone, not just stopped — for example after hand-editing an already-applied Flyway
migration and needing a clean slate rather than a checksum mismatch on next boot:

```bash
docker compose -f docker/docker-compose.yml down -v
```

Run `up -d` again afterward to recreate both containers from scratch; Flyway will
reapply migrations from `V1` on the next `bootRun` since the schema history table was
wiped along with everything else.

To reset **only one** service's database — e.g. you hand-edited an already-applied
migration for `inventory-service` only, and `order-db`'s data is fine and shouldn't be
touched — name the service after `down -v`:

```bash
docker compose -f docker/docker-compose.yml down -v inventory-db
docker compose -f docker/docker-compose.yml up -d inventory-db
```

Verified directly (2026-09-14): this removes only `inventory-db`'s container and its
own named volume (`inventory_data`) — `order-db`'s container, volume, and data are left
running and untouched. This is the practical proof of the per-service isolation
Milestone 0.2 established: even the *tooling* for resetting one service's data can't
accidentally reach into another's.

## How to Build

From the repository root:

```bash
./gradlew build
```

## How to Run `order-service`

```bash
./gradlew :order-service:bootRun
```

Health check: `http://localhost:8080/actuator/health`

## How to Run `inventory-service`

```bash
./gradlew :inventory-service:bootRun
```

Health check: `http://localhost:8080/actuator/health`

## Ports

Neither service has a configured port yet — both default to Spring Boot's standard
port **8080**. To run both at once locally, override one:

```bash
./gradlew :inventory-service:bootRun --args='--server.port=8081'
```

Per-service port configuration is expected as part of Milestone 0.2.

## Checking Database Structure and Content

To check a table's structure, list tables, or view content using `psql` inside the `order-db` container, use the following commands:

#### List All Tables
```bash
docker exec distributed-microservices-order-db-1 psql -U order -d order-db -c "\dt"
```

#### Structure (Columns, Types, Constraints)
```bash
docker exec distributed-microservices-order-db-1 psql -U order -d order-db -c "\d orders"
```

#### Content (All Rows)
```bash
docker exec distributed-microservices-order-db-1 psql -U order -d order-db -c "SELECT * FROM orders;"
```

#### Inventory Database Examples
For the `inventory-db` container, swap in the corresponding names (user: `inventory`, database: `inventory-db`, container: `distributed-microservices-inventory-db-1`):

```bash
docker exec distributed-microservices-inventory-db-1 psql -U inventory -d inventory-db -c "SELECT * FROM inventory_items;"
```

#### Interactive Session
If you prefer to stay inside an interactive `psql` session instead of running one-off `-c` commands, execute:

```bash
docker exec -it distributed-microservices-order-db-1 psql -U order -d order-db
```

Once inside the prompt, you can run your queries directly:
```sql
\dt
\d orders
SELECT * FROM orders;
```


## Documentation

- [`ROADMAP.md`](ROADMAP.md) — curriculum, milestones, and current progress
- [`CLAUDE.md`](CLAUDE.md) — operating rules for how this repository is developed
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — current and target system architecture
- [`ADR/`](ADR/) — architecture decision records
