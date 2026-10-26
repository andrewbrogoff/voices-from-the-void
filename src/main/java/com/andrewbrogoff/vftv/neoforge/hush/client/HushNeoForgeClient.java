package com.andrewbrogoff.vftv.neoforge.hush.client;

import com.andrewbrogoff.vftv.core.hush.HushContent;
import com.andrewbrogoff.vftv.core.hush.client.HollowModel;
import com.andrewbrogoff.vftv.core.hush.client.HollowRenderer;
import com.andrewbrogoff.vftv.core.hush.client.HushHud;
import com.andrewbrogoff.vftv.core.hush.client.HushMoteParticle;
import com.andrewbrogoff.vftv.core.hush.client.HushMuffle;
import com.andrewbrogoff.vftv.neoforge.vis.client.VisNeoForgeClient;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * The hush feature's client registration, called once from the client wiring on a physical client
 * only: the Hollow's layer and renderer, the mote, the reading's layer (after the vis layer, before
 * the crosshair cards), the muffle's tick and resets, and the harness dump.
 */
public final class HushNeoForgeClient {
    /** The reading's layer. */
    public static final Identifier HUD = Identifier.fromNamespaceAndPath("vftv", "hush");

    private HushNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterLayerDefinitions.class,
                event ->
                        event.registerLayerDefinition(
                                HollowRenderer.LAYER, HollowModel::createBodyLayer));
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event -> event.registerEntityRenderer(HushContent.hollow(), HollowRenderer::new));
        modBus.addListener(
                RegisterParticleProvidersEvent.class,
                event ->
                        event.registerSpriteSet(
                                HushContent.mote(), HushMoteParticle.Provider::new));
        modBus.addListener(
                RegisterGuiLayersEvent.class,
                event -> event.registerAbove(VisNeoForgeClient.HUD, HUD, HushHud::layer));
        modBus.addListener(
                AddClientReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "hush_hud"),
                                (ResourceManagerReloadListener) manager -> HushHud.invalidate()));
        modBus.addListener(SoundEngineLoadEvent.class, event -> HushMuffle.engineReset());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> HushMuffle.tick());
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> HushMuffle.reset());
        NeoForge.EVENT_BUS.addListener(
                LevelEvent.Unload.class,
                event -> {
                    if (event.getLevel().isClientSide()) {
                        HushMuffle.reset();
                    }
                });
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.<String, Function<String, String>>entry(
                                                "vftv:hush_client", HushHud::dump)));
    }
}
