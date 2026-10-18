package com.andrewbrogoff.vftv.core.crucible.model;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

/**
 * The crucible tuning, {@code data/vftv/tuning/crucible.json} (crucible spec, "Tuning and limits"):
 * every key optional, defaulting to the spec's value.
 *
 * @param capacity {@code pool.capacity}, 16-10,000
 * @param stepInterval {@code step.interval}, 1-40 ticks
 */
public record CrucibleTuning(int capacity, int stepInterval) {
    /** The spec's values. */
    public static final CrucibleTuning DEFAULTS = new CrucibleTuning(200, 5);

    private static final String CAPACITY = "pool.capacity";
    private static final String INTERVAL = "step.interval";

    /** Checks the ranges. */
    public CrucibleTuning {
        if (capacity < 16 || capacity > Pool.MAX_TOTAL) {
            throw new IllegalArgumentException(CAPACITY + ": " + capacity + " outside 16-10000");
        }
        if (stepInterval < 1 || stepInterval > 40) {
            throw new IllegalArgumentException(INTERVAL + ": " + stepInterval + " outside 1-40");
        }
    }

    /** The file's keys and values, as datagen writes them. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty(CAPACITY, capacity);
        json.addProperty(INTERVAL, stepInterval);
        return json;
    }

    /**
     * Reads a tuning file: strict JSON, one object, every key optional.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static CrucibleTuning parse(String text) {
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
            if (!key.equals(CAPACITY) && !key.equals(INTERVAL)) {
                problems.add(key + ": unknown key");
            }
        }
        int capacity = integer(object, CAPACITY, 16, Pool.MAX_TOTAL, DEFAULTS.capacity, problems);
        int interval = integer(object, INTERVAL, 1, 40, DEFAULTS.stepInterval, problems);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return new CrucibleTuning(capacity, interval);
    }

    private static int integer(
            JsonObject object, String key, int min, int max, int fallback, List<String> problems) {
        JsonElement value = object.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof JsonPrimitive p) || !p.isNumber()) {
            problems.add(key + ": not a number");
            return fallback;
        }
        double d = p.getAsDouble();
        if (d != Math.rint(d) || d < min || d > max) {
            problems.add(key + ": " + p.getAsString() + " is not an integer " + min + "-" + max);
            return fallback;
        }
        return (int) d;
    }
}
