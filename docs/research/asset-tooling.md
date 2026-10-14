# AI asset tooling available on this machine

Resolves #8 (map #1). Researched 2026-10-14 on the dev box. This is a reading of licence texts, not legal advice.

**Bottom line:** everything shippable can be made today with tools already installed. Use Python + Pillow for pixel art, hand-written JSON for models, headless Blender for preview renders, and sox for sounds. AI generation is optional. Only Apache, MIT or OpenRAIL weights are allowed, and the AI models currently on disk do not qualify.

## 1. The machine

| Thing | Found | How |
|---|---|---|
| GPU | NVIDIA RTX 3090, 24 GB VRAM, driver 615.71 | `nvidia-smi` |
| CUDA | 13.2 toolkit at `/usr/local/cuda` | `nvcc --version` |
| CPU / RAM / disk | Ryzen 9 7950X3D (32 threads), 61 GiB RAM, about 585 GB free on /home | `lscpu`, `free`, `df` |
| Python | Miniforge base env (Python 3.13): torch 2.11+cu130 (CUDA works), diffusers 0.38, transformers, Pillow, numpy, scipy. No audio ML packages. | import check |
| Java | OpenJDK 25.0.4 (also needed for the mod build) | `java -version` |
| Blender | **5.2.2 LTS** at `~/.local/bin/blender` | `blender --version` |
| Blockbench | **5.2.2**, Flathub flatpak `net.blockbench.Blockbench` (GPL-3.0) | `flatpak info` |
| ImageMagick | 7.1.2 (`magick`) | rpm |
| ffmpeg | `ffmpeg-free` 8.1.2, includes the `libvorbis` and `libopus` encoders | `ffmpeg -encoders` |
| sox | 14.4.2, reads and writes `ogg`/`vorbis` | `sox -h` |
| Audacity | 3.7.9 (GUI only, for human review) | rpm |
| Piper TTS | `piper-tts` uv tool, with one voice (`en_US-lessac-high`) | `~/.local/bin/piper` |
| ComfyUI | 0.35.0, bundled by MooshieUI at `~/Code/.img/MooshieUI/comfyui`, with its own venv (torch 2.13+cu130). Runs headless. | smoke test below |
| Image weights on disk | `Juice.safetensors` (an SDXL-architecture anime merge of unknown provenance), `anima-base-v1.0` (CircleStone Anima), anime upscalers. The HF cache entries for `Comfy-Org/z_image` and `HiDream-O1-Image-Dev` are empty stubs, so nothing was downloaded. | `find` |
| Prism Launcher | 11.1.0 flatpak, for testing assets in game | `flatpak list` |
| Not installed | Aseprite, LibreSprite, GIMP, Krita, Inkscape, `oggenc`, AudioCraft, stable-audio-tools, any pixel-art LoRA. LM Studio has no image or audio models. | `which`, `find` |

## 2. Smoke tests

All outputs went to `/tmp/vftv/`.

| Test | Result | Time |
|---|---|---|
| Procedural 16x16 item texture: Python + Pillow, palette-locked, seeded, auto-outlined (`tex.py`, about 20 lines). Then a 1600% nearest-neighbour preview with `magick`. | Works. Produces a 255-byte 16x16 RGBA PNG. | 58 ms |
| Headless Blender: `blender -b --factory-startup --python bl.py`. Builds a cube textured with that PNG (Closest interpolation), adds an ortho isometric camera, renders a 128x128 transparent icon with EEVEE and exports `.glb`. | Works | 3.7 s wall time, including startup |
| Headless ComfyUI: `main.py --port 8199` plus an API workflow (SDXL txt2img, 1024², 20 steps, local `Juice` checkpoint, used **for speed only**). Then a Pillow box-downscale to 16x16 and an 8-colour quantise. | Works end to end | 19.6 s cold (includes model load), **6.6 s warm** per image |
| diffusers `StableDiffusionXLPipeline.from_single_file` in the base env | **Fails.** `CLIPTextModel` has no `text_model`, a diffusers/transformers version skew. Use ComfyUI, or pin versions in a dedicated env. | n/a |
| sox synthesis: layered sines + pink noise, bandpass, tremolo, reverb, written straight to `.ogg`. Also a square-wave sweep "zap". | Works. Output is Vorbis, mono, 44.1 kHz. | 79 ms for both |
| Piper TTS "it hears you" piped through sox (pitch -500, reverse-reverb, tremolo) to make a whisper `.ogg` | Works. Output is Vorbis mono. Watch the voice licence (see §6). | 3.0 s |
| Blockbench headless | **Not possible** (see §3) | n/a |

