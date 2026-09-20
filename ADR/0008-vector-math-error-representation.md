# ADR-0008: Represent invalid/undefined vector operations as named exceptions, not sentinel values

Status: Accepted
Date: 2026-09-19

## Context

Milestone 7.1 implements `dotProduct`, `magnitude`, and `cosineSimilarity` from scratch.
Three distinct invalid-input situations can occur: a vector with zero length (no
dimensions at all), two vectors with mismatched dimensions, and — specific to cosine
similarity — a vector with zero magnitude, for which cosine similarity is
mathematically undefined (division by zero in the denominator). Each needed a decision:
how should the code represent "this input is invalid," and should the three cases be
distinguished from one another at all?

## Options Considered

**On representation of the zero-magnitude case specifically:**
1. **Return a sentinel value** — e.g. define cosine similarity of a zero vector as
   `0.0` by convention, matching what some libraries do.
2. **Return `Optional.empty()` / `NaN`** — signal "no valid result" without deciding a
   fabricated numeric answer.
3. **Throw a named, unchecked exception.**

**On how many exception types to use across all three invalid-input cases:**
1. **One generic type** (`IllegalArgumentException`, or a single custom exception) for
   every invalid-input case.
2. **Three distinct named exceptions** — one per genuinely different failure
   condition: `EmptyVectorException` (no dimensions), `VectorDimensionMismatchException`
   (mismatched dimensions), `ZeroVectorException` (zero magnitude).

## Decision

Zero-magnitude case: **throw** (`ZeroVectorException`), not a sentinel. Cosine
similarity is mathematically undefined when either vector has zero magnitude — not
conventionally zero, actually undefined, since the formula requires dividing by a
quantity that is zero. Returning `0.0` would let a caller (or a future reader of this
code) wrongly conclude "cosine similarity of a zero vector is zero," which is a false
mathematical claim quietly baked into the code. Throwing preserves the true statement
("undefined") instead of inventing a false one that happens to look plausible. This is
explicitly *not* claimed as "throwing is mathematically required" — mathematics says
only "undefined"; software still has to pick a representation for that. Throwing was
chosen because it best serves this milestone's actual purpose (teaching the real
mathematics, not designing a production-hardened retrieval API) by refusing to let a
fabricated answer masquerade as a real one.

Exception taxonomy: **three distinct types**, not one generic type. The three failure
conditions are operationally different, not just cosmetically different:
`EmptyVectorException` means "this input isn't a vector at all — investigate what
produced a zero-length array, almost certainly a bug upstream (e.g. a broken embedding
call)." `VectorDimensionMismatchException` means "these two vectors are individually
valid but cannot be compared — investigate model/schema compatibility (e.g. accidentally
mixing outputs from two different embedding models)." `ZeroVectorException` means "the
inputs are individually well-formed, but the specific operation requested is undefined
for this input — investigate why an embedding has no direction (a degenerate or
corrupted embedding)." A caller catching these needs to take different remediation
action for each; collapsing them into one type (or one message string) would erase that
distinction and force a caller back to string-parsing an exception message to figure out
what actually went wrong — exactly the kind of ambiguity structured fields on
`VectorDimensionMismatchException` (`getExpected()`/`getActual()`) already exist to
avoid.

An empty vector's dot product is a related but distinct case worth being precise about:
unlike the zero-magnitude case, an empty vector's dot product is not mathematically
undefined — an empty sum is conventionally `0`. `EmptyVectorException` is thrown anyway,
but the justification is different: an embedding vector with zero dimensions is invalid
for this domain, and silently returning `0.0` would let an upstream bug (a broken
embedding call producing no dimensions) masquerade as a successful computation. This is
an operational-validity decision, not a mathematical-necessity one — worth stating
precisely rather than conflating the two justifications.

## Consequences

- Precedence is fixed and tested explicitly in both directions: null check → empty
  check → dimension-mismatch check → (in `cosineSimilarity` only) zero-magnitude check.
  A vector that is simultaneously empty *and* mismatched in length against its
  counterpart always raises `EmptyVectorException`, never
  `VectorDimensionMismatchException` — this was a real ambiguous case until the
  precedence was decided and locked in with tests covering both operand positions.
- `magnitude([0.0, 0.0, 0.0])` correctly returns `0.0`, not an exception — magnitude
  alone is mathematically well-defined for a zero vector (`sqrt(0) = 0`); the
  undefined operation only arises later, specifically where a magnitude becomes a
  division denominator in `cosineSimilarity`. Callers must not assume "zero magnitude
  throws" is a universal rule across every method in this class — it's specific to
  where division by that magnitude actually occurs.
- **Accepted limitation, explicitly not fixed in this milestone:** `ZeroVectorException`
  is raised via exact floating-point equality (`magA == 0.0`), not an epsilon threshold.
  This is safe for exact-zero inputs (squaring and summing exact zeros produces an exact
  zero under IEEE 754), but an astronomically small, nonzero magnitude from a real,
  degenerate embedding would bypass this check entirely and produce an enormous or
  `Infinity` result instead of a clean exception. Deferred deliberately: Milestone 7.1
  is pure math with hand-constructed test vectors, not real embeddings; if a real
  degenerate embedding surfaces once Milestone 7.2 introduces an actual embedding model,
  an epsilon-based threshold should be added then, driven by a real observed case rather
  than a hypothetical one — the same "don't solve it until it's real" discipline ADR-0006
  used for the `common` module.

## Rejected Alternatives

- **Sentinel value (`0.0`) for zero-magnitude cosine similarity**: rejected — fabricates
  a mathematically false answer that looks indistinguishable from a real one.
- **`Optional`/`NaN` for zero-magnitude cosine similarity**: not chosen for this
  milestone — an unchecked exception was judged clearer for a caller to notice and
  handle than a `NaN` silently propagating through further arithmetic, or an `Optional`
  requiring every call site to remember to unwrap it correctly.
- **One generic exception type for all three invalid-input cases**: rejected — would
  erase a real operational distinction (three different remediation paths) and regress
  callers back to string-matching exception messages.
