package com.andrewbrogoff.vftv.core.text.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The locked Ink &amp; Brass palette (art style, §2): 48 colours as {@code 0xRRGGBB}, named as
 * {@code tools/art/lib.py} names them ({@code <ramp><step>}, dark to light). A unit test keeps the
 * two in step, so code-drawn HUD and screen colours never leave the palette.
 */
public final class Palette {
    /** Outline, HUD card interiors. */
    public static final int INK1 = 0x1a1a2a;

    /** Button faces and label strips. */
    public static final int BRASS4 = 0xd8c399;

    /** Panel bodies and text columns. */
    public static final int BRASS5 = 0xe8d8b4;

    /** Voice text with a {@code void} segment. */
    public static final int AMETHYST3 = 0xc4b0ec;

    /** Backed HUD text: the status line, Voice text, card text. */
    public static final int HUSH3 = 0xc6c2c4;

    /** Every colour, by name, in the art style's order. */
    public static final Map<String, Integer> COLOURS = colours();

    private Palette() {}

    /** An opaque ARGB colour from a palette RGB. */
    public static int opaque(int rgb) {
        return 0xff000000 | rgb;
    }

    private static Map<String, Integer> colours() {
        Map<String, Integer> m = new LinkedHashMap<>();
        ramp(m, "ink", 0x0b0e1c, 0x1a1a2a, 0x2a2a3e, 0x343849);
        ramp(m, "mauve", 0x464153, 0x5e566b, 0x7d7189);
        ramp(m, "brass", 0x5b4d3b, 0x85663f, 0xa57c43, 0xb89459, 0xd8c399, 0xe8d8b4);
        ramp(m, "copper", 0x5a2e2a, 0x8a4430, 0xb4643c, 0xd88a58, 0xf0b88a);
        ramp(m, "amethyst", 0x3d2c5c, 0x634a92, 0x8f74c4, 0xc4b0ec);
        ramp(m, "teal", 0x1e4a4e, 0x3f8a86, 0x82c9bc, 0xd2f2ea);
        ramp(m, "glass", 0x8fa9b4, 0xd5e6e8);
        ramp(m, "hush", 0x55545c, 0x77757c, 0x9c99a0, 0xc6c2c4);
        ramp(m, "ember", 0xa2441f, 0xe0862e);
        ramp(m, "tide", 0x27428a, 0x4a78c4);
        ramp(m, "loam", 0x5c5428, 0x9a8838);
        ramp(m, "gale", 0x7a9ccc, 0xb8d4ec);
        ramp(m, "verdure", 0x356a38, 0x66a64e);
        ramp(m, "vital", 0x7e2442, 0xc8445c);
        ramp(m, "blight", 0x5c2456, 0x9c4886);
        ramp(m, "lumen", 0xb0942a, 0xf0dc64);
        return Collections.unmodifiableMap(m);
    }

    private static void ramp(Map<String, Integer> m, String name, int... steps) {
        for (int i = 0; i < steps.length; i++) {
            m.put(name + i, steps[i]);
        }
    }
}
