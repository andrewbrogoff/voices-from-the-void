package com.andrewbrogoff.vftv.core.common.model;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.util.Arrays;

/**
 * The allocation budget for a hot path designed to allocate nothing (quality gates, "Performance"):
 * some round of many calls allocates exactly 0 bytes on this thread.
 *
 * <p>Rounds repeat until one measures zero, at most {@value #ROUNDS}. The JVM itself allocates on
 * the measuring thread a few times, and not at a fixed point: when HotSpot queues a C2 compile it
 * resolves the string constants of the compiled method's class on the requesting thread, and when
 * that happens depends on how busy the compilers are. Each such allocation happens once, so later
 * rounds are clean. Allocation by the code under test recurs in every round of many calls, so it
 * can never give a zero round; the failure message lists every round.
 */
public final class Allocations {
    private static final int ROUNDS = 50;

    private Allocations() {}

    /** Fails unless one run of {@code round} allocates nothing; {@code what} names the round. */
    public static void assertNone(String what, Runnable round) {
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long[] bytes = new long[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            long before = threads.getThreadAllocatedBytes(thread);
            round.run();
            bytes[i] = threads.getThreadAllocatedBytes(thread) - before;
            if (bytes[i] == 0) {
                return;
            }
        }
        fail(what + " allocated in every round, bytes per round: " + Arrays.toString(bytes));
    }
}
