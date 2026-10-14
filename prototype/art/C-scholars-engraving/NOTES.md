# Option C: notes

## What worked
- **ASCII map + rule library.** Hand-placing the silhouette and form while `engrave.py` applies the hatch period, the Hush remap and line failure kept every sprite on-model. Making a Stage 4 or Hush variant is a parameter change, not a redraw.
- **The jar.** Four elements, translucent smoky glass, an inner liquid block and a brass lid. It reads at once as a glass jar of something, on light and dark grounds. The ripple on the liquid's top face is a nice acoustic touch.
- **The Hollow Bell's decline.** A tarnished, darker ramp; hatching that drops out; the Hush draining the right side through a Bayer-dithered edge; outline pixels missing; ink pooling and dripping off the lip; and a flat void mouth with no clapper. It tells "the plate going silent" without a single purple pixel.
- **The sound.** Energy sits below 2 kHz. The clang mode is short and quiet. Peak is -1.92 dBFS, DC is 0.000014, the last 50 ms are below 0.0006 (no click), and the duration is exactly 2.60 s.

## Revision passes (textures)
1. **Pass 1.** The ingot read as a **loaf of bread**: stepped rounded sides and a soft khaki gradient. The bell was a grey tent with near-grey "tarnish", so the Hush didn't contrast and the hatching was invisible. The jar's liquid hatching was blotchy, and the lid's ink ring made dark X corners in iso.
2. **Pass 2.** The bell got a straight waist with a flare, a warmer tarnish ramp (so it is visibly brass *being* drained) and a mid-shadow hatch band where line failure is visible. The jar liquid became flat bands, following a new rule: no hatching behind glass. The lid ring became soft brass, not ink. The hatch legend was split into `h` (mid) and `H` (deep). The fork lost a tine row to gain a longer stem.
3. **Pass 3.** The bell dropped one shade overall (Stage 4 should be darker than Stage 2) and the Hush radius grew. The ingot was flattened to 8 rows, and the stripe that made it look like a chest lid was removed.
4. **Pass 4.** The ingot got metal shading: a hard bright top edge directly over a dark band, with reflected light at the bottom. This was the biggest single improvement in readability.

At GUI scale 2, the period-3 diagonal hatch survives as visible dashes on the fork's shadow tine and on the ingot's front face. Checkerboard dithering was never used for shading, so nothing turned to mush.

## What fought me
- A tool outage killed the first attempt, and this run's shell kept dropping commands, so the loop was slower than it should have been.
- 16×16 leaves very little room for "engraving". At period 3 there are only about five lines across a sprite, so the hatching has to stay a seasoning and not become the whole texture. The style's identity at slot size comes more from the sepia ink line, the palette and the upright specimen pose than from the hatching.
- I can't hear the sound. It is judged only by its spectrogram and statistics. It may be *too* pure and soft; a listener might want a touch more of the felt "thup".

## Self-critique
- **Weakest asset: `resonant_brass_ingot`.** It is distinct from vanilla copper and gold (olive brass, front-on, verdigris hallmark), but its stepped bevel still gives a slightly soft, cushion-like silhouette. At slot size the hallmark is a speck. A sharper trapezoid (straight 45° ends, or a vertical cut end with its own darker side face) would make it read as cast metal faster.
- The jar's inventory look depends on the custom `gui` display scale (0.75) and on a translucent render layer set in code. Neither can be checked by the shared render tool, which ignores display transforms.
- The bell's line failure is random per pixel (seeded). A more authored failure, such as whole lines missing or broken mid-stroke, would read more as "engraving failing" and less as "noise".
