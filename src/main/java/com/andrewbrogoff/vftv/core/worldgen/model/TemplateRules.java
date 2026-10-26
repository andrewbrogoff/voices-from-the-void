package com.andrewbrogoff.vftv.core.worldgen.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The {@code choir_ruin} template family's structural rules (worldgen spec, "Pieces and topology"
 * and "Authored content, Templates"): sizes, role counts, connectors, footing, paths and the
 * chancel node's height. The format, block-state, namespace and Hush rules belong to the converter,
 * which reads block states; these read only what it extracts.
 */
public final class TemplateRules {
    private TemplateRules() {}

    /** The chancel's node must sit at least this far above the floor layer (y 1). */
    public static final int NODE_ABOVE_FLOOR = 8;

    /** The floor layer: the connectors' layer, standing on the foundation at y 0. */
    public static final int FLOOR = 1;

    /** A template's role in the ruin. */
    public enum Role {
        /** The start piece. */
        CHANCEL,
        /** A wing, at depth 1. */
        WING,
        /** An end, at depth 2. */
        END
    }

    /**
     * One row of the connector table (worldgen spec, "Jigsaw fields").
     *
     * @param name the jigsaw's {@code name}
     * @param target its {@code target}
     * @param pool its {@code pool}
     */
    public record Fields(String name, String target, String pool) {
        /** Every connector's {@code final_state}. */
        public static final String FINAL_STATE = "minecraft:air";

        /** Every connector's {@code joint}. */
        public static final String JOINT = "aligned";
    }

    /** {@code wing_socket}. */
    public static final Fields WING_SOCKET =
            new Fields(
                    "vftv:choir_ruin/wing_socket",
                    "vftv:choir_ruin/wing_plug",
                    "vftv:choir_ruin/wings");

    /** {@code wing_plug}. */
    public static final Fields WING_PLUG =
            new Fields("vftv:choir_ruin/wing_plug", "minecraft:empty", "minecraft:empty");

    /** {@code end_socket}. */
    public static final Fields END_SOCKET =
            new Fields(
                    "vftv:choir_ruin/end_socket", "vftv:choir_ruin/end_plug", "vftv:choir_ruin/ends");

    /** {@code end_plug}. */
    public static final Fields END_PLUG =
            new Fields("vftv:choir_ruin/end_plug", "minecraft:empty", "minecraft:empty");

    /** The eight files of the family and their roles. */
    public static final Map<String, Role> FILES =
            Map.of(
                    "chancel_a", Role.CHANCEL,
                    "chancel_b", Role.CHANCEL,
                    "cloister", Role.WING,
                    "bell_pit", Role.WING,
                    "scriptorium", Role.WING,
                    "fallen_nave", Role.WING,
                    "rubble_a", Role.END,
                    "rubble_b", Role.END);

    /**
     * A jigsaw block in a template.
     *
     * @param x its x
     * @param y its y
     * @param z its z
     * @param facing its orientation's front: {@code north}, {@code east}, {@code south} or {@code
     *     west} (anything else never matches a slot)
     * @param up its orientation's top, {@code up} for every connector
     * @param fields its name, target and pool
     * @param finalState its {@code final_state}
     * @param joint its {@code joint}
     */
    public record Jigsaw(
            int x,
            int y,
            int z,
            String facing,
            String up,
            Fields fields,
            String finalState,
            String joint) {}

    /**
     * A block with a facing: the Reliquary.
     *
     * @param x its x
     * @param y its y
     * @param z its z
     * @param facing its front, a horizontal direction name
     */
    public record Faced(int x, int y, int z, String facing) {}

    /**
     * An entity.
     *
     * @param x its standing cell's x
     * @param y its standing cell's y
     * @param z its standing cell's z
     * @param cantor whether it is a Cantor
     */
    public record Entity(int x, int y, int z, boolean cantor) {}

    /**
     * A cell position.
     *
     * @param x its x
     * @param y its y
     * @param z its z
     */
    public record Pos(int x, int y, int z) {}

    /** The template's cells, as the footing rules read them. */
    public interface Cells {
        /** Whether the cell holds air (structure void is not air). */
        boolean air(int x, int y, int z);

        /** Whether the cell holds a block with a sturdy top face. */
        boolean sturdy(int x, int y, int z);
    }

