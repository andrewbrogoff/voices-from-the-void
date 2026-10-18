package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.core.aspect.model.Derivation;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

/**
 * The one adapter from a recipe holder to the derivation's recipe (aspects spec, "Shared adapter";
 * compatibility, "Accepted recipes"). Datagen's capture, the export and the load-time table all use
 * it. A holder is accepted iff its recipe's class is exactly one of vanilla's eight below; a {@code
 * TransmuteRecipe} only with a material count of exactly {@code [1, 1]} and no added result count.
 *
 * <p>Ingredients must be plain item or tag ingredients with bound contents (a server's loaded data,
 * or datagen after it binds the vanilla tags). The {@code unvalued remainder} rule needs the
 * resolved set, so {@link Derivation#derive} applies it.
 */
public final class DerivationRecipes {
    /** Why a holder is rejected, counted for the report. */
    public enum Rejection {
        /** Not one of vanilla's crafting, cooking or stonecutting types. */
        ANOTHER_TYPE("another type"),
        /** A vanilla type, but not exactly one of the accepted classes. */
        ANOTHER_CLASS("another class"),
        /** A transmute whose inputs or result count vary with the grid. */
        VARIABLE_TRANSMUTE("variable transmute"),
        /** A NeoForge custom ingredient. */
        CUSTOM_INGREDIENT("a custom ingredient"),
        /** An ingredient with no alternatives. */
        NO_ALTERNATIVES("no alternatives"),
        /** An empty result. */
        EMPTY_RESULT("an empty result"),
        /** An alternative's remainder has no authored or default value. */
        UNVALUED_REMAINDER("unvalued remainder");

        private final String word;

        Rejection(String word) {
            this.word = word;
        }

        /** The report's words. */
        public String word() {
            return word;
        }
    }

    /**
     * Every accepted recipe, and the count of every rejected holder by reason (without {@code
     * unvalued remainder}, which the derivation counts).
     *
     * @param recipes the accepted recipes, in ascending id
     * @param rejected how many holders each reason rejected
     * @param seen how many holders there were
     */
    public record Capture(
            List<Derivation.Recipe> recipes, Map<Rejection, Integer> rejected, int seen) {}

    private static final Set<RecipeType<?>> TYPES =
            Set.of(
                    RecipeType.CRAFTING,
                    RecipeType.SMELTING,
                    RecipeType.BLASTING,
                    RecipeType.SMOKING,
                    RecipeType.CAMPFIRE_COOKING,
                    RecipeType.STONECUTTING);

    private static final Set<Class<?>> CLASSES =
            Set.of(
                    ShapedRecipe.class,
                    ShapelessRecipe.class,
                    TransmuteRecipe.class,
                    SmeltingRecipe.class,
                    BlastingRecipe.class,
                    SmokingRecipe.class,
                    CampfireCookingRecipe.class,
                    StonecutterRecipe.class);

    private DerivationRecipes() {}

    /**
     * Adapts every holder, in ascending recipe id. The bounds are counted as it goes: it stops at
     * the first recipe past either one, so an oversized pack never expands in full.
     */
    public static Capture capture(
            Collection<RecipeHolder<?>> holders, HolderLookup.Provider registries) {
        List<RecipeHolder<?>> sorted = new ArrayList<>(holders);
        sorted.sort(Comparator.comparing(h -> h.id().identifier().toString()));
        List<Derivation.Recipe> recipes = new ArrayList<>();
        Map<Rejection, Integer> rejected = new EnumMap<>(Rejection.class);
        long edges = 0;
        for (RecipeHolder<?> holder : sorted) {
            Adapted adapted = adapt(holder, registries);
            if (adapted.recipe() != null) {
                recipes.add(adapted.recipe());
                edges += Derivation.edges(List.of(adapted.recipe()));
                if (Derivation.overLimit(recipes.size(), edges)) {
                    break;
                }
            } else {
                rejected.merge(adapted.rejection(), 1, Integer::sum);
            }
        }
        return new Capture(List.copyOf(recipes), rejected, sorted.size());
    }

    /**
     * One holder's recipe or why it is rejected; exactly one is non-null.
     *
     * @param recipe the derivation's recipe
     * @param rejection why the holder is rejected
     */
    public record Adapted(Derivation.@Nullable Recipe recipe, @Nullable Rejection rejection) {}

