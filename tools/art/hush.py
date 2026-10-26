"""The Hush's items, bells, the Hollow and the reading frame (Hush spec, "Production tickets", "Hush
items, bells and Hollow assets").

Usage: python3 hush.py <assets_root> <hush_dir>

Items (16x16): the Clearing Bell is a Stage 2 upright brass handbell (art style §4: an instrument stands
upright) with the teal tick outside its top-right; Hush Ash is a pale heap in the hush ramp, no teal.
The Pealing Bell's block textures are Stage 3: brass with mauve creeping into the shadows and the teal
dimmed into a groove (teal1). The Hollow (64 x 32, box UVs in vanilla's layout) is ash and plaster in the
hush ramp with an ink0 chest hole on the body's front and sides, no eyes, no teal. The frame is the
reading's nine-slice (16x16, border 4; datagen writes its .mcmeta): a hush band, an ink groove and an
opaque ink1 interior (localisation, "Text contrast").

Into <hush_dir>: hollow.bbmodel, with exactly the parts, pivots, cuboids, UV origins and mirroring of
"Rendering and sound", and hollow_uv.png, each face region labelled at 8x. Deterministic.
"""

import json
import os
import sys
import uuid

import numpy as np
from PIL import Image, ImageDraw

from lib import canvas, check, outline, put, rgb, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
OUT = sys.argv[2] if len(sys.argv) > 2 else "hush"
TEX = os.path.join(ROOT, "vftv/textures")


def clearing_bell():
    img = canvas()
    for y in range(1, 4):  # the handle: a short brass grip and its knob
        put(img, 7, y, "brass3")
        put(img, 8, y, "brass1")
    put(img, 7, 1, "brass5")
    widths = {4: 2, 5: 3, 6: 3, 7: 4, 8: 4, 9: 4, 10: 5, 11: 5, 12: 6}  # half-widths, crown to lip
    for y, w in widths.items():
        for x in range(8 - w, 8 + w):
            step = 4 if x == 8 - w else 3 if x < 8 else 2 if x < 8 + w - 1 else 1
            put(img, x, y, f"brass{step}")
    for x in range(2, 14):  # the lip: a bright rim, a dark mouth beneath
        put(img, x, 12, "brass4" if x < 8 else "brass2")
    for x in range(5, 11):
        put(img, x, 13, "ink2")
    put(img, 7, 14, "copper2")  # the clapper hanging out of the mouth
    put(img, 8, 14, "copper1")
    put(img, 5, 6, "brass5")  # one narrow specular streak on the lit side
    put(img, 4, 8, "brass5")
    outline(img)
    put(img, 13, 3, "teal2")  # the Stage 2 tick, outside, top-right
    put(img, 14, 2, "teal2")
    put(img, 14, 4, "teal1")
    return img


def hush_ash():
    img = canvas()
    r = np.random.default_rng(1700)
    widths = {9: 2, 10: 4, 11: 5, 12: 6, 13: 7}
    for y, w in widths.items():
        for x in range(8 - w, 8 + w):
            put(img, x, y, "hush3" if y < 11 and x < 8 else "hush2")
    for _ in range(10):  # flecks of darker ash, in small clusters
        x, y = 8 + int(r.integers(-6, 6)), 11 + int(r.integers(0, 3))
        put(img, x, y, "hush1")
    for x in range(1, 15):
        if img[13, x, 3]:
            put(img, x, 13, "hush1")  # the heap's shadowed base
    outline(img)
    return img


def yoke():
    img = canvas()
    for y in range(16):
        for x in range(16):
            band = y % 8
            put(img, x, y, "brass4" if band == 0 else "brass2" if band < 6 else "mauve1")
    for x in range(16):
        put(img, x, 3, "teal1")  # the dimmed teal, in its groove
    for y in range(16):
        put(img, 0, y, "ink1")
        put(img, 15, y, "ink1")
    return img


def bell_face():
    img = canvas()
    for y in range(16):
        for x in range(16):
            step = 3 if x < 5 else 2 if x < 11 else 1
            put(img, x, y, f"brass{step}")
    for x in range(16):
        put(img, x, 0, "brass4")
        put(img, x, 14, "mauve0")  # mauve creeping into the shadow under the lip
        put(img, x, 15, "ink1")
        put(img, x, 9, "teal1")  # the groove round the waist
    put(img, 2, 3, "brass5")
    put(img, 2, 4, "brass5")
    return img


# Hollow parts in the spec's y-up feet-origin space: (pivot, cuboid from, size, uv, mirror)
PARTS = {
    "body": ((0, 7, 0), (-3, 7, -7), (6, 5, 14), (0, 0), False),
    "head": ((0, 10, -7), (-2, 9, -11), (4, 5, 4), (40, 0), False),
    "leg_front_left": ((2, 7, -5), (1, 0, -6), (2, 7, 2), (0, 19), False),
    "leg_front_right": ((-2, 7, -5), (-3, 0, -6), (2, 7, 2), (0, 19), True),
    "leg_back_left": ((2, 7, 5), (1, 0, 4), (2, 7, 2), (0, 19), False),
    "leg_back_right": ((-2, 7, 5), (-3, 0, 4), (2, 7, 2), (0, 19), True),
}
POSES = {  # walk: diagonal pairs swing ±25°; rise: drawn as a scale-in by the renderer
    "walk": (1.0, {"leg_front_left": [(25, 0, 0), (-25, 0, 0)], "leg_back_right": [(25, 0, 0), (-25, 0, 0)],
                   "leg_front_right": [(-25, 0, 0), (25, 0, 0)], "leg_back_left": [(-25, 0, 0), (25, 0, 0)]}),
    "idle": (2.0, {"head": [(10, 0, 0), (10, 0, 0)]}),
}