    /**
     * What the rules read of one template.
     *
     * @param role its role
     * @param sx its x size
     * @param sy its y size
     * @param sz its z size
     * @param cells its cells
     * @param jigsaws its jigsaw blocks
     * @param reliquaries its Reliquaries
     * @param nodes its node positions
     * @param chests its chest count
     * @param entities its entities
     */
    public record Template(
            Role role,
            int sx,
            int sy,
            int sz,
            Cells cells,
            List<Jigsaw> jigsaws,
            List<Faced> reliquaries,
            List<Pos> nodes,
            int chests,
            List<Entity> entities) {}

    private record Slot(int x, int z, String facing, Fields fields) {}

    /** Every structural problem; empty when the template passes. */
    public static List<String> check(Template t) {
        List<String> problems = new ArrayList<>();
        int sx = t.sx();
        int sy = t.sy();
        int sz = t.sz();
        long answerers = t.entities().stream().filter(e -> !e.cantor()).count();
        long cantors = t.entities().size() - answerers;
        List<Slot> required = new ArrayList<>();
        List<Slot> optional = new ArrayList<>();
        switch (t.role()) {
            case CHANCEL -> {
                size(problems, sx, sy, sz, 23, 30);
                count(problems, "Reliquary", t.reliquaries().size(), 1, 1);
                count(problems, "node", t.nodes().size(), 1, 1);
                count(problems, "Answerer", answerers, 1, 1);
                count(problems, "Cantor", cantors, 1, 1);
                count(problems, "chest", t.chests(), 0, 0);
                required.add(new Slot(11, 0, "north", WING_SOCKET));
                required.add(new Slot(22, 11, "east", WING_SOCKET));
                required.add(new Slot(11, 22, "south", WING_SOCKET));
                required.add(new Slot(0, 11, "west", WING_SOCKET));
            }
            case WING -> {
                size(problems, sx, sy, sz, 17, 14);
                count(problems, "Reliquary", t.reliquaries().size(), 0, 0);
                count(problems, "node", t.nodes().size(), 0, 0);
                count(problems, "Answerer", answerers, 0, 2);
                count(problems, "Cantor", cantors, 0, 0);
                count(problems, "chest", t.chests(), 0, 1);
                required.add(new Slot(8, 0, "north", WING_PLUG));
                optional.add(new Slot(0, 8, "west", END_SOCKET));
                optional.add(new Slot(16, 8, "east", END_SOCKET));
            }
            case END -> {
                size(problems, sx, sy, sz, 9, 6);
                count(problems, "Reliquary", t.reliquaries().size(), 0, 0);
                count(problems, "node", t.nodes().size(), 0, 0);
                count(problems, "Answerer", answerers, 0, 0);
                count(problems, "Cantor", cantors, 0, 0);
                count(problems, "chest", t.chests(), 0, 0);
                required.add(new Slot(4, 0, "north", END_PLUG));
            }
        }
        connectors(problems, t.jigsaws(), required, optional);
        Set<Long> seen = new HashSet<>();
        for (Entity e : t.entities()) {
            if (!seen.add(key(e.x(), e.y(), e.z()))) {
                problems.add("entities: two at " + where(e.x(), e.y(), e.z()));
            }
            if (!standing(t, e.x(), e.y(), e.z())) {
                problems.add("entities: " + where(e.x(), e.y(), e.z()) + " is not a standing cell");
            }
        }
        if (!problems.isEmpty()) {
            return problems; // the paths need the counts and connectors right
        }
        switch (t.role()) {
            case CHANCEL -> {
                Pos node = t.nodes().getFirst();
                if (node.y() - FLOOR < NODE_ABOVE_FLOOR) {
                    problems.add(
                            "node: "
                                    + where(node.x(), node.y(), node.z())
                                    + " is fewer than "
                                    + NODE_ABOVE_FLOOR
                                    + " blocks above the floor layer");
                }
                Faced r = t.reliquaries().getFirst();
                int[] front = step(r.x(), r.y(), r.z(), r.facing(), 1);
                if (front == null || !standing(t, front[0], front[1], front[2])) {
                    problems.add("Reliquary: the cell in front of its face is not a standing cell");
                } else {
                    for (Jigsaw j : t.jigsaws()) {
                        if (!reachable(t, inward(j), front)) {
                            problems.add(
                                    "paths: the Reliquary is not reachable from the socket at "
                                            + where(j.x(), j.y(), j.z()));
                        }
                    }
                }
            }
            case WING -> {
                Jigsaw plug =
                        t.jigsaws().stream()
                                .filter(j -> j.fields().equals(WING_PLUG))
                                .findFirst()
                                .orElseThrow();
                for (Entity e : t.entities()) {
                    if (!reachable(t, inward(plug), new int[] {e.x(), e.y(), e.z()})) {
                        problems.add(
                                "paths: "
                                        + where(e.x(), e.y(), e.z())
                                        + " is not reachable from the plug");
                    }
                }
            }
            case END -> {}
        }
        return problems;
    }

