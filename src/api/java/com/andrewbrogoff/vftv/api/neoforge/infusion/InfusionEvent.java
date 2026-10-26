package com.andrewbrogoff.vftv.api.neoforge.infusion;

import com.andrewbrogoff.vftv.api.Experimental;
import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import net.neoforged.bus.api.Event;
import org.jspecify.annotations.Nullable;

/**
 * Posted after the state an infusion occurrence reports has been written: a run started, upset,
 * spilled, completed or was abandoned, once per occurrence. Nothing fires per draw, absorb, stall
 * or pause.
 *
 * <p>Posted on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the logical server's
 * thread, never on a client and never by an addon. Not cancellable. A throwing listener is logged
 * once; the state stands and nothing is rolled back. The payload is immutable and its live handles
 * are valid only during dispatch. See the infusion spec, "Events".
 */
public final class InfusionEvent extends Event {
    private final InfusionPayload payload;
    private final @Nullable FluxRules fluxRules;

    /**
     * Constructed by vftv only.
     *
     * @param payload what the occurrence reported
     * @param fluxRules the flux rules a {@code Spilled} post applies, or null
     */
    @Internal
    public InfusionEvent(InfusionPayload payload, @Nullable FluxRules fluxRules) {
        this.payload = payload;
        this.fluxRules = fluxRules;
    }

    /**
     * What the occurrence reported.
     *
     * @return the payload
     */
    public InfusionPayload payload() {
        return payload;
    }

    /**
     * The flux rules to apply to a {@link InfusionPayload.Spilled} payload: the run's frozen rules
     * for an upset's spill, the current rules for an abandon's or a removal's; null for an upset
     * from a run frozen before flux rules existed, and for every other kind.
     *
     * @return the flux rules, or null
     */
    @Experimental
    public @Nullable FluxRules fluxRules() {
        return fluxRules;
    }
}
