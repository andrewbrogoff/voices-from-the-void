# Asset provenance

One row per shipped asset, or per batch made by one generator script. Required by the [licensing notice](../NOTICE.md) and the [quality gates](spec/quality-gates.md) definition of done. Clean room: nothing here may come from Thaumcraft's code or assets.

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `assets/icon.svg`, `voices_from_the_void.png` | kept project branding | reserved (see Branding in NOTICE) | n/a | n/a | before 2026-10-14 | owner |

Each feature appends its rows under its own heading below, never another's, so parallel tickets don't conflict.

## flux

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## aspect

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `src/main/resources/assets/vftv/textures/gui/sprites/aspect/*.png` (37 aspects and `unheard`), `tools/art/aspect_identity.json` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/aspects.py` (letter grids per the aspects spec's glyph briefs; `machina` procedural); deterministic, no seed | `lib.outline()`, palette lock in `lib.save()`; greyscale contact sheet `build/previews/aspects.png` reviewed at GUI scale 1 and 4 | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/item/tuning_fork.png`, `tuning_fork_ringing.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/fork.py`, from the chosen prototype `prototype/art-pipeline:prototype/art/B-ink-and-brass/gen/textures.py` `tuning_fork()` | `lib.outline()`, palette lock; contact sheet `build/previews/fork.png` beside the vanilla-style slot | 2026-10-17 | owner chose the prototype direction (art style §1) |
| `src/main/resources/assets/vftv/textures/gui/sprites/hud/vis_frame.png` (+ datagen `.mcmeta`, nine-slice border 4) | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/fork.py` `card_frame()` | palette lock; interior opaque `ink1`, checked by `AccessibilityGatesTest` | 2026-10-17 | none (agent-authored) |

## warp

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## research

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `src/main/resources/assets/vftv/textures/item/commonplace_book.png`, `choir_page.png` | Python 3 + Pillow/numpy (no model) | MPL-2.0 (our generator) | `tools/art/research_items.py` (deterministic, no seed) | `lib.save()` palette check; contact sheet reviewed at GUI scale 2 | 2026-10-17 | none (agent-written code) |

## vis

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `src/main/resources/assets/vftv/textures/item/wand/rod/{wooden,chime,unknown}.png`, `…/wand/cap/{copper,resonant_brass,unknown}.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/wands.py` (shared rod and cap silhouettes, outline split by layer); deterministic, no seed | palette lock in `lib.save()`; every rod × cap composite on the contact sheet `build/previews/wands.png`, reviewed at GUI scale 2 and 3 with greyscale and CVD previews | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/item/chime_rod.png`, `resonant_brass_cap.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/wands.py` `chime_rod_icon()`, `resonant_brass_cap_icon()` | outline, palette lock; Stage 2 inlaid teal pip; contact sheet `build/previews/wands.png` | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/block/node_core.png` (16×128, 8 frames) | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/nodes.py` `core_strip()` | cutout, palette lock; flash test asserted in the generator (adjacent frames differ < 0.10 in mean luminance); contact sheet `build/previews/nodes.png` | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/particle/node_glint_{0..3}.png` (8×8) | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/nodes.py` `glint()` | palette lock; contact sheet `build/previews/nodes.png` | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/item/bell_jar.png`, `bell_jar_filled.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/nodes.py` `bell_jar()`; deterministic, no seed | `lib.outline()` once, palette lock, no partial alpha; empty and filled differ by the teal orb's shape in greyscale; contact sheet `build/previews/nodes.png` at GUI scale 2 with greyscale and CVD previews | 2026-10-26 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/block/node_tap.png`, `node_tap_mouth.png`, `vis_relay_fork.png`, `vis_relay_stand.png`, `wand_cradle_base.png`, `wand_cradle_rest.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/nodes.py` (painted in block space for position-derived UVs on the datagen models of at most six cuboids, `VisClientData.relayMembers`); deterministic, no seed | palette lock in `lib.save()`; Stage 2 inlaid teal pip on the tap's mouth and the relay's tine tips; contact sheet `build/previews/nodes.png` | 2026-10-26 | none (agent-authored) |

