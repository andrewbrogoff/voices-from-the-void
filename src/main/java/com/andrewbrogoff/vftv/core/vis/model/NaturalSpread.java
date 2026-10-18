package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Natural node spread: one seeded candidate per 64×64 cell (wands spec, "Natural spread"). The
 * draws are successive {@code next()} of {@code stream(fold([NODE, seed, rx, rz]))}, in a fixed
 * order, all made whatever a later step decides.
 */
public final class NaturalSpread {
    /** The cell side, in blocks. */
    public static final int CELL = 64;

    private NaturalSpread() {}

    /**
     * The placement numbers.
     *
     * @param chance the chance a cell spawns, 0 to 1
     * @param heightMin the least offset above the surface
     * @param heightMax the greatest offset above the surface
     * @param retries how many blocks above the first the air search tries
     */
    public record Placement(double chance, int heightMin, int heightMax, int retries) {}

    /**
     * One eligible natural type.
     *
     * @param id its id
     * @param weight its spread weight
     * @param aspects each aspect's {@code [min, max]} range, by aspect id
     * @param clarity its clarity weights: faint, steady, clear
     */
    public record Eligible(
            String id, int weight, SortedMap<String, int[]> aspects, List<Integer> clarity) {}

    /**
     * A cell's candidate, before the biome and the air search.
     *
     * @param h the cell's hash
     * @param spawns whether the cell spawns
     * @param x the column's x
     * @param z the column's z
     * @param offset the offset above the surface
     */
    public record Candidate(long h, boolean spawns, int x, int z, int offset) {}

    /**
     * The rolled node, from the eligible list.
     *
     * @param type the type's id
     * @param clarity 0 faint, 1 steady, 2 clear
     * @param maxes each aspect's rolled max, by aspect id
     */
    public record Roll(String type, int clarity, SortedMap<String, Integer> maxes) {}

    /** The cell of a block coordinate: {@code floorDiv(x, 64)}. */
    public static int cell(int block) {
        return Math.floorDiv(block, CELL);
    }

    /** The cell's hash, {@code fold([NODE, worldSeed, rx, rz])}. */
    public static long hash(long seed, int rx, int rz) {
        return Hashes.fold(Salts.word(Salts.NODE), seed, rx, rz);
    }

    /** Draws 1 to 3: whether the cell spawns, the column and the offset. */
    public static Candidate candidate(long seed, int rx, int rz, Placement p) {
        long h = hash(seed, rx, rz);
        long d0 = Hashes.at(h, 1);
        long d1 = Hashes.at(h, 2);
        long d2 = Hashes.at(h, 3);
        boolean spawns = (d0 >>> 11) * 0x1.0p-53 < p.chance();
        int x = CELL * rx + (int) (d1 & 63);
        int z = CELL * rz + (int) ((d1 >>> 6) & 63);
        int offset = (int) Hashes.bounded(d2, p.heightMin(), p.heightMax());
        return new Candidate(h, spawns, x, z, offset);
    }

    /**
     * Draws 4 onward: the type, weighted over {@code eligible} sorted by id; the clarity; then one
     * max per aspect in ascending id.
     *
     * @param eligible the eligible types, non-empty, in any order
     */
    public static Roll roll(long h, List<Eligible> eligible) {
        List<Eligible> sorted =
                eligible.stream().sorted((a, b) -> a.id().compareTo(b.id())).toList();
        int[] weights = sorted.stream().mapToInt(Eligible::weight).toArray();
        Eligible type = sorted.get(Hashes.weighted(Hashes.at(h, 4), weights));
        int clarity =
                Hashes.weighted(
                        Hashes.at(h, 5), type.clarity().stream().mapToInt(i -> i).toArray());
        SortedMap<String, Integer> maxes = new TreeMap<>();
        long draw = 6;
        for (var aspect : new TreeMap<>(type.aspects()).entrySet()) {
            int[] range = aspect.getValue();
            maxes.put(
                    aspect.getKey(),
                    (int) Hashes.bounded(Hashes.at(h, draw++), range[0], range[1]));
        }
        return new Roll(type.id(), clarity, maxes);
    }
}
