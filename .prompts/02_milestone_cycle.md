# Execute Current Milestone

Read:

1. `CLAUDE.md`
2. `ROADMAP.md`
3. `ARCHITECTURE.md`
4. relevant ADRs
5. the current repository state

Determine the **single active milestone**.

Do not work on future milestones.

## Before coding

Tell me:

1. What concept are we learning?
2. Why does it exist?
3. What production problem does it solve?
4. What failure modes matter?
5. What architectural choices do we have?
6. What trade-offs exist?
7. What will we measure?
8. What tests will prove the behavior?

Then give me a small architectural decision/question to answer myself.

Challenge my answer before proceeding.

## Implementation

Implement only what is required for the current milestone.

Prefer:

```text
small change
→ test
→ run
→ inspect result
→ explain
→ next change
```

Do not refactor unrelated code.

Do not introduce future technologies prematurely.

## Verification

Before marking anything complete:

* run relevant unit tests
* run integration tests
* run failure scenarios
* inspect logs
* inspect metrics/traces where applicable
* verify the expected behavior
* review the git diff

If something fails, investigate the root cause rather than simply modifying the test.

## Documentation

Update the appropriate:

* `ROADMAP.md`
* `ARCHITECTURE.md`
* ADR
* README
* experiment/benchmark results

## Interview

After the milestone passes verification, switch roles.

Become a Staff-level interviewer.

Ask me 5–10 increasingly difficult questions about what we just built.

Do not immediately provide answers.

Evaluate my answers for:

* correctness
* depth
* trade-off awareness
* distributed-systems reasoning
* production awareness

Then tell me what I would need to improve to defend this design in a Senior/Staff interview.

Only after that may the milestone be marked `[x]`.
