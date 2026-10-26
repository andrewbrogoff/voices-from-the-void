"""The Answerers' textures, editable model and UV guide (worldgen spec, "Answerer assets").

Usage: python3 answerers.py <assets_root> <answerers_dir>

Writes assets/vftv/textures/entity/answerer.png and answerer_cantor.png (64 x 64, box UVs in
vanilla's layout), and, into <answerers_dir>, answerer.bbmodel (the frozen parts, pivots, cuboids,
UV origins and mirroring of "Rendering and sound", with its four poses) and answerer_uv.png (each
face's region labelled, at 8x).

The look: living people with faces drawn in the ink outline, mauve cloth with the hood painted on
the head, a brass ear-trumpet worn backwards as the mouthpiece, and no teal (they don't resonate).
The Cantor wears an amethyst sash and carries the cracked bell. Light from the upper left: the
model's right side (the viewer's left) is lit. Deterministic: no randomness.
"""

import json
import os
import sys
import uuid

import numpy as np
from PIL import Image, ImageDraw

from lib import check, put, rgb

ASSETS = sys.argv[1] if len(sys.argv) > 1 else "assets"
OUT = sys.argv[2] if len(sys.argv) > 2 else "answerers"

# name: (pivot, cuboid from, size, uv origin, mirror, parent) in the spec's y-up feet-origin space
PARTS = {
    "head": ((0, 24, 0), (-4, 24, -4), (8, 8, 8), (0, 0), False, None),
    "mouthpiece": ((0, 24, 0), (-1, 26, -8), (2, 2, 4), (32, 0), False, "head"),
    "body": ((0, 24, 0), (-4, 12, -2), (8, 12, 4), (16, 16), False, None),
    "robe": ((0, 12, 0), (-5, 2, -3), (10, 10, 6), (0, 32), False, None),
    "right_arm": ((-5, 22, 0), (-8, 12, -2), (4, 12, 4), (40, 16), False, None),
    "left_arm": ((5, 22, 0), (4, 12, -2), (4, 12, 4), (40, 16), True, None),
    "right_leg": ((-2, 12, 0), (-4, 0, -2), (4, 12, 4), (0, 16), False, None),
    "left_leg": ((2, 12, 0), (0, 0, -2), (4, 12, 4), (0, 16), True, None),
    "bell": ((-6, 12, 0), (-8, 8, -2), (3, 3, 3), (44, 0), False, "right_arm"),
}
POSES = {  # name: {part: (x, y, z rotation in degrees)} keyframes at t=0 and the half period
    "idle": (2.0, {"right_arm": [(0, 0, 3), (3, 0, 0)], "left_arm": [(0, 0, -3), (-3, 0, 0)]}),
    "walk": (1.0, {"right_arm": [(-30, 0, 0), (30, 0, 0)], "left_arm": [(30, 0, 0), (-30, 0, 0)],
                   "right_leg": [(30, 0, 0), (-30, 0, 0)], "left_leg": [(-30, 0, 0), (30, 0, 0)]}),
    "attack": (0.3, {"right_arm": [(0, 0, 0), (-115, 0, 0)]}),
    "chant": (0.2, {"right_arm": [(-60, 0, -10), (-60, 0, 10)], "left_arm": [(-60, 0, 0), (-60, 0, 0)],
                    "head": [(-15, 0, 0), (-15, 0, 0)]}),
}


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


SHADE = {"top": 1, "right": 1, "front": 0, "left": -1, "back": -1, "bottom": -1}


def fill(img, region, ramp, base, face):
    x0, y0, x1, y1 = region
    steps = {"mauve": 3, "brass": 6, "copper": 5, "ink": 4, "amethyst": 4}[ramp]
    step = max(0, min(steps - 1, base + SHADE[face]))
    for y in range(y0, y1):
        for x in range(x0, x1):
            put(img, x, y, f"{ramp}{step}")


