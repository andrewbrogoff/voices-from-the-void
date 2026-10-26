"""Node, jar and relay assets (wands spec, "Production tickets"): the node core strip, the glint particle,
the Bell Jar item (empty and filled) and the relay members' block textures. Usage: python3 nodes.py <assets_root>

The core is an 8-frame 16x128 strip of a ringing teal orb, cutout (no partial alpha): a bright centre,
the teal ramp outward and an ink rim, its radius breathing by one pixel over the cycle so neighbouring
frames differ only slightly in mean luminance (the flash test). The glint is 4 frames at 8x8: a spark that
opens into a cross and fades down the ramp.

Stage 2 (art style §5): brass dominant, cooler (ink) shadows, teal as a small inlaid pip only. The member
block models take default UVs from position (at most six cuboids each), so each block texture is painted
in block space:
  node_tap        a ringed brass sheet: dark seams on the rows and columns where the trumpet's cuboids
                  step (rim, bell, throat, tube, earpiece), so every face shows its bands.
  node_tap_mouth  the rim's node-facing face (x, y 3..12): a brass ring round the dark throat, the teal pip
                  inlaid at its heart.
  vis_relay_fork  the fork's brass, the tine tips (rows 1..3) inlaid teal.
  vis_relay_stand slate with a brass edge band round the 10x10 foot.
  wand_cradle_*   oak-toned planks for the base, brass saddles for the two rests.
Item sprites (bell_jar, bell_jar_filled) follow §3: outlined once in ink1, no partial alpha; the filled jar
holds a small teal orb, so the two read apart in greyscale by the orb's shape.
Deterministic: no randomness.
"""

import math
import os
import sys

import numpy as np

from lib import canvas, outline, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")


def core_strip():
    strip = np.zeros((128, 16, 4), np.uint8)
    for f in range(8):
        frame = canvas(16)
        r = 5.0 + 0.8 * math.sin(2 * math.pi * f / 8)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= r - 3.2:
                    put(frame, x, y, "teal3")
                elif d <= r - 1.8:
                    put(frame, x, y, "teal2")
                elif d <= r - 0.6:
                    put(frame, x, y, "teal1")
                elif d <= r + 0.4:
                    put(frame, x, y, "ink1")
        strip[f * 16 : (f + 1) * 16] = frame
    return strip


def luminance(img):
    """Mean relative luminance of the opaque pixels (the flash test's measure)."""
    total, n = 0.0, 0
    for row in img:
        for p in row:
            if p[3]:
                lin = [(c / 255) / 12.92 if c / 255 <= 0.04045 else ((c / 255 + 0.055) / 1.055) ** 2.4 for c in p[:3]]
                total += 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
                n += 1
    return total / max(1, n)


def glint(frame):
    img = canvas(8)
    shapes = [
        [(3, 3, "teal3"), (4, 4, "teal3"), (3, 4, "teal2"), (4, 3, "teal2")],
        [(3, 3, "teal3"), (4, 4, "teal3"), (3, 4, "teal3"), (4, 3, "teal3"), (3, 2, "teal2"), (4, 5, "teal2"), (2, 4, "teal2"), (5, 3, "teal2")],
        [(3, 3, "teal2"), (4, 4, "teal2"), (3, 4, "teal2"), (4, 3, "teal2"), (3, 1, "teal1"), (4, 6, "teal1"), (1, 4, "teal1"), (6, 3, "teal1")],
        [(3, 3, "teal1"), (4, 4, "teal1")],
    ]
    for x, y, c in shapes[frame]:
        put(img, x, y, c)
    return img


