"""The hushed block textures and the mote (Hush spec, "Production tickets", "Hushed block assets").

Usage: python3 hush_blocks.py <assets_root>

Every texture is an original drawing in the hush ramp only (art style §5, "The Hush"): each keeps its
source's light structure at the same steps (lit upper left, broad clusters, no speckle noise) and its
shape, but the colour is gone and there is no teal, never purple, glossy or organic. The 1-px checker
fray appears only where a side face's drained top band meets its body: the grass side's band, and the
bark rim of each log top. Leaves and short grass are cutouts (fully clear or fully opaque pixels).

The mote is 4 frames of 8x8 in the hush ramp: a soft speck that shrinks as it fades, so frames differ by
shape and by at most one step of luminance (the flash test; the particle holds each frame 10 ticks).
Deterministic: fixed seeds, SEED + n per asset.
"""

import os
import sys

import numpy as np

from lib import canvas, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")
SEED = 1600
H = [f"hush{i}" for i in range(4)]


def rng(n):
    return np.random.default_rng(SEED + n)


def fill(img, name):
    for y in range(16):
        for x in range(16):
            put(img, x, y, name)


def clusters(img, r, names, count, size, rows=range(16)):
    """Broad blobs of the given steps: a seed pixel grown into its 4-neighbours, wrapping (tileable)."""
    rows = list(rows)
    for _ in range(count):
        name = names[int(r.integers(len(names)))]
        x, y = int(r.integers(16)), rows[int(r.integers(len(rows)))]
        for _ in range(size):
            if y in rows:
                put(img, x % 16, y, name)
            dx, dy = [(1, 0), (-1, 0), (0, 1), (0, -1)][int(r.integers(4))]
            x, y = (x + dx) % 16, y + dy
            if y not in rows:
                y = rows[int(r.integers(len(rows)))]


def lit(img, x, y, name_lit, name_dark):
    """A pebble's light: the upper-left pixel lit, the lower-right shadowed."""
    put(img, x, y, name_lit)
    put(img, (x + 1) % 16, (y + 1) % 16, name_dark)


def dirt(n=0):
    img = canvas()
    r = rng(n)
    fill(img, "hush1")
    clusters(img, r, ["hush0"], 14, 4)
    clusters(img, r, ["hush2"], 9, 3)
    return img


def stone():
    img = canvas()
    r = rng(1)
    fill(img, "hush2")
    clusters(img, r, ["hush1"], 12, 5)
    clusters(img, r, ["hush3"], 6, 2)
    return img


