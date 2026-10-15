# Crucible alchemy

A vessel of boiling water that dissolves thrown items into **essentia** and spends that essentia to transform a **catalyst**: the **crucible** block, its pool, the one recipe type `vftv:crucible`, the spill that emptying costs, the extract-only face that essentia logistics pull from, **Resonant Brass**, and the authoritative preview that the book and JEI read. Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). The code layout, data rules and sync rules come from the [architecture spec](architecture.md), every gate from the [quality gates](quality-gates.md), aspect values, knowledge and icons from the [aspects spec](aspects.md), research gates, recipe pages and fact fields from the [research spec](research.md), the Worktable and the `vftv:arcane` recipe type from the [arcane crafting spec](arcane.md), the HUD frame and the coalesced-sync and dormant-record patterns from the [wands spec](wands.md), and every asset from the [art style](art-style.md).

Resolved in [Crucible alchemy spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/19). Inputs: [TC4 alchemy](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-alchemy/docs/research/tc4-alchemy.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. Each tunable one is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 2 Distillation | the crucible, dissolving, the pool and its spill, the `vftv:crucible` recipe type, the extract-only essentia face, and Resonant Brass | the opener `vftv:distillation`, whose find `vftv:find/distillation` is [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s ([research, Stage](research.md#stage)). Inside the Stage, the two `note` entries below. |
| 3–4 | nothing of its own | other specs' crucible recipes ride the same type, each behind its own `research` |

| Entry | Kind | Parents | Gates |
|---|---|---|---|
| **`vftv:crucible`** | `note` | `vftv:distillation` and `vftv:arcane_crafting`, as in the research spec's [own example](research.md#entry-files) | the arcane recipe `vftv:crucible` (the vessel) |
| **`vftv:resonant_brass`** | `note` | `vftv:crucible` | the crucible recipe `vftv:resonant_brass_ingot` |

- **Gate the recipe, not the vessel.** Dissolving is ungated: a crucible someone gave you dissolves for you, as a Worktable works for anyone ([arcane crafting, Stage](arcane.md#stage)). **Obtaining counts.**
- **Every crucible recipe carries its own `research`**, checked on the server against the item's **thrower** before anything is consumed, and again before anything is debited (see "Selection" and "Committing a craft").
- **Options considered:** a separate find for the crucible; gating dissolving itself. Gating the use of a block someone handed you buys nothing, and the progression spec already puts the crucible and Resonant Brass in Stage 2 ([progression, Stages](progression.md#stages)).

## The crucible

### The block

**`vftv:crucible`**: a block with a block entity.

| Property | Value |
|---|---|
| Behaviour properties | `BlockBehaviour.Properties.ofFullCopy(Blocks.CAULDRON)`: the cauldron's strength, sound, map colour and tool requirement |
| Shape | outline and collision: the full cube minus the **interior box** `[2,4,2]–[14,16,14]` px. The entity-inside shape (`getEntityInsideCollisionShape`) is the interior box. |
| Block state | `water` (boolean, default `false`) and `boiling` (boolean, default `false`); no facing; not waterloggable |
| Use | `useItemOn` handles buckets (see "Water"); everything else passes |
| Item | the block item `vftv:crucible`, stacking to 64 |
| Piston push | none: vanilla's piston refuses any block with a block entity |
| Comparator, redstone, random ticks, entity damage | none |

**Build-phase data** (datagen from code, not production deliverables). Copying the cauldron's properties copies **no tag membership**, so the crucible declares its own: `vftv:crucible` in `minecraft:mineable/pickaxe`; the loot table `vftv:blocks/crucible` dropping itself (the vanilla `dropSelf` shape, explosion survival included); the block tag `vftv:crucible_heat` and the item tag `vftv:crucible_refuses`, with the defaults below; and membership of the [essentia spec's](essentia.md#tags) block tags `vftv:essentia_endpoints` (Essentia Tubes connect to it) and `vftv:alembic_bases` (an Alembic may hang on it), whose defaults include `vftv:crucible`. The crucible's aspect values are [Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)'s hand entry (see the contracts).

**It is crafted, not converted.** The arcane recipe **`vftv:crucible`** (`kind: ordinary`, `research: vftv:crucible`), made at the Worktable. Its content rules ("Authored content, Recipes"):
- its ingredients include `minecraft:cauldron`, and **every ingredient is a `minecraft:` item or tag**, so the path to it never needs Resonant Brass, which needs this crucible;
- its `vis` uses only the six built-in primals, each ≤ the Wellspring's per-aspect amount in `content/nodes/types.json` (32 today), so a wooden-rod, copper-cap wand filled at the Wellspring pays it at zero warp. The chime rod needs Resonant Brass ([wands, Parts](wands.md#parts)), so no better wand can exist yet;
- its research `vftv:crucible` is learned in [Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35)'s affordability path (see the contracts).

**Options considered:** a wand-on-cauldron conversion (as TC4 did); a vanilla-grid recipe. **Recommend** the arcane recipe: it gives the server-side research gate, the recipe page and JEI for free ([arcane crafting, Arcane recipes](arcane.md#arcane-recipes)), the research spec's example already names a `vftv:recipe` page `vftv:crucible`, which a conversion can't supply, and a second conversion listener would repeat the Worktable's whole `BreakBlockEvent` commit for no gain.

### Heat

A crucible is **heated** iff the block state directly below it is in the block tag **`vftv:crucible_heat`** and either has no `lit` property or has `lit = true`.

- **Tag default:** `#minecraft:fire`, `#minecraft:campfires`, `minecraft:lava` and `minecraft:magma_block`. Packs may extend it.
- **Evaluated** in `getStateForPlacement` (reading the state below), in `updateShape` when the direction is `DOWN` (its arguments carry the neighbour's state, so no level read is needed), and by every **refresh**.
- **No warm-up timer:** no decision rides on one ([progression, The first hour](progression.md#the-first-hour): no passive waiting).

**The refresh queue.** Placement and neighbour callbacks can't see a change to the tag itself, nor a saved `boiling` written under other tags, so a per-level queue re-derives it:
- **The loaded set:** a per-level in-memory set of crucible positions, kept by the block entity's `onLoad` and `setRemoved`.
- **Queued by:** the block entity's `onLoad` (chunk loads, `/setblock`, `/clone` and structures placed into loaded chunks); NeoForge's `TagsUpdatedEvent.ServerDataLoad`, subscribed directly (every position in the loaded set; the older `getUpdateCause()` and its enum are deprecated for removal); a new tuning record (every position in the loaded set; see "Sync and state").
- **Processed** at the end of each server tick, at most **64 positions per level per tick** (`MAX_REFRESHES_PER_TICK`), in ascending `BlockPos.asLong()`. A position whose chunk `ServerChunkCache#getChunkNow` no longer returns is dropped (it is queued again when it loads), and so is one that is no longer a crucible. **The queue never loads a chunk.**
- **A refresh** recomputes heat from the state below, sets `boiling = water ∧ heated` with `UPDATE_CLIENTS` if that differs, and marks the block entity for sync, so trackers receive the capacity in force even for an idle pool.
- **A queued crucible processes nothing.** Its step does nothing until its refresh has run.

### Water

The **`water`** property is boolean, and **water is never consumed** by dissolving or crafting.

| Held item | Crucible (first match) | Effect | Returns |
|---|---|---|---|
| water or empty bucket | dormant (see "Sync and state") | refused: nothing changes; the action bar shows `message.vftv.crucible.dormant` | `CONSUME` |
| water or empty bucket | transaction-busy, or an operation open (see "Essentia out" and "Emptying by bucket") | refused: nothing changes, so an open extraction's rollback can never restore a pool into a dry crucible | `CONSUME` |
| water bucket | dry | **fill:** `water = true`, `boiling` recomputed; the bucket becomes an empty one through `ItemUtils.createFilledResult` (creative play keeps its bucket, and a full inventory drops the result, as vanilla's cauldron does); `crucible.fill` plays; game event `FLUID_PLACE` | `SUCCESS` |
| water bucket | has water | nothing changes: no water is placed anywhere and the bucket is kept | `CONSUME` |
| empty bucket | has water | **empty** (see "Spilling, Emptying by bucket") | `SUCCESS`, or `FAIL` with nothing changed |
| empty bucket | dry | nothing changes | `CONSUME` |
| anything else | any | `TRY_WITH_EMPTY_HAND`; `useWithoutItem` returns `PASS`, so the held item's own use runs | |

- Both bucket paths run inside vanilla's `useItemOn`, after spawn protection, `mayInteract` and `PlayerInteractEvent.RightClickBlock`: a claim mod that cancels the event, or denies the block's use, wins.
- **Sneaking:** vanilla skips the block's use when the player is sneaking with an item in either hand, unless the held item's `doesSneakBypassUse` is true or a `RightClickBlock` listener allows the block's use (`ServerPlayerGameMode#useItemOn`). Buckets don't bypass, so a sneaking bucket click does the bucket's own thing, and a sneaking wand click reaches [Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22)'s block-targeted path ([wands, Hand-tapping](wands.md#hand-tapping), interaction precedence).
- **Invariant:** on every survival path, **a non-empty pool implies `water`**: only emptying sets `water = false`, and emptying spills the pool. A command that writes `water=false` directly leaves the pool as it is; it reads as `dry` and spills on removal.
- **Options considered:** water consumed per craft (as TC4 did); a fluid capability; a sneak-click drain. **Recommend** buckets only: per-craft water is a chore with no decision ([progression, pain point 11](progression.md#answers-to-the-top-community-pain-points)); with nothing consuming water a fluid capability is YAGNI; sneak-use with a wand is Foci's path; and the empty bucket is the vanilla verb for "take the water out".

### Boiling and the step

**`boiling = water ∧ heated`**, a block-state property recomputed in the paths under "Heat" and "Water".

- **Ticker:** `getTicker` returns a server ticker **only for `boiling = true` states**, and vanilla re-evaluates the ticker on every state change of the same block. **There is no idle ticker:** a cold or dry crucible costs nothing per tick, and an item touching one costs a constant-time callback (see "Admission").
- **Phase:** a boiling crucible runs a **step** on the ticks where `level.getGameTime() mod step.interval = phase(pos)`, with

  ```
  phase(pos) = Long.remainderUnsigned(mix64(pos.asLong()), step.interval)
  ```

  where `mix64` is the SplitMix64 finaliser pinned in the [research spec](research.md#generating-a-note) and `asLong` is vanilla's `BlockPos` packing (26 bits of x shifted by 38, 26 bits of z shifted by 12, 12 bits of y). The phase is deterministic and spreads neighbouring crucibles across ticks.
- **A step does nothing** while the block entity is dormant, queued for refresh, inside an operation (see "Emptying by bucket") or **transaction-busy** (see "Essentia out": an extraction's transaction is still open), and **it never re-enters itself**: a step started while another is running on the same block entity (a listener driving the crucible from inside a step) returns at once.

**Vectors** (computed by a reference implementation and cross-checked against the JDK):

| Position | `asLong` | `mix64` | Phase at 5 | Phase at 40 |
|---|---|---|---|---|
| (0, 64, 0) | `0x0000000000000040` | `0x8AA449CE2D0CA1D3` | 0 | 35 |
| (1, 64, 0) | `0x0000004000000040` | `0xCCA546706B2F3BE2` | 0 | 10 |
| (0, 64, 1) | `0x0000000000001040` | `0x72C7F386FC53A75C` | 4 | 4 |
| (−37, 70, 512) | `0xFFFFF6C000200046` | `0x3522C0D35D1C6561` | 4 | 9 |
| (29999999, −64, −29999999) | `0x7270DFE363C81FC0` | `0xFD59B0EF405AF3EB` | 4 | 19 |

### Admission

**Item entities collect themselves.** Item entities receive block effects every tick, resting ones included, and in a server tick entities tick before block entities. So the step needs no world query: the entities touching the crucible offer themselves, and the step drains what was offered.

- **Contacts.** The crucible's `entityInside` (server only) runs for every entity overlapping its entity-inside shape. It acts only when all of these hold: the entity is an alive `ItemEntity` without the refusal mark; the state is `boiling`; the block entity is readable, not queued for refresh, not in an operation, not transaction-busy and not mid-step; and the game time is this crucible's phase tick. Then it **offers** the entity's id to the block entity's candidate set. Otherwise it returns after those checks.
- **The candidate set** holds at most **16** ids (`MAX_ENTITIES_PER_STEP`) and the game time they were offered in. An offer in a new game time first empties it. Then: an id already held is ignored; with fewer than 16 held, the id is added; otherwise, if the id is below the largest held, it replaces that one. The set therefore holds the **lowest 16 distinct ids offered this tick**, at constant work per contact however many entities are inside.
- **Draining,** at the start of the step: if the set's game time isn't the current one, it is treated as empty. Otherwise it is copied, sorted ascending and emptied. Each id is resolved through `level.getEntity(id)` and **rechecked live** before its action: an alive `ItemEntity`, still unmarked, whose bounding box still intersects the interior box in world space; and the block entity is still the one at its position, not removed, readable, with the state still `boiling`. A failed recheck skips that id.
- **Each entity gets exactly one action per step:** one craft of one item, one dissolve of as many items as fit, or one refusal (see "Processing an entity").
- **Entities past the 16 wait** for a later step. A flood of hundreds of resting entities costs one constant-time offer each per phase tick, and at most 16 actions.

**Vector** (reference implementation, cross-checked against the JDK): offers in the order 40, 7, 7, 93, 12, 5, 61, 18, 33, 2, 77, 21, 9, 50, 14, 88, 3, 70, 1, 40, 64, 6 are drained as **1, 2, 3, 5, 6, 7, 9, 12, 14, 18, 21, 33, 40, 50, 61, 64**.

**Options considered:** a periodic box query over the interior (the hopper pattern), whose list and sort grow with the entities inside; per-tick processing. **Recommend** contact admission: it is bounded per contact, it still catches items that were resting inside when the crucible came to the boil, and its order is pinned.

## Processing an entity

### The decision

For one drained entity, first match:

| # | When | Outcome |
|---|---|---|
| 1 | its stack is **protected** (see "Protection") | refused `PROTECTED` |
| 2 | it carries the **dissolve mark** (a sneak-drop, below) | **dissolved** (see "Dissolving"); recipes are not consulted |
| 3 | "Selection" finds an affordable candidate for its thrower | **crafts one** (see "Committing a craft") |
| 4 | "Selection" finds candidates, none affordable | refused `SHORT` |
| 5 | otherwise: no thrower, nothing the thrower has learned matches, or not a catalyst at all | **dissolved** |

Protection runs **before both** crafting and dissolving, so a container or book that some recipe's catalyst matches is never consumed either way.

**Sneak-drop.** A player who drops an item while sneaking means "melt this". NeoForge's `ItemTossEvent`, posted on the server when a player tosses an item, sets the entity attachment **`vftv:crucible_dissolve`** (a boolean, serialised with the entity, never synced) iff `player.isShiftKeyDown()` at that moment. It is never inferred later from posture at landing. It bypasses recipe selection only: **protection and capacity still apply.** Without it, an unaffordable learned catalyst is refused, never melted.

**Vectors** (reference implementation, cross-checked against the JDK). Recipes, by id: `vftv:a_brass` (catalyst copper ingot, research `vftv:x`, cost Praecantatio 4, Sonus 2), `vftv:b_bell` (copper ingot, `vftv:y`, Sonus 10), `vftv:c_glass` (sand, `vftv:x`, Ignis 3). The item is a copper ingot.

| Thrower has learned | Pool | Other | Outcome |
|---|---|---|---|
| `x`, `y` | Praecantatio 4, Sonus 12 | | crafts `vftv:a_brass` |
| `x`, `y` | Sonus 12 | | crafts `vftv:b_bell` |
| `x`, `y` | Sonus 1 | | `SHORT` on `vftv:a_brass`: Praecantatio 4, Sonus 1 |
| `y` | Sonus 9 | | `SHORT` on `vftv:b_bell`: Sonus 1 |
| nothing | Sonus 99 | | dissolved |
| (no thrower) | Sonus 99 | | dissolved |
| `x`, `y` | Praecantatio 9, Sonus 99 | dissolve mark | dissolved |
| `x` | empty | protected stack, dissolve mark | `PROTECTED` |

### Protection

An item is **protected** iff it is in the item tag **`vftv:crucible_refuses`** (default: `vftv:wand`, `vftv:bell_jar`, `vftv:commonplace_book` and `vftv:essentia_jar`, empty or filled), or its stack carries any of: a non-empty `minecraft:container`; a non-empty `minecraft:bundle_contents`; a non-empty `minecraft:writable_book_content`; any `minecraft:written_book_content`. The aspect lookup ignores components ([aspects, Coverage and supply](aspects.md#coverage-and-supply)), so dissolving these would silently destroy contents, a captured node, a jar's essentia ([essentia spec, The jar as an item](essentia.md#the-jar-as-an-item)) or a player's writing.

### Dissolving

- **Lookup:** exactly the aspects spec's item lookup: the item's `vftv:aspects` entry, else its `vftv:aspect_defaults` tag entry ([aspects, Data maps](aspects.md#data-maps)). `{}` and no entry are the same here: refused `NOTHING`.
- **Yield: exactly 1×.** There is no multiplier: pack makers already get whole-value replacement in the maps themselves, and 1× makes non-amplification checkable by totals.
- **Remainder-bearing items** (a milk bucket, a potion) **dissolve whole.** Returning the bucket or bottle would amplify, because its value is already inside the item's.

**The fit rule,** in `long` arithmetic (the aspects spec bounds how many aspects an entry holds, not how large each amount is):
1. `v` = the item's values; `t` = Σ`v`.
2. `room` = max(0, `pool.capacity` − the pool's total). The total includes missing ids (see "The pool").
3. If the union of the pool's ids (missing ones included) and `v`'s ids holds more than **64**, `k` = 0. Otherwise `k` = min(count, ⌊`room` / `t`⌋).
4. **`k` > 0:** the pool gains `k` × `v` (revision + 1), the entity's stack shrinks by `k` (discarded at 0), and `CrucibleEvent(DISSOLVED)` is posted. A remainder is refused `FULL` on the same entity.
5. **`k` = 0:** the whole entity is refused `FULL`.

**Vectors** (reference implementation, cross-checked against the JDK):

| Capacity | Pool | Item values | Count | `k` | Then |
|---|---|---|---|---|---|
| 200 | Terra 190 | Terra 2 | 64 | 5 | 59 refused `FULL` |
| 200 | empty | Terra 2 | 64 | 64 | none left |
| 200 | Aer 150 | Ignis 3, Terra 60 | 10 | 0 | all 10 refused `FULL` |
| 200 | Aer 250 (capacity since lowered) | Aer 1 | 3 | 0 | `FULL` |
| 200 | 63 distinct ids of 1 each | two new ids, 1 each | 1 | 0 | `FULL` (65 ids) |
| 200 | the same 63 | one new id and one held id, 1 each | 1 | 1 | none left (64 ids) |
| 10,000 | empty | Aer 2,147,483,647, Terra 2,147,483,647 | 64 | 0 | `FULL` (`t` overflows an `int`) |
| 10,000 | empty | Aer 3 | 64 | 64 | none left |
| 200 | empty | `{}` | 5 | 0 | `NOTHING` |

### Refusal and ejection

**One enum, `CrucibleRefusal`, in `core.crucible`:**

| Refusal | Raised by | Lang key | Args |
|---|---|---|---|
| `PROTECTED` | "The decision", row 1 | `message.vftv.crucible.protected` | 0 |
| `NOTHING` | "Dissolving": no aspects | `message.vftv.crucible.nothing` | 0 |
| `FULL` | the fit rule | `message.vftv.crucible.full` | 0 |
| `SHORT` | "The decision", row 4 | `message.vftv.crucible.short` | 1: the shortfall |
| `ERROR` | "Committing a craft", steps 3 and 4 | `message.vftv.crucible.error` | 0 |

**A refused entity, and every crafted result:**
- gets the entity attachment **`vftv:crucible_refused`** (a boolean, serialised with the entity, never synced). No crucible processes it **while that entity survives**;
- gets vanilla's default pickup delay;
- gets the velocity `(0.15 · dx/ℓ, 0.3, 0.15 · dz/ℓ)`, where (`dx`, `dz`) runs from the crucible's centre column (x + 0.5, z + 0.5) to the resolved thrower's position and ℓ is its length; with no thrower, or when ℓ² < 10⁻⁸, the velocity is `(0, 0.3, 0)`. A refused entity is not moved: it pops from where it lies. Without a thrower it may fall back inside, where its mark keeps it inert.

**Vectors** (reference implementation, cross-checked against the JDK; crucible at x = 10, z = −5):

| Thrower at (x, z) | Velocity |
|---|---|
| (13.25, −1.75) | (0.106066, 0.300000, 0.106066) |
| (10.5, −4.5), coincident | (0, 0.3, 0) |
| (10.50005, −4.5), ℓ² = 2.5 × 10⁻⁹ | (0, 0.3, 0) |
| (−100.0, 3.0) | (−0.149656, 0.300000, 0.010158) |
| no thrower | (0, 0.3, 0) |

**Merging is vanilla's** (`ItemEntity#tryToMerge`): the surviving entity keeps its own thrower and attachments, and the absorbed entity's are lost. So:
- a refused entity that absorbs a fresh stack makes the whole stack inert while it survives;
- a refused entity absorbed into an unmarked one loses its mark, and the result may be processed again (a crafted result that falls back in can be dissolved);
- the dissolve mark follows the survivor in the same way.

Which entity survives an equal-count merge is vanilla's choice; a GameTest records it, so a vanilla change is noticed. The promise is no stronger than "while the marked entity survives": a mark on the `ItemStack` would leak into inventories through hoppers, and a merge mixin isn't justified.

**Feedback:** the online thrower gets **at most one action-bar line per step**: their first refusal in drain order. Chat is never used ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)). The `SHORT` argument is the shortfall, cost − pool for each short aspect, as "*Aspect* *n*" in ascending aspect id, joined by ", ". An aspect the thrower hasn't discovered is named `crucible.vftv.unheard`, with its exact *n*. Numbers are plain integers in ASCII digits ([arcane crafting, The screen](arcane.md#the-screen), number formats).

## Crucible recipes

### The recipe type

**One type, `vftv:crucible`** (recipe type and serializer share the id). Its body is the body of every crucible recipe in `content/`, this spec's and other specs'.

| Field | Required | Range | Meaning |
|---|---|---|---|
| `type` | yes | `vftv:crucible` | |
| `research` | yes | an id in the `vftv:research` registry | the gate |
| `catalyst` | yes | one vanilla `Ingredient` | the item thrown in; NeoForge's custom ingredients work unchanged |
| `aspects` | yes | 1–8 entries; each key any aspect in the `vftv:aspect` registry (compounds and Vacuos included); each value 1–1000 | the cost, drawn from the pool |
| `result` | yes | a vanilla `ItemStackTemplate` (`id`, `count`, optional `components`) | the output |

```jsonc
{"type": "vftv:crucible",
 "research": "vftv:resonant_brass",
 "catalyst": "minecraft:copper_ingot",
 "aspects": {"vftv:metallum": 6, "vftv:sonus": 4},
 "result": {"id": "vftv:resonant_brass_ingot", "count": 1}}
```

- **The result** is static and is **created and checked** at decode, as in the [arcane recipe type](arcane.md#the-recipe-type): the created stack must be non-empty and within its own component-resolved maximum stack size.
- **Validation at decode, registry-aware,** through the same layered registry ops: `research` must name an existing entry and every `aspects` key an existing aspect. A recipe that fails any rule fails to decode, and vanilla skips it with a logged error naming it.
- **Unpayable costs:** a cost whose total exceeds `pool.capacity` can never be paid. Such a recipe is logged once at load and after each tuning `/reload`, naming the recipe, the total and the capacity; a content lint forbids it for built-in recipes.
- **Matching** evaluates the catalyst `Ingredient` on a **count-one copy** of the entity's stack, so a count-sensitive custom ingredient sees exactly the one item a craft consumes.
- **Unknown fields** are ignored at runtime (vanilla `MapCodec`) and rejected in `content/`.
- **Stream codec:** bounded, because the recipe sync encodes through it: `research` and the result id ≤ 256 characters each, `aspects` ≤ 8 entries of 1–1000; the catalyst and result through vanilla's own stream codecs. Out of bounds is rejected at decode.

**Not vanilla crafting:** the type is not `RecipeType.CRAFTING`; `isSpecial()` is true, so the recipe book never adds it; `showNotification()` is false; `placementInfo()` is `NOT_PLACEABLE`. `display()` returns one registered `RecipeDisplay` type, **`vftv:crucible`**: the catalyst's `SlotDisplay`, the result's, and the crucible item as the crafting station. It carries no cost: costs come from the preview.

### Selection

For an entity that reached row 3 of "The decision":
1. **The thrower:** `ItemEntity#getOwner()` must resolve to a `ServerPlayer` that is alive and in this level. Vanilla records a thrower only on a player's own drops; droppers, death drops, broken containers and other mods' entities have none unless they set one (see the [Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23) contract).
2. **Candidates:** the current snapshot's recipes whose catalyst matches the count-one copy and whose `research` the thrower **has learned**, in ascending id (`String.compareTo` on `ns:path`). With no thrower there are none.
3. **Affordable** means every cost aspect's pool amount ≥ its cost. The **lowest-id affordable** candidate crafts one item.
4. **Candidates, none affordable:** refused `SHORT`, naming the **lowest-id** candidate's shortfall. A learned catalyst is never silently dissolved.
5. **No candidates:** dissolved. An unlearned player's catalyst is an ordinary item, and dissolving it is matter processing, never a gated recipe running.

- **Options considered:** "an unaffordable catalyst melts" (as TC4 did); matching against whoever placed the crucible, or whoever last touched it. **Why this:** lost catalysts and recipe collisions were the community's complaint, and an owner or last-interactor model changes whose research authorises the craft, letting an unlearned player use someone else's ([research, Contracts](research.md#contracts-on-sibling-specs): per-player gates).
- **The cost of the rule:** dissolving a catalyst you know takes a sneak-drop. A dropper always dissolves.

### Recipe sync and reload

The **`CrucibleRecipeSnapshot`** follows the [arcane recipe snapshot](arcane.md#recipe-sync-and-reload) exactly:
- **Sync:** `neoforge` calls `OnDatapackSyncEvent#sendRecipes(vftv:crucible)` on join and `/reload`. The client builds its snapshot from each `RecipesReceivedEvent` and clears it at logout.
- **Built** in `ServerStartingEvent`, then rebuilt by the `OnDatapackSyncEvent` handler, before sending, whenever the server's recipe manager isn't the instance it was built from. Replaced whole; nothing holds a recipe object across a replacement.
- **The cap, `MAX_CRUCIBLE_RECIPES` = 4,096** (`CrucibleLimits` in `api`), applied as a set: over it at server start, **startup fails** naming the count and the cap; over it on `/reload`, the new snapshot is **empty** and an error is logged. The client applies the same rule.
- **Index:** every recipe sorted by id; a map from item to the recipes whose catalyst is simple (`Ingredient#isSimple`) and lists that item, each list in id order; and one id-ordered list of recipes with non-simple catalysts. A selection merges the stack's item list with the non-simple list in id order and tests each on the count-one copy.

### Committing a craft

On the server thread, inside a step. Spawning an entity runs other mods' join listeners before it returns, and a listener may cancel the spawn or change the world, so **the result is provisional until it has been revalidated**, and nothing is debited before then.

1. **Snapshot** what the craft expects: the block entity instance and its block state; the pool's `revision` (a counter bumped by every pool change); the catalyst entity and a copy of its stack; the recipe snapshot instance and the recipe id; the thrower, and that they have learned the recipe's `research`.
2. **Create** the result stack from the template, and an `ItemEntity` holding it at `pos + (0.5, 1.0625, 0.5)`, marked refused, with the default pickup delay and the ejection velocity toward the thrower.
3. **Spawn** it with `addFreshEntity`. **If it returns `false`** (a cancelled join) **or throws:** our result entity is discarded if it was added; nothing is debited; the catalyst is refused `ERROR`; one log line names the recipe.
4. **Revalidate** everything from step 1 against the live world: the same block entity at the position, not removed, readable, not in an operation and not transaction-busy; the same block state, still `boiling`; the same pool revision; the catalyst entity alive and unmarked, its stack still matching the copy and the recipe; the same snapshot instance; the thrower still resolving and still having the research; **our result entity alive, holding the stack it was given**. Any failure: our result entity is discarded if it is still alive, nothing is debited, the catalyst is refused `ERROR`, and one log line names the recipe and the failed check. **Whatever listeners changed themselves is left as they made it.**
5. **Commit:** debit the cost (revision + 1); shrink the catalyst by one, discarding it at zero; mark the block entity for the coalesced sync; record the step's sound (see "Sound and particles").
6. **Notifications,** after the commit, each exactly once, **each caught separately so the rest still run**, in this order:
   1. `CrucibleEvent(CRAFTED)`;
   2. `onCraftedBy(player, count)` on the result entity's stack (statistics);
   3. NeoForge's `PlayerEvent.ItemCraftedEvent`, through the platform seam calling `EventHooks.firePlayerCraftingEvent(Player, ItemStack, Container)` with **a copy** of the result and **a defensive one-slot `SimpleContainer`** holding a count-one copy of the catalyst;
   4. `CriteriaTriggers.RECIPE_CRAFTED.trigger(player, recipe key, [the catalyst copy])`.

   A notification that throws is logged with the recipe id. **A committed craft is never reported, refused or refunded as `ERROR`.**

- **Removal during the spawn:** if a listener removes the crucible in step 3, the removal hook spills the still-intact pool once (see "Removal"), and step 4 fails on the removed block entity: nothing is debited and our result is discarded. Nothing is doubled.
- **No flux and no vis.** The warp tax is on vis ([progression, Warp](progression.md#warp); [wands, The quote](wands.md#the-quote)), and the crucible spends none. A craft that should spill flux would need [Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26) or [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25) to propose it.

## Combining essentia, and Vacuos

**No essentia ever combines in this system:** there is no compound synthesis in the crucible, anywhere else in this spec, or as a recipe type.

- So **Aer and Perditio essentia never combine into Vacuos.** The world is truthful from the start ([aspects, Vacuos](aspects.md#vacuos)), and with no combining at all there is nothing for the book to misstate.
- Compounds come from matter, and the aspects spec guarantees at least 3 accessible sources for every aspect ([aspects, Coverage and supply](aspects.md#coverage-and-supply)).
- **Scope:** this is a statement about this system. Any other spec that wants a combiner must propose it explicitly, treating Vacuos as a true primal (see the contracts).
- **Puzzle synthesis is a separate economy** of aspect points ([research, Paying for placements](research.md#paying-for-placements)) and implies nothing about essentia.

## The pool

**Shape:** a map from aspect id to a positive integer amount, **at most 64 entries**, with a total cap **`pool.capacity`** (200, tuning). There is no per-aspect cap.

**Nothing happens over time.** There is **no decay**: no compound splits, no primal vanishes, and **Vacuos never breaks down into Aer or Perditio**, because nothing breaks down at all. A cold pool keeps its contents.
- **Options considered:** decay as in TC4 (compounds split, primals vanish into flux); decay only while cold.
- **Recommend** no decay. **Why:** the TC4 alchemy research's own friction notes say the crucible punished slowness and drove players to workarounds; the progression spec forbids passive-waiting pressure; the Hush must be caused by the player ([progression, TC4 systems](progression.md#tc4-systems-kept-redesigned-cut)), and a timer is not a choice; and a pool that never decays needs no per-tick work.
- **The pressure that remains is a decision.** Leftovers from dissolving fill the cap, and the player either extracts them (see "Essentia out"), which keeps the material but needs somewhere to put it, or empties them, which is the priced alternative (see "Spilling").

**Missing aspect ids.** An entry of a valid record whose aspect the registry doesn't hold (a pack removed it) is **kept**: written back unchanged, counted toward the total and the 64-id union, and spilled with the rest. It is never matched, extracted or used for the tint, and it is shown with the missing sprite and its exact amount. Restoring the pack restores it.

**A lowered capacity** after a tuning `/reload` never removes essentia: the crucible accepts nothing until its total falls below the new cap.

**A dormant record is not a pool.** A record that can't be read at all (see "Sync and state") is never read, changed, extracted or spilled.

## Spilling

| Cause | When | Player |
|---|---|---|
| **`EMPTIED`** | emptying by bucket | the player |
| **`REMOVED`** | the block entity's removal hook: mining, explosions, replacement | none |

- **No spill from:** dissolving, refusing, crafting, extraction or overfilling. Overfilling is impossible, by the fit rule.
- **How much:** exactly the pool's contents, per aspect, missing ids included: at most 10,000 in total, the format bound.
- **Feedback:** a non-empty spill plays `crucible.spill` and sends 8 vanilla `minecraft:smoke` particles above the rim.

### Emptying by bucket

At most **one operation** is open on a crucible at a time. While it is open, the crucible runs no step, its essentia handler reads empty and extracts nothing, the removal hook leaves the pool to the operation, and no sync is published. On the server thread, in order:
1. **Check:** the crucible has water, is readable, has no operation open and is not transaction-busy. Otherwise nothing changes (see "Water").
2. **Open** the operation, and copy the pool into an immutable `spilled`.
3. **Stage the clear:** empty the pool (revision + 1), keeping `spilled` for rollback.
4. `setBlock(pos, the state with water = false and boiling = false, UPDATE_ALL)`. Neighbour callbacks run inside it and may remove the crucible. **If it returns `false`:** restore the pool from `spilled`, close the operation, and return `FAIL`. Nothing changed.
5. **Resolve removal once.** Whether or not the block entity was removed during step 4, the pool left with this operation, and a removal hook that ran saw the operation open and spilled nothing. Exactly one spill follows.
6. **Close** the operation. Swap the bucket through `ItemUtils.createFilledResult(held, player, a water bucket)`; post the game event `FLUID_PICKUP`; mark the block entity for sync if it survives; play `crucible.empty`. If `spilled` is non-empty, play `crucible.spill` and post `CrucibleEvent(SPILLED)` with cause `EMPTIED`, the player and `spilled`. Return `SUCCESS`.

### Removal

**`BlockEntity#preRemoveSideEffects`** (called when the block is replaced, not when it unloads):
- **an operation is open:** nothing; the operation owns the pool;
- **transaction-busy:** the hook records **one pending removal** and does nothing else yet. When the open root transaction closes, **on commit or on abort**, the pending removal copies the pool as it then stands, clears it, plays the spill feedback and posts `CrucibleEvent(SPILLED)` with cause `REMOVED`, exactly once. The journal settles first, so the spill is the pool the transaction actually left;
- **dormant:** nothing is spilled, and a warning names the position. The unreadable record is lost with the block, which drops as a plain crucible; nothing tries to parse or spill contents it can't read;
- **otherwise,** if the pool is non-empty: copy it, clear it, play the spill feedback, and post `CrucibleEvent(SPILLED)` with cause `REMOVED`.

**Qualified:** the hook runs only when the block update doesn't suppress block-entity side effects (flag 256). A removal through such a path (some commands and structure placements) loses the pool without an event.

### The boundary with Flux and the Hush

- This spec posts **`CrucibleEvent(SPILLED)`** after the pool is cleared. [Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26) converts it to flux through its pure function **`essentiaFlux(spilled, cause)`** and applies that amount **once**, on that event.
- **Until #26 lands,** `essentiaFlux` is the **typed zero**: it returns 0, never an absent value, and a spill makes sound and particles only.
- **Bounds:** `essentiaFlux` returns an integer 0–1,000,000 (`MAX_SPILL_FLUX`). Its input is a valid format-1 pool, whose total is at most 10,000; a dormant record is never an input.
- **Explicit, synced inputs:** it is a pure function of the spilled map, the cause and #26's synced rule record, nothing else. The HUD previews `essentiaFlux(pool, EMPTIED)` on the client from the same synced inputs, so the preview equals the application, and the cost of emptying is never hidden ([lore, Listening and warp](lore.md#listening-and-warp): never a hidden tax).

## Essentia out

The crucible exposes the architecture's **`BlockCapability<ResourceHandler<EssentiaResource>, Direction>`** ([architecture, Essentia and capabilities](architecture.md#essentia-and-capabilities)) on **every face but `UP`, and for the null side**, **extract-only**. The `Resource`, handler and journal adapters live in `neoforge.crucible`; the pool's operations (credit, debit, the fit rule, snapshot and restore) are loader-free, in `core.crucible.model`.

- **Slots** are stable for the world: slot *i* is the *i*-th aspect of the `vftv:aspect` registry in ascending id. The registry is fixed when a world opens ([ADR 0003](../adr/0003-aspects-as-datapack-registry.md)), so the slot count never changes. An aspect → slot lookup is built at world open. Missing ids have no slot.
- **Resources:** an `EssentiaResource` is either empty or one aspect. Two are equal iff both are empty or they name the same aspect key; the crucible never compares holders by identity.
- **Arguments first.** Both `insert` overloads and both `extract` overloads begin with `TransferPreconditions.checkNonEmptyNonNegative(resource, amount)` in the platform adapter, so an empty resource or a negative amount throws `IllegalArgumentException`, as the `ResourceHandler` contract documents. After that, the rules below never throw.
- **Every handler method:**

| Method | Rule |
|---|---|
| `size()` | the number of aspects in the registry |
| `getResource(int)` | slot *i*'s aspect; the empty resource for an index outside 0 … size − 1 |
| `getAmountAsLong(int)` | the pool's amount of slot *i*'s aspect, 0 when absent; 0 for an invalid index; 0 while unavailable |
| `getCapacityAsLong(int, EssentiaResource)` | 0: nothing can be inserted |
| `getAmountAsInt(int)`, `getCapacityAsInt(int, EssentiaResource)` | the interface's defaults over the `long` forms, kept as they are (`@ApiStatus.NonExtendable`) |
| `isValid(int, EssentiaResource)` | false |
| `insert(int, resource, amount, transaction)`, `insert(resource, amount, transaction)` | after the preconditions: 0 |
| `extract(int, resource, amount, transaction)` | after the preconditions: 0 for `amount` = 0, a resource that isn't slot *i*'s, an invalid index, or an unavailable handler. Otherwise `n` = min(`amount`, the pool's amount); if `n` > 0 the journal snapshots the pool for this transaction depth and the pool is debited by `n`. Returns `n`. |
| `extract(resource, amount, transaction)` | after the preconditions, **overridden** to O(1): the slot lookup, then the indexed rule. The interface's default, which walks every slot, is never used. |

- **Lenient indices, strict arguments:** an invalid index is a valid question with the answer "nothing" (the interface says such indices *usually* throw, not that they must); an empty resource or a negative amount is a caller's error and throws.
- **Unavailable** means the block entity is removed or isn't the one at its position, the record is dormant, an operation is open, or a step is running. A capability cache that outlived a block replacement therefore reads an empty, inert handler, never the old pool. The crucible calls `level.invalidateCapabilities(pos)` whenever it becomes dormant or readable again. **A cold, dry or empty crucible stays extractable.**
- **Transactions:** a `SnapshotJournal` over the pool. A snapshot (≤ 64 entries) is taken **once per transaction depth** the handler touches. A child's commit stands only as long as its parent commits; a root abort restores the pool exactly, committed children included.
- **Transaction-busy:** from the journal's first snapshot until its root transaction closes, on commit or on abort, the pool is **transaction-busy**. The platform adapter reports this to `core` through the loader-free essentia seam ([architecture, Seams](architecture.md#seams)), so `core` never sees a transaction type. While busy, admission offers nothing, steps do nothing (their entities wait), both bucket paths refuse, and a craft's revalidation fails. So **an abort can only ever restore the pool it snapshotted**: no other commit can have changed it meanwhile. A removal while busy is deferred to the transaction's close (see "Removal"); the pool object stays with that pending removal so the journal can still revert into it.
- **On the root commit:** the block entity is marked changed and dirty for the coalesced sync, unless it has been removed. This is publication only, separate from correctness: the debit happened in `extract`, and busy-state release and any pending removal run on close whichever way it ends. No event, no flux and no sound.
- **Suction:** the crucible offers **0** for every aspect: it never pulls.

**Options considered:** insert-capable (piped dosing); no capability. **Recommend** extract-only: it makes the crucible the Stage 2 source for jars and the flux-free way to clear leftovers. Piped dosing would need its own capacity and suction rule, which is [Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)' to propose. The [essentia spec](essentia.md#contracts-on-sibling-specs) declines it: lines and Alembics only ever extract from a crucible.

## Resonant Brass

**`vftv:resonant_brass_ingot`** (stacking to 64) is attuned copper ([lore, Vocabulary policy](lore.md#vocabulary-policy)), made by the crucible recipe **`vftv:resonant_brass_ingot`**: catalyst `minecraft:copper_ingot`, research `vftv:resonant_brass`, result count 1. **This item id is frozen here.**

- **Content rules for its cost:** 1–3 aspects, totalling at most ¼ of the default `pool.capacity` (50 today; the lint reads the tuning default).
- **Reachable before the first Brass craft.** The content ticket authors a **dose plan** (see "Authored content, The first Brass"): vanilla items obtainable in Stages 1–2 without Resonant Brass, which, dissolved in order into an empty pool at the default capacity, each fit whole and leave the pool holding the full cost. A build-phase test replays it with the real fit rule and the generated values.
- **What it is for:** the Stage 2 instrument metal. It feeds the wands spec's frozen recipes `vftv:chime_rod` and `vftv:resonant_brass_cap` ([wands, Recipes](wands.md#recipes)) and later specs' recipes. Its tags `c:ingots` and `c:ingots/resonant_brass` are build-phase data.
- **No nugget or storage block in v1:** later content can add them.
- **Options considered:** a Worktable recipe. **Why the crucible:** the progression spec pairs Resonant Brass with the crucible, and it makes the crucible the Stage 2 gateway.

## Reading the crucible

### The HUD

**HUD layer `vftv:crucible`.** It shows while the client's block hit is a crucible within the player's `block_interaction_range`, no screen or debug overlay is open, F1 is off, the player isn't a spectator, and the aspects spec's strike card isn't showing.

- **Layer order:** the wands spec's `vftv:vis` layer, then `vftv:crucible`, then the strike card. Both of the latter sit at the crosshair, so **the strike card wins**: while its hold lasts ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)) the crucible HUD isn't drawn at all.

| Part | What it shows |
|---|---|
| Frame | `vftv:hud/vis_frame`, the wands spec's nine-slice (16×16, border 4), with 4-px padding |
| Header | first match: dormant → `hud.vftv.crucible.dormant` (0 args); dry → `hud.vftv.crucible.dry`; cold → `hud.vftv.crucible.cold`; else `hud.vftv.crucible.boiling` (each 2 args: the total and the capacity in force) |
| Cells | one per pool entry, in ascending id: a 16×16 icon and the exact amount. A discovered aspect shows its icon ([aspects, Icons](aspects.md#icons)); one the viewer hasn't discovered shows the **unheard** icon; a missing id shows `vftv:hud/crucible_missing`. None when dormant. |
| Spill line | `hud.vftv.crucible.spill` (1 arg: `essentiaFlux(pool, EMPTIED)`), iff the crucible has water and a non-empty, readable pool |

**Geometry,** in GUI-scaled pixels on a scaled screen of `W` × `H`:
- the header and spill lines are 9 px tall, clipped with "…" to the widest inner width, `maxInner = W − 12` (a 2-px screen margin and the 4-px padding on each side);
- a **cell** is 16 px tall: the icon at (0, 0), the amount at (18, 4). Its width is `18 + T`, where `T` is the widest amount string in the pool by the font's own metrics, measured once per layout. The column pitch is cell + 4 and the row pitch 18;
- **no entries** (an empty pool, or a dormant record): columns = rows = 0 and no grid is drawn;
- otherwise `maxFit = max(1, ⌊(maxInner + 4) / pitch⌋)`. The **columns** `c` are the smallest count from `min(n, 4, maxFit)` up to `min(n, maxFit)` whose frame fits `H − 4` tall, else `min(n, maxFit)`; rows are ⌈`n` / `c`⌉;
- frame height = 17 + 18 × rows + 11 if there is a spill line; frame width = 8 + the widest of the header, the spill line and the grid (`c` × cell + (`c` − 1) × 4, or 0 with no columns);
- **fallback:** if the frame is still wider than `W − 4` or taller than `H − 4`, it is drawn at the pose scale `s = min((W − 4) / width, (H − 4) / height)`;
- **position:** the drawn size is ⌈width × `s`⌉ × ⌈height × `s`⌉ (rounded **up**, so a fractional edge never eats the margin). The frame is anchored at (⌊`W`/2⌋ + 8, ⌊`H`/2⌋ + 8), then clamped: `x = max(2, min(anchor x, W − drawn width − 2))`, and likewise `y`, so it stays at least 2 px inside the screen.

**Every pool fits.** The total is at most 10,000, so only a one-entry pool can hold a five-digit amount; with 2–64 entries every amount has at most 4 digits. With the vanilla font (6-px digits) the largest pool fits **unscaled** on the smallest scaled screen, 320 × 240, which vanilla's GUI scale never goes below for a window that size or larger. The pose-scale fallback covers resource-pack fonts and smaller windows.

**Vectors** (reference implementation, cross-checked against the JDK):

| `W` × `H` | Entries | `T` | Header, spill widths | Columns × rows | Frame | Drawn at | Scale |
|---|---|---|---|---|---|---|---|
| 320 × 240 | 64 | 24 | 120, 180 | 6 × 11 | 280 × 226 | (38, 12) | 1 |
| 640 × 360 | 6 | 12 | 100, 150 | 4 × 2 | 158 × 64 | (328, 188) | 1 |
| 640 × 360 | 0 (empty, no spill line) | | 90, — | 0 × 0 | 98 × 17 | (328, 188) | 1 |
| 320 × 240 | 1 | 30 | 120, 180 | 1 × 1 | 188 × 46 | (130, 128) | 1 |
| 320 × 240 | 64 | 36 (a wide pack font) | 120, 180 | 5 × 13 | 294 × 262, drawn 265 × 236 | (53, 2) | 0.9008 |

- **Precomputing:** the layout is rebuilt only when the block entity's synced data, the viewer's knowledge, #26's synced rules, the resources or the scaled screen size change. Our draw code allocates nothing per frame.
- **Overlap:** the clamp can push a very large frame over the `vftv:vis` layer at the top left; the layer order above decides what is on top, and the worst-case mockups show it.
- **The fork's strike** on a crucible still reads the crucible's own aspects, never its contents ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)).

### The liquid

A block-entity renderer draws **one horizontal quad at y = 15/16** over the interior, from the sprite `vftv:block/crucible_liquid` (block atlas, translucent: liquids are one of the style's two translucencies, [art style §3](art-style.md#3-resolution-and-pixel-rules)), with the block's packed light.

- **Tint:** the colour of the **dominant aspect**: the registry aspect with the largest amount, ties to the lowest id; missing ids are skipped. An empty pool, a pool holding only missing ids, or a dormant record uses `vftv:aqua`'s colour.
- **Dry:** no quad at all.
- **Animation:** 4 frames of 5 ticks each, no interpolation (the sprite's `.mcmeta`).
- **Identity never rests on the tint:** the HUD's glyphs carry it ([aspects, Colours](aspects.md#colours)). The renderer allocates nothing per frame.

**Vectors** (reference implementation, cross-checked against the JDK; registry Aer, Aqua, Ignis, Terra): Terra 5, Aer 5, Ignis 3 → Aer; a missing id at 50 and Ignis 3 → Ignis; only a missing id → Aqua; empty → Aqua.

### Sound and particles

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:crucible.fill` | 1 | a fill | at the block's centre, to everyone in range; `SoundSource.BLOCKS`, volume 1.0, pitch 1.0 |
| `vftv:crucible.empty` | 1 | an emptying | likewise |
| `vftv:crucible.spill` | 1 | a non-empty spill, either cause | likewise, with 8 `minecraft:smoke` particles above the rim |
| `vftv:crucible.dissolve` | 3 | the step sound, below | likewise |
| `vftv:crucible.craft` | 3 | the step sound | likewise |
| `vftv:crucible.refuse` | 1 | the step sound | likewise |
| `vftv:crucible.boil` | 3 | ambient, while boiling | client-only (`playLocalSound`), `SoundSource.BLOCKS`, volume 0.6, pitch 1.0, at most once per crucible per 40 ticks |

- **One step sound per crucible per step,** after the step: `craft` if any craft committed, else `dissolve` if anything dissolved, else `refuse` if anything was refused; none for a step that did nothing.
- **Bubbles:** `animateTick` on boiling states spawns `1 + random.nextInt(2)` vanilla `minecraft:bubble_pop` particles at random points of the interior at y + 15/16. It is client randomness; nothing depends on it.
- **Files:** `assets/vftv/sounds/crucible/fill.ogg`, `empty.ogg`, `spill.ogg`, `refuse.ogg`, `dissolve_0.ogg` … `dissolve_2.ogg`, `craft_0.ogg` … `craft_2.ogg` and `boil_0.ogg` … `boil_2.ogg`, in the Stage 2 register ([art style §7](art-style.md#7-sound-palette)): bright and warm, with clean partials. `sounds.json` entries carry no volume or pitch overrides and name the subtitle keys `subtitles.vftv.crucible.<event>`.

## The authoritative preview

**`CruciblePreview.of(player, recipe, context)`** in `core.crucible` is the **only** cost source for the book's `vftv:recipe` pages, the fact fields and JEI.

**The context** is the crucible the player is looking at: the client's block hit is a `vftv:crucible` within the player's `block_interaction_range`. With none, or one whose chunk isn't loaded, the context is absent.

| Field | Value |
|---|---|
| `recipe` | the recipe id |
| `output` | the created result stack |
| `catalyst` | the catalyst's slot display |
| `aspects` | the cost in ascending aspect id, each marked discovered or unheard for this player |
| `spill` | always **0**, typed, never absent: crafting spills nothing |
| `availability` | first match: `NO_CRUCIBLE` (no context), `UNREADABLE` (a dormant record), `DRY`, `COLD`, `SHORT`, `READY` |
| `shortfall` | present iff `SHORT`: cost − pool for each short aspect, in ascending id |
| `crucible` | the context's position; absent iff `NO_CRUCIBLE` |

- **Absent is never affordable:** without a readable context no availability says ready, and every place that shows one says why.
- **Identity is visible:** the book's recipe page and JEI show `crucible.vftv.checked_at` (x, y, z) whenever there is a context.
- **Live dependencies:** a preview is recomputed when any input changes: the context (which block is targeted, and its synced pool, `water`, `boiling` and readability), the learned set and the player's discovered aspects, the recipe snapshot, or the resources (names). Open book pages and JEI views refresh. Caches may keep layout, **never an old availability**.
- **The preview decides nothing.** The server's step decides, against the live pool.

**Fact fields,** `{fact:recipe:<ns>:<path>:<field>}` ([research, Prose and fact references](research.md#prose-and-fact-references)), resolved through the preview:

| Field | Renders |
|---|---|
| `output` | the result's name, preceded by "*n* × " when its count is above 1 |
| `inputs` | the catalyst as "1 × *name*" of the alternative it lists first |
| `aspects` | the cost, "*Aspect* *n*", ascending aspect id, joined by ", "; an unheard aspect as `crucible.vftv.unheard` with its exact *n* |
| `flux` | "0" |
| `status` | `READY`: `crucible.vftv.status.ready`; `SHORT`: `crucible.vftv.status.short` with the shortfall in the `aspects` form; otherwise `crucible.vftv.status.no_crucible`, `.unreadable`, `.dry` or `.cold` |

Aspect names are the localised names; numbers are plain integers in ASCII digits. The research spec's fact grammar and live-value rule are amended in this change so that a preview may take a world context, which is then one of its dependencies.

## JEI

A soft dependency ([architecture, Soft dependencies](architecture.md#soft-dependencies)), never on the progression path. Every JEI class lives in `…neoforge.compat.jei.client`.

- **One category** for `vftv:crucible`, 160×84: the catalyst in an 18-px slot at (0, 8); the result at (106, 8); the **cost panel** at (0, 30), 4 × 2 cells of 40×18 under the **shared cost-cell rule** ([arcane crafting, The screen](arcane.md#the-screen), amended in this change): the icon at (1, 1), the number confined to x = 18…38 and drawn at the scale min(1, 20 / its measured width), so a cost of 1000 (24 px in the vanilla font) shrinks rather than covering the icon, and the cell's tooltip shows the exact, unscaled value. The sprites are the Worktable's `vftv:worktable/cost_cell` and, for an aspect short against the context, `vftv:worktable/cost_cell_short`; an unheard aspect draws the unheard icon with its exact cost. A **status line** at (0, 72), **clipped to 160 px** with "…": `crucible.vftv.checked_at`, or the absent availability's text. Ordinary world coordinates already overflow it, so its tooltip carries both lines in full: the context with its complete coordinates, and the full `status` fact. Title `jei.vftv.category.crucible`. The crucible item is its catalyst. **No transfer handler.**
- **Reconcile:** the arcane spec's order-independent reconcile function, unchanged, applied to `vftv:crucible` with the crucible snapshot and the learned set; it runs on runtime start, on each `RecipesReceivedEvent` and on each research baseline and delta, and logout clears its caches ([arcane crafting, JEI](arcane.md#jei), amended in this change to say the function is shared). No new lifecycle.
- **Affordability** is marked per recipe against the context; open views refresh on the preview's live dependencies.

## Multiplayer and automation

- **The crucible is unowned.** The research gate belongs to the thrower, and anyone can read the HUD.
- **The protection promise is exactly** vanilla's spawn protection and `mayInteract` and `RightClickBlock` for filling and emptying, and `BreakBlockEvent` for mining. Claim mods don't guard thrown items, so **another player can dissolve junk into your crucible**, or feed it a catalyst they have learned. This is a stated limitation: the owner extracts the junk into storage, or empties it at its price.
- **Two players throwing in one step** are ordered by entity id (see "Admission").

| Path | Behaviour |
|---|---|
| Players, droppers, anything that makes item entities | can feed the crucible. A dropper's items have no thrower, so they always dissolve. |
| Recipes | only for a resolved thrower who has learned the recipe: **no vanilla dropper or hopper crafting** |
| [Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23) | **golem-assisted crafting**: a golem drops items as its online, same-level owner (see the contracts), plus automatic dissolving and extraction |
| Hoppers, pipes | cannot insert: there is no item capability |
| Extraction | through Essentia logistics, under "Essentia out" |
| Thaumatorium-style auto-crafting | none in v1 |

There is no silent fallback to whoever placed or last touched the crucible, and no second craft authority. **Why:** one gate path, and no second spend path to prove ([arcane crafting, Multiplayer and automation](arcane.md#multiplayer-and-automation)).

## Authored content

The content ticket delivers authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen, which writes the resources into `src/generated/resources`. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails its recipe type's rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/crucible/recipes.json       the two recipes this spec owns
content/crucible/first_brass.json   the first-Brass dose plan
content/lang/en_us/crucible.json    every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `recipes.json` | `vftv:crucible_recipes/1` | each recipe `<ns>:<path>` → `data/<ns>/recipe/<path>.json`, its body unchanged |
| `first_brass.json` | `vftv:crucible_dose_plan/1` | nothing: read by the reachability test only |
| `lang/<lang>/crucible.json` | `vftv:lang/1` | its `strings` object merged into `assets/vftv/lang/<lang>.json`; a duplicate key across all `content/lang` files fails datagen |

**Other specs' crucible recipes** live in their own content files with the recipe type's body, under their own `research`. A recipe id declared in two `content/` files, of any type, fails datagen.

### Recipes

`{"schema": "vftv:crucible_recipes/1", "recipes": {<id>: <body>}}`. **"Exactly two" applies to this file**, not to the recipe type:
- **`vftv:crucible`**: a [`vftv:arcane`](arcane.md#the-recipe-type) body with `research` `vftv:crucible`, `kind` `ordinary` (or omitted), and result `vftv:crucible` count 1. Its ingredients include `minecraft:cauldron`, and every ingredient is a `minecraft:` item or tag. Its `vis` uses only the six built-in primals, each ≤ the Wellspring's per-aspect amount in `content/nodes/types.json` (the lint reads it).
- **`vftv:resonant_brass_ingot`**: a `vftv:crucible` body with `research` `vftv:resonant_brass`, catalyst `minecraft:copper_ingot`, and result `vftv:resonant_brass_ingot` count 1. Its `aspects` hold 1–3 entries totalling ≤ ¼ of the default `pool.capacity`.

A complete file (ingredients and costs illustrative; the content ticket chooses them within the rules):

```json
{"schema": "vftv:crucible_recipes/1", "recipes": {
  "vftv:crucible": {
    "type": "vftv:arcane",
    "research": "vftv:crucible",
    "kind": "ordinary",
    "ingredients": ["minecraft:cauldron", "minecraft:amethyst_shard", "minecraft:copper_ingot"],
    "result": {"id": "vftv:crucible", "count": 1},
    "vis": {"vftv:aqua": 12, "vftv:ignis": 12, "vftv:terra": 6}},
  "vftv:resonant_brass_ingot": {
    "type": "vftv:crucible",
    "research": "vftv:resonant_brass",
    "catalyst": "minecraft:copper_ingot",
    "aspects": {"vftv:metallum": 6, "vftv:sonus": 4},
    "result": {"id": "vftv:resonant_brass_ingot", "count": 1}}
}}
```

**Rejected:** any id other than these two, or either missing; another `type`, `research`, `kind`, catalyst or result; any field outside its recipe type's table; for `vftv:crucible`, no `minecraft:cauldron` ingredient, an ingredient outside `minecraft:`, or a `vis` key that isn't a built-in primal or a value over the Wellspring bound; for `vftv:resonant_brass_ingot`, more than 3 aspects or a total over ¼ of the default capacity; a created result that is empty or over its maximum stack.

### The first Brass

`{"schema": "vftv:crucible_dose_plan/1", "recipe": <id>, "doses": [{"item": <id>, "count": 1–64, "why": <one line>}, …]}`, with 1–16 doses (`MAX_DOSES`).

**The reachability test** (build phase): start from an empty pool at the default `pool.capacity`; dissolve each dose in order with the real fit rule and the generated `vftv:aspects` values (#30's table, item lookup, 1×). **Every dose must dissolve whole** (`k` = count), and afterwards the pool must hold at least the recipe's cost for every aspect. Leftovers are allowed: they are what the player later extracts or empties. Every dose item is a `minecraft:` item, is not protected, and its reviewed `why` says how it is obtained in Stages 1–2 without Resonant Brass.

```json
{"schema": "vftv:crucible_dose_plan/1", "recipe": "vftv:resonant_brass_ingot",
 "doses": [
   {"item": "minecraft:raw_copper",      "count": 3, "why": "copper ore is common at every Stage 1 depth"},
   {"item": "minecraft:amethyst_shard",  "count": 2, "why": "mined from the geode that opens Stage 1"}]}
```

**Rejected:** a `recipe` other than `vftv:resonant_brass_ingot`; no doses or more than 16; a count out of range; a missing or empty `why`; an item outside `minecraft:` or a protected one; a dose that doesn't dissolve whole; an end pool short of the cost.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text, and its messages teach filling, heating, ordinary against sneak drops, `SHORT`, safe extraction and the price of emptying.

| Key | Args | Sense |
|---|---|---|
| `block.vftv.crucible` | 0 | "Crucible" (also its item's name) |
| `item.vftv.resonant_brass_ingot` | 0 | "Resonant Brass Ingot" |
| `hud.vftv.crucible.boiling`, `.cold`, `.dry` | 2 | "Boiling: %s of %s", "Cold: %s of %s", "Dry: %s of %s" (total, capacity) |
| `hud.vftv.crucible.dormant` | 0 | "I can't read what's in it." |
| `hud.vftv.crucible.spill` | 1 | "Emptying it spills %s flux." |
| `message.vftv.crucible.protected` | 0 | "I won't melt that down." |
| `message.vftv.crucible.nothing` | 0 | "Nothing in it will dissolve." |
| `message.vftv.crucible.full` | 0 | "There's no room left in it." |
| `message.vftv.crucible.short` | 1 | "The crucible is short: %s." (the shortfall) |
| `message.vftv.crucible.error` | 0 | "Something slipped. Nothing was used." |
| `message.vftv.crucible.dormant` | 0 | "I can't read what's in it, so I won't touch it." |
| `crucible.vftv.unheard` | 0 | "something unheard" |
| `crucible.vftv.checked_at` | 3 | "Checked against the crucible at %s, %s, %s." |
| `crucible.vftv.status.ready` | 0 | "Enough is dissolved." |
| `crucible.vftv.status.short` | 1 | "Short of %s." |
| `crucible.vftv.status.dry`, `.cold` | 0 | "The crucible is dry.", "The crucible is cold." |
| `crucible.vftv.status.no_crucible` | 0 | "(no crucible before me)" |
| `crucible.vftv.status.unreadable` | 0 | "(a crucible I can't read)" |
| `jei.vftv.category.crucible` | 0 | "Crucible" |
| `subtitles.vftv.crucible.fill`, `.empty`, `.spill`, `.dissolve`, `.craft`, `.refuse`, `.boil` | 0 | "Crucible fills", "Crucible empties", "Essentia spills", "Crucible dissolves", "Crucible rings", "Crucible refuses", "Crucible bubbles" |

**Used, not declared here:** every aspect's name belongs to the aspects spec's lang.

```json
{"schema": "vftv:lang/1", "strings": {
  "block.vftv.crucible": "Crucible",
  "hud.vftv.crucible.spill": "Emptying it spills %s flux.",
  "message.vftv.crucible.short": "The crucible is short: %s.",
  "crucible.vftv.checked_at": "Checked against the crucible at %s, %s, %s.",
  "subtitles.vftv.crucible.craft": "Crucible rings"
}}
```

**Rejected:** a key outside the table; a required key missing in `en_us`; a placeholder count that differs from the table; an empty string; a duplicate key, including any other spec's key redeclared here.

## Events

**`CrucibleEvent`**, a NeoForge post-event in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server **after the commit** through a publisher seam that `neoforge` implements: `core` never imports a NeoForge event, capability or attachment type. Its payload records live in `api` and are **immutable**: item stacks are copies, and aspect maps are unmodifiable and ordered by ascending id.

| Kind | Payload |
|---|---|
| `DISSOLVED` | level and position; the thrower or none; a copy of the item whose count is the number dissolved; the aspects added |
| `CRAFTED` | level and position; the player; the recipe id; a count-one copy of the catalyst; a copy of the result; the aspects spent |
| `SPILLED` | level and position; the player or none; the aspects spilled, as `ResourceKey` → amount, missing ids included; the cause, `api`'s `SpillCause.EMPTIED` or `REMOVED` (the enum's other values are posted only by other events: `DESTROYED` by the [essentia spec's](essentia.md#events) `EssentiaEvent`, and `UPSET` and `ABANDONED` by the [infusion spec's](infusion.md#events) `InfusionEvent`, which also posts `REMOVED`) |

- **Once per commit:** one `DISSOLVED` per dissolve action, one `CRAFTED` per craft, one `SPILLED` per non-empty spill.
- **Nothing fires** for a refusal, an extraction, a fill, a refresh or a step.
- **A throwing listener** is logged once; the commit stands, and the remaining notifications still run (see "Committing a craft").

**Plain `core` services:** `CruciblePreview.of`; `CrucibleRecipes.candidates(player, stack)` over the current snapshot. Whether they are exposed in `api` is the API spec's call.

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| `water`, `boiling` | block state | as blocks are |
| the pool | the block entity, `{"format": 1, "pool": {<aspect id>: 1–10000}}`: at most 64 entries, total at most 10,000 | trackers: the full state in the chunk's initial block-entity data (`getUpdateTag`), then the coalesced block-entity update. The update tag also carries the **capacity in force** and whether the record is **dormant**, so the tuning file stays server-only. |
| pool revision, operation, candidate set, step guard | block-entity fields | never |
| the refusal mark `vftv:crucible_refused`, the dissolve mark `vftv:crucible_dissolve` | entity attachments, serialised with the entity, reached from `core` through the architecture's `DataSlot` seam | never |
| the loaded set, the refresh queue, the dirty set | per-level memory | never |
| crucible recipes | the recipe manager; `CrucibleRecipeSnapshot` in memory | every player: `sendRecipes(vftv:crucible)` on join and `/reload`; the client's snapshot is cleared at logout |
| HUD layout, preview cache, JEI reconcile state | client memory | never; cleared at logout |

- **Coalesced updates:** every change marks the block entity in a per-level dirty set, flushed once per tick after the level ticks: **at most one update packet per crucible per tick**, the [wands spec's flush](wands.md#sync-and-state). The set holds block entities, not positions, and the flush skips any that is removed or no longer the one at its position, so **a stale entry never updates a replacement**.
- **Formats:** a record with an unknown format, or out of bounds (an amount outside 1–10,000, more than 64 entries, a total over 10,000, a malformed or duplicate id), is **kept as its raw tag and written back unchanged**, with one log line, and the crucible is **dormant**: no admission, step, extraction, bucket change or spill; the HUD and the preview read it as unreadable; removal loses it with a warning (see "Removal"). **Decoding checks the format bounds, never the current capacity.** Zero amounts are never written.
- **No custom payload** in either direction.
- **Bounded codecs:** the block entity as above; the recipe `MapCodec` and `StreamCodec` under "The recipe type"; the tuning file; both attachments (one boolean each).

**Reloads:**

| Change | Effect |
|---|---|
| Recipes (`/reload`) | the snapshot is replaced, or emptied if over the cap; recipes re-sent; previews recompute; JEI reconciles |
| Tags (`/reload`) | every loaded crucible is queued for refresh (heat re-derived) |
| Tuning (`/reload`) | a new record; every loaded crucible is queued for refresh, so trackers receive the capacity in force; unpayable recipes are logged |
| The aspect or research registries | need the world reopened |
| Resource reload | the HUD layout, names and the liquid sprite rebuild |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/crucible.json`**, loaded on reload into an immutable record in `core.crucible` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client never reads it: the capacity rides the block-entity update.

| Key | Value | Range | Notes |
|---|---|---|---|
| `pool.capacity` | 200 | 16–10,000 | the pool's total cap |
| `step.interval` | 5 ticks | 1–40 | ticks between one crucible's steps |

**Other homes:**
- the heat sources and protected items: the block tag `vftv:crucible_heat` and the item tag `vftv:crucible_refuses` (packs may extend them);
- costs, catalysts, results and gates: each recipe's data (`content/crucible/recipes.json` and the other specs' content files, or packs);
- the bootstrap bound (32 today): the Wellspring's per-aspect amount in `content/nodes/types.json`, read by the lint;
- the first-Brass doses: `content/crucible/first_brass.json`.

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`CrucibleLimits`** in `api`:

| Constant | Value |
|---|---|
| `MAX_CRUCIBLE_RECIPES` | 4,096 |
| `MAX_COST_ENTRIES` | 8 |
| `MIN_COST`, `MAX_COST` | 1, 1000 |
| `MAX_ENTITIES_PER_STEP` | 16 |
| `MAX_POOL_ENTRIES` | 64 |
| `MAX_POOL_TOTAL` | 10,000 (the format bound, and the capacity's ceiling) |
| `MAX_REFRESHES_PER_TICK` | 64 per level |
| `MAX_ID_CHARS` | 256 |
| `MAX_SPILL_FLUX` | 1,000,000 |
| `MAX_DOSES` | 16 |

Also fixed in code: the refusal codes; the two entity attachments; the phase function; the HUD and JEI geometry; the number formats. **Fixed feedback constants:** the ejection velocity (0.3 up, 0.15 toward the thrower, coincidence below ℓ² = 10⁻⁸); the result's spawn offset (0.5, 1.0625, 0.5); one step sound per step, craft over dissolve over refuse; the 40-tick boil cooldown and its volume 0.6; 1–2 bubbles per `animateTick`; 8 spill particles; the liquid's 4 frames of 5 ticks; playback volume and pitch 1.0 for every other sound.

## Performance

**Heavy-base workload:**
- **16 boiling crucibles,** each with a resting flood of 128 refused entities inside. Every step, an online player who has learned the recipes throws one fresh 64-stack of a dissolvable item and one stack of a learned catalyst into each. The fixture (harness work, not the mod's) doses each pool and removes outputs and refusals every second, so **every step commits one craft and one dissolve**: 64 crafts a second at `step.interval` 5.
- **512 crucible recipes** are loaded, **half with non-simple catalysts**, so every craft runs custom `Ingredient` predicates.
- **4 #20 pullers** (stubs) extract from each crucible every tick, in transactions nested two deep, half of them aborted at the root.
- **4 players,** each with the HUD up on one crucible; on one client, a book recipe page and a JEI category with a context; one join near the crucibles.

**Share:** server ≤ 0.03 ms mean, ≤ 0.15 ms p99, measured in the combined stress world, including contact admission, the custom predicates, spawns and their events, and sync. Client 0 with no crucible within the block-entity view distance and no crucible page or JEI view open; ≤ 0.05 ms p50 with the workload's view, including the renderer's extraction and submission. **Measured separately as load scenarios** (reported): worst-case selection at 4,096 non-simple catalysts with the match on the highest id; a recipe `/reload` at 4,096; a tag `/reload` with 1,024 loaded crucibles (the refresh queue at its cap); the sync after a tuning `/reload`; a 500-entity flood in one crucible.

**Hot paths:**

| When | Work |
|---|---|
| Per tick | nothing for cold or dry crucibles; one phase compare per boiling crucible; per item contact, the checks and, on a phase tick, one offer into a 16-slot set; the refresh queue, ≤ 64 per level; the dirty flush, ≤ 1 packet per crucible |
| Per step | ≤ 16 actions. A dissolve: O(≤ 5 aspects) plus the ≤ 64-id union. A craft: one index lookup, ≤ 4,096 candidate tests on a count-one copy, one spawn, one revalidation and ≤ 4 notifications. |
| Per extraction | O(1) through the slot lookup, plus one pool snapshot of ≤ 64 entries **per touched transaction depth** |
| Packets | ≤ 1 block-entity update per crucible per tick; one recipe sync per join and reload |

- **Allocation:** bounded, not zero. **Each action** allocates a constant amount: the result stack and entity, stack copies, the event payloads and at most one update tag. **Each snapshot** allocates ≤ 64 entries per transaction depth. **Selection** may allocate scratch per non-simple candidate, budgeted as `setup + perCandidate × candidates`, capped at 4,096 candidates. The byte ceilings are calibrated by the build and stored in its committed allocation-test fixture, which asserts them at 64, 512 and 4,096 candidates and per depth. **Zero** per contact offer, and zero per frame in our HUD and renderer draw code (allocation-budget tests); engine draw calls and JEI's own rendering are outside that claim.
- **Client:** the HUD layout and previews are recomputed only on a dependency change, never per frame.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **the phase:** every vector above, `step.interval` 1 and 40;
- **admission:** the drain vector; duplicates; a new game time emptying the set; the lowest-16 rule under any offer order; constant work per offer;
- **the decision:** its order, including a **protected stack that matches a catalyst** (neither crafted nor dissolved) and the dissolve mark; every selection vector; non-simple catalysts; the count-one copy;
- **the fit rule:** every vector above, including the 64/65-id union, `long` totals and a lowered capacity;
- **ejection** and **tint:** every vector above;
- **the HUD layout:** every vector above, translated header and spill widths, the pose-scale fallback;
- **the recipe codec:** required `research`; aspect keys through registry ops (a compound and Vacuos accepted, an unknown aspect rejected); 0, 1001 and a ninth entry rejected; a created result empty or over its maximum rejected; the `StreamCodec` round trip and its bounds;
- **the snapshot:** index merge order, the 4,096 cap (start refusal, reload emptying);
- **the handler:** every method row; **an empty resource or a negative amount throws `IllegalArgumentException` from all four `insert` and `extract` overloads**; a zero amount, a mismatched resource, an invalid index and each unavailable state return 0 without throwing; **nested transactions:** a child commit undone by a root abort, one snapshot per touched depth, the root-commit publication skipped for a removed block entity;
- **the preview:** each availability; every fact field's format, including unheard masking and absent contexts;
- **codecs:** the block entity (format 1, every bound, zero amounts never written, duplicates rejected, the raw-tag round trip of a dormant record, missing ids preserved), the update tag, both attachments, the tuning file and every `content/` schema;
- **content lints:** the bootstrap bound and the `minecraft:` ingredient rule; the Brass rules; built-in costs ≤ the default capacity; **the first-Brass reachability test**; **non-amplification** per built-in crucible recipe (see the contracts); every `content/` rejection rule above.

**GameTest:**
- **filling and boiling:** fill and empty; a second fill and an empty on a dry crucible change nothing; creative buckets and a full inventory; heat on and off (fire, a campfire unlit then lit, lava, magma); a cold crucible ignores items until it boils; **a heat-tag `/reload`** that adds and removes a source, with the queued crucible processing nothing until refreshed; a sneaking bucket click;
- **admission:** stationary items resting inside when the crucible comes to the boil are processed; **a 500-entity flood** processes ≤ 16 per step in id order;
- **dissolving:** a stack dissolves; `FULL` partial; the **65th id** refused whole; `NOTHING`; `PROTECTED` (a filled Bell Jar, a filled and an empty Essentia Jar, a shulker box with contents, a written book, and a **protected item that matches a recipe catalyst**) comes out unchanged and marked;
- **the gate:** an unlearned thrower's catalyst dissolves while a learned one crafts; a thrower offline, in another dimension, or a dropper means dissolve; `SHORT` is refused with its message; **a sneak-dropped learned catalyst dissolves**, and a sneak-drop captured at the toss survives the thrower standing up;
- **crafting:** one craft per step; a marked result never reprocessed; the merge behaviour as stated, including the equal-count case;
- **the provisional spawn:** a cancelling `EntityJoinLevelEvent` listener; a listener that **changes the pool**, **removes the catalyst**, **revokes the research**, **replaces the result's stack** or **removes the crucible** during the spawn: each leaves no debit, our result gone, the catalyst refused `ERROR`, and the listener's own changes in place (the removal spills once);
- **notifications:** listeners on `CrucibleEvent(CRAFTED)` and `ItemCraftedEvent` that throw: the craft stands, every other notification still runs, and nothing reports `ERROR`;
- **spill paths:** emptying by bucket spills once with the exact amounts; **a neighbour that removes the crucible during the bucket's `setBlock`** still spills exactly once; breaking and an explosion spill as `REMOVED`; a flag-256 removal posts nothing; a dormant crucible refuses both buckets and spills nothing on removal;
- **extraction:** a committed extraction; an aborted one (the pool identical afterwards); a child commit undone by its root's abort; a capability cache held across a block replacement reads empty; a cold or dry crucible still extracts;
- **open transactions:** extract, then **empty by bucket** before the root closes (refused, nothing changed), then abort: the pool is restored and the crucible still has water; extract, then **a phase tick** (the step does nothing, its entities wait), then abort; extract, then **remove the crucible**, then **commit**, and the same with **abort**: each spills exactly once, as `REMOVED`, the pool the transaction left;
- **reload and persistence:** a recipe `/reload` with the same id and a changed result; over-cap startup fails and an over-cap reload empties the snapshot; save and reload with both marks intact; a dormant record and missing ids survive save and reload unchanged;
- **sync:** ≤ 1 update per crucible per tick under 4 pullers; **after a tuning `/reload` an idle crucible's trackers receive the new capacity**; a stale dirty entry never updates a replacement crucible;
- **bounds:** no chunk is loaded by the step, the refresh queue or the capability;
- every test id in the discovery guard.

**E2E:**
- the HUD in every state (boiling, cold, dry, dormant), with unheard aspects and missing ids, **at the maximum layout (64 entries, 4-digit amounts) on a 320 × 240 scaled screen**, with a **long test translation**, and **overlapping** the strike card and the vis HUD (screenshots plus a state dump of the pool);
- the liquid tint and its absence when dry; the bubbles, sounds and subtitles;
- the book recipe pages and fact fields with no crucible targeted, a dry one, a cold one, a short one and a ready one;
- **with JEI:** hide and unhide on grant and revoke, on join with research learned, with recipes arriving before and after the runtime, and after a recipe reload; the status line following the targeted crucible; **a maximum cost (1000) cell** with the vanilla font and with a wide resource-pack font (the number never reaches the icon; the tooltip exact); **a long-coordinate context** (e.g. x = −29,999,999) clipped on the line and complete in its tooltip; **without JEI**, nothing breaks;
- the Stage 2 flow: learn `vftv:crucible`, craft the vessel at the Worktable, fill and heat it, learn `vftv:resonant_brass`, dissolve the dose plan, make an ingot, then a chime rod (a state dump at each step);
- sync on join, reconnect and `/reload`;
- the asset, lang and model validators: every key under "Lang keys", every sound and subtitle, the block's model, blockstate and item definition, the liquid sprite and its `.mcmeta`, and the HUD sprite.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Aspects and scanning spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/15)'s contracts on this ticket):

| Aspects contract | Met by |
|---|---|
| Dissolves using `vftv:aspects` and `vftv:aspect_defaults` as they are, with its own yield multiplier if it needs one | "Dissolving": the item lookup verbatim, and **yield 1×, no multiplier**; the data is never forked. Protection and the capacity and 64-id checks come first. |
| Vacuos behaves as a primal in decay and never breaks down into Aer or Perditio | "The pool": **no decay at all**. Extraction and spill keep aspects as they are. |
| States whether Aer and Perditio essentia combine into Vacuos; the book never shows a recipe the world refuses | "Combining essentia, and Vacuos": **they do not, because nothing combines in this system**. A contract on #35's prose (below); book recipe pages show only synced recipes ([research, Page descriptors](research.md#page-descriptors)). |
| No round trip yields more essentia than went in | The necessary per-recipe lint, `count × total(v(result)) ≤ min over catalyst alternatives of total(v(alt)) + total(aspects)`, with 1× and whole-item dissolving. **Conditional on** "Committing a craft" (no debit unless the output really exists and every precondition still holds after the spawn), "Essentia out" (extraction only moves essentia, a nested abort restores it exactly, and while a transaction is open no step, bucket or removal can commit anything an abort would overwrite) and #30's round-trip test over every built-in crucible recipe in every content file and every reversible conversion, the arcane vessel included. Ordering alone establishes none of these; each has its GameTest or unit test. |
| Jars and tubes carry labels as well as tint | Jars and tubes are #20's, so the contract is **forwarded** (below). The crucible's own displays identify aspects by glyph; its tint is decoration. |

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts on this ticket):

| Research contract | Met by |
|---|---|
| The gate applies to crucible matching and consumption | "Selection": candidates are the thrower's learned recipes, before any consumption, and "Committing a craft" rechecks the research after the spawn, before any debit. An unlearned catalyst is not a catalyst; dissolving it is matter processing, not a gated recipe. GameTests with an unlearned thrower and with research revoked during the spawn. |
| Book pages follow the real essentia rules, including the Vacuos statement; point synthesis is separate | "Combining essentia, and Vacuos" and the #35 prose contract; puzzle synthesis is untouched |
| **Gate** (`research` on every gated recipe), **recipe-page validator**, **one authoritative preview**, **JEI** by the learned gate | `research` is required on the type; each entry's recipe page is gated by its own entry; "The authoritative preview": a typed, visible context whose absence is never affordable, typed spill 0, frozen fact formats and live dependencies, with the research spec's fact rules amended in this change; "JEI": the arcane spec's reconcile reused (amended in this change), tested with and without JEI |

**Met here** (the [Arcane crafting spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)'s contracts on this ticket):

| Arcane contract | Met by |
|---|---|
| Any recipe made at the Worktable is a `vftv:arcane` body with its own `research`; `kind: void` only behind a Stage 4 gate | "The block": `vftv:crucible` is `ordinary`, gated by `vftv:crucible` |
| Stage 1 arcane recipes meet the Stage 1 affordability test | None is in Stage 1. The vessel adds a Stage 2 bootstrap lint at the same bound, plus a real material path (`minecraft:` ingredients only) and its research in #35's path. |

**Wands, vis and nodes** places no contract on this ticket; "Resonant Brass" supplies the ingot its Stage 2 recipes need.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35):
- **Entries:** `vftv:crucible` (a `note`, parents `vftv:distillation` and `vftv:arcane_crafting`) gates the arcane recipe `vftv:crucible` and carries its `vftv:recipe` page. `vftv:resonant_brass` (a `note`, parent `vftv:crucible`) gates `vftv:resonant_brass_ingot` and carries its page.
- **Ancestry:** `vftv:singing_wands` has `vftv:resonant_brass` in its transitive parents, because the chime rod and cap need the ingot, so no progression cycle is possible.
- **Prose** never presents any essentia combination, Aer + Perditio → Vacuos included; the taught view is notebook theory only ([research, Links](research.md#links)). It teaches filling and heating, ordinary against sneak drops, `SHORT`, safe extraction and the price of emptying, with every number in fact spans.
- **Fact fields** for `vftv:crucible`: `output`, `inputs`, `aspects`, `flux` and `status`, in the formats under "The authoritative preview", with the targeted-crucible context, its typed availability and its live dependencies. The research spec's fact grammar and live-value rule now admit a preview context (amended here).
- **The affordability artifact:** `content/research/path-stage2.json` learns `vftv:crucible`, then `vftv:resonant_brass`, before any step that needs a Brass item; the crucible content's `content/crucible/first_brass.json` is reviewed beside it as the essentia half of the proof.

**[Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)**
- It pulls from a crucible **only through the capability under "Essentia out"**, inside a transaction, honouring nested rollback, the argument preconditions and the unavailable handler, and closing its transactions promptly: while one is open the crucible is transaction-busy. The crucible's suction is 0.
- **It meets the aspects spec's label contract:** jars and tubes carry a glyph label as well as tint; tint alone is not enough.
- **Extensions need explicit proposals.** Any insert path into a crucible, and any essentia combiner, is #20's to propose: it counts toward `pool.capacity` and the 64-entry bound, and treats Vacuos as a true primal (never made from Aer + Perditio).

**[Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)**
- It supplies the pure **`essentiaFlux(spilled, cause)`**: integer 0–1,000,000, a function only of the spilled map, the cause and its synced rule record, with the typed zero as the fallback until it lands. It applies flux equal to it **once** on `CrucibleEvent(SPILLED)`, and owns no second crucible spill producer. GameTest: the HUD's previewed spill equals the applied flux.
- It adds no flux from dissolving, crafting, refusing or extraction.
- **Bounds:** a spill's input is a valid format-1 pool, ≤ 10,000 in total; dormant records are never inputs.

**[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)**
- A golem that drops items into a crucible sets each `ItemEntity`'s thrower to **its owning player**. Recipes then match only while that player is online, alive and in the same level, with the research learned. **The golem spec documents or prevents delivery while those fail**, since the input then dissolves.
- Golems touch the pool only through the capability under "Essentia out". Their own crucible recipes, if any, live in their own content files with the recipe type's body.

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- **Hand entries** for `vftv:crucible` and `vftv:resonant_brass_ingot`.
- **Round-trip non-amplification** includes **every** built-in crucible recipe across all content files, and every reversible conversion, the arcane `vftv:crucible` recipe included, counting vis as no material input ([arcane crafting, Contracts](arcane.md#contracts-on-sibling-specs)).
- **Brass supply:** values for every item in `content/crucible/first_brass.json`, so that a feasible sequence fitting the default pool, with its prerequisites, reaches the Brass cost before any Brass exists; every cost aspect keeps at least 3 accessible sources.

**[Wand and node content](https://github.com/andrewbrogoff/voices-from-the-void/issues/40)**
- `vftv:chime_rod` and `vftv:resonant_brass_cap` take `vftv:resonant_brass_ingot` (frozen here).

**[Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21) and [Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- Their crucible recipes use the recipe type's body with their own `research`, in their own content files, under the common validation and the global non-amplification test.
- Any essentia combination they add keeps Vacuos a true primal, and their book pages stay truthful about it.

## Production tickets

This spec settles every decision. Two required child tickets deliver the assets and the authored content, and **the system is not done until both land**. **Neither writes mod code:** no block, block entity, renderer, HUD layer, recipe type, capability adapter, JEI plugin, datagen provider or validator. The asset ticket delivers a generator (authorised tooling under `tools/art/`) and assets under the [art style](art-style.md); the content ticket delivers authored inputs under `content/`, in the schemas above. The block and item tags, the mineable tag, the loot table, the runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Crucible assets** | The generator `tools/art/crucible.py`. **Block textures,** 16×16, Stage 2 row of [art style §5](art-style.md#5-tone-ramp-by-stage): `assets/vftv/textures/block/crucible_side.png`, `crucible_top.png`, `crucible_inner.png` and `crucible_bottom.png` (a brass-banded vessel, the teal tick as a small inlaid pip on the rim). **Liquid:** `assets/vftv/textures/block/crucible_liquid.png`, a 16×64 strip of 4 frames in light neutral steps (`glass1`, `hush3`) for runtime tinting, with `crucible_liquid.png.mcmeta` = `{"animation": {"frametime": 5, "interpolate": false}}`. **Model** `assets/vftv/models/block/crucible.json`: 5 cuboids (floor `[0,0,0]–[16,4,16]`, walls `[0,4,0]–[2,16,16]`, `[14,4,0]–[16,16,16]`, `[2,4,0]–[14,16,2]` and `[2,4,14]–[14,16,16]`), textures `side`, `top`, `inner`, `bottom` and `particle` (= side). **Blockstate** `assets/vftv/blockstates/crucible.json`: the 4 variants of `water` × `boiling`, all on that model (the liquid is the renderer's). **Item definitions** `assets/vftv/items/crucible.json` (`minecraft:model` of `vftv:block/crucible`) and `assets/vftv/items/resonant_brass_ingot.json`. **Resonant Brass:** `assets/vftv/textures/item/resonant_brass_ingot.png` by the chime-bar rule ([art style §4](art-style.md#4-silhouette-language)), from the prototype, and `assets/vftv/models/item/resonant_brass_ingot.json` (parent `minecraft:item/generated`). **HUD sprite:** `assets/vftv/textures/gui/sprites/hud/crucible_missing.png` (16×16; the frame reuses `vftv:hud/vis_frame`). **Sounds** under "Sound and particles": `assets/vftv/sounds/crucible/fill.ogg`, `empty.ogg`, `spill.ogg`, `refuse.ogg`, `dissolve_0.ogg` … `dissolve_2.ogg`, `craft_0.ogg` … `craft_2.ogg`, `boil_0.ogg` … `boil_2.ogg` (≤ 3 s, faded tails), with the `sounds.json` entries `crucible.fill`, `crucible.empty`, `crucible.spill`, `crucible.dissolve`, `crucible.craft`, `crucible.refuse` and `crucible.boil` naming `subtitles.vftv.crucible.<event>`, no volume or pitch overrides; the playback rules (category, volume, pitch, cooldowns, one step sound per step) are this spec's. **Review:** a contact sheet at GUI scale 2 with the crucible and the ingot beside vanilla's cauldron and copper ingot, and every tint of the liquid; Blender renders of the model, dry and filled; **worst-case HUD mockups** composed from the reused frame, the icons and the vanilla font: 64 entries with 4-digit amounts on a 320 × 240 scaled screen, the wide-font fallback, a long test translation, a dormant record, missing ids and unheard aspects, and the frame clamped over the vis HUD. Provenance rows. | none |
| **Crucible content** | `content/crucible/recipes.json` (`vftv:crucible_recipes/1`, exactly the two recipes under "Authored content, Recipes": their ingredients, costs and Brass aspects chosen within those rules), `content/crucible/first_brass.json` (`vftv:crucible_dose_plan/1`, a dose plan that passes the reachability test against #30's values, each `why` reviewed), and `content/lang/en_us/crucible.json` (`vftv:lang/1`) with every key under "Lang keys". Its review supplies the bootstrap, material and source proof, and checks that the messages teach filling, heating, ordinary against sneak drops, `SHORT`, safe extraction and the price of emptying. | [Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (the dose plan replays its values) |

[Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) and [Wand and node content](https://github.com/andrewbrogoff/voices-from-the-void/issues/40) consume the entry ids, recipe ids, fact fields and the ingot id frozen here.
