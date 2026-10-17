#!/usr/bin/env bash
# The one entry point (art style, "Pipeline"): regenerates every asset and fixture, byte for byte.
# Asset tickets append their generator lines here, then their contact sheet into build/previews/.
set -euo pipefail
ART="$(cd "$(dirname "$0")" && pwd)"
python3 "$ART/lib.py"
python3 "$ART/wide_font_pack.py"
