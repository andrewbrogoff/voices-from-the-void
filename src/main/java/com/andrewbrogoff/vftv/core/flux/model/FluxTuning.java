package com.andrewbrogoff.vftv.core.flux.model;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The flux and Hush tuning keys, their ranges and defaults (Hush spec, "Tuning and limits"), the
 * strict reader of {@code data/vftv/tuning/hush.json}, and the one way to build a {@link
 * FluxRules}: from its field values, in key order, with its generation folded from them.
 */
public final class FluxTuning {
    /** The format of {@link FluxRules}. */
    public static final int FORMAT = 1;

    /**
     * One integer field: its name, inclusive range and default.
     *
     * @param name the tuning key, or the record field's name
     */
    public record Field(String name, int min, int max, int fallback) {}

    /** The tuning keys, in key order: every key of {@code hush.json}. */
    public static final List<Field> KEYS =
            List.of(
                    new Field("flux.essentia_pm", 0, 1000, 1000),
                    new Field("flux.tax_pm", 0, 1000, 1000),
                    new Field("flux.void_pm", 0, 1000, 250),
                    new Field("hush.onset", 0, 100_000, 50),
                    new Field("hush.block_cost", 1, 10_000, 2),
                    new Field("hush.node_cost", 1, 100_000, 50),
                    new Field("hush.interval", 1, 1200, 10),
                    new Field("hush.walk", 1, 64, 32),
                    new Field("hush.radius", 1, 16, 12),
                    new Field("hush.max_misses", 1, 255, 32),
                    new Field("hush.player_chunks", 1, 32, 8),
                    new Field("hollow.every", 1, 1000, 8),
                    new Field("hollow.flux", 0, 100_000, 25),
                    new Field("hollow.max_near", 0, 16, 3),
                    new Field("hollow.min_player_distance", 0, 32, 8),
                    new Field("bell.flux", 0, 100_000, 50),
                    new Field("bell.restore", 0, 64, 8),
                    new Field("bell.radius", 1, 8, 5),
                    new Field("bell.cooldown", 0, 1200, 40),
                    new Field("peal.interval", 20, 24_000, 200),
                    new Field("peal.flux", 0, 100_000, 50),
                    new Field("peal.restore", 0, 64, 8),
                    new Field("peal.radius", 1, 16, 12),
                    new Field("peal.scan", 1, 4096, 1024),
                    new Field("peal.capacity", 1, 1000, 64),
                    new Field("peal.suction", 0, 128, 16));

    /**
     * Every field of {@link FluxRules} but its generation, in order: the format, the tuning keys,
     * then the three server switches as 0 or 1. The record's codecs carry exactly these, and the
     * generation folds them.
     */
    public static final List<Field> FIELDS;

