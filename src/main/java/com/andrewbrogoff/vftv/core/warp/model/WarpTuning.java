package com.andrewbrogoff.vftv.core.warp.model;

import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

/**
 * The warp tuning keys, their ranges and defaults (warp spec, "Tuning and limits"), the strict
 * reader of {@code data/vftv/tuning/warp.json} with the milestone rules that need no research
 * ("Permanent warp"), and the one way to build a {@link WarpRules}.
 *
 * <p>The file's keys are flat and dotted, as {@code hush.json}'s. The record's own JSON form, which
 * its codec uses, adds {@code format} and the four server switches.
 */
public final class WarpTuning {
    /** The format of {@link WarpRules}. */
    public static final int FORMAT = 1;

    /** The milestone every tuning must list: the Eldritch tier's Joining choice. */
    public static final String JOINING = "vftv:joining";

    /** The four server switches' keys, in the record's order. */
    public static final List<String> SWITCHES =
            List.of("warp.voices.enabled", "warp.tax", "warp.amplifier", "warp.gear");

    private static final String SCHEMA = "vftv:warp_tuning/1";
    private static final int CAP = 10;
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** One integer key: its name, inclusive range, default and reader. */
    private record IntKey(
            String name, int min, int max, int fallback, ToIntFunction<WarpRules> get) {}

    private static final List<IntKey> INTS =
            List.of(
                    new IntKey(
                            "temporary.decay_interval",
                            20,
                            24_000,
                            400,
                            WarpRules::temporaryDecayInterval),
                    new IntKey("tax.per_warp_pm", 0, 100, 20, WarpRules::taxPerWarpPm),
                    new IntKey("tax.max_pm", 0, 3000, 500, WarpRules::taxMaxPm),
                    new IntKey("infusion.per_warp_pm", 0, 1000, 500, WarpRules::infusionPerWarpPm),
                    new IntKey("infusion.max_penalty", 0, 100, 20, WarpRules::infusionMaxPenalty),
                    new IntKey("check.interval", 200, 24_000, 1200, WarpRules::checkInterval),
                    new IntKey("counter.per_gain", 0, 100, 10, WarpRules::counterPerGain),
                    new IntKey("counter.per_event", 1, 1000, 30, WarpRules::counterPerEvent),
                    new IntKey("counter.max", 1, 10_000, 1000, WarpRules::counterMax),
                    new IntKey("counter.per_void_work", 0, 100, 5, WarpRules::counterPerVoidWork),
                    new IntKey("chance.base_pm", 0, 1000, 15, WarpRules::chanceBasePm),
                    new IntKey(
                            "chance.per_permanent_pm", 0, 1000, 5, WarpRules::chancePerPermanentPm),
                    new IntKey("chance.per_counter_pm", 0, 1000, 1, WarpRules::chancePerCounterPm),
                    new IntKey("chance.max_pm", 0, 1000, 250, WarpRules::chanceMaxPm),
                    new IntKey("grace", 0, 6000, 600, WarpRules::grace),
                    new IntKey("context.window", 0, 6000, 600, WarpRules::contextWindow),
                    new IntKey(
                            "joining.amplifier_bonus", 0, 50, 10, WarpRules::joiningAmplifierBonus),
                    new IntKey("gear.max", 0, 100, 12, WarpRules::gearMax),
                    new IntKey("bargain.ttl", 1200, 168_000, 12_000, WarpRules::bargainTtl),
                    new IntKey("listen.cooldown", 200, 168_000, 6000, WarpRules::listenCooldown),
                    new IntKey("still.amount", 1, 100, 4, WarpRules::stillAmount),
                    new IntKey("permanent.cap", 0, 100, CAP, WarpRules::permanentCap));

    private static final Schema.Int COST = new Schema.Int(0, 20);

    /** The file's keys, each optional. */
    private static final List<Schema.Field> FILE_FIELDS = fields(false);

    /** The record's form: every key required, with the format and the switches. */
    private static final List<Schema.Field> RECORD_FIELDS = fields(true);

