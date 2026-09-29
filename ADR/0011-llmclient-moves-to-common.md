# ADR-0011: `LlmClient` interface and DTOs move to `common`, not the concrete implementations

Status: Accepted
Date: 2026-09-28

## Context

Milestone 8.4 needs `dispute-service` to make a real LLM call for the first time —
classifying a customer dispute claim against a chargeback reason code, using the
context already retrieved from `pgvector`. `llm-fundamentals` already has exactly the
abstraction this needs: `LlmClient` (ADR-0007's stateless, full-history-per-call
interface), `ChatMessage`, `LlmResponse`, and two real implementations
(`OllamaLlmClient`, `GeminiLlmClient`). `dispute-service` has no dependency on it.

This directly parallels Milestone 8.2's decision to duplicate `OllamaEmbeddingService`
into `dispute-service` rather than depend on `llm-fundamentals` for it. That decision
needed re-examining here, not silently repeated, once its actual justification was
tested against the same standard used to reach it.

## The Rejected Framing, Named Explicitly

The first justification offered for treating this differently from the embedding-client
case — "this is a real, demonstrated need, unlike the embedding case" — does not survive
scrutiny and is recorded here as rejected, not quietly dropped. Counted the same way
`ADR-0006` counted duplicate `CorrelationIdFilter` instances: `llm-fundamentals` plus
`dispute-service` needing embedding was two instances; `llm-fundamentals` plus
`dispute-service` needing LLM-calling is also two instances. Instance count alone gives
no principled reason to treat the two cases differently — by that measure alone, the
correct move would be to duplicate again, exactly as before.

## Options Considered

1. **Duplicate a minimal LLM-calling client into `dispute-service` again**, consistent
   with the embedding-client precedent.
2. **Add `project(':llm-fundamentals')` as a real dependency**, reusing `LlmClient` and
   an existing implementation directly.
3. **Extract a new, dedicated shared module** (e.g. `llm-client`) for just this
   abstraction.
4. **Move `LlmClient` and its DTOs into `common`**, with each service writing its own
   concrete implementation against the shared interface.

## Decision

Option 4. The distinguishing factor between this case and the embedding-client
precedent is not instance count — it's real, that this project got wrong at first —
it's **complexity and risk of what's being duplicated**:

- `OllamaEmbeddingService` was roughly 30 lines, one HTTP call, no known bugs, stable —
  genuinely low-risk to fork.
- `LlmClient` is a settled interface with two real implementations, tool-calling
  mechanics, and a **named, already-tracked, unresolved bug** —
  `GeminiLlmClient.serializeMessages()`'s prompt-injection-shaped flattening issue,
  recorded in `CLAUDE.md`'s Known Existing Debt, explicitly required to be fixed before
  Phase 10 routes any real tool-calling loop through it. Duplicating a minimal LLM
  client now risks forking that exact unresolved bug into a second, independently-
  drifting copy, or writing something new that eventually has to reconcile with the
  real thing anyway once any consumer needs more than one classification call.

Option 2 (depend on `llm-fundamentals` directly) is rejected on separate, independent
grounds: `llm-fundamentals` is a full, independently-runnable Spring Boot application —
its own `ChatController`, its own port, its own demo/provider-comparison tests — not a
module shaped to be a dependency target. Depending on it would pull a REST controller
and another service's learning-exercise machinery onto `dispute-service`'s classpath
for zero benefit.

Option 3 (a new dedicated module) is rejected because it fails this project's own
instance-count bar just as badly as the rejected framing above: two consumers
(`llm-fundamentals`, `dispute-service`) is the same count `ADR-0006` already judged
insufficient to justify new structure. `common` sidesteps this — it isn't new
structure, it's already-justified shared infrastructure `llm-fundamentals` already
depends on (for `CorrelationIdFilter`), so extending it adds no new dependency edge
anywhere in the graph.

Only the **interface and shared DTOs** (`LlmClient`, `ChatMessage`, `LlmResponse`) move
to `common`. Concrete implementations stay local to each consumer, deliberately:
`dispute-service`'s classification need is a single, single-turn call — it needs
neither tool-calling, structured output, nor `GeminiLlmClient`'s multi-turn
`serializeMessages()` logic at all. Sharing only the interface means `dispute-service`
never inherits the tracked bug in the first place, rather than sharing it and hoping it
doesn't matter for a use case that happens not to exercise it today.

## Consequences

- `com.distributedplatform.common.llm` becomes a new package in `common`, holding
  `LlmClient`, `ChatMessage`, `LlmResponse`. `llm-fundamentals`'s `OllamaLlmClient` and
  `GeminiLlmClient` are updated to implement the interface from `common` instead of
  their own local copy — a real refactor, not just an addition, since the interface
  they currently implement moves out from under them.
