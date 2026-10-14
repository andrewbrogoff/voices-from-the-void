# Option C: Scholar's Engraving (proposed style guide)

## 1. Pitch

Every VFTV object is a figure from an engraved plate in an 18th-century treatise on natural philosophy that has come to life. It is drawn upright on the plate's centre line, outlined in sepia ink, and shaded with a few disciplined engraver's lines. The palette is warm and low in saturation: aged brass, verdigris copper, smoky glass and sepia shadow. It fits the lore's tone ramp because the ramp can be shown through the *engraving itself*. At Stage 1 the plate is crisp and warm, the instruments of a curious scholar. Through Stage 2 the cool verdigris of resonance creeps into the shadows. By Stage 4 the lines start to fail: hatching drops out, the outline breaks, ink pools and drips, and where the Hush has got in, the colour simply drains away. The plate is going silent. Horror arrives as *absence*, never as goo.

## 2. Palette (locked project-wide)

One table in `gen/engrave.py` (`RAMPS`). No hex outside it may appear in any texture. Ramps run from dark to light. Materials pick a ramp; they don't get private colours.

| Ramp | Kind | Hexes |
|---|---|---|
| `ink` | line | `#211915` `#3b2d25` (sepia, never black) |
| `copper` | material, Stage 1 | `#4f2d22` `#7c4634` `#a6664b` `#c98e6a` `#e6c09b` |
| `brass` | material, Stage 2+ (Resonant Brass, lids, fittings) | `#3d3419` `#6b5e33` `#958650` `#bfb07a` `#e2d8a8` |
| `tarnish` | material, Stage 4 brass | `#3a3122` `#62563d` `#8a7c5c` `#b0a37f` `#d3c9a8` |
| `glass` | material (alpha set per use) | `#2b302e` `#545e5a` `#8d9893` `#d4dbd2` |
| `verdigris` | accent: patina, resonance, essentia | `#2f4a44` `#4f7569` `#7ea595` `#b6cfbf` |
| `amethyst` | accent: the only "bright" note | `#3c2a42` `#664c73` `#977aa3` `#d0bdd4` |
| `hush` | state: the Hush (no hue) | `#1c1b1c` `#3a393a` `#5f5e5c` `#8b8985` `#b9b6b0` |
| `void` | state: silence | `#0e0d0f` |

Glass alpha steps: body 70, edges and highlight 150, rim 200, base 210. Liquids use 235. Branding link: the brass and verdigris ramps are the logo's brass gold and teal arcs, aged. Amethyst is the logo's slate mauve.

## 3. Resolution and pixel rules

- **16×16** items and block faces. No HD textures, no animation for these samples.
- **Outline: ink.** Every silhouette gets a 1 px sepia `ink` line. On the lit edges (top and left), the line is *thinned toward the light*: it becomes the material ramp's darkest shade (`q` in the maps). Interior lines are ink only where they are structural (the gap between tines, a gem setting).
- **Light** comes from the top left, always.
- **Shades:** 5 per metal ramp and 4 per accent or glass ramp. A sprite uses at most 3 ramps plus ink.
- **Hatching is the texture language, and it is rationed.** There is one house pattern: 1 px `/` diagonal lines with a period of 3 (`is_line`). Use it only in mid-shadow (`h` = shade 1 over shade 2) and deep shadow (`H` = shade 0 over shade 1). Core shadows and areas 1 px wide stay flat. Never cross-hatch, and never use a checkerboard: both turn to mush at GUI scale 2. Never hatch behind glass.
- **Ordered dithering** (Bayer 4×4) is allowed in exactly one place: the edge of the Hush. Never use it for ordinary shading.
- **No anti-aliasing.** Glass partial alpha is the only soft edge.
- **Fill:** a sprite fills 12 to 15 px on its long axis and is centred on the plate's centre line.

## 4. Silhouette language

VFTV items are **specimens, not tools in use**. They stand upright or front-on, symmetric where the object is, like figures on a plate. Vanilla draws them on a diagonal. So the Tuning Fork stands vertical, not as a 45° tool, and the Resonant Brass ingot is a front-on cast bar with a bevelled top face and a stamped verdigris ring hallmark, not vanilla's diagonal ingot. Every instrument has one small **accent jewel** (amethyst or verdigris) at its acoustic heart: the fork's yoke, the ingot's hallmark, the jar's ripple. At slot size, a VFTV item reads as a muted, ink-lined object with one cool spark.

## 5. Tone ramp by Stage

