"""Ink & Brass textures and models for the sample set. Usage: python3 textures.py <assets_root>"""

import json
import os
import sys

import numpy as np

from lib import canvas, outline, put, ramp, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")
SEED = 1307  # one seed per asset family; bump only to re-roll noise deliberately


def tuning_fork():
    """Upright U: the tool silhouette rule (the instrument's own shape, standing, read in one glance)."""
    img = canvas()
    cu = lambda i: f"copper{i}"
    for y in range(1, 9):  # tines: 2 px each, 4 px gap (ink, clear, clear, ink)
        put(img, 4, y, cu(3))
        put(img, 5, y, cu(2))
        put(img, 10, y, cu(3))
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
    # amethyst shard set in the crotch, pointing up between the tines
    for x, y, c in [(7, 7, "amethyst3"), (7, 8, "amethyst3"), (8, 8, "amethyst2"), (7, 9, "amethyst2"), (8, 9, "amethyst1")]:
        put(img, x, y, c)
    outline(img)
    for x, y, c in [(13, 0, "teal3"), (14, 1, "teal2"), (14, 2, "teal2")]:
        put(img, x, y, c)  # Stage 1: the resonance tick sits outside the sprite by a tine tip, in the light
    return img


def resonant_brass_ingot():
    """Chime bar: the alloy/ingot rule (flat bar, crisp bevels, a punched hanging hole, teal ring off the free end)."""
    img = canvas()
    rows = {5: "brass5", 6: "brass3", 7: "brass3", 8: "brass2", 9: "brass1", 10: "brass0"}  # bright top edge -> dark front
    for y, c in rows.items():
        x0, x1 = (2, 13) if y in (5, 10) else (1, 14)  # 1-px 45-degree bevel on all four corners
        for x in range(x0, x1 + 1):
            put(img, x, y, c)
        if y not in (5, 10):  # end faces: lit left, shadowed right
            put(img, 1, y, "brass4" if y < 9 else "brass2")
            put(img, 14, y, "brass2" if y < 9 else "brass0")
    for x in range(3, 9):
        put(img, x, 6, "brass4")  # sheen along the top face
    outline(img)
    for x in (3, 4):  # hanging hole: punched clean through (transparent), ink on its shadowed upper wall
        img[7, x] = 0
        put(img, x, 6, "ink1")
    put(img, 2, 7, "ink1")
    for x, y, c in [(15, 5, "teal3"), (15, 6, "teal2"), (15, 7, "teal2")]:
        put(img, x, y, c)  # it rings
    return img


def hollow_bell():
    img = canvas()
    hw = {3: 3, 4: 4, 5: 4, 6: 4, 7: 4, 8: 5, 9: 5, 10: 5, 11: 6, 12: 7, 13: 7}
    for y, w in hw.items():
        for x in range(8 - w, 8 + w):
            n = (x + 0.5 - 8) / w  # -1 .. 1 across the bell
            t = 1 - min(1, abs(n + 0.35) * 0.95)  # cylinder lit from the upper left
            if y in (5, 11):
                t -= 0.25  # cast bands
            drain = (n + 1) / 2 + (y - 8) / 12  # the Hush creeps in from the lower right
            hushed = drain > 0.68 or (drain > 0.5 and (x + y) % 2)  # Hush edge: the one place we dither
            put(img, x, y, ramp("hush" if hushed else "brass", t))
    for x in (7, 8):
        put(img, x, 1, "brass2")
    put(img, 6, 2, "brass3")
    put(img, 9, 2, "brass1")
    for x in range(3, 13):  # the mouth: void, not shadow
        put(img, x, 13, "ink0")
    for x, y in [(9, 7), (10, 7), (9, 8), (10, 8), (11, 8), (10, 9)]:  # the hollow, punched through
        img[y, x] = 0
    outline(img)
    for x, y in [(9, 7), (10, 7), (9, 8), (10, 9)]:
        put(img, x, y, "ink0")
    put(img, 10, 8, "teal2")  # Stage 4: teal lives inside the dark, haloed in ink
    return img


