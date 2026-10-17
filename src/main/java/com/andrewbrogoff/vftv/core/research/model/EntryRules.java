package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The load validation of the research registries (research spec, "Registry format"), run on the
 * full registry when a world opens and by datagen. Errors refuse the world; warnings are logged.
 * The rules that need the warp tuning or the aspects' {@code revealed_by} are the full research
 * ticket's.
 */
public final class EntryRules {
    /** The entry that teaches stilling, which lasting and permanent warp must descend from. */
    public static final String BEING_HEARD = "vftv:being_heard";

    /** The category whose entries keep their own Stage. */
    public static final String CHOIR_PAGES = "vftv:choir_pages";

    /** A note grid of the largest radius: {@code 3R(R + 1) + 1} cells. */
    static final int MAX_CELLS =
            3 * ResearchLimits.MAX_GRID_RADIUS * (ResearchLimits.MAX_GRID_RADIUS + 1) + 1;

    /**
     * The most silent cells any preset may ask for (the tuning's range).
     *
     * <p>ponytail: the warning uses the range's top until the tuning file exists; read the hard
     * preset's {@code silent_cells} once it does.
     */
    static final int MAX_SILENT = 12;

    private EntryRules() {}

    /**
     * The outcome.
     *
     * @param errors problems that refuse the world, one line each, naming the entry
     * @param warnings problems that are only logged
     */
    public record Report(List<String> errors, List<String> warnings) {
        /** Copies both lists. */
        public Report {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Validates the full registries.
     *
     * @param entries every research entry by id
     * @param categories every category by id
     * @param aspects the aspect set
     * @param requiredEntries the built-in entry ids that must exist
     * @param requiredCategories the built-in category ids that must exist
     */
    public static Report validate(
            Map<String, ResearchEntry> entries,
            Map<String, CategoryEntry> categories,
            AspectGraph aspects,
            Set<String> requiredEntries,
            Set<String> requiredCategories) {
        Map<String, ResearchEntry> sorted = new TreeMap<>(entries);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, String> positions = new HashMap<>();
        Map<Integer, List<String>> openers = new TreeMap<>();
        sorted.forEach(
                (id, e) -> {
                    List<String> found = new ArrayList<>();
                    entry(id, e, sorted, categories, aspects, found, warnings);
                    String at = e.category() + " " + e.x() + "," + e.y();
                    String other = positions.putIfAbsent(at, id);
                    if (other != null) {
                        found.add("its position collides with " + other);
                    }
                    if (e.opener()) {
                        openers.computeIfAbsent(e.opensStage(), s -> new ArrayList<>()).add(id);
                    }
                    found.forEach(p -> errors.add(name(id) + ": " + p));
                });
        for (int stage = 1; stage <= 4; stage++) {
            List<String> ids = openers.getOrDefault(stage, List.of());
            if (ids.size() != 1) {
                errors.add("Stage " + stage + " has " + ids.size() + " openers " + ids);
            }
        }
        Map<String, List<String>> learnFirst = new LinkedHashMap<>();
        Map<String, List<String>> revealFirst = new LinkedHashMap<>();
        sorted.forEach(
                (id, e) -> {
                    learnFirst.put(id, e.allParents());
                    revealFirst.put(
                            id,
                            Stream.concat(e.allParents().stream(), revealPages(e).stream())
                                    .toList());
                });
        List<String> cycle = cycle(learnFirst);
        if (cycle != null) {
            errors.add(name(cycle.getFirst()) + ": parents form a cycle " + cycle);
        } else {
            List<String> revealCycle = cycle(revealFirst);
            if (revealCycle != null) {
                errors.add(
                        name(revealCycle.getFirst())
                                + ": reveal dependencies form a cycle "
                                + revealCycle);
            }
        }
        requiredEntries.stream()
                .sorted()
                .filter(id -> !entries.containsKey(id))
                .forEach(id -> errors.add("the required built-in entry " + id + " is missing"));
        requiredCategories.stream()
                .sorted()
                .filter(id -> !categories.containsKey(id))
                .forEach(id -> errors.add("the required built-in category " + id + " is missing"));
        return new Report(errors, warnings);
    }

    private static void entry(
            String id,
            ResearchEntry e,
            Map<String, ResearchEntry> entries,
            Map<String, CategoryEntry> categories,
            AspectGraph aspects,
            List<String> found,
            List<String> warnings) {
        CategoryEntry category = categories.get(e.category());
        Integer stage = category == null ? null : category.stage();
        if (category == null) {
            found.add("its category " + e.category() + " does not exist");
        } else if (stage != null && stage != e.stage()) {
            found.add("its Stage " + e.stage() + " differs from its category's " + stage);
        }
        for (String parent : e.allParents()) {
            ResearchEntry p = entries.get(parent);
            if (p == null) {
                found.add("its parent " + parent + " does not exist");
            } else if (p.stage() > e.stage()) {
                found.add("its parent " + parent + " is in a later Stage");
            } else if (e.opener() && p.stage() >= e.opensStage()) {
                found.add("an opener's parent " + parent + " is not in an earlier Stage");
            }
        }
        hint(e, entries, found);
        for (Trigger t : Stream.concat(e.triggers().stream(), e.reveal().stream()).toList()) {
            switch (t) {
                case Trigger.ChoirPage page when !entries.containsKey(page.page()) ->
                        found.add("its trigger's page " + page.page() + " does not exist");
                case Trigger.Discovered d when !aspects.contains(d.aspect()) ->
                        found.add("its trigger's aspect " + d.aspect() + " does not exist");
                default -> {}
            }
        }
        if (revealPages(e).contains(id)) {
            found.add("it reveals itself");
        }
        kind(id, e, aspects, found, warnings);
        Stream.concat(e.skipCost().keySet().stream(), e.rewards().keySet().stream())
                .filter(a -> !aspects.contains(a))
                .distinct()
                .forEach(a -> found.add("its aspect " + a + " does not exist"));
        if (e.secret() == e.reveal().isEmpty()) {
            found.add(e.secret() ? "it is secret with no reveal" : "it has a reveal but no secret");
        }
        if (e.opener() && e.stage() != e.opensStage()) {
            found.add("it opens Stage " + e.opensStage() + " but is in Stage " + e.stage());
        }
        long actions = e.pages().stream().filter(p -> p instanceof Page.Action).count();
        if (e.kind().committed() ? actions != 1 : actions != 0) {
            found.add("it has " + actions + " action pages");
        }
        if (e.warp().any() && e.stage() <= 2) {
            warnings.add(name(id) + ": warp on a Stage " + e.stage() + " entry");
        }
        if ((e.warp().lasting() > 0 || e.warp().permanent() > 0)
                && !closure(e, entries).contains(BEING_HEARD)) {
            warnings.add(name(id) + ": lasting or permanent warp without " + BEING_HEARD);
        }
    }

    private static void hint(
            ResearchEntry e, Map<String, ResearchEntry> entries, List<String> found) {
        String hintedBy = e.hintedBy();
        boolean needed = e.secret() || e.opensStage() >= 2;
        if (hintedBy == null) {
            if (needed) {
                found.add("it needs a hinted_by");
            }
            return;
        }
        ResearchEntry hint = entries.get(hintedBy);
        if (hint == null) {
            found.add("its hinted_by " + hintedBy + " does not exist");
        } else if (hint.secret()) {
            found.add("its hinted_by " + hintedBy + " is secret");
        } else if (hint.stage() > e.stage()) {
            found.add("its hinted_by " + hintedBy + " is in a later Stage");
        } else if (e.opensStage() >= 2 && hint.stage() != e.opensStage() - 1) {
            found.add("an opener's hinted_by " + hintedBy + " is not in the Stage before");
        }
    }

    private static void kind(
            String id,
            ResearchEntry e,
            AspectGraph aspects,
            List<String> found,
            List<String> warnings) {
        boolean note = e.kind() == EntryKind.NOTE;
        if (note) {
            anchors(id, e, aspects, found, warnings);
        } else {
            if (!e.anchors().isEmpty()) {
                found.add("anchors on a " + e.kind().id());
            }
            if (!e.skipCost().isEmpty()) {
                found.add("skip_cost on a " + e.kind().id());
            }
        }
        if (e.warp().any() && !e.kind().committed()) {
            found.add("warp on a " + e.kind().id());
        }
        boolean find = e.kind() == EntryKind.FIND;
        if (find == e.triggers().isEmpty()) {
            found.add(find ? "a find with no triggers" : "triggers on a " + e.kind().id());
        }
        if (e.opener() && !find) {
            found.add("opens_stage on a " + e.kind().id());
        }
    }

    private static void anchors(
            String id,
            ResearchEntry e,
            AspectGraph aspects,
            List<String> found,
            List<String> warnings) {
        List<String> anchors = e.anchors();
        if (anchors.size() < ResearchLimits.MIN_ANCHORS
                || anchors.size() > ResearchLimits.MAX_ANCHORS) {
            found.add("a note needs 2 to 5 anchors, not " + anchors.size());
            return;
        }
        if (new HashSet<>(anchors).size() != anchors.size()) {
            found.add("its anchors are not distinct");
            return;
        }
        List<String> unknown = anchors.stream().filter(a -> !aspects.contains(a)).toList();
        if (!unknown.isEmpty()) {
            found.add("its anchors " + unknown + " are not aspects");
            return;
        }
        List<String> chain = aspects.chain(anchors);
        if (chain == null) {
            found.add("its anchors don't connect in the taught view");
        } else if (chain.size() == anchors.size()) {
            found.add("its reference vector is empty: every anchor links to the next");
        } else if (chain.size() + MAX_SILENT > MAX_CELLS) {
            warnings.add(
                    name(id)
                            + ": its full-knowledge chain of "
                            + chain.size()
                            + " cells may not fit a note");
        }
    }

    /** The pages named by an entry's {@code choir_page} reveal triggers. */
    private static List<String> revealPages(ResearchEntry e) {
        return e.reveal().stream()
                .filter(t -> t instanceof Trigger.ChoirPage)
                .map(t -> ((Trigger.ChoirPage) t).page())
                .toList();
    }

    /** Every entry reachable through {@code parents} and {@code hidden_parents}. */
    static Set<String> closure(ResearchEntry e, Map<String, ResearchEntry> entries) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(e.allParents());
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            ResearchEntry parent = entries.get(id);
            if (seen.add(id) && parent != null) {
                queue.addAll(parent.allParents());
            }
        }
        return seen;
    }

    /** The first cycle found, walking ids in their map order, or null; edges to unknowns skip. */
    static @Nullable List<String> cycle(Map<String, List<String>> edges) {
        Map<String, Integer> state = new HashMap<>(); // 1 on the path, 2 finished
        for (String start : edges.keySet()) {
            List<String> path = new ArrayList<>();
            List<String> found = visit(start, edges, state, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable List<String> visit(
            String id,
            Map<String, List<String>> edges,
            Map<String, Integer> state,
            List<String> path) {
        Integer s = state.get(id);
        if (s != null) {
            return s == 1 ? List.copyOf(path.subList(path.indexOf(id), path.size())) : null;
        }
        state.put(id, 1);
        path.add(id);
        for (String next : edges.getOrDefault(id, List.of())) {
            if (edges.containsKey(next)) {
                List<String> found = visit(next, edges, state, path);
                if (found != null) {
                    return found;
                }
            }
        }
        path.removeLast();
        state.put(id, 2);
        return null;
    }

    private static String name(String id) {
        return "research entry " + id;
    }
}
