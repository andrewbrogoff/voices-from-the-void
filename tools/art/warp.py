"""The warp feature's assets (warp spec, "Production tickets", Warp assets).

Usage: python3 warp.py <assets_root> <warp_dir>

Writes, under <assets_root>/vftv/textures: item/stilling_draught.png (16 x 16, art style §5's
Stage 3 row: a stoppered phial, a felt-bound neck and a brass cap, the stilled draught hush grey
with the teal dimmed into one groove); entity/apparition.png (64 x 64: mauve cloth, an ink hollow
for the face, no teal); gui/sprites/ledger/offer_panel.png (130 x 64, the offer card's frame) and
gui/sprites/hud/voice_text.png (16 x 16, nine-slice border 4). Into <warp_dir>: apparition.bbmodel
(the parts, pivots, cuboids, UV origins and mirroring of ApparitionRenderer's layer) and
apparition_uv.png (each face's region labelled, at 8x). Deterministic: no randomness.
"""

import json
import os
import sys
import uuid

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lib import canvas, check, outline, put, rgb, save  # noqa: E402

ASSETS = sys.argv[1] if len(sys.argv) > 1 else "assets"
OUT = sys.argv[2] if len(sys.argv) > 2 else "warp"

# name: (pivot, cuboid from, size, uv origin, mirror) in y-up, feet-origin model space
PARTS = {
    "hood": ((0, 23, 0), (-3, 23, -3), (6, 7, 6), (0, 0), False),
    "robe": ((0, 23, 0), (-4, 0, -3), (8, 23, 6), (0, 16), False),
}
SHADE = {"top": 1, "right": 1, "front": 0, "left": -1, "back": -1, "bottom": -1}


def faces(u, v, w, h, d):
    """Box UV regions: name -> (x0, y0, x1, y1), exclusive ends."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def stilling_draught():
    """An upright phial: glass shoulders, a hush-grey stilled draught, a teal1 groove ring, a mauve
    felt wrap at the neck and a brass cap with one specular tick."""
    img = canvas()
    for y in range(7, 14):  # the body, rounded at the base
        for x in range(5, 11):
            if y == 13 and x in (5, 10):
                continue
            if y <= 8:
                put(img, x, y, "glass1" if x == 5 else "glass0")  # empty glass above the draught
            else:
                put(img, x, y, "hush3" if x == 5 else ("hush1" if x == 10 else "hush2"))
    for x in range(6, 10):
        put(img, x, 11, "teal1")  # the resonance, dimmed into a ground groove
    put(img, 6, 9, "glass1")  # a glint on the glass over the draught
    for y in range(4, 7):  # the neck, bound in felt
        for x in range(6, 10):
            put(img, x, y, "mauve2" if x == 6 else ("mauve0" if x == 9 else "mauve1"))
    put(img, 7, 5, "mauve2")
    for x in range(6, 10):  # the brass cap
        put(img, x, 2, "brass4" if x == 6 else "brass3")
        put(img, x, 3, "brass2" if x < 9 else "brass1")
    put(img, 6, 2, "brass5")
    outline(img)
    return img


def fill(img, region, ramp, base, face):
    x0, y0, x1, y1 = region
    steps = {"mauve": 3, "ink": 4}[ramp]
    step = max(0, min(steps - 1, base + SHADE[face]))
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(img, x, y, f"{ramp}{step}")


def apparition():
    """Mauve cloth over the whole figure, the hood's opening an ink hollow with no face, the robe's
    hem fraying darker; no teal, since the Voices' glimpse resonates with nothing."""
    img = np.zeros((64, 64, 4), np.uint8)
    for name, (_, _, (w, h, d), (u, v), _) in PARTS.items():
        for face, region in faces(u, v, w, h, d).items():
            fill(img, region, "mauve", 1, face)
    x0, y0, x1, y1 = faces(0, 0, 6, 7, 6)["front"]
    for y in range(y0 + 1, y1):  # the hollow where a face would be
        for x in range(x0 + 1, x1 - 1):
            put(img, x, y, "ink0")
    for x in range(x0 + 1, x1 - 1):
        put(img, x, y0 + 1, "ink1")  # the hood's inner lip
    for face in ("right", "front", "left", "back"):
        a, b, c, e = faces(0, 16, 8, 23, 6)[face]
        for x in range(a, c):
            put(img, x, e - 1, "ink2")  # the hem
            if (x + e) % 2:
                put(img, x, e - 2, "mauve0")
        for y in range(b + 2, e - 2, 3):  # long folds
            put(img, a + (c - a) // 2, y, "mauve0")
    check(img)
    return img


def offer_panel():
    """The offer card's 130 x 64 frame: a brass rule around paper, ink corners, a dim teal groove
    under the title line."""
    img = np.zeros((64, 130, 4), np.uint8)
    for y in range(64):
        for x in range(130):
            edge = min(x, y, 129 - x, 63 - y)
            if edge == 0:
                put(img, x, y, "ink1")
            elif edge == 1:
                put(img, x, y, "brass4" if x < 2 or y < 2 else "brass2")
            elif edge == 2:
                put(img, x, y, "brass1")
            else:
                put(img, x, y, "brass5")
    for x in range(8, 122):
        put(img, x, 13, "teal1")
    for cx, cy in ((3, 3), (126, 3), (3, 60), (126, 60)):
        put(img, cx, cy, "ink2")
    check(img)
    return img


def voice_text():
    """The Voice text backing's nine-slice: ink void inside a 1-px hush-lit rule, border 4."""
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            put(img, x, y, "ink2" if edge == 0 else "ink0", 204)
    check(img)
    return img


def save_any(img, path):
    check(img)
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    Image.fromarray(img, "RGBA").save(path, optimize=False)


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "vftv:apparition/" + name))


