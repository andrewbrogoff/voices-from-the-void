# Golems

Crafted servants that carry items and essentia between the blocks their owner marks. This covers:
- the **golem**: one entity whose **body** (a material) sets its stats and whose **core** sets its job;
- making, fitting, placing and picking up a golem, and the Stage 3 **Echo** body;
- control: a **home**, up to sixteen **marks**, and the **Baton** that edits them;
- the v1 jobs, **carrying** and **decanting**, and the five **roles** a mark can have;
- the think loop: deterministic selection, travel, pathing and acts, each under a per-level budget;
- transactions, carry conservation, chunk reloads and persistence;
- inspection: the golem card, the marks overlay and the book.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the data and sync rules and the seams;
- the [quality gates](quality-gates.md) for every gate;
- the [aspects spec](aspects.md) for aspect names, the unheard rule and values;
- the [research spec](research.md) for research gates, recipe pages and fact spans;
- the [shared conventions](conventions.md) for raw-record retention, operation guards, `fold` and phases, Rules records and the authored-content envelope;
- the [wands spec](wands.md) for interaction precedence, the coalesced flush and the part data;
- the [arcane crafting spec](arcane.md) for the `vftv:arcane` recipe type and the Worktable;
- the [crucible spec](crucible.md) for the `vftv:crucible` recipe type, thrower resolution, the provisional-spawn pattern, the HUD frame rules and the spill boundary;
- the [essentia spec](essentia.md) for the essentia capability, transactions, capability caches and the inspection cursor;
- the [infusion spec](infusion.md) for the `vftv:infusion` recipe type, the pedestal's item handler and the HUD wrapping rule;
- the [foci spec](foci.md) for the inventory-click attach pattern;
- the [art style](art-style.md) for every asset.

