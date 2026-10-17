package com.andrewbrogoff.vftv.core.vis.model;

/**
 * Node regeneration, computed and never ticked (wands spec, "Regeneration"). Allocation-free: it
 * settles parallel arrays in place.
 */
public final class Regen {
    private Regen() {}

    /**
     * Settles a node at {@code now}: each aspect below its max gains {@code n × step}, where {@code
     * n} whole intervals have passed, capped at its max; the remainder is kept. A clock that went
     * backwards counts as zero elapsed and moves the tick back to {@code now}.
     *
     * @param max each aspect's max
     * @param current each aspect's current, updated in place
     * @param tick the regeneration clock
     * @param now the game time
     * @param interval the interval in force since {@code tick}, 1 to 24,000
     * @param divisor the divisor in force since {@code tick}, 1 to 1,000
     * @return the new clock
     */
    public static long settle(
            int[] max, int[] current, long tick, long now, int interval, int divisor) {
        if (now < tick) {
            return now;
        }
        long n = (now - tick) / interval;
        for (int a = 0; a < max.length; a++) {
            int step = Math.max(1, max[a] / divisor);
            if (current[a] < max[a]) {
                long need = Math.ceilDiv(max[a] - current[a], step);
                current[a] = n >= need ? max[a] : current[a] + (int) n * step;
            }
        }
        return tick + n * interval;
    }
}
