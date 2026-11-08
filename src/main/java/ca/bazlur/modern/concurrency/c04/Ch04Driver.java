package ca.bazlur.modern.concurrency.c04;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chapter 4 driver.
 *
 * <p>Every demo class in this package declares a package-private, no-argument
 * <em>instance</em> method {@code void main()} rather than the static
 * {@code public static void main(String[])} the JVM looks for. That is a
 * deliberate style for listings inside a book - the author calls the method
 * from inside the same package - but it means {@code exec:java},
 * {@code java -cp} and {@code mvn exec} cannot start any of them:
 *
 * <pre>
 * Error: Main method not found in class ...DefaultPolicyDemo
 * </pre>
 *
 * <p>This class lives in the same package so it can see those package-private
 * members, and invokes them the way the book intends. Each demo runs in its own
 * thread with a timeout, so a demo that blocks (the fixed-pool deadlock pattern
 * from Chapter 3, or a network client) cannot hang the whole run.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * mvn -Dexec.mainClass=ca.bazlur.modern.concurrency.c04.Ch04Driver exec:java \
 *     -Dexec.args="BasicExceptionHandling DefaultPolicyDemo"
 * }</pre>
 *
 * <p>With no arguments every demo runs. Structured concurrency is JEP 505 and
 * is still in preview on JDK 25, so the JVM needs {@code --enable-preview}.
 */
public final class Ch04Driver {

    private static final long DEMO_TIMEOUT_SECONDS = 60;

    private Ch04Driver() {
    }

    /** Every class in this package that declares a no-arg {@code void main()}. */
    private static final Map<String, Runnable> DEMOS = new LinkedHashMap<>();

    static {
        DEMOS.put("AwaitAllDemo", () -> new AwaitAllDemo().main());
        DEMOS.put("BackupDemo", () -> new BackupDemo().main());
        DEMOS.put("BasicExceptionHandling", () -> new BasicExceptionHandling().main());
        DEMOS.put("BatchValidationDemo", () -> new BatchValidationDemo().main());
        DEMOS.put("ComprehensiveConfigurationExample",
                () -> new ComprehensiveConfigurationExample().main());
        DEMOS.put("DefaultPolicyDemo", () -> new DefaultPolicyDemo().main());
        DEMOS.put("DistributedDatabase", () -> new DistributedDatabase().main());
        DEMOS.put("DocumentProcessor", () -> new DocumentProcessor().main());
        DEMOS.put("ExceptionBehaviorDemo", () -> new ExceptionBehaviorDemo().main());
        DEMOS.put("FailingProductServiceWithStructuredConcurrency",
                () -> new FailingProductServiceWithStructuredConcurrency().main());
        DEMOS.put("NamedThreadExample", () -> new NamedThreadExample().main());
        DEMOS.put("NewsAggregator", () -> new NewsAggregator().main());
        DEMOS.put("OrderProcessingService", () -> new OrderProcessingService().main());
        DEMOS.put("ProductService", () -> new ProductService().main());
        DEMOS.put("RacePolicyDemo", () -> new RacePolicyDemo().main());
        DEMOS.put("RateLimitedAPIService", () -> new RateLimitedAPIService().main());
        DEMOS.put("ResilientServer", () -> new ResilientServer().main());
        DEMOS.put("SystemHealthCheckDemo",
                () -> runInterruptible(() -> new SystemHealthCheckDemo().main()));
        DEMOS.put("TimeoutExample", () -> new TimeoutExample().main());
        DEMOS.put("WebCrawlerWithCircuitBreaker",
                () -> new WebCrawlerWithCircuitBreaker().main());
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            for (String name : args) {
                if (!DEMOS.containsKey(name)) {
                    System.err.printf("unknown demo %s (available: %s)%n",
                            name, String.join(", ", DEMOS.keySet()));
                    System.exit(2);
                }
            }
        }

        var selected = args.length > 0 ? args : DEMOS.keySet().toArray(String[]::new);
        int failures = 0;

        for (String name : selected) {
            System.out.println();
            System.out.println("=".repeat(72));
            System.out.printf(">>> %s%n", name);
            System.out.println("=".repeat(72));

            try {
                runWithTimeout(name, DEMOS.get(name));
            } catch (RuntimeException e) {
                failures++;
                System.out.printf("--- %s threw %s: %s%n",
                        name, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        System.out.println();
        System.out.println("=".repeat(72));
        System.out.printf("ran %d demo(s), %d failure(s)%n", selected.length, failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    /**
     * Adapts a task that declares a checked {@code InterruptedException} into a
     * {@link Runnable}.
     *
     * <p>One demo in this chapter ({@code SystemHealthCheckDemo}) declares
     * {@code void main() throws InterruptedException}, which a plain lambda
     * cannot propagate.
     */
    private static void runInterruptible(Interruptible task) {
        try {
            task.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("--- interrupted: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Interruptible {
        void run() throws InterruptedException;
    }

    /**
     * Runs one demo in its own thread and waits at most
     * {@value #DEMO_TIMEOUT_SECONDS} seconds.
     *
     * <p>The timeout is what makes this driver safe to run unattended. Without
     * it, a single demo that blocks - for example any example built on a fixed
     * thread pool that waits on its own future, the deadlock shown in Chapter 3
     * - would hang the whole driver forever.
     */
    private static void runWithTimeout(String name, Runnable demo) {
        Thread runner = Thread.ofPlatform().name("demo-" + name).start(demo);
        try {
            runner.join(Duration.ofSeconds(DEMO_TIMEOUT_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (runner.isAlive()) {
            System.out.printf("--- %s still running after %ds; abandoning it%n",
                    name, DEMO_TIMEOUT_SECONDS);
            runner.interrupt();
        }
    }
}