def jar():
    side = canvas()
    for y in range(7, 16):  # glass body
        for x in range(3, 13):
            if x in (3, 12) or y == 15:  # ink frame (leaded-glass read); rim stays light
                put(side, x, y, "ink1")
            elif y == 7:
                put(side, x, y, "glass1", 200)  # shoulder rim catches light
            elif x == 4 or (x == 5 and 8 < y < 12):
                put(side, x, y, "glass1", 150)  # highlight streak, left (lit) side
            elif x == 11:
                put(side, x, y, "glass0", 120)  # far wall thickness
            else:
                put(side, x, y, "glass0", 60)
    for x in range(4, 12):
        put(side, x, 6, "ink2")  # neck: a shadowed groove under the lid
    for x in range(3, 13):
        put(side, x, 4, "brass4")
        put(side, x, 5, "brass1")
    lid = canvas()
    for y in range(3, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 12)
            put(lid, x, y, "brass2" if edge else "brass3")
    for i in range(4, 11):
        put(lid, i, 4, "brass5")
        put(lid, 4, i, "brass4")
    for x, y, c in [(7, 7, "brass1"), (8, 7, "brass1"), (7, 8, "brass1"), (8, 8, "brass0")]:
        put(lid, x, y, c)  # knob socket
    put(lid, 7, 7, "teal2")
    glass_top = canvas()
    for y in range(3, 13):
        for x in range(3, 13):
            edge = x in (3, 12) or y in (3, 12)
            put(glass_top, x, y, "glass1" if edge else "glass0", 200 if edge else 60)
    ess = canvas()  # rows 0-7: surface (up face); row 8: meniscus; rows 9-12: body (side faces)
    rng = np.random.RandomState(SEED + 7)
    for y in range(13):
        for x in range(16):
            c = "teal2" if y < 8 else "teal3" if y == 8 else "teal2" if y < 10 else "teal1"
            if (y < 8 and rng.rand() < 0.08) or (y > 10 and rng.rand() < 0.2):
                c = "teal3" if y < 8 else "teal2"
            put(ess, x, y, c, 235)
    return {"essentia_jar_side": side, "essentia_jar_lid": lid, "essentia_jar_glass_top": glass_top, "essentia_jar_essentia": ess}


def jar_model():
    t = lambda n: f"vftv:block/{n}"
    sides = lambda tex, uv=None: {f: ({"texture": tex} | ({"uv": uv} if uv else {})) for f in ("north", "south", "east", "west")}
    return {
        "render_type": "minecraft:translucent",  # NeoForge model key; vanilla ignores it
        "textures": {"particle": t("essentia_jar_side"), "side": t("essentia_jar_side"), "lid": t("essentia_jar_lid"),
                     "glass": t("essentia_jar_glass_top"), "essentia": t("essentia_jar_essentia")},
        "elements": [
            {"from": [4, 1, 4], "to": [12, 6, 12], "faces": sides("#essentia", [0, 8, 8, 13]) | {"up": {"texture": "#essentia", "uv": [0, 0, 8, 8]}}},
            {"from": [3, 0, 3], "to": [13, 9, 13], "faces": sides("#side") | {"up": {"texture": "#glass"}, "down": {"texture": "#glass"}}},
            {"from": [4, 9, 4], "to": [12, 10, 12], "faces": sides("#side")},
            {"from": [3, 10, 3], "to": [13, 12, 13], "faces": sides("#side") | {"up": {"texture": "#lid"}, "down": {"texture": "#lid"}}},
        ],
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.85, 0.85, 0.85]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.4, 0.4, 0.4]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        },
    }


def write_json(rel, obj):
    p = os.path.join(ROOT, "vftv", rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    for name, fn in [("tuning_fork", tuning_fork), ("resonant_brass_ingot", resonant_brass_ingot), ("hollow_bell", hollow_bell)]:
        save(fn(), f"{TEX}/item/{name}.png")
        write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"vftv:item/{name}"}})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"vftv:item/{name}"}})
    for name, img in jar().items():
        save(img, f"{TEX}/block/{name}.png")
    write_json("models/block/essentia_jar.json", jar_model())
    write_json("blockstates/essentia_jar.json", {"variants": {"": {"model": "vftv:block/essentia_jar"}}})
    write_json("items/essentia_jar.json", {"model": {"type": "minecraft:model", "model": "vftv:block/essentia_jar"}})
    print("textures ok")
