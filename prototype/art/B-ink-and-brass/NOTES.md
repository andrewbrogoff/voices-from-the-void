# Option B: Ink & Brass (notes)

Regenerate with `bash gen/build.sh`. Two full builds produced byte-identical textures, JSON and OGG. The only file that changed was `previews/essentia_jar.png` from the shared Blender tool (the sheet built from it came out identical, so this is most likely render metadata or noise).

## Revision passes
- **Pass 0:** the fork's amethyst was lost in a cramped yoke, so it read as a trident. The ingot was a thin stick that looked like a baguette. The bell's Hush was a random-threshold hard split. The jar had a double ink frame on every glass corner and a stepped, sandy lid.
- **Pass 1:** I widened the fork's tine gap (3 px of real transparency between ink lines), turned the amethyst into a plus-shaped gem in the yoke and moved the teal tick off the tips. The ingot got a 4-px top face, two node holes and a teal stamp. The bell's Hush became a deterministic checker band creeping in from the lower right, with a larger punched hollow. The jar now has ink on one vertical edge only, a light glass rim, a shadow groove at the neck and a clean lid with an L highlight.
- **Pass 2:** I made the ingot shorter and chunkier (a 3-px front face), so it reads as stock rather than a plank. The jar's ink is now on both vertical edges, because one-sided ink left the right silhouette un-outlined, which broke the ink rule.
- **Pass 3:** the ingot's broad cream top looked like butter, so it now uses mid tones with one narrow specular streak. I checked every item at GUI scale 2 next to vanilla copper, gold and amethyst. All four read, and the ingot is clearly not a vanilla ingot.
- **Sound:** the first pass was fine on stats. I raised the reverb (60 reverberance, wet −4 dB) for a more audible airy tail and added a 9 kHz lowpass after the reverb. Final figures: peak −1.64 dBFS, DC offset 0.00001, >10 kHz band RMS −89 dB, last 50 ms below −84 dBFS, 2.8 s long.

## Revision 2 (owner chose B; two changes before the lock)
- **Tuning Fork made upright.** At slot size the diagonal fork read as chopsticks or pliers. It is now an upright U: 2-px tines with a 4-px gap (so the slot shows through), a rounded crotch, a short stem and a knob, and the amethyst in the crotch pointing up the gap. The teal tick sits by the right tine tip, outside the outline. The U is unmistakable at GUI 2.
- **Ingot redesigned as a chime bar.** I tried six variants at slot size beside vanilla copper and gold: (A) a top-down plate with two 2×2 holes; (B) a 3/4 bar with holes through the top face; (C) B tilted; then flatter, higher-contrast plates with (D) two 1-px holes, (E) two 2×1 holes and (F) one 2×1 hole. B and C still read as loaves. A and E read as a face (two eyes), and D as riveted strapping. **Kept F: the flat bar with crisp bevels and a single 2-px punched hole at the left end (transparent, ink upper wall), a bright top edge, a dark 2-row front face and a teal ring off the free end.** The asymmetry is what makes it read as a bar that hangs and rings instead of a face. It is clearly not a vanilla ingot in shape or colour, and it still sits like a stackable metal ingredient.
- The diagonal `diag()` helper was removed: nothing uses it any more. STYLE.md gains the upright-tool and chime-bar rules (§4), and the `render_type` note now says it must be verified against NeoForge 26.2 at build time.
- The rebuild is still byte-identical for all textures, JSON and OGG (the shared Blender preview PNG still differs run to run, as before).

## What worked
- Starting from the instrument's real silhouette (upright fork, hanging bar, bell) rather than from vanilla's item conventions.
- Auto-outline *then* accents: the ink rule is enforced by code, and teal or void accents can deliberately sit outside or inside it.
- Teal *placement* as the Stage signal (outside in light, then inside void) reads instantly and costs 1–3 px.
- `save()` refusing off-palette colours kept the 32-colour lock honest.

## What fought me
- Diagonal pixel parity (first pass): in a 45° frame only same-parity pairs exist, so small details were hard to place. Going upright and horizontal removed the problem.
- A glass cube with an ink frame either double-inks the corners or loses one silhouette edge. I chose the cage. It reads well at slot size but is heavier than real glass.
- I can't listen to the sound. The beating and the tail are verified only by the spectrogram and stats.
- The tool environment intermittently refused long compound shell commands, so checks ran from small script files.

## Self-critique
- **Weakest asset (after revision 2): `hollow_bell`.** Its teal sits in a 1-px pocket that is easy to miss at GUI 2, so the void mouth and the grey drain carry the horror on their own. The chime-bar ingot is now solid, but its 2-px hole is the whole idea, and it leans on the transparent pixel showing a contrasting slot background.
- The jar's ink cage makes it read slightly like a lantern or frame block. Inking only the base and lid seam might be more elegant.
- The fork's amethyst is only 5 px. At GUI 2 it reads as "a purple jewel", not specifically as a shard.
- The sound is pleasant but quite pure. The detuned pair at the octave is gentle enough that some people may not notice the shimmer at all. That is the right side to err on for a sound heard 500 times.
