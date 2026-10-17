package com.andrewbrogoff.vftv.core.aspect;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * A {@code vftv:strike} recipe (aspects spec, "The Commonplace Book"): an ingredient and a result,
 * converted one at a time by an inventory strike with the Tuning Fork. Types in code, instances in
 * data: {@code {"type": "vftv:strike", "ingredient": …, "result": …}}.
 */
public final class StrikeRecipe extends SingleItemRecipe {
    /** The JSON codec. */
    public static final MapCodec<StrikeRecipe> MAP_CODEC = simpleMapCodec(StrikeRecipe::new);

    /** The network codec. */
    public static final StreamCodec<RegistryFriendlyByteBuf, StrikeRecipe> STREAM_CODEC =
            simpleStreamCodec(StrikeRecipe::new);

    /** The serializer, registered as {@code vftv:strike}. */
    public static final RecipeSerializer<StrikeRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    /** A recipe as the codec reads it. */
    public StrikeRecipe(Recipe.CommonInfo info, Ingredient ingredient, ItemStackTemplate result) {
        super(info, ingredient, result);
    }

    @Override
    public RecipeSerializer<StrikeRecipe> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public RecipeType<StrikeRecipe> getType() {
        return AspectContent.strikeType();
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return AspectContent.strikeCategory();
    }

    @Override
    public List<RecipeDisplay> display() {
        return List.of(
                new StonecutterRecipeDisplay(
                        input().display(),
                        new SlotDisplay.ItemStackSlotDisplay(result()),
                        new SlotDisplay.ItemSlotDisplay(AspectContent.tuningFork())));
    }
}
