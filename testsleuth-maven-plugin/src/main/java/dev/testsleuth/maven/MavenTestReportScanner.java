package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthRunContext;
import dev.testsleuth.core.event.TestSleuthEvent;
import dev.testsleuth.core.event.TestSubjectIdentity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

final class MavenTestReportScanner {
    private final TestSleuthRunContext runContext;

    MavenTestReportScanner(TestSleuthRunContext runContext) {
        this.runContext = Objects.requireNonNull(runContext, "runContext");
    }

    ScanResult scan(List<Path> reportDirectories) {
        Objects.requireNonNull(reportDirectories, "reportDirectories");
        return scanReportDirectories(reportDirectories.stream()
                .map(path -> new ReportDirectory(path, Map.of()))
                .toList());
    }

    ScanResult scanReportDirectories(List<ReportDirectory> reportDirectories) {
        Objects.requireNonNull(reportDirectories, "reportDirectories");
        List<TestSleuthEvent> events = new ArrayList<>();

        for (ReportDirectory reportDirectory : reportDirectories) {
            if (!Files.isDirectory(reportDirectory.path())) {
                continue;
            }
            scanDirectory(reportDirectory, events);
        }

        return new ScanResult(List.copyOf(events));
    }

    private void scanDirectory(ReportDirectory reportDirectory, List<TestSleuthEvent> events) {
        try (Stream<Path> paths = Files.list(reportDirectory.path())) {
            paths.filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> scanFile(reportDirectory, path, events));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan Maven test report directory " + reportDirectory, e);
        }
    }

    private void scanFile(ReportDirectory reportDirectory, Path reportFile, List<TestSleuthEvent> events) {
        Document document = readXml(reportFile);
        NodeList testCases = document.getElementsByTagName("testcase");
        for (int index = 0; index < testCases.getLength(); index++) {
            Node node = testCases.item(index);
            if (node instanceof Element testCase) {
                events.add(toEvent(reportDirectory, reportFile, testCase));
            }
        }
    }

    private static Document readXml(Path reportFile) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);

        try (InputStream input = Files.newInputStream(reportFile)) {
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(input);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("Failed to parse Maven test report " + reportFile, e);
        }
    }

    private TestSleuthEvent toEvent(ReportDirectory reportDirectory, Path reportFile, Element testCase) {
        return toEvent(reportDirectory, reportFile, testCase, runContext);
    }

    private static TestSleuthEvent toEvent(
            ReportDirectory reportDirectory,
            Path reportFile,
            Element testCase,
            TestSleuthRunContext runContext
    ) {
        String className = attribute(testCase, "classname", "unknown");
        String testName = attribute(testCase, "name", "unknown");
        String methodName = TestSubjectIdentity.normalizeMethodName(testName);
        String subjectId = TestSubjectIdentity.testMethod(className, methodName);
        String status = status(testCase);
        String durationMillis = durationMillis(attribute(testCase, "time", "0"));

        return new TestSleuthEvent(
                new EventId("maven-report:" + subjectId + ":" + eventsafe(reportFile.getFileName().toString())),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, subjectId),
                Instant.EPOCH,
                0,
                attributes(
                        reportDirectory,
                        reportFile,
                        className,
                        methodName,
                        testName,
                        subjectId,
                        status,
                        durationMillis,
                        runContext
                )
        );
    }

    private static Map<String, String> attributes(
            ReportDirectory reportDirectory,
            Path reportFile,
            String className,
            String methodName,
            String testName,
            String subjectId,
            String status,
            String durationMillis,
            TestSleuthRunContext runContext
    ) {
        java.util.HashMap<String, String> attributes = new java.util.HashMap<>(runContext.attributes());
        attributes.putAll(reportDirectory.attributes());
        attributes.put("collector", "maven-test-report");
        attributes.put("reportFile", reportFile.toString());
        attributes.put("reportDirectory", reportDirectory.path().toString());
        attributes.put("className", className);
        attributes.put("methodName", methodName);
        attributes.put("testName", testName);
        attributes.put("testIdentity", subjectId);
        attributes.put("status", status);
        attributes.put("durationMillis", durationMillis);
        return Map.copyOf(attributes);
    }

    private static String status(Element testCase) {
        if (testCase.getElementsByTagName("failure").getLength() > 0) {
            return "failed";
        }
        if (testCase.getElementsByTagName("error").getLength() > 0) {
            return "error";
        }
        if (testCase.getElementsByTagName("skipped").getLength() > 0) {
            return "skipped";
        }
        return "passed";
    }

    private static String durationMillis(String seconds) {
        try {
            double parsedSeconds = Double.parseDouble(seconds);
            return Long.toString(Math.round(parsedSeconds * 1_000));
        } catch (NumberFormatException e) {
            return "0";
        }
    }

    private static String attribute(Element element, String name, String fallback) {
        if (!element.hasAttribute(name)) {
            return fallback;
        }
        String value = element.getAttribute(name);
        if (value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String eventsafe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    record ScanResult(List<TestSleuthEvent> events) {
        ScanResult {
            events = List.copyOf(events);
        }

        int testCount() {
            return events.size();
        }
    }

    record ReportDirectory(Path path, Map<String, String> attributes) {
        ReportDirectory {
            Objects.requireNonNull(path, "path");
            attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        }
    }
}
