package com.andrewbrogoff.vftv.core.research.model;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/**
 * The research tuning, {@code data/vftv/tuning/research.json} (research spec, "Tuning"): every key
 * optional, defaulting to the spec's value. A per-preset key is an object {@code {"gentle",
 * "standard", "hard"}}, each member optional.
 *
 * @param placeCost {@code place_cost}, 1-4: points per placement, spent at Learn
 * @param parentCost {@code combine.parent_cost}, 1-4: of each synthesis parent per unit
 * @param anchorBonusAt {@code grid.anchor_bonus_at}, 2-6: +1 radius at this many anchors used
 * @param maxAttempts {@code generate.max_attempts}, 1-64: walk attempts before the fallback
 * @param skipMultiplier {@code skip.multiplier}, 1.0-10.0: on the reference vector
 * @param gentle the gentle preset
 * @param standard the standard preset
 * @param hard the hard preset
 */
public record ResearchTuning(
        int placeCost,
        int parentCost,
        int anchorBonusAt,
        int maxAttempts,
        BigDecimal skipMultiplier,
        Preset gentle,
        Preset standard,
        Preset hard) {
    /** The spec's values. */
    public static final ResearchTuning DEFAULTS =
            new ResearchTuning(
                    1,
                    1,
                    4,
                    32,
                    new BigDecimal("1.5"),
                    new Preset(2, 3, 2, 0, Note.PaletteHelp.NEIGHBOURS),
                    new Preset(3, 5, 2, 0, Note.PaletteHelp.PARENTS),
                    new Preset(3, 5, 3, 5, Note.PaletteHelp.PARENTS));

    private static final BigDecimal MULTIPLIER_MIN = BigDecimal.ONE;
    private static final BigDecimal MULTIPLIER_MAX = BigDecimal.TEN;
    private static final List<String> PRESETS = List.of("gentle", "standard", "hard");
    private static final Set<String> KEYS =
            Set.of(
                    "place_cost",
                    "combine.parent_cost",
                    "grid.base_radius",
                    "grid.anchor_bonus_at",
                    "anchors.max_used",
                    "anchors.min_distance",
                    "silent_cells",
                    "palette_help",
                    "generate.max_attempts",
                    "skip.multiplier");

    /** Checks the ranges. */
    public ResearchTuning {
        check("place_cost", placeCost, 1, 4);
        check("combine.parent_cost", parentCost, 1, 4);
        check("grid.anchor_bonus_at", anchorBonusAt, 2, 6);
        check("generate.max_attempts", maxAttempts, 1, 64);
        if (skipMultiplier.compareTo(BigDecimal.ONE) < 0
                || skipMultiplier.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException(
                    "skip.multiplier: " + skipMultiplier + " outside 1.0-10.0");
        }
    }

    /**
     * One preset's numbers.
     *
     * @param baseRadius {@code grid.base_radius}, 1-7
     * @param maxUsed {@code anchors.max_used}, 2-5: the entry's first n anchors
     * @param minDistance {@code anchors.min_distance}, 1-6: hex steps between any two anchors
     * @param silentCells {@code silent_cells}, 0-12
     * @param paletteHelp {@code palette_help}
     */
    public record Preset(
            int baseRadius,
            int maxUsed,
            int minDistance,
            int silentCells,
            Note.PaletteHelp paletteHelp) {
        /** Checks the ranges. */
        public Preset {
            check("grid.base_radius", baseRadius, 1, 7);
            check("anchors.max_used", maxUsed, 2, 5);
            check("anchors.min_distance", minDistance, 1, 6);
            check("silent_cells", silentCells, 0, Note.MAX_SILENT);
        }
    }

    /** What a note begun under {@code preset} snapshots. */
    public Note.Snapshot snapshot(Preset preset) {
        return new Note.Snapshot(
                preset.minDistance(),
                preset.silentCells(),
                preset.paletteHelp(),
                placeCost,
                parentCost);
    }

    /** The file's keys and values, as datagen writes them. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("place_cost", placeCost);
        json.addProperty("combine.parent_cost", parentCost);
        json.add("grid.base_radius", perPreset(p -> new JsonPrimitive(p.baseRadius())));
        json.addProperty("grid.anchor_bonus_at", anchorBonusAt);
        json.add("anchors.max_used", perPreset(p -> new JsonPrimitive(p.maxUsed())));
        json.add("anchors.min_distance", perPreset(p -> new JsonPrimitive(p.minDistance())));
        json.add("silent_cells", perPreset(p -> new JsonPrimitive(p.silentCells())));
        json.add("palette_help", perPreset(p -> new JsonPrimitive(p.paletteHelp().id())));
        json.addProperty("generate.max_attempts", maxAttempts);
        json.addProperty("skip.multiplier", skipMultiplier);
        return json;
    }

    private JsonObject perPreset(Function<Preset, JsonElement> value) {
        JsonObject out = new JsonObject();
        out.add("gentle", value.apply(gentle));
        out.add("standard", value.apply(standard));
        out.add("hard", value.apply(hard));
        return out;
    }

    /**
     * Reads a tuning file: strict JSON, one object, every key optional.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static ResearchTuning parse(String text) {
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
        object.keySet().stream()
                .filter(k -> !KEYS.contains(k))
                .sorted()
                .forEach(k -> problems.add(k + ": unknown key"));
        ResearchTuning d = DEFAULTS;
        int placeCost =
                integer(object.get("place_cost"), "place_cost", 1, 4, d.placeCost, problems);
        int parentCost =
                integer(
                        object.get("combine.parent_cost"),
                        "combine.parent_cost",
                        1,
                        4,
                        d.parentCost,
                        problems);
        int bonusAt =
                integer(
                        object.get("grid.anchor_bonus_at"),
                        "grid.anchor_bonus_at",
                        2,
                        6,
                        d.anchorBonusAt,
                        problems);
        int attempts =
                integer(
                        object.get("generate.max_attempts"),
                        "generate.max_attempts",
                        1,
                        64,
                        d.maxAttempts,
                        problems);
        BigDecimal multiplier = multiplier(object.get("skip.multiplier"), problems);
        int[] radius = perPreset(object, "grid.base_radius", 1, 7, Preset::baseRadius, problems);
        int[] used = perPreset(object, "anchors.max_used", 2, 5, Preset::maxUsed, problems);
        int[] distance =
                perPreset(object, "anchors.min_distance", 1, 6, Preset::minDistance, problems);
        int[] silent =
                perPreset(
                        object, "silent_cells", 0, Note.MAX_SILENT, Preset::silentCells, problems);
        JsonObject help = presetObject(object, "palette_help", problems);
        Note.PaletteHelp[] helps = new Note.PaletteHelp[3];
        for (int i = 0; i < 3; i++) {
            helps[i] =
                    paletteHelp(
                            help == null ? null : help.get(PRESETS.get(i)),
                            "palette_help." + PRESETS.get(i),
                            d.presets().get(i).paletteHelp(),
                            problems);
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        Preset[] presets = new Preset[3];
        for (int i = 0; i < 3; i++) {
            presets[i] = new Preset(radius[i], used[i], distance[i], silent[i], helps[i]);
        }
        return new ResearchTuning(
                placeCost,
                parentCost,
                bonusAt,
                attempts,
                multiplier,
                presets[0],
                presets[1],
                presets[2]);
    }

    /** The three presets: gentle, standard, hard. */
    public List<Preset> presets() {
        return List.of(gentle, standard, hard);
    }

    private static int[] perPreset(
            JsonObject object,
            String key,
            int min,
            int max,
            ToIntFunction<Preset> fallback,
            List<String> problems) {
        JsonObject per = presetObject(object, key, problems);
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] =
                    integer(
                            per == null ? null : per.get(PRESETS.get(i)),
                            key + "." + PRESETS.get(i),
                            min,
                            max,
                            fallback.applyAsInt(DEFAULTS.presets().get(i)),
                            problems);
        }
        return out;
    }

    private static @Nullable JsonObject presetObject(
            JsonObject object, String key, List<String> problems) {
        JsonElement value = object.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof JsonObject per)) {
            problems.add(key + ": not an object of gentle, standard and hard");
            return null;
        }
        per.keySet().stream()
                .filter(k -> !PRESETS.contains(k))
                .sorted()
                .forEach(k -> problems.add(key + "." + k + ": unknown preset"));
        return per;
    }

    private static int integer(
            @Nullable JsonElement value,
            String key,
            int min,
            int max,
            int fallback,
            List<String> problems) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof JsonPrimitive p) || !p.isNumber()) {
            problems.add(key + ": not a number");
            return fallback;
        }
        BigDecimal d = p.getAsBigDecimal();
        if (d.stripTrailingZeros().scale() > 0
                || d.compareTo(BigDecimal.valueOf(min)) < 0
                || d.compareTo(BigDecimal.valueOf(max)) > 0) {
            problems.add(key + ": " + p.getAsString() + " is not an integer " + min + "-" + max);
            return fallback;
        }
        return d.intValueExact();
    }

    private static BigDecimal multiplier(@Nullable JsonElement value, List<String> problems) {
        if (value == null) {
            return DEFAULTS.skipMultiplier;
        }
        if (!(value instanceof JsonPrimitive p) || !p.isNumber()) {
            problems.add("skip.multiplier: not a number");
            return DEFAULTS.skipMultiplier;
        }
        BigDecimal d = p.getAsBigDecimal();
        if (d.compareTo(MULTIPLIER_MIN) < 0 || d.compareTo(MULTIPLIER_MAX) > 0) {
            problems.add("skip.multiplier: " + p.getAsString() + " outside 1.0-10.0");
            return DEFAULTS.skipMultiplier;
        }
        return d;
    }

    private static Note.PaletteHelp paletteHelp(
            @Nullable JsonElement value,
            String key,
            Note.PaletteHelp fallback,
            List<String> problems) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof JsonPrimitive p && p.isString()) {
            for (Note.PaletteHelp h : Note.PaletteHelp.values()) {
                if (h.id().equals(p.getAsString())) {
                    return h;
                }
            }
        }
        problems.add(key + ": not neighbours or parents");
        return fallback;
    }

    private static void check(String name, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "%s: %d outside %d-%d", name, value, min, max));
        }
    }
}
