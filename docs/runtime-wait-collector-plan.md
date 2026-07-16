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

When `testsleuth.runtime.waits=true`, Maven instrumentation should add the collector only to test JVMs. The default must stay disabled.

When `testsleuth.runtime.waitStacks=true`, the collector may include stack evidence. Stack collection must remain separately opt-in because it has higher overhead and privacy impact.

## Slice 4: Detection and Reporting

Runtime wait findings should merge with source wait findings rather than replace them.

The detector should distinguish:

- source wait found but not executed
- runtime wait executed without a known source match
- repeated short waits with large cumulative cost
- configured timeout versus actual observed wait

## Slice 5: Overhead and Failure Behavior

The collector must measure its own overhead and report it in the Maven console summary.

Collector failure should degrade to partial reports. It must not fail the user's test suite unless the user explicitly enables a future strict diagnostic mode.
