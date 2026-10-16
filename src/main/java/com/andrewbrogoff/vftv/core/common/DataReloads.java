package com.andrewbrogoff.vftv.core.common;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The client's signal that data maps changed: the flag the client tick reads (armor spec, "After a
 * reload").
 *
 * <p>A remote client learns of a reload from {@code DataMapsUpdatedEvent} with cause {@code
 * CLIENT_SYNC}. An integrated world's own connection is in memory and receives no sync, so there
 * the server's {@code SERVER_RELOAD} event, on the server thread, stands in for it. Both only
 * {@link #markChanged mark} the flag; the next client tick with a level open runs every registered
 * client rebuild once, on the client thread. One reload posts an event per registry, so a rebuild
 * may run more than once for it (at most once per client tick) and must be idempotent.
 */
public final class DataReloads {
    private static final List<Runnable> CLIENT_REBUILDS = new CopyOnWriteArrayList<>();
    private static volatile boolean changed;
    private static int clientRebuilds; // client thread only

    private DataReloads() {}

    /** Registers a client-thread rebuild of whatever the client derives from data maps. */
    public static void onClientRebuild(Runnable rebuild) {
        CLIENT_REBUILDS.add(rebuild);
    }

    /** Marks the data maps changed; safe from any thread. */
    public static void markChanged() {
        changed = true;
    }

    /** Runs the rebuilds if the data maps changed since the last call; the client tick calls it. */
    public static void clientTick() {
        if (!changed) {
            return;
        }
        changed = false;
        clientRebuilds++;
        CLIENT_REBUILDS.forEach(Runnable::run);
    }

    /** How many times the client rebuilt since the game started; read on the client thread. */
    public static int clientRebuilds() {
        return clientRebuilds;
    }
}
