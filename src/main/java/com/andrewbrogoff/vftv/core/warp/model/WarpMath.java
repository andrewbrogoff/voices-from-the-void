package com.andrewbrogoff.vftv.core.warp.model;

import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The warp arithmetic, all integer and pure over its inputs and the {@link WarpRules} (warp spec):
 * bands, the three dials, the chance, the counter and the preview. Client and server call the same
 * functions, so they compute the same values.
 */
public final class WarpMath {
    /** The largest size of each part of a {@code WarpCost}: 1,000. */
    public static final int COST_MAX = 1000;

    private WarpMath() {}

    /** {@code |{t ∈ bands : total ≥ t}|}, 0–3 ("Bands"). */
    public static int band(int total, WarpRules rules) {
        int band = 0;
        for (int threshold : rules.bands()) {
            if (total >= threshold) {
                band++;
            }
        }
        return band;
    }

    /**
     * The vis factor in per mille ("The dials"): {@code 1000 + min(tax.max_pm, total ×
     * tax.per_warp_pm)} for ordinary vis with the tax on, else 1000.
     */
    public static int visFactorPm(int total, boolean ordinary, WarpRules rules) {
        return ordinary && rules.tax()
                ? 1000 + Math.min(rules.taxMaxPm(), total * rules.taxPerWarpPm())
                : 1000;
    }

    /** The infusion stability penalty ("The dials"); 0 for Void work or with the tax off. */
    public static int infusionPenalty(int total, boolean ordinary, WarpRules rules) {
        return ordinary && rules.tax()
                ? Math.min(
                        rules.infusionMaxPenalty(),
                        Math.floorDiv(total * rules.infusionPerWarpPm(), 1000))
                : 0;
    }

    /**
     * The Void-tier amplifier's warp ("The dials"): {@code total}, plus the Joining bonus for a
     * Joined Listener; 0 with the amplifier off.
     */
    public static int amplifierWarp(int total, boolean joined, WarpRules rules) {
        return rules.amplifier() ? total + (joined ? rules.joiningAmplifierBonus() : 0) : 0;
    }

    /** A stored counter as read under a possibly lowered {@code counter.max}. */
    public static int counter(int stored, WarpRules rules) {
        return Math.min(stored, rules.counterMax());
    }

    /**
     * The chance per check in per mille ("The counter and the chance"): 0 without warp, else {@code
     * min(max, base + P × per_permanent + counter × per_counter)}.
     */
    public static int chancePm(int total, int permanent, int counter, WarpRules rules) {
        if (total == 0) {
            return 0;
        }
        long p =
                rules.chanceBasePm()
                        + (long) permanent * rules.chancePerPermanentPm()
                        + (long) counter(counter, rules) * rules.chancePerCounterPm();
        return (int) Math.min(rules.chanceMaxPm(), p);
    }

    /** The counter after a commit or apply that gained {@code gains} warp points in all. */
    public static int counterAfterGain(int counter, int gains, WarpRules rules) {
        return (int)
                Math.min(
                        rules.counterMax(),
                        counter(counter, rules) + (long) gains * rules.counterPerGain());
    }

    /** The counter after one Void spend. */
    public static int counterAfterVoidWork(int counter, WarpRules rules) {
        return Math.min(rules.counterMax(), counter(counter, rules) + rules.counterPerVoidWork());
    }

    /** The counter after a fired Voice event. */
    public static int counterAfterEvent(int counter, WarpRules rules) {
        return Math.max(0, counter(counter, rules) - rules.counterPerEvent());
    }

    /** The positive parts of a change, summed: what raises the counter. */
    public static int gains(WarpPreview actual) {
        return Math.max(0, actual.dT()) + Math.max(0, actual.dL()) + Math.max(0, actual.dP());
    }

    /**
     * Whether a cost names a listed milestone ("Preview", rule 3): a positive permanent part from
     * an entry or milestone source whose id is in {@code permanent.milestones}. A commit writes its
     * id into {@code milestones_paid} even when the cap clipped it.
     */
    public static boolean isMilestone(
            int dP, WarpSourceKind kind, @Nullable String id, WarpRules rules) {
        return dP > 0
                && (kind == WarpSourceKind.ENTRY || kind == WarpSourceKind.MILESTONE)
                && id != null
                && rules.permanentMilestones().containsKey(id);
    }

    /**
     * The preview of a cost against a readable record ("Preview", rules 2-4), or null for {@code
     * INVALID}.
     *
     * @param paid the record's {@code milestones_paid}, as {@code ns:path} strings
     * @param id the source's id, for an entry, bargain or milestone
     */
    public static @Nullable WarpPreview preview(
            int t,
            int l,
            int p,
            Set<String> paid,
            int dT,
            int dL,
            int dP,
            WarpSourceKind kind,
            @Nullable String id,
            WarpRules rules) {
        boolean command = kind == WarpSourceKind.COMMAND;
        if (Math.abs(dT) > COST_MAX || Math.abs(dL) > COST_MAX || Math.abs(dP) > COST_MAX) {
            return null;
        }
        if (dP < 0 && !command) {
            return null;
        }
        int askedP = dP;
        if (dP > 0 && !command) {
            Integer amount = id == null ? null : rules.permanentMilestones().get(id);
            if (!isMilestone(dP, kind, id, rules) || amount == null || amount != dP) {
                return null;
            }
            if (paid.contains(id)) {
                askedP = 0;
            }
        }
        int t2 = clamp(t + dT, WarpLimits.T_MAX);
        int l2 = clamp(l + dL, WarpLimits.L_MAX);
        int p2 =
                askedP >= 0
                        ? Math.max(p, Math.min(rules.permanentCap(), p + askedP))
                        : Math.max(0, p + askedP);
        int aT = t2 - t;
        int aL = l2 - l;
        int aP = p2 - p;
        return new WarpPreview(aT, aL, aP, aT != dT || aL != dL || aP != dP);
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }
}
