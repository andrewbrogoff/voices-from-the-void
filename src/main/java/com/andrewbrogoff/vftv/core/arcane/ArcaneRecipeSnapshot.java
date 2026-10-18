package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import com.andrewbrogoff.vftv.api.research.Research;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * One side's immutable set of {@code vftv:arcane} recipes, sorted by id (arcane spec, "Recipe sync
 * and reload"). It is replaced whole; nothing holds a recipe across a replacement.
 *
 * @param recipes every arcane recipe holder, in ascending id
 */
public record ArcaneRecipeSnapshot(List<RecipeHolder<ArcaneRecipe>> recipes) {
    /** No recipes: before the first build, after logout, and after an over-cap reload. */
    public static final ArcaneRecipeSnapshot EMPTY = new ArcaneRecipeSnapshot(List.of());

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Comparator<RecipeHolder<?>> BY_ID =
            Comparator.comparing(h -> h.id().identifier().toString());

    /** A snapshot of exactly these holders, which must already be in ascending id. */
    public ArcaneRecipeSnapshot {
        recipes = List.copyOf(recipes);
    }

    /**
     * Builds a snapshot from every arcane recipe the recipe manager holds. A recipe whose result
     * creates an empty stack or one over its maximum stack size is left out with a logged error
     * naming it: that check needs item components bound, which they are not yet while recipes
     * decode. More than {@value ArcaneLimits#MAX_ARCANE_RECIPES} recipes reject the set, never
     * truncate it.
     *
     * @return the snapshot, or an error naming the count and the cap
     */
    public static DataResult<ArcaneRecipeSnapshot> of(
            Collection<RecipeHolder<ArcaneRecipe>> holders) {
        List<RecipeHolder<ArcaneRecipe>> valid = new ArrayList<>(holders.size());
        for (RecipeHolder<ArcaneRecipe> holder : holders) {
            holder.value()
                    .createResult()
                    .ifSuccess(stack -> valid.add(holder))
                    .ifError(
                            e ->
                                    LOGGER.error(
                                            "Skipping arcane recipe {}: {}",
                                            holder.id().identifier(),
                                            e.message()));
        }
        if (valid.size() > ArcaneLimits.MAX_ARCANE_RECIPES) {
            int count = valid.size();
            return DataResult.error(
                    () ->
                            count
                                    + " arcane recipes exceed the cap of "
                                    + ArcaneLimits.MAX_ARCANE_RECIPES);
        }
        valid.sort(BY_ID);
        return DataResult.success(new ArcaneRecipeSnapshot(valid));
    }

    /**
     * The selected recipe (arcane spec, "Selection"): the one with the lowest id that matches the
     * grid and whose research is learned. An unlearned match never hides a learned one.
     *
     * @param grid the grid as {@code CraftingInput.ofPositioned} builds it, trimmed to its occupied
     *     area
     * @param level the crafting player's level
     * @param learned whether the crafting player has learned an entry
     */
    public Optional<RecipeHolder<ArcaneRecipe>> select(
            CraftingInput grid, Level level, Predicate<ResourceKey<Research>> learned) {
        if (grid.isEmpty()) {
            return Optional.empty();
        }
        for (RecipeHolder<ArcaneRecipe> holder : recipes) {
            ArcaneRecipe recipe = holder.value();
            if (learned.test(recipe.research()) && recipe.matches(grid, level)) {
                return Optional.of(holder);
            }
        }
        return Optional.empty();
    }
}
