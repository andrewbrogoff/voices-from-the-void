package com.andrewbrogoff.vftv.neoforge.arcane;

import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The arcane feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class ArcaneNeoForge {
    private ArcaneNeoForge() {}

    /** Builds the recipe snapshot at start and on reload, and sends arcane recipes to clients. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        // Posted by dedicated and integrated servers alike before any player can join.
        gameBus.addListener(
                ServerStartingEvent.class,
                event -> ArcaneRecipes.onServerStarting(event.getServer().getRecipeManager()));
        // On join and after every /reload: rebuild first (only if the recipes changed), then send.
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> {
                    ArcaneRecipes.beforeSync(event.getPlayerList().getServer().getRecipeManager());
                    event.sendRecipes(ArcaneRecipe.TYPE);
                });
        gameBus.addListener(ServerStoppedEvent.class, event -> ArcaneRecipes.clearServer());
    }
}
