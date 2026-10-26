package com.andrewbrogoff.vftv.core.hush.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.andrewbrogoff.vftv.core.vis.model.QuoteMath;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Replays every section of {@code content/hush/fixtures.json} (Hush spec, "Fixtures") against the
 * model: the spill and essentia tables, phases, streams, the walk kernel on {@code flat63}, the
 * onset-enforced scheduler with its node and rise, the HUD bound and the ball tables.
 */
class HushFixturesTest {
    private static final JsonObject FIXTURES = load();

    private static JsonObject load() {
        Path file =
                Path.of(System.getProperty("vftv.projectDir", "."), "content/hush/fixtures.json");
        try {
            return StrictJson.parse(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<JsonObject> rows(String section) {
        List<JsonObject> rows = new ArrayList<>();
        FIXTURES.getAsJsonArray(section).forEach(row -> rows.add(row.getAsJsonObject()));
        assertFalse(rows.isEmpty(), section);
        return rows;
    }

    private static long hex(JsonElement e) {
        return new BigInteger(e.getAsString().substring(2), 16).longValue();
    }

    private static int[] ints(JsonArray a) {
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsInt();
        }
        return out;
    }

    private static FluxRules rules(int essentiaPm, int taxPm, int voidPm) {
        int[] tuning = FluxTuning.defaults();
        tuning[0] = essentiaPm;
        tuning[1] = taxPm;
        tuning[2] = voidPm;
        return FluxTuning.rules(tuning, true, true, false);
    }

    @Test
    void spill() {
        for (JsonObject row : rows("spill")) {
            int[] base = ints(row.getAsJsonArray("base"));
            int minM = row.has("min_m") ? row.get("min_m").getAsInt() : 500;
            int maxM = row.has("max_m") ? row.get("max_m").getAsInt() : 4000;
            int cap = row.get("cap").getAsInt();
            int d = row.get("d").getAsInt();
            int m = QuoteMath.multiplier(cap, d, row.get("w").getAsInt(), minM, maxM);
            int neutralM = QuoteMath.multiplier(cap, d, 1000, minM, maxM);
            int[] effective = new int[base.length];
            int[] neutral = new int[base.length];
            int surcharge = 0;
            for (int i = 0; i < base.length; i++) {
                effective[i] = QuoteMath.effective(base[i], m);
                neutral[i] = QuoteMath.effective(base[i], neutralM);
                surcharge += Math.max(0, effective[i] - neutral[i]);
            }
            VisKind kind =
                    row.get("kind").getAsString().equals("void") ? VisKind.VOID : VisKind.ORDINARY;
            if (kind == VisKind.VOID) {
                // VOID work's factor is 1.0 (warp, "The dials"): effective is the neutral cost.
                effective = neutral.clone();
            }
            FluxRules rules =
                    rules(
                            1000,
                            row.has("tax_pm") ? row.get("tax_pm").getAsInt() : 1000,
                            row.has("void_pm") ? row.get("void_pm").getAsInt() : 250);
            JsonObject expect = row.getAsJsonObject("expect");
            String at = row.toString();
            assertEquals(
                    List.of(boxed(ints(expect.getAsJsonArray("effective")))),
                    List.of(boxed(effective)),
                    at);
            assertEquals(
                    List.of(boxed(ints(expect.getAsJsonArray("neutral")))),
                    List.of(boxed(neutral)),
                    at);
            if (expect.has("surcharge")) {
                assertEquals(expect.get("surcharge").getAsInt(), surcharge, at);
            }
            assertEquals(
                    expect.get("spill").getAsInt(),
                    FluxMath.visSpill(kind, effective, neutral, rules),
                    at);
        }
    }

    private static List<Integer> boxed(int[] a) {
        List<Integer> out = new ArrayList<>();
        for (int v : a) {
            out.add(v);
        }
        return out;
    }

    @Test
    void essentia() {
        for (JsonObject row : rows("essentia")) {
            FluxRules rules = rules(row.get("pm").getAsInt(), 1000, 250);
            int times = row.has("times") ? row.get("times").getAsInt() : 1;
            int total = row.get("total").getAsInt();
            assertEquals(
                    row.get("expect").getAsInt(),
                    times * FluxMath.essentiaFluxBound(total, SpillCause.UPSET, rules),
                    row.toString());
            assertEquals(
                    FluxMath.essentiaFluxBound(total, SpillCause.EMPTIED, rules),
                    FluxMath.essentiaFlux(new int[] {total}, SpillCause.EMPTIED, rules));
        }
    }

    private static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | (z & 0xFFFFFFFFL) << 32;
    }

    @Test
    void phase() {
        for (JsonObject row : rows("phase")) {
            JsonArray c = row.getAsJsonArray("chunk");
            long pack = pack(c.get(0).getAsInt(), c.get(1).getAsInt());
            JsonObject expect = row.getAsJsonObject("expect");
            assertEquals(hex(expect.get("fold")), HushMath.chunkFold(pack), row.toString());
            assertEquals(
                    expect.get("phase").getAsInt(),
                    HushMath.chunkPhase(pack, row.get("interval").getAsInt()),
                    row.toString());
        }
    }

    @Test
    void stream() {
        for (JsonObject row : rows("stream")) {
            JsonArray c = row.getAsJsonArray("chunk");
            long h =
                    HushMath.attemptSeed(
                            hex(row.get("seed")),
                            pack(c.get(0).getAsInt(), c.get(1).getAsInt()),
                            hex(row.get("steps")));
            JsonObject expect = row.getAsJsonObject("expect");
            assertEquals(hex(expect.get("h")), h);
            com.andrewbrogoff.vftv.core.common.model.Hashes.Stream s =
                    new com.andrewbrogoff.vftv.core.common.model.Hashes.Stream(h);
            int[] dirs = ints(expect.getAsJsonArray("directions"));
            assertEquals(row.get("count").getAsInt(), dirs.length);
            for (int dir : dirs) {
                assertEquals(dir, (int) ((s.next() >>> 1) % 6));
            }
        }
    }

    /** {@code flat63}: sources at y ≤ 63, air above, the four chunks in {-1, 0}² loaded. */
    static final class Flat63 implements Walk.Cells {
        final Set<Long> hushed = new HashSet<>();
        final Set<Long> nodes = new HashSet<>();

        static long key(int x, int y, int z) {
            return ((long) x & 0xFFFFF) | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF) << 40;
        }

        @Override
        public int classify(int x, int y, int z) {
            int cx = Math.floorDiv(x, 16);
            int cz = Math.floorDiv(z, 16);
            if (cx < -1 || cx > 0 || cz < -1 || cz > 0) {
                return Walk.UNLOADED;
            }
            long k = key(x, y, z);
            if (nodes.contains(k)) {
                return Walk.NODE;
            }
            return y <= 63 && !hushed.contains(k) ? Walk.SOURCE : Walk.PASS;
        }

        boolean unhushedAir(int x, int y, int z) {
            return y >= 64 && !nodes.contains(key(x, y, z));
        }
    }