## arcane

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `src/main/resources/assets/vftv/textures/block/worktable_top.png`, `worktable_side.png`, `worktable_bottom.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/worktable.py` (Stage 1 boards in a crafting table's frame; inlaid fork and chime bar, hanging fork and striker; teal tick outside every object on the top); deterministic, no seed | palette lock in `lib.save()`; contact sheet `build/previews/worktable.png` at GUI scale 2 and 3, greyscale and CVD previews | 2026-10-18 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/gui/container/worktable.png` (176×220 on 256×256) | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/worktable.py` `background()` (a well at every menu slot; title and inventory strips uniform `brass4`, status rectangle uniform `brass5`) | palette lock; text regions checked by `AccessibilityGatesTest` | 2026-10-18 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/gui/sprites/worktable/preview_{idle,ready,hover,disabled}.png`, `cost_cell.png`, `cost_cell_short.png`, `beyond.png`, `…/sprites/container/slot/wand.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/worktable.py` (preview frames distinct by ring, brightness and hatch; the short cell's 3×3 `ink1` notch) | palette lock; luma variant check and the number field's and count corner's contrast checked by `AccessibilityGatesTest`; contact sheet `build/previews/worktable.png` | 2026-10-18 | none (agent-authored) |

## crucible

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `src/main/resources/assets/vftv/textures/block/crucible_side.png`, `crucible_top.png`, `crucible_inner.png`, `crucible_bottom.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/crucible.py` (Stage 2 brass-banded vessel, teal pip inlaid in the top's north rim); deterministic, no seed | palette lock in `lib.save()`; contact sheet `build/previews/crucible.png` at GUI scale 2 and 3, greyscale and CVD previews | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/block/crucible_liquid.png` (+ datagen `.mcmeta`, 4 frames of 5 ticks, no interpolation) | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/crucible.py` `crucible_liquid()` (16x64, 4 frames of `glass1` with an expanding `hush3` ring, α 235, tinted at runtime); deterministic, no seed | palette lock; `.mcmeta` written by `CrucibleData` datagen (frametime 5, no interpolation); flash review on the contact sheet (frame mean luminances 0.649–0.661) | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/item/resonant_brass_ingot.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/crucible.py`, from the chosen prototype `prototype/art-pipeline:prototype/art/B-ink-and-brass/gen/textures.py` `resonant_brass_ingot()`, unchanged | `lib.outline()`, palette lock; contact sheet `build/previews/crucible.png` | 2026-10-17 | owner chose the prototype direction (art style §1) |
| `src/main/resources/assets/vftv/textures/gui/sprites/hud/crucible_missing.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/crucible.py` `missing()` (a broken, struck-through ring in the `hush` family, drawn by `aspects.py` `draw()`); deterministic, no seed | `lib.outline()`, palette lock; luma-distinct from `unheard`, checked by `AccessibilityGatesTest` | 2026-10-17 | none (agent-authored) |

## essentia

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `src/main/resources/assets/vftv/textures/block/essentia_jar_glass.png`, `essentia_jar_lid.png`, `essentia_jar_liquid.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/essentia.py`, from the chosen prototype `prototype/art-pipeline:prototype/art/B-ink-and-brass/gen/textures.py` `jar()`; deterministic, no seed | palette lock in `lib.save()`; §6 glass recipe; contact sheet `build/previews/essentia.png` beside the vanilla-style slot | 2026-10-17 | owner chose the prototype direction (art style §1) |
| `src/main/resources/assets/vftv/textures/block/essentia_label.png`, `essentia_tube_arm.png`, `essentia_tube_core.png`, `essentia_tube_cap.png`, `essentia_tube_band.png`, `essentia_tube_band_ring.png`, `tube_valve.png`, `tube_valve_wheel_open.png`, `tube_valve_wheel_closed.png`, `alembic_glass.png`, `alembic_brass.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/essentia.py`; deterministic, no seed | palette lock in `lib.save()`; contact sheet `build/previews/essentia.png` reviewed at GUI scale 2, with greyscale and CVD previews (the valve wheels differ by spoke orientation and hub) | 2026-10-17 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/item/label.png`, `label_written.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/essentia.py` `label_item()`; deterministic, no seed | `lib.outline()`, palette lock, no partial alpha; contact sheet `build/previews/essentia.png` | 2026-10-17 | none (agent-authored) |

## infusion

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `src/main/resources/assets/vftv/textures/block/singing_bowl_side.png`, `singing_bowl_top.png`, `singing_bowl_inner.png`, `singing_bowl_bottom.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/infusion.py` (Stage 3 brass bowl on a deepslate plinth, mauve shadows, teal1 groove); deterministic, no seed | palette lock in `lib.save()`; contact sheet `build/previews/infusion.png` at GUI scale 2 and 3, greyscale and CVD previews | 2026-10-26 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/block/pedestal_side.png`, `pedestal_top.png`, `pedestal_bottom.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/infusion.py` (polished deepslate column, brass top plate, teal1 groove); deterministic, no seed | palette lock in `lib.save()`; contact sheet `build/previews/infusion.png` at GUI scale 2 and 3, greyscale and CVD previews | 2026-10-26 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/item/echo_rod.png`, `echo_cap.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/infusion.py` (upright flared rod and squat cup, Stage 3 shading); deterministic, no seed | `lib.outline()`, palette lock in `lib.save()`; contact sheet `build/previews/infusion.png` at GUI scale 2 and 3, greyscale and CVD previews | 2026-10-26 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/item/wand/rod/echo.png`, `wand/cap/echo.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/infusion.py`, on the shared rod and cap silhouettes of `tools/art/wands.py`; composites of every Echo wand in `build/previews/infusion/` | palette lock in `lib.save()`; contact sheet `build/previews/infusion.png` at GUI scale 2 and 3, greyscale and CVD previews | 2026-10-26 | none (agent-authored) |

## focus

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## golem

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## armor

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## hush

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## worldgen

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
| `content/worldgen/templates/chancel_a.json`, `chancel_b.json`, `cloister.json`, `bell_pit.json`, `scriptorium.json`, `fallen_nave.json`, `rubble_a.json`, `rubble_b.json` (and their datagen output `src/generated/resources/data/vftv/structure/choir_ruin/*.nbt`) | Python 3 (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/choir_ruin.py`: seeded per template (`random.Random(crc32(name))`), byte-identical on re-run; `rubble_a` is the spec's example file exactly | the build's `vftv:template/1` converter validates every rule and writes the NBT through vanilla's `StructureTemplate` and `NbtIo`; isometric review sheets with standing cells marked in `build/previews/choir_ruin/` | 2026-10-26 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/block/reliquary_side.png`, `reliquary_front.png`, `reliquary_top.png`, `reliquary_bottom.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/reliquary.py` (Stage 3: mauve-stone plinth, brass-bound page box, teal1 groove, ink page slot); deterministic, no seed | palette lock in `lib.save()`; contact sheet `build/previews/worldgen.png` at GUI scale 2 and 3, greyscale and CVD previews | 2026-10-26 | none (agent-authored) |
| `src/main/resources/assets/vftv/textures/entity/answerer.png`, `answerer_cantor.png` | Python 3 + Pillow + numpy (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/answerers.py` (64 × 64 box UVs: mauve cloth and hood, faces in the ink outline, backwards brass mouthpiece, no teal; the Cantor's amethyst sash and cracked bell); deterministic, no seed | palette lock in `lib.check()`; contact sheet `build/previews/worldgen.png` | 2026-10-26 | none (agent-authored) |
| `tools/art/answerers/answerer.bbmodel`, `tools/art/answerers/answerer_uv.png` | Python 3 + Pillow (code-authored, no model) | MPL-2.0 generator; outputs CC-BY-4.0 (NOTICE.md) | `tools/art/answerers.py`: the spec's frozen parts, pivots, cuboids, UV origins and mirroring, with the idle, walk, attack and chant poses; a labelled UV guide at 8× | none | 2026-10-26 | none (agent-authored) |

## road

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
