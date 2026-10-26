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
python3 "$ART/wands.py" "$ASSETS" "$PREVIEWS/wands"
python3 "$ART/sheet.py" "$PREVIEWS/wands.png" "$PREVIEWS"/wands/wand_*.png \
    "$ASSETS/vftv/textures/item/chime_rod.png" "$ASSETS/vftv/textures/item/resonant_brass_cap.png"
python3 "$ART/nodes.py" "$ASSETS"
python3 "$ART/sheet.py" "$PREVIEWS/nodes.png" "$ASSETS/vftv/textures/block/node_core.png" \
    "$ASSETS"/vftv/textures/particle/node_glint_*.png "$ASSETS/vftv/textures/item/bell_jar.png" \
    "$ASSETS/vftv/textures/item/bell_jar_filled.png" "$ASSETS/vftv/textures/block/node_tap.png" \
    "$ASSETS/vftv/textures/block/node_tap_mouth.png" "$ASSETS/vftv/textures/block/vis_relay_fork.png" \
    "$ASSETS/vftv/textures/block/vis_relay_stand.png" "$ASSETS/vftv/textures/block/wand_cradle_base.png" \
    "$ASSETS/vftv/textures/block/wand_cradle_rest.png"
python3 "$ART/crucible.py" "$ASSETS"
CRU="$ASSETS/vftv/textures"
python3 "$ART/sheet.py" "$PREVIEWS/crucible.png" "$CRU/block/crucible_side.png" "$CRU/block/crucible_top.png" \
    "$CRU/block/crucible_inner.png" "$CRU/block/crucible_bottom.png" "$CRU/block/crucible_liquid.png" \
    "$CRU/item/resonant_brass_ingot.png" "$CRU/gui/sprites/hud/crucible_missing.png" "$CRU/gui/sprites/aspect/unheard.png"
python3 "$ART/research_items.py" "$ART/../.."
python3 "$ART/sheet.py" "$PREVIEWS/research.png" "$ASSETS/vftv/textures/item/commonplace_book.png" \
    "$ASSETS/vftv/textures/item/choir_page.png"
python3 "$ART/essentia.py" "$ASSETS"
python3 "$ART/sheet.py" "$PREVIEWS/essentia.png" "$ASSETS/vftv/textures/block/essentia_jar_glass.png" \
    "$ASSETS/vftv/textures/block/essentia_jar_lid.png" "$ASSETS/vftv/textures/block/essentia_jar_liquid.png" \
    "$ASSETS/vftv/textures/block/essentia_label.png" "$ASSETS/vftv/textures/block/essentia_tube_arm.png" \
    "$ASSETS/vftv/textures/block/essentia_tube_core.png" "$ASSETS/vftv/textures/block/essentia_tube_cap.png" \
    "$ASSETS/vftv/textures/block/essentia_tube_band.png" "$ASSETS/vftv/textures/block/essentia_tube_band_ring.png" \
    "$ASSETS/vftv/textures/block/tube_valve.png" "$ASSETS/vftv/textures/block/tube_valve_wheel_open.png" \
    "$ASSETS/vftv/textures/block/tube_valve_wheel_closed.png" "$ASSETS/vftv/textures/block/alembic_glass.png" \
    "$ASSETS/vftv/textures/block/alembic_brass.png" "$ASSETS/vftv/textures/item/label.png" \
    "$ASSETS/vftv/textures/item/label_written.png"
python3 "$ART/worktable.py" "$ASSETS"
WT="$ASSETS/vftv/textures/gui/sprites"
python3 "$ART/sheet.py" "$PREVIEWS/worktable.png" "$ASSETS/vftv/textures/block/worktable_top.png" \
    "$ASSETS/vftv/textures/block/worktable_side.png" "$ASSETS/vftv/textures/block/worktable_bottom.png" \
    "$WT/worktable/preview_idle.png" "$WT/worktable/preview_ready.png" "$WT/worktable/preview_hover.png" \
    "$WT/worktable/preview_disabled.png" "$WT/worktable/cost_cell.png" "$WT/worktable/cost_cell_short.png" \
    "$WT/worktable/beyond.png" "$WT/container/slot/wand.png"
python3 "$ART/infusion.py" "$ASSETS" "$PREVIEWS/infusion"
INF="$ASSETS/vftv/textures"
python3 "$ART/sheet.py" "$PREVIEWS/infusion.png" "$INF/block/singing_bowl_side.png" "$INF/block/singing_bowl_top.png" \
    "$INF/block/singing_bowl_inner.png" "$INF/block/singing_bowl_bottom.png" "$INF/block/pedestal_side.png" \
    "$INF/block/pedestal_top.png" "$INF/block/pedestal_bottom.png" "$INF/item/echo_rod.png" "$INF/item/echo_cap.png" \
    "$PREVIEWS"/infusion/wand_*.png
python3 "$ART/choir_ruin.py" "$ART/../../content" "$PREVIEWS/choir_ruin"
python3 "$ART/reliquary.py" "$ASSETS"
python3 "$ART/answerers.py" "$ASSETS" "$ART/answerers"
WG="$ASSETS/vftv/textures"
python3 "$ART/sheet.py" "$PREVIEWS/worldgen.png" "$WG/block/reliquary_side.png" "$WG/block/reliquary_front.png" \
    "$WG/block/reliquary_top.png" "$WG/block/reliquary_bottom.png" "$WG/entity/answerer.png" \
    "$WG/entity/answerer_cantor.png"
python3 "$ART/warp.py" "$ASSETS" "$ART/warp"
WP="$ASSETS/vftv/textures"
python3 "$ART/sheet.py" "$PREVIEWS/warp.png" "$WP/item/stilling_draught.png" "$WP/entity/apparition.png" \
    "$WP/gui/sprites/hud/voice_text.png"
python3 "$ART/hush_blocks.py" "$ASSETS"
python3 "$ART/hush.py" "$ASSETS" "$ART/hush"
HU="$ASSETS/vftv/textures"
python3 "$ART/sheet.py" "$PREVIEWS/hush.png" "$HU"/block/hushed_*.png "$HU"/particle/hush_mote_*.png \
    "$HU/item/clearing_bell.png" "$HU/item/hush_ash.png" "$HU/block/pealing_bell_yoke.png" \
    "$HU/block/pealing_bell_bell.png" "$HU/gui/sprites/hud/hush_frame.png" "$HU/entity/hollow.png"
