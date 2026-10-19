package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The resolution after any state change (research spec, "Triggers"), a pure function of the data
 * and the record. It runs in rounds until nothing changes; each round runs the steps in order, and
 * within a step entries go in ascending id order, each seeing what the step has done so far:
 *
 * <ol>
 *   <li><b>Stage:</b> if the opener of Stage {@code stage + 1} is latched and its parents are
 *       learned, the Stage advances and the opener is learned;
 *   <li><b>Reveals:</b> a secret with a latched reveal becomes revealed;
 *   <li><b>Free:</b> each OPEN {@code free} entry is learned;
 *   <li><b>Finds:</b> each OPEN {@code find} (its trigger has latched) is learned.
 * </ol>
 *
 * Every round learns or reveals at least one entry or the resolution stops, so it is bounded by the
 * registry size.
 */
public final class Resolution {
    private Resolution() {}

    /** One thing the resolution (or a direct change) did, in order, for the events. */
    public sealed interface Step {}

    /**
     * An entry learned.
     *
     * @param entry the entry's id
     * @param source how: {@code FOUND} for a find or opener, {@code FREE} for a free entry, or the
     *     direct change's own source
     */
    public record Learned(String entry, ResearchSource source) implements Step {}

    /**
     * The Stage moved.
     *
     * @param previous the Stage before
     * @param stage the Stage after
     * @param source how
     */
    public record Staged(int previous, int stage, ResearchSource source) implements Step {}

    /**
     * What a resolution did.
     *
     * @param record the record after it
     * @param steps what it did, in order: learnings and Stage steps
     */
    public record Outcome(ResearchRecord record, List<Step> steps) {
        /** Copies the steps. */
        public Outcome {
            steps = List.copyOf(steps);
        }
    }

    /** Resolves {@code record} against every entry, to its fixed point. */
    public static Outcome resolve(Map<String, ResearchEntry> entries, ResearchRecord record) {
        Map<String, ResearchEntry> sorted = new TreeMap<>(entries);
        Map<Integer, String> openers = new TreeMap<>();
        sorted.forEach(
                (id, e) -> {
                    if (e.opener()) {
                        openers.putIfAbsent(e.opensStage(), id);
                    }
                });
        List<Step> steps = new ArrayList<>();
        ResearchRecord r = record;
        boolean changed = true;
        while (changed) {
            changed = false;
            String opener = openers.get(r.stage() + 1);
            ResearchEntry next = opener == null ? null : sorted.get(opener);
            if (opener != null
                    && next != null
                    && r.latched().contains(opener)
                    && next.allParents().stream().allMatch(r::isLearned)) {
                int previous = r.stage();
                r = r.withStage(previous + 1);
                steps.add(new Staged(previous, previous + 1, ResearchSource.FOUND));
                if (!r.isLearned(opener)) {
                    r = r.learn(opener);
                    steps.add(new Learned(opener, ResearchSource.FOUND));
                }
                changed = true;
            }
            for (Map.Entry<String, ResearchEntry> e : sorted.entrySet()) {
                String id = e.getKey();
                if (e.getValue().secret()
                        && r.revealLatched().contains(id)
                        && !r.revealed().contains(id)) {
                    r = r.reveal(id);
                    changed = true;
                }
            }
            for (EntryKind kind : List.of(EntryKind.FREE, EntryKind.FIND)) {
                for (Map.Entry<String, ResearchEntry> e : sorted.entrySet()) {
                    String id = e.getKey();
                    if (e.getValue().kind() == kind
                            && RenderState.of(id, e.getValue(), r, entry -> false)
                                    == RenderState.OPEN) {
                        r = r.learn(id);
                        steps.add(
                                new Learned(
                                        id,
                                        kind == EntryKind.FREE
                                                ? ResearchSource.FREE
                                                : ResearchSource.FOUND));
                        changed = true;
                    }
                }
            }
        }
        return new Outcome(r, steps);
    }
}
