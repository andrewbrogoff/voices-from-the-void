package com.andrewbrogoff.vftv.neoforge.common.client;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.neoforge.arcane.client.ArcaneNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.armor.WornSlots;
import com.andrewbrogoff.vftv.neoforge.armor.client.ArmorNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.aspect.client.AspectNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.config.client.VftvClientConfig;
import com.andrewbrogoff.vftv.neoforge.crucible.client.CrucibleNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.essentia.client.EssentiaNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.flux.client.FluxNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.focus.client.FocusNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.golem.client.GolemNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.hush.client.HushNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.infusion.client.InfusionNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.research.client.ResearchNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.road.client.RoadNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.text.client.TextNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.vis.client.VisNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.warp.client.WarpNeoForgeClient;
import com.andrewbrogoff.vftv.neoforge.worldgen.client.WorldgenNeoForgeClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/**
 * The client half of the entrypoint, loaded only on a physical client. Every feature's client
 * registration is wired here once; a feature ticket edits only its own {@code <F>NeoForgeClient}.
 */
public final class ClientWiring {
    private ClientWiring() {}

    /**
     * Builds the client platform implementations, passes them to {@code core}, and wires each
     * feature.
     */
    public static void init(IEventBus modBus, ModContainer container, WornSlots worn) {
        Vftv.initClient(new NeoForgeClientNetwork(modBus));
        CommonNeoForgeClient.register(modBus);
        TextNeoForgeClient.register(modBus);
        FluxNeoForgeClient.register(modBus);
        AspectNeoForgeClient.register(modBus);
        WarpNeoForgeClient.register(modBus);
        ResearchNeoForgeClient.register(modBus);
        VisNeoForgeClient.register(modBus);
        ArcaneNeoForgeClient.register(modBus);
        CrucibleNeoForgeClient.register(modBus);
        EssentiaNeoForgeClient.register(modBus);
        InfusionNeoForgeClient.register(modBus);
        FocusNeoForgeClient.register(modBus);
        GolemNeoForgeClient.register(modBus);
        ArmorNeoForgeClient.register(modBus, worn);
        HushNeoForgeClient.register(modBus);
        WorldgenNeoForgeClient.register(modBus);
        RoadNeoForgeClient.register(modBus);
        VftvClientConfig.register(container, modBus);
    }
}
