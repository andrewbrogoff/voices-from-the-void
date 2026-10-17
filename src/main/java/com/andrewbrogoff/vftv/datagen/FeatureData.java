package com.andrewbrogoff.vftv.datagen;

import com.andrewbrogoff.vftv.core.text.model.LangEntry;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * One feature's contribution to every shared data output. Each feature has one implementation,
 * {@code datagen.<F>Data}, listed once in {@link VftvData#FEATURES}; it overrides only the hooks it
 * needs. The aggregating providers in {@link VftvData} call every feature's hook, so two features
 * that write into one file (a tag, {@code sounds.json}, {@code en_us.json}) never need two
 * providers.
 */
public interface FeatureData {
    /** The feature's code-defined lang table (accessibility, "The lang pipeline"). */
    default List<LangEntry> lang() {
        return List.of();
    }

    /** Recipes. */
    default void recipes(HolderLookup.Provider registries, RecipeOutput output) {}

    /** Tags of any registry, vanilla's or ours. */
    default void tags(Tags tags) {}

    /** Data-map entries, through {@code maps.builder(type)}. */
    default void dataMaps(DataMapProvider maps, HolderLookup.Provider registries) {}

    /** Loot table sub-providers. */
    default List<LootTableProvider.SubProviderEntry> loot() {
        return List.of();
    }

    /** Advancements. */
    default void advancements(
            HolderLookup.Provider registries, Consumer<AdvancementHolder> output) {}

    /**
     * {@code sounds.json} entries: each sound event's id and its subtitle key, written with an
     * empty sound list until the audio ticket (NeoForge's {@code SoundDefinition} cannot serialise
     * an empty list, so the hook takes the subtitle alone).
     */
    default void sounds(BiConsumer<Identifier, String> subtitles) {}

    /** Datapack registry entries: worldgen and any other registry bootstrapped in code. */
    default void datapackEntries(DatapackEntries entries) {}

    /**
     * Providers of the feature's own, for outputs no other feature writes (e.g. a {@link
     * ContentProvider} over its {@code content/} directory).
     */
    default void providers(GatherDataEvent event) {}
}
