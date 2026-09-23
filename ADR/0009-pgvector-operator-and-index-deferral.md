# ADR-0009: `dispute-service` uses `vector_cosine_ops`, no ANN index yet

Status: Accepted
Date: 2026-09-23

## Context

Milestone 8.1 introduces `dispute-service`'s `pgvector`-backed `dispute_documents`
table. Two real decisions were required before any query could be written: which
`pgvector` operator class to query and (eventually) index with, and whether to add an
approximate-nearest-neighbor (ANN) index at this milestone at all.

`nomic-embed-text` (the embedding model used throughout Phase 7) was measured in
Milestone 7.2 to produce vectors with magnitude `~0.9999998` — essentially unit
length. Milestone 7.2's own closing interview named the general trap directly:
treating an observed property of one model's current output as a guaranteed
invariant, rather than as a fact that could silently stop being true.

## Options Considered — operator class

1. **`vector_l2_ops`** (`<->`, Euclidean distance).
2. **`vector_ip_ops`** (`<#>`, negative inner product / dot product).
3. **`vector_cosine_ops`** (`<=>`, cosine distance).

## Options Considered — indexing

1. **Add an IVFFlat index now.**
2. **Add an HNSW index now.**
3. **No index — sequential scan, exact results, defer indexing.**

## Decision

**Operator class: `vector_cosine_ops`.**

`vector_l2_ops` is rejected on semantic grounds independent of normalization — L2
distance conflates magnitude and direction into a single number. Two semantically
aligned sentences that happened to pool into vectors of different magnitude (a real
effect of pooling strategy, sentence length, and token count, even within one model)
would register as "far apart" under L2 despite pointing the same direction. This is
the wrong tool for embedding similarity regardless of what any specific model's
magnitude behavior turns out to be.

`vector_ip_ops` is rejected because its correctness is *conditional*, not intrinsic:
raw inner product only equals cosine similarity when every vector involved is
exactly unit-length. That is not a property of the operator — it is a property that
would have to hold for every row in the table, forever, including every future
write, and nothing in the schema enforces it (no default `CHECK` constraint on
magnitude ships with `pgvector`). `dispute-service`'s corpus is specifically the
place in this project under the most pressure to violate that assumption: Phase 8's
own stated goal involves comparing chunking strategies and retrieval methods
experimentally, which means re-embedding this exact corpus repeatedly, plausibly
with different models or model versions across benchmark runs — precisely the
"careless migration" scenario already traced for `BruteForceRetriever` in Milestone
7.2 (a mixed-dimensionality corpus there failed loudly; a mixed-normalization corpus
under `vector_ip_ops` would fail silently, producing plausible-looking wrong
rankings instead). Choosing `vector_ip_ops` today would be the same mistake,
relocated from application code into a database index.

`vector_cosine_ops` is correct regardless of whether input vectors happen to be
normalized — it measures what actually matters for embedding similarity (direction),
not an incidental byproduct of one model's current behavior.

**Indexing: none, for now.**

IVFFlat specifically cannot be built meaningfully against a near-empty table — its
clustering step (the `lists` parameter) needs a real, representative sample of
vectors present at build time to produce meaningful clusters; building it now would
produce a degenerate index requiring a rebuild once real data exists anyway, paying
the cost twice rather than deferring it. HNSW does not have that specific build-time
problem (it builds incrementally), but the deeper issue applies to both: an ANN
index converts an exact nearest-neighbor query into an approximate one, trading real
recall for query speed that has not been measured to be a problem. Adding that
trade-off with zero real query-latency numbers is the same category of mistake as
fabricating a benchmark — Rule 9's discipline applied to justifying an architectural
choice, not only to reporting a number.

Without an index, `ORDER BY embedding <=> query_vector` still works correctly via a
sequential scan — Postgres computes the exact cosine distance for every row, the
same computational shape as `BruteForceRetriever.topK`, just moved from a Java loop
into the database's execution engine. Nothing about this milestone's retrieval logic
is blocked by the absence of an index.

## Consequences

- Every future write to `dispute_documents` is implicitly relying on `pgvector`'s
  cosine operator, which does not require or assume normalized input — this is a
  genuinely safer default than `vector_ip_ops` for a table expected to be
  re-embedded repeatedly across Phase 8's planned experiments.
- Query performance will degrade linearly as the corpus grows, with no index to
  bound it. This is accepted and expected to be revisited, not permanent: once
  `dispute-service` has a real corpus size from actual ingestion and Phase 8's
  planned chunking/retrieval benchmarking is running, real sequential-scan query
  latency should be measured at that size, and an index added only if that number
  shows an actual problem — HNSW would be the reasonable default to try first
  (better recall/build-time trade-off than IVFFlat in most current community
  benchmarks), but even that should be checked against this project's own measured
  numbers before being trusted.
- If `dispute-service`'s embedding model ever changes to one that does not normalize
  its output, this decision does not need to be revisited — `vector_cosine_ops`
  remains correct either way, which is precisely the point of choosing it now.

## Rejected Alternatives

- **`vector_l2_ops`**: rejected — wrong semantic tool for direction-based similarity,
  independent of any model's normalization behavior.
- **`vector_ip_ops`**: rejected — correctness depends on an unenforced, easily-broken
  invariant (unit-length vectors) in exactly the table most likely to violate it.
- **IVFFlat now**: rejected — cannot be built meaningfully against near-empty data;
  would require a rebuild once real data exists, paying the indexing cost twice.
- **HNSW now**: rejected — no measured latency problem exists yet to justify trading
  exact results for approximate ones.
