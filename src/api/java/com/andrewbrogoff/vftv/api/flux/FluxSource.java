package com.andrewbrogoff.vftv.api.flux;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;

/**
 * What generated a flux deposit: a vis spend's spill, spilled essentia with its cause, or an
 * operator's command. See the Hush spec, "Sources".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface FluxSource {
    /** A vis spend's spill, reported by {@code VisSpentEvent}. */
    record Vis() implements FluxSource {
        /** Constructed by vftv only. */
        @Internal
        public Vis {}
    }

    /**
     * Spilled essentia: from a crucible, a jar, an infusion, a golem's death or a Pealing Bell's
     * removal.
     *
     * @param cause why the essentia spilled
     */
    record Essentia(SpillCause cause) implements FluxSource {
        /** Constructed by vftv only. */
        @Internal
        public Essentia {}
    }

    /** An operator's {@code /vftv flux add}. */
    record Command() implements FluxSource {
        /** Constructed by vftv only. */
        @Internal
        public Command {}
    }
}
