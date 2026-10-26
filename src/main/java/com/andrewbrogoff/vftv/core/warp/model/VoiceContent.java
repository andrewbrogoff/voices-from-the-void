package com.andrewbrogoff.vftv.core.warp.model;

import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The Voices' authored content and data (warp spec, "Authored content"): the {@code
 * vftv:voice_event} and {@code vftv:bargain} entries as the registries load them, and every content
 * schema with its "Rejected:" rules, the lexicon's phonology and the Voice line script's included.
 * Pure: datagen, the registry codecs and the unit tests share it.
 */
public final class VoiceContent {
    /** {@code vftv:whisper}: a Voice line from the band's pool. */
    public static final String WHISPER = "vftv:whisper";

    /** {@code vftv:insight}: points of a known aspect. */
    public static final String INSIGHT = "vftv:insight";

    /** {@code vftv:bargain}: an offer. */
    public static final String BARGAIN = "vftv:bargain";

    /** {@code vftv:sound}: a phantom sound. */
    public static final String SOUND = "vftv:sound";

    /** {@code vftv:colour}: the colour drain. */
    public static final String COLOUR = "vftv:colour";

    /** {@code vftv:phantom_mob}: an apparition. */
    public static final String PHANTOM_MOB = "vftv:phantom_mob";

    /** {@code vftv:status}: effects, in context only. */
    public static final String STATUS = "vftv:status";

    /** The {@code vftv:voice_event_type} code registry, in its config keys' order. */
    public static final List<String> TYPES =
            List.of(WHISPER, INSIGHT, SOUND, COLOUR, PHANTOM_MOB, BARGAIN, STATUS);

    /** The one bargain type, {@code vftv:effect}. */
    public static final String EFFECT = "vftv:effect";

    /** The Voice line pools and how many lines each holds. */
    public static final Map<String, Integer> POOLS = pools();

    /** The longest Voice line text, in code points. */
    public static final int MAX_LINE = 80;

    /** The shipped Voice events, exactly. */
    public static final String SHIPPED_EVENTS =
            """
            {"vftv:whisper":       {"type": "vftv:whisper",       "weight": 40},
             "vftv:insight":       {"type": "vftv:insight",       "weight": 15, "points": 3},
             "vftv:footsteps":     {"type": "vftv:sound",         "weight": 15, "min_band": 1},
             "vftv:drained":       {"type": "vftv:colour",        "weight": 10, "min_band": 1},
             "vftv:offer":         {"type": "vftv:bargain",       "weight": 8,  "min_band": 1},
             "vftv:watcher":       {"type": "vftv:phantom_mob",   "weight": 6,  "min_band": 2},
             "vftv:pressing_dark": {"type": "vftv:status",        "weight": 10, "min_band": 2,
                                    "context": true, "effects": [{"effect": "minecraft:slowness",
                                    "amplifier": 0, "duration": 100}]}}
            """;

    /** The shipped bargains, exactly. */
    public static final String SHIPPED_BARGAINS =
            """
            {"vftv:keen_eyes":   {"type": "vftv:effect", "weight": 30, "cost": {"temporary": 3},
                                  "effect": "minecraft:night_vision", "amplifier": 0,
                                  "duration": 6000},
             "vftv:quick_hands": {"type": "vftv:effect", "weight": 20,
                                  "cost": {"temporary": 2, "lasting": 1},
                                  "effect": "minecraft:haste", "amplifier": 1, "duration": 3600}}
            """;

    /** The Stilling Draught's body, exactly. */
    public static final String STILLING_BODY =
            """
            {"type": "vftv:crucible", "research": "vftv:being_heard",
             "catalyst": "minecraft:glass_bottle",
             "aspects": {"vftv:silentium": 6, "vftv:ordo": 4, "vftv:aqua": 4},
             "result": {"id": "vftv:stilling_draught", "count": 1}}
            """;

    /** The Stilling Draught's recipe id. */
    public static final String STILLING = "vftv:stilling_draught";

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Schema.Str TEXT = new Schema.Str(1, 256, null);

