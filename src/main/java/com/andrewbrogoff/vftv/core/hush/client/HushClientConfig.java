package com.andrewbrogoff.vftv.core.hush.client;

/**
 * The Hush client keys 5-6 (configuration, "Client keys").
 *
 * @param muffle {@code hush.muffle}: quieten sounds inside the Hush
 * @param hud {@code hush.hud}: show the Clearing Bell's reading
 */
public record HushClientConfig(boolean muffle, boolean hud) {
    /** The defaults: both on. */
    public static final HushClientConfig DEFAULT = new HushClientConfig(true, true);

    /** {@code hush.muffle}'s comment, verbatim from the configuration spec. */
    public static final String MUFFLE_COMMENT = "Quieten sounds inside the Hush. Default: true.";

    /** {@code hush.hud}'s comment. */
    public static final String HUD_COMMENT = "Show the Clearing Bell's reading. Default: true.";
}
