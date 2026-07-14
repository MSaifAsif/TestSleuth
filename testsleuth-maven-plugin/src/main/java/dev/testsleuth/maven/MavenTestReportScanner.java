package dev.testsleuth.maven;

import dev.testsleuth.core.event.EventId;
import dev.testsleuth.core.event.EventKind;
import dev.testsleuth.core.event.Subject;
import dev.testsleuth.core.event.SubjectType;
import dev.testsleuth.core.event.TestSleuthEvent;
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
import java.util.Optional;
import java.util.stream.Stream;

final class MavenTestReportScanner {
    ScanResult scan(List<Path> reportDirectories) {
        List<TestSleuthEvent> events = new ArrayList<>();

        for (Path reportDirectory : reportDirectories) {
            if (!Files.isDirectory(reportDirectory)) {
                continue;
            }
            scanDirectory(reportDirectory, events);
        }

        return new ScanResult(List.copyOf(events));
    }

    private void scanDirectory(Path reportDirectory, List<TestSleuthEvent> events) {
        try (Stream<Path> paths = Files.list(reportDirectory)) {
            paths.filter(path -> path.getFileName().toString().startsWith("TEST-"))
                    .filter(path -> path.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> scanFile(path, events));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan Maven test report directory " + reportDirectory, e);
        }
    }

    private void scanFile(Path reportFile, List<TestSleuthEvent> events) {
        Document document = readXml(reportFile);
        NodeList testCases = document.getElementsByTagName("testcase");
        for (int index = 0; index < testCases.getLength(); index++) {
            Node node = testCases.item(index);
            if (node instanceof Element testCase) {
                events.add(toEvent(reportFile, testCase));
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

    private static TestSleuthEvent toEvent(Path reportFile, Element testCase) {
        String className = attribute(testCase, "classname", "unknown");
        String testName = attribute(testCase, "name", "unknown");
        String subjectId = className + "." + testName;
        String status = status(testCase);
        String durationMillis = durationMillis(attribute(testCase, "time", "0"));

        return new TestSleuthEvent(
                new EventId("maven-report:" + subjectId + ":" + eventsafe(reportFile.getFileName().toString())),
                Optional.empty(),
                EventKind.TEST_FINISHED,
                new Subject(SubjectType.TEST_METHOD, subjectId),
                Instant.EPOCH,
                0,
                Map.of(
                        "collector", "maven-test-report",
                        "reportFile", reportFile.toString(),
                        "className", className,
                        "testName", testName,
                        "status", status,
                        "durationMillis", durationMillis
                )
        );
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
}

