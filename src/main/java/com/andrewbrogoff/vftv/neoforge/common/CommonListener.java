package com.andrewbrogoff.vftv.neoforge.common;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.DataReloads;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;

/**
 * The game-bus listeners for {@code core.common} and the {@code /vftv} root: each calls {@code
 * core} in one line.
 */
public final class CommonListener {
    private CommonListener() {}

    /** Subscribes every listener on the game bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Pre.class, event -> TickBudget.closeTick());
        // CLIENT_SYNC on a remote client; SERVER_RELOAD stands in for it in an integrated world.
        gameBus.addListener(DataMapsUpdatedEvent.class, event -> DataReloads.markChanged());
        gameBus.addListener(
                ServerStartingEvent.class, event -> Vftv.onServerStarting(event.getServer()));
        gameBus.addListener(
                RegisterCommandsEvent.class,
                event -> event.getDispatcher().register(Vftv.commandRoot(event.getBuildContext())));
    }
}
