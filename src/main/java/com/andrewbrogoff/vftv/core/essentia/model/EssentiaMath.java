package com.andrewbrogoff.vftv.core.essentia.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The essentia spec's small pinned rules, loader-free: the jar's ranks, the removal carry, the
 * liquid height, the Alembic's choice, line admission and the labelled-tube face normalisation.
 * Aspects are opaque values compared with {@code equals} (the adapter passes ids or indices).
 */
public final class EssentiaMath {
    /** A tube face that joins nothing. */
    public static final int NONE = 0;

    /** A tube face joined to a neighbour. */
    public static final int CONNECTED = 1;

    /** A tube face closed by hand. */
    public static final int BLOCKED = 2;

    /** An Alembic label: unlabelled. */
    public static final int LABEL_NONE = -1;

    /** An Alembic label that admits nothing (a missing aspect). */
    public static final int LABEL_NOTHING = -2;

    private EssentiaMath() {}

    /**
     * What a jar holds, or a snapshot of it: one aspect and its amount, or nothing.
     *
     * @param aspect the held aspect, or null when empty
     * @param amount 0 when empty, otherwise positive
     */
    public record Contents<A>(@Nullable A aspect, int amount) {
        /** Checks that the aspect and a positive amount are both present or both absent. */
        public Contents {
            if ((aspect == null) != (amount == 0) || amount < 0) {
                throw new IllegalArgumentException("aspect " + aspect + " with amount " + amount);
            }
        }

        /** The empty contents. */
        public static <A> Contents<A> empty() {
            return new Contents<>(null, 0);
        }

        /** Whether nothing is held. */
        public boolean isEmpty() {
            return aspect == null;
        }
    }

    /** Whether a jar accepts {@code x}: the label is X or absent, and the aspect is X or absent. */
    public static <A> boolean accepts(@Nullable A label, @Nullable A held, A x) {
        return (label == null || label.equals(x)) && (held == null || held.equals(x));
    }

    /**
     * A readable, available jar's suction for {@code x} (essentia spec, "Suction"): its rank if it
     * has room and accepts X, else 0.
     */
    public static <A> int jarSuction(
            @Nullable A label,
            Contents<A> contents,
            int capacity,
            A x,
            int unlabelledRank,
            int labelledRank) {
        if (contents.amount() >= capacity || !accepts(label, contents.aspect(), x)) {
            return 0;
        }
        return label == null ? unlabelledRank : labelledRank;
    }

    /** A readable, available jar's hold for {@code x}: its rank if it holds X, else 0. */
    public static <A> int jarHold(
            @Nullable A label, Contents<A> contents, A x, int unlabelledRank, int labelledRank) {
        if (!x.equals(contents.aspect())) {
            return 0;
        }
        return label == null ? unlabelledRank : labelledRank;
    }

    /**
     * The removal carry (essentia spec, "Removal and drops"): the live aspect at the minimum of the
     * live amount and its amount in every live snapshot, an absent aspect counting as 0.
     */
    public static <A> Contents<A> carry(Contents<A> live, List<Contents<A>> snapshots) {
        A x = live.aspect();
        if (x == null) {
            return Contents.empty();
        }
        int min = live.amount();
        for (Contents<A> s : snapshots) {
            min = Math.min(min, x.equals(s.aspect()) ? s.amount() : 0);
        }
        return min == 0 ? Contents.empty() : new Contents<>(x, min);
    }

    /**
     * What settlement spills: the settled contents minus the reserved carry. The carry never
     * exceeds a settled outcome, so this never goes negative.
     *
     * @throws IllegalArgumentException if the carry is not part of the settled contents
     */
    public static <A> Contents<A> spill(Contents<A> settled, Contents<A> carry) {
        if (carry.isEmpty()) {
            return settled;
        }
        if (!Objects.equals(carry.aspect(), settled.aspect())
                || carry.amount() > settled.amount()) {
            throw new IllegalArgumentException("carry " + carry + " exceeds settled " + settled);
        }
        int left = settled.amount() - carry.amount();
        return left == 0 ? Contents.empty() : new Contents<>(settled.aspect(), left);
    }

