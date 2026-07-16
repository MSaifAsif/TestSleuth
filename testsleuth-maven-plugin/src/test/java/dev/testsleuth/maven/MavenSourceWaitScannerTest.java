package dev.testsleuth.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MavenSourceWaitScannerTest {
    @TempDir
    private Path tempDir;

    @Test
    void scansThreadSleepsAndCommonJdkTimedWaits() throws IOException {
        writeSource("ExampleTest.java", """
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.CountDownLatch;
                import java.util.concurrent.Future;
                import java.util.concurrent.Semaphore;
                import java.util.concurrent.TimeUnit;

                final class ExampleTest {
                    void waits(CountDownLatch latch, Semaphore semaphore, Future<String> future) throws Exception {
                        Thread.sleep(1_250L);
                        latch.await(2, TimeUnit.SECONDS);
                        semaphore.tryAcquire(300, java.util.concurrent.TimeUnit.MILLISECONDS);
                        future.get(4, TimeUnit.MINUTES);
                        CompletableFuture.completedFuture("ok").orTimeout(5, TimeUnit.MILLISECONDS);
                        CompletableFuture.completedFuture("ok").completeOnTimeout("fallback", 6, TimeUnit.SECONDS);
                    }
                }
                """);

        List<MavenSourceWaitScanner.SourceWait> waits = scan();

        assertEquals(List.of(
                "Thread.sleep:1250:false",
                "await(timeout):2000:false",
                "tryAcquire(timeout):300:false",
                "get(timeout):240000:false",
                "orTimeout(timeout):5:false",
                "completeOnTimeout(timeout):6000:false"
        ), summarize(waits));
    }

    @Test
    void marksWaitsInsideNearbyLoops() throws IOException {
        writeSource("ExampleTest.java", """
                import java.util.concurrent.Semaphore;
                import java.util.concurrent.TimeUnit;

                final class ExampleTest {
                    void sleepPolling() throws Exception {
                        for (int attempt = 0; attempt < 3; attempt++) {
                            Thread.sleep(100);
                        }
                    }

                    void timedPolling(Semaphore semaphore) throws Exception {
                        while (!semaphore.tryAcquire(250, TimeUnit.MILLISECONDS)) {
                            break;
                        }
                    }

                    void notPolling() throws Exception {
                        Thread.sleep(300);
                    }
                }
                """);

        List<MavenSourceWaitScanner.SourceWait> waits = scan();

        assertEquals(List.of(
                "Thread.sleep:100:true",
                "tryAcquire(timeout):250:true",
                "Thread.sleep:300:false"
        ), summarize(waits));
    }

    @Test
    void ignoresCommentsStringsAndUnsupportedExpressions() throws IOException {
        writeSource("ExampleTest.java", """
                import java.util.concurrent.CountDownLatch;
                import java.util.concurrent.TimeUnit;

                final class ExampleTest {
                    void commentsAndStrings(CountDownLatch latch, long timeout) throws Exception {
                        // for (;;) {
                        // Thread.sleep(9_000);
                        String sleep = "Thread.sleep(8000)";
                        String wait = "latch.await(7, TimeUnit.SECONDS)";
                        /*
                         * Thread.sleep(6_000);
                         * latch.await(5, TimeUnit.SECONDS);
                         */
                        latch.await(timeout, TimeUnit.SECONDS);
                        Thread.sleep(400); // Thread.sleep(9000)
                    }
                }
                """);

        List<MavenSourceWaitScanner.SourceWait> waits = scan();

        assertEquals(1, waits.size());
        MavenSourceWaitScanner.SourceWait wait = waits.get(0);
        assertEquals("Thread.sleep", wait.expression());
        assertEquals(400, wait.duration().toMillis());
        assertFalse(wait.insideLoop());
    }

    @Test
    void ignoresMissingAndBlankRoots() throws IOException {
        writeSource("ExampleTest.java", """
                final class ExampleTest {
                    void waits() throws Exception {
                        Thread.sleep(250);
                    }
                }
                """);

        List<MavenSourceWaitScanner.SourceWait> waits = new MavenSourceWaitScanner()
                .scanRoots(List.of("", tempDir.resolve("missing").toString(), tempDir.toString()))
                .toList();

        assertEquals(1, waits.size());
        assertTrue(waits.get(0).source().endsWith("ExampleTest.java"));
    }

    private List<MavenSourceWaitScanner.SourceWait> scan() {
        return new MavenSourceWaitScanner()
                .scanRoots(List.of(tempDir.toString()))
                .sorted(Comparator.comparing(wait -> wait.source().toString()))
                .toList();
    }

    private void writeSource(String fileName, String source) throws IOException {
        Files.writeString(tempDir.resolve(fileName), source);
    }

    private static List<String> summarize(List<MavenSourceWaitScanner.SourceWait> waits) {
        return waits.stream()
                .map(wait -> wait.expression() + ":" + wait.duration().toMillis() + ":" + wait.insideLoop())
                .toList();
    }
}
