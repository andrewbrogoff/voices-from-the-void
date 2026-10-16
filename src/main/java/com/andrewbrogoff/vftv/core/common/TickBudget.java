package com.andrewbrogoff.vftv.core.common;

import com.andrewbrogoff.vftv.core.common.model.TickHistogram;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The server instrument for the whole-mod envelope (architecture, "Tick timing").
 *
 * <p>Every seam listener, block-entity ticker and {@code Mob.tick} override of ours runs inside
 * {@code try (var _ = TickBudget.enter("<feature>")) { ... }} (the unnamed variable keeps {@code
 * -Xlint:try} quiet). Nested entries count toward the outermost only. {@link #closeTick} runs at
 * the start of each server tick and records each section's sum, and the total ("mod time"), into a
 * preallocated histogram.
 *
 * <p>Off unless the system property {@value #PROPERTY} is {@code true}: the disabled wrapper is a
 * branch on a static final the JIT removes. Only the server thread is measured; an entry on any
 * other thread is a no-op.
 */
public final class TickBudget {
    /** The system property that turns measurement on. */
    public static final String PROPERTY = "vftv.tickBudget";

    /** Whether measurement is on, read once at class load. */
    public static final boolean ENABLED = Boolean.getBoolean(PROPERTY);

    /** The name of the total across every section. */
    public static final String TOTAL = "total";

    private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();
    private static final TickHistogram TOTAL_HISTOGRAM = new TickHistogram();
    private static final Scope MEASURED = new Scope();
    private static final Scope IGNORED = new Scope();

    private static @Nullable Thread serverThread;
    private static @Nullable Section active;
    private static int depth;
    private static long start;

    private TickBudget() {}

    /** Enters a section; close the returned scope in a try-with-resources. */
    public static Scope enter(String section) {
        if (!ENABLED || !Thread.currentThread().equals(serverThread)) {
            return IGNORED;
        }
        if (depth++ == 0) {
            active = SECTIONS.computeIfAbsent(section, Section::new);
            start = System.nanoTime();
        }
        return MEASURED;
    }

    /**
     * Closes the tick that just ended, recording each section's sum and the total. Called by a
     * {@code ServerTickEvent.Pre} listener; the first call only marks the server thread.
     */
    public static void closeTick() {
        if (!ENABLED) {
            return;
        }
        if (!Thread.currentThread().equals(serverThread)) {
            serverThread = Thread.currentThread();
            return;
        }
        long total = 0;
        for (Section section : SECTIONS.values()) {
            section.histogram.record(section.tickSum);
            total += section.tickSum;
            section.tickSum = 0;
        }
        TOTAL_HISTOGRAM.record(total);
    }

    /** One histogram's summary, in nanoseconds per tick. */
    public record Summary(String section, long ticks, double mean, long p99, long max) {}

    /** The summaries of the total and of every section, the total first. */
    public static List<Summary> snapshot() {
        List<Summary> out = new ArrayList<>();
        out.add(summary(TOTAL, TOTAL_HISTOGRAM));
        SECTIONS.values().forEach(s -> out.add(summary(s.name, s.histogram)));
        return out;
    }

    /** Forgets every recorded tick, e.g. at the end of a warm-up. */
    public static void reset() {
        TOTAL_HISTOGRAM.reset();
        SECTIONS.values().forEach(s -> s.histogram.reset());
    }

    private static Summary summary(String name, TickHistogram h) {
        return new Summary(name, h.count(), h.mean(), h.percentile(0.99), h.max());
    }

    private static void exit() {
        if (--depth == 0 && active != null) {
            active.tickSum += System.nanoTime() - start;
            active = null;
        }
    }

    /** An entered section; closing it ends the section's time. */
    public static final class Scope implements AutoCloseable {
        private Scope() {}

        @Override
        public void close() {
            if (this == MEASURED) {
                exit();
            }
        }
    }

    private static final class Section {
        final String name;
        final TickHistogram histogram = new TickHistogram();
        long tickSum;

        Section(String name) {
            this.name = name;
        }
    }
}
