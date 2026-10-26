"""The infusion assets (infusion spec, "Production tickets", "Infusion assets").

Usage: python3 infusion.py <assets_root>

Every asset is Stage 3 (art style §5): brass with mauve and slate creeping into the shadows, and the
teal dimmed to teal1 and set in grooves rather than outside the object. Block faces are front
elevations in block space with default UVs (art style §6):

- Singing Bowl side: rows 2-5 the bowl's wall (y 10-14), rows 6-7 the floor's band (y 8-10), rows
  8-15 the deepslate plinth (y 0-8). One teal1 groove runs round the wall.
- Pedestal side: rows 4-5 the brass top plate (y 10-12), rows 6-13 the polished deepslate shaft
  (y 2-10) with a teal1 groove, rows 14-15 the slate base (y 0-2).

The Echo rod and cap icons and wand layers reuse the chime rod's and brass cap's silhouettes
(wands.py), so an Echo part composes with every other part; their shadows are mauve and their
grooves teal1.
"""

import os
import sys

from lib import canvas, outline, put, save
from wands import CAP_BOTTOM, CAP_EDGE, CAP_TOP, ROD, ROD_EDGE, TICK

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")


def bowl_side():
    img = canvas()
    for y in range(16):
        for x in range(16):
            if y < 2:
                continue  # above the rim: never shown
            if y <= 5:  # the wall: rim lip lit, body brass, shadowed right
                c = "brass4" if y == 2 else "brass3" if x < 8 else "brass2"
                if x >= 12:
                    c = "mauve2"  # Stage 3: mauve creeps into the shadowed side
            elif y <= 7:  # the floor's band under the wall
                c = "brass1" if y == 6 else "mauve1"
            else:  # the deepslate plinth
                c = "mauve1" if x < 5 else "ink3" if x < 12 else "ink2"
                if y == 8:
                    c = "mauve0"  # the plinth's shadowed shoulder under the floor
            put(img, x, y, c)
    for x in range(2, 14):
        put(img, x, 4, "teal1")  # the groove that rings
    for x in range(3, 6):
        put(img, x, 2, "brass5")  # one narrow specular streak on the rim
    return img


def bowl_top():
    """The rim (the walls' top faces): a lit brass ring with a teal1 groove pip."""
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            lit = x + y < 15
            if edge < 2:
                c = "ink2"
            elif edge == 2:
                c = "brass4" if lit else "brass2"
            else:
                c = "mauve1"  # the opening, dark (the floor shows through the inner texture)
            put(img, x, y, c)
    for x in range(4, 7):
        put(img, x, 2, "brass5")
    put(img, 10, 2, "teal1")
    return img


def bowl_inner():
    """The walls' insides and the floor: brass in shadow, mauve deepest, a teal1 groove ring."""
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            c = "brass2" if y <= 2 else "brass1" if edge > 4 else "mauve2"
            if edge == 5:
                c = "teal1"  # a groove ring on the floor
            put(img, x, y, c)
    return img


def bowl_bottom():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            put(img, x, y, "ink2" if edge < 3 else "mauve0" if edge == 3 else "ink3")
    return img


def pedestal_side():
    img = canvas()
    for y in range(4, 16):
        for x in range(16):
            if y <= 5:  # the brass top plate
                c = "brass4" if y == 4 else "brass2"
                if y == 4 and 4 <= x <= 6:
                    c = "brass5"
            elif y <= 13:  # the polished deepslate shaft: lit left, slate right
                c = "mauve2" if x <= 5 else "mauve1" if x <= 9 else "ink3"
            else:  # the slate base
                c = "mauve0" if y == 14 else "ink2"
            put(img, x, y, c)
    for x in range(4, 12):
        put(img, x, 9, "teal1")  # the groove
    return img


def pedestal_top():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            lit = x + y < 15
            if edge < 3:
                c = "mauve1" if lit else "mauve0"  # the base's slate shoulder
            elif edge == 3:
                c = "brass4" if lit else "brass2"
            elif edge == 5:
                c = "teal1"  # a groove ring on the plate
            else:
                c = "brass3"
            put(img, x, y, c)
    return img


