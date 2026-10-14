# Option A: Vanilla Kin, proposed style guide

## 1. Pitch

VFTV should look as if Mojang shipped it: an item from this mod sitting in a chest next to a copper ingot or a brush should not look foreign. Every texture follows vanilla's own conventions (16×16, hue-shifted ramps per material, top-left light, edges in the material's darkest shade, small and restrained accents). The mod's identity comes from what it depicts (forks, bells, glass, brass) and from how the material ramps drift across the Stages, not from a house rendering style. This fits the tone ramp because the mod starts as calm, believable natural philosophy. Stage 1 is plain worked copper and a vanilla mineral, so nothing looks occult yet. The Void arrives by subtraction: colour drains out, accents go cold and quiet, and by Stage 4 the cosmic horror comes from absence (a hollow, a crack, a pale glint in the dark) rather than from purple goo.

## 2. Palette

The palette is **per material, not locked project-wide**, as in vanilla. Each material gets one ramp, ordered darkest (0) to lightest. Every ramp is produced by `ramp()` in `gen/palette.py`, so the rules below are code, not taste.

| Ramp | Kind | Shades (0 → n) |
|---|---|---|
| `copper` | material, Stage 1 | `#4c1c14 #793321 #9c4d32 #bd6f4c #dc9a75 #facfb0` |
| `amethyst` | accent, Stage 1 | `#332b66 #5b4295 #8b60bb #c190de #f8daff` |
| `brass` | material, Stage 2 | `#45361a #725e2c #968240 #b7a75b #d6cd81 #f5f2b5` |
| `ring` | accent, Stage 2 (resonance glint) | `#3e6a6b #61a09e #8ecbc5 #d4f2ed` |
| `glass` | material, any Stage | `#76838c #94a5b0 #b0c2cc #cddfe6 #effbff` |
| `hushed` | material, Stage 3–4 (drained bronze; also the Hush) | `#292624 #544e4a #766f69 #968f86 #b4afa4 #d1cdc3` |
| `void` | accent, Stage 4 | `#0c1214 #2e444b #4f7176 #7b9d9e` |

How a ramp is built: `ramp(hue, sat, n, shift, lo, hi, sat_hi)`. Hue travels `shift` degrees across the ramp (darks one way, lights the other: warm metals go redder in shadow and yellower in light, and amethyst goes bluer in shadow and pinker in light). Saturation is full in the darks and mid-tones and falls to `sat_hi`× at the highlight. Value eases from `lo` to `hi`. **Distinctness rule:** a new metal must differ from vanilla's nearest metal in hue *and* saturation. Brass is olive-khaki (hue about 48°, saturation 0.62) against vanilla gold's saturated yellow-orange, and copper's red-orange.

## 3. Resolution and pixel rules

- **Size:** 16×16 for items and block faces. Nothing larger.
- **Outline:** none. A silhouette edge uses shade 0–1 of its own material on the shadow side (bottom and right), and shade 2–3 on the lit side. Never black, and never another material's colour.
- **Light:** from the top left. Lit faces and left-facing edges take the upper shades, and right and under faces take the lower ones. A 2-px diagonal line (a tine or stem) is `light, dark`: the upper-left pixel uses shade 4 and the lower-right pixel shade 2.
- **Shades:** 4–6 per material ramp and 3–5 per accent ramp. Use at most 2 ramps per item, plus at most one accent ramp of 1–4 pixels.
- **Dithering:** not allowed on items. On blocks it is allowed only as sparse single-pixel speckle in natural materials (stone, ore), never as a gradient.
- **Anti-aliasing:** none. Alpha is either 0 or 255 on items. Partial alpha is used only for glass panes (64 for the body, 225 for the edges).
- **Fill:** sprites span 12–15 px on their long axis, keep at least 1 px of margin on the sides, and sit on the visual centre. Tools and long items run diagonally from bottom-left to top-right, as vanilla tools do. Compact objects (ingots, bells) are drawn front-on or in a slight top-down view.
- **Clusters:** no lone pixel of a mid shade inside a flat area. Highlights are short 1–3 px runs, not speckles. Accents are the only single pixels allowed, and they are deliberate.

## 4. Silhouette language

