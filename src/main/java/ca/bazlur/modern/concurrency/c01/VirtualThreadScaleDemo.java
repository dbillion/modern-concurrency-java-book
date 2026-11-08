package ca.bazlur.modern.concurrency.c01;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chapter 1, "Revolutionizing Concurrency in Java": measures the platform-thread
 * ceiling and the virtual-thread ceiling on the same machine, then prints the
 * ratio.
 *
 * <p>The book compares these two limits conceptually (and measures the platform
 * side in {@code ThreadLimitTest}) but never runs the virtual side. Both limits
 * here are facts about the operating system and the heap, not JVM tuning:
 *
 * <ul>
 *   <li>Platform threads are backed one-to-one by native threads, so the limit
 *       is reached when {@code pthread_create} fails with EAGAIN once the
 *       process exhausts its thread slots or address space. A default 1024 KB
 *       stack per thread is what makes that ceiling small.</li>
 *   <li>Virtual threads are heap-resident continuations that the JVM
 *       multiplexes onto a small carrier pool, so the ceiling is heap space
 *       rather than OS thread slots.</li>
 * </ul>
 *
 * <p>Both loops are unbounded by design, so always run this under a hard
 * timeout. Bind the heap so the virtual-thread number is reproducible rather
 * than dependent on host RAM:
 *
 * <pre>{@code
 * timeout 300 java --enable-preview -Xmx512m \
 *      -cp target/classes ca.bazlur.modern.concurrency.c01.VirtualThreadScaleDemo
 * }</pre>
 *
 * <p>Note that {@code OutOfMemoryError} is an {@link Error}, not an
 * {@link Exception}, so it must be caught as {@code Error}.
 */
public final class VirtualThreadScaleDemo {

    private VirtualThreadScaleDemo() {
    }

    public static void main(String[] args) {
        System.out.println("=== host ===");
        System.out.println("availableProcessors = "
                + Runtime.getRuntime().availableProcessors());
        System.out.println("maxHeap             = "
                + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024 * 1024)
                + " MB");
        System.out.println();

        long platform = measurePlatformThreads();
        System.out.println("platform threads reached : " + platform);

        long virtual = measureVirtualThreads();
        System.out.println("virtual threads reached  : " + virtual);

        System.out.println();
        System.out.println("ratio (virtual / platform) = "
                + String.format("%.1f", virtual / (double) platform) + "x");
    }

    /**
     * Starts platform threads until {@code pthread_create} fails.
     *
     * <p>Every thread is parked immediately and held until the end, so the
     * process really does hold all of them at once - which is the point. The
     * failure surfaces as {@code OutOfMemoryError} ("unable to create native
     * thread") once the OS refuses to allocate another.
     */
    private static long measurePlatformThreads() {
        List<Thread> threads = new ArrayList<>();
        AtomicInteger started = new AtomicInteger();
        Instant begin = Instant.now();

        try {
            while (true) {
                threads.add(Thread.ofPlatform()
                        .name("probe-", started.get())
                        .start(VirtualThreadScaleDemo::parkForever));
                started.incrementAndGet();
            }
        } catch (OutOfMemoryError e) {
            System.out.println("  platform failure: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        System.out.println("  elapsed = " + Duration.between(begin, Instant.now()).toMillis()
                + " ms for " + started.get() + " platform threads");
        threads.forEach(Thread::interrupt);
        return started.get();
    }

    /**
     * Starts virtual threads until the heap is exhausted.
     *
     * <p>Virtual threads are cheap but not free: each one carries a
     * continuation and a heap chunk for its stack, and frozen frames must stay
     * reachable while the thread is parked. Past a couple of million,
     * allocation fails.
     */
    private static long measureVirtualThreads() {
        List<Thread> threads = new ArrayList<>();
        AtomicInteger started = new AtomicInteger();
        Instant begin = Instant.now();

        try {
            while (true) {
                threads.add(Thread.startVirtualThread(VirtualThreadScaleDemo::parkForever));
                started.incrementAndGet();
            }
        } catch (OutOfMemoryError e) {
            System.out.println("  virtual failure: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        System.out.println("  elapsed = " + Duration.between(begin, Instant.now()).toMillis()
                + " ms for " + started.get() + " virtual threads");
        threads.forEach(Thread::interrupt);
        return started.get();
    }

    private static void parkForever() {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
