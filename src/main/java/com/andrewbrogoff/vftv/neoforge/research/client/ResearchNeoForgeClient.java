package com.andrewbrogoff.vftv.neoforge.research.client;

import com.andrewbrogoff.vftv.core.research.ResearchItems;
import com.andrewbrogoff.vftv.core.research.client.CommonplaceScreen;
import com.andrewbrogoff.vftv.core.research.client.ResearchClient;
import java.util.Map;
import java.util.function.Function;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The research feature's client registration, called once from the client wiring on a physical
 * client only: renderers, GUI layers, screens, particles and key mappings, one line each.
 */
public final class ResearchNeoForgeClient {
    private ResearchNeoForgeClient() {}

    /** Registers the feature's client-side types on the mod bus. */
    public static void register(IEventBus modBus) {
        modBus.addListener(
                RegisterMenuScreensEvent.class,
                event -> event.register(ResearchItems.menu(), CommonplaceScreen::new));
        NeoForge.EVENT_BUS.addListener(
                ClientPlayerNetworkEvent.LoggingOut.class, event -> ResearchClient.clear());
        // The harness's vftv:research dump; goes nowhere without the harness.
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_client",
                                () ->
                                        Map.<String, Function<String, String>>entry(
                                                "vftv:research", ResearchClient::dump)));
    }
}
