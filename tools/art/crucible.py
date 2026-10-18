"""The crucible's final assets (crucible spec, "Production tickets", "Crucible assets").

Usage: python3 crucible.py <assets_root>

Block faces are Stage 2 (art style §5): a brass vessel with two raised bands, brass dominant, shadows a
step cooler, and the teal tick as a small inlaid pip on the north rim of the top texture. The side is
painted as a front elevation in block space (art style §6): the rim lip at rows 0-1, the walls to row 11,
the floor's edge at rows 12-15, so one texture covers all four sides with default UVs.

The liquid is a 16x64 strip of 4 frames in two light neutral steps (glass1 ground, hush3 ripples) at the
style's liquid alpha, multiplied by the dominant aspect's colour at runtime: one ring expands per frame,
so frames differ by position only, never by brightness (the flash test).

The ingot is the chosen prototype's chime bar (prototype/art-pipeline, B-ink-and-brass gen/textures.py
`resonant_brass_ingot()`), unchanged. The missing-aspect glyph is drawn like an aspect icon (aspects.py
`draw()`), in the hush family: a broken ring struck through, so it reads apart from `unheard`'s whole ring
by shape and, on the HUD's ink1, by luminance.
"""

import math
import os
import sys

from aspects import draw
from lib import canvas, outline, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")
LIQUID_ALPHA = 235  # art style §6: liquids are an inset element at about 235


def crucible_side():
    img = canvas()
    rows = {  # front elevation, top to bottom: lip, under-lip, band, body, band, floor edge
        0: "brass4", 1: "brass2", 2: "brass0",
        3: "brass4", 4: "brass3", 5: "brass1",
        6: "brass1", 7: "brass1", 8: "brass1",
        9: "brass4", 10: "brass3", 11: "brass1",
        12: "brass1", 13: "brass1", 14: "brass0", 15: "ink2",
    }
    for y, c in rows.items():
        for x in range(16):
            put(img, x, y, c)
    for y in (6, 7, 8, 12, 13):  # plain metal between the bands: lit left column, shadowed right (art style §3)
        put(img, 1, y, "brass2")
        put(img, 14, y, "brass0")
    for x in range(2, 6):
        put(img, x, 0, "brass5")  # the lip's one narrow specular streak
    for x in (3, 12):  # rivets on each band, lit above and shadowed below
        for y in (3, 9):
            put(img, x, y, "brass5")
            put(img, x, y + 1, "brass1")
    for y in range(16):  # the leaded edge: ink on the vertical edges, as the jar's frame (art style §6)
        put(img, 0, y, "ink1")
        put(img, 15, y, "ink1")
    return img


def crucible_top():
    """Only the 2-px rim shows (the walls' tops); the middle is the opening, painted as dark interior."""
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            lit = x + y < 15
            if edge == 0:
                c = "ink1"
            elif edge == 1:
                c = "brass4" if lit else "brass2"
            elif edge == 2:
                c = "brass0"  # the inner lip's shadow
            else:
                c = "ink2"
            put(img, x, y, c)
    for x in range(3, 6):
        put(img, x, 1, "brass5")  # specular on the lit (north-west) rim
    put(img, 11, 1, "teal3")  # Stage 2: the teal tick as a small inlaid pip, set in the north rim
    put(img, 11, 2, "teal1")
    return img


def crucible_inner():
    """The inside walls (rows 0-11 with default UVs) and the floor (rows 2-13 show): brass in shadow, the
    lit inner lip at row 0 and one waterline groove; kept calm, since the liquid covers most of it."""
    img = canvas()
    for y in range(16):
        c = "brass2" if y == 0 else "brass0" if y == 3 else "brass1"
        for x in range(16):
            put(img, x, y, c)
    return img


def crucible_bottom():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            c = "ink1" if edge == 0 else "brass0" if edge == 1 else "brass1"
            if edge > 1 and (x in (7, 8) or y in (7, 8)):
                c = "brass2" if x + y < 15 else "brass0"  # a cross brace under the floor
            put(img, x, y, c)
    return img


def crucible_liquid():
    strip = canvas(16).repeat(4, axis=0)  # 16 wide, 64 tall
    for f in range(4):
        for y in range(16):
            for x in range(16):
                r = math.hypot(x - 7.5, y - 7.5)
                ripple = (r - f) % 4 < 1  # one ring per 4 px, moving out 1 px a frame: loops after 4
                put(strip, x, 16 * f + y, "hush3" if ripple else "glass1", LIQUID_ALPHA)
    return strip


def resonant_brass_ingot():
    """Chime bar: the alloy/ingot rule (flat bar, crisp bevels, a punched hanging hole, teal ring off the free end)."""
    img = canvas()
    rows = {5: "brass5", 6: "brass3", 7: "brass3", 8: "brass2", 9: "brass1", 10: "brass0"}  # bright top edge -> dark front
    for y, c in rows.items():
        x0, x1 = (2, 13) if y in (5, 10) else (1, 14)  # 1-px 45-degree bevel on all four corners
        for x in range(x0, x1 + 1):
            put(img, x, y, c)
        if y not in (5, 10):  # end faces: lit left, shadowed right
            put(img, 1, y, "brass4" if y < 9 else "brass2")
            put(img, 14, y, "brass2" if y < 9 else "brass0")
    for x in range(3, 9):
        put(img, x, 6, "brass4")  # sheen along the top face
    outline(img)
    for x in (3, 4):  # hanging hole: punched clean through (transparent), ink on its shadowed upper wall
        img[7, x] = 0
        put(img, x, 6, "ink1")
    put(img, 2, 7, "ink1")
    for x, y, c in [(15, 5, "teal3"), (15, 6, "teal2"), (15, 7, "teal2")]:
        put(img, x, y, c)  # it rings
    return img


def missing():
    """A ring broken at its four cardinal points and struck through from lower left to upper right:
    "no such aspect here". Lit from the upper left, as the aspect glyphs (aspects.py letter grid)."""
    rows = []
    for y in range(13):
        row = ""
        for x in range(13):
            dx, dy = x - 6, y - 6
            r = math.hypot(dx, dy)
            arc = 4.7 <= r <= 6.2 and 12 < math.degrees(math.atan2(dy, dx)) % 90 < 78
            slash = dx + dy in (0, 1) and r <= 3.6  # a 4-connected stair, clear of the ring
            row += ("S" if dx + dy > 3 else "H" if dx + dy < -3 else "B") if arc or slash else "."
        rows.append(row)
    return rows


if __name__ == "__main__":
    save(crucible_side(), f"{TEX}/block/crucible_side.png")
    save(crucible_top(), f"{TEX}/block/crucible_top.png")
    save(crucible_inner(), f"{TEX}/block/crucible_inner.png")
    save(crucible_bottom(), f"{TEX}/block/crucible_bottom.png")
    # The animation .mcmeta is datagen output (CrucibleData), not a hand-written resource.
    save(crucible_liquid(), f"{TEX}/block/crucible_liquid.png")
    save(resonant_brass_ingot(), f"{TEX}/item/resonant_brass_ingot.png")
    save(draw("hush", "hush3", missing())[0], f"{TEX}/gui/sprites/hud/crucible_missing.png")
    print("crucible ok")
