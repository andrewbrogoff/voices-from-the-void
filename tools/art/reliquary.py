"""The Reliquary's textures (worldgen spec, "Production tickets", "Reliquary assets").

Usage: python3 reliquary.py <assets_root>

Stage 3 (art style §5): mauve stone and slate in the shadows, brass bindings, the teal dimmed to
teal1 and set in a groove. Side faces are one front elevation in block space with default UVs (art
style §6), matching the four cuboids in WorldgenClientData:

- rows 2-3: the lid's edge (y 12-14), brass, its shadowed right end mauve;
- rows 4-9: the page box (y 6-12), slate panels bound in brass at both ends and both edges;
- rows 10-12: the neck (y 3-6), mauve stone;
- rows 13-15: the plinth (y 0-3), mauve stone lit along its top.

The front adds the box's brass hasp and the teal1 groove; the top is the lid in plan with its ink
page slot. Deterministic: no randomness.
"""

import os
import sys

from lib import canvas, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures/block")


def elevation(front):
    img = canvas()
    for y in range(2, 16):
        for x in range(16):
            if y <= 3:  # the lid's edge
                c = "brass4" if y == 2 else "brass2"
                if x >= 12:
                    c = "mauve2" if y == 2 else "mauve1"
            elif y <= 9:  # the page box
                edge = y in (4, 9) or x in (2, 3, 12, 13)
                if edge:
                    c = "brass3" if x < 8 else "brass1"
                    if y == 4 and x < 8:
                        c = "brass4"
                else:
                    c = "ink3" if x < 8 else "ink2"
            elif y <= 12:  # the neck
                c = "mauve1" if x < 8 else "mauve0"
                if y == 10:
                    c = "ink2"  # the box's shadow on the neck
            else:  # the plinth
                c = "mauve2" if y == 13 else ("mauve1" if x < 10 else "mauve0")
            put(img, x, y, c)
    if front:
        for x in range(4, 12):
            put(img, x, 7, "teal1")  # the groove, dimmed: Stage 3
        for y in (5, 6):
            for x in (7, 8):
                put(img, x, y, "brass4" if x == 7 else "brass2")  # the hasp
        put(img, 7, 4, "brass5")  # one narrow specular tick on the hasp's top
    return img


def top():
    img = canvas()
    for y in range(16):
        for x in range(16):
            ring = x in (0, 15) or y in (0, 15)
            lid = 1 <= x <= 14 and 2 <= y <= 13
            if lid:
                c = "brass3" if x + y < 15 else "brass2"
                if x >= 12 or y >= 12:
                    c = "mauve1"  # Stage 3: mauve creeps into the lid's shadowed edge
            else:
                c = "mauve2" if ring and (x < 8 and y < 8) else "mauve1"
            put(img, x, y, c)
    for x in range(4, 12):
        put(img, x, 7, "ink0")  # the page slot
        put(img, x, 8, "ink1")
    for x in range(3, 6):
        put(img, x, 3, "brass5")  # one specular streak on the lid
    return img


def bottom():
    img = canvas()
    for y in range(16):
        for x in range(16):
            put(img, x, y, "mauve0" if (x + y) % 7 else "ink3")
    return img


if __name__ == "__main__":
    save(elevation(False), os.path.join(TEX, "reliquary_side.png"))
    save(elevation(True), os.path.join(TEX, "reliquary_front.png"))
    save(top(), os.path.join(TEX, "reliquary_top.png"))
    save(bottom(), os.path.join(TEX, "reliquary_bottom.png"))
