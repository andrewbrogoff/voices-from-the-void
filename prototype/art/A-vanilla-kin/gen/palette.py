"""Vanilla Kin palette: one hue-shifted ramp per material, as vanilla does it.

Every ramp is built by `ramp()` so new materials follow the same rules:
darks drift one way round the hue wheel, lights the other, saturation peaks
in the mid-tones and falls off at the highlight, and value runs dark->light.
Index 0 is the darkest shade (used for edges; there are no black outlines).
"""

import colorsys


def ramp(hue, sat, n=6, shift=24, lo=0.26, hi=0.97, sat_hi=0.45):
    """n shades, dark to light. hue in degrees; shift = total hue travel (lights move +shift/2)."""
    out = []
    for i in range(n):
        t = i / (n - 1)
        h = ((hue + shift * (t - 0.5)) % 360) / 360
        s = sat * (1 - (1 - sat_hi) * t ** 2.2)
        v = lo + (hi - lo) * (t ** 0.85)
        r, g, b = colorsys.hsv_to_rgb(h, min(1, s), min(1, v))
        out.append("#%02x%02x%02x" % (round(r * 255), round(g * 255), round(b * 255)))
    return out


RAMPS = {
    # Stage 1: worked copper (the fork). Warm, red-leaning darks, peach lights.
    "copper": ramp(17, 0.74, 6, shift=16, lo=0.30, hi=0.98, sat_hi=0.40),
    # Accent: amethyst, the vanilla mineral, lights drift to pink.
    "amethyst": ramp(268, 0.58, 5, shift=40, lo=0.40, hi=1.00, sat_hi=0.25),
    # Stage 2: resonant brass. Olive-khaki, lower saturation than gold, greener than copper.
    "brass": ramp(48, 0.62, 6, shift=18, lo=0.27, hi=0.96, sat_hi=0.42),
    # Stage 2 resonance accent: a quiet verdigris-teal ring glint (from the branding's teal arcs).
    "ring": ramp(176, 0.42, 4, shift=-12, lo=0.42, hi=0.95, sat_hi=0.30),
    # Glass: near-neutral, very slightly cool, like vanilla glass.
    "glass": ramp(200, 0.16, 5, shift=-10, lo=0.55, hi=1.00, sat_hi=0.40),
    # Stage 4: hushed bronze. The bell's metal with the colour drained out (the Hush).
    "hushed": ramp(32, 0.12, 6, shift=24, lo=0.16, hi=0.82, sat_hi=0.55),
    # Stage 4 accent: echo-dark. Deep desaturated sea-teal, a silent cousin of sculk.
    "void": ramp(190, 0.40, 4, shift=-16, lo=0.08, hi=0.62, sat_hi=0.55),
}


def rgba(hex_, a=255):
    return tuple(int(hex_[i:i + 2], 16) for i in (1, 3, 5)) + (a,)


if __name__ == "__main__":
    for k, v in RAMPS.items():
        print(f"{k:9s}", " ".join(v))
