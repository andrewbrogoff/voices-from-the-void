package com.andrewbrogoff.vftv.core.worldgen.model;

import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import java.util.ArrayList;
import java.util.List;

/**
 * The structure node roll (worldgen spec, "Processors", "The roll"): pure and keyed by position.
 * {@code h = fold([CHOR, worldSeed, asLong(target)])}; the clarity is {@code weighted} over the
 * first draw of {@code stream(h)}, then each aspect's max is {@code bounded} over the next draw, in
 * ascending aspect id.
 */
public final class NodeRoll {
    private NodeRoll() {}

    /**
     * One aspect of the type and its range.
     *
     * @param aspect the aspect id
     * @param min the least max
     * @param max the greatest max
     */
    public record Range(String aspect, int min, int max) {}

    /**
     * A roll's result.
     *
     * @param h the fold
     * @param clarity the clarity index: faint 0, steady 1, clear 2
     * @param maxes each aspect's max, in the ranges' order
     */
    public record Result(long h, int clarity, List<Integer> maxes) {
        /** Copies the maxes. */
        public Result {
            maxes = List.copyOf(maxes);
        }
    }

    /** {@code fold([CHOR, worldSeed, posLong])}. */
    public static long hash(long worldSeed, long posLong) {
        return Hashes.fold(Salts.word(Salts.CHOR), worldSeed, posLong);
    }

    /**
     * Rolls a node: one clarity draw and one draw per aspect, at most 7.
     *
     * @param worldSeed the world seed
     * @param posLong the target's {@code BlockPos.asLong()}
     * @param clarity the faint, steady and clear weights
     * @param aspects the type's aspects, ascending id
     */
    public static Result roll(long worldSeed, long posLong, int[] clarity, List<Range> aspects) {
        long h = hash(worldSeed, posLong);
        int c = Hashes.weighted(Hashes.at(h, 1), clarity);
        List<Integer> maxes = new ArrayList<>(aspects.size());
        for (int i = 0; i < aspects.size(); i++) {
            Range r = aspects.get(i);
            maxes.add((int) Hashes.bounded(Hashes.at(h, i + 2), r.min(), r.max()));
        }
        return new Result(h, c, maxes);
    }
}
