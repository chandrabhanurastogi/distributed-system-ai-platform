# ADR-0001: Multi-module Gradle monorepo over per-service repos

Status: Accepted
Date: 2026-09-12

## Context

This project will eventually contain several services (`order-service`,
`inventory-service`, and others added only as curriculum phases justify them). We need
to decide, up front, whether each service lives in its own repository or whether all
services live in one repository as Gradle subprojects sharing one root build.

## Options Considered

1. **One repo per service.** Closest to how independently-deployable microservices are
   usually run in production — each service has its own CI pipeline, its own version
   history, its own access control.
2. **Multi-module monorepo, one Gradle root, shared build conventions.** All services
   live under one root project; common dependency versions and plugin config live once
   in the root `build.gradle`'s `subprojects {}` block.
3. **Monolith (single Spring Boot app, multiple packages).** Rejected outright — this
   project's explicit purpose is to learn distributed-systems failure modes, which
   requires actual process/network boundaries between services.

## Decision

Multi-module monorepo (Option 2).

This is a **learning-project decision, not a production-default endorsement.** The
reason: this project's unit of work is a *milestone that often touches two services at
once* (e.g., "add a synchronous call from order-service to inventory-service and observe
a timeout"). A monorepo lets one commit and one PR capture that whole unit of learning.
Splitting into per-service repos would scatter a single conceptual change across
multiple repos and multiple commit histories, which actively works against the "one
milestone, one reviewable unit" model this project runs on.

Production teams often do choose per-repo-per-service for independent deployability and
blast-radius isolation of CI/CD — that trade-off is real and will be called out
explicitly when Phase 14 (capstone) discusses deployment, but it is not the right
trade-off for this project's current goal.

## Consequences

- Shared Gradle plugin versions and dependency versions live in one place (root
  `build.gradle`), so there's no risk of the two services silently drifting onto
  different Spring Boot versions by accident.
- A single `git log` shows the full history of the whole system, which matters for a
  project meant to be explained and defended later.
- Loses independent versioning/release cadence per service — acceptable here since
  nothing is actually deployed independently yet.
- If this project later adds a CI pipeline (Phase 0 backlog item), it will need path
  filters or a build matrix to avoid rebuilding every service on every change — noted
  here so it isn't a surprise later.

## Rejected Alternatives

- **Per-service repos**: rejected for now because it fragments milestones that
  deliberately span two services, and because there is no independent-deployment
  requirement yet that would justify the coordination overhead.
- **Monolith**: rejected because the entire point of this project is to encounter and
  solve distributed-systems problems that only exist across a real process boundary.
