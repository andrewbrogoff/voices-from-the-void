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

## arcane

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

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

## infusion

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

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

## road

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|