A VFTV item reads as **an instrument or a material of sound**. The three recurring shapes are the *open U* (a fork's tines), the *flare* (a bell's lip and a jar's shoulder) and the *ring-groove* (a band cut round a bar or a neck). Where vanilla has an obvious twin, VFTV turns the object off-axis on purpose. Vanilla ingots lie diagonally, so Resonant Brass lies level and is pinched at the waist by its groove. Vanilla's bell is a squat front-on block on a beam, so the Hollow Bell is a tall hand-bell with a crown loop and a visible mouth. Accents sit at a *junction* (the fork's crotch, the groove's edge, the bell's mouth), never floating in the middle of a face.

## 5. Tone ramp by Stage

| Stage | Material | Saturation | Accent | Noise |
|---|---|---|---|---|
| 1 Resonance (Tuning Fork) | warm copper, full vanilla saturation | high | amethyst: vanilla's own mineral, 4 px | none |
| 2 Distillation (Resonant Brass, Essentia Jar) | olive brass, glass | medium | `ring` teal, one bright glint pixel (the metal *rings*) | none |
| 3 Ruins | brass and copper start to grey towards `hushed` | falling | `ring` gets rarer, `void` appears in seams | none |
| 4 The Choir's Road (Hollow Bell) | `hushed` drained bronze, near-neutral | very low (≤0.12) | `void`: near-black sea-teal in hollows and cracks, one pale pixel | a single crack line, never blotches |

**The Hush** uses the same subtraction on the world. A Hushed block is its vanilla-kin ramp with saturation scaled towards that of `hushed` and its value range compressed slightly, so it reads as quiet and grey rather than tinted. Hollows and seams take `void` 0–1. It never uses purple, magenta or any organic shapes. The closest vanilla reference is sculk handled with restraint: dark, cold, mostly still.

## 6. Blocks and models

- **Element budget:** up to 6 elements for a decorative block and up to 12 for a machine. Every element is axis-aligned and on whole pixels. Rotations are used only for things that hang or lean.
- **Essentia Jar:** 4 elements: a 10×9×10 glass body, a 6×1×6 glass neck, an 8×2×8 brass lid and a 4×1×4 knob (13 px tall). It uses `parent: minecraft:block/block` for the standard display transforms, with `ambientocclusion: false` as for lanterns.
- **Glass:** a 1-px frame of `glass` 3 (top and left) and `glass` 1 (bottom and right) at alpha 225, with the body as `glass` 4 at alpha 64. The pane corners are clipped to transparent so a box reads as rounded. Panes larger than 6 px get one 1-px vertical highlight streak at the upper left. The semi-transparent pixels make the block render translucent; confirm the 26.2 render-type rule (or `force_translucent`) once the mod skeleton exists.
- **UV layout:** one texture per material-surface (`_side`, `_top`, `_lid`). Each face's region starts at the top-left of the texture, and the four sides of an element share one region. Small trim parts (the knob) pack into the unused right half of the same material's texture. Every UV is explicit in the JSON.

## 7. Sound palette

- **Source:** numpy modal synthesis. Each sound is a list of `(frequency ratio, amplitude, decay τ)` modes on an exponential envelope, plus a few milliseconds of band-limited, seeded noise for the excitation (strike, scrape, tap). Real instrument ratios: tuning fork 1 : 6.2 : 17.4, bell (minor-third bell) 0.5 : 1 : 1.2 : 1.5 : 2, and glass close to harmonic with a long τ.
- **Chain:** synth → seeded TPDF-dithered 16-bit WAV → sox `reverb 18 65 22` (small room, mostly dry) → `lowpass 9000` → `highpass 60` → fade-out to true zero → `gain -n -1.5` → mono 44.1 kHz Vorbis q5. Use `sox -R` so the output is byte-identical on every run.
- **Loudness and length:** peak −1.5 dBFS. A UI or tool sound lasts at most 3 s, and its tail must reach digital silence. Nothing above about 10 kHz is louder than −90 dB, and the onset ramps in over at least 1.5 ms so there is no click.
- **By Stage:** Stage 1–2 sounds are bright and pure, with clear inharmonic partials and a small room. Stage 3 sounds get a longer reverb and slower decays. Stage 4 sounds have their upper partials removed (the Hush takes the brightness first), keep the fundamental with a slow beat between two near-unison modes, and end early, so the silence after them is part of the sound.
- **Variants:** vary only `--f0` (±1 semitone across variants, or let Minecraft's own pitch field do it) and `--seed`, which changes only the strike noise. Never vary the mode ratios, because they are the instrument's identity.

## 8. How a build agent applies this

```
gen/palette.py    ramp() + RAMPS: the only place colours exist. New material = one new ramp() line.
gen/textures.py   sprite(rows, legend): a 16-line character grid; digits = main material shade,
                  letters = accent ramps. glass_panel() for any pane. Items are authored as grids,
                  not drawn procedurally, because 16x16 needs per-pixel decisions.
gen/models.py     box(from, to, (texture, {face: uv}) ...) + side()/caps(); writes models,
                  blockstates and 26.2 item definitions.
gen/sound.py      synth(f0, seed) with a mode table; one sox chain for every sound.
gen/build.sh      regenerates everything from scratch (assets/ is deleted first) and the previews.
```
To add an asset: (1) pick or add a ramp; (2) draw the grid using digits 0–5 for one material and letters for at most one accent; (3) apply the light rule (upper-left pixel of each run lighter) and the edge rule (bottom and right edges shade 0–1); (4) run `build.sh` and judge `previews/sheet.png` at the GUI ×2 slot first. If the item reads as its vanilla twin, change the silhouette, not the colour.
