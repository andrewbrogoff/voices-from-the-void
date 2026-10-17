package com.andrewbrogoff.vftv.core.warp;

/**
 * The warp server switches that {@code WarpRules} carries, keys 2 and 10-12 of {@code
 * vftv-server.toml} (configuration, "Server keys"). The seven per-type Voice switches (keys 3-9)
 * join it with the Voice events.
 *
 * @param voices {@code warp.voices.enabled}: off, no Voice check rolls
 * @param tax {@code warp.tax}: off, the vis factor and the infusion penalty go neutral
 * @param amplifier {@code warp.amplifier}: off, the amplifier's warp is 0
 * @param gear {@code warp.gear}: off, the gear sample is 0
 */
public record WarpConfig(boolean voices, boolean tax, boolean amplifier, boolean gear) {
    /** The defaults: every switch on. */
    public static final WarpConfig DEFAULT = new WarpConfig(true, true, true, true);

    /**
     * {@code warp.voices.enabled}'s file comment, verbatim from the configuration spec's "Lang
     * keys"; also its tooltip's English.
     */
    public static final String VOICES_COMMENT =
            "Voice events at all. Off: no check rolls. Default: true.";

    /** {@code warp.tax}'s comment. */
    public static final String TAX_COMMENT =
            "Warp raises vis costs and lowers infusion stability. Off: neither. Running infusions"
                    + " keep their plan. Default: true.";

    /** {@code warp.amplifier}'s comment. */
    public static final String AMPLIFIER_COMMENT =
            "Warp strengthens Void workings and Void armour. Off: no amplification. Default: true.";

    /** {@code warp.gear}'s comment. */
    public static final String GEAR_COMMENT =
            "Warp from worn and carried gear. Off: gear adds none. Default: true.";
}
