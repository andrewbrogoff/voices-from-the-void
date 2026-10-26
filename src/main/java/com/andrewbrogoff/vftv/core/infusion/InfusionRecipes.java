package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The current {@link InfusionRecipeSnapshot} of each side (infusion spec, "Recipe sync and
 * reload"), following the crucible's: built at server start, rebuilt before sending when the recipe
 * manager changes, replaced whole with a generation +1. Internal in v1.
 */
public final class InfusionRecipes {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile InfusionRecipeSnapshot server = InfusionRecipeSnapshot.EMPTY;
    private static volatile InfusionRecipeSnapshot client = InfusionRecipeSnapshot.EMPTY;
    private static @Nullable RecipeManager builtFrom; // server thread only
    private static long generation;

    private InfusionRecipes() {}

    /** The server's current snapshot. */
    public static InfusionRecipeSnapshot server() {
        return server;
    }

    /** The client's current snapshot. */
    public static InfusionRecipeSnapshot client() {
        return client;
    }

    /**
     * Builds the initial server snapshot. Over the cap, startup fails.
     *
     * @throws IllegalStateException naming the count and the cap
     */
    public static void onServerStarting(RecipeManager recipes) {
        server =
                InfusionRecipeSnapshot.of(
                                recipes.recipeMap().byType(InfusionRecipe.TYPE), ++generation)
                        .getOrThrow(IllegalStateException::new);
        builtFrom = recipes;
    }

    /** Rebuilds the server snapshot if the recipe manager is new (a {@code /reload}). */
    @SuppressWarnings("ReferenceEquality") // the instance is the identity of a recipe load
    public static void beforeSync(RecipeManager recipes) {
        if (recipes != builtFrom) {
            server = orEmpty(recipes.recipeMap());
            builtFrom = recipes;
        }
    }

    /** Drops the server snapshot when the server stops. */
    public static void clearServer() {
        server = InfusionRecipeSnapshot.EMPTY;
        builtFrom = null;
    }

    /** Replaces the client snapshot with the recipes just received. */
    public static void onRecipesReceived(RecipeMap recipes) {
        client = orEmpty(recipes);
    }

    /** Clears the client snapshot at logout. */
    public static void clearClient() {
        client = InfusionRecipeSnapshot.EMPTY;
    }

    private static InfusionRecipeSnapshot orEmpty(RecipeMap recipes) {
        long next = ++generation;
        return InfusionRecipeSnapshot.of(recipes.byType(InfusionRecipe.TYPE), next)
                .resultOrPartial(
                        e ->
                                LOGGER.error(
                                        "No infusion recipe is usable until a reload brings the"
                                                + " count within {}: {}",
                                        InfusionLimits.MAX_INFUSION_RECIPES,
                                        e))
                .orElse(InfusionRecipeSnapshot.EMPTY);
    }
}
