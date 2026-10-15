# Essentia logistics

How essentia is stored and moved once the crucible has dissolved it. This covers:
- the **Essentia Jar** that stores one aspect, and keeps it as an item;
- the **Label** that names an aspect;
- the **Alembic** that pours from a crucible with no tubes;
- **Essentia Tubes** and **Valves**, which join vessels into a **line**;
- the **suction** capability, and the pinned solver that decides what a line moves;
- inspection and teaching, and what a line costs per tick.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the data and sync rules, and the essentia model;
- the [quality gates](quality-gates.md) for every gate;
- the [aspects spec](aspects.md) for aspect icons, the unheard rule and colours;
- the [research spec](research.md) for research gates, recipe pages and fact spans;
- the [arcane crafting spec](arcane.md) for the `vftv:arcane` recipe type;
- the [crucible spec](crucible.md) for the extract-only face, the transaction-busy pattern, the phase function and the spill boundary;
- the [wands spec](wands.md) for the Node Tap, the coalesced flush, status lines and the rotating sink order;
- the [art style](art-style.md) for every asset.

Resolved in [Essentia logistics spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/20). Inputs: [TC4 alchemy](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-alchemy/docs/research/tc4-alchemy.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. Each tunable one is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port. Both print the same 47 lines (2,013 bytes, SHA-256 `6c67d00b2c03b11b17fe212b1bcbc0360458d5bc0aee638984a0385ed1f0a31f`).

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 2 Distillation | the Essentia Jar, Label, Alembic, Essentia Tube, Valve, suction and lines | the opener `vftv:distillation`, whose find `vftv:find/distillation` is [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s ([research, Stage](research.md#stage)). Inside the Stage, the two `note` entries below. |
| 3–4 | nothing of its own | other specs' consumers join lines through the capabilities here |

| Entry | Kind | Parents | Gates |
|---|---|---|---|
| **`vftv:essentia_jars`** | `note` | `vftv:resonant_brass` | the arcane recipes `vftv:essentia_jar` and `vftv:alembic` |
| **`vftv:essentia_tubes`** | `note` | `vftv:essentia_jars` | the arcane recipes `vftv:essentia_tube` and `vftv:tube_valve` |

- **Gate the recipe, not the block.** Using a jar, tube, valve or alembic is ungated: one someone gave you works for you, as a crucible does ([crucible, Stage](crucible.md#stage)). **Obtaining counts.**
- **Labels are ungated vanilla recipes:** paper and an ink sac make labels, and a label crafted alone comes out blank. A label does nothing by itself.
- **Brass ancestry** prevents a progression cycle, because the jar and tube recipes may use `vftv:resonant_brass_ingot`. It follows the singing-wands precedent ([crucible, Contracts](crucible.md#contracts-on-sibling-specs)).
- **Options considered:** one entry for everything; a find for tubes. **Why two notes:** "jars, then tubes" is the [research roster](research.md#roster)'s order, and the Alembic makes jars useful before any tube exists.

## The Essentia Jar

### The block

**`vftv:essentia_jar`**: a block with a block entity.

| Property | Value |
|---|---|
| Behaviour properties | strength 0.3, `SoundType.GLASS`, `noOcclusion()`, no tool required; `PushReaction.BLOCK` |
| Shape | outline and collision `[3,0,3]–[13,12,13]` px, the 10×12×10 jar of [art style §6](art-style.md#6-blocks-and-models) |
| Block state | `facing` (horizontal): the face that carries the glyph, set at placement to face the player (the opposite of the player's horizontal facing). Not waterloggable. |
| Use | `useItemOn` handles labels and shears (see "Labels"); `useWithoutItem` handles status (see "Inspection"); everything else passes |
| Item | the block item `vftv:essentia_jar`, stacking to 64 (see "The jar as an item") |
| Ticker, comparator, redstone, random ticks | none |

**Build-phase data** (datagen from code, not production deliverables):
- the loot table `vftv:blocks/essentia_jar`: drops itself with `minecraft:copy_components` of `vftv:jar_contents` and `vftv:label` from the block entity, with no explosion condition, as vanilla's shulker box does;
- membership of `vftv:essentia_endpoints` (see "Tags");
- its aspect values are [Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)'s hand entry.

### Contents and the handler

**The record:** `aspect` (optional), `amount` (0 when the aspect is absent, otherwise 1 … `jar.capacity`), and `label` (optional).
- A jar holds **one aspect**, or none.
- The aspect is cleared when the amount reaches 0. The label stays.
- **A readable record is consistent:** if both `label` and `aspect` are present, they are equal. Otherwise the record is dormant (see "Sync and state").

**Exposure:** the architecture's essentia capability ([architecture, Essentia and capabilities](architecture.md#essentia-and-capabilities)) and the suction capability (see "Suction"), both on **every face and the null side**.
- **One explicit adapter** in `neoforge.essentia` implements `ResourceHandler<EssentiaResource>` over the loader-free contents in `core.essentia.model`, with **our own `SnapshotJournal`**.
- The stock `ResourceStacksResourceHandler` isn't reused unmodified. Its journal is private, so its busy state and snapshots can't be read, and its indexed methods check indices before arguments (`NF:transfer/StacksResourceHandler.java:55,218–269,278–298`).

**Accepts X** iff the label is X or absent, and the aspect is X or absent.

| Method | Rule |
|---|---|
| `size()` | 1 |
| `getResource(int)` | slot 0's aspect, or the empty resource; the empty resource for any other index |
| `getAmountAsLong(int)` | slot 0's amount; 0 for any other index; 0 while unavailable |
| `getCapacityAsLong(int, r)` | the capacity in force if the jar accepts `r`, else 0; 0 for any other index or while unavailable |
| `getAmountAsInt`, `getCapacityAsInt` | the interface's defaults, kept as they are (`@ApiStatus.NonExtendable`) |
| `isValid(int, r)` | index 0 and the jar accepts `r` |
| `insert(int, r, n, tx)`, `insert(r, n, tx)` | after the preconditions: 0 if unavailable, if the index isn't 0 or if the jar doesn't accept `r`. Otherwise `k = min(n, max(0, capacity − amount))`; if `k` > 0 the journal snapshots this depth, then the aspect is set and the amount credited. Returns `k`. |
| `extract(int, r, n, tx)`, `extract(r, n, tx)` | after the preconditions: 0 if unavailable, if the index isn't 0 or if `r` isn't the held aspect. Otherwise `k = min(n, amount)`; if `k` > 0 the journal snapshots this depth, then the amount is debited (and the aspect cleared at 0). Returns `k`. |

- **Arguments first.** All four `insert` and `extract` overloads begin with `TransferPreconditions.checkNonEmptyNonNegative(resource, amount)`, so an empty resource or a negative amount throws `IllegalArgumentException`. After that, nothing throws ([crucible, Essentia out](crucible.md#essentia-out)).
- **The resource-only overloads** are overridden to O(1).
- **Unavailable** means any of these:
  - the block entity is removed, or isn't the one at its position;
  - its record is dormant;
  - it holds a **drop reservation** (see "Removal and drops").

  The jar calls `level.invalidateCapabilities(pos)` whenever it becomes dormant or readable. A content change needs no invalidation, because the same live interface stays valid.
- **A lowered capacity** never deletes: the jar accepts nothing until its amount is below the new capacity.

**No hand-emptying, and no void jar.** Essentia leaves a jar only through the handler, or as a priced spill (see "The jar as an item"). The crucible's bucket emptying stays the one hand disposal path ([crucible, Spilling](crucible.md#spilling)).
- **Options considered:** priced hand-emptying, which would also keep the crucible's economics; TC4's void jar as an overflow sink.
- **Recommend** neither in v1. **Why:**
  - a free void sink would make the crucible's price optional;
  - buffer-free tubes never back up, so nothing needs an overflow;
  - deliberate disposal costs the jar (see "Loss").

  The book discloses every way contents can be lost (see the contracts on #35).

### Labels

**`vftv:label`**, stacking to 64. Its data component **`vftv:label`** is an aspect id in its native `ResourceKey` codec, like the wands spec's scalar part components ([wands, Sync and state](wands.md#sync-and-state)). **Absent means blank.** A label naming an aspect the registry doesn't hold is kept unchanged and admits nothing.

**Applying, copying and cutting.** Every row needs the target to be in reach, and runs inside vanilla's `useItemOn` after spawn protection, `mayInteract` and `PlayerInteractEvent.RightClickBlock`. A cancelled event or a denied block use wins. "Consumes" never applies to a player with infinite materials. First match:

| Held (in the hand being used) | Target | Effect | Consumes | Returns |
|---|---|---|---|---|
| a label or shears | a **dormant** jar, tube or alembic | refused: `message.vftv.essentia.unreadable` | nothing | `CONSUME` |
| a label or shears | a jar that is **transaction-busy** (see "Transactions") | refused: `message.vftv.essentia.busy` | nothing | `CONSUME` |
| a label naming a missing aspect | anything | refused: `message.vftv.essentia.label_unreadable` | nothing | `CONSUME` |
| an X label | a target already labelled X | nothing changes | nothing | `CONSUME` |
| any label | a target labelled with anything else | refused: `message.vftv.essentia.cut_label_first` | nothing | `CONSUME` |
| a **blank** label | an unlabelled jar holding X | the jar is labelled X; `essentia.label_apply` plays | one label | `SUCCESS` |
| a blank label | an empty jar, a tube or an alembic | refused: `message.vftv.essentia.nothing_to_name` | nothing | `CONSUME` |
| an **X** label | an unlabelled jar holding X, or empty | labelled X (an empty jar is now locked to X); the sound plays | one label | `SUCCESS` |
| an X label | a jar holding Y ≠ X | refused: `message.vftv.essentia.label_mismatch` | nothing | `CONSUME` |
| an X label | an unlabelled tube or alembic | labelled X; the sound plays. A tube with all six faces `connected` is normalised at once (see "Connections, Labelled tubes"). | one label | `SUCCESS` |
| shears (the item tag `c:tools/shear`, NeoForge's `Tags.Items.TOOLS_SHEAR`) | a labelled target | the label is removed and handed over as one label carrying the same component: `Inventory#placeItemBackInInventory`, which drops it at the player when the inventory is full. The shears take 1 damage through vanilla's `hurtAndBreak`. `essentia.label_cut` plays. | 1 durability | `SUCCESS` |
| shears | an unlabelled target | `TRY_WITH_EMPTY_HAND` | | |
| anything else | any | `TRY_WITH_EMPTY_HAND` | | |

**Copying a label** is the label item's own `useOn`, reached only when the player is sneaking: vanilla skips the block's use when a sneaking player holds anything (`MC:server/level/ServerPlayerGameMode.java:398–399`).
- **When it acts:** the held stack is blank; the target is a readable, non-busy jar holding X, or empty and labelled X; and the jar is in reach.
- **What it does:** one label leaves the held stack (unless the player has infinite materials), and one X label is handed over through `placeItemBackInInventory`. Returns `SUCCESS`.
- **Otherwise,** and whenever the player isn't sneaking, it returns `PASS`.

**Hands.** Vanilla tries the main hand first.
- An empty main hand reaches `useWithoutItem` before an off-hand label or pair of shears is consulted. On a tube that toggles a face (see "Connections").
- `useWithoutItem` acts **only when the main hand is empty**. With anything else in the main hand it returns `PASS`, so a held block places normally.

**The lock and the gate.**
- A labelled jar accepts only X, empty or not.
- A labelled tube is a **gate**: only X crosses it (see "Lines").
- A labelled alembic pours only X.

**Glyphs, not tint, carry identity** (see "Rendering"). This meets the aspects spec's rule that jars and tubes carry labels as well as tint ([aspects, Colours](aspects.md#colours)).

### The jar as an item

**Components:**
- `vftv:jar_contents`: `{"format": 1, "aspect": <id>, "amount": 1–10000}`. It is absent for an empty jar.
- `vftv:label`, as above.

**Decoding never fails.** The contents codec decodes either a valid record, or anything else as a **dormant** raw tag, which is kept and written back unchanged. A dormant item:
- places a dormant jar;
- is never an input to a spill;
- shows `tooltip.vftv.essentia_jar.unreadable`.

**Placing:** `BlockItem` placement hands the components to the block entity through `applyImplicitComponents`, and `collectImplicitComponents` writes them back out (`MC:world/level/block/entity/BlockEntity.java:337,366`). A record that is inconsistent or out of bounds makes the new jar dormant.

**Stacking:** identical stacks stack to 64. Each placement consumes one item with its full contents, so stacking conserves essentia.

**Tooltip** (built on the client):
- `tooltip.vftv.essentia_jar.holds` with the amount and the aspect's name;
- `tooltip.vftv.essentia_jar.labelled` with the label's name.

An aspect the viewer hasn't discovered is named `essentia.vftv.unheard`, and a missing one `essentia.vftv.missing` ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)). The amount is always exact.

**Crucible protection:** `vftv:essentia_jar` is in the crucible's refusal tag, empty or filled ([crucible, Protection](crucible.md#protection), amended in this change).

#### Removal and drops

Every path that removes a jar while keeping its contents runs **one reservation** first. **`reserveForDrop()`** is idempotent:
1. It computes the **carry**: for each aspect, the minimum of the live amount and that aspect's amount in **every live journal snapshot**, with an absent aspect counting as 0.
2. It records the carry, and from then on **the handler is unavailable**, so no callback can extract after the copy.
3. `collectImplicitComponents` writes the carry, never the live amount, while a reservation exists.

**Who reserves, and why each path is safe:**

| Path | Reserves in | Order (verified) |
|---|---|---|
| a player breaking it in survival | `playerWillDestroy` | `playerWillDestroy`, then removal, then `playerDestroy` drops the reserved copy (`MC:server/level/ServerPlayerGameMode.java:287–301`) |
| a creative break of a non-empty jar | `playerWillDestroy`, which then spawns the filled item, as vanilla's shulker box does (`MC:world/level/block/ShulkerBoxBlock.java:108–122`) | spawn, then removal |
| `Level.destroyBlock` with drops (`/setblock … destroy`, other mods) | `getDrops` | drops, then `setBlock` (`MC:world/level/Level.java:305–321`) |
| an explosion | `getDrops` | drops, then removal |

**Settlement** runs in `preRemoveSideEffects`. The settled contents are compared with the **reserved carry** (none if nothing reserved), and **every unit not reserved is spilled**: `EssentiaEvent(SPILLED)` with cause `REMOVED` (see "Events"). **A reservation isn't a delivery.** Settlement spills the settled contents minus the reserved carry, whether or not that carry actually reaches the world as an item (see "Qualified" below).
- **If the jar is transaction-busy,** settlement waits for the root to close, on commit or on abort. The settled contents are then whatever the journal left.
- **No duplication is possible.** The settled amount of each aspect is the live amount or one of the snapshots, and the carry is never more than any of them.
- **A removal with no drop** (`/setblock` replace, another mod's `setBlock`) carries nothing, so the whole contents spill as `REMOVED`.
- **A surviving reservation:** a jar still in place, and not removed, at the end of the tick in which it reserved (a cancelled break, say) releases its reservation, and its handler becomes available again. Anything a caller already dropped from it is that caller's, just as a failed `destroyBlock` duplicates vanilla's containers.

**Vectors** (reference implementation):

| Case | Live, snapshots | Carried | Settles on root abort | Settles on root commit |
|---|---|---|---|---|
| a | root Aer 100; a nested extract of 50 committed; a nested insert of 30 open; removed now: live Aer 80, snapshots Aer 100 and Aer 50 | Aer 50 | Aer 100 → spill Aer 50 | (nested abort, then root commit) Aer 50 → spill nothing |
| b | root Aer 100; extract 100; insert Ignis 100; removed: live Ignis 100, snapshot Aer 100 | nothing | Aer 100 → spill Aer 100 | Ignis 100 → spill Ignis 100 |
| c | not busy, Terra 40 | Terra 40 | | spill nothing |

**Qualified:**
- a removal with block-update flag 256 skips `preRemoveSideEffects` and loses the contents without an event, as it does for the crucible ([crucible, Removal](crucible.md#removal));
- a jar removal can't be refused from inside the pathways above;
- **external drop loss.** The reserved carry is lost without a spill when the drop never becomes an item:
  - the `doTileDrops` game rule is off (`MC:world/level/block/Block.java:402–447` checks it);
  - a `BlockDropsEvent` listener cancels the drops, or removes or changes the jar item (`NF:common/CommonHooks.java:573–585`);
  - the item entity's spawn is rejected, a result NeoForge ignores (the same lines).

  These are vanilla's and other mods' decisions about drops, and they apply to every container in the same way. The book lists them with the other loss exceptions (see the contracts on #35).

#### Loss

| Fate of a filled jar item | Result |
|---|---|
| destroyed: fire, lava, cactus, an explosion (`ItemEntity#hurtServer` calls `onDestroyed(ItemEntity, DamageSource)` and then `discard`, `MC:world/entity/item/ItemEntity.java:297–315`; the NeoForge overload is noted at `MC:world/item/Item.java:187`) | **`onDestroyed` claims the contents first:** it removes `vftv:jar_contents` from the entity's stack. Only then does it post `EssentiaEvent(SPILLED)` with cause `DESTROYED` and the count × contents. A re-entrant second call finds nothing to claim, so it spills once. Dormant contents claim and spill nothing, with one warning. |
| expires (`ItemEntity.java:192–197`), falls below the world (`MC:world/entity/Entity.java:665`), or is discarded by a command | **lost silently.** No hook of ours runs, exactly as for vanilla's shulker box. Stated in the book. |

**Bound:** a destroyed stack spills at most 64 × 10,000 = **640,000** units (`MAX_ESSENTIA_SPILL`), computed in `long`.

## The Alembic

**`vftv:alembic`**: a still-head hung on a crucible's side. It pours one aspect from the crucible into whatever lies beneath its spout.

| Property | Value |
|---|---|
| Behaviour properties | strength 0.5, `SoundType.GLASS`, `noOcclusion()`, no tool required; `PushReaction.BLOCK` |
| Shape | outline and collision `[3,0,3]–[13,14,13]` px |
| Block state | `facing` (horizontal): toward its **base** |
| Block entity | the label only: `{"format": 1, "label"?: <id>}`; a server ticker |
| Placement | `getStateForPlacement` takes the first of these whose neighbour in that direction is in the block tag **`vftv:alembic_bases`**: the clicked face's opposite (if horizontal), then the player's horizontal facing, then clockwise from it. With none, placement is refused and the placer sees `message.vftv.essentia.no_base`. |
| Survival | `canSurvive` iff the block at `facing` is in `vftv:alembic_bases`. When `updateShape` finds it isn't, the alembic breaks and drops, as a torch does. |
| Use | labels and shears (see "Labels"); status (see "Inspection") |
| Loot | itself, plus its label as a separate item (a dynamic drop), both under vanilla `dropSelf`'s explosion-survival condition |

**The tag**, not a block reference, decides the base, so `core.essentia` never imports `core.crucible` ([architecture, Enforcement](architecture.md#enforcement), `FEATURES_ACYCLIC`).

**The pour.** It runs on the ticks where `level.getGameTime() mod alembic.interval = phase(pos)`, with the [crucible's `phase`](crucible.md#boiling-and-the-step). In order:
1. **Guards:**
   - the alembic isn't dormant;
   - the block at `facing` is **still in `vftv:alembic_bases` now**, checked before every pour, not only when shapes update;
   - the base position and the **target** (the block **directly below** the alembic) are both in loaded chunks (`getChunkNow`) and **block-ticking** (`ServerLevel#shouldTickBlocksAt(long)`, `MC:server/level/ServerLevel.java:492`);
   - `Transaction.getLifecycle() == Lifecycle.NONE` (see "Transactions").

   Otherwise the pour does nothing this phase tick.
2. **Ends**, each with **side-matched** handler and suction capabilities:
   - **the source** is the base on side `facing.getOpposite()`, the base's face that touches the alembic. On a crucible that face is exposed, because only `UP` is withheld ([crucible, Essentia out](crucible.md#essentia-out)). The base's suction capability on that side gives its **hold**; with none, hold is 0;
   - **the target** is the block below, on side `UP`.

   Both are looked up through `BlockCapabilityCache`s (see "Transactions, Capability caches"). A missing source or target handler, or one with more than `MAX_HANDLER_SLOTS` slots, means no pour.
3. **The aspect.** Only aspects X that the source holds and for which **`target.suction(X) > source.hold(X)`** count. This is the rule under "Suction", so a pack that extends `alembic_bases` to a store never pours downhill or between equal ranks.
   - **Labelled X:** X, if it counts.
   - **Unlabelled:** of the aspects that count, the one with the highest `target.suction`, ties to the larger source amount, then the lowest id.
   - No aspect means no pour.
4. **The move:** exactly one source contribution under the rule in "The pulse, step 6", with `need = alembic.rate`. The same inequality is rechecked immediately before the move.
5. **Feedback:** if anything moved, `essentia.alembic_drip` plays, at most once per alembic per 40 ticks.

- **It holds nothing.** It has no handler, connects to no tube, and loses nothing when broken. **Heat isn't required:** a cold crucible stays extractable.
- **Combining and insertion:** neither. An alembic only ever extracts from its base.
- **Options considered:**
  - cutting it, since tubes can do the job;
  - a top-mounted head, which would block what is thrown into the crucible;
  - TC4's furnace-top stack, which needs a second dissolver.
- **Recommend** the side-hung pour. **Why:** it gives Stage 2 a tube-free first step that puts the crucible's leftovers into jars ([crucible, The pool](crucible.md#the-pool)), using the crucible's own extract face.

## Suction

**`vftv:essentia_suction`**: a `BlockCapability<EssentiaSuction, @Nullable Direction>` in `api.neoforge`, beside the handler capability **`vftv:essentia`** and **`EssentiaResource`**, both in `api.neoforge.essentia` ([API spec, Capabilities and views](api.md#capabilities-and-views)). The interface **`EssentiaSuction`** lives in `api`, is loader-free and is implementable by addons:

```java
int suction(ResourceKey<Aspect> aspect);                  // 0–128
default int hold(ResourceKey<Aspect> aspect) { return 0; } // 0–128
```

- **`suction(X)`** is how strongly the endpoint pulls X now. **A positive value means it would accept at least one unit of X now.** An endpoint that would accept may still advertise 0, which opts it out of automatic pulling (the Node Tap does so when `cultivate.suction` = 0). Values outside 0–128 are clamped, and logged once per block entity type.
- **`hold(X)`** is the rank at which the endpoint keeps the X it holds. **It does not change with fullness.**
- **An endpoint without the capability** has suction 0 and hold 0. It is a pure source (a crucible), or inert.
- **Scope:** suction and hold are a **routing policy** that the movers in this spec apply (lines and alembics). `extract` doesn't know where essentia is going, so no handler enforces it. A golem or other consumer that routes like a line must apply the same rule itself (see the contracts).

**The rule:** a mover moves X from A to B only if **`B.suction(X) > A.hold(X)`**, strictly.
- Hold is static and the inequality is strict, so essentia only ever flows uphill in rank, and endpoints of equal rank never trade.
- A full jar's suction falls to 0 while its hold stays, so a full jar never drains into an empty peer.

| Endpoint | `suction(X)` | `hold(X)` |
|---|---|---|
| crucible (no capability) | 0 | 0 |
| Essentia Jar, unlabelled | `suction.jar` if it has room and is empty or holds X; else 0 | `suction.jar` if it holds X; else 0 |
| Essentia Jar, labelled X | `suction.jar_labelled` if it has room; 0 for every other aspect | `suction.jar_labelled` if it holds X; else 0 |
| a dormant or reserved jar | 0 | 0 |
| Node Tap | `cultivate.suction` if it would accept X now; else 0 ([wands, Cultivation](wands.md#cultivation), amended in this change) | 0 |

**Bands** (fixed, `EssentiaLimits`):
- **0:** sources and opted-out sinks.
- **1–15 (`STORAGE_MAX`):** storage, meaning jars and any addon buffer.
- **16–128 (`MAX_SUCTION`):** consumers, meaning the Node Tap, infusion and addon machines.

The tap's default of 16 sits at the bottom of the consumer band and above every store, so **no mapping from the wands spec's scale is needed**.

**Vector** (reference implementation): the jars of "The pulse" end holding Aer 12 and Aer 8 at rank 4, and Terra 30 at rank 4. With the crucible empty, nothing moves and the line sleeps: equal ranks never trade.

**Options considered:**
- TC4's typed suction decaying with distance, which needs per-tube state and is the source of jams (TC4 alchemy research, §2);
- one suction per endpoint;
- fill-dependent rank, which oscillates.

**Why this:** it is cheap, it cannot oscillate, and its suction semantics match the existing tap contract.

## Essentia Tubes and Valves

### The blocks

| | **`vftv:essentia_tube`** | **`vftv:tube_valve`** |
|---|---|---|
| Behaviour properties | strength 0.5, `SoundType.COPPER`, `noOcclusion()`, `PushReaction.BLOCK`; in `minecraft:mineable/pickaxe`, no tool required | the same |
| Block state | `down`, `up`, `north`, `south`, `west`, `east` ∈ {`none`, `connected`, `blocked`}, all `none` by default (729 states) | `axis` (x, y, z), `open` (boolean), `powered` (boolean) |
| Placement | faces computed as under "Connections" | `axis` = the clicked face's axis; `open` = true; `powered` = `hasNeighborSignal` |
| Shape | outline and collision: the core `[5,5,5]–[11,11,11]` px plus, for each non-`none` face, an arm from the core to that face | the body `[4,4,4]–[12,12,12]` plus both arms along its axis |
| Block entity | holds the optional label: `{"format": 1, "label"?: <id>}`; no ticker | no data; no ticker |
| Use | an empty main hand toggles a face (see "Connections"); labels and shears; status | an empty main hand toggles `open`; status |
| Loot | itself, plus its label as a separate item (a dynamic drop), both under vanilla `dropSelf`'s explosion-survival condition | itself, `dropSelf` |

**Both have block entities.** A tube's holds its label, because aspects are a datapack registry and can't be a block-state value ([ADR 0003](../adr/0003-aspects-as-datapack-registry.md)). Both block entities also give `onLoad` and `setRemoved` hooks for the topology queue, as the wands spec's relays use theirs ([wands, Relays](wands.md#relays)).

**Cut:** directional and restricted tubes (with no distance decay they mean nothing), buffers, centrifuges, mirrors and the void jar. Labelled tubes are the filters.

### Connections

Faces are computed in `getStateForPlacement` and `updateShape` **from block states and the tube's own label alone**, so the client and server agree and no capability is looked up on a `LevelReader`. A tube face in direction *d* is **`connected`** iff it isn't `blocked` and the neighbour at *d* is one of:
- a tube whose face toward us isn't `blocked`;
- a valve whose `axis` is *d*'s axis, open or not (passing is decided by the topology, so a valve toggle never redraws its neighbours);
- a block in the block tag **`vftv:essentia_endpoints`**.

Otherwise the face is `none`. A valve's two axis ends join on the same terms; its other four faces never connect.

**Toggling a face:** an empty main hand, not sneaking, on a tube toggles one face between `blocked` and its computed state.
- **Which face:** the hit point is taken in block-local coordinates. If it lies inside an arm's box (with a 10⁻⁴ tolerance), that arm's face is toggled; otherwise, the clicked face.
- The neighbour's matching face updates through `updateShape`.
- Returns `SUCCESS`.

**Deaf arms:** a `connected` face toward an endpoint-tag block that exposes no essentia handler on that side (a crucible's `UP`, say) is drawn but moves nothing. The status line counts it.

**Labelled tubes keep a glyph face.** A labelled tube (readable or dormant) must always have at least one face that is `none` or `blocked`, because those faces carry its glyph (see "Rendering, The tube band"). Whenever its computed faces would all be `connected`, **the first of them in Direction order is set to `blocked`**. This normalisation runs in:
- `getStateForPlacement` and `updateShape` (which read the label from the block entity through the `LevelReader`; the client has it from the block-entity sync);
- applying a label;
- every face toggle and face refresh.

So the sixth connection is refused at the tube, and the would-be neighbour sees a `blocked` face. A toggle that would unblock the only free face leaves it `blocked`.

### Valves

- **Passing:** a valve **passes iff `open ∧ ¬powered`**. While passing, it is a member of a line, with arms only along its axis. Otherwise it is a wall.
- **Redstone:** `neighborChanged` sets `powered` from `hasNeighborSignal` when it differs.
- **By hand:** an empty main hand, not sneaking, toggles `open` and plays `essentia.valve_open` or `essentia.valve_close`. **Opening by hand is refused** with `message.vftv.essentia.line_too_long` if it would join lines beyond the cap (see "Admission").
- **No labels on valves.**
- **Options considered:** TC4's rising and falling edge toggle. **Why** a level-triggered `powered` that overrides a hand `open`: the state is visible and doesn't depend on edge history.

### Tags

| Tag | Kind | Default | Meaning |
|---|---|---|---|
| **`vftv:essentia_endpoints`** | block | `vftv:essentia_jar`, `vftv:crucible`, `vftv:node_tap` | tube faces connect to these blocks; packs and addons extend it |
| **`vftv:alembic_bases`** | block | `vftv:crucible` | an Alembic may hang on these blocks |

**A tag reload** (`TagsUpdatedEvent.ServerDataLoad`) does three things:
1. **It freezes every line at once.** Every live line in the level is retired, so no line runs on routes the reload has removed, and nothing moves until the refresh below finishes.
2. **It queues a refresh** of every loaded tube, valve and Alembic. The per-level loaded set is kept by the block entities' `onLoad` and `setRemoved`.
3. **It pauses the rebuild queue** (see "Rebuilding") while that level's refresh queue is non-empty.

**The refresh queue** runs at the end of each server tick, at most `MAX_FACE_REFRESHES_PER_TICK` per level, in ascending `asLong`. **It never loads a chunk.**
- **A tube or valve:** the faces are recomputed (with the labelled-tube normalisation), the state is set with `UPDATE_CLIENTS` if it differs, and the position is queued for rebuilding.
- **An Alembic:** it is revalidated. If its base is no longer in `vftv:alembic_bases`, it breaks and drops, as under "Survival". Every pour also checks the tag (see "The pour").

**So no route survives a removed tag entry.** Lines are frozen from the reload until their members' faces have been refreshed and rebuilt, and an Alembic whose base left the tag never pours again.

## Lines

### Topology

A **line** is a connected component of **members**: tubes, and valves that pass. Joins run along `connected` faces. **Topology is derived from block states and never persisted.**

**A line records:**
- its members, at most `MAX_MEMBERS` (256);
- its **key**, the lowest member `BlockPos.asLong()`;
- its **generation**, a per-level counter value;
- its **endpoints**;
- its **nodes**;
- its **chunk set**: every chunk holding a member or an endpoint position;
- its state;
- an executed-pulse counter, set to 0 at each build.

**Endpoints.** For each member face *d* that is `connected` to a non-member neighbour *N*, there is one endpoint **(N, d.getOpposite())**.
- Its handler and suction are the two capabilities at *N* **for that side**, and it belongs to the **node of that member**.
- There is at most one endpoint per (position, side) pair, and at most `MAX_ENDPOINTS` (64) per line.
- **Endpoint order** is by (`N.asLong()`, side ordinal), where Direction order is D, U, N, S, W, E (`MC:core/Direction.java:33–38`).
- **No move ever has its source and sink at the same position,** whatever the sides.
- **Sides matter.** A crucible touched from above and from the side is one deaf arm (its `UP`) and one endpoint (its side). A sided addon handler is reached only through the face its arm actually touches, so a gate can't be bypassed through another face.

**Nodes:**
- **segments**: the maximal joined sets of unlabelled tubes and passing valves;
- **gates**: each labelled tube on its own, which **admits only its label's aspect**;
- a tube whose label record is dormant, or names a missing aspect, is a gate that **admits nothing**. It is never treated as an unfiltered tube.

**Reachability:** endpoints A and B are **X-connected** iff A's node and B's node both admit X and share a component of the union-find over X-admitting nodes, joined by member adjacency.
- It is built lazily, per aspect queried, over ≤ 256 nodes, and cached for the line's generation.

**States:**

| State | Meaning | Runs? |
|---|---|---|
| `building` | a rebuild is queued or in progress | no |
| `ready` | built, every chunk in its set loaded and block-ticking | yes |
| `incomplete` | a `connected` face crosses into a chunk that isn't loaded or isn't block-ticking, or a chunk in its set has stopped being either | no; it has **chunk rechecks** scheduled (see "Scheduling") |
| `overgrown` | over 256 members or 64 endpoints | no; the status line says why |

**An overgrown record** keeps only what retirement and inspection need: the exact member and endpoint counts, its key and generation, and its membership (each member's position maps to it). **It builds no nodes, no endpoint records and no capability caches.**

### Rebuilding

**Changes** retire the affected line **at once** and queue positions for rebuilding. Retiring bumps the generation, so its members are unassigned, its scheduled pulses and capability caches are dead, and a cached reachability is dropped.

| Change at position P | Retires | Queues |
|---|---|---|
| a tube or valve placed, removed, or changing state (faces, `open`, `powered`) | the line holding P, and the lines of P's six neighbours | P and its six neighbours |
| a member's block entity `onLoad` (chunk loads, `/setblock`, `/clone`, structures) | the lines of P's six neighbours | P |
| a member's block entity `setRemoved` (removal or chunk unload: `LevelChunk#clearAllBlockEntities` calls `onChunkUnloaded` then `setRemoved`, `MC:world/level/chunk/LevelChunk.java:685–687`) | the line holding P | P's six neighbours |
| a label applied, cut or turned dormant | the line holding P | P |
| a tuning `/reload` | nothing (its rules apply at the next pulse; see "Sync and state") | |

**The rebuild queue** is a per-level FIFO of positions, processed at the end of each server tick, and paused while that level's refresh queue is non-empty (see "Tags").
- A queued position that already belongs to a live line, or isn't a member, or whose chunk `getChunkNow` no longer returns, is dropped.
- Otherwise a **BFS** starts from it, in Direction order along `connected` faces, through members only.
- **It never crosses into an unloaded chunk.** A `connected` face into a chunk that `getChunkNow` doesn't return, or that isn't block-ticking, records that chunk as a **boundary chunk**, and the line will be `incomplete`.
- **A BFS counts every member,** so `overgrown` is exact, not a guess. There are no stale overgrown markers: a cut retires the overgrown line like any other, and the pieces rebuild from scratch.
- **Finalisation** follows the BFS. A line within the caps records its nodes, its endpoints (each with its two capability caches; see "Transactions"), its chunk set and its state under a new generation. An overgrown line records only its overgrown record. A line under finalisation is `building`.
- **On completion:** a `ready` line is scheduled for pulses, and an **`incomplete` line is scheduled for chunk rechecks at once** (see "Scheduling").

**Every step of this is budgeted.** One per-level allowance, `MAX_TOPOLOGY_WORK_PER_TICK` (4,096 units), is shared by:

| Work | Units |
|---|---|
| a rebuild-queue pop, kept or dropped | 1 |
| a member visit in a BFS, with its ≤ 6 neighbour reads | 1 |
| finalising a member (node assignment, chunk set) | 1 |
| finalising an endpoint (its record and two cache creations) | 2 |
| removing one stale membership entry left by a retired line | 1 |

Unfinished work resumes next tick in the same order. A BFS or finalisation in progress restarts if a position it has visited, or holds in its frontier, changes meanwhile. **Retiring** is O(1): it bumps the generation, so stale membership entries read as unassigned, and a cleanup cursor removes them under the same allowance.

**Admission.** For the player's sake, placing a tube or valve, or opening a valve by hand, is **refused** with `message.vftv.essentia.line_too_long` (1 arg: `MAX_MEMBERS`) when the **proposed** state would join lines beyond the cap:
- **Joined lines** are only those of neighbours that the proposed state actually connects to on both sides. A tube joins a neighbour only if both facing faces would be `connected`. A valve joins only through its two axis ends, and only if it would pass.
- **The check:** every joined line is live and built, and 1 plus the sum of their member counts exceeds 256. A neighbour joined on two faces is counted once.

A perpendicular line next to a valve that doesn't touch its axis never blocks opening it. The check is O(6). Commands, structures, pistons and redstone can still build an `overgrown` line; it stays inert, and says why.

**Options considered:**
- per-tube buffers and ticking, which is TC4's model and the lag the community remembers (pain points, §4: "cached graph, not per-tile ticking");
- persisted topology, which the wands spec's relays need for range links but tubes don't, because the blocks are the graph;
- running half-loaded lines.

**Why this:** bounded rebuilds; no ghost state to reconcile; behaviour that doesn't depend on which half of a line is loaded.

## The pulse

**One move per line per pulse: one aspect, into one sink.** This is TC4's "one aspect at a time, highest suction wins" without the jam, because nothing waits in a tube. The selection runs in `core.essentia.model` over loader-free endpoint records; the moves run in the `neoforge.essentia` adapter ([architecture, Seams](architecture.md#seams)).

1. **Preconditions:**
   - the line is `ready`;
   - every chunk in its set is still loaded and block-ticking. If not, the line becomes `incomplete` and is scheduled for chunk rechecks at once (see "Scheduling");
   - **`Transaction.getLifecycle() == Lifecycle.NONE`** (`NF:transfer/transaction/Transaction.java:99–109,247–263`). If not, the pulse is **deferred** to the next tick with its original due time.
2. **Count:** `n` = the line's executed-pulse counter, which is then incremented.
3. **Read** each endpoint, in endpoint order, through its caches. Querying a cache re-arms its invalidation.
   - An endpoint whose handler is missing is skipped. So is one whose handler has more than `MAX_HANDLER_SLOTS` (128) slots; it counts as **skipped** in the status line.
   - **That is the supported-size policy.** A crucible's handler has one slot per registered aspect ([crucible, Essentia out](crucible.md#essentia-out)), so an aspect registry of more than 128 entries makes every crucible a skipped endpoint. The server logs that once at world open. The built-in registry has 37.
   - For every non-empty slot, record the aspect X, the amount and `hold(X)`.
   - **Per (X, X-component),** keep the two lowest (hold, endpoint order) sources at distinct positions. Two are kept, so a sink can exclude its own position.
4. **Candidates** `(s, B, X)`, for every endpoint B and every recorded aspect X whose pair (B, X) isn't **stalled**: `s = B.suction(X) > 0`, and the lowest recorded source in B's X-component at a position other than B's has `hold < s`.
5. **The winner:**
   - the highest `s`;
   - the **tied sinks** are the distinct B at that `s`, in endpoint order, and the winner is the one at index **`n mod count`**. This rotation follows the wands spec's ([wands, Flow](wands.md#flow)), but counts the line's own executed pulses, so a line deferred under the global cap still rotates;
   - for that sink, the lowest X.
6. **The move** runs in **one root transaction**, with `need = tube.rate`. The sources are every X-connected endpoint at a position other than B's with X > 0 and `hold(X) < s`, in (hold, endpoint order). For each source, while `need > 0`:
   - **attempt,** in a nested transaction: `e = A.extract(X, need)`, then `i = B.insert(X, e)`.
     - **`i = e`:** commit it; `need −= i`; go on to the next source (a source that ran short just contributes less).
     - **`i ≠ e`:** abort it. If `i > 0`, **retry once in a fresh nested transaction**: `e₂ = A.extract(X, i)`, `i₂ = B.insert(X, e₂)`. Commit **only if `e₂ > 0` and `i₂ = e₂`**; otherwise abort it too. Either way, **the move stops** here.
   - **Then:** commit the root.
7. **Tries and stalls.** A pulse makes at most `MAX_TRIES_PER_PULSE` (4) tries, each its own root transaction.
   - **Something moved:** the line stays awake, the flow payload is sent (see "Rendering") and the pulse ends.
   - **Nothing moved:** the pair (B, X) is marked **stalled**, and selection repeats from step 4 over the pairs not yet stalled.
   - **Stalled pairs are kept** from pulse to pulse, so selection **resumes** where it left off across the four-try boundary: the next pulse's tries go to untried pairs first.
   - **Leaving the stalled set:** a pair leaves it only when a sweep moves something for it (below), when a capability-cache invalidation arrives at its sink (which unstalls that sink's pairs), or when the line is rebuilt (which empties the set).
   - **The sweep.** On every pulse where `n mod tube.idle_every = 0`, and on every pulse of a sleeping line, the **first try** goes to one stalled pair.
     - **Which pair:** the first in (endpoint order, aspect id) **after the line's sweep cursor**, wrapping round. The cursor then moves to it. It persists across pulses and resets only on rebuild, so polling never restarts ahead of pairs not yet retried.
     - **The try:** the pair is retried by steps 5 and 6 at its current suction, if it still has a legal source. If it moves something it is unstalled and the pulse ends; otherwise it stays stalled, and the pulse's remaining tries proceed as normal.

     So a sink that recovers silently is retried within (stalled pairs × `tube.idle_every`) pulses, even while other candidates keep the line awake.
   - **Sleep:** after its tries, a line **sleeps only if nothing moved and no pair outside the stalled set is still a candidate**. Otherwise it stays awake, and the next pulse resumes.

   A slot that reports an amount but never yields, or a sink whose acceptance changed, costs at most one try per pulse until it is stalled, and one sweep try every `tube.idle_every` pulses after that. **Four permanently failing higher-ranked pairs delay a valid sink by one pulse, never more.**

**Vector** (reference implementation). One line, `tube.rate` 8; every jar has capacity 100. The endpoints, in order:
- C, a crucible holding Aer 30, Ignis 5 and Terra 12;
- J1, an empty unlabelled jar;
- J2, an empty jar labelled Ignis;
- J3, an unlabelled jar holding Terra 20;
- T, a tap taking Aer, room 10;
- J4, a jar labelled Terra, holding 98;
- J5, an empty unlabelled jar **behind a gate labelled Aer**.

| Pulse | Move | Tied sinks |
|---|---|---|
| 0 | T ← Aer: C 8 | 1 |
| 1 | T ← Aer: C 2 (8 offered, 2 accepted: aborted, then a fresh retry of 2 committed) | 1 |
| 2 | J2 ← Ignis: C 5 (the source runs dry) | index 0 of {J2, J4} |
| 3 | J4 ← Terra: C 2 (the move stops; J3 isn't tried) | 1 |
| 4 | J3 ← Terra: C 8 | index 1 of {J1, J3, J5} |
| 5 | J5 ← Aer: C 8 (through the gate) | index 2 of 3 |
| 6 | J1 ← Aer: C 8 (Aer is lower than Terra) | index 0 of 3 |
| 7 | J3 ← Terra: C 2 | index 1 of 3 |
| 8 | J1 ← Aer: C 4 | index 0 of {J1, J5} |
| 9 | nothing moved; the line sleeps | |

End: C empty; J1 Aer 12; J2 Ignis 5; J3 Terra 30; T room 0; J4 Terra 100; J5 Aer 8.

**Vector, changed acceptance** (reference implementation). C holds Aer 10. F is a consumer with suction 16 for Aer, whose first insert accepts 2 and every later one 0. J1 is an empty unlabelled jar. Pulse 0:
- F ← Aer: 8 offered, 2 accepted, aborted; the fresh retry of 2 accepts 0 and is aborted. **Stalled.**
- Then J1 ← Aer: C 8.

Aer is conserved: C 2 + F 0 + J1 8 = 10.

**Vectors, stalls and sweeps** (reference implementation). Each is one line with `tube.rate` 8. F, F1 … F4 are consumers with suction 16 for Aer and scripted acceptance; J1 is a jar. The reference counts executed pulses; for a sleeping line, an executed pulse is one of its idle checks.

| Fixture | Pulse | Result |
|---|---|---|
| **Four dead sinks:** C Aer 20; F1–F4 accept nothing, ever; J1 an empty unlabelled jar; `tube.idle_every` 3 | 0 | F1, F2, F3, F4 each stalled (4 tries); J1 untried, so the line **stays awake** |
| | 1 | J1 ← Aer: C 8 |
| | 2 | J1 ← Aer: C 8 |
| | 3 | sweep: F1 still stalled; then J1 ← Aer: C 4 |
| | 4 | nothing moved; the line sleeps (Aer total 20) |
| **Silent recovery:** C Aer 40; F refuses its first insert and accepts everything after; J1 an empty unlabelled jar; `tube.idle_every` 3 | 0 | F stalled; then J1 ← Aer: C 8 |
| | 1, 2 | J1 ← Aer: C 8 each |
| | 3 | **sweep: F ← Aer: C 8; unstalled** |
| | 4 | F ← Aer: C 8 |
| | 5 | F ← Aer: J1 8 (the consumer outranks the jar's hold of 4) (Aer total 40) |
| **Recovery while asleep:** C Aer 12; F as above; J1 labelled Ignis (no candidate) | 0 | F stalled; nothing else; the line sleeps |
| | 1 (its next idle check) | sweep: F ← Aer: C 8; unstalled |
| | 2 | F ← Aer: C 4 (Aer total 12) |

- **Options considered:** every aspect per pulse under a shared budget; proportional splitting.
- **Recommend** one move per pulse. **Why:**
  - bounded work;
  - a priority that labels and suction express legibly;
  - throughput is a stated, tunable gameplay number, as the pain-points research asks (§7: "limits are deliberate gameplay choices, explained in the game").

## Scheduling

A per-level **due-time scheduler**: a priority queue of entries (due tick, line key, line id, generation, epoch), ordered by (due tick, key). Nothing scans every line each tick.

- **Phase:** a line is due on the ticks where `gameTime mod tube.pulse = phase(key)`, with the [crucible's `phase`](crucible.md#boiling-and-the-step). Phases at a pulse of 10 for the crucible spec's five vector positions: (0, 64, 0) → 5; (1, 64, 0) → 0; (0, 64, 1) → 4; (−37, 70, 512) → 9; (29999999, −64, −29999999) → 9.
- **After an executed pulse:**
  - an awake line is rescheduled at its next phase tick;
  - a sleeping one at the phase tick `tube.idle_every` pulses later.
- **Entry kinds:** a `ready` line has **pulse** entries; an `incomplete` line has **recheck** entries.
- **Processing:** at the end of each server tick, entries due at or before now are popped in order, under two per-level budgets:
  - **every pop counts** toward `MAX_SCHEDULER_POPS_PER_TICK` (256), whether the entry is stale, a pulse or a recheck. An entry whose generation or epoch is stale is dropped. A recheck also costs one chunk-state check per chunk it examines (≤ 320);
  - at most **`MAX_PULSES_PER_TICK` (16)** pulses run.

  Entries left over by either budget stay queued with their **original due time**, so they run first next tick. A line has at most one live entry.
- **Waking** a sleeping line bumps its epoch and schedules it at its next phase tick. It doesn't clear the stalled set; only the rules in "The pulse, step 7" do. **Wakes come from:**
  - a capability-cache invalidation at one of its endpoints;
  - the root-commit publication of one of our jars at an endpoint position;
  - `CrucibleEvent(DISSOLVED)` or `CrucibleEvent(CRAFTED)` at an endpoint position. A `neoforge.essentia` listener calls `core` in one line;
  - a tuning `/reload`, which wakes every live line.

  Foreign endpoints that change silently are caught by the idle schedule.
- **`incomplete` lines** have recheck entries, scheduled **immediately** when a build finishes `incomplete` or a `ready` line is demoted at a pulse (step 1). After that they recur at the idle cadence (`tube.idle_every` pulses).
  - **A recheck** examines the line's boundary chunks and its chunk set. Once every one is loaded and block-ticking, **the line is retired first**: its generation is bumped and its membership released. Then its key position is queued for a rebuild, so the queue doesn't discard the key as belonging to a live line.
  - That covers chunks that become ticking, or stop, with no load or unload.
- **No re-entry:** while any pulse or pour of ours runs, no other one starts. A wake or change arriving meanwhile only queues work.

**Options considered:** a ticker per tube, which the pain-points research rejects (§4); purely event-driven, which would never see foreign endpoints. **Why the hybrid:** idle lines cost almost nothing, and correctness never depends on an event arriving.

## Transactions

- **Every root we open closes within the same call:** each pulse try and each pour. So a crucible's transaction-busy window never outlives one try ([crucible, Essentia out](crucible.md#essentia-out)).
- **We open a root only when `Transaction.getLifecycle() == Lifecycle.NONE`** (`NF:transfer/transaction/Transaction.java:99–109,247–263`); otherwise the pulse or pour is deferred (see "The pulse", step 1). `TransactionManager` itself rejects only an active depth, and **permits** a new root during root-commit callbacks (`NF:transfer/transaction/TransactionManager.java:36–43,108–115`; `SnapshotJournal.java:71–85`). Refusing during root closing as well is **our deliberately stricter policy**: no pulse or pour ever starts inside another transfer's publication.
- **The jar's journal:** one snapshot per touched depth, holding the record's aspect and amount. A child's commit stands only while its parent commits, and a root abort restores the record exactly.
- **Transaction-busy:** a jar is busy from its journal's first snapshot until its root closes, on commit or on abort. While busy:
  - label changes are refused;
  - removal settlement waits (see "Removal and drops").
- **On the root's close,** whichever way it went:
  - busy is released, and any pending settlement runs;
  - **only after a commit:** the root-commit **publication** runs. It marks the jar changed and dirty for the coalesced sync, wakes lines touching the jar, and plays `essentia.jar_fill` if the amount rose (at most once per jar per 40 ticks). It is skipped for a removed block entity.

  Publication is never part of correctness.
- **Failures after the commit:**
  - a throwing event listener, sound or wake is caught and logged once, with the position;
  - the remaining publication and cleanup still run;
  - a committed move is never retried, refunded or re-spilled.

### Capability caches

Each endpoint holds two `BlockCapabilityCache`s, one for the essentia handler and one for suction:
- created with the six-argument `BlockCapabilityCache.create(capability, serverLevel, pos, side, isValid, listener)` (`NF:capabilities/BlockCapabilityCache.java:59–67`);
- **`isValid`** is "this line's generation is still live", so the caches of a retired line go quiet and are released with it. A live line holds them strongly;
- **the listener** only marks the endpoint stale, unstalls that endpoint's pairs, and wakes the line. It never touches the level (`BlockCapabilityCache.java:38–47`);
- **`getCapability()`** is called again at the next read, which re-arms notification (`:38–47`); its implementation is at `:131–149`. A null result is cached like any other.

An Alembic holds the same pair for its base (handler and suction, side-matched) and its target, valid while the alembic is loaded.

## Consumers

- **The Node Tap** ([wands, Cultivation](wands.md#cultivation)) is a sink endpoint. It is in `vftv:essentia_endpoints`, has suction on its handler's faces, and holds at rank 0. Lines and an alembic directly above it feed it. Rolling back the node's state is the tap's own journal's job.
- **[Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)** and **[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)** move essentia only through the essentia capability, in roots closed within the call (see the contracts).

**Why:** one transfer path makes conservation a property of the handlers ([aspects, Contracts](aspects.md#contracts-on-sibling-specs), non-amplification).

## Multiplayer and automation

- **Lines are unowned.** The research gates belong to recipes, and anyone can read a status line.
- **The protection promise is exactly** vanilla's spawn protection, `mayInteract` and `RightClickBlock` for every hand path (labels, shears, faces, valves, status), plus vanilla's placement and `BreakBlockEvent`. A cancelled event or a denied use changes nothing.
- **Stated limitation:** capability access isn't a player action. A line built against someone's exposed jar or crucible can draw from it, just as a hopper draws from a chest.

| Path | Behaviour |
|---|---|
| Players | labels, shears, faces, valves, status, placement and breaking, as above |
| Redstone | valves only |
| Hoppers, item pipes | jars have no item capability; a jar item is an ordinary item |
| Pistons | refuse every block here (`PushReaction.BLOCK`, and all have block entities) |
| Other mods' essentia handlers | reached through the capabilities on the touched face; they act as sinks only if they offer suction |
| Golems, infusion | through the capabilities, under the contracts |

**No custom client-to-server payload.** Every input arrives through vanilla use, placement and breaking packets, validated as such.

## Inspection and teaching

Every number a decision rests on is shown exactly, live, and never in prose.

**Status lines** are messages on the [status line](accessibility.md#r5-the-status-line), built on the server from live values; it wraps, so a page naming two long block names (the alembic's ends) is always shown whole. Every refusal under "Labels" goes there too. They appear on a use with **both hands empty while sneaking**: vanilla skips the block when a sneaking player holds anything (`MC:server/level/ServerPlayerGameMode.java:398–399`).
- **Paging:** each such use shows the **next page**, one line per use.
- **The cursor** is per player: the block, the page and the game time. It resets to page 0 after 100 ticks, or on a different block.
- Aspect names are masked for the viewer: `essentia.vftv.unheard` for an undiscovered aspect, and `essentia.vftv.missing` for a missing one.

| Block | Page 0 | Page 1 | Page 2 |
|---|---|---|---|
| jar | `status.vftv.essentia_jar.contents` (aspect, amount, capacity), or `.empty` (capacity), or `.unreadable` | `status.vftv.essentia_jar.ranks` (label or `essentia.vftv.label_none`, suction, hold) | |
| tube | `status.vftv.essentia_line.<state>` (members, `MAX_MEMBERS`, endpoints, `MAX_ENDPOINTS`) | `status.vftv.essentia_tube.label` (the tube's label, masked; or `essentia.vftv.label_none`; or `essentia.vftv.label_unreadable` for a dormant or missing-aspect label) | `status.vftv.essentia_line.last` (aspect, amount, ticks ago), or `.no_moves` |
| valve | as the tube's page 0 | `status.vftv.essentia_line.last`, or `.no_moves` | `status.vftv.essentia_line.faults` |
| alembic | `status.vftv.alembic.ends` (base, target: block names, or `essentia.vftv.nothing`) | `status.vftv.alembic.next` (label or `essentia.vftv.label_none`, next aspect or `essentia.vftv.nothing`) | |

- **A tube has a page 3:** `status.vftv.essentia_line.faults` (deaf arms, stalled pairs, skipped endpoints).
- **A valve that isn't passing** has two axis ends, and may border two lines. It reports the line at **the clicked axis end**, when the clicked face is one of its axis faces. On a lateral click it reports the line at the **first occupied axis end in Direction order**. With no line at that end, it reports `.building` with zero counts.
- **No HUD layer.** A jar's identity and fill level are visible in the world, and exact numbers are one use away. That avoids a second HUD layout ([crucible, The HUD](crucible.md#the-hud)). The status line is the localisation spec's shared layer, not one of essentia's.
- **The fork's strike** on any block here reads that block's own aspects, never its contents ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)).
- **The book:** the research spec's fact grammar has a source **`essentia`** (amended in this change), `{fact:essentia:<key>}`, over the synced `EssentiaRules` ([research, Prose and fact references](research.md#prose-and-fact-references)). So pages state capacity, ranks, throughput, the idle delay and the caps live.

## Rendering and sound

### The jar

A block-entity renderer, within the fixed block-entity view distance. **Our draw code allocates nothing per frame.**

- **Liquid:** one cuboid `[4,1,4]–[12, 1 + h, 12]` px, with **`h = min(7, ⌈7 × amount / capacity⌉)`** under the client's `EssentiaRules` capacity. It uses the sprite `vftv:block/essentia_jar_liquid` (block atlas, translucent: liquids are one of the style's translucencies, [art style §3](art-style.md#3-resolution-and-pixel-rules)), tinted with the aspect's colour, at the block's packed light.
  - It is drawn for neither an empty nor a dormant jar.
  - A missing aspect uses `vftv:aqua`'s colour.
  - **Vectors** (reference implementation), amount → `h` px:
    - at capacity 100: 0 → 0, 1 → 1, 14 → 1, 15 → 2, 50 → 4, 99 → 7, 100 → 7;
    - over a lowered capacity of 16: 1 → 1, 16 → 7, 100 → 7.
- **The glyph,** on the `facing` face, 0.01 px outside the glass: a 6×6 px quad centred horizontally, from y = 2 to y = 8 px.

| Jar | Draws |
|---|---|
| unlabelled, holding X | X's glyph |
| labelled X, holding X | the label paper (an 8×8 px quad from `vftv:block/essentia_label`, from y = 1 to y = 9, just behind the glyph), then X's glyph |
| labelled X, empty | the label paper, then X's glyph |
| unlabelled, empty | nothing |
| dormant | nothing |

  - **The glyph** is the aspect's own icon (`vftv:aspect/<id>`, as the wands spec's motes draw it, [wands, Rendering and sound](wands.md#rendering-and-sound)).
  - **An undiscovered aspect** shows the unheard icon ([aspects, Icons](aspects.md#icons)).
  - **A missing id** shows the crucible's `vftv:hud/crucible_missing`.
  - **All three sprites are resolved from the GUI sprite atlas**, where the aspect icons live (`assets/vftv/textures/gui/sprites/`, [aspects, Icons](aspects.md#icons)). Only the liquid and paper sprites come from the block atlas.
- **Identity never rests on the tint** ([aspects, Colours](aspects.md#colours)).

### The tube band

A labelled tube's block-entity renderer draws a **band**: the box `[4,4,4]–[12,12,12]` px around the core.
- **Faces with an arm or cap** use `vftv:block/essentia_tube_band_ring` (an 8×8 paper frame around a 6×6 transparent window that the arm passes through).
- **Faces that are `none`** use `vftv:block/essentia_tube_band` (solid paper), with the label's glyph as a 6×6 px quad on top.
- **Each `blocked` cap** also carries the glyph, 6×6 px, on its outer face at the block boundary.
- **There is always a glyph face.** A labelled tube can never have all six faces `connected` (see "Connections, Labelled tubes keep a glyph face"), so at least one `none` band face or `blocked` cap always shows the glyph, for its whole life. Its status page 1 names the label too.
- **A dormant label** draws `vftv:hud/crucible_missing` in place of a glyph.
- **Glyph rules** as for the jar. Unlabelled tubes have no renderer.

### Flow motes

- **The payload:** S2C **`vftv:essentia_flow`**, `{line key: long, source: BlockPos, sink: BlockPos, aspect: id ≤ 256 chars, amount: 1–1000}`. It names the **first source that contributed, and its contribution**; the other contributions of a multi-source move are omitted, and that omission is cosmetic.
  - At most one per line per pulse, sent to players tracking the sink's chunk through the networking seam's `toTracking(chunk)` ([architecture, Seams](architecture.md#seams)).
  - The decoder is bounded and rejects anything out of range.
- **The path:** the client runs a BFS over its own tube and valve states and labels, from the member next to the source to the member next to the sink. It uses Direction order, visits at most `MAX_FLOW_PATH` (256) members, and **honours the aspect's gates**.
- **The mote:** one per payload. It draws the **aspect's icon** (masked as above; never a tint-only sprite) at `mote.size`, travelling the path at `mote.speed`.
- **Caps:** at most `MAX_LIVE_MOTES` (64) alive client-wide; further ones are dropped. None at the "minimal" particle setting.
- **No path, or chunks the client lacks,** means no mote. Motes are cosmetic, and decide nothing.

### Sound

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:essentia.jar_fill` | 3 | a jar's root-commit publication when its amount rose | at the block's centre, to everyone in range; `SoundSource.BLOCKS`, volume 1.0, pitch 1.0; at most once per jar per 40 ticks |
| `vftv:essentia.label_apply` | 1 | a label applied | likewise, no cooldown |
| `vftv:essentia.label_cut` | 1 | a label cut | likewise |
| `vftv:essentia.valve_open`, `vftv:essentia.valve_close` | 1 each | a hand or redstone toggle that changes passing | likewise |
| `vftv:essentia.alembic_drip` | 3 | a pour that moved essentia | likewise; at most once per alembic per 40 ticks |

**Files:** `assets/vftv/sounds/essentia/jar_fill_0.ogg` … `jar_fill_2.ogg`, `label_apply.ogg`, `label_cut.ogg`, `valve_open.ogg`, `valve_close.ogg`, `alembic_drip_0.ogg` … `alembic_drip_2.ogg`. They are in the Stage 2 register ([art style §7](art-style.md#7-sound-palette)): bright and warm, with clean partials. The `sounds.json` entries carry no volume or pitch overrides and name the subtitle keys `subtitles.vftv.essentia.<event>`. Placement and breaking use the blocks' sound types.

## Authored content

The content ticket delivers authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen, which writes the resources into `src/generated/resources`. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails its recipe type's rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/essentia/recipes.json       the six recipes this spec owns
content/essentia/client.json        mote numbers (client)
content/lang/en_us/essentia.json    every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `recipes.json` | `vftv:essentia_recipes/1` | each recipe `<ns>:<path>` → `data/<ns>/recipe/<path>.json`, its body unchanged except the [compatibility spec's ingredient widening](compat.md#consumed) |
| `client.json` | `vftv:essentia_client/1` | `assets/vftv/essentia/client.json` |
| `lang/<lang>/essentia.json` | `vftv:lang/1` | its `strings` object merged into `assets/vftv/lang/<lang>.json`; a duplicate key across all `content/lang` files fails datagen |

### Recipes

`{"schema": "vftv:essentia_recipes/1", "recipes": {<id>: <body>}}`. **Exactly these six,** each required:

| Id | Type | Research | Result | Other rules |
|---|---|---|---|---|
| `vftv:essentia_jar` | `vftv:arcane`, `kind` `ordinary` or omitted | `vftv:essentia_jars` | `vftv:essentia_jar`, count 1–4, no components | |
| `vftv:alembic` | likewise | `vftv:essentia_jars` | `vftv:alembic`, count 1 | |
| `vftv:essentia_tube` | likewise | `vftv:essentia_tubes` | `vftv:essentia_tube`, count 4–16 | |
| `vftv:tube_valve` | likewise | `vftv:essentia_tubes` | `vftv:tube_valve`, count 1–4 | |
| `vftv:label` | `minecraft:crafting_shapeless` | none | `vftv:label`, count 1–8, no components | ingredients **exactly** `minecraft:paper` and `minecraft:ink_sac`, one each |
| `vftv:label_blank` | `minecraft:crafting_shapeless` | none | `vftv:label`, count 1, **no components** | ingredients exactly one `vftv:label`. The result carries no `vftv:label`, so it is blank, and any label (written or blank) crafts to one blank label. |

- **The four arcane bodies** follow the [arcane recipe type](arcane.md#the-recipe-type) exactly. Their `vis` uses only the six built-in primals, each ≤ the Wellspring's per-aspect amount in `content/nodes/types.json` (32 today; the lint reads it), as for the crucible's vessel.
- **Ingredients** are any vanilla `Ingredient`s; `vftv:resonant_brass_ingot` is allowed, because both entries descend from `vftv:resonant_brass` (see "Stage").
- **The vanilla bodies** use vanilla's shapeless codec: `category` (default `misc`), `ingredients` and `result`.

**Rejected:**
- any id other than these six, or any of them missing;
- another `type`, `research` or `kind`;
- a result id, count or component outside its row;
- any field outside its type's body;
- a `vis` key that isn't a built-in primal, or a value over the Wellspring bound;
- label ingredients other than exactly paper and an ink sac;
- a `vftv:label_blank` with any ingredient other than one `vftv:label`.

A complete file (counts, ingredients and costs illustrative; the content ticket chooses them within the rules):

```json
{"schema": "vftv:essentia_recipes/1", "recipes": {
  "vftv:essentia_jar": {
    "type": "vftv:arcane", "research": "vftv:essentia_jars", "kind": "ordinary",
    "ingredients": ["minecraft:glass", "minecraft:glass", "vftv:resonant_brass_ingot"],
    "result": {"id": "vftv:essentia_jar", "count": 2},
    "vis": {"vftv:aqua": 8, "vftv:terra": 6}},
  "vftv:alembic": {
    "type": "vftv:arcane", "research": "vftv:essentia_jars", "kind": "ordinary",
    "ingredients": ["minecraft:glass_bottle", "vftv:resonant_brass_ingot", "minecraft:copper_ingot"],
    "result": {"id": "vftv:alembic", "count": 1},
    "vis": {"vftv:aqua": 6, "vftv:ignis": 6}},
  "vftv:essentia_tube": {
    "type": "vftv:arcane", "research": "vftv:essentia_tubes", "kind": "ordinary",
    "ingredients": ["minecraft:glass_pane", "vftv:resonant_brass_ingot", "minecraft:copper_ingot"],
    "result": {"id": "vftv:essentia_tube", "count": 8},
    "vis": {"vftv:aqua": 4, "vftv:ordo": 4}},
  "vftv:tube_valve": {
    "type": "vftv:arcane", "research": "vftv:essentia_tubes", "kind": "ordinary",
    "ingredients": ["vftv:essentia_tube", "minecraft:lever"],
    "result": {"id": "vftv:tube_valve", "count": 1},
    "vis": {"vftv:ordo": 4}},
  "vftv:label": {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": ["minecraft:paper", "minecraft:ink_sac"],
    "result": {"id": "vftv:label", "count": 4}},
  "vftv:label_blank": {
    "type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": ["vftv:label"],
    "result": {"id": "vftv:label", "count": 1}}
}}
```

### Client numbers

`{"schema": "vftv:essentia_client/1", "mote": {"speed": <0.05–2.0>, "size": <0.0625–1.0>}}`. Both fields are required, in blocks per tick and in blocks.

```json
{"schema": "vftv:essentia_client/1", "mote": {"speed": 0.25, "size": 0.25}}
```

- **Rejected** in `content/`: a missing or unknown field, a wrong type, or a value out of range.
- **At runtime**, a resource pack may override `assets/vftv/essentia/client.json`. An invalid override is ignored with a logged error, the built-in file is used, and the values are re-read on every resource reload ([research, Overlay numbers](research.md#overlay-numbers)).

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text. Its messages teach labels as locks and gates, suction ranks, hold, one move per pulse, the idle delay and the caps.

| Key | Args | Sense |
|---|---|---|
| `block.vftv.essentia_jar`, `block.vftv.alembic`, `block.vftv.essentia_tube`, `block.vftv.tube_valve` | 0 | "Essentia Jar", "Alembic", "Essentia Tube", "Valve" (also their items' names) |
| `item.vftv.label` | 0 | "Label" |
| `tooltip.vftv.label.blank` | 0 | "Blank" |
| `tooltip.vftv.label.written` | 1 | "%s" (the masked aspect name) |
| `tooltip.vftv.essentia_jar.holds` | 2 | "Holds %s of %s" (amount, aspect) |
| `tooltip.vftv.essentia_jar.labelled` | 1 | "Labelled %s" |
| `tooltip.vftv.essentia_jar.unreadable` | 0 | "I can't read what's in it." |
| `message.vftv.essentia.unreadable` | 0 | "I can't read what's in it, so I won't touch it." |
| `message.vftv.essentia.busy` | 0 | "Not while it's being drawn on." |
| `message.vftv.essentia.label_unreadable` | 0 | "I can't read this label." |
| `message.vftv.essentia.cut_label_first` | 0 | "It's already labelled. I'd have to cut that off first." |
| `message.vftv.essentia.nothing_to_name` | 0 | "There's nothing to name it by." |
| `message.vftv.essentia.label_mismatch` | 0 | "That isn't what's inside." |
| `message.vftv.essentia.line_too_long` | 1 | "Too long a line to carry (%s)." |
| `message.vftv.essentia.no_base` | 0 | "It needs a crucible to hang on." |
| `status.vftv.essentia_jar.contents` | 3 | "%s: %s of %s" |
| `status.vftv.essentia_jar.empty` | 1 | "Empty, holds %s" |
| `status.vftv.essentia_jar.unreadable` | 0 | "I can't read what's in it." |
| `status.vftv.essentia_jar.ranks` | 3 | "Label: %s · pulls at %s · holds at %s" |
| `status.vftv.essentia_line.building`, `.ready`, `.asleep`, `.incomplete`, `.overgrown` | 4 each | "Line (building/flowing/asleep/partly unloaded/too long): %s of %s tubes, %s of %s vessels" |
| `status.vftv.essentia_line.last` | 3 | "Last: %s %s, %s ticks ago" |
| `status.vftv.essentia_line.no_moves` | 0 | "Nothing has moved yet." |
| `status.vftv.essentia_line.faults` | 3 | "Deaf arms %s · stalled %s · unreadable vessels %s" |
| `status.vftv.essentia_tube.label` | 1 | "This tube's label: %s" (masked) |
| `status.vftv.alembic.ends` | 2 | "From %s into %s" |
| `status.vftv.alembic.next` | 2 | "Label: %s · next: %s" |
| `essentia.vftv.unheard` | 0 | "something unheard" |
| `essentia.vftv.missing` | 0 | "an aspect I no longer know" |
| `essentia.vftv.nothing` | 0 | "nothing" |
| `essentia.vftv.label_none` | 0 | "none" |
| `essentia.vftv.label_unreadable` | 0 | "a label I can't read" |
| `subtitles.vftv.essentia.jar_fill`, `.label_apply`, `.label_cut`, `.valve_open`, `.valve_close`, `.alembic_drip` | 0 | "Jar fills", "Label pressed on", "Label snipped", "Valve opens", "Valve closes", "Alembic drips" |

**Used, not declared here:** every aspect's name belongs to the aspects spec's lang. Status lines are one status-line message per page, so no page joins two keys.

```json
{"schema": "vftv:lang/1", "strings": {
  "block.vftv.essentia_jar": "Essentia Jar",
  "message.vftv.essentia.label_mismatch": "That isn't what's inside.",
  "status.vftv.essentia_jar.ranks": "Label: %s · pulls at %s · holds at %s",
  "status.vftv.essentia_line.asleep": "Line asleep: %s of %s tubes, %s of %s vessels",
  "subtitles.vftv.essentia.jar_fill": "Jar fills"
}}
```

**Rejected:**
- a key outside the table;
- a required key missing in `en_us`;
- a placeholder count that differs from the table;
- an empty string;
- a duplicate key, including any other spec's key redeclared here (`message.vftv.jar.*` is the Bell Jar's, [wands, Lang keys](wands.md#lang-keys)).

## Events

**`EssentiaEvent`**, a NeoForge post-event in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)). It is posted on the server **after the settled state**, through a publisher seam that `neoforge` implements, so `core` never imports a NeoForge event, capability or transaction type. Its payload records live in `api` and are **immutable**: aspect maps are unmodifiable and in ascending id. The payload shape and failure rules are the [API spec's](api.md#events).

| Kind | Payload |
|---|---|
| `SPILLED` | level and position (a destroyed item's position, or the jar's); the aspects spilled, as `ResourceKey` → amount (missing ids included), totalling 1 … 640,000; the cause, `api`'s `SpillCause.DESTROYED` (a filled jar item destroyed) or `REMOVED` (a jar's unsettled excess) |

- **Once per spill,** under the ownership rules in "Removal and drops" and "Loss": the item claims its contents before posting, and a settlement runs once per removal.
- **Nothing fires** for a move, a pour, a label, a valve, a rebuild or a stall. No sibling needs them, and they are hot paths. Addons can read handlers.
- **A throwing listener** is logged once. The spill stands, and the remaining cleanup runs (see "Transactions").

**Plain `core` services:** `EssentiaLines.status(level, pos)` (the status pages' values) and `EssentiaLines.of(level, pos)` (a read-only view of a live line: state, counts, last move). They are **internal in v1** ([API spec, Services](api.md#services)): addons read and move essentia through the two capabilities. `EssentiaKeys` in `api` holds the `#vftv:essentia_endpoints` and `#vftv:alembic_bases` tag keys, the capability ids and the component ids.

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| jar | block entity `{"format": 1, "aspect"?: <id>, "amount"?: 1–10000, "label"?: <id>}`: aspect and amount both present or both absent; zero never written | trackers: the full state in the chunk's initial block-entity data (`getUpdateTag`), then the coalesced block-entity update |
| tube, alembic | block entity `{"format": 1, "label"?: <id>}` | likewise, on a label change only |
| valve | block entity with no data | never |
| faces, `open`, `powered`, `axis`, `facing` | block state | as blocks are |
| jar item | `vftv:jar_contents` (format 1, or a dormant raw tag), `vftv:label` (a native scalar id) | as items are |
| lines, queues, scheduler, caches, loaded set, status cursors | per-level or per-player memory | never |
| essentia rules | the tuning record and the fixed limits | **`EssentiaRules`**, S2C to every player through `OnDatapackSyncEvent` (join and `/reload`) |
| flow motes | `vftv:essentia_flow` | trackers of the sink's chunk |

- **Coalesced updates:** every jar, tube and alembic change marks its block entity in a per-level dirty set, flushed once per tick after the level ticks. That is **at most one update packet per block entity per tick**, by the [wands spec's flush](wands.md#sync-and-state). The set holds block entities, not positions, and skips any that are removed or replaced, so a stale entry never updates a replacement.
- **`EssentiaRules`** is a fixed record: every key under "Tuning", the derived `tube.idle_ticks`, and the `limits.*` values the research grammar lists (`max_members`, `max_endpoints`, `max_suction`, `storage_max` and `max_handler_slots`). The client's jar renderer, tooltips and book facts read it, so an idle jar's render follows a capacity change with no block-entity resend. Client caches are invalidated when it arrives and on resource reload.
- **Formats.** A block-entity record with an unknown format, or out of bounds, is **kept as its raw tag and written back unchanged**, with one log line. Out of bounds means: an amount outside 1–10,000; aspect and amount not both present; a label that isn't an id; a readable label that differs from the aspect.
  - **A dormant jar** has no handler and no suction, refuses labels and shears, draws no liquid or glyph, and reads `.unreadable`. On removal it drops a plain jar carrying the raw tag in its dormant component.
  - **A dormant tube** is a gate admitting nothing; **a dormant alembic** pours nothing.
  - Decoding checks the **format bounds**, never the capacity in force.
- **Missing aspect ids** are kept. They are shown with the missing sprite and their exact amount, and never matched, moved or poured. A label naming one admits nothing. Restoring the pack restores them.
- **Bounded codecs:**
  - the records as above;
  - ids ≤ 256 characters;
  - `EssentiaRules` a fixed record;
  - `vftv:essentia_flow` as under "Flow motes";
  - the tuning file and the client file.

**Reloads:**

| Change | Effect |
|---|---|
| Tuning (`/reload`) | a new record; `EssentiaRules` resent (book facts re-resolve; jar renders follow); every live line woken and rescheduled under the new `tube.pulse` |
| Tags (`/reload`) | every line frozen (retired) at once; the refresh queue for tubes, valves and Alembics; rebuilding paused until the refresh finishes (see "Tags") |
| Recipes (`/reload`) | the arcane spec's snapshot handles the four arcane bodies ([arcane, Recipe sync and reload](arcane.md#recipe-sync-and-reload)); vanilla handles the labels |
| The aspect or research registries | need the world reopened |
| Resource reload | sprites, `client.json` and the masked names rebuild |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/essentia.json`**, loaded on reload into an immutable record in `core.essentia` and written by datagen from that record's defaults. Every key is optional and defaults to the value below. An unknown key, a wrong type or a value out of range makes the file invalid, and so does a `suction.jar` that isn't below `suction.jar_labelled`. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client receives it in `EssentiaRules`.

| Key | Value | Range | Notes |
|---|---|---|---|
| `jar.capacity` | 100 | 16–10,000 | units per jar |
| `suction.jar` | 4 | 1–15 | unlabelled rank |
| `suction.jar_labelled` | 8 | 1–15 | labelled rank; must exceed `suction.jar` |
| `tube.pulse` | 10 ticks | 1–200 | between a line's pulses |
| `tube.rate` | 8 | 1–1,000 | most units per move |
| `tube.idle_every` | 10 pulses | 1–100 | a sleeping line's check interval |
| `alembic.interval` | 20 ticks | 1–200 | between pours |
| `alembic.rate` | 4 | 1–1,000 | most units per pour |

**Other homes:**
- the endpoints and alembic bases: the block tags `vftv:essentia_endpoints` and `vftv:alembic_bases` (packs may extend them);
- the tap's suction: `cultivate.suction` in the wands spec's `data/vftv/tuning/vis.json`;
- recipes: `content/essentia/recipes.json`, or packs;
- `mote.speed` 0.25 and `mote.size` 0.25: `content/essentia/client.json` → `assets/vftv/essentia/client.json`, overridable by resource packs.

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`EssentiaLimits`** in `api`:

| Constant | Value |
|---|---|
| `MAX_SUCTION`, `STORAGE_MAX` | 128, 15 |
| `MAX_MEMBERS`, `MAX_ENDPOINTS` | 256 and 64 per line |
| `MAX_HANDLER_SLOTS` | 128 per endpoint |
| `MAX_TRIES_PER_PULSE` | 4 |
| `MAX_PULSES_PER_TICK` | 16 per level |
| `MAX_SCHEDULER_POPS_PER_TICK` | 256 per level, stale entries and rechecks included |
| `MAX_TOPOLOGY_WORK_PER_TICK` | 4,096 units per level (pops, visits, finalisation, cleanup; see "Rebuilding") |
| `MAX_FACE_REFRESHES_PER_TICK` | 64 per level (tubes, valves and Alembics) |
| `MAX_JAR_AMOUNT` | 10,000 (the format bound, and the capacity's ceiling) |
| `MAX_ESSENTIA_SPILL` | 640,000 (a stack of 64 full jars) |
| `MAX_LIVE_MOTES`, `MAX_FLOW_PATH` | 64 (client), 256 |
| `MAX_ID_CHARS` | 256 |

**Also fixed in code:**
- the suction rule and bands;
- the pulse's order and its rotation;
- the phase function;
- the endpoint order;
- the status pages and their 100-tick cursor reset.

**Fixed feedback constants:**
- the 40-tick cooldowns on `jar_fill` and `alembic_drip`;
- the 6-px glyph, the 8-px paper and the 7-px liquid span;
- the band box `[4,4,4]–[12,12,12]`;
- playback volume and pitch 1.0.

## Performance

**Heavy-base workload:**
- **8 lines of 64 tubes,** each with: 1 boiling crucible from the [crucible spec's workload](crucible.md#performance) (its fixture doses the pools, so supply is sustained); 12 jars (6 labelled); 2 gates; 1 valve on a redstone clock toggling every 40 ticks; and 1 Node Tap. **The fixture sustains both ends:** it empties the jars every 30 s and resets each tap's node to an uncultivated state every 30 s, so every line keeps moving.
- **One line at the caps:** 256 members, 64 endpoints, 8 gates, supplied and drained the same way, so it always moves.
- **4 alembics** under crucibles, into jars the fixture empties.
- **4 players** within view, with motes on; one join near the base.

**Share:** server ≤ 0.04 ms mean, ≤ 0.2 ms p99, measured in the combined stress world. That includes the scheduler, reads, selection, transfers and journals, rebuilds, chunk-state checks, cache calls, publication, payloads and sync. Client: 0 with no jar, labelled tube or mote in view; ≤ 0.05 ms p50 with the workload's view, including the renderers' extraction and submission.

**Measured separately as load scenarios** (reported):
- a chunk of 1,024 tubes loading (the topology budget at its cap);
- a 10,000-tube `overgrown` field, its full traversal and its overgrown record, then cut into pieces;
- **stale-entry floods:** 10,000 stale membership entries after a large retirement, and 5,000 stale scheduler entries after a burst of wakes;
- 500 sleeping lines, and 500 `incomplete` lines rechecking;
- 64 lines due in one tick (the pulse cap);
- a line whose endpoints each expose `MAX_HANDLER_SLOTS` slots;
- an aspect registry of 128 entries;
- a tag `/reload` with 2,048 loaded tubes (lines frozen through the refresh backlog);
- a tuning `/reload`.

**Hot paths:**

| When | Work |
|---|---|
| Per tick, per level | **the scheduler:** ≤ 256 pops (stale entries and rechecks included, each recheck ≤ 320 chunk-state checks), of which ≤ 16 run pulses. **Topology:** ≤ 4,096 units (queue pops, member visits, finalisation, stale-entry cleanup). **Refresh:** ≤ 64. One phase compare per loaded alembic. The dirty flush: ≤ 1 packet per block entity. |
| Per pulse | ≤ 4 tries, the sweep's included. The read: ≤ 64 endpoints × ≤ 128 slot reads, plus one `hold` per non-empty slot. Selection: ≤ 64 endpoints × A aspects suction queries, A ≤ the distinct aspects read; candidate feasibility in O(1) each from the per-(aspect, component) two-lowest table; union-find over ≤ 256 nodes per aspect, cached for the generation. The move: ≤ 64 sources × ≤ 4 transfer calls. One chunk-state check per chunk in the set (≤ 320). |
| Per pour | one tag check; ≤ 128 slot reads; one `hold` and one suction query per aspect read; ≤ 4 transfer calls |
| Per rebuild | spread across ticks by the topology budget. A line within the caps finalises O(members + 2 × endpoints) units; an overgrown line finalises only its overgrown record (no nodes, no endpoints, no caches). |
| Packets | ≤ 1 block-entity update per block entity per tick; ≤ 1 flow payload per line per pulse; one `EssentiaRules` per player per join or reload |

- **Allocation:** **zero** in the selection kernel (the model's preallocated arrays, per line). **Bounded per move:** one journal snapshot per touched depth per jar, one payload, and at most one update tag per changed block entity. **Bounded per rebuild:** the line's arrays and its caches. The byte ceilings are calibrated by the build and stored in its committed allocation-test fixture. **Zero per frame** in our renderers' draw code (allocation-budget tests).

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **the pulse:** every vector above. Also:
  - **equal ranks never trade;**
  - a full jar never drains into an empty peer;
  - rotation by the executed-pulse counter (a line run at every second due tick still rotates);
  - the strict inequality at equality;
  - **the changed-acceptance retry** (no essentia lost or created, under any scripted acceptance);
  - a slot that reports an amount but never yields (stalled, then the next candidate);
  - the 4-try cap;
  - **the stall and sweep vectors:** four dead higher-ranked sinks delay a valid jar by exactly one pulse; a silently recovered sink is retried by the sweep while other candidates keep the line awake, and also while it sleeps; the sweep cursor persists and wraps, and never restarts ahead of untried pairs; a cache invalidation unstalls only its sink's pairs; a line never sleeps while an unstalled candidate remains;
  - multi-source moves with a short source;
  - the feasibility table matching a brute-force O(E²A) search on random lines;
- **reachability:** segments and gates; adjacent gates of different aspects; a dormant or missing-aspect gate admitting nothing; endpoints on different nodes of one position;
- **endpoints:** (position, side) identity; a crucible touched above and beside (one deaf arm, one endpoint); no self-moves across sides; ordering;
- **the carry:** every vector above, and a **property test** over random nested commit and abort trees with aspect changes. The carry never exceeds any settled outcome, and carried plus spilled always equals settled;
- **the alembic choice:** labelled, unlabelled with ties, and targets without suction; **an equal-rank pair rejected** (a base holding X at rank 4 over a target with suction 4 for X); **a lower-suction but legal alternative chosen** over a higher-suction aspect the base holds at a rank too high to move;
- **admission:** only mutually connected neighbours count; a valve counts only through its axis and only if it would pass; a perpendicular 256-member line beside a valve doesn't block opening it; a neighbour joined on two faces is counted once;
- **the labelled-tube normalisation:** every combination of six neighbours leaves a `none` or `blocked` face, and the first `connected` face in Direction order is the one blocked;
- **the liquid height:** every vector above;
- **the handler:** every method row; **an empty resource or a negative amount throws `IllegalArgumentException` from all four overloads**; invalid indices, mismatches and each unavailable state return 0 without throwing; nested transactions (a child commit undone by a root abort; one snapshot per touched depth);
- **codecs:** every block-entity record (format 1, every bound, zero never written, label and aspect consistency, the raw-tag round trip of dormant records, missing ids preserved); `vftv:jar_contents` with the dormant fallback; `vftv:label`; `EssentiaRules`; `vftv:essentia_flow` and its bounds; the tuning file (each key's range, the jar-rank ordering, unknown keys); the client file; every `content/` schema and every rejection rule above.

**GameTest:**
- **faces:**
  - the endpoint tag; toggling by hit point, arm against core; deaf arms; valves by hand and by redstone; opening refused at the cap;
  - **the sixth connection to a labelled tube** (placing a sixth neighbour leaves a glyph-bearing `blocked` cap, and labelling a six-connected tube blocks its first face);
  - **a tag `/reload` that removes a live endpoint and an Alembic's base during a refresh backlog**: no transfer or pour happens between the reload and the refresh, and the Alembic breaks when refreshed;
- **topology:**
  - placement refused at the cap; an `overgrown` line built by `/fill` stays inert, **and recovers when cut** to under the cap; a valve-only line discovered after a chunk load;
  - **a line built `incomplete`** (its boundary chunk not yet loaded) is rechecked at once and recovers when the chunk loads;
  - a line across a **loaded but non-ticking** chunk moves nothing and loads nothing, then runs after promotion with no unload or reload; **demotion** without unloading stops it, and promotion again recovers it;
  - dirty graphs waiting on the topology budget; a BFS or finalisation restarted by a change mid-build;
  - **a large overgrown completion** (no nodes or caches built) and **stale-entry floods**, each within its per-tick budget;
- **the pulse:** the vector world end to end; a gate; rotation under the global cap; **scheduler fairness** (64 lines due in one tick: the deferred ones keep their due time and run first next tick, and none starves); **capability caches re-arming** after an invalidation (a replaced jar is seen at the next pulse); a stale cache across a block replacement reads empty;
- **transactions:**
  - a crucible extracted mid-pulse while a bucket is refused, using the crucible's busy state;
  - a pulse deferred while another transaction is open;
  - **removal mid-transaction** of a jar, with commit and with abort (spill exactly the excess, never a duplicate);
  - **a drop callback that tries to extract after the reservation** (it gets 0);
  - a cancelled break releasing the reservation;
  - **external drop loss:** with `doTileDrops` off, and with a `BlockDropsEvent` listener that cancels the drops, the spill is exactly the settled contents minus the reserved carry;
  - roots refused at every lifecycle state other than `NONE`, including during another transfer's root-commit callbacks;
- **labels:** every row of the label table, including consumption, the identical no-op, cut-first, mismatch, a missing-aspect label, the busy refusal, creative non-consumption and a full inventory on cut and copy; the off-hand precedence; denied uses and cancelled events changing nothing;
- **items and loss:** a jar broken full in survival and in creative; destroyed in lava (one `SPILLED` of count × contents, **once**, even with a listener that hurts the entity again); a stack of 64 destroyed (the 640,000 bound); replaced by `/setblock` (`REMOVED`); flag 256 (nothing); stacking and placing filled jars conserves; a filled jar thrown into a crucible is refused `PROTECTED`;
- **the alembic:** into a jar and into a tap; guarded against an unloaded or non-ticking base or target; a changed-acceptance retry; breaks when its base goes; a pack-extended base (a labelled jar) never pours downhill or between equal ranks;
- **the supported-size policy:** a **129-aspect registry fixture**, in which every crucible is a skipped endpoint for lines and gives an Alembic nothing to pour, with the world-open log line and the status count;
- **inspection:** a non-passing valve between two lines reports the line at the clicked axis end, and at the first occupied end on a lateral click; a labelled tube's label page, masked;
- **notifications:** a throwing `EssentiaEvent` listener and a throwing wake: the spill or commit stands, cleanup runs, nothing is retried;
- **dormancy:** dormant jars, tubes, alembics and item components survive save and reload unchanged; a dormant gate stays closed;
- **sync:** ≤ 1 update per block entity per tick under the workload; after a tuning `/reload`, idle jars render at the new capacity and lines reschedule; **real save and reload, and join and reconnect**, with lines rebuilding;
- **bounds:** no chunk is loaded by any queue, pulse, pour or cache; the pulse and rebuild caps hold under load;
- every test id in the discovery guard.

**E2E:**
- **glyphs** on jars (labelled empty, labelled full, unlabelled), on tube bands and caps (including a labelled tube with five connected faces and its normalised cap), and in motes: discovered, unheard and missing (resolved from the GUI atlas), at 1 and 4 blocks, with a greyscale capture;
- **fill levels,** including over a lowered capacity (clamped inside the glass);
- **motes** along elbows, tees and gates, and at the "minimal" particle setting;
- **status pages** in every state, on a narrow window with a **long test translation**;
- **tooltips** with masked aspects;
- **the Stage 2 flow:** learn `vftv:essentia_jars`, craft jars and an alembic, pour from a crucible into a jar, learn `vftv:essentia_tubes`, build a line to a Node Tap, and watch cultivation, with a state dump at each step;
- **book fact spans** for every `essentia` key, re-resolved after a tuning `/reload`;
- **the asset, lang and model validators:** every key under "Lang keys", every sound and subtitle, every model ≤ 6 cuboids per rendered combination, every blockstate and item definition.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)'s contracts on this ticket):

| Wands contract | Met by |
|---|---|
| Tubes and jars feed a Node Tap only through the essentia capability, inside a transaction whose rollback they honour | "The pulse" (steps 6 and 7) and "The Alembic" use only the capability, in nested transactions with **equality-checked fresh retries**. "Transactions": roots closed within the call, opened only when `Transaction.getLifecycle() == Lifecycle.NONE`, publication only after commit, and post-commit failures never retried. "Removal and drops": the per-aspect carry with its reservation. **Conditional on** those rules, each with its test (the changed-acceptance retry, the carry property test, removal mid-transaction, throwing notifications). |
| #20 reads the tap's suction as `cultivate.suction` for an accepted aspect and 0 otherwise; a different scale states its mapping | "Suction": the scale is 0–128, and 16 is the bottom of the consumer band, so **no mapping is needed** and the default stands. The semantics are corrected to "positive implies acceptance; 0 may opt out", and the wands spec is amended in this change: face-matched suction exposure, membership of `vftv:essentia_endpoints`, hold 0, and 0 meaning disabled pulling but never disabled insertion. |

**Met here** (the [Crucible alchemy spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)'s contracts on this ticket):

| Crucible contract | Met by |
|---|---|
| Pull from a crucible only through "Essentia out", in a transaction, honouring nested rollback, the argument preconditions and the unavailable handler, closing promptly; the crucible's suction is 0 | Lines and alembics use only the capability, on a side the crucible exposes. An unavailable handler's 0 is an ordinary short read. Retries are equality-checked, so a source going unavailable mid-retry leaves no unmatched debit. Roots close within the call, and open only when `Transaction.getLifecycle() == Lifecycle.NONE`. The crucible has no suction capability, so its suction is 0. GameTest: a bucket refused mid-pulse, and abort restores. |
| Jars and tubes carry a glyph label as well as tint (forwarding the [aspects spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/15)) | A non-empty jar always shows its contents' glyph, and a labelled jar its label's even when empty. **A labelled tube shows its glyph for its whole life:** the faces are normalised so it is never six-connected, and its sixth would-be connection becomes a `blocked`, glyph-bearing cap ("Connections"). The glyph sits on the band's free faces and on every cap ("Rendering, The tube band"), and status page 1 names the label, masked. Motes are icons; tooltips and status mask unknown aspects; dormant labels show the missing sprite. Buffer-free tubes hold no aspect needing a permanent label. GameTest: the sixth-connection transition. E2E greyscale review. |
| Insert paths into a crucible and combiners need explicit proposals | **Declined, both.** No line or alembic inserts into a crucible, because a crucible is only ever a source; nothing here combines essentia, and Vacuos stays a true primal. A future proposal would count toward `pool.capacity` and the 64-entry bound. |

**Met here** (the [Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16) and [Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18) recipe-gate contracts, which bind this ticket's recipes):

| Contract | Met by |
|---|---|
| **Gate** (`research` on every gated recipe, refused on the server), **recipe-page validator**, **one authoritative preview**, **JEI** by the learned gate | The four gated recipes are `vftv:arcane` bodies, so the arcane spec's server gate, `ArcanePreview`, JEI reconcile and page-closure validator apply unchanged, and the validator checks the authored pages. The labels are ungated. **No new recipe type.** |
| Any Worktable recipe is a `vftv:arcane` body with its own research; Stage 1 affordability | `ordinary`, Stage 2 only; the Wellspring-bound lint is reused |

**Met here** (the [quality gates](quality-gates.md#performance) and [progression spec](progression.md#answers-to-the-top-community-pain-points)):

| Requirement | Met by |
|---|---|
| A workload, a share and hot paths; bounded work; numbers with homes | "Performance" (supply and consumers sustained), "Scheduling" and "Lines" (every queue capped), "Tuning and limits". **Conditional on** the traversal, scan, scheduler and re-entry rules above, each with its test. |
| Networks cheap by design, with limits deliberate and explained | **No per-tube contents and no ticker:** faces, labels and block entities are the only per-tube state. A cached, generation-checked graph; sleeping lines; recovery from chunk, ticking and cut changes with no stale routing. Limits are shown in status lines and the book. |

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)** (amended in this change)
- The Node Tap exposes `vftv:essentia_suction` on exactly its handler's faces, with hold 0, and is in `vftv:essentia_endpoints`.
- `cultivate.suction` = 0 disables automatic pulling, never insertion.
- **1–15 loses guaranteed consumer precedence:** ordering against each jar depends on the configured ranks, with ties rotated. The default of 16 keeps the guarantee.
- The tap never claims "accepts iff positive".

**[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)** (amended in this change)
- `vftv:essentia_jar` joins the default of the **item** tag `vftv:crucible_refuses`.
- `api` `SpillCause` gains `DESTROYED`.
- `vftv:crucible` is in the block tags `vftv:essentia_endpoints` and `vftv:alembic_bases`.
- It stays extract-only, with no suction capability.
- **Its own 10,000-unit spill input is unchanged.**

**[Architecture](architecture.md)** (amended in this change)
- Jars implement the handler through an explicit adapter, not an unmodified `ResourceStacksResourceHandler`. The Alembic has no handler.
- The essentia seam's model works on loader-free inputs and returns a plan; `neoforge.essentia` owns the capabilities, caches, handlers, journals and transactions.

**[Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)**
- It generates `essentiaFlux(spilled, cause)` **once** on `EssentiaEvent(SPILLED)`, as on `CrucibleEvent(SPILLED)`, for the causes `DESTROYED` and `REMOVED` (amended from "applies": what an aura retains is reported separately).
- **The input bound for this event is a total of 640,000** (`MAX_ESSENTIA_SPILL`), computed in `long`; the output is still an integer 0–1,000,000. It is a pure function of the spilled map, the cause and its synced rule record, with the typed zero as the fallback until it lands.
- **No other essentia flux:** moving, pouring, labelling, valves, stalls and caps spill nothing.
- GameTest: a destroyed stack generates its flux once.
- *Met by the [Hush spec](hush.md#contracts-on-sibling-specs): one `HIGHEST` listener; the input summed in `long` up to 640,000; one rate for every cause; generated, retained, pending and overflow reported.*

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35)
- **The fact source `essentia`:** its grammar, every key, the value format, the `EssentiaRules` arrival and reload invalidation, and `[?]` before the first arrival. All of it is in the research spec's "Prose and fact references".
- **Entries:** `vftv:essentia_jars` (a `note`, parent `vftv:resonant_brass`) gates `vftv:essentia_jar` and `vftv:alembic`. `vftv:essentia_tubes` (a `note`, parent `vftv:essentia_jars`) gates `vftv:essentia_tube` and `vftv:tube_valve`. Each carries `vftv:recipe` pages for its recipes. The ungated label recipes may sit on `vftv:essentia_jars`.
- **Prose** teaches:
  - suction ranks, hold, labels as locks and gates;
  - one move per pulse, throughput, the idle delay before a sleeping line notices a change;
  - rank-based starvation (a hungrier vessel is always served first);
  - the caps;
  - **every loss exception:** item expiry, falling below the world, command discards and flag-256 removals; and the **external drop losses** (the `doTileDrops` rule off, a `BlockDropsEvent` that cancels or changes the drops, a rejected item spawn), where the reserved contents vanish without a spill.

  Every number goes through fact spans.
- **The affordability artifact:** `content/research/path-stage2.json` learns `vftv:essentia_jars` after `vftv:resonant_brass`, then `vftv:essentia_tubes`, through actual note eligibility.

**[Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)**
- It moves essentia only through the essentia capability, in roots closed within the call, never across ticks.
- Any suction it offers is in the consumer band (16–128). Its blocks join `vftv:essentia_endpoints` if tubes should connect to them.
- **Direct jar pulls** (without tubes) inherit neither a line's connectivity nor its throughput, and need not apply the suction rule. If it routes like a line, it applies the rule.

**[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)**
- They move essentia only through the essentia capability, in roots closed within the call.
- A golem that routes by suction applies the rule explicitly, because a handler can't know its destination's rank.
- A golem carrying a filled jar keeps its components intact.

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- **Hand entries** for `vftv:essentia_jar`, `vftv:label`, `vftv:alembic`, `vftv:essentia_tube` and `vftv:tube_valve`.
- **Round-trip non-amplification** over all six recipes in `content/essentia/recipes.json`, with their output counts and the component-stripping blanking conversion, counting vis as no material input.
- A jar's values ignore its contents, as for every component ([aspects, Coverage and supply](aspects.md#coverage-and-supply)); the crucible's refusal tag makes that safe.

## Production tickets

This spec settles every decision. Two required child tickets deliver the assets and the authored content, and **the system is not done until both land**. **Neither writes mod code:** no block, block entity, renderer, capability adapter, solver, scheduler, payload, item tint source, datagen provider or validator. The asset ticket delivers a generator (authorised tooling under `tools/art/`) and assets under the [art style](art-style.md). The content ticket delivers authored inputs under `content/`, in the schemas above. The tags, loot tables, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Essentia assets** | **Generator:** `tools/art/essentia.py`. Every asset is in the Stage 2 row of [art style §5](art-style.md#5-tone-ramp-by-stage), from the prototype's Essentia Jar where it applies.<br>**Block textures,** 16×16, under `assets/vftv/textures/block/`:<br>• `essentia_jar_glass.png` and `essentia_jar_lid.png` (the teal pip inlaid on the lid);<br>• `essentia_jar_liquid.png` (light neutral steps, `glass1` and `hush3`, for runtime tinting; the renderer's and the filled item's);<br>• `essentia_label.png` (the paper the renderer draws behind a glyph);<br>• `essentia_tube_arm.png`, `essentia_tube_core.png` and `essentia_tube_cap.png`;<br>• `essentia_tube_band.png` (solid paper) and `essentia_tube_band_ring.png` (an 8×8 paper frame around a transparent 6×6 window), both drawn by the renderer on the band box `[4,4,4]–[12,12,12]`;<br>• `tube_valve.png`, `tube_valve_wheel_open.png` and `tube_valve_wheel_closed.png`;<br>• `alembic_glass.png` and `alembic_brass.png`.<br>**Block models** under `assets/vftv/models/block/`, each ≤ 6 cuboids, pixel-aligned, no element rotations ([art style §6](art-style.md#6-blocks-and-models)), with translucent glass as §6 specifies:<br>• `essentia_jar.json`: the glass body `[3,0,3]–[13,9,13]`, neck `[4,9,4]–[12,10,12]` and lid `[3,10,3]–[13,12,13]` (the liquid is the renderer's);<br>• `essentia_tube_core.json`: `[5,5,5]–[11,11,11]`;<br>• `essentia_tube_arm.json`: the north arm `[5,5,0]–[11,11,11]`, reaching past the core so that any combination covers it;<br>• `essentia_tube_cap.json`: the north cap, of the same extent, with the cap texture on its outer face;<br>• `tube_valve_open.json` and `tube_valve_closed.json`: on the z axis, 4 cuboids each (body `[4,4,4]–[12,12,12]`, two arms, wheel);<br>• `alembic.json`: ≤ 5 cuboids, facing north.<br>UVs are derived from position, so overlapping parts draw identical texels.<br>**Blockstates** under `assets/vftv/blockstates/`:<br>• `essentia_jar.json`: 4 `facing` variants by `y` rotation;<br>• `essentia_tube.json`: **multipart**. The core applies only when all six faces are `none`; per direction, the arm applies when `connected` and the cap when `blocked`, each rotated by blockstate `x`/`y`. Any combination draws ≤ 6 cuboids;<br>• `tube_valve.json`: 12 variants (`axis` × `open` × `powered`). The open model is used iff `open` and not `powered`, rotated per axis;<br>• `alembic.json`: 4 `facing` variants.<br>**Item models and definitions:**<br>• `assets/vftv/models/item/essentia_jar_filled.json`: the jar's three cuboids plus the liquid element `[4,1,4]–[12,6,12]` with tint index 0, as in [art style §6](art-style.md#6-blocks-and-models);<br>• `assets/vftv/items/essentia_jar.json`: `minecraft:condition` on `minecraft:has_component` `vftv:jar_contents`. True gives the filled model with `"tints": [{"type": "vftv:essentia"}]` (the tint source is build-phase code); false gives `vftv:block/essentia_jar`. **The jar item shows no glyph;** its tooltip carries the aspect;<br>• `assets/vftv/models/item/label.json` and `label_written.json` (parent `minecraft:item/generated`, textures `item/label.png` and `item/label_written.png`, 16×16, paper with an ink mark), and `assets/vftv/items/label.json`, a condition on `minecraft:has_component` `vftv:label`;<br>• `assets/vftv/models/item/essentia_tube.json` (a straight tube: two arms on the z axis), `assets/vftv/items/essentia_tube.json`, `items/tube_valve.json` (the open model) and `items/alembic.json` (the block model).<br>**Sounds:** `assets/vftv/sounds/essentia/jar_fill_0.ogg` … `jar_fill_2.ogg`, `label_apply.ogg`, `label_cut.ogg`, `valve_open.ogg`, `valve_close.ogg`, `alembic_drip_0.ogg` … `alembic_drip_2.ogg` (≤ 3 s, faded tails), with the `sounds.json` entries `essentia.jar_fill`, `essentia.label_apply`, `essentia.label_cut`, `essentia.valve_open`, `essentia.valve_close` and `essentia.alembic_drip`, naming `subtitles.vftv.essentia.<event>`, with no volume or pitch overrides. The playback rules are this spec's.<br>**No new missing sprite:** the renderer reuses `vftv:hud/crucible_missing`, resolved from the GUI sprite atlas.<br>**Review:**<br>• a contact sheet at GUI scale 2 beside vanilla glass, copper and a lever, with every item;<br>• Blender renders of the jar (empty, half, full, labelled empty, labelled full, and over a lowered capacity, clamped), of the tube shapes (straight, elbow, tee, cross, six-way, one capped face, a labelled band with a free face, a labelled band on a cap, and a labelled tube with five connected faces whose sixth is the normalised, glyph-bearing cap), of the valve in each state and of the alembic hung on a crucible over a jar;<br>• a **greyscale review** that a glyph reads on a jar and on a band at 1 and 4 blocks.<br>Provenance rows. | none |
| **Essentia content** | `content/essentia/recipes.json` (`vftv:essentia_recipes/1`, exactly the six recipes under "Authored content, Recipes", with their ingredients, counts and costs chosen within those rules); `content/essentia/client.json` (`vftv:essentia_client/1`); and `content/lang/en_us/essentia.json` (`vftv:lang/1`) with every key under "Lang keys". Its review checks that the messages teach labels as locks and gates, suction ranks, hold, one move per pulse, the idle delay and the caps. | none |

[Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) consumes the entry ids, recipe ids and `essentia` fact keys frozen here.
