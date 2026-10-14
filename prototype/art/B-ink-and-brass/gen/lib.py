"""Ink & Brass generator library: the locked palette plus the few pixel helpers every asset uses.

Every texture is a 16x16 RGBA numpy array. Paint with palette *names*, never raw hex, then call
outline() and save(); save() refuses any colour that is not in PAL, which is what keeps the palette locked.
"""

import os

import numpy as np
from PIL import Image

# One palette for the whole mod, derived from voices_from_the_void.png. Ramps run dark -> light.
RAMPS = {
    "ink": ["#0b0e1c", "#1a1a2a", "#2a2a3e", "#343849"],  # void, outline, ink-lift, slate
    "mauve": ["#464153", "#5e566b", "#7d7189"],  # logo panel slate-mauve
    "brass": ["#5b4d3b", "#85663f", "#a57c43", "#b89459", "#d8c399", "#e8d8b4"],  # logo brass to cream
    "copper": ["#5a2e2a", "#8a4430", "#b4643c", "#d88a58", "#f0b88a"],
    "amethyst": ["#3d2c5c", "#634a92", "#8f74c4", "#c4b0ec"],
    "teal": ["#1e4a4e", "#3f8a86", "#82c9bc", "#d2f2ea"],  # resonance; #82c9bc is the logo arc
    "glass": ["#8fa9b4", "#d5e6e8"],
    "hush": ["#55545c", "#77757c", "#9c99a0", "#c6c2c4"],  # drained, silent greys
}
PAL = {f"{r}{i}": c for r, cs in RAMPS.items() for i, c in enumerate(cs)}
OUTLINE = "ink1"
VOID = "ink0"
HEX = set(PAL.values())


def rgb(name):
    h = PAL[name]
    return tuple(int(h[i : i + 2], 16) for i in (1, 3, 5))


def canvas(n=16):
    return np.zeros((n, n, 4), np.uint8)


def put(img, x, y, name, a=255):
    if 0 <= x < img.shape[1] and 0 <= y < img.shape[0]:
        img[y, x] = (*rgb(name), a)


def ramp(name, t):
    """Pick a step of a ramp from t in [0, 1] (0 = darkest)."""
    cs = RAMPS[name]
    return f"{name}{min(len(cs) - 1, max(0, int(round(t * (len(cs) - 1)))))}"


def outline(img, name=OUTLINE):
    """Ink rule: every empty pixel 4-adjacent to an opaque one becomes ink navy."""
    solid = img[..., 3] > 0
    grow = np.zeros_like(solid)
    grow[1:] |= solid[:-1]
    grow[:-1] |= solid[1:]
    grow[:, 1:] |= solid[:, :-1]
    grow[:, :-1] |= solid[:, 1:]
    for y, x in zip(*np.nonzero(grow & ~solid)):
        put(img, x, y, name)
    return img


def check(img):
    used = {"#%02x%02x%02x" % tuple(p[:3]) for p in img.reshape(-1, 4) if p[3]}
    bad = used - HEX
    assert not bad, f"off-palette colours: {sorted(bad)}"
    return used


def save(img, path):
    check(img)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(img, "RGBA").save(path, optimize=False)


if __name__ == "__main__":  # self-check: palette size, outline rule, lock
    assert 24 <= len(PAL) <= 32, len(PAL)
    t = canvas(4)
    put(t, 1, 1, "brass3")
    outline(t)
    assert tuple(t[0, 1][:3]) == rgb("ink1") and t[0, 0, 3] == 0  # 4-adjacent only
    t[2, 2] = (1, 2, 3, 255)
    try:
        check(t)
        raise SystemExit("lock failed")
    except AssertionError:
        pass
    print("lib ok,", len(PAL), "colours")
