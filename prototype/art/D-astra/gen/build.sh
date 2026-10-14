#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
set -euo pipefail
option_dir="$(cd "$(dirname "$0")/.." && pwd)"
repo_dir="$(cd "$option_dir/../../.." && pwd)"
cd "$option_dir"
export PYTHONDONTWRITEBYTECODE=1
export TMPDIR="$option_dir/gen/tmp"
mkdir -p "$TMPDIR" previews assets/vftv/sounds
trap 'rm -r -- "$option_dir/gen/tmp"' EXIT
revisions=(0 1 2)
if [[ "$#" != 0 ]]; then
    [[ "$#" == 2 && "$1" == --revision && "$2" =~ ^[012]$ ]] || { echo 'Usage: build.sh [--revision 0|1|2]' >&2; exit 2; }
    revisions=("$2")
fi
mkdir -p previews/revisions
revision_names=(00-initial 01-openings 02-final)
for revision in "${revisions[@]}"; do
    python3 gen/generate.py --revision "$revision"
    sox -D gen/tmp/strike.wav -C 5 assets/vftv/sounds/tuning_fork_strike.ogg
    python3 gen/verify.py --canonicalize
    blender -b --factory-startup --python "$repo_dir/prototype/art/tools/render_model.py" -- "$option_dir/assets" vftv:block/essentia_jar "$option_dir/previews/essentia_jar.png" 512 > previews/render.log 2>&1
    python3 gen/verify.py --canonicalize-render
    python3 "$repo_dir/prototype/art/tools/sheet.py" previews/sheet.png assets/vftv/textures/item/*.png assets/vftv/textures/block/*.png --render previews/essentia_jar.png
    python3 gen/verify.py
    cp previews/slots.png "previews/revisions/${revision_names[$revision]}.png"
done
sox assets/vftv/sounds/tuning_fork_strike.ogg -n spectrogram -x 1000 -y 513 -z 90 -t 'Opaline Resonance | paired-tine strike | 2.60 s' -o previews/spectrogram.png
sox assets/vftv/sounds/tuning_fork_strike.ogg -n stat 2> previews/sound-stat.txt
python3 gen/present.py