def pedestal_bottom():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            put(img, x, y, "ink2" if edge < 2 else "ink3")
    return img


def echo_rod_icon():
    """An upright brass bar like the chime rod, flared at the top like a horn's bell; mauve in the
    shadow column, two teal1 grooves."""
    img = canvas()
    for y in range(3, 15):
        for x in (6, 7, 8):
            put(img, x, y, "brass4" if x == 6 else "brass3" if x == 7 else "mauve2")
    for x in range(5, 10):  # the flare
        put(img, x, 2, "brass4" if x < 8 else "mauve2")
    for x in range(4, 11):
        put(img, x, 1, "brass5" if x == 5 else "brass3" if x < 8 else "mauve1")
    for y in (6, 11):
        for x in (6, 7, 8):
            put(img, x, y, "teal1")
    outline(img)
    return img


def echo_cap_icon():
    """A squat cup like the brass cap, mauve in its shadow and a teal1 groove round its waist."""
    img = canvas()
    for y in range(5, 12):
        half = 3 if y < 7 else 4
        for x in range(8 - half, 8 + half):
            lit = x < 8 - half + 2
            c = "brass4" if y == 5 else "brass3" if lit else "brass2" if x < 8 + half - 2 else "mauve1"
            put(img, x, y, c)
    for x in range(5, 11):
        put(img, x, 11, "mauve0")  # the open mouth
    for x in range(4, 12):
        put(img, x, 8, "teal1")
    put(img, 5, 5, "brass5")
    outline(img)
    return img


def echo_rod_layer():
    """The wand's rod layer for the Echo rod: the shared shaft, mauve in shadow, teal1 grooves."""
    img = canvas()
    for x, y in ROD:
        put(img, x, y, "brass4" if x == 7 else "mauve2")
    for y in (6, 9):
        put(img, 7, y, "teal1")
        put(img, 8, y, "teal1")
    for x, y in ROD_EDGE:
        put(img, x, y, "ink1")
    return img


def echo_cap_layer():
    """The wand's cap layer for the Echo cap: the shared caps, mauve in shadow, a teal1 groove."""
    img = canvas()
    for part in (CAP_TOP, CAP_BOTTOM):
        top = min(y for _, y in part)
        for x, y in part:
            shade = "brass4" if (y == top or x == 6) else "mauve1" if (x == 9 or y == top + 2) else "brass2"
            put(img, x, y, shade)
        for x in (7, 8):
            put(img, x, top + 1, "teal1")
    for x, y in CAP_EDGE:
        put(img, x, y, "ink1")
    for x, y, c in TICK:
        put(img, x, y, c)
    return img


if __name__ == "__main__":
    save(bowl_side(), f"{TEX}/block/singing_bowl_side.png")
    save(bowl_top(), f"{TEX}/block/singing_bowl_top.png")
    save(bowl_inner(), f"{TEX}/block/singing_bowl_inner.png")
    save(bowl_bottom(), f"{TEX}/block/singing_bowl_bottom.png")
    save(pedestal_side(), f"{TEX}/block/pedestal_side.png")
    save(pedestal_top(), f"{TEX}/block/pedestal_top.png")
    save(pedestal_bottom(), f"{TEX}/block/pedestal_bottom.png")
    save(echo_rod_icon(), f"{TEX}/item/echo_rod.png")
    save(echo_cap_icon(), f"{TEX}/item/echo_cap.png")
    save(echo_rod_layer(), f"{TEX}/item/wand/rod/echo.png")
    save(echo_cap_layer(), f"{TEX}/item/wand/cap/echo.png")
    if len(sys.argv) > 2:  # preview composites: every wand that includes an Echo part
        from PIL import Image

        out = sys.argv[2]
        os.makedirs(out, exist_ok=True)
        for r, c in [("echo", "echo"), ("echo", "copper"), ("echo", "resonant_brass"),
                     ("wooden", "echo"), ("chime", "echo")]:
            base = Image.open(f"{TEX}/item/wand/rod/{r}.png").convert("RGBA")
            base.alpha_composite(Image.open(f"{TEX}/item/wand/cap/{c}.png").convert("RGBA"))
            base.save(f"{out}/wand_{r}_{c}.png")
    print("infusion ok")
