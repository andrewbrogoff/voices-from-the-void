package com.andrewbrogoff.vftv.core.vis.model;

/**
 * One relay pulse of one component (wands spec, "Flow"), over preallocated arrays so it allocates
 * nothing: sinks visited from a rotating start, each vis aspect in ascending order, each taking
 * {@code min(rate, room)} from the sources in order (already sorted by node id).
 */
public final class RelayPulse {
    private RelayPulse() {}

    /** Receives each transfer, in the order the pulse makes it. */
    @FunctionalInterface
    public interface Moves {
        /** {@code amount} of aspect index {@code aspect} from source {@code source} to sink. */
        void move(int sink, int aspect, int source, int amount);
    }

    /** The first sink a pulse visits: {@code (gameTime / period) % sinkCount}. */
    public static int start(long gameTime, int period, int sinkCount) {
        return (int) Long.remainderUnsigned(Long.divideUnsigned(gameTime, period), sinkCount);
    }

    /**
     * Plans and applies one pulse in place.
     *
     * @param gameTime the level's game time
     * @param period {@code relay.pulse}
     * @param rate {@code relay.rate}
     * @param aspects the vis aspect count; arrays are indexed {@code [i * aspects + a]}
     * @param sources each source's current per aspect; decremented by what it gives
     * @param sourceCount how many sources
     * @param room each sink's room per aspect; decremented by what it takes
     * @param sinkCount how many sinks, at least 1
     * @param moves told of every transfer
     * @return the start index
     */
    public static int run(
            long gameTime,
            int period,
            int rate,
            int aspects,
            int[] sources,
            int sourceCount,
            int[] room,
            int sinkCount,
            Moves moves) {
        int start = start(gameTime, period, sinkCount);
        for (int i = 0; i < sinkCount; i++) {
            int sink = (start + i) % sinkCount;
            for (int a = 0; a < aspects; a++) {
                int need = Math.min(rate, room[sink * aspects + a]);
                for (int s = 0; s < sourceCount && need > 0; s++) {
                    int have = sources[s * aspects + a];
                    int take = Math.min(need, have);
                    if (take > 0) {
                        sources[s * aspects + a] = have - take;
                        room[sink * aspects + a] -= take;
                        need -= take;
                        moves.move(sink, a, s, take);
                    }
                }
            }
        }
        return start;
    }
}
