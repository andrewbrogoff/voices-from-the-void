# Configuration and server administration

What an operator and a player can set, and what an operator can do. This covers:
- the **boundary** between config, datapack tuning, resource packs and code;
- the two **files**, where they live and which one is active;
- every **server key** and every **client key**: name, type, range, default, when a change applies, and how the client learns it;
- the **lifecycle**: loading, publishing a change, and what `/reload` does;
- the **starter opt-out** for worlds that can't host the Wellspring, and the command that places it by hand;
- the consolidated **`/vftv` command tree** and its permission;
- **validation**, **sync and trust**, the in-game **config screen**, the **lang keys** and the **compatibility** promise for keys.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the seams, the data rules and the threading rule;
- the [quality gates](quality-gates.md) for the test layers and the performance protocol;
- the [API spec](api.md) for the release-change rules this spec mirrors;
- every system spec that names a key or a command. Each owner keeps its mechanics; this spec gathers them into one surface and owns what they handed off.

Resolved in [Configuration and server-admin spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/76). Inputs: [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md) §§3 and 10 (tested switches for research difficulty, warp effects and spread; no chunkloader-only Hush).

**Source citations:** `MC:` is `minecraft-patched-26.2.0.88-sources.jar`, `NF:` the NeoForge 26.2.0.88 sources and `FML:` the FancyModLoader `loader-11.0.16` sources, which own config types, tracking and events; package prefixes (`net/minecraft/`, `net/neoforged/neoforge/`, `net/neoforged/fml/`) are omitted.

## Config, data and resources

Every knob has exactly one home. The test for a new one:

| Question | Home |
|---|---|
| Would two players on one server reasonably want different values (presentation, accessibility, HUD placement)? | **client config**, `vftv-client.toml` |
| Is it an on/off policy, a mode, a performance admission cap or a failure-mode opt-out that the **operator** owns? | **server config**, `vftv-server.toml` |
| Is it a number that shapes balance (a cost, rate, chance, threshold or range)? | **datapack tuning**, `data/vftv/tuning/<owner>.json`, each owner's "Tuning" ([architecture, Data and code](architecture.md#data-and-code)) |
| Is it a client-visual number (motes, overlay bands)? | **resource pack**: `assets/vftv/nodes/client.json`, `assets/vftv/essentia/client.json`, `assets/vftv/commonplace/overlay.json` |
| Is it a format or an invariant? | **code**, the `*Limits` constants in `api` |

So **no balance number lives in config.** The research presets' numbers stay in `research.json`; only the choice of preset is config ([research, Presets and modes](research.md#presets-and-modes)).

## Files

**Two types, two files** (`FML:config/ModConfig.java:87–125`; `FML:config/ConfigTracker.java:101–104`):

| Type | File | Holds |
|---|---|---|
| `ModConfig.Type.SERVER` | `vftv-server.toml` | the 19 server keys |
| `ModConfig.Type.CLIENT` | `vftv-client.toml` | the 11 client keys |

**No `COMMON` and no `STARTUP` file.** No key is needed before a server exists or on both sides unsynced, and registration is fixed by code and data. One is added only when a key needs it.

### Where they live

- **The server file has an instance base and a per-world override.** When a server starts, the active file is `<world>/serverconfig/vftv-server.toml` **if that file exists**, and otherwise `<instance>/config/vftv-server.toml` (`NF:server/ServerLifecycleHooks.java:78–105`; `FML:config/ConfigTracker.java:149–157`). So the instance file is the default for every world, and a world overrides it by holding its own copy. On a dedicated server, `<world>` is the `level-name` folder.
- **A missing active file** is created by copying it from the defaults directory, normally `<instance>/defaultconfigs/`, if a copy is there, and otherwise from the built-in defaults (`FML:config/ConfigTracker.java:59,250–258`; `FML:loading/FMLConfig.java:41`). A pack sets its policy (for example `research.mode = "skip"`) by shipping `defaultconfigs/vftv-server.toml`. Nothing copies a file into each world automatically.
- **The client file** is `<instance>/config/vftv-client.toml`. The client spec is registered only by the client entrypoint, so a dedicated server never creates or reads it.
- **The file watcher** reloads an edited file while the game runs, unless an operator turned automatic watching off in `fml.toml` (`FML:config/ConfigTracker.java:143–146`; `FML:loading/FMLConfig.java:36`). With watching off, an edit takes effect at the next server start.

### Code layout

