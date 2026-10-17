package com.andrewbrogoff.vftv.core.essentia.model;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The essentia tuning, {@code data/vftv/tuning/essentia.json} (essentia spec, "Tuning and limits"):
 * every key optional, defaulting to the spec's value. An unknown key, a wrong type, a value out of
 * range, or a {@code suction.jar} not below {@code suction.jar_labelled} makes the file invalid.
 *
 * @param jarCapacity units per jar
 * @param suctionJar the unlabelled jar's rank
 * @param suctionJarLabelled the labelled jar's rank
 * @param tubePulse ticks between a line's pulses
 * @param tubeRate most units per move
 * @param tubeIdleEvery a sleeping line's check interval, in pulses
 * @param alembicInterval ticks between pours
 * @param alembicRate most units per pour
 */
public record EssentiaTuning(
        int jarCapacity,
        int suctionJar,
        int suctionJarLabelled,
        int tubePulse,
        int tubeRate,
        int tubeIdleEvery,
        int alembicInterval,
        int alembicRate) {
    /** One key: its name, inclusive range and default. */
    public record Key(String name, int min, int max, int fallback) {}

    /** Every key, in key order. */
    public static final List<Key> KEYS =
            List.of(
                    new Key("jar.capacity", 16, 10_000, 100),
                    new Key("suction.jar", 1, 15, 4),
                    new Key("suction.jar_labelled", 1, 15, 8),
                    new Key("tube.pulse", 1, 200, 10),
                    new Key("tube.rate", 1, 1000, 8),
                    new Key("tube.idle_every", 1, 100, 10),
                    new Key("alembic.interval", 1, 200, 20),
                    new Key("alembic.rate", 1, 1000, 4));

    /** The spec's values. */
    public static final EssentiaTuning DEFAULTS =
            of(KEYS.stream().mapToInt(Key::fallback).toArray());

    /** Checks every range and the jar-rank ordering. */
    public EssentiaTuning {
        int[] v = {
            jarCapacity,
            suctionJar,
            suctionJarLabelled,
            tubePulse,
            tubeRate,
            tubeIdleEvery,
            alembicInterval,
            alembicRate
        };
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < v.length; i++) {
            Key k = KEYS.get(i);
            if (v[i] < k.min() || v[i] > k.max()) {
                problems.add(k.name() + ": " + v[i] + " outside " + k.min() + ".." + k.max());
            }
        }
        if (problems.isEmpty() && suctionJar >= suctionJarLabelled) {
            problems.add("suction.jar must be below suction.jar_labelled");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    /** The tuning holding these values, in key order. */
    public static EssentiaTuning of(int[] v) {
        if (v.length != KEYS.size()) {
            throw new IllegalArgumentException(v.length + " values");
        }
        return new EssentiaTuning(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
    }

    /** The values, in key order. */
    public int[] values() {
        return new int[] {
            jarCapacity,
            suctionJar,
            suctionJarLabelled,
            tubePulse,
            tubeRate,
            tubeIdleEvery,
            alembicInterval,
            alembicRate
        };
    }

    /** A sleeping line's delay in ticks: {@code tube.pulse × tube.idle_every}. */
    public int tubeIdleTicks() {
        return tubePulse * tubeIdleEvery;
    }

    /** The file's keys and values, in key order, as datagen writes them. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        int[] v = values();
        for (int i = 0; i < v.length; i++) {
            json.addProperty(KEYS.get(i).name(), v[i]);
        }
        return json;
    }

    /**
     * Reads a tuning file: strict JSON, one object, every key optional.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static EssentiaTuning parse(String text) {
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
        int[] v = new int[KEYS.size()];
        for (int i = 0; i < v.length; i++) {
            Key k = KEYS.get(i);
            v[i] = k.fallback();
            JsonElement value = object.get(k.name());
            if (value == null) {
                continue;
            }
            BigDecimal n =
                    value instanceof JsonPrimitive p && p.isNumber() ? p.getAsBigDecimal() : null;
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
                v[i] = n.intValueExact();
            }
        }
        if (problems.isEmpty()) {
            try {
                return of(v);
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        throw new IllegalArgumentException(String.join("; ", problems));
    }
}
