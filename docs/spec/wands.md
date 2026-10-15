# Wands, vis and nodes

The one vis economy: the wand that stores vis and the single API that spends it, the nodes that are its only source, and how nodes are found, tapped, cultivated, moved and shared, up to the Stage 2 relay network that carries vis to charging points. Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). The code layout, data rules and sync rules come from the [architecture spec](architecture.md), every gate from the [quality gates](quality-gates.md), striking and sounding from the [aspects spec](aspects.md), research gates and the book from the [research spec](research.md), and every asset from the [art style](art-style.md).

Resolved in [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17). Inputs: [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md), [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. Each tunable one is listed with its data home under "Tuning". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 1 Resonance | the wand and its Stage 1 parts, the vis store and the spend API, natural nodes, the starter node, hand-tapping | the Stage is opened by the amethyst geode ([research, Stage](research.md#stage)). Inside it, the wand entries hang off `vftv:knots_in_the_air`, whose find is a strike on any node, so **a node is the find that opens wands**. The starter node makes it a short walk. |
| 2 Distillation | the chime rod and Resonant Brass cap, cultivation, the Bell Jar, the Node Tap, Vis Relay and Wand Cradle | the opener `vftv:distillation`, whose find is [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s |

- **Gate actions, not the craft.** No hook proves a research gate on the vanilla crafting grid before the result is taken: `CraftingInput` carries no player, and `ItemCraftedEvent` fires too late and cannot be cancelled. So the Stage 1 wand is an **ungated** vanilla crafting-table recipe, and what is gated is every action that draws or spends vis, checked on the server on each action: tapping needs `vftv:tapping`, and each spend needs its consumer's own research (see "Spending vis"). Stage 2 parts and blocks that need a gate are crafted at [Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)'s research-gated station.
- **Obtaining counts.** A wand can be crafted or handed over at any Stage. Until its holder learns `vftv:tapping` it cannot tap, and it spends only on actions whose own research its holder has learned.

## The wand

One item, **`vftv:wand`**, stacking to 1, whose parts are data components. A new combination of parts is new data, never a new item or model.

### Parts

Two **synced datapack registries**, fixed when a world opens, like the aspect set ([ADR 0003](../adr/0003-aspects-as-datapack-registry.md)): **`vftv:wand_rod`** (`data/<ns>/vftv/wand_rod/<path>.json`) and **`vftv:wand_cap`** (`data/<ns>/vftv/wand_cap/<path>.json`). Each part has **one job**: a rod sets capacity, a cap sets cost.

| Registry | Field | Required | Range | Meaning |
|---|---|---|---|---|
| `vftv:wand_rod` | `item` | yes | an item id, not air | the ingredient that is this rod (ours or vanilla) |
| | `capacity` | yes | 1–1000 | vis per pool |
| `vftv:wand_cap` | `item` | yes | an item id, not air | the ingredient that is this cap |
| | `cost_multiplier` | yes | 0.25–2.00, at most 2 decimal places | the cap factor in the quote |

| Entry | Item | Value |
|---|---|---|
| rod `vftv:wooden` | `minecraft:stick` | capacity 40 |
| rod `vftv:chime` | `vftv:chime_rod` | capacity 150 |
| cap `vftv:copper` | `minecraft:copper_nugget` | ×1.00 |
| cap `vftv:resonant_brass` | `vftv:resonant_brass_cap` | ×0.85 |

- **Required built-in keys** (`WandPartKeys` in `api`): the four entries above. Packs may override their values and add parts; they cannot delete these.
- **Load validation** refuses the world with a clear error naming the entry: an unknown item or air; one item used by two parts, across both registries; a value out of range; a required key missing.
- **Later tiers** (Stages 3–4) are entries added by [Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21) or [Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28), with no code. Stage 3's are the [infusion spec](infusion.md#the-echo-rod-and-cap)'s rod `vftv:echo` (`vftv:echo_rod`, capacity 300) and cap `vftv:echo` (`vftv:echo_cap`, ×0.70). An addon's part model needs an override of `items/wand.json`; two addons overriding that one file conflict, a known limitation handed to the API spec.
- Names are lang keys `wand_rod.<ns>.<path>` and `wand_cap.<ns>.<path>`.

### Assembly

One special crafting recipe type, **`vftv:wand_assembly`**, with one instance at the stable id **`vftv:wand_assembly`**. It has no research gate and costs no vis: the first wand must exist before any vis does.

- **Matches** a 3×3 crafting grid holding exactly three items, in one column and three consecutive rows: a cap item on top, a rod item in the middle and a cap item at the bottom. Both caps must be the same cap entry. Any rod goes with any cap.
- **Result:** one `vftv:wand` with `vftv:wand_rod` and `vftv:wand_cap` set and an empty `vftv:vis`. Each ingredient slot gives up one item and leaves no remainder. A new wand always starts empty: there is no disassembly and no upgrade in place.
- **Every vanilla crafting path behaves the same:** a click, a shift-click (crafts as many as the grid allows) and the vanilla Crafter block all take the same result, because nothing about the recipe depends on a player. Special recipes don't appear in the recipe book.
- **Display:** the instance supplies a shaped `RecipeDisplay` (cap, rod, cap in one column, each slot listing every registered part item of its kind), so the book's recipe page and JEI can draw it.

### Components

All persistent and network-synced.

| Component | Value |
|---|---|
| `vftv:wand_rod` | a bare `ResourceKey` into `vftv:wand_rod`: an id string, so the item model can select on it through its native codec without registry access. It has no format envelope. |
| `vftv:wand_cap` | a bare `ResourceKey` into `vftv:wand_cap`, likewise |
| `vftv:vis` | the pools (below), in a versioned envelope |
| `vftv:wand_focus` | the attached focus: `{"format": 1, "focus": <ItemStack>}`, the [foci spec](foci.md#on-the-wand)'s, absent on a wand with none |

**Model, no code for the parts.** `assets/vftv/items/wand.json` is a `minecraft:composite` of two `minecraft:select` models on the `minecraft:component` property, one for `vftv:wand_rod` and one for `vftv:wand_cap`, each with a `fallback` to its `unknown` layer. The [foci spec](foci.md#on-the-wand) appends a third member: a select on its item-model property `vftv:focus_effect`, whose `vftv:none` case is `minecraft:empty` (a bare wand draws no focus) and whose fallback is the focus `unknown` layer. The wand is drawn upright, caps top and bottom, with the teal tick beside the top cap ([art style §4](art-style.md#4-silhouette-language)).

```json
{"model": {"type": "minecraft:composite", "models": [
  {"type": "minecraft:select", "property": "minecraft:component", "component": "vftv:wand_rod",
   "cases": [
     {"when": "vftv:wooden", "model": {"type": "minecraft:model", "model": "vftv:item/wand/rod/wooden"}},
     {"when": "vftv:chime",  "model": {"type": "minecraft:model", "model": "vftv:item/wand/rod/chime"}}],
   "fallback": {"type": "minecraft:model", "model": "vftv:item/wand/rod/unknown"}},
  {"type": "minecraft:select", "property": "minecraft:component", "component": "vftv:wand_cap",
   "cases": [
     {"when": "vftv:copper",         "model": {"type": "minecraft:model", "model": "vftv:item/wand/cap/copper"}},
     {"when": "vftv:resonant_brass", "model": {"type": "minecraft:model", "model": "vftv:item/wand/cap/resonant_brass"}}],
   "fallback": {"type": "minecraft:model", "model": "vftv:item/wand/cap/unknown"}}
]}}
```

**Later tiers append cases.** [Wand assets](https://github.com/andrewbrogoff/voices-from-the-void/issues/38) delivers the file above first. A later tier's asset ticket then appends one case per part to the matching select, keeping every existing case, every fallback and the foci member; it never rewrites the file while #38 is open. **Every append is additive and serialised:** #38, then [Infusion assets](https://github.com/andrewbrogoff/voices-from-the-void/issues/47), then the foci spec's assets ticket (the third member), each blocked by the one before. A focus effect added later appends its case to the foci member the same way. The first is the [infusion spec](infusion.md#production-tickets)'s: `{"when": "vftv:echo", "model": {"type": "minecraft:model", "model": "vftv:item/wand/rod/echo"}}` in the rod select and `{"when": "vftv:echo", "model": {"type": "minecraft:model", "model": "vftv:item/wand/cap/echo"}}` in the cap select, with the layer textures `assets/vftv/textures/item/wand/rod/echo.png` and `…/wand/cap/echo.png`.

### Vis storage

**One pool per vis aspect, every pool at the rod's capacity, whole vis only.** Per-primal pools make *which* node you find matter, and one capacity for every pool is one number to read.

- **Vis aspects** are the **exposed primals**: registry aspects with `parents: []` and no `hidden_primal` ([aspects, registry format](aspects.md#registry-format)). The six built-in ones are Aer, Terra, Ignis, Aqua, Ordo and Perditio; an addon may add a seventh or eighth, which addon node types supply (the starter never does). Vacuos is never one. Making it one would take an eligibility extension that [Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) would have to propose; the limit of eight vis aspects leaves room, but doesn't do it alone. More than 8 exposed primals refuses the world (a fixed limit).
- **`vftv:vis`** is `{"format": 1, "pools": {<aspect id>: 0–1000}}`, at most 16 entries. A missing entry is 0. A value with an unknown format or out of bounds is kept as its raw tag and the wand is inert until it decodes (see "Sync and state").
- **Reads clamp** each pool to the current capacity, and the next write stores the clamp. A reopened pack that shrinks a rod can only lose the excess, never invent vis.
- **Entries for aspects that aren't vis aspects** (an aspect a pack removed, or one that stopped being primal) are **kept as they are** and ignored, so restoring the pack restores them. Writes carry them through. If a write would exceed 16 entries, the dormant entries with the highest ids are dropped and logged.
- **Inert wands.** A wand is inert when it is **unfinished** (its `vftv:wand_rod` or `vftv:wand_cap` component is missing, e.g. from `/give`) or when **a part is missing** (a component names a key the registry doesn't hold). An inert wand cannot tap, be charged or spend, and nothing writes its `vftv:vis`: no clamping, no debit. Its tooltip and HUD name the case and, for a missing part, the id. Restoring the pack restores the wand.

### Display

**Instruments never lie**, so every number is exact.

- **HUD layer `vftv:vis`.** It describes the wand in the **main hand**, or else the one in the offhand.
  - One row per vis aspect, in ascending aspect id: the aspect's 16×16 icon ([aspects, icons](aspects.md#icons)) and its exact `current` as text.
  - **Geometry** (GUI-scaled pixels): the frame is the nine-slice sprite `vftv:hud/vis_frame` (16×16, border 4). Inside it, a 4-px padding; rows 16 px tall with a 2-px gap; the icon at x = 4, the number at x = 24, vertically centred on the icon. The frame is **52 wide and 6 + 18*n* tall** for *n* rows (114 at six). An inert wand shows one text line instead, and the frame widens to fit it, up to 160.
  - **Position:** top-left at (`hud.x`, `hud.y`), default (4, 4), clamped so the frame stays on screen. Hidden with F1, while a screen or the debug overlay is open, and in spectator mode.
  - The layout is rebuilt only when the held wand's components, the hand, the part registries (on join) or the resources change; our draw code allocates nothing per frame.
- **Shift tooltip:** rod and capacity, cap and multiplier, and every pool as `current / capacity`. Without Shift, one line says how to read it.
- **Item bar:** total current ÷ total capacity over the vis aspects, 13 px wide like vanilla's, in `teal2` (`#82c9bc`). Hidden on an inert wand.

## Spending vis

One pure **quote** and one checked **spend**, in `core.vis`, used by every consumer. Vis enters only from nodes (tapping and relays) and leaves only through `spend`. There is no conversion, no transfer between wands and no other debit path.

### The context

Every quote and spend carries a **`VisContext`** (a record in `api`):

| Field | Meaning |
|---|---|
| `source` | the recipe or focus id, for events and logs |
| `kind` | `ORDINARY` or `VOID`. Warp makes ordinary magic dearer, and #25 decides what it does to Void work, so the factor needs to know which this is. |
| `research` | the research entry the action needs, or none. `spend` refuses if it is set and not learned. |

### The quote

`VisService.quote(player, wand, base, ctx) → VisQuote` in `core.vis` resolves the player, stack and registry inputs from synced data (the part registries, the attribute, `VisRules` (see "Sync and state") and #25's synced factor) into an immutable, Minecraft-free `QuoteInputs` record, then calls the pure **`core.vis.model` function `quote(QuoteInputs)`**, which never sees a `Player` or `ItemStack` (the architecture's `MODEL_IS_MINECRAFT_FREE` rule). Both sides compute it identically. **All arithmetic is integer per-mille after one conversion per input.**

- **Inputs,** resolved once into `QuoteInputs`:
  - `base`: a map from vis aspect to an integer 0–1000, at most 8 entries. A key that isn't a vis aspect or a value out of range is a data error: consumers reject such data when it loads, and `quote` throws.
  - `cap = Math.round(cost_multiplier × 1000)`.
  - **Discount:** the attribute `vftv:vis_discount` (#24's). `discount_capped` iff its value > `quote.max_discount`. `d = Math.round(clamp(value, 0, quote.max_discount) × 1000)`.
  - **Warp factor:** `W = vis_factor(player, ctx)` from [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25), 1.0 until #25 lands. A non-finite `W` counts as 1.0 and is logged once. `w = Math.round(clamp(W, 0, 10) × 1000)`.
- **Multiplier:** `raw = cap × (1000 − d) × w` in `long` arithmetic (at most 2 × 10¹⁰). `m₀ = (raw + 500,000) / 1,000,000`, which rounds half up. `m = clamp(m₀, Math.round(quote.min_multiplier × 1000), Math.round(quote.max_multiplier × 1000))`, and `clamped` iff `m ≠ m₀`.
- **Effective cost:** `effective[a] = base[a] == 0 ? 0 : max(1, ceilDiv(base[a] × m, 1000))`.
- **Spill:** [Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)'s `spill(inputs, ctx)`, a pure function of the resolved inputs and `effective` (never of a quote, which would be circular); 0 until #26 lands.
- **`VisQuote`** (a record in `api`): `base`, `effective`, `m`, `spill`; the **resolved factors** `cap`, `discount` (`d`) and `warp` (`w`), the per-mille integers above (so a station can show how much of `m` is warp, [arcane crafting, The screen](arcane.md#the-screen)); and the flags `discount_capped`, `clamped`, `exceeds_capacity` (some effective cost > the rod's capacity), `inert`, `affordable` (not inert, and every pool ≥ its effective cost) and `short` (the aspects whose pool is below their cost, in ascending id).
- **Echo equality:** two quotes are the same cost iff `base`, `effective`, `m`, `spill`, `discount_capped`, `clamped` and `exceeds_capacity` are all equal. The factors explain `m` and are not compared: `m` already covers them.

**Vectors** (computed by a reference implementation and cross-checked against the JDK; `quote.min_multiplier` 0.50, `quote.max_multiplier` 4.00):

| cap | discount | `max_discount` | W | base | factors (`cap`, `d`, `w`) | `m` | effective | modifier flags |
|---|---|---|---|---|---|---|---|---|
| 1.00 | 0 | 0.30 | 1.00 | Aer 10, Ignis 0, Ordo 1 | 1000, 0, 1000 | 1000 | Aer 10, Ignis 0, Ordo 1 | none |
| 0.85 | 0 | 0.30 | 1.00 | Aer 10, Ignis 3, Ordo 1 | 850, 0, 1000 | 850 | Aer 9, Ignis 3, Ordo 1 | none |
| 0.85 | 0.40 | 0.30 | 1.00 | Aer 10, Ignis 3, Ordo 1 | 850, 300, 1000 | 595 | Aer 6, Ignis 2, Ordo 1 | `discount_capped` |
| 0.85 | 0.10 | 0.30 | 1.25 | Aer 32, Terra 7 | 850, 100, 1250 | 956 | Aer 31, Terra 7 | none |
| 2.00 | 0 | 0.30 | 10.00 | Aer 1000 | 2000, 0, 10000 | 4000 | Aer 4000 | `clamped` |
| 0.25 | 0.90 | 0.90 | 1.00 | Aer 5 | 250, 900, 1000 | 500 | Aer 3 | `clamped` |

The modifier flags are `discount_capped` and `clamped`. The capacity flags depend on the wand: the 4000-cost row is `exceeds_capacity` for every supported rod (capacity ≤ 1000), and the others are not for the built-in rods.

### The spend

`VisService.spend(player, wand, base, ctx, expected, effect, boundary) → SpendResult`, on the server thread only. It is **one checked commit over vis, materials and output**, so they are taken together or not at all.

- **`effect` is a prepared state swap, not a callback.** It is an immutable list of at most 64 **slot changes** `(container, slot, expectedBefore, after)`, one per material slot it consumes and output slot it fills, and **at most one experience change** `(player, expectedLevel, after)` with `0 ≤ after < expectedLevel`, the spending player being the only one it may name. `spend` applies them itself; no consumer code runs inside the commit, so a consumer can't leave a half-applied change behind. Two changes may not name the same slot.
- **The experience change posts nothing.** NeoForge's `PlayerXpEvent.LevelChange`, which `Player#giveExperienceLevels` posts (`MC:world/entity/player/Player.java:1626–1629`), is the consumer's to resolve **before** it calls `spend`:
  - the consumer snapshots the player's level, and everything else its price depends on, **before** posting the event; refuses if it is cancelled or its levels are changed; rechecks the snapshot after it; and prepares the change against the **original** level as `expectedLevel`, so a listener that drained the level during the event is refused as stale, never charged a different price;
  - a **zero** price prepares no experience change and posts no event (`after < expectedLevel` holds for every change that exists);
  - the commit writes `experienceLevel` directly, leaving progress and total experience as they are, as vanilla's own level debit does.

  Its first consumer is the [foci spec's](foci.md#the-lathe-payload) rank-up.
- **Owner sync.** Vanilla resends experience only when it invalidates its last-sent value in `giveExperienceLevels` or when the **total** changes (`MC:server/level/ServerPlayer.java:544–550,748–751`), and a direct level write does neither. So after a commit that applied an experience change, `spend` sends the owner vanilla's `ClientboundSetExperiencePacket` with the current progress, total and level, and posts no second event.
- **`boundary`** (optional) is the consumer's **apply boundary**: `applied(committed)` is called **exactly once** when step 6 has finished, after every write (`committed` true) or after the rollback has restored every snapshot (`committed` false), and **before** step 7. It lets a consumer that suppressed its own change notifications during the swap end that suppression and record the swap as its own, so anything a `VisSpentEvent` listener changes afterwards is seen as a separate, external change. It may only update the consumer's own bookkeeping: no world, slot or vis change. An exception from it after a commit is handled like a step-7 failure; after a rollback the original error is rethrown, with the boundary's suppressed onto it.
- **Steps,** the first failure refusing with its message and changing nothing:
  1. the stack is a `vftv:wand` and not inert ("This wand is missing a part.");
  2. `ctx.research`, if set, is learned;
  3. the quote is recomputed. If `expected` is given and is not the same cost, refuse with "The cost has changed." UI paths (#18) always pass `expected`; same-tick paths (a cast) may pass none;
  4. `affordable`, else "Not enough …" naming every `short` aspect;
  5. every slot still holds exactly its `expectedBefore` (`ItemStack.matches`), and the player's level equals any experience change's `expectedLevel`, else "The cost has changed.";
  6. **commit under a rollback scope:** snapshot the wand's `vftv:vis`, every named slot and, with an experience change, the player's level; write the debited `vftv:vis` in place; set each slot to its `after` in list order; set the level to the experience change's `after`, then send the owner the experience packet (above). If anything throws, every snapshot is restored (vis, materials, output and level), the boundary is told `applied(false)`, no event is posted, and the error is rethrown and logged. Otherwise the boundary is told `applied(true)`;
  7. after the commit, post `VisSpentEvent` through the platform publisher. #26 applies its flux on that event. **If posting throws** (a listener failed), the commit stands: nothing is rolled back, the error is logged once with the `source`, and `spend` returns **`COMMITTED_NOTIFY_FAILED`** instead of rethrowing.
- **Outcomes:** `SPENT` and `COMMITTED_NOTIFY_FAILED` both mean vis, materials and output were committed; each refusal of steps 1–5 is a refusal outcome naming its message (inert, unlearned, stale, short) and changed nothing; only a step-6 failure throws, and it changed nothing either. A consumer never treats `COMMITTED_NOTIFY_FAILED` as a refusal or a rollback.
- Nothing but the boundary can run between steps 5 and 7: the server thread runs them in one call, and the consumer posts its own events only after `spend` returns.
- **Effects that aren't slot or experience changes** (a focus's work in the world) run after `spend` returns either committed outcome. The consumer checks they can happen before it spends; the spend's atomicity covers vis, slots and the experience change only, and #22 states what its casts do on a later failure ([foci, Failure after the spend](foci.md#failure-after-the-spend)).

## Nodes

### The node block

**`vftv:node`** is a block with a block entity: it persists with its chunk, vanilla raycasts target it, and spawn protection and claim mods already govern block interaction. An entity would tick and isn't position-indexed; virtual data couldn't be targeted.

- No collision; a selection shape 0.5 blocks across, centred, so the crosshair and the fork can target it. Render shape invisible (a renderer draws it, see "Rendering and sound"); no light emission.
- **Survival-unbreakable** (`destroyTime −1`), explosion-proof, `PushReaction.BLOCK`, not replaceable, not waterloggable. Tagged `minecraft:wither_immune`, `minecraft:dragon_immune` and `minecraft:features_cannot_replace`. Creative players can break it (see "Creation, removal and sharing").
- **It is only ever placed into air:** every placement path below checks that the target position `isAir()`. Nothing afterwards requires its surroundings to stay air.
- **Distances** between a position and a node are always to the node block's **centre**, in 3-D.

### Node types

A **synced datapack registry, `vftv:node_type`** (`data/<ns>/vftv/node_type/<path>.json`), fixed when a world opens.

| Field | Required | Range | Meaning |
|---|---|---|---|
| `aspects` | yes | 1–6 entries `{"aspect": <id>, "range": [min, max]}`, 1 ≤ min ≤ max ≤ 1000 | the aspects and the rolled max capacity of each |
| `main_aspect` | yes | one of `aspects` | the type's defining aspect; the coverage test uses it |
| `clarity` | yes | `{"faint", "steady", "clear"}`, integers 0–1000, sum > 0 | weights for the clarity rolled once per node |
| `natural` | no, default `false` | | eligible for natural spread |
| `biomes` | iff `natural` | 1–8 biome tag ids | where it may spread (any one suffices) |
| `weight` | iff `natural` | 1–1000 | weighted pick among the eligible types |
| `node_reward_divisor` | no | 1–64 | overrides the aspects tuning default for first-strike rewards |
| `anchored` | no, default `false` | | cannot be captured |

- **A node holds at most 6 aspects** (a fixed limit: the bound the aspects spec's performance asks for). Any aspect may appear; only vis aspects can be drawn, and the rest pay first-strike points only. Hush and Choir node types are entries here.
- **Required built-in key** (`NodeTypeKeys` in `api`): `vftv:wellspring`, which the starter places.
- **Load validation** refuses the world with a clear error naming the entry: an unknown or duplicated aspect; `main_aspect` not among `aspects`; a range or weight out of range; clarity weights summing to 0; `biomes` or `weight` present without `natural`, or missing with it; an unknown field. **`vftv:wellspring`** must also hold **exactly the six built-in primals** `vftv:aer`, `vftv:terra`, `vftv:ignis`, `vftv:aqua`, `vftv:ordo` and `vftv:perditio`, each with range **[32, 32]**, and each must still be a vis aspect in the loaded registry. It must also be anchored and not natural, with weight only on `clear`, because the starter guarantee depends on all of it. A seventh or eighth exposed primal from an addon is allowed and simply isn't in the Wellspring.
- A natural type whose biome tags resolve to no biome: built-ins fail a test; addons get a warning.
- Names are lang keys `node_type.<ns>.<path>`; clarities are `node.vftv.clarity.faint`, `.steady` and `.clear`.

**Built-in types.** The compositions are this spec's; the content ticket sets biome tags, weights and clarity weights.

| Type | Aspects (range) | Main | Natural | Spreads in |
|---|---|---|---|---|
| `wellspring` | the six built-in primals [32, 32] (fixed: validated at load) | Aer | no; anchored; always clear | the starter only |
| `windward` | Aer [16, 40], Terra [8, 24] | Aer | yes | hills, plains, mountains |
| `hearth` | Ignis [16, 40], Terra [8, 24] | Ignis | yes | hot, dry land |
| `tidewater` | Aqua [16, 40], Aer [8, 24] | Aqua | yes | ocean, river, beach |
| `mire` | Perditio [16, 40], Aqua [8, 24] | Perditio | yes | swamp, dark forest |
| `greenwood` | Terra [16, 40], Ordo [8, 24] | Terra | yes | forests |
| `stillness` | Ordo [16, 40], Aqua [8, 24] | Ordo | yes | snowy land |
| `wild` | the six built-in primals [4, 12] | Aer | yes, low weight | `#minecraft:is_overworld`, so every overworld column has an eligible type |

### Node state

Held by the node block entity, and by a filled Bell Jar, as `{"format": 1, …}` with the fields below.

| Field | Range | Meaning |
|---|---|---|
| `id` | `long` ≥ 1, or 0 = unset | the stable identity. Worldgen and structure templates write 0; only reconciliation and the starter's placement mint. |
| `origin` | `natural`, `structure`, `starter` | how it was born; used only for the per-cell quotas at minting |
| `type` | an id | kept as written even when the type is missing (see "Pack changes") |
| `clarity` | `faint`, `steady`, `clear` | |
| `aspects` | 1–6 `{"aspect", "max" 0–1000, "current" 0–max}`, ascending id | ids kept as written |
| `regen` | `{"tick": long, "interval": 1–24000, "divisor": 1–1000}` | the regeneration clock and the parameters in force since `tick` |
| `cultivation` | `{"counters": {aspect: 0–63}, "sonus": 0–4095}` | progress toward the next step (see "Cultivation") |

**At birth** every `current` equals its `max`, and the counters are zero. The regeneration clock and parameters are stamped when the id is minted.

### Regeneration

**Computed, never ticked.** Tapping never damages a node: no aspect is lost, no max shrinks, nothing dies of draining.

`settle(node, now)`, with `now = level.getGameTime()` (shared by every dimension through the overworld's level data):

```
if now < regen.tick:                         // a clock that went backwards
    regen.tick = now; stop                   // counts as zero elapsed
e = now − regen.tick                         // long, ≥ 0
n = e / regen.interval                       // floor
for each aspect:
    step = max(1, max / regen.divisor)       // floor
    if current < max:
        need = ceilDiv(max − current, step)
        current = (n ≥ need) ? max : current + n × step   // compared before multiplying: no overflow
regen.tick += n × regen.interval             // ≤ e, so no overflow; the remainder is kept
```

- **Settlement runs before every read or write** of `current`, `max` or `clarity`: a strike, a tap or relay pulse, a capture or release, cultivation, a `NodeService` change, and the sync tag. So `regen.tick` is always within one interval of the last touch, and time spent full never banks.
- **Rebase:** after settling, if the node's stored `interval` or `divisor` differs from the current tuning for its clarity, the current values are adopted and `regen.tick = now`. Elapsed time is therefore always priced by the parameters that were in force, and a tuning reload never reprices an unloaded node's past. A clarity change settles, changes, then rebases.
- **Carried nodes** keep their clock; release settles them like any other touch.
- **The client** runs the same function from the synced state and its level's game time to draw growth, so regeneration needs no packets.
- Throughput scales with max, so cultivation raises it.

**Vectors** (reference implementation, cross-checked against the JDK):

| max | current | `tick` | now | interval | divisor | → current | → `tick` |
|---|---|---|---|---|---|---|---|
| 40 | 0 | 1000 | 1079 | 80 | 20 | 0 | 1000 |
| 40 | 0 | 1000 | 1080 | 80 | 20 | 2 | 1080 |
| 40 | 10 | 1000 | 1573 | 80 | 20 | 24 | 1560 |
| 40 | 39 | 0 | 2⁶² | 80 | 20 | 40 | 4611686018427387840 |
| 250 | 0 | 0 | 2⁶³ − 1 | 1 | 1000 | 250 | 9223372036854775807 |
| 8 | 0 | 500 | 400 | 40 | 20 | 0 | 400 |

### Identity and reconciliation

**A global registry of ids**, the SavedData `vftv_nodes` in the overworld's storage (format 1):

| Field | Meaning |
|---|---|
| `next_id` | the next id to mint, a monotonic `long` from 1 |
| `next_token` | the next capture token, a monotonic `long` from 1 |
| `starter` | the starter's id and the bootstrap position recorded at its placement, or absent |
| `nodes` | per id: `origin` and a state, one of `PLACED(dimension, pos)`, `CARRIED(token)` or `REMOVED` (a tombstone) |

- **Ids are minted only when a node is born:** at the first reconciliation of a worldgen or structure node, or when the starter is placed. Capture and release keep the id. Ids are unique across dimensions, because a jar can cross them, and decimal in scan keys (`node:<id>`). They follow chunk-load order, so they are stable but not seed-reproducible; nothing needs them to be.
- **Loading** rebuilds the index and a chunk → ids lookup from `nodes`, and repairs `next_id` to at least the highest id + 1 (likewise `next_token`).
- **Every change marks the SavedData dirty.**

**Reconciliation** settles what the chunks hold against the registry.

- **Queued, never immediate.** `ChunkEvent.Load` on a server level, and a node block entity's `onLoad` (which also covers `/setblock`, `/clone` and structures placed into loaded chunks), add the chunk to a per-level pending set. NeoForge requires level interaction to wait for a later tick, so the set is processed at the start of the next server tick, in ascending `ChunkPos.toLong()` order.
- For each pending chunk: if `ServerChunkCache#getChunkNow` no longer returns it, it is dropped (it will be queued again when it loads). Otherwise its node positions are `ChunkAccess#getBlockEntitiesPos()` (saved **and** instantiated block entities) whose block is `vftv:node`, visited in ascending `BlockPos.asLong()` order, each resolved through `level.getBlockEntity`.

| Block entity | Registry | Action |
|---|---|---|
| id unset | | **Mint**, if the cell admits it: it holds fewer than 8 nodes, and, for a `natural` node, no other natural one, or for a `structure` node, fewer than 2 structure ones. Then `next_id` is taken, `PLACED` here is recorded, the clock is stamped, and `NodeEvent(CREATED)` is posted. Otherwise the block is removed with a warning. |
| id set | `PLACED` here | nothing |
| | `PLACED` elsewhere | a **duplicate**: this block is removed with a warning, and the registry is untouched, so the original keeps its entry |
| | `CARRIED` | the chunk was rolled back, or the server stopped between a capture and its save. **The block wins** if the cell has room: `PLACED` here, so every jar of that capture is stale (release needs `CARRIED` with its token). If the cell is full, the block is removed and the jar stays valid. Either way one copy survives. |
| | `REMOVED` | the block is removed (deferred cleanup of a removal made while unloaded) |
| | no entry | **recovery** (the SavedData was lost or rolled back): `PLACED` here if the cell has room, with `next_id` advanced past it and `NodeEvent(CREATED, RECONCILE)` posted; otherwise removed with a warning |

  Then each id the lookup holds as `PLACED` in this chunk with no node block entity at its position is a **ghost**: it becomes `REMOVED`, leaves the index, and `NodeEvent(REMOVED, RECONCILE)` is posted with a warning.
- **The removal hook:** `BlockEntity#preRemoveSideEffects` (called when the block is replaced, not when it unloads) tombstones an id **only if its entry is `PLACED` at exactly this position**, then posts `NodeEvent(REMOVED)`. A capture, command or duplicate removal has already changed or never owned the entry, so the hook does nothing for them. The hook is conditional on the block-update flags, so anything it misses is caught as a ghost at the next load.
- **An interaction with a node whose entry isn't `PLACED` here** (a clone made in a loaded chunk, before its reconciliation) is refused, and its chunk is queued.
- **Crash consistency:** the chunk, the player's inventory and `vftv_nodes` are separate files and not one filesystem transaction. The promise is narrower and testable: after any interrupted save, **at most one live copy of a node exists**. A crash in the middle of a move may lose the node being moved; it never duplicates it.

### The index

Per dimension, **in memory**, rebuilt from the registry at server start: cells of 64×64 blocks over all heights, keyed (`floorDiv(x, 64)`, `floorDiv(z, 64)`), each holding **at most 8 nodes** (a fixed limit). Carried nodes are absent, so sounding never points at a jar.

- **`nearest(dimension, point, r)`** examines the cells overlapping the square of half-side *r* around the point: **at most 7 × 7 = 49 cells and 392 entries at *r* = 192**. Distance is 3-D Euclidean from the point (sounding passes the eye position) to each node's centre; the radius is inclusive and ties go to the lowest id. It reads memory only: **0 chunk loads or generations**, by construction.
- **Supported radius: 0–256** (at most 9 × 9 cells and 648 entries). A larger radius is rejected, so the scanning tuning's `sounding.radius` gains the upper bound 256 at load.
- **The cap holds at every insertion:** natural spread puts at most 1 per cell and templates at most 2, enforced when minting; a Bell Jar release into a full cell is refused; the starter is placed into a cell with room.
- **Starter reservation:** worldgen origins can fill at most 3 of a cell's 8 places, so only Bell Jar releases compete with the starter, and the starter is placed at server start before any player can release one. A re-placement after a removal skips full cells.

### Natural spread

**One seeded candidate per 64×64 cell**: deterministic, unit-testable from (seed, cell) alone, at most one natural node per cell by construction, and no level state touched on worldgen threads.

- **Placement:** a `Feature` **`vftv:node`**, configured as `vftv:node` and placed with no modifiers, added to `#minecraft:is_overworld` at `top_layer_modification` by the biome modifier `vftv:add_nodes`. It runs once per chunk. Other dimensions are a pack's choice, with no code.
- **Seed** for cell (`rx`, `rz`), with the SplitMix64 finaliser and `fold` exactly as pinned in the [research spec](research.md#generating-a-note), all arithmetic wrapping and `rx`, `rz` sign-extended to 64 bits:

```
h = fold([0x4E4F4445 ("NODE"), worldSeed, rx, rz])
stream: s = h;  next() = { s = s + 0x9E3779B97F4A7C15L; return mix64(s) }
bounded(d, lo, hi) = lo + (d >>> 1) % (hi − lo + 1)
weighted(d, weights) = t = (d >>> 1) % Σweights; the first i with t < weights[0] + … + weights[i]
```

- **Draws,** in this order, all made even if a later step gives up:
  1. `d0`: the cell spawns iff `(d0 >>> 11) × 2⁻⁵³ < chance` (a `double` comparison; the product is exact);
  2. `d1`: the column `x = 64·rx + (d1 & 63)`, `z = 64·rz + ((d1 >>> 6) & 63)`;
  3. `d2`: the offset `bounded(d2, height.min, height.max)`;
  4. `d3`: the type, `weighted` over the **eligible types**, sorted by id with `String.compareTo` on `ResourceLocation.toString()`;
  5. `d4`: the clarity, `weighted` over the type's clarity weights in the order faint, steady, clear;
  6. one draw per aspect of the type, in ascending aspect id: its max is `bounded(d, min, max)`.
- **Steps** in the chunk at (`cx`, `cz`):
  1. The cell is (`floorDiv(cx, 4)`, `floorDiv(cz, 4)`). If it doesn't spawn, or the column isn't in this chunk, stop.
  2. `y₀ = WORLD_SURFACE_WG(x, z) + offset`. The **biome sample point** is (`x`, `y₀`, `z`). The eligible types are the natural types with any `biomes` tag containing that biome. If there are none, stop.
  3. **Air search:** the first of `y₀ … y₀ + retries` that is inside the build height and `isAir()` takes the node; if none is, stop. Retries never change the biome sample or the draws.
  4. Write the node with id unset, `origin: natural`, the rolled type, clarity and maxes, every `current` at its max.
- The candidate is a function of the seed alone; whether it lands also depends on terrain, biome data and occupancy.
- At `chance` 0.35 a 192-block disc holds about 9 natural nodes.

**Vectors** (world seed `0x0123456789ABCDEF`, `chance` 0.35, height [4, 12]). For the type and later draws, the eligible list is fixed for the test: `greenwood` (weight 100: Ordo [8, 24], Terra [16, 40]), `wild` (10: six primals [4, 12]) and `windward` (100: Aer [16, 40], Terra [8, 24]), all with clarity weights 50 / 35 / 15. Rows where the cell doesn't spawn still list the later draws, to pin the stream.

| Cell | `h` | `d0` | Spawns | Column | Offset | Type | Clarity | Maxes |
|---|---|---|---|---|---|---|---|---|
| (0, 0) | `0xF7F4BAECAA30AB81` | `0xDDD161D1D3818970` | no | (11, 17) | 4 | windward | faint | Aer 17, Terra 21 |
| (−2, 0) | `0x210C73FDF14FED9D` | `0x1503086017200B0D` | yes | (−82, 4) | 10 | windward | faint | Aer 36, Terra 22 |
| (−2, −2) | `0x1F49B6C4DBAE9FEC` | `0x27F1A1204F24D6B7` | yes | (−92, −97) | 10 | greenwood | faint | Ordo 24, Terra 31 |
| (−3, 3) | `0x24CE84F92D542318` | `0x49DD76C4566F0C72` | yes | (−190, 197) | 8 | windward | faint | Aer 38, Terra 23 |
| (3, −7) | `0xB4E4081CE2FB6903` | `0xF5DED0D424C63C21` | no | (219, −387) | 12 | wild | clear | Aer 7, Aqua 9, Ignis 4, Ordo 6, Perditio 8, Terra 8 |
| (−12345, 6789) | `0x24A1C168448EC4CE` | `0x4BFEECD3A1C35050` | yes | (−790027, 434554) | 12 | windward | faint | Aer 29, Terra 15 |

Over the 10,000 cells in [−50, 49]², the reference spawns 35.02%. Cell keys: x = −1 and −64 fall in cell −1, −65 in −2, 63 in 0 and 64 in 1.

### The starter

**A real, usable node near spawn, guaranteed.** It is how the first sounding answers and the first wand fills.

- **Bootstrap location:** the overworld respawn position, `level.getRespawnData().pos()`, at the moment the starter is placed. It is recorded in `vftv_nodes`.
- **When:** at `ServerStartedEvent`, whenever `starter` is absent or its id is `REMOVED`: a new world, an existing world the mod was added to, or after an operator removed it. A starter that is `PLACED` in an unloaded chunk is never duplicated, because the id is persisted.
- **Where.** Let `P` be the **effective** protected radius for a non-op player, from every condition vanilla's `DedicatedServer#isUnderSpawnProtection` applies: `P` = the configured `spawn-protection` radius iff the server is dedicated, the respawn position's dimension is the overworld, the operator list is non-empty and the radius is > 0; otherwise `P` = 0 (so always 0 on an integrated server). Let `a = max(starter.min_ring, P + 1)`. Under those conditions vanilla protects a position iff its Chebyshev distance from the spawn position is ≤ `P`, so every candidate is outside actual protection.
  - **Rings** *k* = `a`, `a + 1`, … `a + starter.ring − 1`, never beyond 180. If `a` > 180 there are no candidates.
  - **Within ring *k*,** the perimeter of the Chebyshev square has 8*k* columns, indexed *i* = 0 … 8*k* − 1: with `side = i / 2k` and `t = i % 2k`, side 0 is (−*k* + *t*, −*k*), side 1 (*k*, −*k* + *t*), side 2 (*k* − *t*, *k*) and side 3 (−*k*, *k* − *t*), as (dx, dz) from the bootstrap. The walk starts at `start = Long.remainderUnsigned(fold([0x53544152 ("STAR"), worldSeed, k]), 8k)` and visits `(start + j) % 8k` for *j* = 0 … 8*k* − 1.
  - **A candidate column** (`x`, `z`) is valid iff all of these hold:
    1. it is inside the world border;
    2. with `h = WORLD_SURFACE(x, z)`, the node position `N = (x, h + starter.height, z)` is `isAir()` and inside the build height;
    3. the 3-D distance between the centres of `N` and the bootstrap is ≤ 180;
    4. a **standing spot** `S = (x, h, z)`: the block below `S` has a sturdy top face and no fluid, and `S` and the block above it have no collision and no fluid;
    5. **in reach:** the distance from `S`'s eye (`S.y + 1.62`) to `N`'s centre is ≤ 4.5, vanilla's default `block_interaction_range`, which is the reach of both the fork and the wand. Everything between them is air by the heightmap;
    6. `N`'s cell holds fewer than 8 nodes, and no indexed node is within `node.min_spacing`.
  - **The first valid candidate wins.** Placement loads and generates the chunks it examines (placement is not a query). The search is bounded: every candidate lies within Chebyshev 180, so it touches at most 24 × 24 = 576 chunks, and at default tuning with `P` = 0 it examines at most 3,744 columns in at most 25 chunks.
- **What:** type `vftv:wellspring`, `origin: starter`, minted at once, with `starter` and the bootstrap recorded, then `NodeEvent(CREATED, STARTER)`.
- **Impossible:** if no candidate is valid (for example an effective `P` ≥ 180, or no open surface near spawn), **server startup fails** with an error naming the configured and effective spawn-protection radius, the bootstrap position and both remedies: lower `spawn-protection`, or move the world spawn. A warning would not be a guarantee. Void-preset worlds therefore can't host the mod in v1; an opt-out that drops the guarantee belongs to the configuration spec.
- **Changed spawn or protection:** if, at a later start, the recorded starter lies inside the current effective protection (an operator was added, or the radius raised) or more than 180 blocks from the current respawn position, a warning names `/vftv node remove <id>`, after which the next start places a new one.
- **The guarantee:** id **`vftv:starter`**: Stage 1, **one distinct node per world**, type `vftv:wellspring`, composition the six built-in primals (Aer, Terra, Ignis, Aqua, Ordo and Perditio) at exactly 32 each, enforced by the Wellspring's load validation. At the default divisor its first strike pays 4 of each, per player. Within 180 of the bootstrap, the 12-block margin under the 192-block sounding radius covers eye height and a player standing near spawn.

**Ring vectors** (world seed `0x0123456789ABCDEF`):

| *k* | `start` | First (dx, dz) | Second |
|---|---|---|---|
| 8 | 27 | (8, 3) | (8, 4) |
| 9 | 16 | (7, −9) | (8, −9) |
| 32 | 236 | (−32, −12) | (−32, −13) |

The reference also checks that each perimeter visits all 8*k* columns at Chebyshev *k* exactly once, each next to the one before.

## Hand-tapping

**Hold use on a node with a wand.** Vis moves from the node into the wand's pools, a little every pulse.

**Interaction precedence** for a held wand:

| Target | Result |
|---|---|
| a node | tapping, or a refusal that consumes the click (so an air-use focus can't fire by accident) |
| another block, while sneaking, with a focus attached, in the main hand | #22's block gesture ([foci, Entry points](foci.md#entry-points)) |
| another block, otherwise | `PASS`: the block's own use, and #18's worktable listener |
| an entity, or air | `use`: #22's entity gesture if a focus is attached and the wand is in the main hand, else nothing |

**Casting is main hand only** ([foci, Entry points](foci.md#entry-points)); tapping works from either hand. A cast's `VisContext.source` is its focus's **effect id**, since a focus has no id of its own.

**One session per player.** The transient per-player session slot holds either a tap session (below) or the foci spec's **beam session**, never both. The wand's `onUseTick`, `releaseUsing` and `onStopUsing`, and the logout clean-up, dispatch to whichever session the slot holds. The slot exposes its **session kind** so the foci spec can sync its beam-state signal; a tap sends nothing.

**Harvesting.** The wand's `Item#isCorrectToolForDrops(stack, state)` answers for an attached focus whose effect is a harvesting type (`delve`, `exchange`): that effect's `tool` stack's own answer, else false ([foci, Block work](foci.md#block-work)).

**Start** (server, `Item#onItemUseFirst`, which runs before the block's own use and outside NeoForge's placement snapshot). The click arrives through `useItemOn`, so spawn protection and `PlayerInteractEvent.RightClickBlock` have already applied. Refusals, each shown on the action bar:

| Check | Refusal |
|---|---|
| `vftv:tapping` learned | "I don't yet know how to draw from it." |
| the wand is not inert | "This wand is missing a part." |
| the node's entry is `PLACED` here and its type is present | "It rings in a key I don't know." (the chunk is queued if the entry doesn't match) |
| the node holds a vis aspect with max > 0 | "Nothing here I can draw." |
| some such aspect has room in the wand | "My wand can hold no more of this." |

Otherwise `startUsingItem`; a **tap session** `{node id, dimension, position, hand, stack, slot}` goes into transient per-player server memory, where `stack` is the **server's `ItemStack` object** in that hand and `slot` the selected hotbar index (main hand) or the offhand. The start sound plays. The client starts the use on the same visible checks from its synced state. The server decides, and a refused or stopped use ends on the client through the synced use flag.

- **Use duration** 72,000 ticks (a fixed limit; the default for this item would be zero). Reaching it ends the tap.
- **`canContinueUsing`** is true whenever both stacks are wands, so a client-side slot resync never breaks the use visually. It is **not** the server's check: the pulse below compares stack identity itself.

**Pulses** run in `onUseTick` on the ticks where `gameTime % tap.interval == 0`, aligned for every player and node, so the first pulse comes 0 to `tap.interval − 1` ticks after the start. Each pulse first checks, stopping the use on any failure:
- the session hand still holds **the same `ItemStack` object** (`==`, not an equal copy) in the same slot, and it is not inert. In-place component writes keep the object, so the tap's own writes pass; switching to another wand, even an identical one, or changing the selected slot stops the tap;
- `vftv:tapping` is still learned;
- `level.mayInteract` and spawn protection still allow the position;
- a block raycast from the eye within the player's `block_interaction_range` hits the session position;
- the block entity there is a node with the session id, `PLACED` here, with its type present.

Then the node is settled, and for each vis aspect of the node in ascending id it moves `min(tap.amount, current, room)`. If nothing moved because the node is empty, the tap stops ("It has nothing left to give."); if every aspect the node holds is full in the wand, it stops with the full sound. The node is marked dirty for sync (see "Sync and state").

- **Ends** on release, a stop, logout, death or a dimension change. `onStopUsing` and the logout event clear the session.
- **Several players on one node** pulse on the same aligned tick, in server player order. No flux, no damage.
- **Sound:** start ×3, stop ×3 and full ×3 variants; while tapping, a 3-second drone (×3) is retriggered every 40 ticks, so the faded-tail convention ([art style §7](art-style.md#7-sound-palette)) holds with no seamless loop. The tapping player's client draws 2 `vftv:node_glint` particles per pulse (a fixed feedback constant) from the node toward the hand, subject to the global glint cap (see "Rendering and sound").
- At 2 per 4 ticks, a wooden wand fills from empty in 4 seconds at a node holding 40.

## Cultivation

**Stage 2: feed a node essentia through a Node Tap.** Stage 2 is essentia, so the cultivating input is the new Stage's resource, and a clearer ring from Sonus is the lore in the mechanic. TC4's draining hazards are dropped.

The Node Tap (see "Relays") exposes the architecture's essentia capability, **insert-only**, on every face but the one facing its node, and for the null side, inside the transfer transaction ([architecture, essentia](architecture.md#essentia-and-capabilities)). It exposes the [essentia spec's suction capability](essentia.md#suction) `vftv:essentia_suction` on **exactly the same faces**. The block `vftv:node_tap` is in the block tag `vftv:essentia_endpoints`, so Essentia Tubes connect to it.

- **Snapshot and settle:** the first insert in a transaction snapshots the **node's state** through the transfer API's snapshot journal and settles regeneration first. An aborted transaction restores the snapshot exactly.
- **A vis aspect `A`** (an exposed primal): the room is the units left before `max_A` reaches `cultivate.max_capacity`, that is `(cultivate.max_capacity − max_A) × cultivate.per_max − c_A`, or 0 if `max_A` is already there. Inserting *k* ≤ room units adds *k* to `c_A`, then `max_A += c_A / per_max` and `c_A %= per_max`. At the cap, `c_A` becomes 0. **A missing `A`** is added with max 0 and current 0 when its first unit is accepted, if the node holds fewer than 6 aspects, and that reserves its slot inside the transaction; it is drawn and tappable once its max reaches 1.
- **Sonus** (sound: Aer + Ordo): room is `(steps to clear) × cultivate.clarity_cost − sonus`. Inserting adds to `sonus`; each `clarity_cost` raises clarity one step (settle, change, rebase). At *clear*, `sonus` becomes 0 and nothing more is accepted.
- **Anything else** is refused (0 accepted).
- **Partial acceptance:** a bulk insert accepts `min(amount, room)` and returns that count.
- **On the root commit only:** the block entity is marked changed and dirty for sync, `NodeEvent(CULTIVATED)` is posted, and the absorb sound plays (at most one per node per 20 ticks, a fixed feedback constant), plus the clarity sound if clarity rose.
- **Tuning changes:** a lowered `per_max` or `clarity_cost` carries any counter at or over the new threshold into steps at the next settlement. A lowered `max_capacity` never shrinks a node; it only stops accepting.
- **Suction:** the tap's `suction(X)` is `cultivate.suction` for an aspect it would accept now, and 0 for anything else. A positive value always means the tap would accept at least one unit of X; the converse isn't promised. Its `hold` is 0 for every aspect, since it keeps nothing that can be extracted. **`cultivate.suction` = 0 disables automatic pulling** by lines and alembics, but never insertion: a direct insert is still accepted. Values 1–15 put the tap in the essentia spec's storage band, where it **loses guaranteed consumer precedence**: its ordering against each jar depends on the configured ranks, with ties rotated. For example, at the default jar ranks a tap at 15 still outranks a labelled jar at 8, and a tap at 8 ties with it. The default of 16 is the bottom of the consumer band and outranks every store ([essentia spec, Suction](essentia.md#suction)).

## Moving nodes: the Bell Jar

**`vftv:bell_jar`**, stacking to 1: a natural philosopher's glass dome. A node is moved whole, its id, vis and cultivation intact. **No cost and no penalty**: the decision is where to put it, not whether to gamble. Both directions run in `Item#onItemUseFirst`, outside NeoForge's placement snapshot, so this spec controls every mutation and its order.

**Capture** (an empty jar on a node). Checks, in order:
1. `vftv:moving_nodes` learned ("I don't yet know how to move it.");
2. the node's entry is `PLACED` here (otherwise refused, and the chunk is queued);
3. its type is present ("It rings in a key I don't know.") and not anchored ("It will not leave this place.");
4. `level.mayInteract` and spawn protection;
5. a posted `BreakBlockEvent` for the node position is not cancelled (claim mods answer here, with their own message).

**Commit,** in one server-thread step: settle; take `token = next_token++`; record `CARRIED(token)`; remove the node from the index; set **`vftv:captured_node`** `{format 1, token, node state}` on the jar in place; remove the block (the removal hook sees `CARRIED` and does nothing). Then post `NodeEvent(CAPTURED)` and play the capture sound.

**Release** (a filled jar on any block face; the click is consumed even if refused). The target is the adjacent position on the clicked face. Checks, in order:
1. `vftv:moving_nodes` learned;
2. the component decodes ("The jar holds nothing I recognise.");
3. the id's entry is `CARRIED` with **exactly this jar's token**. `PLACED`, `REMOVED`, or another token means a stale copy ("This echo is old; the node has moved on."). **No entry** is refused in survival; for a creative player with permission level 2 it is a **recovery**, which changes nothing yet and is registered only at the final commit;
4. the target `isAir()`, inside the build height and the world border ("There is no room for it here.");
5. no indexed node in the dimension is closer than `node.min_spacing` (equality is allowed; "Too close to another node.");
6. the target's cell holds fewer than 8 ("The air here is already crowded.");
7. `level.mayInteract` and spawn protection.

**Provisional install and the placement event.** NeoForge's `BlockEvent.EntityPlaceEvent` builds its `BlockSnapshot` and placed state from the **live world**, so the node must be in place when the event is posted, or protection mods would be asked about air:
1. **Snapshot** the target (air) with `BlockSnapshot.create`.
2. **Install provisionally,** inside a per-level guarded scope: set the node block and its block entity from the jar's settled state, with flags that neither notify neighbours nor send to clients. While the scope is open, the node block entity's `onLoad` queues no reconciliation, the removal hook does nothing, and no event, sound or sync is published. Jar, registry and index are untouched.
3. **Post** `BlockEvent.EntityPlaceEvent` with that snapshot, the real player and the clicked-against block state. Its placed block is the node, and its replaced snapshot is air.
4. **Cancelled:** restore the snapshot (air) inside the same scope, close it, and refuse silently (the claim mod speaks). Nothing else ever changed.
5. **Accepted:** close the scope and **commit**: record `PLACED` (for a recovery, register the id with its origin, raise `next_id` above it, and raise `next_token` to at least the jar's token + 1); insert into the index; remove the jar's component, so the jar is empty again; mark the SavedData dirty; send the block update to clients. Then post `NodeEvent(PLACED)`, or `NodeEvent(CREATED, COMMAND)` for a recovery, and play the release sound.

- **Copies:** every capture takes a new token, so a duplicated jar (a creative copy, or an exploit) can be released at most once, and any older copy is stale for good.
- **Lost jars** (despawned, burned) leave their id `CARRIED` forever; the node is gone, and the entry only takes registry space.
- **A jar can be handed to a friend.** That is sharing; the friend needs `vftv:moving_nodes` to release it.

## Creation, removal and sharing

- **Created only** by natural spread, the starter, and structure templates ([Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27), and later #26 and #28). Players cannot create nodes in Stages 1–2.
- **Removed only** by:
  - an operator, with **`/vftv node remove <id>`** (permission 2). A loaded node's block goes at once. An unloaded one is tombstoned and leaves the index at once, and its block is removed at that chunk's next reconciliation;
  - a creative player breaking it;
  - a sibling, through `NodeService.remove(id, cause)`.

  Each tombstones the id and posts `NodeEvent(REMOVED)` once.
- **No survival mechanic destroys a node.** Qualified: an anchorless node can be captured by anyone allowed to interact there, and a jar can be lost, so a node can be stolen or lost, never broken in place.
- **Sharing.** Nodes, taps, relays and cradles have **no owner**, and first-strike rewards are per player per node ([aspects, aspect points](aspects.md#aspect-points)). A relay network charges any wand set in its cradles, whether or not that player could tap by hand.
- **Protection covers what a player does by hand, when they do it:** tap starts, captures, releases and the placement of relay members pass through vanilla's `useItemOn` checks, `RightClickBlock`, `BreakBlockEvent` and `EntityPlaceEvent`, and every tap pulse rechecks `mayInteract` and spawn protection. Relay flow and cultivation are automatic and aren't re-authorised: a network built where its builder was allowed keeps running. A claim mod that wants more needs an integration, which the mod-compatibility spec owns.

## Relays

**Stage 2: set up once, and vis comes to the wand.** The same economy made convenient, not a second currency.

### Members

| Block | Role | Placement |
|---|---|---|
| **Node Tap** `vftv:node_tap` | source; also the cultivation input | Placed against a node: it faces the clicked block, which must be a node ("There is nothing here to listen to."). At most one tap per node: a node another tap already faces refuses ("Something already listens here."). If the node leaves, the tap goes idle; if a node arrives in front of an idle tap, the one tap facing it with the lowest `BlockPos.asLong()` is the active one. |
| **Vis Relay** `vftv:vis_relay` | pass-through | anywhere |
| **Wand Cradle** `vftv:wand_cradle` | sink; holds one wand | anywhere. Right-click with a wand inserts it into an empty cradle; with an empty hand, not sneaking, takes it into the hand; anything else passes. Breaking drops the wand. |

- All three have `PushReaction.BLOCK`, a block entity and no block-entity ticker. The tap's and relay's block entities hold no data; they exist so that `ChunkAccess#getBlockEntitiesPos()` and `onLoad` can find every member, as they find nodes. The cradle's holds its wand.
- **Status lines,** sneaking with an empty hand, on the action bar, built on the server with live values: a relay shows its network's members, sources and charging cradles; a tap shows its node's type, clarity and id, and its Sonus progress; a cradle shows its wand's fill, or empty, full or idle.

### Topology

- **Two members link iff their centres are ≤ `relay.range` apart.** No line of sight and no loss per hop.
- **A network** is a connected component, at most **32 members** (a fixed limit).
- **Membership** lives in the per-dimension SavedData **`vftv_relays`** (format 1: positions and kinds). Components, a spatial hash with cell side `relay.range`, per-component sizes and a **chunk → members lookup** are rebuilt in memory at level load.
- **Admission check.** On placement, `getStateForPlacement` checks the members within range among the 27 hash cells around the new position: if 1 + the sum of the sizes of the distinct components they belong to exceeds 32, it refuses ("Too many voices for one chord."). Members of distinct components are more than `relay.range` apart, so those 27 cells (a cube of side 3 × range) meet at most 122 components (balls of radius range/2 packed into a cube of side 4 × range), and the check examines at most 122 × 32 = 3,904 members.
- **Removal** rebuilds the one affected component by BFS over at most 32 members (at most 496 distance checks).
- **Never above 32.** Every admission path enforces the cap, so no component exceeds 32 in play, and the bounds above always hold.
- **Range changes:** a changed `relay.range` rebuilds every component. If any component would then exceed 32, the change is rejected: on `/reload` the whole tuning reload is **rejected** (the previous record is kept), and at world open the **world is refused**. Either error is actionable: it names the new range, the oversized component's size and one member's dimension and position, and says to restore the range or remove members first.

### Membership reconciliation

Membership changes only through one per-level queue, processed at the **end of each server tick**, so it always reflects the blocks actually there. A placement that NeoForge cancels and rolls back never enters the data, and `/setblock`, `/clone`, replacements, interrupted saves and missed removal callbacks are all repaired.

- **What queues work:**
  - **chunks:** `ChunkEvent.Load` on a server level;
  - **positions:** a member block's placement callback, a member block entity's `onLoad` (which also covers commands and structures placed into loaded chunks) and its removal (`preRemoveSideEffects`), and any state change of a member block.
- **Processing order:** pending chunks first, in ascending `ChunkPos.toLong()`, then pending positions, in ascending `BlockPos.asLong()`. A chunk or position whose chunk `getChunkNow` no longer returns is dropped; it is queued again when it loads.
- **A chunk:** compare its persisted members (from the chunk → members lookup) with its actual ones (`getBlockEntitiesPos()` whose block is a member kind), and treat every position in the union as a pending position, in ascending `BlockPos.asLong()`.
- **A position:**

| Actual block | Persisted | Action |
|---|---|---|
| a member kind | none | **admit** if 1 + the distinct neighbouring component sizes ≤ 32; otherwise break the block and drop it as an item, with "Too many voices for one chord." to the placer if there is one |
| a member kind | the same kind | nothing |
| a member kind | another kind | update the kind (and the active-sink set) |
| not a member | a member | **remove** (a ghost, or an ordinary removal) |

- **Same-tick admissions** are therefore ordered by position, and each one sees the ones before it: of two placements that together would exceed 32, the one with the lower `asLong()` is admitted and the other is broken.
- **After any change:** rebuild the affected components (each at most 32 members), update the active-sink set, and mark the SavedData dirty.
- **Unloaded members are never touched:** their links stay as data, and nothing loads a chunk to check them.

### Flow

- **Active sinks:** a per-level set of cradles in loaded chunks holding a non-inert wand with room in some pool. It is updated when a cradle loads, unloads, gets or loses a wand, and after each pulse. Nothing scans persisted networks.
- **Pulse:** when `gameTime % relay.pulse == 0`, for each component with an active sink, in ascending component key (the lowest member `BlockPos.asLong()`):
  1. **Sources:** its active taps whose chunk and node chunk are both loaded (`getChunkNow`, which never loads) and whose node is `PLACED` with its type present, **sorted by node id**.
  2. **Sinks:** its active sinks sorted by `BlockPos.asLong()`, visited starting at index `(gameTime / relay.pulse) % sinkCount` and wrapping, so no cradle is always last.
  3. For each sink, for each vis aspect in ascending id: `need = min(relay.rate, room)`; take from the sources in order, each settled before its first read in the pulse, until `need` is 0.
- **Intermediate relays need not be loaded**; the topology is data. **No chunk loading:** no tickets, no forced chunks. An unloaded cradle doesn't charge, and an unloaded source doesn't give.
- **One economy:** taps pull the same `current` that hands tap. A tapped node stays hand-tappable, and nothing spills flux. Cradles are the only sinks in v1; the worktable never charges.
- **Feedback:** a charge plays the cradle sound at most once per component per pulse, at the first sink that received vis.

**Vector** (reference implementation, cross-checked against the JDK): `relay.pulse` 20, `relay.rate` 4, sources n0 {Aer 5, Terra 2} and n1 {Aer 9}, sinks s0 {Aer room 10, Terra room 10}, s1 {Aer 3}, s2 {Aer 10}, each pulse from these same states:

| Game time | Start | Transfers (sink: aspect ← source: amount) |
|---|---|---|
| 0 | 0 | s0: Aer ← n0: 4; s0: Terra ← n0: 2; s1: Aer ← n0: 1; s1: Aer ← n1: 2; s2: Aer ← n1: 4 |
| 20 | 1 | s1: Aer ← n0: 3; s2: Aer ← n0: 2; s2: Aer ← n1: 2; s0: Aer ← n1: 4; s0: Terra ← n0: 2 |
| 40 | 2 | s2: Aer ← n0: 4; s0: Aer ← n0: 1; s0: Aer ← n1: 3; s0: Terra ← n0: 2; s1: Aer ← n1: 3 |

## Rendering and sound

- **Nodes are visible to everyone, always,** with or without a fork: they are landmarks. The fork reads them, and sounding finds the unseen ones.
- **Renderer:** a block-entity renderer submitting camera-facing quads through `submitCustomGeometry`, **cutout** (no partial alpha, [art style §3](art-style.md#3-resolution-and-pixel-rules)) and full-bright, within the fixed block-entity view distance of 64 blocks.
  - **Core:** one quad from the 8-frame strip `vftv:block/node_core` (block atlas), frame `(gameTime / frame_ticks[clarity]) % 8`. Size `min(max_size, base_size + size_per_root × √Σcurrent)` blocks, times `scale[clarity]`.
  - **Motes:** one per aspect, at most 6, each drawing **the aspect's own icon** (`vftv:aspect/<id>`, or the aspect's `icon` field), never a tint-only sprite, so identity rests on the approved glyph channel and needs no palette change. Mote *i* of *n* (aspects in ascending id), at `t = gameTime + partialTick`, has angle `θ = 2π × (t / orbit_ticks + i / n)` and sits at offset `(orbit_radius × cos θ, bob × sin 2θ, orbit_radius × sin θ)` from the core's centre: a horizontal circle with a vertical bob twice per orbit. A dormant node draws its core only.
  - **Budget:** at most 7 quads per node. The index caps nodes at 8 per cell, so at most 9 cells and **72 nodes, 504 quads** are within the view distance. Our renderer allocates nothing per frame.
- **Ambient glints:** a `vftv:node_glint` particle at most once per node per `glints.every_ticks`, only within `glints.range`, none at the "minimal" particle setting.
- **Global glint cap:** the client counts every live `vftv:node_glint`, ambient and tapping alike, and **drops any new one while 64 are alive** (a fixed limit). The cap holds whatever the density or the number of tapping players.
- **Hum:** each node within `hum.range` keeps a client-side next-hum time. After each hum (and when the node first comes into range) the next is `now + hum.min_interval_ticks + random.nextInt(hum.min_interval_ticks)`, drawn from the client level's `RandomSource`. A due hum that would make more than `hum.max_playing` play at once is skipped and rescheduled the same way.
- **Sound events,** with subtitles for each:

| Event | Variants | Plays |
|---|---|---|
| `wand.tap_start`, `wand.tap_stop`, `wand.full` | 3 each | a tap starting, stopping, or stopping full |
| `wand.tap_drone` | 3 | every 40 ticks while tapping |
| `node.hum` | 3 | ambient, bounded as above |
| `node.absorb` | 3 | cultivation, at most once per node per 20 ticks |
| `node.clarity_rise` | 1 | a clarity step |
| `relay.link` | 1 | placing a member that links to at least one other |
| `cradle.charge` | 3 | at most once per component per pulse |
| `bell_jar.capture`, `bell_jar.release` | 1 each | a capture or release |

## Inspection and teaching

Every number a decision rests on is shown exactly, live, and never in prose.

- **Strike a node** with the fork: the [aspects readout](aspects.md#what-a-strike-reveals) shows its localised type, clarity, and each aspect as current / max after settlement, through `NodeService.readout`. A dormant node shows its aspects and the type "Unknown".
- **The wand:** HUD, Shift tooltip and item bar (see "Display").
- **Relay members:** the status lines above, including why a network or cradle is idle, empty or full.
- **Refusals** name their cause, with any number as a live argument.
- **The book:** the research spec's fact grammar has a source **`vis`** (added there with this spec), `{fact:vis:<key>}`, re-resolved whenever `VisRules` arrives, for any key of the synced `VisRules` (every tuning key below, plus `limits.network_members`, `limits.nodes_per_cell`, `limits.aspects_per_node`), so pages state tapping rates, cultivation prices, relay range, cap and throughput live. Prose never hardcodes them, which the research lints already enforce (see the contracts).

## Authored content

The content ticket delivers authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen, which writes the resources into `src/generated/resources`. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails load validation. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/wands/parts.json        rods and caps
content/wands/recipes.json      the assembly instance and the Stage 2 arcane recipes
content/nodes/types.json        node types
content/nodes/placement.json    natural spread numbers
content/nodes/guarantees.json   node guarantees
content/nodes/client.json       node rendering and ambience numbers
content/nodes/fixtures.json     vectors and coverage samples
content/lang/en_us/wands.json   every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `parts.json` | `vftv:wand_parts/1` | `data/<ns>/vftv/wand_rod/<path>.json`, `data/<ns>/vftv/wand_cap/<path>.json` |
| `recipes.json` | `vftv:wand_recipes/1` | `data/<ns>/recipe/<path>.json` |
| `types.json` | `vftv:node_types/1` | `data/<ns>/vftv/node_type/<path>.json` |
| `placement.json` | `vftv:node_placement/1` | `data/vftv/worldgen/configured_feature/node.json`; the placed feature and `data/vftv/neoforge/biome_modifier/add_nodes.json` are fixed by code |
| `client.json` | `vftv:node_client/1` | `assets/vftv/nodes/client.json`, overridable by resource packs |
| `guarantees.json`, `fixtures.json` | `vftv:node_guarantees/1`, `vftv:node_fixtures/1` | nothing: read by tests only |
| `lang/<lang>/wands.json` | `vftv:lang/1` | its `strings` object merged into `assets/vftv/lang/<lang>.json`; a duplicate key across all `content/lang` files fails datagen |

### Parts

`{"schema": "vftv:wand_parts/1", "rods": {<id>: <rod>}, "caps": {<id>: <cap>}}`, each in the registry format. These are the shipped values:

```json
{"schema": "vftv:wand_parts/1",
 "rods": {
   "vftv:wooden": {"item": "minecraft:stick",          "capacity": 40},
   "vftv:chime":  {"item": "vftv:chime_rod",           "capacity": 150}},
 "caps": {
   "vftv:copper":         {"item": "minecraft:copper_nugget",  "cost_multiplier": 1.00},
   "vftv:resonant_brass": {"item": "vftv:resonant_brass_cap",  "cost_multiplier": 0.85}}}
```

**Rejected:** a required key missing; anything that fails the parts load validation.

**Other content files** may use the same schema for later-tier parts (the [infusion spec](infusion.md#wand-parts)'s `content/infusion/wand_parts.json` is the first). Datagen merges them; a part id declared in two `content/` files fails datagen.

### Recipes

`{"schema": "vftv:wand_recipes/1", "recipes": {<id>: <recipe>}}`. The ids and gates are frozen here; the arcane recipes' bodies (ingredients, counts, vis costs) use the [arcane recipe type](arcane.md#the-recipe-type)'s format verbatim.

| Id | Type | `research` | Stage |
|---|---|---|---|
| `vftv:wand_assembly` | `vftv:wand_assembly` | none (ungated) | 1 |
| `vftv:chime_rod`, `vftv:resonant_brass_cap` | `vftv:arcane` | `vftv:singing_wands` | 2 |
| `vftv:node_tap` | `vftv:arcane` | `vftv:cultivating_nodes` | 2 |
| `vftv:vis_relay`, `vftv:wand_cradle` | `vftv:arcane` | `vftv:vis_relays` | 2 |
| `vftv:bell_jar` | `vftv:arcane` | `vftv:moving_nodes` | 2 |

```jsonc
{"schema": "vftv:wand_recipes/1", "recipes": {
  "vftv:wand_assembly": {"type": "vftv:wand_assembly", "category": "equipment"},
  "vftv:bell_jar": {
    "type": "vftv:arcane",
    "research": "vftv:moving_nodes",
    // … the rest of the arcane body: pattern and key (or ingredients), result, vis
  }
  // … and the other five
}}
```

**Rejected:** an id outside the table, or one missing from it; a `research` other than the table's; any field on `vftv:wand_assembly` other than `category` (default `equipment`); a body that fails the arcane recipe type's rules.

### Node types

`{"schema": "vftv:node_types/1", "types": {<id>: <type>}}`, in the registry format. Two complete entries (biome tags and weights illustrative):

```jsonc
"vftv:wellspring": {
  "aspects": [
    {"aspect": "vftv:aer",   "range": [32, 32]}, {"aspect": "vftv:aqua",     "range": [32, 32]},
    {"aspect": "vftv:ignis", "range": [32, 32]}, {"aspect": "vftv:ordo",     "range": [32, 32]},
    {"aspect": "vftv:perditio", "range": [32, 32]}, {"aspect": "vftv:terra", "range": [32, 32]}],
  "main_aspect": "vftv:aer",
  "clarity": {"faint": 0, "steady": 0, "clear": 1},
  "anchored": true
},
"vftv:tidewater": {
  "aspects": [{"aspect": "vftv:aqua", "range": [16, 40]}, {"aspect": "vftv:aer", "range": [8, 24]}],
  "main_aspect": "vftv:aqua",
  "clarity": {"faint": 50, "steady": 35, "clear": 15},
  "natural": true,
  "biomes": ["minecraft:is_ocean", "minecraft:is_river", "minecraft:is_beach"],
  "weight": 100
}
```

**Rejected:** anything that fails the node-type load validation; a built-in type's aspects or ranges differing from the table under "Node types".

### Placement

```json
{"schema": "vftv:node_placement/1", "chance": 0.35, "height": [4, 12], "retries": 8}
```

These are the shipped values. **Ranges:** `chance` 0.0–1.0; `height` two integers 0–64, min ≤ max; `retries` 0–32. Every field is required.

### Guarantees

`{"schema": "vftv:node_guarantees/1", "guarantees": {<id>: {"stage", "count", "type", "why"}}}`. The affordability replay reads it to look up a path's `node` source.

```json
{"schema": "vftv:node_guarantees/1", "guarantees": {
  "vftv:starter": {"stage": 1, "count": 1, "type": "vftv:wellspring",
                   "why": "placed at server start near spawn, outside spawn protection, within reach"}}}
```

**The file holds exactly one guarantee**, `vftv:starter`, with `stage` 1, `count` 1 and `type` `vftv:wellspring`, and its composition is the Wellspring's validated one (the six built-in primals at 32). **Rejected:** an empty `guarantees` map; any other id (v1 has no other placement mechanism); any other `stage`, `count` or `type`; a missing or empty `why`. Affordability paths name it in full: `{"node": "vftv:starter"}`.

### Client numbers

```json
{"schema": "vftv:node_client/1",
 "core":   {"base_size": 0.30, "size_per_root": 0.05, "max_size": 1.00,
            "frame_ticks": {"faint": 6, "steady": 4, "clear": 2},
            "scale": {"faint": 0.80, "steady": 0.90, "clear": 1.00}},
 "motes":  {"size": 0.25, "orbit_radius": 0.60, "orbit_ticks": 80, "bob": 0.05},
 "glints": {"every_ticks": 10, "range": 16, "lifetime_ticks": 20},
 "hum":    {"min_interval_ticks": 160, "range": 8, "max_playing": 4}}
```

These are the shipped values. **Ranges:** sizes, scales, `orbit_radius` and `bob` 0.0–2.0; every tick count 1–1200; `glints.range` 0–16 and `hum.range` 0–16 blocks; `max_playing` 0–8. Every field is required. A resource pack's invalid override is ignored with a logged error, and the built-in file is used.

### Fixtures

`{"schema": "vftv:node_fixtures/1", "region": [...], "ring": [...], "quote": [...], "regen": [...], "pulse": [...], "coverage": [...]}`. The first five sections transcribe this spec's vector tables, which an independent reference computed, so the build-phase unit tests check the `model` code against numbers it didn't produce. `coverage` is a reviewed sample of overworld biomes with the natural types each must make eligible. One entry of each:

```jsonc
{"schema": "vftv:node_fixtures/1",
 "region": [{"seed": "0x0123456789ABCDEF", "cell": [-2, 0], "chance": 0.35, "height": [4, 12],
             "eligible": [{"type": "vftv:greenwood", "weight": 100, "aspects": [["vftv:ordo", 8, 24], ["vftv:terra", 16, 40]], "clarity": [50, 35, 15]}
                          /* … wild, windward */],
             "expect": {"h": "0x210C73FDF14FED9D", "spawn": true, "column": [-82, 4], "offset": 10,
                        "type": "vftv:windward", "clarity": "faint", "maxes": {"vftv:aer": 36, "vftv:terra": 22}}}],
 "ring":   [{"seed": "0x0123456789ABCDEF", "k": 8, "start": 27, "first": [8, 3], "second": [8, 4]}],
 "quote":  [{"cap": 0.85, "discount": 0.40, "max_discount": 0.30, "w": 1.00, "base": {"vftv:aer": 10, "vftv:ignis": 3, "vftv:ordo": 1},
             "expect": {"cap": 850, "discount": 300, "warp": 1000, "m": 595, "effective": {"vftv:aer": 6, "vftv:ignis": 2, "vftv:ordo": 1}, "discount_capped": true, "clamped": false}}],
 "regen":  [{"max": 40, "current": 10, "tick": "0x3E8", "now": "0x625", "interval": 80, "divisor": 20, "expect": {"current": 24, "tick": "0x618"}}],
 "pulse":  [{"game_time": "0x14", "period": 20, "rate": 4,
             "sources": [{"vftv:aer": 5, "vftv:terra": 2}, {"vftv:aer": 9}],
             "sinks": [{"vftv:aer": 10, "vftv:terra": 10}, {"vftv:aer": 3}, {"vftv:aer": 10}],
             "expect": {"start": 1, "moves": [[1, "vftv:aer", 0, 3], [2, "vftv:aer", 0, 2], [2, "vftv:aer", 1, 2], [0, "vftv:aer", 1, 4], [0, "vftv:terra", 0, 2]]}}],
 "coverage": [{"biome": "minecraft:plains", "must_include": ["vftv:windward", "vftv:wild"]}]}
```

**Every `long`-typed value** (`seed`, `h`, `tick`, `now`, `game_time`, and `tick` in `expect`) is a hex string, so JSON never rounds it; every other number is a JSON integer. **Rejected:** a section missing; a row of a vector table missing or altered; a `coverage` biome that isn't a vanilla overworld biome; a vanilla overworld biome missing from `coverage`.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text.

| Key | Args | Sense |
|---|---|---|
| `item.vftv.wand`, `item.vftv.chime_rod`, `item.vftv.resonant_brass_cap`, `item.vftv.bell_jar` | 0 | names |
| `block.vftv.node`, `block.vftv.node_tap`, `block.vftv.vis_relay`, `block.vftv.wand_cradle` | 0 | names |
| `wand_rod.<ns>.<path>`, `wand_cap.<ns>.<path>` (every part), `node_type.<ns>.<path>` (every type) | 0 | "Wooden", "Copper", "Wellspring" |
| `node.vftv.clarity.faint`, `.steady`, `.clear`; `node.vftv.unknown_type` | 0 | |
| `attribute.name.vftv.vis_discount` | 0 | "Vis Discount" |
| `tooltip.vftv.wand.parts` | 2 | "%s rod, %s caps" |
| `tooltip.vftv.wand.capacity` | 1 | "Holds %s of each" |
| `tooltip.vftv.wand.cost` | 1 | "Costs ×%s" |
| `tooltip.vftv.wand.pool` | 3 | "%s: %s / %s" |
| `tooltip.vftv.wand.more` | 0 | "Hold Shift to read it" |
| `tooltip.vftv.wand.unfinished` | 0 | "This wand was never finished." |
| `tooltip.vftv.wand.missing_rod`, `.missing_cap` | 1 | "Its rod is missing: %s" |
| `tooltip.vftv.bell_jar.empty` | 0 | "Right-click a node to take it up" |
| `tooltip.vftv.bell_jar.holds` | 3 | "A %s %s node (no. %s)" (clarity, type, id) |
| `hud.vftv.vis.unfinished` | 0 | |
| `hud.vftv.vis.missing` | 1 | |
| `message.vftv.tap.unlearned`, `.tap.inert`, `.tap.unknown`, `.tap.nothing`, `.tap.full`, `.tap.empty` | 0 | the tap refusals and stops above |
| `message.vftv.spend.unlearned`, `.spend.inert`, `.spend.stale` | 0 | |
| `message.vftv.spend.short` | 1 | "Not enough %s." (the short aspects, joined) |
| `message.vftv.jar.unlearned`, `.jar.anchored`, `.jar.unknown`, `.jar.unreadable`, `.jar.stale`, `.jar.no_room`, `.jar.crowded` | 0 | the jar refusals above |
| `message.vftv.jar.too_close` | 1 | "Too close to another node (%s blocks)." |
| `message.vftv.relay.too_many` | 2 | "Too many voices for one chord: %s of %s." |
| `message.vftv.relay.no_node`, `.relay.taken` | 0 | |
| `status.vftv.network` | 4 | "A chord of %s of %s voices: %s listening, %s charging." |
| `status.vftv.node_tap.idle` | 0 | "Nothing to listen to." |
| `status.vftv.node_tap.listening` | 5 | "Listening to a %s %s (no. %s). Toward a clearer ring: %s of %s." |
| `status.vftv.wand_cradle.empty`, `.full`, `.idle` | 0 | |
| `status.vftv.wand_cradle.charging` | 2 | "Charging: %s of %s." |
| `commands.vftv.node.removed`, `commands.vftv.node.unknown` | 1 | |
| `subtitles.vftv.<event>` for every sound event | 0 | |

```json
{"schema": "vftv:lang/1", "strings": {
  "item.vftv.wand": "Wand",
  "node_type.vftv.wellspring": "Wellspring",
  "message.vftv.tap.unlearned": "I don't yet know how to draw from it.",
  "message.vftv.relay.too_many": "Too many voices for one chord: %s of %s.",
  "subtitles.vftv.wand.tap_drone": "Wand draws"
}}
```

**Rejected:** a key outside the table's patterns; a part or type key with no matching entry; a required key missing in `en_us`; a placeholder count that differs from the table; an empty string; a duplicate key.

## Events

NeoForge post-events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server **after the whole commit**, through a small publisher seam that `neoforge` implements (`core` stays loader-free). Their payload records (`VisQuote`, `VisContext`, `NodeRef`) live in `api`, which the separately compiled API source set requires.

| Event | Payload |
|---|---|
| `VisSpentEvent` | player; level and position; the `VisContext`; the `VisQuote` (base, effective, `m`, the factors `cap`, `discount` and `warp`, flags, spill) |
| `NodeEvent` | kind; id; dimension and position (absent while carried); cause `WORLDGEN \| STRUCTURE \| STARTER \| PLAYER \| COMMAND \| RECONCILE \| API` |

**Kinds:**
- `CREATED`: an id was minted, or an unknown id was registered by recovery. Never posted for a move.
- `CAPTURED` and `PLACED`: a Bell Jar took it up or set it down. `PLACED` is never posted for a birth.
- `REMOVED`: an id was tombstoned. It is posted once, whichever path did it.
- `CULTIVATED`: a cultivation transaction committed.
- `CHANGED`: a `NodeService.update`.

No event fires for a tap or relay pulse, regeneration, a duplicate's removal, or a removal-hook call that found nothing to do; no sibling needs them.

**Plain `core` services:**
- `VisService.quote` and `spend`;
- `NodeService.get(id)` (registry state, dimension and position);
- `readout(level, pos)` (loaded nodes only, settled);
- `nearest(dimension, point, r)` (memory only);
- `remove(id, cause)` (works on unloaded ids; the block is removed at reconciliation);
- `update(id, change, cause)`: loaded nodes only, otherwise refused as `NOT_LOADED`. The change may set a new type (registered), new aspects (at most 6, maxes 1–1000) or a new clarity. It settles first and rebases after; it keeps the id, position and the counters of aspects that remain; `current` clamps to a lowered max, and an added aspect starts at current 0.

Whether these are exposed in `api` is the API spec's call.

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| wand parts and vis | item components | as items are |
| node state | node block entity | trackers: the full state in the chunk's initial block-entity data (`getUpdateTag`), then on change through the block-entity update packet |
| captured node | `vftv:captured_node` component | as items are |
| node registry, tokens, starter | `vftv_nodes` SavedData (overworld) | never |
| index, chunk → ids lookup, pending reconciliation | memory | never |
| relay membership | `vftv_relays` SavedData (per dimension) | never |
| components, spatial hash, chunk → members lookup, membership queue, active sinks | memory | never |
| cradle wand | cradle block entity | trackers: initial data, then on insert and take only (charging changes no packet) |
| tap sessions | transient per-player memory | never |
| `vftv:vis_discount` | a syncable attribute (default 0, range 0–1) | the owner, as vanilla syncs attributes |
| vis rules | the tuning record and the fixed limits | **`VisRules`**, S2C to every player through `OnDatapackSyncEvent` (join and `/reload`) |

- **Coalesced updates:** tap pulses, relay pulses and cultivation mark node block entities dirty in a per-level set, which is flushed once per tick, after the level ticks: **at most one update packet per node per tick**, however many players and networks touched it. Hand-tap pulses are aligned on the global clock, so a node tapped by any number of players sends at most one update per `tap.interval` from tapping.
- **One custom payload,** `VisRules`, server to client, carrying everything the client's quote, regeneration preview and book facts need. **No client-to-server payload:** every input arrives through vanilla use, interact and placement packets, validated as such (reach, held item, research, protection).
- **Client caches** (HUD layout, tooltips, book facts) are invalidated when `VisRules` arrives, when the discount attribute changes and on resource reload.
- **Formats:** every persisted record (block entities, the `vftv:vis` and `vftv:captured_node` components, SavedData) carries `format: 1`. The exception is the scalar `vftv:wand_rod` and `vftv:wand_cap` components: they are bare ids in their native `ResourceKey` codec, which the item model selects on, and an unknown id is already handled as a missing part. A record with an unknown format, or one that fails its bounds, is **kept as its raw tag and written back unchanged**, with one log line: the node or jar is dormant rather than deleted, so a newer save opened by an older build loses nothing. Reconciliation leaves a dormant node block entity alone; it is neither minted nor counted as a ghost.
- **Bounded codecs:** node aspects ≤ 6; `vftv:vis` ≤ 16 entries; amounts and maxes 0–1000; counters below 64 and Sonus below 4,096; ids and tokens ≥ 0; relay members per SavedData record ≤ 2²⁰; `VisRules` a fixed record. Decoding checks the **format bounds**, never current tuning; changed tuning is reconciled afterwards (settlement, rebase and counter carry), so a lowered threshold never invalidates a saved node.

**Pack changes:**

| Change | Effect |
|---|---|
| A rod's capacity lowered | pools clamp on read; the excess is lost at the next write |
| A part removed | wands holding it are inert and untouched; restoring the pack restores them |
| A node type removed | its nodes are **dormant**: kept verbatim, drawn as a core only, read as "Unknown", untappable, uncapturable and skipped by relays |
| An aspect removed | node entries and wand entries for it are kept and ignored |
| Tuning `/reload` | a new record; `VisRules` resent (book facts re-resolve); nodes rebase at their next settlement; a range change rebuilds components or rejects the reload; stale quotes are refused by echo |
| Tuning changed while the server was stopped | as above, except that a range change producing a component over 32 refuses the world, with the actionable error under "Topology" |
| Registries | need the world reopened |
| Resource reload | HUD layout, client numbers and icons rebuild |

## Tuning

Every tunable number, all ours. Unless noted, the home is the server-only datapack file **`data/vftv/tuning/vis.json`**, loaded on reload into an immutable record in `core.vis` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client receives the whole record in `VisRules`.

| Key | Value | Range | Notes |
|---|---|---|---|
| `tap.interval` | 4 ticks | 1–40 | aligned pulses |
| `tap.amount` | 2 | 1–64 | per vis aspect per pulse |
| `regen.interval.faint`, `.steady`, `.clear` | 160, 80, 40 ticks | 1–24000 | |
| `regen.divisor` | 20 | 1–1000 | step = max(1, max ÷ divisor) |
| `relay.range` | 10.0 blocks | 2.0–32.0 | |
| `relay.pulse` | 20 ticks | 1–200 | |
| `relay.rate` | 4 | 1–1000 | per sink per vis aspect per pulse |
| `cultivate.per_max` | 4 essentia | 1–64 | per +1 max |
| `cultivate.clarity_cost` | 48 Sonus | 1–4096 | per clarity step |
| `cultivate.max_capacity` | 250 | 1–1000 | |
| `cultivate.suction` | 16 | 0–128 | on the [essentia scale](essentia.md#suction): 0 disables automatic pulling (never insertion), 1–15 is the storage band, 16–128 the consumer band |
| `node.min_spacing` | 3.0 blocks | 1.0–16.0 | Bell Jar releases and the starter |
| `quote.max_discount` | 0.30 | 0.0–0.9 | |
| `quote.min_multiplier`, `quote.max_multiplier` | 0.50, 4.00 | 0.1–1.0, 1.0–10.0 | |
| `starter.min_ring`, `starter.ring`, `starter.height` | 8, 24, 3 | 1–64, 1–64, 2–5 | `height` ≥ 2 keeps the standing spot clear; ≤ 5 keeps the node in reach |

**Other homes:**
- part values: `vftv:wand_rod` and `vftv:wand_cap` entries (`content/wands/parts.json`);
- node compositions, clarity weights, divisors, spread weights and biome tags: `vftv:node_type` entries (`content/nodes/types.json`);
- `chance` 0.35, height [4, 12], `retries` 8: the configured feature `vftv:node` (`content/nodes/placement.json`);
- the core, mote, glint and hum numbers: `assets/vftv/nodes/client.json` (`content/nodes/client.json`), overridable by resource packs;
- `hud.x`, `hud.y` (default 4, 4): client config.

**Fixed limits, in code** (formats and invariants, not balance): 64-block cells; 8 nodes per cell, of which at most 1 natural and 2 structure at minting; 6 aspects per node; 8 vis aspects; 32 members per network; the 180-block starter cap; the `nearest` radius ≤ 256; the 72,000-tick use duration; the 64 slot changes per spend; the HUD geometry; the codec bounds; base costs 0–1000. **Fixed feedback constants:** the 40-tick drone retrigger; 2 tap glints per pulse; the 20-tick absorb-sound cooldown; the global cap of 64 live glints; the block-entity view distance of 64.

## Performance

**Heavy-base workload:**
- one base of 16 cultivated nodes in 2 full cells (every aspect at 250, clear) with 16 taps, in 2 networks of 32 members (8 taps, 4 cradles and 20 relays each), whose 8 cradle wands are drained every second so the sinks stay active;
- one player hand-tapping continuously, and 3 more tapping one node with staggered starts;
- one player making 4 quote-and-spend calls a second (casts and crafts);
- sounding at full cell density (49 cells × 8), with moved nodes;
- one join near the base.
- **Client:** 16 nodes within 24 blocks and 40 more within 64, with the HUD up.

**Share:** server ≤ 0.04 ms mean, ≤ 0.2 ms p99; client ≤ 0.1 ms p50. **Measured separately as load scenarios** (reported): active-sink churn (64 cradles gaining and losing wands), a merge flood (one relay a tick joining components up to the cap), deferred reconciliation (10 chunks a tick, each with 8 nodes and 32 relay members), a tuning `/reload` that rebuilds components, and startup with the starter search at a spawn protection of 120.

**Hot paths:**
- **Server, per tick:** the node reconciliation queue (O(node block entities + registry entries) per queued chunk), the relay membership queue (O(member block entities + persisted members) per queued chunk, plus ≤ 3,904 checks per admission and ≤ 496 per affected component), and the coalesced update flush (≤ 1 packet per dirty node). Nothing else, except:
- **per tap pulse,** per tapping player every `tap.interval`: one raycast, ≤ 6 transfers;
- **per relay pulse,** every `relay.pulse`, per component with an active sink: ≤ active sinks × 8 vis aspects × sources transfers, at most 31 × 8 × 31 = 7,688 per component. Components without an active sink cost nothing.
- **Per sounding:** ≤ 392 distance checks, 0 chunk loads.
- **Per worldgen chunk:** O(1) hashing and at most one placement, with an air search of ≤ `retries` + 1 blocks.
- **Per relay placement:** ≤ 3,904 member distance checks; **per removal:** a rebuild of ≤ 496 checks.
- **Per spend:** O(vis aspects).
- **Allocation:** **zero** in the numerical kernels, which were designed for it: the quote maths, settlement, the pulse plan over preallocated arrays, and `nearest` (allocation-budget tests). The mutation step is finite, not zero: at most one `vftv:vis` component per changed wand and one update packet per dirty block entity, asserted by an allocation test against that count.
- **Client, per frame:** ≤ 7 quads per visible node and ≤ 504 in total; ≤ 64 glints alive (an admission cap, not an estimate); zero allocation in our renderer and HUD draw code.
- **Packets:** ≤ 1 block-entity update per node per tick, and none for charging cradles; one `VisRules` per player per join or reload.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **the quote:** every vector above, factors included; half-up rounding at the per-mille boundary; clamps and flags; zero base; `exceeds_capacity`; the maximum-factor case without overflow; a non-finite factor; `ORDINARY` against `VOID` contexts through a stub factor; echo equality on each cost field;
- **the spend:** all or nothing at every step; a slot whose contents no longer match `expectedBefore` refuses; **an experience change** whose `expectedLevel` no longer matches refuses, one that commits lowers exactly the level and leaves progress and total experience, a throwing later slot change restores the level, the commit posts no `PlayerXpEvent`, and the owner's client receives the debited level, progress and total; a zero price carries no experience change; a second experience change, or one naming another player or raising the level, is rejected; **a deliberately partial, throwing commit** (the second of three slot changes throws) restores the wand's vis, the material slots and the output slot, tells the boundary `applied(false)` once, and posts no event; duplicate slots and more than 64 changes are rejected; **the boundary** is called exactly once, after the last write and before `VisSpentEvent`, on commit and on rollback, and never on a step 1–5 refusal; **a throwing `VisSpentEvent` listener** leaves the commit in place and returns `COMMITTED_NOTIFY_FAILED`;
- **pools:** clamping on a lowered capacity; dormant entries kept through writes; both inert cases untouched;
- **regeneration:** every vector above; remainders kept; every clarity; a long carry; a clock going backwards; rebase after a tuning change prices the past at the old parameters;
- **natural spread:** every region vector; the stream order; weighted picks at every boundary; cell keys for negative coordinates; the spawn rate over a large sample;
- **the starter ring:** every ring vector; each perimeter complete and contiguous; candidate rules (border, height, protection edge at exactly `P` and `P + 1`, the 180 cap, standing spot, reach, spacing, full cell); the **effective** radius for every combination of dimension, empty or non-empty operator list and radius 0 or positive;
- **the Wellspring and guarantee rules:** a registry with seven or eight exposed primals is accepted, with the Wellspring still holding exactly the six built-ins at 32; a Wellspring missing one, holding a seventh, or with any other range is refused; an empty guarantee map, another id, or a wrong stage, count or type is rejected;
- **`nearest`:** exact radius and tie edges, a full 7 × 7 × 8 window, the 256 limit;
- **reconciliation:** every row of its table and the ghost rule, including a duplicate whose removal keeps the original's entry, recovery advancing `next_id`, and the cell quotas by origin;
- **capture tokens:** a stale copy refused after a release, and after a recapture;
- **relays:** components, the 32 cap on create and merge, the early refusal, a split on removal, range rebuild with reload rejection and world-open refusal, every row of the membership reconciliation table, same-tick admissions in position order, **pulse order with the rotating start** (every vector above), and **conservation** (vis out of nodes = vis into wands);
- **cultivation:** bulk and partial acceptance, a new aspect's slot reserved, cap saturation, clarity steps, counter carry after lowered thresholds, refusal of other essentia;
- **coverage:** the union of `aspects` over the built-in natural types contains the six built-in primals, and each is the `main_aspect` of at least one natural type; every vanilla overworld biome in `fixtures.json` has its listed eligible types;
- **fixtures:** every section of `content/nodes/fixtures.json`, checked against the `model` code;
- **every `content/` rejection rule** above;
- **codecs:** every component, block entity, both SavedData, every registry entry, the tuning file, `VisRules` and every `content/` schema, with defaults, ranges, format 1, and the raw-tag fallback for unknown formats.

**GameTest:**
- the registries and tuning load, and each bad entry and file is refused with a clear error; the `vftv:wellspring` rules;
- **wand assembly:** caps must match; click, shift-click and crafter paths; an unlearned player crafts it but cannot tap; no vanilla recipe matches the built-in parts' pattern;
- **tapping:** each refusal (and that it is consumed, so no air use follows); pulse amounts on aligned ticks; every stop condition, including a revoked entry, **switching to another, otherwise identical wand**, changing the selected hotbar slot, a lost raycast, spawn protection and a node swapped for another id; the tap's own in-place writes never stop it; two players on one node;
- **spending:** refused spends leave wand, materials and output unchanged; a stale `expected` refused; a failing output (a full output slot) refused; a throwing slot change mid-commit leaves vis, materials and output as they were with no event; a `VisSpentEvent` listener that changes a named slot sees the commit complete and the boundary already called; a throwing `VisSpentEvent` listener leaves vis, materials and output committed and returns `COMMITTED_NOTIFY_FAILED`; ordinary against Void quotes;
- **starter placement:** a fresh world; an existing world; **active** spawn protection 0, 16 and 120 (operators present), with a **non-op player striking it with the fork and tapping it**; startup failure at active protection 180 with the actionable error; configured protection 180 with **no operators** succeeding on suitable terrain; the later-start warning after an operator is added; no duplicate when the starter's chunk is unloaded; re-placement after `/vftv node remove`;
- **sounding loads 0 chunks** at full density, after moves and removals;
- **identity:** capture then release keeps the id and pays no second first-strike reward; anchored refusal; cell-full and spacing refusals, including spacing equality; a copied jar refused after the original is released and again after a recapture; an unknown id refused in survival and recovered in creative;
- **cancellation:** a cancelled `BreakBlockEvent` on capture and a cancelled `EntityPlaceEvent` on release each change nothing: block, jar, registry, index and events. A listener on the release event sees **the node as the placed block and air as the replaced snapshot**, with the real player. A creative recovery refused by the event, or by a later check (spacing, border), leaves the registry, `next_id` and `next_token` unchanged and posts no `CREATED`;
- **reconciliation:** a lazily loaded chunk whose node block entities are still saved data; a ghost; a duplicate removed while the original keeps its entry; a rolled-back chunk against a carried id; removal of an unloaded id, then its chunk loading;
- **structures:** two copies of one template get two distinct ids; a third structure node in one cell is removed;
- **relays:** a pulse through an unloaded intermediate relay; an unloaded source skipped; no chunk tickets created; the rotating start reaching every cradle; a merge refused at 33; a cancelled member placement leaving no membership; a range `/reload` that would exceed 32 rejected, and the same range at world open refusing the world with the actionable error; a **ghost member** (persisted, block gone after an interrupted save) removed when its chunk loads; members made by `/setblock` and `/clone` admitted, or broken when over the cap; a member replaced by another kind updated;
- **cultivation:** through a transaction that rolls back (node state identical) and one that commits (one event, one sync); the suction capability is present on exactly the handler's faces and absent on the node's; `cultivate.suction` = 0 stops an essentia line pulling while a direct insert still succeeds;
- **packets:** three staggered players tapping one node send at most one update per node per tick;
- save and reload of every block entity, component and SavedData, with dirty marking;
- every test id in the discovery guard.

**E2E:**
- the wand HUD for main hand, offhand and both, with 6 rows and an inert wand, plus the Shift tooltip and item bar (screenshots and a state dump of the component);
- nodes at 3 clarities and with 1, 3 and 6 aspects, plus a dormant node (screenshots);
- tapping particles, sounds and subtitles;
- sounding to a moved node;
- a relay base charging a cradled wand (a dump over time) and the status lines;
- sync on join, reconnect and `/reload` (`VisRules`, node state, quotes);
- the asset, lang and model validators: every key under "Lang keys", every sound event and subtitle, every layer of `items/wand.json`.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Aspects and scanning spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/15)'s contracts on this ticket):

| Aspects contract | Met by |
|---|---|
| Owns node placement and the node index; `nearest(pos, radius)` for the 192-block radius examines a bounded set of cells, with a declared per-cell cap, and never loads or generates a chunk | "The index": at most 49 cells × 8 = 392 entries at 192, memory only. `nearest` accepts radii up to 256; the aspects spec's tuning now bounds `sounding.radius` to 256 and validates its band edges against it (amended in the same change). GameTest: sounding loads 0 chunks at full density after moves and removals. |
| Node ids are stable through moves; creating or re-placing a node never mints a fresh identity | "Identity and reconciliation": ids are minted only at birth; only the entry placed at a position can be tombstoned from it, so removing a duplicate keeps the original. "Moving nodes": capture tokens refuse stale copies. Capture commits block, jar, registry and index together after an uncancelled `BreakBlockEvent`. Release installs the node provisionally, asks `EntityPlaceEvent` about the real node, and commits jar, registry and index only on acceptance, restoring air on cancellation; a creative recovery registers only then. GameTests for each. |
| A real, usable starter node within 192 blocks of a named bootstrap location, proved by a starter-placement test | "The starter": the bootstrap is the overworld respawn position at placement. The node is outside the **effective** spawn protection at placement (vanilla's every condition: dimension, a non-empty operator list and a positive radius), within 180 in 3-D, with a standing spot in fork and wand reach, and its id is persisted. Startup fails with an actionable error if this is impossible. **Qualified:** protection that becomes active later (an operator added, the radius raised) can cover it; the next start warns and names the command that re-places it. GameTests: a non-op strikes **and taps** it at active protection 0, 16 and 120; configured 180 with no operators succeeds. |
| Node-type data supplies aspects and max capacity, may override `node_reward_divisor`, and declares the maximum aspects per node | "Node types": `aspects` with ranges, `node_reward_divisor`, and the fixed limit of 6 |
| (What a strike reveals) a node readout shows its type and "brightness" | **Clarity** is the brightness: `NodeService.readout` supplies the localised type, the clarity and exact current / max after settlement |

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts on this ticket):

| Research contract | Met by |
|---|---|
| The node types that generate in Stage 1 terrain together carry all 6 exposed primals (a unit test), and first-strike rewards are per player per node | The built-in types; the coverage unit test (the union of the built-in natural types' aspects contains the six built-in primals, and each is some natural type's `main_aspect`). An addon's seventh or eighth primal is that addon's to supply. First strikes stay the aspects spec's ledger; cultivation and moves never pay one again. |
| Node guarantee ids, each with its composition, capacity, count and Stage | **`vftv:starter`**: Stage 1, one node per world, type `vftv:wellspring`, the six built-in primals at exactly 32 each (enforced by the Wellspring's load validation), authored in `content/nodes/guarantees.json`, which must hold exactly this guarantee. Paths name it in full, `{"node": "vftv:starter"}`; a bare `"starter"` would parse as `minecraft:starter`, so the research spec's examples and contract are amended to `vftv:starter` in the same change. It counts only once its placement and access GameTests pass. |
| Stable recipe ids for the wand and tapping entries, and those recipes carry `research` | **Reconciled, not met as worded** (see "Stage": gate actions, not the craft). No proven hook gates the vanilla crafting grid before the result is taken. So the wand's stable recipe id is **`vftv:wand_assembly`, ungated**; `vftv:first_wand` is the entry that teaches it (a `free` or `note` entry, whichever the Stage 1 roster needs) and gates nothing. `vftv:tapping` has **no recipe**: it gates the tap action, checked on the server at the start and at every pulse. The contract therefore now reads: *a stable, ungated recipe id for the wand, and a server-checked action gate for tapping*. The research spec's contract and roster are amended to that wording in the same change, and it is recorded in the hand-off to [Stage 1 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/34) below. |
| Node scan keys stay `node:<stable id>` through moves | Decimal ids, never re-minted; the moved-node GameTest |

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)**, handed to [Stage 1 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/34) and [Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35):
- **Stage 1:** `vftv:first_wand` is `free` or `note`, gates nothing, and may show `vftv:wand_assembly` on a recipe page (ungated recipes pass the recipe-page validator anywhere). `vftv:tapping` is a `note` whose learning enables tapping; it has no recipe page of its own.
- **Stage 2:** entries **`vftv:singing_wands`**, **`vftv:cultivating_nodes`**, **`vftv:vis_relays`** and **`vftv:moving_nodes`**, gating the recipes under "Authored content, Recipes". `vftv:moving_nodes` also gates capture and release.
- **Affordability paths** name the starter as `vftv:starter` (research spec amended).
- **Fact source `vis`:** the research spec's fact grammar now includes `vis : <VisRules key>`, re-resolved whenever `VisRules` arrives (join and `/reload`), so pages state tapping rates, cultivation prices, relay range, cap and throughput live (research spec amended). The research lints keep these numbers out of prose.

**[Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)**
- **Every vis charge** goes through `VisService.spend` with the recipe id as `source`, `kind` `ORDINARY` (or `VOID` for Void-tier recipes), `research` set to the recipe's gate, `expected` echoed from the preview, and an `effect` of prepared slot changes: each material slot it consumes and the output slot it fills, with the contents it expects before. Items, vis and output commit together or not at all, and `VisSpentEvent` comes after. GameTests: a stale quote, a failed output (a full output slot) and a material slot changed between preview and commit each refuse and change nothing.
- Its cost preview embeds `VisService.quote` unchanged.
- **Stage 1 affordability:** every Stage 1 arcane recipe has a base cost ≤ 32 per aspect, so a wand filled from the starter alone pays any of them at zero warp (a unit test over recipe data), and the first arcane craft's materials and research are reachable before it in the Stage 1 path.
- Its arcane recipe type carries the Stage 2 recipes at the ids and gates under "Authored content, Recipes"; its recipe schema is the body of those entries.
- Base vis costs are validated at load: vis aspects only, 0–1000 each.
- Its wand slot accepts only `vftv:wand` and never charges it. Turning a block into its worktable with a wand is its own right-click listener, and works with a focused wand too; the wand passes on blocks that aren't nodes.

**[Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)**
- Tubes and jars feed a Node Tap only through the essentia capability, inside a transaction whose rollback they honour.
- It reads the tap's suction as `cultivate.suction` for an aspect the tap would accept and 0 otherwise. If #20's suction scale differs, #20 states the mapping and this spec's default follows it. *Met by the [essentia spec](essentia.md#suction): the scale is 0–128, so no mapping is needed, and 16 is the bottom of its consumer band. The tap exposes suction on its handler's faces, is in `vftv:essentia_endpoints`, and holds at rank 0 (amended under "Cultivation").*

**[Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22)**
- A focus is its own component on `vftv:wand`; #22 adds the third `select` layer to `items/wand.json` and its assets.
- Every cast spends through `VisService.spend` with the focus id as `source` and the focus's `kind`. A cast short of vis does nothing and names the short aspects; a held cast spends per pulse at an interval it states. Foci never draw from nodes or charge wands.
- **Interaction:** `use` on air or an entity casts; **sneak-use on a block that isn't a node** is #22's block-targeted path; use on a node is always tapping. A refused cast consumes the click.
- *Met by the [foci spec](foci.md#contracts-on-sibling-specs): `vftv:wand_focus` and the appended third model member (`vftv:none` → `minecraft:empty`, `unknown` as the fallback); every cast through `spend` with the effect id as `source` and the effect's kind; a short cast refused before anything is applied; the Beam spending every `pulse` ticks with no double payment at its start; no node or charging path; main-hand gestures separated from target domains, every refusal consuming the click; and its failures after the spend stated per medium. Amended here in the same change: the experience change in the spend's prepared state, the one-session dispatch with its exposed kind, and the harvesting answer.*

**[Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24)**
- Vis discounts exist **only** as modifiers on the attribute `vftv:vis_discount` (registered here: default 0, range 0–1, syncable), which the quote caps at `quote.max_discount` and flags. GameTest: gear past the cap shows `discount_capped` and the capped cost, identically on client and server.
- **No vis-storing trinket in v1.** Nodes are the only source and rods set capacity; a trinket that stores vis would need its own proposal of a conserved transfer and capacity rule, as an extension to this spec. Everything works without Curios.

**[Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)**
- It supplies `vis_factor(player, ctx)`, a finite pure function of the owner's synced warp state and `ctx.kind`: ≥ 1.0 for `ORDINARY`, and whatever it states for `VOID` (which may be below 1.0). Client and server compute the same value.

**[Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)**
- It supplies `spill(inputs, ctx)`, a pure function used inside the quote, and applies flux on `VisSpentEvent` equal to that event's `spill`. GameTest: previewed spill = applied flux.
- It adds no flux inside this system: tapping, relays, regeneration, cultivation and capture.
- **Hush node types** are `vftv:node_type` entries with `natural: false`. Hush changes nodes only through `NodeService.update` and `remove`, and relay members only by ordinary block removal; it never rewrites a node's id, and there is no autonomous natural Hush source.

**[Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)**
- Structure templates save node blocks with the id **unset**, `origin: structure` and a valid type. At most 2 structure nodes per 64×64 cell are minted, **across every template copy**, and any surplus is removed with a warning, so its templates place at most one node-bearing piece per cell. GameTests: two copies of one template get two distinct ids; a third structure node in one cell is removed.
- It places no natural nodes and doesn't remove the `vftv:add_nodes` biome modifier; its processors never replace node blocks.

## Production tickets

This spec settles every decision. Three required child tickets deliver the assets and the authored content, and **the system is not done until all three land**. **None of them writes mod code:** no renderer, menu, registry, transfer capability, network code, item-model property, datagen provider or validator. The asset tickets deliver generators and assets under the [art style](art-style.md); the content ticket delivers authored inputs under `content/`, in the schemas above. The runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Wand assets** | The generator `tools/art/wands.py`. **Layer textures**, 16×16 at `assets/vftv/textures/item/wand/rod/{wooden,chime,unknown}.png` and `…/wand/cap/{copper,resonant_brass,unknown}.png` (the cap layer carries both caps and the teal tick), with their layer models `vftv:item/wand/rod/<id>` and `vftv:item/wand/cap/<id>`. **Item icons** `chime_rod` and `resonant_brass_cap` (16×16, Stage 2 row), with models and item definitions. `assets/vftv/items/wand.json` exactly as under "Components". The **HUD frame** `assets/vftv/textures/gui/sprites/hud/vis_frame.png` (16×16, nine-slice border 4, in its `.mcmeta`). **Sounds:** `wand.tap_start` ×3, `wand.tap_stop` ×3, `wand.full` ×3, and `wand.tap_drone` ×3 at 3.0 s with the standard faded tail, with their `sounds.json` entries naming the subtitle keys under "Lang keys". A contact sheet at GUI scale 2 with all 4 rod × cap wands and the unknown fallbacks beside vanilla; provenance rows. | none |
| **Node, jar and relay assets** | The generator `tools/art/nodes.py`. The **node core strip** `assets/vftv/textures/block/node_core.png`, 16×128 (8 frames, teal ramp, cutout), and the node's blockstate and particle-only model. The **glint** particle: 4 frames at 8×8 (`textures/particle/node_glint_0…3.png`) and `particles/node_glint.json`. The **Bell Jar** item, empty and filled at 16×16, with an item definition selecting on `minecraft:has_component` for `vftv:captured_node`. **Block models** of at most 6 cuboids each ([art style §6](art-style.md#6-blocks-and-models)), textures, blockstates and item forms for `node_tap` (an ear-trumpet; six facings by blockstate rotation), `vis_relay` (a fork on a stand) and `wand_cradle`. **Sounds**, with the cadences under "Rendering and sound": `node.hum` ×3, `node.absorb` ×3, `node.clarity_rise` ×1, `relay.link` ×1, `cradle.charge` ×3, `bell_jar.capture` ×1, `bell_jar.release` ×1, with `sounds.json` entries naming the subtitle keys under "Lang keys". Blender renders of every non-cube model; provenance rows. **No mote sprite**: motes reuse the aspect icons. | none |
| **Wand and node content** | Every file under "Authored content", in its schema: `content/wands/parts.json`, `content/wands/recipes.json` (the Stage 2 bodies in the `vftv:arcane` format), `content/nodes/types.json` (biome tags, spread weights and clarity weights for the built-ins), `content/nodes/placement.json`, `content/nodes/guarantees.json`, `content/nodes/client.json`, `content/nodes/fixtures.json` (the vector tables transcribed, plus the reviewed coverage list), and `content/lang/en_us/wands.json` with every key under "Lang keys" | none (the Stage 2 recipe bodies use the [arcane recipe type](arcane.md#the-recipe-type)'s format, frozen) |

The Stage 1 and Stage 2 research content tickets ([#34](https://github.com/andrewbrogoff/voices-from-the-void/issues/34), [#35](https://github.com/andrewbrogoff/voices-from-the-void/issues/35)) consume this spec's entry ids, gates and the recorded change to the research contract.
