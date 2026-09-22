# Start here

This folder is built to work whether you're the original author coming back after a
year with zero memory of this codebase, or a colleague opening it for the first time
with none at all. Don't start by browsing the milestone files below — start with the
path in this section.

## If you have 20-30 minutes and want the big picture

Pick the phase you care about and read, in this order:

1. **The phase's revision guide** (e.g. [`phase-7-revision.md`](phase-7-revision.md)) —
   a one-paragraph summary, an architecture diagram, a flow diagram, a table linking
   every class to its actual source file, the handful of decisions that actually
   mattered (condensed), the real numbers that were measured, and the gaps that were
   found and deliberately left open. This alone should be enough to hold a coherent
   conversation about the phase.
2. **The ADRs it links to** — one or two pages each, only the decisions that were
   genuinely argued about.
3. **The actual source files it links to** — most phases are small; reading the real
   code after the guide is faster than it sounds, and it's the only way to get a
   concrete mental model rather than a summary of one.

## If you want to test what you actually remember

Each milestone has its own file — [`0.2.md`](0.2.md) through [`7.2.md`](7.2.md) — with
the real interview questions, the answer as it was actually first given (including
wrong or imprecise attempts, which are worth reviewing too), any correction, and the
accepted final answer. Read the question, answer it yourself, then check. The revision
guide for each phase also ends with a short "test yourself" list pulled from these
files, if you want a fast pass before committing to the full transcript.

## If you want the full story of how something got built

That's not here — it's `../ROADMAP.md`. This folder is a study record: clean questions
and answers, organized for review. `ROADMAP.md` is the process history: what was
tried, what broke, what got argued about and why, in the order it actually happened.
Come here to refresh understanding and test recall; go there when "revision guide"
isn't enough and you need the full argument trail behind a specific decision.

---

## Revision guides (start here, one per phase)

| Phase | Guide |
|---|---|
| Phase 6 — LLM API Fundamentals | [phase-6-revision.md](phase-6-revision.md) |
| Phase 7 — Vector and Retrieval Foundations | [phase-7-revision.md](phase-7-revision.md) |

*(Phase 0 has no revision guide yet — it predates this folder's convention. Its
milestone files below still exist and follow the same Q&A format.)*

## Full milestone index

| Milestone | Title | Fidelity |
|---|---|---|
| [0.1](../.prompts/completed_milestone_0.1.md) | Starting architecture | Original file, not part of this directory |
| [0.2](0.2.md) | Containerized local infrastructure + database wiring | Reconstructed |
| [0.3](0.3.md) | First persistent domain slice | Reconstructed |
| [0.4](0.4.md) | REST API + layered structure + tests | Reconstructed |
| [6.1](6.1.md) | Raw chat completion mechanics against Ollama | Reconstructed |
| [6.2](6.2.md) | Tool-calling mechanics against Ollama | Reconstructed |
| [6.3](6.3.md) | `LlmClient` interface + real hosted provider | Transcribed |
| [7.1](7.1.md) | Cosine similarity from scratch | Transcribed |
| [7.2](7.2.md) | Real embeddings + hand-rolled brute-force retrieval | Transcribed |

**Fidelity note:** milestones 0.2 through 6.2 predate this conversation's own
transcript and are reconstructed from `ROADMAP.md`'s narrative summaries — accurate in
substance, but not verbatim. Milestones 6.3 onward were transcribed directly from a
live session transcript.
