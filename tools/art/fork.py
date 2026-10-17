"""The Tuning Fork's final assets (aspects spec, "Production tickets"): the fork, its "ringing" cooldown
variant, and the crosshair-card frame its readout draws in. Usage: python3 fork.py <assets_root>

The fork is the chosen prototype (prototype/art-pipeline, B-ink-and-brass gen/textures.py), unchanged in
silhouette: two 2-px copper tines with a 4-px gap, a rounded crotch, stem and knob, the amethyst set in
the crotch, and the Stage 1 teal tick off the right tine tip. Ringing adds a second tick off the left tine
and lights both tines, so the variant differs by shape as well as by tint.

The frame is the shared nine-slice the readout cards use (16x16, border 4; its .mcmeta is written by
datagen): a brass band lit from the upper left, an ink groove, and an interior of opaque ink1.
"""

import os
import sys

from lib import canvas, outline, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")


def tuning_fork(ringing=False):
    img = canvas()
    cu = lambda i: f"copper{i}"
    for y in range(1, 9):  # tines: 2 px each, 4 px gap (ink, clear, clear, ink)
        put(img, 4, y, cu(4 if ringing and y < 5 else 3))
        put(img, 5, y, cu(2))
        put(img, 10, y, cu(4 if ringing and y < 5 else 3))
        put(img, 11, y, cu(1))
    put(img, 4, 1, cu(4))  # lit tine tips
    put(img, 10, 1, cu(4))
    put(img, 6, 8, cu(2))  # rounded inner crotch
    put(img, 9, 8, cu(1))
    for x in range(4, 12):
        put(img, x, 9, cu(3) if x < 7 else cu(2) if x < 10 else cu(1))
    for x in range(5, 11):
        put(img, x, 10, cu(2) if x < 8 else cu(1))
    for x in range(6, 10):
        put(img, x, 11, cu(2) if x < 8 else cu(1))
    put(img, 7, 12, cu(3))  # stem
    put(img, 8, 12, cu(1))
    for y in (13, 14):  # handle knob
        for x, i in zip(range(6, 10), (3, 4 if y == 13 else 3, 2, 1)):
            put(img, x, y, cu(i))
    for x, y, c in [(7, 7, "amethyst3"), (7, 8, "amethyst3"), (8, 8, "amethyst2"), (7, 9, "amethyst2"), (8, 9, "amethyst1")]:
        put(img, x, y, c)  # the amethyst set in the crotch, pointing up the gap
    outline(img)
    for x, y, c in [(13, 0, "teal3"), (14, 1, "teal2"), (14, 2, "teal2")]:
        put(img, x, y, c)  # Stage 1: the resonance tick outside the sprite, by the right tine tip
    if ringing:
        for x, y, c in [(2, 0, "teal3"), (1, 1, "teal2"), (1, 2, "teal2")]:
            put(img, x, y, c)  # and its mirror by the left tine: the fork is sounding
    return img


def card_frame():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                put(img, x, y, "ink1")
            elif edge in (1, 2):
                lit = x + y < 15  # the upper-left half of the band catches the light
                put(img, x, y, ("brass4" if edge == 1 else "brass3") if lit else ("brass1" if edge == 1 else "brass2"))
            elif edge == 3:
                put(img, x, y, "ink2")  # the groove
            else:
                put(img, x, y, "ink1")  # the text region: opaque ink1 (localisation, Text contrast)
    return img


if __name__ == "__main__":
    save(tuning_fork(), f"{TEX}/item/tuning_fork.png")
    save(tuning_fork(ringing=True), f"{TEX}/item/tuning_fork_ringing.png")
    save(card_frame(), f"{TEX}/gui/sprites/hud/vis_frame.png")
    print("fork ok")
