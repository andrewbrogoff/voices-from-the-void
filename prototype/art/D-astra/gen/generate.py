# SPDX-License-Identifier: MPL-2.0
"""Seeded, code-authored pixel clusters, model data and modal synthesis."""
import argparse
import json
from pathlib import Path
import wave

import numpy as np
from PIL import Image, ImageDraw

from palette import RAMPS, rgba

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "assets/vftv"
SEED = 47017


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


def canvas():
    im = Image.new("RGBA", (32, 32))
    return im, ImageDraw.Draw(im)


def texture(im, group, name):
    path = A / "textures" / group / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def items(revision):
    c, b, g, a, o, h = (RAMPS[k] for k in RAMPS)
    rng = np.random.default_rng(SEED)
    im, d = canvas()
    # A deep open U, with a crystal at its heel rather than a third tine.
    d.polygon([(5, 2), (10, 2), (10, 13), (12, 16), (19, 16),
               (21, 13), (21, 2), (26, 2), (26, 15), (23, 20),
               (18, 22), (18, 29), (13, 29), (13, 22), (8, 20), (5, 15)], fill=c[0])
    d.polygon([(5, 2), (9, 2), (9, 14), (12, 18), (20, 18),
               (23, 14), (23, 2), (25, 2), (25, 15), (21, 20),
               (10, 20), (5, 14)], fill=c[2])
    d.rectangle((5, 3, 6, 12), fill=c[4])
    d.rectangle((7, 3, 8, 14), fill=c[3])
    d.rectangle((21, 2, 23, 12), fill=c[3])
    d.rectangle((21, 2, 24, 3), fill=c[4])
    d.line([(7, 15), (10, 18), (12, 18)], fill=c[4], width=1)
    d.rectangle((14, 22, 16, 28), fill=c[2])
    d.line((14, 24, 14, 28), fill=c[4])
    d.polygon([(15, 17), (19, 20), (17, 24), (12, 21)], fill=a[0])
    d.polygon([(15, 17), (17, 20), (14, 22), (12, 21)], fill=a[2])
    d.polygon([(17, 20), (19, 20), (17, 24), (14, 22)], fill=a[1])
    d.line([(15, 17), (12, 20)], fill=a[3])
    if revision >= 1:
        d.polygon([(15, 17), (20, 20), (17, 26), (12, 22)], fill=a[0])
        d.polygon([(15, 17), (17, 20), (14, 23), (12, 22)], fill=a[2])
        d.polygon([(17, 20), (19, 20), (17, 25), (14, 23)], fill=a[1])
        d.line([(15, 17), (12, 21), (14, 22)], fill=a[3])
        d.line((17, 20, 17, 23), fill=a[2])
    if revision >= 2:
        d.polygon([(15, 18), (19, 20), (17, 24), (12, 21)], fill=a[0])
        d.polygon([(15, 18), (16, 20), (14, 22), (12, 21)], fill=a[2])
        d.polygon([(16, 20), (18, 20), (17, 23), (14, 22)], fill=a[1])
        d.line([(15, 18), (13, 20)], fill=a[3])
        d.line((22, 5, 22, int(rng.integers(9, 12))), fill=c[4])
    texture(im, "item", "tuning_fork")

    im, d = canvas()
    d.polygon([(3, 13), (11, 7), (26, 9), (29, 17), (21, 24), (4, 21)], fill=b[0])
    d.polygon([(3, 13), (11, 7), (26, 9), (20, 16)], fill=b[3])
    d.polygon([(3, 14), (20, 17), (20, 23), (4, 20)], fill=b[2])
    d.polygon([(21, 17), (27, 11), (28, 17), (21, 23)], fill=b[1])
    d.line([(4, 13), (20, 16), (25, 10)], fill=b[4], width=1)
    d.polygon([(7, 11), (11, 8), (24, 10), (21, 12)], fill=b[4])
    # Cool reflected band, not an emissive rune or embedded electronics.
    d.polygon([(5, 17), (19, 19), (19, 20), (5, 18)], fill=o[0])
    d.line((6, 17, 18, 19), fill=o[1])
    d.line((13, 10, 10, 12), fill=b[1])
    d.line((18, 11, 15, 13), fill=b[1])
    if revision >= 1:
        d.polygon([(5, 17), (19, 19), (19, 20), (5, 18)], fill=b[2])
        d.line((5, 17, 12, 18), fill=o[0])
        d.line((5, 16, 12, 17), fill=o[1])
        d.line((13, 10, 10, 12), fill=b[0])
        d.line((18, 11, 15, 13), fill=b[0])
    if revision >= 2:
        d.line((5, 20, 18, 22), fill=b[1])
        d.line((6, 14, int(rng.integers(10, 13)), 15), fill=b[4])
    texture(im, "item", "resonant_brass_ingot")

    im, d = canvas()
    d.rectangle((13, 2, 18, 6), fill=h[1])
    d.rectangle((14, 2, 17, 3), fill=h[4])
    d.rectangle((15, 4, 16, 5), fill=(0, 0, 0, 0))
    d.polygon([(11, 6), (20, 6), (24, 10), (25, 19), (29, 23),
               (29, 26), (25, 28), (6, 28), (2, 25), (3, 22),
               (6, 19), (7, 10)], fill=h[1])
    d.polygon([(11, 7), (19, 7), (23, 11), (24, 20), (27, 23),
               (6, 23), (8, 18), (9, 11)], fill=h[3])
    d.polygon([(11, 8), (14, 8), (12, 12), (11, 20), (8, 21), (9, 12)], fill=h[4])
    d.polygon([(20, 9), (23, 12), (24, 20), (27, 23), (22, 23), (20, 18)], fill=h[2])
    d.polygon([(4, 24), (9, 22), (24, 22), (28, 24), (24, 27), (8, 27)], fill=h[0])
    d.line([(4, 24), (8, 22), (24, 22)], fill=o[0])
    d.line([(8, 27), (24, 27), (28, 25)], fill=h[3])
    d.rectangle((14, 23, 17, 26), fill=h[2])
    d.rectangle((14, 24, 15, 25), fill=h[4])
    d.line([(15, 10), (13, 12), (13, 15)], fill=o[1], width=1)
    if revision >= 1:
        d.polygon([(18, 9), (21, 12), (20, 17), (22, 21), (20, 26),
                   (13, 26), (15, 22), (14, 18), (17, 14)], fill=h[0])
        d.line([(18, 9), (17, 14), (14, 18), (15, 21)], fill=h[2])
        d.polygon([(24, 17), (25, 19), (27, 21), (24, 22), (23, 20)], fill=(0, 0, 0, 0))
        d.polygon([(20, 25), (23, 25), (24, 29), (19, 29)], fill=(0, 0, 0, 0))
        d.line((8, 27, 17, 27), fill=h[4])
    if revision >= 2:
        d.line([(15, 10), (13, 12), (13, 15)], fill=h[4], width=1)
        d.line([(4, 24), (8, 22), (13, 22)], fill=h[2])
        d.line((8, 26, 12, 26), fill=h[2])
        d.line((24, 27, 27, 25), fill=h[3], width=2)
    texture(im, "item", "hollow_bell")

    for name in ("tuning_fork", "resonant_brass_ingot", "hollow_bell"):
        save_json(A / f"models/item/{name}.json", {
            "parent": "minecraft:item/generated", "textures": {"layer0": f"vftv:item/{name}"}})
        save_json(A / f"items/{name}.json", {
            "model": {"type": "minecraft:model", "model": f"vftv:item/{name}"}})


