package com.andrewbrogoff.vftv.neoforge;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.Platform;
import com.andrewbrogoff.vftv.datagen.VftvData;
import com.andrewbrogoff.vftv.neoforge.arcane.ArcaneNeoForge;
import com.andrewbrogoff.vftv.neoforge.armor.ArmorNeoForge;
import com.andrewbrogoff.vftv.neoforge.armor.WornSlots;
import com.andrewbrogoff.vftv.neoforge.aspect.AspectNeoForge;
import com.andrewbrogoff.vftv.neoforge.common.CommonListener;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeDataSlots;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeNetwork;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.andrewbrogoff.vftv.neoforge.common.client.ClientWiring;
import com.andrewbrogoff.vftv.neoforge.compat.curios.CuriosCompat;
import com.andrewbrogoff.vftv.neoforge.config.VftvServerConfig;
import com.andrewbrogoff.vftv.neoforge.crucible.CrucibleNeoForge;
import com.andrewbrogoff.vftv.neoforge.essentia.EssentiaNeoForge;
import com.andrewbrogoff.vftv.neoforge.flux.FluxNeoForge;
import com.andrewbrogoff.vftv.neoforge.focus.FocusNeoForge;
import com.andrewbrogoff.vftv.neoforge.golem.GolemNeoForge;
import com.andrewbrogoff.vftv.neoforge.hush.HushNeoForge;
import com.andrewbrogoff.vftv.neoforge.infusion.InfusionNeoForge;
import com.andrewbrogoff.vftv.neoforge.research.ResearchNeoForge;
import com.andrewbrogoff.vftv.neoforge.road.RoadNeoForge;
import com.andrewbrogoff.vftv.neoforge.vis.VisNeoForge;
import com.andrewbrogoff.vftv.neoforge.warp.WarpNeoForge;
import com.andrewbrogoff.vftv.neoforge.worldgen.WorldgenNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The NeoForge entrypoint. It builds every platform implementation and passes them to {@code core}
 * once (ADR 0002), then wires each feature's NeoForge glue. Every feature is wired here once; a
 * feature ticket edits only its own {@code neoforge.<f>} classes, never this one.
 */
@Mod(Vftv.MOD_ID)
public final class VftvMod {
    /** Called by FML during mod construction. */
    public VftvMod(IEventBus modBus, ModContainer container, Dist dist) {
        NeoForgeRegistrar registrar = new NeoForgeRegistrar(Vftv.MOD_ID, modBus);
        Vftv.init(
                new Platform(
                        registrar, new NeoForgeNetwork(modBus), new NeoForgeDataSlots(registrar)));
        // The one reference into neoforge.compat.curios (compatibility, "Wiring the collector").
        WornSlots worn =
                ModList.get().isLoaded("curios") ? CuriosCompat.init(modBus) : WornSlots.NONE;
        IEventBus gameBus = NeoForge.EVENT_BUS;
        FluxNeoForge.register(modBus, gameBus, registrar);
        AspectNeoForge.register(modBus, gameBus, registrar);
        WarpNeoForge.register(modBus, gameBus, registrar, worn);
        ResearchNeoForge.register(modBus, gameBus, registrar);
        VisNeoForge.register(modBus, gameBus, registrar);
        ArcaneNeoForge.register(modBus, gameBus, registrar);
        CrucibleNeoForge.register(modBus, gameBus, registrar);
        EssentiaNeoForge.register(modBus, gameBus, registrar);
        InfusionNeoForge.register(modBus, gameBus, registrar);
        FocusNeoForge.register(modBus, gameBus, registrar);
        GolemNeoForge.register(modBus, gameBus, registrar);
        ArmorNeoForge.register(modBus, gameBus, registrar);
        HushNeoForge.register(modBus, gameBus, registrar);
        WorldgenNeoForge.register(modBus, gameBus, registrar);
        RoadNeoForge.register(modBus, gameBus, registrar);
        VftvServerConfig.register(container, modBus, gameBus);
        if (dist.isClient()) {
            ClientWiring.init(modBus, container, worn);
        }
        CommonListener.register(gameBus);
        VftvData.register(modBus);
    }
}
