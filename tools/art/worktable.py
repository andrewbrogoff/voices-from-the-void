"""The Worktable's assets (arcane spec, "Production tickets", "Worktable assets").

Usage: python3 worktable.py <assets_root>

Block faces are Stage 1 (art style §5): warm wood (the brass ramp's darks) in a crafting table's frame,
turned to sound. The top carries an inlaid tuning fork and a chime bar, with the teal tick in the light at
the top-right corner, outside both objects; the side hangs a fork and a striker on pegs (acoustic tools,
never saws); the bottom is plain boards.

The GUI background is the 176x220 screen on a 256x256 sheet: a brass3 panel, an ink1 outline, a bevel, the
title and inventory label strips uniform brass4, the status line's rectangle uniform brass5, and an 18-px
well at every slot of the menu. The preview frames (26x26) differ by shape and luminance: idle a plain
brass2 frame, ready a teal inner ring, hover a bright frame with a light ring, disabled a hatched hush frame;
all keep the window ink1, so the white count always sits on ink1. The cost cells (40x18) keep their number
field x = 18..38 uniform brass5; the short cell adds a 3x3 ink1 notch in its top-right corner. The beyond
glyph (9x9) is an ink chevron pair: "more than this". The empty wand slot is a hush wand silhouette.
"""

import os
import sys

import numpy as np

from lib import canvas, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")


def blank(w, h):
    return np.zeros((h, w, 4), np.uint8)


def rect(img, x0, y0, x1, y1, c):
    """Fills x0..x1, y0..y1 inclusive."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, c)


def boards(img, vertical=False):
    """Wood boards across the face: brass1 grain, brass0 seams every 4 px, a lit edge on each board."""
    for y in range(16):
        for x in range(16):
            u, v = (x, y) if vertical else (y, x)
            c = "brass0" if u % 4 == 3 else "brass2" if u % 4 == 0 else "brass1"
            if c == "brass1" and (v * 3 + u * 5) % 11 == 0:
                c = "brass0"  # a sparse grain mark, never speckle
            put(img, x, y, c)


def frame(img):
    """The crafting table's frame: an ink1 edge and a lit brass3 band inside it."""
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, "ink1")
    for i in range(1, 15):
        put(img, i, 1, "brass3")
        put(img, 1, i, "brass3")
        put(img, i, 14, "brass0")
        put(img, 14, i, "brass0")


def fork(img, x0, y0, metal="copper"):
    """An upright tuning fork, 5 wide and 8 tall: two tines with a clear gap, a crotch and a stem."""
    for y in range(y0, y0 + 4):
        put(img, x0, y, f"{metal}3")  # lit left tine
        put(img, x0 + 1, y, f"{metal}2")
        put(img, x0 + 3, y, f"{metal}2")
        put(img, x0 + 4, y, f"{metal}1")  # shadowed right tine
    for x in range(x0, x0 + 5):
        put(img, x, y0 + 4, f"{metal}2")
    put(img, x0 + 2, y0 + 4, "amethyst2")  # the amethyst set in the crotch
    for y in range(y0 + 5, y0 + 8):
        put(img, x0 + 2, y, f"{metal}2")
    put(img, x0 + 2, y0 + 7, f"{metal}3")


def worktable_top():
    img = canvas()
    boards(img)
    frame(img)
    # the inlaid fork's ink bed, then the fork itself
    rect(img, 3, 3, 9, 12, "ink2")
    fork(img, 4, 4)
    # a chime bar laid flat beside it: bright top edge, darker front
    for x in range(10, 14):
        put(img, x, 9, "brass5")
        put(img, x, 10, "brass3")
        put(img, x, 11, "brass1")
    put(img, 10, 10, "ink1")  # its hanging hole
    # Stage 1: the teal tick outside every object, in the light at the top-right
    put(img, 12, 3, "teal2")
    put(img, 13, 3, "teal3")
    put(img, 13, 4, "teal2")
    return img


def worktable_side():
    img = canvas()
    boards(img, vertical=True)
    frame(img)
    for x in range(2, 14):  # the pegboard rail
        put(img, x, 3, "brass4")
        put(img, x, 4, "brass0")
    fork(img, 3, 5)
    # a felt-headed striker hanging on the second peg
    for y in range(5, 11):
        put(img, 11, y, "brass3")
        put(img, 12, y, "brass1")
    rect(img, 10, 11, 13, 12, "mauve2")
    put(img, 10, 11, "mauve1")
    return img


def worktable_bottom():
    img = canvas()
    boards(img)
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            put(img, x, y, "ink1")
    return img


# --- GUI ---------------------------------------------------------------------------------------

