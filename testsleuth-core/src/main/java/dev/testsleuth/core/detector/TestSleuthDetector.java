package dev.testsleuth.core.detector;

import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.finding.Finding;

import java.util.List;

public interface TestSleuthDetector {
    List<Finding> detect(List<TestSleuthEvent> events);
}