    /**
     * One {@code vftv:status} effect.
     *
     * @param effect the mob effect id
     * @param amplifier 0–4
     * @param duration 20–600 ticks
     */
    public record Effect(String effect, int amplifier, int duration) {}

    /**
     * One Voice event.
     *
     * @param type its {@code vftv:voice_event_type}
     * @param weight 1–1000
     * @param minBand 0–3
     * @param maxBand {@code minBand}–3
     * @param context eligible only in context
     * @param points an insight's points, else 0
     * @param effects a status event's effects, else empty
     */
    public record Event(
            String type,
            int weight,
            int minBand,
            int maxBand,
            boolean context,
            int points,
            List<Effect> effects) {
        /** Copies the effects. */
        public Event {
            effects = List.copyOf(effects);
        }
    }

    /**
     * One bargain, of the one type {@code vftv:effect}.
     *
     * @param weight 1–1000
     * @param costTemporary 0–20
     * @param costLasting 0–20, at least one of the two above 0
     * @param effect the mob effect id
     * @param amplifier 0–2
     * @param duration 200–24,000 ticks
     */
    public record Bargain(
            int weight,
            int costTemporary,
            int costLasting,
            String effect,
            int amplifier,
            int duration) {}

    private static final Schema.Obj EFFECT_FIELDS =
            new Schema.Obj(
                    List.of(
                            Schema.required("effect", new Schema.Id()),
                            Schema.required("amplifier", new Schema.Int(0, 4)),
                            Schema.required("duration", new Schema.Int(20, 600))));

    private static final List<Schema.Field> EVENT_FIELDS =
            List.of(
                    Schema.required("type", new Schema.Id()),
                    Schema.required("weight", new Schema.Int(1, 1000)),
                    Schema.optional("min_band", new Schema.Int(0, 3)),
                    Schema.optional("max_band", new Schema.Int(0, 3)),
                    Schema.optional("context", new Schema.Bool()),
                    Schema.optional("points", new Schema.Int(1, 64)),
                    Schema.optional("effects", new Schema.ListOf(EFFECT_FIELDS, 1, 3, false)));

    private static final Schema.Obj COST =
            new Schema.Obj(
                    List.of(
                            Schema.optional("temporary", new Schema.Int(0, 20)),
                            Schema.optional("lasting", new Schema.Int(0, 20))));

    private static final List<Schema.Field> BARGAIN_FIELDS =
            List.of(
                    Schema.required("type", new Schema.Id()),
                    Schema.required("weight", new Schema.Int(1, 1000)),
                    Schema.required("cost", COST),
                    Schema.required("effect", new Schema.Id()),
                    Schema.required("amplifier", new Schema.Int(0, 2)),
                    Schema.required("duration", new Schema.Int(200, 24_000)));

    private static final Schema EVENT =
            new Schema("vftv:voice_event/1", EVENT_FIELDS, VoiceContent::eventRules);
    private static final Schema BARGAIN_ENTRY =
            new Schema("vftv:bargain/1", BARGAIN_FIELDS, VoiceContent::bargainRules);

    private VoiceContent() {}

    // ---- Registry entries -------------------------------------------------------------------

    /**
     * Reads one {@code vftv:voice_event} entry.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static Event event(JsonObject json) {
        throwIfAny(entryProblems(EVENT, json));
        int min = optInt(json, "min_band", 0);
        List<Effect> effects = new ArrayList<>();
        if (json.has("effects")) {
            for (JsonElement e : json.getAsJsonArray("effects")) {
                JsonObject o = e.getAsJsonObject();
                effects.add(
                        new Effect(
                                o.get("effect").getAsString(),
                                o.get("amplifier").getAsInt(),
                                o.get("duration").getAsInt()));
            }
        }
        return new Event(
                json.get("type").getAsString(),
                json.get("weight").getAsInt(),
                min,
                optInt(json, "max_band", 3),
                json.has("context") && json.get("context").getAsBoolean(),
                optInt(json, "points", 0),
                effects);
    }

    /**
     * Reads one {@code vftv:bargain} entry.
     *
     * @throws IllegalArgumentException naming every problem
     */
    public static Bargain bargain(JsonObject json) {
        throwIfAny(entryProblems(BARGAIN_ENTRY, json));
        JsonObject cost = json.getAsJsonObject("cost");
        return new Bargain(
                json.get("weight").getAsInt(),
                optInt(cost, "temporary", 0),
                optInt(cost, "lasting", 0),
                json.get("effect").getAsString(),
                json.get("amplifier").getAsInt(),
                json.get("duration").getAsInt());
    }

