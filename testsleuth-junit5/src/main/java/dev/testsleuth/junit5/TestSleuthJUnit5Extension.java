package dev.testsleuth.junit5;

import dev.testsleuth.core.event.EventKind;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class TestSleuthJUnit5Extension implements
        BeforeEachCallback,
        BeforeTestExecutionCallback,
        AfterTestExecutionCallback,
        AfterEachCallback {
    public static final String AUTODETECTION_PROPERTY = "junit.jupiter.extensions.autodetection.enabled";
    private static final JUnitLifecycleEventCollector COLLECTOR = TestSleuthJUnit5Listener.sharedCollector();

    @Override
    public void beforeEach(ExtensionContext context) {
        COLLECTOR.recordPhaseStarted(
                EventKind.SETUP_STARTED,
                "before-each",
                context.getUniqueId(),
                context.getDisplayName(),
                context.getTestClass(),
                context.getTestMethod()
        );
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        COLLECTOR.recordPhaseFinished(
                EventKind.SETUP_FINISHED,
                "before-each",
                context.getUniqueId(),
                context.getDisplayName(),
                context.getTestClass(),
                context.getTestMethod()
        );
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        COLLECTOR.recordPhaseStarted(
                EventKind.TEARDOWN_STARTED,
                "after-each",
                context.getUniqueId(),
                context.getDisplayName(),
                context.getTestClass(),
                context.getTestMethod()
        );
    }

    @Override
    public void afterEach(ExtensionContext context) {
        COLLECTOR.recordPhaseFinished(
                EventKind.TEARDOWN_FINISHED,
                "after-each",
                context.getUniqueId(),
                context.getDisplayName(),
                context.getTestClass(),
                context.getTestMethod()
        );
    }
}
