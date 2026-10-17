package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.BuiltinAspects;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.andrewbrogoff.vftv.core.vis.VisConfig;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisLang;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.model.NodeClientNumbers;
import com.andrewbrogoff.vftv.core.vis.model.VisRegistryRules;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The vis feature's data generation (wands spec, "Authored content"): the part and node-type
 * registry entries, the assembly recipe, the natural-spread worldgen, the client numbers and the
 * tuning, each from its {@code content/wands} or {@code content/nodes} envelope; the lang table;
 * the node's block tags. Listed once in {@link VftvData#FEATURES}.
 */
final class VisData implements FeatureData {
    /** The configuration spec's file for the option keys' translations. */
    private static final String CONFIG = "config.json";

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern HEX = Pattern.compile("0x[0-9A-F]{1,16}");
    private static final Schema.Str KEY = new Schema.Str(3, 256, ID);
    private static final Schema.Str LONG = new Schema.Str(3, 18, HEX);

    /** The spec's built-in types and their aspect ranges, which the file must match. */
    static final Map<String, Map<String, int[]>> BUILT_IN_TYPES = builtInTypes();

    /** {@code vftv:wand_parts/1}. */
    static final Schema PARTS =
            Schema.of(
                    "vftv:wand_parts/1",
                    Schema.required(
                            "rods",
                            new Schema.MapOf(
                                    KEY,
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required("item", new Schema.Id()),
                                                    Schema.required(
                                                            "capacity", new Schema.Int(1, 1000)))),
                                    0,
                                    1000)),
                    Schema.required(
                            "caps",
                            new Schema.MapOf(
                                    KEY,
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required("item", new Schema.Id()),
                                                    Schema.required(
                                                            "cost_multiplier",
                                                            new Schema.Num(0.25, 2.0)))),
                                    0,
                                    1000)));

    /** {@code vftv:wand_recipes/1}: the assembly instance (the Stage 2 bodies are T08b's). */
    static final Schema RECIPES =
            Schema.of(
                            "vftv:wand_recipes/1",
                            Schema.required(
                                    "recipes",
                                    new Schema.MapOf(
                                            KEY,
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "type",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "vftv:wand_assembly"))),
                                                            Schema.optional(
                                                                    "category",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "building",
                                                                                    "redstone",
                                                                                    "equipment",
                                                                                    "misc"))))),
                                            1,
                                            16)))
                    .withExtra(VisData::recipeRules);

    private static final Schema.Obj CLARITY =
            new Schema.Obj(
                    List.of(
                            Schema.required("faint", new Schema.Int(0, 1000)),
                            Schema.required("steady", new Schema.Int(0, 1000)),
                            Schema.required("clear", new Schema.Int(0, 1000))));

    /** {@code vftv:node_types/1}. */
    static final Schema TYPES =
            Schema.of(
                            "vftv:node_types/1",
                            Schema.required(
                                    "types",
                                    new Schema.MapOf(
                                            KEY,
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "aspects",
                                                                    new Schema.ListOf(
                                                                            new Schema.Obj(
                                                                                    List.of(
                                                                                            Schema
                                                                                                    .required(
                                                                                                            "aspect",
                                                                                                            new Schema
                                                                                                                    .Id()),
                                                                                            Schema
                                                                                                    .required(
                                                                                                            "range",
                                                                                                            new Schema
                                                                                                                    .ListOf(
                                                                                                                    new Schema
                                                                                                                            .Int(
                                                                                                                            1,
                                                                                                                            1000),
                                                                                                                    2,
                                                                                                                    2,
                                                                                                                    false)))),
                                                                            1,
                                                                            6,
                                                                            false)),
                                                            Schema.required(
                                                                    "main_aspect", new Schema.Id()),
                                                            Schema.required("clarity", CLARITY),
                                                            Schema.optional(
                                                                    "natural", new Schema.Bool()),
                                                            Schema.optional(
                                                                    "biomes",
                                                                    new Schema.ListOf(
                                                                            new Schema.Id(),
                                                                            1,
                                                                            8,
                                                                            true)),
                                                            Schema.optional(
                                                                    "weight",
                                                                    new Schema.Int(1, 1000)),
                                                            Schema.optional(
                                                                    "node_reward_divisor",
                                                                    new Schema.Int(1, 64)),
                                                            Schema.optional(
                                                                    "anchored",
                                                                    new Schema.Bool()))),
                                            1,
                                            1000)))
                    .withExtra(VisData::typeRules);

    /** {@code vftv:node_placement/1}. */
    static final Schema PLACEMENT =
            Schema.of(
                            "vftv:node_placement/1",
                            Schema.required("chance", new Schema.Num(0.0, 1.0)),
                            Schema.required(
                                    "height",
                                    new Schema.ListOf(new Schema.Int(0, 64), 2, 2, false)),
                            Schema.required("retries", new Schema.Int(0, 32)))
                    .withExtra(
                            root -> {
                                JsonArray h = root.getAsJsonArray("height");
                                return h.get(0).getAsInt() <= h.get(1).getAsInt()
                                        ? List.of()
                                        : List.of("$.height: min above max");
                            });

    /** {@code vftv:node_guarantees/1}. */
    static final Schema GUARANTEES =
            Schema.of(
                            "vftv:node_guarantees/1",
                            Schema.required(
                                    "guarantees",
                                    new Schema.MapOf(
                                            KEY,
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "stage", new Schema.Int(0, 4)),
                                                            Schema.required(
                                                                    "count", new Schema.Int(0, 64)),
                                                            Schema.required(
                                                                    "type", new Schema.Id()),
                                                            Schema.required(
                                                                    "why",
                                                                    new Schema.Str(1, 512, null)))),
                                            1,
                                            16)))
                    .withExtra(VisData::guaranteeRules);

    /** {@code vftv:node_fixtures/1}: the vector tables; the unit tests replay them. */
    static final Schema FIXTURES = fixtures();

    @Override
    public List<LangEntry> lang() {
        List<LangEntry> t = new ArrayList<>(VisLang.TABLE);
        t.add(LangEntry.codeDefined("options.vftv.nodes", 0, CONFIG, "Nodes"));
        t.add(LangEntry.codeDefined("options.vftv.nodes.starter", 0, CONFIG, "Starter Wellspring"));
        t.add(
                LangEntry.codeDefined(
                        "options.vftv.nodes.starter.tooltip",
                        0,
                        CONFIG,
                        VisConfig.STARTER_COMMENT));
        t.add(LangEntry.codeDefined("options.vftv.nodes.starter.required", 0, CONFIG, "Required"));
        t.add(
                LangEntry.codeDefined(
                        "options.vftv.nodes.starter.best_effort", 0, CONFIG, "Best effort"));
        t.add(LangEntry.codeDefined("options.vftv.nodes.starter.off", 0, CONFIG, "Off"));
        t.add(LangEntry.codeDefined("options.vftv.vis_hud", 0, CONFIG, "Vis HUD"));
        t.add(LangEntry.codeDefined("options.vftv.vis_hud.x", 0, CONFIG, "Vis HUD x"));
        t.add(
                LangEntry.codeDefined(
                        "options.vftv.vis_hud.x.tooltip", 0, CONFIG, VisLang.HUD_X_COMMENT));
        t.add(LangEntry.codeDefined("options.vftv.vis_hud.y", 0, CONFIG, "Vis HUD y"));
        t.add(
                LangEntry.codeDefined(
                        "options.vftv.vis_hud.y.tooltip", 0, CONFIG, VisLang.HUD_Y_COMMENT));
        return List.copyOf(t);
    }

    @Override
    public void tags(Tags tags) {
        tags.tag(BlockTags.WITHER_IMMUNE).add(VisContent.NODE_KEY);
        tags.tag(BlockTags.DRAGON_IMMUNE).add(VisContent.NODE_KEY);
        tags.tag(BlockTags.FEATURES_CANNOT_REPLACE).add(VisContent.NODE_KEY);
    }

    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(WandsProvider::new);
        event.createProvider(NodesProvider::new);
        event.createProvider(TuningProvider::new);
        event.createProvider(GlintProvider::new);
    }

    /** {@code assets/vftv/particles/node_glint.json}: the glint's four frames. */
    private record GlintProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            JsonArray textures = new JsonArray();
            for (int f = 0; f < 4; f++) {
                textures.add(VisContent.NODE_GLINT + "_" + f);
            }
            JsonObject json = new JsonObject();
            json.add("textures", textures);
            return save(
                    cache,
                    output,
                    PackOutput.Target.RESOURCE_PACK,
                    VisContent.NODE_GLINT,
                    "particles",
                    json);
        }

        @Override
        public String getName() {
            return "vftv node glint";
        }
    }

    /** The content root, from the data run's system property. */
    static Path contentDir() {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException(
                    "the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        return Path.of(root);
    }

    // --- extra rules --------------------------------------------------------------------------

    private static List<String> recipeRules(JsonObject root) {
        JsonObject recipes = root.getAsJsonObject("recipes");
        List<String> problems = new ArrayList<>();
        for (String id : recipes.keySet()) {
            if (!id.equals(VisContent.WAND_ASSEMBLY.toString())) {
                problems.add("$.recipes." + id + ": not in the recipe table");
            }
        }
        if (!recipes.has(VisContent.WAND_ASSEMBLY.toString())) {
            problems.add("$.recipes: " + VisContent.WAND_ASSEMBLY + " is missing");
        }
        return problems;
    }

    private static List<String> typeRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        Set<String> aspects = new HashSet<>();
        BuiltinAspects.ALL.keySet().forEach(k -> aspects.add(AspectSet.text(k)));
        Set<String> primals = new HashSet<>(VisRegistryRules.BUILT_IN_PRIMALS);
        root.getAsJsonObject("types")
                .entrySet()
                .forEach(
                        e -> {
                            String where = "$.types." + e.getKey() + ": ";
                            if (e.getKey().equals("vftv:hushed")
                                    || e.getKey().equals("vftv:choir")) {
                                problems.add(where + "authored in its owner's content file");
                                return;
                            }
                            VisRegistryRules.Type type = type(e.getValue().getAsJsonObject());
                            VisRegistryRules.type(type, aspects)
                                    .forEach(p -> problems.add(where + p));
                            if (e.getKey().equals(VisRegistryRules.WELLSPRING)) {
                                VisRegistryRules.wellspring(type, primals)
                                        .forEach(p -> problems.add(where + p));
                            }
                            Map<String, int[]> spec = BUILT_IN_TYPES.get(e.getKey());
                            if (spec != null && !sameRanges(spec, type)) {
                                problems.add(where + "differs from the spec's built-in table");
                            }
                        });
        BUILT_IN_TYPES.keySet().stream()
                .filter(id -> !root.getAsJsonObject("types").has(id))
                .forEach(id -> problems.add("$.types: built-in " + id + " is missing"));
        return problems;
    }

    private static boolean sameRanges(Map<String, int[]> spec, VisRegistryRules.Type type) {
        if (spec.size() != type.aspects().size()) {
            return false;
        }
        for (VisRegistryRules.TypeAspect a : type.aspects()) {
            int[] r = spec.get(a.aspect());
            if (r == null || r[0] != a.min() || r[1] != a.max()) {
                return false;
            }
        }
        return true;
    }

    static VisRegistryRules.Type type(JsonObject t) {
        List<VisRegistryRules.TypeAspect> aspects = new ArrayList<>();
        t.getAsJsonArray("aspects")
                .forEach(
                        a -> {
                            JsonObject o = a.getAsJsonObject();
                            JsonArray range = o.getAsJsonArray("range");
                            aspects.add(
                                    new VisRegistryRules.TypeAspect(
                                            o.get("aspect").getAsString(),
                                            range.get(0).getAsInt(),
                                            range.get(1).getAsInt()));
                        });
        JsonObject c = t.getAsJsonObject("clarity");
        List<String> biomes = new ArrayList<>();
        if (t.has("biomes")) {
            t.getAsJsonArray("biomes").forEach(b -> biomes.add(b.getAsString()));
        }
        return new VisRegistryRules.Type(
                aspects,
                t.get("main_aspect").getAsString(),
                List.of(
                        c.get("faint").getAsInt(),
                        c.get("steady").getAsInt(),
                        c.get("clear").getAsInt()),
                t.has("natural") && t.get("natural").getAsBoolean(),
                biomes,
                t.has("weight") ? t.get("weight").getAsInt() : null,
                t.has("anchored") && t.get("anchored").getAsBoolean());
    }

    private static List<String> guaranteeRules(JsonObject root) {
        JsonObject g = root.getAsJsonObject("guarantees");
        List<String> problems = new ArrayList<>();
        if (g.size() != 1 || !g.has("vftv:starter")) {
            problems.add("$.guarantees: exactly one guarantee, vftv:starter");
            return problems;
        }
        JsonObject s = g.getAsJsonObject("vftv:starter");
        if (s.get("stage").getAsInt() != 1
                || s.get("count").getAsInt() != 1
                || !s.get("type").getAsString().equals(VisRegistryRules.WELLSPRING)) {
            problems.add("$.guarantees.vftv:starter: stage 1, count 1, type vftv:wellspring");
        }
        if (s.get("why").getAsString().isBlank()) {
            problems.add("$.guarantees.vftv:starter.why: empty");
        }
        return problems;
    }

    private static Map<String, Map<String, int[]>> builtInTypes() {
        Map<String, Map<String, int[]>> m = new LinkedHashMap<>();
        Map<String, int[]> six = new TreeMap<>();
        Map<String, int[]> wild = new TreeMap<>();
        for (String p : VisRegistryRules.BUILT_IN_PRIMALS) {
            six.put(p, new int[] {32, 32});
            wild.put(p, new int[] {4, 12});
        }
        m.put("vftv:wellspring", six);
        m.put("vftv:windward", pair("vftv:aer", "vftv:terra"));
        m.put("vftv:hearth", pair("vftv:ignis", "vftv:terra"));
        m.put("vftv:tidewater", pair("vftv:aqua", "vftv:aer"));
        m.put("vftv:mire", pair("vftv:perditio", "vftv:aqua"));
        m.put("vftv:greenwood", pair("vftv:terra", "vftv:ordo"));
        m.put("vftv:stillness", pair("vftv:ordo", "vftv:aqua"));
        m.put("vftv:wild", wild);
        return m;
    }

    private static Map<String, int[]> pair(String main, String second) {
        Map<String, int[]> m = new TreeMap<>();
        m.put(main, new int[] {16, 40});
        m.put(second, new int[] {8, 24});
        return m;
    }

    private static Schema fixtures() {
        Schema.Type ints2 =
                new Schema.ListOf(
                        new Schema.Int(Integer.MIN_VALUE, Integer.MAX_VALUE), 2, 2, false);
        Schema.Type amounts = new Schema.MapOf(KEY, new Schema.Int(0, 10_000), 1, 8);
        Schema.Type triple =
                new Schema.Checked(
                        e ->
                                e.isJsonArray()
                                                && e.getAsJsonArray().size() == 3
                                                && e.getAsJsonArray().get(0)
                                                        instanceof JsonPrimitive p
                                                && p.isString()
                                                && e.getAsJsonArray().get(1).isJsonPrimitive()
                                                && e.getAsJsonArray().get(2).isJsonPrimitive()
                                        ? List.of()
                                        : List.of("not [aspect, min, max]"));
        Schema.Type move =
                new Schema.Checked(
                        e ->
                                e.isJsonArray() && e.getAsJsonArray().size() == 4
                                        ? List.of()
                                        : List.of("not [sink, aspect, source, amount]"));
        Schema.Obj eligible =
                new Schema.Obj(
                        List.of(
                                Schema.required("type", new Schema.Id()),
                                Schema.required("weight", new Schema.Int(1, 1000)),
                                Schema.required("aspects", new Schema.ListOf(triple, 1, 6, false)),
                                Schema.required(
                                        "clarity",
                                        new Schema.ListOf(new Schema.Int(0, 1000), 3, 3, false))));
        Schema.Obj region =
                new Schema.Obj(
                        List.of(
                                Schema.required("seed", LONG),
                                Schema.required("cell", ints2),
                                Schema.required("chance", new Schema.Num(0, 1)),
                                Schema.required("height", ints2),
                                Schema.required(
                                        "eligible", new Schema.ListOf(eligible, 1, 16, false)),
                                Schema.required(
                                        "expect",
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required("h", LONG),
                                                        Schema.required("d0", LONG),
                                                        Schema.required("spawn", new Schema.Bool()),
                                                        Schema.required("column", ints2),
                                                        Schema.required(
                                                                "offset", new Schema.Int(0, 64)),
                                                        Schema.required("type", new Schema.Id()),
                                                        Schema.required(
                                                                "clarity",
                                                                new Schema.OneOf(
                                                                        Set.of(
                                                                                "faint", "steady",
                                                                                "clear"))),
                                                        Schema.required("maxes", amounts))))));
        Schema.Obj ring =
                new Schema.Obj(
                        List.of(
                                Schema.required("seed", LONG),
                                Schema.required("k", new Schema.Int(1, 180)),
                                Schema.required("start", new Schema.Int(0, 1440)),
                                Schema.required("first", ints2),
                                Schema.required("second", ints2)));
        Schema.Obj quote =
                new Schema.Obj(
                        List.of(
                                Schema.required("cap", new Schema.Num(0.25, 2.0)),
                                Schema.optional("discount", new Schema.Num(0, 1)),
                                Schema.optional(
                                        "discount_modifiers",
                                        new Schema.ListOf(new Schema.Num(0, 1), 1, 8, false)),
                                Schema.required("max_discount", new Schema.Num(0, 0.9)),
                                Schema.required("w", new Schema.Num(0, 10)),
                                Schema.required(
                                        "kind", new Schema.OneOf(Set.of("ordinary", "void"))),
                                Schema.required("base", amounts),
                                Schema.required(
                                        "expect",
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required(
                                                                "cap", new Schema.Int(0, 2000)),
                                                        Schema.required(
                                                                "discount",
                                                                new Schema.Int(0, 1000)),
                                                        Schema.required(
                                                                "warp", new Schema.Int(0, 10_000)),
                                                        Schema.required(
                                                                "m", new Schema.Int(0, 10_000)),
                                                        Schema.required("effective", amounts),
                                                        Schema.required(
                                                                "discount_capped",
                                                                new Schema.Bool()),
                                                        Schema.required(
                                                                "clamped", new Schema.Bool()),
                                                        Schema.required(
                                                                "spill",
                                                                new Schema.Int(0, 1_000_000)))))));
        Schema.Obj regen =
                new Schema.Obj(
                        List.of(
                                Schema.required("max", new Schema.Int(0, 1000)),
                                Schema.required("current", new Schema.Int(0, 1000)),
                                Schema.required("tick", LONG),
                                Schema.required("now", LONG),
                                Schema.required("interval", new Schema.Int(1, 24000)),
                                Schema.required("divisor", new Schema.Int(1, 1000)),
                                Schema.required(
                                        "expect",
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required(
                                                                "current", new Schema.Int(0, 1000)),
                                                        Schema.required("tick", LONG))))));
        Schema.Obj pulse =
                new Schema.Obj(
                        List.of(
                                Schema.required("game_time", LONG),
                                Schema.required("period", new Schema.Int(1, 200)),
                                Schema.required("rate", new Schema.Int(1, 1000)),
                                Schema.required(
                                        "sources", new Schema.ListOf(amounts, 1, 31, false)),
                                Schema.required("sinks", new Schema.ListOf(amounts, 1, 31, false)),
                                Schema.required(
                                        "expect",
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required(
                                                                "start", new Schema.Int(0, 31)),
                                                        Schema.required(
                                                                "moves",
                                                                new Schema.ListOf(
                                                                        move, 0, 1000, false)))))));
        Schema.Obj coverage =
                new Schema.Obj(
                        List.of(
                                Schema.required("biome", new Schema.Id()),
                                Schema.required(
                                        "must_include",
                                        new Schema.ListOf(new Schema.Id(), 1, 16, true))));
        return Schema.of(
                        "vftv:node_fixtures/1",
                        Schema.required("region", new Schema.ListOf(region, 1, 64, false)),
                        Schema.required("ring", new Schema.ListOf(ring, 1, 64, false)),
                        Schema.required("quote", new Schema.ListOf(quote, 1, 64, false)),
                        Schema.required("regen", new Schema.ListOf(regen, 1, 64, false)),
                        Schema.required("pulse", new Schema.ListOf(pulse, 1, 64, false)),
                        Schema.required("coverage", new Schema.ListOf(coverage, 1, 256, false)))
                .withExtra(
                        root -> {
                            List<String> problems = new ArrayList<>();
                            JsonArray rows = root.getAsJsonArray("quote");
                            for (int i = 0; i < rows.size(); i++) {
                                JsonObject row = rows.get(i).getAsJsonObject();
                                if (row.has("discount") == row.has("discount_modifiers")) {
                                    problems.add(
                                            "$.quote["
                                                    + i
                                                    + "]: exactly one of discount and"
                                                    + " discount_modifiers");
                                }
                            }
                            return problems;
                        });
    }

    // --- providers ----------------------------------------------------------------------------

    private static CompletableFuture<?> save(
            CachedOutput cache,
            PackOutput output,
            PackOutput.Target target,
            Identifier id,
            String dir,
            JsonElement json) {
        return DataProvider.saveStable(
                cache,
                json,
                output.getOutputFolder(target)
                        .resolve(id.getNamespace())
                        .resolve(dir)
                        .resolve(id.getPath() + ".json"));
    }

    /** {@code content/wands}: the part entries and the assembly recipe. */
    private static final class WandsProvider extends ContentProvider {
        WandsProvider(PackOutput output) {
            super(output, "wands", List.of(PARTS, RECIPES));
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            List<CompletableFuture<?>> writes = new ArrayList<>();
            Map<String, VisRegistryRules.Part> rods = new TreeMap<>();
            Map<String, VisRegistryRules.Part> caps = new TreeMap<>();
            List<String> problems = new ArrayList<>();
            for (Envelope e : envelopes) {
                JsonObject json = e.json().getAsJsonObject();
                if (e.schema().id().equals(PARTS.id())) {
                    for (String table : List.of("rods", "caps")) {
                        boolean rod = table.equals("rods");
                        json.getAsJsonObject(table)
                                .entrySet()
                                .forEach(
                                        p -> {
                                            JsonObject body = p.getValue().getAsJsonObject();
                                            var into = rod ? rods : caps;
                                            if (into.put(
                                                            p.getKey(),
                                                            new VisRegistryRules.Part(
                                                                    body.get("item").getAsString(),
                                                                    body.get(
                                                                                    rod
                                                                                            ? "capacity"
                                                                                            : "cost_multiplier")
                                                                            .getAsDouble()))
                                                    != null) {
                                                problems.add(
                                                        "wands/"
                                                                + e.path()
                                                                + ": "
                                                                + p.getKey()
                                                                + " declared twice");
                                            }
                                            writes.add(
                                                    save(
                                                            cache,
                                                            output,
                                                            PackOutput.Target.DATA_PACK,
                                                            Identifier.parse(p.getKey()),
                                                            rod ? "vftv/wand_rod" : "vftv/wand_cap",
                                                            body));
                                        });
                    }
                } else {
                    json.getAsJsonObject("recipes")
                            .entrySet()
                            .forEach(
                                    r ->
                                            writes.add(
                                                    save(
                                                            cache,
                                                            output,
                                                            PackOutput.Target.DATA_PACK,
                                                            Identifier.parse(r.getKey()),
                                                            "recipe",
                                                            r.getValue())));
                }
            }
            problems.addAll(
                    VisRegistryRules.parts(
                            rods,
                            caps,
                            id -> BuiltInRegistries.ITEM.containsKey(Identifier.parse(id))));
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected wands content:\n" + String.join("\n", problems));
            }
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "vftv wands content";
        }
    }

    /** {@code content/nodes}: the node types, the spread feature and the client numbers. */
    private static final class NodesProvider extends ContentProvider {
        NodesProvider(PackOutput output) {
            super(
                    output,
                    "nodes",
                    List.of(TYPES, PLACEMENT, GUARANTEES, NodeClientNumbers.SCHEMA, FIXTURES));
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            List<CompletableFuture<?>> writes = new ArrayList<>();
            Set<String> types = new HashSet<>();
            List<String> problems = new ArrayList<>();
            for (Envelope e : envelopes) {
                JsonObject json = e.json().getAsJsonObject();
                if (e.schema().id().equals(TYPES.id())) {
                    json.getAsJsonObject("types")
                            .entrySet()
                            .forEach(
                                    t -> {
                                        if (!types.add(t.getKey())) {
                                            problems.add(
                                                    "nodes/"
                                                            + e.path()
                                                            + ": "
                                                            + t.getKey()
                                                            + " declared twice");
                                        }
                                        writes.add(
                                                save(
                                                        cache,
                                                        output,
                                                        PackOutput.Target.DATA_PACK,
                                                        Identifier.parse(t.getKey()),
                                                        "vftv/node_type",
                                                        t.getValue()));
                                    });
                } else if (e.schema().id().equals(PLACEMENT.id())) {
                    JsonObject config = json.deepCopy();
                    config.remove("schema");
                    JsonObject feature = new JsonObject();
                    feature.addProperty("type", VisContent.NODE_FEATURE.toString());
                    feature.add("config", config);
                    writes.add(
                            save(
                                    cache,
                                    output,
                                    PackOutput.Target.DATA_PACK,
                                    VisContent.NODE_FEATURE,
                                    "worldgen/configured_feature",
                                    feature));
                    JsonObject placed = new JsonObject();
                    placed.addProperty("feature", VisContent.NODE_FEATURE.toString());
                    placed.add("placement", new JsonArray());
                    writes.add(
                            save(
                                    cache,
                                    output,
                                    PackOutput.Target.DATA_PACK,
                                    VisContent.NODE_FEATURE,
                                    "worldgen/placed_feature",
                                    placed));
                    JsonObject modifier = new JsonObject();
                    modifier.addProperty("type", "neoforge:add_features");
                    modifier.addProperty("biomes", "#minecraft:is_overworld");
                    modifier.addProperty("features", VisContent.NODE_FEATURE.toString());
                    modifier.addProperty("step", "top_layer_modification");
                    writes.add(
                            save(
                                    cache,
                                    output,
                                    PackOutput.Target.DATA_PACK,
                                    Identifier.fromNamespaceAndPath(Vftv.MOD_ID, "add_nodes"),
                                    "neoforge/biome_modifier",
                                    modifier));
                } else if (e.schema().id().equals(NodeClientNumbers.SCHEMA.id())) {
                    writes.add(
                            save(
                                    cache,
                                    output,
                                    PackOutput.Target.RESOURCE_PACK,
                                    Identifier.fromNamespaceAndPath(Vftv.MOD_ID, "client"),
                                    "nodes",
                                    json));
                }
            }
            BUILT_IN_TYPES.keySet().stream()
                    .filter(id -> !types.contains(id))
                    .forEach(id -> problems.add("nodes: built-in type " + id + " is missing"));
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected nodes content:\n" + String.join("\n", problems));
            }
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "vftv nodes content";
        }
    }

    /** {@code data/vftv/tuning/vis.json}, every key at its default. */
    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    VisTuning.DEFAULTS.toJson(),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(VisRulesService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv vis tuning";
        }
    }
}