    /** An event's or a bargain's JSON form, as datagen writes it and the codec re-encodes it. */
    public static JsonObject toJson(Event e) {
        JsonObject json = new JsonObject();
        json.addProperty("type", e.type());
        json.addProperty("weight", e.weight());
        if (e.minBand() != 0) {
            json.addProperty("min_band", e.minBand());
        }
        if (e.maxBand() != 3) {
            json.addProperty("max_band", e.maxBand());
        }
        if (e.context()) {
            json.addProperty("context", true);
        }
        if (e.type().equals(INSIGHT)) {
            json.addProperty("points", e.points());
        }
        if (e.type().equals(STATUS)) {
            JsonArray effects = new JsonArray();
            for (Effect f : e.effects()) {
                JsonObject o = new JsonObject();
                o.addProperty("effect", f.effect());
                o.addProperty("amplifier", f.amplifier());
                o.addProperty("duration", f.duration());
                effects.add(o);
            }
            json.add("effects", effects);
        }
        return json;
    }

    /** A bargain's JSON form. */
    public static JsonObject toJson(Bargain b) {
        JsonObject json = new JsonObject();
        json.addProperty("type", EFFECT);
        json.addProperty("weight", b.weight());
        JsonObject cost = new JsonObject();
        if (b.costTemporary() > 0) {
            cost.addProperty("temporary", b.costTemporary());
        }
        if (b.costLasting() > 0) {
            cost.addProperty("lasting", b.costLasting());
        }
        json.add("cost", cost);
        json.addProperty("effect", b.effect());
        json.addProperty("amplifier", b.amplifier());
        json.addProperty("duration", b.duration());
        return json;
    }

    private static List<String> entryProblems(Schema schema, JsonObject json) {
        if (json.has("schema")) {
            return List.of("$.schema: unknown field");
        }
        JsonObject copy = json.deepCopy();
        copy.addProperty("schema", schema.id());
        return schema.validate(copy);
    }

    private static List<String> eventRules(JsonObject json) {
        List<String> problems = new ArrayList<>();
        String type = json.get("type").getAsString();
        if (!TYPES.contains(type)) {
            problems.add("$.type: " + type + " is not a voice event type");
        }
        if (optInt(json, "min_band", 0) > optInt(json, "max_band", 3)) {
            problems.add("$.min_band: above max_band");
        }
        boolean context = json.has("context") && json.get("context").getAsBoolean();
        if (type.equals(STATUS) && !context) {
            problems.add("$.context: a status event must be context: true");
        }
        if (type.equals(INSIGHT) != json.has("points")) {
            problems.add("$.points: required for an insight and for nothing else");
        }
        if (type.equals(STATUS) != json.has("effects")) {
            problems.add("$.effects: required for a status event and for nothing else");
        }
        return problems;
    }

    private static List<String> bargainRules(JsonObject json) {
        List<String> problems = new ArrayList<>();
        if (!json.get("type").getAsString().equals(EFFECT)) {
            problems.add("$.type: not " + EFFECT);
        }
        JsonObject cost = json.getAsJsonObject("cost");
        if (optInt(cost, "temporary", 0) == 0 && optInt(cost, "lasting", 0) == 0) {
            problems.add("$.cost: no kind above 0");
        }
        return problems;
    }

    // ---- Content schemas --------------------------------------------------------------------

