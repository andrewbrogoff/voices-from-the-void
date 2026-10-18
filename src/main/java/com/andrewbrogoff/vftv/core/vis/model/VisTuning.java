package com.andrewbrogoff.vftv.core.vis.model;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The vis tuning, {@code data/vftv/tuning/vis.json} (wands spec, "Tuning"): every key optional,
 * defaulting to the spec's value; an unknown key, a wrong type or a value out of range is invalid.
 * Values are held by key, in the spec's table order; integers as {@code long}, decimals as {@code
 * double}.
 *
 * @param values every key's value, in {@link #KEYS} order
 */
public record VisTuning(Map<String, Number> values) {
    /** One key: its name, whether it is an integer, its range and its default. */
    public record Key(String name, boolean integer, double min, double max, double fallback) {}

    /** Every key, in the spec's order. */
    public static final List<Key> KEYS =
            List.of(
                    i("tap.interval", 1, 40, 4),
                    i("tap.amount", 1, 64, 2),
                    i("regen.interval.faint", 1, 24000, 160),
                    i("regen.interval.steady", 1, 24000, 80),
                    i("regen.interval.clear", 1, 24000, 40),
                    i("regen.divisor", 1, 1000, 20),
                    d("relay.range", 2.0, 32.0, 10.0),
                    i("relay.pulse", 1, 200, 20),
                    i("relay.rate", 1, 1000, 4),
                    i("cultivate.per_max", 1, 64, 4),
                    i("cultivate.clarity_cost", 1, 4096, 48),
                    i("cultivate.max_capacity", 1, 1000, 250),
                    i("cultivate.suction", 0, 128, 16),
                    d("node.min_spacing", 1.0, 16.0, 3.0),
                    d("quote.max_discount", 0.0, 0.9, 0.30),
                    d("quote.min_multiplier", 0.1, 1.0, 0.50),
                    d("quote.max_multiplier", 1.0, 10.0, 4.00),
                    i("starter.min_ring", 1, 64, 8),
                    i("starter.ring", 1, 64, 24),
                    i("starter.height", 2, 5, 3));

    /** The spec's values. */
    public static final VisTuning DEFAULTS = defaults();

    /** Copies the values, which must hold exactly the keys. */
    public VisTuning {
        Map<String, Number> copy = new LinkedHashMap<>();
        for (Key key : KEYS) {
            Number n = values.get(key.name());
            if (n == null) {
                throw new IllegalArgumentException(key.name() + ": missing");
            }
            copy.put(key.name(), n);
        }
        if (values.size() != KEYS.size()) {
            throw new IllegalArgumentException("unknown key");
        }
        values = java.util.Collections.unmodifiableMap(copy);
    }

    private static Key i(String name, long min, long max, long fallback) {
        return new Key(name, true, min, max, fallback);
    }

    private static Key d(String name, double min, double max, double fallback) {
        return new Key(name, false, min, max, fallback);
    }

    private static VisTuning defaults() {
        Map<String, Number> m = new LinkedHashMap<>();
        for (Key key : KEYS) {
            m.put(key.name(), key.integer() ? (Number) (long) key.fallback() : key.fallback());
        }
        return new VisTuning(m);
    }

    /** An integer key's value. */
    public int integer(String key) {
        return number(key).intValue();
    }

    /** A decimal key's value. */
    public double decimal(String key) {
        return number(key).doubleValue();
    }

    private Number number(String key) {
        Number n = values.get(key);
        if (n == null) {
            throw new IllegalArgumentException("no tuning key " + key);
        }
        return n;
    }

    /** {@code tap.interval}. */
    public int tapInterval() {
        return integer("tap.interval");
    }

    /** {@code tap.amount}. */
    public int tapAmount() {
        return integer("tap.amount");
    }

    /** The regeneration interval of a clarity (0 faint, 1 steady, 2 clear). */
    public int regenInterval(int clarity) {
        return integer(
                switch (clarity) {
                    case 0 -> "regen.interval.faint";
                    case 1 -> "regen.interval.steady";
                    default -> "regen.interval.clear";
                });
    }

    /** {@code regen.divisor}. */
    public int regenDivisor() {
        return integer("regen.divisor");
    }

    /** {@code node.min_spacing}. */
    public double minSpacing() {
        return decimal("node.min_spacing");
    }

    /** The file's keys and values, in key order, as datagen writes them. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        values.forEach(json::addProperty);
        return json;
    }

    /**
     * Reads a tuning file: strict JSON, one object, every key optional.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static VisTuning parse(String text) {
        JsonElement root;
        try {
            root = StrictJson.parse(text);
        } catch (StrictJson.MalformedJsonException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof JsonObject object)) {
            throw new IllegalArgumentException("not a JSON object");
        }
        return fromJson(object, false);
    }

    /**
     * Reads a JSON object of tuning keys. With {@code complete}, every key is required (the rules
     * record's wire form); otherwise a missing key takes its default. Other keys are refused unless
     * {@code extra} lists them.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static VisTuning fromJson(JsonObject object, boolean complete, String... extra) {
        List<String> problems = new ArrayList<>();
        List<String> allowed = List.of(extra);
        for (String key : object.keySet()) {
            if (!DEFAULTS.values.containsKey(key) && !allowed.contains(key)) {
                problems.add(key + ": unknown key");
            }
        }
        Map<String, Number> m = new LinkedHashMap<>();
        for (Key key : KEYS) {
            JsonElement value = object.get(key.name());
            Number fallback = DEFAULTS.values.get(key.name());
            if (value == null) {
                if (complete) {
                    problems.add(key.name() + ": missing");
                }
                m.put(key.name(), fallback);
                continue;
            }
            BigDecimal n =
                    value instanceof JsonPrimitive p && p.isNumber() ? p.getAsBigDecimal() : null;
            if (n == null) {
                problems.add(key.name() + ": not a number");
                m.put(key.name(), fallback);
                continue;
            }
            if (key.integer() && n.stripTrailingZeros().scale() > 0) {
                problems.add(key.name() + ": not an integer");
                m.put(key.name(), fallback);
                continue;
            }
            if (n.compareTo(BigDecimal.valueOf(key.min())) < 0
                    || n.compareTo(BigDecimal.valueOf(key.max())) > 0) {
                problems.add(key.name() + ": " + n + " out of range");
                m.put(key.name(), fallback);
                continue;
            }
            m.put(key.name(), key.integer() ? (Number) n.longValueExact() : n.doubleValue());
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return new VisTuning(m);
    }
}
