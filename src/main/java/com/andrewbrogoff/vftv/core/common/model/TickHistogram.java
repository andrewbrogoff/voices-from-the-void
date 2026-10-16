package com.andrewbrogoff.vftv.core.common.model;

import java.util.Arrays;

/**
 * A preallocated log-linear histogram of non-negative durations in nanoseconds, HDR style.
 * Recording allocates nothing.
 *
 * <p>Values below 32 ns are exact; above, each power of two is split into 32 buckets, so a reported
 * percentile is at most 1/32 (about 3%) above the true value. Values above {@link #MAX} are
 * clamped. The count, total and maximum are exact. Not thread-safe: one thread records.
 */
public final class TickHistogram {
    /** The largest value recorded as itself, about 18 minutes; larger values are clamped. */
    public static final long MAX = (1L << 40) - 1;

    private static final int SUB_BITS = 5;
    private static final int SUB = 1 << SUB_BITS;
    private static final int BUCKETS = SUB + (40 - SUB_BITS) * SUB;

    private final long[] counts = new long[BUCKETS];
    private long count;
    private long total;
    private long max;

    /** Records one value; negative values count as 0. */
    public void record(long nanos) {
        long v = Math.min(Math.max(nanos, 0), MAX);
        counts[index(v)]++;
        count++;
        total += v;
        max = Math.max(max, v);
    }

    /** How many values were recorded. */
    public long count() {
        return count;
    }

    /** The exact sum of the recorded values. */
    public long total() {
        return total;
    }

    /** The exact largest recorded value, or 0 when empty. */
    public long max() {
        return max;
    }

    /** The exact mean, or 0 when empty. */
    public double mean() {
        return count == 0 ? 0 : (double) total / count;
    }

    /**
     * The value at quantile {@code q} in [0, 1]: the upper bound of the bucket holding the {@code
     * ceil(q * count)}-th smallest value, never above {@link #max()}. 0 when empty.
     */
    public long percentile(double q) {
        if (q < 0 || q > 1) {
            throw new IllegalArgumentException("q outside [0, 1]");
        }
        if (count == 0) {
            return 0;
        }
        long rank = Math.max(1, (long) Math.ceil(q * count));
        long seen = 0;
        int i = 0;
        while (seen + counts[i] < rank) {
            seen += counts[i];
            i++;
        }
        return Math.min(upperBound(i), max);
    }

    /** Forgets every recorded value. */
    public void reset() {
        Arrays.fill(counts, 0);
        count = 0;
        total = 0;
        max = 0;
    }

    static int index(long v) {
        if (v < SUB) {
            return (int) v;
        }
        int exponent = 63 - Long.numberOfLeadingZeros(v);
        int shift = exponent - SUB_BITS;
        int sub = (int) (v >>> shift) - SUB;
        return SUB + shift * SUB + sub;
    }

    static long upperBound(int index) {
        if (index < SUB) {
            return index;
        }
        int shift = (index - SUB) / SUB;
        int sub = (index - SUB) % SUB;
        long lower = (long) (SUB + sub) << shift;
        return lower + (1L << shift) - 1;
    }
}
