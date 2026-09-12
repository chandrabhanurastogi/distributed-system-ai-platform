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

## Documentation

- [`ROADMAP.md`](ROADMAP.md) — curriculum, milestones, and current progress
- [`CLAUDE.md`](CLAUDE.md) — operating rules for how this repository is developed
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — current and target system architecture
- [`ADR/`](ADR/) — architecture decision records
