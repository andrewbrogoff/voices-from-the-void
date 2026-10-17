package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One open note, kept in the player's research record (research spec, "Snapshots and reopening"):
 * its board and the numbers that priced it, snapshotted when it was begun. Declared here for the
 * puzzle; the record checks only format bounds and the board's own invariants.
 *
 * @param entry the note entry's id
 * @param snapshot the preset and price numbers it was begun with
 * @param radius the grid radius, 1-7
 * @param anchors the anchors, 2-5
 * @param silent the silent cells, at most 12
 * @param placements the player's placements, at most one per cell
 * @param revision the record revision taken when it was created or last changed
 */
public record Note(
        String entry,
        Snapshot snapshot,
        int radius,
        List<Placed> anchors,
        List<Cell> silent,
        List<Placed> placements,
        long revision) {
    /** The most silent cells a note may hold (the tuning's range). */
    public static final int MAX_SILENT = 12;

    /** Checks the bounds and that every cell is inside the grid and used once. */
    public Note {
        anchors = List.copyOf(anchors);
        silent = List.copyOf(silent);
        placements = List.copyOf(placements);
        if (radius < 1 || radius > ResearchLimits.MAX_GRID_RADIUS) {
            throw new IllegalArgumentException("radius " + radius + " outside 1..7");
        }
        if (anchors.size() < ResearchLimits.MIN_ANCHORS
                || anchors.size() > ResearchLimits.MAX_ANCHORS) {
            throw new IllegalArgumentException("anchors " + anchors.size() + " outside 2..5");
        }
        if (silent.size() > MAX_SILENT) {
            throw new IllegalArgumentException("silent cells " + silent.size() + " over 12");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("negative revision");
        }
        Set<Cell> used = new HashSet<>();
        int r = radius;
        Stream.of(
                        anchors.stream().map(Placed::cell),
                        silent.stream(),
                        placements.stream().map(Placed::cell))
                .flatMap(s -> s)
                .forEach(
                        cell -> {
                            if (cell.distance() > r) {
                                throw new IllegalArgumentException(cell + " outside the grid");
                            }
                            if (!used.add(cell)) {
                                throw new IllegalArgumentException(cell + " used twice");
                            }
                        });
    }

    /**
     * The numbers a note snapshots when begun.
     *
     * @param minDistance {@code anchors.min_distance}, 1-6
     * @param silentCells {@code silent_cells}, 0-12
     * @param paletteHelp {@code palette_help}
     * @param placeCost {@code place_cost}, 1-4
     * @param parentCost {@code combine.parent_cost}, 1-4
     */
    public record Snapshot(
            int minDistance,
            int silentCells,
            PaletteHelp paletteHelp,
            int placeCost,
            int parentCost) {
        /** Checks the tuning ranges. */
        public Snapshot {
            check("min_distance", minDistance, 1, 6);
            check("silent_cells", silentCells, 0, MAX_SILENT);
            check("place_cost", placeCost, 1, 4);
            check("parent_cost", parentCost, 1, 4);
        }

        private static void check(String name, int value, int min, int max) {
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        name + " " + value + " outside " + min + ".." + max);
            }
        }
    }

    /** A preset's palette help. */
    public enum PaletteHelp {
        /** Hovering a cell highlights palette aspects that would link to a neighbour. */
        NEIGHBOURS,
        /** Hovering a palette aspect shows its parents. */
        PARENTS;

        /** The JSON name. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * An axial cell {@code (q, r)}.
     *
     * @param q the column
     * @param r the row
     */
    public record Cell(int q, int r) {
        /** The hex distance from {@code (0, 0)}. */
        public int distance() {
            return (Math.abs(q) + Math.abs(r) + Math.abs(q + r)) / 2;
        }
    }

    /**
     * An aspect in a cell.
     *
     * @param cell the cell
     * @param aspect the aspect id
     */
    public record Placed(Cell cell, String aspect) {}
}
