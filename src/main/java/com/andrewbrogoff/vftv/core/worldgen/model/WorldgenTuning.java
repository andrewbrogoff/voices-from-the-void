package com.andrewbrogoff.vftv.core.worldgen.model;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The worldgen tuning, {@code data/vftv/tuning/worldgen.json} (worldgen spec, "Tuning and limits"):
 * every key optional, defaulting to the spec's value. An unknown key, a wrong type or a value out
 * of range makes the file invalid.
 *
 * @param values every value, in {@link #KEYS} order
 */
public record WorldgenTuning(List<Integer> values) {
    /** One key: its name, inclusive range and default. */
    public record Key(String name, int min, int max, int fallback) {}

    /** Every key, in key order. */
    public static final List<Key> KEYS =
            List.of(
                    new Key("answerer.home_radius", 8, 48, 24),
                    new Key("cantor.interval", 20, 1200, 100),
                    new Key("cantor.radius", 2, 16, 8),
                    new Key("cantor.max_targets", 1, 16, 6),
                    new Key("cantor.duration", 20, 600, 120),
                    new Key("cantor.amplifier", 0, 1, 0));

    /** The spec's values. */
    public static final WorldgenTuning DEFAULTS =
            new WorldgenTuning(KEYS.stream().map(Key::fallback).toList());

    /** Checks the count and every range. */
    public WorldgenTuning {
        values = List.copyOf(values);
        if (values.size() != KEYS.size()) {
            throw new IllegalArgumentException(values.size() + " values, not " + KEYS.size());
        }
        for (int i = 0; i < values.size(); i++) {
            Key k = KEYS.get(i);
            int v = values.get(i);
            if (v < k.min() || v > k.max()) {
                throw new IllegalArgumentException(
                        k.name() + ": " + v + " outside " + k.min() + ".." + k.max());
            }
        }
    }

    /** {@code answerer.home_radius}, in blocks. */
    public int homeRadius() {
        return values.get(0);
    }

    /** {@code cantor.interval}, in ticks. */
    public int chantInterval() {
        return values.get(1);
    }

    /** {@code cantor.radius}, in blocks. */
    public int chantRadius() {
        return values.get(2);
    }

    /** {@code cantor.max_targets}, the Cantor included. */
    public int chantMaxTargets() {
        return values.get(3);
    }

    /** {@code cantor.duration}, in ticks. */
    public int chantDuration() {
        return values.get(4);
    }

    /** {@code cantor.amplifier}: the Resistance level minus one. */
    public int chantAmplifier() {
        return values.get(5);
    }

    /** The file's keys and values, in key order, as datagen writes them. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        for (int i = 0; i < KEYS.size(); i++) {
            json.addProperty(KEYS.get(i).name(), values.get(i));
        }
        return json;
    }

    /**
     * Reads a tuning file: strict JSON, one object, every key optional.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static WorldgenTuning parse(String text) {
        JsonElement root;
        try {
            root = StrictJson.parse(text);
        } catch (StrictJson.MalformedJsonException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof JsonObject object)) {
            throw new IllegalArgumentException("not a JSON object");
        }
        List<String> problems = new ArrayList<>();
        for (String key : object.keySet()) {
            if (KEYS.stream().noneMatch(k -> k.name().equals(key))) {
                problems.add(key + ": unknown key");
            }
        }
        List<Integer> v = new ArrayList<>();
        for (Key k : KEYS) {
            int value = k.fallback();
            JsonElement e = object.get(k.name());
            if (e != null) {
                BigDecimal n =
                        e instanceof JsonPrimitive p && p.isNumber() ? p.getAsBigDecimal() : null;
                if (n == null || n.stripTrailingZeros().scale() > 0) {
                    problems.add(k.name() + ": not an integer");
                } else if (n.compareTo(BigDecimal.valueOf(k.min())) < 0
                        || n.compareTo(BigDecimal.valueOf(k.max())) > 0) {
                    problems.add(k.name() + ": " + n + " outside " + k.min() + ".." + k.max());
                } else {
                    value = n.intValueExact();
                }
            }
            v.add(value);
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return new WorldgenTuning(v);
    }
}