    private static void size(List<String> problems, int sx, int sy, int sz, int side, int high) {
        if (sx != side || sz != side || sy < 1 || sy > high) {
            problems.add(
                    "size: must be exactly " + side + " × ≤ " + high + " × " + side + ", not " + sx
                            + " × " + sy + " × " + sz);
        }
    }

    private static void count(List<String> problems, String what, long n, int min, int max) {
        if (n < min || n > max) {
            problems.add(
                    what + "s: " + n + ", not " + (min == max ? "exactly " + min : min + "–" + max));
        }
    }

    private static void connectors(
            List<String> problems, List<Jigsaw> jigsaws, List<Slot> required, List<Slot> optional) {
        Set<Slot> filled = new HashSet<>();
        for (Jigsaw j : jigsaws) {
            Slot slot =
                    Stream.concat(required.stream(), optional.stream())
                            .filter(s -> s.x() == j.x() && s.z() == j.z() && j.y() == FLOOR)
                            .findFirst()
                            .orElse(null);
            String at = "jigsaw at " + where(j.x(), j.y(), j.z());
            if (slot == null) {
                problems.add(at + ": not at a connector position of its role");
                continue;
            }
            if (!filled.add(slot)) {
                problems.add(at + ": a second connector in one slot");
            }
            if (!slot.facing().equals(j.facing()) || !"up".equals(j.up())) {
                problems.add(at + ": must face " + slot.facing() + "_up");
            }
            if (!slot.fields().equals(j.fields())
                    || !Fields.FINAL_STATE.equals(j.finalState())
                    || !Fields.JOINT.equals(j.joint())) {
                problems.add(at + ": its fields differ from " + slot.fields().name() + "'s row");
            }
        }
        for (Slot s : required) {
            if (!filled.contains(s)) {
                problems.add(
                        "connectors: no " + s.fields().name() + " at " + where(s.x(), FLOOR, s.z()));
            }
        }
    }

    /** A standing cell: a sturdy top below, and air in it and above it (open sky above the top). */
    static boolean standing(Template t, int x, int y, int z) {
        if (x < 0 || x >= t.sx() || z < 0 || z >= t.sz() || y < 1 || y >= t.sy()) {
            return false;
        }
        return t.cells().sturdy(x, y - 1, z)
                && t.cells().air(x, y, z)
                && (y + 1 >= t.sy() || t.cells().air(x, y + 1, z));
    }

    /** On foot: 4-connected standing cells, rising or falling at most one block per step. */
    static boolean reachable(Template t, int[] from, int[] to) {
        if (!standing(t, from[0], from[1], from[2])) {
            return false;
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(from);
        seen.add(key(from[0], from[1], from[2]));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (c[0] == to[0] && c[1] == to[1] && c[2] == to[2]) {
                return true;
            }
            for (int[] d : dirs) {
                for (int dy = -1; dy <= 1; dy++) {
                    int x = c[0] + d[0];
                    int y = c[1] + dy;
                    int z = c[2] + d[1];
                    if (standing(t, x, y, z) && seen.add(key(x, y, z))) {
                        queue.add(new int[] {x, y, z});
                    }
                }
            }
        }
        return false;
    }

    private static int[] inward(Jigsaw j) {
        int[] in = step(j.x(), j.y(), j.z(), j.facing(), -1);
        return in == null ? new int[] {j.x(), j.y(), j.z()} : in;
    }

    private static int @Nullable [] step(int x, int y, int z, String facing, int n) {
        return switch (facing) {
            case "north" -> new int[] {x, y, z - n};
            case "south" -> new int[] {x, y, z + n};
            case "west" -> new int[] {x - n, y, z};
            case "east" -> new int[] {x + n, y, z};
            default -> null;
        };
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) y << 20) ^ z;
    }

    private static String where(int x, int y, int z) {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
