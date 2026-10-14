"""Writes blockstate, block/item models and 26.2 item definitions. Usage: python3 models.py <assets_root>"""

import json
import os
import sys

ITEMS = ["tuning_fork", "resonant_brass_ingot", "hollow_bell"]


def box(frm, to, *groups):
    """groups: (texture, {face: [u1, v1, u2, v2]}) pairs."""
    return {"from": frm, "to": to,
            "faces": {f: {"uv": uv, "texture": tex} for tex, uvs in groups for f, uv in uvs.items()}}


def side(w, h, u=0, v=0):
    return {f: [u, v, u + w, v + h] for f in ("north", "south", "east", "west")}


def caps(w, h, u=0, v=0):
    return {"up": [u, v, u + w, v + h], "down": [u, v, u + w, v + h]}


# Essentia jar: 10x9x10 glass body, 6x1x6 glass neck, 8x2x8 brass lid, 4x1x4 knob (13 px tall). 4 elements.
JAR = {
    "parent": "minecraft:block/block",
    "ambientocclusion": False,
    "textures": {"particle": "vftv:block/essentia_jar_side", "side": "vftv:block/essentia_jar_side",
                 "top": "vftv:block/essentia_jar_top", "lid": "vftv:block/essentia_jar_lid"},
    "elements": [
        box([3, 0, 3], [13, 9, 13], ("#side", side(10, 9)), ("#top", caps(10, 10))),
        box([5, 9, 5], [11, 10, 11], ("#top", side(6, 1, 2, 0))),
        box([4, 10, 4], [12, 12, 12], ("#lid", {**side(8, 2, 0, 8), **caps(8, 8)})),
        box([6, 12, 6], [10, 13, 10], ("#lid", {**side(4, 1, 8, 4), "up": [8, 0, 12, 4]})),
    ],
}


def write(root, rel, obj):
    path = os.path.join(root, "vftv", rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def main(root):
    for i in ITEMS:
        write(root, f"models/item/{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"vftv:item/{i}"}})
        write(root, f"items/{i}.json", {"model": {"type": "minecraft:model", "model": f"vftv:item/{i}"}})
    write(root, "models/block/essentia_jar.json", JAR)
    write(root, "blockstates/essentia_jar.json", {"variants": {"": {"model": "vftv:block/essentia_jar"}}})
    write(root, "items/essentia_jar.json", {"model": {"type": "minecraft:model", "model": "vftv:block/essentia_jar"}})


if __name__ == "__main__":
    main(sys.argv[1])
