package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.HiddenPrimal;
import com.andrewbrogoff.vftv.api.research.Research;
import com.andrewbrogoff.vftv.api.research.ResearchKeys;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.jspecify.annotations.Nullable;

/** Registries and recipes for the arcane unit tests. */
public final class ArcaneTestData {
    /** A research entry in the test registry. */
    public static final ResourceKey<Research> ENTRY = research("test_entry");

    /** Another research entry in the test registry. */
    public static final ResourceKey<Research> OTHER = research("other_entry");

    private static HolderLookup.@Nullable Provider registries;

    private ArcaneTestData() {}

    /** An aspect with the given parents and hidden-primal data. */
    record TestAspect(List<ResourceKey<Aspect>> parents, @Nullable HiddenPrimal hiddenPrimal)
            implements Aspect {
        @Override
        public int color() {
            return 0;
        }

        @Override
        public Identifier icon() {
            return Identifier.fromNamespaceAndPath("vftv", "aspect/test");
        }

        @Override
        public OptionalInt discoveryBonus() {
            return OptionalInt.empty();
        }
    }

    /**
     * Items, two research entries, and the aspects Aer, Ordo and Terra (primals), Sonus (a compound
     * of Aer and Ordo) and Vacuos (hidden).
     */
    public static synchronized HolderLookup.Provider registries() {
        HolderLookup.Provider r = registries;
        if (r != null) {
            return r;
        }
        TestItems.bindComponents();
        MappedRegistry<Research> research =
                new MappedRegistry<>(ResearchKeys.REGISTRY, Lifecycle.stable());
        Registry.register(research, ENTRY, new Research() {});
        Registry.register(research, OTHER, new Research() {});
        MappedRegistry<Aspect> aspects =
                new MappedRegistry<>(AspectKeys.REGISTRY, Lifecycle.stable());
        for (ResourceKey<Aspect> primal :
                List.of(AspectKeys.AER, AspectKeys.ORDO, AspectKeys.TERRA)) {
            Registry.register(aspects, primal, new TestAspect(List.of(), null));
        }
        Registry.register(
                aspects,
                AspectKeys.SONUS,
                new TestAspect(List.of(AspectKeys.AER, AspectKeys.ORDO), null));
        Registry.register(
                aspects,
                AspectKeys.VACUOS,
                new TestAspect(
                        List.of(),
                        new HiddenPrimal(
                                List.of(AspectKeys.AER, AspectKeys.TERRA),
                                ResearchKeys.SILENCE_BENEATH.identifier())));
        r =
                HolderLookup.Provider.create(
                        Stream.<HolderLookup.RegistryLookup<?>>of(
                                BuiltInRegistries.ITEM, research, aspects));
        registries = r;
        return r;
    }

    /** Parses JSON written with single quotes. */
    public static JsonObject json(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    /** Decodes a recipe body through the registry-aware codec. */
    public static DataResult<ArcaneRecipe> decode(String singleQuoted) {
        return ArcaneRecipe.MAP_CODEC
                .codec()
                .parse(
                        registries().createSerializationContext(JsonOps.INSTANCE),
                        json(singleQuoted));
    }

    /** A shaped recipe gated by {@code research}, making one {@code result}, costing Aer 1. */
    public static ArcaneRecipe shaped(
            ResourceKey<Research> research,
            Item result,
            Map<Character, Ingredient> key,
            String... rows) {
        return new ArcaneRecipe(
                research,
                VisKind.ORDINARY,
                Either.left(ShapedRecipePattern.of(key, rows)),
                new ItemStackTemplate(result),
                Map.of(AspectKeys.AER, 1));
    }

    /** A shapeless recipe gated by {@code research}, making one {@code result}, costing Aer 1. */
    public static ArcaneRecipe shapeless(
            ResourceKey<Research> research, Item result, Ingredient... ingredients) {
        return new ArcaneRecipe(
                research,
                VisKind.ORDINARY,
                Either.right(List.of(ingredients)),
                new ItemStackTemplate(result),
                Map.of(AspectKeys.AER, 1));
    }

    /** A holder with the id {@code vftv:<path>}. */
    public static RecipeHolder<ArcaneRecipe> holder(String path, ArcaneRecipe recipe) {
        return new RecipeHolder<>(
                ResourceKey.create(
                        Registries.RECIPE, Identifier.fromNamespaceAndPath("vftv", path)),
                recipe);
    }

    /** A 3x3 grid, row-major, trimmed as {@code CraftingInput.ofPositioned} does. */
    public static CraftingInput grid(ItemStack... cells) {
        ItemStack[] nine = Arrays.copyOf(cells, 9);
        for (int i = 0; i < 9; i++) {
            if (nine[i] == null) {
                nine[i] = ItemStack.EMPTY;
            }
        }
        return CraftingInput.ofPositioned(3, 3, List.of(nine)).input();
    }

    private static ResourceKey<Research> research(String path) {
        return ResourceKey.create(
                ResearchKeys.REGISTRY, Identifier.fromNamespaceAndPath("vftv", path));
    }
}
