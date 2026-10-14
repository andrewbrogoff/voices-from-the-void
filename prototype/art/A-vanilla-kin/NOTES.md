# Option A: Vanilla Kin, notes

Rebuild: `prototype/art/A-vanilla-kin/gen/build.sh`. Two consecutive runs produced byte-identical assets (I checked a combined md5 of every JSON, PNG and the OGG).

## Calibration (look only)
I looked at a handful of vanilla 26.2 textures (copper, gold and iron ingots, the amethyst and echo shards, the bell, brush, shears, glass bottle, and the glass and sculk blocks) and counted their colours, then deleted the temporary extracts. Takeaways: 7–12 colours per item, darks that shift towards red and lights towards yellow or pink, no black, highlights in short runs, and a darkest-shade edge only on the shadow side. Nothing was copied, traced or recoloured. All ramps come from `ramp()`, and every sprite is a hand-authored grid.

## Revision passes
1. **First pass:** the fork read like chopsticks (tines too long, unclear yoke). The ingot was a diagonal bar that looked twisted, and the groove read as a step. The bell was a triangle like a helmet, with "window" crack pixels and a heavy black mouth bar. The jar's glass highlights looked like `!` marks, and the box read as a cube.
2. **Second pass:** the fork got shorter tines, a rounded U closing under a 4-px amethyst rhombus, and a knob pommel. The ingot was redrawn **level**, deliberately off vanilla's diagonal, with a waist groove. The bell became a waisted hand-bell with a crown loop, and the hushed ramp moved from cool lavender-grey to drained warm bronze-grey so it stays in the metal family. The jar got a glass neck, clipped pane corners (so it reads rounded) and a single highlight streak, and the lid moved to brass to tie it to Stage 2.
3. **Third pass:** the groove notch on the ingot was too deep and it read as two bread rolls, so I made the notch a colour band and added an underside edge row. The bell's crack dots read as a *face* at slot size, so I replaced them with one continuous zig-zag in `hushed` 1 with a single `void` glint. I removed a lower lip row that made the mouth look like a plinth.
4. **Sound:** the first render was 1.35 s long because sox `pad` and `fade -0` misbehaved. I moved the padding into numpy and gave the fade explicit times, and lowered the clang mode from 0.30 to 0.24.

Sound checks (`sox stat` / `stats`): 2.69 s, mono 44.1 kHz Vorbis, peak −1.73 dBFS, DC offset 0.000002, RMS above 10 kHz about −102 dB, and the last 0.14 s is digital zero (no end click). The spectrogram shows 440 Hz dominant, the 2.73 kHz clang gone by about 0.6 s, a 7.7 kHz glint gone within 0.1 s, and nothing above 9 kHz.

## What worked
- Character grids plus per-material ramps. Revisions were fast and every pixel decision is reviewable in a diff.
- `ramp()` with a hue drift gave vanilla-feeling ramps on the first try for copper, amethyst and brass.
- Brass reads clearly as neither copper nor gold. The olive hue and lower saturation do most of the work, and the level silhouette does the rest.
- The fork sound: three physical modes and a tiny strike is about as inoffensive as a ringing tone gets.

## What fought me
- Diagonal 2-px forms at 16 px leave very little room. The fork's U and gem had to be placed pixel by pixel.
- Glass. With back-face culling you never see the far walls, so an empty jar is only a frame. It reads, but it's sparse.
- The shell harness rejected writes to `/tmp` and some compound commands, so all scratch work went into the option folder and was cleaned up.

## Self-critique
- **Weakest asset: the Resonant Brass ingot.** It is clearly distinct from vanilla, but the level bar with a waist band can read as a loaf or a cushion rather than worked metal, because it lacks a crisp specular run. A better version would sharpen the end chamfers and run a 2–3 px `brass` 5 specular line along the front edge.
- The Tuning Fork is honest and vanilla-like but **thin at GUI ×2**: it reads as "copper stick with a purple dot" before it reads as "fork". That is acceptable for a tool, but it is the mod's signature item, so it may deserve one more pass (for example a slightly wider U).
- The Hollow Bell is the most successful at "Void-touched but vanilla-plausible". Its crack and pale glint in the mouth are the only horror cues. The warm-grey bronze could still be mistaken for stone.
- The empty jar would read better with contents. Essentia fill will be an inner element in a later variant.
- The sound may be **too polite** for some tastes: it is close to a sine with a soft ping. That is right for the 500th strike, but less characterful than the other options will likely be.
