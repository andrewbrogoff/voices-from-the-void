#!/usr/bin/env bash
# Regenerate every Scholar's Engraving asset and preview. Textures and the .ogg are byte-reproducible.
set -euo pipefail
OPT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS="$OPT/../tools"
cd "$OPT"
mkdir -p previews assets/vftv/sounds

python3 gen/textures.py
python3 gen/sound.py assets/vftv/sounds/tuning_fork_strike.ogg

blender -b --factory-startup --python "$TOOLS/render_model.py" -- \
  assets vftv:block/essentia_jar previews/essentia_jar.png 512 >/dev/null
python3 gen/slot_check.py
python3 "$TOOLS/sheet.py" previews/sheet.png \
  assets/vftv/textures/item/tuning_fork.png \
  assets/vftv/textures/item/resonant_brass_ingot.png \
  assets/vftv/textures/item/hollow_bell.png \
  assets/vftv/textures/block/essentia_jar_glass.png \
  assets/vftv/textures/block/essentia_jar_glass_top.png \
  assets/vftv/textures/block/essentia_jar_lid.png \
  assets/vftv/textures/block/essentia_jar_essentia.png \
  --render previews/essentia_jar.png
sox assets/vftv/sounds/tuning_fork_strike.ogg -n spectrogram -o previews/spectrogram.png
