package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The pure vis quote (wands spec, "The quote"): integer per-mille arithmetic after one conversion
 * per input, identical on both sides. The kernels ({@link #multiplier}, {@link #effective}) are
 * allocation-free; {@link #quote} assembles the immutable result.
 */
public final class QuoteMath {
    private QuoteMath() {}

    /**
     * Everything a quote reads, resolved from synced data.
     *
     * @param base the base cost per vis aspect id, 0 to 1000, at most 8 entries
     * @param costMultiplier the cap's {@code cost_multiplier}
     * @param discount the {@code vftv:vis_discount} attribute's value
     * @param maxDiscount {@code quote.max_discount}
     * @param warpFactor the warp spec's vis factor W
     * @param minMultiplier {@code quote.min_multiplier}
     * @param maxMultiplier {@code quote.max_multiplier}
     * @param kind ordinary or Void work, for the spill
     * @param flux the synced flux rules, or null (the spill is then 0)
     * @param capacity the rod's capacity
     * @param pools the wand's clamped pool per vis aspect id (a missing entry is 0)
     * @param inert whether the wand is inert
     */
    public record Inputs(
            SortedMap<String, Integer> base,
            double costMultiplier,
            double discount,
            double maxDiscount,
            double warpFactor,
            double minMultiplier,
            double maxMultiplier,
            VisKind kind,
            @Nullable FluxRules flux,
            int capacity,
            SortedMap<String, Integer> pools,
            boolean inert) {
        /** Copies the maps and checks the base: a bad base is a data error, so this throws. */
        public Inputs {
            if (base.size() > VisLimits.MAX_VIS_ASPECTS) {
                throw new IllegalArgumentException("more than 8 aspects in a base cost");
            }
            for (int b : base.values()) {
                if (b < VisLimits.MIN_BASE_COST || b > VisLimits.MAX_BASE_COST) {
                    throw new IllegalArgumentException("base cost " + b + " out of range");
                }
            }
            base = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(base));
            pools = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(pools));
        }
    }

    /**
     * The quote's numbers.
     *
     * @param base the base cost, by aspect id
     * @param effective the effective cost, by aspect id
     * @param m the clamped multiplier, per mille
     * @param spill the spill
     * @param cap the cap factor, per mille
     * @param discount {@code d}, per mille
     * @param warp {@code w}, per mille
     * @param discountCapped whether the discount exceeded the maximum
     * @param clamped whether {@code m} was clamped
     * @param exceedsCapacity whether some effective cost exceeds the capacity
     * @param inert whether the wand is inert
     * @param affordable whether not inert and every pool covers its cost
     * @param shortAspects the aspects whose pool is below their cost, ascending
     */
    public record Result(
            SortedMap<String, Integer> base,
            SortedMap<String, Integer> effective,
            int m,
            int spill,
            int cap,
            int discount,
            int warp,
            boolean discountCapped,
            boolean clamped,
            boolean exceedsCapacity,
            boolean inert,
            boolean affordable,
            List<String> shortAspects) {
        /** Copies the collections. */
        public Result {
            base = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(base));
            effective = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(effective));
            shortAspects = List.copyOf(shortAspects);
        }
    }

    /** A per-mille conversion: {@code Math.round(value × 1000)}. */
    public static int perMille(double value) {
        return (int) Math.round(value * 1000);
    }

    /** {@code d}: the discount clamped to {@code [0, max]}, per mille. */
    public static int discount(double value, double max) {
        double v = Double.isNaN(value) ? 0 : value;
        return perMille(Math.max(0, Math.min(max, v)));
    }

    /** Whether the discount exceeds its maximum, compared per mille. */
    public static boolean discountCapped(double value, double max) {
        return !Double.isNaN(value) && Math.round(value * 1000) > Math.round(max * 1000);
    }

    /** {@code w}: W clamped to {@code [0, 10]}, per mille; a non-finite W counts as 1.0. */
    public static int warp(double factor) {
        double w = Double.isFinite(factor) ? factor : 1.0;
        return perMille(Math.max(0, Math.min(10, w)));
    }

    /** {@code m₀}: {@code cap × (1000 − d) × w}, rounded half up to per mille. */
    public static long unclamped(int cap, int d, int w) {
        long raw = (long) cap * (1000 - d) * w;
        return (raw + 500_000) / 1_000_000;
    }

    /** {@code m}: {@code m₀} clamped to {@code [min, max]} (per mille). */
    public static int multiplier(int cap, int d, int w, int min, int max) {
        return (int) Math.max(min, Math.min(max, unclamped(cap, d, w)));
    }

    /** One effective cost: 0 for a zero base, else {@code max(1, ceilDiv(base × m, 1000))}. */
    public static int effective(int base, int m) {
        return base == 0 ? 0 : (int) Math.max(1, Math.ceilDiv((long) base * m, 1000L));
    }

    /** The quote. */
    public static Result quote(Inputs in) {
        int cap = perMille(in.costMultiplier());
        int d = discount(in.discount(), in.maxDiscount());
        int w = warp(in.warpFactor());
        int min = perMille(in.minMultiplier());
        int max = perMille(in.maxMultiplier());
        int m = multiplier(cap, d, w, min, max);
        int mNeutral = multiplier(cap, d, 1000, min, max);
        int n = in.base().size();
        int[] effective = new int[n];
        int[] neutral = new int[n];
        SortedMap<String, Integer> byAspect = new TreeMap<>();
        boolean exceeds = false;
        List<String> shortAspects = new ArrayList<>();
        int a = 0;
        for (var cost : in.base().entrySet()) {
            effective[a] = effective(cost.getValue(), m);
            neutral[a] = effective(cost.getValue(), mNeutral);
            byAspect.put(cost.getKey(), effective[a]);
            exceeds |= effective[a] > in.capacity();
            if (!in.inert() && in.pools().getOrDefault(cost.getKey(), 0) < effective[a]) {
                shortAspects.add(cost.getKey());
            }
            a++;
        }
        return new Result(
                in.base(),
                byAspect,
                m,
                FluxMath.visSpill(in.kind(), effective, neutral, in.flux()),
                cap,
                d,
                w,
                discountCapped(in.discount(), in.maxDiscount()),
                m != unclamped(cap, d, w),
                exceeds,
                in.inert(),
                !in.inert() && shortAspects.isEmpty(),
                shortAspects);
    }
}