Per [architecture, Seams](architecture.md#seams): **no `ModConfigSpec` type reaches `core`.**

- **`neoforge.config`** holds the server spec and its listener, the only code that touches them. **`neoforge.config.client`** holds the client spec, its listener and the config-screen registration.
- Each listener builds **one immutable record per feature** and hands it to that feature's `core` service ("Lifecycle").
- **Server records**, in the feature that reads them:

  | Record | Package | Keys |
  |---|---|---|
  | `ResearchConfig` | `core.research` | `research.mode` |
  | `WarpConfig` | `core.warp` | `warp.*` |
  | `HushConfig` | **`core.flux`**, so `FluxRules` can carry it while `core.flux` stays a leaf ([architecture, Layers](architecture.md#layers)) | `hush.*` |
  | `FocusConfig` | `core.focus` | `foci.block_effects` |
  | `GolemConfig` | `core.golem` | `golems.max_per_chunk` |
  | `VisConfig` | `core.vis` | `nodes.starter` |
  | `AspectConfig` | `core.aspect` | `aspects.derive_modded` |

- **Client records**, each in `core.<feature>.client`: `VisClientConfig`, `ArmorClientConfig`, `HushClientConfig`, `WarpClientConfig`, `ResearchClientConfig`.
- **Every record has a `DEFAULT`** equal to the tables below. A service holds `DEFAULT` until its first publish, and again after its server stops.
- **The ArchUnit rule `CONFIG_SIDES`** ([architecture, Enforcement](architecture.md#enforcement)): within `com.andrewbrogoff.vftv..`, a `*ClientConfig` type or a class in `neoforge.config.client..` is declared in, and used only from, `..client..` packages; and no `..client..` class uses a server `*Config` record or a class in `neoforge.config` outside `.client`. The client learns server switches only from the owners' rules records ("Sync and trust").

## Server keys

All 19 keys of `vftv-server.toml`. **Applies** is the first read of a changed value. **Sync** is how a client learns the value, if it needs it.

**Changes never reverse committed outcomes;** later operations use the new value at the apply points below. A placed or restored block, a paid Beam pulse, a spawned mob, an applied status effect, a standing Bargain offer and a running infusion's frozen plan all stay as they are. Where an owner says a running activity reads the switch again (a Beam's next pulse, the Hush's next service, a live Baffle capacity), that is its apply point.

| # | Key | Type | Range | Default | Applies | Sync | Owner |
|---|---|---|---|---|---|---|---|
| 1 | `research.mode` | enum | `gentle`, `standard`, `hard`, `skip` | `standard` | the next `begin`, and `buy`'s recheck at commit; an open note keeps its snapshot and can still be solved, learned or abandoned ([research, Presets and modes](research.md#presets-and-modes)) | `quotes.mode`, to an open Commonplace Book | [research](research.md#presets-and-modes) |
| 2 | `warp.voices.enabled` | bool | | `true` | the next check (suppression row 3, [warp, Suppression](warp.md#suppression)) | `WarpRules` | [warp](warp.md#tuning-and-limits) |
| 3–9 | `warp.voices.whisper`, `.insight`, `.sound`, `.colour`, `.phantom_mob`, `.bargain`, `.status` | bool each | | `true` each | the next check's eligibility: a type that is off is never eligible, so it never fires and the counter is untouched ([warp, Selection and draws](warp.md#selection-and-draws)) | none: eligibility is server-only, and a cue already names its presentation | warp |
| 10 | `warp.tax` | bool | | `true` | the next vis quote, and the next infusion assessment and start; a running infusion keeps the `Wp` frozen in its plan ([warp, The dials](warp.md#the-dials); [infusion, Stability](infusion.md#stability)) | `WarpRules` | warp |
| 11 | `warp.amplifier` | bool | | `true` | the next quote and cast; `amplifier_warp` is 0, the Joining bonus included, so a Void wearer's live Baffle capacity follows at once ([armor, Capacity](armor.md#capacity)) | `WarpRules` | warp |
| 12 | `warp.gear` | bool | | `true` | the next gear sample: the sample is 0 | `WarpRules` | warp |
| 13 | `hush.spread` | bool | | `true` | the next service; off makes no attempt at all and keeps the backlog, and on resumes it in due order ([Hush, Scheduling](hush.md#scheduling)) | `FluxRules` | [Hush](hush.md#tuning-and-limits) |
| 14 | `hush.hollows` | bool | | `true` | the next rise | `FluxRules` | Hush |
| 15 | `hush.unattended` | bool | | `false` | the next spread service **and** the next Pealing Bell service, with no wake needed: attendance is read at service ([Hush, Where it begins](hush.md#where-it-begins), [Pealing](hush.md#pealing)) | `FluxRules` | Hush |
| 16 | `foci.block_effects` | bool | | `true` | cast check 7, and every Beam pulse's session check: a running Delve Beam ends at its next pulse, and pulses already paid stay paid ([foci, Checks](foci.md#checks), [Beam](foci.md#beam)) | none: a server refusal only | [foci](foci.md#tuning-and-limits) |
| 17 | `golems.max_per_chunk` | int | 1–256 | 16 | the next placement; admission only, so lowering it removes no golem ([golems, Placing](golems.md#placing)) | `GolemRules` | [golems](golems.md#tuning-and-limits) |
| 18 | `nodes.starter` | enum | `required`, `best_effort`, `off` | `best_effort` | **the next server start only** ("The starter opt-out") | none | [wands](wands.md#the-starter) |
| 19 | `aspects.derive_modded` | bool | | `true` | the next server data load (a start or `/reload`), which rebuilds the `DerivedAspects` table, or leaves it empty when off ([aspects, Data maps](aspects.md#data-maps); [compatibility, Aspect values for modded items](compat.md#aspect-values-for-modded-items)) | none: the table itself is synced with the other aspect maps | [aspects](aspects.md#data-maps) |

- **`/vftv warp fire`** keeps the [warp spec's](warp.md#commands) rule: it skips eligibility, so it ignores keys 3–9, but not suppression, so it obeys key 2.
- **No other system has a key.** The [Eldritch tier](eldritch.md#contracts-on-sibling-specs) relies on peaceful and the warp and Hush switches; [worldgen](worldgen.md#contracts-on-sibling-specs) disables ruins through the structure set; the [Choristers](choristers.md#events) have no command and no option; aspects (beyond key 19), arcane, crucible, essentia and infusion keep their numbers in tuning.
- **Defaults** are the mod as designed, except `hush.unattended` off, so chunkloaded empty servers never rot (pain points §3). `research.mode` is `standard` ([progression](progression.md)); `nodes.starter` is `best_effort`, so a world with no candidate still opens and its first operator is told how to place the Wellspring ("The starter opt-out"); `aspects.derive_modded` is on, so modded items have values in play without an export.

## Client keys

All 11 keys of `vftv-client.toml`. Each applies at the **next client frame or tick after the value is saved and published**, and changes nothing on the server.

| # | Key | Type | Range | Default | On change | Owner |
|---|---|---|---|---|---|---|
| 1–2 | `vis_hud.x`, `vis_hud.y` | int | 0–4096 GUI px | 4, 4 | the frame moves, still clamped so it stays on screen ([wands, Display](wands.md#display)) | wands |
| 3 | `spectacles.card` | bool | | `true` | the card is hidden or shown ([armor, Drawing](armor.md#drawing)) | armor |
| 4 | `baffle.hud` | bool | | `true` | the row is hidden or shown ([armor, The HUD](armor.md#the-hud)) | armor |
| 5 | `hush.muffle` | bool | | `true` | off restores the seven category gains to 1.0 at the next tick; on reapplies 0.5 if the player is in the Hush ([Hush, Muffling](hush.md#muffling)) | Hush |
| 6 | `hush.hud` | bool | | `true` | the layer is hidden or shown ([Hush, Reading](hush.md#reading)) | Hush |
| 7 | `voices.text` | bool | | `true` | off hides a line on screen at once; the sound plays on | warp |
| 8 | `voices.colour_shift` | bool | | `true` | off ramps an active drain out, `intensity = max(0, intensity − 0.013)` per tick with frames interpolated, so even the 65% peak reaches zero within 50 ticks and never steps; on affects the next cue ([warp, Perception effects](warp.md#perception-effects)) | warp |
| 9 | `voices.phantoms` | bool | | `true` | off removes existing apparitions silently at the next tick; no new ones | warp |
| 10 | `voices.phantom_sounds` | bool | | `true` | off starts no new phantom sound; one playing finishes (≤ 3 s) | warp |
| 11 | `book.clear_prose` | bool | | `false` | the overlay is skipped from the next frame; the layout cache is untouched ([research, Prose that degrades with warp](research.md#prose-that-degrades-with-warp)) | research |

- **Renamed:** `vis_hud.x` and `vis_hud.y` were the wands spec's `hud.x` and `hud.y`, namespaced before the first release.
- **Accessibility** is the [localisation and accessibility spec's](accessibility.md) baseline: text with every Voice line, on by default and independent of vanilla's subtitles ([warp, Voice lines](warp.md#voice-lines)); state carried by shape, never colour alone ([Choristers, Marks](choristers.md#marks)); screen effects follow vanilla's `screenEffectScale` and `darknessEffectScale`, and Voice volume is vanilla's Voice slider ([warp, Accessibility](warp.md#accessibility)); transient text follows vanilla's notification time.
- **No further key.** HUD positions for the other HUDs, a particle cap and a "reduce motion" switch would govern nothing a spec defines: particle and motion budgets are fixed in each owner's Performance section, and nothing of ours moves the camera ([localisation, Photosensitivity and motion](accessibility.md#photosensitivity-and-motion)). One is added when a spec defines what it governs.
- **Defaults:** every option on, except `book.clear_prose` off: the degraded prose is the lore, and the Ledger shows exact warp either way.

## Lifecycle

### Loading

- **Server config loads before the server starts:** `ServerLifecycleHooks.handleServerAboutToStart` loads the active server file (`NF:server/ServerLifecycleHooks.java:78–105`, the load at `:102–105`), and runs before `handleServerStarted` (`MC:server/MinecraftServer.java:729`). So the starter reads `nodes.starter` correctly at `ServerStartedEvent`.
- **`ModConfigEvent.Loading` runs synchronously** on the loader's calling thread; **`ModConfigEvent.Reloading` may run on any thread** (`FML:config/ModConfig.java:75–84`; `FML:event/config/ModConfigEvent.java:23–42`). Both go through "Publishing a change".
- **When a server stops,** every server-config service resets to `DEFAULT` in our `ServerStoppedEvent` listener, so on an integrated client one world's switches never leak into the next. A remote client also sees config unloads, which is why the reset hangs off the server's own lifecycle instead.

### Publishing a change

The server listener, for `Loading` and `Reloading` of the `SERVER` config:

1. **Only a live local server publishes.** If `ServerLifecycleHooks.getCurrentServer()` is null, the event is ignored **before any value is read**. That is the case on a remote client, which receives the server's file through NeoForge's `ConfigSync` on connection and again after each reload, and raises `Reloading` for it (`NF:network/ConfigSync.java:45–65,68–98,103–132`; `FML:config/ConfigTracker.java:200–209`). Memory connections (single player) are skipped by that sync.
2. **Snapshot:** read every value once and build the seven records (pure). Capture the current server.
3. **Queue a guarded runnable** with `server.execute`. `execute` runs inline when called on the server thread, and also inline once the server has stopped (`MC:util/thread/BlockableEventLoop.java:103–109`; `MC:server/MinecraftServer.java:1506–1508`), so **the runnable guards itself:** it does nothing unless `getCurrentServer()` is still the captured server, that server is not stopped, and it is running on that server's thread. A late publish therefore never overwrites a later world's state.
4. **Apply:** each feature service swaps its record. For each record that **changed** (`equals`), its owner's re-send runs once:

   | Changed | Re-send |
   |---|---|
   | `WarpConfig`'s `enabled`, `tax`, `amplifier` or `gear` | `WarpRules` to every player |
   | `HushConfig` | `FluxRules` to every player |
   | `GolemConfig` | `GolemRules` to every player |
   | `ResearchConfig` | `quotes`, with the new `mode`, to every player with the Commonplace Book open, even if no price changed |

   A change to keys 3–9, 16, 18 or 19 alone sends nothing (key 19 reaches clients through the aspect maps at the next data load). Unchanged records send nothing.

**How an operator changes a key:** edit the active `vftv-server.toml` while the server runs (the watcher fires `Reloading`), or in single player use the config screen ("The config screen"). With automatic watching off, restart. There is **no `/vftv config` command**, and **`/reload` never touches config**: it reloads datapacks and tuning only. A config edit needs no `/reload`.

### Client config

The client listener (in `neoforge.config.client`) builds the five client records on `Loading` and `Reloading` of the `CLIENT` config and publishes them on the client thread, with `Minecraft.execute` when the event arrived on another thread. Consumers read them per frame or tick ("Client keys"). The config screen saves when its top-level section closes (`NF:client/gui/ConfigurationScreen.java:973–982`), which raises `Reloading`.

## The starter opt-out

The [wands spec](wands.md#the-starter) places one Wellspring near spawn at `ServerStartedEvent` whenever `starter` is absent or `REMOVED`, when a candidate satisfies its rules. Whether a world has a candidate depends on the terrain around the respawn position, not on its preset. Vanilla's *The Void* preset has a 33 × 33 stone platform spanning x, z −8 to 24, centred on (8, 8), with a cobblestone centre (`MC:world/level/levelgen/feature/VoidStartPlatformFeature.java:11–13,32–43`). The rings centre on the respawn position, not on the platform: with spawn at (0, 0), even ring 17 (active spawn protection 16) includes the platform column (17, 0). The spawn search itself scans a chunk from its minimum x and z (`MC:server/MinecraftServer.java:503–514`; `MC:server/level/PlayerSpawnFinder.java:178–187`). So neither every Void world nor every small skyblock lacks a candidate; **worlds with no open surface in reach of the rings start without a starter, and an operator places one by command.**

### Modes

`nodes.starter`, read once per start:

| Mode | While `starter` is absent or `REMOVED` |
|---|---|
| `best_effort` (default) | the ring search and placement; no candidate logs **one WARN** naming the radii and the bootstrap, and the server starts with no starter. It is retried at every start. In play, the first player with permission level ≥ 2 to join that server run gets one status line naming `/vftv node starter <pos>`, and an empty sounding says no Wellspring stands ([wands, The starter](wands.md#the-starter)). |
| `required` (opt-in) | the same search; **no candidate throws `IllegalStateException`** from the `ServerStartedEvent` listener, which the game bus rethrows (`EventBus.java:356–365`), so startup ends in a **crash report**. Its message names three remedies: lower `spawn-protection`, move the world spawn, or set `nodes.starter` to `best_effort` or `off` in the active `vftv-server.toml` (it names that file's path). For curated packs that want the hard check. |
| `off` | no search, no log, no join notice and no sounding line |

- **An existing starter is never touched** by any mode, and no mode warns when the spawn or protection later moves. Switching to `off` neither removes nor moves it.
- **What `best_effort` and `off` give up, where no candidate exists:** only the runtime promise that a Wellspring stands within 180 blocks of the bootstrap ([wands, The starter](wands.md#the-starter)). Every content lint still bounds Stage 1 costs by the Wellspring's 32 per aspect ([arcane, Recipes](arcane.md#recipes); [crucible, Recipes](crucible.md#recipes)), the Wellspring stays unhushable ([Hush, Hushed nodes](hush.md#hushed-nodes)), and the affordability paths still name `vftv:starter`. **Until a starter exists, Stage 1 vis comes only from natural nodes, which a void world may lack, so the world may be unwinnable until an operator runs `/vftv node starter`.** The key's comment says so.

### Before the first start

Only a pack that opted into `required` needs this: a world that would fail `required` must be opted out **before** it first starts:
- **One world** (a dedicated server, whose `level-name` folder can be prepared before its first start): create `<world>/serverconfig/vftv-server.toml` containing `[nodes]` and `starter = "best_effort"` (or `"off"`). The rest of the file is filled with defaults when it loads, and the file is backed up and rewritten ("Validation").
- **Every world of an instance, a pack, or a new single-player world** (which starts as soon as it is created): set the key in `<instance>/config/vftv-server.toml`, or ship it in `defaultconfigs/vftv-server.toml`, which is copied there when the instance file is missing.
- **After a failed start:** the active file was already loaded, and so written, before the starter ran ("Loading"); edit it and start again.

Then, once the server is up, place the Wellspring with `/vftv node starter <pos>`, by hand or from a pack's setup function: functions run at permission level 2 by default (`MC:server/dedicated/DedicatedServerProperties.java:93–98`).

### Placing the Wellspring by command

**`/vftv node starter <pos>`**, in the wands spec's node tree, works in every mode. Checks in order; the first failure refuses with its message, **reserves no id and changes nothing**:

| # | Check | Refusal |
|---|---|---|
| 1 | the source's level is the overworld, where the bootstrap and the sounding promise live ([wands, Identity and reconciliation](wands.md#identity-and-reconciliation)) | `commands.vftv.node.starter_overworld` |
| 2 | `pos` is loaded and inside the build height, by vanilla's `BlockPosArgument.getLoadedBlockPos` | `argument.pos.unloaded`, `argument.pos.outofworld` (`MC:commands/arguments/coordinates/BlockPosArgument.java:24–25,32–42`) |
| 3 | the `vftv_nodes` SavedData is readable, not kept as a raw tag ([wands, Sync and state](wands.md#sync-and-state)) | `commands.vftv.node.dormant` |
| 4 | `starter` is absent or its id is `REMOVED` | `commands.vftv.node.starter_exists` (the id) |
| 5 | `pos` is inside the world border; its block `isAir`; its cell holds fewer than 8 nodes; no indexed node is within `node.min_spacing` (the ring's rules 1, 2 and 6) | `commands.vftv.node.starter_blocked` |

**The commit is one birth, exactly as the automatic starter's:** `next_id` is taken; the node block and its block entity are set at `pos` with that id, type `vftv:wellspring`, `origin: starter`; the entry is recorded `PLACED` there; `starter` records the id and the current overworld respawn position as its bootstrap; the index is updated; the SavedData is marked dirty; the block update is sent. **Only then** is `NodeEvent(CREATED, COMMAND)` posted, once, and `commands.vftv.node.starter_placed` (the id and position) reported to the source and operators. The block entity's `onLoad` queues reconciliation, which finds `PLACED` here and does nothing.

**No standing-spot, reach or protection check:** the operator chose the spot. The placement resends `VisRules` with its `starter` flag set, so the book's `{fact:vis:starter}` updates at once.

## Commands

**One root, `/vftv`, with `requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))`**, permission level 2 (`MC:commands/Commands.java:163–167,554–555`; `MC:server/permissions/PermissionLevel.java:9–13`), as `/fill` does (`MC:server/commands/FillCommand.java:40`). Brigadier hides the whole tree from levels 0 and 1, so no subcommand repeats the check. One `neoforge` listener on `RegisterCommandsEvent` registers the root and attaches each feature's subtree, which is built in `core.<feature>` from vanilla Brigadier types only. Each owner's section is authoritative for what its commands change; this table is the index.

| Command | Arguments and bounds | Loaded-only rule | Owner |
|---|---|---|---|
| `research grant <player> <entry\|all>` | a `vftv:research` id, suggested | `<player>` online | [research](research.md#admin-commands) |
| `research revoke <player> <entry\|all>` | as above | online | research |
| `research stage <player> <stage>` | integer 0–4 | online | research |
| `research reset <player>` | | online | research |
| `warp get <player>` | | online | [warp](warp.md#commands) |
| `warp set <player> <temporary\|lasting\|permanent\|counter> <n>` | integer 0–10,000, then clamped to the kind's format bound (a permanent gain also to the cap) | online | warp |
| `warp add <player> <temporary\|lasting\|permanent> <n>` | integer −1,000–1,000, clamped the same way | online | warp |
| `warp clear <player>` | | online | warp |
| `warp fire <player> <event>` | a `vftv:voice_event` id, suggested | online | warp |
| `flux get [<pos>]` | default the executor's position | the chunk loaded | [Hush](hush.md#commands) |
| `flux add <pos> <amount>` | integer 1–1,000,000 | the chunk loaded | Hush |
| `flux clear <pos> [<radius>]` | integer 0–4 **chunks** (Chebyshev), default 0 | the centre chunk loaded; unloaded chunks in the square are skipped, never loaded | Hush |
| `flux repair <pos>` | | the chunk loaded | Hush |
| `hush restore <pos> <radius> [<from>]` | radius 0–16 **blocks**; `from` integer ≥ 0, default 0 | the centre chunk loaded; cells in unloaded chunks are skipped, never loaded, and count as examined | Hush |
| `node remove <id>` | `long` ≥ 1 | none: an unloaded node is tombstoned | [wands](wands.md#creation-removal-and-sharing) |
| `node starter <pos>` | "Placing the Wellspring by command" | loaded, overworld | wands |
| `road get <player>` | | online | [Eldritch tier](eldritch.md#commands) |
| `road progress <player> <progress>` | integer 0–3 | online | Eldritch tier |
| `road evacuate` | | online players in Vuulas | Eldritch tier |
| `aspects derive` | none; **level 4** | none: it reads server data, not positions | [compatibility](compat.md#the-derivation-export) |

- **`<player>`** is `EntityArgument.player()`: exactly one **online** player; selectors are available at level 2 (`MC:server/permissions/LevelBasedPermissionSet.java:15–18`). No command reaches an offline player. **`<pos>`** is `BlockPosArgument.blockPos()`, in the source's level (`execute in` picks another).
- **An out-of-bound integer fails at parse** with Brigadier's own error, so no handler sees one.
- **Feedback:** a query uses `sendSuccess(…, false)`; every mutation uses `sendSuccess(…, true)`, which also informs operators (`MC:commands/CommandSourceStack.java:466–479`). The result is the count changed (entries, restores, players) or 1.
- **All are level 2,** like `/fill` and `/tp`: none changes server settings, so none needs 3 or 4.
- **The one exception is the leaf `aspects derive`,** which also requires `Commands.LEVEL_OWNERS` (4), because it writes files. Vanilla's file-writing `/perf` and `/jfr` require the same (`MC:server/commands/PerfCommand.java:38`; `MC:server/commands/JfrCommand.java:31`). The root stays level 2, so a level-2 source sees the tree without that leaf.
- **No NeoForge permission nodes in v1:** the vanilla predicate keeps vanilla's permission model, which permission mods already map.

**Lang ownership of command keys:** each key belongs to the lang input of the system whose subtree holds the command, never to this spec's: `commands.vftv.research.*` to research's code-defined UI keys ([research, Admin commands](research.md#admin-commands)); `commands.vftv.warp.*` to `content/lang/<lang>/warp.json`; `commands.vftv.flux.*` and `.hush.*` to `hush.json`; `commands.vftv.node.*` to `wands.json`; `commands.vftv.road.*` to `eldritch.json`; `commands.vftv.aspects.*` to the [compatibility spec's](compat.md#lang-keys) code-defined keys, written by datagen. Each owner's lang table lists them.

## Validation

Keys are declared with the spec's own validators and we add **no second validator**:
- **ints** with `defineInRange`, **enums** with `defineEnum`;
- **booleans** with the class-aware overload `define(path, () -> default, v -> v instanceof Boolean, Boolean.class)`, never the plain boolean `define`. The plain form's validator accepts a quoted `"false"` and leaves it a string, which `BooleanValue.getAsBoolean` then casts to `Boolean` and throws (`NF:common/ModConfigSpec.java:754–758,1248–1249,1298–1300`). The class-aware form makes a quoted boolean a wrong type, so the existing correction defaults it, and keeps the screen's boolean metadata.

The native behaviour, which this spec adopts, for **an existing file that parses**:

| Input | Result |
|---|---|
| a number outside its range | **clamped**: `golems.max_per_chunk = 0` becomes 1, `= 300` becomes 256 (`NF:common/ModConfigSpec.java:1054–1061,1132–1133`) |
| a missing key, an unknown enum name, a value of the wrong type (a quoted boolean such as `"false"` included) | the key's default (`NF:common/ModConfigSpec.java:279–290`) |
| an enum name in another case (`"SKIP"`) | accepted (`NF:common/ModConfigSpec.java:686–703`) |
| a key the spec doesn't define | removed (`NF:common/ModConfigSpec.java:279–290`) |
| any of the above | **one file-level WARN**, a backup of the old file, and the file rewritten corrected (`FML:config/ConfigTracker.java:163–172`). The correction listener is empty, so no per-key warning is promised (`NF:common/ModConfigSpec.java:197–198`). |

**The other two loader branches** read a defaults template without that pass:
- **A missing active file** is copied from the defaults directory's template, or written from the built-in defaults (`FML:config/ConfigTracker.java:173–179,250–258`). There is no original to back up. If the template is partial or invalid (a pack's `defaultconfigs/vftv-server.toml` holding only `[research]` and `mode = "skip"`, say), `ModConfigSpec.acceptConfig` corrects it with the same per-value rules, logs a general warning plus per-value correction warnings, and saves it, with no backup of the copy (`NF:common/ModConfigSpec.java:113–125`).
- **A malformed file** is backed up, then recreated from the template or the built-in defaults, which are corrected the same way; if recreation fails, loading throws naming the file (`FML:config/ConfigTracker.java:181–194,250–258`).

The spec promises no warning count on either branch, only usable, corrected values and a rewritten file.

- **A typo'd switch takes its default:** a mistyped `hush.unattended` is **off**, and a mistyped `warp.voices.bargain` is **on**. The warning and the rewritten file are the operator's signal. The `vftv` logger raises no error for any of these ([quality gates, Logging](quality-gates.md#logging)).
- **The records are built only from corrected values,** so no service ever sees an out-of-range value or a value of the wrong type.
- **Every key carries an explicit English `.comment()`** ("Lang keys"), stating its effect, its range or allowed values and its default; when it applies is in the key tables ("Server keys", "Client keys"), and `nodes.starter`'s comment also says it is read at server start. The native machinery does not append a range or default to every kind of value (`NF:common/ModConfigSpec.java:372–377,734–758`), so the comment never relies on it.
- **`nodes.starter` is marked `worldRestart()`** (`NF:common/ModConfigSpec.java:834–840`), so the config screen flags it.

## Sync and trust

- **The server never reads client config.** A dedicated server never loads it, and `CONFIG_SIDES` keeps server code away from it on an integrated server, so a host's accessibility choices never change an outcome.
- **No client-to-server payload carries a config value.** (`VoiceQuiet` is screen state.)
- **The client never reads server config,** although NeoForge syncs the file to remote clients ("Publishing a change"). It learns exactly what it needs from the owners' records:

  | Record | Server values it carries | Sent |
  |---|---|---|
  | `WarpRules` | `warp.voices.enabled`, `warp.tax`, `warp.amplifier`, `warp.gear` (the book's `switch.*` facts and the dials, [research, Prose and fact references](research.md#prose-and-fact-references)) | join, `/reload`, and a publish that changed one of them |
  | `FluxRules` | `hush.spread`, `hush.hollows`, `hush.unattended` | join, `/reload`, a changed publish |
  | `GolemRules` | `golems.max_per_chunk` (the `limits.max_per_chunk` fact) | join, `/reload`, a changed publish |
  | `quotes` | `research.mode`, as one byte: 0 `gentle`, 1 `standard`, 2 `hard`, 3 `skip`; any other value is rejected at decode | to an **open** Commonplace Book only: when it opens, and on a changed publish |

  Keys 3–9, 16, 18 and 19 are absent from our owner payloads (NeoForge still syncs the whole file, which the client ignores).
- **At disconnect** the client drops every rules record and quote it holds, so a fact reads `[?]` until the next server sends its own, as each owner's "until the first … arrives" rule already says.
- **Client options are cosmetic by construction.** Turning `voices.phantoms` off removes apparitions, which stand for nothing in the world ([warp, Phantoms](warp.md#phantoms)); no option reveals anything the server hides.

## The config screen

- **The client entrypoint registers NeoForge's `ConfigurationScreen`** through `IConfigScreenFactory` (`NF:client/gui/IConfigScreenFactory.java:16–26`; `NF:client/gui/ConfigurationScreen.java:98–103`), so a player changes accessibility options in game, from the mods list.
- **Server config in the screen:** ordinary single player can edit the open world's active server file; editing is disabled on a remote server, when no server config is loaded, and when an integrated server is open to LAN (`NF:client/gui/ConfigurationScreen.java:290–301`). An edit saves when its top-level section closes and is published as under "Publishing a change".
- **Tooltips** are translated: the screen shows `<translation>.tooltip` when that key exists, and falls back to the key's comment only when it doesn't (`NF:client/gui/ConfigurationScreen.java:562–575`). Every key carries `options.vftv.<path>.tooltip` ("Lang keys"), so a translated locale never shows our English comment.
- **Enum values display translated:** the screen shows a value through NeoForge's `TranslatableEnum` and otherwise its literal `name()` (`NF:client/gui/ConfigurationScreen.java:790–799`). `core`'s enums can't implement a NeoForge type ([architecture, Layers](architecture.md#layers)), so `neoforge.config` declares the spec's two enums as display adapters implementing `TranslatableEnum`, each value mapped one-to-one onto the `core` enum when the records are built. The TOML values and identifiers stay the English names, unchanged ("Compatibility").

## Lang keys

Every key and section carries `.translation("options.vftv.<path>")`. Eight keys are owned, and already listed, by their systems' lang tables: `options.vftv.spectacles.card` and `.baffle.hud` ([armor](armor.md#lang-keys)), `.hush.muffle` and `.hush.hud` ([Hush](hush.md#lang-keys)), and `.voices.text`, `.voices.colour_shift`, `.voices.phantoms`, `.voices.phantom_sounds` ([warp](warp.md#lang-keys)). **This spec owns every other `options.vftv.*` key**, below. Its English is final, not a sense: datagen writes these as code-defined UI keys into `assets/vftv/lang/en_us.json`, as the research spec's UI keys are written, so no `content/` file and no production ticket is needed. None has a placeholder. Translations go in `content/lang/<lang>/config.json` ([localisation, Files](accessibility.md#files)).

**Tooltips and enum values** are this spec's too, for all 30 keys, the eight owner-held ones included:
- **`options.vftv.<path>.tooltip`** for every key: its English is the key's comment below, verbatim.
- **Enum values:** `options.vftv.research.mode.gentle`, `.standard`, `.hard`, `.skip` ("Gentle", "Standard", "Hard", "Skip: buy research with points") and `options.vftv.nodes.starter.required`, `.best_effort`, `.off` ("Required", "Best effort", "Off").

**Sections:**

| Key | Text | File |
|---|---|---|
| `options.vftv.research` | Research | server |
| `options.vftv.warp` | Warp | server |
| `options.vftv.warp.voices` | The Voices | server |
| `options.vftv.hush` | The Hush | both (one key) |
| `options.vftv.foci` | Foci | server |
| `options.vftv.golems` | Golems | server |
| `options.vftv.nodes` | Nodes | server |
| `options.vftv.aspects` | Aspects | server |
| `options.vftv.vis_hud` | Vis HUD | client |
| `options.vftv.spectacles` | Tuned Spectacles | client |
| `options.vftv.baffle` | The Baffle | client |
| `options.vftv.voices` | The Voices | client |
| `options.vftv.book` | The Commonplace Book | client |

**Keys, with their file comments** (the comment is English-only TOML text in code, not a lang key):

| Key | Text | Comment |
|---|---|---|
| `options.vftv.research.mode` | Research difficulty | "gentle, standard or hard notes, or skip: buy research with aspect points instead. Open notes keep their difficulty. Default: standard." |
| `options.vftv.warp.voices.enabled` | Voices | "Voice events at all. Off: no check rolls. Default: true." |
| `options.vftv.warp.voices.whisper` | Whispers | "Whisper events. Off: never chosen. Default: true." |
| `options.vftv.warp.voices.insight` | Insights | "Insight events (aspect points). Off: never chosen. Default: true." |
| `options.vftv.warp.voices.sound` | Sounds | "Phantom sound events. Off: never chosen. Default: true." |
| `options.vftv.warp.voices.colour` | Colour drain | "Colour drain events. Off: never chosen. Default: true." |
| `options.vftv.warp.voices.phantom_mob` | Apparitions | "Apparition events. Off: never chosen. Default: true." |
| `options.vftv.warp.voices.bargain` | Bargains | "Bargain offers. Off: no new offer; a standing one stays. Default: true." |
| `options.vftv.warp.voices.status` | Status effects | "Status-effect events in context. Off: never chosen. Default: true." |
| `options.vftv.warp.tax` | Warp vis tax | "Warp raises vis costs and lowers infusion stability. Off: neither. Running infusions keep their plan. Default: true." |
| `options.vftv.warp.amplifier` | Warp amplifier | "Warp strengthens Void workings and Void armour. Off: no amplification. Default: true." |
| `options.vftv.warp.gear` | Gear warp | "Warp from worn and carried gear. Off: gear adds none. Default: true." |
| `options.vftv.hush.spread` | Hush spreads | "Flux turns ground into the Hush. Off: no spread; flux still pools. Default: true." |
| `options.vftv.hush.hollows` | Hollows rise | "Hollows rise from the Hush. Off: none rise. Default: true." |
| `options.vftv.hush.unattended` | Spread without players | "Hush spread and Pealing Bells run with no player nearby. Default: false." |
| `options.vftv.foci.block_effects` | Foci change blocks | "Foci that break or change blocks. Off: refused; a running Beam stops at its next pulse. Default: true." |
| `options.vftv.golems.max_per_chunk` | Golems per chunk | "Most golems placed in one chunk. Placement only; never removes golems. Range: 1 to 256. Default: 16." |
| `options.vftv.nodes.starter` | Starter Wellspring | "best_effort: place it near spawn if possible, else warn and tell the first operator to join. required: place it or crash at startup. off: never place it. Without one the world may be unwinnable until /vftv node starter <pos>. Read at server start. Default: best_effort." |
| `options.vftv.aspects.derive_modded` | Derive modded aspects | "Give items without authored aspects values derived from their recipes, at each data load. Authored values win; {} suppresses. Off: no derived values. Default: true." |
| `options.vftv.vis_hud.x` | Vis HUD x | "Left edge of the vis HUD, in GUI pixels. Range: 0 to 4096. Default: 4." |
| `options.vftv.vis_hud.y` | Vis HUD y | "Top edge of the vis HUD, in GUI pixels. Range: 0 to 4096. Default: 4." |
| `options.vftv.book.clear_prose` | Clear prose | "Show the Commonplace Book without warp's marginalia and strikeouts. Default: false." |

The eight owner-held keys get comments in the same form: `spectacles.card` "Show the Tuned Spectacles card. Default: true."; `baffle.hud` "Show the Baffle row above the hotbar. Default: true."; `hush.muffle` "Quieten sounds inside the Hush. Default: true."; `hush.hud` "Show the Clearing Bell's reading. Default: true."; `voices.text` "Show the words of every Voice line. Default: true."; `voices.colour_shift` "Let the Voices drain colour from the screen. Default: true."; `voices.phantoms` "Show apparitions. Default: true."; `voices.phantom_sounds` "Play phantom sounds. Default: true." Enum values show through their translated names (above); the comment text names the TOML values.

## Compatibility

Config keys are an **operator surface**, frozen like a public data format ([API, What each release may change](api.md#what-each-release-may-change)), and the release checklist reviews the diff of the two spec classes:

| Change | Patch | Minor | Major |
|---|---|---|---|
| a comment or label | ✓ | ✓ | ✓ |
| add a key (with a default, so old files stay valid), widen a range, add an enum value | | ✓ | ✓ |
| change a default, named in the release notes | | ✓ | ✓ |
| rename, remove, retype or narrow a key, or remove an enum value | | | ✓ |

A changed default reaches new files only: an existing instance or world file keeps the value already written in it.

## Performance

Configuration has **no tick workload and no hot path of its own:** a read is a field load of an immutable record, charged to the owner's hot path that makes it, with no allocation. Its share of the [whole-mod envelope](quality-gates.md#whole-mod-envelope) is 0. **Publishing** is rare and bounded: seven records, then at most three rules payloads per player plus one `quotes` per open book. It is measured as an explicit **load scenario**, reported apart from steady state ([quality gates, Reference](quality-gates.md#reference)): the reference stress world's player plus the E2E's connected client, with every server key flipped at once. It adds nothing to the reference stress world's steady-state workload.

## Tests

**Unit:**
- each record's `DEFAULT` equals the tables above;
- native correction of an **existing, parseable** file, pinned by building the records: `golems.max_per_chunk = 0` gives 1 and `300` gives 256; `research.mode = "easy"` and `warp.tax = 7` give their defaults; `research.mode = "SKIP"` gives `skip`; **`warp.tax = "false"` gives `true` and `hush.unattended = "true"` gives `false`** (quoted booleans are wrong types), while unquoted `false` and `true` keep their values; an unknown key is removed; a missing key is defaulted; one file-level WARN, a backup and a rewritten file;
- every boolean key is declared through the class-aware `define` overload (a test over the spec's value specs: each boolean's class is `Boolean`);
- the template branches, asserting corrected, usable records and rewritten output, **with no exact warning count:** a missing active file with a partial template (`[research]`, `mode = "skip"` only) gives `skip` and every other key's default; a malformed active file with that template is backed up and recreated with the same records;
- the active path: an instance file alone is used; a world override wins over it; a missing instance file is copied from `defaultconfigs/`;
- publishing: no current server ignores the event before any value is read; a remote synced `Reloading` is ignored; same-thread inline; off-thread queued; a runnable that runs after its server stopped, or after a different server started, does nothing; `equals`-gated re-sends (exactly one per changed record, none when unchanged; keys 3–9, 16, 18 and 19 send nothing); `ServerStoppedEvent` resets every record;
- `quotes.mode`: each byte round-trips; 4 and 255 are rejected at decode.

**ArchUnit:** `CONFIG_SIDES`, with a fixture for each forbidden direction (server code using a `*ClientConfig`; a `..client..` class using `WarpConfig` or the server spec) and a permitted `neoforge.config.client` adapter.

**GameTest:**
- every server key **off and on**, owned and already required by its owner: [warp](warp.md#tests) (every switch), [Hush](hush.md#tests) (spread, Hollows, unattended spread, and a **Pealing Bell with no player near it** idle with `hush.unattended` off and pealing with it on), [foci](foci.md#tests) (`foci.block_effects` off refuses Delve and **ends a running Delve Beam at its next pulse with the paid pulses kept**), [golems](golems.md#tests) (placement up to the cap; a lowered cap removes no golem), [research](research.md#tests) (each preset and skip; a mode switch mid-note keeps the snapshot);
- **the starter modes**, with an impossible candidate set: `best_effort` (the default, with no file present) starts with one WARN and no starter, retries at the next start, sends the first joining level-2 player one status line naming `/vftv node starter <pos>` and a level-0 player and a second level-2 player none; `required` throws `IllegalStateException` from the `ServerStartedEvent` listener with the three remedies and the active file's path; `off` is silent, with no notice; an existing starter is untouched by every mode;
- **`aspects.derive_modded`**, owned by [aspects](aspects.md#tests): on, a modded item with a recipe and no authored entry gets a derived value after a data load; off and `/reload`, it has none; an authored entry and `{}` win either way;
- **`/vftv node starter`:** places a Wellspring with one `NodeEvent(CREATED, COMMAND)` and the starter, index and dirty state set before it; refuses a second; refuses a nether source, an unloaded and an out-of-world position, a dormant `vftv_nodes`, a non-air block, a position outside the border, a full cell and a node within spacing, each with its message, reserving no id;
- **commands:** a level-0 source can neither run nor see `/vftv`; a level-2 source runs every command except `aspects derive`, which it can neither run nor see, and a level-4 source runs that too; every bounded argument rejects its neighbours (stage 5, progress 4, amount 0 and 1,000,001, radius 5 and 17, `add` ±1,001, `set` 10,001); `flux clear` with radius 4 beside unloaded chunks loads none of them; `hush restore` across an unloaded chunk loads none and reports the continuation index;
- each test id in the discovery guard.

**E2E** ([quality gates, E2E](quality-gates.md#e2e-mc-agent-harness)):
- on a dedicated server with a connected client, editing the active file (`warp.tax`, `hush.unattended`, `golems.max_per_chunk`, and `research.mode = "skip"` with the book open) changes the client's dumped `WarpRules`, `FluxRules`, `GolemRules` facts and the Buy buttons, with no `/reload`; with automatic watching off, nothing changes until a restart;
- reconnecting shows the same values; after disconnecting, the facts read `[?]` until the next join;
- two single-player worlds, one with a world override, keep their own values across a switch between them;
- every client option toggled through the config screen applies live (screenshots and dumps; the colour drain turned off at its 65% peak reaches zero within 50 ticks with no frame-to-frame step above 1.3%); the server section is read-only on a remote server and on a LAN-opened world.

**Language:** every key, section, tooltip, enum value and command key above exists in `en_us`, with matching placeholder counts; each `.tooltip` equals its key's comment. The pseudo-locale check of translated tooltips and enum values moves to the second-locale ticket ([localisation](accessibility.md)).

## Contracts on sibling specs

Each spec is amended in this change, as listed. Each amendment is a criterion in that system's build ticket.

- **[Architecture](architecture.md):** "Seams" (the config row: per-feature records, the guarded publish and the `equals`-gated re-sends), "Data and code" (client config, the boundary, a link here), "Threading" (config publication) and "Enforcement" (`CONFIG_SIDES`).
- **[Wands](wands.md):** "The starter" (`best_effort` by default with the op join notice, the sounding line and the `starter` fact; `required` as an opt-in `IllegalStateException` naming the three remedies and the active file's path; the terrain wording instead of a categorical void claim; no later-start warning);
- **[Aspects](aspects.md):** the `DerivedAspects` table behind `aspects.derive_modded`; "Identity and reconciliation" and "Creation, removal and sharing" (the command birth); `/vftv node starter` and its lang keys; `vis_hud.x` and `vis_hud.y`; the tests and the acceptance row.
- **[Research](research.md):** `quotes.mode` and its re-send; the reload table's server-config row; the command lang keys.
- **[Golems](golems.md):** `GolemRules` re-sent on a changed cap; the reload table's server-config row.
- **[Warp](warp.md):** `WarpRules` carries exactly four switches; the reload row's apply points; the client options' on-change behaviour.
- **[Hush](hush.md):** the reload event and row; spread and bell attendance under `hush.unattended`; the commands' loaded-only regions.
- **[API](api.md):** a pointer from "What each release may change" to "Compatibility" here.
- **Unchanged:** foci's pulse contract, armor's live derived values, every tuning file, progression, quality gates and `CONTEXT.md` stay authoritative, and this spec conforms to them.

## Production tickets

**None.** Every string is final in "Lang keys" and is written by datagen; there are no assets and no authored content. The build ticket for configuration delivers both spec classes, the listeners, the screen registration, `CONFIG_SIDES`, `/vftv node starter` and every test above.
