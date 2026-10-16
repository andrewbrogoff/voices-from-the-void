package com.andrewbrogoff.vftv.api.neoforge.focus;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.focus.FocusAffectEntityPayload;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted before a focus working affects each entity (at most 16 per Burst, 1 per Touch, Beam pulse
 * or bolt impact), after the caster check and before the target recheck. It gives claim
 * integrations a seam for non-damage effects.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Cancellable: cancelling it skips that target;
 * the vis already paid stays spent. It fails closed: a throwing listener is logged, the working
 * ends where it was with no refund of the vis already paid, and the cooldown, notifications and
 * guard release still run. Listeners may change the world; vftv rechecks the target after them. The
 * payload is immutable and its live handles are valid only during dispatch. See the foci spec,
 * "Targets" and "Failure after the spend".
 */
public final class FocusAffectEntityEvent extends Event implements ICancellableEvent {
    private final FocusAffectEntityPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload the entity about to be affected, and how
     */
    @Internal
    public FocusAffectEntityEvent(FocusAffectEntityPayload payload) {
        this.payload = payload;
    }

    /**
     * The entity about to be affected, and how.
     *
     * @return the payload
     */
    public FocusAffectEntityPayload payload() {
        return payload;
    }
}
