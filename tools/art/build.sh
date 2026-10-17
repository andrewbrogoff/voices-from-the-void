#!/usr/bin/env bash
# The one entry point (art style, "Pipeline"): regenerates every asset and fixture, byte for byte.
# Asset tickets append their generator lines here, then their contact sheet into build/previews/.
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1 # the generators import lib.py; leave no __pycache__ behind
ART="$(cd "$(dirname "$0")" && pwd)"
python3 "$ART/lib.py"
python3 "$ART/wide_font_pack.py"
ASSETS="$ART/../../src/main/resources/assets"
PREVIEWS="$ART/../../build/previews"
python3 "$ART/aspects.py" "$ASSETS" "$PREVIEWS/aspects.png"
python3 "$ART/fork.py" "$ASSETS"
python3 "$ART/sheet.py" "$PREVIEWS/fork.png" "$ASSETS/vftv/textures/item/tuning_fork.png" \
    "$ASSETS/vftv/textures/item/tuning_fork_ringing.png" "$ASSETS/vftv/textures/gui/sprites/hud/vis_frame.png"
