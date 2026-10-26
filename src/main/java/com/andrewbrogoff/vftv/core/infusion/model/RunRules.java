package com.andrewbrogoff.vftv.core.infusion.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The run-affecting tuning a commit freezes into a run's plan (infusion spec, "The plan"). A stored
 * run is validated only against these values, never against the tuning in force.
 *
 * @param interval {@code run.interval}, ticks between steps
 * @param rate {@code run.rate}, most units per draw
 * @param lonePedestal {@code symmetry.lone_pedestal}
 * @param loneItem {@code symmetry.lone_item}
 * @param perPair {@code stabilise.per_pair}
 * @param maxPairs {@code stabilise.max_pairs}
 * @param maxChance {@code upset.max_chance}, per mille
 * @param max {@code upset.max}
 * @param spill {@code upset.spill}
 * @param joltWeight {@code upset.jolt_weight}
 * @param spillWeight {@code upset.spill_weight}
 */
public record RunRules(
        int interval,
        int rate,
        int lonePedestal,
        int loneItem,
        int perPair,
        int maxPairs,
        int maxChance,
        int max,
        int spill,
        int joltWeight,
        int spillWeight) {
    /** The most total spill of one run, {@code upset.max × upset.spill}. */
    public static final int MAX_SPILL_TOTAL = 10_000;

    /** Each value within its tuning range; the weights sum above 0; the spill product bounded. */
    public RunRules {
        int[] v = {
            interval,
            rate,
            lonePedestal,
            loneItem,
            perPair,
            maxPairs,
            maxChance,
            max,
            spill,
            joltWeight,
            spillWeight
        };
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < v.length; i++) { // the first 11 tuning keys, in order
            InfusionTuning.Key k = InfusionTuning.KEYS.get(i);
            if (v[i] < k.min() || v[i] > k.max()) {
                problems.add(k.name() + ": " + v[i] + " outside " + k.min() + ".." + k.max());
            }
        }
        if (problems.isEmpty()) {
            problems.addAll(crossProblems(max, spill, joltWeight, spillWeight));
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    /** The two cross-key rules: the weights sum above 0, and {@code max × spill ≤ 10,000}. */
    static List<String> crossProblems(int max, int spill, int joltWeight, int spillWeight) {
        List<String> problems = new ArrayList<>();
        if (joltWeight + spillWeight == 0) {
            problems.add("upset.jolt_weight + upset.spill_weight is 0");
        }
        if ((long) max * spill > MAX_SPILL_TOTAL) {
            problems.add("upset.max × upset.spill is over " + MAX_SPILL_TOTAL);
        }
        return problems;
    }
}
