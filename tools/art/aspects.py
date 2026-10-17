"""Aspect icons (aspects spec, "Icons"): one 16x16 glyph per built-in aspect plus the unheard icon.

Usage: python3 aspects.py <assets_root>
Writes <assets_root>/vftv/textures/gui/sprites/aspect/<id>.png and tools/art/aspect_identity.json,
the identity-colour pixels each glyph declares (the unit test checks them against the registry).

Each glyph is drawn from a letter grid, art inside 13x13 so the 1-px ink outline stays inside 15x15:
  B base: the aspect's registry colour (an aspect-ramp family's step 1, or the older ramp's chosen step)
  S shade: the family's step 0 (two steps only for aspect-ramp families, art style §2)
  H highlight / spec from an existing ramp      D detail from an existing ramp (stems, frames, handles)
  C cream (brass5)   K engraved ink1   V void ink0   T teal (Stage 4 only: Vacuos, Alienis)
  _ punched clean through after the outline     . empty
No glyph carries the teal tick: the aspect is the resonance.
"""

import json
import math
import os
import sys

from lib import canvas, outline, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
OUT = os.path.join(ROOT, "vftv/textures/gui/sprites/aspect")
IDENTITY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "aspect_identity.json")

# Family -> (base, shade, default highlight). Aspect-ramp families use their two steps; the older
# ramps pick theirs here (the registry colour is the base, BuiltinAspects mirrors it).
FAMILIES = {
    "gale": ("gale1", "gale0", "brass5"),
    "loam": ("loam1", "loam0", "brass4"),
    "ember": ("ember1", "ember0", "brass5"),
    "tide": ("tide1", "tide0", "glass1"),
    "lumen": ("lumen1", "lumen0", "brass5"),
    "blight": ("blight1", "blight0", "brass4"),
    "vital": ("vital1", "vital0", "brass5"),
    "verdure": ("verdure1", "verdure0", "brass4"),
    "amethyst": ("amethyst2", "amethyst1", "amethyst3"),
    "mauve": ("mauve2", "mauve1", "brass4"),
    "brass": ("brass3", "brass1", "brass5"),
    "ink": ("ink3", "ink2", "mauve2"),
    "hush": ("hush2", "hush1", "hush3"),
}

