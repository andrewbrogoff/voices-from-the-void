# Licensing notice

Voices From The Void
Copyright © 2026 Andrew Rogoff and contributors

This file is the **authoritative** map of which licence covers which file. [ADR 0005](docs/adr/0005-licensing.md) explains why. Licences apply to the extent we hold copyright or similar rights. They cannot create rights in material that has none (e.g. output that is purely machine-generated), and such material is labelled honestly in [`docs/assets-provenance.md`](docs/assets-provenance.md).

| Licence | SPDX | Text |
|---|---|---|
| Mozilla Public License 2.0 | `MPL-2.0` | [`LICENSE`](LICENSE) |
| MIT | `MIT` | [`LICENSE-API`](LICENSE-API) |
| Creative Commons Attribution 4.0 International | `CC-BY-4.0` | [`LICENSE-ASSETS`](LICENSE-ASSETS) |

The MPL-2.0 is used without Exhibit B, so it stays compatible with the GPL and LGPL under its §3.3.

## Which licence covers what

Apply the first rule that matches.

1. **Third-party material** keeps its own licence and notices (e.g. the Gradle wrapper, quoted examples). Nothing below overrides it.
2. **Branding** and **Voice recordings** are covered by their own sections below.
3. **`MIT`:** everything in the `api` source set (`src/api/**`, Java and resources), and original illustrative code examples in the docs.
4. **`MPL-2.0`:** executable code, wherever it is stored:
   - Java in every other source set (`src/main/**`, `src/datagen/**`, `src/test*/**`, `src/archTest/**`, `src/jmh/**`)
   - build files (`*.gradle*`, `settings*`, `gradle/**` except the wrapper)
   - scripts and tools, including asset generators (`tools/**`, and scripts kept under `art/**`)
   - shaders and post-effect pipelines (`**/shaders/**`, `**/post_effect/**`, `*.glsl`, `*.vsh`, `*.fsh`)
   - functions (`**/*.mcfunction`)
   - CI workflows (`.github/**`)
5. **`CC-BY-4.0`:** creative and declarative content, including generated copies:
   - client resources: `src/*/resources/assets/**` (textures, models, blockstates, item definitions, sounds, particles, lang), except the code in rule 4
   - datapack content: `src/*/resources/data/**` (recipes, aspect data maps, research entries, Voice events, worldgen, loot), except the code in rule 4
   - authored content inputs that datagen reads: `content/**` (research entries, page descriptors, book prose, aspect tables), except the code in rule 4
   - editable asset sources (`art/**`: `.blend`, `.bbmodel`, palettes), except scripts
   - documentation: `docs/**` (specs, ADRs, research, provenance), `CONTEXT.md`, `README.md`, this file
6. **Anything else** owned by the project: `MPL-2.0`.

Generated files follow the function of the file, not the licence of the generator that wrote them.

## Attribution for CC-BY-4.0 material

Any reasonable placement is fine; no in-game credit screen is required.

> Voices From The Void by Andrew Rogoff and contributors — [project](https://github.com/andrewbrogoff/voices-from-the-void), [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Changes: *describe them, if any*.

## Branding

The name **Voices From The Void**, the icon (`assets/icon.svg`) and the logo art (`voices_from_the_void.png`) are **not** under any of the licences above. They are reserved, both as marks and as artwork. Permission is granted to copy and display them:
- inside unmodified official builds and their redistributions, including modpacks (commercial ones too);
- in modpack listings, reviews, videos, streams and other truthful references to the mod.

**A modified version that is distributed publicly** must use its own name and art. It may still say what it is based on (e.g. "a fork of Voices From The Void") and state compatibility. Forks **do not** need to change the mod id `vftv`, registry ids, Java packages or historical notices, because that would break saves and addons.

## Voice recordings

The Voices are processed recordings of Andrew Rogoff's voice. The raw takes are not published, and are not among the editable sources this project commits.

The **released, processed clips** are not under CC-BY-4.0. Instead, everyone gets a worldwide, royalty-free permission, irrevocable while its terms are followed, to:
- redistribute the clips with this mod, its forks and ports, and modpacks, including commercial ones;
- convert their format and make technical playback adjustments needed for that;
- include them in gameplay videos and streams, including monetised ones.

**Condition:** keep the recording credit ("Voice recordings by Andrew Rogoff") and this permission notice with the clips.

**Not granted:** standalone reuse of the clips, creative remixing of the recordings, or using them to train voice models. Andrew Rogoff keeps all other rights. This permission doesn't imply that he endorses any downstream project.

## Modpacks and redistribution

Modpacks may include the mod anywhere, commercially or not, without asking. We'd appreciate a link to the official download page rather than a reupload, but that is a request, not a condition. Every release links to its corresponding source, as the MPL requires.

## Contributions

By contributing, you agree that your contribution is licensed under whichever licence this file assigns to the path you change, and that you have the right to license it that way. There is no CLA.
