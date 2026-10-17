package com.andrewbrogoff.vftv.core.warp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.api.warp.WarpSourceKind;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Replays {@code content/warp/fixtures.json} (warp spec, "Fixtures"): the sections this build's
 * warp core computes. The {@code checks} draws and the foci {@code magnitude} rows are the Voice
 * events' and the foci's to replay.
 */
class WarpFixturesTest {
    private static final JsonObject FIXTURES = load();
    private static final WarpRules DEFAULTS = WarpTuning.DEFAULTS;

    private static JsonObject load() {
        Path file =
                Path.of(System.getProperty("vftv.projectDir", "."), "content/warp/fixtures.json");
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

    private static int i(JsonObject o, String key) {
        return o.get(key).getAsInt();
    }

    private static boolean flag(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    /** The default rules with the given switches, cap and Joining bonus. */
    private static WarpRules rules(boolean tax, boolean amplifier, int cap, int bonus) {
        JsonObject json = WarpTuning.toJson(DEFAULTS, true);
        json.addProperty("warp.tax", tax);
        json.addProperty("warp.amplifier", amplifier);
        json.addProperty("permanent.cap", cap);
        json.addProperty("joining.amplifier_bonus", bonus);
        if (cap < 10) {
            // A cap below the shipped milestones' sum needs a tuning that lists Joining alone.
            JsonObject milestones = new JsonObject();
            milestones.addProperty(WarpTuning.JOINING, Math.min(5, Math.max(1, cap)));
            json.add("permanent.milestones", milestones);
        }
        return WarpTuning.fromJson(json, true);
    }

    @Test
    void phases() {
        for (JsonObject row : rows("phase")) {
            UUID uuid =
                    new UUID(
                            Long.parseUnsignedLong(row.get("msb").getAsString(), 16),
                            Long.parseUnsignedLong(row.get("lsb").getAsString(), 16));
            JsonArray expect = row.getAsJsonArray("expect");
            assertEquals(expect.get(0).getAsInt(), WarpPhase.of(uuid, 1200), uuid + " 1200");
            assertEquals(expect.get(1).getAsInt(), WarpPhase.of(uuid, 400), uuid + " 400");
            assertEquals(expect.get(2).getAsInt(), WarpPhase.of(uuid, 20), uuid + " 20");
        }
    }

    @Test
    void dials() {
        for (JsonObject row : rows("dials")) {
            int total = i(row, "total");
            WarpRules rules = rules(flag(row, "tax", true), true, 10, 10);
            JsonObject e = row.getAsJsonObject("expect");
            String at = "total " + total;
            assertEquals(i(e, "band"), WarpMath.band(total, rules), at);
            assertEquals(i(e, "factor_ordinary"), WarpMath.visFactorPm(total, true, rules), at);
            assertEquals(i(e, "factor_void"), WarpMath.visFactorPm(total, false, rules), at);
            assertEquals(
                    i(e, "penalty_ordinary"), WarpMath.infusionPenalty(total, true, rules), at);
            assertEquals(i(e, "penalty_void"), WarpMath.infusionPenalty(total, false, rules), at);
        }
    }

    /** The wands quote recovers the exact per mille from the factor, at every total. */
    @Test
    void theFactorsPerMilleIsRecoveredAtEveryTotal() {
        for (int total = 0; total <= 2200; total++) {
            int pm = WarpMath.visFactorPm(total, true, DEFAULTS);
            double factor = pm / 1000.0;
            assertEquals(
                    pm, Math.round(Math.max(0, Math.min(10, factor)) * 1000), "total " + total);
        }
    }

    @Test
    void previews() {
        for (JsonObject row : rows("preview")) {
            JsonArray state = row.getAsJsonArray("state");
            JsonArray cost = row.getAsJsonArray("cost");
            JsonObject source = row.getAsJsonObject("source");
            Set<String> paid = new HashSet<>();
            if (row.has("paid")) {
                row.getAsJsonArray("paid").forEach(p -> paid.add(p.getAsString()));
            }
            int cap = row.has("cap") ? i(row, "cap") : 10;
            WarpPreview actual =
                    WarpMath.preview(
                            state.get(0).getAsInt(),
                            state.get(1).getAsInt(),
                            state.get(2).getAsInt(),
                            paid,
                            cost.get(0).getAsInt(),
                            cost.get(1).getAsInt(),
                            cost.get(2).getAsInt(),
                            WarpSourceKind.valueOf(
                                    source.get("kind").getAsString().toUpperCase(Locale.ROOT)),
                            source.has("id") ? source.get("id").getAsString() : null,
                            rules(true, true, cap, 10));
            JsonElement expect = row.get("expect");
            if (expect.isJsonPrimitive()) {
                assertEquals("invalid", expect.getAsString());
                assertNull(actual, row.toString());
            } else {
                JsonArray a = expect.getAsJsonObject().getAsJsonArray("actual");
                boolean clipped = expect.getAsJsonObject().get("clipped").getAsBoolean();
                assertEquals(
                        new WarpPreview(
                                a.get(0).getAsInt(),
                                a.get(1).getAsInt(),
                                a.get(2).getAsInt(),
                                clipped),
                        actual,
                        row.toString());
            }
        }
    }

    @Test
    void background() {
        for (JsonObject row : rows("background")) {
            assertEquals(
                    i(row, "expect_pm"),
                    WarpMath.chancePm(
                            i(row, "total"), i(row, "permanent"), i(row, "counter"), DEFAULTS),
                    row.toString());
        }
    }

    @Test
    void reconcile() {
        for (JsonObject row : rows("reconcile")) {
            JsonObject json = WarpTuning.toJson(DEFAULTS, true);
            json.addProperty("counter.max", i(row, "counter_max"));
            WarpRules rules = WarpTuning.fromJson(json, true);
            int counter = i(row, "counter");
            JsonObject e = row.getAsJsonObject("expect");
            String at = row.toString();
            assertEquals(
                    i(e, "pm"),
                    WarpMath.chancePm(i(row, "total"), i(row, "permanent"), counter, rules),
                    at);
            assertEquals(i(e, "after_fire"), WarpMath.counterAfterEvent(counter, rules), at);
            assertEquals(i(e, "after_gain_2"), WarpMath.counterAfterGain(counter, 2, rules), at);
            assertEquals(
                    i(e, "after_void_work"), WarpMath.counterAfterVoidWork(counter, rules), at);
        }
    }

    @Test
    void amplifier() {
        for (JsonObject row : rows("amplifier")) {
            int bonus = row.has("bonus") ? i(row, "bonus") : 10;
            WarpRules rules = rules(true, flag(row, "on", true), 10, bonus);
            assertEquals(
                    i(row.getAsJsonObject("expect"), "w"),
                    WarpMath.amplifierWarp(i(row, "total"), flag(row, "joined", false), rules),
                    row.toString());
        }
    }

    /**
     * The coincident schedule: decay, then the gear sample, then the check, each on its phase,
     * reading the state after that tick's earlier steps.
     */
    @Test
    void schedule() {
        for (JsonObject row : rows("schedule")) {
            UUID uuid = UUID.fromString(row.get("uuid").getAsString());
            JsonObject start = row.getAsJsonObject("start");
            JsonObject held = row.getAsJsonObject("gear_held");
            int t = i(start, "T");
            int l = i(start, "L");
            int p = i(start, "P");
            int gear = i(start, "gear");
            int counter = i(start, "counter");
            int decayPhase = WarpPhase.of(uuid, DEFAULTS.temporaryDecayInterval());
            int gearPhase = WarpPhase.of(uuid, 20);
            int checkPhase = WarpPhase.of(uuid, DEFAULTS.checkInterval());
            List<String> ops = new ArrayList<>();
            int index = 0;
            for (int g = 0; g <= i(row, "until"); g++) {
                if (g % DEFAULTS.temporaryDecayInterval() == decayPhase && t > 0) {
                    t--;
                    ops.add(g + " decay T " + t + " total " + (t + l + p + gear));
                }
                if (g % 20 == gearPhase) {
                    int sample = g >= i(held, "from") && g <= i(held, "to") ? i(held, "value") : 0;
                    if (sample != gear) {
                        gear = Math.min(DEFAULTS.gearMax(), sample);
                        ops.add(g + " gear " + gear + " total " + (t + l + p + gear));
                    }
                }
                if (g % DEFAULTS.checkInterval() == checkPhase) {
                    int total = t + l + p + gear;
                    ops.add(
                            g
                                    + " check total "
                                    + total
                                    + " band "
                                    + WarpMath.band(total, DEFAULTS)
                                    + " index "
                                    + index++
                                    + " pm "
                                    + WarpMath.chancePm(total, p, counter, DEFAULTS));
                }
            }
            List<String> expected = new ArrayList<>();
            for (JsonElement element : row.getAsJsonArray("expect")) {
                JsonObject op = element.getAsJsonObject();
                int tick = i(op, "tick");
                expected.add(
                        switch (op.get("op").getAsString()) {
                            case "decay" ->
                                    tick + " decay T " + i(op, "T") + " total " + i(op, "total");
                            case "gear" ->
                                    tick + " gear " + i(op, "gear") + " total " + i(op, "total");
                            default ->
                                    tick
                                            + " check total "
                                            + i(op, "total")
                                            + " band "
                                            + i(op, "band")
                                            + " index "
                                            + i(op, "index")
                                            + " pm "
                                            + i(op.getAsJsonObject("expect"), "pm");
                        });
            }
            assertEquals(expected, ops);
            assertTrue(counter > 0, "gear and decay never touch the counter");
        }
    }
}