# id, family, detail colour, grid (rows of letters).
GLYPHS = [
    ("aer", "gale", "glass1", [
        "......BB.",
        ".....BBBH",
        "....BBBSH",
        "...BBBBSH",
        "...BBBSSH",
        "..BBBBS.H",
        "..BBBSS.H",
        ".BBBBS..H",
        ".BBSS...H",
        "..S.....H",
        ".......H.",
        "......H..",
    ]),
    ("terra", "loam", "brass1", [
        "....HBB....",
        "...HBBBBS..",
        "....SSSS...",
        "..HBBBBBBS.",
        ".HBBBBBBBBS",
        "..SSSSSSSS.",
        ".HBBBBBBBBS",
        "HBBBBBBBBBBS",
        "BBBBBBBBBBSS",
        ".SSSSSSSSSS.",
    ]),
    ("ignis", "ember", "brass2", [
        "...B...B..",
        "..BB..BB..",
        "..BSB.BSB.",
        "..BSB.BS..",
        "...BSBS...",
        "....BSB...",
        ".....K....",
        "...CCCCD..",
        "...CCCCD..",
        "...CCCCD..",
        "...CCCCD..",
        "..DDDDDDD.",
    ]),
    ("aqua", "tide", "brass1", [
        ".....BBBB....",
        "...BBBSSBB...",
        "..BBS...SBB..",
        "..BS..BB.BB..",
        "..BS.BSB.BBB.",
        "...BBSB..BBB.",
        "....SS..BBBBB",
        ".......BBBBSS",
        ".....BBBBBSSS",
        "..BBBBBBBSSSS",
        ".BBBBBBBSSSSS",
    ]),
    ("ordo", "lumen", "brass1", [
        "...D...",
        "...D...",
        "...D...",
        "...D...",
        "..HBB..",
        ".HBBBS.",
        ".BBBBS.",
        "HBBBBSS",
        "BBBBBSS",
        ".BBBSS.",
        "..BBS..",
        "...S...",
    ]),
    ("perditio", "blight", "brass1", [
        "..HBBBBB..",
        "...BBBS...",
        "..HBBBBS..",
        ".HBBKBBSS.",
        "HBBBBKBBSS",
        "BBBBKBBBSS",
        "BBBBBKBBSS",
        "BBBBKBBBSS",
        ".BBBBKBSS.",
        "..BBBKSS..",
        "...SSSS...",
    ]),
    ("vacuos", "ink", "mauve2", [
        "....HHHB....",
        "..HHBBBBBB..",
        ".HBBVVVVBBS.",
        ".HBVVVVVVBS.",
        "HBVVVVVVVVBS",
        "HBVVVVTVVVBS",
        "HBVVVVVVVVBS",
        ".BBVVVVVVBS.",
        ".BBBVVVVBSS.",
        "..SBBBBBBS..",
        "....SSSS....",
    ]),
    ("lux", "lumen", "brass2", [
        "....D....",
        "...D.D...",
        "...DDD...",
        "..DDDDD..",
        "..DBHBD..",
        "..DBHBD..",
        "..DBBBD..",
        "..DBBSD..",
        "..DSSSD..",
        "..DDDDD..",
        "...DDD...",
    ]),
    ("gelum", "tide", "brass2", [
        "DDDDDDDDDDD",
        "DDDDDDDDDDD",
        ".BBB.BBB.BB",
        ".BHS.BHS.BS",
        ".BS..BBS.S.",
        ".BS..BBS...",
        "..S..BS....",
        ".....BS....",
        ".....B.....",
        ".....S.....",
    ]),
    ("motus", "gale", "brass2", [
        ".......DD..",
        "......DD...",
        "....HBBBB..",
        "..HBBBBBBS.",
        ".HBBBBBBBSS",
        "..SSBBBBSS.",
        "....SSSSS..",
        ".....SS....",
        ".....D.....",
        "...........",
        ".SS........",
        "S..SS......",
        ".....SS....",
    ]),
    ("potentia", "ember", "brass1", [
        "..BBBBBBBBS",
        "..........S",
        ".SBBBBBBBB.",
        ".S.........",
        "..BBBBBBBBS",
        "..........S",
        ".SBBBBBBBB.",
        ".S.........",
        "..BBBBBBBBS",
    ]),
    ("victus", "vital", "brass1", [
        "...HBB...",
        "..HBBBB..",
        ".HBBBBBS.",
        ".BBBBBBS.",
        "HBBBCBBSS",
        "BBBBBCBSS",
        "BBBBCBBSS",
        "BBBBBCBSS",
        ".BBBBBSS.",
        "..SSSSS..",
    ]),
    ("vitreus", "lumen", "glass1", [
        ".....B.....",
        "....BBS....",
        "....HBS....",
        "...HBBSS...",
        "...BHBSS...",
        "..HBBBSSS..",
        "..BHBBSSS..",
        ".HBBBBSSSS.",
        ".BBBBBSSSS.",
        "HBBBBBSSSSS",
        "SSSSSSSSSSS",
    ]),
    ("venenum", "blight", "brass1", [
        "...BBBBB...",
        ".BBBCBBBBB.",
        "BBCCBBBCBBS",
        "BBBBBBCCBBS",
        "BBBBBBBBBSS",
        ".SSSSSSSSS.",
        "....CCC....",
        "....CCC....",
        "....CCD....",
        "...CCCDD...",
    ]),
    ("permutatio", "blight", "brass2", [
        "DDDDDDDDDDD",
        ".BBBBBBBBB.",
        ".SBBBBBBBS.",
        "..SBBBBBS..",
        "...SBBBS...",
        "....SBS....",
        ".....B.....",
        "....S.S....",
        "...S...S...",
        "..S..B..S..",
        ".S..BBB..S.",
        ".BBBBBBBBB.",
        "DDDDDDDDDDD",
    ]),
    ("sonus", "gale", "brass1", [
        "..HBBBB......",
        ".HBS..SB.....",
        "HBS....SB....",
        "BS......B...B",
        "BS.....BB..BB",
        ".BS...BBBBBBB",
        "..BBBBBBBBBBB",
        "....SSSSSSBBS",
        "...........SS",
    ]),
    ("metallum", "brass", "brass4", [
        ".HHHHHHHHHH.",
        "HBBDDDBBBBBB",
        "BK__BBBBBBBB",
        "BB__BBBBBBBS",
        "SSSSSSSSSSSS",
        ".SSSSSSSSSS.",
    ]),
    ("tenebrae", "ink", "mauve2", [
        "....HBBB...",
        "..HBBBS....",
        ".HBBS......",
        ".HBS.......",
        "HBBS.......",
        "HBS........",
        "HBS........",
        "HBBS.......",
        ".HBS.......",
        ".HBBS......",
        "..HBBBS....",
        "....HBBB...",
    ]),
    ("herba", "verdure", "brass1", [
        ".....HB.....",
        "....HBBS....",
        "....BBBS....",
        ".....BS.....",
        ".HB..S...HB.",
        "HBBS.S..HBBS",
        "BBBBSS.SBBBS",
        ".SBBSS.SBBS.",
        "...SSS.SS...",
        ".....S.S....",
        ".....SS.....",
        ".....S......",
    ]),
    ("bestia", "vital", "brass1", [
        "...HB...HB...",
        "..HBBS.HBBS..",
        "..BBBS.BBBS..",
        "...SS...SS...",
        "HB.........HB",
        "BBS.......BBS",
        ".S..HBBBB..S.",
        "...HBBBBBS...",
        "..HBBBBBBBS..",
        "..BBBBBBBBS..",
        "...BBBBBBS...",
        "....SSSS.....",
    ]),
    ("mortuus", "blight", "hush2", [
        "....DDDD.....",
        "...D....D....",
        "...D...HBBB..",
        "...D..HBBBBB.",
        "...D..BBBBSS.",
        "...D..SBBSS..",
        "...D...S.S...",
        "...D.........",
        "...D.........",
        "..DD.........",
        ".D.D.........",
        "...D.........",
    ]),
    ("spiritus", "amethyst", "brass1", [
        "....HB.....",
        "...B..B....",
        "...B...B...",
        "....BB.B...",
        ".......B...",
        "......B....",
        "....BB.....",
        "...B.......",
        "..HBBBBBB..",
        ".HBS....SB.",
        ".BS.....SB.",
        "..SSSSSSS..",
    ]),
    ("praecantatio", "amethyst", "brass1", [
        ".HHHHHHHHH..",
        "HBBBBBBBBBH.",
        "BSSSSSSSSSB.",
        ".CCCCCCCC...",
        ".CKKKKKCC...",
        ".CCCCCCCC...",
        ".CKKKKCCC...",
        ".CCCCCCHB...",
        ".CCCCCHBBS..",
        ".CCCCCBBBS..",
        ".SSSSSSSS...",
    ]),
    ("fames", "ink", "hush3", [
        ".........D..",
        "........D...",
        ".......D....",
        "......D.....",
        ".....HB.....",
        "HHBBBBBBBBBB",
        "BVVVVVVVVVVB",
        ".BVVVVVVVVB.",
        ".HBBBBBBBBS.",
        "..BBBBBBBS..",
        "...SSSSSS...",
    ]),
    ("instrumentum", "brass", "copper2", [
        ".HHHHHHHHH.",
        ".BBBBBBBBBS",
        ".BBBBBBBBBS",
        ".SSSSSSSSSS",
        ".....DS....",
        ".....DS....",
        ".....DS....",
        ".....DS....",
        ".....DS....",
        ".....DS....",
        ".....DS....",
    ]),
    ("silentium", "hush", "brass1", [
        ".....D.....",
        "....DDD....",
        ".....D.....",
        "...HBBBS...",
        "..HBBBBBS..",
        "..BBBBBBS..",
        ".HBBBBBBSS.",
        ".BBBBBBBSS.",
        "HBBBBBBBBSS",
        "SSSSSSSSSSS",
        "...K.K.K...",
        "....KKK....",
        ".....B.....",
    ]),
    ("arbor", "verdure", "brass1", [
        "...HBBBB...",
        ".HBBBBBBBB.",
        "HBBBBBBBBBS",
        "BBBBBBBBBBS",
        "BBBBBBBBBSS",
        ".BBBBBBBSS.",
        "..SSSSSSS..",
        ".....D.....",
        ".....DS....",
        ".....DS....",
        "....DDDS...",
    ]),
    ("sano", "vital", "brass2", [
        ".........DD",
        "........DD.",
        ".......DD..",
        "......DD...",
        "HBBBBBDBBBB",
        "BBBBBBBBBBS",
        ".BBBBBBBBS.",
        ".BBBBBBBBS.",
        "..BBBBBBS..",
        "...SSSSS...",
        "..DDDDDDD..",
    ]),
    ("corpus", "vital", "brass5", [
        "..........DD",
        ".........DD.",
        "........DD..",
        "..HBBBBDD...",
        ".HBBBBBBS...",
        "HBBBBBBBSS..",
        "BBBBBBBBSS..",
        "BBBBBBBSS...",
        ".BBBBBSS....",
        ".DDSSSS.....",
        "DD..........",
        "D...........",
    ]),
    ("exanimis", "blight", "hush1", [
        "...HBBBB...",
        ".HBBBBBBBB.",
        ".BBBBBBBBS.",
        ".BBBKBBBBS.",
        ".BBBBKBBBS.",
        ".BBBKKBBBS.",
        ".BBBBBKBBS.",
        ".BBBBKBBBS.",
        ".BBBBBBBBS.",
        ".BBBBBBBBS.",
        "DDDDDDDDDDD",
    ]),
    ("cognitio", "amethyst", "brass1", [
        ".HBBB...HBBB.",
        "HB...S.HB...S",
        "B....SDB....S",
        "B....S.B....S",
        "B....S.B....S",
        ".S..S...S..S.",
        "..SS.....SS..",
    ]),
    ("humanus", "vital", "brass1", [
        "...H.H.H...",
        "...B.B.B.H.",
        "H..B.B.B.B.",
        "BB.BBBBB.B.",
        ".BBBBBBBBB.",
        ".BBBBBBBBS.",
        "..BBBBBBBS.",
        "..BBBBBBS..",
        "...BBBBS...",
        "...SSSS....",
    ]),
    ("telum", "brass", "copper2", [
        ".....H.....",
        "....HBS....",
        "....BBS....",
        "...HBBSS...",
        "...BBBBS...",
        "..HBBBBSS..",
        "..BBBBBSS..",
        ".HBBBBBBSS.",
        ".BBBBBBBSS.",
        "BBSS.D.SBSS",
        "BS...D..SS.",
        ".....D.....",
        ".....D.....",
    ]),
    ("tutamen", "brass", "brass4", [
        "HHHHHHHHHHH",
        "HBBBBBBBBBS",
        "HBBBBDBBBBS",
        "HBBBBDBBBBS",
        ".BBDDDDDBS.",
        ".BBBBDBBBS.",
        "..BBBDBBS..",
        "..BBBBBBS..",
        "...BBBBS...",
        "....BBS....",
        ".....S.....",
    ]),
    ("pannus", "mauve", "brass2", [
        ".DDDDDDDD...",
        "..BBBBBB....",
        "..BSBSBS....",
        "..BBBBBB....",
        "..BSBSBS..H.",
        "..BBBBBB.H..",
        "..BSBSBSH...",
        "..BBBBBH....",
        ".DDDDDHDD...",
        ".....H......",
        "....H.......",
    ]),
    ("alienis", "ink", "mauve2", [
        "...HHHHH...",
        "..HBBBBBS..",
        ".HBVVVVVBS.",
        ".HBVVVVVBS.",
        ".HBVVVVVBS.",
        ".HBVVTVVBS.",
        ".HBVVVVVBS.",
        ".HBVVVVVBS.",
        ".HBVVVVVBS.",
        ".HBVVVVVBS.",
        "HHBBBBBBBSS",
    ]),
    ("unheard", "ink", "mauve2", [
        "....HH.HH....",
        "..HHB...BBB..",
        ".HBB.....BBS.",
        ".HB.......BS.",
        "HB.........BS",
        "HB.........BS",
        "HB.........BS",
        ".HB.......BS.",
        ".HBB.....BSS.",
        "..SBBBBBBSS..",
        "....SSSSS....",
    ]),
]


