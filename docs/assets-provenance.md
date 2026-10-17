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

## vis

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## arcane

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

## crucible

| Output path | Tool and exact model revision | Licence of the tool's terms, and version | Generator script, or prompt and seed | Processing steps | Date | Human contribution |
|---|---|---|---|---|---|---|

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
