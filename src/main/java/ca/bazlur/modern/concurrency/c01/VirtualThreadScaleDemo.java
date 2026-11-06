package ca.bazlur.modern.concurrency.c01;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * Chapter 1, "Revolutionizing Concurrency in Java":
 * how many virtual threads can the JVM create, against the
 * 9,469 platform threads measured by ThreadLimitTest on the same host?
 */
public class VirtualThreadScaleDemo {

    public static void main(String[] args) {
        var started = new AtomicInteger(0);

        try {
            while (true) {
                Thread.ofVirtual().name("vt-" + started.getAndIncrement()).start(() -> {
                    LockSupport.park();
                });
                if (started.get() % 1_000_000 == 0) {
                    System.out.println("virtual threads alive: " + started.get());
                }
            }
        } catch (Error e) {
            System.out.println("Stopped after creating: " + started.get() + " virtual threads");
            System.out.println("Cause: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