def machina():
    """Six teeth round a hub, procedurally: top, bottom and the four diagonals, a punched axle."""
    rows = []
    for y in range(13):
        row = ""
        for x in range(13):
            dx, dy = x - 6, y - 6
            r = math.hypot(dx, dy)
            a = (math.degrees(math.atan2(dy, dx)) + 90) % 60
            tooth = r <= 6.2 and (a <= 16 or a >= 44)
            if r <= 1.0:
                row += "_"
            elif r <= 3.7 or tooth:
                row += "S" if dx + dy > 2 else ("H" if dx + dy < -6 else "B")
            else:
                row += "."
        rows.append(row)
    return rows


GLYPHS.insert(35, ("machina", "brass", "brass1", machina()))

COLOURS = {"C": "brass5", "K": "ink1", "V": "ink0", "T": "teal2"}


def draw(family, detail, rows):
    base, shade, high = FAMILIES[family]
    letters = dict(COLOURS, B=base, S=shade, H=high, D=detail)
    h, w = len(rows), max(len(r) for r in rows)
    if h > 13 or w > 13:
        raise ValueError(f"glyph {w}x{h} exceeds 13x13")
    x0, y0 = 1 + (13 - w) // 2, 1 + (13 - h) // 2
    img, holes, identity = canvas(), [], []
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "_":
                holes.append((x0 + x, y0 + y))
            elif c != ".":
                put(img, x0 + x, y0 + y, letters[c])
                if c == "B":
                    identity.append([x0 + x, y0 + y])
    outline(img)
    for x, y in holes:
        img[y, x] = 0
    return img, base, identity


