package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.api.crucible.CrucibleLimits;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The current {@link CrucibleRecipeSnapshot} of each side (crucible spec, "Recipe sync and
 * reload"), following the arcane snapshot exactly. Internal in v1.
 */
public final class CrucibleRecipes {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile CrucibleRecipeSnapshot server = CrucibleRecipeSnapshot.EMPTY;
    private static volatile CrucibleRecipeSnapshot client = CrucibleRecipeSnapshot.EMPTY;
    private static @Nullable RecipeManager builtFrom; // server thread only

    private CrucibleRecipes() {}

    /** The server's current snapshot. */
    public static CrucibleRecipeSnapshot server() {
        return server;
    }

    /** The client's current snapshot. */
    public static CrucibleRecipeSnapshot client() {
        return client;
    }

    /** The candidates for a thrower over the server's snapshot, in ascending id. */
    public static List<RecipeHolder<CrucibleRecipe>> candidates(
            ServerPlayer player, ItemStack stack) {
        return server.candidates(stack, key -> ResearchService.isLearned(player, key));
    }

    /**
     * Builds the initial server snapshot. Over the cap, startup fails.
     *
     * @throws IllegalStateException naming the count and the cap
     */
    public static void onServerStarting(RecipeManager recipes) {
        server =
                CrucibleRecipeSnapshot.of(recipes.recipeMap().byType(CrucibleRecipe.TYPE))
                        .getOrThrow(IllegalStateException::new);
        builtFrom = recipes;
        CrucibleService.logUnpayable();
    }

    /** Rebuilds the server snapshot if the recipe manager is new (a {@code /reload}). */
    @SuppressWarnings("ReferenceEquality") // the instance is the identity of a recipe load
    public static void beforeSync(RecipeManager recipes) {
        if (recipes != builtFrom) {
            server = orEmpty(recipes.recipeMap());
            builtFrom = recipes;
            CrucibleService.logUnpayable();
        }
    }

    /** Drops the server snapshot when the server stops. */
    public static void clearServer() {
        server = CrucibleRecipeSnapshot.EMPTY;
        builtFrom = null;
    }

    /** Replaces the client snapshot with the recipes just received. */
    public static void onRecipesReceived(RecipeMap recipes) {
        client = orEmpty(recipes);
    }

    /** Clears the client snapshot at logout. */
    public static void clearClient() {
        client = CrucibleRecipeSnapshot.EMPTY;
    }

    private static CrucibleRecipeSnapshot orEmpty(RecipeMap recipes) {
        return CrucibleRecipeSnapshot.of(recipes.byType(CrucibleRecipe.TYPE))
                .resultOrPartial(
                        e ->
                                LOGGER.error(
                                        "No crucible recipe is usable until a reload brings the"
                                                + " count within {}: {}",
                                        CrucibleLimits.MAX_CRUCIBLE_RECIPES,
                                        e))
                .orElse(CrucibleRecipeSnapshot.EMPTY);
    }
}