- `dispute-service` writes its own, minimal `LlmClient` implementation for whichever
  provider it actually calls (most likely a local Ollama-based one, matching this
  project's free/local-first pattern) — new code, not shared code, deliberately scoped
  to only what classification needs.
- **A genuine, named tension with `ADR-0006`'s own stated charter for `common`, worth
  being honest about rather than glossed over:** `common` today holds inert,
  non-functional cross-cutting plumbing (correlation IDs, logging configuration).
  `LlmClient` is a different flavor of shared thing — a functional capability multiple
  services do real work with, not background infrastructure. It still satisfies
  `common`'s literal charter (genuinely domain-free — nothing about `LlmClient` is
  order/inventory/dispute-specific), but this decision stretches what `common` has
  actually been used for so far. Future additions to `common` should be judged against
  this precedent honestly: "domain-free" is the actual bar, not "must be inert
  infrastructure like everything already there."
- If `GeminiLlmClient.serializeMessages()`'s tracked bug is ever fixed, that fix
  applies once, in `llm-fundamentals`, and nothing in `dispute-service` is affected
  either way, since `dispute-service` never depended on that implementation.

## Rejected Alternatives

- **Duplicate again**: rejected once the actual differentiator (complexity/risk of
  what's duplicated, not instance count) was named correctly — this project's own
  reasoning was initially applied inconsistently here and corrected before
  implementation, not after.
- **Direct dependency on `llm-fundamentals`**: rejected — it's an application module,
  not a library, and depending on it drags in unrelated service-specific machinery.
- **A new dedicated shared module**: rejected — fails the same instance-count bar this
  project already uses to gate new structure; `common` already exists and is already
  the right shape for this.

## Addendum (2026-09-30): Wire-format separation for `dispute-service`'s `OllamaLlmClient`

A related decision, found and fixed during this same milestone's implementation, not
originally covered above and worth recording in full rather than leaving it to live
only in a class-level Javadoc.

**The problem:** the first version of `dispute-service.llm.OllamaLlmClient` had
`OllamaChatRequest.messages` and `OllamaChatResponse.message` typed directly as
`common.llm.ChatMessage` — reusing the shared interface DTO as the literal Jackson
wire-serialization type for Ollama's `/api/chat` payload. This worked today only
because `ChatMessage`'s fields (`role`, `content`) happen to coincide exactly with
what Ollama's API expects — the same kind of coincidental-alignment risk this ADR
already named for instance-counting, applied one layer lower, to wire formats instead
of interfaces.

**Why this is a real risk, not a style preference:** `ChatMessage` is shared,
provider-agnostic state — both `llm-fundamentals` (backing `OllamaLlmClient` and
`GeminiLlmClient` there) and `dispute-service` depend on it. If `OllamaChatRequest`
serializes `ChatMessage` directly, then any future change to `ChatMessage` made for an
unrelated reason — for instance, a field a hypothetical `dispute-service` Gemini client
might need someday — would silently also change what gets sent over the wire to
Ollama, since Jackson serializes whatever fields the type actually has. The correctness
of Ollama's wire format would depend on nobody ever having a reason to touch the shared
type, an invariant enforced by nothing but discipline.

**Decision:** `dispute-service` gets its own `OllamaMessage` wire-format class,
structurally identical to `ChatMessage` today, but a distinct type. `OllamaLlmClient`
explicitly maps `ChatMessage` → `OllamaMessage` before making the HTTP call — mirroring
the pattern `llm-fundamentals`'s own `OllamaLlmClient` already used (mapping to its
local `dto.ollama.Message`), which the first draft here departed from without a stated
reason.

**Consequence — a structural guarantee, not a smaller diff:** because
`OllamaChatRequest.messages` is `List<OllamaMessage>`, not `List<ChatMessage>`, no
change made to the shared interface DTO for any other provider's sake can ever reach
Ollama's wire format, even by accident — there is no shared mutable surface between the
two providers' wire representations for such a change to travel through. This was
verified directly, not just asserted: adding a hypothetical Gemini client to
`dispute-service` requires its own wire DTOs regardless of this fix, since Gemini's
real shape (a flat string, not a messages array) was never going to reuse
`OllamaChatRequest` either way — so this decision buys no reduction in that migration's
file count. What it buys instead is that the untouched files in that migration
(`OllamaLlmClient`, `OllamaChatRequest`, `OllamaChatResponse`, `OllamaMessage`) are
*provably* untouched, not merely untouched today by the coincidence that no one has
needed to change `ChatMessage` yet.
