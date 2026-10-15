# Worldgen and mobs

What the world holds for a Listener to find in Stages 1–3. This covers:
- the **finds** that open Stages 2 and 3, and the find for the Answerers;
- **Choir ruins**: the structure, its placement, its templates and how they are made, its processors, its one node and its loot;
- the **Reliquary**, which gives each Listener their own Choir pages;
- **the Answerers**: the Answerer and the Cantor;
- the Choir site as a Void-touched place for warp;
- what v1 leaves out: ambient magical mobs and an optional biome.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the data and sync rules and the seams;
- the [quality gates](quality-gates.md) for every gate;
- the [lore](lore.md) and [progression](progression.md) specs for the Choir, the Answerers and the Stage rule;
- the [research spec](research.md) for Stage openers, Choir pages, affordability paths, `mix64` and `fold`;
- the [wands spec](wands.md) for the node block, node state, minting and the natural spread's stream;
- the [warp spec](warp.md) for the place query and its danger rules;
- the [Hush spec](hush.md) for the player-caused guarantee;
- the [art style](art-style.md) for every asset.

Resolved in [Worldgen and mobs spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/27). Inputs: [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md) (§5 Worldgen, §6 Mobs, §7 the obelisk and the cult), [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md) (nodes), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md) (§9, gating by finds; the hard-to-find cult portals), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md) (§5 Worldgen).

**Every number in this spec is ours.** None is taken from TC4. TC4's obelisk platform and capstones, its cult's stats and drop rates, its node-type frequencies and its Wisp and Pech numbers are not reused, and nothing here is a TC4 figure rescaled. Each tunable number is listed with its data home under "Tuning and limits". Numbers inside JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

