# ADR 0001: Repository Foundation

## Status

Accepted

## Context

The roadmap defines TestSleuth as a local-first diagnostic tool for slow test suites. The repository currently starts from the product roadmap and needs a buildable foundation that can evolve toward Maven and Gradle plugin delivery.

The current GitHub remote is `MSaifAsif/TestSleuth`. That is accepted as the working repository identity for now. Public package coordinates remain `dev.testsleuth` unless a later release decision changes them.

## Decision

Use a Maven reactor as the first build foundation with Java 17 bytecode:

- `testsleuth-core` for event and finding types.
- `testsleuth-report` for report rendering.
- `testsleuth-maven-plugin` for the first plugin shell.

Gradle plugin work remains part of the roadmap, but it is not scaffolded as a buildable module in this first commit because Gradle is not yet available in the local toolchain.

## Consequences

The repository can be verified with `mvn verify` immediately. The first implementation work should extend the core schemas and plugin behavior before adding framework-specific collectors.

