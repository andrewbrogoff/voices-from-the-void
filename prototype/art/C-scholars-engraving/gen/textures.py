"""Generate every Scholar's Engraving texture. Run from anywhere; writes into ../assets."""

import os

import numpy as np

from engrave import c, hatch, hush_field, save, sprite

OUT = os.path.join(os.path.dirname(__file__), "..", "assets", "vftv", "textures")
INK = c("ink", 0)


def ramp_legend(ramp, lit_outline=True):
    """Digits are ramp shades; 'o' ink; 'q' the line thinned toward the light; 'h' mid-shadow hatch, 'H' deep hatch."""
    n = 5 if ramp in ("copper", "brass", "tarnish", "hush") else 4
    leg = {str(i): c(ramp, i) for i in range(n)}
    leg.update(o=INK, q=c(ramp, 0) if lit_outline else INK, h=hatch(ramp, 1, 2), H=hatch(ramp, 0, 1))
    return leg


# --- Stage 1: Tuning Fork. Upright specimen pose, copper, one amethyst shard in the yoke. ---
FORK = [
    "....qo....qo....",
    "...q43o..q32o...",
    "...q42o..q3Ho...",
    "...q32o..q2Ho...",
    "...q32oo.q2Ho...",
    "...q32owoo2Ho...",
    "...q32ovmo2Ho...",
    "...q32ovdo2Ho...",
    "...q32omdo21o...",
    "...q332oo211o...",
    "....q3221g1o....",
    ".....oo21oo.....",
    "......q21o......",
    "......q21o......",
    ".....q3210o.....",
    "......oooo......",
]


def tuning_fork():
    leg = ramp_legend("copper")
    leg.update(w=c("amethyst", 3), v=c("amethyst", 2), m=c("amethyst", 1), d=c("amethyst", 0), g=c("verdigris", 1))
    return sprite(FORK, leg)


# --- Stage 2: Resonant Brass ingot. Front-on cast bar (not vanilla's diagonal), olive brass, verdigris hallmark ring. ---
INGOT = [
    "................",
    "................",
    "................",
    "................",
    "...qqqqqqqqqq...",
    "..q44433T3332o..",
    "..q4433T4T332o..",
    ".q444444T44443o.",
    ".q111111111110o.",
    ".q2hhhhhhhhhH0o.",
    ".qg22222222210o.",
    ".oooooooooooooo.",
    "................",
    "................",
    "................",
    "................",
]


def resonant_brass_ingot():
    leg = ramp_legend("brass")
    leg.update(T=c("verdigris", 1), g=c("verdigris", 1))
    return sprite(INGOT, leg)


# --- Stage 4: Hollow Bell. Tarnished brass, clapperless void mouth, the Hush eating in, lines failing, ink pooling. ---
BELL = [
    "......qooo......",
    ".....q3..1o.....",
    "....qq2oo1oo....",
    "...q23321hh0o...",
    "...q23321hh0o...",
    "...q23321hh0o...",
    "...q23321hh0o...",
    "...q23321hh0o...",
    "..q223321hhh0o..",
    ".q2223321hhhH0o.",
    ".q333332221100o.",
    ".o1VVVVVVVVVV0o.",
    "..o22PP110000o..",
    "...ooPooooooo...",
    ".....P..........",
    "................",
]


def hollow_bell(seed=4):
    leg = ramp_legend("tarnish")
    leg.update(V=c("void", 0), P=INK)
    hush = hush_field(16, 16, centre=(14, 9), radius=4.5, band=2.5, seed=seed)
    rng = np.random.default_rng(seed)
    fail = {(x, y) for y in range(16) for x in range(16) if rng.random() < 0.45}  # engraved lines drop out
    return sprite(BELL, leg, fail=fail, hush=hush, hush_drops_ink=0.5, seed=seed)


# --- Stage 2 block: Essentia Jar. Smoky glass (translucent), brass lid, verdigris essentia with a standing ripple. ---
def jar_glass():
    rows = []
    for y in range(16):
        row = ""
        for x in range(16):
            if not (3 <= x <= 12 and 6 <= y <= 15):
                row += "."
            elif y == 6:
                row += "r"  # rim catching light
            elif y == 15:
                row += "b"  # thick base
            elif x in (3, 12):
                row += "e"  # wall edge: you look through more glass
            elif x == 4 and y < 14:
                row += "s"  # highlight streak, light from the left
            elif x == 10 and 8 <= y <= 12 and y % 2 == 0:
                row += "l"  # sparse engraved reflection line
            else:
                row += "g"
        rows.append(row)
    leg = {
        "r": c("glass", 3, 200), "b": c("glass", 1, 210), "e": c("glass", 2, 150),
        "s": c("glass", 3, 150), "l": c("glass", 0, 120), "g": c("glass", 1, 70),
    }
    return sprite(rows, leg)


def jar_glass_top():
    rows = ["................"] * 3
    for y in range(3, 13):
        # only the rim shows round the lid; the centre is empty so it can't z-fight the lid's underside
        rows.append("..." + "".join("r" if x in (3, 12) or y in (3, 12) else "." for x in range(3, 13)) + "...")
    rows += ["................"] * 3
    return sprite(rows, {"r": c("glass", 3, 200)})


LID = [
    "....43333332....",  # lid side, row 0: lit lip
    "....hhhhhhhh....",  # lid side, row 1: engraved shadow
    "................",
    "................",
    "....44444433....",  # lid top 8x8 at [4,4]-[12,12]: a soft engraved ring, no ink
    "....43111132....",
    "....41333312....",
    "....41344312....",
    "....41344312....",
    "....41333312....",
    "....32111122....",
    "....22222222....",
    "................",
    "..43............",  # knob side [2,13]-[4,14]
    "................",
    "................",
]


def jar_lid():
    return sprite(LID, ramp_legend("brass"))


ESSENTIA = [
    "33333333........",  # liquid side [0,0]-[8,5]: lit surface line, then flat bands (no hatch behind glass)
    "22222222........",
    "22222222........",
    "11111111........",
    "00000000........",
    "................",
    "................",
    "................",
    "22222222........",  # liquid top [0,8]-[8,16]: standing ripple
    "21111112........",
    "21233212........",
    "21322312........",
    "21322312........",
    "21233212........",
    "21111112........",
    "22222222........",
]


def jar_essentia():
    return sprite(ESSENTIA, {str(i): c("verdigris", i, 235) for i in range(4)})


def main():
    for sub in ("item", "block"):
        os.makedirs(os.path.join(OUT, sub), exist_ok=True)
    save(tuning_fork(), os.path.join(OUT, "item", "tuning_fork.png"))
    save(resonant_brass_ingot(), os.path.join(OUT, "item", "resonant_brass_ingot.png"))
    save(hollow_bell(), os.path.join(OUT, "item", "hollow_bell.png"))
    save(jar_glass(), os.path.join(OUT, "block", "essentia_jar_glass.png"))
    save(jar_glass_top(), os.path.join(OUT, "block", "essentia_jar_glass_top.png"))
    save(jar_lid(), os.path.join(OUT, "block", "essentia_jar_lid.png"))
    save(jar_essentia(), os.path.join(OUT, "block", "essentia_jar_essentia.png"))
    print("textures written")


if __name__ == "__main__":
    main()
