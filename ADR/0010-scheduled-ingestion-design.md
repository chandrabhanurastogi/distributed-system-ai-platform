# ADR-0010: Scheduled dispute corpus ingestion — source of truth, idempotency, and run correlation

Status: Proposed (design complete, implementation pending — see `ROADMAP.md` Milestone 8.3)
Date: 2026-09-26

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