    /** {@code vftv:voice_events/1}: exactly the shipped roster. */
    public static final Schema EVENTS =
            Schema.of(
                            "vftv:voice_events/1",
                            Schema.required(
                                    "events",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Checked(e -> entryList(EVENT, e)),
                                            1,
                                            WarpLimits.MAX_EVENTS)))
                    .withExtra(root -> shipped(root, "events", SHIPPED_EVENTS));

    /** {@code vftv:bargains/1}: exactly the shipped roster. */
    public static final Schema BARGAINS =
            Schema.of(
                            "vftv:bargains/1",
                            Schema.required(
                                    "bargains",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Checked(e -> entryList(BARGAIN_ENTRY, e)),
                                            1,
                                            WarpLimits.MAX_BARGAINS)))
                    .withExtra(root -> shipped(root, "bargains", SHIPPED_BARGAINS));

    /** {@code vftv:phantoms/1}: 1–32 distinct sound ids (their existence is datagen's check). */
    public static final Schema PHANTOMS =
            Schema.of(
                    "vftv:phantoms/1",
                    Schema.required("sounds", new Schema.ListOf(new Schema.Id(), 1, 32, true)));

    /** {@code vftv:warp_recipes/1}: exactly the Stilling Draught's body. */
    public static final Schema RECIPES =
            Schema.of(
                            "vftv:warp_recipes/1",
                            Schema.required(
                                    "recipes",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Checked(e -> List.of()),
                                            1,
                                            1)))
                    .withExtra(VoiceContent::recipeRules);

    /** {@code vftv:warp_bootstrap/1}: the catalyst and the three aspects' physical sources. */
    public static final Schema BOOTSTRAP =
            Schema.of(
                            "vftv:warp_bootstrap/1",
                            Schema.required(
                                    "items",
                                    new Schema.ListOf(
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "item", new Schema.Id()),
                                                            Schema.required("why", TEXT))),
                                            1,
                                            16,
                                            false)),
                            Schema.required(
                                    "aspects",
                                    new Schema.ListOf(
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "aspect", new Schema.Id()),
                                                            Schema.required(
                                                                    "sources",
                                                                    new Schema.ListOf(
                                                                            new Schema.Str(
                                                                                    3,
                                                                                    257,
                                                                                    Pattern.compile(
                                                                                            "#?"
                                                                                                    + ID
                                                                                                            .pattern())),
                                                                            1,
                                                                            32,
                                                                            true)),
                                                            Schema.required("why", TEXT))),
                                            1,
                                            16,
                                            false)))
                    .withExtra(VoiceContent::bootstrapRules);

    /** {@code vftv:warp_fixtures/1}: the nine sections, each a list of rows. */
    public static final Schema FIXTURES = fixtures();

    private static final Schema.Obj SEGMENT =
            new Schema.Obj(
                    List.of(
                            Schema.required("lang", new Schema.OneOf(Set.of("en", "void"))),
                            Schema.required("text", new Schema.Str(1, MAX_LINE, null))));

    private static final Schema.ListOf SEGMENTS = new Schema.ListOf(SEGMENT, 1, 8, false);

    /** {@code vftv:lexicon/1}: the words, the two names and the six ending lines. */
    public static final Schema LEXICON =
            Schema.of(
                            "vftv:lexicon/1",
                            Schema.required(
                                    "words",
                                    new Schema.MapOf(
                                            new Schema.Str(1, 32, Pattern.compile("[a-z]+")),
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "gloss",
                                                                    new Schema.Str(1, 64, null)),
                                                            Schema.required(
                                                                    "stress",
                                                                    new Schema.Int(1, 16)),
                                                            Schema.required(
                                                                    "uses",
                                                                    new Schema.ListOf(
                                                                            new Schema.OneOf(
                                                                                    Set.of(
                                                                                            "voice",
                                                                                            "name",
                                                                                            "ending")),
                                                                            1,
                                                                            3,
                                                                            true)))),
                                            18,
                                            24)),
                            Schema.required(
                                    "names",
                                    new Schema.MapOf(
                                            new Schema.Str(1, 256, null),
                                            new Schema.Str(1, 64, null),
                                            2,
                                            2)),
                            Schema.required(
                                    "ending_lines",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "pool",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "joining",
                                                                                    "silencing"))),
                                                            Schema.required("segments", SEGMENTS),
                                                            Schema.optional("gloss", TEXT))),
                                            6,
                                            6)))
                    .withExtra(VoiceContent::lexiconRules);

    /** {@code vftv:voice_lines/1}: the script (its cross-file rules are {@link #scriptRules}). */
    public static final Schema VOICE_LINES =
            Schema.of(
                    "vftv:voice_lines/1",
                    Schema.required(
                            "session",
                            new Schema.Obj(
                                    List.of(
                                            Schema.required("lines", new Schema.Int(46, 46)),
                                            Schema.required(
                                                    "perform_seconds_max",
                                                    new Schema.Num(3.5, 3.5)),
                                            Schema.required(
                                                    "estimated_minutes", new Schema.Int(1, 600))))),
                    Schema.required(
                            "lines",
                            new Schema.MapOf(
                                    new Schema.Str(3, 256, ID),
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required(
                                                            "pool",
                                                            new Schema.OneOf(POOLS.keySet())),
                                                    Schema.required("segments", SEGMENTS),
                                                    Schema.optional("gloss", TEXT),
                                                    Schema.required(
                                                            "perform_seconds",
                                                            new Schema.Num(0.5, 3.5)),
                                                    Schema.required(
                                                            "note", new Schema.Str(1, 120, null)))),
                                    1,
                                    64)));

    /** Every schema under {@code content/warp/}. */
    public static final List<Schema> WARP_SCHEMAS =
            List.of(EVENTS, BARGAINS, PHANTOMS, VOICE_LINES, RECIPES, BOOTSTRAP, FIXTURES);

    // ---- The lexicon and the script ---------------------------------------------------------

    private static List<String> lexiconRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonObject words = root.getAsJsonObject("words");
        words.asMap()
                .forEach(
                        (word, entry) -> {
                            if (!VoidTongue.valid(word)) {
                                problems.add("$.words." + word + ": breaks the phonology");
                            } else if (VoidTongue.stress(word)
                                    != entry.getAsJsonObject().get("stress").getAsInt()) {
                                problems.add(
                                        "$.words."
                                                + word
                                                + ": stress is "
                                                + VoidTongue.stress(word));
                            }
                        });
        JsonObject names = root.getAsJsonObject("names");
        for (String key : List.of("focus_effect.vftv.unmaking", "focus_effect.vftv.undertow")) {
            if (!names.has(key)) {
                problems.add("$.names." + key + ": missing");
                continue;
            }
            String[] parts = names.get(key).getAsString().split(" ", -1);
            if (parts.length > 3) {
                problems.add("$.names." + key + ": more than 3 words");
            }
            for (String part : parts) {
                if (part.isEmpty()
                        || !Character.isUpperCase(part.charAt(0))
                        || !words.has(Character.toLowerCase(part.charAt(0)) + part.substring(1))) {
                    problems.add("$.names." + key + ": " + part + " is not a capitalised word");
                }
            }
        }
        Map<String, Integer> count = new TreeMap<>();
        root.getAsJsonObject("ending_lines")
                .asMap()
                .forEach(
                        (id, e) -> {
                            JsonObject line = e.getAsJsonObject();
                            String pool = line.get("pool").getAsString();
                            count.merge(pool, 1, Integer::sum);
                            problems.addAll(
                                    lineProblems(
                                            "$.ending_lines." + id, pool, line, words.keySet()));
                        });
        if (count.getOrDefault("joining", 0) != 3 || count.getOrDefault("silencing", 0) != 3) {
            problems.add("$.ending_lines: not 3 in each pool");
        }
        return problems;
    }

    /**
     * The script's rules across files (warp spec, "Voice lines"): the pool counts, the segments
     * against the lexicon, each text against {@code voices.json}, and the ending lines copied
     * verbatim from the lexicon.
     *
     * @param script a valid {@code vftv:voice_lines/1} envelope
     * @param lexicon a valid {@code vftv:lexicon/1} envelope
     * @param voices the English {@code voices.json} strings
     */
    public static List<String> scriptRules(
            JsonObject script, JsonObject lexicon, Map<String, String> voices) {
        List<String> problems = new ArrayList<>();
        Set<String> words = lexicon.getAsJsonObject("words").keySet();
        Map<String, Integer> count = new TreeMap<>();
        JsonObject lines = script.getAsJsonObject("lines");
        lines.asMap()
                .forEach(
                        (id, e) -> {
                            JsonObject line = e.getAsJsonObject();
                            String pool = line.get("pool").getAsString();
                            count.merge(pool, 1, Integer::sum);
                            String where = "$.lines." + id;
                            problems.addAll(lineProblems(where, pool, line, words));
                            String key = langKey(id);
                            if (!text(line).equals(voices.get(key))) {
                                problems.add(where + ": differs from " + key + " in voices.json");
                            }
                        });
        POOLS.forEach(
                (pool, n) -> {
                    if (count.getOrDefault(pool, 0).intValue() != n) {
                        problems.add(
                                "$.lines: " + pool + " holds " + count.get(pool) + ", not " + n);
                    }
                });
        if (lines.size() != script.getAsJsonObject("session").get("lines").getAsInt()) {
            problems.add("$.session.lines: not the line count");
        }
        lexicon.getAsJsonObject("ending_lines")
                .asMap()
                .forEach(
                        (id, e) -> {
                            JsonObject ending = e.getAsJsonObject();
                            JsonObject line = lines.getAsJsonObject(id);
                            if (line == null
                                    || !line.get("pool").equals(ending.get("pool"))
                                    || !line.get("segments").equals(ending.get("segments"))) {
                                problems.add("$.lines." + id + ": not the lexicon's ending line");
                            }
                        });
        return problems;
    }

    /** {@code voice.<ns>.<path>}, the lang key of line {@code <ns>:<path>}. */
    public static String langKey(String line) {
        int colon = line.indexOf(':');
        return "voice." + line.substring(0, colon) + "." + line.substring(colon + 1);
    }

    /** A line's text: its segments concatenated. */
    public static String text(JsonObject line) {
        StringBuilder text = new StringBuilder();
        line.getAsJsonArray("segments")
                .forEach(s -> text.append(s.getAsJsonObject().get("text").getAsString()));
        return text.toString();
    }

    /** Whether a line has a {@code void} segment. */
    public static boolean hasVoid(JsonObject line) {
        for (JsonElement s : line.getAsJsonArray("segments")) {
            if (s.getAsJsonObject().get("lang").getAsString().equals("void")) {
                return true;
            }
        }
        return false;
    }

    /** One line's segment rules ("Language segments"), its text length and its gloss. */
    private static List<String> lineProblems(
            String where, String pool, JsonObject line, Set<String> words) {
        List<String> problems = new ArrayList<>();
        JsonArray segments = line.getAsJsonArray("segments");
        boolean en = false;
        boolean voids = false;
        String previous = null;
        for (int i = 0; i < segments.size(); i++) {
            JsonObject s = segments.get(i).getAsJsonObject();
            String lang = s.get("lang").getAsString();
            if (lang.equals(previous)) {
                problems.add(where + ".segments[" + i + "]: the same language as the one before");
            }
            previous = lang;
            if (lang.equals("en")) {
                en = true;
                continue;
            }
            voids = true;
            for (String token : VoidTongue.tokens(s.get("text").getAsString())) {
                if (token.isEmpty() || !token.chars().allMatch(c -> c >= 'a' && c <= 'z')) {
                    problems.add(where + ".segments[" + i + "]: the token '" + token + "'");
                } else if (!words.contains(token)) {
                    problems.add(
                            where + ".segments[" + i + "]: " + token + " is not in the lexicon");
                } else if (!VoidTongue.valid(token)) {
                    problems.add(
                            where + ".segments[" + i + "]: " + token + " breaks the phonology");
                }
            }
        }
        boolean ok =
                switch (pool) {
                    case "band0", "band1" -> !voids;
                    case "band2" -> en && voids;
                    case "band3" -> !en;
                    default -> true;
                };
        if (!ok) {
            problems.add(where + ": the languages break " + pool + "'s rule");
        }
        String text = text(line);
        if (text.codePointCount(0, text.length()) > MAX_LINE) {
            problems.add(where + ": over " + MAX_LINE + " characters");
        }
        if (voids != line.has("gloss")) {
            problems.add(where + ".gloss: required exactly when a segment is void");
        }
        return problems;
    }

    // ---- Recipes, bootstrap, fixtures --------------------------------------------------------

    private static List<String> recipeRules(JsonObject root) {
        JsonObject recipes = root.getAsJsonObject("recipes");
        JsonElement body = recipes.get(STILLING);
        if (body == null || !body.equals(StrictJson.parse(STILLING_BODY))) {
            return List.of("$.recipes: not exactly " + STILLING + " with its shipped body");
        }
        return List.of();
    }

    private static List<String> bootstrapRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        boolean bottle = false;
        for (JsonElement e : root.getAsJsonArray("items")) {
            bottle |=
                    e.getAsJsonObject().get("item").getAsString().equals("minecraft:glass_bottle");
        }
        if (!bottle) {
            problems.add("$.items: the catalyst minecraft:glass_bottle is missing");
        }
        Map<String, JsonArray> sources = new LinkedHashMap<>();
        for (JsonElement e : root.getAsJsonArray("aspects")) {
            JsonObject o = e.getAsJsonObject();
            sources.put(o.get("aspect").getAsString(), o.getAsJsonArray("sources"));
        }
        for (String aspect : List.of("vftv:silentium", "vftv:ordo", "vftv:aqua")) {
            if (!sources.containsKey(aspect)) {
                problems.add("$.aspects: " + aspect + " is missing");
            }
        }
        JsonArray silentium = sources.get("vftv:silentium");
        if (silentium != null && !silentium.contains(StrictJson.parse("\"#minecraft:wool\""))) {
            problems.add("$.aspects: vftv:silentium's sources must include #minecraft:wool");
        }
        return problems;
    }

    private static Schema fixtures() {
        Schema.ListOf rows =
                new Schema.ListOf(
                        new Schema.Checked(
                                e -> e.isJsonObject() ? List.of() : List.of("not an object")),
                        1,
                        256,
                        false);
        List<Schema.Field> fields = new ArrayList<>();
        for (String section :
                List.of(
                        "phase",
                        "dials",
                        "magnitude",
                        "preview",
                        "checks",
                        "background",
                        "schedule",
                        "reconcile",
                        "amplifier")) {
            fields.add(Schema.required(section, rows));
        }
        return new Schema("vftv:warp_fixtures/1", fields, root -> List.of());
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private static List<String> entryList(Schema schema, JsonElement e) {
        return e.isJsonObject()
                ? entryProblems(schema, e.getAsJsonObject())
                : List.of("not an object");
    }

    private static List<String> shipped(JsonObject root, String field, String roster) {
        return root.get(field).equals(StrictJson.parse(roster))
                ? List.of()
                : List.of("$." + field + ": differs from the shipped roster");
    }

    private static int optInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static void throwIfAny(List<String> problems) {
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
    }

    private static Map<String, Integer> pools() {
        Map<String, Integer> pools = new LinkedHashMap<>();
        for (int b = 0; b < 4; b++) {
            pools.put("band" + b, 8);
        }
        pools.put("insight", 4);
        pools.put("offer", 4);
        pools.put("joining", 3);
        pools.put("silencing", 3);
        return java.util.Collections.unmodifiableMap(pools);
    }
}
