package com.andrewbrogoff.vftv.core.vis;

/**
 * The vis server switch, key 18 of {@code vftv-server.toml} (configuration, "Server keys"): {@code
 * nodes.starter}, read once per server start.
 *
 * @param starter what the starter search does when no candidate is valid
 */
public record VisConfig(StarterMode starter) {
    /** The default: {@code best_effort}. */
    public static final VisConfig DEFAULT = new VisConfig(StarterMode.BEST_EFFORT);

    /**
     * {@code nodes.starter}'s file comment, verbatim from the configuration spec's "Lang keys";
     * also its tooltip's English.
     */
    public static final String STARTER_COMMENT =
            "best_effort: place it near spawn if possible, else warn and tell the first operator to"
                    + " join. required: place it or crash at startup. off: never place it. Without"
                    + " one the world may be unwinnable until /vftv node starter <pos>. Read at"
                    + " server start. Default: best_effort.";

    /** The starter modes (configuration, "The starter opt-out"). */
    public enum StarterMode {
        /** Place it or crash at startup. */
        REQUIRED,
        /** Place it if possible, else warn and tell the first operator. */
        BEST_EFFORT,
        /** Never place it. */
        OFF
    }
}
