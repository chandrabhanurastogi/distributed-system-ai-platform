# ADR-0007: `LlmClient` is a stateless, full-history-per-call interface

Status: Accepted
Date: 2026-09-18

## Context

Milestone 6.3 introduced a second real provider (Gemini, hosted) behind the same
abstraction already used for Ollama (local). Ollama's `/api/chat` endpoint is
naturally stateless: every call takes the full message list and returns one reply,
with no server-side memory between calls. Gemini's Interactions API is different: it
supports `previous_interaction_id`, letting a caller send only the newest message and
have Gemini's servers retain and replay the prior turns itself.

This is a real semantic mismatch, not a naming difference. Designing `LlmClient.chat()`
requires picking one contract that both providers implement, and the two providers
don't naturally agree on who owns conversation state.

## Options Considered

1. **Option A — expose provider-native statefulness.** Let `LlmClient` implementations
   each manage their own session/continuation semantics (e.g. an optional
   `continuationId` in `LlmResponse`, passed back in on the next call). Ollama's adapter
   would fake this by concatenating history internally; Gemini's would pass
   `previous_interaction_id` straight through to the real API.
2. **Option B — provider-specific interfaces.** Don't unify at all; give Ollama and
   Gemini separate client interfaces with their own natural contracts, and let callers
   (or a higher-level orchestrator) deal with the difference.
3. **Option C — uniform, stateless, full-history-every-call contract.** `LlmClient.chat(List<ChatMessage> messages)`
   always takes the complete conversation so far and returns one `LlmResponse`. No
   provider gets to keep server-side state on the caller's behalf through this
   interface; the caller is always the source of truth for history.

## Decision

Option C. `LlmClient` stays exactly as implemented: one method, full history in, one
response out, both providers stateless from the caller's perspective.

This is a deliberate trade-off, not an oversight:

- **What it sacrifices:** Gemini's `previous_interaction_id` mechanism lets Gemini's
  servers avoid re-processing (and re-billing token cost for) the full transcript on
  every turn. `GeminiLlmClient` does not use it — every call reserializes and resends
  the entire history via the `input` field, meaning a long conversation against Gemini
  through this interface costs strictly more in input tokens over time than the native
  API would allow.
- **What it buys:** a genuinely provider-agnostic interface. Any caller of `LlmClient`
  (a future orchestrator, an agent loop in Phase 10) can swap providers via
  `llm.provider` (Milestone 6.3's `@ConditionalOnProperty` switch) without caring which
  one is underneath, because neither implementation is allowed to depend on hidden
  server-side state that the other doesn't have. Ollama has no equivalent to
  `previous_interaction_id` at all — Option A would have meant faking statefulness for
  one provider and using it for real on the other, which is a worse abstraction leak
  than paying Gemini's extra token cost.
- Option B was rejected because it defeats the actual purpose of this milestone
  (interface-level provider swapping) and pushes the state-management problem onto
  every caller instead of solving it once.

## Consequences

- Every multi-turn call to `GeminiLlmClient` resends full history and is charged input
  tokens for it — a real, measurable cost difference from what Gemini's own API is
  capable of. This is tracked debt, not silently accepted: if a future milestone
  (agents, Phase 10, or a cost-sensitive production path) needs Gemini-native session
  continuation, it belongs behind a *different*, explicitly stateful interface — not a
  quiet special-case added to `LlmClient`.
- `LlmResponse` stays a plain `(content, inputTokens, outputTokens)` record with no
  continuation/session identifier field, because no implementation is allowed to need
  one through this interface.
- This decision only concerns the `LlmClient` abstraction used by application code in
  this repository. It says nothing about whether Gemini's native API is well-designed —
  only that this project chose not to expose that specific capability through a
  provider-agnostic seam.

## Rejected Alternatives

- **Option A (expose provider-native statefulness)**: rejected — would require
  Ollama's adapter to fake a capability it doesn't have, producing an interface whose
  contract quietly differs by implementation.
- **Option B (separate interfaces per provider)**: rejected — abandons the milestone's
  actual goal (swap providers behind one seam) and moves the problem to every caller.
