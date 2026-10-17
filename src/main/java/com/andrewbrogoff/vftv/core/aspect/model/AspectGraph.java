package com.andrewbrogoff.vftv.core.aspect.model;

import com.andrewbrogoff.vftv.api.aspect.AspectLimits;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The aspect graph and its validator (aspects spec, "Graph rules"), run on the true view (a hidden
 * primal is a primal) and the taught view (it is a compound of its taught parents) separately.
 *
 * <p>A fatal problem refuses the world. A soft one (depth, the tier-1 minimum, fan-out) is a test
 * failure for the shipped table and a warning for a datapack's.
 */
public final class AspectGraph {
    private AspectGraph() {}

    /**
     * One aspect's place in the graph.
     *
     * @param parents its true parents: none, or two
     * @param taughtParents for a hidden primal, the two parents the notebook teaches; else empty
     */
    public record Node(List<String> parents, List<String> taughtParents) {
        /** Copies the lists. */
        public Node {
            parents = List.copyOf(parents);
            taughtParents = List.copyOf(taughtParents);
        }

        /** A primal or compound with no hidden-primal data. */
        public static Node of(String... parents) {
            return new Node(List.of(parents), List.of());
        }

        /** Whether this is a hidden primal. */
        public boolean hidden() {
            return !taughtParents.isEmpty();
        }

        /** What discovery needs known first: its parents, or a hidden primal's taught parents. */
        public List<String> prerequisites() {
            return hidden() ? taughtParents : parents;
        }

        private List<String> parentsIn(boolean taught) {
            return taught && hidden() ? taughtParents : parents;
        }
    }

    /**
     * One rule broken.
     *
     * @param fatal whether the world refuses to load
     * @param message what is wrong, naming the aspect and the view
     */
    public record Problem(boolean fatal, String message) {}

    /**
     * Checks every rule on both views.
     *
     * @param nodes every aspect by id
     * @param required the ids that must be present (every built-in {@code api} key)
     * @return the problems, fatal ones first; empty when the graph is valid
     */
    public static List<Problem> validate(Map<String, Node> nodes, Collection<String> required) {
        List<Problem> fatal = new ArrayList<>();
        Map<String, Node> sorted = new TreeMap<>(nodes);
        for (String id : new TreeSet<>(required)) {
            if (!sorted.containsKey(id)) {
                fatal.add(new Problem(true, "the built-in aspect " + id + " is missing"));
            }
        }
        sorted.forEach((id, node) -> checkShape(id, node, sorted, fatal));
        if (!fatal.isEmpty()) {
            return fatal;
        }
        List<Problem> soft = new ArrayList<>();
        for (boolean taught : new boolean[] {false, true}) {
            String view = taught ? "taught view" : "true view";
            String cycle = cycle(sorted, taught);
            if (cycle != null) {
                fatal.add(new Problem(true, "a cycle in the " + view + ": " + cycle));
                continue;
            }
            Map<String, String> pairs = new HashMap<>();
            sorted.forEach(
                    (id, node) -> {
                        List<String> p = node.parentsIn(taught);
                        if (p.size() == 2) {
                            String pair =
                                    p.get(0).compareTo(p.get(1)) < 0
                                            ? p.get(0) + "+" + p.get(1)
                                            : p.get(1) + "+" + p.get(0);
                            String other = pairs.putIfAbsent(pair, id);
                            if (other != null) {
                                fatal.add(
                                        new Problem(
                                                true,
                                                other
                                                        + " and "
                                                        + id
                                                        + " share the parents "
                                                        + pair
                                                        + " in the "
                                                        + view));
                            }
                        }
                    });
            checkSoft(sorted, taught, view, soft);
        }
        List<Problem> all = new ArrayList<>(fatal);
        all.addAll(soft);
        return all;
    }

    /**
     * Each aspect's tier in one view: 0 for a primal, else one more than its deepest parent. Only
     * for a graph that {@link #validate} found free of fatal problems.
     */
    public static Map<String, Integer> tiers(Map<String, Node> nodes, boolean taught) {
        Map<String, Integer> tiers = new HashMap<>();
        nodes.keySet().forEach(id -> tier(id, nodes, taught, tiers));
        return tiers;
    }

