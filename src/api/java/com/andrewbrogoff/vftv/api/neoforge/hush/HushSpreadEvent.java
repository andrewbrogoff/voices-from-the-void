package com.andrewbrogoff.vftv.api.neoforge.hush;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.hush.HushSpreadPayload;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted before each Hush mutation: a block converted, a node hushed, a Hollow risen, or a Pealing
 * Bell's restore. It is not posted for a Clearing Bell's restore (which posts NeoForge's {@code
 * BreakBlockEvent}) nor for commands. It reports the kind, the cell, the change and its origin.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Cancellable: cancelling it makes the mutation a
 * miss, with nothing changed and nothing debited. It fails closed: a throwing listener is logged
 * and the mutation is a miss, with nothing changed and nothing debited. Listeners may change the
 * world; vftv revalidates the cell afterwards. The payload is immutable and its live handles are
 * valid only during dispatch. See the Hush spec, "Claims" and "The operation".
 */
public final class HushSpreadEvent extends Event implements ICancellableEvent {
    private final HushSpreadPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload the mutation about to happen
     */
    @Internal
    public HushSpreadEvent(HushSpreadPayload payload) {
        this.payload = payload;
    }

    /**
     * The mutation about to happen.
     *
     * @return the payload
     */
    public HushSpreadPayload payload() {
        return payload;
    }
}
