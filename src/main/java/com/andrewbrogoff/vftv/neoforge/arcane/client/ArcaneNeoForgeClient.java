package com.andrewbrogoff.vftv.neoforge.arcane.client;

import com.andrewbrogoff.vftv.core.arcane.ArcaneContent;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import com.andrewbrogoff.vftv.core.arcane.client.WorktableScreen;
import java.util.Map;
import java.util.function.Function;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The arcane feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class ArcaneNeoForgeClient {
    private ArcaneNeoForgeClient() {}

    /**
     * Registers the Worktable's screen and keeps the client's arcane recipe snapshot: replaced on
     * each sync, cleared at logout.
     */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                RegisterMenuScreensEvent.class,
                event -> event.register(ArcaneContent.menu(), WorktableScreen::new));
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(
                RecipesReceivedEvent.class,
                event -> ArcaneRecipes.onRecipesReceived(event.getRecipeMap()));
        gameBus.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> ArcaneRecipes.clearClient());
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.<String, Function<String, String>>entry(
                                                "vftv:worktable", WorktableScreen::dump)));
    }
}
