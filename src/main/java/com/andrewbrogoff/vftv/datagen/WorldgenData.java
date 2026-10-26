package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenLimits;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenContent;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenService;
import com.andrewbrogoff.vftv.core.worldgen.model.Offers;
import com.andrewbrogoff.vftv.core.worldgen.model.SiteRules;
import com.andrewbrogoff.vftv.core.worldgen.model.WorldgenTuning;
import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.KilledTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jspecify.annotations.Nullable;

/**
 * The worldgen feature's data generation (worldgen spec, "Authored content"): the Choir ruin's
 * structure, structure set, pools, processor list and templates, the site kind, the {@code
 * vftv:choir} node type and the loot tables, each from its {@code content/worldgen} envelope; the
 * tags, the Answerers' find advancement, the tuning file and the lang table from code. Listed once
 * in {@link VftvData#FEATURES}.
 */
final class WorldgenData implements FeatureData {
    private static final String FILE = "worldgen.json";
    private static final java.util.regex.Pattern ID =
            java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Schema.Str KEY = new Schema.Str(3, 256, ID);
    private static final Schema.Str HEX =
            new Schema.Str(3, 18, java.util.regex.Pattern.compile("0x[0-9A-F]{1,16}"));

    /** {@code #vftv:has_structure/choir_ruin}. */
    static final TagKey<Biome> HAS_CHOIR_RUIN =
            TagKey.create(Registries.BIOME, id("has_structure/choir_ruin"));

    /** The fixed list of {@code #vftv:choir_weathering} (worldgen spec, "Processors"). */
    static final List<String> WEATHERING =
            List.of(
                    "minecraft:tuff",
                    "minecraft:tuff_bricks",
                    "minecraft:chiseled_tuff",
                    "minecraft:polished_tuff",
                    "minecraft:calcite",
                    "minecraft:deepslate_tiles",
                    "minecraft:cracked_deepslate_tiles",
                    "minecraft:cobbled_deepslate",
                    "minecraft:waxed_cut_copper",
                    "minecraft:waxed_exposed_cut_copper",
                    "minecraft:waxed_weathered_cut_copper",
                    "minecraft:waxed_oxidized_cut_copper");

    /** The pools' exact elements (worldgen spec, "Pieces and topology"). */
    static final Map<String, Set<String>> POOLS = pools();

    /** The three loot tables. */
    static final Set<String> LOOT_TABLES =
            Set.of(
                    "vftv:chests/choir_ruin",
                    "vftv:entities/answerer",
                    "vftv:entities/answerer_cantor");

    /** {@code vftv:choir_ruin/1}. */
    static final Schema RUIN = ruinSchema();