    @Test
    void kernel() {
        for (JsonObject row : rows("kernel")) {
            assertEquals("flat63", row.get("world").getAsString());
            Flat63 world = new Flat63();
            int[] seep = ints(row.getAsJsonArray("seep"));
            long seed = hex(row.get("seed"));
            JsonArray expect = row.getAsJsonArray("expect");
            assertEquals(row.get("attempts").getAsInt(), expect.size());
            for (int i = 0; i < expect.size(); i++) {
                JsonObject e = expect.get(i).getAsJsonObject();
                long steps = hex(e.get("steps"));
                long out =
                        Walk.attempt(
                                world,
                                HushMath.attemptSeed(seed, pack(0, 0), steps),
                                seep[0],
                                seep[1],
                                seep[2],
                                row.get("radius").getAsInt(),
                                row.get("walk").getAsInt(),
                                -64,
                                320,
                                true,
                                true);
                String at = row.get("seep") + " steps " + steps;
                assertEquals(e.get("used").getAsInt(), Walk.used(out), at);
                if (e.get("result").getAsString().equals("convert")) {
                    assertEquals(Walk.BLOCK, Walk.kind(out), at);
                    int[] cell = ints(e.getAsJsonArray("at"));
                    assertEquals(cell[0], seep[0] + Walk.dx(out), at);
                    assertEquals(cell[1], seep[1] + Walk.dy(out), at);
                    assertEquals(cell[2], seep[2] + Walk.dz(out), at);
                    world.hushed.add(Flat63.key(cell[0], cell[1], cell[2]));
                } else {
                    assertEquals(
                            e.get("reason").getAsString().equals("none")
                                    ? Walk.MISS_NONE
                                    : Walk.MISS_UNLOADED,
                            Walk.kind(out),
                            at);
                }
            }
        }
    }