SLOTS = (
    [(8, 35), (134, 35)]
    + [(30 + 18 * c, 17 + 18 * r) for r in range(3) for c in range(3)]
    + [(8 + 18 * c, 136 + 18 * r) for r in range(3) for c in range(9)]
    + [(8 + 18 * c, 194) for c in range(9)]
)


def well(img, x, y):
    """An 18-px well around the item at (x, y): shadowed top-left, lit bottom-right, brass1 floor."""
    rect(img, x - 1, y - 1, x + 16, y + 16, "brass1")
    for i in range(-1, 17):
        put(img, x + i, y - 1, "ink2")
        put(img, x - 1, y + i, "ink2")
        put(img, x + i, y + 16, "brass4")
        put(img, x + 16, y + i, "brass4")


def background():
    img = blank(256, 256)
    rect(img, 0, 0, 175, 219, "brass3")
    for x in range(176):
        put(img, x, 0, "ink1")
        put(img, x, 219, "ink1")
        put(img, x, 1, "brass4")
        put(img, x, 218, "brass1")
    for y in range(220):
        put(img, 0, y, "ink1")
        put(img, 175, y, "ink1")
        if 0 < y < 219:
            put(img, 1, y, "brass4")
            put(img, 174, y, "brass1")
    rect(img, 6, 5, 169, 14, "brass4")  # title strip: text at (8, 6)
    rect(img, 6, 124, 169, 133, "brass4")  # inventory label strip: text at (8, 125)
    rect(img, 8, 112, 167, 120, "brass5")  # status line: 160x9 at (8, 112)
    for x, y in SLOTS:
        well(img, x, y)
    # an ink chevron from the grid to the preview
    for i in range(4):
        put(img, 88 + i, 40 + i, "ink1")
        put(img, 88 + i, 46 - i, "ink1")
    return img


def preview(state):
    img = blank(26, 26)
    border = {"idle": "brass2", "ready": "brass3", "hover": "brass5", "disabled": "hush1"}[state]
    rect(img, 0, 0, 25, 25, border)
    for i in range(26):
        for x, y in ((i, 0), (i, 25), (0, i), (25, i)):
            put(img, x, y, "ink1")
    rect(img, 3, 3, 22, 22, "ink1")  # the window: the item and its white count sit on ink1
    if state in ("ready", "hover"):
        ring = "teal2" if state == "ready" else "teal3"
        for i in range(2, 24):
            for x, y in ((i, 2), (i, 23), (2, i), (23, i)):
                put(img, x, y, ring)
    if state == "disabled":  # a 1-px hatch across the frame
        for y in range(1, 25):
            for x in range(1, 25):
                if (x + y) % 3 == 0 and not (3 <= x <= 22 and 3 <= y <= 22):
                    put(img, x, y, "ink2")
    return img


def cost_cell(short):
    img = blank(40, 18)
    rect(img, 0, 0, 39, 17, "brass3")  # the icon's ground at (1, 1)
    rect(img, 18, 1, 38, 16, "brass5")  # the number field, uniform
    for x in range(40):
        put(img, x, 0, "ink1")
        put(img, x, 17, "ink1")
    for y in range(18):
        put(img, 0, y, "ink1")
        put(img, 39, y, "ink1")
        put(img, 17, y, "brass1")  # the field's edge
    if short:
        rect(img, 36, 1, 38, 3, "ink1")  # the notch: the state never rests on hue
    return img


def beyond():
    img = blank(9, 9)
    for i in range(4):  # a chevron pair pointing right
        for dx in (0, 4):
            put(img, 1 + dx + i, 1 + i, "ink1")
            put(img, 1 + dx + i, 7 - i, "ink1")
    return img


def wand_slot():
    img = canvas()
    for i in range(10):  # a rod on the slot's diagonal, as vanilla's empty-slot icons lie
        put(img, 3 + i, 12 - i, "hush1")
    for x, y in ((2, 13), (3, 13), (2, 12), (12, 3), (13, 3), (13, 2), (12, 2)):
        put(img, x, y, "hush2")  # the two caps
    return img


if __name__ == "__main__":
    save(worktable_top(), f"{TEX}/block/worktable_top.png")
    save(worktable_side(), f"{TEX}/block/worktable_side.png")
    save(worktable_bottom(), f"{TEX}/block/worktable_bottom.png")
    save(background(), f"{TEX}/gui/container/worktable.png")
    sprites = f"{TEX}/gui/sprites"
    for state in ("idle", "ready", "hover", "disabled"):
        save(preview(state), f"{sprites}/worktable/preview_{state}.png")
    save(cost_cell(False), f"{sprites}/worktable/cost_cell.png")
    save(cost_cell(True), f"{sprites}/worktable/cost_cell_short.png")
    save(beyond(), f"{sprites}/worktable/beyond.png")
    save(wand_slot(), f"{sprites}/container/slot/wand.png")
    print("worktable ok")
