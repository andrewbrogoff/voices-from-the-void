package com.andrewbrogoff.vftv.core.text;

import com.andrewbrogoff.vftv.core.common.Platform;

/**
 * The text feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once, during
 * mod construction, before every other feature, which may send status lines.
 */
public final class TextFeature {
    private TextFeature() {}

    /** Registers the status line's payload. */
    public static void init(Platform platform) {
        StatusLines.init(platform.network());
    }
}