    /** Every key at its default, with every switch on. */
    public static final WarpRules DEFAULTS = fromJson(new JsonObject(), false);

    private WarpTuning() {}

    /**
     * Reads a tuning file: strict JSON, one object whose every key is optional and defaults. The
     * switches are on; {@link #withSwitches} sets them.
     *
     * @throws IllegalArgumentException naming every problem: malformed JSON, an unknown key, a
     *     wrong type, a value out of range, or a broken milestone rule
     */
    public static WarpRules parse(String json) {
        JsonElement root;
        try {
            root = StrictJson.parse(json);
        } catch (StrictJson.MalformedJsonException e) {
            throw new IllegalArgumentException("malformed JSON: " + e.getMessage(), e);
        }
        if (!root.isJsonObject()) {
            throw new IllegalArgumentException("not a JSON object");
        }
        return fromJson(root.getAsJsonObject(), false);
    }

    /**
     * Reads a tuning object, or with {@code record} the record's own form, which also requires
     * {@code format} and the four switches.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static WarpRules fromJson(JsonObject json, boolean record) {
        JsonObject copy = json.deepCopy();
        copy.addProperty("schema", SCHEMA);
        Schema schema = new Schema(SCHEMA, record ? RECORD_FIELDS : FILE_FIELDS, WarpTuning::rules);
        List<String> problems = schema.validate(copy);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        int[] ints = new int[INTS.size()];
        for (int i = 0; i < ints.length; i++) {
            IntKey key = INTS.get(i);
            ints[i] = json.has(key.name()) ? json.get(key.name()).getAsInt() : key.fallback();
        }
        List<Integer> bands = new ArrayList<>(List.of(6, 14, 24));
        if (json.has("bands")) {
            bands.clear();
            json.getAsJsonArray("bands").forEach(b -> bands.add(b.getAsInt()));
        }
        List<String> dimensions = new ArrayList<>(List.of("vftv:vuulas"));
        if (json.has("context.dimensions")) {
            dimensions.clear();
            json.getAsJsonArray("context.dimensions").forEach(d -> dimensions.add(d.getAsString()));
        }
        int listenT = 0;
        int listenL = 2;
        if (json.has("listen.cost")) {
            JsonObject cost = json.getAsJsonObject("listen.cost");
            listenT = cost.has("temporary") ? cost.get("temporary").getAsInt() : 0;
            listenL = cost.has("lasting") ? cost.get("lasting").getAsInt() : 0;
        }
        Map<String, Integer> milestones = milestones(json);
        boolean[] on = {true, true, true, true};
        if (record) {
            for (int i = 0; i < on.length; i++) {
                on[i] = json.get(SWITCHES.get(i)).getAsBoolean();
            }
        }
        int k = 0;
        return new WarpRules(
                FORMAT,
                bands,
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                ints[k++],
                dimensions,
                ints[k++],
                ints[k++],
                ints[k++],
                listenT,
                listenL,
                ints[k++],
                ints[k++],
                ints[k],
                milestones,
                on[0],
                on[1],
                on[2],
                on[3]);
    }

    /**
     * The JSON of {@code rules}: every tuning key, and with {@code record} also {@code format} and
     * the switches. Reading it back with the same flag gives {@code rules}.
     */
    public static JsonObject toJson(WarpRules rules, boolean record) {
        JsonObject json = new JsonObject();
        JsonArray bands = new JsonArray();
        rules.bands().forEach(bands::add);
        json.add("bands", bands);
        INTS.forEach(k -> json.addProperty(k.name(), k.get().applyAsInt(rules)));
        JsonArray dimensions = new JsonArray();
        rules.contextDimensions().forEach(dimensions::add);
        json.add("context.dimensions", dimensions);
        JsonObject cost = new JsonObject();
        cost.addProperty("temporary", rules.listenCostTemporary());
        cost.addProperty("lasting", rules.listenCostLasting());
        json.add("listen.cost", cost);
        JsonObject milestones = new JsonObject();
        rules.permanentMilestones().forEach(milestones::addProperty);
        json.add("permanent.milestones", milestones);
        if (record) {
            json.addProperty("format", rules.format());
            boolean[] on = {rules.voicesEnabled(), rules.tax(), rules.amplifier(), rules.gear()};
            for (int i = 0; i < on.length; i++) {
                json.addProperty(SWITCHES.get(i), on[i]);
            }
        }
        return json;
    }

