package com.andrewbrogoff.vftv.neoforge.infusion.client;

import com.andrewbrogoff.vftv.core.infusion.InfusionAltars;
import com.andrewbrogoff.vftv.core.infusion.InfusionContent;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipes;
import com.andrewbrogoff.vftv.core.infusion.client.InfusionHud;
import com.andrewbrogoff.vftv.core.infusion.client.InfusionRenderer;
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
 * The infusion feature's client registration, called once from the client wiring on a physical
 * client only: the held-item renderers, the HUD layer (above the crucible's, below the strike card,
 * which wins), the client recipe snapshot, logout clearing and the harness dump, one line each.
 */
public final class InfusionNeoForgeClient {
    /** The infusion HUD's layer. */
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath("vftv", "infusion");

    private InfusionNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event -> {
                    event.registerBlockEntityRenderer(
                            InfusionContent.bowlType(), InfusionRenderer::new);
                    event.registerBlockEntityRenderer(
                            InfusionContent.pedestalType(), InfusionRenderer::new);
                });
        modBus.addListener(
                RegisterGuiLayersEvent.class,
                event -> event.registerBelow(AspectNeoForgeClient.CARD, LAYER, InfusionHud::layer));
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(
                RecipesReceivedEvent.class,
                event -> InfusionRecipes.onRecipesReceived(event.getRecipeMap()));
        gameBus.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class,
                event -> {
                    InfusionRecipes.clearClient();
                    InfusionAltars.setClientRules(null);
                    InfusionHud.clear();
                });
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.<String, Function<String, String>>entry(
                                                "vftv:infusion", InfusionHud::dump)));
    }
}
