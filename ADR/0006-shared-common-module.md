# ADR-0006: Introduce a shared `common` module for cross-cutting infrastructure

Status: Accepted
Date: 2026-09-16

## Context

Milestone 0.1's original interview questions (2026-09-12) asked, unanswered until now:
"When (if ever) would you introduce a `common`/shared-kernel module, and what's the
risk of introducing it too early?" Milestone 0.4 answered part of this concretely: a
`CorrelationIdFilter` was duplicated identically between `order-service` and
`inventory-service` rather than shared, on the reasoning that "two small filter
classes don't meet the bar for introducing one."

Milestone 6.1 (`llm-fundamentals`) now needs the same filter a third time.

## Options Considered

1. **Duplicate a third time.** Consistent with the precedent set in Milestone 0.4.
   Zero coupling risk, but three identical copies of the same class to keep in sync
   if the logic ever changes — the exact risk that justifies a shared module once
   repetition is real rather than speculative.
2. **Extract a shared `common` module now**, containing only genuinely cross-cutting,
   non-domain-specific infrastructure (starting with `CorrelationIdFilter`), consumed
   by all three (and future) modules as a dependency.

## Decision

Option 2. Two instances of duplication (Milestone 0.4) were correctly judged
insufficient to justify the coupling cost of a shared module — that was not a mistake,
it was the right call with the information available at the time. A third identical
instance is a different situation: this is no longer a hypothetical "might need this
again," it's a demonstrated, repeated pattern. This is precisely the bar Milestone
0.1's original question was testing for, and Milestone 0.4's restraint is what makes
this decision now well-justified rather than reflexive — the module isn't being
introduced speculatively, it's being introduced because speculation already failed to
apply twice and a third repetition made the need concrete.

## Consequences

- `common` is a Gradle subproject like the others, but is **not** a Spring Boot
  application — no main class, `bootJar` disabled, plain `jar` enabled instead. It
  exists to be depended on, not run.
- Cross-module component scanning does not happen automatically: `common`'s classes
  live outside `order-service`/`inventory-service`/`llm-fundamentals`'s own base
  packages, so Spring Boot's default component scan (base package + sub-packages of
  the `@SpringBootApplication` class) will not find them. `common` exposes its beans
  via a proper Spring Boot auto-configuration (`@AutoConfiguration` +
  `META-INF/spring/...AutoConfiguration.imports`) — the same mechanism this project
  spent all of Milestone 0.3/0.4/6.1 learning about from the consumer side (Flyway,
  Testcontainers, MockMvc, TestRestTemplate all work this way). This is the first
  milestone where that mechanism is built, not just relied upon.
- Scope discipline going forward: `common` holds only genuinely cross-cutting,
  domain-free infrastructure (correlation IDs, and whatever earns the same 3x-repeated
  bar later — not business logic, not anything specific to orders/inventory/disputes).
  Adding something to `common` "because it might be useful elsewhere" without a third
  real instance would repeat the exact premature-abstraction risk Milestone 0.1's
  question warned about.

## Rejected Alternatives

- **Continued duplication**: rejected once repetition became a demonstrated pattern
  (3 instances) rather than a hypothetical (2 instances, correctly left alone in
  Milestone 0.4).