**Renamed coinages** ([lore, Vocabulary policy](lore.md#vocabulary-policy)):
- Crimson Cult → **the Answerers**. Its Knight → the **Answerer**. The **Cantor** is a new role, not a renamed Cleric.
- Knowledge fragments and the Crimson Rites → **Choir pages** (the research spec's).
- The Eldritch obelisk → nothing in Stages 1–3 ([Eldritch tier](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) owns Stage 4).
- Wisps, Pech and Firebats → nothing ("Not in v1").

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port that uses `java.util.Random` for vanilla's LCG. Both print the same 13 lines (807 bytes, SHA-256 `69eedf5d6f96a119efd3a81f3be857a626520eec81d443a80f0ee7b9787bc37d`). The references cover arithmetic only: placement draws and node rolls. Their agreement says nothing about terrain, biomes, jigsaw assembly, callbacks or performance, which the build's tests prove (see "Tests").

**Source citations:** `MC:` is `minecraft-patched-26.2.0.88-sources.jar` and `NF:` the NeoForge 26.2.0.88 sources, with package prefixes omitted.

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 1 Resonance | nothing | `vftv:find/resonance`, the [research spec's](research.md#stage): a shard from a vanilla amethyst geode, or a Tuning Fork |
| 2 Distillation | the find `vftv:find/distillation` | dripstone ("Stage 2: dripstone") |
| 3 Ruins | Choir ruins, the Reliquary, the Answerers, structure nodes, the finds `vftv:find/ruins` and `vftv:find/answerers` | a Choir page in hand ("Stage 3: a Choir page") |

- **Nodes: #17 alone owns natural placement.** The [wands spec's natural spread](wands.md#natural-spread), its feature `vftv:node` and its biome modifier `vftv:add_nodes` are untouched. This spec places **no natural node** and never edits or removes that modifier. It owns **structure nodes** only: one position in each ruin's start piece ("Structure nodes"). That uses one of the two structure places the wands spec allows per 64×64 cell and leaves the other for #28.
- **Gates are per player.** Every find is something a player obtains or does. A ruin looted, mined or cleared by someone else takes no find away from anyone.
- **Options considered:** owning node placement wholesale. **Why not:** the wands spec already pins the natural spread's seed, draws and vectors, so a second owner would mean two sources of truth for one feature.

## Finds

### Stage 1: the geode

**No change, and nothing added.** The research spec owns `vftv:find/resonance` (`inventory_changed`: an amethyst shard or a Tuning Fork). Geodes are in every overworld biome (`MC:data/worldgen/biome/OverworldBiomes.java:48–55`, `MC:data/worldgen/BiomeDefaultFeatures.java:429–431`), so this spec adds no feature, tag or structure for Stage 1. The first-hour chain is the research spec's and the wands spec's; this spec makes no new time-to-geode promise.

### Stage 2: dripstone

**`vftv:find/distillation`:** `inventory_changed` with `minecraft:pointed_dripstone` or `minecraft:dripstone_block`.

- **Why:**
  - Dripstone is stone that distils water drop by drop: the world's own still.
  - It mirrors Stage 1's geode ("vanilla amethyst already chimes"), so natural philosophy keeps its register until the Choir appears in Stage 3.
  - Obtaining it is the find, as with the shard. So it is per player, can't be griefed, and works with only NeoForge installed.
- **Where:** pointed dripstone generates in dripstone caves (`MC:BiomeDefaultFeatures.java:41–45`, `OverworldBiomes.java:927`). The book teaches **exploring caves** for it. Wandering traders also sell it in their common pool (`MC:data/minecraft/tags/villager_trade/wandering_trader/common.json:69`). That is an alternative, not a promised wait or a guaranteed trade.
- **The affordability source** for the end of Stage 1's path is under "Contracts on sibling specs".
- **Options considered:**

  | Option | Why not |
  |---|---|
  | a village bell, rung | villages are absent from whole biome families |
  | a small Stage 2 structure of our own | a template, a block and a per-player claim mechanism, two Stages before the Choir appears |
  | striking a clear natural node | not guaranteed: the wands spec guarantees only the starter, which is always clear and is Stage 1 |
  | a cauldron | crafted, so not a find |

### Stage 3: a Choir page

**`vftv:find/ruins`:** `inventory_changed` with `vftv:choir_page`.

- Pages come only from Reliquaries ("The Reliquary") or from another player's hand ("obtaining counts as finding", [research, Stage](research.md#stage)). The find is earned at a ruin's heart, past its Answerers. It can't be griefed, and it needs no polling, no structure lookup and no location trigger.
- **Acquisition and tip-in are separate.** Holding a page latches the Stage 3 opener's find, which can happen in Stage 2. Tipping a page in is the research spec's act and needs Stage 3 ([research, Choir pages](research.md#choir-pages)). So Stage 2's path ends by acquiring a page, and Stage 3's path tips it in ("Contracts on sibling specs").
- **Options considered:** entering a ruin through vanilla's `location` predicate with `structures`. **Why not:** it calls `StructureManager.getStructureWithPieceAt` (`MC:advancements/predicates/LocationPredicate.java:56`) every 20 ticks per player (`MC:server/level/ServerPlayer.java:758–759`), the non-loaded-only path the [warp spec](warp.md#context) rejects. A code criterion polling "The Choir-site place query" was also considered. **Why not:** it is code and polling, for a find the page already gives.

### The Answerers' find

**`vftv:find/answerers`:** `player_killed_entity` whose entity is in `#vftv:answerers`. This is the research roster's "advancement for killing one".

- **It is not a guaranteed source.** Peaceful prevents Answerers ("Placement and persistence"), and a ruin's defenders are finite.
- **So its research entry is a leaf:** no entry lists it in `parents` or `hidden_parents`, and no affordability path `use`s it (the research spec amended).

### The advancements

Fixed by code in datagen (an `AdvancementProvider`); not authored content. All three have **no `display`** (hidden, with no toast and no advancement-screen entry), no parent and no rewards.

```json
{"criteria": {"dripstone": {"trigger": "minecraft:inventory_changed",
  "conditions": {"items": [{"items": ["minecraft:pointed_dripstone", "minecraft:dripstone_block"]}]}}}}
```
is `vftv:find/distillation`;

```json
{"criteria": {"page": {"trigger": "minecraft:inventory_changed",
  "conditions": {"items": [{"items": "vftv:choir_page"}]}}}}
```
is `vftv:find/ruins`; and

```json
{"criteria": {"killed": {"trigger": "minecraft:player_killed_entity",
  "conditions": {"entity": [{"condition": "minecraft:entity_properties", "entity": "this",
                             "predicate": {"minecraft:entity_type": "#vftv:answerers"}}]}}}}
```
is `vftv:find/answerers`.

- **The entity predicate** dispatches on registered sub-predicate ids (`MC:advancements/predicates/entity/EntityPredicate.java:38–43`), and the type test's id is **`minecraft:entity_type`** (`EntitySubPredicates.java:8`). `KilledTrigger` accepts the condition-list envelope (`MC:advancements/triggers/KilledTrigger.java:27–35`). Trigger ids: `MC:advancements/triggers/CriteriaTriggers.java:10,13`.
- **What re-fires `inventory_changed`:** with one item predicate, vanilla tests **only the changed stack** (`MC:advancements/triggers/InventoryChangeTrigger.java:84–110`). Acquiring or moving a qualifying stack fires it; changing an unrelated slot doesn't. After the research spec's `reset` revokes an advancement, it relatches on the next change to a qualifying stack. A held stack doesn't re-fire by itself.
- **No infusion craft criterion:** every criterion is `inventory_changed` or `player_killed_entity`, asserted by a datagen test ([infusion spec, Contracts](infusion.md#contracts-on-sibling-specs)).
- **No opener cycle:**
  - Structurally: no advancement has a parent, and every criterion names an ungated vanilla trigger over an ungated item or entity.
  - Behaviourally: the research spec's affordability replay acquires each find from its published source in the Stage before ("Contracts on sibling specs") and must end with the next opener latched.

## Choir ruins

### The structure

**A vanilla `minecraft:jigsaw` structure, `vftv:choir_ruin`.** There is no custom structure type, no structure-piece code and no code in assembly.

| Field | Value | Home |
|---|---|---|
| `start_pool` | `vftv:choir_ruin/chancel` | fixed |
| `size` | 2: the chancel, wings at depth 1, ends at depth 2 | fixed |
| `max_distance_from_center` | 40 (40 + 12 for terrain adaptation ≤ 128, `MC:levelgen/structure/structures/JigsawStructure.java`, `verifyRange`) | fixed |
| `start_height` | absolute 0, `project_start_to_heightmap` `WORLD_SURFACE_WG` | fixed |
| `use_expansion_hack` | false | fixed |
| `step` | `surface_structures` | fixed |
| `terrain_adaptation` | `beard_thin` | fixed |
| `spawn_overrides` | `{}` | fixed |
| `biomes` | `#vftv:has_structure/choir_ruin` | content (`choir_ruin.json`) |

**Fixed** here means written by datagen from code and part of this spec's invariants (the topology, the piece count and the place query's extent depend on them). A datapack may override the generated files. The bounds below are promised only for the shipped values.

**Tags:**
- `#vftv:choir_sites`: a structure tag holding `vftv:choir_ruin` and the [Eldritch tier's](eldritch.md#the-threshold) `vftv:threshold` (its datagen writes that member). The query's eight-structure bound and expanded-column semantics apply to both unchanged.
- `#vftv:has_structure/choir_ruin`: a biome tag, generated from content.

### Placement

**The structure set `vftv:choir_ruin`:** `minecraft:random_spread`, `spacing` **36**, `separation` **14**, `spread_type` `linear`, `salt` **1128812370** (`0x43484F52`, "CHOR"), `frequency` 1.0, no exclusion zone.

- **Vanilla's algorithm** (`MC:levelgen/structure/placement/RandomSpreadStructurePlacement.java:62–71`, `MC:levelgen/WorldgenRandom.java:66–69`, `LegacyRandomSource.java`, `BitRandomSource.java:17–34`): region `(rx, rz) = (floorDiv(cx, 36), floorDiv(cz, 36))`. The seed is `rx·341873128712 + rz·132897987541 + worldSeed + salt` (wrapping `long`). Two `nextInt(22)` draws give the offsets in x then z, and the start is `(36·rx + ox, 36·rz + oz)`. A start is kept only if the start's surface biome is in the tag.
- **Separation by construction:** starts of distinct regions are at least `separation + 1` = **15** chunks apart by Chebyshev distance. The reference's minimum over 6,400 regions is 15. So ruins never overlap.
- **The salt is fixed in content and never tuned:** changing it moves every ruin in unexplored land. `spacing` and `separation` are content (`choir_ruin.json`); the separation proofs below hold for the shipped values.
- **Rarity:** one start attempt per 576×576 blocks, kept on land only. This is a hypothesis, measured by the pregeneration scenario under "Performance", not a promise. `/locate structure vftv:choir_ruin` works natively.
- **Findability** (TC4's cult portals were hard to find, TE §7): every chancel has a broken bell tower whose top is at least 20 blocks above its floor, visible over the treeline.

**Vectors** (world seed `0x0123456789ABCDEF`; region → start chunk):

| Region | Start chunk |
|---|---|
| (0, 0) | (12, 6) |
| (−1, 0) | (−16, 17) |
| (0, −1) | (4, −24) |
| (3, −7) | (127, −247) |
| (−12345, 6789) | (−444402, 244420) |
| (52000, −52000) | (1872002, −1871988) |

A unit test runs vanilla's own `getPotentialStructureChunk` against these rows, so a vanilla change to the algorithm is caught.

### Pieces and topology

Three pools, every one with `fallback` `minecraft:empty`. Every element is a `minecraft:single_pool_element` with `location` `vftv:choir_ruin/<name>`, processor list `vftv:choir_ruin` and `projection` `rigid`, except the one `minecraft:empty_pool_element` in `ends`.

| Pool | Elements (shipped weights) | Template size (x × y × z) | Connectors |
|---|---|---|---|
| `vftv:choir_ruin/chancel` | `chancel_a` 1, `chancel_b` 1 | exactly 23 × ≤ 30 × 23 | 4 `wing_socket`s, one at the centre of each side on the floor layer: (11, 1, 0) facing north, (22, 1, 11) east, (11, 1, 22) south, (0, 1, 11) west |
| `vftv:choir_ruin/wings` | `cloister` 1, `bell_pit` 1, `scriptorium` 1, `fallen_nave` 1 | exactly 17 × ≤ 14 × 17 | 1 `wing_plug` at (8, 1, 0) facing north; 0–2 `end_socket`s at (0, 1, 8) facing west and (16, 1, 8) facing east |
| `vftv:choir_ruin/ends` | `rubble_a` 2, `rubble_b` 2, `minecraft:empty_pool_element` 1 | exactly 9 × ≤ 6 × 9 | 1 `end_plug` at (4, 1, 0) facing north |

**Jigsaw fields** (the block entity's keys, `MC:world/level/block/entity/JigsawBlockEntity.java:30–37,109–127`):

| Connector | `name` | `target` | `pool` | `final_state` | `joint` |
|---|---|---|---|---|---|
| `wing_socket` | `vftv:choir_ruin/wing_socket` | `vftv:choir_ruin/wing_plug` | `vftv:choir_ruin/wings` | `minecraft:air` | `aligned` |
| `wing_plug` | `vftv:choir_ruin/wing_plug` | `minecraft:empty` | `minecraft:empty` | `minecraft:air` | `aligned` |
| `end_socket` | `vftv:choir_ruin/end_socket` | `vftv:choir_ruin/end_plug` | `vftv:choir_ruin/ends` | `minecraft:air` | `aligned` |
| `end_plug` | `vftv:choir_ruin/end_plug` | `minecraft:empty` | `minecraft:empty` | `minecraft:air` | `aligned` |

Every connector block is `minecraft:jigsaw[orientation=<facing>_up]`, `placement_priority` and `selection_priority` 0.

**Bounds:**
- A ruin has at most 1 chancel + 4 wings + 8 ends = **13 pieces**.
- Its **piece envelope** lies within 40 blocks of the start horizontally, so it is ≤ 81 × 81.
- `beard_thin` grows the structure's reference box by 12 on every side (`MC:levelgen/structure/Structure.java:81–82`, `StructureStart.java:69–76`), so the terrain-expanded box is ≤ 105 × 105.
- The jigsaw's collision checks may reject a candidate, and its fallback is then `minecraft:empty`.

**Role counts per template** (validated; "Authored content"):

| Role | Reliquary | Node | Answerers | Cantors | Chests | Connectors |
|---|---|---|---|---|---|---|
| chancel | exactly 1 | exactly 1 | exactly 1 | exactly 1 | 0 | exactly 4 `wing_socket` |
| wing | 0 | 0 | 0–2 | 0 | 0–1 | exactly 1 `wing_plug`, 0–2 `end_socket` |
| end | 0 | 0 | 0 | 0 | 0 | exactly 1 `end_plug` |

So a ruin holds 1 Reliquary, at most 1 node, 2–10 Answerers (1 of them a Cantor) and at most 4 chests.

**Footing and paths** (validated per template by a flood fill over its own cells):
- A **standing cell** has a block with a sturdy top face below it and air in it and the cell above.
- Every entity's `pos` is a standing cell, and its feet are placed at that cell's bottom centre (`x + 0.5, y, z + 0.5`).
- In a chancel, the cell in front of the Reliquary's face is a standing cell, reachable on foot from the inward neighbour of every `wing_socket`.
- In a wing, every entity's cell is reachable from the `wing_plug`'s inward neighbour.
- "On foot" means 4-connected standing cells, rising or falling at most 1 block per step.
- The template alone can't prove the assembled ruin walkable over real terrain; the E2E walkthrough checks that.

### Templates

**Code-authored, like every other asset** ([art style, Pipeline](art-style.md#pipeline)). A seeded Python generator writes **text templates** under `content/` (CC-BY-4.0 data). The build's datagen converts them into `data/vftv/structure/choir_ruin/<name>.nbt` under `src/generated/resources`, through vanilla's `StructureTemplate` and `NbtIo`, deterministically. The generator, the text templates and the review renders are production deliverables. The converter and every validator are build-phase.

- **The look:** vanilla blocks in the `mauve` Choir-masonry and slate register: tuff, tuff bricks, chiseled tuff, calcite and deepslate tiles. Brass accents from waxed cut copper. Vanilla bells. The only `vftv:` blocks are `vftv:reliquary` and `vftv:node`. There is nothing in the `hush` ramp ([art style §§2, 5](art-style.md#5-tone-ramp-by-stage), Stage 3 row).
- **Template entities** carry only `{id}` and their position. `StructureTemplate` supplies `Pos`, removes `UUID`, loads the entity and finalizes it (`MC:levelgen/structure/templatesystem/StructureTemplate.java:500–534`), and the entity type's defaults supply the rest, full health included (`MC:world/entity/EntityType.java:335–350`, `Entity.java:2151–2196`, `LivingEntity.java:836`).
- **Options considered:**

  | Option | Why not |
  |---|---|
  | building in a test world with structure blocks, committing the `.nbt` | binary in git, no review by diff, not reproducible, a human or a harness client in the loop, and palettes that can silently carry stray blocks (what the [Hush spec's contract](hush.md#contracts-on-sibling-specs) guards against) |
  | writing `.nbt` directly from Python | generated resources come from datagen ([architecture, Datagen](architecture.md#datagen)), and binary inputs can't be reviewed |

**The no-Hush check** ([Hush spec, The player-caused guarantee](hush.md#the-player-caused-guarantee)) is a datagen-phase test that **parses** (gunzips and decodes) every generated `.nbt`. For every palette state, block-entity tag and entity tag it checks:
- no block in `#vftv:hushed`;
- no node `type` `vftv:hushed`;
- no entity `vftv:hollow`;
- no key `neoforge:attachments` anywhere, so no aura can ride in.

It runs the same rules over every generated worldgen, structure, processor and loot JSON, and every loot entry is checked to yield no hushed block item and no `vftv:hush_ash`. It covers **every** generated template under `data/*/structure/`, so #28's templates are checked by the same test.

### Processors

**The processor list `vftv:choir_ruin`**, in this order, after the two that every pool element adds itself (`block_ignore` for structure blocks and `jigsaw_replacement`, `MC:levelgen/structure/pools/SinglePoolElement.java:164–178`):

1. **`minecraft:protected_blocks`** with `#minecraft:features_cannot_replace`. A template block whose target already holds a block in that set is dropped (`MC:levelgen/structure/templatesystem/ProtectedBlockProcessor.java:13–28`). Node blocks and the Reliquary carry the tag ([wands, The node block](wands.md#the-node-block)), so no template overwrites an existing node.
2. **Weathering** (optional): at most `MAX_WEATHERING_RULES` (8) `minecraft:rule` rules, each with a `random_block_match` input (one block, a probability), `always_true` location and an output block's default state.
   - Input and output are both drawn from **`#vftv:choir_weathering`**, a fixed list of vanilla masonry: tuff, tuff bricks, chiseled tuff, polished tuff, calcite, deepslate tiles, cracked deepslate tiles, cobbled deepslate, and waxed cut copper with its weathered forms.
   - So weathering never matches or produces `vftv:node`, `vftv:reliquary`, a jigsaw, a container or air.
   - No `block_rot` or `block_age` processor is used, since either could delete a node.
3. **`vftv:roll_node`** (a code registry processor, build-phase). For a template block that is `vftv:node`:
   - if the **target position is not air** (`isAir()`, as the wands spec's every placement path requires), the node is **dropped**: the template places nothing there, and the terrain stays;
   - otherwise it rolls ("The roll") and **replaces only the clarity and each aspect's `max` and `current`** (current = max) in the template's record, keeping every other field.

   Every other block passes through unchanged.

**The saved template record.** The datagen converter expands a template's `{"node": {"type": …}}` shorthand into a **complete, valid** record of the [wands spec's Node state](wands.md#node-state) before writing the NBT. So every template saves, as the wands spec requires, id 0 and `origin: structure`, whether or not a processor ever runs. The fields:
- `format` 1, `id` 0, `origin` `structure`, `type` as authored;
- `clarity` `steady`;
- `aspects` in ascending id, each with `max` and `current` at its range minimum;
- `regen` `{tick 0, interval 1, divisor 1}`. This placeholder lies within the format bounds and is replaced when the id is minted, which stamps the clock ([wands, Identity and reconciliation](wands.md#identity-and-reconciliation));
- `cultivation` `{counters {}, sonus 0}`.

For the shipped type, every chancel's node NBT is exactly:

```
{format: 1, id: 0L, origin: "structure", type: "vftv:choir", clarity: "steady",
 aspects: [{aspect: "vftv:aer", max: 16, current: 16}, {aspect: "vftv:ordo", max: 24, current: 24},
           {aspect: "vftv:sonus", max: 4, current: 4}, {aspect: "vftv:vacuos", max: 4, current: 4}],
 regen: {tick: 0L, interval: 1, divisor: 1}, cultivation: {counters: {}, sonus: 0}}
```

A successful roll replaces the clarity and the maxes and currents. **Every fallback below keeps this complete record**, so a node placed without a roll is a valid, steady node at its minima.

**The roll.** It is pure and keyed by position, and it touches no level state on worldgen threads.

```
h = fold([0x43484F52 ("CHOR"), worldSeed, BlockPos.asLong(target)])        // the research spec's fold and mix64
stream: s = h;  next() = { s = s + 0x9E3779B97F4A7C15L; return mix64(s) }   // the wands spec's stream
clarity = weighted(next(), [faint, steady, clear])                          // the wands spec's weighted
for each aspect of the type, ascending id:  max = bounded(next(), min, max) // the wands spec's bounded
```

- **`worldSeed`** comes from `WorldGenLevel#getSeed()` (`MC:world/level/WorldGenLevel.java`). During generation the level is a `WorldGenRegion`; under `/place` it is the `ServerLevel`, and both implement it. A level that doesn't is never seen in practice. It keeps the complete template record unrolled, logged once.
- **A missing type** (removed by a pack): the complete template record is kept unrolled, logged once, and the node is dormant under the wands spec's pack rules.
- **`BlockPos.asLong`** packs x in 26 bits at offset 38, z in 26 bits at offset 12 and y in 12 bits at offset 0 (`MC:core/BlockPos.java:48–55,110–114`).
- **Draws:** one clarity draw and one per aspect, so at most 7 for the 6-aspect maximum.

**Vectors** (seed `0x0123456789ABCDEF`, type `vftv:choir` as shipped):

| Position | `asLong` | `h` | Clarity | Aer | Ordo | Sonus | Vacuos |
|---|---|---|---|---|---|---|---|
| (0, 64, 0) | `0x0000000000000040` | `0x7F227C6F5C24323D` | steady | 30 | 37 | 11 | 6 |
| (−1, 70, −1) | `0xFFFFFFFFFFFFF046` | `0x9338065CEBA0BF6E` | steady | 29 | 41 | 8 | 8 |
| (1000, −20, −3000) | `0x0000FA3FFF448FEC` | `0xDDBFDF7BEE832E0D` | steady | 23 | 45 | 10 | 5 |
| (−12345, 101, 6789) | `0xFFF3F1C001A85065` | `0x810864C8E931A0C4` | clear | 22 | 32 | 8 | 9 |
| (29999999, 319, −29999999) | `0x7270DFE363C8113F` | `0x70536F2D4B1B14FE` | steady | 25 | 26 | 4 | 12 |

### Structure nodes

**The type `vftv:choir`:**
- `aspects`: Aer [16, 32], Ordo [24, 48], Sonus [4, 12], Vacuos [4, 12]. Sonus and Vacuos pay first-strike points only ([wands, Node types](wands.md#node-types)).
- `main_aspect` Ordo.
- `clarity` faint 0, steady 60, clear 40.
- `natural: false`, **not anchored**. A strong node is a ruin's reward, and a Bell Jar can carry it home.
- Named "Choir".

Its only declaration is in this spec's `content/worldgen/node_types.json`, and its name only in `content/lang/en_us/worldgen.json`. The [wands content](wands.md#authored-content) excludes both, and datagen checks the required types and names after merging every file, rejecting a duplicate.

- **One authored node per chancel, at most one placed.** It sits in the bell tower's open belfry, at least 8 blocks above the floor layer, where terrain rarely reaches. "Processors" drops it when its target isn't air.
- **The wands spec's quota holds for ruins by construction:**
  - The start piece is anchored at the start chunk's minimum x and z (`MC:JigsawStructure.java:141–145`; `MC:levelgen/structure/pools/JigsawPlacement.java:78–114` keeps that horizontal origin with no `start_jigsaw_name`, rotates the piece and adjusts y).
  - A 23-wide chancel keeps its node within 23 blocks of that corner on each axis.
  - Starts are ≥ 15 chunks apart, and 15 × 16 − 2 × 23 = 194 > 64, so two ruins' nodes never share a 64×64 cell.
  - Any surplus, for example from #28's structures in the same cell, is removed by the wands spec's minting check with a warning.
- **Ids** are minted only by the wands spec's reconciliation, from `origin: structure`.
- **Not an affordability source:** the wands spec's guarantees file holds only `vftv:starter`.

### Loot

- **Chests:** `vftv:chests/choir_ruin`, at most one per wing, set as the chest's `LootTable` (`MC:world/RandomizableContainer.java:22–23`; vanilla seeds it at placement). Vanilla items only: paper, books, candles, amethyst shards, copper ingots, gold nuggets and a rare bell. There is **no progression item**: no `vftv:choir_page` (the Reliquary alone gives pages, per player), no Tuning Fork and nothing research-gated.
- **Answerers:** `vftv:entities/answerer` and `vftv:entities/answerer_cantor`, vanilla items only (paper, candles, and a book for the Cantor), with Looting.
- **No new drop item in v1:** no sibling recipe asks for one.
- **First-come is fine.** Chests are vanilla, shared and first-come, and nothing in them gates progression.

### Existing worlds and removal

- **Added to an old world:** ruins generate only in chunks generated after that. Explored land has none, so players travel; `/locate` finds the nearest. The Stage 3 hint says the ruins lie in unexplored land (#35's prose).
- **Removed:**
  - Our blocks and entities fall to vanilla's and NeoForge's missing-registry handling.
  - A chunk reference to the unknown structure is discarded with a **warning**; an unknown structure start logs an **error** (`MC:world/level/chunk/storage/SerializableChunkData.java:609–631`).
  - Ruins are left as vanilla masonry.
  - This is how registry loss behaves, not a tested migration promise.
- **No dimension is added here.** The [progression spec's](progression.md#endgame) stale-dimension note is #28's.

## The Reliquary

### The block

**`vftv:reliquary`**, one per chancel, is a block with a block entity.

- Survival-unbreakable (`destroyTime −1`), explosion-proof, `PushReaction.BLOCK`, not replaceable.
- Tagged `minecraft:wither_immune`, `minecraft:dragon_immune` and `minecraft:features_cannot_replace`.
- Creative players can break it. Four facings. It emits no light and has no ambient sound.
- **Block entity:** `{format 1, site}`, where `site` is a `vftv:choir_site` id ("Sync and state").

### Site kinds

A **server-only datapack registry, `vftv:choir_site`** (`data/<ns>/vftv/choir_site/<path>.json`), fixed when a world opens and never synced.

| Field | Required | Range | Meaning |
|---|---|---|---|
| `pages` | yes | 1–64 distinct entry ids | the pages this kind gives, in the order it gives them |
| `pages_per_claim` | yes | 1–8 | how many a player's first claim at one site takes |

- **The required built-in key** (`WorldgenKeys` in `api`) is `vftv:choir_ruin`. Its `pages` are every Stage 3 entry in `vftv:choir_pages`, with **`vftv:choir/beneath_the_notes` first** and the rest in the narrative order the [Stage 3 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) freezes. `pages_per_claim` is **3**.
- **Load validation**, after the research registry. It refuses the world with a clear error naming the entry on: an id that isn't a `find` entry in `vftv:choir_pages`; a duplicate id; a range broken; the built-in key missing.
  - **For the built-in `vftv:choir_ruin` only** (a test failure): a page outside Stage 3; `beneath_the_notes` not first; a Stage 3 Choir page missing.
- #28 adds its own kinds for Stage 4 pages, `vftv:threshold` and `vftv:road`, under its [own rules](eldritch.md#sites); the rules above that aren't scoped to `vftv:choir_ruin` apply to them too.

### Claims

**The player attachment `vftv:choir_claims`** (`copyOnDeath`, server-only, never synced):

```
{format: 1, sites: [{dim: <dimension id>, pos: <long, BlockPos.asLong>, pages: [<entry id>, …]}, …]}
```

- **Default:** absent means `{format 1, sites []}`.
- **Bounds:** at most `MAX_CLAIM_SITES` (65,536) sites; `pages` holds 1–8 distinct ids; every `dim` and page id is a syntactically valid id of at most 256 characters; `pos` is any signed 64-bit word; `sites` sorted by (`dim`, `pos`) with no two entries sharing them.
- **Inert keys:** a `dim` naming no loaded dimension and a `pos` that unpacks outside the world are **kept as they are**. Nothing resolves them, loads anything or checks them against a world; they simply never match a Reliquary. So "a bound broken" means only the syntactic bounds above.
- **No eviction, ever.**
- **Keyed by the Reliquary's position,** not the structure, so a claim survives the ruin being mined, the block being removed by an operator, and the chunk being regenerated.
- **Unknown page ids** (a pack removed the entry) are **kept** in the record and filtered out of every offer, so a page restored by a pack is still claimed. No blank or unknown page is ever handed out.
- **Raw retention:** an unreadable record keeps its raw tag and is written back unchanged, with one warning; the player's Reliquary uses then refuse (row 3 below). Unreadable means an unknown or future format, a duplicate key, a bound broken or an id that doesn't parse.
- **Saving:** the attachment is part of the player's saved data, written with it at every player save. There is no separate dirty mark for player attachments.
- **The "claimed anywhere" set** is the union of a player's claimed pages. It lives in memory per online player, built at login in O(sites × 8) and kept in step with every claim.

### Using it

Any hand, not a spectator. Vanilla's `useItemOn` checks and NeoForge's `PlayerInteractEvent.RightClickBlock` come first, so spawn protection and claim mods govern it ([wands, Creation, removal and sharing](wands.md#creation-removal-and-sharing) states the same for nodes). Then **the first matching row** decides. `R` is the claimed pages at this site that exist in the research registry and that the player has neither latched nor learned (`isLatchedOrLearned`, the research spec amended):

| # | Outcome | Case | Message (`reliquary.vftv.*`) | Effect |
|---|---|---|---|---|
| 0 | `busy` | the player's operation guard is held (re-entry from a callback during a hand-out) | none | none |
| 1 | `attempt_cap` | the player has already made `MAX_USES_PER_PLAYER_PER_TICK` (1) attempts this tick; counted before anything below | none | none |
| 2 | `silent` | the block entity's record is unreadable, or its `site` resolves to no `vftv:choir_site` | `.silent` | none; logged once per block |
| 3 | `unreadable` | the player's `vftv:choir_claims` is unreadable | `.unreadable` | none; logged once per player |
| 4 | `done` | the player has a claim at this position and `R` is empty | `.done` | none |
| 5 | `reissued` | the player has a claim at this position and `R` is not empty | `.reissued` | hand out `R` |
| 6 | `all_held` | no claim here; `B` (the first `pages_per_claim` pages of the site's `pages` that exist in the registry and that the player has neither latched nor learned **nor claimed at any site**) is empty | `.all_held` | **nothing recorded**, so a page a pack adds later can still be given |
| 7 | `full` | no claim here, `B` not empty, `MAX_CLAIM_SITES` reached | `.full` | none; logged once (unreachable in play) |
| 8 | `given` | otherwise | `.given` | record `{dim, pos, B}`; then hand out `B` |

- **Reissue is free and immediate:**
  - A lost page is given back at the next use, whatever happened to it: tossed, burnt, dropped from a full inventory and taken, or a cancelled drop.
  - Pages are transferable by design ([research, Choir pages](research.md#choir-pages)), and every player can claim independently, so pacing duplicate copies would protect no knowledge.
  - The attempt cap bounds the work.
- **Research contract cases:**
  - two players each get `vftv:choir/beneath_the_notes` from one Reliquary (row 8 twice: each claim is its player's alone);
  - the 129th claimant is row 8 like the first, since nothing has a capped history (unlike a vanilla vault, which keeps at most 128 UUIDs and evicts the oldest, `MC:world/level/block/entity/vault/VaultServerData.java:29,65–73`);
  - one player's claim never touches another's.

**Offer vectors** (pages `p1`…`p12`, `p1` standing for `beneath_the_notes`, `pages_per_claim` 3; transcribed into the fixtures):

| Step | Tick | Player state before | Use at | Outcome |
|---|---|---|---|---|
| 1 | 0 | A: nothing | R1 | `given` p1 p2 p3 |
| 2 | — | A latches p1 and p2 | — | — |
| 3 | 1 | A | R1 | `reissued` p3 |
| 4 | 1 | A, same tick | R1 | `attempt_cap` |
| 5 | 2 | A | R2 | `given` p4 p5 p6 (p3 is claimed at R1) |
| 6 | — | B latches p1 (a handed page) | — | — |
| 7 | 2 | B | R1 | `given` p2 p3 p4 |
| 8 | — | A latches p3 | — | — |
| 9 | 3 | A | R1 | `done` |
| 10 | — | p5's entry is removed by a pack | — | — |
| 11 | 4 | A | R2 | `reissued` p4 p6 (p5 is filtered out) |
| 12 | 5 | A | R3 | `given` p7 p8 p9 |
| 13 | — | C latches every page | — | — |
| 14 | 5 | C | R1 | `all_held` |
| 15 | 6 | C | R1 | `all_held` again: nothing was recorded |

### Handing out

Under the player's **operation guard**. It is taken right after rows 0 and 1 pass, held across the whole decision (rows 2–8) and every callback below, and released on **every** exit, a thrown exception included:

1. **The claim is written first** (row 8 only).
2. For each id, in order: a `vftv:choir_page` stack with the `vftv:page` component. `Inventory.add`; any remainder goes to `Player.drop(stack, false)` (`MC:world/entity/player/Player.java:597–599`), which posts the cancellable `ItemTossEvent` before the item enters the world (`NF:common/CommonHooks.java:446–460`). A cancelled or failed drop loses that copy; the claim stands, and row 5 gives it back at the next use.
3. **An exception** from a callback is caught and logged once. The claim stands, and the remaining items are still attempted.
4. The `reliquary.give` sound, then the message, which goes to the action bar.

**The recovery promise, narrowly:**
- A **claim** recorded in a saved player file is never lost.
- **Physical copies are not transactional with it.** Dropped items save with their chunk, not with the player, so an interrupted save can lose a copy (reissued at the next use) or duplicate one (a dropped copy saved in a chunk whose player file was not).
- Duplicates are harmless: a page already latched or learned is "Already in my book." and is kept for handing on ([research, Choir pages](research.md#choir-pages)).

**Options considered:**

| Option | Why not |
|---|---|
| a vanilla vault | its 128-UUID eviction and its key item |
| claims in the block entity | lost with the block, and growing with the server's population at one position |
| a random page set per ruin | coverage then needs luck, and the narrative order is lost |
| a fresh batch at every revisit | one ruin would yield every page |
| a reissue cooldown | it paces copies, not knowledge, and makes a loss cost waiting |
| refusing while Answerers are near | an Answerer stuck out of reach could keep a page from a player forever |

## The Answerers

### The creatures

**`vftv:answerer`** and **`vftv:answerer_cantor`** (Stage 3). [Lore](lore.md#cosmology): living would-be listeners who speak back to the Voices without understanding them, hostile and found at Choir sites.

- Both are `Monster`s, category `MONSTER`, size 0.6 × 1.95, eye height 1.62, registered with **`.notInPeaceful()`** (`MC:world/entity/EntityType.java:641–643`; `MONSTER` alone doesn't set it), and tagged **`#vftv:answerers`**.
- **Stats** *(ours; fixed at registration and asserted by a unit test)*:

  | | Health | Attack | Armour | Speed | Follow range | XP |
  |---|---|---|---|---|---|---|
  | Answerer | 24 | 4 | 2 | 0.25 | 20 | 8 |
  | Cantor | 20 | 2 | 0 | 0.23 | 20 | 10 |

- **Never:** opens doors, picks up items, breaks or places blocks, burns in daylight, rides, joins raids, or is a Voice spawn.

### Placement and persistence

- **Template entities only.** There is no natural spawn, no biome entry, no `spawn_overrides`, no spawner and **no runtime spawning of any kind**.
  - Each chancel holds 1 Answerer and 1 Cantor; each wing holds 0–2 Answerers ("Pieces and topology").
  - Pool elements finalize template entities (`MC:SinglePoolElement.java:171`), so `finalizeSpawn` runs with `EntitySpawnReason.STRUCTURE` (`MC:StructureTemplate.java:519`). It sets the home with `setHomeTo(own block position, answerer.home_radius)` (`MC:world/entity/Mob.java:1241`).
- **`requiresCustomPersistence()` returns true** (`Mob.java:710`), so they never despawn, however they arrived. Persistent mobs don't count toward the natural mob cap (`MC:world/level/NaturalSpawner.java:74`), so a ruin never suppresses vanilla spawns. Their number is fixed by the templates.
- **Peaceful:** `.notInPeaceful()` means creation can fail at generation, and an existing Answerer is discarded at its despawn check (`Mob.java:715–717`). **A ruin generated in peaceful has no defenders, then or later.** Stated.
- **The home radius is saved with the mob** (`Mob.java:393–394,418–420`). A changed `answerer.home_radius` applies to Answerers finalized after it; existing ones keep theirs.

### Behaviour

Goals in priority order:

| Priority | Goal | Flags |
|---|---|---|
| 0 | float | `JUMP` |
| 1 | **return home** while outside it, which drops any target | `MOVE` |
| 2 | melee attack (vanilla `MeleeAttackGoal`, `MC:world/entity/ai/goal/MeleeAttackGoal.java:25–29`) | `MOVE`, `LOOK` |
| 3 | **chant** (Cantor only) | none, so it runs **concurrently** with melee and movement |
| 4 | wander within home | `MOVE` |
| 5 | look at a player | `LOOK` |

**Targeting** (ours) uses **one predicate everywhere: `validTarget(mob, player)`**. It is true iff the player:
- is attackable (alive, in the mob's level, not creative or spectator);
- stands inside **that mob's** home;
- is within **that mob's** follow range.

Vanilla's restriction goal only starts the walk home, and its target goals don't recheck home, so the predicate is ours, as with the [Hush spec's Hollow](hush.md#the-creature). It is applied at every step:
- **Acquisition:** the nearest player for whom `validTarget` holds.
- **Retention:** checked every tick; the target is dropped as soon as it fails.
- **Answerers only ever target players.** Hurt by a non-player, they change nothing.
- **Retaliation and alert.** When an Answerer is hurt by a player `P`, directly or as a projectile's owner:
  - it targets `P` iff `validTarget(itself, P)`; otherwise nothing changes and no alert is sent;
  - one AABB of radius 20 (the follow range) selects the `#vftv:answerers` mobs with no target, nearest first with ties by entity id, at most `MAX_ALERT` (16);
  - each of those for which `validTarget(recipient, P)` holds targets `P`. A recipient whose home contains `P` but who is out of range of `P` stays as it was.

  Vanilla's `HurtByTargetGoal` isn't used: it alerts by Java class and retaliates against non-players (`MC:world/entity/ai/goal/target/HurtByTargetGoal.java:33–47,72–115`).

### The chant

The Cantor keeps a transient `next_chant` game time, set to the time it is created or loaded plus `cantor.interval`, so a reload never chants at once. Each tick, while it has a valid target and `now ≥ next_chant`:

1. One AABB of radius `cantor.radius` (read now) around it selects the `#vftv:answerers` mobs, the Cantor included, nearest first with ties by entity id, at most `cantor.max_targets` (read now).
2. Each gets one `addEffect(new MobEffectInstance(minecraft:resistance, cantor.duration, cantor.amplifier))`. The callbacks are vanilla's and NeoForge's; side effects are disclosed and never rolled back.
3. `vftv:cantor.chant` plays. `next_chant = now + cantor.interval` (read now). The synced `chanting` flag is set for `CHANT_SHOW_TICKS` (40, fixed) and then cleared. It is transient: false on load, never saved.

A tuning change applies at the next chant for the radius and target count, and at the next scheduling for the interval. An already scheduled `next_chant` stands.

### Warp and phantoms

- **Never a Voice spawn:** the tags `#vftv:answerers` and `vftv:voice_spawnable` are disjoint, asserted by a datagen test ([warp, Danger](warp.md#danger)).
- **No warp:** Answerers apply none and trigger no Voice event.
- **Real entities,** so the Tuning Fork reads them truly ([aspects spec](aspects.md)).
- **Not to be confused with apparitions.** The [warp spec's apparitions](warp.md#phantoms) are robed and **faceless** Choir figures. Answerers are living, with **faces**, mauve cloth and brass mouthpieces; the asset review sets them side by side.
- **A Choir site is a Void-touched place** ("The Choir-site place query"), so the warp spec's context-only danger can happen there. That rule is the warp spec's.

**Options considered:**

| Option | Why not |
|---|---|
| `spawn_overrides`, a respawning cult | farmable, never cleared, and a native natural-spawn term |
| a vanilla trial spawner | its look and key economy are off-tone |
| a ranged caster | TC4's Cleric; a projectile entity and assets for little gain |
| a unique drop | no recipe needs one |

## Not in v1

- **Ambient magical mobs are deferred from v1 by the owner.** The Answerers and the [Hush spec's](hush.md#hollows) Hollows are the whole creature roster. This is an owner-approved scope cut, recorded in the map's "Out of scope". The `add_spawns` biome-modifier seam (NFR §5) is there for a later spec.
- **No TerraBlender biome.** No decision in Stages 1–3 needs a biome. One would add a climate slot, surface rules (which need TerraBlender's Java API, NFR §5), a palette checked against [art style §5](art-style.md#5-tone-ramp-by-stage) and aspect values, for no experience the player lacks. [Architecture, Soft dependencies](architecture.md#soft-dependencies) already keeps progression free of it, and `neoforge.compat.terrablender` stays empty.

## The Choir-site place query

**`ChoirSitePlaceQuery`,** registered at init with `WarpService.registerPlaceQuery` ([warp, Context](warp.md#context)). It answers true iff:
1. `ServerChunkCache#getChunkNow` holds the chunk at the position (`MC:server/level/ServerChunkCache.java:184`), and
2. for some structure `s` in the resolved `#vftv:choir_sites`, `chunk.getReferencesForStructure(s)` is not empty (`MC:world/level/chunk/ChunkAccess.java:224–225`).

- **Per-chunk metadata indexed at generation:**
  - At generation, vanilla's `createReferences` gives every chunk whose 16×16 column meets a start's bounding box a reference to the start's chunk (`MC:world/level/chunk/ChunkGenerator.java:608–636`).
  - The references persist with the chunk (`SerializableChunkData.java:335,598`).
  - The query **reads that the set is non-empty**. It never resolves a start and never follows a reference: pure work on one loaded chunk, bounded by `MAX_SITE_STRUCTURES` (8) map lookups, with zero allocation.
- **The extent is the terrain-expanded box's chunk columns, at any y.** The reference box is the piece envelope grown by 12 on every side for `beard_thin` (`MC:Structure.java:81–82`). Mining the masonry changes nothing: the references stay.
- **Only generated ruins are sites.** `/place structure` places pieces without registering starts or references (`MC:server/commands/PlaceCommand.java:277–316`), so a command-placed ruin is not a Choir site. Its Reliquary still works. No runtime index is added.
- **The resolved set:**
  - The tag is resolved on the server at start and on `TagsUpdatedEvent` with `ServerDataLoad` (`NF:event/TagsUpdatedEvent.java:53–66`), never on a client's notification.
  - At most 8 structures are kept, the first 8 by id, with one warning if the tag holds more.
- **Independent of the [Hush spec's query](hush.md#warp-context).** `core.worldgen` imports `core.warp`'s interface; `core.warp` imports nothing of ours.
- **No warp is ever gained from a site.**

## Protection and multiplayer

| Thing | Rule |
|---|---|
| Pages and the Stage 3 find | per player ("The Reliquary"). Looting, mining or killing first takes nothing from anyone else. |
| The Reliquary | survival-unbreakable and immovable; use passes vanilla's and NeoForge's interaction checks |
| Ruin masonry | ordinary vanilla blocks, minable: exploring is the reward. Claim mods see vanilla's events. |
| The Choir node | the [wands spec's rules](wands.md#creation-removal-and-sharing): shareable, capturable where interaction is allowed, never broken in place |
| The Answerers | finite. A peer can clear a fight before you arrive; the pages and finds stay yours. |
| Spawn protection | nothing special: ruins are ordinary blocks under vanilla's rules |

Integrations for named claim mods belong to the mod-compatibility spec.

## Rendering and sound

### Models

- **The Reliquary:** at most 6 cuboids ([art style §6](art-style.md#6-blocks-and-models)): a mauve-stone plinth carrying a brass-bound page box with a slot, teal dimmed into a groove (Stage 3 row). Four facings by blockstate rotation. Item form from the block model.
- **The node** is drawn by the wands spec's renderer; `vftv:choir` needs only its name.

**The Answerers,** frozen geometry in model pixels (1/16 block). The origin is at the feet's centre, y up, −z the front, and +x the entity's left. Texture 64 × 64, one per type. Box UVs in vanilla's layout; `mirror` reuses the right net for the left side.

| Part | Pivot | Cuboid from | Size (w × h × d) | UV origin | Mirror | Type |
|---|---|---|---|---|---|---|
| `head` | (0, 24, 0) | (−4, 24, −4) | 8 × 8 × 8 | (0, 0) | | both |
| `mouthpiece` | (0, 24, 0) | (−1, 26, −8) | 2 × 2 × 4 | (32, 0) | | both |
| `body` | (0, 24, 0) | (−4, 12, −2) | 8 × 12 × 4 | (16, 16) | | both |
| `robe` | (0, 12, 0) | (−5, 2, −3) | 10 × 10 × 6 | (0, 32) | | both |
| `right_arm` | (−5, 22, 0) | (−8, 12, −2) | 4 × 12 × 4 | (40, 16) | | both |
| `left_arm` | (5, 22, 0) | (4, 12, −2) | 4 × 12 × 4 | (40, 16) | yes | both |
| `right_leg` | (−2, 12, 0) | (−4, 0, −2) | 4 × 12 × 4 | (0, 16) | | both |
| `left_leg` | (2, 12, 0) | (0, 0, −2) | 4 × 12 × 4 | (0, 16) | yes | both |
| `bell` (child of `right_arm`) | (−6, 12, 0) | (−8, 8, −2) | 3 × 3 × 3 | (44, 0) | | Cantor |

- **Faces are drawn,** in the ink outline: the Answerers are living people. The hood is painted on the head. The mouthpiece is a brass ear-trumpet worn **backwards**, the Answerers' sign. There is no teal, since they don't resonate.
- **Poses** (client time only):
  - **idle:** vanilla's humanoid arm sway;
  - **walk:** vanilla's limb swing;
  - **attack:** the right arm swings down over vanilla's attack animation;
  - **chant** (Cantor, while `chanting`): both arms raised 60° forward, the bell arm shaking ±10° on a 4-tick period, the head pitched up 15°.

  Shadow radius 0.5. No glow, no emissive layer.
- The model layer, renderer and animation are build-phase code. **Allocation:** zero per frame in our renderer code.

### Sound

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:answerer.ambient` | 3 | vanilla's ambient sound, at an interval of 240 ticks (fixed) | `SoundSource.HOSTILE`, volume 1.0, vanilla's mob voice pitch |
| `vftv:answerer.hurt` | 3 | hurt | likewise |
| `vftv:answerer.death` | 2 | death | likewise |
| `vftv:cantor.chant` | 3 | each chant | at the Cantor; `HOSTILE`, volume 1.5, pitch 1.0 |
| `vftv:reliquary.give` | 2 | rows 5 and 8 | at the block; `SoundSource.BLOCKS`, volume 1.0 |
| `vftv:reliquary.empty` | 1 | rows 2, 3, 4, 6 and 7 | at the block; `BLOCKS`, volume 0.6 |

The Cantor shares the Answerer's ambient, hurt and death sounds. Rows 0 and 1 are silent.

**Files:** `assets/vftv/sounds/answerer/mutter_0` … `mutter_2`, `hurt_0` … `hurt_2`, `death_0`, `death_1`; `cantor/chant_0` … `chant_2`; `reliquary/give_0`, `give_1`, `empty`.
- The variant is vanilla's uniform choice among a `sounds.json` entry's files; entries carry no volume or pitch overrides and name `subtitles.vftv.<event>`.
- Every file follows [art style §7](art-style.md#7-sound-palette) in the Stage 3 register (lower fundamental, more reverb, dimmed clang).
- The Answerers' sounds are **breath through brass with no words**. They are not Voice lines and not the owner's recordings, and the Void-tongue is never used for them.

## Authored content

The content ticket delivers authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, a duplicate JSON key, or an entry that fails its rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/worldgen/templates/<name>.json   the eight templates (the templates ticket)
content/worldgen/choir_ruin.json         placement numbers, pool weights, weathering, biomes
content/worldgen/sites.json              the vftv:choir_ruin site kind
content/worldgen/node_types.json         the vftv:choir node type
content/worldgen/loot.json               the three loot tables
content/worldgen/fixtures.json           the vector tables, transcribed
content/lang/en_us/worldgen.json         every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `templates/<name>.json` | `vftv:template/1` | `data/vftv/structure/choir_ruin/<name>.nbt` |
| `choir_ruin.json` | `vftv:choir_ruin/1` | `data/vftv/worldgen/structure/choir_ruin.json`, `worldgen/structure_set/choir_ruin.json`, `worldgen/template_pool/choir_ruin/{chancel,wings,ends}.json`, `worldgen/processor_list/choir_ruin.json`, `tags/worldgen/biome/has_structure/choir_ruin.json` |
| `sites.json` | `vftv:choir_sites/1` | `data/<ns>/vftv/choir_site/<path>.json` |
| `node_types.json` | the wands spec's `vftv:node_types/1` | `data/vftv/vftv/node_type/choir.json`; datagen merges node-type files and rejects an id declared twice |
| `loot.json` | `vftv:worldgen_loot/1` | `data/vftv/loot_table/chests/choir_ruin.json`, `…/entities/answerer.json`, `…/entities/answerer_cantor.json` |
| `fixtures.json` | `vftv:worldgen_fixtures/1` | nothing: read by the unit tests |
| `lang/<lang>/worldgen.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json`; a duplicate key across `content/lang` fails datagen |

The advancements, the tags `#vftv:choir_sites`, `#vftv:answerers` and `#vftv:choir_weathering`, the fixed structure fields and the tuning file `data/vftv/tuning/worldgen.json` (from the record's defaults) are written by datagen from code. They are not authored content.

### Templates

`{"schema": "vftv:template/1", "size", "palette", "layers", "block_entities", "entities"}`.

**Families.** Each template belongs to one **family**, which sets its file list, sizes, roles and counts, its allowed `vftv:` blocks, block-entity forms and entities. This spec's family is **`choir_ruin`**: exactly eight files, `chancel_a`, `chancel_b`, `cloister`, `bell_pit`, `scriptorium`, `fallen_nave`, `rubble_a`, `rubble_b`, under the rules below. The [Eldritch tier spec](eldritch.md#templates) owns the families `threshold` and `road`, under its own family rules. **Every family** shares the format rules, the `minecraft` and `vftv` namespaces only, no `minecraft:structure_block`, spawner or vault, the Hush and attachment rules, and the datagen-phase parsed-NBT no-Hush test. The size limit is 48 per side (`MAX_TEMPLATE_SIDE`) for every family.

| Field | Required | Type and range |
|---|---|---|
| `size` | yes | `[x, y, z]`, ints 1–48 (`MAX_TEMPLATE_SIDE`), fixed per role as under "Pieces and topology" |
| `palette` | yes | 1–64 entries; key one printable ASCII character other than space; value a full block state string (`id[prop=value,…]`), every property of the block given exactly once |
| `layers` | yes | exactly `y` layers (y = 0 upward), each exactly `z` strings (z = 0 northward-first) of exactly `x` characters (x = 0 westward-first); a space is **structure void** (nothing placed, the terrain kept); any other character must be a palette key |
| `block_entities` | no, default `{}` | key `"x,y,z"` (ints inside `size`), value exactly one of `{"jigsaw": {…}}` (the connector table's fields, `placement_priority` and `selection_priority` 0–100, default 0), `{"chest": {"loot_table": "vftv:chests/choir_ruin"}}`, `{"reliquary": {"site": <vftv:choir_site id>}}` or `{"node": {"type": <node type id>}}` (a shorthand: the converter writes the complete record under "Processors, The saved template record") |
| `entities` | no, default `[]` | `{"pos": [x, y, z], "type": "vftv:answerer" \| "vftv:answerer_cantor"}`, ints inside `size`, positions unique. `pos` is the **standing cell** the entity occupies; the converter writes its feet at `(x + 0.5, y, z + 0.5)` (the template's entity position) and its block position `(x, y, z)`. So the standing-cell check and the placed entity describe the same space. |

**Rejected** (in the `choir_ruin` family; the format, namespace, Hush and attachment rules in every family):
- **Format:** an unknown or missing field; a layer, row or string of the wrong length; a character not in the palette; a block state that doesn't parse or omits or adds a property.
- **Block entities:** one on a block of another kind, or a block that needs one without it (`minecraft:jigsaw`, `minecraft:chest`, `vftv:reliquary`, `vftv:node`); a jigsaw whose fields differ from its connector's row, or whose position or facing differs from its role's.
- **Blocks and namespaces:** any namespace other than `minecraft` and `vftv`; `minecraft:structure_block`, any container other than a chest, any spawner or vault; any `vftv:` block other than `vftv:reliquary` and `vftv:node`.
- **Hush and attachments:** any block in `#vftv:hushed`; a node of type `vftv:hushed`; any entity outside the two types; the key `neoforge:attachments` anywhere.
- **Structure:** a role count, footing or path rule broken ("Pieces and topology"); a chancel node fewer than 8 blocks above the floor layer.

**One complete valid file** (`rubble_a`; the other seven follow the same shape at their sizes):

```json
{"schema": "vftv:template/1",
 "size": [9, 2, 9],
 "palette": {"#": "minecraft:tuff_bricks", "c": "minecraft:chiseled_tuff", "J": "minecraft:jigsaw[orientation=north_up]"},
 "layers": [
  ["#########", "#########", "#########", "#########", "#########", "#########", "#########", "#########", "#########"],
  ["    J    ", " #       ", "   #  c  ", "         ", "  ##     ", "         ", "      #  ", " c       ", "         "]],
 "block_entities": {
  "4,1,0": {"jigsaw": {"name": "vftv:choir_ruin/end_plug", "target": "minecraft:empty", "pool": "minecraft:empty",
                       "final_state": "minecraft:air", "joint": "aligned"}}},
 "entities": []}
```

### The ruin

`{"schema": "vftv:choir_ruin/1", "structure_set", "pools", "weathering", "biomes"}`:

| Field | Required | Type and range |
|---|---|---|
| `structure_set.spacing` | yes | int 16–256, > `separation` |
| `structure_set.separation` | yes | int 8–255 (≥ 8 keeps ruins apart: 9 × 16 > 2 × 40 + 2 × 12) |
| `structure_set.salt` | yes | must equal 1128812370 |
| `pools.chancel`, `pools.wings`, `pools.ends` | yes | maps of exactly the role's elements (`minecraft:empty` only in `ends`) to weights 1–16 |
| `weathering` | no, default `[]` | ≤ 8 objects `{"input": <block>, "probability": 0.0–1.0, "output": <block>}`; both blocks in `#vftv:choir_weathering`, input ≠ output |
| `biomes.include` | yes | vanilla biome ids |
| `biomes.exclude` | yes | map of vanilla biome id to a one-line reason |

**Rejected:** a field out of range; an element missing or extra in a pool; a biome id outside vanilla's `#minecraft:is_overworld`; a biome in both lists; **any of `#minecraft:is_overworld`'s 55 biomes in neither**. The shipped file:

```json
{"schema": "vftv:choir_ruin/1",
 "structure_set": {"spacing": 36, "separation": 14, "salt": 1128812370},
 "pools": {
  "chancel": {"vftv:choir_ruin/chancel_a": 1, "vftv:choir_ruin/chancel_b": 1},
  "wings": {"vftv:choir_ruin/cloister": 1, "vftv:choir_ruin/bell_pit": 1, "vftv:choir_ruin/scriptorium": 1, "vftv:choir_ruin/fallen_nave": 1},
  "ends": {"vftv:choir_ruin/rubble_a": 2, "vftv:choir_ruin/rubble_b": 2, "minecraft:empty": 1}},
 "weathering": [
  {"input": "minecraft:tuff_bricks", "probability": 0.15, "output": "minecraft:tuff"},
  {"input": "minecraft:deepslate_tiles", "probability": 0.2, "output": "minecraft:cracked_deepslate_tiles"}],
 "biomes": {
  "include": ["minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:snowy_plains", "minecraft:windswept_gravelly_hills",
              "minecraft:grove", "minecraft:windswept_hills", "minecraft:snowy_taiga", "minecraft:windswept_forest",
              "minecraft:taiga", "minecraft:plains", "minecraft:meadow", "minecraft:forest", "minecraft:old_growth_spruce_taiga",
              "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:pale_garden",
              "minecraft:savanna_plateau", "minecraft:savanna", "minecraft:jungle", "minecraft:badlands", "minecraft:desert",
              "minecraft:wooded_badlands", "minecraft:ice_spikes", "minecraft:old_growth_pine_taiga", "minecraft:sunflower_plains",
              "minecraft:old_growth_birch_forest", "minecraft:sparse_jungle", "minecraft:bamboo_jungle",
              "minecraft:eroded_badlands", "minecraft:windswept_savanna", "minecraft:cherry_grove"],
  "exclude": {
   "minecraft:mushroom_fields": "a rare island, left to itself",
   "minecraft:deep_frozen_ocean": "water", "minecraft:frozen_ocean": "water", "minecraft:deep_cold_ocean": "water",
   "minecraft:cold_ocean": "water", "minecraft:deep_ocean": "water", "minecraft:ocean": "water",
   "minecraft:deep_lukewarm_ocean": "water", "minecraft:lukewarm_ocean": "water", "minecraft:warm_ocean": "water",
   "minecraft:stony_shore": "a narrow shore", "minecraft:snowy_beach": "a narrow shore", "minecraft:beach": "a narrow shore",
   "minecraft:snowy_slopes": "too steep", "minecraft:jagged_peaks": "too steep", "minecraft:stony_peaks": "too steep",
   "minecraft:frozen_peaks": "too steep",
   "minecraft:frozen_river": "water", "minecraft:river": "water",
   "minecraft:dripstone_caves": "underground", "minecraft:lush_caves": "underground",
   "minecraft:sulfur_caves": "underground", "minecraft:deep_dark": "underground"}}}
```

### Sites

`{"schema": "vftv:choir_sites/1", "sites": {"vftv:choir_ruin": {"pages": [...], "pages_per_claim": 3}}}`, with exactly that one site in the registry format and the load-validation rules under "Site kinds". The `pages` list is transcribed from the Stage 3 research content's frozen narrative order. **Rejected:** another site id; a page list that differs from that order; `pages_per_claim` other than 3.

### The node type

`{"schema": "vftv:node_types/1", "types": {"vftv:choir": …}}` with exactly this shipped entry. **Rejected:** any other id or any difference.

```json
{"schema": "vftv:node_types/1", "types": {
  "vftv:choir": {"aspects": [{"aspect": "vftv:aer", "range": [16, 32]}, {"aspect": "vftv:ordo", "range": [24, 48]},
                             {"aspect": "vftv:sonus", "range": [4, 12]}, {"aspect": "vftv:vacuos", "range": [4, 12]}],
                 "main_aspect": "vftv:ordo",
                 "clarity": {"faint": 0, "steady": 60, "clear": 40}}}}
```

### Loot

`{"schema": "vftv:worldgen_loot/1", "tables": {<id>: <vanilla loot table body>}}`, with exactly the ids `vftv:chests/choir_ruin`, `vftv:entities/answerer` and `vftv:entities/answerer_cantor`. Each body is vanilla's loot table format verbatim (`type` `minecraft:chest` or `minecraft:entity`).

**Rejected:**
- any item outside the `minecraft` namespace (the `vftv:` allowance is empty in v1);
- explicitly: `vftv:choir_page`, `vftv:hush_ash` and every item of a `#vftv:hushed` block;
- a body vanilla's codec rejects.

A sketch:

```json
{"schema": "vftv:worldgen_loot/1", "tables": {
  "vftv:chests/choir_ruin": {"type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6},
    "entries": [{"type": "minecraft:item", "name": "minecraft:paper", "weight": 10,
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
                {"type": "minecraft:item", "name": "minecraft:amethyst_shard", "weight": 4},
                {"type": "minecraft:item", "name": "minecraft:bell", "weight": 1}]}]},
  "vftv:entities/answerer": {"type": "minecraft:entity", "pools": [{"rolls": 1,
    "entries": [{"type": "minecraft:item", "name": "minecraft:paper",
                 "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 0, "max": 2}},
                               {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                                "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]}]}]},
  "vftv:entities/answerer_cantor": {"type": "minecraft:entity", "pools": [{"rolls": 1,
    "entries": [{"type": "minecraft:item", "name": "minecraft:book"}]}]}}}
```

### Fixtures

`{"schema": "vftv:worldgen_fixtures/1", "placement": [...], "roll": [...], "offers": [...]}`. **All three sections are required.** Together they transcribe every vector table in this spec exactly, one row per vector. The unit tests replay them against the code; that consumer is build work. Every field is required unless a default is given.

**Types:** *int* is a JSON integer; *hex* is a string `"0x"` followed by 1–16 uppercase hex digits, read as a 64-bit word.

| Record | Fields |
|---|---|
| `placement` | `seed` hex; `spacing` int 16–256; `separation` int 8–255; `salt` int ≥ 0; `region` `[rx, rz]` ints; `expect` `[cx, cz]` ints |
| `roll` | `seed` hex; `pos` `[x, y, z]` ints (x and z −30,000,000…30,000,000, y −64…319); `clarity` three ints 0–1000 (faint, steady, clear; sum > 0); `aspects` 1–6 triples `[id, min, max]`, ascending id; `expect` `{key: hex, h: hex, clarity: "faint" \| "steady" \| "clear", maxes: {id: int}}` |
| `offers` | `pages` 1–64 distinct opaque ids; `pages_per_claim` int 1–8; `steps`, each exactly one of `{"use": {"tick": int ≥ 0, "player": string, "site": string}, "expect": {"outcome": <a row's outcome name>, "pages": ids, present iff the outcome is "given" or "reissued"}}`, `{"latch": {"player": string, "pages": ids}}` or `{"remove": ids}` (entries no longer in the registry). Ticks never decrease. |

**The complete file:**

```json
{"schema": "vftv:worldgen_fixtures/1",
 "placement": [
  {"seed": "0x123456789ABCDEF", "spacing": 36, "separation": 14, "salt": 1128812370, "region": [0, 0], "expect": [12, 6]},
  {"seed": "0x123456789ABCDEF", "spacing": 36, "separation": 14, "salt": 1128812370, "region": [-1, 0], "expect": [-16, 17]},
  {"seed": "0x123456789ABCDEF", "spacing": 36, "separation": 14, "salt": 1128812370, "region": [0, -1], "expect": [4, -24]},
  {"seed": "0x123456789ABCDEF", "spacing": 36, "separation": 14, "salt": 1128812370, "region": [3, -7], "expect": [127, -247]},
  {"seed": "0x123456789ABCDEF", "spacing": 36, "separation": 14, "salt": 1128812370, "region": [-12345, 6789], "expect": [-444402, 244420]},
  {"seed": "0x123456789ABCDEF", "spacing": 36, "separation": 14, "salt": 1128812370, "region": [52000, -52000], "expect": [1872002, -1871988]}],
 "roll": [
  {"seed": "0x123456789ABCDEF", "pos": [0, 64, 0], "clarity": [0, 60, 40],
   "aspects": [["vftv:aer", 16, 32], ["vftv:ordo", 24, 48], ["vftv:sonus", 4, 12], ["vftv:vacuos", 4, 12]],
   "expect": {"key": "0x40", "h": "0x7F227C6F5C24323D", "clarity": "steady", "maxes": {"vftv:aer": 30, "vftv:ordo": 37, "vftv:sonus": 11, "vftv:vacuos": 6}}},
  {"seed": "0x123456789ABCDEF", "pos": [-1, 70, -1], "clarity": [0, 60, 40],
   "aspects": [["vftv:aer", 16, 32], ["vftv:ordo", 24, 48], ["vftv:sonus", 4, 12], ["vftv:vacuos", 4, 12]],
   "expect": {"key": "0xFFFFFFFFFFFFF046", "h": "0x9338065CEBA0BF6E", "clarity": "steady", "maxes": {"vftv:aer": 29, "vftv:ordo": 41, "vftv:sonus": 8, "vftv:vacuos": 8}}},
  {"seed": "0x123456789ABCDEF", "pos": [1000, -20, -3000], "clarity": [0, 60, 40],
   "aspects": [["vftv:aer", 16, 32], ["vftv:ordo", 24, 48], ["vftv:sonus", 4, 12], ["vftv:vacuos", 4, 12]],
   "expect": {"key": "0xFA3FFF448FEC", "h": "0xDDBFDF7BEE832E0D", "clarity": "steady", "maxes": {"vftv:aer": 23, "vftv:ordo": 45, "vftv:sonus": 10, "vftv:vacuos": 5}}},
  {"seed": "0x123456789ABCDEF", "pos": [-12345, 101, 6789], "clarity": [0, 60, 40],
   "aspects": [["vftv:aer", 16, 32], ["vftv:ordo", 24, 48], ["vftv:sonus", 4, 12], ["vftv:vacuos", 4, 12]],
   "expect": {"key": "0xFFF3F1C001A85065", "h": "0x810864C8E931A0C4", "clarity": "clear", "maxes": {"vftv:aer": 22, "vftv:ordo": 32, "vftv:sonus": 8, "vftv:vacuos": 9}}},
  {"seed": "0x123456789ABCDEF", "pos": [29999999, 319, -29999999], "clarity": [0, 60, 40],
   "aspects": [["vftv:aer", 16, 32], ["vftv:ordo", 24, 48], ["vftv:sonus", 4, 12], ["vftv:vacuos", 4, 12]],
   "expect": {"key": "0x7270DFE363C8113F", "h": "0x70536F2D4B1B14FE", "clarity": "steady", "maxes": {"vftv:aer": 25, "vftv:ordo": 26, "vftv:sonus": 4, "vftv:vacuos": 12}}}],
 "offers": [
  {"pages": ["test:p1", "test:p2", "test:p3", "test:p4", "test:p5", "test:p6",
             "test:p7", "test:p8", "test:p9", "test:p10", "test:p11", "test:p12"],
   "pages_per_claim": 3,
   "steps": [
    {"use": {"tick": 0, "player": "A", "site": "R1"}, "expect": {"outcome": "given", "pages": ["test:p1", "test:p2", "test:p3"]}},
    {"latch": {"player": "A", "pages": ["test:p1", "test:p2"]}},
    {"use": {"tick": 1, "player": "A", "site": "R1"}, "expect": {"outcome": "reissued", "pages": ["test:p3"]}},
    {"use": {"tick": 1, "player": "A", "site": "R1"}, "expect": {"outcome": "attempt_cap"}},
    {"use": {"tick": 2, "player": "A", "site": "R2"}, "expect": {"outcome": "given", "pages": ["test:p4", "test:p5", "test:p6"]}},
    {"latch": {"player": "B", "pages": ["test:p1"]}},
    {"use": {"tick": 2, "player": "B", "site": "R1"}, "expect": {"outcome": "given", "pages": ["test:p2", "test:p3", "test:p4"]}},
    {"latch": {"player": "A", "pages": ["test:p3"]}},
    {"use": {"tick": 3, "player": "A", "site": "R1"}, "expect": {"outcome": "done"}},
    {"remove": ["test:p5"]},
    {"use": {"tick": 4, "player": "A", "site": "R2"}, "expect": {"outcome": "reissued", "pages": ["test:p4", "test:p6"]}},
    {"use": {"tick": 5, "player": "A", "site": "R3"}, "expect": {"outcome": "given", "pages": ["test:p7", "test:p8", "test:p9"]}},
    {"latch": {"player": "C", "pages": ["test:p1", "test:p2", "test:p3", "test:p4", "test:p5", "test:p6",
                                        "test:p7", "test:p8", "test:p9", "test:p10", "test:p11", "test:p12"]}},
    {"use": {"tick": 5, "player": "C", "site": "R1"}, "expect": {"outcome": "all_held"}},
    {"use": {"tick": 6, "player": "C", "site": "R1"}, "expect": {"outcome": "all_held"}}]}]}
```

**Rejected:** a section missing; an unknown field; a conditional field present when its condition is false, or absent when true; a value outside its type or range; a row of a vector table missing or altered; ticks that decrease. The file above is the one the content ticket ships, so it passes every rule. The fixtures model arithmetic and offer selection only; the guard, callbacks, persistence and generation are proved by the GameTests.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text, and its review applies the research spec's number-word lint.

| Key | Args | Sense |
|---|---|---|
| `block.vftv.reliquary` | 0 | "Reliquary" |
| `entity.vftv.answerer`, `entity.vftv.answerer_cantor` | 0 | "Answerer", "Cantor" |
| `node_type.vftv.choir` | 0 | "Choir" |
| `reliquary.vftv.silent` | 0 | "The reliquary is silent." |
| `reliquary.vftv.unreadable` | 0 | "My notes of these places won't come clear." |
| `reliquary.vftv.done` | 0 | "I have taken all that this reliquary holds for me." |
| `reliquary.vftv.reissued` | 0 | "It gives back what I lost." |
| `reliquary.vftv.all_held` | 0 | "I already hold every page these ruins keep." |
| `reliquary.vftv.full` | 0 | "I can keep no more of these places in mind." |
| `reliquary.vftv.given` | 0 | "The reliquary yields its pages." |
| `subtitles.vftv.<event>` for every sound event above | 0 | "Answerer mutters", "Answerer hurts", "Answerer dies", "Cantor chants", "Reliquary gives pages", "Reliquary is still" |

**Rejected:** a key outside the table; a required key missing in `en_us`; a placeholder count that differs; an empty string; a duplicate key, including any other spec's key redeclared here.

## Events

**None of our own.**
- The finds post vanilla's advancement award and NeoForge's `AdvancementEvent.AdvancementEarnEvent` on the server, hidden advancements included (`NF:event/entity/player/AdvancementEvent.java:32–48`), which the research spec consumes.
- A page claim is a block use (`RightClickBlock` before it; the items after).
- Answerer deaths are vanilla's.

No sibling needs more.

**Plain `core` services:** `ChoirSitePlaceQuery` (`core.worldgen`, registered with `core.warp`); `ReliquaryService.use(player, pos)` (server only). They are **internal in v1** ([API spec, Services](api.md#services)).

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| claims | `vftv:choir_claims` player attachment, `copyOnDeath` | never |
| the Reliquary's site | its block entity `{format 1, site}` | never (the client needs only the block state) |
| structure nodes | the wands spec's node block entity | the wands spec's |
| Answerers | vanilla's entity data, with home and persistence saved (`MC:Mob.java:393–394,418–420`) | vanilla's, plus a `chanting` boolean in `SynchedEntityData`, transient (false on load, never saved) |
| sites | vanilla's per-chunk structure references | with the chunk |
| the "claimed anywhere" sets, the guards, the attempt counts, the resolved `#vftv:choir_sites` | memory | never |
| tuning | `data/vftv/tuning/worldgen.json` | never; the client needs none |

- **No payload of our own.** Messages use the vanilla action bar.
- **The Reliquary's record:**
  - **The owned record** is the block entity's saved tag **minus vanilla's own keys** (`id`, `x`, `y`, `z`, and the components NeoForge and vanilla write for every block entity). Only the owned keys decide what follows.
  - **Absent or empty owned record** (a genuinely data-free placement: a creative player, or `/setblock` without data): it initializes to `{format 1, site vftv:choir_ruin}` on its first server load and calls `setChanged()`. This is the only path that initializes.
  - **Any non-empty owned record that isn't a readable format-1 record is kept raw**, written back unchanged, and is silent (row 2) with one warning. That covers a missing `format` (for example `{site: "pack:road"}`), a missing `site`, a value of the wrong type, an unknown or future format, a broken bound, or a `site` that doesn't parse. It never becomes a built-in site.
  - A parseable `site` naming no kind is kept as written and is silent too, so restoring the pack restores it.
- **Saving:** the Reliquary marks `setChanged()` on initialization. Claims save with the player. Nothing else of ours persists.
- **Bounded codecs:** the claims record under "Claims"; the block entity's `site` ≤ 256 characters. Out of bounds is rejected at decode, and the record then stays raw.

**Reloads:**

| Change | Effect |
|---|---|
| Tuning (`/reload`) | a new record; the chant reads radius and target count at its next chant and the interval at its next scheduling; homes already saved keep their radius |
| Tags (`/reload`, `ServerDataLoad`) | `#vftv:choir_sites`, `#vftv:answerers` and `#vftv:choir_weathering` re-resolved |
| Registries (`vftv:choir_site`, the research registry) | need the world reopened |
| Worldgen files (a datapack override) | apply to chunks generated afterwards |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/worldgen.json`**, loaded on reload into an immutable record in `core.worldgen` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. Every rule above is defined across these full ranges.

| Key | Value | Range | Notes |
|---|---|---|---|
| `answerer.home_radius` | 24 | 8–48 | blocks; saved per mob when it is finalized |
| `cantor.interval` | 100 ticks | 20–1,200 | |
| `cantor.radius` | 8 | 2–16 | blocks |
| `cantor.max_targets` | 6 | 1–16 | the Cantor included |
| `cantor.duration` | 120 ticks | 20–600 | |
| `cantor.amplifier` | 0 | 0–1 | Resistance level − 1 |

**Other homes:**
- spacing 36, separation 14, the salt, pool weights, weathering and biomes: `content/worldgen/choir_ruin.json`, written into worldgen JSON a datapack can override;
- the fixed structure fields: code ("The structure");
- `pages_per_claim` 3 and the page order: the `vftv:choir_site` registry (`content/worldgen/sites.json`);
- node ranges and clarity weights: `content/worldgen/node_types.json`;
- loot: the loot tables (`content/worldgen/loot.json`);
- the Answerers' stats: code, fixed at registration and asserted by unit tests.

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`WorldgenLimits`** in `api`:

| Constant | Value |
|---|---|
| `RUIN_NODES` | 1 (authored per chancel; at most 1 placed) |
| `MAX_TEMPLATE_SIDE`, `MAX_WEATHERING_RULES` | 48, 8 |
| `MAX_PAGES_PER_SITE`, `MAX_PAGES_PER_CLAIM` | 64, 8 |
| `MAX_CLAIM_SITES` | 65,536 |
| `MAX_USES_PER_PLAYER_PER_TICK` | 1 |
| `MAX_SITE_STRUCTURES` | 8 |
| `MAX_ALERT` | 16 |
| `CHANT_SHOW_TICKS` | 40 |

**Also fixed in code:**
- the salt `CHOR` of the node roll;
- the jigsaw `size` 2 and the 40-block distance;
- the connector table;
- the row order of "Using it";
- the goal priorities;
- the ambient sound interval of 240 ticks.

## Performance

**Heavy-base workload,** sustained through warm-up and the 6,000 measured ticks:
- **One naturally generated ruin, populated by the fixture to its maximum:** 9 Answerers and its 1 Cantor. The player fights 6 Answerers and the Cantor inside their homes, so the Cantor chants at the default interval. The fixture revives the dead every 30 s (`/summon` with the home set), so the fight never ends.
- **A synthetic second ruin**, placed by the fixture 128 blocks away (labelled synthetic: natural starts are at least 240 blocks apart). Its 10 are loaded and idle, outside every follow range.
- **The player uses its Reliquary every 2 s.** Outside the measured operation, the fixture alternately clears the player's claim and latches (so the use is a `given`) and deletes the handed pages from the inventory (so the next use is a `reissued`). The cycle includes recovery that succeeds.
- The [warp spec's workload](warp.md#performance) runs its checks with `ChoirSitePlaceQuery` registered.

**Client:** the same ruin in view: 10 Answerers rendering, the Cantor chanting, the Reliquary and the node on screen.

**Share:** server ≤ **0.12 ms mean, ≤ 0.30 ms p99**, measured in the combined stress world. That includes the Answerers' vanilla AI and pathfinding (**native**, disclosed), the alerts' and chants' AABB queries and effect callbacks, the Reliquary's inventory insertions and drops, and the place query. Client ≤ **0.04 ms p50**. Shares are acceptance targets, not measurements.

**The envelope's running totals** (planning arithmetic, never a combined percentile): the finished specs' server shares total **0.55 ms mean / 2.22 ms p99** with the Hush; with worldgen, **0.67 / 2.52**, leaving **0.33 / 0.48** for the Eldritch tier. Client shares total **0.84 ms p50**, **0.88** with worldgen, leaving **0.12**.

**Measured separately as load scenarios** (reported):
- 64 players using Reliquaries in one tick;
- a reload bringing back 10 persistent Answerers at once;
- maximum tuning (`cantor.radius` 16, `cantor.max_targets` 16, `cantor.interval` 20) in a crowd of 40 Answerers;
- the pregeneration scenario below.

**Hot paths:**

| When | Work |
|---|---|
| Per Answerer tick | vanilla goals; our home and target checks O(1); vanilla's target selection, one AABB every few ticks (**native**, O(entities in its sections)) |
| Per alert | 1 AABB of radius 20 (the follow range), then sorting its E results, **O(E log E)**, and ≤ 16 target changes |
| Per chant | 1 AABB of radius ≤ 16, sorting O(E log E), ≤ 16 `addEffect` calls with their callbacks (**native**) |
| Per Reliquary attempt | the guard and the per-player attempt cap (1 per tick), checked **before** any scan; then an O(1) claim lookup, ≤ 64 page checks against the research state and the claimed set, and ≤ 8 hand-outs, each `Inventory.add` (**native**, O(slots)) and possibly a drop (an entity allocation and `ItemTossEvent`) |
| Per place query | 1 `getChunkNow` + ≤ 8 map lookups; **zero allocation** (an allocation-budget test) |
| Advancements | vanilla's `inventory_changed` (per changed stack) and `player_killed_entity`, O(the player's live criteria) |
| Per tick | nothing of ours runs without a mob, a use or a query |
| Packets | none of ours; the `chanting` flag twice per chant |

**Generation time** runs on worker threads, outside the tick share, and is measured separately:
- **Per chunk:** the structure set's placement test is two `nextInt(22)` draws per random-spread calculation, more only on vanilla's rare rejection loop (vanilla's).
- **Per start:** one jigsaw assembly. The final piece count is ≤ 13, a bound kept separate from the search.
  - **The search:** a pool repeats each element once per unit of weight (`MC:levelgen/structure/pools/StructureTemplatePool.java:48–57,109–110`), and the placer iterates those occurrences, then rotations, then connector matches (`MC:levelgen/structure/pools/JigsawPlacement.java:343–359,391–392`).
  - **Shipped weights:** ≤ 4 sockets × 4 wing occurrences × 4 rotations plus ≤ 8 end sockets × 4 non-empty end occurrences × 4 rotations = **192** non-empty element and rotation attempts per ruin.
  - **Across the authored weight range 1–16:** ≤ **2,048**.
  - An empty element ends its socket's search. Connector checks come first and can skip the voxel-shape collision test (**native**), so collision tests number at most the attempts.
- **Per placed template block:** `block_ignore`, `jigsaw_replacement`, `protected_blocks` (one `getBlockState`), ≤ 8 weathering rules, and `roll_node` (O(1), except at the ruin's node: one `getBlockState` and ≤ 7 draws).
- **Per ruin:** ≤ 10 entity creations.
- **The pregeneration scenario** (reported; gating at release checkpoints): pregenerate a 128 × 128-chunk square around spawn on the reference machine, twice. One run uses the shipped data. The other uses a test datapack that sets the structure set's `frequency` to 0.0 (`MC:StructurePlacement.java`: `nextFloat() < 0` never holds).
  - **Target:** the added wall time ≤ **3 %** *(ours)*, and no server-thread stall over 50 ms attributable to us.
  - **Also reported:** the ruins generated (the rarity hypothesis) and the pieces per ruin.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **every vector** in this spec, replayed from `content/worldgen/fixtures.json`. The placement rows run through vanilla's own `RandomSpreadStructurePlacement#getPotentialStructureChunk`; the roll and the offer rows through ours;
- **the offer function:** every row of "Using it", including unknown pages filtered while their claims are kept, `all_held` recording nothing, and the attempt cap;
- **codecs:** the claims record (bounds, defaults, unknown and future formats kept raw, duplicate keys refused, unknown ids kept), the Reliquary record (fresh initialization against raw retention), the site registry entry, the tuning file and every `content/` schema;
- **every `content/` rejection rule:** templates (each listed rule, including role counts, footing, paths, connectors and namespaces), `choir_ruin.json` (including the 55-biome coverage), `sites.json`, `node_types.json`, `loot.json` and the fixtures;
- the Answerers' stats; `#vftv:answerers` ∩ `vftv:voice_spawnable` = ∅;
- the place query's allocation budget.

**Datagen-phase:**
- the **parsed-NBT no-Hush test** over every generated template, worldgen, processor and loot file ("Templates");
- each generated template parses back and round-trips its palette, block entities and entities; one node in each chancel and none elsewhere. **Each chancel's node NBT equals the pinned record** under "Processors" exactly, and every entity's saved feet position is its cell's bottom centre;
- **the advancements:** exact JSON, no parent, and every criterion `inventory_changed` or `player_killed_entity`. **The kill predicate decodes** through vanilla's advancement codec, matches an Answerer and a Cantor, and doesn't match a zombie.

**GameTest** (connected `ServerPlayer` fixtures for interaction and advancements, since a direct helper block use skips `RightClickBlock`; `NF:common/util/FakePlayerFactory.get(level, profile)` with distinct profiles where only claim counts matter, `FakePlayerFactory.java:32–35`, whose fake handler swallows messages, `FakePlayer.java:96–103`):
- **The Reliquary:**
  - two players each get `beneath_the_notes` from one Reliquary;
  - **129 distinct fake players** each get theirs, and the 129th's claim is identical to the first's;
  - every row of "Using it" with real players;
  - **reissue at once** after the page is tossed, after a full inventory dropped it, and after a cancelled `ItemTossEvent`;
  - a throwing insertion callback (the claim kept, the rest still attempted);
  - **re-entry:** a callback that uses the Reliquary again is refused `busy`;
  - **two uses in one tick:** the second is `attempt_cap` and scans nothing;
  - claims through save, reload and death; a dormant claims record (raw kept, row 3);
  - **the Reliquary record, case by case:** a genuinely data-free `/setblock` initializes to `vftv:choir_ruin`. Each of these stays raw, silent (row 2) and byte-identical through save and reload, never initialized over: a missing `format` (`{site: "pack:road"}`), a missing `site`, a wrong-typed `site`, a future `format`;
  - a cancelled `RightClickBlock` changes nothing;
  - survival breaking, a creeper's explosion and a piston leave it in place.
- **The structure** (`/place structure vftv:choir_ruin`, which assembles pieces without registering references):
  - assembly within the piece envelope;
  - the node minted with `origin: structure` and the rolled record of its position's vector (clarity, maxes and currents replaced; every other field as pinned);
  - **a missing type** (the test pack removes `vftv:choir`): the placed node's record equals the pinned, unrolled template record field for field, and the node is dormant;
  - **two copies of one template get two distinct ids**;
  - **a third structure node in one cell is removed** with a warning;
  - an existing node under a template's node or block is kept (`protected_blocks`);
  - **an obstructed node target** (a block at the belfry cell) leaves the terrain and places no node;
  - chests carry `vftv:chests/choir_ruin`;
  - template entities are Answerers with homes, at full health.
- **Answerers:**
  - persistent beyond 128 blocks;
  - return home, and drop a target that leaves home or range;
  - hurt by a zombie, they ignore it;
  - **`validTarget` everywhere:** retaliation by melee and by an arrow, each with the player valid and with the player inside the home but beyond follow range (no target, no alert);
  - **cross-role alerts:** hitting the Cantor alerts the Answerers for which the player is valid, and none whose home doesn't contain the player. A recipient whose home contains the player but who stands beyond follow range from them stays untargeted;
  - the chant runs **concurrently** with melee (the Cantor attacks and chants in the same window), reaches ≤ `max_targets` within the radius, and waits a full interval after a reload;
  - a lowered `cantor.radius` applies at the next chant;
  - peaceful creates none in a newly placed ruin and discards existing ones;
  - drops;
  - a warp spawn event naming `vftv:answerer` is rejected at load.
- **Finds:**
  - picking up pointed dripstone grants `vftv:find/distillation`;
  - picking up a page grants `vftv:find/ruins`;
  - killing an Answerer with a sword, and with an arrow, grants `vftv:find/answerers`;
  - after the research spec's `reset`, moving the held dripstone stack relatches it, and changing an unrelated slot doesn't;
  - with the research spec, **the prefix bootstrap:** a page acquired in Stage 2 latches the Stage 3 opener, which opens once Stage 2's openers allow, and only then tips in.
- **Natural generation.** Vanilla's standalone GameTest server fixes seed 0, turns structures off and uses the flat preset (`MC:gametest/framework/GameTestServer.java:92,124–134`), and the built-in test environments don't replace its generator (`TestEnvironmentDefinition.java:40–47`). `GameTestHelper` has no generate-with-references helper.
  - **So these GameTests run in the persistent harness world**, a normal overworld at a fixed seed. `/test` runs a test in the command source's `ServerLevel` (`MC:gametest/framework/TestCommand.java:190–207,465–468`), and the test generates untouched chunks through the ordinary chunk pipeline, which creates starts and references (`MC:world/level/chunk/status/ChunkStatusTasks.java:44–56,70–73`).
  - `/place` still can't establish these assertions.
  - The placement vectors give candidate chunks, not biome-approved starts. The fixture selects an **actual** valid ruin in that world and records its start chunk.
  - Save and restart are the harness's lifecycle work.

  The tests:
  - the place query is true in every chunk of the terrain-expanded box, at the top and the bottom of the build height;
  - false one chunk outside;
  - true in a referenced chunk while the **start chunk is unloaded**;
  - false, with nothing loaded, at an unloaded query chunk;
  - all of it unchanged after save and reload.
- Every test id in the discovery guard.

**E2E:**
- walk from spawn to a seeded ruin: screenshots of the tower from 100 blocks and of the chancel, and the assembled ruin walkable from each wing to the Reliquary;
- fight the Answerers and the Cantor (the chant pose and sound);
- claim at a first Reliquary (a dump of the claims attachment and the inventory), **tip in the whole batch** (the research spec's), relog and use it again (`done`). Claim at a second ruin's Reliquary, toss one of its pages untipped, and use it again to get that page back (`reissued`, with dumps);
- sync on join and reconnect;
- the asset, lang, model and sound validators;
- the pregeneration scenario.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts on this ticket):

| Research contract | Met by |
|---|---|
| Ships the hidden advancements `vftv:find/distillation` and `vftv:find/ruins` at those ids, attainable with only NeoForge installed; a test proves no opener prerequisite cycle | "The advancements": vanilla triggers on vanilla dripstone and our page, no parents. The cycle test is structural (datagen) **and** behavioural: the research spec's affordability replay acquires each find from the source published below in the Stage before (the research spec amended). |
| Per-player Choir page blocks: persistent claims once per player per page per site, no eviction, a free reissue for a claimed page neither latched nor learned; one player's claim never consumes another's | "The Reliquary": positional claims on the player, no eviction, raw retention; `reissued` free and immediate, guarded and capped at one attempt a tick; unknown pages filtered, never handed out; no shared state. GameTests: reissue after a toss, a full inventory and a cancelled drop; re-entry; dormant records. |
| `vftv:choir/beneath_the_notes` obtainable in Stage 3 with no Stage 3 research; a GameTest has two players obtain it independently from one ruin | First in every ruin's order with no `requires`; GameTest with two connected players |
| Publishes source identities, their prerequisites, quantities and pre-spend reachability for the Stage 2 and 3 affordability paths | The research spec's "World sources" (amended): acquisition of `vftv:find/distillation` ends Stage 1's path, acquisition of `vftv:find/ruins` ends Stage 2's, and the pages are tipped in in Stage 3's, each with its `count`, `requires` and `why`, and batch reachability from distinct ruins |
| (Tests) the 129th claimant still gets their page | No capped history anywhere; GameTest with 129 distinct fake players |

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)'s contracts):

| Wands contract | Met by |
|---|---|
| Templates save node blocks with the id unset, `origin: structure` and a valid type; ≤ 2 structure nodes per cell across every copy, surplus removed; at most one node-bearing piece per cell; GameTests for two copies and a third node | "Processors, The saved template record": the converter saves a complete node record with id 0 and `structure` (pinned for `vftv:choir`, asserted on the generated NBT), and `roll_node` replaces only the rolled fields. "Structure nodes": one authored node, only in the start piece, and the separation proof (194 > 64). GameTests for both cases. |
| Places no natural nodes; doesn't remove `vftv:add_nodes`; processors never replace node blocks | "Stage"; "Processors": `protected_blocks` first, weathering confined to vanilla masonry, no `block_rot` or `block_age`, and `roll_node` dropping a node whose target isn't air (the wands spec's air-only rule). GameTests for an existing node under a template and an obstructed target. |

**Met here** (the [Infusion spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)): no advancement shipped for progression requires an infusion craft criterion. Every criterion is `inventory_changed` or `player_killed_entity` ("The advancements"), checked by a datagen test.

**Met here** (the [Warp and the Voices spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)): a place query for Choir sites backed by vanilla's per-chunk structure references written at generation, read and never followed, loaded-only and bounded by 8 lookups ("The Choir-site place query"). No Void-touched mob is added to `vftv:voice_spawnable`, so "Danger"'s extension isn't invoked. The Answerers are never Voice spawns (disjoint tags, tested).

**Met here** (the [Flux and the Hush spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)): no feature, structure, processor or loot places a hushed block, a `vftv:hushed` node, a Hollow or an aura. This is checked on every generated file **and** on every generated template's parsed NBT palette, block-entity and entity data, including any `neoforge:attachments` ("Templates", "Processors", "Loot"). The place query is independent of the Hush's.

**Met here** (the Stage openers): the [crucible](crucible.md#stage), [essentia](essentia.md#stage), [foci](foci.md#stage), [golems](golems.md#stage), [armor](armor.md#stage), [warp](warp.md#stage), [Hush](hush.md#stage) and [infusion](infusion.md#stage) specs hang their Stage 2 and 3 content off `vftv:distillation` and `vftv:ruins`, whose finds ship here at the exact ids. That adds no placement authority.

**Met here** (the [progression](progression.md#stages) and [architecture](architecture.md#soft-dependencies) specs): every Stage is opened by a find in the world, per player (a geode, dripstone, a ruin's page); nothing needs a soft dependency.

**Met here** (the [lore spec](lore.md#cosmology)): the Answerers are hostile, living, faced, found at Choir sites and renamed from TC4's cult; the Choir's pages come from its ruins.

**Met here** (the [quality gates](quality-gates.md#what-each-system-spec-must-declare)): a sustained workload, a share, hot paths with fixed limits and disclosed native terms, an allocation budget, and generation time accounted separately ("Performance").

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 1](https://github.com/andrewbrogoff/voices-from-the-void/issues/34), [Stage 2](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) and [Stage 3](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) research content:
- **The query** `isLatchedOrLearned(player, entry)` beside `isLearned` and `stage`.
- **World sources**, in the research spec's "Affordability paths":
  - **#34:** `path-stage1.json` ends by `use` of `{"advancement": "vftv:find/distillation"}`: Distillation evidence.
  - **#35:** `path-stage2.json` ends by `use` of `{"advancement": "vftv:find/ruins"}`: acquiring the first ruin's batch latches Ruins. The early Clearing Bell order stays as set.
  - **#36:** `path-stage3.json` tips the pages in, through `{"choir_page": <id>, "site": "vftv:choir_ruin"}` sources.

  Acquisition and tip-in are one copy each, never published as two guaranteed copies.
- **#34 and #35** are blocked by this spec. #34's hint for the Stage 2 opener points at stone that weeps into stone (dripstone caves), with traders as an aside. #35's hint for the Stage 3 opener points at bell towers over the treeline in unexplored land, and the Reliquary at their heart.
- **#36:** the Answerers' find entry is a **leaf** (nobody's `parents` or `hidden_parents`; never a path source). #36 freezes the Stage 3 pages' **narrative order**, `beneath_the_notes` first, which the worldgen content ticket transcribes into `sites.json`.

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)** (amended in this change): `vftv:choir` joins the built-in node types and the global required-type and name coverage. Its entry and `node_type.vftv.choir` are excluded from the wands spec's own content files and production brief, exactly as `vftv:hushed` is. Datagen checks after merging and rejects duplicates. Minting, quotas and reconciliation are used exactly as written.

**[Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- It may add `vftv:choir_site` kinds, place Reliquaries for Stage 4 pages, add structures to `#vftv:choir_sites` and reuse `#vftv:answerers`. It publishes its own Stage 4 sources and reachability.
- Its structure nodes share the wands spec's two structure places per cell with ruins, reserve nothing, and follow the rules under "Processors". Its templates meet the parsed-NBT no-Hush check, which covers every generated template.
- It owns the progression spec's stale-dimension note.
- *Met by the [Eldritch tier spec](eldritch.md#contracts-on-sibling-specs): the site kinds `vftv:threshold` and `vftv:road` on this spec's Reliquary, the Threshold in `#vftv:choir_sites` (amended here) defended by Answerers, and its Stage 4 sources and reachability; no node in any of its templates, so no quota is claimed; its `threshold` and `road` template families under the family scoping amended here, with the no-Hush test over every one; its "Existing worlds and removing the mod". Amended here too: the built-in site checks scoped to `vftv:choir_ruin`.*

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- Hand values for the entities `vftv:answerer` and `vftv:answerer_cantor`, and a block value or a reviewed exemption for `vftv:reliquary`. No new item or recipe adds a conversion to prove.

**Configuration**
- No option of ours. Peaceful removes the defenders, not the ruins. A pack disables ruins by overriding the structure set (for example `frequency` 0.0).

**API**
- In `api`: the marker interface `ChoirSite` (the registry is server-only), `WorldgenKeys` (the `vftv:choir_site` registry key, the `#vftv:choir_sites`, `#vftv:answerers` and `#vftv:choir_weathering` tag keys, the `vftv:choir_ruin` structure and site keys, the `vftv:choir` node-type key and the `vftv:choir_claims` attachment's id) and `WorldgenLimits`.
- The `vftv:choir_claims` `AttachmentType` is **not** public: its id is published, and direct use is unsupported ([API spec, Lifecycle and ownership](api.md#lifecycle-and-ownership)).
- Services, models and codecs stay implementations, all internal in v1.

## Production tickets

This spec settles every decision. Four required child tickets deliver the assets and the authored content, and **the system is not done until all four land**. **None of them writes mod code:** no block, entity, AI goal, processor, query, attachment, listener, renderer, datagen provider or validator. The asset tickets deliver generators and assets under the [art style](art-style.md); the content tickets deliver authored inputs under `content/`, in the schemas above. The advancements, tags, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Choir ruin templates** | **Generator:** `tools/art/choir_ruin.py`, seeded and byte-identical on re-run, writing the eight `content/worldgen/templates/*.json` in `vftv:template/1` at the sizes, connectors, role counts, footing and paths under "Pieces and topology". The chancels each carry a broken bell tower ≥ 20 blocks above the floor, the Reliquary, one node in the belfry ≥ 8 above the floor, 1 Answerer and 1 Cantor. Wings carry ≤ 1 chest and 0–2 Answerers. Vanilla blocks in the Stage 3 register only ("Templates"), plus `vftv:reliquary` and `vftv:node`. **Review:** an isometric preview of each template with its standing cells and paths marked, and a Blender render of one assembled ruin (chancel, four wings and their ends in fixed slots) at noon and at dusk. Provenance rows. | none |
| **Worldgen content** | `content/worldgen/choir_ruin.json` (as shipped above), `sites.json` (the page order transcribed), `node_types.json` (exactly the shipped entry), `loot.json`, `fixtures.json` (the file above) and `content/lang/en_us/worldgen.json` (every key under "Lang keys") | [Stage 3 research content #36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36), only for its frozen page order (#36 depends on this spec, not on this ticket) |
| **Answerer assets** | **Generator:** `tools/art/answerers.py`. `tools/art/answerers/answerer.bbmodel`, an editable model **with exactly the parts, pivots, cuboids, UV origins and mirroring of "Rendering and sound"**, its four poses (idle, walk, attack, chant), and a UV guide `answerer_uv.png`. `assets/vftv/textures/entity/answerer.png` and `answerer_cantor.png` (64 × 64): mauve cloth, ink outline, visible faces, brass mouthpiece, the Cantor's cracked bell, no teal. **Sounds:** the files and `sounds.json` entries of the Answerer and Cantor rows under "Sound", naming their subtitle keys, no overrides. **Review:** Blender renders front, side and at 16 blocks beside a vanilla villager and the warp spec's apparition silhouette, plus each pose. Provenance rows. | none |
| **Reliquary assets** | **Generator:** `tools/art/reliquary.py`. The block model (at most 6 cuboids, as under "Models"), its texture, blockstate (four facings), item model and `assets/vftv/items/reliquary.json`. **Sounds:** the files and `sounds.json` entries of the Reliquary rows under "Sound". **Review:** a contact sheet at GUI scale 2 beside a vanilla lectern and vault, and a Blender render. Provenance rows. | none |

**Handed off, not a child of ours:** [#30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) gives the two Answerer types and the Reliquary their aspect values. [Stage 1 #34](https://github.com/andrewbrogoff/voices-from-the-void/issues/34), [Stage 2 #35](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) and [Stage 3 #36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) research content consume the finds, sources, site id, hints and the leaf rule frozen here.
