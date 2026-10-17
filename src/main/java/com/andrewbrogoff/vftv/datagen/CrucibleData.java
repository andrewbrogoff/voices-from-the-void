package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.crucible.CrucibleKeys;
import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.BuiltinAspects;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.crucible.CrucibleContent;
import com.andrewbrogoff.vftv.core.crucible.CrucibleLang;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipe;
import com.andrewbrogoff.vftv.core.crucible.CrucibleService;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleTuning;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The crucible feature's data generation (crucible spec, "Authored content" and the build-phase
 * data): the lang table, the mineable/heat/refuses/essentia tags, the self-dropping loot table,
 * {@code data/vftv/tuning/crucible.json}, and the two recipes of {@code content/crucible}.
 */
final class CrucibleData implements FeatureData {
    /** The authored directory under {@code content/}. */
    static final String DIR = "crucible";

    /** The crucible's own recipes. */
    static final String VESSEL = "vftv:crucible";

    /** Resonant Brass's recipe. */
    static final String BRASS = "vftv:resonant_brass_ingot";

    private static final Pattern ID = ArcaneBodies.ID;
    private static final Schema.Str INGREDIENT =
            new Schema.Str(3, 257, Pattern.compile("#?" + ID.pattern()));

    /** One {@code vftv:crucible} body as content writes it: a catalyst id or tag, no components. */
    static final Schema.Obj CRUCIBLE_BODY =
            new Schema.Obj(
                    List.of(
                            Schema.required("type", new Schema.OneOf(Set.of("vftv:crucible"))),
                            Schema.required("research", new Schema.Id()),
                            Schema.required("catalyst", INGREDIENT),
                            Schema.required(
                                    "aspects",
                                    new Schema.MapOf(
                                            new Schema.Str(3, 256, ID),
                                            new Schema.Int(
                                                    CrucibleLimits.MIN_COST,
                                                    CrucibleLimits.MAX_COST),
                                            1,
                                            CrucibleLimits.MAX_COST_ENTRIES)),
                            Schema.required(
                                    "result",
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.required("id", new Schema.Id()),
                                                    Schema.optional(
                                                            "count", new Schema.Int(1, 99)))))));

    /** {@code vftv:crucible_dose_plan/1}. */
    static final Schema DOSE_PLAN =
            Schema.of(
                            "vftv:crucible_dose_plan/1",
                            Schema.required("recipe", new Schema.Id()),
                            Schema.required(
                                    "doses",
                                    new Schema.ListOf(
                                            new Schema.Obj(
                                                    List.of(
                                                            Schema.required(
                                                                    "item", new Schema.Id()),
                                                            Schema.required(
                                                                    "count", new Schema.Int(1, 64)),
                                                            Schema.required(
                                                                    "why",
                                                                    new Schema.Str(1, 256, null)))),
                                            1,
                                            CrucibleLimits.MAX_DOSES,
                                            false)))
                    .withExtra(CrucibleData::doseRules);

    @Override
    public List<LangEntry> lang() {
        return CrucibleLang.TABLE;
    }

    @Override
    public void tags(com.andrewbrogoff.vftv.datagen.Tags tags) {
        tags.tag(BlockTags.MINEABLE_WITH_PICKAXE).add(CrucibleContent.CRUCIBLE_BLOCK_KEY);
        tags.tag(CrucibleKeys.CRUCIBLE_HEAT)
                .addTag(BlockTags.FIRE)
                .addTag(BlockTags.CAMPFIRES)
                .add(key(Blocks.LAVA))
                .add(key(Blocks.MAGMA_BLOCK));
        // Defaults name items other features register; optional until they exist.
        tags.tag(CrucibleKeys.CRUCIBLE_REFUSES)
                .addOptional(item("wand"))
                .addOptional(item("bell_jar"))
                .addOptional(item("commonplace_book"))
                .addOptional(item("essentia_jar"));
        tags.tag(blockTag("essentia_endpoints")).add(CrucibleContent.CRUCIBLE_BLOCK_KEY);
        tags.tag(blockTag("alembic_bases")).add(CrucibleContent.CRUCIBLE_BLOCK_KEY);
        tags.tag(Tags.Items.INGOTS).add(CrucibleContent.INGOT_KEY);
        tags.tag(CrucibleKeys.RESONANT_BRASS_INGOTS).add(CrucibleContent.INGOT_KEY);
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
                                        dropSelf(CrucibleContent.crucible());
                                    }

                                    @Override
                                    protected Iterable<Block> getKnownBlocks() {
                                        return List.of(CrucibleContent.crucible());
                                    }
                                },
                        LootContextParamSets.BLOCK));
    }

    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(TuningProvider::new);
        event.createProvider(Recipes::new);
        event.createProvider(LiquidProvider::new);
    }

    /** The liquid sprite's animation: 4 frames of 5 ticks, no interpolation. */
    private record LiquidProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            JsonObject animation = new JsonObject();
            animation.addProperty("frametime", 5);
            animation.addProperty("interpolate", false);
            JsonObject json = new JsonObject();
            json.add("animation", animation);
            return DataProvider.saveStable(
                    cache,
                    json,
                    output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve("textures/block/crucible_liquid.png.mcmeta"));
        }

        @Override
        public String getName() {
            return "vftv crucible liquid animation";
        }
    }

    private static net.minecraft.resources.ResourceKey<Block> key(Block block) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getResourceKey(block)
                .orElseThrow();
    }

    private static net.minecraft.resources.ResourceKey<Item> item(String path) {
        return net.minecraft.resources.ResourceKey.create(
                Registries.ITEM, Identifier.fromNamespaceAndPath(Vftv.MOD_ID, path));
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Vftv.MOD_ID, path));
    }

    /** {@code vftv:crucible_recipes/1} with its "Rejected:" rules for this Wellspring bound. */
    static Schema recipesSchema(Map<String, Integer> wellspring) {
        return Schema.of(
                        "vftv:crucible_recipes/1",
                        Schema.required(
                                "recipes",
                                new Schema.Obj(
                                        List.of(
                                                Schema.required(VESSEL, ArcaneBodies.BODY),
                                                Schema.required(BRASS, CRUCIBLE_BODY)))))
                .withExtra(root -> recipeRules(root, wellspring));
    }

    private static List<String> recipeRules(JsonObject root, Map<String, Integer> wellspring) {
        JsonObject recipes = root.getAsJsonObject("recipes");
        List<String> problems = new ArrayList<>();
        JsonObject vessel = recipes.getAsJsonObject(VESSEL);
        String at = "$.recipes." + VESSEL;
        expect(problems, at + ".research", vessel.get("research"), "vftv:crucible");
        if (vessel.has("kind")) {
            expect(problems, at + ".kind", vessel.get("kind"), "ordinary");
        }
        expect(problems, at + ".result.id", vessel.getAsJsonObject("result").get("id"), VESSEL);
        if (vessel.getAsJsonObject("result").has("count")) {
            expect(
                    problems,
                    at + ".result.count",
                    vessel.getAsJsonObject("result").get("count"),
                    "1");
        }
        List<String> ingredients = new ArrayList<>();
        if (vessel.has("key")) {
            vessel.getAsJsonObject("key")
                    .asMap()
                    .values()
                    .forEach(i -> ingredients.add(i.getAsString()));
        }
        if (vessel.has("ingredients")) {
            vessel.getAsJsonArray("ingredients").forEach(i -> ingredients.add(i.getAsString()));
        }
        if (!ingredients.contains("minecraft:cauldron")) {
            problems.add(at + ": needs a minecraft:cauldron ingredient");
        }
        ingredients.stream()
                .filter(i -> !i.startsWith("minecraft:") && !i.startsWith("#minecraft:"))
                .forEach(i -> problems.add(at + ": " + i + " is not a minecraft: item or tag"));
        vessel.getAsJsonObject("vis")
                .entrySet()
                .forEach(
                        e -> {
                            Integer bound = wellspring.get(e.getKey());
                            if (!ArcaneData.PRIMALS.contains(e.getKey()) || bound == null) {
                                problems.add(at + ".vis." + e.getKey() + ": not a built-in primal");
                            } else if (e.getValue().getAsInt() > bound) {
                                problems.add(
                                        at
                                                + ".vis."
                                                + e.getKey()
                                                + ": over the Wellspring's "
                                                + bound);
                            }
                        });
        problems.addAll(brassRules(recipes.getAsJsonObject(BRASS)));
        return problems;
    }

    private static List<String> brassRules(JsonObject brass) {
        List<String> problems = new ArrayList<>();
        String at = "$.recipes." + BRASS;
        expect(problems, at + ".research", brass.get("research"), "vftv:resonant_brass");
        expect(problems, at + ".catalyst", brass.get("catalyst"), "minecraft:copper_ingot");
        expect(problems, at + ".result.id", brass.getAsJsonObject("result").get("id"), BRASS);
        if (brass.getAsJsonObject("result").has("count")) {
            expect(
                    problems,
                    at + ".result.count",
                    brass.getAsJsonObject("result").get("count"),
                    "1");
        }
        JsonObject aspects = brass.getAsJsonObject("aspects");
        int total = aspects.asMap().values().stream().mapToInt(JsonElement::getAsInt).sum();
        int quarter = CrucibleTuning.DEFAULTS.capacity() / 4;
        if (aspects.size() > 3) {
            problems.add(at + ".aspects: at most 3 aspects");
        }
        if (total > quarter) {
            problems.add(
                    at
                            + ".aspects: total "
                            + total
                            + " over a quarter of the capacity, "
                            + quarter);
        }
        Set<String> builtIn = new HashSet<>();
        BuiltinAspects.ALL.keySet().forEach(k -> builtIn.add(AspectSet.text(k)));
        aspects.keySet().stream()
                .filter(a -> !builtIn.contains(a))
                .forEach(a -> problems.add(at + ".aspects." + a + ": not a built-in aspect"));
        return problems;
    }

    private static List<String> doseRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        expect(problems, "$.recipe", root.get("recipe"), BRASS);
        root.getAsJsonArray("doses")
                .forEach(
                        d -> {
                            String item = d.getAsJsonObject().get("item").getAsString();
                            if (!item.startsWith("minecraft:")) {
                                problems.add("$.doses: " + item + " is not a minecraft: item");
                            }
                            if (d.getAsJsonObject().get("why").getAsString().isBlank()) {
                                problems.add("$.doses: " + item + " has an empty why");
                            }
                        });
        return problems;
    }

    private static void expect(List<String> problems, String at, JsonElement value, String want) {
        if (!want.equals(value.getAsString())) {
            problems.add(at + ": must be " + want);
        }
    }

    /** {@code data/vftv/tuning/crucible.json}, every key at its default. */
    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    CrucibleTuning.DEFAULTS.toJson(),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(CrucibleService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv crucible tuning";
        }
    }

    /** Reads, checks and writes {@code content/crucible}. */
    private static final class Recipes extends ContentProvider {
        private final CompletableFuture<HolderLookup.Provider> lookup;

        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(
                    output,
                    DIR,
                    List.of(recipesSchema(ArcaneData.wellspring(contentRoot())), DOSE_PLAN));
            this.lookup = lookup;
        }

        private static Path contentRoot() {
            String root = System.getProperty(CONTENT_DIR);
            if (root == null) {
                throw new IllegalStateException(
                        "the system property " + CONTENT_DIR + " is not set");
            }
            return Path.of(root);
        }

        @Override
        protected CompletableFuture<?> generate(List<Envelope> envelopes, CachedOutput cache) {
            return lookup.thenCompose(
                    registries -> {
                        Map<String, JsonObject> arcane = new LinkedHashMap<>();
                        Map<String, JsonObject> crucible = new LinkedHashMap<>();
                        List<String> problems = new ArrayList<>();
                        Set<String> research =
                                envelopes.isEmpty()
                                        ? Set.of()
                                        : ResearchContent.read(contentRoot()).entries().keySet();
                        for (Envelope envelope : envelopes) {
                            JsonObject json = envelope.json().getAsJsonObject();
                            if (!json.has("recipes")) {
                                continue; // the dose plan generates nothing
                            }
                            JsonObject recipes = json.getAsJsonObject("recipes");
                            JsonObject vessel = recipes.getAsJsonObject(VESSEL);
                            problems.addAll(
                                    ArcaneBodies.check(VESSEL, vessel, registries, research));
                            arcane.put(VESSEL, vessel);
                            JsonObject brass = recipes.getAsJsonObject(BRASS);
                            problems.addAll(check(BRASS, brass, registries));
                            if (!research.contains(brass.get("research").getAsString())) {
                                problems.add(
                                        "recipe "
                                                + BRASS
                                                + ": unknown research entry "
                                                + brass.get("research").getAsString());
                            }
                            crucible.put(BRASS, brass);
                        }
                        if (!problems.isEmpty()) {
                            throw new IllegalStateException(
                                    "Rejected crucible content:\n" + String.join("\n", problems));
                        }
                        List<CompletableFuture<?>> writes = new ArrayList<>();
                        arcane.forEach(
                                (id, body) ->
                                        writes.add(ArcaneBodies.write(cache, output, id, body)));
                        crucible.forEach(
                                (id, body) ->
                                        writes.add(
                                                DataProvider.saveStable(
                                                        cache,
                                                        widen(body),
                                                        output.createRegistryElementsPathProvider(
                                                                        Registries.RECIPE)
                                                                .json(Identifier.parse(id)))));
                        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
                    });
        }

        @Override
        public String getName() {
            return "vftv crucible recipes";
        }
    }

    /**
     * A crucible body's load validation through its codec (known items, the result, the cost) with
     * every aspect built in; the research entry is the runtime decode's to check.
     */
    static List<String> check(String id, JsonObject body, HolderLookup.Provider registries) {
        String where = "recipe " + id + ": ";
        List<String> problems = new ArrayList<>();
        JsonObject untagged = body.deepCopy();
        if (untagged.get("catalyst").getAsString().startsWith("#")) {
            untagged.add("catalyst", new JsonPrimitive("minecraft:stick"));
        }
        CrucibleRecipe.BODY_CODEC
                .codec()
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), untagged)
                .ifError(e -> problems.add(where + e.message()))
                .ifSuccess(
                        recipe -> {
                            Set<String> builtIn = new HashSet<>();
                            BuiltinAspects.ALL
                                    .keySet()
                                    .forEach(k -> builtIn.add(AspectSet.text(k)));
                            recipe.cost().keySet().stream()
                                    .filter(a -> !builtIn.contains(a))
                                    .forEach(a -> problems.add(where + "unknown aspect " + a));
                            if (recipe.result().item().areComponentsBound()) {
                                recipe.createResult()
                                        .ifError(e -> problems.add(where + e.message()));
                            }
                        });
        return problems;
    }

    /** The compatibility spec's widening applied to the catalyst. */
    static JsonObject widen(JsonObject body) {
        JsonObject copy = body.deepCopy();
        TagKey<Item> tag = ArcaneBodies.WIDENED.get(copy.get("catalyst").getAsString());
        if (tag != null) {
            copy.add("catalyst", new JsonPrimitive("#" + tag.location()));
        }
        return copy;
    }
}
