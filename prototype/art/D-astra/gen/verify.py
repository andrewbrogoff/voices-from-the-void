# SPDX-License-Identifier: MPL-2.0
"""Canonical Ogg pages, small asset checks, decoded audio metrics and slot proofs."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFont

from palette import RAMPS

ROOT = Path(__file__).resolve().parents[1]
A = ROOT / "assets/vftv"
OGG = A / "sounds/tuning_fork_strike.ogg"


def canonicalize():
    # libogg chooses random serial numbers; fix only that header and its CRC.
    data = bytearray(OGG.read_bytes())
    pos = 0
    while pos < len(data):
        assert data[pos:pos + 4] == b"OggS"
        segments = data[pos + 26]
        length = 27 + segments + sum(data[pos + 27:pos + 27 + segments])
        data[pos + 14:pos + 18] = (47017).to_bytes(4, "little")
        data[pos + 22:pos + 26] = b"\x00" * 4
        crc = 0
        for value in data[pos:pos + length]:
            crc ^= value << 24
            for _ in range(8):
                crc = ((crc << 1) ^ (0x04C11DB7 if crc & 0x80000000 else 0)) & 0xFFFFFFFF
        data[pos + 22:pos + 26] = crc.to_bytes(4, "little")
        pos += length
    assert pos == len(data)
    OGG.write_bytes(data)


def verify():
    palette = {tuple(bytes.fromhex(c[1:])) for ramp in RAMPS.values() for c in ramp}
    for path in (A / "textures").rglob("*.png"):
        im = Image.open(path).convert("RGBA")
        assert im.size == (32, 32), path
        assert all(tuple(p[:3]) in palette for p in im.get_flattened_data() if p[3]), path
        if path.parent.name == "item":
            assert set(im.getchannel("A").get_flattened_data()) <= {0, 255}, path
    for path in A.rglob("*.json"):
        json.loads(path.read_text())
    for name in ("tuning_fork", "resonant_brass_ingot", "hollow_bell", "essentia_jar"):
        item = json.loads((A / f"items/{name}.json").read_text())
        assert item["model"]["type"] == "minecraft:model"
        ref = item["model"]["model"].split(":")[1]
        assert (A / f"models/{ref}.json").is_file()
        if name != "essentia_jar":
            resource = json.loads((A / f"models/{ref}.json").read_text())
            assert resource["parent"] == "minecraft:item/generated"
            texture = resource["textures"]["layer0"].split(":")[1]
            assert (A / f"textures/{texture}.png").is_file()
    assert json.loads((A / "blockstates/essentia_jar.json").read_text()) == {
        "variants": {"": {"model": "vftv:block/essentia_jar"}}}
    model = json.loads((A / "models/block/essentia_jar.json").read_text())
    assert len(model["elements"]) <= 8
    for element in model["elements"]:
        assert all(0 <= lo < hi <= 16 for lo, hi in zip(element["from"], element["to"]))
        for face in element["faces"].values():
            ref = model["textures"][face["texture"][1:]].split(":")[1]
            assert (A / f"textures/{ref}.png").is_file()
            assert all(0 <= uv <= 16 for uv in face["uv"])
    sr = int(subprocess.check_output(["soxi", "-r", str(OGG)]))
    channels = int(subprocess.check_output(["soxi", "-c", str(OGG)]))
    assert (sr, channels) == (44100, 1)
    decoded = subprocess.check_output(["sox", str(OGG), "-t", "f32", "-L", "-"])
    x = np.frombuffer(decoded, dtype="<f4").astype(float)
    peak = float(np.max(np.abs(x)))
    energy = np.abs(np.fft.rfft(x)) ** 2
    hz = np.fft.rfftfreq(len(x), 1 / sr)
    high_fraction = float(energy[hz > 10000].sum() / energy.sum())
    end_peak = float(np.max(np.abs(x[-441:])))
    stats = {"duration_s": len(x) / sr, "sample_rate_hz": sr, "channels": channels,
             "peak_dbfs": float(20 * np.log10(peak)),
             "rms_dbfs": float(20 * np.log10(np.sqrt(np.mean(x * x)))),
             "dc_mean": float(x.mean()), "energy_fraction_above_10khz": high_fraction,
             "last_10ms_peak_dbfs": float(20 * np.log10(max(end_peak, 1e-12))),
             "first_sample": float(x[0]), "last_sample": float(x[-1])}
    assert 1.5 <= stats["duration_s"] <= 3
    assert stats["peak_dbfs"] <= -1
    assert abs(stats["dc_mean"]) < 0.0001
    assert high_fraction < 0.0001
    assert end_peak < 0.0001
    (ROOT / "previews/audio-metrics.json").write_text(json.dumps(stats, indent=2) + "\n")
    print(json.dumps(stats, indent=2))


def previews():
    font = ImageFont.load_default(14)
    small = ImageFont.load_default(11)
    board = Image.new("RGB", (800, 310), "#222932")
    d = ImageDraw.Draw(board)
    d.text((18, 12), "D / OPALINE RESONANCE  |  native GUI scale 2 + 4x inspection", font=font, fill="#F5F2C7")
    names = ["tuning_fork", "resonant_brass_ingot", "hollow_bell", "essentia_jar"]
    for i, name in enumerate(names):
        x = 16 + i * 196
        if name == "essentia_jar":
            render = Image.open(ROOT / "previews/essentia_jar.png").convert("RGBA")
            render = render.crop(render.getbbox())
            render.thumbnail((28, 28), Image.Resampling.LANCZOS)
            im = Image.new("RGBA", (32, 32))
            im.alpha_composite(render, ((32 - render.width) // 2, (32 - render.height) // 2))
            im.save(ROOT / "previews/essentia_jar_slot.png")
        else:
            im = Image.open(A / f"textures/item/{name}.png").convert("RGBA")
        d.text((x, 42), name.replace("resonant_brass_ingot", "resonant brass"), font=small, fill="#DDF0E8")
        for j, bg in enumerate(("#8B8B8B", "#EDEDED", "#14141C")):
            tile = Image.new("RGBA", (36, 36), bg)
            td = ImageDraw.Draw(tile)
            td.line([(0, 35), (0, 0), (35, 0)], fill="#373737", width=2)
            td.line([(1, 35), (35, 35), (35, 1)], fill="#FFFFFF", width=2)
            tile.alpha_composite(im, (2, 2))
            board.paste(tile.convert("RGB"), (x + j * 48, 67))
        large = Image.new("RGBA", (128, 128), "#343C46")
        large.alpha_composite(im.resize((128, 128), Image.Resampling.NEAREST))
        board.paste(large.convert("RGB"), (x, 124))
    d.text((18, 274), "32×32 authored pixels → 32 screen pixels / jar slot is a fitted Blender proxy, not an in-game capture", font=small, fill="#BDC6C8")
    board.save(ROOT / "previews/slots.png")
    files = sorted(A.rglob("*"))
    manifest = {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in files if p.is_file()}
    (ROOT / "previews/asset-sha256.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--canonicalize", action="store_true")
    parser.add_argument("--canonicalize-render", action="store_true")
    args = parser.parse_args()
    if args.canonicalize:
        canonicalize()
    elif args.canonicalize_render:
        path = ROOT / "previews/essentia_jar.png"
        im = Image.open(path)
        # Blender writes wall-clock render metadata; preserve every decoded pixel.
        Image.frombytes(im.mode, im.size, im.tobytes()).save(path)
    else:
        verify()
        previews()
