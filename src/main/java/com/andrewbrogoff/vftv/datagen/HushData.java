package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.hush.Hollow;
import com.andrewbrogoff.vftv.core.hush.HushContent;
import com.andrewbrogoff.vftv.core.hush.HushLang;
import com.andrewbrogoff.vftv.core.hush.client.HushClientConfig;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jspecify.annotations.Nullable;

/**
 * The hush feature's data generation (Hush spec, "Authored content" and the build-phase data): the
 * lang table and the client keys' tooltips; {@code #vftv:hushed}, {@code #vftv:hush_immune} and
 * each form's support-parity tags derived from vanilla's tag data; the loot tables; the content of
 * {@code content/hush} (both recipes, the {@code vftv:hushed} node type, the fixture schema); the
 * mote's particle definition and the reading frame's nine-slice metadata.
 */
final class HushData implements FeatureData {
    /** The authored directory under {@code content/}. */
    static final String DIR = "hush";

    private static final String CONFIG = "config.json";
    private static final String CLEARING = "vftv:clearing_bell";
    private static final String PEALING = "vftv:pealing_bell";
    private static final String ASH = "vftv:hush_ash";

    /** The Wellspring's six guaranteed primals: the Clearing Bell's only vis. */
    private static final Set<String> PRIMALS =
            Set.of(
                    "vftv:aer",
                    "vftv:terra",
                    "vftv:ignis",
                    "vftv:aqua",
                    "vftv:ordo",
                    "vftv:perditio");

    private static final Set<String> CLEARING_INGREDIENTS =
            Set.of(
                    "vftv:resonant_brass_ingot",
                    "minecraft:copper_ingot",
                    "minecraft:amethyst_shard",
                    "minecraft:stick",
                    "minecraft:string");

    /** The vanilla tags whose members' forms join them: supports, mineable, leaf decay. */
    static final List<TagKey<Block>> PARITY_TAGS = parityTags();

    /** The shipped {@code vftv:hushed} entry, which {@code node_types.json} must equal exactly. */
    static final String HUSHED_TYPE =
            "{\"aspects\": [{\"aspect\": \"vftv:silentium\", \"range\": [1, 1]}], \"main_aspect\":"
                    + " \"vftv:silentium\", \"clarity\": {\"faint\": 1, \"steady\": 0, \"clear\": 0}}";

    @Override
    public List<LangEntry> lang() {
        List<LangEntry> t = new ArrayList<>(HushLang.TABLE);
        t.add(
                LangEntry.codeDefined(
                        "options.vftv.hush.muffle.tooltip",
                        0,
                        CONFIG,
                        HushClientConfig.MUFFLE_COMMENT));
        t.add(
                LangEntry.codeDefined(
                        "options.vftv.hush.hud.tooltip", 0, CONFIG, HushClientConfig.HUD_COMMENT));
        return List.copyOf(t);
    }