def contact_sheet(images, path):
    """The review sheet: each glyph at GUI scale 1 and 4 on the HUD card's ink1, in colour and
    greyscale (Rec. 709 luma), labelled; every pair must be told apart in the greyscale columns."""
    from PIL import Image, ImageDraw

    from lib import rgb

    cols, cell = 6, 4 * 16 + 4
    rows = (len(images) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (2 * cell + 20 + 18), rows * (cell + 14)), rgb("ink1"))
    draw_ = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(images):
        x, y = (i % cols) * (2 * cell + 38), (i // cols) * (cell + 14)
        tile = Image.fromarray(img, "RGBA")
        grey = tile.convert("LA").convert("RGBA")
        sheet.paste(tile, (x + 2, y + 2), tile)
        sheet.paste(tile.resize((64, 64), Image.NEAREST), (x + 20, y + 2), tile.resize((64, 64), Image.NEAREST))
        big = grey.resize((64, 64), Image.NEAREST)
        sheet.paste(big, (x + 20 + cell, y + 2), big)
        draw_.text((x + 2, y + cell + 1), name, fill=rgb("hush3"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)


if __name__ == "__main__":
    from lib import PAL

    declared = {}
    images = []
    for name, family, detail, rows in GLYPHS:
        img, base, identity = draw(family, detail, rows)
        images.append((name, img))
        save(img, os.path.join(OUT, f"{name}.png"))
        if name != "unheard":
            declared[f"vftv:{name}"] = {"colour": PAL[base], "pixels": identity}
    with open(IDENTITY, "w") as f:
        json.dump(declared, f, indent=1, sort_keys=True)
        f.write("\n")
    if len(sys.argv) > 2:
        contact_sheet(images, sys.argv[2])
    print("aspects ok,", len(GLYPHS), "icons")
