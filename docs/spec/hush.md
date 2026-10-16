# Flux and the Hush

Where the residue of careless magic goes, and what it does to the land. This covers:
- **flux**: every source, the pure functions that price a spill, and how generated flux is retained, held or lost to overflow;
- the **aura**: the flux a chunk holds, pooled at its **seep**;
- **the Hush**: where it begins, the seeded, budgeted walk that spreads it, and the thirteen **hushed blocks** it leaves, each exactly reversible;
- **Hollows**, the one creature the Hush raises, and **hushed nodes**;
- the counterplay: the Stage 2 **Clearing Bell** and the Stage 3 **Pealing Bell**;
- the Hush as a Void-touched place for warp, its muffled sound, and the guarantee that it is only ever caused by a player.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the data and sync rules and the seams;
- the [quality gates](quality-gates.md) for every gate;
- the [lore](lore.md) and [progression](progression.md) specs for what the Hush is and the rules it keeps;
- the [wands spec](wands.md) for the quote, `VisSpentEvent` and `NodeService`;
- the [crucible](crucible.md), [essentia](essentia.md), [infusion](infusion.md) and [golems](golems.md) specs for the spill events;
- the [warp spec](warp.md) for the place query;
- the [research spec](research.md) for entries, fact spans, `mix64` and `fold`;
- the [art style](art-style.md) for every asset.

Resolved in [Flux and the Hush spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/26). Inputs: [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md) (§4, Flux and taint), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md) (§3, taint), [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md) (§5, nodes), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4: TC4's goo units, random-tick chances, taint reach, scrubber cube and node cadence are not reused, and nothing here is a TC4 ratio rescaled. Each tunable number is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

