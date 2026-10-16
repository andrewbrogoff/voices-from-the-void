package com.andrewbrogoff.vftv.neoforge.common.client;

import com.andrewbrogoff.vftv.core.common.DataReloads;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The client listeners for {@code core.common}: each calls {@code core} in one line. */
public final class CommonNeoForgeClient {
    private CommonNeoForgeClient() {}

    /** Subscribes the data-reload client tick and offers the harness dumps. */
    public static void register(IEventBus modBus) {
        modBus.addListener(InterModEnqueueEvent.class, event -> InspectionDumps.send());
        NeoForge.EVENT_BUS.addListener(
                ClientTickEvent.Post.class,
                event -> {
                    if (Minecraft.getInstance().level != null) {
                        DataReloads.clientTick();
                    }
                });
    }
}
