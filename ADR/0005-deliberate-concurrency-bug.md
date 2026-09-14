# ADR-0005: Ship a known lost-update race in `reserve`, on purpose, for Phase 1

Status: Accepted
Date: 2026-09-14

## Context

Milestone 0.4 introduces `InventoryService.reserve(sku, quantity)` — the first
operation in this codebase that reads a value and then conditionally writes based on
what it read. Two implementations were available:

1. **Naive**: a separate `SELECT` (via `findBySku`) followed by a separate `UPDATE`
   (via `updateQuantity`), wrapped in `@Transactional` for atomicity of the service's
   own two statements only.
2. **Atomic conditional update**: a single `UPDATE inventory_items SET quantity =
   quantity - :n WHERE sku = :sku AND quantity >= :n`, checking rows-affected, with no
   separate read step at all.

Option 2 is safe under concurrency without needing any locking — Postgres evaluates
the `WHERE` clause and the write atomically within one statement. Option 1 has a real,
reproducible lost-update race: two concurrent callers can both read the same quantity
before either writes, both decide "sufficient," and both write back a decremented
value — overselling in the process.

`ROADMAP.md`'s Phase 1 is reserved for directly experiencing race conditions and
locking strategies (optimistic vs. pessimistic) as a hands-on experiment, not an
abstract description. This decision was tested in-session: after Milestone 0.4's
interview walked through the exact interleaving that causes the race, the human asked
to implement pessimistic locking immediately, inside Milestone 0.4. That request was
named as a direct conflict with this plan and the human consciously chose to stay on
plan rather than jump ahead — the strongest possible confirmation that leaving the bug
in was the right call, since the temptation to fix it immediately was real and
deliberately not acted on.

## Decision

Implement Option 1 (naive, racy) in Milestone 0.4. Leave the race in place,
undocumented in code but explicitly documented in `ROADMAP.md` and here. Phase 1 will:
measure the actual oversell rate under real concurrent load (not a synthetic claim),
then apply and compare optimistic locking (a version/quantity check in the `UPDATE`'s
`WHERE` clause, detecting the conflict and failing or retrying) against pessimistic
locking (`SELECT ... FOR UPDATE`, blocking the second reader until the first
transaction commits), measuring the throughput/latency trade-off of each against the
now-fixed baseline.

## Consequences

- `inventory-service` currently has a real, exploitable data-integrity bug in
  production terms — acceptable here because this is a learning project with no real
  users or real money at stake, and the bug is knowingly shipped and tracked (`CLAUDE.md`
  → Known Existing Debt), not accidentally shipped and forgotten.
- Phase 1 gets a genuine baseline to measure against, rather than a synthetic race
  condition example — the "before" numbers in any Phase 1 benchmark will be real,
  reproduced measurements against this exact code, satisfying `CLAUDE.md` Rule 9 (no
  fabricated benchmark numbers) in the strongest possible way: the baseline is the
  actual current system, not a hypothetical.
- Anyone reading `InventoryService.reserve` without this ADR or the `ROADMAP.md` note
  could reasonably mistake this for an oversight rather than a decision — mitigated by
  the Javadoc comment already on the method and this ADR.

## Rejected Alternatives

- **Atomic conditional `UPDATE`** (Option 2 above): rejected *for this milestone
  specifically*, not on general merit — it's arguably the better production pattern
  for this exact operation, since it avoids the race without needing any locking
  strategy at all. Rejected here because using it now would mean Phase 1 has nothing
  broken to fix, undermining the hands-on experiment the phase is built around. This
  option remains a legitimate real-world alternative to raise during Phase 1's
  discussion of "did we actually need locking, or would an atomic statement have been
  enough" — that comparison is itself good interview material and shouldn't be
  foreclosed by fixing the bug too early.
