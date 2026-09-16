# Architecture Decision Records

An ADR is written when a decision was hard enough to argue about — not for every
choice made in the project. Rule of thumb: if a future session (or a future you) might
reasonably ask "wait, why did we do it this way instead of the obvious alternative?",
it needs an ADR. Routine implementation choices don't.

## Format

```markdown
# ADR-NNNN: Title

Status: Proposed | Accepted | Superseded by ADR-XXXX | Deprecated
Date: YYYY-MM-DD

## Context
What problem are we solving? What constraints apply?

## Options Considered
Each real option, with its actual trade-offs — not a strawman list where one option
is obviously correct.

## Decision
What we chose, and the specific reason it won.

## Consequences
What this makes easier, what this makes harder, what we're accepting as a cost.

## Rejected Alternatives
Why the other options lost — specifically, not just "we preferred X."
```

## Index

| ADR | Title | Status |
|---|---|---|
| [0001](0001-multi-module-gradle-monorepo.md) | Multi-module Gradle monorepo over per-service repos | Accepted |
| [0002](0002-java-toolchain.md) | Java 25 (LTS) pinned via Gradle toolchain + auto-provisioning | Accepted |
| [0003](0003-local-postgres-per-service.md) | Local Postgres container per service, not shared, not cloud-hosted | Accepted |
| [0004](0004-plain-jdbc-and-flyway.md) | Plain JDBC over Spring Data JPA, and Flyway over Liquibase | Accepted |
| [0005](0005-deliberate-concurrency-bug.md) | Ship a known lost-update race in `reserve`, on purpose, for Phase 1 | Accepted |
| [0006](0006-shared-common-module.md) | Introduce a shared `common` module for cross-cutting infrastructure | Accepted |
