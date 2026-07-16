package dev.testsleuth.runtimewait;

public final class RuntimeWaitOperations {
    public static final String THREAD_SLEEP = "Thread.sleep";
    public static final String OBJECT_WAIT = "Object.wait";
    public static final String LOCK_SUPPORT_PARK_NANOS = "LockSupport.parkNanos";
    public static final String LOCK_SUPPORT_PARK_UNTIL = "LockSupport.parkUntil";

    private RuntimeWaitOperations() {
    }
}
