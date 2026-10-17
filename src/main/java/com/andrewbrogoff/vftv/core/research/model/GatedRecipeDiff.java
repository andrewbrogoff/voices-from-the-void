package com.andrewbrogoff.vftv.core.research.model;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The JEI reconcile for one gated recipe category (compatibility spec, "The diff"): the desired
 * state from whatever is present now, so every arrival order of runtime, recipes and research
 * converges. Identity is the recipe object, never its id, so a same-id replacement is hidden for
 * good while the new object is added.
 *
 * <p>Applying it: add {@link #add}, hide {@link #hideForGood} and {@link #hide}, then unhide {@link
 * #unhide}. Afterwards the added set is exactly the snapshot.
 *
 * @param add snapshot objects not yet added
 * @param hideForGood added objects no longer in the snapshot
 * @param unhide snapshot objects the gate passes
 * @param hide snapshot objects the gate refuses
 * @param <K> the recipe object type
 */
public record GatedRecipeDiff<K>(List<K> add, List<K> hideForGood, List<K> unhide, List<K> hide) {
    /** Copies every list. */
    public GatedRecipeDiff {
        add = List.copyOf(add);
        hideForGood = List.copyOf(hideForGood);
        unhide = List.copyOf(unhide);
        hide = List.copyOf(hide);
    }

    /**
     * The diff.
     *
     * @param snapshot the current recipe snapshot, ordered by recipe id
     * @param added the objects added to this runtime so far
     * @param gate whether a recipe is shown (its research learned; always true for Striking)
     */
    public static <K> GatedRecipeDiff<K> of(
            List<K> snapshot, Collection<K> added, Predicate<? super K> gate) {
        Set<K> current = identitySet(snapshot);
        Set<K> present = identitySet(added);
        return new GatedRecipeDiff<>(
                snapshot.stream().filter(k -> !present.contains(k)).toList(),
                added.stream().filter(k -> !current.contains(k)).toList(),
                snapshot.stream().filter(gate).toList(),
                snapshot.stream().filter(gate.negate()).toList());
    }

    private static <K> Set<K> identitySet(Collection<K> items) {
        Set<K> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(items);
        return set;
    }
}
