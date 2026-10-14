"""Vanilla Kin textures. Each sprite is a hand-authored 16x16 index grid: one character per pixel,
each character mapped to (ramp, shade[, alpha]) from palette.py. Deterministic, no randomness needed.

Usage: python3 textures.py <assets_root>
"""

import os
import sys

from PIL import Image

from palette import RAMPS, rgba


def sprite(rows, legend, size=16):
    assert len(rows) == size and all(len(r) == size for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            ramp, i, *a = legend[ch]
            img.putpixel((x, y), rgba(RAMPS[ramp][i], *a))
    return img


def digits(ramp_name):
    """'0'..'5' -> shade 0..5 of one ramp: the main material of a sprite."""
    return {str(i): (ramp_name, i) for i in range(len(RAMPS[ramp_name]))}


# Stage 1. Copper fork, tines up-right like vanilla tools; amethyst set in the crotch of the U.
TUNING_FORK = sprite([
    "................",
    "..........54....",
    ".........42.....",
    "........42..53..",
    ".......42..42...",
    "......42..42....",
    ".....42ec42.....",
    "....42ba41......",
    "....43221.......",
    "....321.........",
    "...42...........",
    "..42............",
    ".431............",
    ".321............",
    "..1.............",
    "................",
], {**digits("copper"), "a": ("amethyst", 0), "b": ("amethyst", 2), "c": ("amethyst", 3), "e": ("amethyst", 4)})

# Stage 2. Resonant brass: a level cast bar (vanilla ingots lie diagonal; this one lies flat) in 3/4 view,
# with a ring-groove round its waist that pinches the silhouette. One teal glint: it rings.
RESONANT_BRASS_INGOT = sprite([
    "................",
    "................",
    "................",
    "................",
    "................",
    "...3444334443...",
    "..34444224r443..",
    "..455553354443..",
    ".23333311333321.",
    ".23333311333321.",
    ".12222211222221.",
    ".01111100111110.",
    "..000000000000..",
    "................",
    "................",
    "................",
], {**digits("brass"), "r": ("ring", 3)})

# Stage 4. Hollow bell: hushed bronze, a crack, and the mouth opening onto the Void.
HOLLOW_BELL = sprite([
    "................",
    "......2332......",
    "......3..1......",
    ".....234321.....",
    "....24543321....",
    "....24541321....",
    "....24543121....",
    "....2454v321....",
    "....24541321....",
    "...2345431211...",
    "...2345443110...",
    "..234555433210..",
    ".23455555443210.",
    "..1wwwwwxwwww0..",
    "................",
    "................",
], {**digits("hushed"), "v": ("void", 2), "w": ("void", 0), "x": ("void", 3)})


GLASS_FILL, GLASS_EDGE = 64, 225  # alpha: pane body vs. thickened edge


def glass_panel(w, h, streak):
    """A w x h pane at the texture's top-left: frame in the glass ramp (lit top/left, shaded bottom/right),
    near-clear body, clipped corners so the jar's silhouette reads rounded, and an optional
    1-px highlight streak on the upper left (as on vanilla glass)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    g = RAMPS["glass"]
    for y in range(h):
        for x in range(w):
            if (x in (0, w - 1)) and (y in (0, h - 1)):
                continue  # clipped corner
            if y == 0 or x == 0:
                c = rgba(g[3], GLASS_EDGE)
            elif y == h - 1 or x == w - 1:
                c = rgba(g[1], GLASS_EDGE)
            else:
                c = rgba(g[4], GLASS_FILL)
            img.putpixel((x, y), c)
    if streak:
        for x, y, a in [(2, 2, 210), (2, 3, 210), (2, 4, 210), (2, 5, 170), (3, 2, 150), (2, 7, 120)]:
            img.putpixel((x, y), rgba(g[4], a))
    return img


def jar_lid():
    """Lid: brass cap. Top face uv (0,0..8,8), side band uv (0,8..8,10), knob (8,0..12,4) top, (8,4..12,5) side."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    b = RAMPS["brass"]
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            shade = 2 if edge else (4 if x + y < 7 else 3)
            if (x, y) in ((1, 1), (2, 1), (1, 2)):
                shade = 5
            img.putpixel((x, y), rgba(b[shade]))
    for y in (8, 9):
        for x in range(8):
            img.putpixel((x, y), rgba(b[3 if y == 8 else 1]))
    for y in range(4):
        for x in range(8, 12):
            img.putpixel((x, y), rgba(b[5 if (x, y) == (9, 1) else 4 if x + y < 12 else 3]))
    for x in range(8, 12):
        img.putpixel((x, 4), rgba(b[2]))
    return img


def main(root):
    item = os.path.join(root, "vftv/textures/item")
    block = os.path.join(root, "vftv/textures/block")
    os.makedirs(item, exist_ok=True)
    os.makedirs(block, exist_ok=True)
    TUNING_FORK.save(os.path.join(item, "tuning_fork.png"))
    RESONANT_BRASS_INGOT.save(os.path.join(item, "resonant_brass_ingot.png"))
    HOLLOW_BELL.save(os.path.join(item, "hollow_bell.png"))
    glass_panel(10, 9, streak=True).save(os.path.join(block, "essentia_jar_side.png"))
    glass_panel(10, 10, streak=False).save(os.path.join(block, "essentia_jar_top.png"))
    jar_lid().save(os.path.join(block, "essentia_jar_lid.png"))


if __name__ == "__main__":
    main(sys.argv[1])
