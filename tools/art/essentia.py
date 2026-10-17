"""Essentia assets (essentia spec, "Production tickets"): the jar, label, tube, valve and alembic textures.
Usage: python3 essentia.py <assets_root>

Stage 2 (art style §5): brass dominant, cooler (ink) shadows, the teal pip inlaid on the jar lid only.
Block textures are painted in block space, because the models take default UVs from position:
  jar     the prototype's Essentia Jar side as a front elevation (lid rows 4-5, neck row 6, body rows
          7-15) with the §6 glass recipe; the lid top carries the inlaid teal pip.
  tube    every tube part is 6 px across (5..10). A face of an arm samples rows 5..10 along its length
          on its sides and columns 5..10 on its top, so the pipe pattern is painted in both directions:
          a lit-edge to ink-edge profile across, copper flanges at the block boundary, and a copper
          collar in the centre square (what the core and the arm ends sample). The cap closes that
          centre with a bossed end plate.
  band    the renderer stretches the whole texture over an 8 px face, so 2 texels = 1 px: the ring is
          a 2-texel paper frame round a transparent 12x12 window (6 px of 8, the arm's width).
  valve   the pipe pattern with a brass housing on the body square (4..11); the wheel is a top-down
          handwheel, open = upright "+" spokes and a teal hub, closed = "x" spokes and a void hub.
  alembic tile-safe (its datagen geometry is free): leaded glass on an 8-px came lattice, so any
          position-derived crop shows leading, and ringed brass sheet.
Item sprites (label, label_written) follow §3: outlined once in ink1, no partial alpha.
Deterministic: no randomness.
"""

import os
import sys

from lib import canvas, outline, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")


def fill(img, x0, y0, x1, y1, name, a=255):
    """Inclusive rectangle."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(img, x, y, name, a)


# ---- jar ----

def jar_glass():
    img = canvas()
    for y in range(7, 16):  # glass body, §6: ink frame on the verticals and base, light shoulder rim
        for x in range(3, 13):
            if x in (3, 12) or y == 15:
                put(img, x, y, "ink1")
            elif y == 7:
                put(img, x, y, "glass1", 200)
            elif x == 4 or (x == 5 and 8 < y < 12):
                put(img, x, y, "glass1", 150)  # highlight streak on the lit side
            elif x == 11:
                put(img, x, y, "glass0", 120)  # far wall
            else:
                put(img, x, y, "glass0", 60)
    for x in range(4, 12):
        put(img, x, 6, "ink2")  # neck: a shadowed groove under the lid
    for x in range(3, 13):  # lid band: lit top row, dark front row, specular tick on the left
        put(img, x, 4, "brass4" if x > 4 else "brass5")
        put(img, x, 5, "brass1" if x < 12 else "brass0")
    return img


def jar_lid():
    img = canvas()
    for y in range(3, 13):
        for x in range(3, 13):
            put(img, x, y, "brass2" if x in (3, 12) or y in (3, 12) else "brass3")
    for i in range(4, 11):
        put(img, i, 4, "brass5")
        put(img, 4, i, "brass4")
    for x, y, c in [(7, 7, "brass1"), (8, 7, "brass1"), (7, 8, "brass1"), (8, 8, "brass0")]:
        put(img, x, y, c)  # knob socket
    put(img, 7, 7, "teal2")  # Stage 2: the teal pip inlaid in the socket
    return img


def jar_liquid():
    img = canvas()
    for y in range(16):
        for x in range(16):
            lit = (x == 4 and y >= 4) or (y == 4 and 4 <= x <= 11)  # the lit edges of the sampled faces
            put(img, x, y, "glass1" if lit else "hush3", 235)  # tinted at runtime
    return img


# ---- paper ----

def paper(img, x0, y0, x1, y1, fold=True):
    """A paper slip: ink rim, brass4 margin, brass5 face, optional dog-ear top right."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            edge = min(x - x0, y - y0, x1 - x, y1 - y)
            put(img, x, y, "ink1" if edge == 0 else "brass4" if edge == 1 else "brass5")
    if fold:
        for x, y, c in [(x1 - 2, y0, "ink1"), (x1 - 1, y0, None), (x1, y0, None), (x1, y0 + 1, None),
                        (x1 - 1, y0 + 1, "ink1"), (x1, y0 + 2, "ink1"), (x1 - 2, y0 + 1, "brass3"),
                        (x1 - 2, y0 + 2, "brass3"), (x1 - 1, y0 + 2, "brass3")]:
            if c:
                put(img, x, y, c)
            else:
                img[y, x] = 0
    return img


