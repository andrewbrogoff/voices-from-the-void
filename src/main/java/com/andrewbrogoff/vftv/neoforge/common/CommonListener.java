package com.andrewbrogoff.vftv.neoforge.common;

import com.andrewbrogoff.vftv.core.Vftv;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The game-bus listeners for {@code core.common} and the {@code /vftv} root: each calls {@code
 * core} in one line.
 */
public final class CommonListener {
    private CommonListener() {}

    /** Subscribes every listener on the game bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ServerTickEvent.Pre.class, event -> TickBudget.closeTick());
        gameBus.addListener(
                ServerStartingEvent.class, event -> Vftv.onServerStarting(event.getServer()));
        gameBus.addListener(
                RegisterCommandsEvent.class,
                event -> event.getDispatcher().register(Vftv.commandRoot(event.getBuildContext())));
    }
}
