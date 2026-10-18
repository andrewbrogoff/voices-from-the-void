package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.api.vis.VisLimits;

/**
 * Reconciliation's decision table and the cell quotas (wands spec, "Identity and reconciliation"):
 * what to do with one node block entity found in a loaded chunk, given the registry's entry for its
 * id.
 */
public final class Reconcile {
    private Reconcile() {}

    /** How a node was born. */
    public enum Origin {
        /** Natural spread. */
        NATURAL,
        /** A structure template. */
        STRUCTURE,
        /** The starter. */
        STARTER
    }

    /** The registry's entry for the block's id, relative to the block. */
    public enum Entry {
        /** No entry. */
        NONE,
        /** {@code PLACED} at exactly this position. */
        PLACED_HERE,
        /** {@code PLACED} elsewhere. */
        PLACED_ELSEWHERE,
        /** {@code CARRIED} by a Bell Jar. */
        CARRIED,
        /** {@code REMOVED}. */
        REMOVED
    }

    /** What to do. */
    public enum Action {
        /** Nothing. */
        NOTHING,
        /** Mint a new id here. */
        MINT,
        /** Record the entry {@code PLACED} here (a carried id whose block wins). */
        ADOPT,
        /** Register the unknown id {@code PLACED} here and advance {@code next_id} past it. */
        RECOVER,
        /** Remove the block (a duplicate, a tombstone, or a full cell). */
        REMOVE
    }

    /** The nodes a cell holds now, by origin. */
    public record Cell(int total, int natural, int structure) {}

    /**
     * Whether a cell admits one more node of an origin: fewer than 8 in all, and for a natural node
     * no other natural one, for a structure node fewer than 2 structure ones.
     */
    public static boolean admits(Origin origin, Cell cell) {
        if (cell.total() >= VisLimits.MAX_NODES_PER_CELL) {
            return false;
        }
        return switch (origin) {
            case NATURAL -> cell.natural() < VisLimits.MAX_NATURAL_NODES_PER_CELL;
            case STRUCTURE -> cell.structure() < VisLimits.MAX_STRUCTURE_NODES_PER_CELL;
            case STARTER -> true;
        };
    }

    /** Whether a cell has room for any node: fewer than 8. */
    public static boolean hasRoom(Cell cell) {
        return cell.total() < VisLimits.MAX_NODES_PER_CELL;
    }

    /**
     * The table's row for one block.
     *
     * @param idSet whether the block entity's id is set (not 0)
     * @param origin the block entity's origin
     * @param entry the registry's entry for its id ({@link Entry#NONE} for an unset id)
     * @param cell the cell's nodes, not counting this block
     */
    public static Action decide(boolean idSet, Origin origin, Entry entry, Cell cell) {
        if (!idSet) {
            return admits(origin, cell) ? Action.MINT : Action.REMOVE;
        }
        return switch (entry) {
            case PLACED_HERE -> Action.NOTHING;
            case PLACED_ELSEWHERE, REMOVED -> Action.REMOVE;
            case CARRIED -> hasRoom(cell) ? Action.ADOPT : Action.REMOVE;
            case NONE -> hasRoom(cell) ? Action.RECOVER : Action.REMOVE;
        };
    }
}
