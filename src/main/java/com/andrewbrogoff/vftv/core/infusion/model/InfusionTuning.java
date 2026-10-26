package com.andrewbrogoff.vftv.core.infusion.model;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The infusion tuning, {@code data/vftv/tuning/infusion.json} (infusion spec, "Tuning and limits"):
 * every key optional, defaulting to the spec's value. An unknown key, a wrong type, a value out of
 * range, {@code upset.jolt_weight + upset.spill_weight = 0} or {@code upset.max × upset.spill >
 * 10,000} makes the file invalid.
 *
 * @param values every value, in {@link #KEYS} order
 */
public record InfusionTuning(List<Integer> values) {
    /** One key: its name, inclusive range and default. */
    public record Key(String name, int min, int max, int fallback) {}

    /** Every key, in key order. */
    public static final List<Key> KEYS =
            List.of(
                    new Key("run.interval", 1, 200, 10),
                    new Key("run.rate", 1, 1000, 4),
                    new Key("symmetry.lone_pedestal", 0, 100, 6),
                    new Key("symmetry.lone_item", 0, 100, 4),
                    new Key("stabilise.per_pair", 0, 100, 3),
                    new Key("stabilise.max_pairs", 0, 64, 8),
                    new Key("upset.max_chance", 0, 1000, 150),
                    new Key("upset.max", 0, 64, 6),
                    new Key("upset.spill", 1, 1000, 50),
                    new Key("upset.jolt_weight", 0, 100, 1),
                    new Key("upset.spill_weight", 0, 100, 1),
                    new Key("enchant.instability_per_level", 0, 100, 5),
                    new Key("arm.ttl", 20, 12_000, 1200),
                    new Key("arm.refresh", 1, 200, 10),
                    new Key("abandon.window", 20, 1200, 100));

    /** The spec's values. */
    public static final InfusionTuning DEFAULTS =
            new InfusionTuning(KEYS.stream().map(Key::fallback).toList());

    /** Checks every range and the two cross-key rules. */
    public InfusionTuning {
        values = List.copyOf(values);
        if (values.size() != KEYS.size()) {
            throw new IllegalArgumentException(values.size() + " values, not " + KEYS.size());
        }
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Key k = KEYS.get(i);
            int v = values.get(i);
            if (v < k.min() || v > k.max()) {
                problems.add(k.name() + ": " + v + " outside " + k.min() + ".." + k.max());
            }
        }
        if (problems.isEmpty()) {
            problems.addAll(
                    RunRules.crossProblems(
                            values.get(7), values.get(8), values.get(9), values.get(10)));
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    /** The value of the key named {@code name}. */
    public int get(String name) {
        for (int i = 0; i < KEYS.size(); i++) {
            if (KEYS.get(i).name().equals(name)) {
                return values.get(i);
            }
        }
        throw new IllegalArgumentException("no infusion tuning key " + name);
    }

    /** {@code run.interval}. */
    public int interval() {
        return values.get(0);
    }

    /** {@code enchant.instability_per_level}. */
    public int instabilityPerLevel() {
        return values.get(11);
    }

    /** {@code arm.ttl}. */
    public int armTtl() {
        return values.get(12);
    }

    /** {@code arm.refresh}. */
    public int armRefresh() {
        return values.get(13);
    }

    /** {@code abandon.window}. */
    public int abandonWindow() {
        return values.get(14);
    }

    /** The run-affecting values a commit freezes into the plan. */
    public RunRules runRules() {
        return new RunRules(
                values.get(0),
                values.get(1),
                values.get(2),
                values.get(3),
                values.get(4),
                values.get(5),
                values.get(6),
                values.get(7),
                values.get(8),
                values.get(9),
                values.get(10));
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
    public static InfusionTuning parse(String text) {
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
                    problems.add(
                            k.name()
                                    + ": "
                                    + n.toPlainString()
                                    + " outside "
                                    + k.min()
                                    + ".."
                                    + k.max());
                } else {
                    value = n.intValueExact();
                }
            }
            v.add(value);
        }
        if (problems.isEmpty()) {
            try {
                return new InfusionTuning(v);
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        throw new IllegalArgumentException(String.join("; ", problems));
    }
}
