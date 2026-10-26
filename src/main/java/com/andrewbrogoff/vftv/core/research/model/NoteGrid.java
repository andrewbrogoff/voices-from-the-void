package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.api.research.ResearchLimits;
import com.andrewbrogoff.vftv.core.research.model.Note.Cell;
import java.util.ArrayList;
import java.util.List;

/**
 * The note grid's geometry (research spec, "Grid geometry"): axial {@code (q, r)}, pointy-top, the
 * neighbour order {@code D0…D5}, the spiral order and the twelve symmetries.
 */
public final class NoteGrid {
    /** {@code D0…D5}: E, NE, NW, W, SW, SE. Every scan over neighbours uses this order. */
    public static final List<Cell> DIRECTIONS =
            List.of(
                    new Cell(1, 0),
                    new Cell(1, -1),
                    new Cell(0, -1),
                    new Cell(-1, 0),
                    new Cell(-1, 1),
                    new Cell(0, 1));

    /** The number of symmetries. */
    public static final int SYMMETRIES = 12;

    private static final List<List<Cell>> SPIRALS = spirals();

    private NoteGrid() {}

    /** The cells of the grid of radius {@code radius}: {@code 3R(R + 1) + 1}. */
    public static int cells(int radius) {
        return 3 * radius * (radius + 1) + 1;
    }

    /** The hex distance between two cells. */
    public static long distance(Cell a, Cell b) {
        return new Cell(a.q() - b.q(), a.r() - b.r()).distance();
    }

    /** The neighbour of {@code cell} in direction {@code d} (0-5). */
    public static Cell step(Cell cell, int d) {
        Cell dir = DIRECTIONS.get(d);
        return new Cell(cell.q() + dir.q(), cell.r() + dir.r());
    }

    /** Whether {@code cell} lies in the grid of radius {@code radius}. */
    public static boolean inside(Cell cell, int radius) {
        return cell.distance() <= radius;
    }

    /**
     * The spiral order of the grid of radius {@code radius} (0-7): {@code (0, 0)}, then each ring k
     * from {@code (−k, 1)}, {@code D5} (k − 1) times, then {@code D0…D4} k times each.
     */
    public static List<Cell> spiral(int radius) {
        return SPIRALS.get(radius);
    }

    /**
     * Symmetry {@code t} (0-11): for {@code t ≥ 6} reflect {@code (q, r) → (r, q)}, then rotate
     * {@code t mod 6} times by {@code (q, r) → (−r, q + r)}.
     */
    public static Cell transform(Cell cell, int t) {
        int q = t >= 6 ? cell.r() : cell.q();
        int r = t >= 6 ? cell.q() : cell.r();
        for (int i = 0; i < t % 6; i++) {
            int nq = -r;
            r = q + r;
            q = nq;
        }
        return new Cell(q, r);
    }

    private static List<List<Cell>> spirals() {
        List<Cell> order = new ArrayList<>(List.of(new Cell(0, 0)));
        List<List<Cell>> out = new ArrayList<>(List.of(List.copyOf(order)));
        for (int k = 1; k <= ResearchLimits.MAX_GRID_RADIUS; k++) {
            Cell at = new Cell(-k, 1);
            order.add(at);
            for (int i = 0; i < k - 1; i++) {
                at = step(at, 5);
                order.add(at);
            }
            for (int d = 0; d <= 4; d++) {
                for (int i = 0; i < k; i++) {
                    at = step(at, d);
                    order.add(at);
                }
            }
            out.add(List.copyOf(order));
        }
        return List.copyOf(out);
    }
}
