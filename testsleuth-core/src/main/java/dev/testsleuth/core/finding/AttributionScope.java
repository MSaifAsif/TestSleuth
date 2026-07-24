package dev.testsleuth.core.finding;

/** Describes the execution scope to which an observed cost can be assigned. */
public enum AttributionScope {
    DIRECT_TEST_THREAD,
    CORRELATED_ASYNCHRONOUS,
    SHARED_CONCURRENT,
    SHARED_JVM,
    FRAMEWORK_OR_FIXTURE,
    FORK_WIDE,
    BUILD_WIDE,
    UNCLASSIFIED
}