def essentia_label():
    return paper(canvas(), 0, 0, 15, 15)


def tube_band():
    return paper(canvas(), 0, 0, 15, 15, fold=False)


def tube_band_ring():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                put(img, x, y, "ink1")
            elif edge == 1:
                put(img, x, y, "brass5" if x + y < 15 else "brass4")  # lit upper-left half
    return img  # 2..13 stays clear: the arm passes through


def label_item(written=False):
    img = canvas()
    # a paper tag, 13x9, with a punched string hole at the left; outlined below
    for y in range(4, 13):
        for x in range(2, 15):
            if (x == 2 and y in (4, 12)) or (x == 3 and y in (4, 12)) or (x == 2 and y in (5, 11)):
                continue  # clipped corners on the tied end
            put(img, x, y, "brass5" if y < 6 or x < 4 else "brass4" if y < 11 else "brass3")
    put(img, 14, 4, "brass4")
    put(img, 14, 12, "brass2")
    for y in range(5, 12):
        put(img, 14, y, "brass3")  # the free edge in shadow
    for x in range(4, 14):
        put(img, x, 12, "brass3")
    img[8, 4] = 0  # the punched hole, which outline() inks
    outline(img)
    for x, y in [(1, 7), (0, 6)]:  # the string end
        put(img, x, y, "copper2")
    if written:  # an ink glyph mark on the face: a short stroke and a hooked line
        for x, y in [(7, 6), (8, 6), (9, 6), (10, 6), (11, 6), (7, 8), (8, 8), (9, 8), (10, 9), (11, 10),
                     (7, 10), (8, 10)]:
            put(img, x, y, "ink1")
    return img


# ---- tube ----

ACROSS = {5: "brass4", 6: "brass3", 7: "brass3", 8: "brass2", 9: "brass1", 10: "ink2"}  # lit -> shadow


def pipe():
    img = canvas()
    for y in range(16):
        for x in range(16):
            inx, iny = 5 <= x <= 10, 5 <= y <= 10
            if inx and iny:  # the copper collar every arm meets at the core
                edge = min(x - 5, y - 5, 10 - x, 10 - y)
                c = "ink1" if edge == 0 else ("copper3" if x + y < 15 else "copper1") if edge == 1 else "copper2"
            elif inx or iny:
                across, along = (y, x) if iny else (x, y)
                if along in (0, 15):
                    c = {5: "copper4", 10: "ink1"}.get(across, "copper2")  # flange at the block boundary
                elif along in (1, 14):
                    c = "ink1"  # its groove
                else:
                    c = ACROSS[across]
            else:
                continue  # never sampled by a tube model
            put(img, x, y, c)
    return img


def tube_core():
    img = pipe()
    for x, y in [(7, 7), (8, 8)]:
        put(img, x, y, "copper4" if x == 7 else "copper0")  # a rivet in the closed collar
    return img


def tube_cap():
    img = pipe()  # the centre becomes the closed end plate, bossed
    for y in range(5, 11):
        for x in range(5, 11):
            edge = min(x - 5, y - 5, 10 - x, 10 - y)
            put(img, x, y, "ink1" if edge == 0 else ("brass4" if x + y < 15 else "brass1") if edge == 1 else "brass2")
    for x, y, c in [(7, 7, "brass5"), (8, 7, "brass3"), (7, 8, "brass3"), (8, 8, "brass0")]:
        put(img, x, y, c)
    return img


