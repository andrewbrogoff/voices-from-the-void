package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * A debit plan (research spec, "Paying for placements"), worked out in full before anything is
 * applied: {@code plan(a, k)} takes min(k, what is left of a) from the pool, and any remainder r is
 * synthesised, {@code plan(p, r × parent_cost)} for each synthesis parent p in ascending id order,
 * depth first, at most {@value ResearchLimits#MAX_SYNTHESIS_DEPTH} levels. All or nothing.
 *
 * <p>Synthesis parents come from the player's current view: a compound's parents; none for an
 * exposed primal; a hidden primal's taught parents before its reveal, and after it the two exposed
 * primals with the most left at that point in the plan (ties by ascending id).
 *
 * @param debits what to take from the pool, by aspect; empty when short
 * @param shortLeaf the first aspect in plan order that couldn't be met, or null when the plan holds
 */
public record DebitPlan(SortedMap<String, Integer> debits, @Nullable String shortLeaf) {
    /** Copies the debits. */
    public DebitPlan {
        debits = new TreeMap<>(debits);
    }

    /** Whether the plan can be met. */
    public boolean ok() {
        return shortLeaf == null;
    }

    /**
     * Plans {@code needs} (aspect to amount, planned in ascending id order) against {@code pool}.
     *
     * @param graph the aspect graph, for parents
     * @param view the player's current view, for a hidden primal's reveal
     * @param parentCost {@code combine.parent_cost}: of each parent per unit synthesised
     */
    public static DebitPlan of(
            Map<String, Integer> needs,
            Map<String, Integer> pool,
            AspectGraph graph,
            AspectGraph.LinkView view,
            int parentCost) {
        Planner planner = new Planner(pool, graph, view, parentCost);
        for (Map.Entry<String, Integer> need : new TreeMap<>(needs).entrySet()) {
            if (need.getValue() > 0 && !planner.plan(need.getKey(), need.getValue(), 0)) {
                return new DebitPlan(new TreeMap<>(), planner.shortLeaf);
            }
        }
        return new DebitPlan(planner.taken, null);
    }

    private static final class Planner {
        final Map<String, Integer> pool;
        final AspectGraph graph;
        final AspectGraph.LinkView view;
        final int parentCost;
        final SortedMap<String, Integer> taken = new TreeMap<>();
        @Nullable String shortLeaf;

        Planner(
                Map<String, Integer> pool,
                AspectGraph graph,
                AspectGraph.LinkView view,
                int parentCost) {
            this.pool = new HashMap<>(pool);
            this.graph = graph;
            this.view = view;
            this.parentCost = parentCost;
        }

        int left(String aspect) {
            return pool.getOrDefault(aspect, 0) - taken.getOrDefault(aspect, 0);
        }

        boolean plan(String aspect, long k, int depth) {
            long take = Math.min(k, Math.max(0, left(aspect)));
            if (take > 0) {
                taken.merge(aspect, (int) take, Integer::sum);
            }
            long rest = k - take;
            if (rest == 0) {
                return true;
            }
            List<String> parents = parents(aspect);
            if (parents.isEmpty() || depth >= ResearchLimits.MAX_SYNTHESIS_DEPTH) {
                shortLeaf = aspect;
                return false;
            }
            for (String parent : parents) {
                if (!plan(parent, rest * parentCost, depth + 1)) {
                    return false;
                }
            }
            return true;
        }

        List<String> parents(String aspect) {
            if (!view.revealed(aspect)) {
                return graph.parents(aspect);
            }
            return graph.exposedPrimals().stream()
                    .sorted(
                            Comparator.comparingInt((String p) -> -left(p))
                                    .thenComparing(Comparator.naturalOrder()))
                    .limit(2)
                    .sorted()
                    .toList();
        }
    }
}