    /** {@code vftv:choir_sites/1}. */
    static final Schema SITES =
            Schema.of(
                            "vftv:choir_sites/1",
                            Schema.required(
                                    "sites",
                                    new Schema.MapOf(
                                            KEY,
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "pages",
                                                                    new Schema.ListOf(
                                                                            new Schema.Id(),
                                                                            1,
                                                                            WorldgenLimits
                                                                                    .MAX_PAGES_PER_SITE,
                                                                            true)),
                                                            Schema.required(
                                                                    "pages_per_claim",
                                                                    new Schema.Int(
                                                                            1,
                                                                            WorldgenLimits
                                                                                    .MAX_PAGES_PER_CLAIM)))),
                                            1,
                                            64)))
                    .withExtra(WorldgenData::siteRules);

    /** The shipped {@code vftv:choir} entry, which {@code node_types.json} must equal exactly. */
    static final String CHOIR_TYPE =
            "{\"aspects\": [{\"aspect\": \"vftv:aer\", \"range\": [16, 32]}, {\"aspect\":"
                    + " \"vftv:ordo\", \"range\": [24, 48]}, {\"aspect\": \"vftv:sonus\", \"range\":"
                    + " [4, 12]}, {\"aspect\": \"vftv:vacuos\", \"range\": [4, 12]}], \"main_aspect\":"
                    + " \"vftv:ordo\", \"clarity\": {\"faint\": 0, \"steady\": 60, \"clear\": 40}}";

    /** {@code vftv:node_types/1}, in this owner's file: exactly the shipped entry. */
    static final Schema NODE_TYPES =
            Schema.of(
                            "vftv:node_types/1",
                            Schema.required(
                                    "types",
                                    new Schema.MapOf(
                                            KEY,
                                            new Schema.Checked(
                                                    e ->
                                                            e.isJsonObject()
                                                                    ? List.of()
                                                                    : List.of("not an object")),
                                            1,
                                            1)))
                    .withExtra(
                            root -> {
                                JsonObject types = root.getAsJsonObject("types");
                                JsonElement choir = types.get("vftv:choir");
                                return choir != null
                                                && choir.equals(
                                                        com.andrewbrogoff.vftv.core.common.model
                                                                .StrictJson.parse(CHOIR_TYPE))
                                        ? List.of()
                                        : List.of("$.types: exactly the shipped vftv:choir entry");
                            });

    /** {@code vftv:worldgen_loot/1}. */
    static final Schema LOOT =
            Schema.of(
                            "vftv:worldgen_loot/1",
                            Schema.required(
                                    "tables",
                                    new Schema.MapOf(
                                            KEY,
                                            new Schema.Checked(
                                                    e ->
                                                            e.isJsonObject()
                                                                            && e.getAsJsonObject()
                                                                                    .has("type")
                                                                    ? List.of()
                                                                    : List.of(
                                                                            "not a loot table body")),
                                            3,
                                            3)))
                    .withExtra(WorldgenData::lootRules);

    /** {@code vftv:worldgen_fixtures/1}: the vector tables; the unit tests replay them. */
    static final Schema FIXTURES = fixturesSchema();

    @Override
    public List<LangEntry> lang() {
        List<LangEntry> t = new ArrayList<>();
        for (String key :
                List.of(
                        "block.vftv.reliquary",
                        "entity.vftv.answerer",
                        "entity.vftv.answerer_cantor",
                        "node_type.vftv.choir")) {
            t.add(LangEntry.authored(key, 0, FILE, LangEntry.Kind.TEMPLATE));
        }
        for (String row : List.of("silent", "done", "reissued", "all_held", "full", "given")) {
            t.add(LangEntry.authored("reliquary.vftv." + row, 0, FILE, LangEntry.Kind.TEMPLATE));
        }
        for (Identifier sound : WorldgenContent.SOUNDS) {
            t.add(
                    LangEntry.authored(
                            "subtitles.vftv." + sound.getPath(), 0, FILE, LangEntry.Kind.TEMPLATE));
        }
        return List.copyOf(t);
    }

    @Override
    public void tags(Tags tags) {
        ResourceKey<Block> reliquary = WorldgenContent.RELIQUARY_KEY;
        tags.tag(BlockTags.WITHER_IMMUNE).add(reliquary);
        tags.tag(BlockTags.DRAGON_IMMUNE).add(reliquary);
        tags.tag(BlockTags.FEATURES_CANNOT_REPLACE).add(reliquary);
        tags.tag(WorldgenKeys.ANSWERERS)
                .add(WorldgenContent.ANSWERER_KEY)
                .add(WorldgenContent.CANTOR_KEY);
        WEATHERING.forEach(
                b ->
                        tags.tag(WorldgenKeys.CHOIR_WEATHERING)
                                .add(ResourceKey.create(Registries.BLOCK, Identifier.parse(b))));
        // Optional: the structure is written from content as JSON, not bootstrapped in code.
        tags.tag(WorldgenKeys.CHOIR_SITES).addOptional(WorldgenKeys.CHOIR_RUIN);
        JsonObject ruin = readContent("choir_ruin.json");
        ruin.getAsJsonObject("biomes")
                .getAsJsonArray("include")
                .forEach(
                        b ->
                                tags.tag(HAS_CHOIR_RUIN)
                                        .add(
                                                ResourceKey.create(
                                                        Registries.BIOME,
                                                        Identifier.parse(b.getAsString()))));
    }

    /** {@code vftv:find/answerers}: hidden, no parent, no rewards. */
    @Override
    public void advancements(HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {
        output.accept(
                Advancement.Builder.advancement()
                        .addCriterion(
                                "killed",
                                KilledTrigger.TriggerInstance.playerKilledEntity(
                                        EntityPredicate.Builder.entity()
                                                .of(
                                                        registries.lookupOrThrow(
                                                                Registries.ENTITY_TYPE),
                                                        WorldgenKeys.ANSWERERS)))
                        .build(id("find/answerers")));
    }

    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(RuinProvider::new);
        event.createProvider(TuningProvider::new);
    }

    // --- the content ---------------------------------------------------------------------------

    static Path contentDir() {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException(
                    "the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        return Path.of(root);
    }

    private static JsonObject readContent(String file) {
        try {
            return com.andrewbrogoff.vftv.core.common.model.StrictJson.parse(
                            Files.readString(
                                    contentDir().resolve("worldgen").resolve(file),
                                    StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, Set<String>> pools() {
        Map<String, Set<String>> m = new LinkedHashMap<>();
        m.put("chancel", Set.of("vftv:choir_ruin/chancel_a", "vftv:choir_ruin/chancel_b"));
        m.put(
                "wings",
                Set.of(
                        "vftv:choir_ruin/cloister",
                        "vftv:choir_ruin/bell_pit",
                        "vftv:choir_ruin/scriptorium",
                        "vftv:choir_ruin/fallen_nave"));
        m.put(
                "ends",
                Set.of("vftv:choir_ruin/rubble_a", "vftv:choir_ruin/rubble_b", "minecraft:empty"));
        return java.util.Collections.unmodifiableMap(m);
    }

    /** The overworld's biomes: what vanilla's {@code #minecraft:is_overworld} holds. */
    static Set<String> overworldBiomes() {
        return MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD
                .usedBiomes()
                .map(k -> k.identifier().toString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Schema ruinSchema() {
        Schema.Type weights = new Schema.MapOf(KEY, new Schema.Int(1, 16), 1, 8);
        Schema.Type block = new Schema.Id();
        return Schema.of(
                        "vftv:choir_ruin/1",
                        Schema.required(
                                "structure_set",
                                new Schema.Obj(
                                        List.of(
                                                Schema.required("spacing", new Schema.Int(16, 256)),
                                                Schema.required(
                                                        "separation", new Schema.Int(8, 255)),
                                                Schema.required(
                                                        "salt",
                                                        new Schema.Int(1128812370, 1128812370))))),
                        Schema.required(
                                "pools",
                                new Schema.Obj(
                                        List.of(
                                                Schema.required("chancel", weights),
                                                Schema.required("wings", weights),
                                                Schema.required("ends", weights)))),
                        Schema.optional(
                                "weathering",
                                new Schema.ListOf(
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required("input", block),
                                                        Schema.required(
                                                                "probability",
                                                                new Schema.Num(0.0, 1.0)),
                                                        Schema.required("output", block))),
                                        0,
                                        WorldgenLimits.MAX_WEATHERING_RULES,
                                        false)),
                        Schema.required(
                                "biomes",
                                new Schema.Obj(
                                        List.of(
                                                Schema.required(
                                                        "include",
                                                        new Schema.ListOf(
                                                                new Schema.Id(), 0, 64, true)),
                                                Schema.required(
                                                        "exclude",
                                                        new Schema.MapOf(
                                                                KEY,
                                                                new Schema.Str(1, 200, null),
                                                                0,
                                                                64))))))
                .withExtra(WorldgenData::ruinRules);
    }

    static List<String> ruinRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonObject set = root.getAsJsonObject("structure_set");
        if (set.get("spacing").getAsInt() <= set.get("separation").getAsInt()) {
            problems.add("$.structure_set: spacing must exceed separation");
        }
        JsonObject pools = root.getAsJsonObject("pools");
        POOLS.forEach(
                (pool, elements) -> {
                    Set<String> found = pools.getAsJsonObject(pool).keySet();
                    if (!found.equals(elements)) {
                        problems.add("$.pools." + pool + ": exactly " + new TreeSet<>(elements));
                    }
                });
        if (root.has("weathering")) {
            JsonArray rules = root.getAsJsonArray("weathering");
            for (int i = 0; i < rules.size(); i++) {
                JsonObject r = rules.get(i).getAsJsonObject();
                String in = r.get("input").getAsString();
                String out = r.get("output").getAsString();
                if (!WEATHERING.contains(in) || !WEATHERING.contains(out)) {
                    problems.add("$.weathering[" + i + "]: both blocks in #vftv:choir_weathering");
                }
                if (in.equals(out)) {
                    problems.add("$.weathering[" + i + "]: input equals output");
                }
            }
        }
        Set<String> overworld = overworldBiomes();
        JsonObject biomes = root.getAsJsonObject("biomes");
        Set<String> include = new TreeSet<>();
        biomes.getAsJsonArray("include").forEach(b -> include.add(b.getAsString()));
        Set<String> exclude = new TreeSet<>(biomes.getAsJsonObject("exclude").keySet());
        for (String b : include) {
            if (!overworld.contains(b)) {
                problems.add("$.biomes.include: " + b + " is not in #minecraft:is_overworld");
            }
            if (exclude.contains(b)) {
                problems.add("$.biomes: " + b + " is in both lists");
            }
        }
        for (String b : exclude) {
            if (!overworld.contains(b)) {
                problems.add("$.biomes.exclude: " + b + " is not in #minecraft:is_overworld");
            }
        }
        for (String b : overworld) {
            if (!include.contains(b) && !exclude.contains(b)) {
                problems.add("$.biomes: " + b + " is in neither list");
            }
        }
        return problems;
    }

    static List<String> siteRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonObject sites = root.getAsJsonObject("sites");
        if (sites.size() != 1 || !sites.has(SiteRules.CHOIR_RUIN)) {
            problems.add("$.sites: exactly one site, " + SiteRules.CHOIR_RUIN);
            return problems;
        }
        JsonObject ruin = sites.getAsJsonObject(SiteRules.CHOIR_RUIN);
        if (ruin.get("pages_per_claim").getAsInt() != 6) {
            problems.add("$.sites." + SiteRules.CHOIR_RUIN + ".pages_per_claim: must be 6");
        }
        return problems;
    }

    static List<String> lootRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonObject tables = root.getAsJsonObject("tables");
        if (!tables.keySet().equals(LOOT_TABLES)) {
            problems.add("$.tables: exactly " + new TreeSet<>(LOOT_TABLES));
        }
        Set<String> banned = new TreeSet<>(TemplateConverter.hushedBlocks());
        banned.add("vftv:choir_page");
        banned.add("vftv:hush_ash");
        tables.entrySet()
                .forEach(
                        t -> {
                            String type = t.getValue().getAsJsonObject().get("type").getAsString();
                            String want =
                                    t.getKey().startsWith("vftv:chests/")
                                            ? "minecraft:chest"
                                            : "minecraft:entity";
                            if (!type.equals(want)) {
                                problems.add("$.tables." + t.getKey() + ".type: must be " + want);
                            }
                            items(t.getValue())
                                    .forEach(
                                            item -> {
                                                if (banned.contains(item)) {
                                                    problems.add(
                                                            "$.tables."
                                                                    + t.getKey()
                                                                    + ": "
                                                                    + item
                                                                    + " is never loot");
                                                } else if (!item.startsWith("minecraft:")) {
                                                    problems.add(
                                                            "$.tables."
                                                                    + t.getKey()
                                                                    + ": "
                                                                    + item
                                                                    + " is outside the minecraft"
                                                                    + " namespace");
                                                }
                                            });
                        });
        return problems;
    }

    /** Every item a loot body names: each {@code minecraft:item} entry's {@code name}. */
    static List<String> items(JsonElement body) {
        List<String> out = new ArrayList<>();
        if (body instanceof JsonObject o) {
            if (o.has("type")
                    && o.get("type") instanceof JsonPrimitive p
                    && p.isString()
                    && p.getAsString().equals("minecraft:item")
                    && o.has("name")) {
                out.add(o.get("name").getAsString());
            }
            o.entrySet().forEach(e -> out.addAll(items(e.getValue())));
        } else if (body instanceof JsonArray a) {
            a.forEach(e -> out.addAll(items(e)));
        }
        return out;
    }

    private static Schema fixturesSchema() {
        Schema.Type anyInt = new Schema.Int(Integer.MIN_VALUE, Integer.MAX_VALUE);
        Schema.Type ints2 = new Schema.ListOf(anyInt, 2, 2, false);
        Schema.Obj placement =
                new Schema.Obj(
                        List.of(
                                Schema.required("seed", HEX),
                                Schema.required("spacing", new Schema.Int(16, 256)),
                                Schema.required("separation", new Schema.Int(8, 255)),
                                Schema.required("salt", new Schema.Int(0, Integer.MAX_VALUE)),
                                Schema.required("region", ints2),
                                Schema.required("expect", ints2)));
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
                                        : List.of("not [id, min, max]"));
        Schema.Obj roll =
                new Schema.Obj(
                        List.of(
                                Schema.required("seed", HEX),
                                Schema.required(
                                        "pos",
                                        new Schema.Checked(
                                                e -> {
                                                    if (!e.isJsonArray()
                                                            || e.getAsJsonArray().size() != 3) {
                                                        return List.of("not [x, y, z]");
                                                    }
                                                    JsonArray a = e.getAsJsonArray();
                                                    long x = a.get(0).getAsLong();
                                                    long y = a.get(1).getAsLong();
                                                    long z = a.get(2).getAsLong();
                                                    return Math.abs(x) <= 30_000_000
                                                                    && Math.abs(z) <= 30_000_000
                                                                    && y >= -64
                                                                    && y <= 319
                                                            ? List.of()
                                                            : List.of("outside the world");
                                                })),
                                Schema.required(
                                        "clarity",
                                        new Schema.ListOf(new Schema.Int(0, 1000), 3, 3, false)),
                                Schema.required("aspects", new Schema.ListOf(triple, 1, 6, false)),
                                Schema.required(
                                        "expect",
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required("key", HEX),
                                                        Schema.required("h", HEX),
                                                        Schema.required(
                                                                "clarity",
                                                                new Schema.OneOf(
                                                                        Set.of(
                                                                                "faint", "steady",
                                                                                "clear"))),
                                                        Schema.required(
                                                                "maxes",
                                                                new Schema.MapOf(
                                                                        KEY,
                                                                        new Schema.Int(0, 1000),
                                                                        1,
                                                                        6)))))));
        Schema.Type opaque = new Schema.ListOf(new Schema.Str(1, 256, null), 0, 64, true);
        Set<String> outcomes = new TreeSet<>();
        for (Offers.Outcome o : Offers.Outcome.values()) {
            outcomes.add(o.id());
        }
        Schema.Obj use =
                new Schema.Obj(
                        List.of(
                                Schema.required("tick", new Schema.Int(0, Long.MAX_VALUE)),
                                Schema.required("player", new Schema.Str(1, 64, null)),
                                Schema.required("site", new Schema.Str(1, 64, null))));
        Schema.Obj step =
                new Schema.Obj(
                        List.of(
                                Schema.optional("use", use),
                                Schema.optional(
                                        "expect",
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required(
                                                                "outcome",
                                                                new Schema.OneOf(outcomes)),
                                                        Schema.optional("pages", opaque)))),
                                Schema.optional(
                                        "latch",
                                        new Schema.Obj(
                                                List.of(
                                                        Schema.required(
                                                                "player",
                                                                new Schema.Str(1, 64, null)),
                                                        Schema.required("pages", opaque)))),
                                Schema.optional("remove", opaque)));
        Schema.Obj offers =
                new Schema.Obj(
                        List.of(
                                Schema.required(
                                        "pages",
                                        new Schema.ListOf(
                                                new Schema.Str(1, 256, null), 1, 64, true)),
                                Schema.required("pages_per_claim", new Schema.Int(1, 8)),
                                Schema.required("steps", new Schema.ListOf(step, 1, 256, false))));
        return Schema.of(
                        "vftv:worldgen_fixtures/1",
                        Schema.required("placement", new Schema.ListOf(placement, 1, 64, false)),
                        Schema.required("roll", new Schema.ListOf(roll, 1, 64, false)),
                        Schema.required("offers", new Schema.ListOf(offers, 1, 64, false)))
                .withExtra(WorldgenData::fixtureRules);
    }

    static List<String> fixtureRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonArray offers = root.getAsJsonArray("offers");
        for (int o = 0; o < offers.size(); o++) {
            JsonArray steps = offers.get(o).getAsJsonObject().getAsJsonArray("steps");
            long tick = Long.MIN_VALUE;
            for (int s = 0; s < steps.size(); s++) {
                String at = "$.offers[" + o + "].steps[" + s + "]";
                JsonObject step = steps.get(s).getAsJsonObject();
                int kinds =
                        (step.has("use") ? 1 : 0)
                                + (step.has("latch") ? 1 : 0)
                                + (step.has("remove") ? 1 : 0);
                if (kinds != 1) {
                    problems.add(at + ": exactly one of use, latch and remove");
                    continue;
                }
                if (step.has("use") != step.has("expect")) {
                    problems.add(at + ": expect iff use");
                    continue;
                }
                if (step.has("use")) {
                    long t = step.getAsJsonObject("use").get("tick").getAsLong();
                    if (t < tick) {
                        problems.add(at + ": ticks never decrease");
                    }
                    tick = t;
                    JsonObject expect = step.getAsJsonObject("expect");
                    String outcome = expect.get("outcome").getAsString();
                    boolean pages = outcome.equals("given") || outcome.equals("reissued");
                    if (expect.has("pages") != pages) {
                        problems.add(at + ".expect: pages iff the outcome is given or reissued");
                    }
                }
            }
        }
        return problems;
    }

    // --- providers ----------------------------------------------------------------------------

    private static CompletableFuture<?> save(
            CachedOutput cache, PackOutput output, Identifier id, String dir, JsonElement json) {
        return DataProvider.saveStable(
                cache,
                json,
                output.getOutputFolder(PackOutput.Target.DATA_PACK)
                        .resolve(id.getNamespace())
                        .resolve(dir)
                        .resolve(id.getPath() + ".json"));
    }

    /** {@code content/worldgen}: every worldgen output of the Choir ruin. */
    private static final class RuinProvider extends ContentProvider {
        private final CompletableFuture<HolderLookup.Provider> lookup;

        RuinProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(
                    output,
                    "worldgen",
                    List.of(TemplateConverter.SCHEMA, RUIN, SITES, NODE_TYPES, LOOT, FIXTURES));
            this.lookup = lookup;
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            return lookup.thenCompose(registries -> generate(envelopes, cache, registries));
        }

        private CompletableFuture<?> generate(
                List<Envelope> envelopes, CachedOutput cache, HolderLookup.Provider registries) {
            List<CompletableFuture<?>> writes = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            Map<String, JsonObject> bySchema = new TreeMap<>();
            Map<String, JsonObject> templates = new TreeMap<>();
            for (Envelope e : envelopes) {
                JsonObject json = e.json().getAsJsonObject();
                if (e.schema().id().equals(TemplateConverter.SCHEMA.id())) {
                    if (!e.path().startsWith("templates/")) {
                        problems.add("worldgen/" + e.path() + ": templates live in templates/");
                    }
                    templates.put(
                            e.path().substring(e.path().lastIndexOf('/') + 1).replace(".json", ""),
                            json);
                } else if (bySchema.put(e.schema().id(), json) != null) {
                    problems.add("worldgen/" + e.path() + ": a second " + e.schema().id());
                }
            }
            for (Schema s : List.of(RUIN, SITES, NODE_TYPES, LOOT, FIXTURES)) {
                if (!bySchema.containsKey(s.id())) {
                    problems.add("worldgen: no " + s.id() + " file");
                }
            }
            if (!templates.keySet().equals(TemplateConverter.CHOIR_RUIN.files().keySet())) {
                problems.add(
                        "worldgen/templates: exactly "
                                + new TreeSet<>(TemplateConverter.CHOIR_RUIN.files().keySet()));
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected worldgen content:\n" + String.join("\n", problems));
            }
            JsonObject types = one(bySchema, NODE_TYPES).getAsJsonObject("types");
            templates.forEach(
                    (name, json) -> {
                        TemplateConverter.Result r =
                                TemplateConverter.convert(
                                        name,
                                        json,
                                        TemplateConverter.CHOIR_RUIN,
                                        type -> minima(types, type));
                        problems.addAll(r.problems());
                        if (r.nbt() != null) {
                            byte[] bytes = TemplateConverter.gzip(r.nbt());
                            Path path =
                                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                                            .resolve("vftv/structure/choir_ruin/" + name + ".nbt");
                            writes.add(
                                    CompletableFuture.runAsync(
                                            () -> {
                                                try {
                                                    cache.writeIfNeeded(
                                                            path,
                                                            bytes,
                                                            Hashing.sha256().hashBytes(bytes));
                                                } catch (IOException ex) {
                                                    throw new UncheckedIOException(ex);
                                                }
                                            }));
                        }
                    });
            problems.addAll(siteContent(one(bySchema, SITES)));
            problems.addAll(lootBodies(one(bySchema, LOOT), registries));
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected worldgen content:\n" + String.join("\n", problems));
            }
            writes.addAll(ruin(cache, one(bySchema, RUIN)));
            one(bySchema, SITES)
                    .getAsJsonObject("sites")
                    .entrySet()
                    .forEach(
                            s ->
                                    writes.add(
                                            save(
                                                    cache,
                                                    output,
                                                    Identifier.parse(s.getKey()),
                                                    "vftv/choir_site",
                                                    s.getValue())));
            types.entrySet()
                    .forEach(
                            t ->
                                    writes.add(
                                            save(
                                                    cache,
                                                    output,
                                                    Identifier.parse(t.getKey()),
                                                    "vftv/node_type",
                                                    t.getValue())));
            one(bySchema, LOOT)
                    .getAsJsonObject("tables")
                    .entrySet()
                    .forEach(
                            t ->
                                    writes.add(
                                            save(
                                                    cache,
                                                    output,
                                                    Identifier.parse(t.getKey()),
                                                    "loot_table",
                                                    t.getValue())));
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        /** The structure, its set, the three pools and the processor list. */
        private List<CompletableFuture<?>> ruin(CachedOutput cache, JsonObject content) {
            List<CompletableFuture<?>> writes = new ArrayList<>();
            Identifier ruin = WorldgenKeys.CHOIR_RUIN.identifier();
            JsonObject structure = new JsonObject();
            structure.addProperty("type", "minecraft:jigsaw");
            structure.addProperty("biomes", "#" + HAS_CHOIR_RUIN.location());
            structure.addProperty("step", "surface_structures");
            structure.add("spawn_overrides", new JsonObject());
            structure.addProperty("terrain_adaptation", "beard_thin");
            structure.addProperty("start_pool", "vftv:choir_ruin/chancel");
            structure.addProperty("size", 2);
            JsonObject height = new JsonObject();
            height.addProperty("absolute", 0);
            structure.add("start_height", height);
            structure.addProperty("project_start_to_heightmap", "WORLD_SURFACE_WG");
            structure.addProperty("max_distance_from_center", 40);
            structure.addProperty("use_expansion_hack", false);
            writes.add(save(cache, output, ruin, "worldgen/structure", structure));

            JsonObject spread = content.getAsJsonObject("structure_set");
            JsonObject placement = new JsonObject();
            placement.addProperty("type", "minecraft:random_spread");
            placement.addProperty("spacing", spread.get("spacing").getAsInt());
            placement.addProperty("separation", spread.get("separation").getAsInt());
            placement.addProperty("salt", spread.get("salt").getAsInt());
            placement.addProperty("spread_type", "linear");
            placement.addProperty("frequency", 1.0);
            JsonObject member = new JsonObject();
            member.addProperty("structure", ruin.toString());
            member.addProperty("weight", 1);
            JsonArray structures = new JsonArray();
            structures.add(member);
            JsonObject set = new JsonObject();
            set.add("structures", structures);
            set.add("placement", placement);
            writes.add(save(cache, output, ruin, "worldgen/structure_set", set));

            JsonObject pools = content.getAsJsonObject("pools");
            for (String pool : POOLS.keySet()) {
                JsonArray elements = new JsonArray();
                pools.getAsJsonObject(pool)
                        .entrySet()
                        .forEach(
                                e -> {
                                    JsonObject element = new JsonObject();
                                    if (e.getKey().equals("minecraft:empty")) {
                                        element.addProperty(
                                                "element_type", "minecraft:empty_pool_element");
                                    } else {
                                        element.addProperty(
                                                "element_type", "minecraft:single_pool_element");
                                        element.addProperty("location", e.getKey());
                                        element.addProperty("processors", ruin.toString());
                                        element.addProperty("projection", "rigid");
                                    }
                                    JsonObject weighted = new JsonObject();
                                    weighted.add("element", element);
                                    weighted.addProperty("weight", e.getValue().getAsInt());
                                    elements.add(weighted);
                                });
                JsonObject body = new JsonObject();
                body.addProperty("fallback", "minecraft:empty");
                body.add("elements", elements);
                writes.add(
                        save(
                                cache,
                                output,
                                id("choir_ruin/" + pool),
                                "worldgen/template_pool",
                                body));
            }

            JsonArray processors = new JsonArray();
            JsonObject protect = new JsonObject();
            protect.addProperty("processor_type", "minecraft:protected_blocks");
            protect.addProperty("value", "#minecraft:features_cannot_replace");
            processors.add(protect);
            JsonArray weathering =
                    content.has("weathering")
                            ? content.getAsJsonArray("weathering")
                            : new JsonArray();
            if (!weathering.isEmpty()) {
                JsonArray rules = new JsonArray();
                weathering.forEach(
                        w -> {
                            JsonObject r = w.getAsJsonObject();
                            JsonObject input = new JsonObject();
                            input.addProperty("predicate_type", "minecraft:random_block_match");
                            input.addProperty("block", r.get("input").getAsString());
                            input.add("probability", r.get("probability"));
                            JsonObject location = new JsonObject();
                            location.addProperty("predicate_type", "minecraft:always_true");
                            JsonObject state = new JsonObject();
                            state.addProperty("Name", r.get("output").getAsString());
                            JsonObject rule = new JsonObject();
                            rule.add("input_predicate", input);
                            rule.add("location_predicate", location);
                            rule.add("output_state", state);
                            rules.add(rule);
                        });
                JsonObject rule = new JsonObject();
                rule.addProperty("processor_type", "minecraft:rule");
                rule.add("rules", rules);
                processors.add(rule);
            }
            JsonObject roll = new JsonObject();
            roll.addProperty("processor_type", WorldgenContent.ROLL_NODE.toString());
            processors.add(roll);
            JsonObject list = new JsonObject();
            list.add("processors", processors);
            writes.add(save(cache, output, ruin, "worldgen/processor_list", list));
            return writes;
        }

        @Override
        public String getName() {
            return "vftv worldgen content";
        }
    }

    private static JsonObject one(Map<String, JsonObject> bySchema, Schema schema) {
        return java.util.Objects.requireNonNull(bySchema.get(schema.id()), schema.id());
    }

    /** The site kinds against the research content: the general and the built-in rules. */
    static List<String> siteContent(JsonObject sites) {
        Map<String, Offers.Site> kinds = new TreeMap<>();
        sites.getAsJsonObject("sites")
                .entrySet()
                .forEach(
                        s -> {
                            List<String> pages = new ArrayList<>();
                            s.getValue()
                                    .getAsJsonObject()
                                    .getAsJsonArray("pages")
                                    .forEach(p -> pages.add(p.getAsString()));
                            kinds.put(
                                    s.getKey(),
                                    new Offers.Site(
                                            pages,
                                            s.getValue()
                                                    .getAsJsonObject()
                                                    .get("pages_per_claim")
                                                    .getAsInt()));
                        });
        Map<String, SiteRules.Entry> entries = new TreeMap<>();
        ResearchContent.read(contentDir())
                .plainEntries()
                .forEach(
                        (id, e) ->
                                entries.put(
                                        id,
                                        new SiteRules.Entry(
                                                e.category(), e.stage(), e.kind().id())));
        List<String> problems = new ArrayList<>(SiteRules.check(kinds, entries));
        Offers.Site ruin = kinds.get(SiteRules.CHOIR_RUIN);
        if (ruin != null) {
            problems.addAll(SiteRules.builtIn(ruin, entries));
        }
        return problems.stream().map(p -> "worldgen/sites.json: " + p).toList();
    }

    /** Each loot body decodes through vanilla's loot table codec. */
    static List<String> lootBodies(JsonObject loot, HolderLookup.Provider registries) {
        List<String> problems = new ArrayList<>();
        loot.getAsJsonObject("tables")
                .entrySet()
                .forEach(
                        t ->
                                LootTable.DIRECT_CODEC
                                        .parse(
                                                registries.createSerializationContext(
                                                        JsonOps.INSTANCE),
                                                t.getValue())
                                        .ifError(
                                                e ->
                                                        problems.add(
                                                                "worldgen/loot.json: "
                                                                        + t.getKey()
                                                                        + ": "
                                                                        + e.message())));
        return problems;
    }

    /** A node type's aspects at their range minima, ascending id; null when it is unknown. */
    static @Nullable List<NodeState.NodeAspect> minima(JsonObject types, String type) {
        JsonElement t = types.get(type);
        if (t == null) {
            return null;
        }
        Map<String, Integer> mins = new TreeMap<>();
        t.getAsJsonObject()
                .getAsJsonArray("aspects")
                .forEach(
                        a ->
                                mins.put(
                                        a.getAsJsonObject().get("aspect").getAsString(),
                                        a.getAsJsonObject()
                                                .getAsJsonArray("range")
                                                .get(0)
                                                .getAsInt()));
        List<NodeState.NodeAspect> out = new ArrayList<>();
        mins.forEach(
                (id, min) -> out.add(new NodeState.NodeAspect(Identifier.parse(id), min, min)));
        return out;
    }

    /** {@code data/vftv/tuning/worldgen.json}, every key at its default. */
    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    WorldgenTuning.DEFAULTS.toJson(),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(WorldgenService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv worldgen tuning";
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Vftv.MOD_ID, path);
    }
}