def fill(img, x0, y0, x1, y1, name):
    """Inclusive rectangle."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, name)


def node_tap():
    img = canvas()
    fill(img, 0, 0, 15, 15, "brass3")
    for i in range(16):
        for j in (1, 4, 7, 13):  # the cuboid steps, along z on the sides and as rings on the ends
            put(img, j, i, "brass1")
            put(img, i, 16 - j, "brass1")
        put(img, 0, i, "brass4")  # lit edge
        put(img, i, 0, "brass4")
        put(img, 15, i, "ink2")  # cool shadow edge
        put(img, i, 15, "ink2")
    for i in range(2, 14, 3):
        put(img, i, 8, "brass5")  # a specular run along the tube
    return img


def node_tap_mouth():
    img = canvas()
    fill(img, 3, 3, 12, 12, "brass3")
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3 <= x <= 12 and 3 <= y <= 12:
                if d <= 2.2:
                    put(img, x, y, "ink0")
                elif d <= 3.4:
                    put(img, x, y, "ink1")
                elif d <= 4.2:
                    put(img, x, y, "brass1")
    for x in range(3, 13):
        put(img, x, 3, "brass4")
        put(img, x, 12, "ink2")
    put(img, 7, 7, "teal2")  # Stage 2: the inlaid pip
    put(img, 8, 8, "teal1")
    return img


def vis_relay_fork():
    img = canvas()
    fill(img, 0, 0, 15, 15, "brass3")
    for i in range(16):
        put(img, i, 6, "brass4")  # the yoke's lit top
        put(img, i, 8, "brass1")  # its underside
        put(img, 15, i, "ink2")
    for y in range(1, 4):
        for x in range(16):
            put(img, x, y, "teal2" if y > 1 else "teal3")  # the tine tips, inlaid
    for x in range(16):
        put(img, x, 0, "ink1")
    return img


def vis_relay_stand():
    img = canvas()
    fill(img, 0, 0, 15, 15, "ink3")
    for i in range(3, 13):
        for e in (3, 12):
            put(img, i, e, "brass2")
            put(img, e, i, "brass2")
    for y in range(14, 16):
        for x in range(16):
            put(img, x, y, "brass1" if y == 14 else "ink2")  # the foot's rim on the sides
    put(img, 7, 7, "mauve1")
    put(img, 8, 8, "mauve0")
    return img


def wand_cradle_base():
    img = canvas()
    for y in range(16):
        for x in range(16):
            plank = y // 4
            seam = y % 4 == 3
            put(img, x, y, "ink2" if seam else ("brass1" if (x + plank * 5) % 11 else "brass0"))
    for y in (1, 5, 9, 13):
        put(img, 3, y, "copper2")  # nail heads
        put(img, 12, y, "copper2")
    return img


def wand_cradle_rest():
    img = canvas()
    fill(img, 0, 0, 15, 15, "brass3")
    for i in range(16):
        put(img, i, 9, "brass5")  # the saddle's lit lip
        put(img, i, 10, "brass1")
        put(img, 15, i, "ink2")
    return img


def bell_jar(filled):
    img = canvas()
    # half-widths per row of the dome, rows 2..11, on a brass foot at rows 12..14
    dome = {2: 2, 3: 3, 4: 4, 5: 4, 6: 5, 7: 5, 8: 5, 9: 5, 10: 5, 11: 5}
    for y, w in dome.items():
        for x in range(8 - w, 8 + w):
            edge = x in (8 - w, 8 + w - 1) or y == 2
            if edge:
                put(img, x, y, "glass1" if x < 8 else "glass0")
    put(img, 7, 1, "brass4")  # the knob
    put(img, 8, 1, "brass3")
    for x in (5, 6):
        put(img, x, 4 + (x - 5), "glass1")  # a highlight streak on the lit side
    for x in range(2, 14):
        put(img, x, 12, "brass4")
        put(img, x, 13, "brass2")
        put(img, x, 14, "brass1")
    outline(img)
    if filled:
        for x, y, c in [(7, 7, "teal3"), (8, 7, "teal2"), (7, 8, "teal2"), (8, 8, "teal1"),
                        (6, 7, "teal1"), (9, 8, "teal1"), (7, 6, "teal2"), (8, 9, "teal1")]:
            put(img, x, y, c)
    return img


if __name__ == "__main__":
    strip = core_strip()
    save(strip, f"{TEX}/block/node_core.png")
    lums = [luminance(strip[f * 16 : (f + 1) * 16]) for f in range(8)]
    # Flash test: frames at the fastest schedule (2 ticks) never alternate with a jump of 0.10 or more.
    assert max(abs(lums[i] - lums[(i + 1) % 8]) for i in range(8)) < 0.10, lums
    for f in range(4):
        save(glint(f), f"{TEX}/particle/node_glint_{f}.png")
    save(node_tap(), f"{TEX}/block/node_tap.png")
    save(node_tap_mouth(), f"{TEX}/block/node_tap_mouth.png")
    save(vis_relay_fork(), f"{TEX}/block/vis_relay_fork.png")
    save(vis_relay_stand(), f"{TEX}/block/vis_relay_stand.png")
    save(wand_cradle_base(), f"{TEX}/block/wand_cradle_base.png")
    save(wand_cradle_rest(), f"{TEX}/block/wand_cradle_rest.png")
    empty, full = bell_jar(False), bell_jar(True)
    assert (empty != full).any()
    save(empty, f"{TEX}/item/bell_jar.png")
    save(full, f"{TEX}/item/bell_jar_filled.png")
    print("nodes ok; core frame luminances", [round(float(l), 3) for l in lums])
