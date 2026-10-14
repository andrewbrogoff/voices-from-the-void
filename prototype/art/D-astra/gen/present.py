# SPDX-License-Identifier: MPL-2.0
"""Self-contained review card; all images and sound are generated locally."""
import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def uri(rel, mime="image/png"):
    return f"data:{mime};base64," + base64.b64encode((ROOT / rel).read_bytes()).decode()


cards = []
for label, path in [("Copper + amethyst", "assets/vftv/textures/item/tuning_fork.png"),
                    ("Ringing brass", "assets/vftv/textures/item/resonant_brass_ingot.png"),
                    ("Hollow bell", "assets/vftv/textures/item/hollow_bell.png"),
                    ("Essentia jar", "previews/essentia_jar_slot.png")]:
    src = uri(path)
    cards.append(f'<article><div class="large"><img src="{src}" alt="{label}" width="96" height="96"></div>'
                 f'<div class="slot"><img src="{src}" alt="GUI scale 2" width="32" height="32"></div>'
                 f'<p>{label}</p></article>')
html = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Opaline Resonance — option D</title><style>
*{box-sizing:border-box}body{margin:0;color:var(--foreground,#dde4e5);font:14px var(--font-sans,system-ui);background:transparent}
.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}
article{padding:16px;background:#29323b;border-radius:var(--radius,10px);color:#ddf0e8;text-align:center}
.large{height:112px;display:grid;place-items:center}.large img,.slot img{image-rendering:pixelated}
.slot{width:36px;height:36px;background:#8b8b8b;border:2px inset #d4d4d4;margin:8px auto 0}
p{margin:12px 0 0;font-size:12px}.sound{display:flex;gap:16px;align-items:center;flex-wrap:wrap;margin-top:16px}
audio{height:36px;max-width:100%}small{color:var(--muted-foreground,#aebbc1)}
details{margin-top:16px}summary{cursor:pointer}.proof{width:100%;height:auto;margin-top:12px}
@media(max-width:500px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style><body><div class="grid">''' + "".join(cards) + '''</div>
<div class="sound"><audio controls preload="none" src="''' + uri("assets/vftv/sounds/tuning_fork_strike.ogg", "audio/ogg") + '''"></audio><small>2.60 s · mono · 44.1 kHz · −7.05 dBFS peak</small></div>
<p>Native 32 px slots beneath enlarged views. Jar slot is a fitted Blender proxy.</p>
<details><summary>Revision passes and spectrum</summary>'''
for name in ("revisions/00-initial.png", "revisions/01-openings.png", "revisions/02-final.png", "spectrogram.png"):
    html += f'<img class="proof" src="{uri("previews/" + name)}" alt="{name}">'
html += "</details></body></html>"
(ROOT / "previews/review.html").write_text(html)
