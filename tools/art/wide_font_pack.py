"""The wide-font test pack (accessibility, "Fixtures"): never shipped, loaded by the E2E harness.

Usage: python3 wide_font_pack.py [out.zip]   (default: build/fixtures/vftv-wide-font.zip)

Replaces minecraft:default with a bitmap provider whose glyphs for U+0021-U+007E and U+00A1-U+00FF
all advance 9 px (vanilla's digits advance 6), plus a space provider giving U+0020 and U+00A0 the
same 9 px. Each glyph is an 8x8 cell drawn from Pillow's built-in font so screenshots stay legible;
a near-invisible pixel (alpha 1) in the cell's last column makes vanilla measure every glyph 8 wide,
so its advance is 8 + 1 = 9. Byte-identical on re-run (fixed zip timestamps).
"""

import io
import json
import os
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

CELL = 8
PACK_FORMAT = 88  # Minecraft 26.2's resource pack version
CHARS = [c for c in range(0x21, 0x7F)] + [c for c in range(0xA1, 0x100)]
ROW = 16


def glyph_sheet():
    rows = (len(CHARS) + ROW - 1) // ROW
    sheet = np.zeros((rows * CELL, ROW * CELL, 4), np.uint8)
    font = ImageFont.load_default(8)
    for i, c in enumerate(CHARS):
        cell = Image.new("L", (CELL, CELL), 0)
        ImageDraw.Draw(cell).text((0, -1), chr(c), fill=255, font=font)
        ink = np.asarray(cell) >= 128
        y, x = divmod(i, ROW)
        block = sheet[y * CELL : (y + 1) * CELL, x * CELL : (x + 1) * CELL]
        block[ink] = (255, 255, 255, 255)
        if not block[:, CELL - 1, 3].any():
            block[CELL - 1, CELL - 1] = (255, 255, 255, 1)
    return sheet, rows


def font_json(rows):
    grid = []
    for r in range(rows):
        line = "".join(chr(c) for c in CHARS[r * ROW : (r + 1) * ROW])
        grid.append(line + "\u0000" * (ROW - len(line)))
    return {
        "providers": [
            {"type": "space", "advances": {" ": 9, " ": 9}},
            {"type": "bitmap", "file": "vftv_fixture:font/wide.png", "ascent": 7, "height": CELL, "chars": grid},
        ]
    }


def entry(z, name, data):
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    z.writestr(info, data)


def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "build", "fixtures", "vftv-wide-font.zip")
    sheet, rows = glyph_sheet()
    png = io.BytesIO()
    Image.fromarray(sheet, "RGBA").save(png, format="PNG", optimize=False)
    meta = {"pack": {"description": "vftv wide-font fixture (9-px glyphs); never shipped", "min_format": PACK_FORMAT, "max_format": PACK_FORMAT}}
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with zipfile.ZipFile(out, "w") as z:
        entry(z, "pack.mcmeta", json.dumps(meta, indent=2))
        entry(z, "assets/minecraft/font/default.json", json.dumps(font_json(rows), indent=2, ensure_ascii=False))
        entry(z, "assets/vftv_fixture/textures/font/wide.png", png.getvalue())
    print("wrote", out)


if __name__ == "__main__":
    main()
