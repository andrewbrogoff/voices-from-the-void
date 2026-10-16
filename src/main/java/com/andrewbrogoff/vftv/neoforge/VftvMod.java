package com.andrewbrogoff.vftv.neoforge;

import com.andrewbrogoff.vftv.core.Platform;
import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.datagen.VftvData;
import com.andrewbrogoff.vftv.neoforge.common.CommonListener;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeDataSlots;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeNetwork;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.andrewbrogoff.vftv.neoforge.common.client.ClientWiring;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The NeoForge entrypoint. It builds every platform implementation and passes them to {@code core}
 * once (ADR 0002), then subscribes the thin listeners.
 */
@Mod(Vftv.MOD_ID)
public final class VftvMod {
    /** Called by FML during mod construction. */
    public VftvMod(IEventBus modBus, Dist dist) {
        NeoForgeRegistrar registrar = new NeoForgeRegistrar(Vftv.MOD_ID, modBus);
        Vftv.init(
                new Platform(
                        registrar, new NeoForgeNetwork(modBus), new NeoForgeDataSlots(registrar)));
        if (dist.isClient()) {
            ClientWiring.init(modBus);
        }
        CommonListener.register(NeoForge.EVENT_BUS);
        VftvData.register(modBus);
    }
}
