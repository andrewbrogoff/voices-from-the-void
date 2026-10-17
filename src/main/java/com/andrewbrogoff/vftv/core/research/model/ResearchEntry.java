package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.Research;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * One {@code vftv:research} entry (research spec, "Registry format"). Its codec checks each field's
 * own range; {@link EntryRules} checks the rules across fields and entries.
 *
 * @param stage 1-4
 * @param category a {@code vftv:research_category} id
 * @param x the canvas column, -16..16
 * @param y the canvas row, -16..16
 * @param icon the item or sprite drawn for it
 * @param kind how it is learned
 * @param parents entries that must be learned, drawn as lines
 * @param hiddenParents entries that must be learned, never drawn
 * @param anchors a note's anchors, in data order; empty elsewhere
 * @param skipCost a note's authored skip price, aspect to amount, sorted by id
 * @param triggers a find's triggers, any one of which suffices
 * @param secret whether it is HIDDEN until revealed
 * @param reveal a secret's reveal triggers
 * @param hintedBy the entry whose prose hints at this one, or null
 * @param opensStage the Stage a find opens, or 0
 * @param warp the warp applied on commit
 * @param rewards points paid once on first learning, aspect to amount, sorted by id
 * @param pages the page descriptors
 */
public record ResearchEntry(
        int stage,
        String category,
        int x,
        int y,
        Icon icon,
        EntryKind kind,
        List<String> parents,
        List<String> hiddenParents,
        List<String> anchors,
        Map<String, Integer> skipCost,
        List<Trigger> triggers,
        boolean secret,
        List<Trigger> reveal,
        @Nullable String hintedBy,
        int opensStage,
        Warp warp,
        Map<String, Integer> rewards,
        List<Page> pages)
        implements Research {
    /** Copies every collection, sorting the maps by id. */
    public ResearchEntry {
        parents = List.copyOf(parents);
        hiddenParents = List.copyOf(hiddenParents);
        anchors = List.copyOf(anchors);
        skipCost = sorted(skipCost);
        triggers = List.copyOf(triggers);
        reveal = List.copyOf(reveal);
        rewards = sorted(rewards);
        pages = List.copyOf(pages);
    }

    /** Whether it is its Stage's opener. */
    public boolean opener() {
        return opensStage > 0;
    }

    /** {@code parents} then {@code hidden_parents}: every entry that must be learned first. */
    public List<String> allParents() {
        return Stream.concat(parents.stream(), hiddenParents.stream()).toList();
    }

    private static Map<String, Integer> sorted(Map<String, Integer> map) {
        return Collections.unmodifiableMap(new TreeMap<>(map));
    }

    /**
     * An icon: exactly one of an item or a GUI sprite.
     *
     * @param sprite true for {@code {"sprite": id}}, false for {@code {"item": id}}
     * @param id the item or sprite id
     */
    public record Icon(boolean sprite, String id) {}

    /**
     * Warp applied on commit, each 0-100.
     *
     * @param temporary temporary warp
     * @param lasting lasting warp
     * @param permanent permanent warp
     */
    public record Warp(int temporary, int lasting, int permanent) {
        /** No warp. */
        public static final Warp NONE = new Warp(0, 0, 0);

        /** Whether any kind is above 0. */
        public boolean any() {
            return temporary > 0 || lasting > 0 || permanent > 0;
        }
    }
}
