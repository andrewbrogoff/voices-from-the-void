package com.andrewbrogoff.vftv.core.flux.model;

import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import org.jspecify.annotations.Nullable;

/**
 * The pinned functions that price a spill in flux (Hush spec, "The pinned functions"). Pure, over
 * primitive inputs, allocation-free. Absent rules (a client before the first {@code FluxRules})
 * price every spill at 0.
 */
public final class FluxMath {
    /** The most entries {@code effective} and {@code neutral} hold: one per vis aspect. */
    public static final int MAX_VIS_ASPECTS = 8;

    /** The most vis one aspect of a quote costs. */
    public static final int MAX_VIS = 10_000;

    private FluxMath() {}

    /**
     * The flux a vis spend spills. {@code ORDINARY} work spills the vis warp added, {@code Σ max(0,
     * effective − neutral)}, at {@code flux.tax_pm}; {@code VOID} work spills its whole effective
     * cost at {@code flux.void_pm}. Each is rounded up and capped at {@link
     * HushLimits#MAX_SPILL_FLUX}.
     *
     * @param effective the quote's effective cost per aspect, 0 to 10,000 each, at most 8 entries
     * @param neutral the same quote's cost at warp factor 1.0, parallel to {@code effective}; read
     *     for {@code ORDINARY} only
     * @throws IllegalArgumentException on an input outside those bounds
     */
    public static int visSpill(
            VisKind kind, int[] effective, int[] neutral, @Nullable FluxRules rules) {
        checkVis(effective);
        return switch (kind) {
            case ORDINARY ->
                    perMille(surcharge(effective, neutral), rules == null ? 0 : rules.fluxTaxPm());
            case VOID -> perMille(total(effective), rules == null ? 0 : rules.fluxVoidPm());
        };
    }

    private static long surcharge(int[] effective, int[] neutral) {
        checkVis(neutral);
        if (neutral.length != effective.length) {
            throw new IllegalArgumentException("effective and neutral differ in length");
        }
        long sum = 0;
        for (int a = 0; a < effective.length; a++) {
            sum += Math.max(0, effective[a] - neutral[a]);
        }
        return sum;
    }

    private static long total(int[] effective) {
        long sum = 0;
        for (int e : effective) {
            sum += e;
        }
        return sum;
    }

    /**
     * The flux spilled essentia generates: {@code ceilDiv(total × flux.essentia_pm, 1000)}, capped
     * at {@link HushLimits#MAX_SPILL_FLUX}. One rate for every cause.
     *
     * @param amounts every spilled amount, missing aspects included
     * @param cause why it spilled; every cause prices alike
     * @throws IllegalArgumentException on a negative amount, or a total above {@link
     *     HushLimits#MAX_ESSENTIA_SPILL}
     */
    public static int essentiaFlux(int[] amounts, SpillCause cause, @Nullable FluxRules rules) {
        long total = 0;
        for (int amount : amounts) {
            if (amount < 0) {
                throw new IllegalArgumentException("negative essentia amount " + amount);
            }
            total += amount;
        }
        return essentiaFluxBound(total, cause, rules);
    }

    /**
     * The flux any spill totalling {@code units} generates: exactly {@link #essentiaFlux} for every
     * split of {@code units}, and monotone in {@code units}, so a preview of an upper bound is
     * exact.
     *
     * @throws IllegalArgumentException if {@code units} is negative or above {@link
     *     HushLimits#MAX_ESSENTIA_SPILL}
     */
    public static int essentiaFluxBound(long units, SpillCause cause, @Nullable FluxRules rules) {
        if (units < 0 || units > HushLimits.MAX_ESSENTIA_SPILL) {
            throw new IllegalArgumentException("essentia total " + units + " out of range");
        }
        return perMille(units, rules == null ? 0 : rules.fluxEssentiaPm());
    }

    private static int perMille(long amount, int rate) {
        return (int) Math.min(HushLimits.MAX_SPILL_FLUX, Math.ceilDiv(amount * rate, 1000L));
    }

    private static void checkVis(int[] costs) {
        if (costs.length > MAX_VIS_ASPECTS) {
            throw new IllegalArgumentException(costs.length + " vis aspects, above 8");
        }
        for (int cost : costs) {
            if (cost < 0 || cost > MAX_VIS) {
                throw new IllegalArgumentException("vis cost " + cost + " out of range");
            }
        }
    }
}
