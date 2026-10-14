# D — Opaline Resonance

Polished instruments catch colours that do not quite belong to their material: a pale blue reflection across ringing brass, a milky flash on copper, a peach meniscus behind clear glass. The early scholar owns careful, useful objects; beauty appears in their surfaces rather than in decorative magic symbols. As listening deepens, those reflections drain away and the openings grow until the instrument seems to contain more absence than matter. This serves occult natural philosophy first, melancholy wonder second, and cosmic horror last. The family resemblance comes from broad polished facets, paired forms and open cavities. Its distinctness comes from pearly light, not vanilla texture noise, a global ink outline or antique hatching.

## Palette

**Per material, extensible project-wide; not a locked indexed palette.** These are all colours used by this sample's texture generators, in dark-to-light order. There are 28 ramp entries, 27 unique RGB colours; opal and glass share ivory. A new material gets a named ramp with the same value separation and a reviewed slot proof. Do not independently add colours inside individual asset functions.

| Ramp | Hex values | Use |
|---|---|---|
| Copper | `#623C43` `#A95748` `#D98262` `#F2B18A` `#FFE2BF` | Warm copper body; rose-brown recession; peach-white polished edge |
| Ringing brass | `#53534A` `#85845B` `#B6B974` `#DCE299` `#F5F2C7` | Pale green-yellow alloy, distinct from orange copper and saturated yellow gold |
| Glass / pearl | `#405568` `#7193A0` `#A9CCD0` `#DDF0E8` `#FFF9E5` | Cool structural edges and sparse ivory reflections |
| Amethyst | `#544975` `#8D75B6` `#C8ADE0` `#F0E0F3` | Only the physical amethyst shard; not a universal magic colour |
| Opal reflections | `#709FBC` `#9DD9CF` `#EDB2AB` `#FFF9E5` | Blue, mint, peach and ivory bands; not a monotonic shading ramp |
| Hush / spent metal | `#252A32` `#515965` `#89949C` `#BDC6C8` `#E6E9DF` | Desaturated shell and deep recess; no pure black |

Glass alpha is authored separately, in discrete levels; no opacity gradient or edge anti-aliasing is painted into textures. Invisible pixels are transparent black and are not palette colours. Preview backgrounds, rendered shading, compositing and audio plots naturally contain additional colours. The logo informed the relationship of warm metal to cool resonance, but these colours were authored independently. No branding pixels are embedded in the assets.

## Resolution and pixel rules

- **All textures are 32×32.** At GUI scale 2, one texture pixel becomes one screen pixel. Most silhouette runs and structural bands are 2 px wide: the broad geometry still follows vanilla's effective 16×16 scaffold. The extra resolution buys the fork's open throat, a faceted amethyst setting and the bell's missing rim. This is a deliberate fidelity tradeoff: it will look more polished than a strictly 16×16 pack.
- No enclosing ink keyline. Use a darker-of-material edge only on the underside, recess or right face; a lit outside edge may be the brightest material colour. Actual holes use transparency; deep cavities use the Hush shadow.
- Light comes from upper left. Broad top facets are lightest, the front is middle value, right and lower planes are darker. Model preview lighting also applies the shared renderer's directional face shading.
- Five shades per structural material, four for amethyst; most contiguous faces use only two or three. Opal is a reflection accent set, never a rainbow gradient covering an entire sprite.
- No dithering, stippling, random wear, engraving hatches, blur or painted anti-aliasing. Cluster boundaries stay hard. Narrow 1 px glints and seal grooves are allowed; no lone bright pixel unless it describes a physical corner.
- Leave at least 2 px clear at top and sides in item sprites, and 2 px below the lowest pixel. Aim for 24–28 px on the long dimension, equivalent to 12–14 vanilla pixels. The fork is 22×28, the ingot 27×18 and the bell 28×27 before transparent cutouts.
- Do not rotate or resample completed sprite art. Draw the projected facets explicitly. Use nearest-neighbour scaling for item checks. Blender render reduction is a preview operation, not an authored texture.

## Silhouette language

Think of an instrument as **two sides around an intentional opening**: fork tines, a hollow bell, glass enclosing empty volume. Turn the tool upright and let its cavity define it. At the same time, preserve the familiar item: two copper tines, an ingot's sloped top, a bell's flared skirt, a jar's neck and lid. Never put loudspeaker cones, circuit traces, antennae, telescope forms or waveforms on screens into the design. The brass's paired diagonal marks are shallow physical score marks. The jar lid carries concentric pressed seal rings. Avoid abstract symbols unless a future spec gives them a meaning.

Compared with vanilla, these are more deliberate, smooth instruments with fewer texture clusters, larger uninterrupted highlights and more internal empty space. They should feel made by the same scholar, not like miscellaneous magical loot.

## Tone ramp

| Stage | Treatment | Example / intended application |
|---|---|---|
| 1 — Resonance | Warm intact material; high legibility; no damage or drifting particles. Accent belongs to a real substance. | Copper fork with physical amethyst at the heel. Its clear central throat is larger than the crystal. |
| 2 — Distillation | Paler metal; cool reflection on warm material; light crosses translucent surfaces. Quiet wonder without universal glow. | Ringing brass ingot; low peach essentia in a glass jar. Neither has emissive textures. |
| 3 — Ruins | Fewer saturated surfaces; reflections become incomplete; leave an extra gap or absent polished band. | Future instruments keep an intact functional silhouette but look less answered by their surroundings. No extra grunge noise. |
| 4 — Choir's Road | Spend the colour budget down to near-monochrome. Open a large dark cavity, remove a segment of the rim, retain enough silhouette to name the object. | Hollow bell: drained shell, an impossible recess through the body and a genuinely missing lower-right rim segment. |

