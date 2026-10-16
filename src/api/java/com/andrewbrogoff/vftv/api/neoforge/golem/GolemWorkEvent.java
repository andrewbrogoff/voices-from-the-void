package com.andrewbrogoff.vftv.api.neoforge.golem;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.golem.GolemWorkPayload;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted at a golem act, inside the golem's operation guard, after arrival and before any
 * capability call or spawn. It is the claim-mod hook: golem work is automatic and isn't otherwise
 * re-authorised.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Cancellable: cancelling it makes the act {@code
 * REFUSED}: nothing moves, nothing is debited, and the mark backs off. It fails closed: a throwing
 * listener also makes the act {@code REFUSED}. Listeners may change the world; the act revalidates
 * everything it depends on after them. The payload is immutable and its live handles are valid only
 * during dispatch. See the golems spec, "An act".
 */
public final class GolemWorkEvent extends Event implements ICancellableEvent {
    private final GolemWorkPayload payload;

    /**
     * Constructed by vftv only.
     *
     * @param payload the act about to run
     */
    @Internal
    public GolemWorkEvent(GolemWorkPayload payload) {
        this.payload = payload;
    }

    /**
     * The act about to run.
     *
     * @return the payload
     */
    public GolemWorkPayload payload() {
        return payload;
    }
}