# ---- valve ----

def tube_valve():
    img = pipe()
    for y in range(4, 12):
        for x in range(4, 12):
            edge = min(x - 4, y - 4, 11 - x, 11 - y)
            c = "ink1" if edge == 0 else ("brass4" if x + y < 15 else "brass1") if edge == 1 else "brass3"
            put(img, x, y, c)
    for x, y in [(6, 6), (9, 6), (6, 9), (9, 9)]:
        put(img, x, y, "brass5" if (x, y) == (6, 6) else "brass0")  # housing bolts
    return img


def valve_wheel(opened):
    img = canvas()
    for y in range(16):
        for x in range(16):
            d2 = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if 4.5 ** 2 <= d2 <= 6.5 ** 2:
                put(img, x, y, "copper3" if x + y < 15 else "copper1")  # the rim, lit upper left
    for i in range(2, 14):  # spokes: upright "+" when open, diagonal "x" when closed
        for x, y in ([(i, 7), (i, 8), (7, i), (8, i)] if opened else [(i, i), (i + 1, i), (i, 15 - i), (i + 1, 15 - i)]):
            if img[y, x, 3] == 0 and (x - 7.5) ** 2 + (y - 7.5) ** 2 < 4.5 ** 2:  # inside the rim
                put(img, x, y, "copper2" if x + y < 15 else "copper1")
    fill(img, 6, 6, 9, 9, "brass3")
    put(img, 6, 6, "brass5")
    hub = "teal2" if opened else "ink0"  # open: resonance flows; closed: a void hub
    for x, y in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        put(img, x, y, hub)
    return img  # no outline: the gaps between spokes stay clear (cutout)


# ---- alembic ----

def alembic_glass():
    img = canvas()
    for y in range(16):
        for x in range(16):
            if x % 8 == 7 or y % 8 == 7:
                put(img, x, y, "ink1")  # the cames
            elif y % 8 == 0:
                put(img, x, y, "glass1", 200)  # each pane's light rim
            elif x % 8 == 0 or (x % 8 == 1 and 1 < y % 8 < 5):
                put(img, x, y, "glass1", 150)  # its lit-side streak
            elif x % 8 == 6:
                put(img, x, y, "glass0", 120)  # its far wall
            else:
                put(img, x, y, "glass0", 60)
    return img


def alembic_brass():
    img = canvas()
    for y in range(16):
        for x in range(16):
            r = y % 4
            c = "brass4" if r == 0 else "brass1" if r == 3 else "brass3" if x % 8 < 4 else "brass2"
            if r in (1, 2) and x % 8 == 1:
                c = "brass5"  # a narrow specular streak per panel
            put(img, x, y, c)
    return img


if __name__ == "__main__":
    for name, img in [
        ("essentia_jar_glass", jar_glass()), ("essentia_jar_lid", jar_lid()), ("essentia_jar_liquid", jar_liquid()),
        ("essentia_label", essentia_label()), ("essentia_tube_arm", pipe()), ("essentia_tube_core", tube_core()),
        ("essentia_tube_cap", tube_cap()), ("essentia_tube_band", tube_band()),
        ("essentia_tube_band_ring", tube_band_ring()), ("tube_valve", tube_valve()),
        ("tube_valve_wheel_open", valve_wheel(True)), ("tube_valve_wheel_closed", valve_wheel(False)),
        ("alembic_glass", alembic_glass()), ("alembic_brass", alembic_brass()),
    ]:
        save(img, f"{TEX}/block/{name}.png")
    save(label_item(), f"{TEX}/item/label.png")
    save(label_item(written=True), f"{TEX}/item/label_written.png")
    print("essentia ok")
