package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.core.common.Platform;

/**
 * The crucible feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once,
 * during mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class CrucibleFeature {
    private CrucibleFeature() {}

    /** Registers the feature's content, registries, data slots and payloads. */
    public static void init(Platform platform) {}
}
