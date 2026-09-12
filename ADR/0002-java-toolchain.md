# ADR-0002: Java 25 (LTS) pinned via Gradle toolchain + auto-provisioning

Status: Accepted
Date: 2026-09-12

## Context

The build needs a specific, reproducible Java version rather than "whatever JDK happens
to be first on the developer's `PATH`." At scaffold time, the development machine had
JDK 8, JDK 21, and JDK 26 installed, but **not** JDK 25 — so this decision was tested
for real, not theoretical.

## Options Considered

1. **No toolchain — rely on ambient JDK.** Whatever `java -version` resolves to is what
   builds the project. Zero configuration, but not reproducible across machines or CI.
2. **Gradle toolchain pinned to a specific version, manually installed.** Reproducible,
   but every new machine (including CI) needs a manual JDK install step before the
   first build.
3. **Gradle toolchain pinned to a specific version, auto-provisioned via the
   `foojay-resolver-convention` plugin.** Reproducible *and* self-bootstrapping — a
   fresh clone on a machine with zero JDKs installed can still run `./gradlew build`.

## Decision

Option 3: Java 25 (the current LTS release) declared via
`java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }` in the root
`build.gradle`'s `subprojects {}` block, with `org.gradle.toolchains.foojay-resolver-convention`
applied in `settings.gradle` so Gradle can download a matching JDK automatically when
none is found locally.

LTS over the newest non-LTS release (JDK 26, which was actually already installed on
the dev machine) because this project is meant to model production-grade engineering
judgment, and production services default to LTS for the longer support window — not
because 26 is "wrong," but because "latest stable" for a system meant to run
continuously should default to the release with a real support lifecycle behind it.

## Consequences

- `./gradlew build` works identically on a machine with no JDK installed, one with the
  "wrong" JDK installed, or one with exactly JDK 25 installed — verified during scaffold
  (the initial build failed with no toolchain resolver configured, specifically because
  JDK 25 was not present locally; adding the resolver plugin fixed it without installing
  anything by hand).
- First build on a fresh machine is slower (downloads a JDK), which is an acceptable
  one-time cost.
- CI must have network access to `api.foojay.io` on first run, or the JDK must be
  pre-cached — worth remembering when Phase 0's CI milestone is built.

## Rejected Alternatives

- **No toolchain**: rejected — makes "works on my machine" failures likely, and this
  project explicitly cares about reproducible, explainable engineering decisions.
- **Manual JDK install per machine**: rejected — adds a manual step future-you (or a
  fresh Claude Code session) is likely to forget, for no benefit over auto-provisioning.
