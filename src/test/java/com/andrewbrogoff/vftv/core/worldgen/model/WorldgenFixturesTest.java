package com.andrewbrogoff.vftv.core.worldgen.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Replays {@code content/worldgen/fixtures.json} (worldgen spec, "Fixtures"): the node roll and the
 * offer tables through the model. The placement rows run through vanilla's own placement in {@code
 * WorldgenPlacementTest}.
 */
class WorldgenFixturesTest {
    static final JsonObject FIXTURES = load("content/worldgen/fixtures.json");

    static JsonObject load(String path) {
        Path file = Path.of(System.getProperty("vftv.projectDir", "."), path);
        try {
            return StrictJson.parse(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static long hex(JsonElement e) {
        return new BigInteger(e.getAsString().substring(2), 16).longValue();
    }

    static List<JsonObject> rows(String section) {
        List<JsonObject> rows = new ArrayList<>();
        FIXTURES.getAsJsonArray(section).forEach(r -> rows.add(r.getAsJsonObject()));
        assertFalse(rows.isEmpty(), section);
        return rows;
    }

    /** BlockPos.asLong, transcribed: 26 bits of x at 38, 26 bits of z at 12, 12 bits of y. */
    static long asLong(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }

    @Test
    void everyRollRow() {
        List<JsonObject> rows = rows("roll");
        assertEquals(5, rows.size(), "the spec's five rows");
        for (JsonObject row : rows) {
            JsonArray pos = row.getAsJsonArray("pos");
            long key = asLong(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt());
            JsonObject expect = row.getAsJsonObject("expect");
            assertEquals(hex(expect.get("key")), key, "key " + pos);
            JsonArray c = row.getAsJsonArray("clarity");
            List<NodeRoll.Range> ranges = new ArrayList<>();
            row.getAsJsonArray("aspects")
                    .forEach(
                            a ->
                                    ranges.add(
                                            new NodeRoll.Range(
                                                    a.getAsJsonArray().get(0).getAsString(),
                                                    a.getAsJsonArray().get(1).getAsInt(),
                                                    a.getAsJsonArray().get(2).getAsInt())));
            NodeRoll.Result r =
                    NodeRoll.roll(
                            hex(row.get("seed")),
                            key,
                            new int[] {
                                c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt()
                            },
                            ranges);
            assertEquals(hex(expect.get("h")), r.h(), "h " + pos);
            assertEquals(
                    List.of("faint", "steady", "clear")
                            .indexOf(expect.get("clarity").getAsString()),
                    r.clarity(),
                    "clarity " + pos);
            JsonObject maxes = expect.getAsJsonObject("maxes");
            for (int i = 0; i < ranges.size(); i++) {
                assertEquals(
                        maxes.get(ranges.get(i).aspect()).getAsInt(),
                        r.maxes().get(i),
                        ranges.get(i).aspect() + " " + pos);
            }
        }
    }

    @Test
    void everyOfferTable() {
        List<JsonObject> tables = rows("offers");
        assertEquals(2, tables.size(), "the spec's two tables");
        for (JsonObject table : tables) {
            replay(table);
        }
    }

    /** One table: players claim, latch and lose entries; each use's outcome and pages match. */
    private static void replay(JsonObject table) {
        List<String> pages = new ArrayList<>();
        table.getAsJsonArray("pages").forEach(p -> pages.add(p.getAsString()));
        Offers.Site site = new Offers.Site(pages, table.get("pages_per_claim").getAsInt());
        Set<String> registry = new HashSet<>(pages);
        Map<String, Set<String>> held = new HashMap<>();
        Map<String, ChoirClaims> claims = new HashMap<>();
        Map<String, long[]> attempts = new HashMap<>();
        int step = 0;
        for (JsonElement e : table.getAsJsonArray("steps")) {
            JsonObject s = e.getAsJsonObject();
            step++;
            if (s.has("latch")) {
                JsonObject l = s.getAsJsonObject("latch");
                Set<String> h =
                        held.computeIfAbsent(l.get("player").getAsString(), k -> new HashSet<>());
                l.getAsJsonArray("pages").forEach(p -> h.add(p.getAsString()));
                continue;
            }
            if (s.has("remove")) {
                s.getAsJsonArray("remove").forEach(p -> registry.remove(p.getAsString()));
                continue;
            }
            JsonObject use = s.getAsJsonObject("use");
            String player = use.get("player").getAsString();
            long tick = use.get("tick").getAsLong();
            long at = use.get("site").getAsString().hashCode();
            long[] a = attempts.computeIfAbsent(player, k -> new long[] {-1, 0});
            if (a[0] != tick) {
                a[0] = tick;
                a[1] = 0;
            }
            boolean capped = a[1] >= 1;
            a[1]++;
            ChoirClaims c = claims.getOrDefault(player, ChoirClaims.EMPTY);
            Set<String> h = held.getOrDefault(player, Set.of());
            Set<String> anywhere = c.claimedAnywhere();
            Offers.Decision d =
                    Offers.decide(
                            false,
                            capped,
                            false,
                            site,
                            c.at("test:dim", at),
                            c.sites().size(),
                            new Offers.State() {
                                @Override
                                public boolean exists(String page) {
                                    return registry.contains(page);
                                }

                                @Override
                                public boolean held(String page) {
                                    return h.contains(page);
                                }

                                @Override
                                public boolean claimedAnywhere(String page) {
                                    return anywhere.contains(page);
                                }
                            });
            JsonObject expect = s.getAsJsonObject("expect");
            assertEquals(expect.get("outcome").getAsString(), d.outcome().id(), "step " + step);
            List<String> want = new ArrayList<>();
            if (expect.has("pages")) {
                expect.getAsJsonArray("pages").forEach(p -> want.add(p.getAsString()));
            }
            assertEquals(want, d.pages(), "step " + step);
            if (d.outcome() == Offers.Outcome.GIVEN) {
                claims.put(player, c.with("test:dim", at, d.pages()));
            }
        }
    }
}
