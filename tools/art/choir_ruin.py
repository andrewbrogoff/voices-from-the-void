"""The Choir ruin's eight text templates (worldgen spec, "Production tickets", "Choir ruin templates").

Usage: python3 choir_ruin.py <content_root> [<preview_dir>]

Writes content/worldgen/templates/<name>.json in vftv:template/1 and, given a preview directory, an
isometric review sheet per template with its standing cells (teal) and the cells reachable on foot
from its connectors (brass) marked. Seeded (one random.Random per template, seeded by its name's
CRC-32) and byte-identical on re-run.

The look (spec, "Templates"): Stage 3 masonry only. Tuff, tuff bricks, chiseled and polished tuff,
calcite, deepslate tiles (cracked and cobbled for the broken courses), waxed cut copper for brass,
vanilla bells, a chest per wing at most, and the only vftv: blocks, the Reliquary and the node.

Coordinates: x runs east, y up, z south; layer y, row z, column x. y 0 is the foundation and y 1 the
floor layer, where the connectors sit in the outer walls' doorways. Every room is cleared with
explicit air up to its wall tops, so the footing and path rules hold whatever the terrain was.
"""

import json
import os
import sys
import zlib
from random import Random

PALETTE = {
    "#": "minecraft:tuff_bricks",
    "t": "minecraft:tuff",
    "c": "minecraft:chiseled_tuff",
    "p": "minecraft:polished_tuff",
    "k": "minecraft:calcite",
    "d": "minecraft:deepslate_tiles",
    "x": "minecraft:cracked_deepslate_tiles",
    "b": "minecraft:cobbled_deepslate",
    "w": "minecraft:waxed_cut_copper",
    "o": "minecraft:waxed_oxidized_cut_copper",
    ".": "minecraft:air",
    "^": "minecraft:jigsaw[orientation=north_up]",
    ">": "minecraft:jigsaw[orientation=east_up]",
    "v": "minecraft:jigsaw[orientation=south_up]",
    "<": "minecraft:jigsaw[orientation=west_up]",
    "R": "vftv:reliquary[facing=north]",
    "O": "vftv:node",
    "B": "minecraft:bell[attachment=ceiling,facing=north,powered=false]",
    "F": "minecraft:bell[attachment=floor,facing=east,powered=false]",
    "C": "minecraft:chest[facing=north,type=single,waterlogged=false]",
}

CONNECTORS = {
    "wing_socket": ("vftv:choir_ruin/wing_socket", "vftv:choir_ruin/wing_plug", "vftv:choir_ruin/wings"),
    "wing_plug": ("vftv:choir_ruin/wing_plug", "minecraft:empty", "minecraft:empty"),
    "end_socket": ("vftv:choir_ruin/end_socket", "vftv:choir_ruin/end_plug", "vftv:choir_ruin/ends"),
    "end_plug": ("vftv:choir_ruin/end_plug", "minecraft:empty", "minecraft:empty"),
}
JIGSAW_CHAR = {"north": "^", "east": ">", "south": "v", "west": "<"}
WALL = "#ttcd"  # the wall courses' mix, before the weathering processor varies it further


class Template:
    def __init__(self, name, sx, sy, sz):
        self.name, self.sx, self.sy, self.sz = name, sx, sy, sz
        self.cells = [[[" "] * sx for _ in range(sz)] for _ in range(sy)]
        self.block_entities = {}
        self.entities = []
        self.rng = Random(zlib.crc32(name.encode()))

    def set(self, x, y, z, ch):
        if 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz:
            self.cells[y][z][x] = ch

    def get(self, x, y, z):
        return self.cells[y][z][x]

    def box(self, x0, y0, z0, x1, y1, z1, ch):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    self.set(x, y, z, ch)

    def mix(self, chars):
        return chars[self.rng.randrange(len(chars))]

    def wall(self, x, z, low, high, chars=WALL):
        """One wall column from y 1 to a broken top between low and high."""
        top = self.rng.randint(low, high)
        for y in range(1, top + 1):
            self.set(x, y, z, self.mix(chars))
        return top

    def foundation(self, chars="dd#"):
        for z in range(self.sz):
            for x in range(self.sx):
                self.set(x, 0, z, self.mix(chars))

    def connector(self, x, z, facing, kind):
        name, target, pool = CONNECTORS[kind]
        self.set(x, 1, z, JIGSAW_CHAR[facing])
        self.set(x, 2, z, ".")  # a doorway two blocks high
        self.set(x, 3, z, self.mix("ck"))  # its lintel
        self.block_entities[f"{x},1,{z}"] = {
            "jigsaw": {"name": name, "target": target, "pool": pool, "final_state": "minecraft:air", "joint": "aligned"}
        }

    def entity(self, x, y, z, kind):
        self.entities.append({"pos": [x, y, z], "type": kind})

    def json(self):
        used = sorted({ch for layer in self.cells for row in layer for ch in row if ch != " "})
        out = {
            "schema": "vftv:template/1",
            "size": [self.sx, self.sy, self.sz],
            "palette": {ch: PALETTE[ch] for ch in used},
            "layers": [["".join(row) for row in layer] for layer in self.cells],
            "block_entities": dict(sorted(self.block_entities.items())),
            "entities": self.entities,
        }
        return out