**The Hush is desaturated silence.** Replace material colours with corresponding Hush values, shorten or remove bright reflections, simplify detail, and hollow out structure. Reduce audio presence along with visual saturation. Do not introduce purple fluids, flesh, tendrils, eyes or a noisy corruption overlay. The bell's cavity is a designed late-stage object; the Hush treatment is a future world-material rule, not an extra sample asset.

## Blocks and models

- Elements-only Java block model; no parent, custom loader, element rotation, tint or emissive dependency. The jar uses **5 elements**, within an 8-element budget: shell, neck, lid, interior fill and foot. Bounds are `[3, 0.5, 3]` through `[13, 13, 13]`: 10×12.5×10 model units, clearly smaller than a block.
- The glass body is one transparent cuboid. Its body faces are mostly alpha 32/255, with stronger rim and glint clusters. Low-opacity closed faces let the contained fill remain visible. The smaller neck and lid establish the jar silhouette.
- The model declares `render_type: minecraft:translucent`; glass and contained essentia have partial alpha, metal is opaque. Do not mark inset faces `cullface`, or neighbours could incorrectly remove them. Keep inner fill away from the shell to avoid coincident planes.
- Textures use a 32×32 canvas and UVs expressed in Java's 0–16 coordinates: normally 2 texels per world-model unit. Face regions occupy the upper-left area of separate named textures; unused texels are transparent. Body side uses UV `[0,0,10,9]`; body top `[0,0,10,10]`; fill uses `[0,0,8,3]` on its side and `[0,0,8,8]` on top. The lid's 8×8 top deliberately maps the 10×10 UV design so its seal stays centred; this is the one 2.5-texel/unit exception.
- The item definition directly references the block model. GUI transform is present in the JSON. The supplied renderer does not apply GUI transforms, so `essentia_jar_slot.png` fits its render into a 28 px footprint as a readability proxy. It is **not** an in-game screenshot or proof of inventory rendering.
- Translucency sorting, render-layer behaviour and actual GUI framing still need an in-game check before shipping. No runtime code is part of this art prototype.

## Sound palette

The strike is original additive/modal synthesis: a 523.25 Hz tine and a quieter 524.55 Hz companion, plus brief modes at 1047, 1432.7 and 2317 Hz. A seeded, heavily smoothed noise impulse suggests contact with the metal. The fundamental remains calm; the higher glass modes disappear quickly. There is no recorded sample, voice, AI synthesis or borrowed preset.

Processing: exponential modal decays → soft attack → quiet 43/71/113 ms room taps → a smooth spectral rolloff around 4.5 kHz → DC removal → 220 ms final fade → source peak −7 dBFS → mono 16-bit intermediate → SoX Vorbis quality 5, without random dither. Final 10 ms are silent. Canonical Ogg serial numbers and recalculated page CRCs remove the encoder's random container variation without changing its audio packets.

- Frequent Stage 1 actions: 1.5–3 s, target encoded peaks around −7 dBFS, always below −1 dBFS. Keep average energy low; this sample is 2.60 s and −22.75 dBFS RMS. The optional sound-event JSON applies volume 0.65 as a starting mix value.
- Stage 2: add one quiet glass mode and a slightly longer tail. Stage 3: lengthen sparse room responses and reduce the bright attack. Stage 4: introduce gentle inharmonic spacing and absent partials, not shrillness or louder peaks. Hush removes the ring and shortens or suppresses the sound.
- Variants: transpose all modes together by −25, 0 or +25 cents and use a different documented seed for the tiny contact impulse. Keep attack and peak fixed. Do not independently randomise each partial on every use. This delivery contains one canonical strike; variant policy is for future assets.
- Spectrogram and decoded metrics are checked by code; headphone comfort and the 500th hearing require human listening. An audio player is included in the review page.

## How a build agent applies this

Run `bash prototype/art/D-astra/gen/build.sh` from any directory. It rebuilds all three revision checkpoints, then leaves the final assets, shared-tool sheet, jar render, slot board, sound checks and self-contained review page. `--revision 0|1|2` rebuilds a single checkpoint for inspection; run the default command again to restore final assets.

`gen/palette.py` is the sole colour authority. `gen/generate.py` holds explicit polygon clusters, the small cuboid/UV helper, resource JSON and modal audio. Seed 47017 controls small, bounded polish variations and the strike impulse; the jar uses 47018. Geometry is authored, not randomised. `gen/verify.py` validates palette membership, item alpha, dimensions, model geometry and texture references, and decoded audio constraints; it also canonicalises the Ogg container and builds slot proofs. `gen/present.py` packages those proofs for review.

The shared Blender renderer is invoked unchanged. Afterwards, `verify.py --canonicalize-render` strips its variable date/render-time metadata by re-encoding the identical RGBA pixels. This keeps the jar PNG reproducible as a file, not merely as an image.

For 200 assets, add one clearly named drawing function per asset and reuse material ramps, the transparent canvas, JSON writer, UV box helper and slot checks. Split functions into material/family modules only when that makes the generator easier to read. Preserve large silhouettes; vary physical purpose, not decorative noise. For each family, review the native slot, two contrast grounds and a world/model view; record two concrete texture revisions before approval.

Assets and documentation: **CC-BY-4.0**, Voices From The Void by Andrew Rogoff and contributors. Generator scripts: **MPL-2.0**. These are original code-authored prototype designs; the reserved logo and icon were read as context only and are not redistributed here.
