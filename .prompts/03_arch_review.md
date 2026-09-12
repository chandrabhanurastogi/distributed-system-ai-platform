# Senior/Staff Architecture Review

Review the current repository as if you were interviewing me for a Senior/Staff Distributed Systems + AI Engineer position.

Do not modify code.

Inspect the current implementation, tests, architecture documentation, ADRs, Kafka configuration, database design, resilience configuration, AI/RAG/agent components, and observability.

Evaluate:

## Distributed Systems

* service boundaries
* coupling
* failure isolation
* consistency
* idempotency
* transaction boundaries
* retry behavior
* timeout behavior
* circuit breakers
* Saga design
* outbox usage
* Kafka semantics

## Kafka

* partition strategy
* key selection
* ordering
* consumer groups
* offset handling
* rebalance behavior
* retry/DLT strategy
* duplicate processing
* schema evolution

## Data

* ownership
* transactions
* isolation
* locking
* consistency
* migrations

## AI

* context construction
* token usage
* retrieval architecture
* chunking
* embeddings
* reranking
* GraphRAG
* tool calling
* agent architecture
* memory
* guardrails

## Production

* observability
* security
* scalability
* performance
* operational complexity
* failure recovery
* testing

For every significant weakness provide:

```text
Problem
Why it matters
Evidence in repository
Production consequence
Interview risk
Recommended improvement
```

Then give the project scores from 1–10 for:

* Architecture
* Distributed Systems
* Kafka
* Reliability
* Testing
* Observability
* Data consistency
* AI engineering
* RAG
* Agents
* Evaluation
* Production readiness
* Interview readiness

Finally, give me the **10 hardest questions an interviewer could ask about this repository**.

Do not flatter me.

The purpose is to expose weaknesses before an interviewer does.
