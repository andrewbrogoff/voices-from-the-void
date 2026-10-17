package com.andrewbrogoff.vftv.core.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import org.junit.jupiter.api.Test;

class DerivationRecipesTest {
    private static final HolderLookup.Provider REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final Recipe.CommonInfo INFO = new Recipe.CommonInfo(true);
    private static final CraftingRecipe.CraftingBookInfo CRAFTING =
            new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");
    private static final AbstractCookingRecipe.CookingBookInfo COOKING =
            new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "");

    private static DerivationRecipes.Adapted adapt(Recipe<?> recipe) {
        return DerivationRecipes.adapt(
                new RecipeHolder<>(
                        ResourceKey.create(Registries.RECIPE, Identifier.parse("test:r")), recipe),
                REGISTRIES);
    }

    private static Derivation.Recipe accept(Recipe<?> recipe) {
        return Objects.requireNonNull(adapt(recipe).recipe(), "accepted");
    }

    private static ShapedRecipe shaped() {
        return new ShapedRecipe(
                INFO,
                CRAFTING,
                ShapedRecipePattern.of(
                        Map.of(
                                '#',
                                Ingredient.of(Items.STICK),
                                'X',
                                Ingredient.of(Items.COAL, Items.CHARCOAL)),
                        "X",
                        "#"),
                new ItemStackTemplate(Items.TORCH, 4));
    }

    @Test
    void acceptsAShapedRecipeWithItsAlternativesInIdOrder() {
        Derivation.Recipe r = accept(shaped());
        assertEquals("test:r", r.id());
        assertEquals("minecraft:torch", r.result());
        assertEquals(4, r.count());
        assertFalse(r.smelted());
        assertEquals(2, r.ingredients().size());
        List<String> coal =
                r.ingredients().get(0).stream().map(Derivation.Alternative::item).toList();
        assertEquals(List.of("minecraft:charcoal", "minecraft:coal"), coal);
    }

    @Test
    void acceptsShapelessStonecutterAndEveryCookingClass() {
        assertEquals(
                2,
                accept(
                                new ShapelessRecipe(
                                        INFO,
                                        CRAFTING,
                                        new ItemStackTemplate(Items.PAPER),
                                        List.of(
                                                Ingredient.of(Items.SUGAR_CANE),
                                                Ingredient.of(Items.STICK))))
                        .ingredients()
                        .size());
        assertFalse(
                accept(
                                new StonecutterRecipe(
                                        INFO,
                                        Ingredient.of(Items.STONE),
                                        new ItemStackTemplate(Items.STONE_BRICKS)))
                        .smelted());
        Ingredient ore = Ingredient.of(Items.RAW_IRON);
        ItemStackTemplate ingot = new ItemStackTemplate(Items.IRON_INGOT);
        for (Recipe<?> cooking :
                List.of(
                        new SmeltingRecipe(INFO, COOKING, ore, ingot, 0, 200),
                        new BlastingRecipe(INFO, COOKING, ore, ingot, 0, 100),
                        new SmokingRecipe(INFO, COOKING, ore, ingot, 0, 100),
                        new CampfireCookingRecipe(INFO, COOKING, ore, ingot, 0, 600))) {
            assertTrue(accept(cooking).smelted(), cooking.getClass().getSimpleName());
        }
    }

    @Test
    void rejectsASubclassAndAnotherType() {
        ShapedRecipe sub =
                new ShapedRecipe(
                        INFO,
                        CRAFTING,
                        ShapedRecipePattern.of(Map.of('#', Ingredient.of(Items.STICK)), "#"),
                        new ItemStackTemplate(Items.TORCH)) {};
        assertEquals(DerivationRecipes.Rejection.ANOTHER_CLASS, adapt(sub).rejection());
        assertEquals(
                DerivationRecipes.Rejection.ANOTHER_TYPE,
                adapt(
                                new StrikeRecipe(
                                        INFO,
                                        Ingredient.of(Items.BOOK),
                                        new ItemStackTemplate(Items.PAPER)))
                        .rejection());
        assertNull(adapt(sub).recipe());
    }

    @Test
    void rejectsACustomIngredient() {
        Ingredient custom =
                CompoundIngredient.of(Ingredient.of(Items.STONE), Ingredient.of(Items.DIRT));
        assertTrue(custom.isCustom());
        assertEquals(
                DerivationRecipes.Rejection.CUSTOM_INGREDIENT,
                adapt(
                                new StonecutterRecipe(
                                        INFO, custom, new ItemStackTemplate(Items.STONE_BRICKS)))
                        .rejection());
    }

    private static TransmuteRecipe transmute(MinMaxBounds.Ints count, int result, boolean add) {
        return new TransmuteRecipe(
                INFO,
                CRAFTING,
                Ingredient.of(Items.BOOK),
                Ingredient.of(Items.FEATHER),
                count,
                new ItemStackTemplate(Items.WRITABLE_BOOK, result),
                add);
    }

    @Test
    void acceptsOnlyAFixedOneToOneTransmute() {
        Derivation.Recipe ok = accept(transmute(TransmuteRecipe.DEFAULT_MATERIAL_COUNT, 1, false));
        assertEquals(2, ok.ingredients().size(), "the input and one material");
        assertEquals("minecraft:writable_book", ok.result());
        for (TransmuteRecipe variable :
                List.of(
                        transmute(MinMaxBounds.Ints.exactly(8), 1, false),
                        transmute(MinMaxBounds.Ints.between(1, 4), 1, false),
                        transmute(TransmuteRecipe.DEFAULT_MATERIAL_COUNT, 1, true))) {
            assertEquals(
                    DerivationRecipes.Rejection.VARIABLE_TRANSMUTE, adapt(variable).rejection());
        }
    }

    @Test
    void readsEachAlternativesRemainder() {
        Derivation.Recipe r =
                accept(
                        new ShapelessRecipe(
                                INFO,
                                CRAFTING,
                                new ItemStackTemplate(Items.CAKE),
                                List.of(
                                        Ingredient.of(Items.MILK_BUCKET),
                                        Ingredient.of(Items.SUGAR))));
        Derivation.Alternative milk = r.ingredients().get(0).getFirst();
        assertEquals(new Derivation.Remainder("minecraft:bucket", 1), milk.remainder());
        assertNull(r.ingredients().get(1).getFirst().remainder());
    }

    @Test
    void capturesInIdOrderAndCountsRejections() {
        RecipeHolder<?> a =
                new RecipeHolder<>(
                        ResourceKey.create(Registries.RECIPE, Identifier.parse("test:b")),
                        shaped());
        RecipeHolder<?> b =
                new RecipeHolder<>(
                        ResourceKey.create(Registries.RECIPE, Identifier.parse("test:a")),
                        new StrikeRecipe(
                                INFO,
                                Ingredient.of(Items.BOOK),
                                new ItemStackTemplate(Items.PAPER)));
        DerivationRecipes.Capture capture = DerivationRecipes.capture(List.of(a, b), REGISTRIES);
        assertEquals(2, capture.seen());
        assertEquals(1, capture.recipes().size());
        assertEquals(Map.of(DerivationRecipes.Rejection.ANOTHER_TYPE, 1), capture.rejected());
        assertEquals("another type", DerivationRecipes.Rejection.ANOTHER_TYPE.word());
    }
}
