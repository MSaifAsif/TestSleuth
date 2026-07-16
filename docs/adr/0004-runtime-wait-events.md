# ADR 0004: Runtime Wait Events

## Status

Accepted

## Context

TestSleuth already has opt-in source detectors for direct `Thread.sleep(...)` calls and selected JDK timed waits. Source scanning is cheap and useful, but it cannot prove whether a wait executed, how often it executed, how long it actually blocked, or whether the configured timeout was reached.

The roadmap calls for runtime wait diagnosis, likely through a Java agent or Byte Buddy instrumentation. That instrumentation must not bake Maven-specific assumptions into the event model.

## Decision

Runtime wait collection will emit normalized events into the shared TestSleuth event stream. The first runtime wait event shape should represent:

- wait operation, such as `Thread.sleep`, `Object.wait`, `LockSupport.parkNanos`, `CountDownLatch.await`, `Semaphore.tryAcquire`, `Future.get`, or `CompletableFuture.orTimeout`
- observed wait duration
- configured timeout when available
- timeout unit or normalized timeout duration
- whether the wait completed normally, timed out, was interrupted, or failed
- thread name and thread id when safe to collect
- test identity, class, method, module, fork, process, and build run when available
- optional stack trace or top application frame when enabled
- collector name and collector overhead metadata

Runtime wait collection remains opt-in. Source wait detectors remain useful and should not be replaced. Detectors should merge runtime evidence with source evidence when identities or source locations can be correlated.

Broader source detection for JDK timed waits can ship before automatic runtime interception. It provides low-cost evidence for configured waits while runtime collection matures behind opt-in configuration.

Initial Maven configuration should use explicit properties, likely:

- `testsleuth.runtime.waits`
- `testsleuth.runtime.waitStacks`

Stack collection must be disabled by default unless the overhead and privacy behavior are acceptable.

## Consequences

The Java agent or Byte Buddy implementation can be developed after the event contract is testable in `testsleuth-core`.

Runtime wait findings can distinguish:

- configured timeout from actual observed waiting
- waits that are present in source but did not execute
- waits that execute many times with small per-call cost
- waits hidden in helper methods or libraries

Collectors must measure and report their own overhead. Collector failures should degrade to partial reports and must not change test results.
