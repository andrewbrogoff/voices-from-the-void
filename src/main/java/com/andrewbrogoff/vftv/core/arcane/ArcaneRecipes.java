package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.arcane.ArcaneLimits;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The current {@link ArcaneRecipeSnapshot} of each side (arcane spec, "Recipe sync and reload").
 * The server's is built at server start and rebuilt before each recipe sync whose recipe manager is
 * new (every {@code /reload}); the client's from each received recipe map, cleared at logout.
 * Internal in v1.
 */
public final class ArcaneRecipes {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile ArcaneRecipeSnapshot server = ArcaneRecipeSnapshot.EMPTY;
    private static volatile ArcaneRecipeSnapshot client = ArcaneRecipeSnapshot.EMPTY;
    private static @Nullable RecipeManager builtFrom; // server thread only

    private ArcaneRecipes() {}

    /** The server's current snapshot. */
    public static ArcaneRecipeSnapshot server() {
        return server;
    }

    /** The client's current snapshot. */
    public static ArcaneRecipeSnapshot client() {
        return client;
    }

    /** The current snapshot of the logical side a level is on. */
    public static ArcaneRecipeSnapshot of(boolean clientSide) {
        return clientSide ? client : server;
    }

    /**
     * The player's selected recipe for a grid (arcane spec, "Selection"), against the current
     * snapshot of the player's side: the lowest id that matches and whose research the player has
     * learned. Client and server run this same function on the same synced data.
     *
     * @param grid the grid as {@code CraftingInput.ofPositioned} builds it, trimmed to its occupied
     *     area
     */
    public static Optional<RecipeHolder<ArcaneRecipe>> select(Player player, CraftingInput grid) {
        Level level = player.level();
        return of(level.isClientSide())
                .select(grid, level, entry -> ResearchService.isLearned(player, entry));
    }

    /**
     * Builds the initial server snapshot, before any player can join. Over the cap, startup fails.
     *
     * @throws IllegalStateException naming the count and the cap, when over it
     */
    public static void onServerStarting(RecipeManager recipes) {
        server =
                ArcaneRecipeSnapshot.of(recipes.recipeMap().byType(ArcaneRecipe.TYPE))
                        .getOrThrow(IllegalStateException::new);
        builtFrom = recipes;
    }

    /**
     * Rebuilds the server snapshot if the recipe manager is not the one it was built from: after
     * each {@code /reload}, never on an ordinary join. Over the cap the new snapshot is empty and
     * an error is logged.
     */
    @SuppressWarnings("ReferenceEquality") // the instance is the identity of a recipe load
    public static void beforeSync(RecipeManager recipes) {
        if (recipes != builtFrom) {
            server = orEmpty(recipes.recipeMap());
            builtFrom = recipes;
        }
    }

    /** Drops the server snapshot when the server stops, so no closed world's recipes stay held. */
    public static void clearServer() {
        server = ArcaneRecipeSnapshot.EMPTY;
        builtFrom = null;
    }

    /** Replaces the client snapshot with the recipes just received, by the server's rule. */
    public static void onRecipesReceived(RecipeMap recipes) {
        client = orEmpty(recipes);
    }

    /** Clears the client snapshot at logout. */
    public static void clearClient() {
        client = ArcaneRecipeSnapshot.EMPTY;
    }

    private static ArcaneRecipeSnapshot orEmpty(RecipeMap recipes) {
        return ArcaneRecipeSnapshot.of(recipes.byType(ArcaneRecipe.TYPE))
                .resultOrPartial(
                        e ->
                                LOGGER.error(
                                        "No arcane recipe is usable until a reload brings the"
                                                + " count within {}: {}",
                                        ArcaneLimits.MAX_ARCANE_RECIPES,
                                        e))
                .orElse(ArcaneRecipeSnapshot.EMPTY);
    }
}
