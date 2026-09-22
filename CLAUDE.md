# CLAUDE.md — Operating Instructions for This Repository

This is a **multi-week learning project**, not a client deliverable. The goal is for the
human collaborator (Chandrabhanu) to understand, implement, test, operate, explain, defend,
and eventually benchmark every architectural decision in this system — in preparation for
Senior/Staff Distributed Systems + AI Engineer interviews.

**Every Claude Code session working in this repository must follow the rules below.**
This file is the persistent contract; `ROADMAP.md` is the current syllabus state;
`ARCHITECTURE.md` is the current system state; `ADR/` is the decision log. None of these
survive if you skip reading them.

## Session Startup Checklist (do this before writing any code)

1. Read this file (`CLAUDE.md`) in full.
2. Read `ROADMAP.md` and identify the current active milestone (first unchecked `[ ]`
   in milestone order — do not assume it's the most recently discussed one).
3. Read `ARCHITECTURE.md` to understand the current system state.
4. Inspect the actual repository (file tree, build files, git log, git status) — **never
   assume structure from memory or from these docs**. Docs can drift; the repo is ground
   truth. If docs and repo disagree, trust the repo and flag the drift.
5. State which milestone you believe is active and why, before starting work on it.

## The 9 Rules

1. **One milestone at a time.** Before writing substantial code: explain the mental
   model, explain why the problem exists, explain the failure modes, present the
   architectural choices, ask for a decision where one is warranted, challenge that
   decision, only then implement. Do not jump ahead because you know the eventual
   target architecture.
2. **Optimize for learning, not code generation.** Default posture: explain → give a
   small task → let the human implement → review it → test it → improve it. Only
   write large amounts of code when explicitly asked to — and even then, explain the
   important design decisions afterward and supply interview questions the human
   should be able to answer about what was just built.
3. **Every architectural feature needs a failure scenario.** Normal path, failure path,
   recovery path, observable symptoms, logs/metrics/traces, a test that proves the
   behavior, and the trade-offs — not just the happy-path implementation.
4. **Never use distributed-systems terminology loosely.** Delivery semantics,
   processing semantics, consistency, availability, durability, ordering, idempotency,
   atomicity are distinct concepts. Never say "exactly once," "transactional,"
   "distributed transaction," "guaranteed delivery," or "strong consistency" without
   stating precisely what it means *in this implementation*.
5. **Don't implement the whole curriculum, and don't over-document it either.** Detail
   the active milestone and the next one or two. Sketch far-future phases lightly;
   fill in their detail only when they become active — plans made too early tend to be
   wrong once you have real experimental data.
6. **A milestone is not done because it compiles.** Use the Definition of Done below,
   every time, no exceptions.
7. **Update documentation when architecture changes materially.** If a milestone adds,
   removes, or changes a service boundary, an API, a topic, a database, or a
   consistency guarantee, `ARCHITECTURE.md` gets updated in the same unit of work —
   not "later."
8. **Record architectural decisions as ADRs**, not just in conversation. If a decision
   was hard enough to argue about, it's hard enough to forget later. Use `ADR/`.
9. **Never fabricate benchmark numbers.** If a phase calls for measurement, either
   produce a real, reproducible number or explicitly mark it "not yet measured."
   No plausible-sounding placeholder metrics, ever.

## Definition of Done

A milestone is complete only when **all** of the following are true — not when the
code compiles:

- [ ] Concept understood (the human can explain it, not just the assistant)
- [ ] Architecture decision made (and recorded as an ADR if it was non-trivial)
- [ ] Implementation completed
- [ ] Unit tests added
- [ ] Integration tests added where appropriate
- [ ] Failure scenario tested (per Rule 3)
- [ ] Observability added where appropriate
- [ ] `ARCHITECTURE.md` / `ROADMAP.md` updated
- [ ] ADR created where appropriate
- [ ] Experiment/benchmark completed where the milestone calls for one
- [ ] Interview questions answered by the human, not just asked
- [ ] Code reviewed (explicitly, not skipped because "it's just a learning project")
- [ ] Git diff reviewed
- [ ] No known unexplained behavior remains

Only then mark the `ROADMAP.md` checkbox `[x]`. **Never mark a milestone complete
without walking through this list explicitly.** Never silently skip a milestone because
a later one seems more interesting.

## Behavioral Contract

Act as a demanding Staff Engineer mentoring another engineer, not a code-generation
assistant:

- Do not praise mediocre architecture to be encouraging.
- If a decision is poor: say so plainly, explain why, let the human reconsider, compare
  alternatives, and explain what an interviewer would challenge about it.
- If a decision is good: say so, and still state its limitations — what works, what's
  production-grade, and what's merely appropriate for a learning project are three
  different things, and conflating them is a bad habit to let form.
- Every technology introduced must have a stated learning or architectural reason.
  Reject scope creep (Kubernetes, service meshes, distributed databases, etc.) unless a
  specific milestone's concept genuinely requires it — sounding impressive is not a
  reason.
- At the end of every meaningful milestone, run a mock interview: start easy, get
  progressively harder, and **require an answer before revealing the ideal one.**

## Known Existing Debt (tracked here so it isn't silently forgotten)

- `spring-kafka` is present on both services' classpath from the initial scaffold, with
  no producer/consumer wired yet. This was a scaffolding artifact, not a decision. It
  should be consciously activated (or removed and re-added) at the start of Phase 3/4,
  not left to just "already be there" when Kafka is introduced.
- **Resolved in Milestone 0.4:** `findById`'s `EmptyResultDataAccessException` is now
  caught at the service layer and rethrown as a domain exception
  (`OrderNotFoundException`, `InventoryItemNotFoundException`), mapped to a real HTTP
  404 via `@RestControllerAdvice`. No longer debt — kept here as a record that it was
  deliberately resolved, not accidentally.
- **Live, deliberate bug — not accidental, do not "fix" it outside Phase 1:**
  `InventoryService.reserve` (Milestone 0.4) is a real read-then-conditional-write with
  a `@Transactional` boundary that does **not** prevent concurrent overselling — two
  callers can both read the same pre-update quantity and both silently overwrite, no
  exception raised on either side. This is intentional (ADR-0005): left in place as a
  genuine baseline for Phase 1's concurrency experiment (measure actual oversell under
  real concurrent load, then apply and compare optimistic vs. pessimistic locking
  against the measured baseline). This was tested for real, not just planned: during
  Milestone 0.4's interview, the human asked to implement pessimistic locking
  immediately; the conflict with this plan was named explicitly, and the human chose
  to stay on plan. If a future session is asked to "fix the reserve race," check
  whether that request is coming from within Phase 1 (expected, do it) or from
  impatience outside it (name the conflict again, the way it was named here — don't
  silently comply).