I could not look at the output images myself: the agent sandbox blocks reading files outside the working directories. Any agent doing visual QA has to write its previews somewhere it is allowed to read (see open questions).

## 3. Models: Blockbench vs hand-written JSON vs Blender

**Blockbench**
- **Licence:** GPL-3.0 ([LICENSE.MD](https://github.com/JannisX11/blockbench/blob/master/LICENSE.MD)). The GPL covers the program, not the models you make with it.
- **No headless mode.**
  - In 5.x, the Electron entry `electron/main.js` only reads `--userData <path>`, an optional file to open, and GPU switches. There are no export or convert flags ([main.js](https://raw.githubusercontent.com/JannisX11/blockbench/master/electron/main.js)).
  - The feature request for headless batch conversion has been open since 2023 ([issue #1764](https://github.com/JannisX11/blockbench/issues/1764)).
- **Plugins** (`Plugin.register`, `Codec.compile()` / `Codec.export()`) can script exports, but only inside the running GUI ([plugin docs](https://www.blockbench.net/wiki/docs/plugin/), [codec.ts](https://raw.githubusercontent.com/JannisX11/blockbench/master/js/io/codec.ts)).
- **.bbmodel** is JSON with no formal spec; the reference is `js/formats/bbmodel.js` ([bbmodel docs](https://www.blockbench.net/wiki/docs/bbmodel/)). No community CLI converts `.bbmodel` to Java JSON.
- **Verdict:** agents should not route through Blockbench. A human can use it to look at or touch up models; it opens Java JSON directly.

**Hand-written Java model JSON (recommended)**

Block and item models are small JSON files built from cuboid `elements` (`from`/`to`/`faces`/`uv`). An LLM agent can write them directly.

Files a mod needs as of 26.2 ([Model](https://minecraft.wiki/w/Model), [Items model definition](https://minecraft.wiki/w/Items_model_definition)):
- **Item:** `assets/<ns>/items/<id>.json` (the item model definition, added in 1.21.4), `models/item/<id>.json` (usually `parent: minecraft:item/generated` with `layer0`), and `textures/item/<id>.png`.
- **Block:** `blockstates/<id>.json`, `models/block/<id>.json`, `textures/block/*.png`, plus `items/<id>.json` for the BlockItem.

Version changes worth knowing:
- **26.1:** any block model may use cutout or translucent textures. A `textures` entry can now be a string or `{"sprite": "...", "force_translucent": bool}`, and translucency is detected from the pixels.
- **26.1 item definitions:** gained `transformation` and the special types `bell`, `book` and `end_cube`.
- **26.2:** removed the `bed`, `standing_sign` and `hanging_sign` special types. No block-model format change.
- **26.3 snapshots:** replace `shade` with `shade_direction_override`. Pin to 26.2.
- Check the exact 26.x `textures` syntax against a vanilla 26.2 client jar. The wiki does not show the full before and after.

Bulk files should come from NeoForge datagen (`BlockStateProvider`/`ItemModelProvider`), not hand-written JSON. That is a build-side decision for another ticket.

**Entity models** are Java code (`LayerDefinition` / `ModelPart` cubes), not JSON. Agents write them directly. Blockbench's "Modded Entity" Java export is GUI-only.

**Blender (headless)**
- **Licence:** GPL. "What you create with Blender is your sole property" ([blender.org/about/license](https://www.blender.org/about/license/)).
- Proven here: `blender -b --python` runs fully scripted, renders on the GPU, and exports glTF in about 4 s per job.
- Minecraft does not load Blender meshes, so Blender's role is **QA and marketing renders**: isometric previews of a texture or model for an agent or a human to check, and CurseForge/Modrinth images.
- Optional later: a roughly 50-line `bpy` script that turns Java model JSON into cubes, giving agents a visual check without launching the game.

## 4. Pixel textures: procedural code vs AI generation

**Procedural or hand-authored in code (recommended)**
- **Stack:** Python + Pillow (Pillow is MIT-CMU, [LICENSE](https://github.com/python-pillow/Pillow/blob/main/LICENSE)), numpy, and ImageMagick for previews and sheets. All installed.
- **Licences:** no licence risk. The output is 100% original.
- **Speed:** milliseconds per texture.
- **Determinism:** seed plus palette plus script gives a reproducible, diff-able source of truth.
- **Quality ceiling:** set by the agent's art direction. A locked project palette, 1px outlines, dithering, hue-shifted ramps and noise-based block tiling give clean, consistent "vanilla-adjacent" results. The weakness is organic or figurative subjects such as creatures and faces.

**AI image generation (optional, for concepts and 32x32+)**
- **What works here:** ComfyUI headless at about 6.6 s per 1024² SDXL image on the 3090. A Flux-class or 6B model (Z-Image-Turbo, FLUX.2-klein-4B) fits in 24 GB.
- **What doesn't work:** no model produces true 16x16. The proven pattern is to generate at 512–1024 with a pixel-art LoRA, recover the grid, downscale, quantise to the project palette, then clean up by hand or in code.
- **Grid-recovery tools (all MIT):**
  - [proper-pixel-art](https://github.com/KennethJAllen/proper-pixel-art) (`ppa` CLI)
  - [pixeldetector](https://github.com/Astropulse/pixeldetector)
  - [unfake](https://pypi.org/project/unfake/)
- **Quality ceiling:**
  - 32x32 and up (GUI art, icons, the book): decent.
  - 16x16: the downscale tends to muddy. Expect to redraw, so AI is mostly a concept or reference source at this size.
- **Needs install:** a permissively licensed checkpoint and LoRA. The weights on disk are **not** acceptable for shipping: Juice's provenance is unknown, and Anima is non-commercial.

| Model / LoRA | Weights licence | Ship outputs in a public free mod? | Source |
|---|---|---|---|
| SDXL base 1.0 | CreativeML Open RAIL++-M | Yes. Licensor claims no rights in outputs; use restrictions apply. | [LICENSE](https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/raw/main/LICENSE.md) |
| + nerijs/pixel-art-xl LoRA | OpenRAIL-M | **Yes. Best low-effort pixel option.** | [card](https://huggingface.co/nerijs/pixel-art-xl) |
| SD 1.5 + PublicPrompts All-In-One-Pixel-Model | OpenRAIL-M | Yes, but an older and weaker model | [card](https://huggingface.co/PublicPrompts/All-In-One-Pixel-Model) |
| FLUX.1-schnell | Apache-2.0 | Yes | [card](https://huggingface.co/black-forest-labs/FLUX.1-schnell) |
| FLUX.2-klein-4B | Apache-2.0 | Yes | [card](https://huggingface.co/black-forest-labs/FLUX.2-klein-4B) |
| Z-Image-Turbo (Tongyi) | Apache-2.0 | Yes | [card](https://huggingface.co/Tongyi-MAI/Z-Image-Turbo) |
| Qwen-Image | Apache-2.0 | Yes. Large, about 20B. | [card](https://huggingface.co/Qwen/Qwen-Image) |
| HiDream-I1 / HiDream-O1-Image-Dev | MIT. I1 also needs Llama-3.1-8B, which has its own licence. | Yes, with that caveat | [I1](https://huggingface.co/HiDream-ai/HiDream-I1-Full), [O1](https://huggingface.co/HiDream-ai/HiDream-O1-Image-Dev) |
| SD 3.5 | Stability Community License | Yes under US$1M revenue; registration required | [licence](https://stability.ai/community-license-agreement) |
| Illustrious-XL / NoobAI-XL (the probable lineage of "Juice") | Fair AI Public License 1.0-SD | Outputs not covered by the licence, but Juice itself is unverified, so **don't use it** | [FAIPL](https://freedevproject.org/faipl-1.0-sd/) |
| FLUX.1-dev, FLUX.2-dev, FLUX.2-klein-9B, and any LoRA built on them (UmeAiRT Modern Pixel, prithivMLmods Retro-Pixel) | FLUX Non-Commercial | **Avoid.** §4(a) bars "any data produced by" the model for production use, and use with "impact on end users" counts as commercial. | [FLUX.1-dev LICENSE](https://huggingface.co/black-forest-labs/FLUX.1-dev/blob/main/LICENSE.md) |
| Anima (on disk) | CircleStone Non-Commercial v1.2 | **Avoid.** Same end-user problem. | [LICENSE](https://huggingface.co/circlestone-labs/Anima/blob/main/LICENSE.md) |
| nerijs/pixel-art-medium-128 | CC-BY-NC-4.0 | **No** | [card](https://huggingface.co/nerijs/pixel-art-medium-128-v0.1) |
| Retro Diffusion | Closed, paid | Outputs are reportedly the user's (developer statement, not licence text). Not needed. | [itch](https://astropulse.itch.io/retrodiffusion) |

**Tool licences:**
- ComfyUI: GPL-3.0 ([repo](https://github.com/comfyanonymous/ComfyUI)).
- diffusers: Apache-2.0 ([repo](https://github.com/huggingface/diffusers)).
- Neither affects the licence of what they generate.

**Pixel editors:** none are needed by agents.
- Aseprite is proprietary: you may compile it for personal use only, with no redistribution ([EULA](https://github.com/aseprite/aseprite/blob/main/EULA.txt)).
- LibreSprite is GPL-2.0 ([repo](https://github.com/LibreSprite/LibreSprite)).
- If a human wants a GUI editor, `flatpak install` LibreSprite or Pixelorama.

## 5. Sound effects

**Minecraft format** ([Sounds.json](https://minecraft.wiki/w/Sounds.json)):
- Put `.ogg` files under `assets/<ns>/sounds/` and register them in `sounds.json` without the extension.
- **Mono** files are positional (they fade with distance); stereo files are not.
- Use `"stream": true` for long sounds.
- Use Vorbis encoding. Vanilla uses Vorbis; `sox` or `ffmpeg -c:a libvorbis -ac 1` both work here.

**Procedural synthesis: sox, or numpy/scipy, then sox**
- Works now, takes milliseconds, and the output is wholly original.
- **Licence:** sox is GPL and libsox is LGPL; neither touches your audio ([COPYING](https://sourceforge.net/p/sox/code/ci/master/tree/COPYING)).
- **Quality ceiling:** strong for UI clicks, zaps, hums, drones, chimes, risers and glitchy void ambience, which suits this mod's theme. Weak for organic, foley-like sounds (footsteps, creature vocalisations).

**AI generation (needs install)**

| Model | Weights licence | Ship outputs? | Source |
|---|---|---|---|
| **Stable Audio Open 1.0 / Small** | Stability AI Community License. Trained only on CC0, CC-BY and CC Sampling+ audio. | **Yes** under US$1M revenue (you own outputs). Gated HF download, needs `stable-audio-tools`. Easily fits 24 GB. | [1.0](https://huggingface.co/stabilityai/stable-audio-open-1.0), [Small](https://huggingface.co/stabilityai/stable-audio-open-small), [licence](https://stability.ai/community-license-agreement) |
| EzAudio | MIT (code and weights) | Probably. Training data likely includes AudioCaps/WavCaps (academic-only), not verified. | [card](https://huggingface.co/OpenSound/EzAudio) |
| AudioCraft (AudioGen / MusicGen) | Code MIT, **weights CC-BY-NC-4.0** | **No** | [repo](https://github.com/facebookresearch/audiocraft) |
| AudioLDM2 | CC-BY-NC-SA-4.0 | **No** | [card](https://huggingface.co/cvssp/audioldm2) |
| MMAudio | Weights CC-BY-NC-4.0 | **No** | [repo](https://github.com/hkchengrex/MMAudio) |
| TangoFlux | Non-commercial, research-only | **No** | [LICENSE](https://huggingface.co/declare-lab/TangoFlux/raw/main/LICENSE.md) |

- **Quality ceiling:** good for organic SFX and textures up to about 47 s (1.0) or about 11 s (Small).
- **Speed:** not measured here; expect seconds per clip on the 3090.
- **Post-processing:** always run through sox: trim, fade, `norm`, mono 44.1 kHz Vorbis.

**Voiced "whispers" (Piper TTS + sox)**
- Works now (3 s), and fits the "Voices" theme.
- **Licence check needed:**
  - Piper's code is MIT or GPL depending on the version. That doesn't affect output.
  - Each voice inherits its training **dataset** licence. The installed `lessac` voice comes from the Blizzard Lessac corpus, whose terms look research-only. **Treat it as non-shippable until checked.**
  - Prefer a voice trained on a public-domain dataset such as LJSpeech (`en_US-ljspeech`), after confirming its model card.

## 6. Recommended pipeline per asset type

| Asset | Pipeline | Ready here? |
|---|---|---|
| **Item textures** (16x16) | An agent writes a seeded Python + Pillow generator per item (or a shared palette module), using the locked project palette. Preview with `magick -filter point -resize`. Optionally take a concept from SDXL + pixel-art-xl or Z-Image-Turbo, grid-recover and quantise, then redraw in code. | Yes (AI path needs model download) |
| **Block textures** (16x16, tiling) | Python + numpy noise + palette quantisation, checked for seamless tiling. Animated textures are a vertical strip + `.png.mcmeta`. Use 26.1+ `force_translucent` only when needed. | Yes |
| **Block / item models** | Hand-written Java JSON `elements`, or NeoForge datagen for bulk. Preview with headless Blender or in game via Prism Launcher. Blockbench is for optional human touch-up only. | Yes |
| **Entity models** | Java `LayerDefinition`/`ModelPart` code written by the agent. Entity textures (64x64) from Pillow with a UV-layout helper. Preview renders in Blender if a JSON-to-Blender script is built. | Yes |
| **GUI** | Pillow-drawn sprites and 9-slice panels under `textures/gui/sprites/` with `.mcmeta` `gui.scaling` (nine_slice). Larger art (book pages, 32x32+ icons) is where AI plus quantisation earns its keep. | Yes |
| **Particles** | Pillow, 8x8 or 16x16 frame sequences + `particles/<id>.json` | Yes |
| **Sounds** | sox or numpy synthesis for UI, magic and ambient sounds (now). Stable Audio Open (Small or 1.0) for organic SFX (needs install plus a gated HF licence acceptance). Piper + sox for whispers, with a public-domain voice. Always finish as mono 44.1 kHz Vorbis `.ogg`. | Synthesis yes; SAO needs install |

## 7. Open questions for the map

1. **Visual QA path for agents.** Agents cannot read images outside the working directories. Should we keep a gitignored `build/previews/` folder in the repo, or add `/tmp/vftv` as an allowed directory?
2. **Is AI image or audio generation wanted at all?** Code-generated art is cheaper, deterministic and licence-clean. If yes, someone must accept the Stability Community License on Hugging Face (a human with an account) for Stable Audio Open. Pixel-art-xl and Z-Image-Turbo need no click-through.
3. **Copyrightability.** Purely AI-generated outputs may not be copyrightable in the US. That does not stop us shipping them, but others could reuse them. Code-authored assets avoid this.
4. **Exact 26.2 `textures` map syntax.** Confirm against a vanilla 26.2 jar when the mod skeleton exists.
5. **Licence per Piper voice** before any voiced whisper ships.
6. **Housekeeping.** `~/Code/.img/MooshieUI/config.json` stores a Civitai API key in plain text. That is unrelated to the mod, but worth knowing if agents get read access there.
