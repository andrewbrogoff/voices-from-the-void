"""The jar render shrunk to real icon size (32 px = GUI scale 2, 48 px = scale 3), blown up x3 on slot grey.
sheet.py only shows block renders large; this is the honest slot-size view. Writes previews/jar_slot_check.png."""

import os

from PIL import Image

base = os.path.join(os.path.dirname(__file__), "..")
r = Image.open(os.path.join(base, "previews", "essentia_jar.png")).convert("RGBA")
out = Image.new("RGBA", (270, 164), "#8b8b8b")
x = 10
for s in (32, 48):
    out.alpha_composite(r.resize((s, s), Image.LANCZOS).resize((s * 3, s * 3), Image.NEAREST), (x, 10))
    x += s * 3 + 10
out.save(os.path.join(base, "previews", "jar_slot_check.png"))
