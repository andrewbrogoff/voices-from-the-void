package com.andrewbrogoff.vftv.core.research.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The aspect set as the notebook teaches it (research spec, "Links"): in the taught view every
 * compound links to its two parents, and a hidden primal to its taught parents. After a player's
 * reveal ({@link #view}), each hidden primal whose {@code revealed_by} entry they have learned also
 * links to every exposed primal. Neighbours are kept in ascending id order, the order every scan
 * uses.
 */
public final class AspectGraph {
    private final Map<String, List<String>> taught;
    private final Map<String, List<String>> parents;
    private final Map<String, String> revealedBy;
    private final List<String> exposed;

    /**
     * A graph over {@code taughtParents} with no hidden primal: every aspect id, mapped to its
     * parents in the taught view (none for an exposed primal).
     */
    public AspectGraph(Map<String, List<String>> taughtParents) {
        this(taughtParents, Map.of());
    }

    /**
     * A graph over {@code taughtParents}, where each hidden primal in {@code revealedBy} maps to
     * the research entry that reveals it. An exposed primal is an aspect with no parents that isn't
     * hidden.
     */
    public AspectGraph(Map<String, List<String>> taughtParents, Map<String, String> revealedBy) {
        this.revealedBy = Collections.unmodifiableMap(new TreeMap<>(revealedBy));
        Map<String, List<String>> sortedParents = new HashMap<>();
        taughtParents.forEach((a, ps) -> sortedParents.put(a, ps.stream().sorted().toList()));
        this.parents = Collections.unmodifiableMap(sortedParents);
        this.exposed =
                taughtParents.entrySet().stream()
                        .filter(e -> e.getValue().isEmpty() && !revealedBy.containsKey(e.getKey()))
                        .map(Map.Entry::getKey)
                        .sorted()
                        .toList();
        Map<String, Set<String>> links = new TreeMap<>();
        taughtParents.keySet().forEach(a -> links.put(a, new TreeSet<>()));
        taughtParents.forEach(
                (aspect, parents) -> {
                    Set<String> own = links.computeIfAbsent(aspect, a -> new TreeSet<>());
                    for (String parent : parents) {
                        Set<String> theirs = links.get(parent);
                        if (!parent.equals(aspect) && theirs != null) {
                            own.add(parent);
                            theirs.add(aspect);
                        }
                    }
                });
        Map<String, List<String>> frozen = new HashMap<>();
        links.forEach((aspect, set) -> frozen.put(aspect, List.copyOf(set)));
        this.taught = Collections.unmodifiableMap(frozen);
    }

    /** Whether {@code aspect} is in the set. */
    public boolean contains(String aspect) {
        return taught.containsKey(aspect);
    }

    /** The aspects {@code aspect} links to in the taught view, ascending; empty if unknown. */
    public List<String> taughtLinks(String aspect) {
        return taught.getOrDefault(aspect, List.of());
    }

    /**
     * An aspect's parents in the taught view, ascending: a compound's two parents, a hidden
     * primal's taught parents, none for an exposed primal or an unknown id.
     */
    public List<String> parents(String aspect) {
        return parents.getOrDefault(aspect, List.of());
    }

    /** Each hidden primal, mapped to the research entry that reveals it. */
    public Map<String, String> revealedBy() {
        return revealedBy;
    }

    /** The exposed primals, ascending: no parents, not hidden. */
    public List<String> exposedPrimals() {
        return exposed;
    }

    /**
     * A player's view of the links (research spec, "Links"): the taught links, plus, for each
     * hidden primal whose {@code revealed_by} entry {@code learned} holds, a link to every exposed
     * primal. Links only grow with learning, so a note begun before the reveal stays valid.
     */
    public LinkView view(Predicate<String> learned) {
        Set<String> revealed = new TreeSet<>();
        revealedBy.forEach(
                (aspect, entry) -> {
                    if (learned.test(entry)) {
                        revealed.add(aspect);
                    }
                });
        return new LinkView(revealed);
    }

    /** One player's links: taught, or true for each revealed hidden primal. */
    public final class LinkView {
        private final Set<String> revealed;

        private LinkView(Set<String> revealed) {
            this.revealed = Collections.unmodifiableSet(revealed);
        }

        /** Whether {@code aspect} is a hidden primal this view has revealed. */
        public boolean revealed(String aspect) {
            return revealed.contains(aspect);
        }

        /** The aspects {@code aspect} links to in this view, ascending; empty if unknown. */
        public List<String> links(String aspect) {
            List<String> base = taughtLinks(aspect);
            Collection<String> extra =
                    revealed.contains(aspect)
                            ? exposed
                            : exposed.contains(aspect) ? revealed : List.of();
            if (extra.isEmpty()) {
                return base;
            }
            Set<String> all = new TreeSet<>(base);
            all.addAll(extra);
            all.remove(aspect);
            return List.copyOf(all);
        }

        /** Whether two aspects link in this view: one is a parent of the other, never itself. */
        public boolean linked(String a, String b) {
            return !a.equals(b) && links(a).contains(b);
        }

        /**
         * The shortest path between each consecutive pair of {@code anchors} in the player's
         * known-link graph: the {@code known} aspects only, linked by this view (research spec,
         * "Generating a note", step 1). Returns null when an anchor is unknown or a pair doesn't
         * connect.
         */
        public @Nullable List<List<String>> segments(List<String> anchors, Set<String> known) {
            if (!known.containsAll(anchors)) {
                return null;
            }
            return AspectGraph.segments(
                    anchors, a -> links(a).stream().filter(known::contains).toList());
        }
    }

    /**
     * The unpadded chain over {@code anchors} in the taught view with every aspect known (research
     * spec, "Generating a note", step 1): each consecutive pair joined by a BFS from the first,
     * neighbours in ascending id order, a predecessor set on first discovery, the path read back
     * from the second. Returns null when a pair doesn't connect.
     */
    public @Nullable List<String> chain(List<String> anchors) {
        if (!anchors.stream().allMatch(this::contains)) {
            return null;
        }
        List<List<String>> segments = segments(anchors, this::taughtLinks);
        return segments == null ? null : join(anchors, segments);
    }

    /**
     * Joins consecutive segments, sharing the anchors: the unpadded chain. With no segment it is
     * the first anchor alone, or empty.
     */
    public static List<String> join(List<String> anchors, List<List<String>> segments) {
        if (anchors.isEmpty()) {
            return List.of();
        }
        List<String> chain = new ArrayList<>(List.of(anchors.getFirst()));
        segments.forEach(path -> chain.addAll(path.subList(1, path.size())));
        return List.copyOf(chain);
    }

    /**
     * Each consecutive pair of {@code anchors} joined by a BFS from the first over {@code links}
     * (neighbours in the order given, a predecessor set on first discovery, the path read back from
     * the second), or null when a pair doesn't connect.
     */
    static @Nullable List<List<String>> segments(
            List<String> anchors, Function<String, List<String>> links) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 1; i < anchors.size(); i++) {
            List<String> path = path(anchors.get(i - 1), anchors.get(i), links);
            if (path == null) {
                return null;
            }
            out.add(path);
        }
        return List.copyOf(out);
    }

    private static @Nullable List<String> path(
            String from, String to, Function<String, List<String>> links) {
        Map<String, String> previous = new HashMap<>();
        previous.put(from, from);
        Deque<String> queue = new ArrayDeque<>(List.of(from));
        while (!queue.isEmpty() && !previous.containsKey(to)) {
            String at = queue.removeFirst();
            for (String next : links.apply(at)) {
                if (previous.putIfAbsent(next, at) == null) {
                    queue.addLast(next);
                }
            }
        }
        if (!previous.containsKey(to)) {
            return null;
        }
        List<String> path = new ArrayList<>();
        for (String at = to; !at.equals(from); at = Objects.requireNonNull(previous.get(at))) {
            path.add(at);
        }
        path.add(from);
        return List.copyOf(path.reversed());
    }
}
