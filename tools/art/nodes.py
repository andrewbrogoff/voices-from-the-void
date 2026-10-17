"""The node core strip and the glint particle (wands spec, "Production tickets": Node assets, the parts T08
ships). Usage: python3 nodes.py <assets_root>

The core is an 8-frame 16x128 strip of a ringing teal orb, cutout (no partial alpha): a bright centre,
the teal ramp outward and an ink rim, its radius breathing by one pixel over the cycle so neighbouring
frames differ only slightly in mean luminance (the flash test). The glint is 4 frames at 8x8: a spark that
opens into a cross and fades down the ramp.
"""

import math
import os
import sys

import numpy as np

from lib import canvas, put, save

ROOT = sys.argv[1] if len(sys.argv) > 1 else "assets"
TEX = os.path.join(ROOT, "vftv/textures")


def core_strip():
    strip = np.zeros((128, 16, 4), np.uint8)
    for f in range(8):
        frame = canvas(16)
        r = 5.0 + 0.8 * math.sin(2 * math.pi * f / 8)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= r - 3.2:
                    put(frame, x, y, "teal3")
                elif d <= r - 1.8:
                    put(frame, x, y, "teal2")
                elif d <= r - 0.6:
                    put(frame, x, y, "teal1")
                elif d <= r + 0.4:
                    put(frame, x, y, "ink1")
        strip[f * 16 : (f + 1) * 16] = frame
    return strip


def luminance(img):
    """Mean relative luminance of the opaque pixels (the flash test's measure)."""
    total, n = 0.0, 0
    for row in img:
        for p in row:
            if p[3]:
                lin = [(c / 255) / 12.92 if c / 255 <= 0.04045 else ((c / 255 + 0.055) / 1.055) ** 2.4 for c in p[:3]]
                total += 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
                n += 1
    return total / max(1, n)


def glint(frame):
    img = canvas(8)
    shapes = [
        [(3, 3, "teal3"), (4, 4, "teal3"), (3, 4, "teal2"), (4, 3, "teal2")],
        [(3, 3, "teal3"), (4, 4, "teal3"), (3, 4, "teal3"), (4, 3, "teal3"), (3, 2, "teal2"), (4, 5, "teal2"), (2, 4, "teal2"), (5, 3, "teal2")],
        [(3, 3, "teal2"), (4, 4, "teal2"), (3, 4, "teal2"), (4, 3, "teal2"), (3, 1, "teal1"), (4, 6, "teal1"), (1, 4, "teal1"), (6, 3, "teal1")],
        [(3, 3, "teal1"), (4, 4, "teal1")],
    ]
    for x, y, c in shapes[frame]:
        put(img, x, y, c)
    return img


if __name__ == "__main__":
    strip = core_strip()
    save(strip, f"{TEX}/block/node_core.png")
    lums = [luminance(strip[f * 16 : (f + 1) * 16]) for f in range(8)]
    # Flash test: frames at the fastest schedule (2 ticks) never alternate with a jump of 0.10 or more.
    assert max(abs(lums[i] - lums[(i + 1) % 8]) for i in range(8)) < 0.10, lums
    for f in range(4):
        save(glint(f), f"{TEX}/particle/node_glint_{f}.png")
    print("nodes ok; core frame luminances", [round(float(l), 3) for l in lums])
