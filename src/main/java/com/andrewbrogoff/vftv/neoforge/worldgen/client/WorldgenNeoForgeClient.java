package com.andrewbrogoff.vftv.neoforge.worldgen.client;

import com.andrewbrogoff.vftv.core.worldgen.WorldgenContent;
import com.andrewbrogoff.vftv.core.worldgen.client.AnswererModel;
import com.andrewbrogoff.vftv.core.worldgen.client.AnswererRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The worldgen feature's client registration, called once from the client wiring on a physical
 * client only: the Answerers' model layer and renderer, one line each.
 */
public final class WorldgenNeoForgeClient {
    private WorldgenNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterLayerDefinitions.class,
                event ->
                        event.registerLayerDefinition(
                                AnswererRenderer.LAYER, AnswererModel::createBodyLayer));
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event -> {
                    event.registerEntityRenderer(WorldgenContent.answerer(), AnswererRenderer::new);
                    event.registerEntityRenderer(WorldgenContent.cantor(), AnswererRenderer::new);
                });
    }
}
