# Eldritch tier and the Choir's Road

Stage 4: where a Listener's listening finally leads. This covers:
- the **find** that opens Stage 4, and **the Threshold**, the overworld Choir site where the Choir went in;
- **Void-tier crafting**: the **Hollow Bell**, the Void rod and cap, and the **keepsake** that pays for them;
- **the descent**: ringing the Hollow Bell in a Void-touched place, and coming back;
- **Vuulas**, the dimension where the Choir went, and **the Road** inside it: four stations down one well, the **Step Bells**, the catch and the rule against building;
- the Stage 4 Choir pages;
- the **encounter** at each Step: who takes part, whom it hunts, how it is won and what it pays (the Choristers themselves belong to a split-off spec, "The split");
- **the Heart**, where each Listener makes the final choice, **Joining** or **Silencing**, with each one's capstone and its ending scene;
- what happens to the dimension if the mod is removed.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the data and sync rules and the seams;
- the [quality gates](quality-gates.md) for every gate;
- the [lore](lore.md) and [progression](progression.md) specs for the Choir, the Stage rule and the endgame;
- the [research spec](research.md) for entries, finds, Choir pages, affordability paths and fact spans;
- the [wands](wands.md) and [infusion](infusion.md) specs for wand parts and the recipe type this tier uses;
- the [warp spec](warp.md) for the checked commit, Silencing, the dials, the place query and the ending lines;
- the [worldgen spec](worldgen.md) for the Reliquary, the Answerers, the template pipeline and the Choir-site query;
- the [Hush spec](hush.md) for the player-caused guarantee, attendance and the Hollow;
- the [shared conventions](conventions.md) for raw-record retention, operation guards, checked commits, Rules records and the authored-content envelope;
- the [art style](art-style.md) for every asset.

