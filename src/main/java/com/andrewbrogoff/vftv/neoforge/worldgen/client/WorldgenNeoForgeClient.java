package com.andrewbrogoff.vftv.neoforge.worldgen.client;

import com.andrewbrogoff.vftv.core.worldgen.Answerer;
import com.andrewbrogoff.vftv.core.worldgen.WorldgenContent;
import com.andrewbrogoff.vftv.core.worldgen.client.AnswererModel;
import com.andrewbrogoff.vftv.core.worldgen.client.AnswererRenderer;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
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
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.entry(
                                                "vftv:worldgen_client",
                                                (Function<String, String>)
                                                        WorldgenNeoForgeClient::dump)));
    }

    /**
     * The harness's {@code vftv:worldgen_client} dump: the Answerers the client sees, and the
     * chant.
     */
    private static String dump(String args) {
        JsonObject data = new JsonObject();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) {
            List<Answerer> near =
                    minecraft.level.getEntitiesOfClass(
                            Answerer.class, minecraft.player.getBoundingBox().inflate(128));
            data.addProperty("answerers", near.size());
            data.addProperty("cantors", near.stream().filter(Answerer::isCantor).count());
            data.addProperty("chanting", near.stream().anyMatch(Answerer::chanting));
        }
        return data.toString();
    }
}