    /**
     * The jar renderer's liquid height in pixels: {@code min(7, ceil(7 × amount / capacity))}, and
     * 0 for nothing.
     */
    public static int liquidHeight(int amount, int capacity) {
        if (amount <= 0) {
            return 0;
        }
        return (int) Math.min(7, (7L * amount + capacity - 1) / capacity);
    }

    /**
     * The Alembic's aspect (essentia spec, "The pour", step 3). Only held aspects with {@code
     * suction > hold} count; a labelled alembic pours only its label; an unlabelled one the highest
     * suction, ties to the larger source amount, then the lowest aspect.
     *
     * @param aspects the source's held aspects, ascending, each with a positive amount
     * @param amounts their amounts
     * @param holds the source's hold for each
     * @param suctions the target's suction for each
     * @param label an aspect, {@link #LABEL_NONE} or {@link #LABEL_NOTHING}
     * @return the index into {@code aspects}, or -1 for no pour
     */
    public static int alembicChoice(
            int[] aspects, int[] amounts, int[] holds, int[] suctions, int label) {
        int best = -1;
        for (int i = 0; i < aspects.length; i++) {
            if (amounts[i] <= 0 || suctions[i] <= holds[i]) {
                continue;
            }
            if (label != LABEL_NONE) {
                if (aspects[i] == label) {
                    return i;
                }
                continue;
            }
            if (best < 0
                    || suctions[i] > suctions[best]
                    || (suctions[i] == suctions[best] && amounts[i] > amounts[best])
                    || (suctions[i] == suctions[best]
                            && amounts[i] == amounts[best]
                            && aspects[i] < aspects[best])) {
                best = i;
            }
        }
        return best;
    }

    /**
     * Whether placing a tube or valve, or opening a valve, is refused for joining lines beyond the
     * cap (essentia spec, "Admission"). Per direction: whether the proposed state connects that
     * way, whether the neighbour connects back, and the neighbour's line (its id, member count and
     * whether it is live and built). A line joined on several faces counts once.
     */
    public static boolean admissionRefused(
            boolean[] proposed,
            boolean[] neighbour,
            long[] lineIds,
            int[] counts,
            boolean[] built,
            int max) {
        long[] seen = new long[proposed.length];
        int distinct = 0;
        long sum = 1;
        for (int d = 0; d < proposed.length; d++) {
            if (!proposed[d] || !neighbour[d]) {
                continue;
            }
            if (!built[d]) {
                return false;
            }
            boolean dup = false;
            for (int i = 0; i < distinct; i++) {
                dup |= seen[i] == lineIds[d];
            }
            if (!dup) {
                seen[distinct++] = lineIds[d];
                sum += counts[d];
            }
        }
        return sum > max;
    }

    /**
     * The labelled-tube normalisation: a labelled tube keeps a glyph face, so if all six faces are
     * {@link #CONNECTED} the first in Direction order is {@link #BLOCKED}. Returns a new array.
     */
    public static int[] normalise(int[] faces, boolean labelled) {
        int[] out = faces.clone();
        if (labelled && Arrays.stream(out).allMatch(f -> f == CONNECTED)) {
            out[0] = BLOCKED;
        }
        return out;
    }

    /**
     * A hand toggle of face {@code d} between {@link #BLOCKED} and its computed state. A labelled
     * tube never unblocks its only free face. Returns a new, normalised array.
     */
    public static int[] toggle(int[] faces, int d, int computed, boolean labelled) {
        int[] out = faces.clone();
        if (out[d] != BLOCKED) {
            out[d] = BLOCKED;
            return out;
        }
        out[d] = computed;
        if (labelled && Arrays.stream(out).allMatch(f -> f == CONNECTED)) {
            out[d] = BLOCKED;
        }
        return out;
    }
}
