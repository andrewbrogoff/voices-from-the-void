#!/usr/bin/env bash
# Regenerates every Ink & Brass asset and preview, byte for byte. Run from anywhere.
set -euo pipefail
B="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$B/../tools"
cd "$B"
python3 gen/lib.py
python3 gen/textures.py assets
python3 gen/sound.py assets/vftv/sounds/tuning_fork_strike.ogg
mkdir -p previews
blender -b --factory-startup --python "$TOOLS/render_model.py" -- "$B/assets" vftv:block/essentia_jar previews/essentia_jar.png 512 >/dev/null
python3 "$TOOLS/sheet.py" previews/sheet.png \
  assets/vftv/textures/item/{tuning_fork,resonant_brass_ingot,hollow_bell}.png \
  assets/vftv/textures/block/essentia_jar_{side,lid,glass_top,essentia}.png \
  --render previews/essentia_jar.png
sox assets/vftv/sounds/tuning_fork_strike.ogg -n spectrogram -o previews/spectrogram.png
