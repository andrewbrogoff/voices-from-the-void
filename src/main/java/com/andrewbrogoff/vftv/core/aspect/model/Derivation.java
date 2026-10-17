package com.andrewbrogoff.vftv.core.aspect.model;

import com.andrewbrogoff.vftv.api.aspect.AspectLimits;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The recipe derivation of aspect values (aspects spec, "Vanilla values"; compatibility, "The
 * derivation export", steps 3 to 5): one algorithm for datagen's vanilla table, the load-time
 * {@code DerivedAspects} table and the operator's export. Items and aspects are plain ids; a value
 * is a map from aspect id to a positive amount, in ascending aspect id.
 *
 * <ol>
 *   <li>A recipe with an alternative whose remainder has no authored or default value is rejected
 *       before the graph is built ({@code unvalued remainder}).
 *   <li>Candidates are the results of the accepted recipes that are not resolved and that the
 *       caller's predicate admits.
 *   <li>Every candidate in a non-trivial strongly connected component of the item graph (an edge
 *       from each alternative to its recipe's result) is excluded: no value lies on a conversion
 *       cycle.
 *   <li>The rest are evaluated once each in topological order, ties by ascending id, with rules 1
 *       to 7. A candidate with no usable recipe is unresolved, naming its first blocking item.
 * </ol>
 */
public final class Derivation {
    /** The most accepted recipes one derivation takes (compatibility, "Bounds"). */
    public static final int MAX_RECIPES = 100_000;

    /** The most expanded alternative edges one derivation takes. */
    public static final long MAX_EDGES = 2_000_000L;

    /** At most this many aspects per value (rule 6). */
    public static final int MAX_ASPECTS = AspectLimits.MAX_ASPECTS_PER_ENTRY;

    private Derivation() {}

    /**
     * An alternative's crafting remainder: the item left in the grid, and how many.
     *
     * @param item the remainder's item id
     * @param count how many are left, at least 1
     */
    public record Remainder(String item, int count) {}

    /**
     * One alternative of an ingredient.
     *
     * @param item the alternative's item id
     * @param remainder what crafting leaves of it, or null
     */
    public record Alternative(String item, @Nullable Remainder remainder) {}

    /**
     * One accepted recipe, as the adapter hands it over.
     *
     * @param id the recipe id, for the tie-break of rule 7
     * @param ingredients each ingredient's alternatives, each in ascending item id; never empty
     * @param result the result's item id
     * @param count the result count, at least 1
     * @param smelted whether the smelting bonus applies (rule 5)
     */
    public record Recipe(
            String id,
            List<List<Alternative>> ingredients,
            String result,
            int count,
            boolean smelted) {
        /** Copies the lists. */
        public Recipe {
            ingredients = ingredients.stream().map(List::copyOf).toList();
        }
    }

    /**
     * The two {@code derivation.*} tuning values.
     *
     * @param factor {@code derivation.factor}
     * @param smeltBonus {@code derivation.smelt_bonus}
     */
    public record Tuning(double factor, SortedMap<String, Integer> smeltBonus) {
        /** Copies the bonus. */
        public Tuning {
            smeltBonus = Collections.unmodifiableSortedMap(new TreeMap<>(smeltBonus));
        }
    }

    /** Why a candidate is blocked. */
    public enum Reason {
        /** The blocking item lies on a conversion cycle. */
        EXCLUDED,
        /** The blocking item is itself an unresolved candidate. */
        UNRESOLVED,
        /** The blocking item has no value at all, or derived an empty one. */
        NO_VALUE;

        /** The report's word. */
        public String word() {
            return switch (this) {
                case EXCLUDED -> "excluded";
                case UNRESOLVED -> "unresolved";
                case NO_VALUE -> "no value";
            };
        }
    }

    /**
     * An unresolved candidate's first blocking item.
     *
     * @param item the blocking item
     * @param reason why it blocks
     */
    public record Blocked(String item, Reason reason) {}

    /**
     * What a derivation found.
     *
     * @param values every emitted value, by ascending item id; never empty values
     * @param excluded every excluded candidate, with its component's members in ascending id
     * @param unresolved every unresolved candidate, with its first blocking item
     * @param empty the candidates that derived an empty value, which is not emitted
     * @param candidates how many candidates there were
     * @param unvaluedRemainders how many recipes were rejected as {@code unvalued remainder}
     */
    public record Result(
            SortedMap<String, SortedMap<String, Integer>> values,
            SortedMap<String, List<String>> excluded,
            SortedMap<String, Blocked> unresolved,
            SortedSet<String> empty,
            int candidates,
            int unvaluedRemainders) {}

    /** Whether recipes and edges exceed the bounds. */
    public static boolean overLimit(long recipes, long edges) {
        return recipes > MAX_RECIPES || edges > MAX_EDGES;
    }

    /** The expanded alternative edges of the recipes: every alternative of every ingredient. */
    public static long edges(List<Recipe> recipes) {
        long edges = 0;
        for (Recipe r : recipes) {
            for (List<Alternative> ingredient : r.ingredients()) {
                edges += ingredient.size();
            }
        }
        return edges;
    }

    /**
     * Derives values.
     *
     * @param resolved every authored or default value, by item id; an empty value is the authored
     *     {@code {}}
     * @param admit which unresolved recipe results may be candidates (the export admits every
     *     namespace but {@code minecraft} and {@code vftv}; datagen admits those two)
     * @param recipes the accepted recipes
     * @param tuning the derivation tuning
     */
    public static Result derive(
            Map<String, ? extends SortedMap<String, Integer>> resolved,
            Predicate<String> admit,
            List<Recipe> recipes,
            Tuning tuning) {
        List<Recipe> accepted = new ArrayList<>();
        int unvalued = 0;
        for (Recipe r : recipes) {
            if (remaindersValued(r, resolved)) {
                accepted.add(r);
            } else {
                unvalued++;
            }
        }
        accepted.sort(Comparator.comparing(Recipe::id));

        Set<String> candidates = new TreeSet<>();
        Map<String, List<Recipe>> byResult = new HashMap<>();
        for (Recipe r : accepted) {
            byResult.computeIfAbsent(r.result(), k -> new ArrayList<>()).add(r);
            if (!resolved.containsKey(r.result()) && admit.test(r.result())) {
                candidates.add(r.result());
            }
        }

        Graph graph = Graph.of(accepted);
        SortedMap<String, List<String>> excluded = new TreeMap<>();
        for (List<String> component : graph.cyclicComponents()) {
            for (String item : component) {
                if (candidates.contains(item)) {
                    excluded.put(item, component);
                }
            }
        }

        // Kahn over the remaining candidates, ties by ascending id.
        Map<String, Set<String>> successors = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String c : candidates) {
            if (!excluded.containsKey(c)) {
                indegree.put(c, 0);
            }
        }
        for (Recipe r : accepted) {
            if (!indegree.containsKey(r.result())) {
                continue;
            }
            for (List<Alternative> ingredient : r.ingredients()) {
                for (Alternative a : ingredient) {
                    if (indegree.containsKey(a.item())
                            && successors
                                    .computeIfAbsent(a.item(), k -> new HashSet<>())
                                    .add(r.result())) {
                        indegree.merge(r.result(), 1, Integer::sum);
                    }
                }
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach(
                (item, n) -> {
                    if (n == 0) {
                        ready.add(item);
                    }
                });

        SortedMap<String, SortedMap<String, Integer>> derived = new TreeMap<>();
        SortedMap<String, Blocked> unresolved = new TreeMap<>();
        SortedSet<String> empty = new TreeSet<>();
        Lookup lookup = new Lookup(resolved, derived);
        while (!ready.isEmpty()) {
            String item = ready.poll();
            SortedMap<String, Integer> best = null;
            int bestTotal = 0;
            for (Recipe r : byResult.getOrDefault(item, List.of())) {
                SortedMap<String, Integer> value = evaluate(r, lookup, tuning);
                if (value != null && (best == null || total(value) < bestTotal)) {
                    best = value;
                    bestTotal = total(value);
                }
            }
            if (best == null) {
                unresolved.put(
                        item,
                        blocking(byResult.get(item), lookup, excluded.keySet(), candidates, empty));
            } else if (best.isEmpty()) {
                empty.add(item);
            } else {
                derived.put(item, Collections.unmodifiableSortedMap(best));
            }
            for (String next : successors.getOrDefault(item, Set.of())) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        return new Result(
                Collections.unmodifiableSortedMap(derived),
                Collections.unmodifiableSortedMap(excluded),
                Collections.unmodifiableSortedMap(unresolved),
                Collections.unmodifiableSortedSet(empty),
                candidates.size(),
                unvalued);
    }

    /**
     * The non-amplification check (aspects spec, "Coverage and supply"): every recipe on a
     * conversion cycle (its result and one of its alternatives in one strongly connected component)
     * whose result, times its count, holds more aspects in all than its ingredients put in, each
     * ingredient taken at its lowest-total valued alternative net of remainders. If no craft on a
     * cycle makes more than it consumes, no round trip along it yields more than went in; a craft
     * may turn one aspect into another (a tool's Instrumentum from its metal), as long as the total
     * does not grow.
     *
     * @param values every final value, by item id
     * @param recipes the accepted recipes
     * @return each gaining recipe, described, in ascending recipe id
     */
    public static List<String> amplifying(
            Map<String, ? extends SortedMap<String, Integer>> values, List<Recipe> recipes) {
        Map<String, Integer> component = new HashMap<>();
        List<List<String>> cycles = Graph.of(recipes).cyclicComponents();
        for (int i = 0; i < cycles.size(); i++) {
            for (String item : cycles.get(i)) {
                component.put(item, i);
            }
        }
        Lookup lookup = new Lookup(values, Map.of());
        List<Recipe> sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator.comparing(Recipe::id));
        List<String> out = new ArrayList<>();
        for (Recipe r : sorted) {
            Integer c = component.get(r.result());
            SortedMap<String, Integer> result = values.get(r.result());
            if (c == null || result == null || !onCycle(r, c, component)) {
                continue;
            }
            SortedMap<String, Long> in = inputs(r, lookup);
            if (in == null) {
                continue;
            }
            long made = (long) total(result) * r.count();
            long put = in.values().stream().mapToLong(Long::longValue).sum();
            if (made > put) {
                out.add(
                        r.id()
                                + ": "
                                + r.count()
                                + " "
                                + r.result()
                                + " hold "
                                + made
                                + " aspects in all, its ingredients "
                                + put);
            }
        }
        return out;
    }

    private static boolean onCycle(Recipe r, int component, Map<String, Integer> components) {
        for (List<Alternative> ingredient : r.ingredients()) {
            for (Alternative a : ingredient) {
                if (Integer.valueOf(component).equals(components.get(a.item()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Rules 1 and 2: the summed net contributions, or null when an ingredient has no value. */
    private static @Nullable SortedMap<String, Long> inputs(Recipe recipe, Lookup lookup) {
        SortedMap<String, Long> sum = new TreeMap<>();
        for (List<Alternative> ingredient : recipe.ingredients()) {
            SortedMap<String, Long> chosen = null;
            long chosenTotal = 0;
            for (Alternative a : ingredient) {
                SortedMap<String, Integer> value = lookup.value(a.item());
                if (value == null) {
                    continue;
                }
                SortedMap<String, Long> net = net(value, a.remainder(), lookup);
                long total = net.values().stream().mapToLong(Long::longValue).sum();
                if (chosen == null || total < chosenTotal) {
                    chosen = net;
                    chosenTotal = total;
                }
            }
            if (chosen == null) {
                return null;
            }
            chosen.forEach((aspect, n) -> sum.merge(aspect, n, Long::sum));
        }
        return sum;
    }

    /**
     * Rules 1 to 6 for one recipe: each ingredient's lowest-total valued alternative, net of its
     * remainder; the sum times the factor over the count, rounded down but keeping 1 of every
     * aspect present; the smelting bonus; then the top 5. Null when an ingredient has no valued
     * alternative.
     */
    static @Nullable SortedMap<String, Integer> evaluate(
            Recipe recipe, Lookup lookup, Tuning tuning) {
        SortedMap<String, Long> sum = inputs(recipe, lookup);
        if (sum == null) {
            return null;
        }
        BigDecimal factor = BigDecimal.valueOf(tuning.factor());
        BigDecimal count = BigDecimal.valueOf(recipe.count());
        SortedMap<String, Integer> out = new TreeMap<>();
        sum.forEach(
                (aspect, n) -> {
                    if (n > 0) {
                        long scaled =
                                BigDecimal.valueOf(n)
                                        .multiply(factor)
                                        .divide(count, 0, RoundingMode.FLOOR)
                                        .longValueExact();
                        out.put(aspect, (int) Math.min(Integer.MAX_VALUE, Math.max(1, scaled)));
                    }
                });
        if (recipe.smelted()) {
            tuning.smeltBonus().forEach((aspect, n) -> out.merge(aspect, n, Derivation::add));
        }
        return top(out);
    }

    /** Rule 6: the {@value #MAX_ASPECTS} highest amounts, ties by ascending aspect id. */
    static SortedMap<String, Integer> top(SortedMap<String, Integer> value) {
        if (value.size() <= MAX_ASPECTS) {
            return value;
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(value.entrySet());
        entries.sort(
                Map.Entry.<String, Integer>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()));
        SortedMap<String, Integer> out = new TreeMap<>();
        entries.subList(0, MAX_ASPECTS).forEach(e -> out.put(e.getKey(), e.getValue()));
        return out;
    }

    /** A value's total. */
    public static int total(Map<String, Integer> value) {
        long total = value.values().stream().mapToLong(Integer::longValue).sum();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int add(int a, int b) {
        return (int) Math.min(Integer.MAX_VALUE, (long) a + b);
    }

    private static SortedMap<String, Long> net(
            SortedMap<String, Integer> value, @Nullable Remainder remainder, Lookup lookup) {
        SortedMap<String, Long> net = new TreeMap<>();
        value.forEach((aspect, n) -> net.put(aspect, n.longValue()));
        if (remainder != null) {
            SortedMap<String, Integer> left = lookup.value(remainder.item());
            if (left != null) {
                left.forEach(
                        (aspect, n) ->
                                net.computeIfPresent(
                                        aspect,
                                        (k, have) ->
                                                Math.max(0, have - (long) n * remainder.count())));
            }
        }
        return net;
    }

    private static boolean remaindersValued(
            Recipe recipe, Map<String, ? extends SortedMap<String, Integer>> resolved) {
        for (List<Alternative> ingredient : recipe.ingredients()) {
            for (Alternative a : ingredient) {
                if (a.remainder() != null && !resolved.containsKey(a.remainder().item())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Blocked blocking(
            @Nullable List<Recipe> recipes,
            Lookup lookup,
            Set<String> excluded,
            Set<String> candidates,
            Set<String> empty) {
        for (Recipe r : recipes == null ? List.<Recipe>of() : recipes) {
            for (List<Alternative> ingredient : r.ingredients()) {
                boolean valued = ingredient.stream().anyMatch(a -> lookup.value(a.item()) != null);
                if (!valued) {
                    String item = ingredient.getFirst().item();
                    Reason reason =
                            excluded.contains(item)
                                    ? Reason.EXCLUDED
                                    : candidates.contains(item) && !empty.contains(item)
                                            ? Reason.UNRESOLVED
                                            : Reason.NO_VALUE;
                    return new Blocked(item, reason);
                }
            }
        }
        throw new IllegalStateException("a candidate with no recipe");
    }

    /** The values known so far: resolved first, then derived. */
    record Lookup(
            Map<String, ? extends SortedMap<String, Integer>> resolved,
            Map<String, SortedMap<String, Integer>> derived) {
        @Nullable SortedMap<String, Integer> value(String item) {
            SortedMap<String, Integer> v = resolved.get(item);
            return v != null ? v : derived.get(item);
        }
    }

    /** The item graph of the accepted recipes, with an iterative Tarjan pass. */
    static final class Graph {
        private final String[] names;
        private final int[][] edges;

        private Graph(String[] names, int[][] edges) {
            this.names = names;
            this.edges = edges;
        }

        static Graph of(List<Recipe> recipes) {
            TreeSet<String> items = new TreeSet<>();
            for (Recipe r : recipes) {
                items.add(r.result());
                r.ingredients().forEach(i -> i.forEach(a -> items.add(a.item())));
            }
            String[] names = items.toArray(String[]::new);
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < names.length; i++) {
                index.put(names[i], i);
            }
            List<Set<Integer>> out = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                out.add(new TreeSet<>());
            }
            for (Recipe r : recipes) {
                int to = Objects.requireNonNull(index.get(r.result()));
                for (List<Alternative> ingredient : r.ingredients()) {
                    for (Alternative a : ingredient) {
                        out.get(Objects.requireNonNull(index.get(a.item()))).add(to);
                    }
                }
            }
            int[][] edges = new int[names.length][];
            for (int i = 0; i < names.length; i++) {
                edges[i] = out.get(i).stream().mapToInt(Integer::intValue).toArray();
            }
            return new Graph(names, edges);
        }

        /**
         * Every non-trivial strongly connected component (two or more items, or one with an edge to
         * itself), each in ascending id. Iterative, so a long chain cannot overflow the stack.
         */
        List<List<String>> cyclicComponents() {
            int n = names.length;
            int[] order = new int[n];
            int[] low = new int[n];
            Arrays.fill(order, -1);
            boolean[] onStack = new boolean[n];
            int[] stack = new int[n];
            int top = 0;
            int counter = 0;
            int[] edgeAt = new int[n];
            ArrayDeque<Integer> call = new ArrayDeque<>();
            List<List<String>> components = new ArrayList<>();
            for (int start = 0; start < n; start++) {
                if (order[start] >= 0) {
                    continue;
                }
                call.push(start);
                order[start] = low[start] = counter++;
                stack[top++] = start;
                onStack[start] = true;
                while (!call.isEmpty()) {
                    int v = call.peek();
                    if (edgeAt[v] < edges[v].length) {
                        int w = edges[v][edgeAt[v]++];
                        if (order[w] < 0) {
                            order[w] = low[w] = counter++;
                            stack[top++] = w;
                            onStack[w] = true;
                            call.push(w);
                        } else if (onStack[w]) {
                            low[v] = Math.min(low[v], order[w]);
                        }
                        continue;
                    }
                    call.pop();
                    if (!call.isEmpty()) {
                        int parent = call.peek();
                        low[parent] = Math.min(low[parent], low[v]);
                    }
                    if (low[v] == order[v]) {
                        List<String> component = new ArrayList<>();
                        int w;
                        do {
                            w = stack[--top];
                            onStack[w] = false;
                            component.add(names[w]);
                        } while (w != v);
                        if (component.size() > 1 || selfEdge(v)) {
                            Collections.sort(component);
                            components.add(List.copyOf(component));
                        }
                    }
                }
            }
            return components;
        }

        private boolean selfEdge(int v) {
            return Arrays.binarySearch(edges[v], v) >= 0;
        }
    }
}
