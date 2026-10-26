package com.andrewbrogoff.vftv.core.warp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The warp feature's code-held English that both sides need: the client options' comments
 * (configuration, "Client keys"), which datagen also writes as their tooltips.
 */
public final class WarpLang {
    /** Each client key under {@code voices} with its comment, verbatim. */
    public static final Map<String, String> VOICE_OPTIONS = options();

    private WarpLang() {}

    private static Map<String, String> options() {
        Map<String, String> c = new LinkedHashMap<>();
        c.put("text", "Show the words of every Voice line. Default: true.");
        c.put("colour_shift", "Let the Voices drain colour from the screen. Default: true.");
        c.put("phantoms", "Show apparitions. Default: true.");
        c.put("phantom_sounds", "Play phantom sounds. Default: true.");
        return java.util.Collections.unmodifiableMap(c);
    }
}
