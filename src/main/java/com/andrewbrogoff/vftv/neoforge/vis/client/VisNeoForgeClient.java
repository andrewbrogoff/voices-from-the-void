package com.andrewbrogoff.vftv.neoforge.vis.client;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.client.NodeAmbience;
import com.andrewbrogoff.vftv.core.vis.client.NodeGlintParticle;
import com.andrewbrogoff.vftv.core.vis.client.NodeRenderer;
import com.andrewbrogoff.vftv.core.vis.client.VisClientFeature;
import com.andrewbrogoff.vftv.core.vis.client.VisHud;
import com.andrewbrogoff.vftv.core.vis.model.NodeClientNumbers;
import com.andrewbrogoff.vftv.neoforge.text.client.TextNeoForgeClient;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.slf4j.Logger;

/**
 * The vis feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class VisNeoForgeClient {
    /** The vis HUD's layer, below the caption band like every status row. */
    public static final Identifier HUD = Identifier.fromNamespaceAndPath("vftv", "vis");

    private static final Identifier CLIENT_NUMBERS =
            Identifier.fromNamespaceAndPath("vftv", "nodes/client.json");
    private static final Logger LOGGER = LogUtils.getLogger();

    private VisNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event ->
                        event.registerBlockEntityRenderer(
                                VisContent.nodeEntity(), context -> new NodeRenderer()));
        modBus.addListener(
                RegisterParticleProvidersEvent.class,
                event ->
                        event.registerSpriteSet(
                                VisContent.glint(), NodeGlintParticle.Provider::new));
        modBus.addListener(
                RegisterGuiLayersEvent.class,
                event -> event.registerBelow(TextNeoForgeClient.CAPTION_BAND, HUD, VisHud::layer));
        modBus.addListener(
                AddClientReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "node_client"),
                                (ResourceManagerReloadListener)
                                        manager -> {
                                            VisHud.invalidate();
                                            // A resource reload clears every particle too.
                                            NodeGlintParticle.reset();
                                            NodeAmbience.installNumbers(
                                                    read(manager.getResource(CLIENT_NUMBERS)));
                                        }));
        NeoForge.EVENT_BUS.addListener(
                ItemTooltipEvent.class,
                event ->
                        VisHud.tooltip(
                                event.getItemStack(), event.getToolTip(), event.getEntity()));
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> VisClientFeature.onLogout());
        // A level swap clears every particle without removing it: the glint count starts over.
        NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.level.LevelEvent.Unload.class,
                event -> {
                    if (event.getLevel().isClientSide()) {
                        NodeGlintParticle.reset();
                        NodeAmbience.clear();
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
                                                "vftv:vis_client", VisHud::dump)));
    }

    /** The resource pack's numbers; an invalid override is logged and the defaults are used. */
    private static NodeClientNumbers read(Optional<Resource> resource) {
        if (resource.isEmpty()) {
            return NodeClientNumbers.DEFAULTS;
        }
        try (InputStream in = resource.get().open()) {
            return NodeClientNumbers.parse(
                    StrictJson.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                            .getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Invalid assets/vftv/nodes/client.json; using the built-in numbers", e);
            return NodeClientNumbers.DEFAULTS;
        }
    }
}
