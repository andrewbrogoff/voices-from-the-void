"""Contact sheet for a set of 16x16 (or larger) textures, so agents and humans judge them the same way.

Usage: python3 sheet.py <out.png> <texture.png>... [--render <block_render.png>]...
Each row: large nearest-neighbour view on a light and a dark ground, then the texture as it reads
in a vanilla-like inventory slot at GUI scale 2 and 3 (slot drawn by code, no Mojang assets), then
greyscale, deuteranopia, protanopia and tritanopia previews at GUI scale 2 (Machado 2009 matrices,
severity 1, applied in linear RGB) for the state-variant review (accessibility, "Colour and contrast").
An animated strip (taller than wide) also lists each frame's time (from its .mcmeta) and mean relative
luminance (Rec. 709 over linear sRGB, transparent pixels as black) for the flash review: no two frames
differing by >= 0.10 may alternate faster than once per 7 ticks.
"""

import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

PANEL, SLOT_BG, SLOT_DARK, SLOT_LIGHT = "#C6C6C6", "#8B8B8B", "#373737", "#FFFFFF"
INK = "#14141c"
font = ImageFont.load_default(14)

# Machado, Oliveira & Fernandes 2009, severity 1.0, on linear RGB.
CVD = {
    "deutan": [[0.367322, 0.860646, -0.227968], [0.280085, 0.672501, 0.047413], [-0.011820, 0.042940, 0.968881]],
    "protan": [[0.152286, 1.052583, -0.204868], [0.114503, 0.786281, 0.099216], [-0.003882, -0.048116, 1.051998]],
    "tritan": [[1.255528, -0.076749, -0.178779], [-0.078411, 0.930809, 0.147602], [0.004733, 0.691367, 0.303900]],
}
REC709 = np.array([0.2126, 0.7152, 0.0722])


def to_linear(c):
    c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def to_srgb(c):
    c = np.clip(c, 0, 1)
    return np.round(255 * np.where(c <= 0.0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - 0.055)).astype(np.uint8)


def simulate(img, kind):
    """The texture as a viewer with that colour vision sees it; alpha unchanged."""
    a = np.asarray(img.convert("RGBA")).copy()
    lin = to_linear(a[..., :3].astype(np.float64))
    if kind == "grey":
        out = np.repeat((lin @ REC709)[..., None], 3, axis=-1)
    else:
        out = lin @ np.array(CVD[kind]).T
    a[..., :3] = to_srgb(out)
    return Image.fromarray(a, "RGBA")


def frame_luminances(img):
    """Mean relative luminance of each square frame of a strip."""
    a = np.asarray(img.convert("RGBA")).astype(np.float64)
    w = a.shape[1]
    lum = (to_linear(a[..., :3]) @ REC709) * (a[..., 3] / 255.0)
    return [float(lum[i * w : (i + 1) * w].mean()) for i in range(a.shape[0] // w)]


def frame_times(path, frames):
    """Each frame's ticks from the strip's .mcmeta (vanilla's default frametime is 1)."""
    meta = path + ".mcmeta"
    anim = json.load(open(meta)).get("animation", {}) if os.path.exists(meta) else {}
    default = anim.get("frametime", 1)
    order = anim.get("frames", list(range(frames)))
    return [(f, default) if isinstance(f, int) else (f["index"], f.get("time", default)) for f in order]


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
        cells += [slot(simulate(img, kind), 2) for kind in ("grey", "deutan", "protan", "tritan")]
        name = path.rsplit("/", 1)[-1]
        if img.height > img.width:
            lum = frame_luminances(img)
            name += "  frames (index:ticks lum) " + " ".join(
                f"{i}:{t} {lum[i]:.3f}" for i, t in frame_times(path, len(lum)))
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
    width = max(max(sum(c.width + 8 for c in cells), int(font.getlength(name))) for name, cells in rows) + 16
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
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    sheet.save(out)
    print("wrote", out)


if __name__ == "__main__":
    main()