    /** {@code rules} with the four server switches replaced. */
    public static WarpRules withSwitches(
            WarpRules rules, boolean voices, boolean tax, boolean amplifier, boolean gear) {
        JsonObject json = toJson(rules, true);
        boolean[] on = {voices, tax, amplifier, gear};
        for (int i = 0; i < on.length; i++) {
            json.addProperty(SWITCHES.get(i), on[i]);
        }
        return fromJson(json, true);
    }

    private static List<Schema.Field> fields(boolean record) {
        List<Schema.Field> fields = new ArrayList<>();
        fields.add(
                new Schema.Field(
                        "bands", new Schema.ListOf(new Schema.Int(1, 1000), 3, 3, false), record));
        INTS.forEach(
                k ->
                        fields.add(
                                new Schema.Field(
                                        k.name(), new Schema.Int(k.min(), k.max()), record)));
        fields.add(
                new Schema.Field(
                        "context.dimensions",
                        new Schema.ListOf(new Schema.Id(), 0, 16, true),
                        record));
        fields.add(
                new Schema.Field(
                        "listen.cost",
                        new Schema.Obj(
                                List.of(
                                        new Schema.Field("temporary", COST, record),
                                        new Schema.Field("lasting", COST, record))),
                        record));
        fields.add(
                new Schema.Field(
                        "permanent.milestones",
                        new Schema.MapOf(new Schema.Str(1, 256, ID), new Schema.Int(1, 100), 0, 16),
                        record));
        if (record) {
            fields.add(Schema.required("format", new Schema.Int(FORMAT, FORMAT)));
            SWITCHES.forEach(s -> fields.add(Schema.required(s, new Schema.Bool())));
        }
        return List.copyOf(fields);
    }

    /** The rules beyond the field table: band order, the Listen rite's price, the milestones. */
    private static List<String> rules(JsonObject json) {
        List<String> problems = new ArrayList<>();
        if (json.has("bands")) {
            JsonArray bands = json.getAsJsonArray("bands");
            for (int i = 1; i < bands.size(); i++) {
                if (bands.get(i).getAsInt() <= bands.get(i - 1).getAsInt()) {
                    problems.add("$.bands: not strictly ascending");
                    break;
                }
            }
        }
        if (json.has("listen.cost")) {
            JsonObject cost = json.getAsJsonObject("listen.cost");
            int t = cost.has("temporary") ? cost.get("temporary").getAsInt() : 0;
            int l = cost.has("lasting") ? cost.get("lasting").getAsInt() : 0;
            if (t == 0 && l == 0) {
                problems.add("$.listen.cost: no kind above 0");
            }
        }
        Map<String, Integer> milestones = milestones(json);
        int cap = json.has("permanent.cap") ? json.get("permanent.cap").getAsInt() : CAP;
        int sum = milestones.values().stream().mapToInt(Integer::intValue).sum();
        if (sum > cap) {
            problems.add(
                    "$.permanent.milestones: amounts sum to "
                            + sum
                            + ", over permanent.cap "
                            + cap);
        }
        if (!milestones.containsKey(JOINING)) {
            problems.add("$.permanent.milestones: " + JOINING + " is not listed");
        }
        return problems;
    }

    private static Map<String, Integer> milestones(JsonObject json) {
        Map<String, Integer> milestones = new LinkedHashMap<>();
        if (json.has("permanent.milestones")) {
            json.getAsJsonObject("permanent.milestones")
                    .asMap()
                    .forEach((id, amount) -> milestones.put(id, amount.getAsInt()));
        } else {
            milestones.put("vftv:under_the_silence", 2);
            milestones.put("vftv:learning_the_tongue", 3);
            milestones.put(JOINING, 5);
        }
        return milestones;
    }
}
