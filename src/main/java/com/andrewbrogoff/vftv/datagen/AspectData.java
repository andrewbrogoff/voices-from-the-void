package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDataMaps;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.aspect.AspectConfig;
import com.andrewbrogoff.vftv.core.aspect.AspectContent;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.BuiltinAspects;
import com.andrewbrogoff.vftv.core.aspect.DerivationRecipes;
import com.andrewbrogoff.vftv.core.aspect.ScanningService;
import com.andrewbrogoff.vftv.core.aspect.model.ScanningTuning;
import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.packs.VanillaRecipeProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jspecify.annotations.Nullable;

/**
 * The aspect feature's data generation: the 37 built-in {@code vftv:aspect} entries, the lang
 * table, the Tuning Fork's recipe, its three sound events, {@code data/vftv/tuning/scanning.json},
 * and the aspect data maps from every {@code content/aspects/**.json} plus the vanilla recipe
 * derivation over them, behind the coverage gates ({@link AspectTable}). Listed once in {@link
 * VftvData#FEATURES}.
 */
final class AspectData implements FeatureData {
    /** The authored aspect values' directory under {@code content/}. */
    static final String DIR = "aspects";

    /** The value tables of an envelope. */
    static final List<String> TABLES = List.of("items", "blocks", "entities", "defaults");

    /** The configuration spec's file for the key's translations. */
    private static final String CONFIG = "config.json";

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern KEY = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Schema.MapOf VALUES =
            new Schema.MapOf(new Schema.Str(3, 256, ID), new Schema.Int(1, 1_000_000), 0, 5);
    private static final Schema.MapOf TABLE =
            new Schema.MapOf(new Schema.Str(3, 257, KEY), VALUES, 0, 100_000);
    private static final Schema.MapOf REASONS =
            new Schema.MapOf(new Schema.Str(3, 257, KEY), new Schema.Str(3, 200, null), 0, 100_000);

    /**
     * {@code vftv:aspects/1}: an item, block, entity and defaults table, each from an id ({@code
     * ns:path}) or tag ({@code #ns:path}) to a value of at most 5 aspects; the reviewed {@code
     * exempt} lists (items, blocks, entities, each key to a one-line reason); and an {@code about}
     * text documenting the file. Rejected: a defaults key that is not a tag; an aspect that is not
     * built in; one key twice across every file, or one object covered by two keys of a map.
     */
    static final Schema SCHEMA =
            Schema.of(
                            "vftv:aspects/1",
                            Schema.optional("about", new Schema.Str(1, 4000, null)),
                            Schema.optional("items", TABLE),
                            Schema.optional("blocks", TABLE),
                            Schema.optional("entities", TABLE),
                            Schema.optional("defaults", TABLE),
                            Schema.optional(
                                    "exempt",
                                    new Schema.Obj(
                                            List.of(
                                                    Schema.optional("items", REASONS),
                                                    Schema.optional("blocks", REASONS),
                                                    Schema.optional("entities", REASONS)))))
                    .withExtra(AspectData::extraRules);

    @Override
    public List<LangEntry> lang() {
        List<LangEntry> lang = new ArrayList<>(AspectLang.TABLE);
        lang.add(LangEntry.codeDefined("options.vftv.aspects", 0, CONFIG, "Aspects"));
        lang.add(
                LangEntry.codeDefined(
                        "options.vftv.aspects.derive_modded", 0, CONFIG, "Derive modded aspects"));
        lang.add(
                LangEntry.codeDefined(
                        "options.vftv.aspects.derive_modded.tooltip",
                        0,
                        CONFIG,
                        AspectConfig.DERIVE_MODDED_COMMENT));
        return lang;
    }

    @Override
    public void datapackEntries(DatapackEntries entries) {
        entries.add(
                AspectKeys.REGISTRY,
                context ->
                        BuiltinAspects.ALL.forEach((key, entry) -> context.register(key, entry)));
    }

    @Override
    public void recipes(HolderLookup.Provider registries, RecipeOutput output) {
        ShapelessRecipeBuilder.shapeless(
                        registries.lookupOrThrow(Registries.ITEM),
                        RecipeCategory.TOOLS,
                        AspectContent.tuningFork())
                .requires(Items.COPPER_INGOT)
                .requires(Items.AMETHYST_SHARD)
                .unlockedBy(
                        "has_amethyst_shard",
                        InventoryChangeTrigger.TriggerInstance.hasItems(Items.AMETHYST_SHARD))
                .save(
                        output,
                        ResourceKey.create(
                                Registries.RECIPE, AspectContent.TUNING_FORK_KEY.identifier()));
    }

