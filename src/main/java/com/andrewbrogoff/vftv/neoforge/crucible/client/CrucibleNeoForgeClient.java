package com.andrewbrogoff.vftv.neoforge.crucible.client;

import com.andrewbrogoff.vftv.core.crucible.CrucibleContent;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipes;
import com.andrewbrogoff.vftv.core.crucible.client.CrucibleHud;
import com.andrewbrogoff.vftv.core.crucible.client.CrucibleRenderer;
import com.andrewbrogoff.vftv.neoforge.aspect.client.AspectNeoForgeClient;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The crucible feature's client registration, called once from the client wiring on a physical
 * client only: the liquid renderer, the HUD layer (below the strike card, which wins), the client
 * recipe snapshot and the harness dump, one line each.
 */
public final class CrucibleNeoForgeClient {
    /** The crucible HUD's layer. */
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath("vftv", "crucible");

    private CrucibleNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event ->
                        event.registerBlockEntityRenderer(
                                CrucibleContent.blockEntity(), context -> new CrucibleRenderer()));
        modBus.addListener(
                RegisterGuiLayersEvent.class,
                event -> event.registerBelow(AspectNeoForgeClient.CARD, LAYER, CrucibleHud::layer));
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(
                RecipesReceivedEvent.class,
                event -> CrucibleRecipes.onRecipesReceived(event.getRecipeMap()));
        gameBus.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> CrucibleRecipes.clearClient());
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.<String, Function<String, String>>entry(
                                                "vftv:crucible", CrucibleHud::dump)));
    }
}
