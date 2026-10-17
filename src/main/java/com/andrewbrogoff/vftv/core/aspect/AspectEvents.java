package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.AspectDiscoveredPayload;
import com.andrewbrogoff.vftv.api.aspect.ThingScannedPayload;
import java.util.function.Consumer;

/**
 * Where the knowledge service reports committed scans and discoveries: the platform installs
 * publishers that post {@code ThingScannedEvent} and {@code AspectDiscoveredEvent}, which {@code
 * core} never sees. A publisher logs a throwing listener and returns; the state stands.
 */
public final class AspectEvents {
    private static volatile Consumer<ThingScannedPayload> scanned = p -> {};
    private static volatile Consumer<AspectDiscoveredPayload> discovered = p -> {};

    private AspectEvents() {}

    /** Installs the publishers; called once from the platform's wiring. */
    public static void install(
            Consumer<ThingScannedPayload> onScanned,
            Consumer<AspectDiscoveredPayload> onDiscovered) {
        scanned = onScanned;
        discovered = onDiscovered;
    }

    static void scanned(ThingScannedPayload payload) {
        scanned.accept(payload);
    }

    static void discovered(AspectDiscoveredPayload payload) {
        discovered.accept(payload);
    }
}
