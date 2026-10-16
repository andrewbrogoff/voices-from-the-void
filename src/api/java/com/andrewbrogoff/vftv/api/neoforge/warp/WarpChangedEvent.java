package com.andrewbrogoff.vftv.api.neoforge.warp;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.warp.WarpChangedPayload;
import net.neoforged.bus.api.Event;

/**
 * Posted after a commit, apply or compound operation changes a player's temporary, lasting,
 * permanent or gear warp, once all its writes are complete and while the player's warp guard is
 * still held: one post per change. It reports the warp before and after, the actual deltas and the
 * source. A warp commit or apply made from a listener is refused as busy.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the warp spec, "Events".
 */
public final class WarpChangedEvent extends Event {
    private final WarpChangedPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the warp change reported
     */
    @Internal
    public WarpChangedEvent(WarpChangedPayload payload) {
        this.payload = payload;
    }

    /**
     * What the warp change reported.
     *
     * @return the payload
     */
    public WarpChangedPayload payload() {
        return payload;
    }
}
