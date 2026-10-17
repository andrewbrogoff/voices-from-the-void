package com.andrewbrogoff.vftv.neoforge.essentia.client;

import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.client.ClientEssentia;
import com.andrewbrogoff.vftv.core.essentia.client.EssentiaTint;
import com.andrewbrogoff.vftv.core.essentia.client.EssentiaTooltips;
import com.andrewbrogoff.vftv.core.essentia.client.FlowMotes;
import com.andrewbrogoff.vftv.core.essentia.client.JarRenderer;
import com.andrewbrogoff.vftv.core.essentia.client.TubeRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The essentia feature's client registration, called once from the client wiring on a physical
 * client only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class EssentiaNeoForgeClient {
    private EssentiaNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                EntityRenderersEvent.RegisterRenderers.class,
                event -> {
                    event.registerBlockEntityRenderer(
                            EssentiaContent.jarType(), ctx -> new JarRenderer());
                    event.registerBlockEntityRenderer(
                            EssentiaContent.tubeType(), ctx -> new TubeRenderer<>());
                    event.registerBlockEntityRenderer(
                            EssentiaContent.valveType(), ctx -> new TubeRenderer<>());
                });
        modBus.addListener(
                RegisterColorHandlersEvent.ItemTintSources.class,
                event -> event.register(EssentiaTint.ID, EssentiaTint.CODEC));
        modBus.addListener(
                AddClientReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "essentia_client"),
                                new SimplePreparableReloadListener<Void>() {
                                    @Override
                                    protected Void prepare(
                                            ResourceManager manager, ProfilerFiller profiler) {
                                        return null;
                                    }

                                    @Override
                                    protected void apply(
                                            Void unused,
                                            ResourceManager manager,
                                            ProfilerFiller profiler) {
                                        ClientEssentia.reloadClientFile(manager);
                                    }
                                }));
        NeoForge.EVENT_BUS.addListener(
                ItemTooltipEvent.class,
                event -> EssentiaTooltips.append(event.getItemStack(), event.getToolTip()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> FlowMotes.tick());
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> ClientEssentia.clear());
    }
}
