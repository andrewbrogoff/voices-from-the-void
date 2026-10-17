package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDataMaps;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.aspect.AspectContent;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.BuiltinAspects;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * The aspect feature's data generation: the 37 built-in {@code vftv:aspect} entries, the lang
 * table, the Tuning Fork's recipe, its three sound events, {@code data/vftv/tuning/scanning.json},
 * and the aspect data maps from every {@code content/aspects/<owner>.json}. Listed once in {@link
 * VftvData#FEATURES}.
 */
final class AspectData implements FeatureData {
    /** The authored aspect values' directory under {@code content/}. */
    static final String DIR = "aspects";

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern KEY = Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Schema.MapOf VALUES =
            new Schema.MapOf(new Schema.Str(3, 256, ID), new Schema.Int(1, 1_000_000), 0, 5);
    private static final Schema.MapOf TABLE =
            new Schema.MapOf(new Schema.Str(3, 257, KEY), VALUES, 0, 100_000);
    private static final List<String> TABLES = List.of("items", "blocks", "entities", "defaults");

    /**
     * {@code vftv:aspects/1}: an item, block, entity and defaults table, each from an id ({@code
     * ns:path}) or tag ({@code #ns:path}) to a value of at most 5 aspects. Rejected: a defaults key
     * that is not a tag; an aspect that is not built in; one object keyed twice in a map across
     * every file.
     */
    static final Schema SCHEMA =
            Schema.of(
                            "vftv:aspects/1",
                            Schema.optional("items", TABLE),
                            Schema.optional("blocks", TABLE),
                            Schema.optional("entities", TABLE),
                            Schema.optional("defaults", TABLE))
                    .withExtra(AspectData::extraRules);

    @Override
    public List<LangEntry> lang() {
        return AspectLang.TABLE;
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
    public void sounds(BiConsumer<Identifier, String> subtitles) {
        for (Identifier id :
                List.of(
                        AspectContent.STRIKE_SOUND,
                        AspectContent.ANSWER_SOUND,
                        AspectContent.DISCOVER_SOUND)) {
            subtitles.accept(id, "subtitles.vftv." + id.getPath());
        }
    }

    @Override
    public void dataMaps(DataMapProvider maps, HolderLookup.Provider registries) {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException(
                    "the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        List<ContentProvider.Envelope> files =
                ContentProvider.read(Path.of(root).resolve(DIR), Map.of(SCHEMA.id(), SCHEMA));
        Map<String, Set<String>> seen = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (ContentProvider.Envelope file : files) {
            JsonObject json = file.json().getAsJsonObject();
            add(
                    maps,
                    AspectDataMaps.ITEM,
                    Registries.ITEM,
                    registries,
                    json,
                    "items",
                    file,
                    seen,
                    problems);
            add(
                    maps,
                    AspectDataMaps.BLOCK,
                    Registries.BLOCK,
                    registries,
                    json,
                    "blocks",
                    file,
                    seen,
                    problems);
            add(
                    maps,
                    AspectDataMaps.ENTITY_TYPE,
                    Registries.ENTITY_TYPE,
                    registries,
                    json,
                    "entities",
                    file,
                    seen,
                    problems);
            add(
                    maps,
                    AspectDataMaps.DEFAULTS,
                    Registries.ITEM,
                    registries,
                    json,
                    "defaults",
                    file,
                    seen,
                    problems);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Rejected aspect values:\n" + String.join("\n", problems));
        }
    }

    @Override
    public void providers(GatherDataEvent event) {
        event.createProvider(TuningProvider::new);
        event.createProvider(FrameProvider::new);
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
            HolderLookup.Provider registries,
            JsonObject json,
            String table,
            ContentProvider.Envelope file,
            Map<String, Set<String>> seen,
            List<String> problems) {
        JsonElement entries = json.get(table);
        if (entries == null) {
            return;
        }
        HolderLookup.RegistryLookup<R> lookup = registries.lookupOrThrow(registry);
        DataMapProvider.Builder<AspectValues, R> builder = maps.builder(type);
        Set<String> keys = seen.computeIfAbsent(table, t -> new HashSet<>());
        for (Map.Entry<String, JsonElement> e : entries.getAsJsonObject().entrySet()) {
            String where = DIR + "/" + file.path() + ": $." + table + "." + e.getKey();
            if (!keys.add(e.getKey())) {
                problems.add(where + ": keyed twice");
                continue;
            }
            TreeMap<ResourceKey<Aspect>, Integer> values = new TreeMap<>();
            e.getValue()
                    .getAsJsonObject()
                    .entrySet()
                    .forEach(a -> values.put(AspectSet.key(a.getKey()), a.getValue().getAsInt()));
            AspectValues value = new AspectValues(values);
            if (e.getKey().startsWith("#")) {
                builder.add(
                        TagKey.create(registry, Identifier.parse(e.getKey().substring(1))),
                        value,
                        false);
            } else {
                ResourceKey<R> key = ResourceKey.create(registry, Identifier.parse(e.getKey()));
                if (lookup.get(key).isEmpty()) {
                    problems.add(where + ": no such " + registry.identifier().getPath());
                    continue;
                }
                builder.add(key, value, false);
            }
        }
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
