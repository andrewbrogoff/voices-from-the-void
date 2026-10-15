# Art and sound style guide

The locked art and audio direction for every asset Voices From The Void ships. Every per-spec asset batch follows it, and the [quality gates](quality-gates.md) definition of done checks against it.

Resolved in [Art pipeline proof and style guide](https://github.com/andrewbrogoff/voices-from-the-void/issues/13). Four directions were prototyped on the same sample set (a Tuning Fork, a Resonant Brass ingot, a Void-touched Hollow Bell, an Essentia Jar and the fork strike sound). The owner chose **Ink & Brass**, with its fork strike as the sound palette. Every option, with its generators and previews, is on the [`prototype/art-pipeline` branch](https://github.com/andrewbrogoff/voices-from-the-void/tree/prototype/art-pipeline/prototype/art). The chosen one is `B-ink-and-brass/`.

## Pipeline

- **Code-authored only.** Every texture comes from a seeded Python + Pillow/numpy generator, and every sound from numpy synthesis plus a fixed sox chain. No AI image or audio model is used: none on this machine is licence-clean, and the prototype showed code meets the bar. A later exception needs a licence-clean model and a provenance row naming it.
- **Reproducible.** One entry point regenerates every asset, and a re-run is byte-identical. Blender preview renders are exempt, because they embed a timestamp.
- **Shared library.** The build phase moves the prototype's `gen/lib.py` (the locked palette, `outline()`, and the `save()` that rejects off-palette pixels), `gen/sound.py` and the preview tools (`prototype/art/tools/sheet.py`, `render_model.py`) into `tools/art/` (MPL-2.0, per [`NOTICE.md`](../../NOTICE.md)). Asset generators live beside them.
- **Previews** go to a gitignored `build/previews/`. Before committing, the agent looks at the contact sheet, judging every item at GUI scale 2 beside vanilla, and at the Blender render of every non-cube model.
- **Models** are hand-written or datagen Java JSON. The 26.2 item definition `assets/vftv/items/<id>.json` with `{"model": {"type": "minecraft:model", …}}` was confirmed against the vanilla 26.2 client jar. Blockbench has no headless mode, so it is only for a human to inspect or touch up models.
- **The Voices** are the owner's own recordings, processed with sox (see the [lore spec](lore.md)). TTS is a fallback only, usable once a specific voice model **and** its output licence have been cleared individually and recorded in provenance (a public-domain LJSpeech voice is a candidate; none is cleared today), never the installed `lessac` voice. The owner records the Voices, so no fallback is planned ([warp spec, Production tickets](warp.md#production-tickets)).
- **Provenance:** every shipped asset gets a row in [`docs/assets-provenance.md`](../assets-provenance.md).
- **Checked in game when the build starts:** the jar's translucency (`render_type`, see §6), the `display.gui` scale for small blocks, and how the sounds sit beside vanilla's in volume.

## 1. Direction

Ink & Brass grows the mod's art directly out of its logo: brass instruments drawn with an **ink-navy line**, lit from the upper left, with one small **teal resonance tick** that marks an object as magical. Shapes are a notch more graphic than vanilla: instruments stand upright and bars lie flat, with fewer and broader clusters and no speckle noise. The style sits beside vanilla without clashing because it keeps vanilla's 16×16 grid, light direction and material honesty; it stands apart because of the outline colour and the muted, logo-derived palette. The tone ramp lives in two things only, saturation and *where the teal sits*. At Stage 1 the teal is outside the object, in the light, like the logo's arcs. By Stage 4 it has moved *inside* a hole of void ink, and the warm metal around it is draining to Hush grey. This follows the lore's ramp from a scholar's brass and glass to cosmic horror, without leaving the acoustic world.

## 2. Palette (locked, project-wide, 48 colours)

One palette for every VFTV asset. Generators paint with names (`brass3`) and `lib.save()` rejects any off-palette RGB. Ramps run dark to light. Alpha is free: glass uses palette colours at partial alpha.

| Ramp | Use | Hex (0 → n) |
|---|---|---|
| **ink** | void, **outline**, ink-lift / grooves, slate | `#0b0e1c` `#1a1a2a` `#2a2a3e` `#343849` |
| **mauve** | logo panel slate-mauve: stone, cloth, Choir masonry | `#464153` `#5e566b` `#7d7189` |
| **brass** | the house metal, ending in logo cream | `#5b4d3b` `#85663f` `#a57c43` `#b89459` `#d8c399` `#e8d8b4` |
| **copper** | Stage 1 tools | `#5a2e2a` `#8a4430` `#b4643c` `#d88a58` `#f0b88a` |
| **amethyst** | shards, inlays | `#3d2c5c` `#634a92` `#8f74c4` `#c4b0ec` |
| **teal** | *accent only*: resonance | `#1e4a4e` `#3f8a86` `#82c9bc` `#d2f2ea` |
| **glass** | glass tints (with alpha) | `#8fa9b4` `#d5e6e8` |
| **hush** | drained, silent greys | `#55545c` `#77757c` `#9c99a0` `#c6c2c4` |

**Aspect ramp** (added with the [Aspects and scanning spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/15); owner-approved). These families are used **only** on aspect icons and essentia tints, never on ordinary items or blocks. Each runs dark to light like the other ramps: step 0 is the shade and step 1 the base, and the registry colour is the base. Glyph surfaces in these families shade with their two steps only, an exception to the 3–4 step rule in §3. Mind uses `amethyst`, cloth `mauve`, metal `brass`, void `ink` and silence `hush`, so none of them gets a new family. Colour separates families, but an icon's shape still carries its identity, and jars and tubes also carry a glyph label.

| Family | Use | Hex (0 shade → 1 base) |
|---|---|---|
| **ember** | fire, heat | `#a2441f` `#e0862e` |
| **tide** | water, cold, flow | `#27428a` `#4a78c4` |
| **loam** | earth, stone, ore | `#5c5428` `#9a8838` |
| **gale** | air, motion, sound | `#7a9ccc` `#b8d4ec` |
| **verdure** | plants, growth | `#356a38` `#66a64e` |
| **vital** | life, flesh, healing | `#7e2442` `#c8445c` |
| **blight** | poison, rot, entropy | `#5c2456` `#9c4886` |
| **lumen** | order, light, crystal | `#b0942a` `#f0dc64` |

The source values come from the logo: `#82c9bc` (arcs), `#b89459` (brass strokes), `#e8d8b4` (title cream), `#464153`/`#343849` (panels), `#5b4d3b` (frame lines) and `#1a1a2a` (ground). Adding a colour means amending this table, not a single texture.

## 3. Resolution and pixel rules

- **16×16** items and block faces. No HD. Animated strips use 16×(16·n).
- **Outline: ink navy `ink1` (#1a1a2a), 1 px, 4-connected** (no diagonal corner pixels), drawn by `lib.outline()` around the whole silhouette. It is never black and never darker-of-material. Interior separations use the material's own dark step, or `ink1` for holes and engraved marks.
- **Light from the upper left.** On vertical parts the left column takes the lit step and the right column the dark step. On flat bars the top edge is the brightest row and the front face is the darkest.
- **Shades:** 3–4 steps of a ramp per surface, plus at most one specular tick (the lightest step) per surface. Metal reads as metal through mid tones plus one *narrow* specular streak, never a broad cream face.
- **Dithering:** none, except a 1-px checker on the **Hush/Void boundary**, the only place the world "frays".
- **Anti-aliasing:** none. No partial alpha on item sprites. Glass and liquid are the only translucency.
- **Fill:** silhouette (with outline) inside 15×15; the art proper lives in the 14×14 interior. Orientation follows §4: instruments upright, alloys and ingots horizontal.
- **The teal tick** is ≤ 3 px and sits outside the outline, top-right, as a little wavefront. It is never a large teal area on an item.

## 4. Silhouette language

A VFTV item is recognisable by the **navy line**, **brass/copper in muted, logo-warm tones**, the **teal tick**, and the two orientation rules below. Vanilla uses darker-of-material outlines, busier per-pixel noise and saturated hues, so ours read calmer and "drawn". Instruments over ingredients: even a material should hint at sound.

- **Tool silhouette rule (upright instruments).** Acoustic tools are drawn **upright**, in the instrument's own shape, never laid on vanilla's bottom-left-to-top-right tool diagonal. A diagonal fork read as chopsticks or pliers at slot size; an upright U is unmistakable. The Tuning Fork is two parallel 2-px tines with a 4-px gap (ink, clear, clear, ink, so real background shows through), a rounded crotch, a short stem and a knob, with the amethyst set in the crotch pointing up the gap. A tilt of up to about 15° is allowed if a shape needs it. The signature shape must survive GUI scale 2: if the U, the bell mouth or the horn flare isn't readable there, simplify until it is. The teal tick sits just outside the silhouette, beside the top of the working end (the tine tip).
- **Chime-bar rule (alloys and ingots).** VFTV metals are **not** vanilla bricks; any vanilla-style ingot reads as a loaf, cork or cushion beside vanilla's. An alloy or ingot is a **long, flat, horizontal bar** about 14×6: crisp 1-px 45° bevels on all four corners, a **bright 1-row top edge** (lightest step), a mid-tone face with a short sheen, a **darker 2-row front face** (darkest steps), and lit/shadowed end faces. It has **one punched hanging hole near the left end**: transparent, so the slot shows through, with ink on its shadowed upper wall. The **teal ring tick** sits just off the free (right) end. There is one hole, not two: a symmetric pair reads as a face or as riveted strapping. Different alloys keep this shape and change only the ramp, plus at most one inlay mark on the face.

## 5. Tone ramp by Stage

| Stage | Colour and saturation | Teal | Noise | Sample |
|---|---|---|---|---|
| 1 Resonance | warm copper/brass, full ramps, cream highlights | tick **outside** the object, in light | none | `tuning_fork`: copper, amethyst set in the yoke, tick off the tines |
| 2 Distillation | brass dominant, slightly cooler shadows | tick outside, on the free end or as a small **inlaid** pip | none | `resonant_brass_ingot` (chime bar, teal ring off the end), `essentia_jar` (essentia tinted by its aspect family, teal pip on the lid) |
| 3 Ruins | mauve and slate creep into the shadows | teal dimmer (`teal1`), in grooves | none | (not in this set) |
| 4 Choir's Road | metal drains to **hush** greys from the lower right through a 1-px checker | teal **inside a hole of void ink** (`ink0`), haloed by the outline | the dither edge only | `hollow_bell`: half brass, half Hush, punched through, teal in the dark, void mouth |

**The Hush** is the same subtraction applied to the world: swap a material's ramp for the `hush` ramp at the same step (light stays readable), fray the boundary with the checker, and remove teal entirely (silence). It is never purple, never glossy, never organic. Hushed blocks keep their shapes and lose their colour.

## 6. Blocks and models

- **Element budget:** ≤ 6 cuboids for a small block, all pixel-aligned (no half-pixels), and no rotations unless required. The jar uses 4: essentia [4,1,4]–[12,6,12], glass body [3,0,3]–[13,9,13], neck [4,9,4]–[12,10,12] and lid [3,10,3]–[13,12,13], so 10×12×10.
- **UVs:** use default UVs where possible, and **paint side textures as a front elevation in block space** (body at rows 7–15, lid at rows 4–5). One texture then covers all four sides with no `uv` keys. Shared atlases (the essentia sheet) use explicit `uv` regions.
- **Glass:** an ink frame on the vertical edges and base (a leaded-glass read), a light `glass1` shoulder rim, a highlight streak on the lit (left) side at α≈150, a far-wall column at α≈120 and a body at α≈60. Liquids are a separate inset element at α≈235, so they read through the glass even at slot size. Models with partial alpha carry `"render_type": "minecraft:translucent"`. **This key is unverified: it is a NeoForge model extension that vanilla ignores, and it must be verified against NeoForge 26.2 at build time** (in-game, the glass must blend rather than cut out). If 26.2 has renamed or dropped it, set the render type in code instead.
- **Item view:** blocks smaller than a cube get a `display.gui` scale of about 0.85, so they fill the slot like a full block would.
- Particles use the side texture.

## 7. Sound palette

- **Source:** additive synthesis in numpy (`gen/sound.py`). The fork strike has a fundamental (C5, τ 1.1 s), a **detuned pair an octave up** (±0.06 %, ≈1.25 Hz beat, the slow shimmer), the fork's real clang mode at 6.27·f (τ 50 ms, a brief "ting"), and a **felt strike**: 30 ms of seeded noise through two one-pole lowpasses at about 1.8 kHz. The onset is a 4 ms raised cosine, so there is no click.
- **Chain (sox):** highpass 60 Hz, lowpass 8 kHz, then **reverb** (reverberance 60, HF damping 50, room 90, **pre-delay 40 ms**, wet −4 dB), downmix to mono, lowpass 9 kHz, trim, a 0.9 s quarter-sine fade and normalise.
- **Conventions:** mono, 44.1 kHz, Vorbis q5; **peak −1.5 dBFS** (normalised); ≤ 3 s; the energy above 10 kHz must stay at RMS ≤ −80 dB; the last 50 ms must be under −80 dBFS; and `sox -R` makes re-runs byte-identical.
- **By Stage:** Stage 1–2 instruments are bright and warm, with clean partials. Stage 3 lowers the fundamental, adds more reverb and dims the clang. Stage 4 removes the clang, lengthens the pre-delay (the room answers late) and lets the detuned pair drift wider (an uneasy beat). The Hush is **subtraction**: lowpass, shorter tails, and finally silence. It never adds a growl.
- **Variants:** `sound.py out.ogg <pitch> <seed>`. Pitch scales every partial (keep 0.94–1.06, so ±1 semitone) and the seed changes only the strike noise. Ship 3–4 variants per frequent sound.
- **Voice clips** (the owner's processed recordings, made by the chain in the [warp spec](warp.md#production-tickets)) are the one exception to the duration and peak conventions: a raw performance is **≤ 3.5 s**, a final clip **≤ 4.5 s** including its reverb tail, normalised to **peak −3 dBFS**. Every other convention holds: mono, 44.1 kHz, Vorbis q5, energy above 10 kHz at RMS ≤ −80 dB, the last 50 ms under −80 dBFS, and `sox -R`. A clip's on-screen text stays up for the clip's length plus 20 ticks, scaled by the player's notification time ([localisation, Voice text](accessibility.md#voice-text), which owns the caption's backing, colours and position).

## 8. How a build agent applies this

Paths are as on the prototype branch; the build phase moves them into `tools/art/` (see Pipeline).

```
gen/lib.py       RAMPS/PAL (the locked palette), put(), ramp(), outline(), check(), save()
gen/textures.py  one function per asset returning a 16x16 array; writes model/item JSON
gen/sound.py     synth(pitch, seed) + the fixed sox chain
gen/build.sh     regenerates everything + previews; `python3 gen/lib.py` is the palette self-check
```
Rules for 200 more assets: (1) paint only with palette names, and let `save()` enforce the lock; (2) start from the nearest template (an upright instrument from `tuning_fork()`, an alloy from `resonant_brass_ingot()`, a round silhouette from the per-row half-width profile in `hollow_bell()`), then call `outline()` exactly once, **before** placing teal ticks or void accents; (3) pick the Stage row in §5 and follow its teal placement; (4) fixed seeds only (`SEED + n` per asset); (5) run `sheet.py` and judge every item at GUI scale 2 next to vanilla before committing.

**Accessibility checks** ([localisation and accessibility](accessibility.md)), on every asset batch:
- **Flashes:** every animated strip, and every code-driven frame schedule its spec declares, passes the [flash test](accessibility.md#the-flash-test): no more than 3 flashes in any 20 ticks over more than a quarter of the sprite. No palette colour is a saturated red.
- **States:** every set of state variants (a widget's idle, hover, focus, selected and disabled sprites; a frame's LOCKED, OPEN and LEARNED) differs by an explicit non-hue mark named by its spec, and the contact sheet adds greyscale, deuteranopia, protanopia and tritanopia previews (Machado 2009 matrices in the preview tools), judged at the sprites' real sizes. The luma regression check ([localisation, Colour and contrast](accessibility.md#colour-and-contrast)) runs on the committed sprites.
- **Text:** every text style on every surface, enabled and focused, meets the [contrast table](accessibility.md#text-contrast) over **every pixel of its declared text region** (translucent pixels composited over black and over white), or the region is declared one uniform colour and painted so. HUD frames' interiors are uniform `ink1`; the book's text columns and panel bodies `brass5`; the Choir paper's text column `glass1`; label strips and button faces `brass4` (`brass5` hovered or focused); cost-cell number fields `brass5`.
- **The palette stays locked:** none of this adds a colour.