Resolved in [Golems spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/23). Inputs: [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4, whose public sources rate golems only relative to each other. Each tunable number is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port. Both print the same 51 lines (1,911 bytes, SHA-256 `857557c223f86c1a51c669f83837fd71c1aa11679cbcf44339213c47bebc4446`).

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 2 Distillation | the golem, four bodies, the carrying and decanting cores, the Baton | the opener `vftv:distillation`, whose find `vftv:find/distillation` is [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s ([research, Stage](research.md#stage)). Inside the Stage, the two `note` entries below. |
| 3 Ruins | the Echo body, by infusion | the opener `vftv:ruins`; the `note` below |
| 4 | nothing of its own | new jobs are extensions (see the contracts) |

| Entry | Stage | Kind | Parents | Gates |
|---|---|---|---|---|
| **`vftv:golems`** | 2 | `note` | `vftv:resonant_brass` | the arcane recipes `vftv:golem_wood`, `vftv:golem_terracotta`, `vftv:golem_stone`, `vftv:golem_brass`, `vftv:baton` and `vftv:blank_core`; the crucible recipe `vftv:carrying_core` |
| **`vftv:golem_decanting`** | 2 | `note` | `vftv:golems`, `vftv:essentia_jars` | the crucible recipe `vftv:decanting_core` |
| **`vftv:echo_golems`** | 3 | `note` | `vftv:golems`, `vftv:infusion` | the infusion recipe `vftv:echo_golem` |

- **Gate the recipe, not the golem.** Placing, marking and running a golem someone gave you is ungated, as a crucible is ([crucible, Stage](crucible.md#stage)). **Obtaining counts.**
- **Brass ancestry** prevents a progression cycle: the brass body and the Baton take `vftv:resonant_brass_ingot`, following the singing-wands precedent ([crucible, Contracts](crucible.md#contracts-on-sibling-specs)).
- **Options considered:** one entry for everything; a find for golems. **Why these three:** the [progression spec](progression.md#stages) puts first golems in Stage 2; decanting is useless before jars exist; the Echo body uses Stage 3's altar.

## The golem

### The entity

**`vftv:golem`**: a `PathfinderMob`, `MobCategory.MISC`, box 0.6 × 0.9 blocks, eye height 0.75, client tracking range 8 chunks, update interval 3 ticks.

- **Not an `AbstractGolem`.** It inherits no vanilla golem behaviour, and has no special targeting: zombies target `IronGolem` by class (`MC:world/entity/monster/zombie/Zombie.java:125–128`), so they ignore it. Withers and zoglins choose any attackable living entity (`MC:world/entity/boss/wither/WitherBoss.java:79–81,106`; `MC:world/entity/monster/Zoglin.java:134–138`), and may attack it like any other.
- **No goals and no brain.** The navigation ticks where vanilla's `serverAiStep` ticks it (`MC:world/entity/Mob.java:743`); every decision comes from the level's think queue (see "Thinking"). No decision ever reads `getRandom()`.

| Override | Value | Why |
|---|---|---|
| `checkDespawn` | does nothing | vanilla's posts NeoForge's despawn event and discards non-peaceful mobs and far ones (`MC:world/entity/Mob.java:715–739`); a golem never despawns |
| `removeWhenFarAway` | false | (`Mob.java:706`) |
| `canUsePortal(boolean)` | false | its home and marks belong to one level (`MC:world/entity/Entity.java:3222`) |
| `canRide(Entity)` | false | boats and minecarts would carry it off (`Entity.java:2481`) |
| `canBeLeashed` | false | a lead would fight the navigation (`Mob.java:1322`) |
| `pushEntities`, `isPushable` | no-op, false | golems overlap instead of jamming, and no per-tick neighbour scan runs (`MC:world/entity/LivingEntity.java:3345,3487`) |
| `fireImmune()` | the material's `fire_immune` | (`Entity.java:1578`) |
| `canBreatheUnderwater` | true | (`LivingEntity.java:413`, reached through NeoForge's `ILivingEntityExtension`, `NF:common/extensions/ILivingEntityExtension.java:55–57`). Water is safe; other mods' fluids keep their own effects. |
| `canPickUpLoot` | false (vanilla's default) | the carry moves only by acts |
| `mobInteract` | the Baton (see "The Baton") | |
| `hurtServer`, `dropEquipment`, `die` | see "Damage, death and regeneration" | |

**Attributes,** registered for the type and set from the material at spawn and at every load: `MAX_HEALTH`, `MOVEMENT_SPEED`, `ARMOR`, and `FOLLOW_RANGE` = the tuning's `path.max_length` (for vanilla's own reads only: our searches pass their length explicitly, see "Pathing"). After every refresh, health = min(health, max): **a lowered maximum clamps health and nothing ever restores it to full.**

### Materials

A synced datapack registry, **`vftv:golem_material`**, following [ADR 0003](../adr/0003-aspects-as-datapack-registry.md): fixed when a world opens, with a network codec (`NF:registries/DataPackRegistryEvent.java:63–75`), at `data/<ns>/vftv/golem_material/<path>.json`.

| Field | Required | Range | Meaning |
|---|---|---|---|
| `health` | yes | 1–100 | max health, in health points |
| `speed` | yes | 0.05–0.50, **in hundredths** (a value with a third decimal place is rejected) | the `MOVEMENT_SPEED` base value |
| `carry_items` | yes | 1–64 | most items carried at once, also capped by the item's own max stack size |
| `carry_essentia` | yes | 1–1,000 | most essentia units carried at once |
| `armor` | yes | 0–20 | the `ARMOR` base value |
| `fire_immune` | yes | boolean | |
| `regen` | yes | 20–6,000 | ticks per health point regained |
| `range_bonus` | yes | 0–16 | added to the tuning's `range` |
| `texture` | yes | a resource id | the entity texture |

**Shipped values** (the content ticket ships exactly these):

| Id | Health | Speed | Carry items | Carry essentia | Armor | Fire-immune | Regen | Range bonus |
|---|---|---|---|---|---|---|---|---|
| `vftv:wood` | 12 | 0.30 | 12 | 8 | 0 | no | 100 | 0 |
| `vftv:terracotta` | 20 | 0.26 | 24 | 16 | 2 | yes | 200 | 0 |
| `vftv:stone` | 32 | 0.21 | 40 | 24 | 6 | yes | 300 | 0 |
| `vftv:brass` | 26 | 0.27 | 64 | 40 | 4 | yes | 150 | 0 |
| `vftv:echo` | 36 | 0.30 | 64 | 64 | 6 | yes | 100 | 8 |

- **The trade:** fast and fragile, or slow and sturdy; brass carries the most of Stage 2; echo is the Stage 3 upgrade.
- **No copper body.** Vanilla 26.2 has its own Copper Golem (`MC:world/entity/animal/golem/CopperGolem.java`), and a second one would confuse.
- **A pack that lowers a stat** never deletes anything: health clamps to the new maximum; a carry above the new cap is kept and delivered, and intake waits until the carry is below the cap.

### The golem item

**`vftv:golem`**, stacking to 1. Its components:

| Component | Value | Absent means |
|---|---|---|
| `vftv:golem_body` | a `vftv:golem_material` id (`ResourceKey` codec) | no body: the item can't be placed (`message.vftv.golem.unknown_body`) |
| `vftv:golem_core` | a core item id (`ResourceKey<Item>` codec) | no core fitted |
| `vftv:golem_state` | `{"format": 1, "marks": [<mark>, …], "carry"?: <carry>, "health": <float>}` (see "Sync and state") | a fresh golem: no marks, no carry, full health |

- **An unreadable `vftv:golem_state`** follows [conventions](conventions.md#raw-record-retention) (the component travels whole): the item can't be placed, takes no core, and shows `tooltip.vftv.golem.unreadable`.
- **Its name** is `item.vftv.golem` with the material's name (`golem_material.<ns>.<path>`), or `golem.vftv.unknown_body`.
- **Its model** selects on `vftv:golem_body` (see "Rendering and sound").

### Cores and jobs

**Items:** `vftv:blank_core`, `vftv:carrying_core`, `vftv:decanting_core`, stacking to 16.

**Jobs** are a code registry, **`vftv:golem_job`** ([architecture, Data and code](architecture.md#data-and-code)). v1 registers two:

| Job | Core | Carry kind | Roles, in cycling order | Default role |
|---|---|---|---|---|
| `vftv:carry` | `vftv:carrying_core` | items | TAKE, GIVE, FEED, TIP, COLLECT | TAKE |
| `vftv:decant` | `vftv:decanting_core` | essentia | TAKE, GIVE | TAKE |

**The job contract** (in `core.golem`, loader-free):
- A **job type** declares its carry kind (`ITEMS` or `ESSENTIA`), its ordered valid roles and its default role. It holds no behaviour of its own.
- A **core item** is a `GolemCoreItem` bound in code to exactly one job key. Ours and an addon's alike implement `api`'s `GolemCore`, which is what the golem reads. The `vftv:golem_core` component names the item, and the job comes from it.
- **Planning** is one shared, loader-free planner in `core.golem.model`. It works over primitive views: marks (positions as `long`, faces and roles as ordinals, filters as interned ints), per-think resource tokens whose equality the platform adapter defines (an `ItemResource` is item plus components; an aspect is its index in ascending id), and a **slot reader seam** that charges every read to the think's budget. It returns a bounded plan record (`TAKE`/`GIVE`/`FEED`/`TIP`/`COLLECT`, mark index, slot, token, amount), a yield position, `WAIT` with a reason, or `IDLE`. It never sees a capability, transaction, `BlockPos` or `ResourceKey`.
- **Execution** lives in `neoforge.golem`: the capability calls, the journals, transactions and spawns, which report back what moved.
- **An addon** may register a job type with its core item, choosing a carry kind and a subset of the five roles (`TAKE` and `GIVE` only for `ESSENTIA`). This is public: the record `GolemJob`, the typed `vftv:golem_job` key and the loader-free binding interface `GolemCore` that a core item implements ([API spec, Golem jobs](api.md#golem-jobs)). Acts, the planner and execution stay internal, so a new kind of act is a spec amendment, not an addon's: v1.1's `HARVEST` is the first, and its shape is recorded under "What golems don't do".
- **A missing core or job** (the core item's mod removed) leaves a placed golem **inert** and kept (see "Sync and state").

## Making a golem

### Recipes

- **Bodies:** four `ordinary` arcane recipes at the Worktable, each producing `vftv:golem` with its `vftv:golem_body` set, through a static result template with components, as the [arcane recipe type](arcane.md#the-recipe-type) allows.
- **The Baton and the blank core:** `ordinary` arcane recipes.
- **Cores:** crucible recipes whose catalyst is `vftv:blank_core`, in this spec's own content file with the [crucible recipe type](crucible.md#the-recipe-type)'s body and their own `research`.
- **The Echo body:** the infusion recipe `vftv:echo_golem`: centre `vftv:golem`, result `{"item": {"id": "vftv:golem", "count": 1, "components": {"vftv:golem_body": "vftv:echo"}}, "transmute": true}`. `ItemStackTemplate.apply` lays the template's components **over** the centre's patch (`MC:world/item/ItemStackTemplate.java:116–119`; `MC:world/item/crafting/TransmuteRecipe.java:85–87`), so the body changes and the core, marks, carry and health are kept. Re-infusing an echo golem is legal; the readout shows the unchanged output.

The ids and content rules are under "Authored content, Recipes".

### Fitting a core

By inventory click, the [foci spec's attach pattern](foci.md#attaching-and-detaching): **secondary (right) clicks only**, through `Item#overrideOtherStackedOnMe` (the golem item in the slot, the other stack on the cursor) and `Item#overrideStackedOnOther` (the golem item on the cursor, clicked onto a slot) on `vftv:golem` (`MC:world/item/Item.java:255,259`), with the cursor and the slot changed in the same callback. Any primary click, and any row below that says vanilla, is not overridden (returns `false`). First match, with **"the other"** meaning the cursor's stack in the first orientation and the clicked slot's stack in the second:

| # | Golem item | The other | Effect | Returns |
|---|---|---|---|---|
| 1 | any | any, where the slot's `allowModification(player)` is false | vanilla | `false` |
| 2 | unreadable | a core, or empty | refused: `message.vftv.unreadable` | `true` |
| 3 | carrying anything | a core, or empty | refused: `message.vftv.golem.carrying` | `true` |
| 4 | no core | a core stack of any count | **fitted:** one core leaves the other stack (the stack shrinks; an emptied cursor or slot becomes empty) | `true` |
| 5 | a core fitted | a core stack of count 1 | **swapped:** the fitted core takes the other's place, the other's core is fitted | `true` |
| 6 | a core fitted | a core stack of count 2 or more | refused, nothing changes: `message.vftv.golem.swap_one` | `true` |
| 7 | a core fitted | empty | **removed:** the fitted core goes to the empty cursor (first orientation) or the empty slot (second) | `true` |
| 8 | no core | empty | vanilla | `false` |
| 9 | any | anything else | vanilla | `false` |

- **Rows 4, 5 and 7 clear the marks.** A mark's role may be invalid for the new job, and carry kinds differ. The tooltip says so.
- **Conservation:** every row moves exactly one core between the golem and one stack, or nothing. Row 6 exists because a swap from a stack would need a second place to put the returned core.
- **Only mutable slots,** as vanilla's bundle (`MC:world/item/BundleItem.java:97–125`), so a crafting result never yields a free core.

### Placing

The golem item's `useOn` on a block face, reached after vanilla's spawn protection, `mayInteract`, `RightClickBlock` and the block's own use, like a spawn egg (`MC:server/level/ServerPlayerGameMode.java:383–418`). **The spawn cell** is the clicked position offset by the clicked face; the golem stands at its bottom centre.

1. **Refusals,** first match, each on the [status line](accessibility.md#r5-the-status-line), returning `FAIL` and changing nothing:
   - an unreadable item: `message.vftv.unreadable`; no body or an unknown material: `.unknown_body`; no core: `.no_core`; a core whose item or job isn't registered: `.unknown_core`;
   - the spawn cell isn't in a loaded, entity-ticking chunk (`ServerLevel#isPositionEntityTicking`, `MC:server/level/ServerLevel.java:1823`), is outside the world border, or is outside the build height: `.not_here`;
   - `golems.max_per_chunk` golems are already loaded in the spawn cell's chunk: `.crowded`. **This is admission only,** not a resident cap: golems walking home or reloading can gather more in one chunk, which the load scenarios measure;
   - the golem's box at the spawn point collides: `.no_room`.
2. **Build** the entity from the item: body, core, marks, carry and health from the components; **owner** = the placer; **home** = the spawn cell; `cursor` −1. It is **provisional**: it holds its own operation guard, joins no queue, and can't be interacted with.
3. **Spawn** with `addFreshEntity`. Join listeners run synchronously and may cancel it (`MC:world/level/entity/PersistentEntitySectionManager.java:74–77`).
4. **Revalidate:** it returned `true`; our entity is alive and in this level at the spawn point; the placer is alive and in this level; the hand used still holds **the same stack instance**, still a golem item with equal components; and **every predicate of step 1 holds again**, now: the item's body and core, the spawn cell's chunk, border and build height, the admission cap and the collision box, the last two **excluding our provisional entity** from the count and the collision test. **Any failure, false or throw:** our entity is discarded if it was added, nothing is consumed, and one log line names the failed check. Whatever listeners changed themselves is left as they made it.
5. **Commit:** one item is consumed (none for a player with infinite materials); the provisional flag and guard clear; the first think is queued at the golem's phase; `GolemEvent(PLACED)` posts; `golem.place` plays. Returns `SUCCESS`.

### Picking up

The owner's sneak-use with the Baton on their golem (see "The Baton"). First match:
1. **Refusals:** unreadable: `message.vftv.unreadable`; its operation guard is held: `message.vftv.busy`; the player has no empty inventory slot (`Inventory#getFreeSlot` = −1, `MC:world/entity/player/Inventory.java:101`): `.no_room_inventory`. A golem item never stacks, so it needs an empty slot.
2. **Claim** the record once: the golem is marked picked up and leaves every queue; no act or think can start.
3. **Build** the item from the claimed record: `vftv:golem_body`, `vftv:golem_core`, and `vftv:golem_state` with the marks, carry and health. Home, cursor and backoffs aren't kept.
4. **Discard** the entity, **then** hand the item over with `Inventory#placeItemBackInInventory`. There is never a moment when both exist.
5. `GolemEvent(PICKED_UP)`; `golem.pickup` plays.

**Qualified:** if a removal callback fills the inventory between steps 1 and 4, vanilla drops the item at the player (`Inventory.java:309–318`). If that spawn is then refused, the item and its carry are lost: an **external drop loss**, listed under "Carry conservation".

## Control

### Home and range

- **Home** is one block cell. An idle golem walks there and stands.
- **New marks** must be in range. Marks left out of range by a home move or a tuning change are **kept** and faulted `OUT_OF_RANGE` until they are back in range.
- **Range:** a position is in range iff |dx| ≤ R, |dz| ≤ R and |dy| ≤ `range_vertical`, measured from home, where R = `range` + the material's `range_bonus`.

**Vectors** (reference implementation; home (−37, 70, 512), R 20, vertical 8):

| Position | In range |
|---|---|
| (−17, 70, 532) | yes |
| (−16, 70, 512) | no |
| (−57, 62, 492) | yes |
| (−57, 61, 492) | no |
| (−37, 79, 512) | no |

### Marks

A **mark** is `{pos, face, role, filter?}`: a face of a block, what to do there, and optionally what to do it with.
- **At most `MAX_MARKS` = 16 per golem,** in **placement order**. Removing one closes the gap. Two marks never share a (pos, face).
- **The filter** is an item id for an item job, or an aspect id for an essentia job. It matches by item only (components ignored) or by aspect. Absent means anything.
- **Stored** on the golem, and in the item when picked up. A golem-wide **mark generation** (an int) increases with every edit to its marks or home.
- **After an edit:** the in-flight target, any path request and the selection's resume point are **retired** if they referred to a changed or removed mark, or if home moved. The cursor follows a removal: if it pointed at or past the removed index, it decreases by one, staying within −1 … size − 1.

### The Baton

**`vftv:baton`**, stacking to 1: a conductor's baton of Resonant Brass. Its component **`vftv:baton_attunement`** is `{golem: <UUID>, dimension: <level key>}`; absent means unattuned. It is an ordinary item: copying or handing it over hands over nothing, because **every edit rechecks ownership** at that moment.

**Hands:** main hand only. The off hand matters only to a **sneaking** click on a mark, where its item is the filter; plain clicks ignore it, so a torch, shield or totem carried there never changes what a click does.

**On a golem** (`mobInteract`, `MC:world/entity/Mob.java:1213`, reached after NeoForge's cancellable `PlayerInteractEvent.EntityInteract`: `MC:world/entity/player/Player.java:865–877` calls `CommonHooks.onInteractEntity`, `NF:common/CommonHooks.java:845–848`; a cancel wins). First match:

| Gesture | Golem | Effect | Returns |
|---|---|---|---|
| any | provisional or picked up | nothing | `PASS` |
| use or sneak-use | not yours | nothing on the server; the client turns the golem card's page | `SUCCESS` |
| sneak-use | yours | **pick up** (see "Picking up") | `SUCCESS`, or `FAIL` with its refusal |
| use | yours, any state (unreadable and inert included) | **attune**: the component becomes this golem and level; `baton.attune` plays; `message.vftv.golem.attuned` (1 arg: the golem's name); the client turns the card's page | `SUCCESS` |

**On a block,** in `Item#onItemUseFirst`, which runs after `RightClickBlock` and before the block's own use, so a chest is marked rather than opened (`MC:server/level/ServerPlayerGameMode.java:383–403`). It runs only when the event's item use isn't denied. **Denied block use:** `onItemUseFirst` would still run when only the block's use is denied, and listener priority can't promise a final value, since listeners of one priority run in registration order and a later one may still deny. So the Baton reads **the live event after its dispatch has completed**: `CommonHooks.onRightClickBlock` returns only after every listener (`NF:common/CommonHooks.java:870–873`), before `onItemUseFirst` is called (`MC:server/level/ServerPlayerGameMode.java:383–397`).
- **The click record:** a `RightClickBlock` listener stores, per player, **a reference to the event itself** with the level, hand, position, face and game time, **replacing** any earlier record on every click.
- **The Baton** takes the player's record and **consumes** it. It must match this click's level, hand, position and face and the current game time; then it reads the event's **final** `getUseBlock()`.
- **Fail closed:** no record, a mismatched one (a re-entrant click that replaced it, a mod calling `onItemUseFirst` without the event), or a final `getUseBlock()` of `FALSE` refuses the edit.

First match, each refusal on the status line and returning `FAIL` (no block use follows):

| # | When | Effect |
|---|---|---|
| 1 | unattuned | `PASS`: the block's use proceeds |
| 2 | the click record is missing or mismatched, or its event's final `getUseBlock()` is `FALSE` | refused: `message.vftv.golem.denied` |
| 3 | the attuned golem isn't loaded in this level (`ServerLevel#getEntity(UUID)`), or the attunement names another level | refused: `.not_here` |
| 4 | the player isn't its owner now | refused: `.not_yours` |
| 5 | the golem is unreadable, inert, or its operation guard is held | refused: `message.vftv.unreadable`, `message.vftv.golem.inert` or `message.vftv.busy` |
| 6 | sneaking, on an existing mark (same pos and face) | off hand holds something: its **filter** is set, if valid (`.filter_set`, 2 args: the number and the filter's name), else refused `.bad_filter`, nothing changes; off hand empty: the filter is **cleared** (`.filter_cleared`, 1 arg: the mark's number), `baton.mark_remove` plays. Never removes the mark |
| 7 | sneaking, no mark there | **home** becomes the cell in front of the face, whatever the off hand holds, if it is in a loaded chunk (`.home_set`, 3 args: x, y, z), else refused `.home_unloaded`; marks now out of range stay, faulted |
| 8 | on an existing mark | its **role** cycles to the next valid role of the job, in the job's order (`.role`, 2 args: the number and the role); **after the last role the mark is removed** (`.mark_removed`, 1 arg; `baton.mark_remove` plays). Carrying: TAKE → GIVE → FEED → TIP → COLLECT → removed; decanting: TAKE → GIVE → removed |
| 9 | no mark there | out of range: refused `.out_of_range`; 16 marks already: `.too_many_marks`; else a mark is **added** with the job's default role and no filter (`.mark_added`, 2 args: its number and role); `baton.mark_add` plays |

- **No gesture is both common and destructive:** removal sits at the end of the plain-click role cycle, never on a sneak, and the off hand never redirects a plain click.

- **Valid filters:** for an item job, any non-empty off-hand stack (its item id); for an essentia job, a `vftv:label` naming an aspect the registry holds ([essentia, Labels](essentia.md#labels)). Anything else is invalid, including a blank or missing-aspect label.
- **Mark numbers** in messages are 1-based.
- On air the Baton does nothing (`PASS`).
- **Options considered:** TC4-style filter slots in the core; a configuration GUI. **Why:** one instrument, used in the world, with every state visible on the blocks themselves; seal-style job markers are what players remember fondly (community pain points, Loved §4).

## Roles

| Role | Job | At the mark |
|---|---|---|
| **TAKE** | carry | indexed `extract(slot, R, k)` from `Capabilities.Item.BLOCK` on the mark's face (`NF:capabilities/Capabilities.java:37–38`) |
| | decant | indexed `extract(slot, aspect, k)` from the [essentia capability](essentia.md#contents-and-the-handler) on the face. A crucible exposes it on every face but `UP` ([crucible, Essentia out](crucible.md#essentia-out)), so a mark on its top has no handler. |
| **GIVE** | both | indexed `insert(slot, R, k)` into the same capabilities, slots ascending, until the carry is placed or the slots are exhausted. Pedestals and Singing Bowls enforce their own claim and restoration rules through their registered handler ([infusion, Holding items](infusion.md#holding-items-the-item-handler-and-the-container)). |
| **FEED** | carry | drops **one item** of the carry as an `ItemEntity` **thrown by the owner** (see "Feed and Tip") |
| **TIP** | carry | the same one-item drop, with **no thrower** |
| **COLLECT** | carry | picks up one loose `ItemEntity` near the mark (see "Collect") |

- **`ItemResource` keeps components** (`NF:transfer/item/ItemResource.java:35–38,198–203`), so a filled Essentia Jar, a wand or a focus moves whole, and its component-resolved max stack size caps the amount (`:314–332`).
- **No suction routing.** A golem moves essentia only where it is marked, so the [suction rule](essentia.md#suction) isn't applied: a golem is not a line, as infusion's direct draws aren't ([infusion, Drawing essentia](infusion.md#drawing-essentia)). The book says so.
- **The crucible loop:** FEED catalysts, COLLECT results and refusals, TAKE leftover essentia from a side face into jars.

### Feed and Tip

**The drop:** **one item per act** (`k` = 1): one `ItemEntity` holding a single item of the carry, at the mark's position + (0.5, 1.0, 0.5), velocity 0, vanilla's default pickup delay (`ItemEntity#setDefaultPickUpDelay`, `MC:world/entity/item/ItemEntity.java:437`). The remainder stays in the carry, exactly as after a partial GIVE, and the cursor and backoff rules are unchanged. FEED sets the thrower to the owning player (`setThrower`, `:429`); TIP sets none. Either works on any block; their purpose is the crucible.
- **Why one:** the carry cap is sized for chest logistics, and a crucible wants pacing. One item per act paces a crucible at the golem's think cadence, never floods its pool with a stack's worth of one catalyst, and bounds the handoff loss below to one item, with no count to set on the Baton.

**FEED's owner rule:** a FEED mark is eligible only while the owner resolves, **server-wide** (`server.getPlayerList().getPlayer(uuid)`, as the crucible resolves the thrower), to a `ServerPlayer` that is **online and alive**, checked at selection, at arrival and again after the spawn. The owner may be in any dimension: research is per player, not per dimension.

**The handoff warning,** taught in full by the book and the content's messages:
> The owner must be online at the drop, **and still online when the crucible processes the item.** The crucible resolves the thrower when it processes the entity: at its next step (every `step.interval` ticks, 5 by default), or later if it is cold, dry, full, busy or the item doesn't fit. If by then the owner has logged out or died, **by any path, even at a boiling crucible**, the item has no thrower that resolves, and it **dissolves** ([crucible, Selection](crucible.md#selection), row 5): a learned catalyst becomes essentia. A crafted result, and a refused catalyst, are thrown toward wherever the owner then stands.

This is the crucible contract's documented-risk option: the drop is prevented while the owner is absent, and the window after it is disclosed.

**TIP** is the deliberate dissolve path. Its items have no thrower, exactly as a dropper's, so in a crucible they always dissolve ([crucible, Multiplayer and automation](crucible.md#multiplayer-and-automation)), unless protected. The crucible spec exempts TIP from its owning-thrower golem contract (amended in this change).

### Collect

- **The box:** the marked block's cell, grown by `collect.radius` on every side (at most 5 × 5 × 5 blocks).
- **The query:** one `getEntitiesOfClass(ItemEntity.class, box, predicate)`. **The predicate** keeps only entities that are alive, not removed, without a pickup delay, passing the mark's filter, and matching the carry's resource (or any, with the carry empty) with room left. Ineligible entities never enter the result, so an ineligible low-id prefix can't starve eligible ones.
- **The order:** the result is sorted by entity id ascending; the first `MAX_COLLECT_ENTITIES` = 16 are considered, and the plan names the lowest **by identity**: the entity object, its id and its UUID.
- **The planned amount:** min(the entity's count, the carry's room, the resource's max stack) at selection. **It is a ceiling, never the move:** the act recomputes the amount after its hook (see "An act"). One entity per act.
- **The cost:** the query visits every `ItemEntity` in the box's entity sections, a **density-dependent native term** this spec doesn't bound, as the foci spec discloses for bursts ([foci, Performance](foci.md#performance)). It is charged 1 unit, plus 1 per entity considered.
- Crafted results and refused catalysts carry the default pickup delay, so a fed item is the crucible's for 10 ticks before a COLLECT mark can see it.

### What golems don't do

Not in v1: harvesting, guarding, felling, fishing, using items, fluids and opening doors. Golems never cast, never use the Lathe and never arcane-craft. **Why:** the [progression spec](progression.md#tc4-systems-kept-redesigned-cut) asks for reliable golems with mark-based jobs, not every TC4 core, and the community's complaint was unreliable AI and inexact counting (community pain points, §5).

**Harvesting is deferred to v1.1, not blocked.** Everything it needs already exists in v1: an owner rule and owner attribution (FEED's), and protection events for free through the player's own block paths (the [foci spec's block work](foci.md#block-work): one `BreakBlockEvent` per `destroyBlock`, one `EntityPlaceEvent` per placement-wrapper call). It waits only on v1's scope.

**Farming in v1** is a vanilla farm feeding a golem: an observer-and-piston (or water-flushed) crop farm drops its harvest, a **COLLECT** mark beside the drop point picks it up one entity per act, and GIVE marks carry it to chests. The book prose teaches this pattern (see the contracts on #35).

**The v1.1 shape,** recorded so it is one role, not a redesign:
- **One role, `HARVEST`,** on the carry job, cycling after COLLECT; no new job, core or component.
- **One tag, `#vftv:harvestable`:** a mark acts only on a block in it at its maximum age (`CropBlock#isMaxAge` or the block's age property at its maximum).
- **One act:** eligible under FEED's owner rule (online and alive); breaks through `owner.gameMode.destroyBlock(pos)`, so the break posts the owner's `BreakBlockEvent` and drops land for a COLLECT mark; then replants one matching seed from the carry through the owner's placement wrapper, as Exchange places. The guard, barriers, exits and work budget are the existing ones.
- **Open for v1.1:** `ServerPlayerGameMode` breaks in the player's own level, so either `HARVEST` adds "in the golem's level" to its owner rule or the act breaks through a level-explicit path with the same events.

## Thinking

### Scheduling

- **Think ticks:** a golem thinks on ticks where `gameTime mod think.interval = phase`, with **`phase` = [`phase(GOLE, [UUID], think.interval)`](conventions.md#phases)**.
- **A per-level think queue** of entries (due tick, UUID, golem generation), ordered by due tick then UUID. Each golem has at most one live entry. At the end of each server tick, entries due at or before now are popped in order:
  - **every pop counts** toward `MAX_THINK_POPS_PER_TICK` (64), stale or not; a stale entry (a removed, picked-up or retired golem) is dropped;
  - at most **`MAX_THINKS_PER_TICK` (32)** thinks run.

  Entries left over keep their **original due time** and run first next tick. A think reschedules its golem at its next phase tick under the tuning then in force.
- **No re-entry:** a think never starts while its golem's operation guard is held ([conventions, Re-entry](conventions.md#re-entry): it waits for its next phase tick), and a golem never thinks twice in a tick.

**Vectors** (reference implementation; `phase` at interval 10 and 20):

| UUID (most, least) | 10 | 20 |
|---|---|---|
| `0000000000000000`, `0000000000000001` | 4 | 4 |
| `0123456789ABCDEF`, `FEDCBA9876543210` | 8 | 18 |
| `FFFFFFFFFFFFFFFF`, `FFFFFFFFFFFFFFFF` | 3 | 13 |
| `8000000000000000`, `0000000000000000` | 6 | 6 |

**One think does exactly one thing,** first match:
1. **Inert, unreadable or provisional:** nothing.
2. **A target is in flight:** a **travel check** (see "Travel and arrival"), which may become the act.
3. **Otherwise:** a **selection** (below). A selection never acts in the same think.

### Selection

The planner, pinned. Work is charged as under "The work budget".

1. **Carrying:** scan the marks **in rotation from `cursor + 1`** (indices (`cursor` + 1 + j) mod n). The first GIVE, FEED or TIP mark whose role is valid, whose filter passes the carried resource, which is **eligible**, and which accepts: for GIVE, the first destination slot in ascending order where an **indexed probe** accepts one unit; FEED and TIP accept anything. It becomes the target. **None:** `WAIT` with status `NO_DESTINATION`; the golem holds its carry where it stands. **It never drops a carry on the ground.**
2. **Empty-handed:** the same rotation over TAKE and COLLECT marks. In a TAKE, slots **ascending**; the first resource that passes the mark's filter and is accepted by **any** GIVE, FEED or TIP mark **in list order** (filter, eligibility, then the indexed probe) is planned, with `k = min(amount, carry cap, max stack)` (for essentia, `min(amount, carry_essentia)`). In a COLLECT, the query's lowest entity, under the same acceptance test.
3. **Nothing:** `IDLE`; the golem walks home.

- **Eligibility,** in order; the first failure is the mark's **fault**:
  1. the mark's backoff has expired;
  2. its chunk is loaded (`getChunkNow`) and block-ticking (`ServerLevel#shouldTickBlocksAt(long)`, `MC:server/level/ServerLevel.java:492`): else `UNLOADED`;
  3. it is in range of home: else `OUT_OF_RANGE`;
  4. its filter names something the registries hold: else `UNKNOWN_FILTER`;
  5. FEED: the owner rule holds: else `OWNER_AWAY`;
  6. TAKE and GIVE: the capability exists on that face, read through the mark's cache: else `NO_HANDLER`;
  7. TAKE and GIVE: the handler's `size()` ≤ `MAX_HANDLER_SLOTS` (256): else `TOO_LARGE`.

  Otherwise `OK`.
- **Probes** are indexed: `insert(slot, R, 1, tx)` inside a selection root that is **always aborted**, never the resource-only `insert`, whose default walks every slot (`NF:transfer/ResourceHandler.java:181–190`). The root is opened only when `Transaction.getLifecycle() == NONE` (else the think is deferred, charging nothing) and closed before the think returns, on every path.
- **Barriers inside a selection.** A capability lookup, `size()`, slot read or probe runs foreign code, which may move the golem, replace a block or edit state. **After each one,** the selection rechecks the golem live, not removed or picked up, in its level, the owner and the mark generation unchanged, and the carry unchanged. A failure aborts the selection root and ends the think as `RETIRED` (no plan, no backoff, the resume point cleared). A plan is installed only after the last barrier passes.
- **Partial deliveries** are normal: a take is never sized to a destination's room; the remainder goes to the next acceptor at a later think.
- **The cursor** moves only when an act completes as `MOVED` or `REFUSED` (see "Acts").

**Vectors** (reference implementation; one think every 10 ticks; carry cap 24; each mark at distance 0, so a selection's act follows at the next think):

*Delivery.* m0 TAKE [iron 40, empty, coal 10]; m1 GIVE, filter iron, one slot of room 30; m2 TAKE [sand 5]; m3 GIVE, two empty slots; m4 TAKE [gold 64] in an unloaded chunk.

| Tick | Think | Work | Cursor after |
|---|---|---|---|
| 0 | select take m0 slot 0, iron 24 | 4 | −1 |
| 10 | act take m0: 24 | 2 | 0 |
| 20 | select give m1 | 2 | 0 |
| 30 | act give m1: 24 | 2 | 1 |
| 40 | select take m2 slot 0, sand 5 | 4 | 1 |
| 50 | act take m2: 5 | 2 | 2 |
| 60 | select give m3 | 2 | 2 |
| 70 | act give m3: 5 | 2 | 3 |
| 80 | select take m0 slot 0, iron 16 | 5 | 3 |
| 90 | act take m0: 16 | 2 | 0 |
| 100 | select give m1 | 2 | 0 |
| 110 | act give m1: 6 (its room) | 2 | 1 |
| 120 | select give m3 | 3 | 1 |
| 130 | act give m3: 10 | 3 | 3 |
| 140 | idle | 10 | 3 |

### Travel and arrival

A selection sets **one in-flight target**: the mark (index and generation), the plan, and a path request (see "Pathing"). **The golem never retargets while travelling:** a think during travel checks only that target, even if a better source appears.

**Arrival** is judged only by the **arrival sphere**: the golem's feet within `interact.reach` of the marked block's centre. A path's success is not arrival. The path targets the **stand cell** (the cell in front of the marked face) with `reachRange` 0, so a reached path ends in it; once the path is done, the golem **finishes** by steering straight to the stand cell's bottom centre with its move control (`MoveControl#setWantedPosition`) for at most 20 ticks. That point is inside the sphere at every allowed reach:

| Face of a mark at (0, 0, 0) | Stand cell | Feet at | Distance to (0.5, 0.5, 0.5) |
|---|---|---|---|
| east | (1, 0, 0) | (1.5, 0, 0.5) | 1.118 |
| up | (0, 1, 0) | (0.5, 1, 0.5) | 0.5 |
| down | (0, −1, 0) | (0.5, −1, 0.5) | 1.5 (= the minimum reach) |

First match:

| # | Check | Result |
|---|---|---|
| 1 | the target was retired (its mark edited or removed, or home moved) | `RETIRED`: this think becomes a **selection** |
| 2 | the mark's chunk is no longer loaded and block-ticking | target cleared; fault `UNLOADED` with `path.backoff` |
| 3 | `travel.max_ticks` have passed since the selection (queue wait included) | target cleared; fault `UNREACHABLE` with `path.backoff` |
| 4 | **arrived** (in the sphere) | this think is the **act**, which begins by rerunning the mark's **complete eligibility** (see "An act") |
| 5 | its path request is still queued | wait; status `WAITING_PATH` |
| 6 | the path is null or `!canReach()` (`MC:world/level/pathfinder/Path.java:120`) | target cleared; fault `UNREACHABLE` with `path.backoff` |
| 7 | the path is done and the finishing steer is under way | still walking |
| 8 | the navigation stopped short (vanilla's timeout or stuck detection, `MC:world/entity/ai/navigation/PathNavigation.java:292–319`), or the finishing steer ended outside the sphere | the first time: a new path request; the second: target cleared, `UNREACHABLE` with `path.backoff` |
| 9 | otherwise | still walking |

**Never teleports.** A golem whose home is unreachable stands where it is, with status `HOME_UNREACHABLE`, and tries again after `path.backoff`.

**Vectors** (reference implementation; *fairness*): m0 TAKE [ender pearl 4] at distance 1, observable but never extractable; m1 TAKE [sand 3] at distance 2; m2 GIVE, two empty slots, at distance 1; `refuse.backoff` 30 ticks. The refused first source doesn't starve the later one, and the golem keeps its target at tick 50 although m0's backoff has just expired:

| Tick | Think | Cursor after |
|---|---|---|
| 0 | select take m0, pearl 4 (work 4) | −1 |
| 10 | travel | −1 |
| 20 | act take m0: **REFUSED**, backoff until 50 (work 2) | 0 |
| 30 | select take m1, sand 3 (work 4) | 0 |
| 40, 50 | travel (m0 eligible again at 50: **no retarget**) | 0 |
| 60 | act take m1: 3 (work 2) | 1 |
| 70 | select give m2 (work 2) | 1 |
| 80 | travel | 1 |
| 90 | act give m2: 3 (work 2) | 2 |
| 100 | select take m0, pearl 4 (work 5) | 2 |
| 110 | travel | 2 |
| 120 | act take m0: REFUSED, backoff until 150 (work 2) | 0 |
| 130 | idle (work 3) | 0 |

### The work budget

- **One unit** is: a mark's eligibility read (the capability read and `size()`); an indexed slot read; an indexed probe, insert or extract; a COLLECT query; or one COLLECT candidate considered. Role and filter checks read the mark itself and are free.
- **Each think** may spend **`MAX_THINK_WORK` = 1,024 units**, acts included. So a level spends at most **32 × 1,024 = 32,768 units per tick**.
- **A selection at the cap** stops, closes its root, and saves a **resume point**: (rotation offset, source slot, destination mark, destination slot). The next think resumes there and **revalidates**: it charges the mark's eligibility and the slot read again before continuing. Any mark edit, home change, carry change, cursor change or tuning reload clears the resume point.
- **An act at the cap** commits what it has moved (each indexed op is its own nested transaction) and exits `MOVED` if it moved anything, else `REFUSED`. The rest waits for a later think.

**Vectors** (reference implementation; *budget*, with the cap set to 8 for the vector): m0 TAKE [empty, empty, stone 5]; m1 GIVE with four full slots of dirt, then an empty slot.

| Tick | Think | Work |
|---|---|---|
| 0 | **yield** at (offset 0, source slot 2, destination mark 1, destination slot 3) | 8 |
| 10 | resume: select take m0 slot 2, stone 5 | 5 |
| 20 | act take m0: 5 | 2 |
| 30 | select give m1 | 6 |
| 40 | act give m1: 5 | 6 |
| 50 | idle | 4 |

**Eligibility reads share the cap; they aren't bounded by the mark count.** A destination's eligibility is read again for every candidate resource. *Eligibility* vector (reference implementation): m0 TAKE with 20 slots of 20 different resources, one each; m1 GIVE whose single slot is full of another resource. The idle selection spends **61 units**: 1 eligibility read of m0, then for each slot one read, one eligibility read of m1 and one refused probe, so **21 eligibility reads** in all.

### Backoff and faults

- **REFUSED:** an act that completed and moved nothing, including a cancelled `GolemWorkEvent` and a pre-commit exception. That mark is ineligible for `refuse.backoff` ticks. Because the cursor still advances to it, an observable but unextractable source **can't starve** the marks after it.
- **UNREACHABLE** and **UNLOADED** during travel: `path.backoff` ticks.
- **RETIRED** (an edit, or a barrier that finds the golem, owner, generation, reach or handler changed) and **DEFERRED** (a transaction already open) take no backoff and don't move the cursor.
- **Faults** are kept per mark, at most 16, in the record (see "Sync and state"), and shown in the overlay.

## Pathing

**Vanilla's `GroundPathNavigation`, subclassed as `GolemNavigation`.**

**Deterministic,** pinned as: the job, the target, the path request and the path are pure functions of the golem's record and the world's blocks. Vanilla's A* draws no random numbers (`MC:world/level/pathfinder/PathFinder.java:76–119`). Every request names **one target**: multiple targets would be tie-broken through a `HashSet` stream (`:122–128`). Body motion (physics, water, pistons) is not promised.

**The request:**
- **Target:** the stand cell (the mark's position offset by its face), or home; **`reachRange` 0**, so A* succeeds only on the target node itself (`MC:world/level/pathfinder/PathFinder.java:84–87`). Arrival is the sphere under "Travel and arrival", never the path's success.
- **Surface relocation off:** `setCanPathToTargetsBelowSurface(true)` (`MC:world/entity/ai/navigation/GroundPathNavigation.java:146`), so vanilla never moves the target up its column (`:50–52`, `:57–88`) and no column scan runs. The target's chunk must be loaded (`:45–48`).
- **Doors:** never opened; a closed door is a wall.

**The search queue:** a per-level ordered set of requests (due tick, UUID, golem generation, target), **at most one physical entry per golem**: a newer request **removes** the older entry from the set, and a golem's removal, pickup or unload removes its entry, so the set never holds more entries than loaded golems. A request is also retired, in place, when its target is retired; such a stale entry is dropped when popped.
- **Only queue service searches.** `GolemNavigation#createPath` returns null unless the queue is servicing that golem. Vanilla calls `recomputePath` from `sendBlockUpdated` for every navigating mob whose path crosses a changed shape (`MC:server/level/ServerLevel.java:1178–1196`); ours **only enqueues a request**.
- **Per tick, per level:** at most **`MAX_SEARCH_POPS_PER_TICK` (32)** pops, stale ones included, and at most **`MAX_PATH_SEARCHES_PER_TICK` (2)** searches. Left-over requests keep their due time.
- **The search cap is a derived quantity,** chosen so that the measured worst-case search × the cap stays under half the p99 share ("Performance") on the reference machine; raise it only with a measurement. One vanilla A* over 512 visited nodes is on the order of 0.1–0.5 ms, so 8 would spend the whole share. Two a tick is 40 searches a second, more than 32 golems repathing at this spec's cadence need; the queue carries the rest.

**Bounds of one search,** with every input set explicitly at queue service, never read from the attribute (another mod's `FOLLOW_RANGE` modifier changes nothing):
- **The length** `L` = max(`path.max_length`, 16), passed explicitly through `createPath(target, 0, L)` (`PathNavigation.java:128–129`), which uses it as the maximum path length (`:136–162`).
- **Visited nodes:** `pathFinder.setMaxVisitedNodes(16 × L)` and `resetMaxVisitedNodesMultiplier()` (`:83–85`), immediately before the call. So **512 nodes at the default and 1,024 at the tuning maximum** of 64.
- **The region:** a cube of radius `L` + 8 (`:160–161`), whose constructor calls `getChunkNow` once per chunk column it spans (`MC:world/level/PathNavigationRegion.java:42–47`): ≤ 36 columns at the default (radius 40), ≤ 100 at the maximum (radius 72). **It never loads a chunk.**
- **No column scan** for the target (surface relocation is off). Vanilla's separate start-position scan up through water (`GroundPathNavigation.java:96–113`) stays, bounded by the water column.

## Acts

### The operation guard

Each golem has **one [operation guard](conventions.md#operation-guards)**, taken before the `GolemWorkEvent`, a probe, a capability call or a spawn. While it is held:
- the Baton's edits and pickup refuse (`message.vftv.busy`);
- no think starts for this golem;
- a death **defers its settlement** to the release (see "Damage, death and regeneration").

### The carry ledger

- **The carry** is one resource (an `ItemResource` with its components, or an aspect) and an amount, or nothing.
- **Its journal** is our own `SnapshotJournal` in `neoforge.golem` (as the essentia spec's jars; [essentia, Transactions](essentia.md#transactions)): one snapshot per transaction depth it is touched in; a child's commit stands only while its parent commits; a root abort restores it exactly.
- **Transaction-busy** from its first snapshot until its root closes, on commit or abort.
- **The golem exposes no capability.** Hoppers and other mods can't reach its carry.

### An act

**Exits.** Every act ends in exactly one of four:

| Exit | When | Cursor | Backoff | Target | Notification |
|---|---|---|---|---|---|
| `DEFERRED` | a transaction is already open (step 1) | unchanged | none | kept: the next think retries the act | none |
| `RETIRED` | a barrier fails: the golem, owner, mark generation, reach, handler or carry changed, or the mark is no longer eligible | unchanged | none | cleared, with its path request and the resume point | none |
| `REFUSED` | the attempt completed and moved nothing: the hook cancelled it, the handler or spawn accepted nothing, or a **pre-commit exception** aborted it | this mark | `refuse.backoff` | cleared | none |
| `MOVED` | the attempt completed and moved something | this mark | none | cleared | `GolemEvent(MOVED)` |

Only `REFUSED` and `MOVED` complete an attempt. A pre-commit exception that is itself an invalidation (the golem removed, say) is `RETIRED`. **Classification is separate from cleanup:** the guard's release and any deferred death settlement run in a `finally`, whatever the exit.

**A barrier** rechecks, against the live world: the golem alive, not removed or picked up, in its level; the owner and the mark generation unchanged; still in the arrival sphere; the mark's chunk loaded and block-ticking; for TAKE and GIVE, the mark's cache still returns **the same handler instance**; for FEED, the owner rule; the carry equal to the ledger's last committed value. **A barrier runs after every foreign callback** listed below, and a failure aborts any open root before exiting `RETIRED`.

At arrival, in a think, in this order:
1. **Lifecycle:** if `Transaction.getLifecycle() != NONE`, `DEFERRED`, nothing charged (`NF:transfer/transaction/Transaction.java:99–109`).
2. **Acquire** the operation guard.
3. **Complete eligibility,** rerun (every check under "Selection", in order, with current tuning: range, filter, role validity for the job, capability, `size()`): a failure records the fault and exits `RETIRED`. Then a barrier.
4. **Post `GolemWorkEvent`** (cancellable), immutable payload. **Cancelled:** `REFUSED`. Then a barrier.
5. **Perform** the role (below), with a barrier after each capability call, read, probe, transfer or spawn.
6. **Settle the outcome from the ledger.** A root's close runs publication callbacks that may throw **after** it has committed (`Transaction.java:205–240`; `SnapshotJournal.java:67–91`). The outcome is read from the ledger after close, never from whether close threw; such a throw is logged once and the move stays committed. **A committed move is never retried or refunded.**
7. **Exit** as tabled. `GolemEvent(MOVED)` is posted after the guard's release, caught separately, so the rest still run.

**TAKE and GIVE:** one root, opened in step 5 and closed within the call. Inside it, each indexed op is a nested transaction followed by a barrier; the ledger's credit (TAKE) or debit (GIVE) is in the same root. The root commits iff every barrier passed and the ledger's change equals the handler's reported moves; otherwise it aborts and nothing moved. A TAKE extracts `min(planned amount, the slot's live amount, the carry's live room, the resource's max stack)`, read after step 4. Arguments are checked first, as every handler of ours does ([crucible, Essentia out](crucible.md#essentia-out)).

**FEED and TIP** follow the [provisional-spawn pattern](crucible.md#committing-a-craft):
1. create an `ItemEntity` holding **a copy of one item** of the carry (count 1, components kept), placed and timed as under "Feed and Tip";
2. `addFreshEntity`, inside a `try`;
3. **revalidate:** it returned `true`; a barrier passes; our entity is alive, not removed, holding exactly the copy (`ItemStack.matches`);
4. **only then** debit the ledger by one (to empty if that was the last): `MOVED`.

A false return is `REFUSED`; a barrier failure `RETIRED`; a throw or a changed entity `REFUSED`. In each, our entity is discarded if it was added and nothing is debited. Whatever listeners changed themselves is left as they made it.

**COLLECT,** after step 4's barrier:
1. **Identity:** the planned entity object is still the one `level.getEntity(id)` returns, with the same UUID; it is an `ItemEntity`, alive, not removed, inside the box, without a pickup delay, holding the planned resource (`ItemStack.isSameItemSameComponents`). Else `RETIRED`.
2. **Recompute** `k = min(planned amount, the entity's live count, the carry's live room, the resource's max stack)`, **immediately before** the move. `k` = 0 is `REFUSED`, with nothing credited.
3. In one step with no foreign code between: shrink the entity's stack by `k`, credit the ledger by `k`, and **only then** discard the entity if its stack is empty: `MOVED`. A discard callback that throws finds the move already complete.

### Carry conservation

| Fate | The carry |
|---|---|
| delivered | moved exactly: the ledger and the handler's reports agree, or nothing moved |
| picked up | kept in `vftv:golem_state` |
| the golem dies | the items and the core drop; essentia spills once (see "Damage, death and regeneration") |
| upgraded by infusion | kept: `transmute` keeps the components |
| its chunk unloads | saved with the golem |
| unreadable | kept as the raw record |
| **external drop loss:** a `LivingDropsEvent` listener cancels or changes the death drops (`MC:world/entity/LivingEntity.java:1597–1599`); a drop's spawn is refused; a full-inventory pickup drop is refused | lost without a spill. These are other mods' and vanilla's decisions about drops, as for every container ([essentia, Removal and drops](essentia.md#removal-and-drops)). |
| discarded by a command or another mod (`remove(DISCARDED)`), or removed as `KILLED` without dying | lost silently, as a jar item's expiry is |

The book lists every loss row (see the contracts on #35).

## Chunks and persistence

- **Never loads a chunk.** Every block read and capability lookup checks `getChunkNow` and `shouldTickBlocksAt` first; path regions read loaded chunks only; nothing takes a ticket.
- **A mark in an unloaded chunk** is skipped with fault `UNLOADED`. Carrying with only unloaded destinations is `NO_DESTINATION`.
- **A golem in a chunk that isn't entity-ticking** isn't ticked by vanilla (`ServerLevel#isPositionEntityTicking`, `ServerLevel.java:1823`), and its chunk saves it with its entities. A golem that walks toward such a chunk stops there.
- **Transient, rebuilt after load:** the target, the path request, the resume point, the operation guard, queue entries and overlay subscriptions. A loaded golem schedules a think at its phase and selects afresh, carry intact.
- **On removal** (unload, death, pickup, discard): its queue entries and path request retire, its capability caches are released, and its overlay subscribers are cleared.

**The golem's record** (`addAdditionalSaveData` and `readAdditionalSaveData`, `MC:world/entity/Entity.java:2224–2226`), one key `vftv:golem`, with vanilla's own `EntityReference` holding the owner. Its schema is under "Sync and state".

## Multiplayer and automation

- **Owner** = the placer: the entity implements `OwnableEntity` (`MC:world/entity/OwnableEntity.java`) over the stored UUID, and keeps a display name refreshed whenever the owner attunes. **Giving away the item gives away the golem:** the next placer owns it.
- **Owner only:** attuning, marks, filters, roles, home and pickup, each rechecked at the edit. **Anyone** can read the golem card.
- **The protection promise is exactly:**
  - **marking is a hand action:** spawn protection, `mayInteract`, `RightClickBlock` (a cancel, a denied item use, or a denied block use read from the live event's final `getUseBlock()`) guard every Baton edit, and `EntityInteract` guards the golem gestures;
  - **placing** passes vanilla's use checks and the spawn's join event;
  - **running is automatic and isn't re-authorised,** as a relay network isn't ([wands, Creation, removal and sharing](wands.md#creation-removal-and-sharing)). **Stated limitation:** capability access isn't a player action, so a golem marked on a container while its owner was allowed keeps working;
  - **the claim-mod hook** is `GolemWorkEvent`: posted inside the operation guard, after arrival and before any capability call or spawn; a cancel makes the act REFUSED with `refuse.backoff`; after it, everything the act depends on is revalidated (see "An act"). v1 has no integration with a named claim mod ([compatibility, Claim mods](compat.md#claim-mods)).
- **Offline owner:** the golem keeps working; FEED marks are ineligible (`OWNER_AWAY`). An owner in another dimension is not away.
- **Two golems at one chest** act in think-queue order (due tick, UUID), one root each. Due-time and UUID ordering make queue service deterministic; the order of players' inputs across runs isn't promised.
- **No custom client-to-server payload.** Every input is a vanilla use, interact or container click, validated by vanilla and by the tables above; GameTests drive each with a non-owner, the wrong level, out of range and an unloaded golem.

| Path | Behaviour |
|---|---|
| Players | the Baton, placing, inventory fitting, damage |
| Hoppers, item pipes | nothing: the golem exposes no capability |
| The Worktable, the Lathe, casting, infusion starts | never: no role reaches them |
| Pedestals and Singing Bowls | GIVE loads them and restores promised stacks, under their handler's rules |
| Crucibles | FEED and TIP drop into them; decanting TAKE extracts from their side faces |
| Fake players (other mods) | no special case: their Baton use passes the same checks |

## Damage, death and regeneration

- **It never attacks.**
- **Damage:** the environment and mobs, as vanilla. Damage whose responsible player (`DamageSource#getEntity`, so a projectile's owner counts) isn't the owner is refused unless the level allows PvP (`ServerLevel#isPvpAllowed`, reading the PvP game rule, `MC:server/level/ServerLevel.java:1905–1906`). The owner may always hurt their own golem.
- **Unreadable golems** are invulnerable except to damage in `#minecraft:bypasses_invulnerability` (`/kill`, the void).
- **Regeneration:** one health point every material `regen` ticks, once the golem has gone `regen.quiet` ticks unhurt. There is no repair item; health persists through pickup and upgrade.
- **Death is settled at `dropAllDeathLoot`** (`MC:world/entity/LivingEntity.java:1586–1599`), which the golem overrides **whole**. Vanilla's version always posts `LivingDropsEvent`, even for an empty collection (`NF:common/CommonHooks.java:400–401`), so overriding only `dropEquipment` would post it twice for a deferred death. A golem has no loot table, no equipment and no experience, so nothing of vanilla's is skipped.
  - **The capture:** the death's level and position, the damage source and whether a player hit it recently, taken when `dropAllDeathLoot` is called.
  - **Guard free:** the **settlement** runs now.
  - **Guard held** (death inside a callback during an act): the capture is stored as the **pending settlement**, and `dropAllDeathLoot` returns having posted nothing. The settlement runs in the guard's `finally`, after the root has closed **on commit or on abort** and the journal has settled.
  - **The settlement,** exactly once:
    1. **claim** the carry and the core, clearing them from the record;
    2. build the drop entities at the captured position: the carried stack and the core item;
    3. post **exactly one** `LivingDropsEvent` with them, through the platform seam (`CommonHooks.onLivingDrops`, `LivingEntity.java:1598`);
    4. unless it was cancelled, spawn each remaining drop with `addFreshEntity`;
    5. then, each caught separately: `GolemEvent(SPILLED)` with the claimed essentia (cause `SpillCause.DIED`, the captured level and position), if any; `GolemEvent(DIED)`; `golem.death` plays.
  - A throwing listener in any step is logged once; the claim stands and the later steps still run.
  - The material body is lost.

## Inspection and teaching

Every number a decision rests on is shown exactly, live, and never in prose.

### The golem card

**HUD layer `vftv:golem`.** It shows while the client's entity hit is a golem within the player's `entity_interaction_range`, a Baton is in either hand, no screen or debug overlay is open, F1 is off, the player isn't a spectator, and the aspects spec's strike card isn't showing. Layer order: `vftv:vis`, `vftv:crucible`, `vftv:golem`, then the strike card.

**Pages,** turned on the client when an interact with the Baton on that golem returns `SUCCESS`; page 0 again after 100 ticks without one, or on another golem (the [essentia spec's cursor](essentia.md#inspection-and-teaching)):

| Page | Lines |
|---|---|
| 0 status | the golem's name; `hud.vftv.golem.owner` (the owner's name); the status (below); the target line `hud.vftv.golem.target` (the mark's number and role) while one is in flight |
| 1 carry | `hud.vftv.golem.carry_item` (count, item name) or `.carry_essentia` (amount, aspect name) or `.carry_empty`. An undiscovered aspect is named `essentia.vftv.unheard` and a missing one `essentia.vftv.missing`; the amount is always exact |
| 2 body | `.health` (current and max, one decimal), `.speed`, `.carry_caps` (items, essentia), `.range` (horizontal, vertical), and `.fire_immune` if so |
| 3 marks | `.marks` (count, 16); for the owner with the overlay, `.fault_count` per fault kind present (fault name, count) |

Each page ends with `hud.vftv.golem.page` (page, 4).

**Narration:** a page turn queues the newly selected page's lines once as system narration ([localisation, HUD narration](accessibility.md#hud-narration)). Passive status updates on an unchanged page, and the 100-tick return to page 0, narrate nothing.

**Overflow:** **wrapping, not clipping.** Each line wraps to `maxInner` with the font's own `split`, so no number or name is ever cut; then the frame, padding, position, clamp, caption band, pose-scale fallback and sheets are the [crucible HUD](crucible.md#the-hud)'s, unchanged, as the [infusion HUD](infusion.md#the-hud) does. **Head:** the golem's name; **foot:** `hud.vftv.golem.page`; a card's sheets are separate from its four pages. The layout rebuilds only when the golem's synced data, the viewer's knowledge, the page, the sheet, the resources, the language, the screen size or the [caption band's](accessibility.md#the-caption-band) revision change; our draw code allocates nothing per frame.

**Statuses** (synced as one byte, with the target or mark number as a second):

| Status | When | Lang key, args |
|---|---|---|
| `WORKING` | a target in flight | `status.vftv.golem.working` 2: the role, the mark's number |
| `RETURNING` | walking home with nothing to do | `.returning` 0 |
| `IDLE` | at home with nothing to do | `.idle` 0 |
| `THINKING` | a selection yielded at the work cap | `.thinking` 0 |
| `WAITING_PATH` | its path request is queued | `.waiting_path` 0 |
| `NO_DESTINATION` | carrying, nothing accepts it | `.no_destination` 1: the resource's name |
| `OWNER_AWAY` | only FEED marks would accept, and the owner is absent | `.owner_away` 0 |
| `UNREACHABLE` | the last target couldn't be reached | `.unreachable` 1: the mark's number |
| `HOME_UNREACHABLE` | home couldn't be reached | `.home_unreachable` 0 |
| `INERT` | a missing material, core or job | `.inert` 0 |
| `UNREADABLE` | an unreadable record | `.unreadable` 0 |

### The marks overlay

**S2C `GolemMarks(entityId, golem UUID, revision, home, target, marks)`**, `marks` ≤ 16 of `{pos, face, role, filter id or none, fault}`.
- **Subscribed** while the owner holds a Baton attuned to that golem in either hand, tracks the golem, and is in its level. Each player's subscriptions are re-evaluated on a hand change, an attunement and every 10 ticks.
- **Baseline** on subscribing; **updates** when the revision changes (any mark or home edit, a fault change, a target change), coalesced to at most one per golem per subscriber per tick; **`GolemMarksClear(entityId)`** on unsubscribing: a hand switch, untracking, unload, a level change, logout or the golem's removal.
- **The client** binds each snapshot to the entity id and golem UUID, and drops it when that entity leaves its level or the UUID differs.
- **Drawn** (extract-then-submit, cosmetic): each marked face's role sprite with its 1-based number; a fault glyph on faulted marks; the home ring; a line from the golem to its current target.
- **Roles and statuses never rest on hue:** each role sprite (16 × 16) and each status glyph (9 × 9) is a distinct silhouette at its real size, reviewed in the greyscale and colour-vision contact sheets ([localisation, Colour and contrast](accessibility.md#colour-and-contrast)).

### Other views

- **Status glyph:** the status's 9×9 sprite above the golem, for any player holding a Baton within 24 blocks, from the synced byte.
- **Tooltip** of a golem item: body, core or `.no_core`, marks (count and 16), carry, health; that fitting a core clears the marks.
- **The fork's strike** on a golem reads the golem's own aspects, never its carry ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)).
- **The book:** fact sources `golem` and `golem_material` (the research spec amended in this change), over the synced `GolemRules` and the material registry. Pages state every rule live.

## Rendering and sound

### The model

**Frozen geometry,** in model pixels (1/16 block), origin at the feet's centre, y up, −z the front. Texture 64 × 32. Box UVs in vanilla's layout (each cuboid's net at its UV origin, width 2·d + 2·w, height d + h); `mirror` reuses the left net for the right side.

| Part | Pivot | Cuboid from | Size (w × h × d) | UV origin | Mirror |
|---|---|---|---|---|---|
| `body` | (0, 4, 0) | (−4, 4, −2) | 8 × 6 × 4 | (24, 0) | |
| `head` | (0, 10, 0) | (−3, 10, −3) | 6 × 5 × 6 | (0, 0) | |
| `arm_left` | (5, 9, 0) | (4, 3, −1.5) | 2 × 6 × 3 | (48, 0) | |
| `arm_right` | (−5, 9, 0) | (−6, 3, −1.5) | 2 × 6 × 3 | (48, 0) | yes |
| `leg_left` | (2, 4, 0) | (0.5, 0, −1.5) | 3 × 4 × 3 | (0, 11) | |
| `leg_right` | (−2, 4, 0) | (−3.5, 0, −1.5) | 3 × 4 × 3 | (0, 11) | yes |
| `core` | (0, 7, −2) | (−1, 6, −3) | 2 × 2 × 1 | (12, 11) | |
| `bulb` | (0, 8, 2) | (−2, 6, 2) | 4 × 4 × 2 | (18, 11) | |

- **Motion:** legs swing ±30° and arms ±20° with vanilla's walk animation (opposite phase); carrying, both arms are raised 60° forward. Client time only.
- **The carried item** renders at (0, 6, −5) px, scale 0.5, `ItemDisplayContext.FIXED`, from the synced copy of one item.
- **The bulb** renders only for an essentia carry: glass from the texture, with an inner fill tinted by the aspect's colour, min(4, `⌈4 × amount / carry_essentia⌉`) px tall: a carry kept above a lowered cap fills the bulb and no more, while the card shows the exact amount. Identity is on the card, never the tint.
- **The core** is tinted by nothing; its texture region carries the teal pip.
- The model layer, renderer and animation are build-phase code. **Allocation:** zero per frame in our renderer, card and overlay draw code; engine submission costs count within the client share.

### Items

`assets/vftv/items/golem.json`, vanilla's component select (`MC:client/renderer/item/properties/select/ComponentContents.java:22–38,46–57`; `SelectItemModelProperties.java:24`), with unknown and absent values taking the fallback (`MC:client/renderer/item/SelectItemModel.java:104–117`):

```json
{"model": {"type": "minecraft:select", "property": "minecraft:component", "component": "vftv:golem_body",
  "cases": [
    {"when": "vftv:wood",       "model": {"type": "minecraft:model", "model": "vftv:item/golem/wood"}},
    {"when": "vftv:terracotta", "model": {"type": "minecraft:model", "model": "vftv:item/golem/terracotta"}},
    {"when": "vftv:stone",      "model": {"type": "minecraft:model", "model": "vftv:item/golem/stone"}},
    {"when": "vftv:brass",      "model": {"type": "minecraft:model", "model": "vftv:item/golem/brass"}},
    {"when": "vftv:echo",       "model": {"type": "minecraft:model", "model": "vftv:item/golem/echo"}}],
  "fallback": {"type": "minecraft:model", "model": "vftv:item/golem/unknown"}}}
```

No custom item-model property is registered. The cores and the Baton have plain `minecraft:model` definitions.

### Sound

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:golem.step` | 3 | every 2 blocks walked | at the golem, to everyone in range; `SoundSource.NEUTRAL`, volume 0.5, pitch 1.0 |
| `vftv:golem.work` | 3 | a committed act | likewise, volume 1.0, at most once per golem per 20 ticks |
| `vftv:golem.place` | 1 | a committed placement | likewise |
| `vftv:golem.pickup` | 1 | a pickup | likewise |
| `vftv:golem.hurt` | 3 | hurt | likewise |
| `vftv:golem.death` | 1 | death | likewise |
| `vftv:baton.attune` | 1 | an attunement | at the player, `SoundSource.PLAYERS`, volume 1.0 |
| `vftv:baton.mark_add` | 1 | a mark added, a role or filter changed, home set | likewise |
| `vftv:baton.mark_remove` | 1 | a mark removed or a filter cleared | likewise |

**Files:** `assets/vftv/sounds/golem/step_0.ogg` … `step_2.ogg`, `work_0` … `work_2`, `place.ogg`, `pickup.ogg`, `hurt_0` … `hurt_2`, `death.ogg`; `assets/vftv/sounds/baton/attune.ogg`, `mark_add.ogg`, `mark_remove.ogg`; in the Stage 2 register ([art style §7](art-style.md#7-sound-palette)), ≤ 3 s, faded tails. `sounds.json` entries carry no volume or pitch overrides and name `subtitles.vftv.<event>`.

## Authored content

Authored inputs follow the [authored-content envelope](conventions.md#the-envelope).

```
content/golems/materials.json     the five materials
content/golems/recipes.json       the arcane, crucible and infusion recipes this spec owns
content/golems/bootstrap.json     the reviewed material and essentia sources
content/golems/fixtures.json      the vector tables, transcribed
content/lang/en_us/golems.json    every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `materials.json` | `vftv:golem_materials/1` | each material `<ns>:<path>` → `data/<ns>/vftv/golem_material/<path>.json` |
| `recipes.json` | `vftv:golem_recipes/1` | each recipe `<ns>:<path>` → `data/<ns>/recipe/<path>.json`, its body unchanged except the [compatibility spec's ingredient widening](compat.md#consumed) |
| `bootstrap.json` | `vftv:golem_bootstrap/1` | nothing: read by the review and a lint |
| `fixtures.json` | `vftv:golem_fixtures/1` | nothing: read by the unit tests |
| `lang/<lang>/golems.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json` ([conventions, Lang](conventions.md#lang)) |

A recipe id declared in two `content/` files, of any type, fails datagen.

### Materials

`{"schema": "vftv:golem_materials/1", "materials": {<id>: <entry>}}`, with **exactly** the five ids and the shipped values under "Materials", each entry in that table's fields:

```json
{"schema": "vftv:golem_materials/1", "materials": {
  "vftv:terracotta": {"health": 20, "speed": 0.26, "carry_items": 24, "carry_essentia": 16,
                      "armor": 2, "fire_immune": true, "regen": 200, "range_bonus": 0,
                      "texture": "vftv:textures/entity/golem/terracotta.png"}
}}
```

**Rejected:** another id or a missing one; a field outside the table or out of range; a value differing from the shipped table; a `texture` that isn't `vftv:textures/entity/golem/<path>.png` for its own path.

### Recipes

`{"schema": "vftv:golem_recipes/1", "recipes": {<id>: <body>}}`. The ids, and the type each must have:

| Id | Body | Rules |
|---|---|---|
| `vftv:golem_wood`, `vftv:golem_terracotta`, `vftv:golem_stone`, `vftv:golem_brass` | [`vftv:arcane`](arcane.md#the-recipe-type) | `research` `vftv:golems`; `kind` `ordinary` (or omitted); result `{"id": "vftv:golem", "count": 1, "components": {"vftv:golem_body": "vftv:<material>"}}`; ingredients include the material (`#minecraft:logs`, `minecraft:terracotta`, `minecraft:polished_deepslate`, `vftv:resonant_brass_ingot` respectively) and `minecraft:amethyst_shard`; every ingredient obtainable in Stages 1–2; each `vis` value ≤ the `vftv:chime` rod's capacity in `content/wands/parts.json` (the lint reads it) |
| `vftv:baton` | `vftv:arcane` | `research` `vftv:golems`; `ordinary`; result `vftv:baton`, count 1; ingredients include `vftv:resonant_brass_ingot`; the same `vis` bound |
| `vftv:blank_core` | `vftv:arcane` | `research` `vftv:golems`; `ordinary`; result `vftv:blank_core`, count 1; the same `vis` bound |
| `vftv:carrying_core` | [`vftv:crucible`](crucible.md#the-recipe-type) | `research` `vftv:golems`; catalyst `vftv:blank_core`; result `vftv:carrying_core`, count 1; 1–3 aspects totalling ≤ ½ of the default `pool.capacity` (100 today; the lint reads the tuning default) |
| `vftv:decanting_core` | `vftv:crucible` | `research` `vftv:golem_decanting`; otherwise as above |
| `vftv:echo_golem` | [`vftv:infusion`](infusion.md#the-recipe-type) | `research` `vftv:echo_golems`; `kind` `ordinary`; centre `vftv:golem`; result exactly `{"item": {"id": "vftv:golem", "count": 1, "components": {"vftv:golem_body": "vftv:echo"}}, "transmute": true}`; components obtainable in Stages 1–3; `instability` 5–25 |

```json
{"schema": "vftv:golem_recipes/1", "recipes": {
  "vftv:golem_terracotta": {
    "type": "vftv:arcane", "research": "vftv:golems", "kind": "ordinary",
    "pattern": [" t ", "tbt", "a a"],
    "key": {"t": "minecraft:terracotta", "b": "vftv:resonant_brass_ingot", "a": "minecraft:amethyst_shard"},
    "result": {"id": "vftv:golem", "count": 1, "components": {"vftv:golem_body": "vftv:terracotta"}},
    "vis": {"vftv:terra": 20, "vftv:ordo": 10}},
  "vftv:carrying_core": {
    "type": "vftv:crucible", "research": "vftv:golems",
    "catalyst": "vftv:blank_core",
    "aspects": {"vftv:motus": 8, "vftv:machina": 6},
    "result": {"id": "vftv:carrying_core", "count": 1}}
}}
```

**Rejected:** an id outside the table or a required id missing; a body of the wrong type; any field outside its type's table; any rule above broken; a created result that is empty or over its maximum stack.

### The bootstrap

The [bootstrap](conventions.md#bootstrap) is `{"schema": "vftv:golem_bootstrap/1", "recipes": {<id>: {"items": [{"item": <id>, "why": <one line>}, …], "aspects": [{"aspect": <id>, "why": <one line>}, …]}}}`, for **every** recipe id in `recipes.json`.

- **Coverage:** `items` lists every ingredient, catalyst, centre and component item of that recipe (for a tag, the alternative the review names), and `aspects` every crucible or infusion aspect (empty for arcane recipes, whose vis is checked by the lint).
- **Review:** each `why` says how the thing is obtained in its recipe's Stage without a golem, and for an aspect which matter dissolves into it in a crucible ([aspects, Coverage and supply](aspects.md#coverage-and-supply)). The review checks the sequence is non-circular.

**Rejected,** beyond the convention: a missing recipe.

### Fixtures

`{"schema": "vftv:golem_fixtures/1", "phase": [<phase>, …], "range": [<range>, …], "scenarios": {<name>: <scenario>}}`, transcribing every vector table in this spec exactly: the phase and range tables, and the scenarios `delivery`, `fairness`, `budget` and `eligibility`. Scenario marks are **not** real marks: they model a mark and its handler. The [fixtures convention](conventions.md#fixtures) applies.

| Record | Fields (required unless a default is given) |
|---|---|
| `<phase>` | `msb`, `lsb`: 16 uppercase hex digits each; `interval` 1–200; `phase` 0 … interval − 1 |
| `<range>` | `home`, `pos`: `[x, y, z]` integers; `r` 4–64; `v` 2–24; `in` boolean |
| `<scenario>` | `interval` 1–200 (default 10); `cap` 1–64; `budget` 1–1,024; `refuse_backoff` 0–6,000 ticks; `marks` 1–16 `<smark>`; `thinks` 1–64 `<think>` |
| `<smark>` | `role` `"take"` or `"give"`; `slots` 1–256 `<slot>`; `dist` 0–64 thinks of travel (default 0); `filter` a `<token>` (default none); `loaded` boolean (default true: false makes the mark fail eligibility); `locked` boolean (default false: true makes every extraction return 0; take only) |
| `<slot>` | `res` a `<token>` (absent: an empty slot); `count` 0–64 (default 0; 0 when `res` is absent); `max` 1–64 (default 64; give only, the slot's capacity) |
| `<token>` | a **fixture resource token**, `[a-z0-9_]{1,16}`: an abstract resource, never a registry id; two slots hold the same resource iff their tokens are equal |
| `<think>` | `tick` ≥ 0; `op` `"select"`, `"travel"`, `"act"`, `"yield"`, `"wait"` or `"idle"`; `cursor` −1–15 after the think; and by `op`: **select:** `kind` `"take"` or `"give"`, `mark`, `slot` and `res` and `amount` (take only), `eta`, `work`; **travel:** `mark`, `eta`; **act:** `kind`, `mark`, `result` `"moved"` (with `amount`) or `"refused"` (with `backoff_until`), `work`; **yield:** `resume` `[offset, source slot, destination mark, destination slot]`, `work`; **wait:** `res`, `amount`, `work`; **idle:** `work` |

**The complete `fairness` scenario:**

```json
{"interval": 10, "cap": 24, "budget": 1024, "refuse_backoff": 30,
 "marks": [
   {"role": "take", "slots": [{"res": "pearl", "count": 4}], "dist": 1, "locked": true},
   {"role": "take", "slots": [{"res": "sand", "count": 3}], "dist": 2},
   {"role": "give", "slots": [{}, {}], "dist": 1}],
 "thinks": [
   {"tick": 0,   "op": "select", "kind": "take", "mark": 0, "slot": 0, "res": "pearl", "amount": 4, "eta": 1, "work": 4, "cursor": -1},
   {"tick": 10,  "op": "travel", "mark": 0, "eta": 0, "cursor": -1},
   {"tick": 20,  "op": "act", "kind": "take", "mark": 0, "result": "refused", "backoff_until": 50, "work": 2, "cursor": 0},
   {"tick": 30,  "op": "select", "kind": "take", "mark": 1, "slot": 0, "res": "sand", "amount": 3, "eta": 2, "work": 4, "cursor": 0},
   {"tick": 40,  "op": "travel", "mark": 1, "eta": 1, "cursor": 0},
   {"tick": 50,  "op": "travel", "mark": 1, "eta": 0, "cursor": 0},
   {"tick": 60,  "op": "act", "kind": "take", "mark": 1, "result": "moved", "amount": 3, "work": 2, "cursor": 1},
   {"tick": 70,  "op": "select", "kind": "give", "mark": 2, "eta": 1, "work": 2, "cursor": 1},
   {"tick": 80,  "op": "travel", "mark": 2, "eta": 0, "cursor": 1},
   {"tick": 90,  "op": "act", "kind": "give", "mark": 2, "result": "moved", "amount": 3, "work": 2, "cursor": 2},
   {"tick": 100, "op": "select", "kind": "take", "mark": 0, "slot": 0, "res": "pearl", "amount": 4, "eta": 1, "work": 5, "cursor": 2},
   {"tick": 110, "op": "travel", "mark": 0, "eta": 0, "cursor": 2},
   {"tick": 120, "op": "act", "kind": "take", "mark": 0, "result": "refused", "backoff_until": 150, "work": 2, "cursor": 0},
   {"tick": 130, "op": "idle", "work": 3, "cursor": 0}]}
```

**Rejected,** beyond the convention: a field that doesn't belong to its `op` or role.

### Lang keys

[Lang files](conventions.md#lang); every key below is required in `en_us`. The English is the intended sense; the content ticket writes the final text, and its review checks that the messages teach the Baton's gestures, roles and filters, that fitting a core clears marks, FEED's owner rule **with the complete handoff warning**, TIP as the dissolve path, no suction routing, and every carry-loss row.

| Key | Args | Sense |
|---|---|---|
| `entity.vftv.golem` | 0 | "Golem" |
| `item.vftv.golem` | 1 | "%s Golem" (the material's name) |
| `golem_material.vftv.wood`, `.terracotta`, `.stone`, `.brass`, `.echo` | 0 | "Wooden", "Terracotta", "Stone", "Brass", "Echo" |
| `golem.vftv.unknown_body` | 0 | "Unknown" |
| `item.vftv.blank_core`, `item.vftv.carrying_core`, `item.vftv.decanting_core`, `item.vftv.baton` | 0 | "Blank Core", "Carrying Core", "Decanting Core", "Baton" |
| `golem_job.vftv.carry`, `golem_job.vftv.decant` | 0 | "carrying", "decanting" |
| `golem.vftv.role.take`, `.give`, `.feed`, `.tip`, `.collect` | 0 | "Take", "Give", "Feed", "Tip", "Collect" |
| `golem.vftv.fault.ok`, `.unloaded`, `.out_of_range`, `.unknown_filter`, `.owner_away`, `.no_handler`, `.too_large`, `.unreachable`, `.refused` | 0 | "fine", "beyond my hearing", "out of range", "an unknown filter", "owner away", "nothing to reach", "too large to search", "unreachable", "refused" |
| `tooltip.vftv.golem.body`, `.core` | 1 | "Body: %s", "Core: %s" |
| `tooltip.vftv.golem.no_core`, `.carry_empty`, `.unreadable`, `.core_clears` | 0 | "No core", "Carrying nothing", "I can't read this golem.", "Fitting a core clears its marks." |
| `tooltip.vftv.golem.marks`, `.carry_item`, `.carry_essentia`, `.health` | 2 | "Marks: %s of %s", "Carrying %s %s", "Carrying %s %s", "Health %s of %s" |
| `tooltip.vftv.golem_core.job` | 1 | "Gives a golem the %s job." |
| `tooltip.vftv.baton.attuned` | 1 | "Attuned to %s" |
| `tooltip.vftv.baton.unattuned` | 0 | "Not attuned" |
| `message.vftv.golem.unknown_body`, `.no_core`, `.unknown_core`, `.inert`, `.not_here`, `.crowded`, `.no_room`, `.not_yours`, `.no_room_inventory`, `.carrying`, `.denied`, `.out_of_range`, `.too_many_marks`, `.bad_filter`, `.home_unloaded`, `.swap_one` | 0 | the refusals named above (`.no_room_inventory`: "I have nowhere to carry it.") |
| `message.vftv.golem.attuned`, `.mark_removed`, `.filter_cleared` | 1 | "Attuned to %s.", "Mark %s removed.", "Mark %s takes anything now." |
| `message.vftv.golem.mark_added`, `.role`, `.filter_set` | 2 | "Mark %s: %s.", "Mark %s: %s.", "Mark %s: only %s." |
| `message.vftv.golem.home_set` | 3 | "Home is now %s, %s, %s." |
| `status.vftv.golem.working` | 2 | "%s at mark %s." |
| `status.vftv.golem.no_destination` | 1 | "Holding %s: nothing will take it." |
| `status.vftv.golem.unreachable` | 1 | "Can't reach mark %s." |
| `status.vftv.golem.returning`, `.idle`, `.thinking`, `.waiting_path`, `.owner_away`, `.home_unreachable`, `.inert`, `.unreadable` | 0 | "Going home.", "Idle.", "Thinking.", "Finding the way.", "Waiting for its owner.", "Can't reach home.", "Something it needs is missing.", "I can't read this golem." |
| `hud.vftv.golem.owner` | 1 | "Owner: %s" |
| `hud.vftv.golem.target` | 2 | "Bound for mark %s (%s)" |
| `hud.vftv.golem.carry_item`, `.carry_essentia` | 2 | "Carrying %s %s" |
| `hud.vftv.golem.carry_empty`, `.fire_immune` | 0 | "Carrying nothing", "Unharmed by fire" |
| `hud.vftv.golem.health`, `.carry_caps`, `.range`, `.marks`, `.fault_count`, `.page` | 2 | "Health %s of %s", "Carries %s items or %s essentia", "Range %s across, %s up or down", "Marks: %s of %s", "%s: %s", "%s of %s" |
| `hud.vftv.golem.speed` | 1 | "Speed %s" |
| `golem.vftv.fact.yes`, `golem.vftv.fact.no` | 0 | "yes", "no" |
| `subtitles.vftv.golem.step`, `.work`, `.place`, `.pickup`, `.hurt`, `.death` | 0 | "Golem steps", "Golem works", "Golem wakes", "Golem lifted", "Golem hurts", "Golem breaks" |
| `subtitles.vftv.baton.attune`, `.mark_add`, `.mark_remove` | 0 | "Baton attunes", "Baton marks", "Baton unmarks" |

**Used, not declared here:** item, aspect and player names; `essentia.vftv.unheard` and `essentia.vftv.missing`; the shared [`message.vftv.busy` and `.unreadable`](conventions.md#message-keys).

## Events

NeoForge events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server through a publisher seam that `neoforge` implements. Payload records live in `api` and are **immutable**, captured before any listener runs: item stacks are copies, positions are values. The payload shape, copying on access and failure rules are the [API spec's](api.md#events). **`GolemWorkEvent` fails closed:** a throwing listener makes the act `REFUSED`, with nothing moved and nothing debited ("An act").

| Event | When | Payload |
|---|---|---|
| `GolemWorkEvent` (cancellable) | at an act, inside the operation guard, after arrival and before any capability call or spawn | golem UUID; owner UUID; level; mark position and face; role; the resource (an item copy or an aspect) and the planned amount |
| `GolemEvent` | after the state it reports has been written | kind and payload below |

| `GolemEvent` kind | Payload |
|---|---|
| `PLACED` | golem UUID; owner UUID; level and position; body; core |
| `PICKED_UP` | golem UUID; owner UUID; level and position |
| `MOVED` | golem UUID; owner UUID; level; mark position and face; role (TAKE, GIVE, FEED, TIP or COLLECT); the resource; the amount moved (for FEED and TIP, always 1; for COLLECT, the picked-up count) |
| `SPILLED` | golem UUID; level and death position; the aspect and amount; cause `api`'s `SpillCause.DIED` |
| `DIED` | golem UUID; owner UUID; level and position; copies of what dropped |

- **Order at death:** the drops, then `SPILLED` (if essentia was carried), then `DIED`.
- **Cancellation** of `GolemWorkEvent` makes the act REFUSED with `refuse.backoff`. Listeners may change the world; the act revalidates after them (see "An act").
- **Once per occurrence.** Nothing fires per think, travel check, path or probe.
- **A throwing listener** is logged once with the position; the state stands, the act is never retried, and our remaining notifications still run. NeoForge's own dispatch to its remaining subscribers is NeoForge's.

**Plain `core` services:** `GolemPlanner.plan`, `GolemRange.inRange`, `GolemPhase.of`. They are **internal in v1** ([API spec, Services](api.md#services)).

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| the golem's record | the entity, key `vftv:golem` (below) | trackers, through the entity data below |
| a golem item's state | the `vftv:golem_state` component (below) | as items are |
| the attunement | the `vftv:baton_attunement` component `{golem: <UUID>, dimension: <level key>}` | as items are |
| the think and search queues, operation guards, targets, resume points, overlay subscriptions, the per-player click record (one event reference, consumed by the next Baton use or replaced by the next click) | per-level or per-player memory | never |
| the marks overlay | built from the record | **`GolemMarks`** and **`GolemMarksClear`**, S2C, to subscribers only |
| golem rules | the tuning record, the admin cap and the fixed limits | **`GolemRules`**, a [Rules record](conventions.md#rules-records) |
| materials | the `vftv:golem_material` registry | every player, through its network codec |

**The record:** `{"format": 1, "body": <material id>, "core": <item id>, "home": [x, y, z], "marks": [<mark>, …], "generation": 0–2³¹−1, "carry"?: <carry>, "cursor": −1–15, "backoff": [{"mark": 0–15, "until": <long>}, …], "faults": [<fault>, …], "owner_name": <string>}`.
- **`<mark>`:** `{"pos": [x, y, z], "face": <direction name>, "role": "take" | "give" | "feed" | "tip" | "collect", "filter"?: <id>}`.
- **`<carry>`:** `{"item": <item stack, count 1–64>}` or `{"aspect": <id>, "amount": 1–1,000}`.
- **Invariants,** checked against format bounds only, **never against current materials or tuning:** 0–16 marks; distinct (pos, face); coordinates within ±30,000,000 horizontally and the level's build height; `cursor` < the mark count (−1 with none); ≤ 16 backoffs on distinct marks that exist; `faults` exactly one per mark; `owner_name` ≤ 16 characters; ids ≤ 256 characters. A carry over the material's cap, a mark out of tuning range, or a role invalid for the job is **readable** and handled at run time (kept and delivered, faulted, or skipped).
- **Defaults** for a fresh golem: no marks, generation 0, no carry, cursor −1, no backoffs.
- **Missing ids:** a material, core item or job the registries don't hold makes the golem [inert](conventions.md#missing-ids-are-inert-not-unreadable): no thinks, status `INERT`. A filter naming a missing id faults its mark `UNKNOWN_FILTER`. A carried aspect the registry doesn't hold is kept and can be delivered nowhere.
- **Unreadable records** follow [conventions](conventions.md#raw-record-retention); a failed invariant is unreadable too. The golem does nothing and is invulnerable (as above), its status `UNREADABLE`; dying (to `/kill`) drops nothing it can't read.

**`vftv:golem_state`:** `{"format": 1, "marks": [<mark>, …], "carry"?: <carry>, "health": 0 < h ≤ 100}`, under the same bounds and invariants; unreadable items as under "The golem item".

**Entity data** (synced to trackers): the material id and core item id (≤ 256 characters); the status and its number (bytes); the target's mark index (−1–15) and role; the mark count; the carry display (a copy of one item with the exact count, or an aspect id and an amount); the owner's name (≤ 16 characters).

- **Disk:** every persistent change (an act, an edit, a backoff, a fault) marks the entity's chunk for saving, as vanilla's entity storage does.
- **Bounded codecs:** both records as above; `GolemMarks` (≤ 16 marks, ids ≤ 256 characters) and `GolemMarksClear`; `GolemRules` a fixed record; the tuning file.

**Reloads:**

| Change | Effect |
|---|---|
| Tuning (`/reload`) | a new record; resume points cleared; `think.interval` applies at each golem's next schedule; `range` at its next eligibility check; `path.max_length` at its next search; backoffs already set keep their end ticks |
| Tags and recipes (`/reload`) | nothing of ours: the recipe types' own rules apply |
| Server config (`golems.max_per_chunk`) | the cap applies at the next placement only: no golem is removed and no schedule reset |
| The material, item or aspect registries | need the world reopened |
| Resource reload | models, sprites, the card's layout and names rebuild |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/golems.json`**, loaded on reload into an immutable record in `core.golem` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client receives the whole record in `GolemRules`.

| Key | Value | Range | Notes |
|---|---|---|---|
| `think.interval` | 10 ticks | 1–200 | between a golem's thinks |
| `range` | 20 | 4–48 | blocks across, from home, before the material's bonus |
| `range_vertical` | 8 | 2–24 | blocks up or down |
| `path.max_length` | 32 | 8–64 | a search's length `L` = max(this, 16), passed explicitly; visited nodes = 16 × `L` |
| `path.backoff` | 200 ticks | 20–6,000 | after `UNREACHABLE` or `UNLOADED` |
| `refuse.backoff` | 100 ticks | 20–6,000 | after `REFUSED` |
| `travel.max_ticks` | 400 ticks | 40–2,400 | before a target is given up |
| `interact.reach_tenths` | 25 (2.5 blocks) | 15–40 | arrival distance to the block's centre |
| `collect.radius` | 1 | 0–2 | blocks around a COLLECT mark |
| `regen.quiet` | 100 ticks | 20–1,200 | unhurt before regenerating |

**Other homes:**
- material stats: the `vftv:golem_material` registry (`content/golems/materials.json`);
- costs and gates: each recipe's data (`content/golems/recipes.json`);
- **`golems.max_per_chunk`** (16, range 1–256, an out-of-range value clamped): server config, an admin performance cap ([configuration spec, Server keys](config.md#server-keys)), applied at placement only.

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`GolemLimits`** in `api`:

| Constant | Value |
|---|---|
| `MAX_MARKS` | 16 |
| `MAX_THINKS_PER_TICK`, `MAX_THINK_POPS_PER_TICK` | 32, 64 per level |
| `MAX_THINK_WORK` | 1,024 units per think (32,768 per level per tick) |
| `MAX_PATH_SEARCHES_PER_TICK`, `MAX_SEARCH_POPS_PER_TICK` | 2, 32 per level (the search cap derived from measurement, "Pathing") |
| `MAX_VISITED_NODES` | 1,024 (16 × the maximum `path.max_length`) |
| `MAX_HANDLER_SLOTS` | 256 |
| `MAX_COLLECT_ENTITIES` | 16 |
| `MAX_CARRY_ITEMS`, `MAX_CARRY_ESSENTIA` | 64, 1,000 |
| `MAX_ID_CHARS`, `MAX_OWNER_NAME` | 256, 16 |

**Also fixed in code:** the phase function and its salt; the selection order; the eligibility order; the work unit; the role cycle; the travel checks and the 20-tick finishing steer; the four act exits; the drop offset (0.5, 1.0, 0.5) and velocity 0; the 100-tick card cursor; the 10-tick subscription check. **Fixed feedback constants:** the model geometry and its motion; the 24-block glyph range; the 2-block step cadence; the 20-tick work-sound cooldown; playback volumes as tabled.

## Performance

**Heavy-base workload:** 32 golems with 2 owners online, every one kept busy for the whole measurement:
- **12 carrying** between chests 12 blocks apart, each with 4 TAKE and 4 GIVE marks; **the fixture refills sources and empties destinations** throughout;
- **8 decanting** from a boiling crucible's side faces into jars; the fixture keeps the pool fed and its water filled, and empties the jars;
- **4 at a crucible** with FEED and COLLECT marks; the fixture restocks catalysts and clears results;
- **4 loading pedestals**; the fixture empties the pedestals after each load;
- **4 idle** at home;
- **a piston clock** beside two paths, raising recompute requests every 4 ticks.

**Share:** server ≤ 0.25 ms mean, ≤ 0.8 ms p99, measured in the combined stress world, including the full vanilla mob ticks, handler scans and callbacks, queues, events and sync. Client 0 with no golem in view; ≤ 0.1 ms p50 with 32 golems in view and a 16-mark overlay, including extraction and submission. Shares are acceptance targets, not measurements.

**The envelope's running totals** (planning arithmetic, never a combined percentile): the finished specs' server shares (aspects 0.02/0.10, research 0.03/0.15, wands 0.04/0.20, arcane 0.01/0.05, crucible 0.03/0.15, essentia 0.04/0.20, infusion 0.02/0.10, foci 0.03/0.15) total **0.22 ms mean / 1.10 ms p99**; with golems, **0.47 / 1.90**, leaving **0.53 / 1.10** as the collective ceiling for Armor and trinkets, Warp and the Voices, Flux and the Hush, Worldgen and mobs, and the Eldritch tier. Client shares total **0.67 ms p50**, **0.77** with golems, leaving **0.23**.

**Measured separately as load scenarios** (reported): 256 golems gathered at 16 per chunk over 16 chunks (the admission cap bounds placement, not residence); 1,000 item entities in a COLLECT box; all 32 golems repathing after a block flood, **asserting a maximum tick time**, not only that the caps hold; 16 marks on 256-slot handlers whose first 255 slots refuse; a stale-queue flood (256 golems unloaded at once); `path.max_length` 64 after a reload.

**Hot paths:**

| When | Work |
|---|---|
| Per golem per tick | vanilla's mob tick: movement, navigation following, physics. **An O(golems) native term,** disclosed; no push scan |
| Per tick, per level | think queue: ≤ 64 pops, ≤ 32 thinks; search queue: ≤ 32 pops, ≤ 2 searches; ≤ 32,768 work units in all |
| Per think (selection, travel check or act) | ≤ 1,024 units **in all**: eligibility reads (one chunk check and one cached capability read each, repeated per candidate resource, so not bounded by the 16 marks), indexed slot reads, probes and transfers; ≤ 1 root; a travel check: O(1) |
| Per search | ≤ 1,024 visited nodes (512 by default); a region of ≤ 100 chunk columns (≤ 36 by default); no column scan |
| Per COLLECT selection | one entity query over ≤ 5 × 5 × 5 blocks: **a density-dependent native term**, disclosed; ≤ 16 considered |
| Per act | one root, or one spawn and its revalidation, or one entity shrink |
| Packets | entity data at vanilla's cadence; ≤ 1 `GolemMarks` per golem per subscriber per tick; one `GolemRules` per player per join or reload |

- **Capability caches:** each mark holds a `BlockCapabilityCache` for its capability, created with the six-argument factory, whose listener only flags the mark stale and is re-armed by the next read (`NF:capabilities/BlockCapabilityCache.java:38–47,59–67,137–149`), released when the golem leaves the level.
- **Allocation:** **zero** in the phase, range and selection kernels over preallocated arrays (allocation-budget tests). Bounded per act (one journal snapshot, at most one spawn, the event payloads). Zero per frame in our renderer, card and overlay draw code.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **phase and range:** every vector above, including negative coordinates;
- **selection:** the delivery, fairness, budget and eligibility vectors, replayed from `content/golems/fixtures.json`; rotation; slot order; first acceptor in list order; partial delivery; FEED and TIP acceptance; `NO_DESTINATION`;
- **travel:** a target held across several thinks without retargeting; every travel check row; `travel.max_ticks` at its boundary (tick 399 still walking, 400 given up), counting queue wait, and after a reload;
- **exits:** each of `DEFERRED`, `RETIRED`, `REFUSED` and `MOVED`, with its cursor, backoff and target effect; a pre-commit exception is `REFUSED`, an invalidating one `RETIRED`;
- **the work budget:** yield and resumption inside a source scan and **inside destination probes**; revalidation charges on resume; resume cleared by each edit kind; the per-level ceiling;
- **backoff:** a refused first source never starves later ones;
- **cursor:** removal before, at and after the cursor;
- **codecs:** the record, the item state, the attunement, `GolemMarks`, `GolemMarksClear`, `GolemRules` and the tuning file, with every bound and invariant, defaults, and the [raw-retention template](conventions.md#tests), missing ids included; a carry above a lowered material cap stays readable;
- **every `content/` rejection rule** and lint (the `vis` bound, the crucible cost bound, the Echo body's exact result, the bootstrap's coverage, non-amplification).

**GameTest:**
- the registry and tuning load, and each bad entry and file is refused;
- **placing:** a committed placement; each refusal; a cancelled join; **a join listener that empties or swaps the placer's hand, kills the placer or moves them to another level, fills the spawn cell, unloads or borders it off, or adds golems up to the cap** (our entity discarded, nothing consumed); the admission cap, our provisional entity excluded from it and from the collision test;
- **pickup:** the round trip keeps marks, carry and health; refused while busy and **with a full inventory**; a removal callback that fills the inventory meanwhile (the item drops at the player); no duplication;
- **fitting a core:** in both orientations: fitting from a stack of 16 into an empty socket (15 left); a swap from a stack of 1; **a swap from a stack of 16 refused with nothing changed**; removal to an empty cursor and to an empty slot; refusal while carrying; a primary click (vanilla); marks cleared; a locked slot;
- **the Baton:** every row of both tables, including a chest marked rather than opened; **with a torch in the off hand, plain clicks add a mark and cycle its role through every role to removal**, and a sneak-click on a mark sets the filter; a sneak-click on a mark with an empty off hand clears the filter and never removes the mark; a sneak-click elsewhere sets home whatever the off hand holds; a cancelled `RightClickBlock`, **a denied item use, and a denied block use alone**; **a block-use denial set by a second `LOWEST` listener registered after ours** (refused); **a denied click then an allowed click at the same position in one tick** (the second marks); a re-entrant click from inside a listener (fail closed); a cancelled `EntityInteract`; a non-owner; **a copied attuned Baton used by another player**; an unloaded golem; another level; an invalid essentia filter (no change); unreadable, inert and busy golems;
- **carrying:** chest to chest with exact counts; partial delivery across two destinations; a filled jar moved keeps its components; pedestal loading, **promised-stack restoration** by GIVE, and refusal on a claimed pedestal; Singing Bowl loading;
- **FEED and TIP:** FEED crafts as the owner; **a carry of 64 is fed one item per act**, the remainder kept in the carry; FEED waits while the owner is offline or dead, and **keeps feeding while the owner is in another dimension**; **at a boiling crucible, the owner logging out after the drop and before the crucible's step: the catalyst dissolves** (the warned handoff); TIP dissolves a learned catalyst; a cancelled spawn and a listener that changes our entity: no loss, no duplication;
- **COLLECT:** lowest id first; an ineligible low-id entity doesn't block an eligible one; a listener that removes the planned entity after the hook; **a planned stack of 24 reduced to 1 before arrival, and reduced to 1 inside the hook: exactly 1 credited**; a different entity at the planned id (`RETIRED`);
- **decanting:** from a crucible's side, refused on its top; into jars; the essentia cap;
- **transactions:** a nested abort leaves source and ledger exact; a non-`NONE` lifecycle defers; **a source whose `onRootCommit` throws after a successful extraction keeps the move and retries nothing**;
- **barriers:** a handler whose `extract`, `insert`, `size()` or probe moves the golem out of the sphere, replaces the marked block, changes the owner or edits the marks: the open root aborts, nothing moves, `RETIRED`; the same inside a selection installs no plan; **a `range` reload during travel that puts the mark out of range: the arrival's eligibility rerun retires it**; a filter or role made invalid during travel likewise;
- **re-entry:** a `GolemWorkEvent` listener that picks the golem up, edits its marks, kills it, or moves it to another level; the act refuses or retires and nothing duplicates; **a death inside an act settles once at the release, on commit and on abort, posting exactly one `LivingDropsEvent` and, when it is cancelled, dropping nothing**; `GolemWorkEvent` cancel gives `REFUSED` and its backoff;
- **death:** **exactly one `LivingDropsEvent`** on a normal death, then the drops, then the single `SPILLED`, then `DIED`, in that order; `LivingDropsEvent` cancelled; a throwing `LivingDropsEvent`, `SPILLED` or `DIED` listener (the later steps still run); non-owner player and projectile damage with PvP off and on; a wither attacking a golem;
- **pathing:** a block flood raises requests within the search caps (≤ 2 searches per tick); only queue service searches; `path.max_length` 64 after a reload stays within 1,024 nodes, **and a `FOLLOW_RANGE` modifier from another mod changes nothing**; the queue never holds more entries than loaded golems; **marks on the east, top and bottom faces reached and acted on at `interact.reach_tenths` 15** (the finishing steer), and a path that succeeds without arrival is not an act; UNREACHABLE and its backoff; never a teleport; a target retired by a mark edit clears its request;
- **persistence:** **a chunk unloaded and reloaded mid-carry**; save, quit and reload mid-carry and mid-travel; a golem walking toward a non-ticking chunk; never despawns (peaceful, far away, empty server); a lowered material capacity keeps the carry; unreadable and inert records survive save unchanged;
- **bounds:** no chunk is loaded anywhere; the queues hold their caps under 256 golems; a stale-queue flood;
- every test id in the discovery guard.

**E2E:**
- the golem card on every page and status, at 320 × 240 with a long translation and a long item name (wrapped, nothing cut; the [localisation fixtures](accessibility.md#fixtures), outer pose scale ≥ 0.75), unheard and missing aspects;
- a page turn's narration attempted once with the new page, and none for a passive status update (the localisation capture); a refusal on the status line;
- **the overlay:** subscribing on equip and on attune, updates on a mark edit, a fault and a target change, and clearing on a hand switch, a level change, unload and logout; a second client sees no overlay and the status glyph;
- tooltips; every sound and subtitle; the five bodies, carrying and decanting, seen by a second client;
- the Stage 2 flow: learn `vftv:golems`, craft a terracotta golem and a carrying core, mark a chest and a crucible, and watch a Resonant Brass ingot made by FEED, one catalyst per act; then learn `vftv:golem_decanting` and decant into jars (a state dump at each step);
- sync on join, reconnect and `/reload`;
- the asset, lang and model validators: every key under "Lang keys", every sound and subtitle, every item definition, the golem item's five cases and its `unknown` fallback, and the entity textures.

## Contracts on sibling specs

| Needs from | What | Met at |
|---|---|---|
| arcane | the `vftv:arcane` body, with a static result template carrying components | [arcane, The recipe type](arcane.md#the-recipe-type) |
| crucible | the `vftv:crucible` body in this spec's own content file; the provisional-spawn pattern | [crucible, The recipe type](crucible.md#the-recipe-type), [Committing a craft](crucible.md#committing-a-craft) |
| crucible | the thrower resolved server-wide, online and alive, so a FEED owner in another dimension still crafts; a TIP drop, with no thrower, always dissolves | [crucible, Selection](crucible.md#selection), [Multiplayer and automation](crucible.md#multiplayer-and-automation) |
| crucible, API | `SpillCause.DIED`, posted only by `GolemEvent(SPILLED)` | [crucible, Contracts on sibling specs](crucible.md#contracts-on-sibling-specs), [API, The events](api.md#the-events) |
| essentia | the essentia capability, its transactions and snapshot journal, and labels naming an aspect | [essentia, Contents and the handler](essentia.md#contents-and-the-handler), [Transactions](essentia.md#transactions), [Labels](essentia.md#labels) |
| infusion | the `vftv:infusion` body with `transmute`; pedestals and bowls enforcing claims and restoring promised stacks through their item handler | [infusion, The recipe type](infusion.md#the-recipe-type), [Holding items](infusion.md#holding-items-the-item-handler-and-the-container) |
| foci | the inventory-click attach pattern | [foci, Attaching and detaching](foci.md#attaching-and-detaching) |
| research | the three entries under "Stage", with their parents and recipe pages | [research, Roster](research.md#roster) (the recipe pages are not stated) |
| research | the `golem` and `golem_material` fact sources | [research, Prose and fact references](research.md#prose-and-fact-references) |
| research | `path-stage2.json` learns `vftv:golems` after `vftv:resonant_brass` and `vftv:golem_decanting` after `vftv:essentia_jars`; `path-stage3.json` learns `vftv:echo_golems` after `vftv:infusion` | **not stated** in [research, Affordability and recovery](research.md#affordability-and-recovery) or its production tickets |
| research (#35, #36) | prose teaching the Baton's gestures, home and range, roles and filters, that fitting a core clears the marks, FEED's owner rule **with the complete handoff warning**, one item per FEED or TIP act, TIP as the dissolve path, the v1 farm pattern, no suction routing, refusal backoff and every row under "Carry conservation", every number in a `golem`, `golem_material` or `recipe` span | [research, Roster](research.md#roster) (the handoff warning and the loss rows; the rest by reference to this table) |
| Hush | `essentiaFlux(spilled, DIED)` generated once on `GolemEvent(SPILLED)`, at the event's level and death position | [Hush, Sources](hush.md#sources) |
| compatibility | `GolemWorkEvent` as the claim-mod hook | [compatibility, Claim mods](compat.md#claim-mods) |
| configuration | `golems.max_per_chunk` (16, 1–256), applied at placement only | [configuration, Server keys](config.md#server-keys) |
| API | `GolemLimits`, the payload records, the registry and component keys in `api`; `GolemJob`, `GolemCarryKind`, `GolemRole`, `GolemCore` and `GolemKeys` public; the planner and execution internal | [API, Golem jobs](api.md#golem-jobs), [The inventory](api.md#the-inventory) |
| [vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) | hand entries or exemptions for the golem entity and item, the three cores and the Baton; round-trip non-amplification over every golem recipe | not yet stated in any spec |

## Production tickets

This spec settles every decision. Two required child tickets deliver the assets and the authored content, and **the system is not done until both land**. **Neither writes mod code:** no entity, model layer, renderer, HUD layer, item, payload, registry, planner, scheduler, platform adapter, item-model property, datagen provider or validator. The asset ticket delivers a generator and an editable model (authorised tooling under `tools/art/`) and assets under the [art style](art-style.md); the content ticket delivers authored inputs under `content/`, in the schemas above. The tags, attributes, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Golem assets** | **Generator:** `tools/art/golems.py`.<br>**The model:** `tools/art/golems/golem.bbmodel`, an editable Blockbench model with exactly the parts, pivots, cuboids, UV origins and mirroring under "The model", and `tools/art/golems/golem_uv.png`, a 64 × 32 UV guide labelling every face of every net.<br>**Entity textures,** 64 × 32 on that layout: `assets/vftv/textures/entity/golem/wood.png`, `terracotta.png`, `stone.png`, `brass.png` (Stage 2 row of [art style §5](art-style.md#5-tone-ramp-by-stage), the teal pip on the core's region) and `echo.png` (Stage 3 row). The bulb's region is glass at partial alpha with a neutral inner fill for tinting.<br>**Items** (16 × 16, [art style §4](art-style.md#4-silhouette-language)): `assets/vftv/textures/item/golem/wood.png`, `terracotta.png`, `stone.png`, `brass.png`, `echo.png` and **`unknown.png`**, with item models `vftv:item/golem/<id>` (parent `minecraft:item/generated`) for all six; `assets/vftv/items/golem.json` exactly as under "Items"; `blank_core.png`, `carrying_core.png`, `decanting_core.png` (small brass bell-discs) and `baton.png` (an upright conductor's baton, brass ferrule, the teal tick at its tip), with their item models and plain item definitions `assets/vftv/items/blank_core.json`, `carrying_core.json`, `decanting_core.json`, `baton.json`.<br>**World and HUD sprites** under `assets/vftv/textures/gui/sprites/golem/`: role sprites 16 × 16 `take.png`, `give.png`, `feed.png`, `tip.png`, `collect.png`; `home.png` 16 × 16; `fault.png` 9 × 9; status glyphs 9 × 9, one per status: `working.png`, `returning.png`, `idle.png`, `thinking.png`, `waiting_path.png`, `no_destination.png`, `owner_away.png`, `unreachable.png`, `home_unreachable.png`, `inert.png`, `unreadable.png`. The card's frame reuses `vftv:hud/vis_frame`.<br>**Sounds:** every file and `sounds.json` entry under "Sound", with its triggers, category, volume and variant counts.<br>**Review:**<br>• a contact sheet at GUI scale 2 beside vanilla's armor stand, spyglass and copper golem spawn egg, with every item and the `unknown` icon;<br>• Blender renders of the model in each texture: standing, walking, carrying an item, and with a tinted bulb;<br>• **worst-case card mockups** composed from the reused frame and the vanilla font: each page, every status, a long test translation and a 30-character item name wrapped, unheard and missing aspects, on a 320 × 240 scaled screen;<br>• an **overlay mockup:** 16 marks with every role, faults and the target line, and the home ring.<br>Provenance rows. | none |
| **Golem content** | `content/golems/materials.json` (`vftv:golem_materials/1`, exactly the shipped values); `content/golems/recipes.json` (`vftv:golem_recipes/1`: the six arcane recipes (four bodies, the Baton, the blank core), the two crucible cores and the Echo infusion, within "Authored content, Recipes"); `content/golems/bootstrap.json` (`vftv:golem_bootstrap/1`, every `why` reviewed and the sequence non-circular); `content/golems/fixtures.json` (`vftv:golem_fixtures/1`, every vector table transcribed); and `content/lang/en_us/golems.json` (`vftv:lang/1`) with every key under "Lang keys". Its review checks that the messages teach what that section lists. | [Vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (the non-amplification lint replays its values) |

[Stage 2 research content #35](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) and [Stage 3 research content #36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) consume the entry ids, recipe ids and the `golem` and `golem_material` fact keys frozen here.
