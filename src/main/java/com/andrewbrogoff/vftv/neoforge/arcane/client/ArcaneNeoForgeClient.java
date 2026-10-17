package com.andrewbrogoff.vftv.neoforge.arcane.client;

import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The arcane feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class ArcaneNeoForgeClient {
    private ArcaneNeoForgeClient() {}

    /** Keeps the client's arcane recipe snapshot: replaced on each sync, cleared at logout. */
    public static void register(IEventBus modBus) {
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(
                RecipesReceivedEvent.class,
                event -> ArcaneRecipes.onRecipesReceived(event.getRecipeMap()));
        gameBus.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> ArcaneRecipes.clearClient());
    }
}
