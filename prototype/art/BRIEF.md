# Art pipeline prototype: shared brief

Ticket: "Art pipeline proof and style guide" (`gh issue view 13`). Map: `gh issue view 1`.
Goal: prove that original assets can be made at the quality bar with tools on this machine, and give the owner several **art directions** to choose between. Each option is a complete, comparable sample set plus a proposed style guide.

## Read first
- `docs/spec/lore.md` (tone ramp, acoustic-only imagery, the Hush, naming), `docs/spec/progression.md` (Stages), `CONTEXT.md`.
- `voices_from_the_void.png` and `assets/icon.svg`: the kept branding. Ink navy, brass gold, teal resonance arcs, slate mauve.
- Asset tooling research: `git show origin/research/asset-tooling:docs/research/asset-tooling.md` (§6 pipeline per asset type).
- `NOTICE.md`: assets are CC-BY-4.0, and generator scripts are MPL-2.0.

## Hard rules
- **Clean room.** Never open, unpack or look at anything Thaumcraft: not `Mods/`, not `MC-instances/`, not wiki images, nothing. You may *look at* vanilla 26.2 textures for a sense of scale and convention (`~/.gradle/caches/neoformruntime/artifacts/minecraft_26.2_client.jar`), but never copy, trace or recolour them.
- **Code-authored and reproducible.** Every texture comes from a seeded generator script you write (Python 3 + Pillow + numpy, all installed). Every sound comes from a script (sox 14.4 and/or numpy synthesis). Re-running the scripts must reproduce the outputs byte for byte. No AI image or audio models (none on this machine are licence-clean).
- **Acoustic imagery only:** tuning forks, bells, glass, brass. No radio, antennae, telescopes or waveforms-on-screens.
- **Work only inside your option folder** `prototype/art/<option>/`. Don't touch other options, the tools, git, or GitHub.

## The sample set (same for every option)
| Asset | Stage | What it is |
|---|---|---|
| `tuning_fork` item | 1, Resonance | The signature tool. A copper fork with an amethyst shard set in it. Made from 1 copper ingot + 1 amethyst shard. |
| `resonant_brass_ingot` item | 2, Distillation | A brass alloy made from copper that *rings*. It must read as distinct from vanilla copper and gold ingots. |
| `hollow_bell` item | 4, the Choir's Road | A Void-touched bell: the tone ramp at its far end (cosmic horror). The same family as the others, but the Void has got into it. (Placeholder name.) |
| `essentia_jar` block | 2 | A glass jar with a lid, smaller than a full block (a non-cube model, e.g. about 10×12×10 px), translucent glass. It must read clearly in an inventory slot as well as in the world. |
| `tuning_fork_strike` sound | 1 | Striking the Tuning Fork, the most frequent sound in the mod. 1.5 to 3 s, so it must not grate on the 500th hearing. |

## Deliverables in `prototype/art/<option>/`
```
gen/                         generator scripts (one entry point: gen/build.sh, regenerates everything)
assets/vftv/textures/item/   tuning_fork.png, resonant_brass_ingot.png, hollow_bell.png
assets/vftv/textures/block/  essentia_jar textures (any you need)
assets/vftv/models/block/essentia_jar.json      26.2 Java block model (elements; no parent needed)
assets/vftv/blockstates/essentia_jar.json       {"variants": {"": {"model": "vftv:block/essentia_jar"}}}
assets/vftv/models/item/<item>.json             {"parent": "minecraft:item/generated", "textures": {"layer0": "vftv:item/<item>"}}
assets/vftv/items/<id>.json                     {"model": {"type": "minecraft:model", "model": "vftv:item/<id>"}} (26.2 syntax; jar uses the block model)
assets/vftv/sounds/tuning_fork_strike.ogg       mono, 44.1 kHz, Vorbis; peak at or below -1 dBFS
previews/sheet.png        python3 prototype/art/tools/sheet.py previews/sheet.png <the item and block textures> --render previews/essentia_jar.png
previews/essentia_jar.png blender -b --factory-startup --python prototype/art/tools/render_model.py -- prototype/art/<option>/assets vftv:block/essentia_jar previews/essentia_jar.png 512
previews/spectrogram.png  sox <ogg> -n spectrogram -o previews/spectrogram.png
STYLE.md                  the proposed style guide (below)
```

## STYLE.md must cover
1. **One-paragraph pitch** of the direction, and why it fits the lore's tone ramp.
2. **Palette:** every hex in the shared palette, grouped into named ramps (material ramps and accent ramps). State whether the palette is locked project-wide or per material.
3. **Resolution and pixel rules:** texture size; outline rule (none, darker-of-material, or ink); light direction; how many shades per ramp; dithering (allowed or not, and where); anti-aliasing; how much a sprite may fill the 16×16 canvas.
4. **Silhouette language:** what makes a VFTV item recognisable at a glance, and how it differs from vanilla.
5. **Tone ramp by Stage:** how colour, saturation, accents and noise shift from Stage 1 (Resonance) to Stage 4 (the Choir's Road), shown by the three items. Also how the Hush looks (desaturated and silent; never purple goo).
6. **Blocks and models:** texture and model conventions (element budget, glass and translucency, UV layout).
7. **Sound palette:** sources (synthesis method), processing chain, the loudness and length conventions, how sounds shift by Stage, and how variants are made (pitch and seed).
8. **How a build agent applies this:** the generator-library shape (e.g. a palette module plus helpers) so 200 more assets stay consistent.

## Quality loop (required)
- **Look at your own previews** (Read the PNG files) and iterate until you're satisfied. Do at least two revision passes on the textures and note what changed. Judge each item at slot size (GUI scale 2), not only enlarged.
- For the sound, you can't listen, so check the spectrogram and the stats (`sox <ogg> -n stat`) for clipping, DC offset, harsh high-frequency content above about 10 kHz, and a clean decay to silence (no click at the end).
- Finish with a short `NOTES.md`: what worked, what fought you, and a frank self-critique (weakest asset, and why).
