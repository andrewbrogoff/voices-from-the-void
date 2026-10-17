package com.andrewbrogoff.vftv.core.aspect.model;

import com.andrewbrogoff.vftv.api.aspect.AspectLimits;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The scanning tuning, {@code data/vftv/tuning/scanning.json} (aspects spec, "Tuning"): every key
 * optional, defaulting to the spec's value.
 *
 * @param strikeCooldownTicks the fork's gameplay cooldown
 * @param readoutHoldTicks how long the card stays after the latest strike, before the player's
 *     notification time scales it
 * @param discoveryBonus the default discovery bonus
 * @param nodeRewardDivisor the default node reward divisor
 * @param soundingRadius the sounding radius, inclusive
 * @param soundingNear the near band's outer edge
 * @param soundingMid the mid band's outer edge
 * @param answerOffset how far along the bearing the answer sounds
 * @param particles how many particles drift along the bearing
 * @param derivationFactor the derivation factor (read by the derivation, not by strikes)
 * @param smeltBonus the smelting bonus (read by the derivation, not by strikes)
 */
public record ScanningTuning(
        int strikeCooldownTicks,
        int readoutHoldTicks,
        int discoveryBonus,
        int nodeRewardDivisor,
        int soundingRadius,
        int soundingNear,
        int soundingMid,
        int answerOffset,
        int particles,
        double derivationFactor,
        SortedMap<String, Integer> smeltBonus) {
    /** The spec's values. */
    public static final ScanningTuning DEFAULTS =
            new ScanningTuning(
                    16, 60, 4, 8, 192, 16, 64, 6, 3, 0.75, new TreeMap<>(Map.of("vftv:ignis", 1)));

    private static final Pattern ASPECT_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Copies the bonus map and checks every rule. */
    public ScanningTuning {
        smeltBonus = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(smeltBonus));
        List<String> problems = problems(soundingRadius, soundingNear, soundingMid, smeltBonus);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    /** The file's keys and their values, in key order, as datagen writes them. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("strike_cooldown_ticks", strikeCooldownTicks);
        json.addProperty("readout_hold_ticks", readoutHoldTicks);
        json.addProperty("discovery_bonus", discoveryBonus);
        json.addProperty("node_reward_divisor", nodeRewardDivisor);
        json.addProperty("sounding.radius", soundingRadius);
        json.addProperty("sounding.near", soundingNear);
        json.addProperty("sounding.mid", soundingMid);
        json.addProperty("sounding.answer_offset", answerOffset);
        json.addProperty("sounding.particles", particles);
        json.addProperty("derivation.factor", derivationFactor);
        JsonObject bonus = new JsonObject();
        smeltBonus.forEach(bonus::addProperty);
        json.add("derivation.smelt_bonus", bonus);
        return json;
    }

    /**
     * Reads a tuning file: strict JSON, one object, every key optional.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static ScanningTuning parse(String text) {
        JsonElement root;
        try {
            root = StrictJson.parse(text);
        } catch (StrictJson.MalformedJsonException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof JsonObject object)) {
            throw new IllegalArgumentException("not a JSON object");
        }
        ScanningTuning d = DEFAULTS;
        List<String> problems = new ArrayList<>();
        for (String key : object.keySet()) {
            if (!d.toJson().has(key)) {
                problems.add(key + ": unknown key");
            }
        }
        ScanningTuning parsed;
        try {
            parsed =
                    new ScanningTuning(
                            integer(
                                    object,
                                    "strike_cooldown_ticks",
                                    0,
                                    1200,
                                    d.strikeCooldownTicks,
                                    problems),
                            integer(
                                    object,
                                    "readout_hold_ticks",
                                    1,
                                    1200,
                                    d.readoutHoldTicks,
                                    problems),
                            integer(object, "discovery_bonus", 0, 1000, d.discoveryBonus, problems),
                            integer(
                                    object,
                                    "node_reward_divisor",
                                    1,
                                    64,
                                    d.nodeRewardDivisor,
                                    problems),
                            integer(
                                    object,
                                    "sounding.radius",
                                    1,
                                    Integer.MAX_VALUE,
                                    d.soundingRadius,
                                    problems),
                            integer(
                                    object,
                                    "sounding.near",
                                    1,
                                    Integer.MAX_VALUE,
                                    d.soundingNear,
                                    problems),
                            integer(
                                    object,
                                    "sounding.mid",
                                    1,
                                    Integer.MAX_VALUE,
                                    d.soundingMid,
                                    problems),
                            integer(
                                    object,
                                    "sounding.answer_offset",
                                    1,
                                    32,
                                    d.answerOffset,
                                    problems),
                            integer(object, "sounding.particles", 0, 16, d.particles, problems),
                            number(object, "derivation.factor", 0, 4, d.derivationFactor, problems),
                            bonus(object, d.smeltBonus, problems));
        } catch (IllegalArgumentException e) {
            problems.add(e.getMessage());
            parsed = d;
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return parsed;
    }

    private static List<String> problems(
            int radius, int near, int mid, Map<String, Integer> bonus) {
        List<String> problems = new ArrayList<>();
        if (radius < 1 || radius > AspectLimits.MAX_SOUNDING_RADIUS) {
            problems.add(
                    "sounding.radius: "
                            + radius
                            + " outside 1.."
                            + AspectLimits.MAX_SOUNDING_RADIUS);
        }
        if (!(0 < near && near < mid && mid <= radius)) {
            problems.add("sounding band edges must satisfy 0 < near < mid <= radius");
        }
        if (bonus.size() > AspectLimits.MAX_ASPECTS_PER_ENTRY) {
            problems.add("derivation.smelt_bonus: more than 5 aspects");
        }
        return problems;
    }

    private static int integer(
            JsonObject o, String key, int min, int max, int fallback, List<String> problems) {
        JsonElement value = o.get(key);
        if (value == null) {
            return fallback;
        }
        BigDecimal n = numberOf(value);
        if (n == null || n.stripTrailingZeros().scale() > 0) {
            problems.add(key + ": not an integer");
            return fallback;
        }
        if (n.compareTo(BigDecimal.valueOf(min)) < 0 || n.compareTo(BigDecimal.valueOf(max)) > 0) {
            problems.add(key + ": " + n.toPlainString() + " outside " + min + ".." + max);
            return fallback;
        }
        return n.intValueExact();
    }

    private static double number(
            JsonObject o, String key, double min, double max, double fallback, List<String> out) {
        JsonElement value = o.get(key);
        if (value == null) {
            return fallback;
        }
        BigDecimal n = numberOf(value);
        if (n == null || n.doubleValue() < min || n.doubleValue() > max) {
            out.add(key + ": not a number in " + min + ".." + max);
            return fallback;
        }
        return n.doubleValue();
    }

    private static SortedMap<String, Integer> bonus(
            JsonObject o, SortedMap<String, Integer> fallback, List<String> problems) {
        JsonElement value = o.get("derivation.smelt_bonus");
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof JsonObject map)) {
            problems.add("derivation.smelt_bonus: not an object");
            return fallback;
        }
        SortedMap<String, Integer> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : map.entrySet()) {
            if (!ASPECT_ID.matcher(e.getKey()).matches()) {
                problems.add("derivation.smelt_bonus: bad aspect id " + e.getKey());
                continue;
            }
            int amount = integer(map, e.getKey(), 1, Integer.MAX_VALUE, 0, problems);
            if (amount > 0) {
                out.put(e.getKey(), amount);
            }
        }
        return out;
    }

    private static @Nullable BigDecimal numberOf(@Nullable JsonElement value) {
        return value instanceof JsonPrimitive p && p.isNumber() ? p.getAsBigDecimal() : null;
    }
}