def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


SHADE = {"top": 3, "right": 2, "front": 1, "left": 1, "back": 0, "bottom": 0}


def hollow():
    img = np.zeros((32, 64, 4), np.uint8)
    for name, (_, _, (w, h, d), (u, v), _) in PARTS.items():
        for face, (x0, y0, x1, y1) in faces(u, v, w, h, d).items():
            step = SHADE[face] if name != "head" else min(3, SHADE[face] + 1)  # plaster head, a step paler
            for y in range(y0, y1):
                for x in range(x0, x1):
                    put(img, x, y, f"hush{step}")
            r = np.random.default_rng(1800 + x0 * 64 + y0)
            for _ in range((x1 - x0) * (y1 - y0) // 6):  # ash mottling: a step darker, in small clusters
                x, y = int(r.integers(x0, x1)), int(r.integers(y0, y1))
                for dx in range(2):
                    if x + dx < x1:
                        put(img, x + dx, y, f"hush{max(0, step - 1)}")
    # the chest hole: ink0 on the body's front and the front of both sides
    x0, y0, x1, y1 = faces(0, 0, 6, 5, 14)["front"]
    for y in range(y0 + 1, y1 - 1):
        for x in range(x0 + 1, x1 - 1):
            put(img, x, y, "ink0")
    for side in ("right", "left"):
        x0, y0, x1, y1 = faces(0, 0, 6, 5, 14)[side]
        near = range(x0, x0 + 3) if side == "left" else range(x1 - 3, x1)
        for y in range(y0 + 1, y1 - 1):
            for x in near:
                put(img, x, y, "ink0")
    check(img)
    return img


def save_raw(img, path):
    check(img)
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    Image.fromarray(img, "RGBA").save(path, optimize=False)


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "vftv:hollow/" + name))


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
    animations = []
    for pose, (length, parts) in POSES.items():
        animators = {}
        for part, (a, b) in parts.items():
            kf = []
            for t, rot in ((0.0, a), (length / 2, b), (length, a)):
                kf.append({"channel": "rotation", "data_points": [{"x": rot[0], "y": rot[1], "z": rot[2]}],
                           "uuid": uid(f"{pose}/{part}/{t}"), "time": t, "interpolation": "linear"})
            animators[uid(part)] = {"name": part, "type": "bone", "keyframes": kf}
        animations.append({"uuid": uid("anim/" + pose), "name": pose, "loop": "loop", "length": length,
                           "snapping": 20, "animators": animators})
    return {
        "meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
        "name": "hollow", "model_identifier": "vftv:hollow",
        "resolution": {"width": 64, "height": 32},
        "elements": elements, "outliner": outliner, "textures": [], "animations": animations,
    }


def uv_guide(path):
    tint = {"top": "brass4", "bottom": "brass1", "right": "teal2", "front": "copper3", "left": "teal1", "back": "amethyst2"}
    s = 8
    img = Image.new("RGB", (64 * s, 32 * s), rgb("ink0"))
    d = ImageDraw.Draw(img)
    for name, (_, _, (w, h, dd), (u, v), _) in PARTS.items():
        for face, (x0, y0, x1, y1) in faces(u, v, w, h, dd).items():
            d.rectangle([x0 * s, y0 * s, x1 * s - 1, y1 * s - 1], fill=rgb(tint[face]), outline=rgb("ink1"))
            if (x1 - x0) * s >= 24:
                d.text((x0 * s + 2, y0 * s + 2), face[0], fill=rgb("ink1"))
        d.text((u * s + 2, (v + dd) * s + 12), name, fill=rgb("ink0"))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    img.save(path)


def frame():
    img = canvas()
    for y in range(16):
        for x in range(16):
            edge = min(x, y, 15 - x, 15 - y)
            if edge == 0:
                put(img, x, y, "ink1")
            elif edge in (1, 2):
                lit = x + y < 15
                put(img, x, y, ("hush3" if edge == 1 else "hush2") if lit else ("hush1" if edge == 1 else "hush2"))
            else:
                put(img, x, y, "ink2" if edge == 3 else "ink1")  # groove, then opaque ink1 text region
    return img


if __name__ == "__main__":
    save(clearing_bell(), os.path.join(TEX, "item/clearing_bell.png"))
    save(hush_ash(), os.path.join(TEX, "item/hush_ash.png"))
    save(yoke(), os.path.join(TEX, "block/pealing_bell_yoke.png"))
    save(bell_face(), os.path.join(TEX, "block/pealing_bell_bell.png"))
    save(frame(), os.path.join(TEX, "gui/sprites/hud/hush_frame.png"))
    save_raw(hollow(), os.path.join(TEX, "entity/hollow.png"))
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "hollow.bbmodel"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(bbmodel(), f, indent=1, sort_keys=True)
        f.write("\n")
    uv_guide(os.path.join(OUT, "hollow_uv.png"))
