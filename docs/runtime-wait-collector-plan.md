# Runtime Wait Collector Implementation Plan

This plan expands ADR 0004 into implementation slices. Runtime wait collection remains opt-in and should not change test results.

## Slice 1: Event Model and Configuration

Done:

- Runtime wait event attributes in `testsleuth-core`.
- JSON round-trip coverage for `WAIT_FINISHED` events.
- Maven flags reserved for future collection:
  - `testsleuth.runtime.waits`
  - `testsleuth.runtime.waitStacks`
- Runtime wait module shell with normalized observation-to-event mapping and overhead attributes.
- Runtime wait collector API that captures `Thread.sleep`, `Object.wait`, `LockSupport.parkNanos`, and `LockSupport.parkUntil` calls when invoked directly.
- Maven opt-in classpath and event-file wiring for direct runtime wait collector use.

## Slice 2: Collector Prototype

Extend the separate collector module before wiring it into the Maven plugin. The prototype should capture:

- Direct calls through the collector API:
  - `Thread.sleep(long)`
  - `Object.wait(long)`
  - `LockSupport.parkNanos(long)`
  - `LockSupport.parkUntil(long)`
- Automatic test-JVM interception for the same operations in a later wiring slice.

The collector should write events with:

- operation name
- observed duration
- configured timeout when available
- outcome
- thread metadata
- build/module/process/fork context
- collector overhead

## Slice 3: Maven Opt-In Wiring

Done:

- When `testsleuth.runtime.waits=true`, Maven instrumentation adds the runtime wait collector to test JVM classpaths and exposes `testsleuth.runtime.waits.file`.
- The default stays disabled.
- Tests that import the direct collector API still need a normal test dependency on `testsleuth-runtime-wait` for compilation.

Remaining:

- Automatic JDK wait interception still needs an agent or equivalent test-JVM hook.

When `testsleuth.runtime.waitStacks=true`, the collector may include stack evidence. Stack collection must remain separately opt-in because it has higher overhead and privacy impact.

## Slice 4: Detection and Reporting

Done:

- Runtime wait events produce Maven findings above the configured fixed-wait threshold when `testsleuth.runtime.waits=true`.
- Runtime wait findings are additive and do not replace source wait findings.

Remaining:

The detector should distinguish:

- source wait found but not executed
- runtime wait executed without a known source match
- repeated short waits with large cumulative cost
- configured timeout versus actual observed wait

## Slice 5: Overhead and Failure Behavior

Done:

- Direct runtime wait events include per-event collector overhead.
- Maven console summaries report runtime wait event count, observed wait time, and aggregate collector overhead when runtime wait events are present.

Remaining:

Collector failure should degrade to partial reports. It must not fail the user's test suite unless the user explicitly enables a future strict diagnostic mode.