    @Test
    void scheduler() {
        for (JsonObject row : rows("scheduler")) {
            Flat63 world = new Flat63();
            row.getAsJsonArray("nodes")
                    .forEach(
                            n -> {
                                int[] c = ints(n.getAsJsonArray());
                                world.nodes.add(Flat63.key(c[0], c[1], c[2]));
                            });
            int[] seep = ints(row.getAsJsonArray("seep"));
            long seed = hex(row.get("seed"));
            int flux = row.get("flux").getAsInt();
            int onset = row.get("onset").getAsInt();
            int blockCost = row.get("block_cost").getAsInt();
            int nodeCost = row.get("node_cost").getAsInt();
            int every = row.get("every").getAsInt();
            int hollowFlux = row.get("hollow_flux").getAsInt();
            long steps = 0;
            long taken = 0;
            JsonArray expect = row.getAsJsonArray("expect");
            assertEquals(row.get("visits").getAsInt(), expect.size());
            for (JsonElement el : expect) {
                JsonObject e = el.getAsJsonObject();
                String at = "visit " + e.get("visit");
                String result = e.get("result").getAsString();
                if (flux < HushMath.threshold(onset, blockCost)) {
                    assertEquals("idle", result, at);
                } else {
                    boolean turn = HushMath.hollowTurn(taken, every);
                    long out =
                            Walk.attempt(
                                    world,
                                    HushMath.attemptSeed(seed, pack(0, 0), steps),
                                    seep[0],
                                    seep[1],
                                    seep[2],
                                    row.get("radius").getAsInt(),
                                    row.get("walk").getAsInt(),
                                    -64,
                                    320,
                                    flux >= blockCost,
                                    flux >= nodeCost);
                    steps++;
                    assertEquals(e.get("used").getAsInt(), Walk.used(out), at);
                    int x = seep[0] + Walk.dx(out);
                    int y = seep[1] + Walk.dy(out);
                    int z = seep[2] + Walk.dz(out);
                    switch (Walk.kind(out)) {
                        case Walk.BLOCK -> {
                            assertEquals("block", result, at);
                            assertEquals(boxed(ints(e.getAsJsonArray("at"))), List.of(x, y, z), at);
                            flux -= blockCost;
                            taken++;
                            world.hushed.add(Flat63.key(x, y, z));
                            if (turn) {
                                boolean rose = world.unhushedAir(x, y + 1, z) && flux >= hollowFlux;
                                assertEquals(
                                        e.get("rise").getAsString(), rose ? "rose" : "no_rise", at);
                                if (rose) {
                                    flux -= hollowFlux;
                                }
                            } else {
                                assertFalse(e.has("rise"), at);
                            }
                        }
                        case Walk.NODE_TARGET -> {
                            assertEquals("node", result, at);
                            assertEquals(boxed(ints(e.getAsJsonArray("at"))), List.of(x, y, z), at);
                            flux -= nodeCost;
                            world.nodes.remove(Flat63.key(x, y, z));
                        }
                        default -> {
                            assertEquals("miss", result, at);
                            assertEquals(
                                    e.get("reason").getAsString().equals("none")
                                            ? Walk.MISS_NONE
                                            : Walk.MISS_UNLOADED,
                                    Walk.kind(out),
                                    at);
                        }
                    }
                }
                assertEquals(e.get("flux").getAsInt(), flux, at);
                assertEquals(hex(e.get("steps")), steps, at);
                assertEquals(hex(e.get("taken")), taken, at);
            }
        }
    }

    @Test
    void hud() {
        for (JsonObject row : rows("hud")) {
            assertEquals(
                    row.get("expect").getAsInt(),
                    HushMath.blocks(
                            row.get("flux").getAsInt(),
                            row.get("onset").getAsInt(),
                            row.get("block_cost").getAsInt()),
                    row.toString());
        }
    }

    @Test
    void ball() {
        for (JsonObject row : rows("ball")) {
            int r = row.get("radius").getAsInt();
            JsonObject expect = row.getAsJsonObject("expect");
            assertEquals(expect.get("count").getAsInt(), BallTables.size(r));
            JsonArray first = expect.getAsJsonArray("first");
            for (int i = 0; i < first.size(); i++) {
                assertEquals(boxed(ints(first.get(i).getAsJsonArray())), triple(r, i));
            }
            assertEquals(
                    boxed(ints(expect.getAsJsonArray("last"))), triple(r, BallTables.size(r) - 1));
        }
    }

    private static List<Integer> triple(int r, int i) {
        return List.of(BallTables.dy(r, i), BallTables.dz(r, i), BallTables.dx(r, i));
    }
}
