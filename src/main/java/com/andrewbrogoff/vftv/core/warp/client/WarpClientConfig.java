package com.andrewbrogoff.vftv.core.warp.client;

import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Each key under {@code voices} with its comment (verbatim; also its tooltip). */
    public static final Map<String, String> COMMENTS = comments();

    private static Map<String, String> comments() {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("text", "Show the words of every Voice line. Default: true.");
        c.put("colour_shift", "Let the Voices drain colour from the screen. Default: true.");
        c.put("phantoms", "Show apparitions. Default: true.");
        c.put("phantom_sounds", "Play phantom sounds. Default: true.");
        return java.util.Collections.unmodifiableMap(c);
    }
}
