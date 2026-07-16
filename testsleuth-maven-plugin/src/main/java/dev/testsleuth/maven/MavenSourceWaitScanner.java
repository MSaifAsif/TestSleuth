package dev.testsleuth.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class MavenSourceWaitScanner {
    private static final Pattern THREAD_SLEEP = Pattern.compile("Thread\\.sleep\\(\\s*([0-9][0-9_]*)(?:[lL])?\\s*\\)");
    private static final Pattern TIMED_WAIT = Pattern.compile(
            "\\.\\s*(await|tryAcquire|get|orTimeout|completeOnTimeout)\\s*\\("
                    + "[^;]*?([0-9][0-9_]*)(?:[lL])?\\s*,\\s*"
                    + "(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*TimeUnit\\."
                    + "(NANOSECONDS|MICROSECONDS|MILLISECONDS|SECONDS|MINUTES)\\b"
    );
    private static final Pattern LOOP = Pattern.compile("\\b(for|while)\\s*\\(");

    Stream<SourceWait> scanRoots(List<String> testSourceRoots) {
        Objects.requireNonNull(testSourceRoots, "testSourceRoots");
        return testSourceRoots.stream()
                .filter(root -> root != null && !root.isBlank())
                .map(Path::of)
                .filter(Files::isDirectory)
                .flatMap(this::scanRoot);
    }

    private Stream<SourceWait> scanRoot(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .flatMap(path -> scanFile(root, path).stream())
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source root " + root, e);
        }
    }

    private List<SourceWait> scanFile(Path root, Path source) {
        try {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            java.util.ArrayList<SourceWait> waits = new java.util.ArrayList<>();
            java.util.ArrayList<String> sanitizedLines = new java.util.ArrayList<>();
            boolean insideBlockComment = false;
            for (int index = 0; index < lines.size(); index++) {
                SanitizedLine sanitized = sanitizeJavaLine(lines.get(index), insideBlockComment);
                insideBlockComment = sanitized.insideBlockComment();
                String line = sanitized.code();
                sanitizedLines.add(line);
                if (line.isBlank()) {
                    continue;
                }
                waits.addAll(scanThreadSleeps(root, source, sanitizedLines, index, line));
                waits.addAll(scanTimedWaits(root, source, sanitizedLines, index, line));
            }
            return waits;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan test source file " + source, e);
        }
    }

    private static SanitizedLine sanitizeJavaLine(String line, boolean insideBlockComment) {
        StringBuilder code = new StringBuilder(line.length());
        boolean inString = false;
        boolean inChar = false;
        boolean escaped = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            char next = index + 1 < line.length() ? line.charAt(index + 1) : '\0';

            if (insideBlockComment) {
                if (current == '*' && next == '/') {
                    insideBlockComment = false;
                    index++;
                }
                code.append(' ');
                continue;
            }

            if (inString || inChar) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (inString && current == '"') {
                    inString = false;
                } else if (inChar && current == '\'') {
                    inChar = false;
                }
                code.append(' ');
                continue;
            }

            if (current == '/' && next == '/') {
                break;
            }
            if (current == '/' && next == '*') {
                insideBlockComment = true;
                code.append(' ');
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                code.append(' ');
                continue;
            }
            if (current == '\'') {
                inChar = true;
                code.append(' ');
                continue;
            }

            code.append(current);
        }
        return new SanitizedLine(code.toString(), insideBlockComment);
    }

    private static List<SourceWait> scanThreadSleeps(
            Path root,
            Path source,
            List<String> lines,
            int index,
            String line
    ) {
        java.util.ArrayList<SourceWait> waits = new java.util.ArrayList<>();
        Matcher matcher = THREAD_SLEEP.matcher(line);
        while (matcher.find()) {
            waits.add(new SourceWait(
                    root.relativize(source),
                    index + 1,
                    Duration.ofMillis(parseMillis(matcher.group(1))),
                    "Thread.sleep",
                    nearLoop(lines, index)
            ));
        }
        return waits;
    }

    private static List<SourceWait> scanTimedWaits(
            Path root,
            Path source,
            List<String> lines,
            int index,
            String line
    ) {
        java.util.ArrayList<SourceWait> waits = new java.util.ArrayList<>();
        Matcher matcher = TIMED_WAIT.matcher(line);
        while (matcher.find()) {
            String method = matcher.group(1);
            waits.add(new SourceWait(
                    root.relativize(source),
                    index + 1,
                    duration(parseMillis(matcher.group(2)), matcher.group(3)),
                    method + "(timeout)",
                    nearLoop(lines, index)
            ));
        }
        return waits;
    }

    private static boolean nearLoop(List<String> lines, int waitLineIndex) {
        int start = Math.max(0, waitLineIndex - 4);
        for (int index = waitLineIndex; index >= start; index--) {
            String line = lines.get(index).stripLeading();
            if (line.startsWith("//")) {
                continue;
            }
            if (LOOP.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private static Duration duration(long value, String timeUnit) {
        return switch (timeUnit.toUpperCase(Locale.ROOT)) {
            case "NANOSECONDS" -> Duration.ofNanos(value);
            case "MICROSECONDS" -> Duration.ofNanos(Math.multiplyExact(value, 1_000));
            case "MILLISECONDS" -> Duration.ofMillis(value);
            case "SECONDS" -> Duration.ofSeconds(value);
            case "MINUTES" -> Duration.ofMinutes(value);
            default -> throw new IllegalArgumentException("Unsupported TimeUnit " + timeUnit);
        };
    }

    private static long parseMillis(String literal) {
        return Long.parseLong(literal.replace("_", ""));
    }

    record SourceWait(Path source, int lineNumber, Duration duration, String expression, boolean insideLoop) {
    }

    private record SanitizedLine(String code, boolean insideBlockComment) {
    }
}