    @Override
    public void dataMaps(DataMapProvider maps, HolderLookup.Provider registries) {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException(
                    "the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        AspectTable table = new AspectTable();
        table.read(ContentProvider.read(Path.of(root).resolve(DIR), Map.of(SCHEMA.id(), SCHEMA)));
        SortedMap<String, SortedMap<String, Integer>> derived =
                table.derive(DerivationRecipes.capture(capture(registries), registries));
        add(maps, AspectDataMaps.ITEM, Registries.ITEM, table.table("items"), derived);
        add(maps, AspectDataMaps.BLOCK, Registries.BLOCK, table.table("blocks"), Map.of());
        add(
                maps,
                AspectDataMaps.ENTITY_TYPE,
                Registries.ENTITY_TYPE,
                table.table("entities"),
                Map.of());
        add(maps, AspectDataMaps.DEFAULTS, Registries.ITEM, table.table("defaults"), Map.of());
    }

    @Override
    public void providers(GatherDataEvent event) {
        bindTags(event.getResourceManager(PackType.SERVER_DATA));
        event.createProvider(TuningProvider::new);
        event.createProvider(FrameProvider::new);
    }

    /**
     * Binds vanilla's and NeoForge's tags into the static registries, as a server's data load does,
     * so the captured recipes' tag ingredients and the tables' tag keys have members. Datagen loads
     * no data otherwise; the generated mod's own tags are not among them.
     */
    private static void bindTags(ResourceManager data) {
        TagLoader.loadTagsForExistingRegistries(
                        data, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
                .forEach(Registry.PendingTags::apply);
    }

    /**
     * Every vanilla recipe, through a small subclass of {@code VanillaRecipeProvider} feeding a
     * capturing output, and every VFTV recipe the features write.
     */
    private static List<RecipeHolder<?>> capture(HolderLookup.Provider datagen) {
        // Datagen's item lookup hands out tag sets that refuse their members; the static registry,
        // with the tags bound above, is what a server's recipes hold.
        HolderLookup.Provider registries =
                new HolderLookup.Provider() {
                    @Override
                    public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                        return datagen.listRegistryKeys();
                    }

                    @Override
                    @SuppressWarnings("unchecked") // the key is the item registry's, so T is Item
                    public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(
                            ResourceKey<? extends Registry<? extends T>> key) {
                        return key.equals(Registries.ITEM)
                                ? Optional.of(
                                        (HolderLookup.RegistryLookup<T>) BuiltInRegistries.ITEM)
                                : datagen.lookup(key);
                    }
                };
        List<RecipeHolder<?>> holders = new ArrayList<>();
        RecipeOutput output =
                new RecipeOutput() {
                    @Override
                    public void accept(
                            ResourceKey<Recipe<?>> id,
                            Recipe<?> recipe,
                            @Nullable AdvancementHolder advancement,
                            ICondition... conditions) {
                        holders.add(new RecipeHolder<>(id, recipe));
                    }

                    @Override
                    public Advancement.Builder advancement() {
                        return Advancement.Builder.recipeAdvancement();
                    }

                    @Override
                    public void includeRootAdvancement() {}
                };
        new VanillaRecipeProvider(registries, output) {
            {
                buildRecipes();
            }
        };
        VftvData.FEATURES.forEach(f -> f.recipes(registries, output));
        return holders;
    }

    /** The shared card frame's nine-slice metadata (16x16, border 4). */
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
                            .resolve("textures/gui/sprites/hud/vis_frame.png.mcmeta"));
        }

        @Override
        public String getName() {
            return "vftv card frame";
        }
    }

    private static <R> void add(
            DataMapProvider maps,
            DataMapType<R, AspectValues> type,
            ResourceKey<? extends Registry<R>> registry,
            SortedMap<String, AspectTable.Entry> authored,
            Map<String, SortedMap<String, Integer>> derived) {
        if (authored.isEmpty() && derived.isEmpty()) {
            return;
        }
        DataMapProvider.Builder<AspectValues, R> builder = maps.builder(type);
        authored.forEach(
                (key, entry) -> {
                    if (key.startsWith("#")) {
                        builder.add(
                                TagKey.create(registry, Identifier.parse(key.substring(1))),
                                values(entry.value()),
                                false);
                    } else {
                        builder.add(
                                ResourceKey.create(registry, Identifier.parse(key)),
                                values(entry.value()),
                                false);
                    }
                });
        derived.forEach(
                (key, value) ->
                        builder.add(
                                ResourceKey.create(registry, Identifier.parse(key)),
                                values(value),
                                false));
    }

    private static AspectValues values(SortedMap<String, Integer> value) {
        TreeMap<ResourceKey<Aspect>, Integer> out = new TreeMap<>();
        value.forEach((aspect, n) -> out.put(AspectSet.key(aspect), n));
        return new AspectValues(out);
    }

    private static List<String> extraRules(JsonObject root) {
        List<String> problems = new ArrayList<>();
        Set<String> builtIn = new HashSet<>();
        BuiltinAspects.ALL.keySet().forEach(k -> builtIn.add(AspectSet.text(k)));
        for (String table : TABLES) {
            JsonElement entries = root.get(table);
            if (entries == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : entries.getAsJsonObject().entrySet()) {
                if (table.equals("defaults") && !e.getKey().startsWith("#")) {
                    problems.add("$.defaults." + e.getKey() + ": defaults are keyed by tags");
                }
                for (String aspect : e.getValue().getAsJsonObject().keySet()) {
                    if (!builtIn.contains(aspect)) {
                        problems.add(
                                "$." + table + "." + e.getKey() + ": unknown aspect " + aspect);
                    }
                }
            }
        }
        return problems;
    }

    /** {@code data/vftv/tuning/scanning.json}, every key at its default. */
    private record TuningProvider(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            return DataProvider.saveStable(
                    cache,
                    ScanningTuning.DEFAULTS.toJson(),
                    output.getOutputFolder(PackOutput.Target.DATA_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve(ScanningService.TUNING_PATH));
        }

        @Override
        public String getName() {
            return "vftv scanning tuning";
        }
    }
}
