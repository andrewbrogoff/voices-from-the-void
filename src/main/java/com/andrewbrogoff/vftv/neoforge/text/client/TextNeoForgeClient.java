package com.andrewbrogoff.vftv.neoforge.text.client;

import com.andrewbrogoff.vftv.core.text.client.StatusLineHud;
import com.andrewbrogoff.vftv.core.text.client.TextClient;
import com.andrewbrogoff.vftv.core.text.client.TextKeys;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Registers the text feature's client pieces: the {@code vftv:caption_band} layer directly above
 * vanilla's last status-row layer, {@code vftv:status} above it, the key mappings and their
 * category, the client tick and the reload hook. Each calls {@code core} in one line.
 *
 * <p>Our own status rows (the Baffle row) register below {@link #CAPTION_BAND}; the Voice text and
 * every crosshair card register above {@link #STATUS}.
 */
public final class TextNeoForgeClient {
    /** The zero-draw layer that publishes the caption band. */
    public static final Identifier CAPTION_BAND =
            Identifier.fromNamespaceAndPath("vftv", "caption_band");

    /** The status line's layer. */
    public static final Identifier STATUS = Identifier.fromNamespaceAndPath("vftv", "status");

    private TextNeoForgeClient() {}

    /** Subscribes on the mod bus, and the client tick on the game bus. */
    public static void register(IEventBus modBus) {
        IEventBus gameBus = NeoForge.EVENT_BUS;
        modBus.addListener(
                RegisterGuiLayersEvent.class,
                event -> {
                    event.registerAbove(
                            VanillaGuiLayers.AIR_LEVEL, CAPTION_BAND, StatusLineHud::bandLayer);
                    event.registerAbove(CAPTION_BAND, STATUS, StatusLineHud::statusLayer);
                });
        modBus.addListener(
                RegisterKeyMappingsEvent.class,
                event -> {
                    event.registerCategory(TextKeys.CATEGORY);
                    TextKeys.ALL.forEach(event::register);
                });
        modBus.addListener(
                AddClientReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "text"),
                                (ResourceManagerReloadListener)
                                        manager -> TextClient.onResourcesReloaded()));
        gameBus.addListener(ClientTickEvent.Post.class, event -> TextClient.tick());
        // The harness's vftv:text dump; goes nowhere without the harness.
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.<String, Function<String, String>>entry(
                                                "vftv:text", TextClient::dump)));
    }
}