    /** Adapts one holder. */
    public static Adapted adapt(RecipeHolder<?> holder, HolderLookup.Provider registries) {
        Recipe<?> recipe = holder.value();
        if (!TYPES.contains(recipe.getType())) {
            return reject(Rejection.ANOTHER_TYPE);
        }
        if (!CLASSES.contains(recipe.getClass())) {
            return reject(Rejection.ANOTHER_CLASS);
        }
        if (recipe instanceof TransmuteRecipe transmute && !fixedTransmute(transmute, registries)) {
            return reject(Rejection.VARIABLE_TRANSMUTE);
        }
        PlacementInfo placement = recipe.placementInfo();
        if (placement.isImpossibleToPlace()) {
            return reject(Rejection.NO_ALTERNATIVES);
        }
        // Only a crafting grid returns remainders; a furnace or stonecutter consumes the input.
        boolean crafting = recipe.getType().equals(RecipeType.CRAFTING);
        List<List<Derivation.Alternative>> ingredients = new ArrayList<>();
        for (Ingredient ingredient : placement.ingredients()) {
            if (ingredient.isCustom()) {
                return reject(Rejection.CUSTOM_INGREDIENT);
            }
            List<Derivation.Alternative> alternatives =
                    ingredient.getValues().stream()
                            .map(item -> alternative(item, crafting))
                            .sorted(Comparator.comparing(Derivation.Alternative::item))
                            .toList();
            if (alternatives.isEmpty()) {
                return reject(Rejection.NO_ALTERNATIVES);
            }
            ingredients.add(alternatives);
        }
        ItemStackTemplate result = result(recipe);
        if (result == null || result.item().value().equals(Items.AIR) || result.count() < 1) {
            return reject(Rejection.EMPTY_RESULT);
        }
        return new Adapted(
                new Derivation.Recipe(
                        holder.id().identifier().toString(),
                        ingredients,
                        id(result.item().value()),
                        result.count(),
                        recipe instanceof AbstractCookingRecipe),
                null);
    }

    /** An item's id text. */
    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Derivation.Alternative alternative(Holder<Item> item, boolean crafting) {
        ItemStackTemplate left =
                crafting ? new ItemStackTemplate(item).getCraftingRemainder() : null;
        Derivation.Remainder remainder =
                left == null || left.item().value().equals(Items.AIR) || left.count() < 1
                        ? null
                        : new Derivation.Remainder(id(left.item().value()), left.count());
        return new Derivation.Alternative(id(item.value()), remainder);
    }

    /** Every accepted class's first display shows its result stack. */
    private static @Nullable ItemStackTemplate result(Recipe<?> recipe) {
        List<RecipeDisplay> displays = recipe.display();
        if (!displays.isEmpty()
                && displays.getFirst().result() instanceof SlotDisplay.ItemStackSlotDisplay stack) {
            return stack.stack();
        }
        return null;
    }

    /**
     * Reads the transmute's material count and result flag through its own codec, without
     * reflection: accepted only for exactly {@code [1, 1]} and no added result count.
     */
    private static boolean fixedTransmute(
            TransmuteRecipe recipe, HolderLookup.Provider registries) {
        Optional<JsonElement> encoded =
                TransmuteRecipe.MAP_CODEC
                        .codec()
                        .encodeStart(
                                registries.createSerializationContext(JsonOps.INSTANCE), recipe)
                        .result();
        if (encoded.isEmpty() || !(encoded.get() instanceof JsonObject json)) {
            return false;
        }
        JsonElement add = json.get("add_material_count_to_result");
        if (add != null
                && !(add instanceof JsonPrimitive p && p.isBoolean() && !p.getAsBoolean())) {
            return false;
        }
        JsonElement count = json.get("material_count");
        if (count == null) {
            return true;
        }
        return TransmuteRecipe.MATERIAL_COUNT_BOUNDS
                .parse(JsonOps.INSTANCE, count)
                .result()
                .filter(TransmuteRecipe.DEFAULT_MATERIAL_COUNT::equals)
                .isPresent();
    }

    private static Adapted reject(Rejection rejection) {
        return new Adapted(null, rejection);
    }
}
