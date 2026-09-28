# ADR-0010: Scheduled dispute corpus ingestion — source of truth, idempotency, and run correlation

Status: Accepted
Date: 2026-09-26, revised 2026-09-28 to reflect what was actually implemented and
verified (see `ROADMAP.md` Milestone 8.3) rather than only the original design

## Context

Milestone 8.2 proved chunk → embed → store composes for one manually-triggered
document. A real system needs this to happen on its own, repeatedly, against a real
corpus that changes over time — without re-embedding unchanged documents, without one
bad document aborting everything else, and with real visibility into what a run
actually did.

A prerequisite decision was made before this one: Kafka was considered for decoupling
ingestion from any synchronous path and rejected for now, since the project's actual
execution order places Phase 8 before Phase 3/4 — Kafka doesn't exist in this codebase
yet, and pulling it forward would mean teaching it shallowly before its own dedicated
milestone. A scheduled batch job was chosen instead, matching this corpus's actual
change frequency (days/weeks, not seconds). This ADR covers the four decisions that
followed from designing that scheduled job for real.

## Decision 1 — Source of truth: scanned filesystem directory, not a new raw-document table

**Options considered:** (a) a new database table holding raw source-document content,
scanned/authored some other way; (b) a directory of `.txt` files on disk, scanned
directly.

**Decision:** (b). Nothing in this project currently authors documents into a
database — no upload mechanism, no admin UI, no other service writing policy text
anywhere. A raw-content table would be a second, currently-unpopulated source of truth
for exactly the same bytes the filesystem already holds, introduced on pure
speculation — the same reasoning ADR-0009 used to reject an ANN index and Milestone
8.1's Q4 used to reject an early repository class. This does not mean no new table at
all: idempotency needs a durable place to compare content hashes against, and that
need is concrete and immediate, not speculative — see Decision 2.

## Decision 2 — Idempotency schema: a tracking table, not content duplication

**Options considered:** (a) store a hash directly on `dispute_documents` rows and infer
document-level state from chunk-level rows; (b) a separate `source_documents(id,
source_identifier, content_hash, ingested_at)` table, with a nullable
`source_document_id` FK on `dispute_documents`.

