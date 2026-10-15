# Infusion

Altar crafting that drives essentia into a central item. This covers:
- the **Singing Bowl** that holds the central item and the **Pedestals** around it;
- the **altar**: the bounded box around a bowl, its symmetry and its **stabilisers**;
- the server's **readout** of an altar, shown before you commit, and the two strikes that start it;
- the one recipe type `vftv:infusion`, including **enchanting through infusion**;
- the **run**: drawing essentia from vessels, absorbing components, **upsets**, pauses, interruption and abandoning;
- the Stage 3 wand tier, the **Echo rod** and **Echo cap**;
- the authoritative preview that the book and JEI read.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the data and sync rules, and the essentia model;
- the [quality gates](quality-gates.md) for every gate;
- the [aspects spec](aspects.md) for aspect icons, the unheard rule and values;
- the [research spec](research.md) for research gates, recipe and structure pages, fact spans, `mix64` and `fold`;
- the [wands spec](wands.md) for wands, the part registries, the coalesced flush and interaction precedence;
- the [arcane crafting spec](arcane.md) for the `vftv:arcane` recipe type, the shared cost-cell rule and the JEI reconcile;
- the [crucible spec](crucible.md) for the phase function, the HUD frame rules, the provisional-spawn pattern and the spill boundary;
- the [essentia spec](essentia.md) for the essentia capability, transactions and the endpoint tag;
- the [art style](art-style.md) for every asset.