**Renamed coinages** ([lore, Vocabulary policy](lore.md#vocabulary-policy)): taint → **the Hush**; tainted soil and crust → **hushed blocks**; tainted animals, Taintacles, crawlers and swarms → one creature, the **Hollow**; Flux Goo and Gas → nothing (flux is never a block); Ethereal Bloom and the Flux Scrubber → the **Clearing Bell** and the **Pealing Bell**; tainted nodes → **hushed nodes**.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port. Both print the same 82 lines (2,177 bytes, SHA-256 `bc71762ad18418e5ebac5047eca25e269900ce24cfb4b4adfa7a3712dd391140`). The references cover arithmetic, streams and the walk on a synthetic world only: their agreement is no evidence for scheduling, callbacks, persistence or performance, which the build's GameTests prove (see "Tests").

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 2 Distillation | flux from the crucible, jars and golems; the aura, the Hush, hushed blocks and nodes, Hollows; the Clearing Bell | the opener `vftv:distillation`, whose find `vftv:find/distillation` is [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s ([research, Stage](research.md#stage)); then the entry below |
| 3 Ruins | flux from infusion and from the warp tax on vis; the Pealing Bell | the opener `vftv:ruins` (#27's find); then the entry below |
| 4 The Choir's Road | flux from Void work | the opener `vftv:choirs_road` ([Eldritch tier](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)'s find) |

| Entry | Stage | Kind | Parents | Gates |
|---|---|---|---|---|
| **`vftv:clearing_bell`** | 2 | `note` | `vftv:resonant_brass` | the arcane recipe `vftv:clearing_bell` |
| **`vftv:the_hush`** | 3 | `note` | `vftv:infusion`, `vftv:clearing_bell` | the infusion recipe `vftv:pealing_bell` |

- **Flux and the Hush are world physics, not research.** They run whatever anyone has learned. Their first source is the crucible ([crucible, Spilling](crucible.md#spilling)), so the counterplay sits beside Resonant Brass, its material, and is learnable before anyone can have hushed anything ([progression, TC4 systems](progression.md#tc4-systems-kept-redesigned-cut): "counterplay available early").
- **The early bell is provably early.** Stage 2's affordability path learns `vftv:clearing_bell` immediately after `vftv:resonant_brass` ([research, Affordability and recovery](research.md#affordability-and-recovery), amended), and its recipe is constrained to be built spill-free from that point (see "Authored content, Recipes").
- **No find of our own.** A find on a hushed block would make an entry depend on having caused the Hush, which no affordability path could stage.
- **Options considered:** everything in Stage 3, as the research spec's provisional roster listed "the Hush". **Why not:** flux exists from the crucible on, so Stage 3 counterplay would leave Stage 2 spills uncured, the exact TC4 complaint ([pain points §3](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md): "the cure comes late").

## Flux

### Sources

Flux enters the world **only** through **`FluxService.deposit`** (see "Depositing"), called by exactly these producers:

| Producer | Event | Generated | Stage |
|---|---|---|---|
| a vis spend | `VisSpentEvent` ([wands, Events](wands.md#events)) | **that event's `quote.spill`**, never recomputed | 3 (the warp tax), 4 (Void work) |
| a crucible emptied or removed | `CrucibleEvent(SPILLED)`, cause `EMPTIED` or `REMOVED` ([crucible, Events](crucible.md#events)) | `essentiaFlux(spilled, cause)` under the current rules | 2 |
| a filled jar item destroyed; a jar's unsettled excess | `EssentiaEvent(SPILLED)`, cause `DESTROYED` or `REMOVED` ([essentia, Events](essentia.md#events)) | likewise | 2 |
| an infusion upset, abandon or bowl removal | `InfusionEvent(SPILLED)`, cause `UPSET`, `ABANDONED` or `REMOVED` ([infusion, Events](infusion.md#events)) | `essentiaFlux(spilled, cause, rules)` with **the rules the event carries** in `InfusionEvent.fluxRules()` (on the event, not in its payload: [API spec, The events](api.md#the-events)): a run's frozen ones for `UPSET`, the current ones otherwise. An `UPSET` from a run frozen before this spec landed carries none and generates **0**, matching the bound its readout showed | 3 |
| a golem's death with claimed essentia | `GolemEvent(SPILLED)`, cause `DIED`, at its death position ([golems, Events](golems.md#events)) | `essentiaFlux(spilled, DIED)` | 2 |
| a Pealing Bell removed holding Sonus | its own removal hook (see "The Pealing Bell") | `essentiaFlux({Sonus: n}, REMOVED)` | 3 |
| an operator | `/vftv flux add` | as typed | |

- **Once per delivered occurrence.** One `neoforge.hush` listener per sibling event, subscribed at **`EventPriority.HIGHEST`**, calls `deposit` and never throws (an exception is caught and logged once). It is **not** a guarantee against every subscriber: another `HIGHEST` subscriber registered earlier can throw first, and NeoForge's bus stops dispatch and rethrows (`BUS:ListenerList.java:164–166`, `EventBus.java:356–365`). That is the siblings' committed-notification-failure case (for example `COMMITTED_NOTIFY_FAILED`, [wands, The spend](wands.md#the-spend)), not ours to repair. An addon that reposts a sibling event causes a second deposit; nothing deduplicates arbitrary reposts.
- **Foci** add no producer: casts spill through `VisSpentEvent`, and a Delve or Exchange that breaks a crucible, jar or ringing bowl triggers that sibling's own event ([foci, Contracts](foci.md#contracts-on-sibling-specs)).
- **Not sources:** tapping, relays, regeneration, cultivation and capture ([wands](wands.md#contracts-on-sibling-specs)); dissolving, crafting, refusing and extraction ([crucible](crucible.md#contracts-on-sibling-specs)); moving, pouring, labels, valves, stalls and caps ([essentia](essentia.md#contracts-on-sibling-specs)); drawing, absorbing, stalls, pauses and finishing ([infusion](infusion.md#contracts-on-sibling-specs)); golem carrying; the Baffle ([armor, The Baffle](armor.md#the-baffle)); Bargains, stilling and warp of every kind ([warp](warp.md#sources)); worldgen, structures, biomes and time.
- **Options considered:** a flux trickle from every spend. **Why not:** Stage 1 would spill from its first craft, before any counterplay, and the [progression spec](progression.md#warp) names exactly what ordinary magic spills: "the more you sound like the Void, the more your resonant magic … spills in flux."

### The pinned functions

In **`core.flux.model`**, Minecraft-free, over primitive inputs; `core.flux` is a leaf feature ([architecture, Layers](architecture.md#layers), amended), so `core.vis`, `core.crucible`, `core.infusion` and `core.hush` call it without a cycle.

```
visSpill(kind, effective[], neutral[], rules):            // computed inside the quote, never from a quote
  rules absent → 0
  ORDINARY: s = ceilDiv(Σ_a max(0, effective[a] − neutral[a]) × flux.tax_pm, 1000)
  VOID:     s = ceilDiv(Σ_a effective[a] × flux.void_pm, 1000)
  return min(s, MAX_SPILL_FLUX)

essentiaFlux(spilled, cause, rules)    = rules absent ? 0 : min(MAX_SPILL_FLUX, ceilDiv(total × flux.essentia_pm, 1000))
essentiaFluxBound(units, cause, rules) = rules absent ? 0 : min(MAX_SPILL_FLUX, ceilDiv(units × flux.essentia_pm, 1000))
essentiaFlux(spilled, cause)           = essentiaFlux(spilled, cause, the current rules)
```

- **`neutral`** is the effective cost the same quote would charge at warp factor 1.0: the [wands quote's](wands.md#the-quote) own multiplier, rounding and clamping with `w = 1000`, computed by `core.vis.model` alongside `effective` (amended there). So the warp tax spills **exactly the vis it actually added**: a Listener at zero warp spills nothing doing ordinary work, and a surcharge the multiplier's clamp swallowed spills nothing either. `VOID` work, whose factor is 1.0 ([warp, The dials](warp.md#the-dials)), spills its own fixed share.
- **Essentia:** one rate for every cause. The crucible HUD's "Emptying it spills *n*" is then also exactly what breaking it spills (its HUD previews `EMPTIED` only), and the bound is exact: `essentiaFluxBound(u)` equals `essentiaFlux(m)` for every `m` totalling `u`, and is monotone in `u`. `cause` stays in the signature the siblings froze.
- **Inputs:** `total` sums every amount, **missing ids included** ([crucible, The pool](crucible.md#the-pool)), in `long`. A total above `MAX_ESSENTIA_SPILL` (640,000, [essentia's](essentia.md#contracts-on-sibling-specs)) or a negative amount is the caller's data error and throws. `effective` and `neutral` hold at most 8 entries of 0–10,000.
- **Bounds:** crucible ≤ 10,000 in → ≤ 10,000 out; essentia ≤ 640,000 → ≤ 640,000; infusion ≤ 8,000; golem ≤ 1,000; Pealing Bell ≤ 1,000; a vis spill ≤ 80,000 by construction (8 × 10,000 at 1000 ‰). All within 0–1,000,000.
- **Rules:** **`FluxRules`** (see "Sync and state") carries every key of "Tuning and limits". Until it arrives on a client, every client preview of flux is the typed zero; a server echo then refuses the stale quote ("The cost has changed."), as for any changed quote input ([wands, The quote](wands.md#the-quote)).

**Vectors** (default rates: `tax_pm` 1000, `void_pm` 250, `essentia_pm` 1000; `quote.min_multiplier` 0.50, `quote.max_multiplier` 4.00; `cap`, `d` and `w` per mille as in the wands quote):

| `cap` | `d` | `w` | base | kind | effective | neutral | surcharge | spill |
|---|---|---|---|---|---|---|---|---|
| 1000 | 0 | 1000 | 10, 0, 1 | ordinary | 10, 0, 1 | 10, 0, 1 | 0 | **0** |
| 850 | 300 | 1000 | 10, 3, 1 | ordinary | 6, 2, 1 | 6, 2, 1 | 0 | **0** |
| 850 | 100 | 1250 | 32, 7 | ordinary | 31, 7 | 25, 6 | 7 | **7** |
| 2000 | 0 | 10000 | 1000 | ordinary | 4000 | 2000 | 2000 | **2000** |
| 250 | 900 | 1000 | 5 | ordinary | 3 | 3 | 0 | **0** |
| 250 | 900 | 1500 | 100 | ordinary | 50 | 50 | 0 | **0** (both clamped to `m` 500) |
| 1000 | 0 | 1200 | 30 | ordinary | 36 | 30 | 6 | **6** |
| 1000 | 0 | 1030 | 3 | ordinary | 4 | 3 | 1 | **1** |
| 850 | 0 | 1370 | 12, 12 | ordinary | 14, 14 | 11, 11 | 6 | **6** |
| 1000 | 0 | 1000 | 40 | void | 40 | 40 | | **10** |
| 1000 | 0 | 1000 | 1 | void | 1 | 1 | | **1** |
| 2000 | 0 | 10000 | 1000 × 8 | ordinary | 4000 × 8 | 2000 × 8 | 16,000 | **16,000** |

`essentiaFlux` at 1000 ‰ / 500 ‰: 0 → 0/0; 1 → 1/1; 3 → 3/2; 200 → 200/100; 1,000 → 1,000/500; 10,000 → 10,000/5,000; 640,000 → 640,000/320,000. Infusion's upset bound at its defaults (`upset.max` 6, `upset.spill` 50): 6 × `essentiaFluxBound(50)` = **300**, enough to hush the altar room ("Where it begins").

### Generated, retained and overflow

**The flux a producer generates is what its sibling previews.** Where it goes is reported separately, because the stores are finite:

```
generated = spent + cleared + retained + pending + overflow
```

- **spent:** converted into Hush (blocks, nodes and Hollows); **cleared:** removed by a bell or a command; **retained:** in a readable aura now; **pending:** held for an unloaded or unreadable chunk; **overflow:** lost to a store's bound (an aura at `FLUX_FORMAT_MAX`, a full pending table). A pending entry merged into its aura moves from pending to retained (or overflow) once and is never counted again.
- **The siblings' equality contracts mean generated flux** ("previewed spill = generated flux"), amended in place in the [wands](wands.md#contracts-on-sibling-specs), [arcane](arcane.md#contracts-on-sibling-specs), [crucible](crucible.md#contracts-on-sibling-specs), [essentia](essentia.md#contracts-on-sibling-specs), [infusion](infusion.md#contracts-on-sibling-specs) and [golems](golems.md#contracts-on-sibling-specs) specs. Retained flux is at most generated; any difference is overflow, reported by `FluxEvent` and `/vftv flux get`, and counted in the level's `overflow` total. At default rates overflow needs a chunk already holding about a million flux, which a [walk at its default cadence](#scheduling) spends long before.
- **Options considered:** an unbounded store, so retained always equals generated. **Why not:** every persisted record here has a format bound ([architecture, State and sync](architecture.md#state-and-sync)); naming the loss honestly beats a promise the codec can't keep.

## The aura

### The record

**Chunk attachment `vftv:aura`** ([architecture, State and sync](architecture.md#state-and-sync)), created only by a deposit that retains flux, persisted with the chunk:

| Field | Format bounds | Default | Meaning |
|---|---|---|---|
| `format` | 1 | | |
| `flux` | 0–1,000,000 (`FLUX_FORMAT_MAX`) | 0 | pooled flux |
| `seep` | a `BlockPos` (as `asLong()`) inside this chunk | absent | where the latest retained deposit landed: the Hush grows from it |
| `spiller` | a UUID | absent | the last known player behind a retained deposit: an attributed deposit sets it, an unattributed one keeps it; claims read it |
| `steps` | any `long` | 0 | attempts made: the stream index |
| `taken` | any `long` | 0 | blocks converted: the Hollow turn |

- **Decoding checks format bounds only.** An unknown `format`, an out-of-bounds value, or a `seep` outside the chunk keeps the raw tag, written back unchanged with one warning: the aura is **dormant**. A dormant aura is never a candidate, never cleared and never written, and deposits for its chunk go to pending (see "Pending and dormant").
- `steps` and `taken` increment with wrapping 64-bit arithmetic and are read as unsigned words, so they never overflow into an error and the stream never replays.

### Depositing

**`FluxService.deposit(level, pos, generated, source, player?) → DepositResult(generated, retained, pending, overflow)`**, server thread only:
1. `generated` < 0: throws (a caller's data error; the listener logs it once).
2. `generated` = 0: **a strict no-op**: no seep move, no attribution, no wake, no event.
3. The chunk at `pos` is loaded (`ServerChunkCache.getChunkNow`, `MC:server/level/ServerChunkCache.java:184`) and its aura is readable or absent: `retained = min(generated, FLUX_FORMAT_MAX − flux)`; if `retained` > 0, `flux += retained`, `seep = pos`, `spiller` = the player if one is given (an unattributed deposit keeps the **last known** spiller), the chunk's saturation is cleared and it is offered to the due queue (a chunk already queued **keeps its due**, so an overdue entry stays overdue; only a new candidate enters at the next aligned tick after `g`); `overflow = generated − retained`.
4. Otherwise (unloaded, which no producer above should reach, or dormant): add to the level's pending entry for that chunk as far as `FLUX_FORMAT_MAX` and the table allow (see below); the rest is overflow.
5. Mark the chunk unsaved (`ChunkAccess.markUnsaved()`, `MC:world/level/chunk/ChunkAccess.java:264`) and dirty for the coalesced sync; add overflow to the level's total; post **`FluxEvent(ADDED)`** with all four amounts.

Every producer fires in a loaded chunk (a player, a ticking entity or a block entity is there), so step 4 is a fallback, never a path. **No chunk is ever loaded.**

### Pending and dormant

- **Per-level SavedData `vftv_flux_pending`**: `{format 1, overflow: long ≥ 0 (saturating), entries: [{chunk: long, flux: 1–1,000,000, seep: long, spiller?: UUID}]}`, at most **`MAX_PENDING` = 4,096** entries with distinct chunks. A deposit adds to its chunk's entry (moving its seep and spiller) or creates one if there is room; what doesn't fit is overflow.
- **Merging:** `ChunkEvent.Load` on a server level queues the chunk; at the start of the next server tick, each queued chunk still returned by `getChunkNow`, in ascending `ChunkPos.pack()`, has its entry merged into a readable or absent aura (retained up to the bound, the rest overflow) and removed. An entry for a dormant aura stays.
- **Repair:** a dormant aura is changed only by **`/vftv flux repair <pos>`**: it logs the raw tag, replaces it with an empty readable record and merges any pending entry. A newer build's record is never discarded without an operator's say.
- **An unreadable store** (either SavedData with an unknown format or out of bounds) keeps its raw tag, written back unchanged, with one warning. While `vftv_flux_pending` is unreadable, deposits that would need it are overflow, counted in memory for the session only, and merging is off.
- **Options considered:** decay, as TC4's goo lost units; diffusion to neighbouring chunks. **Why not:** a timer is not a choice, and decay makes the Hush toothless for anyone who waits ([pain points §3](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), both camps); diffusion moves the Hush away from what caused it. Flux leaves a chunk only by being spent or cleared, so the land the Hush can take is bounded by what was spilled.

## The Hush

### Where it begins

- **`threshold = max(hush.onset, hush.block_cost)`.** Below it a chunk's ground holds its flux quietly; the Clearing Bell's reading says so. A chunk at or above it **can** hush.
- **A candidate** is a loaded chunk whose aura is readable, holds a seep and `flux ≥ threshold`, and is not saturated. Candidacy doesn't depend on attendance or the switches; those are checked when a candidate is served.
- **Attended:** a non-spectator player **who isn't Silenced** (`WarpService.isSilenced`, the [Eldritch tier's](eldritch.md#the-capstones) Silencing capstone, "The Hush can't hear you") in the level stands within Chebyshev `hush.player_chunks` chunks of it, unless the server switch `hush.unattended` (default off) is on. The exclusion is the spread's alone: a Silenced player's spills still deposit, anyone else's presence or `hush.unattended` still runs the walk, and the Pealing Bell's attendance (under "Pealing") still counts Silenced players. **Chunk loaders alone never run the Hush** ([pain points §3](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md)), and walking back to an old pool wakes it with no new deposit.
- **At defaults** a crucible's full pool (200 essentia → 200 flux) hushes **up to 76 blocks** (`1 + ⌊(200 − 50) / 2⌋`, the HUD's bound) and leaves 48. Hollows and nodes take their share: if every Hollow turn (each 8th conversion) rises, up to the near cap of 3, it is **38 blocks and 3 Hollows**, leaving 49 (`hush_ref.py`, line `crucible`). Infusion's worst run (300 flux) hushes up to 126.
- **Options considered:** converting at any flux. **Why not:** single vis surcharges of 1–7 flux would leave single grey blocks everywhere; a threshold makes the Hush a consequence of **pooling**, which is the lore's wording ([lore, The Hush](lore.md#the-hush-replaces-taint)).

### Scheduling

**Per level, one due queue**: an ordered set of `(due, ChunkPos.pack())` with a map from chunk to its `due`, so a chunk is in it **at most once**. `phase(c) = Long.remainderUnsigned(fold([0x48555348 ("HUSH"), ChunkPos.pack(c)]), hush.interval)`, with the research spec's [`fold` and `mix64`](research.md#generating-a-note); a chunk enters at the first tick `t > g` with `t mod hush.interval = phase(c)`.

- **Membership changes** on a retaining deposit, a clear, a merge, a chunk's load (queued to the next tick) and unload, and saturation: each an O(log n) insert or removal. A chunk that stops being a candidate leaves.
- **Service**, in the server-side `LevelTickEvent.Post` (`NF:event/tick/LevelTickEvent.java:57–64`, server levels only), **only while `hush.spread` is on**: pop in `(due, pack)` order while `due ≤ g`, up to **`MAX_POPS_PER_TICK` (64)** pops and **`MAX_VISITS_PER_TICK` (32)** visits:
  1. not attended → re-due at the next aligned tick after `g` (a pop, not a visit);
  2. the attempt needs **1 mutation slot, or 2 when it is a Hollow turn** (`(taken + 1) mod hollow.every = 0`, decided before walking). Fewer slots remain of `MAX_SPREAD_MUTATIONS_PER_TICK` (4): stop serving this tick, leaving it due, so it is served first next tick (**fair carry: oldest due first**);
  3. otherwise visit (one attempt, below), then re-due at the next aligned tick after `g` if it is still a candidate.
- **Wakeups:** turning `hush.spread` on resumes service with the backlog in due order; a tuning `/reload` recomputes every due from the new phases and clears every saturation (one O(n log n) rebuild, a load scenario); turning `hush.unattended` on or off needs no wake, since attendance is read at service.
- **Options considered:** random ticks on hushed blocks (TC4). **Why not:** that is the autonomous, chunkloader-fed spread [pain points §3](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md) names, with no budget. Phase buckets with a carry list. **Why not:** a chunk can sit in a bucket and the carry at once; one ordered set is deduplicated by construction.

### An attempt

**Stream:** `h = fold([0x48555348, worldSeed, ChunkPos.pack(c), steps])`, with `worldSeed = server.overworld().getSeed()`; `next() = { s += 0x9E3779B97F4A7C15; return mix64(s) }`, as the [wands spec's natural spread](wands.md#natural-spread). Then `steps + 1`, whatever the outcome. From `p = seep`, for `j` in `0 … hush.walk − 1`:

1. `k = (next() >>> 1) % 6`; `q = p + Direction.from3DDataValue(k)`, the order DOWN, UP, NORTH, SOUTH, WEST, EAST (`MC:core/Direction.java:33–38`).
2. `|q − seep|² > hush.radius²`, or `q` outside the build height: **stay** (`p` unchanged; the step is spent).
3. `getChunkNow(q)` is null, or that chunk's full status is below `BLOCK_TICKING` (a client could not be sent the change, `MC:world/level/Level.java:268–270`): **miss, `UNLOADED`**; the attempt ends.
4. `q`'s state is a **source** of the hushed-form table (see "Hushed blocks"), not in `#vftv:hush_immune`, and `flux ≥ hush.block_cost`: **block target**.
5. `q` holds a **hushable node** (see "Hushed nodes") and `flux ≥ hush.node_cost`: **node target**.
6. Otherwise `p = q`: the walk passes through air, fluids, hushed blocks and anything else, so worked walls don't contain it ("the Void seeps through").

No target after `hush.walk` steps: **miss, `NONE`**. A target goes through "The operation" (see "Protection and multiplayer"); a refusal there is a miss with nothing debited. A converted block debits `hush.block_cost` and adds 1 to `taken`; a hushed node debits `hush.node_cost`; either clears the chunk's misses. On a Hollow turn, a converted block is followed by one rise try (see "Hollows").

- **Every read** is `getChunkNow` plus `LevelChunk.getBlockState`, which returns air outside its sections (`MC:world/level/chunk/LevelChunk.java:226–236`); nothing reads through `Level.getBlockState`, which can load (`MC:world/level/Level.java:383–389`).
- **Saturation:** `hush.max_misses` consecutive misses mark the chunk **saturated**, transient and in memory: it leaves the due queue until a retaining deposit, a clear of that chunk (a bell or a peal), its next load, or a tuning `/reload`. Its flux idles. A seep with nothing convertible in reach (a sky base, a deep ocean) is harmless by construction.
- **Bounded:** every hushed block lies within `hush.radius` of a seep; a chunk converts at most one block per `hush.interval`; growth slows as the near ground is taken, because walks must travel further.
- **Deterministic:** an attempt is a function of the world seed, the chunk, `steps` and the loaded world it reads; service runs in the pinned order, so two seeps reaching one cell resolve by it. A world is reproducible from the same state, not from the seed alone; nothing needs that.
- **Options considered:** uniform sampling inside the ball. **Why not:** speckled, not pooled; the walk gives contiguity for the same read budget.

### Vectors

World seed `0x0123456789ABCDEF`, `hush.interval` 10. **Phase:** chunk (0, 0) → 2 (`fold` `C21D48E4BBC2BCC8`); (−1, 0) → 7 (`EF2949749A6FF19D`); (0, −1) → 4 (`970A1F109E0E25D4`); (−12345, 6789) → 3 (`1CFB81FE4D218FCB`). **Stream** for chunk (0, 0), `steps` 0: `h` = `88B1CC95D8BDED57`; directions 2, 5, 5, 5, 4, 0, 4, 1, 3, 3, 3, 4.

**The synthetic world `flat63`:** every cell with y ≤ 63 is a source (grass at y 63, dirt below), every cell with y ≥ 64 is air, and exactly the four chunks with x and z chunk coordinates in {−1, 0} are loaded and block-ticking. Walk 32, radius 12, seep (8, 64, 8) in chunk (0, 0).

**The walk kernel** (budgets disabled: costs 0, no onset; each conversion stays hushed for later steps): `steps` 0 → (10, 63, 7) after 6 steps; 1 → (7, 63, 7) after 5; 2 → (8, 63, 8) after 1; 3 → miss `NONE`; 4 → (13, 63, 9) after 13; 5 → (9, 63, 7) after 15; 6 → (9, 63, 9) after 5; 7 → (5, 63, 8) after 8; 8 → (7, 63, 8) after 2; 9 → (7, 63, 9) after 5; 10 → (9, 63, 8) after 2; 11 → (7, 62, 8) after 15 (beneath the hushed surface); 12, 13 → miss `NONE`; 14 → (11, 63, 11) after 15; 15 → (9, 62, 7) after 4. **At a chunk edge** (seep (15, 64, 15), nothing hushed): `steps` 0 → miss `UNLOADED` at step 2; 1 → (14, 63, 14) after 5; 2 → (15, 63, 15) after 1; 3, 4, 5 → miss `UNLOADED` at steps 2, 1, 2.

**The scheduler** (onset enforced; costs and cadence chosen to reach every outcome in 16 visits, not the defaults): `flat63` plus a hushable node at (8, 64, 9); onset 50, block cost 5, node cost 50, Hollow every 4 at 25 flux, a rise allowed when the cell above the converted one is unhushed air; starting flux 150, `steps` and `taken` 0, one visit per due tick:

| Visit | Outcome | Steps used | Flux after | `steps` | `taken` |
|---|---|---|---|---|---|
| 0 | block (10, 63, 7) | 6 | 145 | 1 | 1 |
| 1 | block (7, 63, 7) | 5 | 140 | 2 | 2 |
| 2 | block (8, 63, 8) | 1 | 135 | 3 | 3 |
| 3 | miss `NONE` | 32 | 135 | 4 | 3 |
| 4 | node (8, 64, 9) | 1 | 85 | 5 | 3 |
| 5 | block (9, 63, 7), a Hollow turn: **rises** | 15 | 80 → 55 | 6 | 4 |
| 6 | block (9, 63, 9) | 5 | 50 | 7 | 5 |
| 7 | block (5, 63, 8) | 8 | 45 | 8 | 6 |
| 8–15 | idle: 45 < threshold 50; no attempt, `steps` unchanged | | 45 | 8 | 6 |

## Hushed blocks

### The forms

**A fixed table in code** (`HushedForms`): a block registration is code anyway, so a data map would promise an extension it can't deliver.

| Source | Hushed form | Properties copied |
|---|---|---|
| `minecraft:grass_block` | `vftv:hushed_grass_block` | `snowy` |
| `minecraft:dirt`, `stone`, `sand`, `gravel` | `vftv:hushed_dirt`, `vftv:hushed_stone`, `vftv:hushed_sand`, `vftv:hushed_gravel` | none |
| `minecraft:deepslate` | `vftv:hushed_deepslate` | `axis` |
| `minecraft:short_grass` | `vftv:hushed_short_grass` | none |
| `minecraft:oak_log`, `birch_log`, `spruce_log` | `vftv:hushed_oak_log`, `vftv:hushed_birch_log`, `vftv:hushed_spruce_log` | `axis` |
| `minecraft:oak_leaves`, `birch_leaves`, `spruce_leaves` | `vftv:hushed_oak_leaves`, `vftv:hushed_birch_leaves`, `vftv:hushed_spruce_leaves` | `distance`, `persistent`, `waterlogged` |

- **Bijective, and exact for the cell:** converting sets `form.withPropertiesOf(state)`, restoring sets `source.withPropertiesOf(state)` (`MC:world/level/block/Block.java:547–556`), and each form declares the same property objects as its source (the leaves' `distance` is `LeavesBlock.DISTANCE`), so a restored cell holds the state that was hushed. That is all "exact" promises: a later edit by anyone is theirs, and vanilla physics resumes on the restored block when something next updates it.
- **Everything else is passed through:** other woods and leaves, ores, worked blocks, every block with a block entity and everything in **`#vftv:hush_immune`** (data, empty by default).
- **Tag `#vftv:hushed`:** all 13 forms; the place query, the bells and the client read it.
- **Options considered:** a generic hushed block remembering its source per position. **Why not:** a per-chunk memory to keep exact across breaks, explosions and pistons, for less art. Per-variant blocks keep the [art style's](art-style.md#5-tone-ramp-by-stage) rule literally: "Hushed blocks keep their shapes and lose their colour." A Hush biome (TC4). **Why not:** biome writes are coarse, need a client resend per chunk, and outlive the blocks.

### The swap

**Both directions are one `level.setBlock(pos, state, 2 | 16 | 512)`**: `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_ON_PLACE` (`MC:world/level/block/Block.java:94,97,102`). So:
- **no neighbour updates and no shape cascade** (`MC:world/level/Level.java:276–287`): flag 1 would send neighbour updates and flag-less 16 would cascade shape updates, which read through `Level.getBlockState` and can load the next chunk (`MC:world/level/redstone/NeighborUpdater.java:43`; `CollectingNeighborUpdater.java:140,195`);
- **no `onPlace`** (`MC:world/level/chunk/LevelChunk.java:329–330`): **the swap schedules no falling tick**; ticks already scheduled before the swap and later updates keep vanilla's behaviour (`MC:server/level/ServerLevel.java:843–846`; `MC:world/level/block/FallingBlock.java:29–50`), so restored sand can still fall then;
- **no light work:** each form has its source's light properties (occlusion, light dampening, sky propagation), so `LightEngine.hasDifferentLightProperties` is false and no light check is queued (`LevelChunk.java:298–303`); a unit test compares them per state;
- **the client is told** (flag 2), because the target chunk is at least `BLOCK_TICKING` (see "An attempt");
- **the swap is checked:** `setBlock` returned true and the cell now holds exactly the new state, else nothing is debited or counted.

**Support parity, so no collateral loss.** With no updates sent, neighbours keep reading the swapped cell through vanilla's own predicates, so each form answers them as its source would:
- **tags:** each form is in every `minecraft:supports_*` tag, every `minecraft:mineable/*` tag and `minecraft:prevents_nearby_leaf_decay` that holds its source, derived by datagen from vanilla's tag data and asserted by a unit test. So plants on hushed soil survive (`VegetationBlock.mayPlaceOn` reads `#minecraft:supports_vegetation`, `MC:world/level/block/VegetationBlock.java:23–49`), and leaves beside hushed logs never decay (`LeavesBlock.getOptionalDistanceAt` reads `#minecraft:prevents_nearby_leaf_decay`, `LeavesBlock.java:132–142`);
- **shapes and faces:** each form has its source's collision, outline and support shapes;
- **fluids:** hushed leaves return a water source while `waterlogged`, as `LeavesBlock` does (`LeavesBlock.java:144`);
- a GameTest swaps both ways at an unloaded chunk boundary and next to flora, vines, snow layers and leaves, and proves nothing broke, decayed, fell or loaded.

### Behaviour and drops

- **Inert:** no random ticks (hushed grass doesn't spread, hushed leaves don't decay), no falling, not flammable, `PushReaction.BLOCK` (pistons can't carry one away from its restore), the source's hardness, explosion resistance and tool.
- **Untinted:** forms carry no biome colour handler; their grey is in the texture.
- **Sound:** the `vftv:hushed` sound type (see "Rendering and sound").
- **Drops:** nothing; with Silk Touch, the hushed block itself (loot tables `vftv:blocks/hushed_*`). A placed hushed block is a hushed block like any other: the bells restore it to its source.
- **Not in vanilla's broad tags** (`#minecraft:dirt`, `#minecraft:logs`, `#minecraft:leaves`): only the parity tags above, so nothing else treats them as their sources.

## Hollows

### Rising

**`vftv:hollow`** rises, never spawns: it has no biome entry and no spawn rule. When a block conversion makes `taken mod hollow.every` = 0 (the turn, decided before the walk), one rise is tried at the converted cell `C`, the slot already reserved ("Scheduling"). First failure ends it, with nothing spent:

1. the server switch `hush.hollows` is on, and the difficulty isn't peaceful;
2. **the loaded admission cap:** Hollows within `hush.radius + 8` of the seep (one AABB query) < `hollow.max_near`. There is no per-level count: it would need an entity-leave hook, and the flux every Hollow costs already bounds the level's total;
3. `flux ≥ hollow.flux`;
4. **the spot:** `C`'s top face is sturdy; `C + 1` and `C + 2` are air, inside the build height and the world border, in chunks at least `BLOCK_TICKING`; the Hollow's box at `(C.x + 0.5, C.y + 1, C.z + 0.5)` has no collision; no player is within `hollow.min_player_distance` blocks (3-D);
5. `HushSpreadEvent(RISE)` is not cancelled; everything above is read again after it ("The operation");
6. create it with `EntitySpawnReason.EVENT` (`MC:world/entity/EntitySpawnReason.java`), `setHomeTo(seep, hush.radius + 4)` (`MC:world/entity/Mob.java:1241`) and `setPersistenceRequired()` (`:1131`), and `addFreshEntity`;
7. **it counts** only if that returned true and, read afterwards, the entity is alive in this level, its block position is `C + 1`, its box has no collision, no player is nearer than `hollow.min_player_distance`, the cap holds counting it, and the aura still holds `flux ≥ hollow.flux`. Only then is `hollow.flux` debited and `hollow.rise` played. Otherwise our entity is discarded.

- **The cap is an admission cap on loaded Hollows near a seep, not a residence bound.** Unloading a chunk lets more rise elsewhere, and reloading can bring more than the cap back at once. Every Hollow cost flux, so their number is still bounded by what was spilled. Stated, and a load scenario.
- **Persistent** (home and persistence are saved, `Mob.java:393–394,418–420`), so flux never leaks by despawn. Peaceful discards it (`Mob.java:715–717`) and its flux is gone; stated.

### The creature

- **A `Monster`,** category `MONSTER`, not allowed in peaceful; size 0.9 × 0.9 blocks, eye height 0.7.
- **Stats** *(ours; fixed at registration and asserted by a unit test)*: health 30, attack damage 5, movement speed 0.26, follow range 16, armour 0. Sturdier and faster than a zombie, and silent, which is the frightening part. 5 experience.
- **Behaviour,** in goal priority order: (0) float; (1) **return home** while outside its home radius, which also drops its target; (2) melee attack; (3) wander within home; (4) look at a nearby player. **Target:** the nearest player it can attack (not creative or spectator, and **not Silenced**: the [Eldritch tier's](eldritch.md#the-capstones) capstone) within follow range **whose position is inside its home**; it drops the target as soon as the target leaves home or follow range, or is Silenced, rechecked every tick. Silenced players still count for the rise's player-distance rule, and Hollows still hunt everyone else. Vanilla's restriction goal only starts the return and its target goals don't recheck home (`MC:world/entity/ai/goal/MoveTowardsRestrictionGoal.java:21–45`; `target/TargetGoal.java:37–71,100–102`), so both checks are ours.
- **Never** converts blocks, opens doors, picks up items or burns in daylight. Silent except when hurt, dying and rising.
- **Drops:** 0–2 `vftv:hush_ash`, +1 per Looting level (loot table `vftv:entities/hollow`). Hush Ash is crucible fodder ([#30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)'s values: a Silentium and Vacuos source, [aspects, Coverage and supply](aspects.md#coverage-and-supply)), stacking to 64.
- **Options considered:** hushed variants of vanilla animals (TC4's tainted animals). **Why not:** a renderer and texture per mob and an infection mechanic, and a hostile cow reads as a joke, not as silence. A creature that rises from its own ground costs no spawn rule, is capped near its seep and is paid for in flux.

## Hushed nodes

- **One built-in node type, `vftv:hushed`** (amended into the [wands spec's built-ins](wands.md#node-types)): `natural: false`, `main_aspect` `vftv:silentium`, `aspects` [Silentium [1, 1]] (spawn ranges unused: it is only ever assigned by `update`), clarity weights faint only. Name "Hushed".
- **Hushable:** a `vftv:node` block entity in a loaded chunk whose registry entry is `PLACED` here, whose type is present, **not anchored** (the Wellspring and every anchored type are never hushed, so [the starter guarantee](wands.md#the-starter) holds) and not already `vftv:hushed`.
- **The journal:** the overworld SavedData **`vftv_hushed_nodes`**, `{format 1, nodes: {<decimal id>: {"type": <id>, "clarity": <clarity>}}}`, at most **`MAX_HUSHED_NODES` = 65,536** entries.

**Both directions settle at `NodeService.update`'s apply boundary** ([wands, Events](wands.md#events), amended): it runs once, after the node is written and **before** `NodeEvent(CHANGED)` is posted, so every Hush decision is final before any listener sees the change. `update` settles first and rebases after, and keeps aspects, maxes, currents, counters, id and position.

**Hushing** a node, under "The operation":
1. the journal is readable and has room, else a miss;
2. call `update(id, {type: vftv:hushed, clarity: faint}, API, boundary)`. **At the boundary:** write the journal entry `{original type, original clarity}` (read under the guard before the call), debit `hush.node_cost` and clear the chunk's misses;
3. **by the result:** `UPDATED` or `COMMITTED_NOTIFY_FAILED` → done (the latter is logged by the service and is **never** a miss or a retry); any refusal (`NOT_LOADED` included) → the boundary never ran, nothing was written; a miss.

**Restoring** a node (a bell or a peal reaching its cell), under "The operation" and the actor's checks:
1. its journal entry exists, else nothing (a node hushed by something else has nothing to restore to);
2. its current type is **not** `vftv:hushed`: someone retyped it since, so the entry is stale; it is deleted and **the later type is never overwritten**;
3. the original type is missing from the registry: left hushed, the entry kept for a restored pack, one warning;
4. otherwise `update(id, {type: original, clarity: original}, API, boundary)`. **At the boundary:** delete the entry and count the restore toward the actor's work. A refusal keeps the entry; `COMMITTED_NOTIFY_FAILED` is a completed restore.

Listeners of `NodeEvent(CHANGED)` may then capture, move, remove or retype the node; they see finished bookkeeping. The journal is keyed by the stable id, so a hushed node carried in a Bell Jar keeps its original ([wands, Identity and reconciliation](wands.md#identity-and-reconciliation)), a later removal deletes the entry, and a later retype makes it stale (step 2).

- `NodeEvent(REMOVED)` deletes the entry. The Hush never calls `NodeService.remove`, never rewrites an id, and touches relay members only as cells the walk passes (they have block entities, so they are never targets).
- **Effect:** a faint node refills slowest ([wands, Regeneration](wands.md#regeneration)) and reads "Hushed". It is **not** a source: hushed nodes don't spread, unlike TC4's taint nodes.

## Protection and multiplayer

### The operation

Every Hush mutation (a block converted, a node hushed or restored, a Hollow risen, a Pealing Bell's restore, a Clearing Bell's restore) runs as one **operation**:
1. **Guards first:** the level's Hush guard and, for a node, the id's guard. Re-entry from any foreign callback is refused (a miss, or a skipped cell). **Trusted nesting:** a ring or a peal takes the level guard once for its whole run, and its own per-cell restores run under that held guard as the same operation, so they never refuse themselves; only code reached through a foreign callback is refused.
2. **Authorise:** post the cancellable event for the actor (below). This is the one point where foreign code runs before the write.
3. **Revalidate everything after it:** the chunk is still loaded and `BLOCK_TICKING`; the cell still holds exactly the state (or node id and type) read before; the rule that chose the target still holds; **for paid spread only** (a conversion, a hushed node, a rise), the source aura is still readable and holds the cost; for a Clearing Bell, the player is still online, alive, in the level and holding the same bell stack object; for a Pealing Bell, its block entity is the same instance and not removed (its fuel is already reserved, see "Pealing"). Restores pay nothing and read no aura. A cell a listener replaced is skipped.
4. **Write** with no foreign code between: the swap, or `update` with its boundary; then the debit, counters and dirty marks.
5. **Then notify:** post-events after the writes are settled. A failure of one of our posts is logged once; nothing committed is repeated or rolled back, and our later posts still run. The remaining subscribers of the post that failed are NeoForge's dispatch, not ours to resume.

### Claims

- **The Hush's own spread** (block, node, rise) posts **`HushSpreadEvent`**, carrying the seep's chunk and **`spiller`**: a claim mod answers "may this player's spill change this place?". Cancelled is a miss.
- **A Pealing Bell's restore** posts `HushSpreadEvent(RESTORE)` with origin **the bell's position** and **no actor**. It never fabricates a seep or a spiller.
- **A Clearing Bell** acts as its player: each restored cell needs `level.mayInteract(player, pos)` (`MC:server/level/ServerLevel.java:901–902`: spawn protection and the world border) and an uncancelled `BreakBlockEvent` on the hushed block, as the [wands spec's](wands.md#moving-nodes-the-bell-jar) captures do.
- **Deposits are not re-authorised:** each is the consequence of an action its sibling already authorised.
- **Shared by design:** flux belongs to the chunk; anyone may ring a bell wherever they may break blocks. One player's spill can hush another's land **only within `hush.radius` of the seep**, and every such change is a claimable event and exactly reversible. v1 has no integration with a named claim mod ([compatibility, Claim mods](compat.md#claim-mods)).
- **Admin:** the server switches `hush.spread`, `hush.hollows` and `hush.unattended`, each proven by a GameTest.

## The Clearing Bell

**`vftv:clearing_bell`**, stacking to 1, durability **64** (fixed at registration, as the [armor spec's](armor.md#wearables) durabilities), not enchantable, repaired by nothing. The arcane recipe `vftv:clearing_bell` behind `vftv:clearing_bell`.

### Reading

**HUD layer `vftv:hush`**, while a Clearing Bell is in either hand, F1 is off, no screen or debug overlay is open, the player isn't a spectator and the client option `hush.hud` is on.

| Line | Text |
|---|---|
| 1, first match | no `FluxRules` yet, or no status known for the player's chunk yet (see "Sync and state": every watched chunk gets one) → `hud.vftv.hush.unknown` (0); dormant → `hud.vftv.hush.dormant` (0); no aura or 0 flux → `hud.vftv.hush.none` (0); `flux < threshold` → `hud.vftv.hush.quiet` (2: flux, threshold); else `hud.vftv.hush.hushing` (2: flux, blocks) |
| 2 | `hud.vftv.hush.seep` (3: x, y, z), iff line 1 is `quiet` or `hushing` |

**`blocks`** is the tight upper bound on blocks the flux can still pay for: 0 below `threshold`, else `1 + floorDiv(flux − threshold, block_cost)`. It is an upper bound, never a promise, because nodes, Hollows and saturation can take less; the text says "up to". **Vectors** (onset, cost, flux → blocks): 50, 2, 200 → 76 (the defaults); 50, 5, 200 → 31; 50, 5, 49 → 0; 50, 5, 50 → 1; 0, 5, 6 → 1; 0, 5, 4 → 0; 10, 3, 11 → 1.

**Geometry** (GUI-scaled pixels, scaled screen `W` × `H`): the frame is the nine-slice `vftv:hud/hush_frame` (16×16, border 4) with 4-px padding; text in `hush3` with a shadow, wrapped at `min(200, W − 12)` with the font's own `split`, in lines of 9 px with **no line cap** (four lines is the English design target; [localisation, R3](accessibility.md#r3-hud-text)); frame width = 8 + the widest line, height = 8 + 9 × lines. **Anchor:** (4, 4), or, while the wands spec's `vftv:vis` layer draws ([wands, Display](wands.md#display)), 2 px below its frame. Clamped 2 px inside the screen; if still too large, drawn at the pose scale `s = min((W − 4) / width, (H − 4) / height)` as the [crucible HUD](crucible.md#the-hud) is. **Layer order:** `vftv:vis`, `vftv:hush`, then the crosshair cards.

- **Rebuilt only** when the player's chunk, that chunk's synced aura, `FluxRules`, the held items, the language or resources, or the scaled screen size change; cleared on a dimension change and at logout. Zero allocation per frame in our draw code.
- **Instruments never lie:** saturation is transient server state, so the reading never claims it; it shows flux, threshold and the bound, which are exact.

### Ringing

**Use** in either hand, on air (`Item#use`) or on any block whose own use passed (`Item#useOn`), sneaking or not. Server, under the player's guard and the level's Hush guard, held for the whole ring (trusted nesting, "The operation"). **At most one ring attempt per player per tick**, counted **before** any scan and including attempts that find nothing: vanilla's packet handler bounds no item use per tick (`MC:server/network/ServerGamePacketListenerImpl.java:1437–1457`), so a second attempt in the same tick is refused silently. First failure refuses:

1. a ring attempt already made by this player this tick, or on cooldown (vanilla's `ItemCooldowns`, `MC:world/item/ItemCooldowns.java:54`): nothing, silently;
2. **clear:** in the chunk at the player's feet, if its aura is readable: `cleared = min(flux, bell.flux)`, debited; its saturation is cleared and it is offered to the due queue. An absent aura is never created;
3. **restore:** walk the precomputed **ball table** for `bell.radius` (every offset with `dx² + dy² + dz² ≤ r²`, in ascending `(d², dy, dz, dx)`), from the player's block position, skipping cells in chunks that aren't `BLOCK_TICKING`, restoring up to `bell.restore` hushed blocks and at most 1 hushed node, each under "The operation" with the player's checks. The two caps are separate: the walk ends at the table's end or once **both** are met, so `bell.restore` 0 still finds a node. **A hushed node takes a whole ring:** restoring one spends the ring's entire `bell.flux`, so a node is restored only by a ring whose clear (step 2) removed nothing. On pooled ground the rings clear the pool first; the first ring that clears nothing restores the node. A wrecked node farm is a real setback;
4. **if anything was cleared or restored:** `hurtAndBreak(1)` (none in creative; a bell at its last point breaks with vanilla's break sound), `clearing_bell.ring`, plus `hush.restore` if anything was restored, 8 `vftv:hush_mote` particles, `FluxEvent(CLEARED, BELL)` if `cleared` > 0, and the cooldown `bell.cooldown`;
5. **otherwise:** `message.vftv.hush.nothing` on the [status line](accessibility.md#r5-the-status-line); no cost, no cooldown.

**Ball tables** (`hush_ref.py`): radius 5 has **515** offsets, radius 8 **2,109**; both start (0, 0, 0), (−1, 0, 0), (0, −1, 0), (0, 0, −1), (0, 0, 1), (0, 1, 0), (1, 0, 0), (−1, −1, 0), written as (dy, dz, dx); radius 5 ends at (5, 0, 0) and radius 8 at (8, 0, 0). Tables for every radius 0–16 are precomputed at class load (the bell uses 1–8, the restore command up to 16); zero allocation per ring.

- **Cheap and chosen:** Brass, copper and amethyst; 64 rings clear 3,200 flux at defaults, about 16 emptied crucibles. Disposal stays priced ([essentia](essentia.md#contracts-on-sibling-specs): no void jar), just early.
- **Options considered:** a ward block that stops conversion in a radius. **Why not:** a free, permanent immunity is the "toothless" half of [pain points §3](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), and it needs an index of ward positions. Walling the seep in. **Why not:** the walk passes walls by design.

## The Pealing Bell

**`vftv:pealing_bell`**, a block with a block entity, four horizontal facings, `PushReaction.BLOCK`. The infusion recipe `vftv:pealing_bell` behind `vftv:the_hush`. "Set up once" cleanup, fed the way a Node Tap is ([wands, Cultivation](wands.md#cultivation)).

**Block entity record:** `{format 1, sonus: 0–1,000, cursor: 0–35,936}`, defaults 0 and 0. An unknown format or an out-of-bounds value keeps the raw tag, written back unchanged with one warning: the bell is **dormant** (no insertion, no pealing, no spill) and drops as a plain bell.

### Feeding

- **The essentia capability**, **insert-only, Sonus only**, on every face and the null side, inside the transfer transaction with a snapshot journal; the handler adapters, journal and `Transaction` calls live in `neoforge.hush`, the plan in `core.hush` ([architecture, Essentia and capabilities](architecture.md#essentia-and-capabilities)). Accepts `min(amount, peal.capacity − sonus)`, 0 when `sonus ≥ peal.capacity`.
- **The suction capability** `vftv:essentia_suction` on the same faces: `peal.suction` for Sonus while there is room, else 0; hold 0 ([essentia, Suction](essentia.md#suction)). In `#vftv:essentia_endpoints`, so tubes connect.
- **A lowered `peal.capacity`** never removes stored Sonus; it only stops accepting until `sonus` is below it.
- **On the root commit:** marked changed and for the coalesced sync. While a transaction is open the bell is transaction-busy and doesn't peal.
- **While a peal owns the bell** (below) the handler is **unavailable**: it accepts 0 and its suction reads 0, so a callback during a peal can't feed or reserve against it.

### Pealing

- **Indexed per level** by block-entity load and removal, with its own due queue of `(due, BlockPos.asLong())` at `phase = Long.remainderUnsigned(fold([0x5045414C ("PEAL"), BlockPos.asLong()]), peal.interval)`, at most once per bell. Every **initial and re-due tick is the next tick strictly after `g`** with `t mod peal.interval = phase`. Served in `LevelTickEvent.Post`, oldest due first. **Every pop counts** against `MAX_PEAL_POPS_PER_TICK` (16), including a bell skipped because it is unattended (the rule under "Where it begins", whatever `hush.spread` says, except that a Silenced player **does** attend a bell), empty, transaction-busy or dormant; at most **`MAX_PEALS_PER_TICK` (4)** of those pops may peal. The rest stay due and are served first next tick.
- **A peal** is one operation that owns the bell. Under the bell's guard and the level's Hush guard (trusted nesting), first failure re-dues without pealing: dormant; transaction-busy; `sonus` < 1. Then:
  1. **reserve** one Sonus: `sonus − 1` is held aside, and the handler goes unavailable (see "Feeding");
  2. **clear** `min(flux, peal.flux)` from each readable aura of the 3 × 3 loaded chunks around the bell, in ascending `ChunkPos.pack()`, clearing each one's saturation; absent auras are never created, and a peal that only restores creates none;
  3. **scan** from `cursor` through the cube of side `2 · peal.radius + 1` in `(y, z, x)` order, wrapping, **at most `peal.scan` cells**, restoring up to `peal.restore` hushed blocks and, separately, 1 hushed node **only if this peal's clear (step 2) removed nothing** (a node takes a whole peal's `peal.flux`, as it takes a whole ring), each under "The operation" with `HushSpreadEvent(RESTORE)`. The scan stops early only when **both** caps are met. `cursor` advances by **the cells actually examined** (cells in unloaded chunks count as examined); with a changed `peal.radius`, `cursor` is taken modulo the new cube. If a callback removed the bell, the scan stops at once;
  4. **settle the reservation:** it is **committed** at the first successful work (a clear above 0 or a committed restore) and **refunded** only if nothing happened. The bell is marked changed whenever `cursor` or `sonus` moved, even on a scan that found nothing;
  5. **notify:** `pealing_bell.ring`, plus `hush.restore` if anything was restored, and one `FluxEvent(CLEARED, PEAL)` per chunk cleared, only if something happened; then the operation releases the bell.
- **A full sweep** at defaults (radius 12, a 15,625-cell cube, 1,024 per peal) takes 16 peals, about 2.7 minutes, **when no peal stops early at its restore cap**; ground with more than 8 hushed blocks in a stretch takes more.

### Removal

- **Drops itself, empty** (loot table `vftv:blocks/pealing_bell`, surviving explosions).
- **Its Sonus spills:** the removal hook (`BlockEntity#preRemoveSideEffects`) deposits `essentiaFlux({vftv:sonus: sonus}, REMOVED)` at its position, source `ESSENTIA(REMOVED)`, once. **While a peal owns the bell or a transaction is open on it,** the hook records one pending removal and does nothing else; it runs when **both** scopes have settled (the peal's reservation committed or refunded, the transaction committed or aborted), so the spill is the final balance, once, as the [crucible's removal](crucible.md#removal) does for transactions. Dormant: nothing spills, with a warning. A removal path that suppresses block-entity side effects (flag 256) loses the Sonus without a spill; stated, as the crucible does.
- **Options considered:** keeping the Sonus in the dropped item, as a jar does. **Why not:** a second path through item destruction for an amount that is a cleaning fuel, not a store. A radius scrubber clearing everything at once (TC4's). **Why not:** unbounded per-tick work and no material price.

## The place query and the senses

### Warp context

**`HushPlaceQuery`**, registered at init with `WarpService.registerPlaceQuery` ([warp, Context](warp.md#context)): true iff `getChunkNow` holds the chunk at the position and the block at the position or the one below it is in `#vftv:hushed`. **Two reads of one loaded chunk, pure, never loading;** `LevelChunk.getBlockState` returns air outside its sections, so the minimum height is safe (`MC:world/level/chunk/LevelChunk.java:226–236`). `core.hush` imports `core.warp`'s interface; `core.warp` imports nothing of ours.

- **No warp is ever gained from the Hush.**
- Silk-Touched hushed blocks placed by a player make a Void-touched place too: chosen, and governed by placement claims.

### Muffling

**"Sound dies away"** ([lore, The Hush](lore.md#the-hush-replaces-taint)). On the client, while the same query holds for the local player and the client option `hush.muffle` is on, the gain of the categories `BLOCKS`, `HOSTILE`, `NEUTRAL`, `PLAYERS`, `AMBIENT`, `WEATHER` and `RECORDS` is set to **0.5** through `SoundManager.updateCategoryVolume(source, gain)` (`MC:client/sounds/SoundManager.java:197–198`). Never `MASTER`, `MUSIC`, `VOICE` or `UI` (`MC:sounds/SoundSource.java:4–14`).

- **How it works:** the engine keeps a gain per category that multiplies the player's own slider and refreshes every playing channel (`MC:client/sounds/SoundEngine.java:122–129,157–159,472–477`; `MC:client/Options.java:1335`), so sounds already playing follow the player in and out.
- **When:** evaluated once per client tick (the query's two reads); set to 0.5 on entering, back to 1.0 on leaving, on turning the option off, on a level change and at disconnect; reapplied after an engine reset, which clears the gains (`SoundEngine.java:172`).
- **Disclosed:** each change refreshes every active sound (a native scan); the gain is shared state another mod could also set, and we restore it to 1.0, not to that mod's value. Subtitles are untouched.
- **Options considered:** `PlaySoundEvent` replacing quieter sounds. **Why not:** it fires only when a sound starts and can replace it, not change its volume (`NF:client/event/sound/PlaySoundEvent.java:61–71`), so a sound playing as the player walks in would stay loud.

### Sight

The hushed blocks themselves, untinted; their `animateTick` emits a `vftv:hush_mote` at 1 in 64 per displayed tick, inside vanilla's own bounded display loop. No shader, no fog.

## The player-caused guarantee

The Hush is never natural, and that is tested:
1. **Entry points:** `FluxService.deposit` is called only from the `neoforge.hush` listeners, the Pealing Bell's removal hook and the command; the `archTest` rule **`FLUX_ENTRY_POINTS`** ([architecture, Enforcement](architecture.md#enforcement), amended) enforces a **package allowlist**: only classes in `neoforge.hush..` and `core.hush..` may call it, and a review keeps the actual callers inside those packages to the three named here. It is a static caller check, not a proof of once-only delivery or conservation; the GameTests below prove those.
2. **No worldgen Hush:** a datagen-phase test parses every generated worldgen, structure and loot file **and every structure template's NBT palette and embedded block-entity and entity data**, and finds no hushed block, `vftv:hushed` node, Hollow or aura; [#27](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s contract below.
3. **Nothing self-propagates:** hushed blocks don't tick, hushed nodes aren't sources, Hollows convert nothing.
4. **Conservation:** `generated = spent + cleared + retained + pending + overflow` over every operation, so the Hush can't outgrow its spills.

**GameTest:** a world with no spill, every system's workload running and a player present, holds 0 flux and 0 hushed blocks after 6,000 ticks.

## Commands

`/vftv flux …` and `/vftv hush …`, permission level 2, under the [configuration spec's](config.md#commands) root. A target must be in a loaded chunk; otherwise `commands.vftv.flux.not_loaded` and nothing changes. **No command loads a chunk:** around a loaded target, `flux clear` skips every unloaded chunk in its square, and `hush restore` skips the cells of unloaded chunks, counting them as examined (as a peal's scan does), so its continuation index still advances. Dormant auras and stores are never changed except by `flux repair`.

| Command | Effect |
|---|---|
| `flux get [<pos>]` | the aura at `pos` (default the executor's position): status, flux, seep, spiller, `steps`, `taken`, saturation, and the level's pending count and overflow total |
| `flux add <pos> <amount 1–1,000,000>` | `deposit(level, pos, amount, COMMAND, executor-or-none)`, reporting the four amounts |
| `flux clear <pos> [<radius 0–4>]` | clears every readable aura in a loaded chunk within Chebyshev `radius` **chunks** (default 0); `FluxEvent(CLEARED, COMMAND)` per chunk |
| `flux repair <pos>` | a dormant aura becomes an empty readable one (the raw tag logged), and its pending entry merges |
| `hush restore <pos> <radius 0–16> [<from>]` | walks the ball of `radius` **blocks** around `pos` in the ball tables' order (ascending `(d², dy, dz, dx)`), starting at index `from` (default 0), examining at most 4,096 cells and making at most 64 restores of hushed blocks and journaled nodes; it reports the restores and **the next index** to pass as `from` (or "done" at the ball's end), so repeated calls finish the ball. No claim events: it is the operator's act |

## Rendering and sound

### Models

- **Hushed blocks:** vanilla parents (`cube_all`, `cube_column`, `cube_bottom_top`, `leaves`, `cross`), every property state of a block mapping to its one model with vanilla's axis rotations; leaves and short grass `cutout`; no tint indices.
- **Items:** `clearing_bell` and `hush_ash`, `minecraft:item/generated`; each hushed block's item uses its block model.
- **The Pealing Bell:** at most 6 cuboids ([art style §6](art-style.md#6-blocks-and-models)), a bell hung in a brass yoke, four facings by blockstate rotation.

**The Hollow,** frozen geometry in model pixels (1/16 block), origin at the feet's centre, y up, −z the front. Texture 64 × 32. Box UVs in vanilla's layout; `mirror` reuses the left net for the right side.

| Part | Pivot | Cuboid from | Size (w × h × d) | UV origin | Mirror |
|---|---|---|---|---|---|
| `body` | (0, 7, 0) | (−3, 7, −7) | 6 × 5 × 14 | (0, 0) | |
| `head` | (0, 10, −7) | (−2, 9, −11) | 4 × 5 × 4 | (40, 0) | |
| `leg_front_left` | (2, 7, −5) | (1, 0, −6) | 2 × 7 × 2 | (0, 19) | |
| `leg_front_right` | (−2, 7, −5) | (−3, 0, −6) | 2 × 7 × 2 | (0, 19) | yes |
| `leg_back_left` | (2, 7, 5) | (1, 0, 4) | 2 × 7 × 2 | (0, 19) | |
| `leg_back_right` | (−2, 7, 5) | (−3, 0, 4) | 2 × 7 × 2 | (0, 19) | yes |

- **The chest hole** is texture, not geometry: an `ink0` patch on the body's front and sides.
- **Motion:** diagonal leg pairs swing ±25° with vanilla's walk animation; the head follows the look direction (yaw clamped to ±45°, pitch to ±30°) and droops 10° with no target. A rise plays a 20-tick scale-in from 0.5 to 1.0 about the feet. Shadow radius 0.4. Client time only; no glow, no emissive layer.
- The model layer, renderer and animation are build-phase code. **Allocation:** zero per frame in our renderer and HUD draw code.

### Sound

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:block.hushed.step`, `.break`, `.place`, `.hit`, `.fall` | 3 (shared files) | the `vftv:hushed` sound type | `SoundSource.BLOCKS`; the sound type's volume 0.5 and pitch 0.8 |
| `vftv:clearing_bell.ring` | 3 | a ring that did something | at the player, to everyone in range; `SoundSource.PLAYERS`, volume 1.0, pitch 1.0 |
| `vftv:pealing_bell.ring` | 3 | a peal that did something | at the bell; `SoundSource.BLOCKS`, volume 2.0 (heard to 32 blocks), pitch 1.0 |
| `vftv:hush.restore` | 1 | a ring or peal that restored at least one block or node | at the first restored cell; `SoundSource.BLOCKS`, volume 0.6 |
| `vftv:hollow.rise` | 1 | a counted rise | at the Hollow; `SoundSource.HOSTILE`, volume 0.8 |
| `vftv:hollow.hurt` | 2 | hurt | likewise, volume 0.6 |
| `vftv:hollow.death` | 1 | death | likewise, volume 0.8 |

**Files:** `assets/vftv/sounds/hushed/soft_0.ogg` … `soft_2.ogg`; `clearing_bell/ring_0` … `ring_2`; `pealing_bell/ring_0` … `ring_2`; `hush/restore.ogg`; `hollow/rise.ogg`, `hurt_0`, `hurt_1`, `death.ogg`. The variant is vanilla's uniform choice among a `sounds.json` entry's files; entries carry no volume or pitch overrides and name `subtitles.vftv.<event>`. **No ambient sound** for the Hollow or the Hush: silence is the point. Every file follows [art style §7](art-style.md#7-sound-palette): the bells in their Stage's register (the Clearing Bell bright, Stage 2; the Pealing Bell lower and wetter, Stage 3), everything else under the Hush rule (lowpass, short tails, never a growl).

## Authored content

The content ticket delivers authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails its type's rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/hush/recipes.json        the Clearing Bell and the Pealing Bell
content/hush/node_types.json     the vftv:hushed node type
content/hush/fixtures.json       the vector tables, transcribed
content/lang/en_us/hush.json     every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `recipes.json` | `vftv:hush_recipes/1` | `data/vftv/recipe/clearing_bell.json`, `data/vftv/recipe/pealing_bell.json`, bodies unchanged except the [compatibility spec's ingredient widening](compat.md#consumed) |
| `node_types.json` | the wands spec's `vftv:node_types/1` | `data/vftv/vftv/node_type/hushed.json`; datagen merges node-type files and rejects an id declared twice |
| `fixtures.json` | `vftv:hush_fixtures/1` | nothing: read by the unit tests |
| `lang/<lang>/hush.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json`; a duplicate key across `content/lang` fails datagen |

The tuning file `data/vftv/tuning/hush.json` is written by datagen from the record's defaults ("Tuning and limits"); it is not authored content.

### Recipes

`{"schema": "vftv:hush_recipes/1", "recipes": {"vftv:clearing_bell": <arcane body>, "vftv:pealing_bell": <infusion body>}}`, exactly those two ids.

- **`vftv:clearing_bell`:** a [`vftv:arcane`](arcane.md#the-recipe-type) body with `research: vftv:clearing_bell`, `kind` ordinary, result one `vftv:clearing_bell`. **The early-construction rules:** every ingredient is one of `vftv:resonant_brass_ingot` (at most 2 in all), `minecraft:copper_ingot`, `minecraft:amethyst_shard`, `minecraft:stick` or `minecraft:string`; every `vis` key is one of the Wellspring's six guaranteed primals (`vftv:aer`, `vftv:terra`, `vftv:ignis`, `vftv:aqua`, `vftv:ordo`, `vftv:perditio`, [wands, Node types](wands.md#node-types)) and every value is **at most 32**, so a Stage 1 wand filled at the Wellspring alone pays it at zero warp, spilling 0 ("The pinned functions"). Brass is made in the crucible, whose crafting spills nothing ([crucible, Contracts](crucible.md#contracts-on-sibling-specs)). So the first bell is buildable, spill-free, as soon as `vftv:resonant_brass` is learned.
- **`vftv:pealing_bell`:** a [`vftv:infusion`](infusion.md#the-recipe-type) body with `research: vftv:the_hush`, `kind` ordinary, centre `minecraft:bell`, components drawn only from `vftv:resonant_brass_ingot`, `minecraft:amethyst_shard` and `#minecraft:wool` (4–8 in all), essentia only Sonus and Ordo (each 1–32), `instability` 0–20, result one `vftv:pealing_bell`.
- **Rejected:** another id; another `research` or `kind`; an ingredient outside its list; a Clearing Bell `vis` key outside the six; a value outside its bound; **any `vftv:hush_ash`** (no recipe may depend on having caused the Hush); anything the recipe type rejects.

```json
{"schema": "vftv:hush_recipes/1", "recipes": {
  "vftv:clearing_bell": {"type": "vftv:arcane", "research": "vftv:clearing_bell",
    "pattern": [" S ", "BAB", " C "],
    "key": {"S": "minecraft:stick", "B": "vftv:resonant_brass_ingot", "A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot"},
    "result": {"id": "vftv:clearing_bell", "count": 1},
    "vis": {"vftv:aer": 12, "vftv:ordo": 12}},
  "vftv:pealing_bell": {"type": "vftv:infusion", "research": "vftv:the_hush",
    "centre": "minecraft:bell",
    "components": ["vftv:resonant_brass_ingot", "vftv:resonant_brass_ingot", "minecraft:amethyst_shard",
                   "minecraft:amethyst_shard", "minecraft:white_wool", "minecraft:white_wool"],
    "essentia": {"vftv:sonus": 16, "vftv:ordo": 8},
    "instability": 10,
    "result": {"item": {"id": "vftv:pealing_bell", "count": 1}}}}}
```

### The node type

`{"schema": "vftv:node_types/1", "types": {"vftv:hushed": …}}` with exactly this shipped entry; **rejected:** any other id or any difference. This is the **only** declaration of `vftv:hushed`, and `node_type.vftv.hushed` is declared only in `content/lang/en_us/hush.json`: the [wands content](wands.md#authored-content) excludes both, and datagen validates required types and names after merging all files, rejecting any duplicate.

```json
{"schema": "vftv:node_types/1", "types": {
  "vftv:hushed": {"aspects": [{"aspect": "vftv:silentium", "range": [1, 1]}],
                  "main_aspect": "vftv:silentium",
                  "clarity": {"faint": 1, "steady": 0, "clear": 0}}}}
```

### Fixtures

`{"schema": "vftv:hush_fixtures/1", "spill": [...], "essentia": [...], "phase": [...], "stream": [...], "kernel": [...], "scheduler": [...], "hud": [...], "ball": [...]}`. **All eight sections are required**, and together they transcribe every vector table in this spec exactly, one row per vector. The unit tests replay them against the `model` code; that consumer is build work. Every field is required unless a default is given or it is marked conditional.

**Types used below:** *int* is a JSON integer; *hex* is a string `"0x"` followed by 1–16 uppercase hex digits, read as a 64-bit word (every `long`); a *cell* is `[x, y, z]` of ints with x and z in −30,000,000…30,000,000 and y in −64…319; a *chunk* is `[x, z]` of ints in −1,875,000…1,875,000. Where a field shares a tuning key's meaning, it **inherits that key's range** from "Tuning and limits".

| Record | Fields |
|---|---|
| `spill` | `cap` int 250–2000 (the wands cap, per mille); `d` int 0–900; `w` int 0–10,000; `base` 1–8 ints 0–1000; `kind` `"ordinary"` \| `"void"`; `tax_pm`, `void_pm` (default 1000, 250; inherit `flux.tax_pm`, `flux.void_pm`); `min_m` int 100–1000 (default 500), `max_m` int 1000–10,000 (default 4000): the wands `quote.min_multiplier` and `quote.max_multiplier` per mille. `expect`: `effective` and `neutral`, each ints 0–10,000 of the same length as `base`; `surcharge` int ≥ 0, **present iff `kind` is `ordinary`**; `spill` int 0–1,000,000 |
| `essentia` | `total` int 0–640,000; `pm` int (inherits `flux.essentia_pm`); `times` int 1–64 (default 1: the expected value is `times × essentiaFlux(total)`, the infusion sequence bound); `expect` int |
| `phase` | `chunk`; `interval` int (inherits `hush.interval`); `expect` `{fold: hex (exactly 16 digits), phase: int 0…interval − 1}` |
| `stream` | `seed` hex; `chunk`; `steps` hex; `count` int 1–64; `expect` `{h: hex (16 digits), directions: count ints 0–5}` |
| `kernel` | `world` exactly `"flat63"` (defined under "Vectors"); `seed` hex; `seep` cell; `radius`, `walk` ints (inherit `hush.radius`, `hush.walk`); `attempts` int 1–64; `expect`: exactly `attempts` rows `{steps: hex, result: "convert" \| "miss", at: cell **iff convert**, reason: "none" \| "unloaded" **iff miss**, used: int 1…walk}`, `steps` counting from 0. Each conversion stays hushed for later attempts; costs and onset are not applied |
| `scheduler` | `world`, `seed`, `seep`, `radius`, `walk` as `kernel`; `nodes`: cells holding a hushable node (default `[]`); `flux` int 0–1,000,000; `onset`, `block_cost`, `node_cost` (inherit `hush.*`); `every` (inherits `hollow.every`); `hollow_flux` (inherits `hollow.flux`); `visits` int 1–64. `expect`: exactly `visits` rows `{visit: int, from 0; result: "block" \| "node" \| "miss" \| "idle"; at: cell **iff block or node**; reason **iff miss**; used: int **iff not idle**; rise: "rose" \| "no_rise" **iff block and a Hollow turn**; flux: int, **after the visit**, so after the conversion's debit and, when it rose, after `hollow_flux` too; steps, taken: hex, after the visit}`. `idle` means `flux < max(onset, block_cost)`: no attempt, `steps` unchanged. A rise is allowed iff the cell above the converted one is unhushed air and the flux after the conversion is at least `hollow_flux`; caps, players and events are not modelled |
| `hud` | `onset`, `block_cost` (inherit); `flux` int 0–1,000,000; `expect` int (the bound under "Reading") |
| `ball` | `radius` int 1–16; `expect` `{count: int, first: exactly 8 triples [dy, dz, dx], last: one triple}` |

**The complete file,** every required row, printed by the reference implementation (`hush_ref.py fixtures`):

```json
{"schema": "vftv:hush_fixtures/1",
 "spill": [
  {"cap": 1000, "d": 0, "w": 1000, "base": [10, 0, 1], "kind": "ordinary", "expect": {"effective": [10, 0, 1], "neutral": [10, 0, 1], "surcharge": 0, "spill": 0}},
  {"cap": 850, "d": 300, "w": 1000, "base": [10, 3, 1], "kind": "ordinary", "expect": {"effective": [6, 2, 1], "neutral": [6, 2, 1], "surcharge": 0, "spill": 0}},
  {"cap": 850, "d": 100, "w": 1250, "base": [32, 7], "kind": "ordinary", "expect": {"effective": [31, 7], "neutral": [25, 6], "surcharge": 7, "spill": 7}},
  {"cap": 2000, "d": 0, "w": 10000, "base": [1000], "kind": "ordinary", "expect": {"effective": [4000], "neutral": [2000], "surcharge": 2000, "spill": 2000}},
  {"cap": 250, "d": 900, "w": 1000, "base": [5], "kind": "ordinary", "expect": {"effective": [3], "neutral": [3], "surcharge": 0, "spill": 0}},
  {"cap": 250, "d": 900, "w": 1500, "base": [100], "kind": "ordinary", "expect": {"effective": [50], "neutral": [50], "surcharge": 0, "spill": 0}},
  {"cap": 1000, "d": 0, "w": 1200, "base": [30], "kind": "ordinary", "expect": {"effective": [36], "neutral": [30], "surcharge": 6, "spill": 6}},
  {"cap": 1000, "d": 0, "w": 1030, "base": [3], "kind": "ordinary", "expect": {"effective": [4], "neutral": [3], "surcharge": 1, "spill": 1}},
  {"cap": 850, "d": 0, "w": 1370, "base": [12, 12], "kind": "ordinary", "expect": {"effective": [14, 14], "neutral": [11, 11], "surcharge": 6, "spill": 6}},
  {"cap": 1000, "d": 0, "w": 1000, "base": [40], "kind": "void", "expect": {"effective": [40], "neutral": [40], "spill": 10}},
  {"cap": 1000, "d": 0, "w": 1000, "base": [1], "kind": "void", "expect": {"effective": [1], "neutral": [1], "spill": 1}},
  {"cap": 2000, "d": 0, "w": 10000, "base": [1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000], "kind": "ordinary", "expect": {"effective": [4000, 4000, 4000, 4000, 4000, 4000, 4000, 4000], "neutral": [2000, 2000, 2000, 2000, 2000, 2000, 2000, 2000], "surcharge": 16000, "spill": 16000}}],
 "essentia": [
  {"total": 0, "pm": 1000, "expect": 0},
  {"total": 0, "pm": 500, "expect": 0},
  {"total": 1, "pm": 1000, "expect": 1},
  {"total": 1, "pm": 500, "expect": 1},
  {"total": 3, "pm": 1000, "expect": 3},
  {"total": 3, "pm": 500, "expect": 2},
  {"total": 200, "pm": 1000, "expect": 200},
  {"total": 200, "pm": 500, "expect": 100},
  {"total": 1000, "pm": 1000, "expect": 1000},
  {"total": 1000, "pm": 500, "expect": 500},
  {"total": 10000, "pm": 1000, "expect": 10000},
  {"total": 10000, "pm": 500, "expect": 5000},
  {"total": 640000, "pm": 1000, "expect": 640000},
  {"total": 640000, "pm": 500, "expect": 320000},
  {"total": 50, "pm": 1000, "times": 6, "expect": 300}],
 "phase": [
  {"chunk": [0, 0], "interval": 10, "expect": {"fold": "0xC21D48E4BBC2BCC8", "phase": 2}},
  {"chunk": [-1, 0], "interval": 10, "expect": {"fold": "0xEF2949749A6FF19D", "phase": 7}},
  {"chunk": [0, -1], "interval": 10, "expect": {"fold": "0x970A1F109E0E25D4", "phase": 4}},
  {"chunk": [-12345, 6789], "interval": 10, "expect": {"fold": "0x1CFB81FE4D218FCB", "phase": 3}}],
 "stream": [
  {"seed": "0x123456789ABCDEF", "chunk": [0, 0], "steps": "0x0", "count": 12, "expect": {"h": "0x88B1CC95D8BDED57", "directions": [2, 5, 5, 5, 4, 0, 4, 1, 3, 3, 3, 4]}}],
 "kernel": [
  {"world": "flat63", "seed": "0x123456789ABCDEF", "seep": [8, 64, 8], "radius": 12, "walk": 32, "attempts": 16, "expect": [{"steps": "0x0", "result": "convert", "at": [10, 63, 7], "used": 6}, {"steps": "0x1", "result": "convert", "at": [7, 63, 7], "used": 5}, {"steps": "0x2", "result": "convert", "at": [8, 63, 8], "used": 1}, {"steps": "0x3", "result": "miss", "reason": "none", "used": 32}, {"steps": "0x4", "result": "convert", "at": [13, 63, 9], "used": 13}, {"steps": "0x5", "result": "convert", "at": [9, 63, 7], "used": 15}, {"steps": "0x6", "result": "convert", "at": [9, 63, 9], "used": 5}, {"steps": "0x7", "result": "convert", "at": [5, 63, 8], "used": 8}, {"steps": "0x8", "result": "convert", "at": [7, 63, 8], "used": 2}, {"steps": "0x9", "result": "convert", "at": [7, 63, 9], "used": 5}, {"steps": "0xA", "result": "convert", "at": [9, 63, 8], "used": 2}, {"steps": "0xB", "result": "convert", "at": [7, 62, 8], "used": 15}, {"steps": "0xC", "result": "miss", "reason": "none", "used": 32}, {"steps": "0xD", "result": "miss", "reason": "none", "used": 32}, {"steps": "0xE", "result": "convert", "at": [11, 63, 11], "used": 15}, {"steps": "0xF", "result": "convert", "at": [9, 62, 7], "used": 4}]},
  {"world": "flat63", "seed": "0x123456789ABCDEF", "seep": [15, 64, 15], "radius": 12, "walk": 32, "attempts": 6, "expect": [{"steps": "0x0", "result": "miss", "reason": "unloaded", "used": 2}, {"steps": "0x1", "result": "convert", "at": [14, 63, 14], "used": 5}, {"steps": "0x2", "result": "convert", "at": [15, 63, 15], "used": 1}, {"steps": "0x3", "result": "miss", "reason": "unloaded", "used": 2}, {"steps": "0x4", "result": "miss", "reason": "unloaded", "used": 1}, {"steps": "0x5", "result": "miss", "reason": "unloaded", "used": 2}]}],
 "scheduler": [
  {"world": "flat63", "seed": "0x123456789ABCDEF", "seep": [8, 64, 8], "radius": 12, "walk": 32, "nodes": [[8, 64, 9]], "flux": 150, "onset": 50, "block_cost": 5, "node_cost": 50, "every": 4, "hollow_flux": 25, "visits": 16, "expect": [{"visit": 0, "result": "block", "at": [10, 63, 7], "used": 6, "flux": 145, "steps": "0x1", "taken": "0x1"}, {"visit": 1, "result": "block", "at": [7, 63, 7], "used": 5, "flux": 140, "steps": "0x2", "taken": "0x2"}, {"visit": 2, "result": "block", "at": [8, 63, 8], "used": 1, "flux": 135, "steps": "0x3", "taken": "0x3"}, {"visit": 3, "result": "miss", "reason": "none", "used": 32, "flux": 135, "steps": "0x4", "taken": "0x3"}, {"visit": 4, "result": "node", "at": [8, 64, 9], "used": 1, "flux": 85, "steps": "0x5", "taken": "0x3"}, {"visit": 5, "result": "block", "at": [9, 63, 7], "used": 15, "rise": "rose", "flux": 55, "steps": "0x6", "taken": "0x4"}, {"visit": 6, "result": "block", "at": [9, 63, 9], "used": 5, "flux": 50, "steps": "0x7", "taken": "0x5"}, {"visit": 7, "result": "block", "at": [5, 63, 8], "used": 8, "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 8, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 9, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 10, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 11, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 12, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 13, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 14, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}, {"visit": 15, "result": "idle", "flux": 45, "steps": "0x8", "taken": "0x6"}]}],
 "hud": [
  {"onset": 50, "block_cost": 2, "flux": 200, "expect": 76},
  {"onset": 50, "block_cost": 5, "flux": 200, "expect": 31},
  {"onset": 50, "block_cost": 5, "flux": 49, "expect": 0},
  {"onset": 50, "block_cost": 5, "flux": 50, "expect": 1},
  {"onset": 0, "block_cost": 5, "flux": 6, "expect": 1},
  {"onset": 0, "block_cost": 5, "flux": 4, "expect": 0},
  {"onset": 10, "block_cost": 3, "flux": 11, "expect": 1}],
 "ball": [
  {"radius": 5, "expect": {"count": 515, "first": [[0, 0, 0], [-1, 0, 0], [0, -1, 0], [0, 0, -1], [0, 0, 1], [0, 1, 0], [1, 0, 0], [-1, -1, 0]], "last": [5, 0, 0]}},
  {"radius": 8, "expect": {"count": 2109, "first": [[0, 0, 0], [-1, 0, 0], [0, -1, 0], [0, 0, -1], [0, 0, 1], [0, 1, 0], [1, 0, 0], [-1, -1, 0]], "last": [8, 0, 0]}}]}
```

**Rejected:** a section missing; an unknown field; a conditional field present when its condition is false, or absent when true; a vector of the wrong length; a value outside its type or range; a row of a vector table missing or altered; a `world` other than `flat63`. The file above is the one the content ticket ships, so it passes every rule. Fixtures model arithmetic, streams and the walk only; scheduling, callbacks, caps and persistence are proved by the GameTests.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text, and its review checks that the HUD lines read as an instrument's exact reading and that "up to" is kept.

| Key | Args | Sense |
|---|---|---|
| `block.vftv.hushed_grass_block`, `.hushed_dirt`, `.hushed_stone`, `.hushed_deepslate`, `.hushed_sand`, `.hushed_gravel`, `.hushed_short_grass`, `.hushed_oak_log`, `.hushed_birch_log`, `.hushed_spruce_log`, `.hushed_oak_leaves`, `.hushed_birch_leaves`, `.hushed_spruce_leaves`, `block.vftv.pealing_bell` | 0 | "Hushed Grass Block", …, "Pealing Bell" |
| `item.vftv.clearing_bell`, `item.vftv.hush_ash`, `entity.vftv.hollow`, `node_type.vftv.hushed` | 0 | "Clearing Bell", "Hush Ash", "Hollow", "Hushed" |
| `hud.vftv.hush.unknown`, `.dormant`, `.none` | 0 | "I can't read this ground yet.", "This ground's record is unreadable.", "Nothing pools here." |
| `hud.vftv.hush.quiet` | 2 | "This ground holds %s flux. It begins to hush at %s." |
| `hud.vftv.hush.hushing` | 2 | "This ground holds %s flux: enough to hush up to %s blocks." |
| `hud.vftv.hush.seep` | 3 | "It pools at %s, %s, %s." |
| `message.vftv.hush.nothing` | 0 | "Nothing here is hushed." |
| `status.vftv.pealing_bell` | 2 | "Sonus: %s of %s." (sneak-use with an empty hand, built on the server) |
| `status.vftv.pealing_bell.dormant` | 0 | "This bell's record is unreadable." |
| `commands.vftv.flux.get` | 9 | "%s: %s flux, seep %s, by %s, %s attempts, %s taken, %s; pending %s, overflow %s" |
| `commands.vftv.flux.added` | 4 | "Generated %s: retained %s, pending %s, overflow %s." |
| `commands.vftv.flux.cleared` | 1 | "Cleared %s flux." |
| `commands.vftv.hush.restored` | 2 | "Restored %s; continue from %s." |
| `commands.vftv.hush.restored_done` | 1 | "Restored %s; the ball is done." |
| `commands.vftv.flux.repaired`, `.not_loaded`, `.not_dormant` | 0 | |
| `commands.vftv.flux.status.readable`, `.dormant`, `.none`, `.saturated`, `.waiting` | 0 | |
| `options.vftv.hush.muffle`, `.hud` | 0 | "Muffle in the Hush", "Hush reading" |
| `hush.vftv.fact.on`, `.off` | 0 | "on", "off" (the research spec's `hush` switch facts) |
| `subtitles.vftv.<event>` for every sound event above | 0 | "Hushed steps", "Bell rings", "A great bell peals", "Colour returns", "A Hollow rises", "Hollow hurts", "Hollow falls apart" |

**Used, not declared here:** aspect and player names; the arcane and infusion recipe pages.

**Rejected:** a key outside the table; a required key missing in `en_us`; a placeholder count that differs; an empty string; a duplicate key, including any other spec's key redeclared here.

## Events

NeoForge events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server through a publisher seam that `neoforge` implements. Payload records live in `api` and are **immutable**, captured before any listener runs. The payload shape (`HushChange`, `HushOrigin`, `FluxSource`, `FluxClearSource`) and the failure rules are the [API spec's](api.md#events). **`HushSpreadEvent` fails closed:** a throwing listener makes the mutation a miss, with nothing changed and nothing debited ("The operation").

| Event | When | Payload |
|---|---|---|
| `HushSpreadEvent` (cancellable) | before each Hush mutation, under "The operation" (not for a Clearing Bell, which posts `BreakBlockEvent`, nor for commands) | kind `CONVERT` \| `NODE` \| `RISE` \| `RESTORE`; level and position; before and after (block states; a node's id, type before and type after; or the entity type); origin: `SEEP(chunk, spiller or none)` for the spread, `BELL(position)` with no actor for a peal |
| `FluxEvent` | after a deposit or a clear has settled | kind `ADDED` \| `CLEARED`; level and chunk; seep or none; for `ADDED`: generated, retained, pending, overflow, the source `VIS` \| `ESSENTIA(SpillCause)` \| `COMMAND` and the player or none; for `CLEARED`: the amount, the source `BELL` \| `PEAL` \| `COMMAND`, the player or none, and the flux before and after |

- **No event** for an attempt, a miss, a saturation, a merge of pending flux or a zero deposit.
- **A failed post** (a listener threw) is logged once; the change stands, and **our later posts** of the same operation still run. The remaining subscribers of the post that failed are NeoForge's dispatch, not ours to resume.

**Plain `core` services:** `FluxService.deposit`, `.clear` (`core.hush`); `FluxMath.visSpill`, `.essentiaFlux`, `.essentiaFluxBound` (`core.flux.model`); `HushService.readout(level, chunk)`. They are **internal in v1** ([API spec, Services](api.md#services)): no addon deposits or clears flux.

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| the aura | `vftv:aura` chunk attachment | players tracking the chunk, through an `AttachmentSyncHandler` (`NF:attachment/AttachmentType.java:263–266`; `AttachmentSyncHandler.java:57,67`) writing **`{status: NONE \| READABLE \| DORMANT, flux: 0–1,000,000, seep?: long}`** only, for every later change of an existing aura; NeoForge's own initial send on `ChunkWatchEvent.Sent` (`NF:attachment/AttachmentSync.java:162–180,203–207`) covers **existing** attachments only |
| the chunk's status at watch | none (derived) | S2C **`HushChunkStatus`** `{chunk: long, status, flux, seep?}`, the same envelope plus the chunk's identity, sent to the watching player on `ChunkWatchEvent.Sent` for **every** chunk, including one with no aura (`NONE`, flux 0), so pristine ground reads "nothing pools here" rather than "unknown" |
| `FluxRules` | the tuning record, the server switches and the fixed limits | S2C to every player through `OnDatapackSyncEvent` (`NF:event/OnDatapackSyncEvent.java:20–22,50–51`: join and `/reload`), and again when a server config change alters a Hush switch (`FML:event/config/ModConfigEvent.java:23–42`; [configuration spec, Publishing a change](config.md#publishing-a-change)) |
| hushed blocks, Hollows | blocks, entities | vanilla's |
| the Pealing Bell | its block entity | trackers: initial data, then on a change of `sonus` (the status line is built on the server; the client draws nothing from it) |
| pending deposits, the overflow total | `vftv_flux_pending` (per level) | never |
| hushed-node originals | `vftv_hushed_nodes` (overworld) | never |
| due queues, saturation, the merge queue, the bell index, guards | memory, rebuilt from loaded chunks and block entities | never |

- **In-place mutation, coalesced sync:** the aura is created once by the first retaining deposit; after that every write mutates it in place and marks the chunk in a per-level dirty set, flushed once per tick after the level ticks with one `LevelChunk.syncData(vftv:aura)` per chunk (`MC:world/level/chunk/LevelChunk.java:749–750`). `setData` is never called again, since it would sync on every write. **Creation** auto-syncs once (`NF:attachment/AttachmentHolder.java:74–81,97–102`), so **the creation tick may carry two sends**, the creation's and the coalesced flush; every later tick at most one. Initial delivery to a newly watching player (`HushChunkStatus`, plus NeoForge's attachment send for an existing aura) is separate.
- **The client's knowledge:** a per-level map from chunk to the last status received (from `HushChunkStatus` or an attachment update, whichever came last), pruned, whenever the local player's chunk changes, to the square of the client's render distance + 1 chunks around it (no unload hook is needed: a chunk is always re-sent, with a fresh `HushChunkStatus`, before the player can stand in it, so a stale entry is never read), and cleared entirely on a level change and at logout.
- **`FluxRules`** is a fixed record: `format` 1, a `generation` (`long`: `fold([0x464C5558 ("FLUX"), format, every value in key order, the switches as 0 or 1])`, so equal rules share a generation across restarts and a changed value changes it, which is what the [infusion spec's](infusion.md#abandoning) persisted abandon arm compares), every tuning key, the three server switches and the fixed limits the client needs. Its `Codec` is what the [infusion spec](infusion.md#sync-and-state) freezes into a run; a run's record is validated against the format bounds only.
- **Bounded codecs:** the aura, the sync payload and `HushChunkStatus` (status one byte, flux 0–1,000,000, an optional seep) as above; `vftv_flux_pending` ≤ 4,096 entries; `vftv_hushed_nodes` ≤ 65,536 entries, type ids ≤ 256 characters; the bell's record; `FluxRules` a fixed record. Out of bounds is rejected at decode, and a persisted record then stays raw (dormant).
- **Raw retention everywhere:** the aura, both SavedData and the bell keep an unreadable record verbatim and write it back unchanged, with one warning each.
- **Saving:** every write marks its holder dirty (`markUnsaved`, `setDirty`, `setChanged`).
- **Removing the mod** leaves hushed blocks and Hollows to NeoForge's missing-registry handling; stated.

**Reloads:**

| Change | Effect |
|---|---|
| Tuning (`/reload`) | a new record (a new generation if any value changed), re-sent; due queues rebuilt and saturation cleared; quotes re-resolve, so stale echoes are refused; infusion runs keep their frozen record |
| Server config | `hush.spread` at the next service (off keeps the backlog; on resumes it in due order); `hush.hollows` at the next rise; `hush.unattended` at the next spread service and the next Pealing Bell service, with no wake. Nothing already converted, restored or risen is reversed. `FluxRules` re-sent if a switch changed. |
| Resource reload | the HUD and models rebuild |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/hush.json`**, loaded on reload into an immutable record in `core.flux` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client receives the whole record in `FluxRules`. Every algorithm above is defined across these full ranges.

| Key | Value | Range | Notes |
|---|---|---|---|
| `flux.essentia_pm` | 1000 | 0–1000 | flux per 1000 essentia spilled |
| `flux.tax_pm` | 1000 | 0–1000 | flux per 1000 vis of warp surcharge |
| `flux.void_pm` | 250 | 0–1000 | flux per 1000 vis of Void work |
| `hush.onset` | 50 | 0–100,000 | `threshold = max(onset, block_cost)` |
| `hush.block_cost`, `hush.node_cost` | 2, 50 | 1–10,000, 1–100,000 | |
| `hush.interval` | 10 ticks | 1–1,200 | |
| `hush.walk` | 32 | 1–64 | steps per attempt |
| `hush.radius` | 12 | 1–16 | blocks from the seep |
| `hush.max_misses` | 32 | 1–255 | |
| `hush.player_chunks` | 8 | 1–32 | attendance, Chebyshev chunks |
| `hollow.every` | 8 | 1–1,000 | conversions per Hollow turn |
| `hollow.flux` | 25 | 0–100,000 | |
| `hollow.max_near` | 3 | 0–16 | the loaded admission cap near a seep |
| `hollow.min_player_distance` | 8 | 0–32 | blocks |
| `bell.flux`, `bell.restore`, `bell.radius` | 50, 8, 5 | 0–100,000, 0–64, 1–8 | |
| `bell.cooldown` | 40 ticks | 0–1,200 | |
| `peal.interval` | 200 ticks | 20–24,000 | |
| `peal.flux`, `peal.restore`, `peal.radius` | 50, 8, 12 | 0–100,000, 0–64, 1–16 | |
| `peal.scan` | 1,024 | 1–4,096 | cells per peal |
| `peal.capacity`, `peal.suction` | 64, 16 | 1–1,000, 0–128 | Sonus; the [essentia scale](essentia.md#suction) |

**Other homes:**
- the hushed-form table, the Hollow's stats and the Clearing Bell's durability 64: code, fixed at registration and asserted by unit tests;
- the recipes: `content/hush/recipes.json`;
- **server config** (admin switches; files, validation and sync are the [configuration spec's](config.md#server-keys)), each proven by a GameTest: `hush.spread` (on; off means no attempt is made at all), `hush.hollows` (on; off means no rise), `hush.unattended` (off; on lets unattended chunks spread and unattended Pealing Bells peal; bells run whether `hush.spread` is on or off, and a Silenced player attends a bell);
- **client config:** `hush.muffle` (on), `hush.hud` (on).

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`HushLimits`** in `api`:

| Constant | Value |
|---|---|
| `FLUX_FORMAT_MAX`, `MAX_SPILL_FLUX`, `MAX_ESSENTIA_SPILL` | 1,000,000, 1,000,000 (the crucible's), 640,000 (the essentia spec's) |
| `MAX_PENDING`, `MAX_HUSHED_NODES` | 4,096, 65,536 |
| `MAX_POPS_PER_TICK`, `MAX_VISITS_PER_TICK`, `MAX_SPREAD_MUTATIONS_PER_TICK` | 64, 32, 4 (per level) |
| `MAX_PEAL_POPS_PER_TICK`, `MAX_PEALS_PER_TICK` | 16, 4 (per level) |
| `MAX_RING_ATTEMPTS_PER_PLAYER_PER_TICK` | 1 |
| `MAX_WALK`, `MAX_RADIUS`, `MAX_BELL_RADIUS`, `MAX_PEAL_SCAN` | 64, 16, 8, 4,096 |
| `MAX_COMMAND_CELLS`, `MAX_COMMAND_MUTATIONS` | 4,096, 64 |

**Also fixed in code:** the salts `HUSH` and `PEAL`; the direction order; the ball and cube scan orders; the swap flags; the operation order. **Fixed feedback constants:** the muffle gain 0.5 and its categories; the mote chance 1 in 64; 8 ring motes; the HUD geometry; the Hollow's model, motion and rise scale-in; sound volumes as stated.

## Performance

**Heavy-base workload,** sustained through warm-up and the 6,000 measured ticks:
- one player at a base whose 4 crucibles are emptied into 4 chunks once a minute;
- 16 attended candidate chunks converting at the default cadence. **The fixture keeps them busy:** every 30 s it tops each one's flux back to 500 (`/vftv flux add`) and restores its hushed ground (`/vftv hush restore`), so terrain stays convertible and the walk never idles;
- 64 more loaded chunks with sub-threshold or saturated pools (no work by construction) and 64 unattended candidates (pops only, with `hush.unattended` off);
- 16 Hollows loaded around four seeps, 8 chasing the player; the fixture kills 4 every 30 s so rises keep succeeding;
- 2 Pealing Bells on a Sonus line whose jar the fixture refills, pealing every 200 ticks;
- the player ringing a Clearing Bell every 2 s over restored-and-rehushed ground;
- the [wands workload's](wands.md#performance) 4 spends a second, carrying a warp surcharge; one golem death a minute.

**Client:** standing in a 400-block Hush with 8 Hollows in view, the HUD up and muffling on, crossing the Hush's edge every 10 s.

**Share:** server ≤ **0.05 ms mean, ≤ 0.20 ms p99**, measured in the combined stress world, including the swaps' native work, the events' listeners, the Hollows' vanilla AI, scheduler maintenance and sync. Client ≤ **0.02 ms p50**, including the muffle's gain refreshes. Shares are acceptance targets, not measurements.

**The envelope's running totals** (planning arithmetic, never a combined percentile): the finished specs' server shares (aspects 0.02/0.10, research 0.03/0.15, wands 0.04/0.20, arcane 0.01/0.05, crucible 0.03/0.15, essentia 0.04/0.20, infusion 0.02/0.10, foci 0.03/0.15, golems 0.25/0.80, warp 0.02/0.08, armour 0.01/0.04) total **0.50 ms mean / 2.02 ms p99**; with the Hush, **0.55 / 2.22**, leaving **0.45 / 0.78** for Worldgen and mobs and the Eldritch tier. Client shares total **0.82 ms p50**, **0.84** with the Hush, leaving **0.16**.

**Measured separately as load scenarios** (reported): every loaded chunk a candidate at once; a deposit flood (1,000 spends in one tick); a tuning `/reload` rebuilding the queues over 4,096 candidates; 64 bells due in one tick; a chunk-load storm merging 4,096 pending entries; maximum tuning (`walk` 64, `peal.scan` 4,096, `bell.radius` 8, `peal.restore` and `bell.restore` 64); a reload that brings back more Hollows than the cap.

**Hot paths,** per level per tick unless stated:

| When | Work |
|---|---|
| Spread service | ≤ 64 pops (each O(log n), with an O(players) attendance check) and ≤ 32 visits of ≤ `hush.walk` `getChunkNow` + block reads each; ≤ 4 spread mutations, each **a native term** (the swap's chunk write and client packet, `NodeService.update` and its event, or `addFreshEntity` and its callbacks), plus their `HushSpreadEvent` listeners and one AABB entity query per rise (**native**, O(entities in its sections)), disclosed |
| Peals | ≤ 16 queue pops (each O(log n), skips included), of which ≤ 4 peal, each ≤ 9 aura reads, ≤ `peal.scan` cell reads and ≤ `peal.restore` + 1 mutations with their events |
| Rings | ≤ 1 **attempt** per player, counted before the scan and including attempts that find nothing, each ≤ the ball table's cells (515 at default radius, 2,109 at 8) and ≤ `bell.restore` + 1 mutations, each with `BreakBlockEvent` |
| **In all** | reads ≤ `32 · walk + 4 · (9 + peal.scan) + R · ball(bell.radius)`; mutations ≤ `4 + 4 · (peal.restore + 1) + R · (bell.restore + 1)`, with R the players attempting a ring that tick; pops ≤ 64 + 16: at defaults ≤ 5,156 + 515 R reads and ≤ 40 + 9 R mutations; at maximum tuning ≤ 18,468 + 2,109 R reads and ≤ 264 + 65 R mutations. Load scenarios beyond these are disclosed above |
| Per deposit | O(1) plus one O(log n) queue update and one dirty mark |
| Merges | the queued chunks of the previous tick, one O(1) merge each |
| Per place query | 2 block reads |
| Hollows | vanilla mob ticks for every loaded Hollow (≤ `hollow.max_near` per seep at admission, all paid in flux; more after a reload, disclosed) |
| Client per tick | one place query; a gain change on entering or leaving refreshes every active sound (**native**) |
| Client per frame | the HUD's cached layout and the visible Hollows' models; **zero allocation in our draw code** |
| Allocation | **zero** in `visSpill`, `essentiaFlux`, the walk, the phase and the ball tables (allocation-budget tests); finite on mutation |
| Packets | ≤ 1 aura sync per chunk per tick, or 2 in its creation tick; one `HushChunkStatus` per chunk a player starts watching (a few bytes, beside the vanilla chunk packet); one block update per swap; `FluxRules` per join, reload or config reload |

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **every vector** in this spec, replayed from `content/hush/fixtures.json`: the spill table (including the clamped surcharge 0 and the clamped 2,000), essentia and the bound, phases, streams, the walk kernel and edge, the onset-enforced scheduler with its node and rise, the HUD bound and both ball tables;
- `essentiaFluxBound ≥ essentiaFlux` for every map up to 1,000 units (exhaustive over small maps) and the infusion sequence bound under per-event rounding; `long` totals at the maxima; negative inputs throw;
- the walk on synthetic grids: staying at the ball and build limits, `UNLOADED` at a non-ticking chunk, node targets, immune cells passed, saturation after `max_misses`;
- the due queue: one entry per chunk under repeated deposits, oldest-due-first carry with no starvation, the 2-slot reservation on a Hollow turn, attendance flips and rebuilds;
- the hushed-form table: bijective, property-complete, light properties equal per state, support-tag parity with vanilla's tag data;
- deposits: zero is a no-op; clipping at `FLUX_FORMAT_MAX`; a full pending table; the conservation identity over random sequences of deposits, attempts, clears and merges;
- codecs: the aura (bounds, defaults, the raw round trip), its sync payload, both SavedData, the bell's record, `FluxRules`, the tuning file;
- every `content/` rejection rule: recipe ingredient lists and bounds, no Hush Ash, the fixture schema.

**GameTest:**
- **previewed spill = generated flux** for an arcane craft and a cast at warp 20 and a `VOID` cast ([wands](wands.md#contracts-on-sibling-specs), [arcane](arcane.md#contracts-on-sibling-specs), [foci](foci.md#contracts-on-sibling-specs)); the crucible HUD's `EMPTIED` preview = the generated flux on emptying **and** on breaking; a destroyed jar stack generates once; an upset under frozen rules and an abandon under current ones; a pre-#26 run's `UPSET` generates 0 while its abandon generates under current rules; a golem's death at its death position;
- **at the caps:** an aura at 999,999 plus a spill of 10 retains 1 and reports 9 overflow, for an ordinary and a frozen-rule event; a full pending table;
- **each once,** with a throwing addon listener registered after ours; the documented limitation with an earlier `HIGHEST` thrower;
- an unloaded target chunk goes to pending, merges at load, and no chunk is ever loaded; a dormant aura holds its pending entry until `flux repair`;
- onset and costs; **every form converted and restored, state for state**; swaps at an unloaded chunk boundary in both directions with nothing loaded; flora, vines, snow layers and leaves beside swapped cells survive; the swap itself schedules no falling tick on restored sand (a tick queued before the swap, or a later update, may still drop it);
- **the operation:** a cancelled `HushSpreadEvent` changes and spends nothing; a listener that replaces the cell, unloads the chunk or drains the aura makes it a miss; a listener that rings a bell is refused by the guard;
- `hush.spread` off (zero conversions in 6,000 ticks with 10,000 flux), `hush.hollows` off, unattended chunks idle, `hush.unattended` on; **walking back to an old pool wakes it**; the per-tick caps under the flood; saturation and each of its exits;
- **nodes:** hushed and restored through `NodeService` (type and clarity exact), carried in a Bell Jar between; **in both directions**, a `NodeEvent(CHANGED)` listener that throws (`COMMITTED_NOTIFY_FAILED`: the cost debited or the entry deleted at the boundary, never a miss, never retried), removes, captures or retypes the node (the journal kept, deleted or left stale as specified, never overwriting the later type), and sees the bookkeeping already settled; the Wellspring never hushed; `NOT_LOADED` a miss with nothing journaled;
- **Hollows:** each rise rule in turn (peaceful, the cap, flux, the spot, players, the event, the insertion check), flux debited only on a counted rise; a join listener that moves ours; pursuit dropped when the target leaves home; return home; persistence; a reload above the cap; the shipped stats (health 30, attack 5, speed 0.26);
- **Silenced players** (the [Eldritch tier's](eldritch.md#the-capstones) capstone): a pool beside only a Silenced player doesn't spread; beside a Silenced and an unsilenced player it does; with `hush.unattended` on it does; a Silenced player's spill still deposits; a Pealing Bell beside only a Silenced player still peals; a Hollow never acquires a Silenced player, drops its target the tick after that player is Silenced, and still hunts an unsilenced one beside them;
- **the Clearing Bell:** each HUD line; ringing (clear, restore, both, nothing), cooldown, **a node beside a pool** (the first rings clear and restore blocks but not the node; the first ring that clears nothing restores it, and a peal likewise), durability and creative, spawn protection and a cancelled `BreakBlockEvent`; **one ring attempt per tick** at cooldown 0, a second use in the tick, including on empty ground, scanning nothing; radius 8; `bell.restore` 0 still restoring a node; a ring's own restores not refused by its held guard;
- **the Pealing Bell:** feeding through a transaction that rolls back and one that commits; suction on exactly the handler's faces, and the handler unavailable during a peal; pealing, the reservation committed on first work and refunded on none; a cursor-only scan marked dirty with no event; a lowered capacity; **a restore listener that removes the bell after the first successful work** (one spill of the final balance after the peal settles, the reservation committed, no debit of a detached entity); **a listener that opens an insertion transaction mid-peal** (refused: the handler is unavailable); removal during an open transaction; every pop counted, including unattended, empty, busy and dormant bells; dormant;
- **chunk status:** a pristine watched chunk reads `none`, not `unknown`; `HushChunkStatus` on watch, pruned outside the render square, cleared on level change and logout; an aura created and mutated again in the same tick sends at most two updates;
- the restore command's continuation index finishing a radius-16 ball over repeated calls;
- the place query (true on and in hushed ground, false with the chunk unloaded); the player-caused world (0 flux after 6,000 ticks); persistence of the aura, pending, the journal and the bell through save and reload; every command; every test id in the discovery guard.

**E2E:**
- an emptied crucible's Hush growing (screenshots at 0, 30 and 60 s, and a dump of the aura and the converted positions);
- the HUD against the dump (unknown, none, quiet, hushing, dormant) beside the vis frame at 320 × 240 with a long test translation (the [localisation fixtures](accessibility.md#fixtures)): more than four lines wrapping, every number whole, the outer pose scale ≥ 0.75;
- a ring restoring blocks (screenshots, dump); Hollows rendered, fought and returning home;
- muffling: entering and leaving the Hush changes the category gains in the sound-state dump, an already-playing sound follows, the option off restores 1.0, and an engine reset reapplies it;
- sync on join, reconnect and `/reload` (aura status, `FluxRules`);
- the asset, lang, model and sound validators, including untinted hushed blocks.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)'s contracts on this ticket):

| Wands contract | Met by |
|---|---|
| `spill(inputs, ctx)`: pure, used inside the quote, a function of the resolved inputs and `effective`, never of a quote; 0 until #26 lands | "The pinned functions": `visSpill` over `effective`, the quote's own `neutral` and the synced rates, all resolved inputs (the wands spec amended); the typed zero while `FluxRules` is absent |
| Flux applied on `VisSpentEvent` equal to that event's `spill`; GameTest: previewed spill = applied flux | "Sources": the event's own `quote.spill` is the generated flux, one `HIGHEST` listener; "Generated, retained and overflow": the equality is of generated flux (amended in place), retention reported; "Tests" |
| No flux from tapping, relays, regeneration, cultivation and capture | "Sources", not sources |
| Hush node types are `vftv:node_type` entries with `natural: false`; nodes changed only through `update` and `remove`, relay members only by ordinary block removal; never rewrites an id; no autonomous natural Hush source | "Hushed nodes": `vftv:hushed`, `update` only with a journal written first, never `remove`; relay members are never targets; "The player-caused guarantee" |

**Met here** (the [Arcane crafting spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)): `spill` applied once on `VisSpentEvent`, no second arcane publisher ("Sources"); a quote's spill ≤ 1,000,000 (≤ 80,000 by construction, clipped at `MAX_SPILL_FLUX`).

**Met here** (the [Crucible alchemy spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)'s contracts):

| Crucible contract | Met by |
|---|---|
| A pure `essentiaFlux(spilled, cause)`: integer 0–1,000,000, a function of the map, the cause and the synced rule record; the typed zero until it lands; generated **once** on `CrucibleEvent(SPILLED)`; no second crucible producer; GameTest: the HUD's previewed spill equals the generated flux | "The pinned functions" (one rate, so `EMPTIED`'s preview is also `REMOVED`'s); "Sources"; "Generated, retained and overflow" (the wording amended in place); "Tests" |
| No flux from dissolving, crafting, refusing or extraction | "Sources" |
| A spill's input is a valid format-1 pool ≤ 10,000; dormant records are never inputs | "The pinned functions": any total ≤ 640,000 is accepted, and the crucible supplies only readable pools |
| `SpillCause` gains `DESTROYED`, `UPSET`, `ABANDONED` and `DIED` through their own events; the crucible's bounds unchanged | "Sources" handles all six causes; this spec adds none |

**Met here** (the [Essentia logistics spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)): generated once on `EssentiaEvent(SPILLED)` for `DESTROYED` and `REMOVED`; the input up to 640,000 in `long`, the output 0–1,000,000, the typed zero until it lands ("The pinned functions"); no other essentia flux ("Sources"); GameTest: a destroyed stack generates its flux once, with retention reported ("Tests").

**Met here** (the [Infusion spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)'s contracts):

| Infusion contract | Met by |
|---|---|
| `essentiaFlux(spilled, cause, rules)` for `UPSET`, `ABANDONED` and `REMOVED`, once on `InfusionEvent(SPILLED)` with the rules the event carries | "Sources": `UPSET` with the run's frozen record (0 for a pre-#26 run), `ABANDONED` and `REMOVED` with the current record the event carries |
| A pure `essentiaFluxBound(units, cause, rules)`, 0–1,000,000, ≥ `essentiaFlux(m)` for every valid map up to `units` (≤ 1,000) | "The pinned functions": exact and monotone; the upset bound vector 300 (`upset.spill` 50); the exhaustive unit test |
| A versioned, synced, codec-backed rules record with a generation, so a run can freeze it; the two-argument form is the current-rules case | "Sync and state": `FluxRules` with its `Codec`, `StreamCodec` and generation |
| An infusion spill is ≤ 8 aspects and ≤ 8,000; no flux for drawing, absorbing, stalls, pauses or finishing | "The pinned functions", "Sources" |

**Met here** (the [Foci spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/22)): foci add no direct producer; indirect breaks trigger the sibling's own event ("Sources").

**Met here** (the [Golems spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)): `essentiaFlux(spilled, DIED)` generated once on `GolemEvent(SPILLED)` at the event's level and death position; ≤ 1,000 units of one aspect within the crucible bounds; no other golem flux ("Sources", "The pinned functions").

**Met here** (the [Warp and the Voices spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)): a place query for hushed ground, pure, loaded-only and bounded (two reads of one loaded chunk), registered by `core.hush` with no reverse import; no warp from the Hush ("The place query and the senses").

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)): no recipe type of our own, so the arcane and infusion types carry the gate, preview and JEI; both recipes paged on their gating entries; every number the prose needs is a `hush` fact (the research spec amended).

**Met here** (the [progression spec](progression.md#tc4-systems-kept-redesigned-cut)): caused only by the player ("Sources", "The player-caused guarantee"); bounded (conservation, the radius, the caps); intense locally (the threshold and the walk); counterplay available early, provably buildable spill-free ("Stage", "Authored content, Recipes"); no collateral loss beyond the hushed cells ("The swap").

**Met here** (the [lore spec](lore.md#the-hush-replaces-taint) and the [art style](art-style.md#5-tone-ramp-by-stage)): desaturated and silent, never purple, glossy or fleshy; Azanor's coinages renamed; hushed blocks keep their shapes and lose their colour, untinted; sound subtracted by muffling.

**Met here** (the [quality gates](quality-gates.md#what-each-system-spec-must-declare)): a sustained workload, a share, hot paths with fixed limits and their aggregate, disclosed native terms and an allocation budget ("Performance"); configs proven by GameTests and no chunkloader-only processing ([pain points §3](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md)).

**[Aspects and scanning](https://github.com/andrewbrogoff/voices-from-the-void/issues/15)** and **[Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24)** place no contract on this ticket; the Baffle adds no flux.

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)** (amended in this change)
- The quote computes `neutral` (the same multiplier, rounding and clamp at `w = 1000`) alongside `effective`, and `QuoteInputs` carries `flux.tax_pm` and `flux.void_pm` from synced `FluxRules`; `spill` is `visSpill` over them. Its spill vectors are this spec's table; a `FluxRules` arrival is a quote-input change that invalidates client quotes and caches.
- `vftv:hushed` joins the built-in node types, `natural: false`.
- The `VisSpentEvent` contract's equality means generated flux.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) and [Stage 3 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/36):
- **Entries:** the two under "Stage", with their parents; `vftv:clearing_bell` carries the recipe page for `vftv:clearing_bell`, `vftv:the_hush` the page for `vftv:pealing_bell`.
- **The fact source `hush`:** its grammar, every key (each tuning key, the derived threshold and seconds, the registration facts `bell.durability`, `hollow.health`, `hollow.attack`, `hollow.xp` and `hollow.drops_max`, and the three switches), formats, typed absence and arrival, in the research spec's "Prose and fact references".
- **Affordability:** `path-stage2.json` learns `vftv:clearing_bell` immediately after `vftv:resonant_brass`; #35 is also blocked by #26.
- **Prose** teaches: what spills and what never does, with the previews to read first; that flux pools where it spilled and doesn't fade; the threshold; that the Hush grows only from a seep and only so far, through walls; that ringing restores hushed land exactly and clears flux; Hollows rise from deep Hush and keep near it; hushed nodes ring faint until restored, and a node takes a whole ring, so the pool is cleared first; the Pealing Bell's Sonus; and that the bell reads "up to", never a promise. Every number in a `hush`, `recipe` or `vis` span.

**[Architecture](architecture.md)** (amended in this change)
- `core.flux` is a **leaf feature**: it imports only `api` and the JDK, never another `core` feature (the `archTest` rule `FLUX_LEAF`). `core.vis`, `core.crucible`, `core.infusion` and `core.hush` may import it.
- The `archTest` rule **`FLUX_ENTRY_POINTS`**: only `neoforge.hush` and `core.hush` call `FluxService.deposit`. It checks callers, not delivery or conservation, which the GameTests prove.

**[Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)**, **[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)**, **[Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)**, **[Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)** and **[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)** (amended in this change)
- Their "previewed = applied" wording now reads "previewed = generated flux", with retention reported under "Generated, retained and overflow".

**[Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)**
- No feature, structure, processor or loot places a hushed block, a `vftv:hushed` node, a Hollow or an aura, checked by parsing its generated files **and** its structure templates' NBT palettes and embedded block-entity and entity data. Its Choir-site place query is independent of ours.
- *Met by the [worldgen spec](worldgen.md#templates): a datagen-phase test parses every generated template's NBT (palette, block-entity and entity data, rejecting any `neoforge:attachments`) and every generated worldgen, processor and loot file; its template and loot rejection rules and `vftv:roll_node` never produce a hushed block, node type or item, Hush Ash or a Hollow; its place query reads structure references only.*

**[Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- Its Void work's flux is `VisSpentEvent`'s spill at `flux.void_pm`, nothing else; any other Hush interaction is an extension it must propose.
- *Met by the [Eldritch tier spec](eldritch.md#contracts-on-sibling-specs): no flux of its own and no hushed block, node, Hollow or aura in any template; its one proposed interaction, Silencing's capstone, is amended here: the spread's attendance and Hollow targeting exclude Silenced players (`WarpService.isSilenced`, a pure read, so `core.hush` gains no dependency on the Road), while spills, other players, `hush.unattended` and the Pealing Bell are unchanged.*

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- Hand entries (or reviewed exemptions) for the 13 hushed blocks, `vftv:hush_ash` (a Silentium and Vacuos source), `vftv:clearing_bell`, `vftv:pealing_bell` and the Hollow; round-trip non-amplification over both recipes, outputs and remainders included, and over the source → hushed (Silk Touch) → restored loop, so a hushed block is never worth more than its source.

**Configuration**
- The three server switches and two client options under "Tuning and limits", as named.

**API**
- In `api`:
  - **Stable:** `HushLimits`, `FluxSource`, `FluxClearSource`, the event payload records, and `HushKeys`. `HushKeys` holds the `#vftv:hushed` and `#vftv:hush_immune` tag keys, the `vftv:hushed` node-type key, the `vftv:hollow` entity-type key, the `vftv:aura` attachment's id, and `FORMS`, an unmodifiable list of `HushedForm(source id, form id)` describing the hushed-form table.
  - **`@Experimental`:** `FluxRules`, whose codec and generation stay as above.
- In `api.neoforge`: `HushSpreadEvent` and `FluxEvent`.
- The `vftv:aura` `AttachmentType` is **not** public: its id is published for diagnostics, and direct use is unsupported ([API spec, Lifecycle and ownership](api.md#lifecycle-and-ownership)).
- Concrete forms and every service stay in `core`.

## Production tickets

This spec settles every decision. Three required child tickets deliver the assets and the authored content, and **the system is not done until all three land**. **None of them writes mod code:** no block, entity, AI goal, renderer, HUD layer, payload, attachment, listener, sound-gain code, datagen provider or validator. The asset tickets deliver generators and assets under the [art style](art-style.md); the content ticket delivers authored inputs under `content/`, in the schemas above. The tags, loot tables, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Hushed block assets** | **Generator:** `tools/art/hush_blocks.py`.<br>**Textures** (16×16), each an original drawing in the **`hush` ramp only**, never a recolour of vanilla art: the source's light structure at the same steps, shapes kept, **no teal, never purple, glossy or organic** ([art style §5](art-style.md#5-tone-ramp-by-stage)): `hushed_grass_block_top`, `hushed_grass_block_side`, `hushed_dirt` (also the grass bottom), `hushed_stone`, `hushed_deepslate`, `hushed_deepslate_top`, `hushed_sand`, `hushed_gravel`, `hushed_short_grass` (cross, cutout), `hushed_{oak,birch,spruce}_log` and `_log_top`, `hushed_{oak,birch,spruce}_leaves` (cutout). The **1-px checker fray** only where a side face's drained top band meets its body (the grass side, the bark rim of a log top).<br>**Models:** blockstates for every property state (axis rotations as vanilla's), block models on the vanilla parents under "Rendering and sound", item models and `assets/vftv/items/<id>.json` for the 13; **no tint indices**.<br>**The mote:** 4 frames 8×8, `textures/particle/hush_mote_0…3.png` (hush ramp) and `particles/hush_mote.json`.<br>**Sounds:** `hushed/soft_0…2.ogg`, with the five `block.hushed.*` `sounds.json` entries naming those files and their subtitle keys.<br>**Review:** a contact sheet at GUI scale 2 with each hushed block beside its vanilla source; a 16×16-block Blender diorama of a hushed hillside meeting untouched grass, at noon and at dusk. Provenance rows. | none |
| **Hush items, bells and Hollow assets** | **Generator:** `tools/art/hush.py`.<br>**Items** (16×16): `clearing_bell` (Stage 2 row: an upright brass handbell, the teal tick outside top-right), `hush_ash` (no Stage row: a pale heap in `hush`, no teal), with models and item definitions.<br>**The Pealing Bell:** a model of at most 6 cuboids (a bell in a brass yoke, Stage 3 row, the teal dimmed into a groove), its texture, blockstate (4 facings) and item definition.<br>**The Hollow:** `tools/art/hush/hollow.bbmodel`, an editable model **with exactly the parts, pivots, cuboids, UV origins and mirroring of "Rendering and sound"**; a UV guide `tools/art/hush/hollow_uv.png`; and `assets/vftv/textures/entity/hollow.png` (64 × 32; ash and plaster in `hush`, an `ink0` chest hole, no eyes, no teal).<br>**GUI:** `assets/vftv/textures/gui/sprites/hud/hush_frame.png` (16×16, nine-slice border 4, in its `.mcmeta`; its interior opaque `ink1`, [localisation, Text contrast](accessibility.md#text-contrast)).<br>**Sounds:** the files and `sounds.json` entries of the bell, restore and Hollow rows under "Sound", naming their subtitle keys, no overrides.<br>**Review:** a contact sheet at GUI scale 2 beside a vanilla bell and gunpowder; Blender renders of the Hollow from the front, side and at 16 blocks on hushed grass, plus its walk and rise poses; the Pealing Bell render; **HUD mockups at 320 × 240**: each line state, stacked under a 6-row vis frame, with a long test translation wrapping to 4 lines. Provenance rows. | none |
| **Hush content** | `content/hush/recipes.json` (both bodies, within "Recipes"' rules), `content/hush/node_types.json` (exactly the shipped entry), `content/hush/fixtures.json` (every vector table transcribed, eight sections) and `content/lang/en_us/hush.json` (every key under "Lang keys"). Its review checks the early-construction rules and the HUD wording. | none (the arcane and infusion body formats are frozen) |

[Stage 2 research content #35](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) and [Stage 3 research content #36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) consume the entry ids, recipe ids and `hush` fact keys frozen here. [Vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) gives the new blocks, items and the Hollow their values.
