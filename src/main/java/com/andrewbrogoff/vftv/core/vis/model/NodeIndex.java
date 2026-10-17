package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * One dimension's in-memory node index (wands spec, "The index"): cells of 64×64 blocks over all
 * heights, each holding at most 8 placed nodes. {@link #nearest} reads memory only and allocates
 * nothing. Not thread-safe: the server thread owns it.
 */
public final class NodeIndex {
    private static final int SLOTS = VisLimits.MAX_NODES_PER_CELL;

    /** One cell's nodes: parallel arrays, the first {@code count} in use. */
    private static final class Cell {
        final long key;
        final long[] ids = new long[SLOTS];
        final int[] xs = new int[SLOTS];
        final int[] ys = new int[SLOTS];
        final int[] zs = new int[SLOTS];
        int count;

        Cell(long key) {
            this.key = key;
        }
    }

    private @Nullable Cell[] table = new Cell[64];
    private int size;
    private int nodes;

    /** A cell's key from its coordinates. */
    public static long cellKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    /** The key of the cell holding block {@code (x, z)}. */
    public static long cellOf(int x, int z) {
        return cellKey(
                Math.floorDiv(x, VisLimits.CELL_SIZE), Math.floorDiv(z, VisLimits.CELL_SIZE));
    }

    /** How many nodes the index holds. */
    public int size() {
        return nodes;
    }

    /** How many nodes the cell holding block {@code (x, z)} holds. */
    public int count(int x, int z) {
        Cell c = find(cellOf(x, z));
        return c == null ? 0 : c.count;
    }

    /** The ids in the cell holding block {@code (x, z)}. */
    public long[] idsIn(int x, int z) {
        Cell c = find(cellOf(x, z));
        return c == null ? new long[0] : Arrays.copyOf(c.ids, c.count);
    }

    /** Whether the index holds a node with this id. */
    public boolean contains(long id) {
        for (Cell c : table) {
            if (c != null && indexOf(c, id) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a node. A full cell refuses it: callers check {@link #count} first, so the cap holds at
     * every insertion.
     *
     * @throws IllegalStateException if the cell is full or the id is already in it
     */
    public void add(long id, int x, int y, int z) {
        long key = cellOf(x, z);
        Cell c = find(key);
        if (c == null) {
            c = insert(key);
        }
        if (c.count >= SLOTS) {
            throw new IllegalStateException("cell full: node " + id);
        }
        if (indexOf(c, id) >= 0) {
            throw new IllegalStateException("node " + id + " is already indexed");
        }
        c.ids[c.count] = id;
        c.xs[c.count] = x;
        c.ys[c.count] = y;
        c.zs[c.count] = z;
        c.count++;
        nodes++;
    }

    /** Removes a node at a position; returns whether it was there. */
    public boolean remove(long id, int x, int z) {
        Cell c = find(cellOf(x, z));
        if (c == null) {
            return false;
        }
        int i = indexOf(c, id);
        if (i < 0) {
            return false;
        }
        int last = --c.count;
        c.ids[i] = c.ids[last];
        c.xs[i] = c.xs[last];
        c.ys[i] = c.ys[last];
        c.zs[i] = c.zs[last];
        nodes--;
        return true;
    }

    /** Empties the index. */
    public void clear() {
        Arrays.fill(table, null);
        size = 0;
        nodes = 0;
    }

    /**
     * The nearest node's id within {@code r} of the point (inclusive), by 3-D distance to its
     * centre, ties to the lowest id; -1 when none. Examines the cells overlapping the square of
     * half-side {@code r}.
     *
     * @throws IllegalArgumentException for a radius outside 0 to 256
     */
    public long nearest(double px, double py, double pz, int r) {
        if (r < 0 || r > VisLimits.MAX_NEAREST_RADIUS) {
            throw new IllegalArgumentException("radius " + r + " outside 0..256");
        }
        int x0 = Math.floorDiv((int) Math.floor(px - r), VisLimits.CELL_SIZE);
        int x1 = Math.floorDiv((int) Math.floor(px + r), VisLimits.CELL_SIZE);
        int z0 = Math.floorDiv((int) Math.floor(pz - r), VisLimits.CELL_SIZE);
        int z1 = Math.floorDiv((int) Math.floor(pz + r), VisLimits.CELL_SIZE);
        double best = (double) r * r;
        long bestId = -1;
        for (int rx = x0; rx <= x1; rx++) {
            for (int rz = z0; rz <= z1; rz++) {
                Cell c = find(cellKey(rx, rz));
                if (c == null) {
                    continue;
                }
                for (int i = 0; i < c.count; i++) {
                    double dx = c.xs[i] + 0.5 - px;
                    double dy = c.ys[i] + 0.5 - py;
                    double dz = c.zs[i] + 0.5 - pz;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < best || (d == best && (bestId < 0 || c.ids[i] < bestId))) {
                        best = d;
                        bestId = c.ids[i];
                    }
                }
            }
        }
        return bestId;
    }

    /**
     * The distance from the point to the closest node centre within {@code r}, or infinity when
     * none is.
     */
    public double closest(double px, double py, double pz, int r) {
        long id = nearest(px, py, pz, r);
        if (id < 0) {
            return Double.POSITIVE_INFINITY;
        }
        int[] at = position(id);
        double dx = at[0] + 0.5 - px;
        double dy = at[1] + 0.5 - py;
        double dz = at[2] + 0.5 - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * A node's position {@code [x, y, z]}.
     *
     * @throws IllegalArgumentException when it isn't indexed
     */
    public int[] position(long id) {
        for (Cell c : table) {
            if (c != null) {
                int i = indexOf(c, id);
                if (i >= 0) {
                    return new int[] {c.xs[i], c.ys[i], c.zs[i]};
                }
            }
        }
        throw new IllegalArgumentException("node " + id + " is not indexed");
    }

    private static int indexOf(Cell c, long id) {
        for (int i = 0; i < c.count; i++) {
            if (c.ids[i] == id) {
                return i;
            }
        }
        return -1;
    }

    private int slot(long key, int length) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & (length - 1);
    }

    private @Nullable Cell find(long key) {
        @Nullable Cell[] t = table;
        for (int i = slot(key, t.length); ; i = (i + 1) & (t.length - 1)) {
            Cell c = t[i];
            if (c == null || c.key == key) {
                return c;
            }
        }
    }

    private Cell insert(long key) {
        if ((size + 1) * 2 > table.length) {
            @Nullable Cell[] old = table;
            table = new Cell[old.length * 2];
            for (Cell c : old) {
                if (c != null) {
                    place(c);
                }
            }
        }
        Cell c = new Cell(key);
        place(c);
        size++;
        return c;
    }

    private void place(Cell c) {
        int i = slot(c.key, table.length);
        while (table[i] != null) {
            i = (i + 1) & (table.length - 1);
        }
        table[i] = c;
    }
}