| | Stage 1, Resonance (Tuning Fork) | Stage 2, Distillation (ingot, jar) | Stage 4, the Choir's Road (Hollow Bell) |
|---|---|---|---|
| Material | warm copper, full ramp | olive brass, cooler | `tarnish`: brass half-drained, the brightest shade dropped |
| Accent | amethyst spark | verdigris spreads into the shadows and the hallmark | none: the accent is replaced by `void` |
| Lines | crisp, complete | complete, more hatching | hatch lines drop out at random (45 %); the outline breaks where the Hush is |
| Ink | contained | contained | pools on the lip and drips past the silhouette |
| Hush | — | — | a ragged region where hue goes to `hush`, hatching vanishes and outline pixels disappear |

**The Hush** maps each material shade to the `hush` shade with the *same index* (`to_hush`), so form is kept and colour is not. Its edge is ordered-dithered, never blurred. Hush areas never carry hatching, because silence has no lines. `void` is flat: no lines, no highlights, ever. There is never any purple and nothing organic.

## 6. Blocks and models

- **Element budget:** at most 6 per block. The jar uses 4: liquid, glass body, lid and knob, in about 10×13×10 px.
- **Explicit UVs** in every face. Each block owns a few small textures (`<block>_<part>.png`). Each texture packs a part's side and top in non-overlapping regions, and a comment in the generator states each region.
- **Glass** is a translucent texture (alpha steps as in §2). Its edges are denser glass, not ink, with one highlight streak on the left and at most one sparse reflection line. A glass top face that is covered by a lid is transparent in the middle so it can't z-fight. The block must be registered with a **translucent render layer** in code (the loader seam), because vanilla JSON can't carry that.
- **Contents** go in an inner element 1 px inside the glass, with flat colour bands and no hatching. The top surface carries a small standing ripple (resonance) as the content's accent.
- Models use `"parent": "minecraft:block/block"` for the display transforms. Small blocks override `gui` scale (the jar uses 0.75) so they fill the slot.

## 7. Sound palette

- **Source:** additive synthesis in numpy (`gen/sound.py`). Fork modes are a fundamental plus the 6.27× "clang" mode. The felt strike is a 7 ms raised-cosine attack, with the clang at -29 dB and τ 45 ms. The **resonator box** is the same pitch with a delayed bloom (τ 80 ms rise, 0.9 s decay), 0.06 % detune for a slow warm beat, and a quiet 2nd harmonic. The **wood/tabletop knock** uses damped modes at 118, 186 and 305 Hz. Felt contact noise is 5 ms of seeded noise through a one-pole lowpass at 900 Hz.
- **Chain** (sox, `-R` for reproducibility): highpass 45 Hz, lowpass 5 kHz, then a small damped room (`reverb 28 70 35 100 12 -4`). After that, trim to length, apply a 0.9 s half-sine fade to true silence, normalise to -1.5 dBFS, and encode Vorbis q5, mono, 44.1 kHz.
- **Loudness and length:** peak ≤ -1.5 dBFS before encoding (-1.9 dBFS after); the frequent sounds sit around -13 dB RMS and are 1.5 to 3 s long; nothing audible above 10 kHz. These are rules, not suggestions, because these sounds play hundreds of times.
- **By Stage:** Stage 1 is warm wood, felt and a dry room. Stage 2 adds a glassy partial (about 2.76×) and a longer room. Stage 3 to 4 shortens the box bloom (*the resonator stops answering*), lowers the pitch and detunes the beat wider. The Hush cuts highs further and ends in an abrupt-but-clickless fade to silence (absence, not noise).
- **Variants:** `sound.py <out.ogg> [pitch_hz] [seed]`. Vary pitch within ±3 % (or move to a different named note for a different fork), and vary the seed for the felt noise. Never randomise in-game pitch beyond ±3 %.

## 8. How a build agent applies this

- `gen/engrave.py` is the whole library: `RAMPS` (the locked palette), `c(ramp, i, alpha)`, `hatch(ramp, line, base)`, `sprite(rows, legend, fail, hush)`, `hush_field(...)`, `to_hush`, `save`. New assets import it and nothing else.
- A sprite is a **16-row ASCII map plus a legend** built by `ramp_legend(ramp)`, where digits are shades, `o` is ink, `q` is the thinned lit line, and `h`/`H` are hatch. Hand-author the silhouette and the form; let the library apply the rules (hatch period, Hush remap, line failure) so they stay identical across 200 assets.
- Stage 4 and Hush-touched variants are *the same map* with `fail=` and `hush=` (both seeded). Don't redraw them.
- One entry point, `gen/build.sh`, regenerates every texture, the sound and the previews. Outputs are byte-reproducible (seeded numpy, `sox -R`).
- Review each asset on `previews/sheet.png` at GUI scale 2 before accepting it. If hatching reads as noise there, remove it.
