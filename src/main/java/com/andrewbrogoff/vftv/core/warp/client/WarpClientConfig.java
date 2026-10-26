package com.andrewbrogoff.vftv.core.warp.client;

/**
 * The client keys 7-10 of {@code vftv-client.toml} (configuration, "Client keys"; warp spec,
 * "Accessibility"): cosmetic switches, each changing nothing on the server.
 *
 * @param text {@code voices.text}: the Voice text with every line
 * @param colourShift {@code voices.colour_shift}: the colour drain
 * @param phantoms {@code voices.phantoms}: apparitions
 * @param phantomSounds {@code voices.phantom_sounds}: phantom sounds
 */
public record WarpClientConfig(
        boolean text, boolean colourShift, boolean phantoms, boolean phantomSounds) {
    /** Every option on. */
    public static final WarpClientConfig DEFAULT = new WarpClientConfig(true, true, true, true);
}