def jar(revision):
    g, b, o = (RAMPS[k] for k in ("glass", "brass", "opal"))
    rng = np.random.default_rng(SEED + 1)
    im, d = canvas()
    d.rectangle((0, 0, 19, 17), fill=rgba(g[1], 32))
    d.rectangle((0, 0, 1, 17), fill=rgba(g[2], 125 if revision >= 1 else 195))
    d.rectangle((18, 0, 19, 17), fill=rgba(g[0], 100 if revision >= 1 else 160))
    d.rectangle((2, 0, 17, 1), fill=rgba(g[2], 145))
    d.rectangle((2, 16, 17, 17), fill=rgba(g[1], 145))
    d.rectangle((3, 3, 4, 10), fill=rgba(g[4], 215))
    d.rectangle((5, 3, 5, 7), fill=rgba(g[3], 120))
    d.rectangle((14, 11, 15, 14), fill=rgba(o[2], 150))
    if revision >= 2:
        d.rectangle((2, 0, 17, 1), fill=rgba(g[2], 85))
        d.rectangle((3, 3, 4, 10), fill=rgba(g[1], 32))
        d.rectangle((3, 3, 4, int(rng.integers(8, 11))), fill=rgba(g[4], 215))
    texture(im, "block", "essentia_jar_glass")
    im, d = canvas()
    d.rectangle((0, 0, 19, 19), fill=rgba(g[1], 30))
    d.rectangle((0, 0, 19, 19), outline=rgba(g[2], 150), width=2)
    if revision >= 2:
        d.rectangle((0, 0, 19, 19), fill=rgba(g[1], 24))
        d.rectangle((0, 0, 19, 19), outline=rgba(g[2], 90), width=1)
    texture(im, "block", "essentia_jar_glass_top")
    im, d = canvas()
    d.rectangle((0, 0, 19, 19), fill=b[2])
    d.rectangle((1, 1, 18, 18), fill=b[3])
    d.rectangle((3, 3, 16, 16), fill=b[4])
    d.line((4, 5, 13, 5), fill=b[2], width=2)
    d.line((4, 9, 13, 9), fill=b[2], width=2)
    d.rectangle((16, 12, 18, 16), fill=o[0])
    if revision >= 2:
        d.rectangle((0, 0, 19, 19), fill=b[2])
        d.rectangle((1, 1, 18, 18), fill=b[3])
        d.rectangle((3, 3, 16, 16), fill=b[4])
        # Broad stepped rings: physical seal impressions on a lid.
        ring = [(7, 4), (12, 4), (15, 7), (15, 12), (12, 15),
                (7, 15), (4, 12), (4, 7), (7, 4)]
        d.line(ring, fill=b[2], width=1)
        d.line([(8, 7), (11, 7), (12, 8), (12, 11), (11, 12), (8, 12), (7, 11), (7, 8), (8, 7)], fill=b[2])
        d.line((2, 15, 2, 17), fill=o[1])
    texture(im, "block", "essentia_jar_lid")
    im, d = canvas()
    d.rectangle((0, 0, 19, 3), fill=b[1])
    d.rectangle((0, 0, 19, 0), fill=b[4])
    d.rectangle((0, 1, 19, 2), fill=b[2])
    d.rectangle((4, 1, 6, 2), fill=b[3])
    texture(im, "block", "essentia_jar_band")
    im, d = canvas()
    d.rectangle((0, 0, 15, 15), fill=rgba(o[2], 220))
    d.rectangle((0, 0, 15, 1), fill=rgba(g[4], 240))
    d.rectangle((0, 2, 15, 3), fill=rgba(o[2], 230))
    d.rectangle((0, 4, 15, 5), fill=rgba(o[0], 215))
    if revision >= 1:
        d.rectangle((0, 0, 15, 5), fill=rgba(o[2], 205))
        d.line((0, 0, 15, 0), fill=rgba(g[4], 210))
        d.line((0, 5, 15, 5), fill=rgba(o[0], 165))
    texture(im, "block", "essentia_jar_essentia")
    im, d = canvas()
    d.rectangle((0, 0, 15, 15), fill=rgba(o[2], 205))
    d.line((2, 2, 7, 2), fill=rgba(g[4], 200))
    texture(im, "block", "essentia_jar_essentia_top")

    def box(lo, hi, side, top=None):
        width, height, depth = [hi[i] - lo[i] for i in range(3)]
        faces = {f: {"texture": f"#{side}", "uv": [0, 0, width if f in ("north", "south") else depth, height]}
                 for f in ("north", "south", "east", "west")}
        for f in ("up", "down"):
            faces[f] = {"texture": f"#{top or side}", "uv": [0, 0, width, depth]}
        return {"from": lo, "to": hi, "faces": faces}

    model = {"render_type": "minecraft:translucent", "ambientocclusion": False,
             "textures": {k: f"vftv:block/essentia_jar_{v}" for k, v in {
                 "glass": "glass", "top": "glass_top", "lid": "lid", "band": "band", "essentia": "essentia", "essentia_top": "essentia_top", "particle": "glass"}.items()},
             "elements": [box([3, 1, 3], [13, 10, 13], "glass", "top"),
                          box([4, 10, 4], [12, 11, 12], "glass", "top"),
                          box([3, 11, 3], [13, 13, 13], "band", "lid"),
                          box([4, 1.5, 4], [12, 4.5, 12], "essentia"),
                          box([3, 0.5, 3], [13, 1, 13], "band", "lid")],
             "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [1, 1, 1]},
                         "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
                         "fixed": {"rotation": [0, 180, 0], "scale": [0.8, 0.8, 0.8]},
                         "thirdperson_righthand": {"rotation": [75, 45, 0], "scale": [0.5, 0.5, 0.5]},
                         "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.65, 0.65, 0.65]}}}
    if revision >= 1:
        model["elements"][1] = box([4.5, 10, 4.5], [11.5, 11.5, 11.5], "glass", "top")
        model["elements"][2] = box([4, 11.5, 4], [12, 13, 12], "band", "lid")
        for face in ("up", "down"):
            model["elements"][2]["faces"][face]["uv"] = [0, 0, 10, 10]
        model["elements"][3] = box([4, 1.5, 4], [12, 4.5, 12], "essentia", "essentia_top")
    save_json(A / "models/block/essentia_jar.json", model)
    save_json(A / "blockstates/essentia_jar.json", {"variants": {"": {"model": "vftv:block/essentia_jar"}}})
    save_json(A / "items/essentia_jar.json", {"model": {"type": "minecraft:model", "model": "vftv:block/essentia_jar"}})


def sound():
    sr, duration = 44100, 2.6
    t = np.arange(round(sr * duration)) / sr
    rng = np.random.default_rng(SEED)
    x = np.zeros_like(t)
    # Paired low tines with barely perceptible beating; brief glass/copper modes.
    for hz, amplitude, decay in [(523.25, 0.65, 0.53), (524.55, 0.30, 0.48),
                                  (1047.0, 0.10, 0.22), (1432.7, 0.065, 0.15),
                                  (2317.0, 0.025, 0.06)]:
        x += amplitude * np.sin(2 * np.pi * hz * t) * np.exp(-t / decay)
    noise = rng.normal(0, 1, len(t))
    noise = np.convolve(noise, np.ones(21) / 21, mode="same")
    x += 0.025 * noise * np.exp(-t / 0.009)
    x *= 1 - np.exp(-(t / 0.0035) ** 2)
    # A quiet, short room; no bright synthetic shimmer or long ambience.
    dry = x.copy()
    for delay, gain in [(0.043, 0.055), (0.071, 0.032), (0.113, 0.018)]:
        n = round(delay * sr)
        x[n:] += dry[:-n] * gain
    spectrum = np.fft.rfft(x)
    hz = np.fft.rfftfreq(len(x), 1 / sr)
    spectrum *= 1 / (1 + (hz / 4500) ** 12)
    x = np.fft.irfft(spectrum, n=len(x))
    x -= x.mean()
    n = round(0.22 * sr)
    x[-n:] *= np.cos(np.linspace(0, np.pi / 2, n)) ** 2
    x[:220] *= np.sin(np.linspace(0, np.pi / 2, 220)) ** 2
    x *= 10 ** (-7 / 20) / np.max(np.abs(x))
    pcm = np.round(x * 32767).astype("<i2")
    pcm[-441:] = 0
    with wave.open(str(ROOT / "gen/tmp/strike.wav"), "wb") as out:
        out.setparams((1, 2, sr, len(pcm), "NONE", "not compressed"))
        out.writeframes(pcm.tobytes())
    save_json(A / "sounds.json", {"tuning_fork_strike": {"sounds": [
        {"name": "vftv:tuning_fork_strike", "volume": 0.65}]}})


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--revision", type=int, choices=(0, 1, 2), default=2)
    args = parser.parse_args()
    items(args.revision)
    jar(args.revision)
    sound()
