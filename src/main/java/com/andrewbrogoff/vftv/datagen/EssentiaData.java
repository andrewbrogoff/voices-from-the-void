package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.EssentiaService;
import com.andrewbrogoff.vftv.core.essentia.EssentiaTubeBlock;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.DynamicLoot;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The essentia feature's data generation (essentia spec, "Authored content"): the lang table, the
 * two block tags and the pickaxe tag, the block loot tables, {@code
 * data/vftv/tuning/essentia.json}, the recipes from {@code content/essentia/recipes.json} and
 * {@code assets/vftv/essentia/client.json} from {@code content/essentia/client.json}. Listed once
 * in {@link VftvData#FEATURES}.
 */
final class EssentiaData implements FeatureData {
    private static final String FILE = "essentia.json";
    private static final String DIR = "essentia";
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern INGREDIENT = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Set<String> PRIMALS =
            Set.of(
                    "vftv:aer",
                    "vftv:terra",
                    "vftv:ignis",
                    "vftv:aqua",
                    "vftv:ordo",
                    "vftv:perditio");

    /** The Wellspring's per-aspect amount, the arcane bodies' vis bound (wands content). */
    static final int WELLSPRING = 32;

    private static final Schema.Type ING = new Schema.Str(3, 257, INGREDIENT);
    private static final Schema.Type RESULT =
            new Schema.Obj(
                    List.of(
                            Schema.required("id", new Schema.Id()),
                            Schema.optional("count", new Schema.Int(1, 99))));

    /** The arcane recipe type's body (arcane spec, "The recipe type"). */
    private static final Schema.Type ARCANE =
            new Schema.Obj(
                    List.of(
                            Schema.required("type", new Schema.OneOf(Set.of("vftv:arcane"))),
                            Schema.required("research", new Schema.Id()),
                            Schema.optional("kind", new Schema.OneOf(Set.of("ordinary"))),
                            Schema.optional(
                                    "pattern",
                                    new Schema.ListOf(new Schema.Str(1, 3, null), 1, 3, false)),
                            Schema.optional(
                                    "key",
                                    new Schema.MapOf(
                                            new Schema.Str(1, 1, Pattern.compile("[^ ]")),
                                            ING,
                                            1,
                                            9)),
                            Schema.optional("ingredients", new Schema.ListOf(ING, 1, 9, false)),
                            Schema.required("result", RESULT),
                            Schema.required(
                                    "vis",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Int(1, WELLSPRING),
                                            1,
                                            8))));

    /** Vanilla's shapeless body. */
    private static final Schema.Type SHAPELESS =
            new Schema.Obj(
                    List.of(
                            Schema.required(
                                    "type",
                                    new Schema.OneOf(Set.of("minecraft:crafting_shapeless"))),
                            Schema.optional(
                                    "category",
                                    new Schema.OneOf(
                                            Set.of("misc", "building", "redstone", "equipment"))),
                            Schema.required("ingredients", new Schema.ListOf(ING, 1, 9, false)),
                            Schema.required("result", RESULT)));

    /**
     * {@code vftv:essentia_recipes/1}: exactly the six recipes. Rejected: a research, kind or
     * result outside its row; a vis key that isn't a built-in primal; a jar or Alembic ingredient
     * outside {@code minecraft:}; label ingredients other than paper and an ink sac; a blanking
     * recipe with any ingredient but one label; a malformed pattern.
     */
    static final Schema RECIPES =
            Schema.of(
                            "vftv:essentia_recipes/1",
                            Schema.required(
                                    "recipes",
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required("vftv:essentia_jar", ARCANE),
                                                    Schema.required("vftv:alembic", ARCANE),
                                                    Schema.required("vftv:essentia_tube", ARCANE),
                                                    Schema.required("vftv:tube_valve", ARCANE),
                                                    Schema.required("vftv:label", SHAPELESS),
                                                    Schema.required(
                                                            "vftv:label_blank", SHAPELESS)))))
                    .withExtra(EssentiaData::recipeRules);

    /** {@code vftv:essentia_client/1}: the mote numbers, both required. */
    static final Schema CLIENT =
            Schema.of(
                    "vftv:essentia_client/1",
                    Schema.required(
                            "mote",
                            new Schema.Obj(
                                    List.of(
                                            Schema.required("speed", new Schema.Num(0.05, 2.0)),
                                            Schema.required(
                                                    "size", new Schema.Num(0.0625, 1.0))))));

    @Override
    public List<LangEntry> lang() {
        List<LangEntry> t = new ArrayList<>();
        for (String block : List.of("essentia_jar", "alembic", "essentia_tube", "tube_valve")) {
            t.add(authored("block.vftv." + block, 0));
        }
        t.add(authored("item.vftv.label", 0));
        t.add(authored("tooltip.vftv.label.blank", 0));
        t.add(authored("tooltip.vftv.label.written", 1));
        t.add(authored("tooltip.vftv.essentia_jar.holds", 2));
        t.add(authored("tooltip.vftv.essentia_jar.labelled", 1));
        t.add(authored("tooltip.vftv.essentia_jar.unreadable", 0));
        for (String m :
                List.of(
                        "label_unreadable",
                        "cut_label_first",
                        "nothing_to_name",
                        "label_mismatch",
                        "no_base")) {
            t.add(authored("message.vftv.essentia." + m, 0));
        }
        t.add(authored("message.vftv.essentia.line_too_long", 1));
        t.add(authored("status.vftv.essentia_jar.contents", 3));
        t.add(authored("status.vftv.essentia_jar.empty", 1));
        t.add(authored("status.vftv.essentia_jar.unreadable", 0));
        t.add(authored("status.vftv.essentia_jar.ranks", 3));
        for (String s : List.of("building", "ready", "asleep", "incomplete", "overgrown")) {
            t.add(authored("status.vftv.essentia_line." + s, 4));
        }
        t.add(authored("status.vftv.essentia_line.last", 3));
        t.add(authored("status.vftv.essentia_line.no_moves", 0));
        t.add(authored("status.vftv.essentia_line.faults", 3));
        t.add(authored("status.vftv.essentia_tube.label", 1));
        t.add(authored("status.vftv.alembic.ends", 2));
        t.add(authored("status.vftv.alembic.next", 2));
        for (String e :
                List.of("unheard", "missing", "nothing", "label_none", "label_unreadable")) {
            t.add(authored("essentia.vftv." + e, 0));
        }
        for (String s :
                List.of(
                        "jar_fill",
                        "label_apply",
                        "label_cut",
                        "valve_open",
                        "valve_close",
                        "alembic_drip")) {
            t.add(authored("subtitles.vftv.essentia." + s, 0));
        }
        return List.copyOf(t);
    }

    private static LangEntry authored(String key, int args) {
        return LangEntry.authored(key, args, FILE, LangEntry.Kind.TEMPLATE);
    }

    @Override
    public void tags(Tags tags) {
        tags.tag(EssentiaKeys.ESSENTIA_ENDPOINTS)
                .add(key(EssentiaContent.jar()))
                .addOptional(blockKey("crucible"))
                .addOptional(blockKey("node_tap"));
        tags.tag(EssentiaKeys.ALEMBIC_BASES).addOptional(blockKey("crucible"));
        tags.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(key(EssentiaContent.tube()))
                .add(key(EssentiaContent.valve()));
    }

    private static ResourceKey<Block> key(Block block) {
        return BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow();
    }

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(
                Registries.BLOCK, Identifier.fromNamespaceAndPath(Vftv.MOD_ID, path));
    }

    @Override
    public List<LootTableProvider.SubProviderEntry> loot() {
        return List.of(
                new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK));
    }

    /** The four blocks' loot: the jar keeps its components; tubes and Alembics drop their label. */
    private static final class Loot extends BlockLootSubProvider {
        Loot(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            Block jar = EssentiaContent.jar();
            add(
                    jar,
                    LootTable.lootTable()
                            .withPool(
                                    LootPool.lootPool()
                                            .setRolls(ConstantValue.exactly(1.0F))
                                            .add(
                                                    LootItem.lootTableItem(jar)
                                                            .apply(
                                                                    CopyComponentsFunction
                                                                            .copyComponentsFromBlockEntity(
                                                                                    LootContextParams
                                                                                            .BLOCK_ENTITY)
                                                                            .include(
                                                                                    EssentiaContent
                                                                                            .contents())
                                                                            .include(
                                                                                    EssentiaContent
                                                                                            .labelComponent())))));
            dropSelf(EssentiaContent.valve());
            for (Block block : List.of(EssentiaContent.tube(), EssentiaContent.alembic())) {
                add(
                        block,
                        createSingleItemTable(block)
                                .withPool(
                                        applyExplosionCondition(
                                                block,
                                                LootPool.lootPool()
                                                        .setRolls(ConstantValue.exactly(1.0F))
                                                        .add(
                                                                DynamicLoot.dynamicEntry(
                                                                        EssentiaTubeBlock
                                                                                .LABEL_DROP)))));
            }
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(
                    EssentiaContent.jar(),
                    EssentiaContent.alembic(),
                    EssentiaContent.tube(),
                    EssentiaContent.valve());
        }
    }

    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(TuningProvider::new);
        event.createProvider(Content::new);
    }

    /** {@code data/vftv/tuning/essentia.json}, every key at its default. */
    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    EssentiaTuning.DEFAULTS.toJson(),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(EssentiaService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv essentia tuning";
        }
    }

    /**
     * {@code content/essentia/}: the vanilla recipe bodies unchanged, and the client numbers. The
     * four arcane bodies are validated here and written once the arcane recipe type exists.
     */
    private static final class Content extends ContentProvider {
        Content(PackOutput output) {
            super(output, DIR, List.of(RECIPES, CLIENT));
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            List<CompletableFuture<?>> writes = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Envelope e : envelopes) {
                JsonObject root = e.json().getAsJsonObject();
                seen.add(e.schema().id());
                if (e.schema().equals(CLIENT)) {
                    JsonObject copy = root.deepCopy();
                    writes.add(
                            DataProvider.saveStable(
                                    cache,
                                    copy,
                                    output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                                            .resolve(Vftv.MOD_ID)
                                            .resolve("essentia/client.json")));
                    continue;
                }
                PackOutput.PathProvider recipes =
                        output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
                for (Map.Entry<String, JsonElement> r :
                        root.getAsJsonObject("recipes").entrySet()) {
                    JsonObject body = r.getValue().getAsJsonObject();
                    if (!"vftv:arcane".equals(body.get("type").getAsString())) {
                        writes.add(
                                DataProvider.saveStable(
                                        cache, body, recipes.json(Identifier.parse(r.getKey()))));
                    }
                }
            }
            if (seen.size() != 2) {
                throw new IllegalStateException(
                        "content/essentia needs recipes.json and client.json");
            }
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "vftv essentia content";
        }
    }

    // --- the recipe rules beyond the schema --------------------------------------------------

    private static List<String> recipeRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        JsonObject recipes = root.getAsJsonObject("recipes");
        row(
                recipes,
                "vftv:essentia_jar",
                "vftv:essentia_jars",
                "vftv:essentia_jar",
                1,
                4,
                true,
                problems);
        row(recipes, "vftv:alembic", "vftv:essentia_jars", "vftv:alembic", 1, 1, true, problems);
        row(
                recipes,
                "vftv:essentia_tube",
                "vftv:essentia_tubes",
                "vftv:essentia_tube",
                4,
                16,
                false,
                problems);
        row(
                recipes,
                "vftv:tube_valve",
                "vftv:essentia_tubes",
                "vftv:tube_valve",
                1,
                4,
                false,
                problems);
        JsonObject label = recipes.getAsJsonObject("vftv:label");
        result(label, "vftv:label", 1, 8, "$.recipes.vftv:label", problems);
        List<String> ings = new ArrayList<>();
        label.getAsJsonArray("ingredients").forEach(i -> ings.add(i.getAsString()));
        if (ings.size() != 2
                || !ings.containsAll(List.of("minecraft:paper", "minecraft:ink_sac"))) {
            problems.add(
                    "$.recipes.vftv:label.ingredients: exactly minecraft:paper and minecraft:ink_sac");
        }
        JsonObject blank = recipes.getAsJsonObject("vftv:label_blank");
        result(blank, "vftv:label", 1, 1, "$.recipes.vftv:label_blank", problems);
        if (blank.getAsJsonArray("ingredients").size() != 1
                || !"vftv:label".equals(blank.getAsJsonArray("ingredients").get(0).getAsString())) {
            problems.add("$.recipes.vftv:label_blank.ingredients: exactly one vftv:label");
        }
        return problems;
    }

    private static void row(
            JsonObject recipes,
            String id,
            String research,
            String result,
            int min,
            int max,
            boolean vanillaOnly,
            List<String> problems) {
        String at = "$.recipes." + id;
        JsonObject body = recipes.getAsJsonObject(id);
        if (!research.equals(body.get("research").getAsString())) {
            problems.add(at + ".research: must be " + research);
        }
        result(body, result, min, max, at, problems);
        boolean shaped = body.has("pattern") || body.has("key");
        if (shaped == body.has("ingredients")
                || (shaped && !(body.has("pattern") && body.has("key")))) {
            problems.add(at + ": exactly one of pattern+key or ingredients");
        }
        List<String> ingredients = new ArrayList<>();
        if (body.has("ingredients")) {
            body.getAsJsonArray("ingredients").forEach(i -> ingredients.add(i.getAsString()));
        }
        if (shaped && body.has("pattern") && body.has("key")) {
            Set<String> keys = new HashSet<>(body.getAsJsonObject("key").keySet());
            Set<String> used = new HashSet<>();
            int width = -1;
            for (JsonElement row : body.getAsJsonArray("pattern")) {
                String s = row.getAsString();
                if (width >= 0 && s.length() != width) {
                    problems.add(at + ".pattern: rows of unequal width");
                }
                width = s.length();
                for (int k = 0; k < s.length(); k++) {
                    if (s.charAt(k) != ' ') {
                        used.add(String.valueOf(s.charAt(k)));
                    }
                }
            }
            if (!keys.equals(used)) {
                problems.add(at + ".key: every symbol keyed and every key used");
            }
            body.getAsJsonObject("key")
                    .entrySet()
                    .forEach(k -> ingredients.add(k.getValue().getAsString()));
        }
        if (vanillaOnly) {
            for (String i : ingredients) {
                if (!i.startsWith("minecraft:") && !i.startsWith("#minecraft:")) {
                    problems.add(at + ": ingredient " + i + " is not a minecraft: item or tag");
                }
            }
        }
        for (String aspect : body.getAsJsonObject("vis").keySet()) {
            if (!PRIMALS.contains(aspect)) {
                problems.add(at + ".vis." + aspect + ": not a built-in primal");
            }
        }
    }

    private static void result(
            JsonObject body, String id, int min, int max, String at, List<String> problems) {
        JsonObject result = body.getAsJsonObject("result");
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        if (!id.equals(result.get("id").getAsString())) {
            problems.add(at + ".result.id: must be " + id);
        }
        if (count < min || count > max) {
            problems.add(at + ".result.count: outside " + min + ".." + max);
        }
    }
}
