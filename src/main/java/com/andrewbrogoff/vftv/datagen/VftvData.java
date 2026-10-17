package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.datagen.client.LanguageCatalogue;
import com.andrewbrogoff.vftv.datagen.client.VftvClientData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.advancements.AdvancementSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * The data providers, run by the {@code clientData} run into {@code src/generated/resources}:
 * everything registers on {@link GatherDataEvent.Client}, because a second run into the same folder
 * would purge this one's files.
 *
 * <p>One aggregating provider per shared output calls every feature's {@link FeatureData} hook.
 * Every feature is listed here once; a feature ticket edits only its own {@code <F>Data}.
 */
public final class VftvData {
    /** Every feature's data, leaves first. */
    static final List<FeatureData> FEATURES =
            List.of(
                    new TextData(),
                    new FluxData(),
                    new AspectData(),
                    new WarpData(),
                    new ResearchData(),
                    new VisData(),
                    new ArcaneData(),
                    new CrucibleData(),
                    new EssentiaData(),
                    new InfusionData(),
                    new FocusData(),
                    new GolemData(),
                    new ArmorData(),
                    new HushData(),
                    new WorldgenData(),
                    new RoadData());

    private VftvData() {}

    /** Subscribes the gather listeners on the mod's bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(GatherDataEvent.Client.class, VftvData::gatherClient);
    }

    private static void gatherClient(GatherDataEvent.Client event) {
        // First, so every later provider's lookup sees the code-bootstrapped entries.
        DatapackEntries entries = new DatapackEntries();
        FEATURES.forEach(f -> f.datapackEntries(entries));
        if (!entries.isEmpty()) {
            event.createDatapackRegistryObjects(entries.build());
        }
        event.createProvider(
                output ->
                        new LangProvider(
                                output,
                                LanguageCatalogue.of(
                                        event.getResourceManager(PackType.CLIENT_RESOURCES))));
        event.createProvider(Recipes::new);
        for (TagKey<?> sample : taggedRegistries()) {
            event.createProvider((output, lookup) -> tagsProvider(output, sample, lookup));
        }
        event.createProvider(DataMaps::new);
        event.createProvider(
                (output, lookup) ->
                        new LootTableProvider(
                                output,
                                Set.of(),
                                FEATURES.stream().flatMap(f -> f.loot().stream()).toList(),
                                lookup));
        event.createProvider(
                (output, lookup) ->
                        new AdvancementProvider(
                                output,
                                lookup,
                                FEATURES.stream()
                                        .map(f -> (AdvancementSubProvider) f::advancements)
                                        .toList()));
        event.createProvider(Sounds::new);
        VftvClientData.gather(event);
        FEATURES.forEach(f -> f.providers(event));
    }

    /**
     * One tag key per registry some feature tags, found by asking each feature once with a
     * recorder.
     */
    private static Collection<TagKey<?>> taggedRegistries() {
        Map<ResourceKey<?>, TagKey<?>> registries = new LinkedHashMap<>();
        Tags recorder =
                new Tags() {
                    @Override
                    public <T> TagAppender<T> tag(TagKey<T> key) {
                        registries.putIfAbsent(key.registry(), key);
                        return TagAppender.forBuilder(TagBuilder.create());
                    }
                };
        FEATURES.forEach(f -> f.tags(recorder));
        return registries.values();
    }

    private static <T> TagsProvider<T> tagsProvider(
            PackOutput output, TagKey<T> sample, CompletableFuture<HolderLookup.Provider> lookup) {
        return new FeatureTags<>(output, sample.registry(), lookup);
    }

    /**
     * One registry's tags from every feature; tags of other registries go to their own provider.
     */
    private static final class FeatureTags<T> extends TagsProvider<T> {
        FeatureTags(
                PackOutput output,
                ResourceKey<? extends Registry<T>> registry,
                CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, registry, lookup, Vftv.MOD_ID);
        }

        @Override
        protected void addTags(HolderLookup.Provider registries) {
            Tags tags =
                    new Tags() {
                        @Override
                        public <X> TagAppender<X> tag(TagKey<X> key) {
                            if (!key.isFor(registryKey)) {
                                return TagAppender.forBuilder(TagBuilder.create());
                            }
                            return TagAppender.forBuilder(getOrCreateRawBuilder(cast(key)));
                        }
                    };
            FEATURES.forEach(f -> f.tags(tags));
        }

        // isFor checked the registry, so the key's element type is T.
        @SuppressWarnings("unchecked")
        private TagKey<T> cast(TagKey<?> key) {
            return (TagKey<T>) key;
        }
    }

    private static final class Recipes extends RecipeProvider.Runner {
        Recipes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected RecipeProvider createRecipeProvider(
                HolderLookup.Provider registries, RecipeOutput output) {
            return new RecipeProvider(registries, output) {
                @Override
                protected void buildRecipes() {
                    FEATURES.forEach(f -> f.recipes(registries, output));
                }
            };
        }

        @Override
        public String getName() {
            return "vftv recipes";
        }
    }

    private static final class DataMaps extends DataMapProvider {
        DataMaps(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
            super(output, lookup);
        }

        @Override
        protected void gather(HolderLookup.Provider registries) {
            FEATURES.forEach(f -> f.dataMaps(this, registries));
        }
    }

    /**
     * {@code sounds.json}: every feature's events with their subtitle and an empty sound list
     * (build plan, "Audio": no sound files until the audio ticket). Written only when some feature
     * has one.
     */
    private record Sounds(PackOutput output) implements DataProvider {
        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            Map<String, JsonObject> entries = new TreeMap<>();
            FEATURES.forEach(
                    f ->
                            f.sounds(
                                    (id, subtitle) -> {
                                        JsonObject entry = new JsonObject();
                                        entry.addProperty("subtitle", subtitle);
                                        entry.add("sounds", new JsonArray());
                                        if (entries.put(id.getPath(), entry) != null) {
                                            throw new IllegalStateException(
                                                    "sound event " + id + " added twice");
                                        }
                                    }));
            if (entries.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            JsonObject json = new JsonObject();
            entries.forEach(json::add);
            return DataProvider.saveStable(
                    cache,
                    json,
                    output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                            .resolve(Vftv.MOD_ID)
                            .resolve("sounds.json"));
        }

        @Override
        public String getName() {
            return "vftv sounds";
        }
    }
}
