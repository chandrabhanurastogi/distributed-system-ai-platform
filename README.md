# Distributed Microservices Platform

A multi-week, hands-on learning project for building, operating, and defending the
architecture of a distributed system from first principles — Spring Boot
microservices, resilience patterns, Kafka, distributed transactions, observability,
and (later) an AI/RAG/agents layer on top. This is a learning project, not a product:
see `ROADMAP.md` for the curriculum and `CLAUDE.md` for how work on this repository is
paced and reviewed.

## Services & Ports

| Service | Port | Database / Dependency | Swagger UI | Readme |
|---|---|---|---|---|
| **`order-service`** | `8080` | `order-db` (Postgres on `5433`) | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) | [`order-service/README.md`](order-service/README.md) |
| **`inventory-service`** | `8081` | `inventory-db` (Postgres on `5434`) | [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html) | [`inventory-service/README.md`](inventory-service/README.md) |
| **`llm-fundamentals`** | `8082` | Ollama (`http://localhost:11434`, `llama3.2`) | [http://localhost:8082/swagger-ui/index.html](http://localhost:8082/swagger-ui/index.html) | [`llm-fundamentals/README.md`](llm-fundamentals/README.md) |
| **`common`** | *(Library)* | None (Shared logging / filters) | N/A | [`common/README.md`](common/README.md) |

See `ARCHITECTURE.md` for the current and target system state.

## Prerequisites

- No local JDK or Gradle install required. The committed Gradle wrapper (9.7.1)
  auto-provisions a Java 25 (LTS) toolchain on first run via the Foojay resolver
  plugin.
- Internet access on first build, to download the Gradle distribution and JDK
  toolchain if they aren't already cached locally.

## Local Infrastructure (Postgres via Docker Compose)

Both database-backed services need their database running before `bootRun` — start them first:

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

## How to Build

From the repository root:

```bash
./gradlew build
```

## Running Services

Each service has a dedicated port preconfigured, so you can run all services simultaneously without passing port override arguments:

### Run `order-service` (Port 8080)
```bash
./gradlew :order-service:bootRun
```
- Health check: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

### Run `inventory-service` (Port 8081)
```bash
./gradlew :inventory-service:bootRun
```
- Health check: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
- Swagger UI: [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)

### Run `llm-fundamentals` (Port 8082)
```bash
./gradlew :llm-fundamentals:bootRun
```
- Health check: [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health)
- Swagger UI: [http://localhost:8082/swagger-ui/index.html](http://localhost:8082/swagger-ui/index.html)

## Stopping Services Running in Background (Resolving Port Conflicts)

If a service fails to start with `Web server failed to start. Port <PORT> was already in use`, an earlier instance or background process is holding the port.

### 1. Identify Which Process is Listening on a Port

Check active processes on ports `8080`, `8081`, or `8082`:

```bash
lsof -i :8080 -i :8081 -i :8082
```

Or for a specific port (e.g. `8081`):

```bash
lsof -i :8081
```

### 2. Terminate the Process by PID

Use `kill -9` with the PID reported in the `PID` column from `lsof`:

```bash
kill -9 <PID>
```

### 3. Kill Process by Port (One-Liner)

To automatically find and terminate whatever process is occupying a port:

```bash
# For order-service (port 8080)
kill -9 $(lsof -t -i:8080)

# For inventory-service (port 8081)
kill -9 $(lsof -t -i:8081)

# For llm-fundamentals (port 8082)
kill -9 $(lsof -t -i:8082)
```

### 4. Stop Lingering Gradle Daemons

If background Gradle tasks or daemons are keeping resources locked:

```bash
./gradlew --stop
```

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

- [`order-service/README.md`](order-service/README.md) — Order service endpoints, testing, and contract
- [`inventory-service/README.md`](inventory-service/README.md) — Inventory service endpoints, testing, and contract
- [`llm-fundamentals/README.md`](llm-fundamentals/README.md) — LLM fundamentals service endpoints, testing, and contract
- [`common/README.md`](common/README.md) — Shared library module
- [`ROADMAP.md`](ROADMAP.md) — curriculum, milestones, and current progress
- [`CLAUDE.md`](CLAUDE.md) — operating rules for how this repository is developed
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — current and target system architecture
- [`ADR/`](ADR/) — architecture decision records