    private static int tier(
            String id, Map<String, Node> nodes, boolean taught, Map<String, Integer> tiers) {
        Integer known = tiers.get(id);
        if (known != null) {
            return known;
        }
        Node node = nodes.get(id);
        int t = 0;
        if (node != null) {
            for (String parent : node.parentsIn(taught)) {
                t = Math.max(t, tier(parent, nodes, taught, tiers) + 1);
            }
        }
        tiers.put(id, t);
        return t;
    }

    private static void checkShape(
            String id, Node node, Map<String, Node> nodes, List<Problem> problems) {
        if (node.hidden()) {
            if (!node.parents().isEmpty()) {
                problems.add(new Problem(true, id + " is a hidden primal with parents"));
            }
            checkPair(id, "taught parents", node.taughtParents(), nodes, problems);
        } else if (!node.parents().isEmpty()) {
            checkPair(id, "parents", node.parents(), nodes, problems);
        }
    }

    private static void checkPair(
            String id, String what, List<String> pair, Map<String, Node> nodes, List<Problem> out) {
        if (pair.size() != 2) {
            out.add(new Problem(true, id + " has " + pair.size() + " " + what + ", not 0 or 2"));
            return;
        }
        if (pair.get(0).equals(pair.get(1))) {
            out.add(new Problem(true, id + " has the same aspect twice in its " + what));
        }
        for (String parent : pair) {
            if (!nodes.containsKey(parent)) {
                out.add(new Problem(true, id + " names the unknown aspect " + parent));
            } else if (parent.equals(id)) {
                out.add(new Problem(true, id + " is its own parent"));
            }
        }
    }

    /** The first cycle found, as {@code a -> b -> a}, or null. */
    private static @Nullable String cycle(Map<String, Node> nodes, boolean taught) {
        Map<String, Integer> state = new HashMap<>(); // 1 = on the path, 2 = done
        for (String id : nodes.keySet()) {
            List<String> path = new ArrayList<>();
            String found = visit(id, nodes, taught, state, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable String visit(
            String id,
            Map<String, Node> nodes,
            boolean taught,
            Map<String, Integer> state,
            List<String> path) {
        Integer s = state.get(id);
        if (s != null && s == 2) {
            return null;
        }
        if (s != null) {
            List<String> loop = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
            loop.add(id);
            return String.join(" -> ", loop);
        }
        state.put(id, 1);
        path.add(id);
        for (String parent : Objects.requireNonNull(nodes.get(id)).parentsIn(taught)) {
            String found = visit(parent, nodes, taught, state, path);
            if (found != null) {
                return found;
            }
        }
        path.removeLast();
        state.put(id, 2);
        return null;
    }

    private static void checkSoft(
            Map<String, Node> nodes, boolean taught, String view, List<Problem> out) {
        Map<String, Integer> tiers = tiers(nodes, taught);
        Map<String, Integer> children = new HashMap<>();
        Map<String, Integer> tierOneChildren = new HashMap<>();
        nodes.forEach(
                (id, node) -> {
                    for (String parent : node.parentsIn(taught)) {
                        children.merge(parent, 1, Integer::sum);
                        if (tiers.getOrDefault(id, 0) == 1) {
                            tierOneChildren.merge(parent, 1, Integer::sum);
                        }
                    }
                });
        nodes.forEach(
                (id, node) -> {
                    int tier = tiers.getOrDefault(id, 0);
                    if (tier > AspectLimits.MAX_DEPTH) {
                        out.add(
                                new Problem(
                                        false,
                                        id
                                                + " is at depth "
                                                + tier
                                                + " in the "
                                                + view
                                                + ", above "
                                                + AspectLimits.MAX_DEPTH));
                    }
                    boolean primal = node.parentsIn(taught).isEmpty();
                    int kids = children.getOrDefault(id, 0);
                    if (!primal && kids > AspectLimits.MAX_FAN_OUT) {
                        out.add(
                                new Problem(
                                        false,
                                        id
                                                + " is a parent of "
                                                + kids
                                                + " aspects in the "
                                                + view
                                                + ", above "
                                                + AspectLimits.MAX_FAN_OUT));
                    }
                    int tierOne = tierOneChildren.getOrDefault(id, 0);
                    if (primal && !node.hidden() && tierOne < AspectLimits.MIN_TIER_ONE_CHILDREN) {
                        out.add(
                                new Problem(
                                        false,
                                        id
                                                + " is a parent of "
                                                + tierOne
                                                + " tier-1 aspects in the "
                                                + view
                                                + ", below "
                                                + AspectLimits.MIN_TIER_ONE_CHILDREN));
                    }
                });
    }
}