def write(t, root):
    """One file, a layer per line group and a row per line, so a diff reads like a floor plan."""
    j = t.json()
    lines = ['{"schema": "vftv:template/1",', ' "size": %s,' % json.dumps(j["size"]), ' "palette": {']
    pal = list(j["palette"].items())
    for i, (k, v) in enumerate(pal):
        lines.append("  %s: %s%s" % (json.dumps(k), json.dumps(v), "," if i < len(pal) - 1 else ""))
    lines.append(" },")
    lines.append(' "layers": [')
    for li, layer in enumerate(j["layers"]):
        lines.append("  [")
        for ri, row in enumerate(layer):
            lines.append("   %s%s" % (json.dumps(row), "," if ri < len(layer) - 1 else ""))
        lines.append("  ]%s" % ("," if li < len(j["layers"]) - 1 else ""))
    lines.append(" ],")
    be = list(j["block_entities"].items())
    lines.append(' "block_entities": {')
    for i, (k, v) in enumerate(be):
        lines.append("  %s: %s%s" % (json.dumps(k), json.dumps(v), "," if i < len(be) - 1 else ""))
    lines.append(" },")
    lines.append(' "entities": [')
    for i, e in enumerate(j["entities"]):
        lines.append("  %s%s" % (json.dumps(e), "," if i < len(j["entities"]) - 1 else ""))
    lines.append(" ]}")
    path = os.path.join(root, "worldgen", "templates", t.name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def room(t, height_low, height_high, clear):
    """Foundation, broken perimeter walls, and the interior cleared with air up to `clear`."""
    t.foundation()
    for y in range(1, clear + 1):
        for z in range(1, t.sz - 1):
            for x in range(1, t.sx - 1):
                t.set(x, y, z, ".")
    for x in range(t.sx):
        for z in (0, t.sz - 1):
            t.wall(x, z, height_low, height_high)
    for z in range(1, t.sz - 1):
        for x in (0, t.sx - 1):
            t.wall(x, z, height_low, height_high)


def chancel(name, tower_corner):
    """23 x 26 x 23: the hall, its four doorways, the Reliquary, and a broken bell tower whose top
    rises 23 blocks above the floor with the node in its open belfry."""
    t = Template(name, 23, 26, 23)
    room(t, 3, 6, 6)
    for x, z, f in ((11, 0, "north"), (22, 11, "east"), (11, 22, "south"), (0, 11, "west")):
        t.connector(x, z, f, "wing_socket")
    # calcite piers down the nave, brass-capped
    for z in (5, 9, 13, 17):
        for x in (6, 16):
            for y in range(1, 5):
                t.set(x, y, z, "k")
            t.set(x, 5, z, "w")
    # the dais and the Reliquary, facing the north door
    t.box(9, 0, 15, 13, 0, 17, "c")
    t.set(11, 1, 16, "R")
    t.block_entities["11,1,16"] = {"reliquary": {"site": "vftv:choir_ruin"}}
    # the bell tower: a hollow 5 x 5 shaft in one corner, open belfry, broken crown
    cx, cz = (2, 2) if tower_corner == "nw" else (16, 2)
    for y in range(1, 24):
        for dz in range(5):
            for dx in range(5):
                edge = dx in (0, 4) or dz in (0, 4)
                x, z = cx + dx, cz + dz
                if edge:
                    corner = dx in (0, 4) and dz in (0, 4)
                    belfry = 17 <= y <= 20 and not corner
                    t.set(x, y, z, "." if belfry else ("d" if corner else t.mix("##td")))
                else:
                    t.set(x, y, z, ".")
    # the crown: corners only, broken to different heights, the tallest reaching y 24
    for i, (dx, dz) in enumerate(((0, 0), (4, 0), (0, 4), (4, 4))):
        top = 24 if i == 0 else 21 + t.rng.randint(0, 2)
        for y in range(21, top + 1):
            t.set(cx + dx, y, cz + dz, t.mix("dx"))
    for dx in range(5):  # the belfry's floor and lintel course, brass-banded
        for dz in range(5):
            t.set(cx + dx, 16, cz + dz, "w" if (dx in (0, 4) or dz in (0, 4)) else "p")
            if dx in (0, 4) or dz in (0, 4):
                t.set(cx + dx, 21, cz + dz, t.mix("wo"))
    t.set(cx + 2, 20, cz + 2, "B")  # the bell, hung from the lintel course above the node
    t.box(cx + 1, 21, cz + 1, cx + 3, 21, cz + 3, "p")
    t.set(cx + 2, 17, cz + 2, "O")
    t.block_entities[f"{cx + 2},17,{cz + 2}"] = {"node": {"type": "vftv:choir"}}
    # the tower's door onto the hall
    door = (cx + 2, cz + 4)
    t.set(door[0], 1, door[1], ".")
    t.set(door[0], 2, door[1], ".")
    t.entity(8, 1, 12, "vftv:answerer")
    t.entity(14, 1, 12, "vftv:answerer_cantor")
    return t


def wing(name, sockets, answerers, chest, style):
    """17 x 8 x 17: one plug in the north wall, 0-2 end sockets in the side walls."""
    t = Template(name, 17, 8, 17)
    room(t, 2, 5, 5)
    t.connector(8, 0, "north", "wing_plug")
    if "west" in sockets:
        t.connector(0, 8, "west", "end_socket")
    if "east" in sockets:
        t.connector(16, 8, "east", "end_socket")
    if style == "cloister":  # an arcade round an open court
        for i in range(3, 14, 2):
            for x, z in ((3, i), (13, i), (i, 3), (i, 13)):
                if (x, z) not in ((8, 3), (3, 8), (13, 8)):
                    for y in range(1, 4):
                        t.set(x, y, z, "k")
                    t.set(x, 4, z, t.mix("wo"))
    elif style == "bell_pit":  # a fallen bell in a ring of chiseled tuff
        for x, z in ((6, 6), (10, 6), (6, 10), (10, 10)):
            t.set(x, 1, z, "c")
        t.set(8, 1, 9, "F")
    elif style == "scriptorium":  # writing desks of polished tuff in rows
        for z in (5, 9, 13):
            for x in (3, 4, 12, 13):
                t.set(x, 1, z, "p")
    elif style == "fallen_nave":  # the collapsed vault in rubble heaps
        for _ in range(14):
            x, z = t.rng.randint(2, 14), t.rng.randint(4, 14)
            if abs(x - 8) > 1:
                t.set(x, 1, z, t.mix("bbx#"))
    if chest:
        t.set(14, 1, 14, "C")
        t.block_entities["14,1,14"] = {"chest": {"loot_table": "vftv:chests/choir_ruin"}}
    for x, z in answerers:
        t.entity(x, 1, z, "vftv:answerer")
    return t


def rubble_a():
    """The spec's example file, exactly."""
    t = Template("rubble_a", 9, 2, 9)
    rows = ["    J    ", " #       ", "   #  c  ", "         ", "  ##     ", "         ", "      #  ", " c       ", "         "]
    for z in range(9):
        for x in range(9):
            t.set(x, 0, z, "#")
            ch = rows[z][x]
            t.set(x, 1, z, {"J": "^"}.get(ch, ch))
    t.block_entities["4,1,0"] = {"jigsaw": {
        "name": CONNECTORS["end_plug"][0],
        "target": CONNECTORS["end_plug"][1],
        "pool": CONNECTORS["end_plug"][2],
        "final_state": "minecraft:air",
        "joint": "aligned",
    }}
    return t


def rubble_b():
    """A broken arch over a heap: 9 x 4 x 9."""
    t = Template("rubble_b", 9, 4, 9)
    t.foundation("bdt")
    t.set(4, 1, 0, "^")
    t.block_entities["4,1,0"] = {
        "jigsaw": {
            "name": CONNECTORS["end_plug"][0],
            "target": CONNECTORS["end_plug"][1],
            "pool": CONNECTORS["end_plug"][2],
            "final_state": "minecraft:air",
            "joint": "aligned",
        }
    }
    for y in range(1, 4):
        t.set(2, y, 5, "k")
        t.set(6, y, 5, "k" if y < 3 else " ")
    t.set(3, 3, 5, "c")
    for _ in range(6):
        t.set(t.rng.randint(1, 7), 1, t.rng.randint(2, 7), t.mix("bx#"))
    return t


def all_templates():
    return [
        chancel("chancel_a", "nw"),
        chancel("chancel_b", "ne"),
        wing("cloister", ("west", "east"), [(8, 8), (5, 11)], False, "cloister"),
        wing("bell_pit", ("east",), [(8, 7)], True, "bell_pit"),
        wing("scriptorium", ("west",), [(8, 6), (8, 11)], True, "scriptorium"),
        wing("fallen_nave", ("west", "east"), [(8, 12)], True, "fallen_nave"),
        rubble_a(),
        rubble_b(),
    ]


# --- review sheet ------------------------------------------------------------------------------

COLOUR = {
    "#": "mauve1", "t": "mauve0", "c": "mauve2", "p": "mauve2", "k": "brass5", "d": "ink3",
    "x": "ink2", "b": "ink2", "w": "brass3", "o": "teal1", "R": "brass4", "O": "teal2",
    "B": "brass4", "F": "brass4", "C": "copper2", "^": "amethyst2", ">": "amethyst2",
    "v": "amethyst2", "<": "amethyst2",
}


def standing(t, x, y, z):
    if not (0 <= x < t.sx and 0 <= z < t.sz and 1 <= y < t.sy):
        return False
    below, here = t.get(x, y - 1, z), t.get(x, y, z)
    above = t.get(x, y + 1, z) if y + 1 < t.sy else "."
    return below not in " .^>v<BFRO" and here == "." and above == "."


def preview(t, out):
    """An isometric block preview: blocks drawn as lit cubes; standing cells marked."""
    from PIL import Image, ImageDraw

    from lib import rgb

    s = 6
    w = (t.sx + t.sz) * s * 2 + 40
    h = (t.sx + t.sz) * s + t.sy * s * 2 + 40
    img = Image.new("RGB", (w, h), rgb("ink0"))
    d = ImageDraw.Draw(img)
    ox, oy = t.sz * s * 2 + 20, t.sy * s * 2 + 20

    def iso(x, y, z):
        return ox + (x - z) * s * 2, oy + (x + z) * s - y * s * 2

    for y in range(t.sy):
        for z in range(t.sz):
            for x in range(t.sx):
                ch = t.get(x, y, z)
                mark = standing(t, x, y, z)
                if ch in " ." and not mark:
                    continue
                px, py = iso(x, y, z)
                top = [(px, py), (px + 2 * s, py + s), (px, py + 2 * s), (px - 2 * s, py + s)]
                if mark:
                    d.polygon([(a, b + 2 * s) for a, b in top], outline=rgb("teal2"))
                    continue
                c = rgb(COLOUR.get(ch, "mauve1"))
                d.polygon(top, fill=c)
                d.polygon([(px - 2 * s, py + s), (px, py + 2 * s), (px, py + 4 * s), (px - 2 * s, py + 3 * s)],
                          fill=tuple(int(v * 0.7) for v in c))
                d.polygon([(px + 2 * s, py + s), (px, py + 2 * s), (px, py + 4 * s), (px + 2 * s, py + 3 * s)],
                          fill=tuple(int(v * 0.5) for v in c))
    for e in t.entities:
        px, py = iso(*e["pos"])
        colour = rgb("amethyst3" if e["type"].endswith("cantor") else "copper3")
        d.rectangle([px - 3, py - 6, px + 3, py + 6], fill=colour)
    d.text((8, 8), t.name, fill=rgb("brass4"))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "content"
    for template in all_templates():
        write(template, root)
        if len(sys.argv) > 2:
            preview(template, os.path.join(sys.argv[2], template.name + ".png"))
