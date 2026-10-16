package com.andrewbrogoff.vftv.core.flux;

/**
 * The Hush's server switches, keys 13-15 of {@code vftv-server.toml} (configuration, "Server
 * keys"). It lives in the leaf {@code core.flux} so {@code FluxRules} can carry it.
 *
 * @param spread {@code hush.spread}: off, no spread attempt is made at all
 * @param hollows {@code hush.hollows}: off, no Hollow rises
 * @param unattended {@code hush.unattended}: on, unattended chunks spread and unattended Pealing
 *     Bells peal
 */
public record HushConfig(boolean spread, boolean hollows, boolean unattended) {
    /** The defaults: spread and Hollows on, unattended off. */
    public static final HushConfig DEFAULT = new HushConfig(true, true, false);

    /**
     * Each key's file comment, verbatim from the configuration spec's "Lang keys"; also its
     * tooltip's English.
     */
    public static final String SPREAD_COMMENT =
            "Flux turns ground into the Hush. Off: no spread; flux still pools. Default: true.";

    /** {@code hush.hollows}' comment. */
    public static final String HOLLOWS_COMMENT =
            "Hollows rise from the Hush. Off: none rise. Default: true.";

    /** {@code hush.unattended}' comment. */
    public static final String UNATTENDED_COMMENT =
            "Hush spread and Pealing Bells run with no player nearby. Default: false.";
}