def deepslate():
    img = canvas()
    r = rng(2)
    for y in range(16):  # horizontal strata: the deepslate's bedding, dark
        for x in range(16):
            put(img, x, y, "hush1" if (y // 2) % 3 else "hush0")
    clusters(img, r, ["hush0"], 8, 4)
    clusters(img, r, ["hush2"], 5, 2)
    return img


def deepslate_top():
    img = canvas()
    r = rng(3)
    fill(img, "hush1")
    for y in range(16):  # concentric squares: the column's end grain
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if int(d) % 3 == 0:
                put(img, x, y, "hush0")
    clusters(img, r, ["hush2"], 4, 2)
    return img


def sand():
    img = canvas()
    r = rng(4)
    fill(img, "hush3")
    clusters(img, r, ["hush2"], 16, 2)
    return img


def gravel():
    img = canvas()
    r = rng(5)
    fill(img, "hush1")
    for _ in range(22):  # pebbles, each lit upper left and shadowed lower right
        x, y = int(r.integers(16)), int(r.integers(16))
        base = H[int(r.integers(1, 4))]
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(img, (x + dx) % 16, (y + dy) % 16, base)
        lit(img, x, y, "hush3", "hush0")
    return img


def grass_top():
    img = canvas()
    r = rng(6)
    fill(img, "hush2")
    clusters(img, r, ["hush3"], 12, 4)
    clusters(img, r, ["hush1"], 10, 3)
    return img


def grass_side():
    img = dirt(7)
    r = rng(8)
    for y in range(4):  # the drained top band: the grass's lip, light
        for x in range(16):
            put(img, x, y, "hush3" if y == 0 else "hush2")
    for x in range(16):
        drop = int(r.integers(0, 2))  # a ragged lower edge, as grass hangs over its soil
        for y in range(4, 4 + drop):
            put(img, x, y, "hush2")
    for x in range(16):  # the 1-px checker fray where the band meets the body
        y = 5 if x % 2 == 0 else 4
        put(img, x, y, "hush2" if x % 2 == 0 else "hush1")
    return img


def short_grass():
    img = canvas()
    r = rng(9)
    for i, x in enumerate((2, 4, 7, 9, 11, 13)):  # blades, upright, lit on their left
        top = int(r.integers(3, 8))
        lean = (i % 3) - 1
        for y in range(15, top - 1, -1):
            xx = x + (lean if y < 8 else 0)
            put(img, xx, y, "hush3" if y < top + 2 else "hush2")
            if y > top + 3:
                put(img, xx + 1, y, "hush1")
    return img


BARK = {
    "oak": ("hush0", "hush1", "hush2"),
    "birch": ("hush3", "hush2", "hush0"),
    "spruce": ("hush0", "hush0", "hush1"),
}


def log_side(kind, n):
    img = canvas()
    r = rng(n)
    dark, mid, light = BARK[kind]
    for x in range(16):  # vertical bark ridges, lit columns left of each groove
        for y in range(16):
            put(img, x, y, light if x % 4 == 0 else mid)
    if kind == "birch":  # birch's dark dashes across its pale bark
        for _ in range(9):
            x, y = int(r.integers(16)), int(r.integers(16))
            for dx in range(int(r.integers(2, 5))):
                put(img, (x + dx) % 16, y, "hush0")
    else:
        for x in range(1, 16, 4):  # the grooves
            for y in range(16):
                if r.random() < 0.8:
                    put(img, x + 2 if x + 2 < 16 else x, y, dark)
    return img


def log_top(kind, n):
    img = canvas()
    dark, mid, light = BARK[kind]
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                put(img, x, y, dark if kind != "birch" else "hush3")  # the bark rim
            else:
                ring = int(max(abs(x - 7.5), abs(y - 7.5)))
                put(img, x, y, "hush2" if ring % 2 else "hush3")  # growth rings, pale wood
    for i in range(1, 15):  # the 1-px checker fray between the bark rim and the wood
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            if (x + y) % 2 == 0:
                put(img, x, y, mid)
    put(img, 7, 7, "hush1")
    put(img, 8, 8, "hush1")
    return img


LEAF = {"oak": ("hush1", "hush2", "hush3"), "birch": ("hush2", "hush3", "hush3"), "spruce": ("hush0", "hush1", "hush2")}


def leaves(kind, n):
    img = canvas()
    r = rng(n)
    dark, mid, light = LEAF[kind]
    fill(img, mid)
    clusters(img, r, [dark], 12, 4)
    clusters(img, r, [light], 7, 3)
    for _ in range(9):  # holes in small clusters: cutout, fully clear
        x, y = int(r.integers(16)), int(r.integers(16))
        for _ in range(3):
            img[y % 16, x % 16] = (0, 0, 0, 0)
            dx, dy = [(1, 0), (0, 1), (-1, 0), (0, -1)][int(r.integers(4))]
            x, y = x + dx, y + dy
    return img


def mote(frame):
    img = canvas(8)
    size = [3, 2, 2, 1][frame]
    step = ["hush3", "hush3", "hush2", "hush2"][frame]
    o = (8 - size) // 2
    for y in range(size):
        for x in range(size):
            put(img, o + x, o + y, step)
    return img


if __name__ == "__main__":
    b = os.path.join(TEX, "block")
    out = {
        "hushed_dirt": dirt(),
        "hushed_stone": stone(),
        "hushed_deepslate": deepslate(),
        "hushed_deepslate_top": deepslate_top(),
        "hushed_sand": sand(),
        "hushed_gravel": gravel(),
        "hushed_grass_block_top": grass_top(),
        "hushed_grass_block_side": grass_side(),
        "hushed_short_grass": short_grass(),
    }
    for i, kind in enumerate(("oak", "birch", "spruce")):
        out[f"hushed_{kind}_log"] = log_side(kind, 20 + i)
        out[f"hushed_{kind}_log_top"] = log_top(kind, 30 + i)
        out[f"hushed_{kind}_leaves"] = leaves(kind, 40 + i)
    for name, img in out.items():
        save(img, os.path.join(b, name + ".png"))
    for f in range(4):
        save(mote(f), os.path.join(TEX, "particle", f"hush_mote_{f}.png"))
