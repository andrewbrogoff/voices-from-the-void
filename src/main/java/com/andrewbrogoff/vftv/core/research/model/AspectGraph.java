package com.andrewbrogoff.vftv.core.research.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The aspect set as the notebook teaches it (research spec, "Links"): in the taught view every
 * compound links to its two parents, and a hidden primal to its taught parents. Neighbours are kept
 * in ascending id order, the order every scan uses.
 */
public final class AspectGraph {
    private final Map<String, List<String>> taught;

    /**
     * A graph over {@code taughtParents}: every aspect id, mapped to its parents in the taught view
     * (none for an exposed primal).
     */
    public AspectGraph(Map<String, List<String>> taughtParents) {
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
     * The unpadded chain over {@code anchors} in the taught view with every aspect known (research
     * spec, "Generating a note", step 1): each consecutive pair joined by a BFS from the first,
     * neighbours in ascending id order, a predecessor set on first discovery, the path read back
     * from the second. Returns null when a pair doesn't connect.
     */
    public @Nullable List<String> chain(List<String> anchors) {
        if (anchors.isEmpty()) {
            return List.of();
        }
        List<String> chain = new ArrayList<>(List.of(anchors.getFirst()));
        for (int i = 1; i < anchors.size(); i++) {
            List<String> path = path(anchors.get(i - 1), anchors.get(i));
            if (path == null) {
                return null;
            }
            chain.addAll(path.subList(1, path.size()));
        }
        return List.copyOf(chain);
    }

    private @Nullable List<String> path(String from, String to) {
        if (!contains(from) || !contains(to)) {
            return null;
        }
        Map<String, String> previous = new HashMap<>();
        previous.put(from, from);
        Deque<String> queue = new ArrayDeque<>(List.of(from));
        while (!queue.isEmpty() && !previous.containsKey(to)) {
            String at = queue.removeFirst();
            for (String next : taughtLinks(at)) {
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
        return path.reversed();
    }
}
