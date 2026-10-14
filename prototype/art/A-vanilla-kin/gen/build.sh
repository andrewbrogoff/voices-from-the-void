#!/usr/bin/env bash
# Regenerates every Vanilla Kin asset and preview. Run from anywhere.
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
GEN="$(cd "$(dirname "$0")" && pwd)"
OPT="$(dirname "$GEN")"
TOOLS="$OPT/../tools"
A="$OPT/assets"
T="$A/vftv/textures"
rm -rf "$A"  # outputs are fully derived; start clean so stale files never linger
mkdir -p "$OPT/previews" "$A/vftv/sounds"

python3 "$GEN/textures.py" "$A"
python3 "$GEN/models.py" "$A"
python3 "$GEN/sound.py" "$A/vftv/sounds/tuning_fork_strike.ogg"

cd "$OPT"
blender -b --factory-startup --python "$TOOLS/render_model.py" -- "$A" vftv:block/essentia_jar previews/essentia_jar.png 512 >/dev/null
python3 "$TOOLS/sheet.py" previews/sheet.png \
  "$T/item/tuning_fork.png" "$T/item/resonant_brass_ingot.png" "$T/item/hollow_bell.png" \
  "$T/block/essentia_jar_side.png" "$T/block/essentia_jar_top.png" "$T/block/essentia_jar_lid.png" \
  --render previews/essentia_jar.png
sox "$A/vftv/sounds/tuning_fork_strike.ogg" -n spectrogram -o previews/spectrogram.png