    private static List<TagKey<Block>> parityTags() {
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String path :
                List.of(
                        "vegetation",
                        "dry_vegetation",
                        "crops",
                        "stem_crops",
                        "stem_fruit",
                        "pumpkin_stem",
                        "melon_stem",
                        "pumpkin_stem_fruit",
                        "melon_stem_fruit",
                        "sugar_cane",
                        "sugar_cane_adjacently",
                        "bamboo",
                        "small_dripleaf",
                        "big_dripleaf",
                        "cactus",
                        "chorus_plant",
                        "chorus_flower",
                        "nether_sprouts",
                        "azalea",
                        "warped_fungus",
                        "crimson_fungus",
                        "mangrove_propagule",
                        "hanging_mangrove_propagule",
                        "nether_wart",
                        "crimson_roots",
                        "warped_roots",
                        "wither_rose",
                        "cocoa",
                        "lily_pad",
                        "frogspawn")) {
            tags.add(BlockTags.create(Identifier.withDefaultNamespace("supports_" + path)));
        }
        tags.add(BlockTags.MINEABLE_WITH_AXE);
        tags.add(BlockTags.MINEABLE_WITH_HOE);
        tags.add(BlockTags.MINEABLE_WITH_PICKAXE);
        tags.add(BlockTags.MINEABLE_WITH_SHOVEL);
        tags.add(BlockTags.PREVENTS_NEARBY_LEAF_DECAY);
        return List.copyOf(tags);
    }

    /**
     * Every block id a vanilla block tag holds, nested tags resolved, read from the vanilla data
     * pack itself.
     */
    static Set<String> vanillaMembers(Identifier tag) {
        return vanillaMembers("block", tag);
    }

    /**
     * Every id a vanilla tag of {@code kind} ({@code block}, {@code item}) holds, nested resolved.
     */
    static Set<String> vanillaMembers(String kind, Identifier tag) {
        Set<String> out = new TreeSet<>();
        collect(kind, tag, out, new LinkedHashSet<>());
        return out;
    }

    private static void collect(
            String kind, Identifier tag, Set<String> out, Set<Identifier> seen) {
        if (!seen.add(tag)) {
            return;
        }
        IoSupplier<InputStream> resource =
                ServerPacksSource.createVanillaPackSource()
                        .getResource(
                                PackType.SERVER_DATA,
                                Identifier.fromNamespaceAndPath(
                                        tag.getNamespace(),
                                        "tags/" + kind + "/" + tag.getPath() + ".json"));
        if (resource == null) {
            return;
        }
        JsonObject json;
        try (InputStream in = resource.get()) {
            json =
                    StrictJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (JsonElement value : json.getAsJsonArray("values")) {
            String id =
                    value.isJsonObject()
                            ? value.getAsJsonObject().get("id").getAsString()
                            : value.getAsString();
            if (id.startsWith("#")) {
                collect(kind, Identifier.parse(id.substring(1)), out, seen);
            } else {
                out.add(Identifier.parse(id).toString());
            }
        }
    }

    @Override
    public void tags(Tags tags) {
        var hushed = tags.tag(HushKeys.HUSHED);
        for (HushedForm f : HushKeys.FORMS) {
            hushed.add(ResourceKey.create(Registries.BLOCK, f.form()));
        }
        tags.tag(HushKeys.HUSH_IMMUNE);
        for (TagKey<Block> tag : PARITY_TAGS) {
            Set<String> members = vanillaMembers(tag.location());
            for (HushedForm f : HushKeys.FORMS) {
                if (members.contains(f.source().toString())) {
                    tags.tag(tag).add(ResourceKey.create(Registries.BLOCK, f.form()));
                }
            }
        }
        tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(HushContent.PEALING_BELL_KEY);
        tags.tag(
                        TagKey.create(
                                Registries.BLOCK,
                                Identifier.fromNamespaceAndPath(Vftv.MOD_ID, "essentia_endpoints")))
                .add(HushContent.PEALING_BELL_KEY);
    }

    @Override
    public List<LootTableProvider.SubProviderEntry> loot() {
        return List.of(
                new LootTableProvider.SubProviderEntry(
                        registries ->
                                new BlockLootSubProvider(
                                        Set.of(), FeatureFlags.REGISTRY.allFlags(), registries) {
                                    @Override
                                    protected void generate() {
                                        HushContent.forms()
                                                .forEach(b -> add(b, createSilkTouchOnlyTable(b)));
                                        // Drops itself, empty, surviving explosions.
                                        add(
                                                HushContent.pealingBell(),
                                                LootTable.lootTable()
                                                        .withPool(
                                                                LootPool.lootPool()
                                                                        .setRolls(
                                                                                ConstantValue
                                                                                        .exactly(1))
                                                                        .add(
                                                                                LootItem
                                                                                        .lootTableItem(
                                                                                                HushContent
                                                                                                        .pealingBell()))));
                                    }

                                    @Override
                                    protected Iterable<Block> getKnownBlocks() {
                                        List<Block> known = new ArrayList<>(HushContent.forms());
                                        known.add(HushContent.pealingBell());
                                        return known;
                                    }
                                },
                        LootContextParamSets.BLOCK),
                new LootTableProvider.SubProviderEntry(
                        registries ->
                                new EntityLootSubProvider(
                                        FeatureFlags.REGISTRY.allFlags(), registries) {
                                    @Override
                                    public void generate() {
                                        add(
                                                HushContent.hollow(),
                                                LootTable.lootTable()
                                                        .withPool(
                                                                LootPool.lootPool()
                                                                        .setRolls(
                                                                                ConstantValue
                                                                                        .exactly(1))
                                                                        .add(
                                                                                LootItem
                                                                                        .lootTableItem(
                                                                                                HushContent
                                                                                                        .hushAsh())
                                                                                        .apply(
                                                                                                SetItemCountFunction
                                                                                                        .setCount(
                                                                                                                UniformGenerator
                                                                                                                        .between(
                                                                                                                                0,
                                                                                                                                Hollow
                                                                                                                                        .DROPS_MAX)))
                                                                                        .apply(
                                                                                                EnchantedCountIncreaseFunction
                                                                                                        .lootingMultiplier(
                                                                                                                registries,
                                                                                                                UniformGenerator
                                                                                                                        .between(
                                                                                                                                0,
                                                                                                                                1))))));
                                    }

                                    @Override
                                    protected java.util.stream.Stream<EntityType<?>>
                                            getKnownEntityTypes() {
                                        return java.util.stream.Stream.of(HushContent.hollow());
                                    }
                                },
                        LootContextParamSets.ENTITY));
    }

    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(Content::new);
        event.createProvider(MoteProvider::new);
        event.createProvider(FrameProvider::new);
    }

    // --- content --------------------------------------------------------------------------

    /** {@code vftv:hush_recipes/1}: exactly the two bodies, within their rules. */
    static final Schema RECIPES =
            Schema.of(
                            "vftv:hush_recipes/1",
                            Schema.required(
                                    "recipes",
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required(CLEARING, ArcaneBodies.BODY),
                                                    Schema.required(
                                                            PEALING, InfusionBodies.BODY)))))
                    .withExtra(HushData::recipeRules);

    /** {@code vftv:node_types/1}, in this owner's file: exactly the shipped entry. */
    static final Schema NODE_TYPES =
            Schema.of(
                            "vftv:node_types/1",
                            Schema.required(
                                    "types",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ArcaneBodies.ID),
                                            new Schema.Checked(
                                                    e ->
                                                            e.isJsonObject()
                                                                    ? List.of()
                                                                    : List.of("not an object")),
                                            1,
                                            1)))
                    .withExtra(
                            root ->
                                    StrictJson.parse(HUSHED_TYPE)
                                                    .equals(
                                                            root.getAsJsonObject("types")
                                                                    .get("vftv:hushed"))
                                            ? List.of()
                                            : List.of(
                                                    "$.types: exactly the shipped vftv:hushed entry"));

    private static final Schema.Str HEX =
            new Schema.Str(3, 18, Pattern.compile("0x[0-9A-F]{1,16}"));
    private static final Schema.Str HEX16 =
            new Schema.Str(18, 18, Pattern.compile("0x[0-9A-F]{16}"));

    private static Schema.Type ints(int length, long min, long max) {
        return new Schema.ListOf(new Schema.Int(min, max), length, length, false);
    }

    private static final Schema.Type CELL =
            new Schema.Checked(
                    e -> {
                        if (!e.isJsonArray() || e.getAsJsonArray().size() != 3) {
                            return List.of("a cell is [x, y, z]");
                        }
                        JsonArray a = e.getAsJsonArray();
                        for (int i = 0; i < 3; i++) {
                            long v = a.get(i).getAsLong();
                            long bound = i == 1 ? 0 : 30_000_000;
                            if ((i == 1 && (v < -64 || v > 319))
                                    || (i != 1 && Math.abs(v) > bound)) {
                                return List.of("cell coordinate " + v + " out of range");
                            }
                        }
                        return List.of();
                    });

    private static final Schema.Type CHUNK = ints(2, -1_875_000, 1_875_000);

    private static Schema.Type rows(Schema.Field... fields) {
        return new Schema.ListOf(new Schema.Obj(List.of(fields)), 1, 64, false);
    }

    /** {@code vftv:hush_fixtures/1}: the eight sections, every row of each vector table. */
    static final Schema FIXTURES =
            Schema.of(
                            "vftv:hush_fixtures/1",
                            Schema.required(
                                    "spill",
                                    rows(
                                            Schema.required("cap", new Schema.Int(250, 2000)),
                                            Schema.required("d", new Schema.Int(0, 900)),
                                            Schema.required("w", new Schema.Int(0, 10_000)),
                                            Schema.required(
                                                    "base",
                                                    new Schema.ListOf(
                                                            new Schema.Int(0, 1000), 1, 8, false)),
                                            Schema.required(
                                                    "kind",
                                                    new Schema.OneOf(Set.of("ordinary", "void"))),
                                            Schema.optional("tax_pm", new Schema.Int(0, 1000)),
                                            Schema.optional("void_pm", new Schema.Int(0, 1000)),
                                            Schema.optional("min_m", new Schema.Int(100, 1000)),
                                            Schema.optional("max_m", new Schema.Int(1000, 10_000)),
                                            Schema.required(
                                                    "expect",
                                                    new Schema.Obj(
                                                            List.of(
                                                                    Schema.required(
                                                                            "effective",
                                                                            new Schema.ListOf(
                                                                                    new Schema.Int(
                                                                                            0,
                                                                                            10_000),
                                                                                    1,
                                                                                    8,
                                                                                    false)),
                                                                    Schema.required(
                                                                            "neutral",
                                                                            new Schema.ListOf(
                                                                                    new Schema.Int(
                                                                                            0,
                                                                                            10_000),
                                                                                    1,
                                                                                    8,
                                                                                    false)),
                                                                    Schema.optional(
                                                                            "surcharge",
                                                                            new Schema.Int(
                                                                                    0,
                                                                                    Long
                                                                                            .MAX_VALUE)),
                                                                    Schema.required(
                                                                            "spill",
                                                                            new Schema.Int(
                                                                                    0,
                                                                                    1_000_000))))))),
                            Schema.required(
                                    "essentia",
                                    rows(
                                            Schema.required("total", new Schema.Int(0, 640_000)),
                                            Schema.required("pm", new Schema.Int(0, 1000)),
                                            Schema.optional("times", new Schema.Int(1, 64)),
                                            Schema.required(
                                                    "expect", new Schema.Int(0, Long.MAX_VALUE)))),
                            Schema.required(
                                    "phase",
                                    rows(
                                            Schema.required("chunk", CHUNK),
                                            Schema.required("interval", new Schema.Int(1, 1200)),
                                            Schema.required(
                                                    "expect",
                                                    new Schema.Obj(
                                                            List.of(
                                                                    Schema.required("fold", HEX16),
                                                                    Schema.required(
                                                                            "phase",
                                                                            new Schema.Int(
                                                                                    0, 1199))))))),
                            Schema.required(
                                    "stream",
                                    rows(
                                            Schema.required("seed", HEX),
                                            Schema.required("chunk", CHUNK),
                                            Schema.required("steps", HEX),
                                            Schema.required("count", new Schema.Int(1, 64)),
                                            Schema.required(
                                                    "expect",
                                                    new Schema.Obj(
                                                            List.of(
                                                                    Schema.required("h", HEX16),
                                                                    Schema.required(
                                                                            "directions",
                                                                            new Schema.ListOf(
                                                                                    new Schema.Int(
                                                                                            0, 5),
                                                                                    1,
                                                                                    64,
                                                                                    false))))))),
                            Schema.required(
                                    "kernel",
                                    rows(
                                            Schema.required(
                                                    "world", new Schema.OneOf(Set.of("flat63"))),
                                            Schema.required("seed", HEX),
                                            Schema.required("seep", CELL),
                                            Schema.required("radius", new Schema.Int(1, 16)),
                                            Schema.required("walk", new Schema.Int(1, 64)),
                                            Schema.required("attempts", new Schema.Int(1, 64)),
                                            Schema.required(
                                                    "expect",
                                                    rows(
                                                            Schema.required("steps", HEX),
                                                            Schema.required(
                                                                    "result",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "convert",
                                                                                    "miss"))),
                                                            Schema.optional("at", CELL),
                                                            Schema.optional(
                                                                    "reason",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "none",
                                                                                    "unloaded"))),
                                                            Schema.required(
                                                                    "used",
                                                                    new Schema.Int(1, 64)))))),
                            Schema.required(
                                    "scheduler",
                                    rows(
                                            Schema.required(
                                                    "world", new Schema.OneOf(Set.of("flat63"))),
                                            Schema.required("seed", HEX),
                                            Schema.required("seep", CELL),
                                            Schema.required("radius", new Schema.Int(1, 16)),
                                            Schema.required("walk", new Schema.Int(1, 64)),
                                            Schema.optional(
                                                    "nodes", new Schema.ListOf(CELL, 0, 64, false)),
                                            Schema.required("flux", new Schema.Int(0, 1_000_000)),
                                            Schema.required("onset", new Schema.Int(0, 100_000)),
                                            Schema.required(
                                                    "block_cost", new Schema.Int(1, 10_000)),
                                            Schema.required(
                                                    "node_cost", new Schema.Int(1, 100_000)),
                                            Schema.required("every", new Schema.Int(1, 1000)),
                                            Schema.required(
                                                    "hollow_flux", new Schema.Int(0, 100_000)),
                                            Schema.required("visits", new Schema.Int(1, 64)),
                                            Schema.required(
                                                    "expect",
                                                    rows(
                                                            Schema.required(
                                                                    "visit", new Schema.Int(0, 63)),
                                                            Schema.required(
                                                                    "result",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "block", "node",
                                                                                    "miss",
                                                                                    "idle"))),
                                                            Schema.optional("at", CELL),
                                                            Schema.optional(
                                                                    "reason",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "none",
                                                                                    "unloaded"))),
                                                            Schema.optional(
                                                                    "used", new Schema.Int(1, 64)),
                                                            Schema.optional(
                                                                    "rise",
                                                                    new Schema.OneOf(
                                                                            Set.of(
                                                                                    "rose",
                                                                                    "no_rise"))),
                                                            Schema.required(
                                                                    "flux",
                                                                    new Schema.Int(0, 1_000_000)),
                                                            Schema.required("steps", HEX),
                                                            Schema.required("taken", HEX))))),
                            Schema.required(
                                    "hud",
                                    rows(
                                            Schema.required("onset", new Schema.Int(0, 100_000)),
                                            Schema.required(
                                                    "block_cost", new Schema.Int(1, 10_000)),
                                            Schema.required("flux", new Schema.Int(0, 1_000_000)),
                                            Schema.required(
                                                    "expect", new Schema.Int(0, 1_000_000)))),
                            Schema.required(
                                    "ball",
                                    rows(
                                            Schema.required("radius", new Schema.Int(1, 16)),
                                            Schema.required(
                                                    "expect",
                                                    new Schema.Obj(
                                                            List.of(
                                                                    Schema.required(
                                                                            "count",
                                                                            new Schema.Int(
                                                                                    1, 20_000)),
                                                                    Schema.required(
                                                                            "first",
                                                                            new Schema.ListOf(
                                                                                    ints(
                                                                                            3, -16,
                                                                                            16),
                                                                                    8,
                                                                                    8,
                                                                                    false)),
                                                                    Schema.required(
                                                                            "last",
                                                                            ints(3, -16, 16))))))))
                    .withExtra(HushData::fixtureRules);

    /** The spec's row counts per section: a missing row is rejected. */
    static final Map<String, Integer> FIXTURE_ROWS =
            Map.of(
                    "spill",
                    12,
                    "essentia",
                    15,
                    "phase",
                    4,
                    "stream",
                    1,
                    "kernel",
                    2,
                    "scheduler",
                    1,
                    "hud",
                    7,
                    "ball",
                    2);

    private static List<String> fixtureRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        FIXTURE_ROWS.forEach(
                (section, n) -> {
                    if (root.getAsJsonArray(section).size() != n) {
                        problems.add("$." + section + ": exactly " + n + " rows");
                    }
                });
        for (JsonElement e : root.getAsJsonArray("spill")) {
            JsonObject row = e.getAsJsonObject();
            JsonObject expect = row.getAsJsonObject("expect");
            int n = row.getAsJsonArray("base").size();
            if (expect.getAsJsonArray("effective").size() != n
                    || expect.getAsJsonArray("neutral").size() != n) {
                problems.add("$.spill: effective and neutral match base's length");
            }
            if (expect.has("surcharge") != row.get("kind").getAsString().equals("ordinary")) {
                problems.add("$.spill: surcharge iff ordinary");
            }
        }
        for (JsonElement e : root.getAsJsonArray("stream")) {
            JsonObject row = e.getAsJsonObject();
            if (row.getAsJsonObject("expect").getAsJsonArray("directions").size()
                    != row.get("count").getAsInt()) {
                problems.add("$.stream: count directions");
            }
        }
        for (JsonElement e : root.getAsJsonArray("kernel")) {
            JsonObject row = e.getAsJsonObject();
            JsonArray expect = row.getAsJsonArray("expect");
            if (expect.size() != row.get("attempts").getAsInt()) {
                problems.add("$.kernel: exactly attempts rows");
            }
            for (JsonElement x : expect) {
                JsonObject r = x.getAsJsonObject();
                boolean convert = r.get("result").getAsString().equals("convert");
                if (r.has("at") != convert || r.has("reason") == convert) {
                    problems.add("$.kernel: at iff convert, reason iff miss");
                }
            }
        }
        for (JsonElement e : root.getAsJsonArray("scheduler")) {
            JsonObject row = e.getAsJsonObject();
            JsonArray expect = row.getAsJsonArray("expect");
            if (expect.size() != row.get("visits").getAsInt()) {
                problems.add("$.scheduler: exactly visits rows");
            }
            for (JsonElement x : expect) {
                JsonObject r = x.getAsJsonObject();
                String result = r.get("result").getAsString();
                boolean target = result.equals("block") || result.equals("node");
                if (r.has("at") != target
                        || r.has("reason") != result.equals("miss")
                        || r.has("used") == result.equals("idle")
                        || (r.has("rise") && !result.equals("block"))) {
                    problems.add("$.scheduler: a conditional field present or absent wrongly");
                }
            }
        }
        return problems;
    }

    private static List<String> ingredients(JsonObject body) {
        List<String> out = new ArrayList<>();
        if (body.get("key") instanceof JsonObject key) {
            Map<Character, String> map = new TreeMap<>();
            key.entrySet().forEach(k -> map.put(k.getKey().charAt(0), k.getValue().getAsString()));
            if (body.get("pattern") instanceof JsonArray pattern) {
                for (JsonElement row : pattern) {
                    for (char c : row.getAsString().toCharArray()) {
                        if (c != ' ' && map.containsKey(c)) {
                            out.add(map.get(c));
                        }
                    }
                }
            }
        }
        if (body.get("ingredients") instanceof JsonArray list) {
            list.forEach(i -> out.add(i.getAsString()));
        }
        if (body.has("centre")) {
            out.add(body.get("centre").getAsString());
        }
        if (body.get("components") instanceof JsonArray components) {
            components.forEach(i -> out.add(i.getAsString()));
        }
        return out;
    }

    private static List<String> recipeRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonObject recipes = root.getAsJsonObject("recipes");
        JsonObject clearing = recipes.getAsJsonObject(CLEARING);
        JsonObject pealing = recipes.getAsJsonObject(PEALING);
        String c = "$.recipes." + CLEARING;
        expect(problems, c + ".research", clearing.get("research"), "vftv:clearing_bell");
        if (clearing.has("kind")) {
            expect(problems, c + ".kind", clearing.get("kind"), "ordinary");
        }
        expect(problems, c + ".result.id", clearing.getAsJsonObject("result").get("id"), CLEARING);
        if (clearing.getAsJsonObject("result").has("count")) {
            expect(
                    problems,
                    c + ".result.count",
                    clearing.getAsJsonObject("result").get("count"),
                    "1");
        }
        List<String> parts = ingredients(clearing);
        long brass = parts.stream().filter("vftv:resonant_brass_ingot"::equals).count();
        if (brass > 2) {
            problems.add(c + ": at most 2 vftv:resonant_brass_ingot");
        }
        parts.stream()
                .filter(i -> !CLEARING_INGREDIENTS.contains(i))
                .forEach(i -> problems.add(c + ": ingredient " + i + " is not allowed"));
        clearing.getAsJsonObject("vis")
                .entrySet()
                .forEach(
                        e -> {
                            if (!PRIMALS.contains(e.getKey())) {
                                problems.add(
                                        c + ".vis." + e.getKey() + ": not a Wellspring primal");
                            } else if (e.getValue().getAsInt() > 32) {
                                problems.add(c + ".vis." + e.getKey() + ": over 32");
                            }
                        });
        String p = "$.recipes." + PEALING;
        expect(problems, p + ".research", pealing.get("research"), "vftv:the_hush");
        if (pealing.has("kind")) {
            expect(problems, p + ".kind", pealing.get("kind"), "ordinary");
        }
        expect(problems, p + ".centre", pealing.get("centre"), "minecraft:bell");
        JsonArray components = pealing.getAsJsonArray("components");
        if (components.size() < 4 || components.size() > 8) {
            problems.add(p + ".components: 4 to 8");
        }
        for (JsonElement e : components) {
            String id = e.getAsString();
            boolean ok =
                    id.equals("vftv:resonant_brass_ingot")
                            || id.equals("minecraft:amethyst_shard")
                            || id.equals("#minecraft:wool")
                            || vanillaMembers("item", Identifier.withDefaultNamespace("wool"))
                                    .contains(id);
            if (!ok) {
                problems.add(p + ".components: " + id + " is not allowed");
            }
        }
        pealing.getAsJsonObject("essentia")
                .entrySet()
                .forEach(
                        e -> {
                            if (!e.getKey().equals("vftv:sonus")
                                    && !e.getKey().equals("vftv:ordo")) {
                                problems.add(
                                        p + ".essentia." + e.getKey() + ": only Sonus and Ordo");
                            } else if (e.getValue().getAsInt() < 1
                                    || e.getValue().getAsInt() > 32) {
                                problems.add(p + ".essentia." + e.getKey() + ": 1 to 32");
                            }
                        });
        if (pealing.get("instability").getAsInt() > 20) {
            problems.add(p + ".instability: 0 to 20");
        }
        JsonObject result = pealing.getAsJsonObject("result");
        if (!(result.get("item") instanceof JsonObject item)
                || !PEALING.equals(item.get("id").getAsString())
                || result.has("transmute")
                || result.has("enchantment")) {
            problems.add(p + ".result: one vftv:pealing_bell");
        }
        if (root.toString().contains("\"" + ASH + "\"")) {
            problems.add("$.recipes: no recipe may use vftv:hush_ash");
        }
        return problems;
    }

    private static void expect(
            List<String> problems, String at, @Nullable JsonElement value, String want) {
        if (value == null || !want.equals(value.getAsString())) {
            problems.add(at + ": must be " + want);
        }
    }

    private static Path contentRoot() {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException(
                    "the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        return Path.of(root);
    }

    /** Reads, checks and writes {@code content/hush}. */
    private static final class Content extends ContentProvider {
        private final CompletableFuture<HolderLookup.Provider> lookup;

        Content(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, DIR, List.of(RECIPES, NODE_TYPES, FIXTURES));
            this.lookup = lookup;
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            return lookup.thenCompose(registries -> generate(envelopes, cache, registries));
        }

        private CompletableFuture<?> generate(
                List<Envelope> envelopes, CachedOutput cache, HolderLookup.Provider registries) {
            Map<String, JsonObject> bySchema = new LinkedHashMap<>();
            List<String> problems = new ArrayList<>();
            for (Envelope e : envelopes) {
                if (bySchema.put(e.schema().id(), e.json().getAsJsonObject()) != null) {
                    problems.add("hush/" + e.path() + ": a second " + e.schema().id());
                }
            }
            for (Schema s : List.of(RECIPES, NODE_TYPES, FIXTURES)) {
                if (!bySchema.containsKey(s.id())) {
                    problems.add("hush: no " + s.id() + " file");
                }
            }
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected hush content:\n" + String.join("\n", problems));
            }
            JsonObject recipes =
                    java.util.Objects.requireNonNull(bySchema.get(RECIPES.id()))
                            .getAsJsonObject("recipes");
            Set<String> research = ResearchContent.read(contentRoot()).entries().keySet();
            JsonObject clearing = recipes.getAsJsonObject(CLEARING);
            JsonObject pealing = recipes.getAsJsonObject(PEALING);
            problems.addAll(ArcaneBodies.check(CLEARING, clearing, registries, research));
            problems.addAll(InfusionBodies.check(PEALING, pealing, registries, research));
            if (!problems.isEmpty()) {
                throw new IllegalStateException(
                        "Rejected hush content:\n" + String.join("\n", problems));
            }
            List<CompletableFuture<?>> writes = new ArrayList<>();
            writes.add(ArcaneBodies.write(cache, output, CLEARING, clearing));
            writes.add(InfusionBodies.write(cache, output, PEALING, pealing));
            java.util.Objects.requireNonNull(bySchema.get(NODE_TYPES.id()))
                    .getAsJsonObject("types")
                    .entrySet()
                    .forEach(
                            t -> {
                                Identifier id = Identifier.parse(t.getKey());
                                writes.add(
                                        DataProvider.saveStable(
                                                cache,
                                                t.getValue(),
                                                output.getOutputFolder(PackOutput.Target.DATA_PACK)
                                                        .resolve(id.getNamespace())
                                                        .resolve("vftv/node_type")
                                                        .resolve(id.getPath() + ".json")));
                            });
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "vftv hush content";
        }
    }

    /** {@code assets/vftv/particles/hush_mote.json}: the mote's four frames. */
    private record MoteProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            JsonArray textures = new JsonArray();
            for (int f = 0; f < 4; f++) {
                textures.add(HushContent.MOTE + "_" + f);
            }
            JsonObject json = new JsonObject();
            json.add("textures", textures);
            return DataProvider.saveStable(
                    cache,
                    json,
                    output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve("particles/hush_mote.json"));
        }

        @Override
        public String getName() {
            return "vftv hush mote";
        }
    }

    /** The reading frame's nine-slice metadata (16x16, border 4). */
    private record FrameProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            JsonObject scaling = new JsonObject();
            scaling.addProperty("type", "nine_slice");
            scaling.addProperty("width", 16);
            scaling.addProperty("height", 16);
            scaling.addProperty("border", 4);
            JsonObject gui = new JsonObject();
            gui.add("scaling", scaling);
            JsonObject json = new JsonObject();
            json.add("gui", gui);
            return DataProvider.saveStable(
                    cache,
                    json,
                    output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve("textures/gui/sprites/hud/hush_frame.png.mcmeta"));
        }

        @Override
        public String getName() {
            return "vftv hush frame";
        }
    }
}
