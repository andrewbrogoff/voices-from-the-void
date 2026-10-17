package com.andrewbrogoff.vftv.core.research.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** The pure JEI diff (compatibility spec, "Tests"). Recipes are compared by identity. */
class GatedRecipeDiffTest {
    /** A recipe object: equal ids, distinct identities. */
    record Recipe(String id, String research) {}

    private static final Recipe A = new Recipe("a", "learned");
    private static final Recipe B = new Recipe("b", "unlearned");
    private static final Recipe C = new Recipe("c", "learned");

    private static boolean learned(Recipe r) {
        return r.research().equals("learned");
    }

    /** Applies a diff to an added set, checking the invariants, and returns the new added set. */
    private static List<Recipe> apply(
            List<Recipe> snapshot, List<Recipe> added, Predicate<Recipe> gate) {
        GatedRecipeDiff<Recipe> d = GatedRecipeDiff.of(snapshot, added, gate);
        Set<Recipe> current = identity(snapshot);
        assertTrue(
                d.hideForGood().stream().noneMatch(current::contains), "hide for good is disjoint");
        assertTrue(current.containsAll(d.add()), "add is a subset of the snapshot");
        Set<Recipe> partition = identity(d.hide());
        assertTrue(
                d.unhide().stream().noneMatch(partition::contains), "hide and unhide are disjoint");
        partition.addAll(d.unhide());
        assertEquals(current, partition, "hide and unhide cover the snapshot");
        List<Recipe> next = new ArrayList<>(added);
        next.removeIf(r -> identity(d.hideForGood()).contains(r));
        next.addAll(d.add());
        assertEquals(current, identity(next), "the added set becomes the snapshot");
        return next;
    }

    private static Set<Recipe> identity(List<Recipe> items) {
        Set<Recipe> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(items);
        return set;
    }

    @Test
    void aFirstAdditionIsAddedAndGatedInTheSameReconcile() {
        GatedRecipeDiff<Recipe> d =
                GatedRecipeDiff.of(List.of(A, B), List.of(), GatedRecipeDiffTest::learned);
        assertEquals(List.of(A, B), d.add());
        assertEquals(List.of(A), d.unhide());
        assertEquals(List.of(B), d.hide());
        assertEquals(List.of(), d.hideForGood());
    }

    @Test
    void aSameIdReplacementIsHiddenForGoodWhileTheNewObjectIsAdded() {
        Recipe newA = new Recipe("a", "learned");
        GatedRecipeDiff<Recipe> d =
                GatedRecipeDiff.of(List.of(newA, B), List.of(A, B), GatedRecipeDiffTest::learned);
        assertEquals(1, d.hideForGood().size());
        assertSame(A, d.hideForGood().getFirst());
        assertEquals(1, d.add().size());
        assertSame(newA, d.add().getFirst());
        apply(List.of(newA, B), List.of(A, B), GatedRecipeDiffTest::learned);
    }

    @Test
    void aRemovedRecipeAndAnEmptySnapshot() {
        GatedRecipeDiff<Recipe> d =
                GatedRecipeDiff.of(List.of(A), List.of(A, C), GatedRecipeDiffTest::learned);
        assertEquals(List.of(C), d.hideForGood());
        GatedRecipeDiff<Recipe> empty =
                GatedRecipeDiff.of(List.of(), List.of(A), GatedRecipeDiffTest::learned);
        assertEquals(List.of(A), empty.hideForGood());
        assertEquals(List.of(), empty.unhide());
        assertEquals(List.of(), empty.hide());
    }

    @Test
    void aRevokeAndAGrantFlipOnlyTheGate() {
        GatedRecipeDiff<Recipe> revoked = GatedRecipeDiff.of(List.of(A), List.of(A), r -> false);
        assertEquals(List.of(A), revoked.hide());
        GatedRecipeDiff<Recipe> granted = GatedRecipeDiff.of(List.of(B), List.of(B), r -> true);
        assertEquals(List.of(B), granted.unhide());
        assertEquals(List.of(), granted.add());
    }

    @Test
    void everyArrivalOrderConverges() {
        // Runtime first (nothing yet), then recipes, then research; and the other orders.
        List<Recipe> added = apply(List.of(), List.of(), GatedRecipeDiffTest::learned);
        added = apply(List.of(A, B), added, r -> false);
        added = apply(List.of(A, B), added, GatedRecipeDiffTest::learned);
        List<Recipe> other = apply(List.of(A, B), List.of(), GatedRecipeDiffTest::learned);
        assertEquals(identity(added), identity(other));
        assertEquals(
                GatedRecipeDiff.of(List.of(A, B), added, GatedRecipeDiffTest::learned).unhide(),
                GatedRecipeDiff.of(List.of(A, B), other, GatedRecipeDiffTest::learned).unhide());
    }
}