def paint(cantor):
    img = np.zeros((64, 64, 4), np.uint8)
    for name, (_, _, (w, h, d), (u, v), _, _) in PARTS.items():
        if name == "bell" and not cantor:
            continue
        for face, region in faces(u, v, w, h, d).items():
            if name == "head":
                fill(img, region, "mauve", 0 if cantor else 1, face)  # the hood
            elif name in ("mouthpiece", "bell"):
                fill(img, region, "brass", 3, face)
            elif name in ("body", "robe"):
                fill(img, region, "mauve", 1, face)
            elif name == "right_arm":
                fill(img, region, "mauve", 1, face)
            elif name == "right_leg":
                fill(img, region, "ink", 2, face)
    # the face, framed by the hood: skin, two eyes, a mouth, drawn in the ink outline
    x0, y0, x1, y1 = faces(0, 0, 8, 8, 8)["front"]
    for y in range(y0 + 2, y1 - 1):
        for x in range(x0 + 1, x1 - 1):
            put(img, x, y, "copper3")
    for x in range(x0 + 1, x1 - 1):
        put(img, x, y0 + 2, "copper4")  # the lit brow
        put(img, x, y0 + 1, "ink1")  # the hood's inner edge
    for y in range(y0 + 2, y1 - 1):
        put(img, x0, y, "ink1")
        put(img, x1 - 1, y, "ink1")
    put(img, x0 + 2, y0 + 4, "ink1")
    put(img, x0 + 5, y0 + 4, "ink1")
    put(img, x0 + 2, y0 + 3, "copper4")
    put(img, x0 + 5, y0 + 3, "copper4")
    for x in range(x0 + 3, x0 + 5):
        put(img, x, y0 + 6, "copper1")  # the mouth
    for x in range(x0, x1):
        put(img, x, y1 - 1, "ink1")  # the chin's outline
    # the hands at the sleeves' ends, and the shoes
    for face, (a, b, c, e) in faces(40, 16, 4, 12, 4).items():
        if face == "top":
            continue
        rows = range(b, e) if face == "bottom" else range(e - 3, e)
        for y in rows:
            for x in range(a, c):
                put(img, x, y, "copper3" if SHADE[face] >= 0 else "copper2")
    for face, (a, b, c, e) in faces(0, 16, 4, 12, 4).items():
        rows = range(b, e) if face == "bottom" else range(e - 2, e) if face != "top" else range(0)
        for y in rows:
            for x in range(a, c):
                put(img, x, y, "ink1")
    # the robe's brass hem and the torso's sash
    for face, (a, b, c, e) in faces(0, 32, 10, 10, 6).items():
        if face in ("top", "bottom"):
            continue
        for x in range(a, c):
            put(img, x, e - 1, "brass2")
            put(img, x, b, "mauve0")  # the belt's shadow
    sash = "amethyst1" if cantor else "brass2"
    for face, (a, b, c, e) in faces(16, 16, 8, 12, 4).items():
        if face in ("front", "back"):
            for i in range(c - a):
                y = b + 1 + i
                if y < e:
                    put(img, a + i, y, sash)
    # the mouthpiece's bell end: an ink hole facing the wearer (worn backwards)
    a, b, c, e = faces(32, 0, 2, 2, 4)["back"]
    put(img, a, b, "ink0")
    if cantor:  # the bell's crack, and its dark mouth
        a, b, c, e = faces(44, 0, 3, 3, 3)["front"]
        put(img, a + 1, b, "ink1")
        put(img, a + 1, b + 1, "ink1")
        put(img, a + 2, b + 2, "ink1")
        a, b, c, e = faces(44, 0, 3, 3, 3)["bottom"]
        for y in range(b, e):
            for x in range(a, c):
                put(img, x, y, "ink0")
    check(img)
    return img


def save64(img, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    Image.fromarray(img, "RGBA").save(path, optimize=False)


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "vftv:answerer/" + name))


def bbmodel():
    elements, groups = [], {}
    for name, (pivot, frm, size, uv, mirror, parent) in PARTS.items():
        to = [frm[i] + size[i] for i in range(3)]
        elements.append({
            "name": name, "box_uv": True, "rescale": False, "locked": False,
            "from": list(frm), "to": to, "autouv": 0, "color": 0, "origin": list(pivot),
            "uv_offset": list(uv), "mirror_uv": mirror, "uuid": uid(name + "/cube"),
        })
        groups[name] = {"name": name, "origin": list(pivot), "uuid": uid(name), "children": [uid(name + "/cube")]}
    for name, (_, _, _, _, _, parent) in PARTS.items():
        if parent:
            groups[parent]["children"].append(groups[name])
    outliner = [g for n, g in groups.items() if not PARTS[n][5]]
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
        "name": "answerer", "model_identifier": "vftv:answerer",
        "resolution": {"width": 64, "height": 64},
        "elements": elements, "outliner": outliner, "textures": [], "animations": animations,
    }


def uv_guide(path):
    """Every face region of every part, tinted per face and labelled, at 8x."""
    tint = {"top": "brass4", "bottom": "brass1", "right": "teal2", "front": "copper3", "left": "teal1", "back": "amethyst2"}
    s = 8
    img = Image.new("RGB", (64 * s, 64 * s), rgb("ink0"))
    d = ImageDraw.Draw(img)
    for name, (_, _, (w, h, dd), (u, v), _, _) in PARTS.items():
        for face, (x0, y0, x1, y1) in faces(u, v, w, h, dd).items():
            d.rectangle([x0 * s, y0 * s, x1 * s - 1, y1 * s - 1], fill=rgb(tint[face]), outline=rgb("ink1"))
            if (x1 - x0) * s >= 24:
                d.text((x0 * s + 2, y0 * s + 2), face[0], fill=rgb("ink1"))
        d.text((u * s + 2, (v + dd) * s + 12), name, fill=rgb("ink0"))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    tex = os.path.join(ASSETS, "vftv/textures/entity")
    save64(paint(False), os.path.join(tex, "answerer.png"))
    save64(paint(True), os.path.join(tex, "answerer_cantor.png"))
    with open(os.path.join(OUT, "answerer.bbmodel"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(bbmodel(), f, indent=1, sort_keys=True)
        f.write("\n")
    uv_guide(os.path.join(OUT, "answerer_uv.png"))
