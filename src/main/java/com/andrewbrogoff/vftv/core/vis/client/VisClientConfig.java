package com.andrewbrogoff.vftv.core.vis.client;

/**
 * The vis client keys 1-2 (configuration, "Client keys"): the vis HUD's top-left corner, in GUI
 * pixels, each 0 to 4096, clamped on screen when drawn.
 *
 * @param x {@code vis_hud.x}
 * @param y {@code vis_hud.y}
 */
public record VisClientConfig(int x, int y) {
    /** The defaults, (4, 4). */
    public static final VisClientConfig DEFAULT = new VisClientConfig(4, 4);
}
