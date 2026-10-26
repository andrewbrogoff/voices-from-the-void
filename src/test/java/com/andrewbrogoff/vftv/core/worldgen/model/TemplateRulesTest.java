package com.andrewbrogoff.vftv.core.worldgen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The {@code choir_ruin} family's structural rules over the shipped templates, read with a
 * simplified block model (air is air; sturdy is any full masonry), and over mutations of them.
 */
class TemplateRulesTest {
    /** A template read from its text file; mutable for the rejection cases. */
    static final class Grid {
        final TemplateRules.Role role;
        final int sx;
        final int sy;
        final int sz;
        final char[][][] cells;
        final Map<String, String> palette = new java.util.HashMap<>();
        final List<TemplateRules.Jigsaw> jigsaws = new ArrayList<>();
        final List<TemplateRules.Faced> reliquaries = new ArrayList<>();
        final List<TemplateRules.Pos> nodes = new ArrayList<>();
        final List<TemplateRules.Entity> entities = new ArrayList<>();
        int chests;

        Grid(String name) {
            role = java.util.Objects.requireNonNull(TemplateRules.FILES.get(name));
            JsonObject json =
                    WorldgenFixturesTest.load("content/worldgen/templates/" + name + ".json");
            JsonArray size = json.getAsJsonArray("size");
            sx = size.get(0).getAsInt();
            sy = size.get(1).getAsInt();
            sz = size.get(2).getAsInt();
            json.getAsJsonObject("palette")
                    .entrySet()
                    .forEach(e -> palette.put(e.getKey(), e.getValue().getAsString()));
            cells = new char[sy][sz][sx];
            JsonArray layers = json.getAsJsonArray("layers");
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    cells[y][z] = layers.get(y).getAsJsonArray().get(z).getAsString().toCharArray();
                }
            }
            for (Map.Entry<String, JsonElement> e :
                    json.getAsJsonObject("block_entities").entrySet()) {
                String[] p = e.getKey().split(",", -1);
                int x = Integer.parseInt(p[0]);
                int y = Integer.parseInt(p[1]);
                int z = Integer.parseInt(p[2]);
                JsonObject body = e.getValue().getAsJsonObject();
                String state = java.util.Objects.requireNonNull(palette.get(String.valueOf(cells[y][z][x])));
                if (body.has("jigsaw")) {
                    JsonObject j = body.getAsJsonObject("jigsaw");
                    String o = state.substring(state.indexOf('=') + 1, state.indexOf(']'));
                    String[] parts = o.split("_", -1);
                    jigsaws.add(
                            new TemplateRules.Jigsaw(
                                    x,
                                    y,
                                    z,
                                    parts[0],
                                    parts[1],
                                    new TemplateRules.Fields(
                                            j.get("name").getAsString(),
                                            j.get("target").getAsString(),
                                            j.get("pool").getAsString()),
                                    j.get("final_state").getAsString(),
                                    j.get("joint").getAsString()));
                } else if (body.has("reliquary")) {
                    reliquaries.add(new TemplateRules.Faced(x, y, z, "north"));
                } else if (body.has("node")) {
                    nodes.add(new TemplateRules.Pos(x, y, z));
                } else {
                    chests++;
                }
            }
            json.getAsJsonArray("entities")
                    .forEach(
                            e -> {
                                JsonArray p = e.getAsJsonObject().getAsJsonArray("pos");
                                entities.add(
                                        new TemplateRules.Entity(
                                                p.get(0).getAsInt(),
                                                p.get(1).getAsInt(),
                                                p.get(2).getAsInt(),
                                                e.getAsJsonObject()
                                                        .get("type")
                                                        .getAsString()
                                                        .endsWith("cantor")));
                            });
        }

        String state(int x, int y, int z) {
            char c = cells[y][z][x];
            return c == ' ' ? "void" : java.util.Objects.requireNonNull(palette.get(String.valueOf(c)));
        }

        List<String> check() {
            return TemplateRules.check(
                    new TemplateRules.Template(
                            role,
                            sx,
                            sy,
                            sz,
                            new TemplateRules.Cells() {
                                @Override
                                public boolean air(int x, int y, int z) {
                                    return state(x, y, z).equals("minecraft:air");
                                }

                                @Override
                                public boolean sturdy(int x, int y, int z) {
                                    String s = state(x, y, z);
                                    return !s.equals("void")
                                            && !s.equals("minecraft:air")
                                            && !s.contains("[")
                                            && !s.equals("vftv:node");
                                }
                            },
                            jigsaws,
                            reliquaries,
                            nodes,
                            chests,
                            entities));
        }
    }

    private static void rejects(String name, Consumer<Grid> mutate, String reason) {
        Grid g = new Grid(name);
        mutate.accept(g);
        List<String> problems = g.check();
        assertTrue(problems.stream().anyMatch(p -> p.contains(reason)), problems.toString());
    }

    @Test
    void theShippedTemplatesPass() {
        for (String name : TemplateRules.FILES.keySet()) {
            assertEquals(List.of(), new Grid(name).check(), name);
        }
    }

    @Test
    void sizesAndCounts() {
        rejects("rubble_a", g -> g.chests = 1, "chests: 1");
        rejects("chancel_a", g -> g.nodes.clear(), "nodes: 0");
        rejects("chancel_a", g -> g.reliquaries.clear(), "Reliquarys: 0");
        rejects("cloister", g -> g.entities.add(new TemplateRules.Entity(8, 1, 4, false)), "Answerers: 3");
        rejects("cloister", g -> g.entities.add(new TemplateRules.Entity(8, 1, 4, true)), "Cantors: 1");
        rejects("chancel_b", g -> g.entities.removeIf(e -> e.cantor()), "Cantors: 0");
        Grid g = new Grid("rubble_a");
        List<String> p =
                TemplateRules.check(
                        new TemplateRules.Template(
                                TemplateRules.Role.END,
                                10,
                                2,
                                9,
                                new TemplateRules.Cells() {
                                    @Override
                                    public boolean air(int x, int y, int z) {
                                        return false;
                                    }

                                    @Override
                                    public boolean sturdy(int x, int y, int z) {
                                        return true;
                                    }
                                },
                                g.jigsaws,
                                g.reliquaries,
                                g.nodes,
                                0,
                                g.entities));
        assertTrue(p.getFirst().contains("size"), p.toString());
    }

    @Test
    void connectors() {
        rejects("rubble_b", g -> g.jigsaws.clear(), "no vftv:choir_ruin/end_plug");
        rejects(
                "rubble_b",
                g -> {
                    TemplateRules.Jigsaw j = g.jigsaws.removeFirst();
                    g.jigsaws.add(
                            new TemplateRules.Jigsaw(
                                    j.x(), j.y(), j.z(), "east", "up", j.fields(), j.finalState(), j.joint()));
                },
                "must face north_up");
        rejects(
                "rubble_b",
                g -> {
                    TemplateRules.Jigsaw j = g.jigsaws.removeFirst();
                    g.jigsaws.add(
                            new TemplateRules.Jigsaw(
                                    j.x(), j.y(), j.z(), j.facing(), j.up(), TemplateRules.WING_PLUG, j.finalState(), "rollable"));
                },
                "fields differ");
        rejects(
                "rubble_b",
                g ->
                        g.jigsaws.add(
                                new TemplateRules.Jigsaw(
                                        3, 1, 3, "north", "up", TemplateRules.END_PLUG, "minecraft:air", "aligned")),
                "not at a connector position");
        rejects(
                "rubble_b",
                g -> g.jigsaws.add(g.jigsaws.getFirst()),
                "a second connector in one slot");
    }

    @Test
    void footingAndPaths() {
        rejects(
                "chancel_a",
                g -> {
                    g.entities.removeFirst();
                    g.entities.add(new TemplateRules.Entity(0, 1, 0, false));
                },
                "is not a standing cell");
        rejects(
                "chancel_a",
                g -> g.entities.add(new TemplateRules.Entity(14, 1, 12, true)),
                "two at");
        rejects(
                "chancel_a",
                g -> g.cells[2][15][11] = '#',
                "in front of its face");
        rejects(
                "chancel_a",
                g -> {
                    for (int x = 1; x < 22; x++) {
                        for (int y = 1; y <= 3; y++) {
                            g.cells[y][13][x] = '#';
                        }
                    }
                },
                "not reachable from the socket");
        rejects(
                "cloister",
                g -> {
                    for (int x = 1; x < 16; x++) {
                        for (int y = 1; y <= 3; y++) {
                            g.cells[y][2][x] = '#';
                        }
                    }
                },
                "is not reachable from the plug");
        rejects(
                "chancel_a",
                g -> {
                    TemplateRules.Pos n = g.nodes.removeFirst();
                    g.nodes.add(new TemplateRules.Pos(n.x(), 8, n.z()));
                },
                "fewer than 8 blocks above");
    }
}