**Decision:** (b). Chunks and source documents are different things with a one-to-many
relationship; inferring source-document state from a variable number of chunk rows
(especially once chunking strategies vary, per Phase 8's own stated comparison plan)
is fragile. A dedicated tracking table makes "is this document unchanged" a single,
direct lookup, and makes "replace this document's chunks" a clean
`DELETE ... WHERE source_document_id = ?` rather than inference. The FK is nullable
specifically so the existing `ingest(String)` method (Milestone 8.2, kept as the
manual/ad-hoc path) keeps working completely unchanged.

**FK delete behavior, decided during implementation, not originally deliberate:** the
migration as written omits `ON DELETE CASCADE` (an earlier draft of this ADR specified
it), so Postgres's default applies — deleting a `source_documents` row while
`dispute_documents` rows still reference it fails with a foreign-key violation rather
than silently cascading. Reviewed after the fact and kept deliberately, not fixed:
this matches the same explicit-over-implicit principle `ADR-0004` already established
for this project (plain JDBC over JPA specifically to keep database operations
visible rather than hidden behind framework-managed automatic behavior). A `CASCADE`
delete would let one `DELETE` on a parent row silently discard every chunk row that
depended on it as an invisible side effect; the current default forces whoever
eventually builds document-deletion (out of scope for this milestone) to delete the
dependent chunks as an explicit, visible step first. Revisit only if that future
deletion feature finds the explicit two-step delete genuinely burdensome in practice,
not preemptively.

## Decision 3 — Cron, not fixed-delay

**Options considered:** `@Scheduled(fixedDelay = ...)` vs. `@Scheduled(cron = ...)`.

**Decision:** cron. This corpus changes on a calendar cadence — "run once nightly
during low traffic" is the actual intent, which `cron` expresses directly. `fixedDelay`
is the correct tool for a tight polling loop with no calendar semantics, which this
isn't; choosing it here would send the wrong signal about what kind of job this is to
anyone reading the code later.

## Decision 4 — A distinct `ingestionRunId` MDC key, not reused `correlationId`

**Options considered:** (a) reuse the existing `correlationId` MDC key from `common`'s
`CorrelationIdFilter`; (b) generate a fresh, distinct `ingestionRunId` per scheduled run.

**Decision:** (b). `CorrelationIdFilter` is a Servlet `Filter`, populated from an
inbound HTTP request header — a `@Scheduled` method has no inbound request to inherit
one from, so the filter simply never fires for this code path; there is nothing to
reuse. Beyond the mechanical impossibility, reusing the same MDC key name for a
locally-generated, never-cross-service-propagated batch-run identifier would conflate
two genuinely different concepts under one log field — a per-HTTP-request identifier
this project already treats as something propagated via a header, versus a per-batch-
run identifier that never leaves this process. A fresh `UUID`, generated at the start
of each run and removed in a `finally`, keeps them distinct.

## Decision 5 — `reingestFromSource` returns a typed `IngestionOutcome`, not `void`

**Options considered:** (a) `void`, with the caller inferring "no exception means
success" and having no way to distinguish a freshly re-embedded document from one
skipped as unchanged; (b) a two-value enum, `IngestionOutcome { INGESTED,
SKIPPED_UNCHANGED }`, returned by `reingestFromSource` and consumed by the runner.

**Decision:** (b), found necessary during implementation review, not part of the
original design. With `void`, `DisputeCorpusIngestionRunner` had no way to tell these
two cases apart — a run where every document was freshly re-embedded and a run where
every document was skipped as unchanged both produced an identical summary
(`succeeded=N, failed=0`). That directly undermines this ADR's own stated goal: real
observability into what a run actually did. An enum is enough — no need for a richer
result type — since there are exactly two non-failure outcomes and the runner only
ever needs to increment one of two counters based on which one came back.

## A Bug Found During Design, and Its Fix

The first draft of the ingestion runner updated `source_documents.content_hash` to the
*new* hash **before** calling the `@Transactional` re-ingestion method. If embedding
failed partway through that method, its transaction would roll back the chunk
changes — but the hash update, made as a separate, already-committed prior write,
would not. Net effect: the tracking table would say "this document is at the new
hash" while the actual stored chunks were still the *old* ones — a failed
re-ingestion permanently mis-marked as successful, silently skipped forever on every
future run. This is a strictly worse failure mode than a loud crash: it hides a real
data-staleness problem behind an apparently-healthy tracking table.

**Fix:** the hash upsert moves inside the transactional method, alongside the chunk
delete/insert, so the whole sequence — check-if-unchanged, upsert hash, delete stale
chunks, insert new ones — is one atomic unit. Any failure anywhere in it rolls back
everything, hash included, so a failed attempt is never mistaken for a successful one.

## Consequences

- `DisputeDocumentIngestionService` gains a dependency on `SourceDocumentRepository`
  it didn't previously need, to make the single-transaction design possible.
- The existing `ingest(String)` method and its test are untouched — tracking only
  applies to the new scheduled path.
- No concurrency guard exists between overlapping runs beyond Spring's default
  single-threaded scheduler (which already prevents two *scheduled* fires from
  overlapping) — no manual trigger exists yet for one to race against; revisit if one
  is ever added.
- Deleting a `.txt` file from the source directory does not clean up its
  `source_documents`/`dispute_documents` rows — only create/change is handled, matching
  what this milestone was actually scoped to solve.
- No index on `dispute_documents.source_document_id` — same "no index before a
  measured need" discipline as ADR-0009, applied consistently rather than assumed to
  only apply to the original decision it was written for.
- `BoundedSentenceChunker` (Milestone 8.2) and `SourceDocumentScanner` (this milestone)
  were both changed from field-initialized (`new X()` hardcoded directly on the field)
  to real `@Component` beans, constructor-injected into `DisputeDocumentIngestionService`
  and `DisputeCorpusIngestionRunner` respectively — not an architectural decision on
  its own, but a real, demonstrated pattern (the same hardcoding choice recurring a
  second time across two milestones) fixed for consistency and testability once it
  stopped being a one-off. `DisputeDocumentIngestionServiceTest` deliberately still
  passes a real `BoundedSentenceChunker` rather than a mock, since that test's
  determinism depends on real chunking behavior — making a dependency injectable
  doesn't obligate every caller to fake it.

## Rejected Alternatives

- **A raw source-document table**: rejected — a second, speculative source of truth
  for content the filesystem already holds, with no current authoring mechanism to
  populate it.
- **Hash-on-`dispute_documents`, no tracking table**: rejected — fragile once
  chunk-per-document counts vary, and makes "replace this document's chunks" an
  inference instead of a direct operation.
- **`fixedDelay`**: rejected — wrong semantic signal for a job with calendar-oriented
  intent, not a tight polling loop.
- **Reusing `correlationId` for the run ID**: rejected — mechanically impossible to
  populate from a `@Scheduled` context the way it's currently wired, and conceptually
  conflates two different kinds of identifier even if it were wired to work.
- **`void` return from `reingestFromSource`**: rejected once its consequence was
  noticed during review — it silently erases the skipped/ingested distinction this
  ADR itself requires the run summary to report.
- **`ON DELETE CASCADE`**: rejected on review, in favor of the project's established
  explicit-over-implicit principle — an automatic cascading delete would hide a real,
  potentially destructive side effect behind an innocuous-looking single-table delete.