Resolved in [Infusion spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/21). Inputs: [TC4 alchemy](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-alchemy/docs/research/tc4-alchemy.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. Each tunable one is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port. Both print the same 50 lines (2,677 bytes, SHA-256 `37a04bc180b0c13f7cfd87dd1b8db3d13a427bdaa4ce735961bb09b2376f6486`).

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 3 Ruins | the Singing Bowl, the Pedestal, the altar, the `vftv:infusion` recipe type, enchanting through infusion, and the Echo rod and cap | the opener `vftv:ruins`, whose find `vftv:find/ruins` is [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s ([research, Stage](research.md#stage)). Inside the Stage, the three `note` entries below. |
| 4 | nothing of its own | other specs' infusion recipes ride the same type, each behind its own `research` |

| Entry | Kind | Parents | Gates |
|---|---|---|---|
| **`vftv:infusion`** | `note` | `vftv:ruins`, `vftv:essentia_jars` | the arcane recipes `vftv:singing_bowl` and `vftv:pedestal` |
| **`vftv:echo_wands`** | `note` | `vftv:infusion`; hidden parent `vftv:singing_wands` | the infusion recipes `vftv:echo_rod` and `vftv:echo_cap` |
| **`vftv:infused_enchanting`** | `note` | `vftv:infusion` | every built-in enchanting recipe |

- **Gate the recipe, not the altar.** Striking, loading and reading an altar are ungated: a bowl someone gave you reads for you, as a crucible does ([crucible, Stage](crucible.md#stage)). **Obtaining counts.**
- **Every infusion recipe carries its own `research`**, checked for the player who starts it at the read and again at the commit, before anything is claimed (see "Starting").
- **The start is the commit.** A run that has started finishes, even if the starter's research is later revoked, and even while they are offline or dead (see "The run"). The research spec's gate contract is amended in this change to say so for machines that run over time.
- **Brass and wand ancestry:** `vftv:essentia_jars` follows `vftv:resonant_brass`, and `vftv:echo_wands` follows `vftv:singing_wands`, because the Echo recipes take the chime rod and the Brass cap as their centres. No progression cycle is possible.
- **Options considered:** a Choir-page find for infusion; one entry for everything. **Why three notes:** the [research roster](research.md#roster) lists infusion as Stage 3 research; the jars parent exists because the altar draws only from vessels; and enchanting and the wand tier are separate ideas.

## The altar

### The blocks

**`vftv:singing_bowl`** and **`vftv:pedestal`**: blocks with block entities.

| Property | Singing Bowl | Pedestal |
|---|---|---|
| Behaviour properties | `BlockBehaviour.Properties.ofFullCopy(Blocks.LECTERN)` without its tags, `noOcclusion()`, `PushReaction.BLOCK` | `ofFullCopy(Blocks.POLISHED_DEEPSLATE)` without its tags, `noOcclusion()`, `PushReaction.BLOCK` |
| Shape (outline and collision, px) | the union of the plinth `[3,0,3]–[13,8,13]`, the floor `[2,8,2]–[14,10,14]` and the four walls `[2,10,2]–[14,14,3]`, `[2,10,13]–[14,14,14]`, `[2,10,3]–[3,14,13]`, `[13,10,3]–[14,14,13]` | the union of the base `[2,0,2]–[14,2,14]`, the shaft `[4,2,4]–[12,10,12]` and the top `[3,10,3]–[13,12,13]` |
| Block state | none (no facing; not waterloggable) | none |
| Use | `useItemOn` and `useWithoutItem` (see "Hand use") | the same |
| Item | the block item, stacking to 64 | the block item, stacking to 64 |
| Ticker, comparator, redstone, random ticks | none (the run is scheduled per level; see "Scheduling") | none |

**Build-phase data** (datagen from code, not production deliverables): both in `minecraft:mineable/pickaxe`; loot tables `vftv:blocks/singing_bowl` and `vftv:blocks/pedestal` dropping themselves (the vanilla `dropSelf` shape). Copying properties copies no tag membership ([crucible, The block](crucible.md#the-block)). Neither is in `vftv:essentia_endpoints`. Their aspect values are [Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)'s hand entries (see the contracts).

**Each holds one item.** Both block entities implement vanilla **`ContainerSingleItem.BlockContainerSingleItem`**, as the decorated pot does (`MC:world/level/block/entity/DecoratedPotBlockEntity.java:27`), with `getMaxStackSize()` = 1. The held item is the **centre** on a bowl and a **component** (once claimed) on a pedestal.

- **Identity.** A bowl has a **bowl id**: a non-zero 64-bit value minted from the level's random source the first time its block entity loads on the server with none, and persisted. Replacing a bowl mints a new id, so nothing that refers to the old one can mistake the new one for it.
- **Drops** on removal come from `BlockEntity#preRemoveSideEffects`'s default `Containers.dropContents` (`MC:world/level/block/entity/BlockEntity.java:255–259`), after this spec's own removal handling (see "Interruption").

### Holding items: the item handler and the container

The two paths that move items without a hand obey **one rule set**.

**Item capability.** `Capabilities.Item.BLOCK` is **registered explicitly** for both block entity types, on every face and the null side. Implementing `Container` doesn't register it: NeoForge registers only vanilla's own containers (`NF:capabilities/CapabilityHooks.java:102–113`), and its stock wrapper extracts without consulting `canTakeItem` (`NF:transfer/item/VanillaContainerWrapper.java:163–165,200–206`; `NF:transfer/item/ItemStackResourceHandler.java:109–128`), so it would bypass every lock below. **One explicit adapter** in `neoforge.infusion` implements `ResourceHandler<ItemResource>` over the slot, with **our own `SnapshotJournal`**.

| Method | Rule |
|---|---|
| `size()` | 1 |
| `getResource(int)`, `getAmountAsLong(int)` | slot 0's item and count (0 or 1); empty and 0 for any other index or while unavailable |
| `getCapacityAsLong(int, r)` | 1 if `r` may be inserted now (below), else 0; 0 for any other index or while unavailable |
| `isValid(int, r)` | index 0 and `r` may be inserted now |
| `insert(…)` | after the preconditions: 0 if unavailable, if the index isn't 0, if the slot is occupied or if `r` may not be inserted. Otherwise `k = min(n, 1)`; if `k` > 0 the journal snapshots this depth and the slot is set. Returns `k`. |
| `extract(…)` | after the preconditions: 0 if unavailable, if the index isn't 0, if the slot doesn't hold `r` or if taking is locked. Otherwise `k = min(n, 1)`; if `k` > 0 the journal snapshots this depth and the slot is emptied. Returns `k`. |

- **Arguments first:** every `insert` and `extract` overload begins with `TransferPreconditions.checkNonEmptyNonNegative(resource, amount)`; after that nothing throws ([crucible, Essentia out](crucible.md#essentia-out)). The resource-only overloads are overridden to O(1).
- **Unavailable:** the block entity is removed or isn't the one at its position, or its record is dormant, or (bowl only) a run operation is open on it.
- **May insert:**
  - **bowl:** it is idle (no run) and empty; any item.
  - **pedestal, unclaimed** (no claim, or a claim found dead; see "Claims"): any item.
  - **pedestal, claimed and live:** only **restoration**: the slot is empty, the claim's component is still pending, and `r` is the **promised stack** (`isSameItemSameComponents` with the copy in the run's plan). This is how a jolted component goes back.
- **Taking is locked** on a ringing bowl and on a pedestal whose claim is live or unknown.
- **Transaction-busy:** from the journal's first snapshot until its root closes, on commit or abort. While busy, the hand paths refuse (`message.vftv.infusion.busy`), the container path below refuses, the run skips that block for absorbing and jolting (the step stalls), a commit refuses, and removal is deferred to the root's close, which then drops whatever the journal left. **An abort can only restore what it snapshotted.**
- **On the root commit:** the block entity is marked changed and dirty for the coalesced sync, unless removed. Publication only.

**The container path.** Vanilla's hopper prefers a `Container` block entity to any capability (`MC:world/level/block/entity/HopperBlockEntity.java:446–455`) and honours `canPlaceItem` and `canTakeItem` (`:340–349`). Both return exactly the handler's "may insert" and "taking is not locked", and both return `false` while the block entity is dormant, unavailable or transaction-busy.

- **Qualified:** code that calls `setItem` or `removeItem` directly (commands such as `/item replace`, other mods) bypasses both locks, exactly as it would on a chest. The run never trusts the slot: an absorb checks the promised stack, and a missing or replaced one stalls (see "Absorbing").

### Hand use

Every row runs inside vanilla's `useItemOn`, after spawn protection, `mayInteract` and `PlayerInteractEvent.RightClickBlock`; a cancelled event or a denied block use wins. **The hand being used decides.** Vanilla tries the main hand first, and an empty main hand reaches `useWithoutItem`, so it takes or abandons before an off-hand item is consulted ([essentia, Labels](essentia.md#labels)). `useWithoutItem` acts **only when the main hand is empty**; otherwise it returns `PASS`. "Places one" never consumes for a player with infinite materials. First match:

| Held (in the hand being used) | Target | Effect | Returns |
|---|---|---|---|
| anything | dormant bowl or pedestal | refused: `message.vftv.infusion.unreadable` | `CONSUME` |
| anything | transaction-busy | refused: `message.vftv.infusion.busy` | `CONSUME` |
| a `vftv:wand` | bowl | a **strike** (see "Reading an altar" and "Starting") | `SUCCESS` |
| any item | idle, empty bowl | places one as the centre; `minecraft:entity.item_frame.add_item` plays | `SUCCESS` |
| any item | ringing bowl | refused: `message.vftv.infusion.ringing` | `CONSUME` |
| any item | empty pedestal, unclaimed | places one | `SUCCESS` |
| the promised stack | empty pedestal, claimed and live, its component pending | **restores** it (places one) | `SUCCESS` |
| any other item | empty pedestal, claimed (live or unknown) | refused: `message.vftv.infusion.promised` (3 args: the bowl's position) | `CONSUME` |
| any item | an occupied bowl or pedestal | `TRY_WITH_EMPTY_HAND` | |
| empty main hand | idle, occupied bowl; unclaimed, occupied pedestal | takes the item through `Inventory#placeItemBackInInventory`; `minecraft:entity.item_frame.remove_item` plays | `SUCCESS` |
| empty main hand | ringing bowl | **abandoning** (see "Abandoning") | `SUCCESS` |
| empty main hand | claimed pedestal (live or unknown) | refused: `.promised` | `CONSUME` |
| empty main hand | anything empty | `PASS` | |

- **Wands:** on a pedestal a wand is an ordinary item. The wands spec's precedence holds: a wand on a non-node block gets the block's own use, and a sneaking wand with a focus is [Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22)'s block-targeted cast, because vanilla skips the block use while sneaking with an item (`MC:server/level/ServerPlayerGameMode.java:400`; [wands, Hand-tapping](wands.md#hand-tapping)). So a wand can't be put into a bowl by hand; a hopper can.
- **Pistons** refuse both blocks (`PushReaction.BLOCK`, and both have block entities).

### The box

An altar is a Singing Bowl plus what its **box** holds: every position at offset (dx, dy, dz) from the bowl with dx, dz ∈ [−4, 4] and dy ∈ [−1, 2], **324 positions**. The box is fixed in code (`InfusionLimits`), because the multiblock page, the bounded-work claims and the claim checks all rest on it.

- **Pedestals count only at dy = 0.** Pedestals elsewhere are ignored, and so are their items. At most **16** pedestals (`MAX_PEDESTALS`); a 17th makes the altar `TOO_MANY`.
- **Vessels** in the box are the essentia sources (see "Drawing essentia").
- **Stabilisers** anywhere in the box count (below).
- **Chunks:** the box spans at most 4 chunks. A read, a commit and every step need each of them loaded (`ServerChunkCache#getChunkNow`) and block-ticking (`ServerLevel#shouldTickBlocksAt(long)`, `MC:server/level/ServerLevel.java:492`). **Nothing in this spec loads a chunk.**

### Symmetry and stabilisers

The **mirror** of (dx, dy, dz) is (−dx, dy, −dz): a half turn about the bowl's column.

- **Lone pedestals** `L`: pedestals whose mirror isn't a pedestal.
- **Lone items** `I`: mirrored pairs of pedestals in which exactly one holds an item. Each pair counts once. Which items they hold doesn't matter.
- **Stabilisers:** a block state is a stabiliser iff its block is in the block tag **`vftv:infusion_stabilisers`** and it has no `lit` property or has `lit = true` (the [crucible's heat rule](crucible.md#heat)). **Tag default:** `minecraft:amethyst_cluster` and `#minecraft:candles`. Packs may extend it.
- **Matched stabilisers** `P`: unordered pairs {s, mirror(s)} that are both stabilisers, with s off the axis (dx = dz = 0 never counts). **Unlike tagged blocks match** (a candle mirrors an amethyst cluster). **One block is one stabiliser:** a block of four candles counts once. An unmatched stabiliser counts nothing and costs nothing.
- **The bonus** `B = min(P, stabilise.max_pairs) × stabilise.per_pair`. The cap ends candle rooms.
- **Options considered:** a fixed template; TC4's free placement with penalties for unmatched stabilisers. **Recommend** bounded free placement with point symmetry and a capped bonus. **Why:** symmetry is the building problem worth keeping; the TC4 alchemy research names candle rooms "a chore built on symmetry", and the pain-points research (§9) asks for a legible danger.

## Reading an altar

### The snapshot and the assessment

**The server reads; the client never assesses.** A player's own client may show phantom blocks ([progression, Reality slipping](progression.md#reality-slipping)), so a readout built from the client's world could lie.

- **The snapshot** is built in `core.infusion` from the live level: for each of the 324 positions, whether it is a pedestal at dy = 0 (with a copy of its item and its claim state), a stabiliser, or in `vftv:essentia_endpoints`; the bowl's centre copy; and the box's chunk state.
- **Resolution stays outside the model.** `core.infusion` evaluates every Minecraft predicate (each candidate recipe's `Ingredient` on each pedestal item, `supportsEnchantment`, the research gate) and hands **`core.infusion.model`** only primitives: the pedestal offsets and item-presence flags, the stabiliser offsets, a candidate's component-by-item match matrix, the costs as aspect indices in ascending id, and integer rule values. The model returns `L`, `I`, `P`, `B`, the matching, the counts and the stability; it never sees an `ItemStack`, `Ingredient`, `ResourceKey` or `BlockPos` ([architecture, Layers](architecture.md#layers)).
- **When:** at a strike, at an arm's refresh, and at the commit's recheck. **Never during a run**, which reads only its claimed pedestals and the box's vessels.

### Statuses

**One enum, `InfusionStatus`, in `core.infusion`,** first match:

| Status | When | Lang key | Args |
|---|---|---|---|
| `UNREADABLE` | the bowl's record is dormant | `status.vftv.infusion.unreadable` | 0 |
| `RINGING` | the bowl has a run (the readout is the run's; see "The readout") | `status.vftv.infusion.ringing` | 1: the output's name |
| `UNLOADED` | a box chunk isn't loaded or block-ticking | `status.vftv.infusion.unloaded` | 0 |
| `EMPTY` | no centre item | `status.vftv.infusion.empty` | 0 |
| `TOO_MANY` | more than 16 pedestals at dy = 0 | `status.vftv.infusion.too_many` | 1: the count |
| `PEDESTAL_UNREADABLE` | a pedestal at dy = 0 is dormant | `status.vftv.infusion.pedestal_unreadable` | 3: its position |
| `PROMISED` | a pedestal at dy = 0 has a live or unknown claim of another bowl | `status.vftv.infusion.promised` | 3: its position |
| `BUSY` | the bowl or a pedestal at dy = 0 is transaction-busy | `status.vftv.infusion.busy` | 0 |
| `NO_RECIPE` | no learned recipe matches (see "Selection") | `status.vftv.infusion.no_recipe` | 1: the number of pedestal items |
| `ENCHANT_REFUSED` | selection found no match, but an enchanting candidate matched centre and components and failed a condition | `status.vftv.infusion.enchant.<reason>` | see "Enchanting through infusion" |
| `READY` | otherwise | `status.vftv.infusion.ready` | 1: the output's name |

Essentia the vessels lack right now is **not** a status: a run may start short and waits without harm (see "Stalls, pauses and interruption").

### Stability

All arithmetic is integer. Every input is exact and shown.

```
R  = min(100, recipe.instability [+ the enchanting term])                       percentage points
Wp = infusion_penalty(starter, recipe.kind)                                      #25's; 0 until #25 lands
S  = clamp(100 − R − L·symmetry.lone_pedestal − I·symmetry.lone_item − Wp + B, 0, 100)   percent
p  = ceilDiv((100 − S) · upset.max_chance, 100)                                  per mille, per rolled step
```

- **Stability** is `S`, the readout's percentage; `p` is derived from it alone.
- **The warp penalty** `Wp` is [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)' pure, kind-aware `infusion_penalty(player, kind)`: an integer 0–100, ≥ 0 for `ORDINARY`, a function only of the player's synced warp state and the recipe's `kind`. **Until #25 lands it is the typed zero.** Infusion is ordinary Stage 3 magic, so warp's dial reaches it: "the more you sound like the Void, the more your resonant magic … spills" ([progression, Warp](progression.md#warp)). It is shown in the breakdown, one of the arm's inputs (so a change bumps the revision), and frozen with the starter's value at the commit.
- **Frozen at the commit:** `R`, `L`, `I`, `P`, `B`, `Wp`, `S`, `p` and the run-affecting rules are written into the run. Moving stabilisers, changing warp or a tuning `/reload` afterwards changes nothing about a run already ringing.

**Vectors** (reference implementation; tuning defaults):

| Altar | (`L`, `I`, `P`, `B`, `S`, `p`) |
|---|---|
| A: 8 pedestals in a ring at distance 2, all loaded; 2 matched pairs; `R` 20, `Wp` 0 | (0, 0, 2, 6, 86, 21) |
| B: 4 mirrored pedestals and 1 lone, one pair half-loaded; `R` 10 | (1, 1, 0, 0, 80, 30) |
| C: the ring; 12 matched pairs (capped at 8); `R` 50 | (0, 0, 12, 24, 74, 39) |
| D: 2 lone pedestals; `R` 100 | (2, 0, 0, 0, 0, 150) |
| E: the ring; two stabilisers on the axis and one unmatched; `R` 0 | (0, 0, 0, 0, 100, 0) |
| F: as A with `Wp` 10 | (0, 0, 2, 6, 76, 36) |

### Counting the run

A **rolled step** is a draw step that drew at least one unit, or an absorb step (see "The run"); stalls and pauses never roll. With the costs (effective, for enchanting) and the frozen `run.rate`:

```
nominal = Σ ceilDiv(cost_a, run.rate) + components          the rolled steps if every draw is full
worst   = Σ cost_a + components                              the most rolled steps without an upset
ceiling = worst + upset.max · upset.spill                     the most rolled steps in any run
clean(p, n): c = 10⁹; repeat n times: c = floor(c · (1000 − p) / 1000); return floor(c / 10⁶)   per mille
```

- **The clean floor** `clean(p, worst)` is a true lower bound on the chance of a run with no upset, **whatever the vessels supply**: every rolled draw takes at least one unit, so a run without upsets rolls at most `worst` times, and each floor only lowers the value. It is labelled "at least".
- **The full-supply figure** `clean(p, nominal)` is labelled "if every draw is full": it is the chance when each draw takes `run.rate` (or what remains).
- Partial supply rolls more often than `nominal`. The readout never presents the full-supply figure as a guarantee.

**Vectors** (reference implementation; `run.rate` 4, `upset.max` 6, `upset.spill` 4; the floored fixed point equals the exact floor in every row):

| Costs; components | `p` | `nominal` | full-supply ‰ | `worst` | clean floor ‰ | `ceiling` |
|---|---|---|---|---|---|---|
| 4; 1 | 150 | 2 | 722 | 5 | 443 | 29 |
| 12, 20, 9; 8 | 21 | 19 | 668 | 49 | 353 | 73 |
| 12, 20, 9; 8 | 0 | 19 | 1000 | 49 | 1000 | 73 |
| 40, 40; 4 | 60 | 24 | 226 | 84 | 5 | 108 |
| 8 × 1000; 16 | 150 | 2016 | 0 | 8016 | 0 | 8040 |

The first row is the trickle case: one unit per draw rolls 5 times, not 2, and the floor says 44.3 %, not 72.2 %.

### The readout

**`InfusionReadout`**, an immutable record in `core.infusion`, is what the HUD, the commit's recheck and the preview read. Fields:
- the status and its arguments; the bowl's position and dimension; the arm's revision;
- for `READY`: the recipe id, `kind` and the output copy (for enchanting, the target level); `R`, `Wp`, `L`, `I`, `P`, `B`, the bonus cap; `S`; `p`; `nominal`, `worst`, `ceiling`; both clean figures; `upset.max`, `upset.spill`; the **upset flux bound** `upset.max × essentiaFluxBound(upset.spill, UPSET, rules)` (see "Upsets"); each cost aspect with its cost and **what the box's sources hold now**;
- the stabiliser-cap flag (`P` > `stabilise.max_pairs`) and the clamp flag (`S` was clamped);
- for `RINGING` (a **run readout**, kind `RUN`): the output; the plan's frozen `S` and `p`; units drawn and owed in all, and owed per aspect; components absorbed and in all; `upsets` and the frozen `upset.max`; whether it is stalled on essentia, stalled on a component (with its position) or paused; and `essentiaFlux(drawn, ABANDONED, rules)` under #26's current rules.

- **"Held now"** is, per cost aspect, the sum over the box's sources (as "Drawing essentia" finds them) of `getAmountAsLong` over the slots holding that aspect, computed in `long` with saturation and **clamped to 99,999** (`HELD_NOW_MAX`), shown as "99999+" when clamped. It is **display only**: it refreshes, but it is not part of the revision, because vessels fill and drain constantly and a start never needs them full.

### Arms and revisions

A strike on an idle bowl creates or refreshes the striker's **arm**: one per player, in server memory, `{kind ARMED, dimension, position, bowl id, revision, seen revision, inputs, readout, last strike tick, due tick}`. A strike on a ringing bowl, and every commit, makes it a **run subscription** instead (kind `RUN`; see below).

- **Inputs** are everything the plan depends on: the snapshot (every reduced cell, every pedestal's block-entity identity, item copy and claim state, the bowl's identity and centre copy), the recipe snapshot's generation, the `InfusionRules` generation, the striker's research revision, `Wp`, and #26's flux-rule generation. **The revision bumps (+1, wrapping 32-bit) whenever the inputs differ**, whether or not any displayed field changed. A same-id recipe `/reload` or an equivalent replacement ingredient therefore bumps it.
- **Selection runs only when inputs other than the claim states and the box's vessels changed** (see "Selection").
- **Refresh:** every `arm.refresh` ticks the arm's due entry pops (see "Scheduling"); the server rebuilds the snapshot and, if the inputs differ, re-assesses, bumps and sends. A change to "held now" alone is sent without a bump, at most once per refresh.
- **Seen:** when the client's HUD takes an `ARMED` readout, it sends **`InfusionSeen(pos, revision)`** (C2S: a `BlockPos` and a VarInt; anything else is rejected at decode), once per readout it receives. The server accepts at most 4 per player per tick and drops extras; an honest client never sends more, because the server sends at most one readout per refresh or strike. The server records it as the arm's **seen revision** iff the arm exists for that dimension and position and `revision` equals the arm's current revision; otherwise it is ignored. A bump leaves the seen revision behind until the client acknowledges the new one. Seen proves only what an honest client displayed; it **authorises nothing** (see "Starting").
- **Run subscription** (`RUN`): it refreshes on the same schedule from the bowl's frozen plan and live counters (progress, owed, upsets, stall or pause, and the abandon price under #26's current rules), **without** reassessing recipes or geometry, and sends only on a change. It ends on the run's completion, abandonment or removal (with a final `CLEARED`), and on the viewer exit conditions below. **A run subscription never authorises a start.**
- **Either ends** on logout, death, a dimension change, the player leaving `Player#isWithinBlockInteractionRange(pos, 1.0)` (`MC:world/entity/player/Player.java:2087`), `arm.ttl` ticks after the last strike, a strike on another bowl, or the bowl's identity changing. The client is sent `CLEARED`. Ending discards the seen revision.
- **Payload:** **`InfusionReadout`** S2C to that player only: the position, the revision, a kind (`ARMED`, `RUN` or `CLEARED`) and the readout. Bounded: ≤ 8 cost cells with costs 1–8,000 and held-now 0–99,999 (VarInts), ids ≤ 256 characters, the output through vanilla's item stream codec.

### The HUD

**HUD layer `vftv:infusion`.** It shows while the client's block hit is the bowl of the player's current readout, within `block_interaction_range`, no screen or debug overlay is open, F1 is off, the player isn't a spectator, and the aspects spec's strike card isn't showing. Layer order: `vftv:vis`, `vftv:crucible`, `vftv:infusion`, then the strike card, which wins at the crosshair.

**Lines,** in this order; each is a lang key with fixed arguments (see "Lang keys"):

| # | `READY` | `RINGING` (a run) | other statuses |
|---|---|---|---|
| 1 | `hud.vftv.infusion.ready`: the output | `hud.vftv.infusion.ringing`: the output | the status's key and arguments |
| 2 | `.stability`: `S` | `.progress`: units drawn, units owed in all, components absorbed, components | |
| 3 | `.breakdown`: `R`, lone pedestals × penalty, lone items × penalty, `Wp`, `B`, `P`, the cap | `.frozen`: `S`, `p` | |
| 4 | `.chance`: `p`, `nominal` | `.upsets`: upsets so far, `upset.max` | |
| 5 | `.clean`: the clean floor, the full-supply figure | `.missing` (the position) when stalled on a component; `.paused` when a box chunk is unloaded; `.waiting` when stalled on essentia | |
| 6 | `.worst`: `upset.max`, `upset.spill`, the upset flux bound | `.abandon`: `essentiaFlux(drawn, ABANDONED, rules)` now | |
| — | cost cells: cost and held now | cost cells: still owed | |
| 7 | `.strike_again`, or `.changed` after a `STALE` refusal | `.abandon_confirm` while abandoning is armed | |

- **Numbers:** `S` as "*n*%"; per-mille values as "*n*.*d*%" (integer division by 10, then the remainder); every other number a plain ASCII integer.
- **Wrapping, not clipping:** each line wraps to `maxInner` with the font's own `split`; no number is ever cut. The cells follow the [crucible HUD](crucible.md#the-hud)'s grid rule (the arcane spec's shared cost-cell sprites `vftv:worktable/cost_cell`, and `cost_cell_short` for an aspect held short now). Then frame, padding, position, clamp and the pose-scale fallback are the crucible's, unchanged, so every readout fits.
- **Unheard aspects** draw the unheard icon with their exact amounts ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)).
- **Narration:** when a readout arrives and vanilla's narrator is on, lines 1 and 2 are queued as system narration. The book's fact fields carry the same values in full (see "The authoritative preview").
- **Precomputing:** the layout is rebuilt only when a readout arrives or clears, the viewer's knowledge, the resources or the scaled screen size change. Our draw code allocates nothing per frame.

## Starting

### Who and with what

- **A wand, any `vftv:wand`,** inert included: it is the striker, not a purse. **No vis is spent.** Essentia is the price, and vis crafting is the Worktable's ([arcane crafting](arcane.md)). A vis charge would need a second spend path with no decision behind it.
- **Who:** any player whose block use is permitted. The recipe gate is **that player's** research, never the placer's or the last user's ([crucible, Selection](crucible.md#selection)).

### The second strike

**Two strikes.** The first reads; the second commits what the first showed. **The commit happens only inside the second strike's own vanilla use**, which claim mods, spawn protection, `mayInteract` and `RightClickBlock` have just permitted. No other interaction, earlier or later, can authorise it, so there is nothing to pair.

A permitted wand use on an **idle** bowl, first match:
1. **A commit attempt** iff the player has an `ARMED` arm for this dimension, position and bowl id that **existed before this use**, and whose **seen revision equals its current revision** (the client acknowledged the readout the server holds now). The attempt captures the hand, the arm's revision and the use's tick.
2. **Otherwise a strike:** assess; create, refresh or keep the arm; send the readout. A strike whose inputs are unchanged keeps the revision.

A wand use on a ringing bowl is a strike that makes a run subscription. The first strike of a sequence can never commit (no arm existed before it), and a cancelled or denied second use reaches neither branch.

- **Why not echo the revision in a C2S start payload:** binding a payload to one particular use needs that use packet's sequence, which vanilla keeps private on both sides (`ServerGamePacketListenerImpl.ackBlockChangesUpTo` is a private field, `MC:server/network/ServerGamePacketListenerImpl.java:242,1367`; `ClientLevel#getBlockStatePredictionHandler` is package-private, `MC:client/multiplayer/ClientLevel.java:221`), so it would take two access transformers. The seen revision gives the same guarantee to an honest client with none: if the server's revision moved after the client acknowledged it, the attempt refuses as stale. If the acknowledgement is still in flight, the attempt also refuses; it never commits an unseen readout.

**Assessments are budgeted.** Strikes, commit attempts and arm refreshes share one per-level allowance, **`MAX_ASSESSMENTS_PER_TICK` = 16** (see "Scheduling"). A use that finds the allowance spent is **queued**: at most one pending assessment per player (a newer use replaces it), in a FIFO by (tick, player UUID). A pending assessment older than **`ASSESS_TTL` = 20 ticks** expires with `message.vftv.infusion.too_busy` and no other effect. When serviced, it is revalidated: the player is alive, not a spectator, in the same dimension and in reach, `level.mayInteract` still allows the position, and the bowl's identity is unchanged. For a commit attempt, the arm's revision and seen revision are also still the captured ones.

### The commit

**Checks,** on the server thread, first failure refusing on the action bar (and, where marked, sending a fresh readout):

| # | Check | On failure |
|---|---|---|
| 1 | the player is alive and not a spectator; the attempt's arm still exists, `ARMED`, for this dimension, position and bowl id | `message.vftv.infusion.strike_first` |
| 2 | the use's hand held a `vftv:wand` | `.strike_first` |
| 3 | the position's chunk is loaded; the player is in reach; `level.mayInteract` (spawn protection, `MC:server/level/ServerLevel.java:901`) | dropped, no message |
| 4 | the arm's revision and seen revision are the captured ones, and equal | `.stale`, fresh readout |
| 5 | **recheck:** rebuild the snapshot, resolve every predicate (ingredients, `supportsEnchantment`, the research gate) and assess; the inputs equal the arm's | `.stale`, bump, fresh readout |
| 6 | the status is `READY` | its status message |
| 7 | **`Transaction.getLifecycle() == Lifecycle.NONE`** (`NF:transfer/transaction/Transaction.java:99–109,247–263`), and neither the bowl nor any matched pedestal is transaction-busy | `.busy` |
| 8 | **claims:** every matched pedestal is unclaimed or its claim is dead; an unknown or unresolved claim refuses | `.promised`, fresh readout |
| 9 | **the live fingerprint**, callback-free, after every callback of check 5: the bowl's and each matched pedestal's block-entity instance (the same object, not removed, still at its position), each slot (`ItemStack.matches` with the assessed copy), each claim, the bowl id and `runs`, the striker's research revision, the recipe snapshot and `InfusionRules` generations, `Wp` and #26's flux-rule generation all equal the assessed inputs | `.stale`, bump, fresh readout |

Check 5 runs every foreign callback (custom `Ingredient` tests, the item's `supportsEnchantment` hook) **before** the commit; check 9 catches anything those callbacks changed. **The commit itself runs no foreign code:**
1. if `runs` = 2³¹ − 1, a new bowl id is minted and `runs` = 0 first (every claim on the old id is dead); then `seq = runs + 1`; `runs = seq`; `seed` (see "Upsets");
2. the **run plan** is written into the bowl's record, complete (see "The plan");
3. each matched pedestal gets the claim `{bowl position, bowl id, seq}`;
4. the arm becomes the starter's run subscription; the bowl and pedestals are marked changed (`setChanged`) and for the coalesced sync; the bowl is entered in the level's step queue.

Then, after the commit and each caught separately: `InfusionEvent(STARTED)`, the `infusion.begin` sound and the first `RUN` readout. **Two overlapping bowls** can't both claim a pedestal: the second commit fails check 8, atomically, because commits run one at a time on the server thread.

- **Options considered:** a client-predicted readout plus an echo (the arcane pattern); a C2S start payload paired with a use; a confirm screen; one click. **Recommend** the server's readout and a commit inside the second permitted use. **Why:** the client can't see the truth here; a payload can't be bound to one use without access transformers; a screen breaks in-world play (pain-points research §6); and one click can race a sync.

## Infusion recipes

### The recipe type

**One type, `vftv:infusion`** (recipe type and serializer share the id). Its body is the body of every infusion recipe in `content/`, this spec's and other specs'.

| Field | Required | Range | Meaning |
|---|---|---|---|
| `type` | yes | `vftv:infusion` | |
| `research` | yes | an id in the `vftv:research` registry | the gate |
| `kind` | no, default `ordinary` | `ordinary` or `void` | passed to #25's penalty; built-in `void` bodies need a Stage 4 gate (a content lint) |
| `centre` | yes | one vanilla `Ingredient` | the bowl's item, tested on a count-one copy |
| `components` | yes | 1–16 `Ingredient`s | the pedestal items, as an exact multiset |
| `essentia` | yes | 1–8 entries; each key any aspect in the `vftv:aspect` registry; each value 1–1000 | the cost; per level for enchanting |
| `instability` | yes | 0–100 | the recipe's own instability, in percentage points |
| `result` | yes | exactly one of the two forms below | the output |

**Result forms:**
- **Item:** `{"item": <ItemStackTemplate with count 1>, "transmute": <boolean, default false>}`. With `transmute: true` the output is vanilla's `TransmuteRecipe.createWithOriginalComponents(result, centre copy)` (`MC:world/item/crafting/TransmuteRecipe.java:81–86`), so an upgrade keeps the centre's components.
- **Enchantment:** `{"enchantment": <id in the enchantment registry>, "max_level": 1–255}`. `transmute` is rejected in this form.

```jsonc
{"type": "vftv:infusion",
 "research": "vftv:echo_wands",
 "centre": "vftv:chime_rod",
 "components": ["minecraft:echo_shard", "minecraft:echo_shard", "minecraft:amethyst_shard", "minecraft:amethyst_shard"],
 "essentia": {"vftv:sonus": 24, "vftv:praecantatio": 16, "vftv:vitreus": 8},
 "instability": 12,
 "result": {"item": {"id": "vftv:echo_rod", "count": 1}}}
```

```jsonc
{"type": "vftv:infusion",
 "research": "vftv:infused_enchanting",
 "centre": "#minecraft:enchantable/sharp_weapon",
 "components": ["minecraft:flint", "minecraft:flint", "minecraft:quartz", "minecraft:quartz"],
 "essentia": {"vftv:telum": 6, "vftv:praecantatio": 4},
 "instability": 10,
 "result": {"enchantment": "minecraft:sharpness", "max_level": 5}}
```

- **Validation at decode, registry-aware,** through the same layered registry ops as the [arcane](arcane.md#the-recipe-type) and [crucible](crucible.md#the-recipe-type) types: `research`, every `essentia` key and `enchantment` must exist. An item result is **created and checked**: non-empty, count 1, within its component-resolved maximum stack size. For enchanting, `essentia[a] × min(max_level, enchantment.getMaxLevel())` ≤ 1000 for every aspect. A recipe that fails any rule fails to decode, and vanilla skips it with a logged error naming it.
- **A transmuted output** is created at the commit's recheck and must be non-empty, count 1 and within its maximum stack size; otherwise that candidate doesn't match (see "Selection").
- **Unknown fields** are ignored at runtime (vanilla `MapCodec`) and rejected in `content/`.
- **Stream codec:** bounded, because the recipe sync encodes through it: ids ≤ 256 characters, ≤ 16 components, ≤ 8 cost entries of 1–1000, `instability` 0–100, `max_level` 1–255, the ingredients and result through vanilla's own stream codecs.

**Not vanilla crafting:** the type is not `RecipeType.CRAFTING`; `isSpecial()` is true; `showNotification()` is false; `placementInfo()` is `NOT_PLACEABLE`. `display()` returns one registered `RecipeDisplay` type, **`vftv:infusion`**: the centre's and components' `SlotDisplay`s, the result (for enchanting, an enchanted book storing the enchantment at level 1) and the bowl item as the station. It carries no cost: costs come from the preview.

### Matching and selection

**Matching** one candidate against an altar:
1. the centre `Ingredient` accepts a count-one copy of the centre;
2. the pedestal items, in **ascending signed `BlockPos.asLong()`** (`Long.compare`), and the components, in recipe order, are equal in number;
3. **Kuhn's augmenting-path matching:** components are taken in recipe order; each runs a depth-first search over the items in pedestal order, reassigning earlier components when that frees an item. Every item must be used, so a stray item on any pedestal means no match;
4. for enchanting, the conditions under "Enchanting through infusion".

**Vectors** (reference implementation): components {iron or gold, iron} against items [iron, gold] → c0 ↦ item 1, c1 ↦ item 0; {iron, iron, amethyst} against [amethyst, iron, iron] → 2, 1, 0; {iron, gold} against [iron, iron] → none; {iron} against [iron, stick] → none.

**Order vectors** (reference implementation, signed `asLong`): a bowl at (−37, 70, 512) with pedestals at (±2, 0) and (0, ±2) absorbs in the order (−39, 70, 512), (−37, 70, 510), (−37, 70, 514), (−35, 70, 512); a bowl at (0, 64, 0) with pedestals at distance 1 orders (−1, 64, 0), (0, 64, 1), (0, 64, −1), (1, 64, 0). Negative x packs into the high bits, so it sorts first; negative z sorts after positive z.

**Selection** for the striker:
1. **Candidates:** the current snapshot's recipes whose centre matches and whose `research` the striker **has learned**, in ascending id (`String.compareTo` on `ns:path`).
2. The **first** candidate that matches wins (`READY`).
3. **None matches:** if some enchanting candidate passed steps 1–3 of matching and failed a condition, the **lowest-id** such candidate gives `ENCHANT_REFUSED` with its first failed condition; otherwise `NO_RECIPE`.

### Recipe sync and reload

The **`InfusionRecipeSnapshot`** follows the [crucible recipe snapshot](crucible.md#recipe-sync-and-reload) exactly: `sendRecipes(vftv:infusion)` on join and `/reload`; built at server start and rebuilt before sending when the recipe manager changes; replaced whole, with a **generation** counter (+1 per replacement) that arms compare; **`MAX_INFUSION_RECIPES` = 4,096**, applied as a set (over it, startup fails; on `/reload` the snapshot is empty and an error is logged); and the index by simple centre item plus one id-ordered list of non-simple centres.

**A running infusion ignores reloads:** its plan holds everything it needs (see "The plan").

## Enchanting through infusion

An enchanting recipe is a `vftv:infusion` body with the enchantment result. Enchantments are data-driven: the `Registries.ENCHANTMENT` registry of `Holder<Enchantment>`s (`MC:world/item/enchantment/Enchantment.java:60,71`).

**Conditions** on the centre copy, in order; the first failure is the refusal reason:

| # | Condition | Reason | Lang key | Args |
|---|---|---|---|---|
| 1 | not in the item tag **`vftv:infusion_unenchantable`** (default `minecraft:book`, `minecraft:enchanted_book`), and `EnchantmentHelper.canStoreEnchantments` (`MC:world/item/enchantment/EnchantmentHelper.java:81–83`; `updateEnchantments` silently returns `EMPTY` otherwise, `:67–79`) | `UNSTORABLE` | `status.vftv.infusion.enchant.unstorable` | 1: the item |
| 2 | `ItemStack#supportsEnchantment(holder)` (`NF:common/extensions/IItemStackExtension.java:109–113`, the replacement for the deprecated `isSupportedItem` and `canEnchant`, `Enchantment.java:122–150`) | `UNSUPPORTED` | `.unsupported` | 2: the enchantment, the item |
| 3 | the current level `L` (from `EnchantmentHelper.getEnchantmentsForCrafting`, `EnchantmentHelper.java:89–91`) is below `min(max_level, getMaxLevel())` (`Enchantment.java:164`) | `AT_MAX` | `.at_max` | 2: the enchantment, the cap |
| 4 | compatible with every **other** enchantment on the item, by `Enchantment.areCompatible` (`:181`). The helper `isEnchantmentCompatible` isn't used: it would reject the holder against itself (`EnchantmentHelper.java:602–610`). | `CONFLICT` | `.conflict` | 2: the enchantment, the first conflicting one in ascending id |

- **Output,** computed at the commit and frozen: a count-one copy of the centre with `EnchantmentHelper.updateEnchantments(copy, m -> m.set(holder, L + 1))` (`ItemEnchantments.Mutable#set` caps at 255, `MC:world/item/enchantment/ItemEnchantments.java:131`). **One level per infusion.**
- **Cost:** `essentia[a] × (L + 1)` for each aspect. **Instability:** `R = min(100, instability + enchant.instability_per_level × Σ levels already on the item)`. **No XP:** essentia is this mod's currency.
- **Vectors** (reference implementation): per level {Praecantatio 4, Telum 6}, `instability` 10: at `L` 0 the cost is {4, 6} for level 1 and `R` 10; at `L` 2 with 3 levels on the item in all, {12, 18} for level 3 and `R` 25.
- `supportsEnchantment` is an item callback, so it runs in the assessment and again in the commit's recheck, never inside the commit.
- **Built-in lint:** every built-in enchanting recipe's enchantment is in `#minecraft:in_enchanting_table` (`MC:tags/EnchantmentTags.java:18`), so no treasure or curse. Packs are free. No enchantment of our own in v1.
- **Options considered:** XP costs (as TC4 did); levels beyond vanilla's maxima. **Why not:** XP is vanilla's economy and essentia is ours; vanilla's maxima keep the anvil and the table coherent.

## The run

### The plan

The commit writes the **whole plan** before anything is published. A run never reads its recipe, the stabiliser or unenchantable tags, or a tuning value again; the only thing it reads afresh is the box's vessels, through the endpoint tag, at each draw.

| Part | Content |
|---|---|
| identity | `seq`, `seed`, the starter's UUID, the recipe id, `kind` |
| output | the frozen output stack |
| components | in absorb order (ascending signed `asLong` of their pedestals): the offset (dx, dz), the **promised stack** (a count-one copy), `done` |
| essentia | `costs` (effective) and `drawn`, aspect → amount; `drawn[a] ≤ costs[a]` |
| stability | `R`, `L`, `I`, `P`, `B`, `Wp`, `S`, `p` |
| rules | as they were at the commit: `run.interval`, `run.rate`, `symmetry.lone_pedestal`, `symmetry.lone_item`, `stabilise.per_pair`, `stabilise.max_pairs`, `upset.max_chance`, `upset.max`, `upset.spill`, `upset.jolt_weight`, `upset.spill_weight`; #26's flux rules record (absent while #26 hasn't landed). The stored stability is validated **only** against these stored values, never against the tuning in force. |
| counters | `rolls` (0 … `ceiling`, so ≤ 18,016 at the tuning maxima) and `upsets` (0 … `upset.max` ≤ 64). **Rolls stop at the cap:** once `upsets` = `upset.max`, no further roll is drawn and `rolls` stops advancing. |
| abandoning | the player and deadline, while armed |

**Owed** essentia for an aspect is `costs[a] − drawn[a]`. A **pending** component is one not `done`.

### Scheduling

A per-level **step queue** of entries `(due tick, signed bowl asLong, bowl id, seq)`, ordered by due tick then position. Nothing scans every bowl, and an idle bowl costs nothing.

- **Phase:** a ringing bowl is due on ticks where `gameTime mod run.interval = phase(pos)`, the [crucible's phase](crucible.md#boiling-and-the-step) with the frozen interval. **Vectors** (reference implementation) at 10: (0, 64, 0) → 5; (1, 64, 0) → 0; (0, 64, 1) → 4; (−37, 70, 512) → 9; (−1, −64, −1) → 3; (29999999, −64, −29999999) → 9.
- **Entered** at the commit and by the bowl's `onLoad` when it has a run; **left** by `setRemoved`, the run's end, or a stale pop (the bowl at the position is gone, or its id or `seq` differs).
- **Processing,** at the end of each level tick: entries due at or before now pop in order; at most **`MAX_STEPS_PER_TICK` = 16** steps run per level. Every pop counts toward **`MAX_QUEUE_POPS_PER_TICK` = 64** (stale entries included). Leftovers keep their original due tick, so overdue bowls go first next tick.
- **The arm queue** is the same shape, keyed `(due tick, player UUID)`, for arm and run-subscription refreshes: at most 64 pops per level per tick, stale entries (an ended arm) included, leftovers keeping their due tick.
- **The shared assessment allowance:** strikes, commit attempts and `ARMED` refreshes each spend one of **`MAX_ASSESSMENTS_PER_TICK` = 16** per level per tick. Run-subscription refreshes read only the frozen plan and counters and spend none. A strike or commit attempt arriving while the allowance has room is serviced at once, inside its use; otherwise it waits in the pending queue (see "The second strike").
- **Order at the end of each level tick:** first the pending assessments (FIFO, expired ones dropped), then the arm queue's refreshes in due order, both drawing on the allowance; then the step queue. Pending player actions go first, so continuous strikes can't starve each other, and refreshes resume as soon as players stop. Every pending assessment is serviced or expired within `ASSESS_TTL`.

### A step

1. **Nothing happens** (the bowl is rescheduled at its next phase tick) when: the record is dormant; abandoning is armed and not expired; a run operation is already open on the bowl (re-entry from a listener); or a box chunk isn't loaded and block-ticking (**paused**).
2. **Open the operation.** While it is open the bowl's item handler is unavailable, and a removal is deferred to its close.
3. **Act,** first match: something is owed → **draw**; a component is pending → **absorb**; otherwise → **finish** (no roll).
4. **Roll** after a rolled step (see "Upsets").
5. **In a `finally`, on every path** (including exceptions from capability discovery, a transfer, a spawn or a listener): close the operation; resolve a deferred removal exactly once; mark the bowl changed (`setChanged`) and for the coalesced sync if anything persistent changed; play the step sound. An exception is logged once with the position; it never makes the scheduler retry a move that committed.

### Drawing essentia

**Direct pulls from the vessels in the box.** No tube connects to the bowl; a line feeds a jar inside the box instead.

- **Sources,** found afresh at every draw step by reading the 324 positions: positions whose state is in `vftv:essentia_endpoints` and whose block entity exposes the essentia capability on the **null side**, in ascending signed `asLong`, the first **`MAX_SOURCES` = 32** that have one. Finding them may query the capability at **every one of the 324 positions**, when earlier tagged blocks expose none. A handler with more than the essentia spec's `MAX_HANDLER_SLOTS` (128) slots is skipped. A crucible counts (its face is extract-only); an Alembic has no handler. Re-reading every step sees new and replaced vessels with no topology cache.
- **Transaction:** if `Transaction.getLifecycle() != Lifecycle.NONE`, the step is deferred to the next tick with its due tick kept ([essentia, Transactions](essentia.md#transactions)). Otherwise **one root**, closed within the call:
  - `need = min(run.rate, owed[X])` for each owed aspect X in ascending id, tried in turn; for each source in order, a nested transaction `e = extract(X, need)`, committed; `need −= e`. **The first X that yields anything ends the search.**
  - **The ledger** (`drawn`) is a `SnapshotJournal` in the platform adapter, enlisted in the same root, and the loader-free core applies the credit. A root abort restores both the vessels and the ledger exactly.
  - **Failures are classified by the ledger, which is the record of truth:** compare `drawn` before the root with `drawn` after the call returns or throws.
    - **Before the commit** (an extract throws, the root is aborted by try-with-resources): the journals revert, the ledger shows no credit, nothing rolls, and the step is a stall.
    - **After the commit:** NeoForge runs root-close callbacks after journals have committed, and one can throw (`NF:transfer/transaction/Transaction.java:215–240`; `NF:transfer/transaction/TransactionManager.java:120–135`). The ledger then shows the credit, which **stands**: the step counts as a rolled draw and rolls, the exception is logged once, and that move is never retried or refunded.
- **Suction:** the bowl offers none and is no endpoint. **Direct pulls don't route like a line**, so they inherit no line throughput and apply no suction rule ([essentia, Contracts](essentia.md#contracts-on-sibling-specs)); the rate is `run.rate`.
- **Partial draws are legal:** a draw that takes one unit is a rolled step. "Counting the run" is built for this.

### Absorbing

The first pending component, in absorb order. The pedestal at its offset must be readable, not busy, and holding a stack that `isSameItemSameComponents` with the promised stack. Then, by its claim (see "Claims"):
- **this run's live claim:** absorb;
- **no claim, or a dead one** (a replacement pedestal, or a claim cleared by its own failure): **claim it for this run first** (`{bowl position, bowl id, seq}`), then absorb, in the same call;
- **another bowl's live claim, or an unknown or unresolved one:** stall, changing nothing.

Absorbing empties the slot (a direct write; no callbacks run), marks the component `done`, clears the pedestal's claim, marks both block entities changed, sends 8 `ItemParticleOption(minecraft:item, promised)` particles up from it, and plays `infusion.absorb` there. Any other case is a **stall** at that component: the readout names its position, and nothing rolls.

### Upsets

After each rolled step, if `upsets < upset.max`:

```
seed = fold([0x494E4655 ("INFU"), server.overworld().getSeed(), bowl pos.asLong(), seq])     the research spec's fold
k    = rolls; rolls = k + 1
u_j  = mix64(seed + (3k + j + 1) · 0x9E3779B97F4A7C15)     j = 0 roll, 1 kind, 2 target; mod 2⁶⁴
upset iff Long.remainderUnsigned(u_0, 1000) < p
kind  = Long.remainderUnsigned(u_1, jolt_weight + spill_weight) < jolt_weight ? JOLT : SPILL
```

On an upset, `upsets + 1` **whatever its effect**, then:

| Kind | Effect | Fallback |
|---|---|---|
| **JOLT** | the **eligible** components are the pending ones whose pedestal is readable, not busy, **holds this run's live claim**, and holds the promised stack, in absorb order; the one at `Long.remainderUnsigned(u_2, count)` pops off. Its pedestal's block-entity instance is noted. A new `ItemEntity` with a copy is created at the pedestal's (x + 0.5, y + 1.0625, z + 0.5), velocity (0, 0.25, 0), default pickup delay, and added with `addFreshEntity`. **Then everything is revalidated:** the call returned `true` without throwing; the entity is alive and holds exactly the copy (`ItemStack.matches`); the pedestal is the noted instance, not removed, still at its position, not busy, still holding this run's live claim and the promised stack; and the bowl still has this run (the same `seq`). Only then is the pedestal's slot emptied (and marked changed). Otherwise our entity is discarded if alive, nothing else changes, and the effect is `NONE` ([crucible, Committing a craft](crucible.md#committing-a-craft)). The component stays pending, and its claim stays, so the run stalls at it until it is restored. | none eligible → SPILL |
| **SPILL** | X = the aspect with the largest `drawn`, ties to the lowest id; `amount = min(drawn[X], upset.spill)`; **`drawn[X] −= amount` first**, so it is owed again; then `InfusionEvent(SPILLED)` with cause `UPSET`, `{X: amount}` and the run's frozen flux rules | nothing drawn → JOLT; neither possible → `NONE` |

- **Every upset posts `InfusionEvent(UPSET)`** with its effect (`JOLT`, `SPILL` or `NONE`), plays `infusion.upset` and sends 8 `minecraft:smoke` particles above the bowl.
- **Never:** the centre item, health, warp, or any block. A jolted item can be lost to lava or picked up by anyone (the book says so).
- **Bounded:** at most `upset.max` upsets per run; the rolled steps never exceed `ceiling`; the flux from upsets is at most **`upset.max × essentiaFluxBound(upset.spill, UPSET, rules)`**. A bound on one map of `upset.max × upset.spill` would not do: a conversion that rounds per event or carries a fixed overhead can exceed it. **Vector** (reference implementation, with a stand-in conversion `ceil(total / 3) + 1`): one map of 24 → 9; six spills of 4 → 18; the sequence bound → 18.
- **Determinism:** `seq` grows with every start and is part of the plan, so waiting never rerolls. Predicting the draws needs the world seed, which a server keeps. The first start on a new bowl has `seq` 1.
- **Vectors** (reference implementation; world seed `0x0123456789ABCDEF`): seeds for (0, 64, 0) at `seq` 1 → `0x3B659D37E1C47D8A`, `seq` 2 → `0x39537E0E88BC9C45`; (−37, 70, 512) at `seq` 6 → `0x851F505AB167CA66`. With the first, `p` 150, weights 1 : 1 and 5 eligible components, the first six upsets are at rolls 5 (r 136, JOLT 3), 8 (r 89, JOLT 0), 11 (r 141, JOLT 4), 21 (r 146, SPILL), 32 (r 4, JOLT 3) and 62 (r 74, JOLT 0); 148 ‰ of 10,000 rolls are upsets.
- **Options considered:** a deterministic strain counter whose outcome the readout states in full; TC4's instability that grows during a run and with stalls. **Recommend** seeded odds with an exact chance, a true clean floor and a hard worst case. **Why:** players love infusion's chaos (pain points, "loved" §5) but hate opaque spirals (§9); the TC4 alchemy research says stall debt is what snowballs, and here stalls cost nothing.

### Finishing

1. With the operation open: the bowl's item becomes the frozen output; the run record is cleared, so every claim of it is dead; the bowl leaves the step queue. **Payloads are captured** (copies) now.
2. Close the operation; mark for sync; play `infusion.finish` and send 12 `minecraft:enchant` particles around the bowl.
3. **Notifications,** each exactly once and each caught separately so the remaining ones still run (a throwing NeoForge post is logged; its own remaining subscribers are NeoForge's business):
   1. `InfusionEvent(COMPLETED)`;
   2. if the starter is online, alive and in this level: `onCraftedBy(player, 1)` on a copy, NeoForge's `PlayerEvent.ItemCraftedEvent` through the platform seam with a copy and a defensive one-slot container holding the centre's copy, and `CriteriaTriggers.RECIPE_CRAFTED` with the recipe key.

- **Offline completion** posts `COMPLETED` (which carries the starter's UUID) and **omits** statistics, `ItemCraftedEvent` and the criterion. So **no progression may require an infusion craft criterion** (see the contracts on #36 and #27).
- **A committed run is never refunded, retried or reported as failed.**

### Stalls, pauses and interruption

| Case | Result |
|---|---|
| nothing drawn, or a component missing | a **stall**: no roll and no cost growth; retried at the next phase tick. Waiting does no harm. |
| a box chunk unloaded or not block-ticking | **paused**; the record persists; on load it resumes from its counters, with no catch-up |
| the starter logs out, dies or leaves | nothing: the altar runs on its plan |
| research revoked | nothing (the start was the commit) |
| recipe, tag or tuning `/reload` | nothing: the plan is frozen |
| a pedestal broken | it drops its item through vanilla; its component, if pending, stalls until a pedestal at that offset holds the promised stack again. A new pedestal there is unclaimed, so it accepts the item, and the run claims it for itself before absorbing; if another bowl has claimed it meanwhile, the run stalls without touching it (see "Absorbing") |
| the bowl removed | see "Removal" |
| a record unreadable at load | dormant (see "Sync and state") |

### Abandoning

**Empty-handed use on a ringing bowl:**
1. **First use** (no abandon armed, or armed by this player and expired): arm `{player, deadline = now + abandon.window, flux-rule generation}`. **The run pauses**, so `drawn` can't change. The action bar shows `message.vftv.infusion.abandon_confirm` with the exact `essentiaFlux(drawn, ABANDONED, rules)` under #26's current rules.
2. **Second use** by the same player before the deadline, with #26's flux rules unchanged: **abandon**. If the rules changed, step 1 runs again with the new value.
3. **Another player's** empty-handed use while it is armed is refused: `message.vftv.infusion.abandon_pending`.
4. **The deadline passing** disarms it; the run resumes.

**Abandoning,** with the operation open: copy `drawn` into the spill and clear the run (claims die with it; unabsorbed components stay on their pedestals, unclaimed; absorbed ones are gone; **the centre stays in the bowl, unchanged**); leave the step queue; close the operation; then, caught separately: `InfusionEvent(SPILLED)` with cause `ABANDONED` and the shown rules, `InfusionEvent(ABANDONED)` with cause `PLAYER`, and `infusion.abandon`.

### Removal

**`BlockEntity#preRemoveSideEffects`** on the bowl:
- **an operation is open** or **the item handler is transaction-busy:** record one pending removal; it runs when the operation or the root closes;
- **dormant:** nothing is spilled and a warning names the position; vanilla drops what it can;
- **a run:** copy `drawn`, clear the run, then post `InfusionEvent(SPILLED)` with cause `REMOVED` under #26's current rules and `InfusionEvent(ABANDONED)` with cause `REMOVED`, each caught separately, **then** the default drop runs in a `finally`, so cleanup happens exactly once whatever a listener throws.

**On a pedestal:** the default drop, deferred to the root's close while transaction-busy.

**Loss exceptions** (stated, and taught in the book): the drop is vanilla's `Containers.dropItemStack`, which splits the stack into item entities and spawns them **without checking the result** (`MC:world/Containers.java:32–47`), so a cancelled spawn loses the item; and a removal with block-update flag 256 skips `preRemoveSideEffects`, losing the item, the run and its spill without an event, as for the crucible and jars ([crucible, Removal](crucible.md#removal)). Apart from these, **the centre item is never lost**: no upset, stall, pause, abandonment or reload consumes it.

### Claims

A pedestal's **claim** is `{bowl position, bowl id, seq}`. Every interaction, insert, extract, assessment and commit validates it lazily, without loading anything:

| Bowl's chunk | Finding | Claim |
|---|---|---|
| not loaded (`getChunkNow` is null) | **unknown** | kept; taking is locked; a new claim is refused (`PROMISED`) |
| loaded | the block entity there is a **dormant** bowl, whose id and run can't be read | **unresolved**: kept; taking is locked; a new claim is refused (`PROMISED`) until its pack is restored and the bowl can be validated |
| loaded | the block entity there is a readable bowl with this id, ringing with this `seq`, and its plan has a pending component at this offset | **live** |
| loaded | anything else (no bowl, another id, another `seq`, no run, or that component `done`) | **dead**: cleared, marked changed and for sync |

The pedestal lies in its bowl's box, so the bowl is at most 4 blocks away and the check is one chunk lookup and one block-entity read. Unknown and unresolved claims are never cleared, because their bowl may simply be paused or waiting for its pack. **Every mutation by a run** (absorbing, jolting) requires this run's live claim, or first takes an unclaimed or dead pedestal for itself (see "Absorbing"); it never touches a pedestal whose claim is foreign, unknown or unresolved.

## The Echo rod and cap

The Stage 3 wand tier, added as data with no code, as the [wands spec](wands.md#parts) assigns to this spec.

| Part | Registry | Item | Value |
|---|---|---|---|
| rod `vftv:echo` | `vftv:wand_rod` | `vftv:echo_rod` (stacks to 64) | capacity 300 |
| cap `vftv:echo` | `vftv:wand_cap` | `vftv:echo_cap` (stacks to 64) | `cost_multiplier` 0.70 |

- **Made by infusion:** `vftv:echo_rod` (centre `vftv:chime_rod`) and `vftv:echo_cap` (centre `vftv:resonant_brass_cap`), research `vftv:echo_wands`, `kind` `ordinary`, `instability` 5–25. **These item ids are frozen here.**
- **Assembly** is the wands spec's unchanged `vftv:wand_assembly`: any rod with any matching pair of caps.
- **Model:** two cases are appended to `assets/vftv/items/wand.json` (see the contracts on #17 and #38).
- **Reachable:** a reviewed bootstrap (see "Authored content, The Echo bootstrap") proves that every centre, component and essentia aspect of both recipes is obtainable in Stages 1–3 without an Echo part.

## The authoritative preview

**`InfusionPreview.of(player, recipe, context)`** in `core.infusion` is the **only** cost source for the book's `vftv:recipe` pages, the fact fields and JEI.

**The context** is the player's current readout for the bowl that is the client's block hit, within reach, **whose recipe id is this one**: either an `ARMED` readout with status `READY`, or a `RUN` readout of a run of this recipe (the run readout carries its recipe id). **Identity:** kind, dimension, position and revision. **Freshness:** the arm or run subscription hasn't been cleared. With none, the context is absent.

| Field | Value |
|---|---|
| `recipe`, `kind` | the recipe |
| `output` | item: the created result (transmuted: "the centre, transmuted to …"); enchanting: with a context, the centre at `L + 1` (its target level); without, "+1 level, up to *n*" |
| `inputs` | the centre and the components, as slot displays |
| `essentia` | with a context: the effective cost (a run: its frozen costs); without: the recipe's cost, per level for enchanting |
| `instability` | without a context: the recipe's own (enchanting: its base, labelled "before levels already on the item"); with one: `R` |
| `stability`, `chance` | from the context (a run: the frozen values); **absent** without one |
| `clean_floor`, `clean_full_supply` | `ARMED` context: the two clean figures; absent otherwise (a run already under way has no clean figure) |
| `upset_flux` | `ARMED` context: the upset flux bound; `RUN`: the bound under the run's frozen rules; absent without a context |
| `abandon_flux` | `RUN` context only: `essentiaFlux(drawn, ABANDONED, rules)` under #26's current rules; absent otherwise |
| `stabilisers_capped`, `clamped` | from the context's readout (booleans); absent without one |
| `availability` | first match: `NO_ALTAR`; `RINGING` (a `RUN` context); the context's non-`READY` status; `SHORT_NOW` (a cost aspect held short now; informational, a start is still allowed); `READY` |
| `altar` | the context's dimension and position; absent iff `NO_ALTAR` |

- **Absent is never a value:** without a context no stability, chance, clean figure, flux, flag or availability is shown, and every place that would show one says why ([crucible, The authoritative preview](crucible.md#the-authoritative-preview)).
- **Identity is visible:** the book's recipe page and JEI show `infusion.vftv.checked_at` (x, y, z) whenever there is a context.
- **Live dependencies:** a preview is recomputed when a readout (of either kind) arrives or clears, the targeted block changes, the recipe snapshot is replaced, `InfusionRules` arrives, the learned set or discovered aspects change, the resources change, or the player disconnects (every cache is cleared at logout). Warp reaches it through the readout, which the server re-sends when `Wp` changes. Caches may keep layout, **never an old value**.
- **The preview decides nothing.** The commit decides, against the live altar.

**Fact fields,** `{fact:recipe:<ns>:<path>:<field>}`:

| Field | Renders |
|---|---|
| `output` | the output's name ("*n* × " never applies: the count is 1); enchanting without a context: `infusion.vftv.fact.enchant` (2: the enchantment, the cap) |
| `inputs` | "1 × *centre*" then each component as "1 × *name*", of the alternative each lists first, joined by ", " |
| `essentia` | "*Aspect* *n*" in ascending id, joined by ", "; enchanting without a context followed by `infusion.vftv.fact.per_level`; unheard as `infusion.vftv.unheard` with the exact *n* |
| `instability` | "*n*" |
| `stability` | "*n*%" |
| `chance`, `clean_floor`, `clean_full_supply` | "*n*.*d*%" |
| `clean` | an alias of `clean_floor`, the guarantee |
| `upset_flux`, `abandon_flux` | "*n*" |
| `flux` | an alias of `upset_flux` |
| `stabilisers_capped`, `clamped` | `infusion.vftv.fact.yes` or `infusion.vftv.fact.no` |

Every field that is absent renders the **absent text** for the preview's availability: `infusion.vftv.status.no_altar` without a context, `infusion.vftv.status.ringing_only` for `clean_floor` and `clean_full_supply` on a run, `infusion.vftv.status.not_ringing` for `abandon_flux` on an armed readout. It never renders a number.
| `status` | `NO_ALTAR`: `infusion.vftv.status.no_altar`; `SHORT_NOW`: `.short_now`; `READY`: `.ready`; otherwise the readout status's own `status.vftv.infusion.*` key and arguments |

**Where they show:** the book's recipe page draws `output`, `inputs`, `essentia`, `instability` and, with a context, `checked_at`, `stability`, `chance`, `clean_floor`, `clean_full_supply`, `upset_flux` (and `abandon_flux` for a run) and both flags; JEI draws the same through its status line and tooltip (see "JEI").

## JEI

A soft dependency, never on the progression path. Every JEI class lives in `…neoforge.compat.jei.client`.

- **One category** for `vftv:infusion`, 160×146: the centre in an 18-px slot at (59, 38); up to 16 components on a ring about it, component *i* of *n* at (59 + round(36 sin θ), 38 − round(36 cos θ)) with θ = 2π*i*/*n*; the result at (134, 38); an instability line at (0, 82), clipped to 160 px with "…" and complete in its tooltip; the **cost panel** at (0, 96), 4 × 2 cells of 40×18 under the shared cost-cell rule ([arcane crafting, The screen](arcane.md#the-screen)); a status line at (0, 136): `infusion.vftv.checked_at` with a context, else the absent availability's text, clipped likewise; its tooltip carries the context's complete coordinates, `stability`, `chance`, both clean figures, `upset_flux` (and `abandon_flux` for a run) and both flags, in the fact formats. Enchanting shows the enchanted book and the line `infusion.vftv.fact.enchant`. Title `jei.vftv.category.infusion`. The bowl item is its catalyst. **No transfer handler.**
- **Reconcile:** the arcane spec's shared reconcile function, unchanged, with the infusion snapshot and the learned set ([arcane crafting, JEI](arcane.md#jei)).
- **Affordability** is marked against the preview: absent or stale contexts are marked unavailable, never affordable.

## Multiplayer and automation

- **Unowned.** The gate belongs to whoever starts; any permitted player can strike, load, abandon or break. Claim mods guard every hand path through `RightClickBlock` and breaking through `BreakBlockEvent`, and **both strikes** pass through vanilla's use.
- **Untrusted input:** one C2S payload, `InfusionSeen`, bounded at decode; it records an acknowledgement and authorises nothing. Starting happens only inside a permitted vanilla use (see "Starting"). Each check has a GameTest that sends bad input.

| Path | Behaviour |
|---|---|
| Players | every hand path above |
| Hoppers | load pedestals and bowls, restore promised stacks, and take finished outputs, through the container path |
| Other mods' item pipes | through the registered item capability, under the same rules |
| [Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23) | may load and restore like hoppers |
| Starting | **only a player**, by a second permitted strike: no automated start in v1 |
| Pistons | refused |

## Rendering and sound

**The held item** (block-entity renderers for both blocks, through vanilla's item model resolver and render state; nothing allocated per frame):

| Block | Position (block space) | Display context | Scale | Motion |
|---|---|---|---|---|
| bowl | (0.5, 0.75, 0.5), resting in the bowl | `ItemDisplayContext.FIXED` | 0.5 | turns 2° per tick about Y; while ringing it also rises 0.125 and bobs ±1/32 with period 80 ticks |
| pedestal | (0.5, 1.0, 0.5), above the top plate | `FIXED` | 0.5 | turns 2° per tick about Y |

At scale 0.5 a sword or a block (8 px across) fits inside the bowl's 12-px rim and above the pedestal's 10-px plate. Rotation and bob use the client's game time and partial tick only.

**Particles:** while ringing, `animateTick` sends one vanilla `minecraft:enchant` particle from each pending, present pedestal (from the bowl's synced view) toward the bowl, at most 16 per call; client randomness, nothing depends on it. Absorb, upset and finish bursts are as above, sent by the server with `sendParticles`.

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:infusion.strike` | 3 | every strike | at the bowl, to everyone in range; `SoundSource.BLOCKS`, volume 1.0; pitch **0.94 + 0.12 × S / 100** for a `READY` readout, else 1.0 |
| `vftv:infusion.begin` | 1 | a commit | likewise, pitch 1.0 |
| `vftv:infusion.draw` | 3 | a draw step that drew | likewise |
| `vftv:infusion.absorb` | 3 | an absorb | at the pedestal, likewise |
| `vftv:infusion.upset` | 3 | an upset, any effect | at the bowl, likewise |
| `vftv:infusion.finish` | 1 | a finish | likewise |
| `vftv:infusion.abandon` | 1 | an abandonment or a removal with a run | likewise |
| `vftv:infusion.ring` | 3 | ambient, while ringing | client-only (`playLocalSound`), `SoundSource.BLOCKS`, volume 0.6, pitch 1.0, at most once per bowl per 40 ticks |

- **One step sound per step:** `absorb` or `draw`; `upset` in addition when one happens.
- Placing and taking reuse vanilla's `minecraft:entity.item_frame.add_item` and `remove_item`.
- **Files:** `assets/vftv/sounds/infusion/strike_0.ogg` … `strike_2.ogg`, `begin.ogg`, `draw_0` … `draw_2`, `absorb_0` … `absorb_2`, `upset_0` … `upset_2`, `finish.ogg`, `abandon.ogg`, `ring_0` … `ring_2`, in the Stage 3 register ([art style §7](art-style.md#7-sound-palette)): a lower fundamental, more reverb, a dimmed clang. `sounds.json` entries carry no volume or pitch overrides and name `subtitles.vftv.infusion.<event>`.

## Authored content

The content ticket delivers authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails its type's rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/infusion/recipes.json        the arcane, Echo and enchanting recipes this spec owns
content/infusion/wand_parts.json     the Echo rod and cap
content/infusion/multiblock.json     the altar the book shows
content/infusion/echo_bootstrap.json the reviewed Echo sources
content/lang/en_us/infusion.json     every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `recipes.json` | `vftv:infusion_recipes/1` | each recipe `<ns>:<path>` → `data/<ns>/recipe/<path>.json`, its body unchanged |
| `wand_parts.json` | `vftv:wand_parts/1` (the [wands schema](wands.md#parts)) | `data/vftv/vftv/wand_rod/echo.json` and `data/vftv/vftv/wand_cap/echo.json`; a part id declared in two `content/` files fails datagen |
| `multiblock.json` | `vftv:multiblocks/1` | each multiblock `<ns>:<path>` → `assets/<ns>/multiblock/<path>.json` (format below) |
| `echo_bootstrap.json` | `vftv:echo_bootstrap/1` | nothing: read by the review and a lint |
| `lang/<lang>/infusion.json` | `vftv:lang/1` | its `strings` merged into `assets/vftv/lang/<lang>.json`; a duplicate key across all `content/lang` files fails datagen |

**Other specs' infusion recipes** live in their own content files with the recipe type's body, under their own `research`. A recipe id declared in two `content/` files, of any type, fails datagen.

### Recipes

`{"schema": "vftv:infusion_recipes/1", "recipes": {<id>: <body>}}`. The ids, and the type each must have:

| Id | Body | Rules |
|---|---|---|
| `vftv:singing_bowl`, `vftv:pedestal` | [`vftv:arcane`](arcane.md#the-recipe-type) | `research` `vftv:infusion`; `kind` `ordinary` (or omitted); result the block item, count 1; ingredients reachable in Stages 1–3 without infusion; each `vis` value ≤ the `vftv:chime` rod's capacity in `content/wands/parts.json` (the lint reads it) |
| `vftv:echo_rod`, `vftv:echo_cap` | `vftv:infusion` | `research` `vftv:echo_wands`; `kind` `ordinary`; centres `vftv:chime_rod` and `vftv:resonant_brass_cap`; item results `vftv:echo_rod` and `vftv:echo_cap`; `transmute` omitted or false; `instability` 5–25; every item and aspect covered by the bootstrap |
| `vftv:infused_enchanting/<path>`, 6–10 of them | `vftv:infusion` | `research` `vftv:infused_enchanting`; `kind` `ordinary`; result `{"enchantment": "minecraft:<path>", …}` with the enchantment in `#minecraft:in_enchanting_table` and `max_level` ≤ its `getMaxLevel()`; `instability` 0–30; ids distinct |

```json
{"schema": "vftv:infusion_recipes/1", "recipes": {
  "vftv:pedestal": {
    "type": "vftv:arcane", "research": "vftv:infusion", "kind": "ordinary",
    "ingredients": ["minecraft:polished_deepslate", "minecraft:polished_deepslate", "vftv:resonant_brass_ingot"],
    "result": {"id": "vftv:pedestal", "count": 1},
    "vis": {"vftv:terra": 20, "vftv:ordo": 10}},
  "vftv:infused_enchanting/sharpness": {
    "type": "vftv:infusion", "research": "vftv:infused_enchanting",
    "centre": "#minecraft:enchantable/sharp_weapon",
    "components": ["minecraft:flint", "minecraft:flint", "minecraft:quartz", "minecraft:quartz"],
    "essentia": {"vftv:telum": 6, "vftv:praecantatio": 4},
    "instability": 10,
    "result": {"enchantment": "minecraft:sharpness", "max_level": 5}}
}}
```

**Rejected:** an id outside the table or a required id missing; a body of the wrong type; any field outside its type's table; any rule above broken; a created result that is empty or over its maximum stack.

### Wand parts

`{"schema": "vftv:wand_parts/1", "rods": {"vftv:echo": …}, "caps": {"vftv:echo": …}}`, exactly these two, with the shipped values:

```json
{"schema": "vftv:wand_parts/1",
 "rods": {"vftv:echo": {"item": "vftv:echo_rod", "capacity": 300}},
 "caps": {"vftv:echo": {"item": "vftv:echo_cap", "cost_multiplier": 0.70}}}
```

**Rejected:** any other id; anything that fails the wands spec's parts load validation.

### The altar multiblock

**Input:** `{"schema": "vftv:multiblocks/1", "multiblocks": {<id>: {"key": {<char>: <block state>}, "layers": [[<row>, …], …]}}}`, exactly one id, `vftv:infusion_altar`.
- **A block state** is vanilla's block-state syntax, `<block id>[<property>=<value>,…]` with the brackets optional: no NBT and no tags. Every property named must exist on the block.
- **Layers** run bottom-up; in each, rows run north to south (−z to +z) and characters west to east (−x to +x). Every row has the same length; width and depth are odd and ≤ 9; ≤ 4 layers. The character `.` is air and may not be keyed; every other character used must be keyed.
- Exactly one character maps to `vftv:singing_bowl`, used exactly once, at the centre column of its layer.

**Generated** `assets/vftv/multiblock/infusion_altar.json`: `{"format": 1, "size": [x, y, z], "bowl": [x, y, z], "blocks": [[x, y, z, <block state>], …]}` with positions from the low corner, air omitted, in ascending (y, z, x). The research spec's `vftv:structure` page reads it.

**Lints:** the bowl's layer has the pedestals; placed in a GameTest with the bowl at the centre, the structure assesses with `L` = 0, `I` = 0 (pedestals empty) and `P` ≥ 1.

### The Echo bootstrap

`{"schema": "vftv:echo_bootstrap/1", "recipes": {<id>: {"items": [{"item": <id>, "why": <one line>}, …], "aspects": [{"aspect": <id>, "why": <one line>}, …]}}}`, for exactly `vftv:echo_rod` and `vftv:echo_cap`.

- **Coverage:** `items` lists the centre and every component item (for a tag, the alternative the review names), and `aspects` every essentia aspect, of that recipe.
- **Review:** each `why` says how the thing is obtained in Stages 1–3 without an Echo part, and for an aspect, which matter dissolves into it in a crucible ([aspects, Coverage and supply](aspects.md#coverage-and-supply)). The review checks the sequence is non-circular.

**Rejected:** another id; a missing centre, component or aspect; an empty `why`.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text, and its review checks that the messages teach striking twice, symmetry, matched stabilisers and their cap, upsets and their bound, waiting without harm, restoring a jolted component, abandoning and its price, and the loss exceptions.

| Key | Args | Sense |
|---|---|---|
| `block.vftv.singing_bowl`, `block.vftv.pedestal` | 0 | "Singing Bowl", "Pedestal" (also their items' names) |
| `item.vftv.echo_rod`, `item.vftv.echo_cap` | 0 | "Echo Rod", "Echo Cap" |
| `wand_rod.vftv.echo`, `wand_cap.vftv.echo` | 0 | "Echo rod", "Echo caps" |
| `status.vftv.infusion.unreadable`, `.unloaded`, `.empty`, `.busy` | 0 | "I can't read this bowl.", "Part of the altar is beyond my hearing.", "The bowl is empty.", "Something is moving through it." |
| `status.vftv.infusion.ringing`, `.ready` | 1 | "Ringing toward %s.", "It will make %s." |
| `status.vftv.infusion.too_many` | 1 | "Too many pedestals: %s." |
| `status.vftv.infusion.no_recipe` | 1 | "Nothing I know takes these %s offerings." |
| `status.vftv.infusion.pedestal_unreadable`, `.promised` | 3 | "I can't read the pedestal at %s, %s, %s.", "The pedestal at %s, %s, %s is promised elsewhere." |
| `status.vftv.infusion.enchant.unstorable` | 1 | "%s can't hold an enchantment." |
| `status.vftv.infusion.enchant.unsupported` | 2 | "%s won't take to %s." |
| `status.vftv.infusion.enchant.at_max` | 2 | "%s is already at %s." |
| `status.vftv.infusion.enchant.conflict` | 2 | "%s quarrels with %s." |
| `hud.vftv.infusion.ready`, `.ringing` | 1 | the header |
| `hud.vftv.infusion.stability` | 1 | "Stability %s" |
| `hud.vftv.infusion.breakdown` | 7 | "Recipe −%s, lone pedestals −%s, lone offerings −%s, warp −%s, stabilisers +%s (%s matched of %s)" |
| `hud.vftv.infusion.chance` | 2 | "Each step: %s chance of an upset; %s steps if every draw is full." |
| `hud.vftv.infusion.clean` | 2 | "A clean run: at least %s (%s if every draw is full)." |
| `hud.vftv.infusion.worst` | 3 | "At most %s upsets, each knocking an offering loose or spilling up to %s essentia: at most %s flux." |
| `hud.vftv.infusion.strike_again`, `.changed` | 0 | "Strike again to begin.", "It changed. Strike again." |
| `hud.vftv.infusion.progress` | 4 | "Drawn %s of %s; absorbed %s of %s." |
| `hud.vftv.infusion.frozen` | 2 | "Stability %s; each step %s." |
| `hud.vftv.infusion.upsets` | 2 | "Upsets: %s of at most %s." |
| `hud.vftv.infusion.missing` | 3 | "Waiting for the offering at %s, %s, %s." |
| `hud.vftv.infusion.paused`, `.waiting` | 0 | "Paused: part of the altar is beyond my hearing.", "Waiting for essentia. Waiting costs nothing." |
| `hud.vftv.infusion.abandon` | 1 | "Abandoning it now spills %s flux." |
| `hud.vftv.infusion.abandon_confirm` | 0 | "Take it out again to abandon." |
| `message.vftv.infusion.unreadable`, `.busy`, `.ringing`, `.strike_first`, `.stale`, `.abandon_pending`, `.too_busy` | 0 | refusals (`.too_busy`: "The altar is crowded with listeners; strike again.") |
| `infusion.vftv.fact.yes`, `infusion.vftv.fact.no` | 0 | "yes", "no" |
| `infusion.vftv.status.ringing_only`, `infusion.vftv.status.not_ringing` | 0 | "(already ringing)", "(not ringing)" |
| `message.vftv.infusion.promised` | 3 | "That is promised to the bowl at %s, %s, %s." |
| `message.vftv.infusion.abandon_confirm` | 1 | "Abandoning spills %s flux. Take it out again to abandon." |
| `infusion.vftv.unheard` | 0 | "something unheard" |
| `infusion.vftv.fact.enchant` | 2 | "%s, one level more, up to %s" |
| `infusion.vftv.fact.per_level` | 0 | " for each level" |
| `infusion.vftv.status.no_altar`, `.short_now`, `.ready` | 0 | "(no altar before me)", "Its vessels are short for now.", "Ready to begin." |
| `infusion.vftv.checked_at` | 3 | "Read at the bowl at %s, %s, %s." |
| `jei.vftv.category.infusion` | 0 | "Infusion" |
| `subtitles.vftv.infusion.strike`, `.begin`, `.draw`, `.absorb`, `.upset`, `.finish`, `.abandon`, `.ring` | 0 | "Bowl is struck", "Bowl begins to sing", "Essentia drawn", "Offering absorbed", "Altar falters", "Bowl falls silent", "Infusion abandoned", "Bowl sings" |

**Used, not declared here:** aspect, enchantment and item names.

**Rejected:** a key outside the table; a required key missing in `en_us`; a placeholder count that differs; an empty string; a duplicate key, including any other spec's key redeclared here.

## Events

**`InfusionEvent`**, a NeoForge post-event in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server **after the state it reports has been written**, through a publisher seam that `neoforge` implements. Payload records live in `api` and are **immutable**, captured before any listener runs: item stacks are copies, aspect maps are unmodifiable and in ascending id.

| Kind | Payload |
|---|---|
| `STARTED` | level and position; the bowl id and `seq`; the starter's UUID; the recipe id; the readout |
| `UPSET` | level and position; `seq`; the effect `JOLT` (the pedestal position and a copy of the item), `SPILL` (the aspect and amount) or `NONE` |
| `SPILLED` | level and position; the aspects spilled; the cause, `api`'s `SpillCause.UPSET`, `ABANDONED` or `REMOVED`; the flux rules to apply (the run's frozen ones for `UPSET`, the current ones otherwise; absent until #26 lands) |
| `COMPLETED` | level and position; `seq`; the starter's UUID; the recipe id; a copy of the output |
| `ABANDONED` | level and position; `seq`; the cause, `PLAYER` (with the player) or `REMOVED`; the drawn map |

- **Once per occurrence.** Nothing fires per draw, absorb, stall or pause.
- **A throwing listener** is logged once with the position; the state stands and our remaining notifications still run.

**Plain `core` services:** `InfusionPreview.of`; `InfusionAltars.readout(level, pos, player)`. Whether they are exposed in `api` is the API spec's call.

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| bowl | block entity `{"format": 1, "id": <long ≠ 0>, "runs": 0–2³¹−1, "item"?: <stack>, "run"?: <run>}` | trackers: the item, ringing, dormant, and the **view** (the pending offsets with a present flag, the aspect being drawn, units drawn and owed, components done and total), in `getUpdateTag`, then the coalesced update |
| pedestal | block entity `{"format": 1, "item"?: <stack>, "claim"?: {"bowl": <long>, "id": <long ≠ 0>, "seq": 1–2³¹−1}}` | trackers: the item, claimed, dormant |
| arms and run subscriptions (with seen revisions), pending assessments, the step and arm queues, the loaded set | per-level or per-player memory | the arm's readout to its player only |
| infusion rules | the tuning record and the fixed limits | **`InfusionRules`** S2C to every player through `OnDatapackSyncEvent` (join and `/reload`) |
| infusion recipes | the recipe manager; `InfusionRecipeSnapshot` | every player, as under "Recipe sync and reload" |

**`<run>`:** `{"seq", "seed": <long>, "starter": <uuid>, "recipe": <id>, "kind", "output": <stack>, "components": [{"at": [dx, dz], "item": <stack>, "done": <bool>}, …], "costs": {<aspect>: 1–1000}, "drawn": {<aspect>: 1–1000}, "stability": {"r", "lone_pedestals", "lone_items", "matched", "bonus", "warp", "s", "chance"}, "rules": {"interval", "rate", "lone_pedestal", "lone_item", "per_pair", "max_pairs", "max_chance", "max", "spill", "jolt_weight", "spill_weight"}, "flux_rules"?: <#26's record>, "rolls": 0–18,016, "upsets": 0–64, "abandon"?: {"player": <uuid>, "until": <long>, "flux_generation": <int>}}`.

- **Cross-field invariants,** checked **only against the run's own stored rules** and the format bounds, never against the tuning in force: 1–16 components with distinct offsets inside the box and off (0, 0), each stack count 1; 1–8 costs; `drawn`'s keys ⊆ `costs`' and `drawn[a] ≤ costs[a]`; `bonus = min(matched, max_pairs) × per_pair`; `s` = clamp(100 − `r` − `lone_pedestals` × `lone_pedestal` − `lone_items` × `lone_item` − `warp` + `bonus`, 0, 100); `chance` = ceilDiv((100 − `s`) × `max_chance`, 100); each stored rule within its tuning range; `max × spill` ≤ 10,000; `upsets ≤ max`; `rolls` ≤ the run's own `ceiling`; `1 ≤ seq ≤ runs`. A run started under one tuning therefore reopens valid under any other. Zero amounts are never written.
- **Disk:** every persistent mutation (a commit, a claim, an absorb, a jolt, a draw's ledger, a roll's counters, abandoning, a finish, a hand or automation slot change, a minted id) calls `setChanged` on each block entity it touched, in addition to marking it for the packet flush.
- **Formats.** A record with an unknown format, out of bounds, failing an invariant, or naming an aspect, item or enchantment the registries don't hold is **kept as its raw tag and written back unchanged**, with one log line, and the block entity is **dormant**: no hand use, no hopper or capability movement (the container path refuses and the handler is unavailable), no step, no claim validated as live, readout `UNREADABLE` or `PEDESTAL_UNREADABLE`. A dormant pedestal's claim can't be read, so the pedestal refuses new claims. A loaded **dormant bowl** leaves its pedestals' claims **unresolved** (see "Claims"): they are kept and refuse reassignment until the bowl can be validated, so restoring its pack can never resume a plan against offerings another bowl has claimed meanwhile. Removal drops nothing it can't decode and logs a warning. Restoring the pack restores it.
- **Coalesced updates:** every change marks the block entity in a per-level dirty set, flushed once per tick after the level ticks: at most one update packet per block entity per tick, by the [wands spec's flush](wands.md#sync-and-state). A stale entry never updates a replacement.
- **`InfusionRules`** is a fixed record: every tuning key and the `limits.*` values the research grammar lists (see the amendment). Client caches are invalidated when it arrives.
- **Bounded codecs:** both records as above; ids ≤ 256 characters; `InfusionReadout` and `InfusionSeen` as under "Arms and revisions"; `InfusionRules` fixed; the tuning file.

**Reloads:**

| Change | Effect |
|---|---|
| Recipes (`/reload`) | the snapshot is replaced (its generation +1); recipes re-sent; arms bump at their next refresh; previews recompute; JEI reconciles; running infusions unaffected |
| Tags (`/reload`) | arms re-read at their next refresh; running infusions read the endpoint tag at each draw (sources) and nothing else |
| Tuning (`/reload`) | a new record; `InfusionRules` re-sent; arms bump; **running infusions keep their frozen rules** |
| The aspect, research, enchantment or wand-part registries | need the world reopened |
| Resource reload | the HUD layout, names and multiblock display data rebuild |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/infusion.json`**, loaded on reload into an immutable record in `core.infusion` and written by datagen from that record's defaults. Every key is optional and defaults to the value below. An unknown key, a wrong type, a value out of range, `upset.jolt_weight + upset.spill_weight` = 0, or `upset.max × upset.spill` > 10,000 makes the file invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. **A new record applies to future starts only.**

| Key | Value | Range | Notes |
|---|---|---|---|
| `run.interval` | 10 ticks | 1–200 | between a run's steps |
| `run.rate` | 4 | 1–1,000 | most units per draw |
| `symmetry.lone_pedestal` | 6 | 0–100 | points per lone pedestal |
| `symmetry.lone_item` | 4 | 0–100 | points per lone item |
| `stabilise.per_pair` | 3 | 0–100 | points per matched pair |
| `stabilise.max_pairs` | 8 | 0–64 | pairs that count |
| `upset.max_chance` | 150 ‰ | 0–1,000 | the chance per rolled step at stability 0 |
| `upset.max` | 6 | 0–64 | upsets per run |
| `upset.spill` | 4 | 1–1,000 | most units per spill |
| `upset.jolt_weight`, `upset.spill_weight` | 1, 1 | 0–100 | |
| `enchant.instability_per_level` | 5 | 0–100 | points per level already on the item |
| `arm.ttl` | 1,200 ticks | 20–12,000 | after the last strike |
| `arm.refresh` | 10 ticks | 1–200 | |
| `abandon.window` | 100 ticks | 20–1,200 | to confirm |

**Other homes:**
- stabilisers and unenchantable items: the tags `vftv:infusion_stabilisers` and `vftv:infusion_unenchantable` (packs may extend them);
- costs, instability, kinds and gates: each recipe's data (`content/infusion/recipes.json`, other specs' files, packs);
- Echo values: `content/infusion/wand_parts.json`;
- the warp penalty: #25's data; the flux conversion: #26's.

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`InfusionLimits`** in `api`:

| Constant | Value |
|---|---|
| `BOX_RADIUS`, `BOX_BELOW`, `BOX_ABOVE` | 4, 1, 2 (324 positions) |
| `MAX_PEDESTALS`, `MAX_COMPONENTS` | 16 |
| `MAX_SOURCES` | 32 |
| `MAX_INFUSION_RECIPES` | 4,096 |
| `MAX_COST_ENTRIES`; `MIN_COST`, `MAX_COST` | 8; 1, 1,000 |
| `MAX_INSTABILITY`, `MAX_WARP_PENALTY` | 100, 100 |
| `MAX_STEPS_PER_TICK`, `MAX_QUEUE_POPS_PER_TICK`, `MAX_ARM_REFRESHES_PER_TICK` | 16, 64, 16 per level |
| `MAX_ASSESSMENTS_PER_TICK`, `ASSESS_TTL` | 16 per level (strikes, commit attempts and armed refreshes share it), 20 ticks |
| `HELD_NOW_MAX` | 99,999 |
| `MAX_RUNS` | 2³¹ − 1 (then a new bowl id) |
| `MAX_ROLLS` | 18,016 (the `ceiling` at the tuning maxima: 8 × 1,000 + 16 + 10,000) |
| `MAX_ID_CHARS` | 256 |
| `MAX_UPSET_SPILL_TOTAL` | 10,000 (`upset.max × upset.spill`) |

**Also fixed in code:** the stability, count and clean formulas; the draw order; the seed and draw functions; the matching and its orders; the claim rules; the HUD order and number formats. **Fixed feedback constants:** the jolt offset (0.5, 1.0625, 0.5) and velocity (0, 0.25, 0); the strike pitch rule; the held-item geometry; 8-particle absorb and upset bursts, 12 at a finish, ≤ 16 ambient per `animateTick`; the 40-tick `ring` cooldown at volume 0.6; playback volume 1.0 otherwise.

## Performance

**Heavy-base workload:**
- **8 ringing altars,** each with 16 loaded pedestals, 8 matched stabiliser pairs plus 16 unmatched stabilisers, and 32 sources: 16 jars, and 16 jars fed by lines from the [essentia workload](essentia.md#performance). Recipes of 8 aspects × 200 with 16 components at stability 40, so upsets fire to the cap. **The fixture sustains it:** it refills the jars, restores jolted items and restarts finished runs.
- **One altar whose sources** are 32 stub handlers of 128 slots each that yield nothing until their last slot, so every draw takes the worst path.
- **4 players** with arms refreshing on 4 of the altars; one player striking once a second; **4 hoppers** loading pedestals and taking outputs.

**Share:** server ≤ 0.02 ms mean, ≤ 0.1 ms p99, measured in the combined stress world, including the box reads, capability lookups, transfers and journals, predicates, spawns, events and sync. Shares are acceptance targets, not measurements. Client 0 with no bowl or pedestal in view and no readout; ≤ 0.05 ms p50 with the workload's view, including the renderers' extraction and submission.

**Measured separately as load scenarios** (reported): 100 armed players; **100 players striking every tick** (mixed strikes, commit attempts and refreshes through the shared allowance); a box whose first 300 tagged positions expose no handler; a strike at 4,096 recipes with non-simple centres and 16-component matches; a box holding 324 stabilisers; a recipe `/reload` with 8 altars armed; 64 bowls due in one tick.

**Hot paths:**

| When | Work |
|---|---|
| Per tick, per level | **≤ 16 assessments** in all (strikes, commit attempts, armed refreshes); the arm queue: ≤ 64 pops, run-subscription refreshes from counters only; the step queue: ≤ 64 pops, ≤ 16 steps; the dirty flush: ≤ 1 packet per block entity |
| Per draw step | ≤ 4 chunk checks; 324 state reads; **≤ 324 capability lookups** (to find ≤ 32 sources); ≤ 8 aspects × 32 sources = **256 resource-only extracts**, which on foreign handlers using the interface's default slot walk (`NF:transfer/ResourceHandler.java:228–237`) can mean **≤ 32,768 indexed attempts**; one ledger snapshot per depth |
| Per absorb | one block-entity read and write |
| Per roll | 3 `mix64`; a jolt adds one spawn and its revalidation |
| Per assessment (strike, armed refresh or commit recheck) | 324 state reads, ≤ 324 capability lookups and ≤ 32 × 128 slot reads for "held now", ≤ 16 pedestal reads, the callback-free fingerprint; selection only when its inputs changed: ≤ 4,096 candidates × (≤ 256 `Ingredient` tests + an O(16³) matching) |
| Packets | ≤ 1 update per block entity per tick; ≤ 1 readout per arm per refresh |

- **Allocation:** **zero** in the model's stability, count, clean and roll code (allocation-budget tests). **Bounded** per step (a ledger snapshot, at most one spawn and its payloads, one update tag), per assessment (the snapshot's ≤ 324 cells and ≤ 16 copies) and per selection (`setup + perCandidate × candidates`, capped at 4,096). The byte ceilings are calibrated by the build and stored in its committed allocation-test fixture. **Zero per frame** in our HUD and renderer draw code.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **stability:** every vector above, including the warp penalty, the cap and the clamp;
- **counts and clean figures:** every vector above, including the tuning-maximum `ceiling` of 18,016 (reference implementation: at that `n`, `p` 0 gives 1000 ‰ and `p` 1 and 1000 give 0 ‰, floored and exact alike); the floored fixed point never exceeds the exact floor (n = 0 to 18,016 at every `p`); the trickle case;
- **counters:** `rolls` stops at the upset cap and never exceeds `ceiling`; `runs` at 2³¹ − 1 mints a new bowl id before the next start;
- **positions:** the phase and signed-order vectors, including negative coordinates;
- **upsets:** the seed and roll vectors; JOLT and SPILL fallbacks; the `NONE` effect counting toward the cap; the sequence flux bound against a per-event-rounding stand-in;
- **matching and selection:** every Kuhn vector; strays; the lowest-id enchanting refusal; learned-only candidates; ascending-id ties;
- **enchanting:** each condition and reason; the cost and `R` vectors; a book refused; conflict excluding the holder itself;
- **the recipe codec:** required fields; `transmute` only with an item result and false by default; created results; per-level caps; the `StreamCodec` round trip and bounds;
- **the item handler:** every method row; arguments first; restoration of the promised stack and refusal of a wrong one; locked taking; nested transactions with one snapshot per depth;
- **codecs:** both block entities (every bound and invariant, raw round trip of dormant records, missing ids), the readout (held-now saturation at 99,999 from `long` totals), the seen payload, `InfusionRules`, the tuning file (the product bound) and every `content/` schema;
- **content lints:** the chime-capacity bound; the enchantment tag and maxima; the multiblock rules; the bootstrap coverage; `void` only behind a Stage 4 gate; non-amplification (see the contracts).

**GameTest:**
- **the shown altar** assesses as its lint says;
- **starting:** strike, strike again, ringing; `STALE` after breaking a stabiliser between strikes, after a same-id recipe `/reload` with a changed ingredient, after replacing a pedestal item with an equivalent one, and when the seen revision lags the current one; **a cancelled second `RightClickBlock` and a denied second block use start nothing while the first strike is still fresh**; the first strike never commits, even with a forged `InfusionSeen` sent before it; `InfusionSeen` bad input (far, another dimension, no arm, a wrong or future revision, spectator, two in a tick);
- **callbacks:** an `Ingredient` test and a `supportsEnchantment` hook that change a pedestal slot, swap a pedestal block entity, claim a pedestal or revoke research during the commit's recheck each refuse at the fingerprint; a jolt's spawn listener that changes or discards the provisional item, empties or replaces the pedestal, **opens a transaction on it**, or ends the run each yields `NONE` with no duplication and no loss;
- **assessment budget:** 100 players striking continuously with mixed commit attempts and refreshes: ≤ 16 assessments per level per tick, every pending one serviced or expired within `ASSESS_TTL`, and a pending commit attempt revalidated (out of reach, spawn protection gained) before it commits;
- **run subscription:** without another strike, the starter's HUD follows progress, upsets and the abandon price, and clears at finish, abandonment and removal; a `RUN` readout never commits;
- **the gate:** an unlearned striker gets `NO_RECIPE`; **research revoked during a committed run** — it finishes; a starter who logs out, and one who dies, mid-run — it finishes, and offline completion posts `COMPLETED` without the criterion;
- **claims:** two overlapping bowls start one after the other on shared pedestals — the second refuses; a bowl replaced mid-run leaves dead claims that clear; a bowl in an unloaded chunk keeps its claims unknown, and they refuse a nearby start; **A's pedestal broken and replaced, then claimed by B: A stalls and never consumes B's offering**; an unclaimed replacement holding the promised stack is claimed by A and absorbed; **A dormant while loaded keeps its claims unresolved**, B can't claim them, and restoring A's pack resumes A;
- **automation:** a hopper and an item-capability caller can neither take a claimed component nor put a wrong item on a claimed pedestal, can restore the promised stack, and can't take from a ringing bowl; an aborted capability extraction leaves the slot exact; a capability transaction open across a start, an absorb and a removal (each refused or deferred, settling once);
- **drawing:** from jars and a crucible in the box; nothing from outside it or behind an unloaded chunk; a trickle of one unit per draw rolls once per unit; a nested abort leaves vessels and ledger exact; **a source whose `onRootCommit` throws after a successful extraction: the credit stands, the step rolls, nothing retries**; an extract that throws before the commit: no credit, no roll; capability discovery that throws still closes the operation and settles a deferred removal; a box whose earlier tagged blocks expose no handler still finds its 32 sources; a non-`NONE` lifecycle defers the step; stalls never roll;
- **upsets:** a fixed seed reproduces the vector's upsets; a jolt with a cancelled spawn, and with a listener emptying the pedestal (no duplication); restoring a jolted item by hand and by hopper; a spill is owed again and posts once; the cap;
- **abandoning:** pause, confirm and timeout, with the exact flux; another player refused; a flux-rule `/reload` between the two uses makes it reconfirm;
- **removal:** the bowl removed mid-run, during a step and during a capability transaction (spills and drops once each); a pedestal broken mid-run, then replaced and restored;
- **persistence:** save and reload mid-run, paused, stalled and abandon-armed; a chunk unloaded mid-run resumes without catch-up; a tuning `/reload` mid-run leaves the run's rules frozen; **a tuning change (symmetry penalties, `upset.max_chance`, the rest) followed by save and reopen** leaves the run readable, valid against its stored rules, and unchanged in odds; dormant records refuse hands, hoppers and capabilities and survive save unchanged;
- **enchanting:** a level added; the cap; a conflict; `transmute` keeping components;
- **bounds:** no chunk is loaded anywhere; the step and arm queues hold their caps under 64 due bowls;
- every test id in the discovery guard.

**E2E:**
- the HUD in every status and during a run (progress, missing, paused, waiting, abandon armed), **at the worst case** (8 cells, 4-digit amounts, a long test translation, unheard aspects) on a 320 × 240 scaled screen, overlapping the strike card and the vis HUD (screenshots plus a state dump);
- the strike pitch at stability 0 and 100; every sound and subtitle; the held items (a sword, a block) on both blocks;
- the book's recipe and structure pages and fact fields with no altar, another recipe's altar, a short one, a ready one and **a live run of that recipe** (abandon flux shown, clean figures absent), with `checked_at` shown whenever there is a context;
- **with JEI:** hide and unhide on grant and revoke, and after a reload; the ring at 1 and 16 components; **without JEI**, nothing breaks;
- the Stage 3 flow: learn `vftv:infusion`, craft and build the shown altar, learn `vftv:echo_wands`, infuse an Echo rod and cap, assemble the wand, learn `vftv:infused_enchanting`, enchant a sword (a state dump at each step);
- sync on join, reconnect and `/reload`;
- the asset, lang and model validators: every key under "Lang keys", every sound and subtitle, both blocks' models, blockstates and item definitions, the Echo items and wand layers, and the multiblock display file.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Arcane crafting spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)'s contracts on this ticket):

| Arcane contract | Met by |
|---|---|
| Any recipe made at the Worktable is a `vftv:arcane` body with its own `research`; `kind: void` only behind a Stage 4 gate | "Authored content, Recipes": the bowl and pedestal are `ordinary`, gated by `vftv:infusion` |
| Stage 1 arcane recipes meet the Stage 1 affordability test | None is in Stage 1. Both add a Stage 3 lint at the chime rod's capacity, and the bootstrap review covers the Echo recipes. |
| A recipe needing warp on craft proposes an extension | None applies warp. The warp penalty is a stability input, not a warp charge (see "Stability"). |

**Met here** (the [Crucible alchemy spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)'s contracts on this ticket):

| Crucible contract | Met by |
|---|---|
| Crucible recipes use the type's body with their own `research`, in their own content files, under the common validation and non-amplification test | Infusion ships no crucible recipe; any later one is bound by this |
| Any essentia combination keeps Vacuos a true primal; book pages truthful | Infusion consumes essentia and combines none ("Drawing essentia") |

**Met here** (the [Essentia logistics spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)'s contracts on this ticket):

| Essentia contract | Met by |
|---|---|
| Moves essentia only through the essentia capability, in roots closed within the call, never across ticks | "Drawing essentia": one root per draw step, nested extracts, the ledger journalled in the same root, the `Lifecycle.NONE` guard and deferral |
| Any suction it offers is in the consumer band; its blocks join `vftv:essentia_endpoints` if tubes should connect | The bowl offers no suction and is no endpoint; tubes feed jars inside the box |
| Direct jar pulls inherit neither a line's connectivity nor its throughput, and need not apply the suction rule; routing like a line applies it | Direct pulls from the box only, at `run.rate`, routing nothing |

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts on this ticket):

| Research contract | Met by |
|---|---|
| **Gate:** `research` on every gated recipe; the server refuses every processing and output path until it is learned; a GameTest with an unlearned player | **Reconciled, not met as originally worded.** The field is required; candidates are learned recipes only; the gate is checked at the read and again at the commit's recheck, **before anything is claimed or drawn**. A run then completes the craft that commit authorised, as a committed Worktable output is never confiscated. The research spec's gate contract is amended in this change to say that a machine that runs over time is gated at its commit. GameTests: an unlearned striker, and research revoked mid-run. |
| Recipe-page validator | Each built-in recipe sits on its gating entry's page (handed to #36) |
| One authoritative cost preview: every decision cost, capped modifiers flagged; backs the page and the fact fields | "The authoritative preview", with an `ARMED` or a matching `RUN` context: costs, effective enchanting costs and target level, per-level reference costs without a context, `stability`, `chance`, `clean_floor` and `clean_full_supply`, `upset_flux`, a run's `abandon_flux`, and the `stabilisers_capped` and `clamped` flags; typed absence with exact absent texts; the visible `checked_at` identity on the page and in JEI; frozen fact formats; live dependencies (the research spec amended). GameTest and E2E: a live run's preview. |
| JEI by the learned gate; updates on grant, revoke, login and logout; accurate unaffordability; works without JEI | "JEI": the shared reconcile; absent or stale contexts unavailable; tested with and without JEI |
| **The preview includes stability as a fact field, and the altar is a `multiblock` id structure pages can show** | `stability` (and `instability`, `chance`, `clean`, `flux`) fact fields; `vftv:infusion_altar` with its resource contract (the research spec amended) and its placement GameTest |

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)): later wand tiers are entries added by Infusion with no code — "The Echo rod and cap".

**Met here** (the [progression spec](progression.md#reality-slipping)): infusion stability is shown before you commit and never lies — the server's readout, a revision bound to the whole plan, a commit only inside the second permitted use against an acknowledged revision and a callback-free fingerprint, exact inputs frozen with their own rules, a true clean floor whatever the supply, and the sequence flux bound.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 3 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/36):
- **Entries:** the three notes under "Stage", with their parents and gates, each carrying `vftv:recipe` pages for the recipes it gates; `vftv:infusion` also carries a `vftv:structure` page with `multiblock: vftv:infusion_altar`.
- **Prose** teaches striking twice, symmetry, matched stabilisers and their cap, upsets and their bound, the difference between the clean floor and the full-supply figure, waiting without harm, restoring a jolted offering, abandoning and its price, the start as the commit, and every loss exception, with every number in `recipe` or `infusion` fact spans. The number-word lint bans "pair": prose says "matched", or lists an exception.
- **The affordability artifact:** `content/research/path-stage3.json` learns `vftv:infusion` after `vftv:ruins` and `vftv:essentia_jars`, then `vftv:echo_wands` and `vftv:infused_enchanting`; the review reads it beside `content/infusion/echo_bootstrap.json`, the material half of the proof.
- **No progression requires an infusion craft criterion**, since offline completion omits it.

**[Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)**
- It supplies **`infusion_penalty(player, kind)`**: pure, an integer 0–100, ≥ 0 for `ORDINARY`, a function only of the player's synced warp state and the kind, identical on both sides; the typed zero until it lands. It re-sends nothing itself: a change to its synced inputs is one of the arm's inputs.
- Infusion applies no warp. Warp on an upset, or Void behaviour beyond the penalty, needs #25 (or #28) to propose a previewed, echoed extension.

**[Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)**
- `essentiaFlux(spilled, cause, rules)` covers `UPSET`, `ABANDONED` and `REMOVED` for infusion and is applied **once** on `InfusionEvent(SPILLED)` with **the rules that event carries**. It supplies a pure **`essentiaFluxBound(units, cause, rules)`**: an integer 0–1,000,000, ≥ `essentiaFlux(m, cause, rules)` for every valid map `m` totalling ≤ `units` (≤ 1,000). Its rules record is versioned, synced and codec-backed, with a generation, so a run can freeze it. The two-argument `essentiaFlux(spilled, cause)` of the crucible and essentia contracts is this function under the current rules, so those contracts are unchanged. Until #26 lands, both functions are the typed zero and the record is absent.
- **Bounds:** an infusion spill is a valid map of ≤ 8 aspects totalling ≤ 8,000.
- It adds no flux for drawing, absorbing, stalls, pauses or finishing.

**[Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28), [Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22), [Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24)**
- Their infusion recipes (`vftv:infusion/hollow_bell` among them) use this body with their own `research`, in their own content files. `kind: void` built-ins need a Stage 4 gate. #24 may use `transmute` for upgrades. Runic augments, Void-tier stability rules or anything else is an extension they propose.

**[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)**
- Golems may load pedestals and bowls and restore promised stacks through the item capability or the container path, under its rules. No golem starts an infusion in v1: a start needs a player's second permitted strike against an acknowledged readout.

**[Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)**
- No advancement it ships for progression requires an infusion craft criterion.

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- Hand entries (or reviewed exemptions) for `vftv:singing_bowl`, `vftv:pedestal`, `vftv:echo_rod` and `vftv:echo_cap`.
- **Round-trip non-amplification** over the two arcane recipes (vis counting as no material input) and every built-in infusion recipe in every content file: `total(v(result)) ≤ min over centre alternatives of total(v(alt)) + Σ min over each component's alternatives + total(essentia)`. Enchanting and transmuting add no value: values ignore components ([aspects, Coverage and supply](aspects.md#coverage-and-supply)).

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)** (amended in this change) **and [Wand assets](https://github.com/andrewbrogoff/voices-from-the-void/issues/38)**
- The wands spec now says that later-tier parts live in their own content files in the parts schema, merged with cross-file duplicate rejection, and that later tiers **append** cases to `items/wand.json`.
- **#38 owns `assets/vftv/items/wand.json` first** and delivers it exactly as the wands spec's "Components" shows. The Infusion assets ticket then appends the `vftv:echo` case to each select (layer models `vftv:item/wand/rod/echo` and `vftv:item/wand/cap/echo`), keeping every existing case, both `unknown` fallbacks and the foci layer. The two tickets never rewrite the file at the same time.

**[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)** (amended in this change)
- `api`'s `SpillCause` gains **`UPSET`** and **`ABANDONED`**, posted only by `InfusionEvent`. The crucible's own spills, producers and bounds are unchanged.

## Production tickets

This spec settles every decision. Two required child tickets deliver the assets and the authored content, and **the system is not done until both land**. **Neither writes mod code:** no block, block entity, renderer, HUD layer, payload, item adapter, recipe type, scheduler, JEI plugin, datagen provider or validator. The asset ticket delivers a generator (authorised tooling under `tools/art/`) and assets under the [art style](art-style.md); the content ticket delivers authored inputs under `content/`, in the schemas above. The tags, loot tables, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Infusion assets** | **Generator:** `tools/art/infusion.py`. Every asset is in the Stage 3 row of [art style §5](art-style.md#5-tone-ramp-by-stage): mauve and slate in the shadows, `teal1` in grooves.<br>**Block textures,** 16×16, under `assets/vftv/textures/block/`: `singing_bowl_side.png`, `singing_bowl_top.png` (the rim), `singing_bowl_inner.png` (walls and floor inside), `singing_bowl_bottom.png`; `pedestal_side.png`, `pedestal_top.png`, `pedestal_bottom.png`.<br>**Block models,** pixel-aligned, no rotations ([art style §6](art-style.md#6-blocks-and-models)): `assets/vftv/models/block/singing_bowl.json` with exactly the 6 cuboids under "The blocks" (textures `side`, `top`, `inner`, `bottom`, `particle` = side), and `assets/vftv/models/block/pedestal.json` with exactly its 3 cuboids (`side`, `top`, `bottom`, `particle` = side).<br>**Blockstates:** `assets/vftv/blockstates/singing_bowl.json` and `pedestal.json`, one variant each.<br>**Item definitions:** `assets/vftv/items/singing_bowl.json`, `pedestal.json` (`minecraft:model` of the block model), `echo_rod.json`, `echo_cap.json`.<br>**Items:** `assets/vftv/textures/item/echo_rod.png` (an upright instrument, [art style §4](art-style.md#4-silhouette-language)) and `echo_cap.png`, with `assets/vftv/models/item/echo_rod.json` and `echo_cap.json` (parent `minecraft:item/generated`).<br>**Wand layers:** `assets/vftv/textures/item/wand/rod/echo.png` and `…/wand/cap/echo.png` with their layer models `vftv:item/wand/rod/echo` and `vftv:item/wand/cap/echo` in the wands spec's layer form, and the **appended** `assets/vftv/items/wand.json` (see the contracts on #38).<br>**Sounds:** every file and `sounds.json` entry under "Rendering and sound" (≤ 3 s, faded tails), with the triggers, category, volume, pitch rule and variant counts in its table.<br>**No new sprite:** the HUD frame is `vftv:hud/vis_frame` and its cells `vftv:worktable/cost_cell` and `cost_cell_short`.<br>**Review:**<br>• a contact sheet at GUI scale 2 beside vanilla's lectern, enchanting table and amethyst cluster, with every item and the 5 rod-and-cap wands that include an Echo part;<br>• Blender renders of the shown altar empty, loaded and ringing, with **a sword and a block held** on the bowl and a pedestal at the geometry under "Rendering and sound";<br>• **worst-case HUD mockups** composed from the reused sprites and the vanilla font: a `READY` readout with 8 cells of 4-digit amounts, a run that is missing an offering, paused, waiting and abandon-armed, a `PROMISED` and an `UNREADABLE` readout, a long test translation, and unheard aspects, on a 320 × 240 scaled screen, overlapping the strike card and the vis HUD.<br>Provenance rows. | [Wand assets #38](https://github.com/andrewbrogoff/voices-from-the-void/issues/38) (it owns `items/wand.json` first) |
| **Infusion content** | `content/infusion/recipes.json` (`vftv:infusion_recipes/1`: the two arcane bodies, the two Echo bodies and 6–10 enchanting bodies, within "Authored content, Recipes"); `content/infusion/wand_parts.json` (`vftv:wand_parts/1`, exactly the shipped Echo values); `content/infusion/multiblock.json` (`vftv:multiblocks/1`, the altar the book shows, passing its lints); `content/infusion/echo_bootstrap.json` (`vftv:echo_bootstrap/1`, with every `why` reviewed and the sequence non-circular); and `content/lang/en_us/infusion.json` (`vftv:lang/1`) with every key under "Lang keys". Its review checks that the messages teach what that section lists. | [Vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (the non-amplification lint replays its values) |

[Stage 3 research content #36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) consumes the entry ids, recipe ids, the multiblock id, the fact fields and the `infusion` fact keys frozen here.
