"""Scholar's Engraving: shared palette and pixel helpers. Every VFTV texture imports this.

A sprite is a 16-row ASCII map plus a legend. Legend values are:
  "#rrggbb" / "#rrggbbaa"    a flat palette colour
  ("hatch", line, base)       engraved shadow: `line` on every 3rd "/" diagonal, `base` between
Stage 4 sprites pass `fail`: a set of (x, y) where engraved lines drop out (the plate going silent).
"""

import numpy as np
from PIL import Image

# Locked project-wide palette. Ramps run dark -> light. Never add a hex outside this table.
RAMPS = {
    "ink":       ["#211915", "#3b2d25"],                                   # line work, sepia not black
    "copper":    ["#4f2d22", "#7c4634", "#a6664b", "#c98e6a", "#e6c09b"],   # Stage 1 metal
    "brass":     ["#3d3419", "#6b5e33", "#958650", "#bfb07a", "#e2d8a8"],   # Resonant Brass, olive not gold
    "verdigris": ["#2f4a44", "#4f7569", "#7ea595", "#b6cfbf"],              # patina + resonance accent
    "amethyst":  ["#3c2a42", "#664c73", "#977aa3", "#d0bdd4"],              # the one bright accent
    "glass":     ["#2b302e", "#545e5a", "#8d9893", "#d4dbd2"],              # smoky glass (alpha set per use)
    "tarnish":   ["#3a3122", "#62563d", "#8a7c5c", "#b0a37f", "#d3c9a8"],   # Stage 4 brass, half-drained
    "hush":      ["#1c1b1c", "#3a393a", "#5f5e5c", "#8b8985", "#b9b6b0"],   # the Hush: colour gone, no hue
    "void":      ["#0e0d0f"],                                               # silence: flat, no lines, ever
}


def c(ramp, i, alpha=None):
    """Palette lookup; alpha 0-255 for glass."""
    h = RAMPS[ramp][i]
    return h if alpha is None else f"{h}{alpha:02x}"


def hatch(ramp, line_i, base_i):
    return ("hatch", c(ramp, line_i), c(ramp, base_i))


def rgba(hexs):
    h = hexs.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4)) + ((int(h[6:8], 16),) if len(h) == 8 else (255,))


def is_line(x, y):
    """The house hatch: 45-degree '/' lines, one pixel wide, period 3. Wider periods vanish at slot size."""
    return (x + y) % 3 == 0


BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) + 0.5) / 16
LOOKUP = {h: (name, i) for name, ramp in RAMPS.items() for i, h in enumerate(ramp)}


def hush_field(w, h, centre, radius, band, seed):
    """Where the Hush has got in: a ragged disc whose edge is ordered-dithered (Bayer 4x4), never blurred."""
    rng = np.random.default_rng(seed)
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx - centre[0], yy - centre[1]) + rng.normal(0, 0.5, (h, w))
    t = np.clip((radius - d) / band + 0.5, 0, 1)
    return t > BAYER4[yy % 4, xx % 4]


def to_hush(hexs):
    """Same shade index, hue removed. Glass/void/ink keep their own rules."""
    name, i = LOOKUP.get(hexs[:7], (None, 0))
    if name in (None, "ink", "void", "hush"):
        return hexs
    n = len(RAMPS[name])
    return RAMPS["hush"][round(i * 4 / (n - 1))] + hexs[7:]


def sprite(rows, legend, fail=frozenset(), hush=None, hush_drops_ink=0.0, seed=0):
    """Paint an ASCII map. In `hush` pixels: engraved lines vanish, colour drains, ink lines may break."""
    rng = np.random.default_rng(seed + 1000)
    h = len(rows)
    w = len(rows[0])
    img = np.zeros((h, w, 4), np.uint8)
    for y, row in enumerate(rows):
        assert len(row) == w, f"row {y} is {len(row)} wide: {row!r}"
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            v = legend[ch]
            hushed = hush is not None and hush[y, x]
            if isinstance(v, tuple):
                _, line, base = v
                v = line if is_line(x, y) and (x, y) not in fail and not hushed else base
            if hushed:
                if v[:7] == RAMPS["ink"][0] and rng.random() < hush_drops_ink:
                    continue  # the outline itself goes silent
                v = to_hush(v)
            img[y, x] = rgba(v)
    return img


def save(img, path):
    Image.fromarray(img, "RGBA").save(path, optimize=False)