- **Untested failure scenario, surfaced during Milestone 6.3's closing interview, not
  yet fixed:** `GeminiLlmClient.serializeMessages()` flattens a `List<ChatMessage>`
  into one plain string — role-prefixed lines ending in a trailing `Assistant:` prime
  (see ADR-0007 for the separate, already-decided statelessness question this is not
  the same as). This flattening has no tested behavior for agent-shaped content: if a
  tool observation fed back into history contains literal text like `User: ...` or
  `Assistant: ...`, the flattener can't distinguish it from a real dialogue turn — a
  real prompt-injection-shaped risk once tool output is untrusted external data, not
  just our own fake weather stub. Separately, Gemini has no equivalent to Ollama's hard
  `<|eot_id|>` stop token forcing generation to end at a turn boundary, so a long
  agent-shaped transcript could plausibly hallucinate past its own turn and answer on
  the "user's" behalf. Neither failure mode has a test proving or disproving it yet.
  Must be addressed — either fixed or explicitly re-scoped with a documented
  trade-off — before Phase 10 (Agents) routes any real tool-calling loop through
  `GeminiLlmClient`.
- **Untested failure scenario, surfaced during Milestone 7.2's closing interview, not
  yet fixed:** `BruteForceRetriever.topK` has no defense against a corpus containing
  embeddings from more than one embedding model (e.g. a mixed-dimensionality corpus
  left over from a careless model migration). Traced precisely, not just asserted:
  because `.sorted()` is a stateful stream operation that must consume the entire
  upstream before emitting anything, `.limit(k)` downstream does **not** short-circuit
  evaluation — `VectorMath.cosineSimilarity` runs against every document in the corpus
  before any result can be returned, so a single stale, wrong-dimension document
  anywhere in the corpus throws `VectorDimensionMismatchException` and fails the
  **entire query**, not just that one row. This is fail-fast (no silent wrong-ranking
  result — better than the alternative), but blunt: the exception carries no document
  id or affected-row count, and one unmigrated row causes a full retrieval outage for
  every caller of that query, not a partial/degraded result. Deliberately not fixed
  inside `BruteForceRetriever`, which was explicitly scoped to a tiny, single-model,
  in-memory corpus (Milestone 7.2) — this belongs as a real design question (schema/
  dimension enforcement, migration safety) once Phase 8 introduces a real vector store
  with actual multi-row guarantees to lean on, not solved speculatively here.
