"""Contact sheet for a set of 16x16 (or larger) textures, so agents and humans judge them the same way.

Usage: python3 sheet.py <out.png> <texture.png>... [--render <block_render.png>]...
Each row: large nearest-neighbour view on a light and a dark ground, then the texture as it reads
in a vanilla-like inventory slot at GUI scale 2 and 3 (slot drawn by code, no Mojang assets).
"""

import sys

from PIL import Image, ImageDraw, ImageFont

PANEL, SLOT_BG, SLOT_DARK, SLOT_LIGHT = "#C6C6C6", "#8B8B8B", "#373737", "#FFFFFF"
INK = "#14141c"
font = ImageFont.load_default(14)


def nearest(img, px):
    return img.resize((px, px * img.height // img.width), Image.NEAREST)


def slot(img, scale):
    s = 18 * scale
    tile = Image.new("RGBA", (s + 8 * scale, s + 8 * scale), PANEL)
    d = ImageDraw.Draw(tile)
    o = 4 * scale
    d.rectangle([o, o, o + s - 1, o + s - 1], fill=SLOT_BG)
    d.rectangle([o, o, o + s - 1, o + scale - 1], fill=SLOT_DARK)
    d.rectangle([o, o, o + scale - 1, o + s - 1], fill=SLOT_DARK)
    d.rectangle([o, o + s - scale, o + s - 1, o + s - 1], fill=SLOT_LIGHT)
    d.rectangle([o + s - scale, o, o + s - 1, o + s - 1], fill=SLOT_LIGHT)
    frame = img.crop((0, 0, img.width, img.width))  # first frame of animated strips
    icon = nearest(frame, 16 * scale)
    tile.alpha_composite(icon, (o + scale, o + scale))
    return tile


def main():
    argv = sys.argv[1:]
    out, renders, textures = argv[0], [], []
    it = iter(argv[1:])
    for a in it:
        (renders.append(next(it)) if a == "--render" else textures.append(a))
    rows = []
    for path in textures:
        img = Image.open(path).convert("RGBA")
        big = nearest(img.crop((0, 0, img.width, img.width)), 192)
        cells = []
        for ground in ("#EDEDED", INK):
            c = Image.new("RGBA", (208, 208), ground)
            c.alpha_composite(big, (8, 8))
            cells.append(c)
        cells += [slot(img, 2), slot(img, 3)]
        name = path.rsplit("/", 1)[-1]
        rows.append((name, cells))
    for path in renders:
        r = Image.open(path).convert("RGBA")
        r = r.resize((416, 416 * r.height // r.width), Image.LANCZOS)
        cells = []
        for ground in ("#EDEDED", INK):
            c = Image.new("RGBA", (r.width + 16, r.height + 16), ground)
            c.alpha_composite(r, (8, 8))
            cells.append(c)
        rows.append((path.rsplit("/", 1)[-1], cells))
    width = max(sum(c.width + 8 for c in cells) for _, cells in rows) + 16
    height = sum(max(c.height for c in cells) + 32 for _, cells in rows) + 16
    sheet = Image.new("RGBA", (width, height), "#2b2b33")
    d = ImageDraw.Draw(sheet)
    y = 8
    for name, cells in rows:
        d.text((8, y), name, fill="#e8e0c8", font=font)
        y += 22
        x = 8
        for c in cells:
            sheet.alpha_composite(c, (x, y))
            x += c.width + 8
        y += max(c.height for c in cells) + 10
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
