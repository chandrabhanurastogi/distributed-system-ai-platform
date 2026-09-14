# ADR-0004: Plain JDBC over Spring Data JPA, and Flyway over Liquibase

Status: Accepted
Date: 2026-09-14

## Context

Milestone 0.3 introduces the first persisted entities (`Order` in `order-service`,
`InventoryItem` in `inventory-service`) and therefore requires two decisions the
project had deliberately deferred until real persistence was on the table (see
`CLAUDE.md` Known Existing Debt discussion and ADR-0003's own precedent of not
pre-deciding things before they're needed):

1. How schema changes get applied and versioned (a migration tool).
2. How Java code talks to the database (an ORM vs. a lower-level data access
   abstraction).

Both were open questions with a real, examined alternative on each side — not a case
of one option being obviously correct.

## Options Considered

**Migration tool:**
1. **Flyway** — versioned, plain `.sql` files, applied in filename order, tracked in a
   `flyway_schema_history` table.
2. **Liquibase** — changesets (XML/YAML/JSON, or its own SQL-like dialect), with
   built-in rollback orchestration and multi-database-dialect abstraction.

**Data access technology:**
1. **Spring Data JPA / Hibernate** — entities annotated and mapped declaratively;
   Hibernate generates SQL, manages a session cache, and defers writes via
   dirty-checking until flush.
2. **Plain JDBC** (`NamedParameterJdbcTemplate` + hand-written `RowMapper`s) — every
   SQL statement and transaction boundary is explicit, hand-written code.

## Decision

Flyway, and plain JDBC.

**Flyway** was the low-friction choice: nothing in this project's curriculum needs
Liquibase's rollback machinery or cross-database portability, and Flyway's plain-SQL
model has less indirection to learn — a meaningful consideration given zero prior
exposure to either tool.

**Plain JDBC** was the deliberate, examined choice, made for a reason specific to
*this* project rather than data-access technology in general: Phase 1 of `ROADMAP.md`
requires directly observing transaction isolation levels, `SELECT ... FOR UPDATE`
locking, and race conditions under concurrent access. Hibernate's session cache and
deferred-flush behavior sit directly on top of that mechanism and would obscure it —
debugging would mean reasoning about Hibernate's flush timing instead of the actual
database-level locking behavior under study. Plain JDBC guarantees every SQL statement
and every transaction boundary in Phase 1's experiments is something explicitly
written, not something generated at a time of Hibernate's choosing.

## Consequences

- More boilerplate now: hand-written `RowMapper`s and explicit SQL for every
  operation, no automatic cascades or relationship management if/when entities
  eventually gain relationships.
- Full visibility into every statement and transaction boundary — a necessary
  condition for Phase 1's isolation-level and locking experiments to be legible and
  defensible in an interview setting, not just something that happened to work.
- Flyway migrations are forward-only in the community tooling used here — there is no
  built-in "undo"; fixing a bad migration means writing a new forward migration, not
  rolling one back. Accepted as normal Flyway practice, not a gap specific to this
  decision.

## Rejected Alternatives

- **Spring Data JPA**: rejected for this project's specific curriculum need, not
  rejected on general merit. It remains the correct default for most production
  CRUD-heavy teams optimizing for time-to-ship — which is precisely why it was the
  unexamined default going into this decision. This project accepts slower, more
  explicit persistence code as the cost of the locking/isolation visibility Phase 1
  needs. Revisit at the Phase 14 capstone: the "production-oriented" final system may
  reasonably choose JPA once the underlying locking/isolation mechanics are already
  understood by hand — this decision is scoped to the learning phases (0–3ish, wherever
  the hand-written-SQL visibility stops mattering), not asserted as a permanent stance.
- **Liquibase**: rejected — no requirement in this curriculum exercises its
  changeset/rollback orchestration or multi-database-dialect abstraction. Revisit only
  if a future phase specifically needs cross-database portability or complex rollback
  semantics (not currently scheduled).