def bbmodel():
    elements, outliner = [], []
    for name, (pivot, frm, size, uv, mirror) in PARTS.items():
        to = [frm[i] + size[i] for i in range(3)]
        elements.append({
            "name": name, "box_uv": True, "rescale": False, "locked": False,
            "from": list(frm), "to": to, "autouv": 0, "color": 0, "origin": list(pivot),
            "uv_offset": list(uv), "mirror_uv": mirror, "uuid": uid(name + "/cube"),
        })
        outliner.append({"name": name, "origin": list(pivot), "uuid": uid(name), "children": [uid(name + "/cube")]})
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": "apparition", "model_identifier": "vftv:apparition",
        "resolution": {"width": 64, "height": 64},
        "elements": elements, "outliner": outliner, "textures": [], "animations": [],
    }


def uv_guide(path):
    tint = {"top": "brass4", "bottom": "brass1", "right": "teal2", "front": "copper3", "left": "teal1", "back": "amethyst2"}
    s = 8
    img = Image.new("RGB", (64 * s, 64 * s), rgb("ink0"))
    d = ImageDraw.Draw(img)
    for name, (_, _, (w, h, dd), (u, v), _) in PARTS.items():
        for face, (x0, y0, x1, y1) in faces(u, v, w, h, dd).items():
            d.rectangle([x0 * s, y0 * s, x1 * s - 1, y1 * s - 1], fill=rgb(tint[face]), outline=rgb("ink1"))
            if (x1 - x0) * s >= 24:
                d.text((x0 * s + 2, y0 * s + 2), face[0], fill=rgb("ink1"))
        d.text((u * s + 2, (v + dd) * s + 12), name, fill=rgb("ink0"))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    tex = os.path.join(ASSETS, "vftv/textures")
    save(stilling_draught(), os.path.join(tex, "item/stilling_draught.png"))
    save_any(apparition(), os.path.join(tex, "entity/apparition.png"))
    save_any(offer_panel(), os.path.join(tex, "gui/sprites/ledger/offer_panel.png"))
    save(voice_text(), os.path.join(tex, "gui/sprites/hud/voice_text.png"))
    with open(os.path.join(tex, "gui/sprites/hud/voice_text.png.mcmeta"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": 16, "height": 16, "border": 4}}}, f, indent=2)
        f.write("\n")
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "apparition.bbmodel"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(bbmodel(), f, indent=1, sort_keys=True)
        f.write("\n")
    uv_guide(os.path.join(OUT, "apparition_uv.png"))
    print("warp ok")
