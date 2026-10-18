package com.andrewbrogoff.vftv.neoforge.aspect.client;

import com.andrewbrogoff.vftv.core.aspect.client.AspectHud;
import com.andrewbrogoff.vftv.core.aspect.client.AspectTooltips;
import com.andrewbrogoff.vftv.core.aspect.client.DerivedAspectsClient;
import com.andrewbrogoff.vftv.neoforge.text.client.TextNeoForgeClient;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The aspect feature's client registration, called once from the client wiring on a physical client
 * only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class AspectNeoForgeClient {
    /** The strike card's layer, above the status line like every crosshair card. */
    public static final Identifier CARD = Identifier.fromNamespaceAndPath("vftv", "strike_card");

    /** The sounding marker's layer. */
    public static final Identifier MARKER = Identifier.fromNamespaceAndPath("vftv", "sounding");

    private AspectNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                RegisterGuiLayersEvent.class,
                event -> {
                    event.registerAbove(TextNeoForgeClient.STATUS, CARD, AspectHud::cardLayer);
                    event.registerAbove(CARD, MARKER, AspectHud::markerLayer);
                });
        NeoForge.EVENT_BUS.addListener(
                ItemTooltipEvent.class,
                event ->
                        AspectTooltips.append(
                                event.getItemStack(), event.getToolTip(), event.getEntity()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> AspectHud.tick());
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> DerivedAspectsClient.clear());
        // The harness's vftv:aspect and vftv:derived dumps; they go nowhere without the harness.
        modBus.addListener(
                InterModEnqueueEvent.class,
                event -> {
                    InterModComms.sendTo(
                            "mc_agent_harness",
                            "dump_client",
                            () ->
                                    Map.<String, Function<String, String>>entry(
                                            "vftv:aspect", AspectHud::dump));
                    InterModComms.sendTo(
                            "mc_agent_harness",
                            "dump_client",
                            () ->
                                    Map.<String, Function<String, String>>entry(
                                            "vftv:derived", DerivedAspectsClient::dump));
                });
    }
}
