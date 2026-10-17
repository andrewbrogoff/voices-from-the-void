"""The wand's layer textures and the Stage 2 part icons (wands spec, "Production tickets": Wand assets).
Usage: python3 wands.py <assets_root>

The wand is one upright instrument drawn in two layers that compose for every rod x cap pair: a rod
layer (the 2-px shaft between the caps) and a cap layer (the top and bottom caps and the Stage 1 teal
tick beside the top cap). Every rod shares one silhouette and every cap another, so the ink outline is
computed once over their union and each outline pixel goes to the layer it touches (the cap layer
where it touches both, since a cap layer is always drawn over the rod). The unknown layers are the
drained hush ramp with an ink notch, so a missing part reads by shape as well as colour.
"""

import os
import sys

from lib import canvas, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")

ROD = [(x, y) for y in range(4, 12) for x in (7, 8)]
CAP_TOP = [(x, y) for y in (1, 2, 3) for x in (6, 7, 8, 9)]
CAP_BOTTOM = [(x, y) for y in (12, 13, 14) for x in (6, 7, 8, 9)]
CAP = CAP_TOP + CAP_BOTTOM
TICK = [(11, 0, "teal3"), (12, 1, "teal2"), (12, 2, "teal2")]


def outline_split():
    """The union's 4-connected outline, split into the rod's and the caps' pixels."""
    rod, cap = set(ROD), set(CAP)
    solid = rod | cap
    rod_edge, cap_edge = [], []
    for y in range(16):
        for x in range(16):
            if (x, y) in solid:
                continue
            near = {(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)}
            if near & cap:
                cap_edge.append((x, y))
            elif near & rod:
                rod_edge.append((x, y))
    return rod_edge, cap_edge


ROD_EDGE, CAP_EDGE = outline_split()


def rod(ramp, steps, notch=None):
    """A rod layer: the lit left column, the shaded right column, and optional notches."""
    img = canvas()
    light, dark = steps
    for x, y in ROD:
        put(img, x, y, f"{ramp}{light if x == 7 else dark}")
    if notch:
        for y in notch:
            put(img, 7, y, "ink2")
            put(img, 8, y, "ink2")
    for x, y in ROD_EDGE:
        put(img, x, y, "ink1")
    return img


def cap(ramp, steps, pip=None, notch=False):
    """A cap layer: both caps lit from the upper left, the teal tick, an optional inlaid pip."""
    img = canvas()
    hi, mid, lo = steps
    for part in (CAP_TOP, CAP_BOTTOM):
        top = min(y for _, y in part)
        for x, y in part:
            shade = hi if (y == top or x == 6) else lo if (x == 9 or y == top + 2) else mid
            put(img, x, y, f"{ramp}{shade}")
    if pip:
        put(img, 8, 2, pip)  # Stage 2: a small inlaid teal pip on the top cap
    if notch:
        put(img, 7, 2, "ink1")
        put(img, 8, 13, "ink1")
    for x, y in CAP_EDGE:
        put(img, x, y, "ink1")
    for x, y, c in TICK:
        put(img, x, y, c)
    return img


def chime_rod_icon():
    """The chime rod: an upright brass bar with two ring grooves, the Stage 2 teal pip inlaid."""
    img = canvas()
    for y in range(1, 15):
        for x in (6, 7, 8):
            put(img, x, y, "brass4" if x == 6 else "brass3" if x == 7 else "brass1")
    for y in (5, 10):  # the tuned grooves
        for x in (6, 7, 8):
            put(img, x, y, "brass0")
    put(img, 6, 1, "brass5")  # one narrow specular tick at the top of the lit side
    solid = img[..., 3] > 0
    for y in range(16):
        for x in range(16):
            if not solid[y, x] and any(
                0 <= x + dx < 16 and 0 <= y + dy < 16 and solid[y + dy, x + dx]
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            ):
                put(img, x, y, "ink1")
    put(img, 7, 7, "teal2")  # inlaid pip
    return img


def resonant_brass_cap_icon():
    """The Resonant Brass cap: a squat cup, open end down, with the inlaid teal pip."""
    img = canvas()
    for y in range(5, 12):
        half = 3 if y < 7 else 4
        for x in range(8 - half, 8 + half):
            lit = x < 8 - half + 2
            shade = "brass4" if y == 5 else "brass3" if lit else "brass2" if x < 8 + half - 2 else "brass1"
            put(img, x, y, shade)
    for x in range(5, 11):  # the open mouth: the dark inner rim
        put(img, x, 11, "brass0")
    put(img, 5, 5, "brass5")
    solid = img[..., 3] > 0
    edge = []
    for y in range(16):
        for x in range(16):
            if not solid[y, x] and any(
                0 <= x + dx < 16 and 0 <= y + dy < 16 and solid[y + dy, x + dx]
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            ):
                edge.append((x, y))
    for x, y in edge:
        put(img, x, y, "ink1")
    put(img, 8, 8, "teal2")
    return img


if __name__ == "__main__":
    save(rod("brass", (2, 1)), f"{TEX}/item/wand/rod/wooden.png")
    save(rod("brass", (4, 3), notch=(6, 9)), f"{TEX}/item/wand/rod/chime.png")
    save(rod("hush", (2, 1), notch=(7,)), f"{TEX}/item/wand/rod/unknown.png")
    save(cap("copper", (4, 3, 1)), f"{TEX}/item/wand/cap/copper.png")
    save(cap("brass", (5, 3, 1), pip="teal2"), f"{TEX}/item/wand/cap/resonant_brass.png")
    save(cap("hush", (3, 2, 0), notch=True), f"{TEX}/item/wand/cap/unknown.png")
    save(chime_rod_icon(), f"{TEX}/item/chime_rod.png")
    save(resonant_brass_cap_icon(), f"{TEX}/item/resonant_brass_cap.png")
    assert not (set(ROD_EDGE) & set(CAP)) and not (set(CAP_EDGE) & set(ROD))
    if len(sys.argv) > 2:  # preview composites: every rod x cap pair, as the item model draws them
        from PIL import Image

        out = sys.argv[2]
        os.makedirs(out, exist_ok=True)
        for r in ("wooden", "chime", "unknown"):
            for c in ("copper", "resonant_brass", "unknown"):
                base = Image.open(f"{TEX}/item/wand/rod/{r}.png").convert("RGBA")
                base.alpha_composite(Image.open(f"{TEX}/item/wand/cap/{c}.png").convert("RGBA"))
                base.save(f"{out}/wand_{r}_{c}.png")
    print("wands ok")
