package com.andrewbrogoff.vftv.core.flux.client;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import org.jspecify.annotations.Nullable;

/**
 * The client's copy of the server's {@link FluxRules}: absent until the first arrives and again
 * after disconnect (conventions, "Rules records"). Absent rules are the typed zero: {@code
 * FluxMath} prices every spill at 0 for them, and fact spans render {@code [?]}. Client thread
 * only.
 */
public final class ClientFluxRules {
    private static @Nullable FluxRules rules;

    private ClientFluxRules() {}

    /** The last rules received, or null before the first and after disconnect. */
    public static @Nullable FluxRules get() {
        return rules;
    }

    /** Takes newly received rules. */
    static void set(FluxRules received) {
        rules = received;
    }

    /** Drops the rules, at disconnect. */
    public static void clear() {
        rules = null;
    }
}