    static {
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("format", FORMAT, FORMAT, FORMAT));
        fields.addAll(KEYS);
        fields.add(new Field("hush.spread", 0, 1, 1));
        fields.add(new Field("hush.hollows", 0, 1, 1));
        fields.add(new Field("hush.unattended", 0, 1, 0));
        FIELDS = List.copyOf(fields);
    }

    private static final Map<String, Integer> KEY_INDEX =
            IntStream.range(0, KEYS.size())
                    .boxed()
                    .collect(Collectors.toUnmodifiableMap(i -> KEYS.get(i).name(), i -> i));

    private FluxTuning() {}

    /** Every tuning key's default, in key order. */
    public static int[] defaults() {
        return KEYS.stream().mapToInt(Field::fallback).toArray();
    }

    /**
     * Reads a tuning file: strict JSON, one object whose every key is optional and defaults.
     *
     * @return the tuning values, in key order
     * @throws IllegalArgumentException naming every problem: malformed JSON, not an object, an
     *     unknown key, a value that isn't an integer, or a value out of range
     */
    public static int[] parse(String json) {
        JsonElement root;
        try {
            root = StrictJson.parse(json);
        } catch (StrictJson.MalformedJsonException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage(), e);
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("not a JSON object");
        }
        int[] values = defaults();
        List<String> problems = new ArrayList<>();
        root.getAsJsonObject()
                .asMap()
                .forEach(
                        (key, value) -> {
                            Integer index = KEY_INDEX.get(key);
                            if (index == null) {
                                problems.add(key + ": unknown key");
                                return;
                            }
                            Field field = KEYS.get(index);
                            BigDecimal number =
                                    value instanceof JsonPrimitive p && p.isNumber()
                                            ? p.getAsBigDecimal()
                                            : null;
                            if (number == null || number.stripTrailingZeros().scale() > 0) {
                                problems.add(key + ": not an integer");
                            } else if (number.compareTo(BigDecimal.valueOf(field.min())) < 0
                                    || number.compareTo(BigDecimal.valueOf(field.max())) > 0) {
                                problems.add(
                                        key
                                                + ": "
                                                + number
                                                + " outside "
                                                + field.min()
                                                + ".."
                                                + field.max());
                            } else {
                                values[index] = number.intValueExact();
                            }
                        });
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        return values;
    }

    /** The rules for tuning values in key order and the three server switches. */
    public static FluxRules rules(
            int[] tuning, boolean spread, boolean hollows, boolean unattended) {
        if (tuning.length != KEYS.size()) {
            throw new IllegalArgumentException(tuning.length + " tuning values");
        }
        int[] values = new int[FIELDS.size()];
        values[0] = FORMAT;
        System.arraycopy(tuning, 0, values, 1, tuning.length);
        values[values.length - 3] = spread ? 1 : 0;
        values[values.length - 2] = hollows ? 1 : 0;
        values[values.length - 1] = unattended ? 1 : 0;
        return fromValues(values);
    }

    /**
     * The rules holding {@link #FIELDS}' values, with their generation.
     *
     * @throws IllegalArgumentException if there are not exactly those fields or one is out of range
     */
    public static FluxRules fromValues(int[] v) {
        if (v.length != FIELDS.size()) {
            throw new IllegalArgumentException(v.length + " fields");
        }
        for (int i = 0; i < v.length; i++) {
            Field field = FIELDS.get(i);
            if (v[i] < field.min() || v[i] > field.max()) {
                throw new IllegalArgumentException(
                        field.name()
                                + ": "
                                + v[i]
                                + " outside "
                                + field.min()
                                + ".."
                                + field.max());
            }
        }
        long[] words = new long[v.length + 1];
        words[0] = Salts.word(Salts.FLUX);
        for (int i = 0; i < v.length; i++) {
            words[i + 1] = v[i];
        }
        return new FluxRules(
                v[0],
                Hashes.fold(words),
                v[1],
                v[2],
                v[3],
                v[4],
                v[5],
                v[6],
                v[7],
                v[8],
                v[9],
                v[10],
                v[11],
                v[12],
                v[13],
                v[14],
                v[15],
                v[16],
                v[17],
                v[18],
                v[19],
                v[20],
                v[21],
                v[22],
                v[23],
                v[24],
                v[25],
                v[26],
                v[27] == 1,
                v[28] == 1,
                v[29] == 1);
    }

    /** {@link #FIELDS}' values of {@code rules}, in order. */
    public static int[] values(FluxRules r) {
        return new int[] {
            r.format(),
            r.fluxEssentiaPm(),
            r.fluxTaxPm(),
            r.fluxVoidPm(),
            r.hushOnset(),
            r.hushBlockCost(),
            r.hushNodeCost(),
            r.hushInterval(),
            r.hushWalk(),
            r.hushRadius(),
            r.hushMaxMisses(),
            r.hushPlayerChunks(),
            r.hollowEvery(),
            r.hollowFlux(),
            r.hollowMaxNear(),
            r.hollowMinPlayerDistance(),
            r.bellFlux(),
            r.bellRestore(),
            r.bellRadius(),
            r.bellCooldown(),
            r.pealInterval(),
            r.pealFlux(),
            r.pealRestore(),
            r.pealRadius(),
            r.pealScan(),
            r.pealCapacity(),
            r.pealSuction(),
            r.spread() ? 1 : 0,
            r.hollows() ? 1 : 0,
            r.unattended() ? 1 : 0
        };
    }
}