Resolved in [Eldritch tier and the Choir's Road spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/28). Inputs: [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md) (§3 the eldritch dimension and its bosses, §7 the obelisk and the cult), [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md) (Void-tier crafting), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md) (§1 warp, §8 hidden gating, §10 the unfinished endgame), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md) (dimensions, portals, travel).

**Every number in this spec is ours.** None is taken from TC4. There is no maze, no obelisk, eye or capstone ritual, no instance per portal, no random boss modifier, no armour-bypassing pulse, no invulnerable or self-healing phase and no random pearl loot, and nothing here is a TC4 figure rescaled. Each tunable number is listed with its data home under "Tuning and limits". Numbers inside JSON examples are illustrative unless the text says they are the shipped values; the content tickets set the real ones.

**Renamed coinages** ([lore, Vocabulary policy](lore.md#vocabulary-policy)):
- the Outer Lands and the eldritch dimension → **Vuulas**;
- the eldritch obelisk, its eyes and its portal → **the Threshold** and **the Hollow Bell**;
- the Eldritch Guardian and the four bosses → three **Choristers**;
- the Primordial Pearl and void metal → nothing; a **keepsake** pays for one wand tier;
- "Opening the Eye" → `vftv:hollow_bell`.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port that uses `java.util.Random` for vanilla's LCG. Both print the same 31 lines (1,395 bytes, SHA-256 `01e55659e3c28367fb8218f523f300132b9cceac0baf8e29d178c182e117fa48`). The references cover arithmetic only: the Threshold's placement, entry health, admission, threat and targets, the Joining bonus and the arrival station. Their agreement says nothing about generation, teleports, callbacks, persistence or performance, which the build's tests prove (see "Tests"). Nothing else in this spec draws randomness: the Road is identical in every world, encounters follow fixed rules, and the ending scene plays its lines in a fixed order.

**Source citations:** `MC:` is `minecraft-patched-26.2.0.88-sources.jar` and `NF:` the NeoForge 26.2.0.88 sources, with package prefixes omitted.

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 3 Ruins | the Threshold and its page (acquired, not yet tipped in) | |
| 4 The Choir's Road | Void-tier crafting, the descent, Vuulas and the Road, the encounters, the Heart and both endings | `vftv:find/choirs_road`: **holding the Choir page `vftv:choir/where_they_went`** ("The find") |

Seven entries, authored by [Stage 4 research content #37](https://github.com/andrewbrogoff/voices-from-the-void/issues/37):

| Entry | Kind | Parents | Warp | Gates and role |
|---|---|---|---|---|
| **`vftv:choirs_road`** | `find`, `opens_stage` 4; trigger `vftv:advancement` `vftv:find/choirs_road`; `hinted_by` the last Stage 3 page in #36's narrative order | `vftv:ruins`, `vftv:silence_beneath` | | the Stage 4 opener |
| **`vftv:hollow_bell`** | `note` | `vftv:choirs_road` | temporary 2, lasting 2 | the infusion recipe `vftv:hollow_bell`, **and the descent** ("The descent") |
| **`vftv:the_road`** | `free` | `vftv:hollow_bell` | | documents Vuulas, the Steps, the encounters, the catch and the rule against building |
| **`vftv:void_wands`** | `note`, **secret**: `reveal` `[{"type": "vftv:warp_at_least", "kind": "lasting_plus_permanent", "value": 4}]`, `hinted_by` `vftv:the_road` | `vftv:hollow_bell`, `vftv:echo_wands` | lasting 2 | the infusion recipes `vftv:void_rod` and `vftv:void_cap` |
| **`vftv:the_heart`** | `free` | `vftv:the_road` | | documents Joining and Silencing, every consequence in a fact span |
| **`vftv:heard`** | `find`, **secret**: trigger and `reveal` `vftv:advancement` `vftv:end/joined`; `hinted_by` `vftv:the_heart` | `vftv:the_heart` | | the Joining capstone's page |
| **`vftv:unheard`** | `find`, **secret**: trigger and `reveal` `vftv:advancement` `vftv:end/silenced`; `hinted_by` `vftv:the_heart` | `vftv:the_heart` | | the Silencing capstone's page |

- **The revelation gates the road.** `vftv:silence_beneath` (lasting 4, [warp, Stage](warp.md#stage)) is a parent of the opener, so nobody takes the Road without having heard the silence beneath. It is already on the Stage 3 path, and lasting warp can be stilled.
- **The key is turned once, by the reveal** ([research, Affordability and recovery](research.md#affordability-and-recovery)): `silence_beneath`'s own commit (L + P = 4) latches `vftv:void_wands`' gate of 4, exactly as it latches [`vftv:void_armor`'s](armor.md#stage). A latched gate stays turned, so a Listener who stills to 0 after the reveal, as the Stage 3 prose teaches, still finds Void wands revealed when Stage 4 opens; a gate above 4 would have hidden them behind permanent warp or luck. `vftv:hollow_bell`'s lasting 2 stays the dose it is. No gate needs permanent warp, a Bargain or the Listen rite ([warp, The Listen rite](warp.md#the-listen-rite)). Every entry here with lasting warp descends from `vftv:being_heard`.
- **"A different capstone opens"** ([progression, Endgame](progression.md#endgame)) literally: each ending latches its capstone's page in the book.
- `vftv:void_foci` ([foci](foci.md#stage)) and `vftv:void_armor` ([armor](armor.md#stage)) hang off the opener too; `vftv:learning_the_tongue` keeps the [warp spec's](warp.md#stage) parents and permanent 3.
- **Options considered:** a warp threshold as the opener. **Why not:** a find is what opens a Stage ([progression, Stages](progression.md#stages)), and hidden warp gating is one of the complaints (pain points §8).

## The split

Combat is specified in its own spec, **[Choristers of the Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/69) (`docs/spec/choristers.md`)**, ticket **#69**, a required v1 ticket blocked by this spec. This spec is one document of reasonable size only because the creatures' moves, timings, damage, animation and sound are there.

| This spec freezes | The Choristers spec specifies |
|---|---|
| the Road's geometry, each Step's **rise cell** and arena ("The Road") | the entity type `vftv:chorister`: size, attributes, AI goals and movement inside the arena |
| **admission**: who takes part, in what order, up to what cap ("Encounters") | **the moves**: each one's windup, recovery, cadence, reach, area, damage and its damage type |
| **threat and target selection**, the query the creature asks | the instances `vftv:wuuren` (Step 1), `vftv:suhlaa` (Step 2) and `vftv:nihthaa` (Step 3) of the synced registry `vftv:chorister`, and its schema |
| **entry health**, from an instance's base and the admitted count, capped at `MAX_ENTRY_HEALTH` (1024) | the creature's base health per instance, through the registry field `health` (1–1000) |
| **the rise**, **defeat and credit**, **reset**, keepsakes, the boss bar | rendering, poses, particles, sound, and its own assets and content |
| **the completion interface** below | fixtures for every move; its own performance share |

**The completion interface** (plain `core` types; nothing here crosses into `neoforge`):
- `ChoristerFactory.create(level, instance, risePos, maxHealth)` → a configured, not yet added Chorister, or none. This spec adds it and checks the insertion.
- `Encounter.targetFor(chorister)` → the current target ("Threat and target"), or none. The creature never chooses its own target.
- `Encounter.isParticipant(player)` and `Encounter.arena()`. The creature never acts on anyone else, and never leaves the arena.
- **`Encounter.isEligibleParticipant(player)`**: the player is a participant **and** eligible now ("Admission"), read at the call. It is **the one predicate** for `targetFor`'s candidates, the credited at a defeat, every Chorister hit, and incoming damage a player causes ([Choristers, Who can hurt it](choristers.md#who-can-hurt-it)).
- **`int Encounter.eligibleParticipants(int[] out)`**: fills `out` (capacity at least `MAX_PARTICIPANTS`) with the entity ids of the players for whom `isEligibleParticipant` holds, ascending, and returns the count (≤ 16). A pure read with zero allocation, so the creature never scans the level's players.
- **`Encounter.onConfirmedDeath(chorister)`**, the creature's only report: its `die(source)` override calls `super.die(source)` and then, **only if** the protected `dead` flag went from false to true across that call, calls this once. `LivingDeathEvent` can't be the signal: it is cancellable (`NF:event/entity/living/LivingDeathEvent.java:27–32`) and is posted **before** vanilla accepts the death (`MC:world/entity/LivingEntity.java:1526–1529`), so a later subscriber could cancel or heal after credit was paid. Health at or below 0 alone isn't enough either. This spec reads the creature's damage through `LivingDamageEvent.Post`.

**Contracts on the Choristers spec** (each is a criterion in that ticket):
- The type is allowed in peaceful, built with `noSave()`, persistence-required while it lives, with knockback resistance 1.0; it changes no blocks, summons nothing, never heals and draws no randomness. It **has no invulnerable phase**: it takes damage at every tick from every source, except damage caused by a player (directly or through a projectile they own) who isn't in creative mode and fails `isEligibleParticipant` at that moment, so nobody outside the arena hurts it without risk.
- Every move's damage type uses a **difficulty-scaled** `scaling` (so it is zero in peaceful, `NF:common/damagesource/IScalingFunction.java:24–38`), is outside `#minecraft:bypasses_armor` and the [armor spec's](armor.md#the-baffle) `#vftv:baffle_ignores`, and is telegraphed.
- It targets only `targetFor`'s answer, every tick, and returns to its rise cell when below the floor − 8 or outside the arena.
- **Its bounding box fits the rise box** ("The layout": 3 × 5 × 3 cells) at every instance's scale, and its maximum-health attribute accepts the entry health it is given (≤ 1024).
- It calls `onConfirmedDeath` exactly as above, and nothing else reports its death.
- Its server share is **0.04 ms mean, 0.13 ms p99**, client **0.04 ms p50**, with its own workload, hot paths and allocation budgets.

## The find

**`vftv:find/choirs_road`**, fixed by code in datagen like the [worldgen spec's finds](worldgen.md#the-advancements): no `display`, no parent, no rewards.

```json
{"criteria": {"page": {"trigger": "minecraft:inventory_changed",
  "conditions": {"items": [{"items": "vftv:choir_page",
                            "components": {"vftv:page": "vftv:choir/where_they_went"}}]}}}}
```

- **One page, exactly:** an item predicate's `components` is an exact component match (`MC:advancements/predicates/ItemPredicate.java:16–31`; `DataComponentMatchers.java:15–21,33–38`), so only that page latches it. With one item predicate vanilla tests only the changed stack, as for the [Stage 3 find](worldgen.md#the-advancements).
- **Obtaining counts:** the page comes only from a Threshold's Reliquary ("The Threshold"), or from another player's hand. Holding it latches the opener in Stage 3; tipping it in needs Stage 4 ([research, Choir pages](research.md#choir-pages)).
- **The two ending advancements** `vftv:end/joined` and `vftv:end/silenced` have one criterion each, `{"trigger": "minecraft:impossible"}`, no display and no parent. Only code awards them ("The Heart").
- **Options considered:** a location trigger on entering the Threshold. **Why not:** it is the non-loaded structure lookup the [worldgen spec](worldgen.md#stage-3-a-choir-page) rejected.

## The Threshold

### The structure

**A single-piece vanilla `minecraft:jigsaw` structure, `vftv:threshold`:** a ring of broken bell frames standing over a shaft into silence, with a stair down its wall to a sunken chancel. "They hung the bells over nothing and went down."

| Field | Value | Home |
|---|---|---|
| `start_pool` | `vftv:threshold/start`, one element `vftv:threshold/threshold`, fallback `minecraft:empty` | fixed |
| `size` | 0 (the start piece only) | fixed |
| `max_distance_from_center` | 24 | fixed |
| `start_height` | absolute −24, `project_start_to_heightmap` `WORLD_SURFACE_WG`, so the template sits 24 blocks into the ground (vanilla's own `data/minecraft/worldgen/structure/trail_ruins.json` sinks its start the same way) | fixed |
| `step`, `terrain_adaptation`, `use_expansion_hack`, `spawn_overrides` | `surface_structures`, `beard_thin`, false, `{}` | fixed |
| `biomes` | `#vftv:has_structure/threshold` | content (`threshold.json`) |

**The structure set `vftv:threshold`:** `minecraft:random_spread`, `spacing` **64**, `separation` **24**, `spread_type` `linear`, `salt` **1414025811** (`0x54485253`, "THRS"), `frequency` 1.0, and `exclusion_zone` `{"other_set": "vftv:choir_ruin", "chunk_count": 8}`, so a Threshold never starts within 8 chunks of a ruin's start and never meets a ruin's ≤ 105-block box ([worldgen, Pieces and topology](worldgen.md#pieces-and-topology)).

- The algorithm is the [worldgen spec's](worldgen.md#placement): region `(floorDiv(cx, 64), floorDiv(cz, 64))`, two `nextInt(40)` draws. **Separation by construction:** starts of distinct regions are at least 25 chunks apart.
- **Rarity:** one start attempt per 1,024 × 1,024 blocks, land only. A hypothesis, measured with the worldgen pregeneration scenario. `/locate structure vftv:threshold` works natively.
- The salt is fixed; spacing, separation, the exclusion's `chunk_count` and the biomes are content.

**Vectors** (world seed `0x0123456789ABCDEF`; region → start chunk): (0, 0) → (31, 26); (−1, 0) → (−38, 6); (0, −1) → (3, −28); (3, −7) → (214, −435); (−12345, 6789) → (−790070, 434520); (29000, −29000) → (1856003, −1855985). Over the 6,400 regions in [−40, 39]², the minimum Chebyshev distance between neighbours' starts is 25. A unit test runs vanilla's `getPotentialStructureChunk` against these rows.

### The template

`threshold` is exactly **33 × 44 × 33**. Template y 24 is the surface the start was projected to; y 0 is the chancel's foundation, 24 blocks down.

| Role | Rule |
|---|---|
| the ring | bell frames on the surface, at least one reaching template y ≥ 40 (16 blocks above the surface): the landmark seen over trees |
| the shaft | a 9 × 9 opening over the cells x, z 12–20, from y 24 down to the chancel; its open cells are **air, never structure void**, so the template carves them |
| the stair | a stair down the shaft's wall from the four **entry cells** (standing cells at y 25 at the middle of each side of the ring, (16, 25, 2), (30, 25, 16), (16, 25, 30), (2, 25, 16)) to the chancel floor, with two air cells of headroom on every step |
| the chancel | the floor layer at y 0, its standing cells at y 1 |
| the Reliquary | exactly one, at (16, 1, 22) facing north, site `vftv:threshold`; the cell in front, (16, 1, 21), is a standing cell |
| defenders | exactly 2 `vftv:answerer` and 1 `vftv:answerer_cantor`, on chancel standing cells |
| nothing else | no node, no chest, no other `vftv:` block, no hushed block |

- **Paths** (the [worldgen spec's](worldgen.md#pieces-and-topology) "on foot", 4-connected standing cells rising or falling at most 1 block a step): every chancel standing cell, the Reliquary's front cell and every defender's cell is reachable from **every** entry cell, and every entry cell from the chancel. So the way down is also the way out.
- The defenders are the worldgen spec's Answerer and Cantor, with every rule of theirs unchanged: template entities, persistent, homes set at finalization, and kept in peaceful, where they take no target and deal no damage ([worldgen, Placement and persistence](worldgen.md#placement-and-persistence)).
- **Look:** the Stage 3 masonry register (tuff, tuff bricks, chiseled tuff, calcite, deepslate tiles, waxed copper) with the bell frames broken and empty, and soul lanterns down the stair: the teal sits inside the dark ([art style §5](art-style.md#5-tone-ramp-by-stage), Stage 4 row).

### A Choir site

- **`#vftv:choir_sites` gains `vftv:threshold`**, as the worldgen spec invites, so its `ChoirSitePlaceQuery` makes a Threshold Void-touched with no new query: the warp spec's context-only danger can happen there, and the Hollow Bell can be rung there.
- **Existing worlds:** Thresholds generate only in chunks generated after the mod is added. Explored land has none, so players travel; `/locate` finds the nearest. A ruin is closer for the descent ("The descent").
- **Options considered:**

  | Option | Why not |
  |---|---|
  | putting the page in ruins | their site kind is Stage 3's alone, by the worldgen spec's validation |
  | an obelisk opened by a ritual | TC4's design, whose portals were the hard-to-find part (TE §7) |
  | a Threshold in every ruin | the find would come with Stage 3's first page, and the pilgrimage would vanish |

## Void-tier crafting

### The items

| Item | Display name | Stack | Made by | Role |
|---|---|---|---|---|
| **`vftv:hollow_bell`** | Thahvis | 1, no durability | the infusion recipe `vftv:hollow_bell` | the descent instrument ("The descent") |
| **`vftv:void_rod`** | Lirhas | 64 | the infusion recipe `vftv:void_rod` | the wand rod `vftv:void` |
| **`vftv:void_cap`** | Ohshaal | 64 | the infusion recipe `vftv:void_cap` | the wand cap `vftv:void` |
| **`vftv:keepsake`** | Yoorel | 64 | nothing: a defeated Chorister's credit ("Defeat and credit") | one per Void rod, one per Void cap |

**The recipes** are three `vftv:infusion` bodies ([infusion, The recipe type](infusion.md#the-recipe-type)), each `kind: void` behind a Stage 4 gate, in `content/eldritch/recipes.json`:

| Recipe | `research` | `centre` | `components` | `essentia` must include | `instability` | `result` |
|---|---|---|---|---|---|---|
| `vftv:hollow_bell` | `vftv:hollow_bell` | `minecraft:bell` | exactly 2 `vftv:resonant_brass_ingot` and 2 `minecraft:amethyst_shard` | Vacuos, Silentium, Sonus | 10–40 | `vftv:hollow_bell` |
| `vftv:void_rod` | `vftv:void_wands` | `vftv:echo_rod` | exactly 1 `vftv:keepsake`, plus 1–4 others | Vacuos | 20–50 | `vftv:void_rod` |
| `vftv:void_cap` | `vftv:void_wands` | `vftv:echo_cap` | exactly 1 `vftv:keepsake`, plus 1–4 others | Vacuos | 20–50 | `vftv:void_cap` |

- **The first craft becomes the way down.** `minecraft:bell` is the result of the [arcane spec's](arcane.md#stage) tutorial recipe `vftv:bell`, the Listener's first arcane craft. Villages and the ruins' towers hold bells too.
- **A full Void wand takes three keepsakes**, one rod and two caps: one per Step.
- **No warp on craft** ([warp, Sources](warp.md#sources)): Void-tier work reaches warp through `vftv:hollow_bell` and `vftv:void_wands`' commits. **No flux** beyond what the [Hush spec](hush.md#sources) already counts: an infusion's own spills, and `VisSpentEvent`'s spill for any Void vis.
- **No arcane or crucible recipe, no recipe type of our own, no stability rule of our own, no runic augmentation.** The infusion type carries the gate, the preview and JEI.

### The Void wand tier

Two **data entries, no code** ([wands, Parts](wands.md#parts)), in `content/eldritch/wand_parts.json`, merged with the other parts files and rejecting a duplicate id or item:

| Entry | Item | Value |
|---|---|---|
| rod `vftv:void` | `vftv:void_rod` | capacity **500** (the Echo rod holds 300) |
| cap `vftv:void` | `vftv:void_cap` | ×**0.55** (the Echo cap is ×0.70) |

Their layers are cases **appended** to `assets/vftv/items/wand.json` after the [Foci assets ticket #49](https://github.com/andrewbrogoff/voices-from-the-void/issues/49), keeping every existing case, fallback and the foci member ([wands, Components](wands.md#components)).

### Gear warp

**None of these items carries `vftv:gear_warp`.** The Hollow Bell is how a Listener comes home, so carrying it must not tax them; the wand parts become components of the one `vftv:wand` item, which a per-item data map can't tell apart ([warp, Gear warp](warp.md#gear-warp)). The [Void armour's](armor.md#void-armour) 9 is unchanged. **No Void weapons or tools in v1:** foci are the Void tier's offence ([foci, The Void tier](foci.md#the-void-tier)).

- **Options considered:** a void-metal ingot; boss-only keys for the bell. **Why not:** a second economy for one tier, and a circle (the bell is how you reach the bosses).

## The descent

### Ringing the Hollow Bell

**Listening is a rite with an instrument** ([lore, Listening and warp](lore.md#listening-and-warp)). The Hollow Bell is used by holding use for `bell.toll` ticks (**60**), with `ItemUseAnimation.TOOT_HORN` (`MC:world/item/ItemUseAnimation.java:25`), tolling every 20 ticks. Releasing early does nothing. Rows 2 and 3 are also checked when use **starts**; the server decides everything when it **finishes**, under the player's **road guard**, an [operation guard](conventions.md#operation-guards) held across every row and callback. First match:

| # | Case | Message (`road.vftv.*`) | Effect |
|---|---|---|---|
| 0 | the road guard is held | `message.vftv.busy` | none |
| 1 | the player already travelled this tick | none | none |
| 2 | `vftv:hollow_bell` not learned | `.bell_mute` | none |
| 3 | riding, a passenger, sleeping, or the item on cooldown | `.not_now` | none |
| 4 | **in Vuulas** | | **return** ("Coming back") |
| 5 | not in a Void-touched place: `WarpService.inVoidTouchedPlace(level, pos)` is false | `.dull` | none |
| 6 | the `vftv:road` record is unreadable | `message.vftv.unreadable` | none |
| 7 | the level `vftv:vuulas` is missing | `.closed` | none |
| 8 | `travel.cooldown` hasn't passed since the player's last Road travel | `.not_now` | none |
| 9 | after loading every chunk the landing's checks read, the landing isn't safe ("Safe standing") | `.closed` | none |
| 10 | otherwise | `.down` | descend (below) |

- **A Void-touched place** is the [warp spec's](warp.md#context) third context clause alone: a registered place query answers true. Today that is a Choir site (every ruin and Threshold) or hushed ground. Recent Void work and the dimension list never count, so a Listener can't descend from ordinary ground by casting first.
- **Descend:** capture the return point (the player's level, position, `yRot` and `xRot`), then `ServerPlayer.teleport(new TeleportTransition(vuulas, landing, Vec3.ZERO, yaw, 0, DO_NOTHING))` (`MC:world/level/portal/TeleportTransition.java:13–24`) to the **arrival station**'s landing:

  | `progress` | 0 | 1 | 2 | 3 |
  |---|---|---|---|---|
  | arrival | Step 1 | Step 2 | Step 3 | the Heart |

  The cross-level transfer runs NeoForge's cancellable `EntityTravelToDimensionEvent` and, after it, `PlayerChangedDimensionEvent` (`MC:server/level/ServerPlayer.java:1129–1150,1172–1188`). **Accepted only if**, after it returns, the result is this player, alive, in Vuulas, and within 0.5 blocks of the landing. Then the return point, `last_station`, the item's cooldown (`bell.cooldown`, through `ItemCooldowns`) and the travel cooldown are written. Otherwise nothing is written and `.closed` shows: a cancelled transfer leaves the player where they were, and a callback that moved them elsewhere leaves them wherever it put them. A throw is logged once and treated the same way.
- **The [warp spec's](warp.md#suppression) grace** begins at every dimension change.

### Coming back

The Hollow Bell in Vuulas (row 4), a sneak-use of Step 1's bell ("The Step Bells"), or `/vftv road evacuate` returns the player:
0. **The cooldown:** an ordinary return obeys `travel.cooldown` like every other Road travel, refusing with `.not_now` and changing nothing. **Two exemptions:** a player whose `vftv:road` record is unreadable (the emergency way out, so nobody is stranded), and `evacuate`. The catch isn't a return and never reads the cooldown. So at the tuning extremes (`bell.toll` 20, `travel.cooldown` 1,200) a Listener who has just descended waits a minute to go back up.
1. **The target** is the record's return point, if the record is readable, holds one, and its level exists; otherwise the **default**, `TeleportTransition.createDefault(player, DO_NOTHING)`, the world-spawn level's spawn.
2. **Load, then check.** Every chunk that the player's bounding box at the target, the supporting cell below its feet, or any cell "Safe standing" reads intersects is loaded first (`level.getChunk`, a native load or generation, disclosed), because a collision read on an unloaded chunk sees nothing (`MC:world/level/Level.java:747–748`) and a box can straddle a chunk boundary. Then the point must pass "Safe standing"; if it doesn't, the default is used.
3. Teleport, accepted as for the descent (the player, alive, at the target). The return point is kept (the next descent overwrites it); `last_station` is cleared; the travel cooldown is set. A refused or redirected return writes nothing.

An unreadable record still returns the player, to the default, writing nothing.

**The Road does not keep you.** A player who logs out in Vuulas is returned before they are saved: our `PlayerEvent.PlayerLoggedOutEvent` listener runs this return for any player in Vuulas who is alive, as `evacuate` does (no cooldown, the target and "Safe standing" as above). NeoForge posts the event from `PlayerList#remove` (`firePlayerLoggedOut`, `MC:server/players/PlayerList.java:313`) before vanilla's `save` of that player (`:316`), so a disconnect's save is written at the return point. **A clean server stop:** `MinecraftServer#stopServer` runs `saveAll()` and then `removeAll()` (`MC:server/MinecraftServer.java:653–654`); the first save may still hold a player in Vuulas, but `removeAll()` disconnects each player through the same `remove()`, which fires our return and then saves again, so the **final** write of every player file is outside Vuulas. `last_station` is transient anyway, and the next descent lands at the arrival station, so the only cost is a toll. A dead player is skipped: the respawn takes them out. **Disclosed:** an autosave still writes a player who is in Vuulas at that moment, so a crash (or a killed process) before their next logout leaves that file in Vuulas, and a missing dimension after an uninstall then loads them at the same coordinates in the spawn level ("Existing worlds and removing the mod"). With VFTV present they load on the Road, where the catch stands under them, and `/vftv road evacuate` remains the remedy.

### Safe standing

A point is safe iff every chunk it reads is loaded (step 2), and inside the world border and the build height:
- the player's bounding box at that point collides with nothing (`noCollision`);
- neither the feet nor the head cell holds a fluid, and neither holds a block in `#vftv:unsafe_return` (fire, soul fire, magma, both campfires, cactus, sweet berry bushes, wither roses, powder snow, pointed dripstone);
- **the supporting block**, below the feet cell, has a sturdy top face **and isn't in `#vftv:unsafe_return`** either: a magma floor under clear feet and head would hurt whoever stands on it (`MC:world/level/block/MagmaBlock.java:23–26`).

The descent's landing (row 9) is checked the same way.

### Options considered

| Option | Why not |
|---|---|
| descending from anywhere | no sense of place, and no common meeting point for a group |
| descending only at a Threshold | a chore: ruins are about every 576 blocks and are already Choir sites |
| a portal block | TC4's portals were the hard part (pain points), and a portal ticks every entity standing in it |

## Vuulas

### The dimension

**The level stem `vftv:vuulas`** (`data/vftv/dimension/vuulas.json`), written by datagen from code: `{"type": "vftv:vuulas", "generator": {"type": "vftv:road", "biome": "vftv:vuulas"}}`.

**The dimension type `vftv:vuulas`** (`data/vftv/dimension_type/vuulas.json`); its colours and light come from `content/eldritch/vuulas.json`:

```json
{"has_fixed_time": true, "has_skylight": false, "has_ceiling": false, "has_ender_dragon_fight": false,
 "coordinate_scale": 1.0, "min_y": 0, "height": 320, "logical_height": 320,
 "infiniburn": "#minecraft:infiniburn_overworld", "ambient_light": 0.0,
 "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0, "skybox": "none",
 "attributes": {
   "minecraft:visual/fog_color": "#0B0E1C",
   "minecraft:visual/sky_color": "#0B0E1C",
   "minecraft:gameplay/bed_rule": {"can_sleep": "never", "can_set_spawn": "never", "explodes": false},
   "minecraft:gameplay/respawn_anchor_works": false,
   "minecraft:audio/background_music": {}}}
```

- **Fields** as in 26.2's `DimensionType` record (`MC:world/level/dimension/DimensionType.java`); `height` and `min_y` are multiples of 16. Fog, sky, beds, anchors and music are **environment attributes** (`MC:world/attribute/EnvironmentAttributes.java:103–130`; `BedRule.java:17–24,39–44`).
- **Music:** `{}` plays nothing: every member of the value is optional (`MC:world/attribute/BackgroundMusic.java:11–20`), and the attribute map accepts that direct value (`AttributeTypes.java:43`; `EnvironmentAttributeMap.java:109–123`).
- **Beds** neither sleep, set a spawn nor explode. **Respawn anchors** don't work, and a charged one would explode if used (`MC:RespawnAnchorBlock.java:93–101`); none can be placed ("No building").
- **The biome `vftv:vuulas`** (`data/vftv/worldgen/biome/vuulas.json`), every required key and no other (`MC:world/level/biome/Biome.java:36–44,362–372`; `BiomeSpecialEffects.java:20–30`; `BiomeGenerationSettings.java:29–34`; `MobSpawnSettings.java:28–42`). It is in no `has_structure` tag.

  ```json
  {"has_precipitation": false, "temperature": 0.5, "downfall": 0.0,
   "effects": {"water_color": "#0B0E1C"},
   "carvers": [], "features": [], "spawners": {}, "spawn_costs": {}}
  ```

  These shapes are verified from source; decoding both files through the real registry ops is a build acceptance test.

### The generator

**`vftv:road`,** a `ChunkGenerator` whose `MapCodec` is registered in `Registries.CHUNK_GENERATOR` (`MC:core/registries/Registries.java:158`; `world/level/chunk/ChunkGenerator.java:83–85`). Codec: `{"biome": <biome id>}`, a fixed biome source.

| Method | Behaviour |
|---|---|
| `fillFromNoise` | the chunk unchanged (all air), completed at once |
| `buildSurface`, `applyCarvers`, `spawnOriginalMobs` | nothing |
| `applyBiomeDecoration` (`ChunkGenerator.java:322`) | **pastes the Road** (below); vanilla decoration is never called |
| `getMinY`, `getGenDepth`, `getSeaLevel` | 0, 320, 0 |
| `getBaseHeight`, `getBaseColumn` | the minimum height; a column of air |

- **Pasting:** for the chunk being decorated, each of the four station templates whose fixed box crosses that chunk is placed with `StructureTemplate.placeInWorld` (`templatesystem/StructureTemplate.java:252–264`) at its fixed origin, rotation and mirror none, entities ignored, its bounding box clipped to that chunk's columns at every height, update flags 2. A chunk outside the Road's 47 × 47 column holds nothing.
- **No seed is read.** Every world's Road is identical, so determinism needs no vector.
- **The template cache:** the four templates are read from the server's `StructureTemplateManager` once, on the first decoration, under a lock, into an immutable table every worker thread reads. A missing or unreadable template logs one error and its station is pasted as nothing. **Templates change only when the world is reopened:** a `/reload` never rebuilds the cache, so no reload races generation.
- **Why a generator of our own:** `minecraft:flat` with only air layers runs biome decoration only when the biome is `minecraft:the_void`, and skips the structure steps (`MC:levelgen/flat/FlatLevelGeneratorSettings.java:147–158,192–201`), so a void level with our own biome can't carry the Road.
- **Options considered:**

  | Option | Why not |
  |---|---|
  | `minecraft:flat` | the decoration rule above |
  | a noise generator with floating islands | unbounded terrain to get lost in (pain points), and generation cost |
  | structures under vanilla placement | seeded offsets and rotations, so landing coordinates would differ per world |
  | a maze per portal | TC4's design, and its complaints: getting lost, failed generation in multiplayer |

## The Road

### The layout

**Fixed in code** (the constants in `RoadLayout` in `api`; the functions over them in `core.road`'s `RoadGeometry`) and checked against the templates at datagen. Four stations stacked on one axis, the centre of block (0, 0), each a **ring** of masonry around an open **well**. From any ring you look down the well at the lights of the rings below and, at the bottom, the Heart. "*Thahvis*: the road, the way down."

| Station | Floor y | Template |
|---|---|---|
| Step 1 | 256 | `vftv:road/step_1` |
| Step 2 | 192 | `vftv:road/step_2` |
| Step 3 | 128 | `vftv:road/step_3` |
| the Heart | 64 | `vftv:road/heart` |

Each template is exactly **47 × 40 × 47** with origin (−23, floor − 8, −23). Positions below are relative to the axis and the floor: a cell (x, y, z) is the block at (x, floor + y, z), and `d² = x² + z²`.

| Role | Rule |
|---|---|
| **well** | every cell with d² ≤ 36, at every height: air |
| **ring** | the floor layer (y 0) of every cell with 37 ≤ d² ≤ 441 is a full block with a sturdy top face; the ring's standing cells are at y 1 |
| **landing** | the standing cell (0, 1, −17); the player arrives at (0.5, floor + 1, −16.5) facing the well (`yRot` 0) |
| **Step Bell** `vftv:step_bell` | (0, 1, 17) at Steps 1–3; (17, 1, 0) at the Heart |
| **rise cell** (Steps 1–3) | the standing cell (0, 1, 12), rise point (0.5, floor + 1, 12.5); the **rise box**, x −1 to 1, y 1 to 5, z 11 to 13, is all air |
| **choir walk** (Steps 1–3) | every cell with 81 ≤ d² ≤ 225 at y 1 to 5 is air: the Chorister's path, the circle of radius 12 through the rise point ([Choristers, The choir walk](choristers.md#the-choir-walk)). It holds the rise box and meets no other role |
| **Reliquary** (Steps 1–3) | (−17, 1, 0), facing east, site `vftv:road`; its front cell (−16, 1, 0) is a standing cell |
| **the Heart** `vftv:heart` | the Heart station only, at (0, 1, 17) |
| **arena** (Steps 1–3) | a player is in it iff `(px − 0.5)² + (pz − 0.5)² ≤ 22²` and `floor − 2 ≤ py < floor + 12` |
| **paths** | every ring standing cell, the Step Bell's and Reliquary's front cells and the rise cell are reachable on foot from the landing |

- **Stations are 64 apart**, so the gap between one template's bottom (floor − 8) and the next one's top (floor − 33) is 25 blocks of void. The catch ("The catch") always fires before anyone reaches a lower ring.
- **The Road's column** is the 16 chunks crossing x, z −23 to 23; nothing else in Vuulas holds a block.
- **Look:** the Stage 4 register ([art style §5](art-style.md#5-tone-ramp-by-stage)): masonry draining to grey, soul lanterns (teal in the dark), empty bell frames. Masonry only from blocks outside the [Hush spec's](hush.md#the-forms) sources, so no Hush can form on the Road.

### Road progress

**Per player,** in the `vftv:road` attachment ("Sync and state"): `progress`, 0 to 3, the number of Steps passed. Only a defeat's credit ("Defeat and credit") and the command raise it; nothing lowers it except the command.

### The Step Bells

**`vftv:step_bell`** (Shohrel), one per station, with no block entity: the station is read from the bell's position against `RoadLayout`. Survival-unbreakable, explosion-proof, `PushReaction.BLOCK`, tagged `minecraft:wither_immune`, `minecraft:dragon_immune` and `minecraft:features_cannot_replace`, as the [Reliquary](worldgen.md#the-block). Used with any hand, ordinary or empty. Vanilla's `useItemOn` checks and `RightClickBlock` come first, then, under the road guard, the first match (Step *k* is 1–3). **Sneak-use** is a use while crouching; **sprint-use** a use while sprinting and not crouching:

| # | Gesture | Case | Message (`road.vftv.*`) | Effect |
|---|---|---|---|---|
| 0 | any | the road guard is held | `message.vftv.busy` | none |
| 1 | any | the player already travelled or rang this tick | none | none |
| 2 | any | the `vftv:road` record is unreadable, and the gesture isn't a sneak-use of Step 1's bell | `message.vftv.unreadable` | none |
| 3 | sneak-use | Step 1 | | **return** ("Coming back") |
| 4 | sneak-use | Step *k* > 1, or the Heart | `.up` | **up** to the previous station's landing (the Heart's goes to Step 3) |
| 5 | use or sprint-use | the Heart's bell | `.up` | up to Step 3's landing |
| 6 | sprint-use | `progress` ≥ *k*, an encounter is running here | `.step_listening` | none |
| 7 | sprint-use | `progress` ≥ *k* | | **a rematch:** start the encounter ("The rise"), which changes no one's progress |
| 8 | use | `progress` ≥ *k* | `.down` | **down** to the next station's landing (Step 3's goes to the Heart) |
| 9 | use or sprint-use | `progress` < *k* − 1 (reached only by a command or a pearl) | `.step_not_mine` | none |
| 10 | use or sprint-use | an encounter is running at this Step | `.step_listening` | none: walking into the ring joins it |
| 11 | use or sprint-use | otherwise (`progress` = *k* − 1) | | **start the encounter** ("The rise") |

- **Up and down** are same-level teleports to the target landing, refused while `travel.cooldown` hasn't passed (`.not_now`), accepted as for the descent (the player, alive, at the landing), and they set `last_station` and the travel cooldown.
- **Nobody needs the bell item to leave:** sneak-using Step 1's bell returns them, and every station's bell goes up. A player further down **walks back up** to help a friend.
- **A rematch** lets a Listener who has passed a Step, alone, earn a replacement keepsake ("Defeat and credit"); crouching keeps its priority, so travel up is never mistaken for one. `vftv:the_road`'s prose teaches it.
- **Any hand, really:** vanilla skips a block's use while the player crouches with either hand holding something (`MC:server/level/ServerPlayerGameMode.java:399–403`), which would swallow the sneak gestures. So at a genuine Road Step Bell (a `vftv:step_bell` in Vuulas at a `RoadLayout` bell position), our `RightClickBlock` listener **promotes the block use to allowed** for the sneak gestures, **unless** an earlier listener already denied it or cancelled the event, or vanilla's permission checks (`mayInteract`, spawn protection) refuse. The item use stays denied ("No building").

### The catch

Each server tick, for each player in Vuulas who isn't in creative or spectator mode, in this order (`PlayerTickEvent.Post`):
1. **`last_station`:** if the player is on the ground, `⌊py⌋ − 1` is a station's floor y, and they are within the arena's horizontal radius of the axis, it becomes that station.
2. **The catch:** if `py` is below `last_station`'s floor y minus `catch.depth` (**16**), the player is teleported in the same level to `last_station`'s landing, with fall distance and velocity cleared. "The silence gives me back." (`road.vftv.caught`).

- `last_station` is transient. It is set by every arrival; at login, respawn and dimension change into Vuulas it is the station whose landing's y is nearest the player's (ties to the higher). The catch never reads the `vftv:road` record, so it works when the record is unreadable, and it sets no cooldown: it is a rescue, and the landing is solid.
- **Gliding or pearling down gains nothing:** a pearl landing on a lower ring moves `last_station`, but progress is checked at every bell, the arena and the Heart. Nobody dies to the void (TC4's complaint: falling into it on arrival).

### No building

In Vuulas, for every player not in creative mode:
- `BreakBlockEvent` (`NF:event/level/block/BreakBlockEvent`) is cancelled;
- `BlockEvent.EntityPlaceEvent` is cancelled, which also covers `EntityMultiPlaceEvent` (`NF:event/level/BlockEvent.java:95–120`);
- `PlayerInteractEvent.RightClickBlock` denies the **item** use for every item not in `#vftv:usable_on_the_road` (the wand, the Tuning Fork, the Hollow Bell, the Commonplace Book), and leaves the **block** use alone (promoting it at Step Bells, "The Step Bells"), so the Step Bells, the Reliquaries and the Heart work with any hand;
- `PlayerInteractEvent.RightClickItem` is cancelled for items in `#vftv:forbidden_on_the_road` (every filled bucket, flint and steel, fire charge, bone meal), because a bucket acts in `use` (`MC:world/item/BucketItem.java:43–83`; `NF:event/entity/player/PlayerInteractEvent.java:115–173,201–223`);
- `ExplosionEvent.Detonate` clears its affected blocks; entities are still hurt.

So shared stations can't be griefed, no landing can be blocked, and no golem, boat, bed or anchor can be placed. Flux spilled in Vuulas pools under the [Hush spec's](hush.md#sources) rules, but nothing on the Road can hush. **Options considered:** per-player barriers between walkable stations. **Why not:** collision that differs per player needs matching client prediction, a new mechanism for no gain.

### A Void-touched place

**`RoadPlaceQuery`,** registered at init with `WarpService.registerPlaceQuery`: true iff the level is Vuulas and the position is inside the Road's column (|x|, |z| ≤ 23, 56 ≤ y < 296). O(1), no chunk read, zero allocation. Vuulas is also in the warp tuning's `context.dimensions` (amended), so the [warp spec's](warp.md#context) context-only danger can happen anywhere in it, never with a menu open.

## Stage 4 Choir pages

Two `vftv:choir_site` kinds, each given by [worldgen Reliquaries](worldgen.md#the-reliquary) with their positional, no-eviction, reissuing claims (the Road's claims carry `dim` `vftv:vuulas`):

| Site | `pages` | `pages_per_claim` | Reliquaries |
|---|---|---|---|
| `vftv:threshold` | `vftv:choir/where_they_went` | 1 | one in every Threshold |
| `vftv:road` | the five Stage 4 pages in #37's narrative order | 2 | at Steps 1, 2 and 3 |

- **Claims are successive and global**, by the worldgen algorithm: the **first** Road Reliquary a player uses gives the first two of the five that player hasn't latched, learned or claimed anywhere; the second distinct one gives the next two; the third the last. A player who pearls down and uses Step 2's first gets pages 1–2 there. A lost page is reissued at **the Reliquary where it was claimed**.
- **Six Stage 4 pages in all,** as the [research spec](research.md#choir-pages) expects. The Reliquaries stand on the rings, reachable without a fight.
- **Published sources** ([research, Affordability paths](research.md#affordability-paths), amended):

  | Path | Source | `count` | `requires` | `why` |
  |---|---|---|---|---|
  | `path-stage3.json`, as its last `use` | `{"advancement": "vftv:find/choirs_road"}` | 1 | none | a Threshold's Reliquary gives every player this page, with no research or aspect needed |
  | `path-stage4.json` | `{"choir_page": "vftv:choir/where_they_went", "site": "vftv:threshold"}` | 1 | none | the copy acquired at the end of Stage 3 |
  | `path-stage4.json` | `{"choir_page": <the 1st or 2nd Road page>, "site": "vftv:road"}` | 1 each | `{"research": ["vftv:hollow_bell"]}` | the first Road Reliquary a player uses, which Step 1's landing reaches before any encounter |

- **Pages 3–5 are not path sources:** they lie past an encounter, which no replay can prove. So no Stage 4 note may need their rewards (a rule on #37's path).

## Encounters

One **encounter slot** per Step (three), in memory in the server's Road service, each `IDLE` or `ACTIVE`, with a **station guard** held across every operation on it and released in `finally`.

### The rise

A Step Bell's row 7 (a rematch) or row 11 starts an encounter. Under the station guard and the player's road guard, first match:

| # | Case | Message (`road.vftv.*`) |
|---|---|---|
| 1 | the rise cell's chunk isn't entity-ticking (`ServerLevel#isPositionEntityTicking`) | `.step_silent` |
| 2 | a non-spectator player is within `rise.clearance` (**3**) blocks, in three dimensions, of the rise point | `.stand_back` |
| 3 | the rise box has any collision | `.step_occupied` |
| 4 | otherwise: compute admission now ("Admission"); `n = max(1, admitted)`; `ChoristerFactory.create(level, the Step's instance, rise point, entry health)`; `addFreshEntity` | |
| 5 | the factory gave none, or `addFreshEntity` returned false | `.step_silent`; the slot stays `IDLE` |
| 6 | **rechecked after the insertion**, against what is really there (a join listener may have moved the Chorister or a player): the Chorister isn't this instance, alive, in this level and within 1.0 block of the rise point; or its **actual** bounding box has any collision; or a non-spectator player is within `rise.clearance` of its **actual** position | `.step_silent`, `.step_occupied` or `.stand_back` respectively; ours discarded; the slot stays `IDLE` |
| 7 | otherwise | the slot becomes `ACTIVE`; the participants join the boss bar; `RoadEncounterEvent(STARTED)` is posted |

So nothing rises on anyone ([progression, What the Voices do](progression.md#what-the-voices-do)).

**Entry health** = `min(MAX_ENTRY_HEALTH, floorDiv(base × (1000 + scale.per_player_pm × (min(n, scale.max_players) − 1)), 1000))`, with `base` the instance's `health`, *n* the admitted count at the rise (at least 1), and `MAX_ENTRY_HEALTH` **1024**, vanilla's ceiling on the maximum-health attribute, which would clamp anything above it (`MC:world/entity/ai/attributes/Attributes.java:68–69`; `RangedAttribute.java:35–36`; `LivingEntity.java:1212–1213,2080–2081`). The entity's maximum health and the boss bar both use this value. Damage is never scaled. **Vectors** (`per_player_pm` 500, `max_players` 4; *n* = 0 to 5): base 200 → 200, 200, 300, 400, 500, 500; base 260 → 260, 260, 390, 520, 650, 650; base 320 → 320, 320, 480, 640, 800, 800; base 1000 → 1000, 1000, 1024, 1024, 1024, 1024.

### Admission

A player is **eligible** at Step *k* iff they are online, alive, in Vuulas, inside that Step's arena, and neither in creative nor spectator mode. Progress does not matter: anyone may help.

- **The arena candidate index:** per Step, a set of the players whose position is inside that Step's arena box, **ordered by entity id**. It is kept by our own per-player update: in the `PlayerTickEvent.Post` pass that runs the catch, each player in Vuulas is tested against the three arena boxes (O(1)) and added to or removed from an index only when that answer changes; login, logout, death, respawn and a level change remove or re-test them. An AABB entity query isn't used: vanilla's result isn't ordered by id and the usual overload allocates the whole list (`MC:world/level/Level.java:771–788`).
- **A refresh** first revalidates the existing participants (≤ 16) by eligibility, keeping those still eligible in their order, **wherever they sit in the index**. Then it examines at most the first `MAX_ARENA_SCAN` (64) index entries in ascending id for newcomers, admitting the eligible ones not yet participants until `MAX_PARTICIPANTS` (16). A newcomer beyond the first 64 waits for a place to free up nearer the front. The rest are **unadmitted**: never targeted, never credited, not on the boss bar, told once `.step_full`.
- **When:** at the rise, and every `encounter.refresh` (**10**) ticks after it. A participant who dies, logs out or changes level leaves at once (those events), and leaves the boss bar with them.

**Vectors** (cap 16; each step is a refresh with that eligible set): ids 1–18 → 1–16; 1–20 without 2, 5, 9 → 1, 3, 4, 6, 7, 8, 10–19; {3, 4, 20, 21} → 3, 4, 20, 21; {} → none.

### Threat and target

- **Threat** is kept per participant, in **tenths of health**: each `LivingDamageEvent.Post` on the Chorister (`NF:event/entity/living/LivingDamageEvent.java:35–44`) whose `DamageSource.getEntity()` is a participant adds `floor(amount × 10)` of the damage actually dealt, saturating at `THREAT_MAX` (1,000,000). Damage from anyone else adds nothing.
- **Decay:** every `threat.interval` (**20**) ticks after the rise, each threat becomes `floorDiv(threat × threat.keep_pm, 1000)` (**900**), before that tick's hits.
- **`targetFor(chorister)`:** among participants for whom `isEligibleParticipant` holds (eligible **now**), the highest threat; ties go to the smaller squared distance to the Chorister (`double`), then the lower entity id. With no participant, none. A participant who leaves loses their threat.

**Vectors:** threats 60 and 45 at squared distances 100 and 4 → the 60; 50 and 50 at 100 and 4 → the nearer; 50 and 50 at 9 and 9 → the lower id; all 0 at 16, 25 and 16 → the lower id of the two nearest; 1,000,000 at 400 against 999,999 at 1 → the 1,000,000. **Decay**, hits of 60 (player 1) at tick 0, 45 (player 2) at 25, 10 (1) at 41 and 30 (2) at 55: ticks 0, 20, 25, 40, 41, 55, 60 read 60/0, 54/0, 54/45, 48/40, 58/40, 58/70, 52/63.

### Defeat and credit

The Chorister's **confirmed death**, `Encounter.onConfirmedDeath` ("The split": called only after vanilla has accepted the death), ends the encounter **once**: under the station guard, if the slot is `ACTIVE` with that Chorister, it becomes `IDLE` before anything else, so a repeated notification does nothing. A `LivingDeathEvent` that a later subscriber cancels, or a heal before death, pays nothing. Then, in order:
1. **The credited** are the participants eligible at that moment (`isEligibleParticipant`). One who died to the same blow, or logged out, is not.
2. **Progress, all first:** for each credited player in ascending entity id, under their road guard, `progress` becomes *k* if it was *k* − 1. An unreadable record is skipped, logged once.
3. **`RoadStepPassedEvent`** for each player who advanced, in the same order.
4. **One keepsake** to each credited player, whoever already passed: `Inventory.add`, any remainder through `Player.drop(stack, false)` (the [worldgen hand-out](worldgen.md#handing-out)). A cancelled drop loses that copy; an exception is caught and logged once, and the rest go on.
5. The boss bar is cleared; `RoadEncounterEvent(DEFEATED)` is posted.

- **Keepsakes have no ledger:** a lost one is recovered by another defeat, a rematch rung alone included ("The Step Bells", row 7), and farming is bounded by fighting. Re-fighting a passed Step pays a keepsake and nothing else.
- **Keepsakes are never a currency beyond the Void rod and cap.** Wuuren can be beaten at range by a patient archer, which is harmless while a keepsake buys only three wand parts; no later spec or content may hang another recipe, trade or material on keepsakes. If one is ever wanted, give Wuuren Suhlaa's exhale first, never a change of cadence.
- **Who killed it doesn't matter:** a command or a fall credits the participants present.

### Reset, unload and peaceful

- **Reset:** with no participant for `encounter.reset` (**1,200**) consecutive ticks, or the Chorister removed or dead without a defeat (unloaded, discarded, killed while the slot wasn't its own), the slot resets: ours is discarded if present, the boss bar cleared, `RoadEncounterEvent(RESET)` posted. The Step stays unpassed.
- **Unload:** Choristers are never saved. If the ring's chunks unload, which happens as soon as nobody in Vuulas keeps them loaded, the Chorister is gone and the slot resets at the next tick. **The 1,200-tick window is a grace only while the ring stays loaded,** for example while another Listener is in Vuulas. A party that wiped alone rings again. Stated, not hidden; no chunk ticket is added.
- **Server stop:** encounters are memory only, so a restart starts every slot `IDLE`.
- **Peaceful:** the Chorister type is allowed in peaceful (`MC:world/entity/Mob.java:715–718` discards only types that aren't), and its damage scales to zero there (a contract on the Choristers spec), so a peaceful world can still finish the endgame.
- **Opening a screen doesn't pause a fight:** it isn't a Voice event. The [warp spec's](warp.md#danger) `pressing_dark` (Slowness only, so the floor marks stay visible) can fire in Vuulas, in context, under its own rules.

### The boss bar

One `ServerBossEvent` per slot (`MC:server/level/ServerBossEvent.java:20–22`), with a fixed UUID per Step, colour `WHITE`, overlay `PROGRESS`, named by the instance's lang key. Its players are exactly the admitted participants; its progress is health ÷ the entry health (≤ `MAX_ENTRY_HEALTH`), set each tick (vanilla sends only a change). **Not narrated:** vanilla's boss-bar overlay has no narration (`MC:client/gui/components/BossHealthOverlay.java:60–89`), an inherited limitation recorded by the [localisation spec](accessibility.md#inherited-surfaces); the fight stays readable through the Choristers' poses, marks, sounds and subtitles.

### Options considered

| Option | Why not |
|---|---|
| four bosses, one per maze | TC4's (TE §3) |
| a shared boss beaten once per world | later players locked out |
| random modifiers or moves | no fight could be learned (the moves are the Choristers spec's, fixed) |
| scaling damage with the group | punishes bringing friends |

## The Heart

### The block and the screen

**`vftv:heart`** (Lehvuun), the hollow of the chest that hums, at the bottom of the well. Survival-unbreakable like the Step Bells, light level 10, no block entity. Using it, under the road guard, first match:

| # | Case | Message (`heart.vftv.*`) |
|---|---|---|
| 0 | the road guard is held | `message.vftv.busy` |
| 1 | a second attempt this tick | none |
| 2 | the `vftv:road` record is unreadable | `message.vftv.unreadable` |
| 3 | `progress` < 3 | `.not_yet` |
| 4 | the warp record is unreadable | `message.vftv.unreadable` |
| 5 | **already chosen** ("The ending authority") | `.answered` (Joined) or `.silenced` |
| 6 | otherwise | opens **`HeartMenu`** |

**`HeartMenu`** is a block-bound menu, not the book ([research, Contracts](research.md#contracts-on-sibling-specs): the choice is made in the world). It holds no items. `stillValid` is vanilla's 8-block reach to the Heart.

**The screen,** 248 × 166 logical pixels, centred: a title; two panels of 116 × 120, **Joining** on the left and **Silencing** on the right, each with its consequences and one button (54 × 18, the research spec's button sprite); a Close button. Each consequence is a line of exact numbers from the menu's quote:

| Joining | Silencing |
|---|---|
| the permanent warp it adds, from the warp spec's preview of `(0, 0, J)` as `MILESTONE(vftv:joining)`, with "(less, at the limit)" when clipped. **J** is the Joining milestone's **current** amount in the warp tuning's `permanent.milestones` (5 as shipped), read for every quote and again at the commit, so a legal retuning never makes Joining `INVALID` | the temporary and lasting warp it removes, exactly the current T and L; permanent warp kept, with its amount |
| "The Voices go on." | "The Voices end, for good." |
| the lasting Void power: the Joining bonus, `joining.amplifier_bonus` | "The Hush can't hear me: it won't wake for me, and its Hollows won't hunt me. My own spills still pool; it still wakes for anyone else, or if left to run alone." |
| "Only I can still listen: Bargains and the Listen rite stay open to me." | "Bargains and the Listen rite close to me for good." |
| "I can never be Silenced." | "I can never Join." |

- **Panels:** each panel's consequence lines are a prose viewport (the [localisation spec's R1](accessibility.md#r1-viewports)) from 4 px below the panel's title to 4 px above its button, wrapping a long translation and scrolling with the research spec's scrollbar sprites. The title and the button stay outside it. Button labels follow R2: clipped at 50 px with "…", the full text as tooltip and narration.
- **Focus order:** the Joining viewport, the Joining button, the Silencing viewport, the Silencing button, Close. Tab and Shift-Tab move, Up and Down scroll a focused viewport, Enter and Space press, Escape closes. A viewport narrates its panel's title and every line, exact numbers included.
- **Each button opens a confirm** (the research spec's 160 × 80 confirm panel, with its body viewport; [research, GUI contract](research.md#gui-contract)), repeating that panel's lines in the body and ending "This cannot be undone in this world." Its buttons are Confirm and Cancel; focus starts on Cancel, then cycles body → Confirm → Cancel; Escape cancels. Every permanent consequence stays in the body, reachable by scrolling.
- **Quotes:** S2C `HeartQuote(containerId, revision, status, joining, silencing)`, where `status` is `READY`, `UNREADABLE` or `CHOSEN` and each side is a full `WarpPreview` (`dT`, `dL`, `dP`, `clipped`; Silencing's is `(−T, −L, 0, false)`). It is sent when the menu opens and again, with the next `revision`, after every `WarpChangedEvent` for its owner and every warp or Road tuning reload while it is open.

### The choice

**C2S `HeartChoose(containerId, revision, choice, echo)`**, a [checked commit](conventions.md#checked-commits) in its reference shape: `choice` one byte (0 Joining, 1 Silencing); `echo` the full `WarpPreview` the confirm showed. Out-of-bounds values are rejected at decode. The server, first match:

| # | Case | Effect |
|---|---|---|
| 0 | a second attempt this tick | nothing |
| 1 | `containerId` isn't this player's open `HeartMenu`, or it fails `stillValid` | nothing |
| 2 | the road guard is held | `BUSY` |
| 3 | `revision` isn't the menu's current one | `STALE` |
| 4 | `progress` < 3, the Road record unreadable, or already chosen | that row's message; the menu closes |
| 5 | `echo` isn't the current preview | `STALE` |
| 6 | **Joining:** `WarpService.commit(player, (0, 0, J), MILESTONE(vftv:joining), echo)`, with J read now | `OK` → row 8; `STALE` or `BUSY` as above; `INVALID` or `UNREADABLE` → `message.vftv.unreadable` |
| 7 | **Silencing:** `WarpService.silence(player, echo)` (amended: checked, returning `OK`, `STALE`, `BUSY` or `UNREADABLE`) | `OK` → row 8; the rest as row 6 |
| 8 | **chosen** | award `vftv:end/joined` or `vftv:end/silenced`; close the menu; send `EndingCue` ("The ending scene"); post `EndingChosenEvent`; release the guard in `finally` |

- **`BUSY`** shows `message.vftv.busy`; **`STALE`** shows `message.vftv.cost_changed` and sends a fresh quote ([conventions, The shape](conventions.md#the-shape)).
- **A retuned milestone:** the warp spec requires `vftv:joining` in `permanent.milestones` (amended there), so J always exists. A warp tuning reload while a confirm is open re-sends the quote with the next revision, so the old confirmation is refused (row 3), and the new J shows before anyone can commit.
- **One checked change, made by the warp spec.** The milestone commit and the silencing are each one guarded write with its event posted while warp's guard is held ([warp, The commit](warp.md#the-commit)); this spec writes no ending record of its own, so a listener or a crash can never observe a half-made choice.
- **A throwing `EndingChosenEvent` listener** is logged once; the choice stands.
- **Per player:** many players can stand at the Heart at once, each with their own menu. One player's choice touches nobody else's state.

### The ending authority

**The warp record is the only authority** ([warp, Kinds and the record](warp.md#kinds-and-the-record)):
- **Joined** ⇔ `vftv:joining ∈ milestones_paid` (written even when the cap clipped the amount, because the choice was made);
- **Silenced** ⇔ `silenced`;
- **chosen** ⇔ either. A record that holds both (only commands can make one) is chosen, and each capstone follows its own predicate.

**Reconciled at login,** never by polling: a Joined player missing `vftv:end/joined`, or a Silenced player missing `vftv:end/silenced`, is awarded it, so a crash between the warp write and the award loses no evidence. The capstone pages then latch through the research spec's own catch-up. Nothing is charged or replayed, and the ending scene is never replayed. The research spec's `reset` keeps warp, so it keeps the ending.

## The capstones

Both are **pure functions of the warp record the owner's client already holds**, so client and server agree and the book and the Ledger print them exactly.

| Ending | Warp ([warp, Joining and Silencing](warp.md#joining-and-silencing)) | Capstone | Price |
|---|---|---|---|
| **Joining** ("Heard") | permanent +J (5 as shipped), once; the Voices go on | **Lasting Void power:** `amplifier_warp` gains the **Joining bonus**, `joining.amplifier_bonus` (**10**, 0–50) warp points, added to total warp (amended in the warp spec); Bargains and the Listen rite stay open | permanent warp and the Voices for good; never Silenced |
| **Silencing** ("Unheard") | temporary and lasting to 0; permanent kept; never checked again | **The Hush can't hear you:** the [Hush spec's](hush.md#where-it-begins) spread attendance counts only players who aren't Silenced, and Hollows never acquire or keep a Silenced target (amended in the Hush spec) | the Voices, Bargains, insights and the Listen rite end; Void power falls to permanent and gear warp; never Joined |

- **The bonus is additive, not a floor.** Current warp still scales Void power ([progression, Warp](progression.md#warp); [warp, Stilling](warp.md#stilling)): a Joined Listener who stills from 12 to 5 goes from 22 to 15, and their Void power from ×1.88 to ×1.6. It is bounded, and the `warp.amplifier` switch still zeroes it. At `void.per_warp_pm` 40 the bonus of 10 is worth +40%, and Joining's own permanent 5 plus the bonus give 15, ×1.6 (1600‰) before lasting warp or gear, below the doubling at 25, so stilling still costs a Joined Listener Void power. The [armor spec's](armor.md#void-armour) Void Baffle reads the same amplifier, so it follows.
- **Vectors** (`per_warp_pm` 40, `max_bonus_pm` 1000; total, Joined, bonus, switch → `w`, `amp_pm`, a base-4000 Void working's magnitude): 12, no, 10, on → 12, 1480, 5920; 12, yes, 10, on → 22, 1880, 7520; 5, yes, 10, on → 15, 1600, 6400; 0, yes, 10, on → 10, 1400, 5600; 12, yes, 0, on → 12, 1480, 5920; 2,200, yes, 50, on → 2,250, 2000 (capped), 8000; 12, yes, 10, off → 0, 1000, 4000.
- **Silencing's scope:** a Silenced player's spills still deposit; another player near the pool, or the Hush spec's `hush.unattended`, still runs it; the Pealing Bell still counts them; Hollows still rise near them under the rise rules and still hunt everyone else. Mixed parties need nothing more.
- **Joining is the stronger ending, and says so.** It keeps the Void, its noise and its power: a band's worth of Void power always, and the only way left to listen (Bargains and the Listen rite close to the Silenced for good). Silencing buys quiet and the Hush's deafness with power. Doom or release stays the player's ([lore, Cosmology](lore.md#cosmology)).
- **Options considered:**

  | Option | Why not |
  |---|---|
  | a Void-power floor for the Joined | stilling 39 → 5 would cost no power, against the settled dial |
  | capstone items | a stat stick doesn't change how you play, and an item is shareable, so not yours |
  | no tax for the Silenced | contradicts the warp spec's settled "taxed and visible" |

## The ending scene

**S2C `EndingCue(choice)`** goes to the chooser only, after the choice is written. On Silencing, `silence()` has already sent `VoiceClear` on the same connection, so it arrives first.

- **Precedence:** on receipt, the client stops any Voice line playing and drops any held Voice cue. While the scene plays, every new `VoiceCue` is dropped (its server outcome stands; a Joined Listener's Voices go on afterwards).
- **The lines:** the pool's three lines in id order (`vftv:joining_0…2` or `vftv:silencing_0…2`, the [lexicon's](warp.md#the-lexicon) ending lines), each through the [warp spec's playback](warp.md#voice-lines) on `SoundSource.VOICE` with its Voice text, honouring `voices.text`. Each next line starts 20 ticks after the later of the previous one's sound stopping (or 100 ticks after it began, if it never started) and, while `voices.text` is on, the end of its caption's hold, which the player's notification time extends ([localisation, Voice text](accessibility.md#voice-text)). So every ending caption is read whole before the next replaces it. A line is resolved when it starts: a missing sound still shows its text for 60 ticks.
- **Joining** also runs the warp spec's colour drain at band 3's peak: in over 50 ticks at the first line, held until the last line ends, out over 50, honouring `screenEffectScale` and `voices.colour_shift`.
- **Held while a screen is open,** with no time limit: it plays when the screen closes, and nothing replaces it.
- **A resource reload** keeps the scene: the queued line ids survive it, each line still to come is resolved against the new resources when it starts, and a line whose clip the reload stopped counts as finished, so the next one follows after the usual 20 ticks. Nothing already played is replayed; a line now missing falls back to its text for 60 ticks.
- **Cleared** (lines stopped, drain ended) at a level change, a `LocalPlayer` replacement (death, respawn) or a disconnect, and never replayed.

## After the ending

- **Everyone keeps playing** ([progression, Endgame](progression.md#endgame)). Joined and Silenced Listeners walk the Road, fight for keepsakes and help others. The Heart refuses only them.
- **Mixed parties need nothing special:** every effect is per player.
- **There is no way back.** No command of ours clears an ending; the warp spec's commands change warp, and the choice's authority stays theirs.

## Existing worlds and removing the mod

**Added to an old world:** datapack stems are merged on every load (`MC:world/level/levelgen/WorldDimensions.java:154–175`), and levels are created once at start, so **Vuulas appears after the world is reopened**. Thresholds generate only in new chunks.

**What this spec relies on, and tests:**
- **The stale stem is skipped.** `WorldDimensions.CODEC` decodes its map with NeoForge's `LenientUnboundedMapCodec` (`MC:WorldDimensions.java:38–41`; `NF:common/LenientUnboundedMapCodec.java:45–59`): an entry that fails to decode is logged and left out, and the map still succeeds. Without that, a world-gen-settings read failure falls back to defaults **with a random seed** (`MC:world/level/storage/LevelStorageSource.java:154–162`), the gravest failure here. So a regression test decodes, with real registry ops, saved settings holding an entry whose generator type is unknown, and asserts that the overworld, the Nether and the End survive with the seed unchanged.

**What we do:**
- The stem is self-contained: nothing vanilla refers to Vuulas.
- A return point in a missing level falls back to the default spawn ("Coming back").
- **`/vftv road evacuate`** returns every online player in Vuulas ("Commands").
- **Logging out returns the player** ("Coming back"), so after a clean stop no player file's final write is in Vuulas. An autosave followed by a crash can still leave one there; `evacuate` (with VFTV present) is the remedy.
- **The uninstall checklist** (in this spec and the release notes): stop the server cleanly (which returns everyone, as above); remove the jar; optionally delete `dimensions/vftv/vuulas/`.

**What we can't do, and what happens:**
- Nothing of ours runs after removal: no offline player can be moved and no save file edited.
- **An offline player saved in Vuulas** (only by an autosave followed by a crash or killed process, since logout returns them) loads into the **configured world-spawn level** (the overworld unless changed) **at the same coordinates** (`MC:server/network/config/PrepareSpawnTask.java:52–62`). The Road lies at x, z ≈ 0 and y 56–296, so that player may stand in terrain or in the air.
- **Every VFTV attachment is lost** the first time a player is loaded and saved without VFTV: unknown attachment types are skipped on load and only registered ones are written (`NF:attachment/AttachmentHolder.java:120–136,144–160`). That is warp (and so the ending), research, Road progress and page claims, for every player. **Re-adding the mod restores Vuulas from its region files, but not that progress.**
- Region folders are never deleted by vanilla (only `deleteLevel()` recurses).

**A release regression** runs this lifecycle (see "Tests").

**Options considered:** a vanilla-only stem (`minecraft:flat` in `minecraft:the_void` with a vanilla type) that survives removal. **Why not:** it loses every attribute of the place, leaves an empty level ticking forever, and the only vanilla type without a ceiling or skylight, `the_end`, starts a dragon fight.

## Names

No new lexicon word: the lexicon keeps 23. **`uses` gains `"name"`** for *vuulas*, *suhlaa*, *yoorel* and *shohrel*; every other use and every ending line is kept. All four already pass the phonology. Void-tongue is for eldritch things only ([lore, Naming register](lore.md#vocabulary-policy)); every id stays English.

| Thing | Display name | Gloss |
|---|---|---|
| the dimension | **Vuulas** | home; where we are (the Joining line "yehlas vuulas") |
| the Hollow Bell | **Thahvis** | road; the way down. It also keeps "Hollow" for the Hush's creature in play |
| the Void rod, cap | **Lirhas**, **Ohshaal** | deep; silence |
| the keepsake | **Yoorel** | to keep; to remember |
| the Step Bell | **Shohrel** | to go |
| the Heart | **Lehvuun** | the hollow of the chest that hums |
| the Choristers (their spec) | **Wuuren**, **Suhlaa**, **Nihthaa** | footfall; breath; the listening brow |

Everyday English: the **Threshold**, the **Road**, a **Step**, a **Chorister**, the **catch**, a **keepsake**, the **Heart**. CONTEXT gains each.

## Rendering and sound

### Models

Pixel-aligned, no rotations, at most 6 cuboids ([art style §6](art-style.md#6-blocks-and-models)), Stage 4 row:

| Block | Cuboids (from → to, model pixels) | Light |
|---|---|---|
| `vftv:step_bell` | posts [1,0,6]–[3,16,10] and [13,0,6]–[15,16,10]; beam [1,14,6]–[15,16,10]; bell [5,5,5]–[11,13,11]; lip [4,4,4]–[12,5,12] | 4 |
| `vftv:heart` | base [2,0,2]–[14,2,14]; core [5,2,5]–[11,12,11] in void ink with the teal inside; ribs [3,2,3]–[4,13,13] and [12,2,3]–[13,13,13]; crown [3,13,3]–[13,15,13] | 10 |

The Step Bell has four facings by blockstate rotation; the Heart one variant. Item forms use the block models. The items are 16 × 16 sprites: `hollow_bell` from the art prototype's `hollow_bell()` (half brass, half Hush, a void mouth, the teal in the dark); `void_rod` an upright instrument and `void_cap` a cap, in the Stage 4 row; `keepsake` a small cracked bell-tongue in `hush` with the teal inside. The wand layers `wand/rod/void` and `wand/cap/void` follow the wands spec's layer form.

### Sound

Stage 4 register ([art style §7](art-style.md#7-sound-palette)): no clang, a late room, the detuned pair drifting wide.

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:hollow_bell.toll` | 3 | every 20 ticks while the bell is held in use | at the player; `PLAYERS`, volume 1.0 |
| `vftv:road.descend`, `vftv:road.return` | 1 each | on arrival in Vuulas; on arrival back | at the arrival point; `PLAYERS`, 1.0 |
| `vftv:road.catch` | 1 | the catch | at the landing; `PLAYERS`, 0.8 |
| `vftv:step_bell.ring` | 3 | a rise, up and down | at the bell; `BLOCKS`, 1.5 |
| `vftv:step_bell.still` | 1 | every refusal with a message | at the bell; `BLOCKS`, 0.6 |
| `vftv:heart.joining`, `vftv:heart.silencing` | 1 each | the choice | at the Heart; `BLOCKS`, 1.0 |

Files under `assets/vftv/sounds/` with the event's path (`hollow_bell/toll_0…2`, `road/descend`, `road/return`, `road/catch`, `step_bell/ring_0…2`, `step_bell/still`, `heart/joining`, `heart/silencing`); `sounds.json` entries carry no overrides and name `subtitles.vftv.<event>`. No particle of our own: the encounters' particles are the Choristers spec's.

## Commands

`/vftv road …`, permission level 2. A target player must be online.

| Command | Effect |
|---|---|
| `get <player>` | prints progress, the return point, `last_station`, and the ending as derived from warp |
| `progress <player> <0–3>` | sets progress; refused, with nothing changed, if the record is unreadable |
| `evacuate` | returns every online player in Vuulas ("Coming back"), one per tick, and prints how many |

There is no ending command: the warp spec's own commands are the only way to change the record that decides it.

## Authored content

Authored inputs follow the [authored-content envelope](conventions.md#the-envelope).

```
content/eldritch/templates/<name>.json   the five templates (the templates ticket)
content/eldritch/threshold.json          placement numbers and biomes
content/eldritch/sites.json              the two site kinds
content/eldritch/vuulas.json             the dimension's colours and light
content/eldritch/recipes.json            the three infusion bodies
content/eldritch/wand_parts.json         the Void rod and cap
content/eldritch/bootstrap.json          the recipes' reviewed sources
content/eldritch/tags.json               the item and block tags
content/eldritch/fixtures.json           the vector tables, transcribed
content/lang/en_us/eldritch.json         every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `templates/<name>.json` | the worldgen spec's `vftv:template/1`, families `threshold` and `road` | `data/vftv/structure/threshold/threshold.nbt`, `data/vftv/structure/road/<name>.nbt` |
| `threshold.json` | `vftv:threshold/1` | `data/vftv/worldgen/structure/threshold.json`, `worldgen/structure_set/threshold.json`, `worldgen/template_pool/threshold/start.json`, `tags/worldgen/biome/has_structure/threshold.json` |
| `sites.json` | `vftv:eldritch_sites/1` | `data/vftv/vftv/choir_site/threshold.json`, `…/road.json` |
| `vuulas.json` | `vftv:vuulas/1` | the colour and light fields of `data/vftv/dimension_type/vuulas.json` |
| `recipes.json` | `vftv:eldritch_recipes/1` | `data/vftv/recipe/<path>.json`, bodies unchanged except the [compatibility spec's ingredient widening](compat.md#consumed) |
| `wand_parts.json` | the wands spec's `vftv:wand_parts/1` | `data/vftv/vftv/wand_rod/void.json`, `…/wand_cap/void.json`; merged with every parts file, a duplicate id or item rejected |
| `bootstrap.json` | `vftv:eldritch_bootstrap/1` | nothing: read by the review |
| `tags.json` | `vftv:eldritch_tags/1` | `data/vftv/tags/item/usable_on_the_road.json`, `…/item/forbidden_on_the_road.json`, `data/vftv/tags/block/unsafe_return.json` |
| `fixtures.json` | `vftv:eldritch_fixtures/1` | nothing: read by the unit tests |
| `lang/<lang>/eldritch.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json` ([conventions, Lang](conventions.md#lang)) |

The stem, the generator's codec entry, the dimension type's fixed fields, the biome, the advancements, `#vftv:choir_sites`' new member, the fixed structure fields and the tuning file `data/vftv/tuning/eldritch.json` (from the record's defaults) are written by datagen from code. They are not authored content.

### Templates

The worldgen spec's `vftv:template/1` format, its format, block-state, namespace, Hush and attachment rules, and **this spec's family rules**:

| Family | Files | Size | `vftv:` blocks allowed | Block entities | Entities |
|---|---|---|---|---|---|
| `threshold` | exactly `threshold` | exactly 33 × 44 × 33 | `vftv:reliquary` (exactly 1, site `vftv:threshold`) | `reliquary` only | exactly 2 `vftv:answerer` and 1 `vftv:answerer_cantor` |
| `road` | exactly `step_1`, `step_2`, `step_3`, `heart` | exactly 47 × 40 × 47 | `vftv:reliquary` (exactly 1 in each Step, site `vftv:road`; none in `heart`), `vftv:step_bell` (exactly 1 each), `vftv:heart` (exactly 1, in `heart` only) | `reliquary` only | none |

**Rejected,** besides the worldgen spec's format rules: a role, count or position other than "The template" or "The layout" states; a well cell that isn't air; a ring floor cell that isn't a full block with a sturdy top face; a rise box with any non-air cell; a choir-walk cell that isn't air; a path rule broken; a shaft or stair headroom cell that is structure void; any `minecraft:jigsaw`, chest, spawner, vault or container; any `vftv:node`; any block in `#vftv:hushed` or in the [Hush spec's](hush.md#the-forms) source states on the Road; the key `neoforge:attachments` anywhere. The datagen-phase **parsed-NBT no-Hush test** covers all five generated files, as it covers every template.

### The Threshold

`{"schema": "vftv:threshold/1", "structure_set": {"spacing", "separation", "salt", "exclusion_chunks"}, "biomes": {"include", "exclude"}}`:

| Field | Range |
|---|---|
| `structure_set.spacing` | 32–256, > `separation` |
| `structure_set.separation` | 16–255 (≥ 16 keeps starts 17 chunks apart, 17 × 16 > 2 × 33) |
| `structure_set.salt` | must equal 1414025811 |
| `structure_set.exclusion_chunks` | 1–16 |
| `biomes.include`, `biomes.exclude` | as the worldgen spec's `choir_ruin.json`: vanilla overworld biomes, every one of `#minecraft:is_overworld`'s 55 in exactly one list, each exclusion with a one-line reason |

The shipped file is `{"schema": "vftv:threshold/1", "structure_set": {"spacing": 64, "separation": 24, "salt": 1414025811, "exclusion_chunks": 8}, "biomes": …}` with the biome lists **transcribed exactly** from the worldgen spec's shipped `choir_ruin.json` (32 included, 23 excluded). **Rejected:** a field out of range; a biome list that differs from the ruin's.

### Sites

```json
{"schema": "vftv:eldritch_sites/1", "sites": {
  "vftv:threshold": {"pages": ["vftv:choir/where_they_went"], "pages_per_claim": 1},
  "vftv:road": {"pages": ["vftv:choir/r1", "vftv:choir/r2", "vftv:choir/r3", "vftv:choir/r4", "vftv:choir/r5"],
                "pages_per_claim": 2}}}
```

`vftv:road`'s five ids are placeholders here: the content ticket transcribes #37's frozen narrative order. **Rejected:** another site id; `vftv:threshold` other than shown; a `vftv:road` list that isn't exactly #37's five Stage 4 pages in its order; `pages_per_claim` other than 2; any page in both sites. At load, both kinds pass the worldgen spec's registry validation; the Stage 3 rules apply to `vftv:choir_ruin` only.

### Vuulas

`{"schema": "vftv:vuulas/1", "fog": <palette name>, "sky": <palette name>, "ambient_light": 0.0–0.25}`, with palette names from the [locked palette](art-style.md#2-palette-locked-project-wide-48-colours), written as their hex. Shipped: `{"schema": "vftv:vuulas/1", "fog": "ink0", "sky": "ink0", "ambient_light": 0.0}`. **Rejected:** an unknown palette name; a value out of range.

### Recipes

`{"schema": "vftv:eldritch_recipes/1", "recipes": {"vftv:hollow_bell": <body>, "vftv:void_rod": <body>, "vftv:void_cap": <body>}}`: exactly these three, each a `vftv:infusion` body meeting "The items"' table, `transmute` false, result count 1. A sketch of one (the content ticket sets the essentia and instability):

```json
{"schema": "vftv:eldritch_recipes/1", "recipes": {
  "vftv:hollow_bell": {"type": "vftv:infusion", "research": "vftv:hollow_bell", "kind": "void",
    "centre": "minecraft:bell",
    "components": ["vftv:resonant_brass_ingot", "vftv:resonant_brass_ingot",
                   "minecraft:amethyst_shard", "minecraft:amethyst_shard"],
    "essentia": {"vftv:vacuos": 16, "vftv:silentium": 8, "vftv:sonus": 8},
    "instability": 25,
    "result": {"item": {"id": "vftv:hollow_bell", "count": 1}}}}}
```

**Rejected:** another id; a missing or extra recipe; any field or value outside the table's rule; any `vftv:keepsake` in `vftv:hollow_bell`.

### Wand parts

The wands spec's `vftv:wand_parts/1`, with exactly rod `vftv:void` (`item` `vftv:void_rod`, `capacity` 500) and cap `vftv:void` (`item` `vftv:void_cap`, `cost_multiplier` 0.55). **Rejected:** any other entry or value.

### The bootstrap

`{"schema": "vftv:eldritch_bootstrap/1", "recipes": {<id>: {"items": [{"item": <item id or #tag>, "why": <one line>}, …], "aspects": [{"aspect": <id>, "why": <one line>}, …]}}}`, the [armor spec's](armor.md#the-bootstrap) record, for exactly the three recipes. **Coverage:** every centre, component and essentia aspect. **Review:** each `why` says how the thing is obtained **in Stage 4 without Vuulas** for `vftv:hollow_bell`, and after the Steps for the keepsake ("a Chorister's credit, Step 1 onwards"); for an aspect, which matter dissolves into it. The sequence is non-circular. **Rejected,** beyond the [bootstrap convention](conventions.md#bootstrap): a missing recipe.

### Tags

`{"schema": "vftv:eldritch_tags/1", "item": {<tag>: [<item id or #tag>]}, "block": {<tag>: [<block id or #tag>]}}`, with exactly:
- `#vftv:usable_on_the_road`: `vftv:wand`, `vftv:tuning_fork`, `vftv:hollow_bell`, `vftv:commonplace_book`;
- `#vftv:forbidden_on_the_road`: `minecraft:water_bucket`, `lava_bucket`, `powder_snow_bucket`, `cod_bucket`, `salmon_bucket`, `pufferfish_bucket`, `tropical_fish_bucket`, `axolotl_bucket`, `tadpole_bucket`, `flint_and_steel`, `fire_charge`, `bone_meal`;
- `#vftv:unsafe_return` (block): `minecraft:fire`, `soul_fire`, `magma_block`, `campfire`, `soul_campfire`, `cactus`, `sweet_berry_bush`, `wither_rose`, `powder_snow`, `pointed_dripstone`.

**Rejected:** a tag or member outside these; one missing.

### Fixtures

`{"schema": "vftv:eldritch_fixtures/1", "placement": [...], "health": [...], "admission": [...], "target": [...], "decay": [...], "amplifier": [...], "arrival": [...]}`: seven sections under the [fixtures convention](conventions.md#fixtures).

| Record | Fields |
|---|---|
| `placement` | the worldgen spec's `placement` record |
| `health` | `base` 1–1000; `per_player_pm` (default 500); `max_players` (default 4); `expect`: six ints, for *n* = 0 to 5 |
| `admission` | `cap` (default 16); `steps`: each `{"eligible": [ids], "expect": [ids]}`, applied in order from no participants |
| `target` | `threat` `{id: 0–1,000,000}`; `dist2` `{id: int ≥ 0}` with the same ids; `expect`: an id |
| `decay` | `interval` (default 20); `keep_pm` (default 900); `hits`: `[tick, id, tenths]` triples; `expect`: `[tick, {id: threat}]` pairs |
| `amplifier` | `total` 0–2,200; `joined`; `bonus` 0–50 (default 10); `on` (default true); `expect` `{w, amp_pm, out}` for a base-4000 Void working at `per_warp_pm` 40, `max_bonus_pm` 1000 |
| `arrival` | `expect`: the four stations for `progress` 0–3 |

**Types:** ids are positive ints; *hex* is as the worldgen spec's. **The complete file:**

```json
{"schema": "vftv:eldritch_fixtures/1",
 "placement": [
  {"seed": "0x123456789ABCDEF", "spacing": 64, "separation": 24, "salt": 1414025811, "region": [0, 0], "expect": [31, 26]},
  {"seed": "0x123456789ABCDEF", "spacing": 64, "separation": 24, "salt": 1414025811, "region": [-1, 0], "expect": [-38, 6]},
  {"seed": "0x123456789ABCDEF", "spacing": 64, "separation": 24, "salt": 1414025811, "region": [0, -1], "expect": [3, -28]},
  {"seed": "0x123456789ABCDEF", "spacing": 64, "separation": 24, "salt": 1414025811, "region": [3, -7], "expect": [214, -435]},
  {"seed": "0x123456789ABCDEF", "spacing": 64, "separation": 24, "salt": 1414025811, "region": [-12345, 6789], "expect": [-790070, 434520]},
  {"seed": "0x123456789ABCDEF", "spacing": 64, "separation": 24, "salt": 1414025811, "region": [29000, -29000], "expect": [1856003, -1855985]}],
 "health": [
  {"base": 200, "expect": [200, 200, 300, 400, 500, 500]},
  {"base": 260, "expect": [260, 260, 390, 520, 650, 650]},
  {"base": 320, "expect": [320, 320, 480, 640, 800, 800]},
  {"base": 1000, "expect": [1000, 1000, 1024, 1024, 1024, 1024]}],
 "admission": [
  {"steps": [
   {"eligible": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18],
    "expect": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]},
   {"eligible": [1, 3, 4, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20],
    "expect": [1, 3, 4, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]},
   {"eligible": [3, 4, 20, 21], "expect": [3, 4, 20, 21]},
   {"eligible": [], "expect": []}]}],
 "target": [
  {"threat": {"1": 60, "2": 45}, "dist2": {"1": 100, "2": 4}, "expect": 1},
  {"threat": {"1": 50, "2": 50}, "dist2": {"1": 100, "2": 4}, "expect": 2},
  {"threat": {"2": 50, "3": 50}, "dist2": {"2": 9, "3": 9}, "expect": 2},
  {"threat": {"1": 0, "2": 0, "3": 0}, "dist2": {"1": 16, "2": 25, "3": 16}, "expect": 1},
  {"threat": {"1": 1000000, "2": 999999}, "dist2": {"1": 400, "2": 1}, "expect": 1}],
 "decay": [
  {"hits": [[0, 1, 60], [25, 2, 45], [41, 1, 10], [55, 2, 30]],
   "expect": [[0, {"1": 60, "2": 0}], [20, {"1": 54, "2": 0}], [25, {"1": 54, "2": 45}], [40, {"1": 48, "2": 40}],
              [41, {"1": 58, "2": 40}], [55, {"1": 58, "2": 70}], [60, {"1": 52, "2": 63}]]}],
 "amplifier": [
  {"total": 12, "joined": false, "expect": {"w": 12, "amp_pm": 1480, "out": 5920}},
  {"total": 12, "joined": true, "expect": {"w": 22, "amp_pm": 1880, "out": 7520}},
  {"total": 5, "joined": true, "expect": {"w": 15, "amp_pm": 1600, "out": 6400}},
  {"total": 0, "joined": true, "expect": {"w": 10, "amp_pm": 1400, "out": 5600}},
  {"total": 12, "joined": true, "bonus": 0, "expect": {"w": 12, "amp_pm": 1480, "out": 5920}},
  {"total": 2200, "joined": true, "bonus": 50, "expect": {"w": 2250, "amp_pm": 2000, "out": 8000}},
  {"total": 12, "joined": true, "on": false, "expect": {"w": 0, "amp_pm": 1000, "out": 4000}}],
 "arrival": [{"expect": ["step_1", "step_2", "step_3", "heart"]}]}
```

JSON object keys are strings, so the `target` and `decay` maps key ids as decimal strings. The file above is the one the content ticket ships. The [warp spec's](warp.md#fixtures) `amplifier` section transcribes the same seven rows.

### Lang keys

[Lang files](conventions.md#lang); every key below is required in `en_us`. The English is the intended sense; the content ticket writes the final text, and its review applies the research spec's number-word lint.

| Key | Args | Sense |
|---|---|---|
| `item.vftv.hollow_bell`, `item.vftv.void_rod`, `item.vftv.void_cap`, `item.vftv.keepsake` | 0 | "Thahvis", "Lirhas", "Ohshaal", "Yoorel" |
| `block.vftv.step_bell`, `block.vftv.heart` | 0 | "Shohrel", "Lehvuun" |
| `wand_rod.vftv.void`, `wand_cap.vftv.void` | 0 | "Lirhas", "Ohshaal" |
| `structure.vftv.threshold` | 0 | "Threshold" |
| `dimension.vftv.vuulas` | 0 | "Vuulas" |
| `road.vftv.bell_mute`, `.not_now`, `.dull`, `.closed`, `.down`, `.up`, `.returned`, `.caught` | 0 | "The bell is only metal to me.", "Not now.", "It rings dull here.", "The way is closed.", "Down.", "Up.", "Back.", "The silence gives me back." |
| `road.vftv.step_not_mine`, `.step_listening`, `.stand_back`, `.step_occupied`, `.step_silent`, `.step_full` | 0 | "This step is not yet mine.", "It is already listening.", "Stand back from the step.", "Something stands on the step.", "The step stays silent.", "The step can hear no more." |
| `heart.vftv.title`, `.joining`, `.silencing`, `.not_yet`, `.answered`, `.silenced`, `.confirm`, `.cancel`, `.close`, `.final` | 0 | "The Heart", "Join them", "Silence them", "The heart does not hear me yet.", "I have answered.", "I have silenced them.", "Confirm", "Cancel", "Close", "This cannot be undone in this world." |
| `heart.vftv.joining.permanent`, `.joining.bonus` | 1 | "%s permanent warp", "My Void workings hear %s more, always." |
| `heart.vftv.joining.voices`, `.joining.listen`, `.joining.never`, `.silencing.voices`, `.silencing.listen`, `.silencing.hush`, `.silencing.never` | 0 | "The Voices go on.", "Only I can still listen: Bargains and the Listen rite stay open to me.", "I can never be Silenced.", "The Voices end, for good.", "Bargains and the Listen rite close to me for good.", "The Hush can't hear me…", "I can never Join." |
| `heart.vftv.silencing.removed` | 2 | "%s temporary and %s lasting warp, stilled" |
| `heart.vftv.silencing.kept` | 1 | "%s permanent warp stays" |
| `narration.vftv.heart.choice` | 2 | "%s: %s" |
| `commands.vftv.road.get` | 5 | "%s: progress %s, return %s, station %s, ending %s" |
| `commands.vftv.road.progress`, `.evacuate` | 2, 1 | "%s: progress %s.", "Returned %s." |
| `subtitles.vftv.<event>` for every sound event above | 0 | "Hollow bell tolls", "Descends", "Returns", "Silence catches", "Step bell rings", "Step bell is still", "Heart answers", "Heart falls silent" |

**Used, not declared here:** the research spec's prose keys for the seven entries (#37's), the shared [`message.vftv.busy`, `.unreadable` and `.cost_changed`](conventions.md#message-keys), the warp spec's ending lines, aspect and player names, the Choristers' names (their spec's).

**Where messages go:** every `road.vftv.*` message, every `heart.vftv.*` refusal of a use (the Hollow Bell, the Step Bells, the Heart's first-match table) and every shared key they show goes to the [status line](accessibility.md#r5-the-status-line) ([conventions, Feedback routing](conventions.md#feedback-routing)); inside the Heart screen, a refusal shows in the screen and its quote refreshes. The Void-tongue names above are protected text: every locale keeps them exactly ([localisation, Protected text](accessibility.md#protected-text)).

**Rejected:** a Void-tongue name that isn't 1–2 lexicon words, capitalised as a proper item name, passing the lexicon's phonology lint.

## Events

NeoForge events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server through a publisher seam that `neoforge` implements. Payload records live in `api` and are **immutable**, captured after the state they report is written. The payload shape and failure rules are the [API spec's](api.md#events).

| Event | When | Payload |
|---|---|---|
| `RoadStepPassedEvent` | after **every** credited player's progress is written, before any keepsake ("Defeat and credit"), once per player who advanced | player; step 1–3; progress after |
| `RoadEncounterEvent` | `STARTED` after a counted rise; `DEFEATED` after credit; `RESET` after a reset | step; outcome; the participants' UUIDs at that moment |
| `EndingChosenEvent` | after the warp change, the award and `EndingCue` ("The choice") | player; `JOINED` or `SILENCED`; the warp change's actual `WarpPreview` |

- **A throwing listener** is logged once; the change stands, and the remaining steps run.
- **Travel** uses NeoForge's own `EntityTravelToDimensionEvent` and `PlayerChangedDimensionEvent`; same-level travel posts nothing of ours.
- **Plain `core` services:** `RoadService` (descent, return, the bells, the Heart, the catch), `RoadGeometry` (the layout functions over `api`'s constants-only `RoadLayout`), `RoadPlaceQuery`, `ChoristerFactory` and the `Chorister` entity (the Choristers spec's), `Encounter`. **All live in the one feature `core.road`**, with the Minecraft-free encounter and Chorister rules in `core.road.model`, so the frame and the creature never form a feature cycle ([architecture, Enforcement](architecture.md#enforcement)). None is public in v1 ([API spec, Services](api.md#services)).

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| Road progress and the return point | the player attachment `vftv:road`, `copyOnDeath` | never: the client reads its capstones from the warp record |
| the ending | the warp record (`milestones_paid`, `silenced`) | the warp spec's owner sync |
| `last_station`, guards, attempt counts, travel cooldowns, encounters, the template cache | memory | never; encounters show through the boss bar and the Choristers' entity data |
| the Road and Thresholds | chunk data (vanilla) | vanilla |
| page claims | the worldgen spec's `vftv:choir_claims` | never |
| `RoadRules` | `data/vftv/tuning/eldritch.json` | every player, as a [Rules record](conventions.md#rules-records), for fact spans |
| Heart quotes | the menu | `HeartQuote`, to the menu's owner |

**The record `vftv:road`:**

```
{format: 1, progress: 0–3, return?: {dim: <id ≤ 256 characters>, pos: [x, y, z], rot: [yRot, xRot]}}
```

- **Default:** absent means `{format 1, progress 0}`, with no return point.
- **Bounds:** `pos` finite doubles, x and z within ±30,000,000, y within −2,048 to 2,048; `rot` finite floats; `dim` a syntactically valid id. A `dim` naming no loaded level is kept as it is and falls back to the default spawn at return.
- **Unreadable records** follow [conventions](conventions.md#raw-record-retention), and **every Road path treats one the same way:** the descent refuses (row 6); the return goes to the default spawn and writes nothing; every Step Bell gesture but Step 1's return refuses; credit skips the player; the Heart refuses; `progress` refuses. The catch never reads it.
- **Saving:** player attachments are written with the player at every save, so no dirty flag is needed.

**Payloads:** `HeartQuote` (`containerId`; `revision` a `long`; `status` one byte; two `WarpPreview`s, each three values −1,000 to 1,000 and a boolean); `HeartChoose` (`containerId`, `revision`, `choice` 0–1, one `WarpPreview`); `EndingCue` (`choice` 0–1). Out-of-bounds values are rejected at decode. Each C2S value has a hostile-input GameTest.

**Reloads:**

| Change | Effect |
|---|---|
| Tuning (`/reload`) | a new `RoadRules`; a toll in progress keeps the duration it started with; set cooldowns stand; encounters read `encounter.refresh`, `threat.interval`, `threat.keep_pm` and `encounter.reset` at their next use; entry health only at a rise; the catch reads `catch.depth` each tick; open Heart menus get fresh quotes |
| The warp tuning | open Heart menus get fresh quotes; the Joining bonus applies at once, through the dial |
| Templates, the stem, the dimension type, the biome, the structure | need the world reopened; the template cache is never rebuilt live |
| Tags | re-resolved, applied at the next interaction |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/eldritch.json`**, loaded on reload by `core.road` into the immutable record `RoadRules` (the record lives in `api`; its loader in `core`) and written by datagen from its defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. Every rule above is defined across these full ranges.

| Key | Value | Range | Notes |
|---|---|---|---|
| `bell.toll` | 60 ticks | 20–200 | the Hollow Bell's use duration |
| `bell.cooldown` | 100 ticks | 0–1,200 | the item's cooldown after a travel |
| `travel.cooldown` | 40 ticks | 20–1,200 | per player, across every Road travel, returns included; exempt: a return with an unreadable record, `evacuate` and the catch ("Coming back"); ≥ 20 keeps teleports bounded |
| `catch.depth` | 16 blocks | 4–24 | below `last_station`'s floor; ≤ 24 keeps it above the next template's top |
| `rise.clearance` | 3 blocks | 1–8 | |
| `encounter.refresh` | 10 ticks | 1–40 | admission |
| `encounter.reset` | 1,200 ticks | 200–6,000 | with no participant |
| `scale.per_player_pm` | 500 | 0–1,000 | entry health per extra participant, per mille |
| `scale.max_players` | 4 | 1–16 | |
| `threat.interval` | 20 ticks | 1–200 | |
| `threat.keep_pm` | 900 | 0–1,000 | |

**Other homes:**
- the Joining bonus: the warp tuning's `joining.amplifier_bonus` (10, 0–50), amended there; Vuulas in its `context.dimensions`;
- spacing, separation, the exclusion and biomes: `content/eldritch/threshold.json`, written into worldgen JSON a datapack can override;
- the fixed structure fields and the salt: code;
- each recipe's essentia and instability: `content/eldritch/recipes.json`; the Void parts' capacity and cost: `wand_parts.json`;
- the dimension's colours and light: `vuulas.json`; every other dimension-type field: code;
- the research entries' warp: research data (#37 transcribes this spec's amounts);
- the Choristers' base health, moves and numbers: the Choristers spec's registry.

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`RoadLimits`** and **`RoadLayout`** in `api`:

| Constant | Value |
|---|---|
| the station floors | 256, 192, 128, 64 |
| the template size and origin, the well, ring, landing, bell, rise cell and box, Reliquary, Heart and arena | as under "The layout" |
| the Road's column | |x|, |z| ≤ 23, 56 ≤ y < 296 |
| `MAX_PARTICIPANTS`, `MAX_ARENA_SCAN` | 16, 64 |
| `MAX_ENTRY_HEALTH` | 1024 (vanilla's maximum-health ceiling) |
| `THREAT_MAX` | 1,000,000 tenths |
| `MAX_ATTEMPTS_PER_PLAYER_PER_TICK` | 1 (Hollow Bell, Step Bells and the Heart together) |
| the ending scene's gap and fallbacks | 20, 100 and 60 ticks |

**Also fixed in code:** the Threshold's salt, `size`, `start_height` and distance; the rows of every first-match table; the catch's tick order; the credit order.

## Performance

**Heavy-base workload,** sustained through warm-up and the 6,000 measured ticks:
- **4 players fight at Step 3** (four admitted, so the full scaling); the fixture raises a fresh encounter whenever one is defeated, so the fight never ends;
- **2 players fight at Step 1** at the same time;
- **1 player descends from a ruin and returns** every 60 s;
- **1 player falls into the well** and is caught every 20 s;
- **1 player keeps the Heart's menu open**, its quotes refreshed by a warp change every 5 s (no commit);
- **2 players stand idle at a Threshold** with its defenders (the worldgen spec's work).

On the client: one player in Step 3's ring, the Chorister and its moves in view, the boss bar, the two lower rings visible down the well.

**Share** (this spec's frame; the Choristers spec carries the creatures): server ≤ **0.04 ms mean, ≤ 0.14 ms p99**; client ≤ **0.02 ms p50**. With the Choristers spec's 0.04 / 0.13 and 0.04, the tier is **0.08 / 0.27** and **0.06**. Shares are acceptance targets, not measurements.

**The envelope's running totals** (planning arithmetic, never a combined percentile): the finished specs' server shares total **0.67 ms mean / 2.52 ms p99** with worldgen; with the Eldritch tier, **0.75 / 2.79**, leaving **0.25 / 0.21** unallocated. Client shares total **0.88 ms p50**, **0.94** with the tier, leaving **0.06**.

**Measured separately as load scenarios** (reported):
- a cold descent into an unloaded Vuulas, and a return into an unloaded overworld chunk;
- 16 players admitted at once, and 70 players crowding one arena (beyond the scan cap), including the index updates as they walk in and out;
- all three encounters active with the maximum tuning (`encounter.refresh` 1, `threat.interval` 1);
- `evacuate` with 64 players in Vuulas;
- **generation:** the first decoration of all 16 of the Road's chunk columns, and the template cache's first load.

**Hot paths:**

| When | Work |
|---|---|
| Per player in Vuulas per tick | `last_station` and the catch: O(1), **zero allocation** (an allocation-budget test) |
| Per Hollow Bell, Step Bell or Heart action | the attempt cap first; O(1) checks; a place query (bounded by the registered queries' own contracts); a teleport: **native** (entity transfer, the target chunk's load or generation, packets), capped by `travel.cooldown` and the attempt cap |
| Per active encounter per tick (≤ 3) | slot checks O(1); threat decay O(participants ≤ 16) on its cadence; boss bar progress |
| Per player in Vuulas per tick (the index) | three O(1) arena-box tests; an index insertion or removal, O(log P) in that arena's players, **only when the answer changes** (an entry node per change, the one allocation); login, logout, death, respawn and level change likewise |
| Per admission refresh | revalidate ≤ 16 participants O(1) each, then ≤ 64 index entries in id order; ≤ 16 admitted; no list built, **zero allocation** (an allocation-budget test) |
| Per Chorister hit | one `LivingDamageEvent.Post` lookup, O(1) threat add, zero allocation |
| Per `targetFor` | O(participants ≤ 16), zero allocation |
| Per defeat | ≤ 16 progress writes, events and keepsake hand-outs (`Inventory.add`, **native**, O(slots), possibly a drop) |
| Per Heart quote | two previews; one packet to one player |
| Per client frame | two block models and their light; the Heart screen when open; **zero allocation in our code** |
| Packets | a quote per warp change while the menu is open; boss bar changes; `EndingCue` once per choice |

**Generation time** runs on worker threads, outside the tick share: only the 16 chunk columns crossing the Road hold content; each gets at most 4 template slices, at most 16 × 40 × 16 = 10,240 block writes each, so at most 40,960 per chunk and 655,360 once per world; every other Vuulas chunk is air with no work. A Threshold is one jigsaw start with a single piece, at most 33 × 44 × 33 template blocks and 3 entity creations.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **every vector** in this spec, replayed from `content/eldritch/fixtures.json`: the placement rows through vanilla's own `RandomSpreadStructurePlacement#getPotentialStructureChunk`; health, admission, targets, decay, the Joining bonus and arrival through ours;
- every row of the descent, Step Bell, rise, Heart-use and `HeartChoose` tables;
- `RoadGeometry` over `RoadLayout`'s constants: arrival, station from a bell's position, the arena predicate on its boundaries, the Road's column, `last_station` at login;
- the catch: below and at `catch.depth`, the order with `last_station`, creative and spectator exempt;
- the capstone predicates over the warp record, including a record holding both;
- codecs: the `vftv:road` record (defaults, bounds, and the [raw-retention template](conventions.md#tests)), `HeartQuote`, `HeartChoose`, `EndingCue`, the tuning file and every `content/` schema and rejection rule;
- **the lenient-stem regression** ("Existing worlds and removing the mod");
- **entry health at the tuning extremes** (`per_player_pm` 1000, `max_players` 16, base 1000: capped at 1024) and the boss bar's fraction against the capped value;
- **the arena candidate index:** insertion and removal only on a change; more than 64 candidates, with eligible incumbents beyond the first 64 kept, and newcomers taken from the first 64 only;
- the allocation budgets of the catch, the index pass without a change, a refresh, the threat add and `targetFor`.

**Datagen-phase:**
- the five templates against "The template" and "The layout" (roles, positions, the well, the ring, the rise box, the choir walk, paths, the Threshold's way down and out); the parsed-NBT no-Hush test over all five;
- the dimension type, biome and stem JSON exactly as specified; the advancements decode, and `vftv:find/choirs_road` matches only its page (another page and a plain `vftv:choir_page` don't).

**GameTest:**
- **the reveal survives stilling:** learn `silence_beneath`, drink Stilling Draughts to lasting 0, open Stage 4 and learn `vftv:hollow_bell`: `vftv:void_wands` is revealed;
- **logout in Vuulas:** a player who logs out at Step 2 is saved at their return point in its level (read back from the saved player data), with the point obstructed saved at the default spawn, and a dead player left alone; a clean server stop leaves every player file's final write at the return point, even though `saveAll()` first wrote it in Vuulas; an autosave of a player in Vuulas followed by a killed process loads them on the Road, and `evacuate` brings them back;
- **the descent:** refused per row (unlearned, ordinary ground, riding, the item's cooldown, the travel cooldown, an obstructed landing) and accepted at a ruin and on hushed ground; a cancelled `EntityTravelToDimensionEvent` writes nothing; a `PlayerChangedDimensionEvent` listener that moves the player elsewhere in Vuulas writes nothing; a throwing listener;
- **the return:** to the point; to the default spawn when the point is obstructed, hazardous, unsupported, **on a magma floor under clear feet and head**, **obstructed in an adjacent, initially unloaded chunk the player's box straddles** (loaded first, then refused), or in a missing level; with an unreadable record (ignoring the travel cooldown); refused by the travel cooldown at `bell.toll` 20 and `travel.cooldown` 1,200; `evacuate` ignoring it;
- **the Step Bells:** every row; a player further down walking up; same-tick double use; **a solo player who passed a Step sprint-uses its bell, wins the rematch and gets a keepsake with progress unchanged**; a sprint-use while an encounter runs; **up and Step 1's return while crouching with an ordinary block, and with the Hollow Bell, in the main hand and a populated off hand** (the block use promoted); an earlier listener that denies the block use keeps it denied; spawn protection refuses;
- **the catch** from every station, and after a relog below a ring;
- **no building:** each forbidden item (every bucket, flint and steel, fire charge, bone meal) by both item paths, block items, a boat and a golem item, an explosion; the bells, Reliquaries and Heart still used with an empty hand and with a block in hand; creative exempt;
- **the Threshold** (in the persistent harness world, as the worldgen spec's natural-generation tests): it generates with its Reliquary, defenders and a walkable way down and out; the Choir-site query answers true there; the exclusion zone keeps it from a ruin;
- **Reliquaries on the Road:** claims in Vuulas; two players claiming in different orders (Step 1 first, and Step 2 first by pearl) get the successive batches; a lost page reissued where it was claimed;
- **encounters:** each rise refusal (unloaded, clearance, an occupied box); a join listener that cancels the Chorister, **moves it less than a block into a wall**, or **moves a player onto the rise** (each discarded, the slot stays `IDLE`); admission order and the cap with 18 players and with 70; leaving by death, logout and dimension change; threat from melee and from an arrow's owner; credit only for those eligible at the death; **a later `LivingDeathEvent` subscriber that cancels the death, and one that heals, pay nothing and the fight goes on**; the actual death pays once; a repeated notification credits once; the last hit's player disconnecting; a throwing keepsake callback (the rest still paid); a full inventory dropping the keepsake; reset by absence, by unload and by a discarded Chorister; peaceful (the Chorister rises, does no damage, can be beaten); the boss bar's membership;
- **the Heart:** every refusal; a stale revision and a stale echo; **Joining once**: the milestone written, the advancement awarded, a second attempt refused; Silencing with `VoiceClear` and no further checks; a `WarpChangedEvent` listener that calls `HeartChoose` again gets `BUSY`; **a crash between the warp write and the award**, then the login reconciliation awarding it without charging; two players choosing differently at the same time; finality after the research spec's `reset`; **a legally retuned Joining amount** (4) quoted and committed as 4; **a warp tuning reload during the confirm** (a fresh quote, the old confirmation refused);
- **the ending scene:** a resource reload mid-scene (the next line resolved anew, a stopped clip counted as finished, nothing replayed, a missing line falling back to text);
- **the capstones:** a Joined Listener's Void magnitude with and without the bonus and with `warp.amplifier` off; Silenced attendance: a pool beside only a Silenced player doesn't spread, beside a Silenced and an unsilenced player does, and with `hush.unattended` does; a Hollow drops a target who becomes Silenced and never acquires one;
- persistence of the record through save and reload, death and dimension change; every tuning boundary, live; every test id in the discovery guard.

**E2E:**
- **the whole road on one client:** walk to a Threshold, down its stair, claim the page; tip it in; learn and craft the Hollow Bell; descend from a ruin; claim at Step 1; fight and pass Step 1; down to Step 3 and the Heart (with commands to set the later Steps); the Heart screen by mouse and **by keyboard only**, with a long test translation; confirm Joining; the three lines with their text and the drain; the Ledger and the capstone page (screenshots and dumps at each step);
- **two clients:** a party fight with both credited, then one Joins and one Silences;
- the Heart screen held behind another screen, then played;
- sync on join, reconnect, dimension change and `/reload`;
- the asset, lang, model and sound validators;
- **the uninstall regression** at release checkpoints: a world with progress and a player who logged out in Vuulas; reopened without VFTV and saved; then reopened with VFTV. It checks the seed and the vanilla dimensions unchanged, the player at their return point (the logout returned them), the VFTV attachments gone, and Vuulas restored from its region files. If the harness can't launch without our jar, it is run by hand on the reference machine and reported.

## Contracts on sibling specs

| Needs from | What | Met at |
|---|---|---|
| Choristers | every obligation under "The split": peaceful, `noSave()`, persistence, knockback resistance, no heal, block change, summon or randomness, no invulnerable phase; difficulty-scaled, telegraphed damage types outside the bypass and Baffle-ignore tags; `targetFor` only, and the leash; the rise box and entry health; `onConfirmedDeath`; its share | [Choristers, The entity type](choristers.md#the-entity-type), [Attributes](choristers.md#attributes), [Created, owned and reported](choristers.md#created-owned-and-reported), [Damage types and difficulty](choristers.md#damage-types-and-difficulty), [Fairness limits](choristers.md#fairness-limits), [Who can hurt it](choristers.md#who-can-hurt-it), [Each tick, in order](choristers.md#each-tick-in-order), [Performance](choristers.md#performance) |
| warp | `context.dimensions` defaulting to `vftv:vuulas`; `registerPlaceQuery`, `inVoidTouchedPlace` and `isSilenced` | [warp, Context](warp.md#context), [Tuning and limits](warp.md#tuning-and-limits), [Joining and Silencing](warp.md#joining-and-silencing) |
| warp | the Joining bonus on `amplifier_warp`, with its fixtures section; `vftv:joining` required in `permanent.milestones`; the dials reading `milestones_paid` | [warp, The dials](warp.md#the-dials), [Permanent warp](warp.md#permanent-warp), [Fixtures](warp.md#fixtures) |
| warp | `silence(player, expected)`, checked, returning `OK`, `STALE`, `BUSY` or `UNREADABLE` | [warp, Joining and Silencing](warp.md#joining-and-silencing) |
| Hush | spread attendance excluding Silenced players, the Pealing Bell still counting them; Hollows never targeting a Silenced player | [Hush, Where it begins](hush.md#where-it-begins), [The creature](hush.md#the-creature) |
| infusion | `kind: void` bodies with their own `research` | [infusion, The recipe type](infusion.md#the-recipe-type) |
| research | the seven entries under "Stage"; `vftv:recipe` pages on `vftv:hollow_bell` and `vftv:void_wands` | [research, Roster](research.md#roster), [Entry files](research.md#entry-files) (the `vftv:void_wands` pages are not stated) |
| research | the `road` fact source; the `warp_rules` key `joining.amplifier_bonus` | [research, Prose and fact references](research.md#prose-and-fact-references) |
| research | `path-stage3.json` ending on `vftv:find/choirs_road`; `path-stage4.json` using the Threshold page and the first two Road pages; pages 3–5 never sources; #36's hint; #37 freezing the Road pages' order | [research, Affordability paths](research.md#affordability-paths), [Production tickets](research.md#production-tickets) |
| research | #37's prose teaching the descent, the Steps and their bells, the catch, no building, the encounters and both endings, every number in a span | [research, Production tickets](research.md#production-tickets) (the endings and the Chorister lessons only) |
| worldgen | `vftv:threshold` in `#vftv:choir_sites`; family-scoped `vftv:template/1` rules; the built-in site checks on `vftv:choir_ruin` only | [worldgen, The structure](worldgen.md#the-structure), [Authored content, Templates](worldgen.md#authored-content), [Site kinds](worldgen.md#site-kinds) |
| wands | the Void rod and cap among the later tiers; the `items/wand.json` append order | [wands, Parts](wands.md#parts), [Components](wands.md#components) |
| foci | `w` as the warp spec's `amplifier_warp`, with the Joined magnitude rows | [foci, Magnitude](foci.md#magnitude) |
| API | `RoadLayout`, `RoadLimits`, `EldritchKeys`, the payload records and `RoadRules` in `api`; the three events in `api.neoforge`; the `vftv:road` `AttachmentType` not public | [API, The inventory](api.md#the-inventory), [Attachments and data components](api.md#attachments-and-data-components) |
| [vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) | hand entries or exemptions for the Hollow Bell, the Void rod and cap, the keepsake, the Step Bell, the Heart and the Chorister; round-trip non-amplification over the three infusion bodies | not yet stated in any spec |

## Production tickets

This spec settles every decision for its own content. Five required child tickets deliver the assets and the authored content, and **the system is not done until all five land and the Choristers of the Road spec is resolved.** **None of them writes mod code:** no block, item, entity, generator, menu, screen, payload, attachment, listener, renderer, datagen provider or validator. The asset tickets deliver generators and assets under the [art style](art-style.md); the content tickets deliver authored inputs under `content/`, in the schemas above. The advancements, tags written from code, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Road and Threshold templates** | **Generator:** `tools/art/road.py`, seeded and byte-identical on re-run, writing `content/eldritch/templates/threshold.json`, `step_1.json`, `step_2.json`, `step_3.json` and `heart.json` in `vftv:template/1` at the sizes, roles, positions and paths under "The template" and "The layout": the Threshold's ring of broken bell frames (one reaching 16 above the surface), its carved shaft and stair with headroom, its chancel with the Reliquary and three defenders; each ring around its open well with the landing, the Step Bell, the rise cell and its clear box, the clear choir walk at Steps 1–3, the Reliquary, and the Heart at the bottom. Stage 4 masonry (tuff family, calcite, deepslate tiles, waxed copper, soul lanterns), never a hushed block or a Hush source on the Road. **Review:** an isometric preview of each template with its standing cells, entry cells, paths, rise box and choir walk marked; Blender renders of the stacked Road from Step 1 looking down the well and from the Heart looking up, and of the Threshold at dusk. Provenance rows. | none |
| **Eldritch assets** | **Generator:** `tools/art/eldritch.py`. **Items** (16 × 16, Stage 4 row): `hollow_bell`, `void_rod`, `void_cap`, `keepsake`, with item models (parent `minecraft:item/generated`) and `assets/vftv/items/<id>.json`. **Wand layers:** `assets/vftv/textures/item/wand/rod/void.png` and `…/cap/void.png` with their layer models, and the **appended** `assets/vftv/items/wand.json` (a `vftv:void` case in each select, every existing case, fallback and the foci member kept). **Blocks:** `step_bell` and `heart` models exactly as under "Models", textures, blockstates (the Step Bell's four facings) and item definitions. **GUI:** `assets/vftv/textures/gui/heart.png` (the 248 × 166 screen with its two 116 × 120 panels); buttons, the confirm panel and scrollbars reuse the research spec's sprites. **Sounds:** every file and `sounds.json` entry under "Sound", naming its subtitle key, no overrides. **Review:** a contact sheet at GUI scale 2 beside a vanilla bell, blaze rod and lodestone, with the five Void-part wand combinations; Blender renders of both blocks; **the Heart screen mockup** at 320 × 240 from the sprites and the vanilla font, at the layout and focus order under "The block and the screen": both panels with a clipped Joining preview, the confirm over each, a long test translation scrolling. Provenance rows. | [Foci assets #49](https://github.com/andrewbrogoff/voices-from-the-void/issues/49) (`items/wand.json` order) |
| **Eldritch content** | `content/eldritch/threshold.json` (as shipped, the ruin's biome lists transcribed), `sites.json` (#37's five Road pages transcribed), `vuulas.json` (as shipped), `recipes.json` (the three bodies within "Recipes"' rules), `wand_parts.json` (as shipped), `bootstrap.json` (every `why` reviewed), `tags.json` (as shipped), `fixtures.json` (the file above), `content/lang/en_us/eldritch.json` (every key under "Lang keys"), and the lexicon's four `uses` additions in `content/lexicon/void_tongue.json`. Its review checks the bootstrap, the names against the lexicon, and that the Heart's lines state every consequence and the Silencing scope. | [Stage 4 research content #37](https://github.com/andrewbrogoff/voices-from-the-void/issues/37), only for its frozen page order; [#30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (the non-amplification lint replays its values) |
| **Chorister assets** | the Choristers spec's generator, editable model, three textures, mark atlas, poses (as review renders) and sounds, as [that spec's "Production tickets"](choristers.md#production-tickets) freezes them | the **[Choristers of the Road spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/69)** |
| **Chorister content** | `content/eldritch/choristers.json` (the three instances and four damage types), `chorister_fixtures.json` and `content/lang/en_us/choristers.json`, in that spec's schemas | the **[Choristers of the Road spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/69)** |

**Handed off, not children of ours:** [#36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) and [#37](https://github.com/andrewbrogoff/voices-from-the-void/issues/37) consume the entries, amounts, sources, hint and fact keys frozen here; [#30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) gives the new items, blocks and the Chorister their values; the [Voice line script #57](https://github.com/andrewbrogoff/voices-from-the-void/issues/57) and [recordings #58](https://github.com/andrewbrogoff/voices-from-the-void/issues/58) already hold the six ending lines, so nothing new is recorded.
