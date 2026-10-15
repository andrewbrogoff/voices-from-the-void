# Foci

What a wand casts. A **focus** is set on a wand and works one **effect**, delivered by one **medium** and changed by **modifiers** up to its **rank**. Foci are composed for free at the **Lathe**, their ranks are raised there for experience and vis, and Void-tier effects grow stronger with warp. Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). The code layout, data rules and sync rules come from the [architecture spec](architecture.md), every gate from the [quality gates](quality-gates.md), the vis economy and the one spend from the [wands spec](wands.md), research gates and the book from the [research spec](research.md), the Worktable recipe type from the [arcane crafting spec](arcane.md), and every asset from the [art style](art-style.md).

Resolved in [Foci spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/22). Inputs: [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md), [TC4 alchemy and infusion](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-alchemy/docs/research/tc4-alchemy.md) (§4, wand foci and the upgrade station), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. Each tunable one is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are the shipped values where the text says so; otherwise the content ticket sets them. Source citations: `MC:` is the `minecraft-patched-26.2.0.88` sources (with NeoForge's patches to vanilla classes) and `NF:` the NeoForge `26.2.0.88` sources, each path relative to `net/minecraft/` or `net/neoforged/neoforge/`.

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 1 Resonance | the focus, the Lathe, the effects Kindle and Gust, the media Bolt and Touch | the Stage is opened by the amethyst geode ([research, Stage](research.md#stage)). Inside it, `vftv:foci` follows `vftv:arcane_crafting`, so **the first arcane craft is the step before the first focus** |
| 2 Distillation | ranks 1–2, the modifiers Force, Reach and Breadth; the effects Delve and Rime, the medium Burst | the opener `vftv:distillation` ([Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s find) |
| 3 Ruins | rank 3; the effects Exchange and Mend, the medium Beam | the opener `vftv:ruins` (#27's find) |
| 4 The Choir's Road | the two Void-tier effects, Unmaking and Undertow | the opener `vftv:choirs_road` ([Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)'s find) |

Five `note` entries, authored by the research content tickets:

| Entry | Stage | Parents | Gates |
|---|---|---|---|
| **`vftv:foci`** | 1 | `vftv:arcane_crafting` | the arcane recipes `vftv:lathe` and `vftv:blank_focus`; effects `vftv:kindle`, `vftv:gust`; media `vftv:bolt`, `vftv:touch` |
| **`vftv:focus_ranks`** | 2 | `vftv:foci`, `vftv:resonant_brass` | ranks 1 and 2; modifiers `vftv:force`, `vftv:reach`, `vftv:breadth` |
| **`vftv:delving`** | 2 | `vftv:foci`, `vftv:distillation` | effects `vftv:delve`, `vftv:rime`; medium `vftv:burst` |
| **`vftv:far_workings`** | 3 | `vftv:focus_ranks`, `vftv:ruins` | effects `vftv:exchange`, `vftv:mend`; medium `vftv:beam`; rank 3 |
| **`vftv:void_foci`** | 4 | `vftv:far_workings`, `vftv:choirs_road` | effects `vftv:unmaking`, `vftv:undertow` |

- **Gate actions, not crafts** ([wands, Stage](wands.md#stage)). The two arcane recipes carry `research` ([arcane crafting, The recipe type](arcane.md#the-recipe-type)). Every other gate is checked **on the server at the action**: a compose checks the effect, the medium and each modifier; a rank-up checks that rank's entry; a cast checks all of a composition's parts, at its start and at every Beam pulse.
- **Obtaining counts.** A focus or a Lathe can be found, carried or handed over. A focus attaches freely and casts only for a holder who has learned all of its parts.
- **Options considered:** a Stage 1 find of its own (striking a blaze, or a second geode visit); everything from Stage 2. **Why notes after `vftv:arcane_crafting`:** the progression puts the first magic in the first session ([progression, The first hour](progression.md#the-first-hour)), and another find would add a chore with no decision behind it. Each later Stage's content hangs off that Stage's own opener, so Stages stay find-opened ([progression, Stages](progression.md#stages)).

## Composition

**Types in code, instances in data** ([architecture, Data and code](architecture.md#data-and-code)). Each of the three parts has a **code registry of behaviour types** and a **synced datapack registry of instances**, fixed when a world opens, like the wand parts ([wands, Parts](wands.md#parts); [ADR 0003](../adr/0003-aspects-as-datapack-registry.md)). **In v1 the behaviour types are internal** ([API spec, Code extension](api.md#code-extension)): an addon or a pack retunes or adds entries over the built-in types, in data only, and the type ids with their `params` are a public data format. A later API minor may open the type registries as an `@Experimental` SPI.

| Code registry (types) | Built-in types | Datapack registry (instances) |
|---|---|---|
| `vftv:focus_effect_type` | `kindle`, `gust`, `rime`, `mend`, `delve`, `exchange`, `unmaking`, `undertow` | **`vftv:focus_effect`**: `data/<ns>/vftv/focus_effect/<path>.json` |
| `vftv:focus_medium_type` | `bolt`, `touch`, `burst`, `beam` | **`vftv:focus_medium`**: `data/<ns>/vftv/focus_medium/<path>.json` |
| `vftv:focus_modifier_type` | `force`, `reach`, `breadth` | **`vftv:focus_modifier`**: `data/<ns>/vftv/focus_modifier/<path>.json` |

Every type declares its `params` codec in code. **Effect types** also declare their **target domain** (`ENTITIES` or `BLOCKS`), whether they have a **magnitude**, and whether they are **harmful** (see "Targets"). A medium has no domain of its own: the effect's domain decides what its aim can touch ("Entry points").

### Effects

| Field | Required | Range | Meaning |
|---|---|---|---|
| `type` | yes | a `vftv:focus_effect_type` id | the behaviour |
| `kind` | yes | `ordinary` \| `void` | the quote context's `kind` ([wands, The context](wands.md#the-context)) and whether warp amplifies it |
| `research` | yes | a `vftv:research` id | the entry that must be learned to compose or cast it |
| `aspect` | yes | a registry aspect id | its voice: the tint of its bead, bolt and particles, and its glyph on the Lathe |
| `media` | yes | 1–16 distinct `vftv:focus_medium` ids | the media it may be composed with |
| `cost` | yes | 1–8 entries; each key a **vis aspect** ([wands, Vis storage](wands.md#vis-storage)), each value 1–1000 | the per-cast base cost before the medium and modifiers |
| `params` | yes | the type's own object, below | its numbers |

**Params by type.** Every field is required; an unknown field is an error. Magnitudes are integers: health and movement in **thousandths** (3000 = 3.0 health, 1200 = 1.2 blocks per tick), durations in ticks.

| Type | Domain | Magnitude | Harmful | Params (range) |
|---|---|---|---|---|
| `kindle` | entities | yes | yes | `damage` 0–20,000; `damage_type` a damage type id; `ignite_ticks` 0–1200 |
| `gust` | entities | yes | yes | `push` 0–3000 |
| `rime` | entities | yes | yes | `damage` 0–20,000; `damage_type`; `slow_ticks` 0–1200; `slow_amplifier` 0–4 |
| `mend` | entities | yes | no | `heal` 0–20,000 |
| `unmaking` | entities | yes | yes | `damage` 0–20,000; `damage_type` |
| `undertow` | entities | yes | yes | `pull` 0–3000 |
| `delve` | blocks | no | no | `tool` an item id, not air; `max_hardness` a finite number 0.0–50.0 |
| `exchange` | blocks | no | no | `tool`; `max_hardness` |

**Scaled quantities** (see "Magnitude"): `damage`, `heal`, `push`, `pull`, `ignite_ticks` and `slow_ticks`. Never scaled: `slow_amplifier`, `tool`, `max_hardness`.

### Media

| Field | Required | Range | Meaning |
|---|---|---|---|
| `type` | yes | a `vftv:focus_medium_type` id | |
| `research` | yes | a `vftv:research` id | |
| `cost_pm` | yes | 100–4000 | the medium's cost factor, per mille |
| `cooldown` | yes | 0–200 ticks | set after a cast, or after a Beam stops |
| `params` | yes | by type, below | |

| Type | Params (range) |
|---|---|
| `bolt` | `speed` 0.5–3.0 blocks per tick; `lifetime` 1–100 ticks |
| `touch` | `range` 1.0–16.0 blocks |
| `burst` | `radius` 1.0–6.0 blocks; `block_radius_sq` 0–16 |
| `beam` | `range` 1.0–16.0 blocks; `pulse` 5–40 ticks |

Every decimal is a finite `double` with at most 2 decimal places.

### Modifiers

| Field | Required | Range | Meaning |
|---|---|---|---|
| `type` | yes | a `vftv:focus_modifier_type` id | |
| `research` | yes | a `vftv:research` id | |
| `cost_pm` | yes | 1000–4000 | applied once per level |
| `max_level` | yes | 1–3 | |
| `media` | no | 1–16 distinct medium ids | if present, the only media it may be composed with |
| `params` | yes | by type, below | |

| Type | Applies to | Params (range) |
|---|---|---|
| `force` | effects whose type has a magnitude | `step_pm` 0–1000: magnitude per mille added per level. **The only authority for Force's step.** |
| `reach` | `ENTITIES` effects, with media of type `touch` or `beam` (the entity ray) | `step` 0.0–4.0 blocks per level |
| `breadth` | media of type `burst` | `radius_step` 0.0–2.0 blocks; `block_step_sq` 0–8, per level |

### Load validation

Runs when the world opens, on the full registries, decoded through registry ops so every id resolves. It **refuses the world** with a clear error naming the entry:
- an unknown type, research entry, aspect, item, damage type or medium id; a `cost` key that isn't a vis aspect; a value out of range or not finite; a missing or unknown field;
- an effect whose `media` names a medium whose type can't carry its domain: a **`BLOCKS` effect with a `bolt`-type medium** (see "Validity");
- more than **64** effects, **16** media or **32** modifiers (fixed limits);
- a required built-in key missing.

**Required built-in keys** (`FocusKeys` in `api`): the 8 effects, 4 media and 3 modifiers under "The roster". Packs may override their values and add entries; they cannot delete these. Names are lang keys `focus_effect.<ns>.<path>`, `focus_medium.<ns>.<path>` and `focus_modifier.<ns>.<path>`.

### Validity

**One pure function, `FocusRules.valid(composition, rank, registries)`,** in `core.focus.model`. A composition is an effect E, a medium M and a map of modifier → level. It is valid iff:
1. E and M exist, and M ∈ E.`media`;
2. at most 3 modifier entries, each level 1 to its `max_level`, and the sum of levels ≤ the focus's **rank**;
3. **at most one modifier instance per behaviour type**: a pack's second Force entry is an alternative choice, never stacked with the first, so Force, Reach and Breadth each have one level and one step in any composition;
4. each modifier's `media`, if present, contains M;
5. each modifier applies: `force` only if E's type has a magnitude (so **never with Delve or Exchange**); `reach` only if E is an `ENTITIES` effect and M's type is `touch` or `beam` (Reach never extends block work, so it is never sold to it); `breadth` only if M's type is `burst`;
6. a `BLOCKS` effect never has a `bolt`-type medium. Load validation already refuses that pair, so a bolt can never change the world at a place and time its caster didn't choose.

The Lathe offers only valid choices. The server runs the function on every compose and at every cast and pulse. A focus whose composition stops being valid after a pack change is **out of tune**: inert until it is recomposed (see "Sync and state").

### The roster

The built-ins: 8 effects, 4 media, 3 modifiers. Depth comes from combining them, not from how many there are (community pain points, loved feature §8). These are the shipped values.

| Effect | Kind | Aspect | Cost | Media | Params | Research |
|---|---|---|---|---|---|---|
| `vftv:kindle` | ordinary | Ignis | Ignis 4 | bolt, touch, burst, beam | damage 3000, `vftv:kindle`, ignite 60 | `vftv:foci` |
| `vftv:gust` | ordinary | Aer | Aer 3 | bolt, touch, burst | push 1200 | `vftv:foci` |
| `vftv:rime` | ordinary | Gelum | Aqua 4, Ordo 2 | bolt, touch, burst, beam | damage 2000, `vftv:focus`, slow 100 ticks at amplifier 0 | `vftv:delving` |
| `vftv:delve` | ordinary | Terra | Terra 3, Perditio 2 | touch, burst, beam | tool `minecraft:iron_pickaxe`, max hardness 3.0 | `vftv:delving` |
| `vftv:mend` | ordinary | Victus | Aqua 4, Terra 4 | touch, burst, beam | heal 3000 | `vftv:far_workings` |
| `vftv:exchange` | ordinary | Permutatio | Ordo 3, Perditio 3, Terra 2 | touch, burst | tool `minecraft:iron_pickaxe`, max hardness 3.0 | `vftv:far_workings` |
| `vftv:unmaking` | void | Vacuos | Perditio 6, Aer 3 | bolt, touch, beam | damage 4000, `vftv:unmaking` | `vftv:void_foci` |
| `vftv:undertow` | void | Alienis | Aer 4, Perditio 4 | touch, burst | pull 1000 | `vftv:void_foci` |

| Medium | `cost_pm` | Cooldown | Params | Research |
|---|---|---|---|---|
| `vftv:bolt` | 1000 | 10 | speed 1.5, lifetime 40 | `vftv:foci` |
| `vftv:touch` | 700 | 8 | range 4.0 | `vftv:foci` |
| `vftv:burst` | 2500 | 30 | radius 3.0, `block_radius_sq` 1 | `vftv:delving` |
| `vftv:beam` | 400 (per pulse) | 10 | range 12.0, pulse 10 | `vftv:far_workings` |

| Modifier | `cost_pm` | Max level | Params | Research |
|---|---|---|---|---|
| `vftv:force` | 1300 | 3 | `step_pm` 250 | `vftv:focus_ranks` |
| `vftv:reach` | 1150 | 2 | `step` 2.0 | `vftv:focus_ranks` |
| `vftv:breadth` | 1500 | 2 | `radius_step` 1.0, `block_step_sq` 3 | `vftv:focus_ranks` |

- **Names** follow the [lore spec's](lore.md#vocabulary-policy) policy: plain English, none of them TC4's (Kindle rather than Fire, Rime rather than Frost, Delve rather than Excavation, Exchange rather than Equal Trade, Force rather than Potency, Breadth rather than Enlarge). The two Void effects are named in Void-tongue by the [Void-tongue lexicon](warp.md#production-tickets) ticket, through their lang keys; their ids stay English.
- **Cut from v1:** a temporary passage through walls (it needs a persistent restore queue across chunk unloads and its own suffocation rules) and a ward that makes blocks unbreakable (it needs a per-block owner store and break overrides). Each is a separate system; either is a later proposal. Packs and addons add effects through the registries.
- **Options considered:** fixed foci, one item per spell (TC4); a free-form glyph language. **Why parts on a simple screen:** the progression asks for composable foci on a simple GUI ([progression, TC4 systems](progression.md#tc4-systems-kept-redesigned-cut)), and the pain-points research found that a programmer-style GUI was resented while composition was loved.

## The focus

### The item

**`vftv:focus`**, stacking to 1. Its one component, **`vftv:focus`**, is persistent and network-synced:

```json
{"format": 1, "rank": 2, "effect": "vftv:rime", "medium": "vftv:burst",
 "modifiers": {"vftv:breadth": 1, "vftv:force": 1}}
```

| Field | Range | Meaning |
|---|---|---|
| `format` | 1 | |
| `rank` | 0–3 | how many modifier levels it can hold |
| `effect`, `medium` | ids, both present or both absent | absent: a **blank focus** |
| `modifiers` | 0–3 entries, id → 1–3 | |

- **A blank focus** has rank 0 and no composition when crafted. It can be ranked but not cast. A focus with no component at all is read as a blank of rank 0.
- **Decoding** checks the format bounds, never the current registries or tuning. An unknown format or an out-of-bounds value is kept as its **raw tag** and written back unchanged, with one log line ([wands, Sync and state](wands.md#sync-and-state)): the focus is **unreadable**, casts nothing, can't be composed or ranked, and its tooltip says so. Ids that name no registry entry decode and are kept; such a focus is **inert** until the pack returns or it is recomposed.
- **The kind of a focus** is its effect's `kind`: `VOID` iff the effect is `void`. A blank, unreadable or inert focus has no kind. `FocusService.kind(stack)` answers for a focus item or a wand holding one.

### Attaching and detaching

**The bundle mechanic, with no payload.** `vftv:wand` overrides `Item#overrideOtherStackedOnMe` and `Item#overrideStackedOnOther` (`MC:world/item/Item.java:255,259`). Vanilla calls them from container clicks on both sides; the server's result is authoritative and vanilla resyncs the client.

| Click | Cursor | Slot | Result |
|---|---|---|---|
| right | a `vftv:focus` | a wand without a focus | attaches: the cursor empties |
| right | a `vftv:focus` | a wand with a focus | **swaps**: the old focus goes to the cursor |
| right | empty | a wand with a focus | detaches it to the cursor |
| right | empty | a wand without a focus | vanilla (no override) |
| left, or any other cursor | | | vanilla (no override) |

- **Only mutable slots.** Like vanilla's bundle (`MC:world/item/BundleItem.java:97–125`), every row requires `slot.allowModification(player)`. A crafting result, the Worktable's output or a locked slot refuses, so no slot that shows a would-be item can yield a free focus.
- **One step.** The cursor and the wand's component change in the same callback, with nothing between: the wand gets its new `vftv:wand_focus` in place, and the carried stack is set through the given `SlotAccess`. An unreadable `vftv:wand_focus` never detaches.
- **Cooldowns follow the wand item**, not the focus (see "The cast"), so a swap never skips a cooldown.
- **Options considered:** a swap key with a radial menu and a focus pouch (TC4); a crafting-grid recipe. **Why the click:** no custom packet, it works in every inventory screen, and a second wand covers quick swaps in a fight. A key and pouch would be their own later proposal.

### On the wand

**`vftv:wand_focus`**, on `vftv:wand` ([wands, Components](wands.md#components)), persistent and network-synced: `{"format": 1, "focus": <ItemStack>}`. It holds **the attached focus stack itself**, through vanilla's `ItemStack` codec, so nothing on it (a name, another mod's data) is lost. The stack must be a `vftv:focus` of count 1. Otherwise, or with an unknown format, the value is kept raw and written back unchanged, and the wand casts nothing and can't detach.

**The model.** One custom select property, **`vftv:focus_effect`**, registered through NeoForge's `RegisterSelectItemModelPropertyEvent` (`MC:client/renderer/item/properties/select/SelectItemModelProperties.java:26`). It is needed because vanilla's `minecraft:component` property can't reach a field inside a record. It reads either the focus's own component (for `vftv:focus`) or the attached focus (for `vftv:wand`):

| The stack | Value |
|---|---|
| a wand with no `vftv:wand_focus` | `vftv:none` |
| a blank focus, or a wand holding one | `vftv:blank` |
| a composed focus, or a wand holding one | its effect id |
| anything unreadable | `vftv:unreadable` |

`assets/vftv/items/wand.json` gains a **third composite member**, after the rod and cap selects:

```json
{"type": "minecraft:select", "property": "vftv:focus_effect",
 "cases": [
   {"when": "vftv:none",   "model": {"type": "minecraft:empty"}},
   {"when": "vftv:blank",  "model": {"type": "minecraft:model", "model": "vftv:item/wand/focus/blank"}},
   {"when": "vftv:kindle", "model": {"type": "minecraft:model", "model": "vftv:item/wand/focus/kindle"}}],
 "fallback": {"type": "minecraft:model", "model": "vftv:item/wand/focus/unknown"}}
```

with one case per built-in effect (the example shows one). **`minecraft:empty`** (`MC:client/renderer/item/ItemModels.java:16`) draws nothing for a bare wand. **The fallback is `unknown`**, so an unreadable value, or an effect a pack added without a model, still visibly shows a focus. `assets/vftv/items/focus.json` is the same select with the focus's own models (`vftv:item/focus/<id>`, `blank`, `unknown`). It needs no `vftv:none` case, because a focus always has a value.

- **Additive cases.** An addon or pack that adds an effect appends its case to both selects, keeping every existing case and both fallbacks, as later wand tiers append to the rod and cap selects ([wands, Components](wands.md#components)).
- **Who writes the file, in order:** [Wand assets #38](https://github.com/andrewbrogoff/voices-from-the-void/issues/38) delivers it first; [Infusion assets #47](https://github.com/andrewbrogoff/voices-from-the-void/issues/47) appends its Echo cases; **Foci assets** appends the third member after both. No two of them write the file at the same time (see "Production tickets").

## The Lathe

**A bellfounder's tuning lathe, where a focus is turned to its note.** It is the one place a focus is composed or ranked.

### The block

**`vftv:lathe`**: no block entity; 4 horizontal facings (the front faces the placer); hardness 2.5; `#minecraft:mineable/axe`; drops itself (a loot table); `PushReaction.NORMAL`. It is made at the Worktable from the arcane recipe `vftv:lathe`, gated by `vftv:foci`.

**Use** (`useWithoutItem` and `useItemOn` returning `TRY_WITH_EMPTY_HAND`) opens the menu for the player, after vanilla's spawn protection, `mayInteract` and `RightClickBlock`. A sneaking click with an item never reaches it (vanilla), and a sneaking click with a focused wand is that wand's block-targeted cast (see "Casting").

### The menu

**`LatheMenu`**, menu type **`vftv:lathe`**, transient and per player, following the [Worktable's](arcane.md#the-menu) pattern.

**Containers,** owned by the menu and never by the block:

| Index | Slot | Logical position (top-left of the 16×16 item) | Rules |
|---|---|---|---|
| 0 | focus | (8, 20) | `mayPlace` iff a `vftv:focus`; max 1 |
| 1 | wand | (8, 44) | `mayPlace` iff a `vftv:wand`; max 1. **Never charges** it; it pays for rank-ups. |
| 2–28 | player inventory, 3 rows | (8 + 18*c*, 158 + 18*r*) | vanilla |
| 29–37 | hotbar | (8 + 18*c*, 216) | vanilla |

**Data slots** (vanilla container data, 16 bits each): 0 the last refusal code (see "Status and refusals"), 0 for none; 1 the revision's low 16 bits; 2 its high 16 bits.

- **Shift-click** (`quickMoveStack`): from slot 0 or 1 to 2–37 in forward order. From the inventory or hotbar: a `vftv:focus` to an empty slot 0, a `vftv:wand` to an empty slot 1, and anything else between the inventory (2–28) and the hotbar (29–37).
- **`stillValid`** is vanilla's block-and-reach check against `vftv:lathe` at the menu's position. Breaking the Lathe closes every menu on it.
- **Return on close:** `removed` returns both slots to the player **exactly once**, through vanilla's container clearing: into the inventory while the player is alive and connected, otherwise dropped at the player.
- A wand in slot 1 can have a focus attached or detached by the click rules above. That is a slot change like any other.

### The screen

**`LatheScreen`**, background **288×234** logical pixels, drawn from a **512×256** texture sheet and centred like vanilla container screens. One sprite texel is one logical pixel. At the smallest supported view, 320×240 scaled, the whole background fits: every slot, strip, socket, button and the hotbar stays on screen, and a tooltip too long for the space beside its anchor flips or wraps inside the view, as vanilla tooltips do.

| Element | Size | Position | Notes |
|---|---|---|---|
| title | one line | (8, 6) | `container.vftv.lathe` |
| focus and wand slots | 18-px wells | as under "The menu" | empty wells draw `vftv:container/slot/focus` and the arcane spec's `vftv:container/slot/wand` |
| **effect strip** | 6 choices of 20×20 | (30, 17); choice *i* at (30 + 20*i*, 17) | the learned effects in ascending id, each drawn with its aspect's icon; scrolls by one with the mouse wheel or arrows when more than 6 |
| **medium strip** | 6 choices of 20×20 | (158, 17); choice *i* at (158 + 20*i*, 17) | the learned media, disabled where invalid for the selected effect; scrolls likewise |
| **sockets** | 3 of 20×20 | (30 + 24*s*, 41) | only *rank* sockets are drawn; the rest show the closed sprite |
| rank pips | 3 of 5×5 | (106 + 7*k*, 48) | filled up to the focus's rank |
| **cost panel** | 2 rows × 4 cells of 40×18 | (8, 65), cell *i* at (8 + 40·(*i* mod 4), 65 + 18·⌊*i*/4⌋) | the [shared cost-cell rule](arcane.md#the-screen); see below for what it prices |
| **detail line** | 160×9 | (8, 103) | cooldown, ranges or radius, pulse, magnitude; clipped with "…", complete in its tooltip |
| **Turn** button | 54×18 | (8, 114) | compose |
| **Raise rank** button | 54×18 | (114, 114) | rank up |
| **status line** | 160×9 | (8, 136) | one line, clipped with "…" |
| inventory label | one line | (8, 147) | vanilla `container.inventory` |

**Sprites** (under `assets/vftv/textures/gui/sprites/`): `lathe/choice_idle`, `choice_hover`, `choice_selected`, `choice_disabled` (20×20); `lathe/socket_open`, `socket_closed` (20×20); `lathe/pip_full`, `pip_empty` (5×5); `lathe/button`, `button_hover`, `button_disabled` (54×18); `container/slot/focus` (16×16). The cost cells reuse `vftv:worktable/cost_cell`, `cost_cell_short` and `beyond`.

**The draft.** The screen edits a client-side draft: the effect, the medium and the socket contents. On opening, and whenever slot 0 changes, it is reset to the focus's composition (empty for a blank).
- **Choosing an effect** keeps the medium if it is still valid, else selects the first valid learned medium in ascending id. It then drops any socket whose modifier is no longer valid.
- **A socket** cycles, on each click, through *none* and then every learned modifier that would keep the draft valid, in ascending id. A modifier whose behaviour type another socket already holds through a **different** entry is never offered (one instance per type, "Validity"). Right-click cycles backwards.
- **Socket aggregation:** a modifier's level is the number of sockets holding that entry, so two Force sockets are Force 2. Socket order is display only: sockets are drawn sorted by modifier id, then empties.

**What the cost panel prices** (the composition's per-cast cost, or, while **Raise rank** is hovered or focused, the rank-up's vis cost): one cell per base aspect, in ascending aspect id, from the authoritative preview with the wand in slot 1 (see "The authoritative preview"). The cost tooltip is the arcane spec's: the aspect's effective and base cost, the pool, the factors (cap, gear discount, warp, `m`), each flag that is set, and the spill when above 0. With no wand or an inert wand, the cells show the base cost and the tooltip names why. The **Raise rank** tooltip adds the experience price, `tooltip.vftv.lathe.rank_xp` (levels, the player's levels).

**The detail line** lists, for the draft: the cooldown, the ranges (Touch, Beam: `entity_range` with Reach for an `ENTITIES` effect, or `block_range` for a `BLOCKS` effect, see "The authoritative preview") or the radius (Burst), the pulse (Beam, with "per pulse" on the cost), and the magnitude now, including Force and the live Void amplifier (see "The Void tier"). For Exchange it adds `tooltip.vftv.focus.exchange_warning`.

**Controls:**

| Input | Effect |
|---|---|
| Left-click a choice | select it |
| Left-/right-click a socket | cycle forward / backward |
| Mouse wheel over a strip | scroll it by one |
| Left-click **Turn** | compose the draft |
| Left-click **Raise rank** | rank up |
| Tab / Shift+Tab | cycle focus: effect strip → medium strip → sockets → Turn → Raise rank |
| Arrow keys | move within the focused strip or between sockets; Left/Right scroll a strip past its ends |
| Enter or Space | select the focused choice; cycle the focused socket forward; activate the focused button |
| Esc, inventory key | close (vanilla) |

The buttons act only when the local prediction for that action is ready (see "Status and refusals"). They send **at most one `LatheAction` per client tick**.

**Narration:** a choice reads `narration.vftv.lathe.choice` (name, selected or not, available or why not); a socket reads `narration.vftv.lathe.socket` (index, modifier and level, or empty); a button reads its label, then one `narration.vftv.worktable.cost` per cell of the cost it would pay (or the base cost and the availability text when unpriced), and for Raise rank the experience price; then the full status line.

### The Lathe payload

Composing and ranking happen **only** in the server handler of one C2S payload:

**`LatheAction(containerId, revision, action)`**

| Field | Wire type and bounds |
|---|---|
| `containerId` | VarInt |
| `revision` | `int`, the 32-bit revision the client holds |
| `action` | one byte tag, then: **`COMPOSE`**: `effect` and `medium` (ids ≤ 256 characters each) and `modifiers` (0–3 entries of id → level 1–3, **no duplicate keys**); or **`RANK`**: `echo`, the [arcane spec's quote echo](arcane.md#the-craft-payload) (`base` ≤ 8 and `effective` ≤ 8 entries with the same key set and no duplicates, `m` 0–10,000, `spill` 0–1,000,000, the three flags), and `xp` 0–100 |

Each collection's size is read and checked **before anything is allocated**. Out-of-range, oversized or malformed input is rejected at decode.

**Server checks,** on the server thread, first failure refusing:

| # | Check | On failure |
|---|---|---|
| 1 | **Rate:** at most 1 `LatheAction` per player per server tick | dropped, no reply |
| 2 | not a spectator; the open menu is a `LatheMenu` with this `containerId`; `stillValid` | dropped, no reply |
| 3 | the menu's change check runs; then `revision` equals the menu's revision | `STALE` |
| 4 | slot 0 holds a `vftv:focus` | `NO_FOCUS` |
| 5 | its component is readable | `UNREADABLE` |
| **COMPOSE** | | |
| 6 | every id exists, and the composition is valid at the focus's rank ("Validity") | `INVALID` |
| 7 | the effect, the medium and every modifier are learned | `UNLEARNED` |
| 8 | the composition differs from the focus's | `UNCHANGED` |
| **RANK** | | |
| 6 | rank < 3 | `MAX_RANK` |
| 7 | the next rank's `research` is learned | `RANK_UNLEARNED` |
| 8 | `xp` equals the next rank's levels (`FociRules`), and the player has at least that many levels (skipped under `hasInfiniteMaterials`) | `STALE`; `XP_SHORT` |
| 9 | slot 1 holds a `vftv:wand` that is not inert | `NO_WAND`; `INERT_WAND` |
| 10 | the quote for that wand: not `exceeds_capacity`, and affordable | `EXCEEDS_CAPACITY`; `SHORT` |
| 11 | **the snapshot,** taken before any foreign code runs: the player's level `L₀`, the slot 0 and slot 1 stack objects and a copy of slot 0's stack, the next-rank plan (rank, `xp`, vis, research, from this `FociRules` record) and the menu's revision | |
| 12 | **the experience hook,** only if `xp` > 0 and not infinite materials: post NeoForge's `PlayerXpEvent.LevelChange(player, −xp)` through the platform seam, the same event `Player#giveExperienceLevels` posts (`MC:world/entity/player/Player.java:1626–1629`). Cancelled, or a listener changed its levels to anything but `−xp` | `XP_REFUSED` |
| 13 | **the recheck,** after the hook: check 2 again (the same open menu, `containerId` and `stillValid`); the change check, and the revision still equals the snapshot's; slots 0 and 1 hold the same objects and slot 0 still `ItemStack.matches` its copy; the next-rank plan is unchanged; and the player's level is still `L₀`. Anything the hook's listeners changed refuses | `STALE` |
| 14 | `VisService.spend(...)`, below | its refusal |

**Committing a compose:** inside the menu's suppression scope, slot 0's stack gets its new `vftv:focus` component (the rank kept), and the scope ends with one bump. **Composing is free**: it costs nothing, so experimenting costs nothing. Then `FocusEvent(COMPOSED)` and `vftv:lathe.turn`.

**Committing a rank-up** is one call: `VisService.spend(player, slot 1 stack, rank vis, VisContext("vftv:rank_<r+1>", ORDINARY, rank research), echo, effect, menu boundary)`. The `effect` holds:
- one **slot change**: slot 0, from the focus as it is to the same focus with `rank + 1`;
- one **experience change** `(player, expectedLevel = L₀, after = L₀ − xp)`, the wands spec's typed extension of the prepared spend ([wands, The spend](wands.md#the-spend)), prepared against the **snapshot's** level. It is **omitted** when `xp` is 0 or the player has infinite materials, and so is the hook.

The spend's step 5 rechecks the slot and the level against `L₀`, and step 6 writes vis, the slot and the level together or restores all three, before `VisSpentEvent`. The experience change writes the level directly and posts nothing, because check 12 already resolved the hook. A listener that drains experience during the hook, even to a level that could still pay, fails check 13, and one that drains it later fails step 5: either way the rank-up refuses as stale and never charges a different price. After a committed experience change, the wands spec sends the owner vanilla's experience packet (its current progress, total and level), because a direct level write leaves the total that vanilla's own sync compares unchanged (`MC:server/level/ServerPlayer.java:544–550,748–751`), and posts no second hook. After either committed outcome: `FocusEvent(RANKED)` and `vftv:lathe.rank`. A rank-up is always `ORDINARY`, even on a Void focus: the Lathe is ordinary craft.

- **The hook is asked once, before the commit.** If check 13 or 14 then refuses, a listener has seen a debit that didn't happen, as with any change a later check cancels; the commit never posts it a second time.
- **Atomic, and nothing can be lost.** The full price (experience and vis) is checked before anything changes, and the rank is one commit. TC4's upgrade station took its price without checking it first and lost the upgrade if the focus was removed mid-way; neither can happen here.
- **`COMMITTED_NOTIFY_FAILED`** is a committed rank-up, as in the arcane spec. A spend that throws was rolled back by the wands spec and is reported as `ERROR`.

### Revision and status

**The revision** is the [arcane spec's preview revision](arcane.md#preview-revision), unchanged in mechanism. It is a server-owned 32-bit counter per open menu, starting at 0 and wrapping, sent in data slots 1 and 2. It bumps on a menu slot change (including the wand's vis written by a spend, and an attach or detach), a change to the player's research, and a replaced `FociRules` or `VisRules`. **Price inputs never bump it:** the discount attribute, #25's factor and amplifier inputs, and the player's experience level. The quote echo, check 8 and checks 11–13 catch a stale price; a change to a price input clears the refusal without a bump. The O(1) change check runs in `broadcastChanges`, at check 3, and before every commit. Torn reads of the two halves are refused as `STALE`, never acted on.

**Status and refusals.** One enum, **`LatheStatus`**, in `core.focus`; its code is the wire value of data slot 0.

| Code | Status | Raised by | Lang key | Args |
|---|---|---|---|---|
| 0 | `READY` | client only | `status.vftv.lathe.ready` | 0 |
| 1 | `NO_FOCUS` | client; check 4 | `status.vftv.lathe.no_focus` | 0 |
| 2 | `UNREADABLE` | client; check 5 | `status.vftv.lathe.unreadable` | 0 |
| 3 | `INCOMPLETE` | client only: no effect or medium chosen | `status.vftv.lathe.incomplete` | 0 |
| 4 | `INVALID` | client; compose check 6 | `status.vftv.lathe.invalid` | 0 |
| 5 | `UNLEARNED` | client; compose check 7; spend "unlearned" | `status.vftv.lathe.unlearned` | 0 |
| 6 | `UNCHANGED` | client; compose check 8 | `status.vftv.lathe.unchanged` | 0 |
| 7 | `MAX_RANK` | client; rank check 6 | `status.vftv.lathe.max_rank` | 0 |
| 8 | `RANK_UNLEARNED` | client; rank check 7 | `status.vftv.lathe.rank_unlearned` | 0 |
| 9 | `XP_SHORT` | client; rank check 8 | `status.vftv.lathe.xp_short` | 2: levels needed, levels held |
| 10 | `NO_WAND` | client; rank check 9 | `status.vftv.lathe.no_wand` | 0 |
| 11 | `INERT_WAND` | client; rank check 9; spend "inert" | `message.vftv.spend.inert` | 0 |
| 12 | `EXCEEDS_CAPACITY` | client; rank check 10; spend "short" with `exceeds_capacity` | `status.vftv.worktable.exceeds_capacity` | 0 |
| 13 | `SHORT` | client; rank check 10; spend "short" | `message.vftv.spend.short` | 1 |
| 14 | `XP_REFUSED` | server only: rank check 12 | `status.vftv.lathe.xp_refused` | 0 |
| 15 | `STALE` | server only: checks 3, 8 and 13; spend "stale" | `message.vftv.spend.stale` | 0 |
| 16 | `ERROR` | server only: a spend that threw and was rolled back | `status.vftv.lathe.error` | 0 |

- **Two predictions.** The client predicts each action from synced state in its check order: **Turn** through `NO_FOCUS`, `UNREADABLE`, `INCOMPLETE`, `INVALID`, `UNLEARNED`, `UNCHANGED`, else `READY`; **Raise rank** through `NO_FOCUS`, `UNREADABLE`, `MAX_RANK`, `RANK_UNLEARNED`, `XP_SHORT`, `NO_WAND`, `INERT_WAND`, `EXCEEDS_CAPACITY`, `SHORT`, else `READY`. The status line shows Raise rank's prediction while that button is hovered or focused, otherwise Turn's.
- **Feedback is separate from eligibility** ([arcane, Status and refusals](arcane.md#status-and-refusals)). A shown refusal never disables a button whose local prediction is ready, so a retry after `STALE`, `SHORT` or `XP_REFUSED` needs no further slot change. Acknowledging, clearing and the forced resync after a refusal are the arcane spec's rules.

### Ranks

**Rank *r* = *r* modifier levels.** A rank never falls; recomposing keeps it. The cap is **3** (`FocusLimits.MAX_RANK`). Values are tuning (see "Tuning and limits"); these are the shipped values:

A rank whose tuning sets 0 levels costs no experience: no hook is asked and no experience change is prepared.

| Rank | Experience levels | Vis (base) | Research |
|---|---|---|---|
| 1 | 4 | Ordo 8 | `vftv:focus_ranks` |
| 2 | 8 | Ordo 16, Aer 8 | `vftv:focus_ranks` |
| 3 | 16 | Ordo 32, Aer 16, Terra 16 | `vftv:far_workings` |

Rank 2's vis fits a chime rod (150 per pool), and rank 3's an Echo rod ([infusion, The Echo rod and cap](infusion.md#the-echo-rod-and-cap)) at the default cap.

## The cost

**One pure function, `FocusCost.base(composition, registries)`,** in `core.focus.model`. Its result is the `base` handed to `VisService.quote` and `spend` unchanged ([wands, The quote](wands.md#the-quote)). For each aspect *a* of the effect's `cost`, in ascending id:

```
x = min(1000, ceilDiv(effect.cost[a] × medium.cost_pm, 1000))
for each modifier in ascending id, level times:
    x = min(1000, ceilDiv(x × modifier.cost_pm, 1000))
base[a] = x                      // int arithmetic: x ≤ 1000 and cost_pm ≤ 4000, so x × cost_pm ≤ 4 × 10⁶
```

- **Per cast:** Bolt, Touch and Burst pay once per cast, whatever the number of targets (which is capped). **A Beam pays this base per pulse.**
- **The ceiling of 1000** is the wands spec's base range, so `quote` never throws.
- **The context** is `VisContext(source = the effect's id, kind = ORDINARY or VOID by the effect's kind, research = the effect's research)`. A focus has no id of its own, and the effect is its stable identity. Ordinary foci therefore pay #25's `vis_factor` ≥ 1 ("ordinary magic suffers", [progression, Warp](progression.md#warp)), and Void foci pay whatever #25 sets for `VOID`. The medium's and modifiers' research is checked before the spend (see "Casting").
- **No discount in foci.** Discounts exist only on the attribute `vftv:vis_discount` ([wands, Contracts](wands.md#contracts-on-sibling-specs), on #24), so there is no thrift modifier.

**Vectors** (computed by a reference implementation and cross-checked against the JDK; the medium and modifier factors are the built-ins'):

| Effect cost | Medium (`cost_pm`) | Modifiers (`cost_pm`) | base |
|---|---|---|---|
| Ignis 4 | Bolt (1000) | none | Ignis 4 |
| Ignis 4 | Touch (700) | none | Ignis 3 |
| Terra 3, Perditio 2 | Burst (2500) | Breadth 1 (1500) | Perditio 8, Terra 12 |
| Aqua 4, Ordo 2 | Burst (2500) | Breadth 2 (1500), Force 1 (1300) | Aqua 30, Ordo 16 |
| Aer 3, Perditio 6 | Beam (400) | Force 3 (1300) | Aer 6, Perditio 8 |
| Aer 900 | 4000 | Force 1 (1300) | Aer 1000 (the ceiling) |

The fourth row through the wands quote: cap 1.00, no discount, W 1.00 → `m` 1000, Aqua 30, Ordo 16; cap 0.85, W 1.25 → `m` 1063, Aqua 32, Ordo 18; cap 0.70, discount 0.30, W 0.80 → `m` 500 (`clamped`), Aqua 15, Ordo 8.

## Casting

### Entry points

**Main hand only.** A wand in the off hand returns `PASS` from both paths, so vanilla carries on. Block work harvests through the main hand (see "Block work"), and one rule is simpler than two.

| Gesture | Hook | Order, from source |
|---|---|---|
| **use on a node** | tapping, always first ([wands, Hand-tapping](wands.md#hand-tapping)) | |
| **sneak-use on any other block**, with a focus | the wand's `onItemUseFirst`: the **block gesture** | it runs after `RightClickBlock` (and only if its item use isn't denied), **before** the sneak bypass, the block's use and vanilla's cooldown check (`MC:server/level/ServerPlayerGameMode.java:383–418`). The packet handler has already checked reach and `mayInteract` for the clicked block (`MC:server/network/ServerGamePacketListenerImpl.java:1375,1390`). |
| **use on air or an entity**, or on a block whose own use didn't consume the click | `Item#use`: the **entity gesture** | the client falls through to `useItem` when an entity's interaction or a block's use returns non-success (`MC:client/Minecraft.java:1736–1806`). The server checks vanilla's cooldown before `use` (`MC:server/level/ServerPlayerGameMode.java:333`). |

- **Not sneaking on an ordinary block,** the block's own use wins (a chest opens). If it doesn't consume the click, the entity gesture follows: a Kindle bolt fired at stone simply strikes the stone. **The world is only ever changed through the deliberate block gesture.**
- **The client** returns `CONSUME` from `onItemUseFirst` whenever its synced state shows sneaking, the main hand, a focus and a non-node block, so no air-use packet follows. The server decides.

**The gesture and the target domain are separate.** Each gesture resolves an aim (see "Media"); the effect's domain decides what the aim can touch:

| Effect domain | Entity gesture | Block gesture |
|---|---|---|
| `ENTITIES` | entities along the medium's aim | entities: Touch and Beam use the entity ray from the eye; Burst centres on the clicked block; Bolt aims through the clicked point |
| `BLOCKS` | **refused**: `message.vftv.focus.needs_block` ("This working needs something solid. Crouch and touch it.") | blocks: Touch the clicked block; Burst around it; Beam the block under the crosshair |

### Checks

**Before anything is spent,** in order. Each refusal returns `FAIL`, which consumes the click (so no air use follows), shows its message on the action bar, and changes nothing:

| # | Check | Refusal |
|---|---|---|
| 1 | a focus is attached (`vftv:wand_focus` present) | none: `PASS`, as [the wands spec](wands.md#hand-tapping) has it |
| 2 | not already inside a cast (the operation guard below) | silent |
| 3 | the wand is not inert | `message.vftv.spend.inert` |
| 4 | the focus is readable, not blank, every id is present and the composition is valid at its rank | `message.vftv.focus.unreadable` / `.blank` / `.unknown` (1: the missing id) / `.out_of_tune` |
| 5 | the effect, the medium and every modifier are learned | `message.vftv.focus.unlearned` |
| 6 | block gesture only: vanilla's cooldown (`isOnCooldown`), which this path checks itself because it runs before vanilla's | silent |
| 7 | a `BLOCKS` effect: the entity gesture is refused (above), and the server config `foci.block_effects` is on | `.needs_block`; `message.vftv.focus.block_effects_off` |
| 8 | the targets resolve (see "Media") and the **cast pin** is taken: Touch and Burst need at least one target; a `BLOCKS` effect needs at least one position that passes the block pre-filter ("Block work"). Bolt and Beam need none to start. | `message.vftv.focus.nothing_answers`; `message.vftv.focus.nothing_yields` |
| 9 | Bolt, Touch and Burst only: the quote is not `inert` and not `exceeds_capacity`, and is affordable. A Beam's start pays nothing itself, so its quote is the pulse's (see "Beam"). | `message.vftv.focus.too_much`; `message.vftv.spend.short` (every short aspect, in the wands spec's order) |

**The cast pin,** taken at check 8 and never recomputed: the caster's level and exact position; the wand's `ItemStack` object, its selected hotbar slot and the attached composition; and the **resolved geometry**: the aim's origin and direction, the Burst origin, the resolved targets in their apply order, and the block positions in their order. **The caster check** compares the current state with the pin: alive, in the pinned level at exactly the pinned position, and the main hand holding the same object in the same slot with the same composition. A teleport within the same level fails it as surely as a change of dimension.

### The cast

**Dispatch by medium.** Bolt, Touch and Burst are **one-shot casts**, below. A Beam is not: after checks 1–8 it installs its session and runs its single pulse procedure for the start tick (see "Beam"), and that procedure is the only thing that pays for it. There is no one-shot spend for a Beam, so a Beam can never pay twice at its start, nor pay for an empty start.

**A one-shot cast:**
1. **The operation guard.** A per-player flag in transient server memory is set before the spend and cleared **last**, in the `finally` below, after every notification. While it is set, any further cast or Beam pulse by that player (from a `VisSpentEvent`, `FocusAffectEntityEvent` or `FocusEvent` listener, say) is refused silently at check 2. So no listener can re-enter a cast before its cooldown is in place.
2. **The spend:** `VisService.spend(player, wand, base, ctx, expected = none, effect = [], boundary = none)`. A same-tick path passes no echo (wands step 3). A refusal maps to check 3, 5 or 9's message. A throw was already rolled back and logged by the wands spec: the cast ends with no cooldown.
3. **On `SPENT` or `COMMITTED_NOTIFY_FAILED`** (both committed), inside a `try`:
   1. **The caster check,** because a `VisSpentEvent` listener may have moved them or changed their hand. If it fails, the working **slips** (`message.vftv.focus.slipped`): nothing is applied.
   2. **Apply** the medium and effect over the pinned geometry (see "Media", "Effects" and "Block work"). The caster check runs again **between every callback-bearing operation of ours**: before each target's `FocusAffectEntityEvent`, before each `destroyBlock` and before each placement. A failure ends the working there; what was done stays done.
4. **`finally`,** whatever step 3 did, including a throw (logged once with the source, and never rethrown to vanilla):
   1. set `player.getCooldowns().addCooldown(stack, medium.cooldown)`. Vanilla's cooldown group is the item id unless a `use_cooldown` component says otherwise (`MC:world/item/ItemCooldowns.java:48–56`), so **every `vftv:wand` the player carries** shares it;
   2. play the cast sound, and post `FocusEvent(CAST)` (see "Events"), each caught and logged separately;
   3. clear the operation guard.

### Failure after the spend

The spend's atomicity covers vis only ([wands, The spend](wands.md#the-spend)); the working runs after it. **There is never a refund.** Vis leaves only through `spend`, and the checks above run first, so a refusal before the spend is the common case.

| Medium | What can fail after the spend | Result |
|---|---|---|
| Touch, Burst | a target resolved at check 8 is gone, dead, in another level, outside the pinned geometry, now protected by the PvP rule, or refused by `FocusAffectEntityEvent` | that target is skipped |
| Bolt | it misses, fizzles, or its impact is cancelled | paid at launch; nothing is applied |
| Beam | per pulse, as Touch | that pulse's targets are skipped |
| block work | a position changed, was denied by a protection event, or failed to place | that position is skipped (Exchange's break stands; see "Block work") |
| any | an exception while applying | logged; the working ends where it was; cooldown, notifications and the guard still run (step 4; for a Beam, its clear path) |
| any | the caster check fails after the spend | slipped: nothing applied |
| any | the caster check fails between operations | the rest of the working is abandoned; what was done stays done |

## Media

### Bolt

**`vftv:focus_bolt`**, an entity extending vanilla `Projectile`, registered with `EntityType.Builder#noSave()` (`MC:world/entity/EntityType.java:601`), so **it never persists**. Size 0.25 × 0.25, no gravity, no drag.

- **Launch** from the eye, along the look vector, at the medium's `speed`. On the block gesture the aim is the clicked point instead. Its owner is the caster.
- **The snapshot,** frozen at launch and never re-read: the caster's UUID, the effect's id and type, the kind, every scaled magnitude as resolved at launch (Force and the Void amplifier included, see "Magnitude"), and the tint. Only the tint is synced, as one `INT` entity data value.
- **Each tick** it moves by its velocity (≤ 3.0 blocks, the bound on `speed`) and finds its first hit along that step with vanilla's move-vector test (`ProjectileUtil.getHitResultOnMoveVector`), which never hits its owner while still leaving them.
- **An impact,** entity or block, is first offered to NeoForge's `ProjectileImpactEvent` through **`EventHooks.onProjectileImpact`**, called by the bolt itself through the platform seam. NeoForge requires custom projectiles to post it themselves (`NF:event/entity/ProjectileImpactEvent.java:15–21`; `NF:event/EventHooks.java:714–716`); extending `Projectile` does not do it (`MC:world/entity/projectile/Projectile.java:273–298`). **Cancelled:** the bolt is **discarded** with the fizzle particles and sound, and nothing is applied. It does not fly on, so it can never be processed twice.
- **An entity hit,** not cancelled: resolve the owner (`getOwner()` in this level, alive, not a spectator). If there is none (logged out, another dimension), the bolt **fizzles**: an absent caster can't be checked for PvP. Otherwise the target filter and the effect apply to that one entity (see "Targets"). Then the bolt is discarded.
- **A block hit** discards it with particles. Bolts never carry a `BLOCKS` effect ("Validity").
- **Lifetime:** discarded at `lifetime` ticks (≤ 100), so a bolt travels at most 300 blocks.
- **At most 8 live per player** (`MAX_LIVE_BOLTS`), tracked in transient server memory. A launch over the cap discards that player's oldest bolt first. Bolts in unloaded chunks are dropped with the chunk (`noSave`).

### Touch

- **Entity ray** (an `ENTITIES` effect, on either gesture): `ProjectileUtil.getHitResultOnViewVector(player, filter, entity_range)` (`MC:world/entity/projectile/ProjectileUtil.java:56`), with `entity_range = min(16, medium.range + reach levels × reach.step)`. The first hit wins: a block in the way means no target. The origin of the working is the caster's position.
- **A `BLOCKS` effect** (block gesture only): the clicked block, which vanilla's packet handler has already found within the player's reach. Its range is the player's, never the medium's: Reach can't be composed with it ("Validity").

### Burst

- **Origin:** on the entity gesture, the caster's bounding-box centre; on the block gesture, the clicked block's centre.
- **Entities:** `Level#getEntities(EntityTypeTest.forClass(LivingEntity), box, predicate, out, 16)` (`MC:world/level/Level.java:781`), where `box` is the origin ± `radius` and `predicate` is "the target's bounding-box centre is within `radius` of the origin, and the target passes the target filter" ("Targets"). The predicate already removes PvP-denied, creative, spectator and dead targets and, for a harmful effect, the caster, **so they never use up places**. It collects **at most 16** (`MAX_BURST_ENTITIES`); then the effect applies in ascending entity id. When more than 16 qualify, which 16 is the engine's section order. `radius = min(6, medium.radius + breadth levels × breadth.radius_step)`. **Bursts pass through walls:** there is no line-of-sight test.
- **Blocks** (a `BLOCKS` effect): offsets (dx, dy, dz) with dx² + dy² + dz² ≤ R², where R² = `min(16, block_radius_sq + breadth levels × block_step_sq)`, taken in ascending (dx² + dy² + dz², signed `BlockPos.asLong()` of the position), **first 27** (`MAX_BURST_BLOCKS`).

**Vectors** (reference implementation, cross-checked against the JDK; positions in order):

| Centre | R² | Candidates | Order (first 7) | 27th or last |
|---|---|---|---|---|
| (0, 64, 0) | 1 | 7 | (0,64,0), (−1,64,0), (0,63,0), (0,65,0), (0,64,1), (0,64,−1), (1,64,0) | (1,64,0) |
| (−1, 64, 1) | 1 | 7 | (−1,64,1), (−2,64,1), (−1,64,0), (−1,63,1), (−1,65,1), (−1,64,2), (0,64,1) | (0,64,1) |
| (−1, −60, 1) | 4 | 33 | (−1,−60,1), (−2,−60,1), (−1,−60,0), (−1,−61,1), (−1,−59,1), (−1,−60,2), (0,−60,1) | (0,−59,2) |

### Beam

**Held and pulsed.** The beam session lives in transient per-player server memory, in the same one-session slot as a tap ([wands, Hand-tapping](wands.md#hand-tapping)): `{stack (the server's ItemStack object), slot, composition, effect domain, start tick, last pulse tick}`.

- **Start,** from either gesture, after checks 1–8: install the session, call `startUsingItem` (the use duration is the wand's 72,000 ticks), send the beam-state signal, then run **the pulse procedure** for the start tick. A start that finds no target pays nothing and keeps beaming.
- **The schedule:** the start call runs the procedure at tick `start`. Later, `onUseTick` runs it on a tick `now` iff `now > last` and `(now − start) % pulse == 0`. Vector: start 100, pulse 10, `onUseTick` on every tick 100–130 → pulses at 100, 110, 120, 130; start 103 → 103, 113, 123.
- **The pulse procedure,** the only thing that pays for a Beam:
  1. **record `last = now`**, before any foreign code can run, and set the operation guard (refused silently if already set);
  2. **session checks,** ending the session on any failure: the same `ItemStack` object (`==`) in the same main-hand slot; the attached composition still equals the session's (an attach, detach or swap ends it); not inert; every part learned; for a `BLOCKS` effect, `foci.block_effects` still on;
  3. **the target, by effect domain, not by gesture:** an `ENTITIES` effect uses the entity ray at `entity_range` (with Reach), even when the beam was started with the block gesture; a `BLOCKS` effect uses the block raycast within **`block_range`** = `min(16, the player's block_interaction_range attribute)`, then that position's pre-filter. The pulse's pin is taken here (the caster, the wand, the composition and the target);
  4. **no target, no spend:** the procedure clears the guard and returns, and the beam continues;
  5. the quote and the spend: not `exceeds_capacity` and affordable, else the session ends with `message.vftv.focus.too_much` or `message.vftv.spend.short`;
  6. inside a `try`, the caster check, then apply, as a one-shot cast does;
  7. `finally`: the pulse's sound every 40 ticks, `FocusEvent(CAST)` (each notification caught separately), then clear the guard. A running Beam has no cooldown yet, so the guard is what stops re-entry through those notifications.
- **A slipped caster or an exception while applying ends the session** through its clear path, below.
- **The clear path,** the only way a session ends (release, a failed session check, short vis, a slip, an exception, logout, death or a dimension change, through `onStopUsing` and the logout event): remove the session, send the beam-state signal `false` (see "Sync and state"), and set the medium's cooldown **once**.
- **Spends per pulse, at the stated interval:** default every 10 ticks. That is also vanilla's invulnerability window after a hit, so a Beam never deals damage the target would ignore.

## Effects

### Targets

**The target filter** for an entity effect, applied wherever a target is chosen and again when it is applied:
- a `LivingEntity`, alive, not a spectator, and `isPickable()` (which excludes marker armour stands);
- a **harmful** effect never touches its caster, never touches a player in creative or spectator mode, and touches another player only if `target.canHarmPlayer(caster)`. That is the target-side PvP and team rule vanilla applies to damage (`MC:server/level/ServerPlayer.java:1021–1042`), applied here to push, pull, slowness and fire too;
- **Mend** is not harmful. It affects anyone, including the caster when a Burst centres on them.
- **`FocusAffectEntityEvent`** (cancellable) is posted for each target just before it is affected. A cancelled target is skipped. It gives claim integrations a seam for non-damage effects, which no vanilla event covers.

**Rechecks around listeners.** Per target, in order: the caster check; `FocusAffectEntityEvent`; then the **target recheck**: alive, in the pinned level, inside the pinned geometry (a Touch or Beam target within its range of the caster's pinned eye, a Burst target within the radius of the pinned origin, a bolt's target where it was hit), and still passing the filter above, including the PvP rule. Only then is the effect applied. For Kindle and Rime, the **target recheck runs again after the damage and before the damage-dependent secondary work** (fire, slowness), because damage listeners run in between. A failed caster check abandons every remaining target. Callbacks *inside* vanilla's own damage, knockback and healing keep vanilla's own behaviour.

**The origin of a working:** for Touch and Beam, the caster's pinned position; for a Bolt, the bolt's position at impact; for a Burst, its pinned origin ("Burst").

**Damage** uses `damageSources().source(type, direct, caster)` (`MC:world/damagesource/DamageSources.java:83`): the direct entity is the bolt for a Bolt, otherwise the caster. Mobs therefore react to the caster as to any player's attack, and vanilla's own PvP check sees the caster as the causing entity. No foci damage type bypasses invulnerability frames.

| Effect | Applies |
|---|---|
| **Kindle** | `hurtServer(damage)` with type `vftv:kindle` (tagged `minecraft:is_fire`, so fire-immune mobs take none); **then, only if the damage was dealt** (`hurtServer` returned true), `igniteForTicks(ignite)` (`MC:world/entity/Entity.java:645`) |
| **Gust** | `knockback(push / 1000, dx, dz, source, 0)` (`MC:world/entity/LivingEntity.java:1711–1737`). Vanilla knockback moves the target **against** the vector it is given (`LivingEntity.java:1728–1732`), so (dx, dz) = (origin.x − target.x, origin.z − target.z), and the target is pushed away from the origin. **Zero vector:** if that horizontal distance is below `GUST_MIN_DISTANCE` (0.0001), (dx, dz) = (sin(yaw), −cos(yaw)) for the caster's yaw, which pushes along the caster's facing: yaw 0 pushes toward +Z. A pushed player is marked `hurtMarked` (`MC:world/entity/Entity.java:238`) so their client moves. Deals no damage. |
| **Rime** | `hurtServer(damage)` with type `vftv:focus`; then, **only if dealt,** Slowness at `slow_amplifier` for `slow_ticks`, and the target is extinguished |
| **Mend** | `heal(heal / 1000)` (`MC:world/entity/LivingEntity.java:1199`) |
| **Unmaking** | `hurtServer(damage)` with type `vftv:unmaking` (tagged `minecraft:bypasses_armor`, `MC:tags/DamageTypeTags.java:9`) |
| **Undertow** | with v = the caster's pinned position − the target's position (3-D): if \|v\| < `UNDERTOW_MIN_DISTANCE` (0.5), nothing is added (it is already there). Otherwise the target's velocity becomes `d = velocity + (v / \|v\|) × pull / 1000`, and if \|d\| > 3.0 the **whole vector** is scaled to length 3.0 (`d × 3.0 / \|d\|`). `hurtMarked`. Deals no damage. |

**Vectors** (reference implementation, cross-checked against the JDK; 6 decimals):

| Gust fallback, yaw | knockback argument (sin, −cos) | the target moves toward |
|---|---|---|
| 0° | (0, −1) | (0, 1): +Z |
| 90° | (1, 0) | (−1, 0): −X |
| 180° | (0, 1) | (0, −1): −Z |
| −45° | (−0.707107, −0.707107) | (0.707107, 0.707107) |

| Undertow: velocity | v (caster − target) | pull | new velocity |
|---|---|---|---|
| (0, 0, 0) | (3, 0, 4) | 1000 | (0.6, 0, 0.8) |
| (2, 0, 2) | (3, 0, 4) | 1000 | (2.041353, 0, 2.198380): (2.6, 0, 2.8) scaled to length 3.0 |
| (0, 0, 0) | (0.3, 0, 0.3) | 3000 | unchanged: \|v\| < 0.5 |
| (0, 0, 0) | (0, −6, 0) | 3000 | (0, −3, 0): exactly at the speed cap, not scaled |

### Block work

Delve and Exchange change the world only through **the player's own mining and placing paths**, so protection, logging and rollback mods see a player mining and placing.

**The pre-filter,** before the spend, for each candidate position in order. None of it posts an event:
- its chunk is loaded (`getChunkNow`), and it is inside the build height and the world border;
- it is not air: only a real block can yield;
- `level.mayInteract(player, pos)` (`MC:server/level/ServerLevel.java:901`);
- `!player.blockActionRestricted(level, pos, gameMode)`, the adventure-mode rule that mining applies (`MC:server/level/ServerPlayerGameMode.java:283`), checked here so it refuses before the spend;
- it is not a node, and not in the block tag `vftv:focus_immune` (empty by default, for packs);
- `0 ≤ state.getDestroySpeed(level, pos) ≤ max_hardness`;
- `!state.requiresCorrectToolForDrops()` or the `tool` stack is correct for it;
- **Exchange only:** the off hand holds a `BlockItem` whose block differs from the position's.

**After the spend,** for each surviving position in order, and **before each one** (each break and placement runs foreign listeners):
1. **Revalidate:** the caster check (pinned level, position, wand, slot and composition; a failure abandons every remaining position), the position's state equals its pre-filter state, and the position still passes the pre-filter (for Exchange, the off hand still holds the same block item with at least one).
2. **Break** with **`player.gameMode.destroyBlock(pos)`**, the exact vanilla mining path (`MC:server/level/ServerPlayerGameMode.java:268–311`). It posts NeoForge's **`BreakBlockEvent`** (`net.neoforged.neoforge.event.level.block`) through `CommonHooks.fireBlockBreak` (`NF:common/CommonHooks.java:600–625`) and returns false if that is cancelled. It also honours game-master blocks, adventure restrictions (`blockActionRestricted`), `playerWillDestroy` (both halves of a door or bed), and drops and experience through `playerDestroy`. So every sibling's own removal handling applies: a crucible spills ([crucible, Removal](crucible.md#removal)), a jar drops as a jar ([essentia spec](essentia.md)), and a pedestal or bowl follows [infusion, Removal](infusion.md#removal).
3. **Harvest:** `destroyBlock` asks `state.canHarvestBlock`, whose default reaches `EventHooks.doPlayerHarvestCheck` (`NF:common/extensions/IBlockExtension.java:217–219`; `NF:event/EventHooks.java:226–230`; `MC:server/level/ServerPlayerGameMode.java:294–301`). Its first answer comes from the main hand. **The wand's `isCorrectToolForDrops(stack, state)`** (`MC:world/item/Item.java:291–294`) answers `tool.isCorrectToolForDrops(state)` for an attached `delve` or `exchange` focus, else false. The eligibility is therefore an iron pickaxe's by default, **subject to the `PlayerEvent.HarvestCheck` hook**, and **the loot receives the wand** as its tool, not an iron pickaxe. One accepted side effect: a wand holding Delve or Exchange also harvests when punched with, at fist speed.
4. **Exchange rechecks after the break,** because the break, harvest and removal listeners have just run: the caster check again; the off hand still holds the same block item with at least one; the level is still the pinned one. A failure abandons the rest, and this position stays broken.
5. **Exchange places,** only if `destroyBlock` returned true **and** the position no longer holds its old state **and** a `BlockPlaceContext` built from a hit on that position with the off-hand stack is not redirected (`getClickedPos()` is the position, `MC:world/item/context/BlockPlaceContext.java:25–54`) and `canPlace()`. `destroyBlock` can return true even when the removal failed (`MC:server/level/ServerPlayerGameMode.java:287–309`), so all three are needed. It places through **the off-hand stack's own `ItemStack#useOn`**, NeoForge's placement wrapper (`MC:world/item/ItemStack.java:364–367` → `CommonHooks.onPlaceItemIntoWorld`, `NF:common/CommonHooks.java:628–709`). The item places first, then the wrapper posts **one** `EntityPlaceEvent`, or for several changed blocks (a bed, a door) **one `EntityMultiPlaceEvent` carrying their snapshots** (`:670–674`; `NF:event/level/BlockEvent.java:95–120`). On cancellation it restores those blocks and the stack's count and components. One item is consumed (none with infinite materials), oriented as a hand placement would.
6. **Skipped positions** (a failed revalidation, a cancelled break, a refused or impossible placement) are skipped with no refund. **An Exchange is not atomic:** a placement refused after its break leaves the position broken, as a Delve would. Its tooltip and book page say so. A Burst Exchange stops when the off hand runs out.

**Callbacks inside vanilla's own mining and placing** (listeners of the break event, `playerWillDestroy`, drops, the placement's own callbacks) keep vanilla's limits: this spec rechecks between those calls, never inside them.

**The placement wrapper's limits.** It is not a general transaction over addon side effects, and it has no `finally` around the item's `useOn` and its snapshot capture (`NF:common/CommonHooks.java:645–653`). On a cancelled placement it sets the level's `restoringBlockSnapshots` before restoring (`:680–683`), so a throwing restore would leave that flag set. The platform adapter that calls it therefore:
1. **refuses entry** when the level is already capturing (`captureBlockSnapshots`), already restoring (`restoringBlockSnapshots`) or holds captured snapshots. The position is skipped as a failed placement and logged once. It never clears another operation's snapshots;
2. saves both flags, calls the wrapper, and in its own `finally` **restores both** `captureBlockSnapshots` and `restoringBlockSnapshots` to their saved values, removing from the captured list only the snapshots this call added;
3. on an escaped exception, logs once with the position and ends the working (see "Failure after the spend").

Blocks already changed stay changed. The promise is the events under "Multiplayer and automation", **not** that every listener sees exactly what a vanilla input packet would cause.

- **Options considered:** posting a break-event query before the spend and then breaking without one; installing and restoring provisional blocks to ask the place event first (the wands spec's Bell Jar pattern). **Why the player paths:** the stock break always posts its own event, so a query first would post it twice, and a provisional install per position is a large surface for a few vis. With the player's own paths there is no bespoke protection code: a few vis can be lost on a denied position, after every check that can run without posting has run.

## The Void tier

**Only `void` effects are amplified, and only in magnitude** (the scaled quantities under "Effects"), never in radius, range, target count, cost or cadence. Every work bound in this spec therefore holds at any warp. Ordinary foci are never weakened in power; warp reaches them only through cost ("The cost"). That keeps one lever, owned by #25.

### Magnitude

**One pure function, `FocusMagnitude.of(base, forceLevel, forceStep, w, rules, void, cap)`,** in `core.focus.model`:

```
power_pm = 1000 + forceLevel × forceStep            // forceLevel 0–3, forceStep 0–1000 (the Force entry's step_pm): 1000–4000
amp_pm   = void ? 1000 + min(rules.max_bonus_pm, clamp(w, 0, 10000) × rules.per_warp_pm) : 1000    // 1000–5000
out      = min(cap, floorDiv(base × power_pm × amp_pm, 1_000_000))                                   // long
```

- **Bounds before multiplying:** `base` ≤ 20,000 (every scaled param's range), `power_pm` ≤ 4000 and `amp_pm` ≤ 5000, so the product is at most 4 × 10¹¹, well inside a `long`. The **caps** (`FocusLimits`) apply after: 40,000 for damage and heal (40.0 health), 3000 for push and pull (3.0 blocks per tick), 1200 ticks for durations.
- **`w = amplifier_warp(player)`** is [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)' input: pure, finite and synced to the owner, so both sides compute the same figure. It is the player's **total warp** in warp points (temporary, lasting, permanent and gear together; [warp spec, Total warp](warp.md#total-warp)), plus the warp spec's **Joining bonus** (`joining.amplifier_bonus`, default 10) for a Joined Listener ([Eldritch tier, The capstones](eldritch.md#the-capstones)), or 0 with the warp server switch `warp.amplifier` off. It is at most 2,250, inside the clamp. Until #25 lands it is 0, so Void effects work at their base magnitude.
- **The scale:** at the default `per_warp_pm` of 20, each warp point adds 2%, and a Void working doubles at 50 warp, just past the warp spec's third band (40). That is a scale decision made with the warp spec's bands, not proven balance.
- **Snapshots:** a Bolt freezes its magnitudes at launch; Touch and Burst resolve them at the cast; a Beam resolves them at every pulse.
- **Units:** health is `out / 1000f`; movement is `out / 1000` blocks per tick; ticks are `out`.

**Vectors** (reference implementation, cross-checked against the JDK; Force step 250; `per_warp_pm` 20 and `max_bonus_pm` 1000 unless stated; the 10 per mille row is a labelled regression, run by the same references):

| base | Force | w | Void | `power_pm` | `amp_pm` | cap | out |
|---|---|---|---|---|---|---|---|
| 3000 | 0 | 0 | no | 1000 | 1000 | 40,000 | 3000 |
| 3000 | 2 | 40 | no | 1500 | 1000 | 40,000 | 4500 (warp ignored) |
| 4000 | 0 | 0 | yes | 1000 | 1000 | 40,000 | 4000 |
| 4000 | 0 | 37 | yes | 1000 | 1740 | 40,000 | 6960 |
| 4000 | 0 | 50 | yes | 1000 | 2000 | 40,000 | 8000 |
| 4000, Joined at `total` 37 (bonus 10) | 0 | 47 | yes | 1000 | 1940 | 40,000 | 7760 |
| 4000, Joined at `total` 5 (bonus 10) | 0 | 15 | yes | 1000 | 1300 | 40,000 | 5200 |
| 4000, Joined at `total` 37, `warp.amplifier` off | 0 | 0 | yes | 1000 | 1000 | 40,000 | 4000 |
| 4000, a regression at `per_warp_pm` 10 | 0 | 37 | yes | 1000 | 1370 | 40,000 | 5480 |
| 4000 | 3 | 250 | yes | 1750 | 2000 (bonus capped) | 40,000 | 14,000 |
| 4000 | 1 | 10,001 | yes | 1250 | 2000 (w clamped) | 40,000 | 10,000 |
| 60 (ticks) | 1 | 0 | no | 1250 | 1000 | 1200 | 75 |
| 1000 (pull) | 3 | 250 | yes | 1750 | 2000 | 3000 | 3000 (capped from 3500) |
| 20,000 | 3 | 10,000 | yes | 1750 | 2000 | 40,000 | 40,000 (capped) |
| 20,000, at the tuning extremes (`per_warp_pm` 1000, `max_bonus_pm` 4000, step 1000) | 3 | 10,000 | yes | 4000 | 5000 | 40,000 | 40,000; the raw product is 400,000,000,000 |

- **The three Joined rows** come from the [Eldritch tier spec's](eldritch.md#the-capstones) references (the Joining bonus is added before this function sees `w`); the magnitude fixtures transcribe them with the rest.
- **No warp is applied by casting.** Warp is only ever applied by an explicit commit through #25 ([research, Warp on entries](research.md#warp-on-entries)), and a cast is not one. **A carried Void focus is not gear warp** ([warp spec, Gear warp](warp.md#gear-warp)): it already grows with warp, and adding warp for carrying it would compound.
- **Options considered:** scaling radius or target count; scaling cost down. **Why magnitude only:** it keeps every performance bound fixed, and cost already has #25's `VOID` factor as its own lever (neutral, 1.0, in the warp spec).

## The authoritative preview

**`FocusPreview.of(player, context, wandSource)`** in `core.focus` is the **only** source for the Lathe's numbers, the tooltips and the fact fields. It embeds the complete `VisQuote` **unchanged**, as the [arcane preview](arcane.md#the-authoritative-preview) does.

**The context** is one of:

| Context | Identity | Fields it has |
|---|---|---|
| **composition** | an effect, a medium, modifiers and a rank (a focus, or the Lathe's draft) | `cost`, `cost_effective`, `flux`, the three quote flags, `availability`, `wand`, `cooldown`, `entity_range`, `block_range`, `radius`, `block_radius_sq`, `pulse`, `magnitude`, `duration`, `slow_amplifier`, `amplifier`, `kind`, `castable` |
| **effect** | an effect id | `cost` (the effect's own), `magnitude`, `duration`, `slow_amplifier`, `kind`, `hardness` |
| **medium** | a medium id | `cost_factor`, `cooldown`, `range`, `radius`, `block_radius_sq`, `pulse`, `speed`, `lifetime` |
| **modifier** | a modifier id | `cost_factor`, `max_level`, `step_pm` (Force), `step` (Reach), `radius_step` and `block_step_sq` (Breadth) |
| **rank** | 1–3 | `xp`, `cost`, `cost_effective`, `flux`, `availability`, `wand` |

| Field | Value |
|---|---|
| `cost` | the base cost: "The cost" for a composition (per pulse for a Beam), the rank's vis for a rank, the entry's own `cost` for an effect |
| `cost_effective`, `flux`, `discount_capped`, `clamped`, `exceeds_capacity` | from the quote, present iff `PRICED` |
| `availability` | `PRICED`, `INERT` or `NO_WAND`, as in the arcane preview |
| `wand` | `STATION`, `HOLDING` (the wand the focus is on), `MAIN_HAND`, `OFFHAND` or `INVENTORY`; absent iff `NO_WAND` |
| `cooldown`, `pulse`, `radius`, `block_radius_sq` | the medium's, with Breadth applied for a composition (`min(6, …)` and `min(16, …)`); for a `BLOCKS` composition `radius` is not applicable, and for an `ENTITIES` one `block_radius_sq` is not |
| `entity_range` | an `ENTITIES` composition by Touch or Beam: `min(16, range + Reach)` |
| `block_range` | a `BLOCKS` composition: by Beam, `min(16, the player's block_interaction_range attribute)`; by Touch or Burst, the attribute itself, which vanilla's reach check applies to the clicked block |
| `range` | a medium's own `range` |
| `magnitude`, `duration` | the effect's primary and secondary scaled quantities, with Force and the **current** Void amplifier for a composition (the effect's base without one); not applicable for Delve and Exchange, and `duration` not applicable for Gust, Mend, Unmaking and Undertow |
| `slow_amplifier` | Rime's; not applicable otherwise |
| `amplifier` | `amp_pm` now; 1000 for an ordinary effect |
| `kind` | the effect's kind |
| `castable` | `READY`, or the first failing cast check 3–5 |

- **Pricing wand:** in the Lathe, slot 1; for a focus on a wand, that wand (`HOLDING`); otherwise the arcane spec's **reference wand** (the first non-inert wand among the selected hotbar slot, the off hand, then inventory slots 0–35).
- **A focus with no composition** is never put through the composition arithmetic. A blank, unreadable, unknown (a missing part) or out-of-tune focus yields a preview carrying only that status, and every composition field renders its text: `focus.vftv.fact.blank`, `.unreadable`, `.unknown` (1: the missing id) or `.out_of_tune`.
- **Absent values are typed, never guessed:** without a priced quote, effective cost and flux render the arcane spec's `arcane.vftv.no_wand` or `arcane.vftv.inert_wand`. A field the context doesn't have, or that its type doesn't apply, renders `focus.vftv.fact.not_applicable`.
- **Live dependencies:** a preview is recomputed when any input changes: the context (the draft, the slots, the held focus), the pricing wand and its pools, the learned set, the focus registries (on join), `FociRules`, `VisRules`, the discount attribute, the player's `block_interaction_range` attribute, #25's factor and amplifier inputs, the resources (names), or a disconnect (every cache is cleared). Caches may keep layout, **never an old value**.
- **The preview decides nothing.** The server's checks decide.

**The tooltip** of a focus, or of a wand with one, with Shift held: the effect, medium and modifiers by name; the rank pips; `cost` and, when priced, `cost_effective` with the multiplier's parts, every set flag and the spill (the arcane cost-tooltip lines); the cooldown, range or radius, pulse and magnitude now; the kind; each unlearned part (`tooltip.vftv.focus.unlearned`); and for Exchange, `tooltip.vftv.focus.exchange_warning`. Without Shift, one line names the effect and medium.

**Fact sources** ([research, Prose and fact references](research.md#prose-and-fact-references), amended in this change). Each has a fixed arity:

| Span | Context |
|---|---|
| `{fact:focus:<key>}` | the **reference focus**: the main hand's wand's focus, else a focus in the main hand, else the off hand's wand's focus, else a focus in the off hand. With none, every key renders `focus.vftv.fact.no_focus`; with a focus that has no composition, the status texts above. |
| `{fact:focus_effect:<ns>:<path>:<key>}` | that effect |
| `{fact:focus_medium:<ns>:<path>:<key>}` | that medium |
| `{fact:focus_modifier:<ns>:<path>:<key>}` | that modifier |
| `{fact:focus_rank:<n>:<key>}` | rank *n*, 1–3, priced with the reference wand |
| `{fact:focus_rules:<key>}` | one of the `FociRules` keys in the last rows below |

**Keys and formats:**

| Key | Renders |
|---|---|
| `cost`, `cost_effective` | "*Aspect* *n*" in ascending aspect id, joined by ", " |
| `flux`, `cooldown`, `pulse`, `lifetime`, `max_level`, `xp`, `duration`, `block_radius_sq`, `block_step_sq` | an integer |
| `slow_amplifier` | the effect level vanilla shows, amplifier + 1, as an integer (amplifier 0 → "1") |
| `range`, `entity_range`, `block_range`, `radius`, `radius_step`, `speed`, `step`, `hardness` | a decimal with up to 2 places, trailing zeros trimmed (4.0 → "4", 1.5 → "1.5") |
| `magnitude` | thousandths as a decimal with up to 3 places, trimmed (3000 → "3", 1200 → "1.2") |
| `cost_factor`, `amplifier` | per mille as the arcane spec's factor format (2500 → "2.50", 1370 → "1.37") |
| `step_pm` | per mille as the arcane spec's percentage format (250 → "25%") |
| `kind` | `focus.vftv.kind.ordinary` or `.void` |
| `discount_capped`, `clamped`, `exceeds_capacity` | `focus.vftv.fact.yes` or `.no` |
| `availability`, `castable` | `arcane.vftv.no_wand`, `arcane.vftv.inert_wand` or `focus.vftv.fact.priced`; `focus.vftv.fact.ready` or the failing check's own `message.vftv.*` text |
| `wand` | the pricing wand's name and the arcane spec's `arcane.vftv.where.*` text, or the absent text |
| **`focus_rules`** `void.per_warp_pm`, `void.max_bonus_pm` | per mille as the percentage format (20 → "2%", 1000 → "100%") |
| **`focus_rules`** `limits.max_rank`, `limits.burst_entities`, `limits.burst_blocks`, `limits.live_bolts` | an integer: 3, 16, 27, 8 |

- A key outside its context's list, a `focus_rules` key outside the last two rows, or any key outside this table is a malformed span, rejected by the validator.
- **Representative spans** (each validated by the build-phase validator and resolved in a test): "A kindled bolt costs {fact:focus_effect:vftv:kindle:cost} before its medium", "this working costs {fact:focus:cost_effective} each time, or each pulse", "a burst reaches {fact:focus_medium:vftv:burst:radius} blocks, and each breadth adds {fact:focus_modifier:vftv:breadth:radius_step}", "each force adds {fact:focus_modifier:vftv:force:step_pm}", "the second rank asks {fact:focus_rank:2:xp} levels", "what hears me strengthens a Void working by {fact:focus_rules:void.per_warp_pm} for each measure, up to {fact:focus_rules:void.max_bonus_pm}".
- **Until the first `FociRules` arrives,** a `focus_rules` or `focus_rank` span renders `[?]` without logging. An effect, medium or modifier id a pack removed renders `[?]` and is logged once ([research, Prose and fact references](research.md#prose-and-fact-references)).
- **JEI:** the Lathe and blank focus are `vftv:arcane` recipes, which the arcane spec's JEI integration already shows. Composing is not a recipe and has no JEI category.

## Multiplayer and automation

| Path | Behaviour |
|---|---|
| Players | every cast path above; each player's casts, bolts and beam session are their own |
| Two players on one target | applied in server order within a tick, under vanilla invulnerability frames; no ordering across runs is promised |
| One Lathe, several players | independent menus: the block holds nothing |
| [Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23) | never cast or use the Lathe in v1 |
| Dispensers, droppers | no behaviour: nothing casts without a player's use |
| Cradled wands | keep their focus; charging ignores it |
| Fake players (other mods) | no special case: they pay from their own wand and pass the same checks and events |

- **Untrusted input:** one C2S payload, `LatheAction`, bounded at decode and checked as above, with a GameTest per bad input. Casting rides vanilla's use packets, whose reach and `mayInteract` checks apply. Attaching rides vanilla's container clicks.
- **The protection promise is exactly:**
  - vanilla's block-use checks (spawn protection, `mayInteract` and `RightClickBlock`) for the clicked block of a **block gesture**, and our own non-posting `mayInteract` and `blockActionRestricted` pre-filter for every block position;
  - **one `BreakBlockEvent` per `destroyBlock` call** we make, for the position we nominate. A counterpart block that vanilla's `playerWillDestroy` removes (the other half of a door or bed) gets no break event of its own;
  - **one `EntityPlaceEvent` or one `EntityMultiPlaceEvent` per placement-wrapper call**;
  - vanilla's damage events for damage; `ProjectileImpactEvent` for every bolt impact; and `FocusAffectEntityEvent` for every entity a focus affects.

  Callbacks inside vanilla's own mining and placing keep vanilla's limits. A claim mod that wants more needs an integration, which the mod-compatibility spec owns.

## Rendering and sound

- **Bolt renderer:** one camera-facing quad from the 4-frame strip `vftv:entity/focus_bolt` (16×64, frame `(age / 2) % 4`), cutout, full-bright, tinted with the effect aspect's base colour (an essentia tint, [art style §2](art-style.md#2-palette-locked-project-wide-48-colours)). It allocates nothing per frame.
- **Bolt trail:** at most one vanilla `DustParticleOptions` (that colour, scale 0.6) per bolt every 2 ticks, client-side, only within 32 blocks of the camera, none at "minimal". The client counts its live trail particles and **drops any new one while 64 are alive** (a fixed limit, the wands spec's glint pattern).
- **Beam renderer:** drawn for a player whose **beam-state signal** is on (see "Sync and state"), never inferred from the use flag, because a Beam-focused wand may be tapping a node. A strip of at most 16 camera-facing quads from the right hand to a client-side ray by the effect's domain (the entity ray at `entity_range`, or the block ray at `block_range`), using `vftv:entity/focus_beam` (16×16, tiled), tinted likewise. It is cosmetic only.
- **Touch and Burst:** the server sends vanilla dust in the effect's tint with `ServerLevel#sendParticles` (`MC:server/level/ServerLevel.java:1345`): at most 6 at a Touch's hit, and at most 24 on a Burst's sphere (fixed feedback constants).
- **Cooldown:** vanilla's hotbar cooldown overlay on every wand. There is no HUD layer of our own.

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:focus.cast_bolt`, `vftv:focus.cast_touch`, `vftv:focus.cast_burst` | 3 each | an ordinary cast | at the caster, to everyone in range; `SoundSource.PLAYERS`, volume 1.0, pitch 1.0 |
| `vftv:focus.beam` | 3 | an ordinary Beam, every 40 ticks while it lasts (the wands spec's drone rule) | likewise |
| `vftv:focus.void_cast` | 3 | any Void cast or Void Beam drone, instead of the medium's | likewise |
| `vftv:focus.hit` | 3 | a bolt hitting an entity | at the hit, likewise |
| `vftv:focus.fizzle` | 1 | a bolt fizzling or cancelled | at the bolt, volume 0.6 |
| `vftv:lathe.turn` | 1 | a committed compose | at the Lathe, `SoundSource.BLOCKS`, volume 1.0, pitch 1.0 |
| `vftv:lathe.rank` | 1 | a committed rank-up | likewise |

**Files:** `assets/vftv/sounds/focus/cast_bolt_0.ogg` … `_2`, `cast_touch_0` … `_2`, `cast_burst_0` … `_2`, `beam_0` … `_2`, `void_cast_0` … `_2`, `hit_0` … `_2`, `fizzle.ogg`; `assets/vftv/sounds/lathe/turn.ogg`, `rank.ogg`. All are ≤ 3 s with the standard faded tail ([art style §7](art-style.md#7-sound-palette)): the ordinary casts and the Lathe in the Stage 1 register, `void_cast` in the Stage 4 register. `sounds.json` entries carry no volume or pitch overrides and name `subtitles.vftv.<event>`.

## Authored content

The content tickets deliver authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen, which writes the resources into `src/generated/resources`. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails load validation. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/foci/effects.json          the six ordinary effects
content/foci/effects_void.json     the two Void effects
content/foci/media.json            the four media
content/foci/modifiers.json        the three modifiers
content/foci/recipes.json          the Lathe and blank-focus arcane recipes
content/foci/damage_types.json     the three damage types and their tags
content/foci/fixtures.json         the vector tables, transcribed
content/lang/en_us/foci.json       every key under "Lang keys" except the Void effects' names
content/lang/en_us/foci_void.json  the Void effects' names
```

| Input | Schema | Generated output |
|---|---|---|
| `effects.json`, `effects_void.json` | `vftv:focus_effects/1` | `data/<ns>/vftv/focus_effect/<path>.json` |
| `media.json` | `vftv:focus_media/1` | `data/<ns>/vftv/focus_medium/<path>.json` |
| `modifiers.json` | `vftv:focus_modifiers/1` | `data/<ns>/vftv/focus_modifier/<path>.json` |
| `recipes.json` | `vftv:foci_recipes/1` | `data/vftv/recipe/<path>.json` |
| `damage_types.json` | `vftv:damage_types/1` | `data/vftv/damage_type/<path>.json`; the `vftv:kindle` entry in `data/minecraft/tags/damage_type/is_fire.json` and the `vftv:unmaking` entry in `.../bypasses_armor.json` (both `"replace": false`) |
| `fixtures.json` | `vftv:foci_fixtures/1` | nothing: read by tests only |
| `lang/<lang>/foci.json`, `foci_void.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json`; a duplicate key across all `content/lang` files fails datagen |

**Merging:** files with the same schema are merged by datagen. An id declared twice **within one output registry** (two `vftv:focus_effects/1` files declaring the same effect, say) fails datagen. Ids in different registries may coincide: the effect `vftv:kindle` and the damage type `vftv:kindle` are different things.

### Effects

`{"schema": "vftv:focus_effects/1", "effects": {<id>: <entry>}}`, each entry in the registry format. `effects.json` holds **exactly** `vftv:kindle`, `vftv:gust`, `vftv:rime`, `vftv:delve`, `vftv:mend` and `vftv:exchange`; `effects_void.json` holds **exactly** `vftv:unmaking` and `vftv:undertow`. Two complete entries (shipped values):

```json
{"schema": "vftv:focus_effects/1", "effects": {
  "vftv:kindle": {
    "type": "vftv:kindle", "kind": "ordinary", "research": "vftv:foci", "aspect": "vftv:ignis",
    "media": ["vftv:bolt", "vftv:touch", "vftv:burst", "vftv:beam"],
    "cost": {"vftv:ignis": 4},
    "params": {"damage": 3000, "damage_type": "vftv:kindle", "ignite_ticks": 60}},
  "vftv:delve": {
    "type": "vftv:delve", "kind": "ordinary", "research": "vftv:delving", "aspect": "vftv:terra",
    "media": ["vftv:touch", "vftv:burst", "vftv:beam"],
    "cost": {"vftv:terra": 3, "vftv:perditio": 2},
    "params": {"tool": "minecraft:iron_pickaxe", "max_hardness": 3.0}}
}}
```

**Rejected:** a missing or unknown field; anything that fails load validation; a built-in entry differing from the roster table; a file holding other than its listed ids; a `void` entry outside `effects_void.json` or an `ordinary` one inside it; **a built-in `void` effect whose `research` is not a Stage 4 entry**, or an ordinary built-in gated above the Stage in "Stage".

### Media and modifiers

`{"schema": "vftv:focus_media/1", "media": {<id>: <entry>}}` and `{"schema": "vftv:focus_modifiers/1", "modifiers": {<id>: <entry>}}`, holding exactly the built-ins. One of each (shipped values):

```json
{"schema": "vftv:focus_media/1", "media": {
  "vftv:beam": {"type": "vftv:beam", "research": "vftv:far_workings", "cost_pm": 400, "cooldown": 10,
                "params": {"range": 12.0, "pulse": 10}}}}
```

```json
{"schema": "vftv:focus_modifiers/1", "modifiers": {
  "vftv:breadth": {"type": "vftv:breadth", "research": "vftv:focus_ranks", "cost_pm": 1500, "max_level": 2,
                   "media": ["vftv:burst"], "params": {"radius_step": 1.0, "block_step_sq": 3}}}}
```

**Rejected:** as for effects; a built-in differing from the roster tables.

### Recipes

`{"schema": "vftv:foci_recipes/1", "recipes": {<id>: <recipe>}}`, holding exactly `vftv:lathe` (result `vftv:lathe`) and `vftv:blank_focus` (result one `vftv:focus` with `{"format": 1, "rank": 0}`). Both bodies use the [arcane recipe type's](arcane.md#the-recipe-type) format verbatim, with `"type": "vftv:arcane"`, `"research": "vftv:foci"` and `"kind": "ordinary"`.

```jsonc
{"schema": "vftv:foci_recipes/1", "recipes": {
  "vftv:blank_focus": {
    "type": "vftv:arcane", "research": "vftv:foci", "kind": "ordinary",
    "result": {"id": "vftv:focus", "count": 1, "components": {"vftv:focus": {"format": 1, "rank": 0}}},
    // … pattern and key, and vis
  }
  // … and vftv:lathe
}}
```

**Rejected:** an id outside the two; a `research`, `kind` or result other than the above; a body that fails the arcane type's rules; a base vis cost above **32 per aspect** (the arcane spec's Stage 1 affordability bound); an ingredient that is not reachable before iron in the Stage 1 path (the content ticket's reviewed list: copper, amethyst, wood and vanilla materials of the same tier).

### Damage types

`{"schema": "vftv:damage_types/1", "types": {<id>: {"exhaustion", "scaling", "tags"}}}`, holding exactly `vftv:kindle` (tags `["minecraft:is_fire"]`), `vftv:focus` (no tags) and `vftv:unmaking` (tags `["minecraft:bypasses_armor"]`), each with `exhaustion` 0.0–1.0 and `scaling` one of vanilla's three strings, `never`, `when_caused_by_living_non_player` or `always`. The generated `message_id` is `<namespace>.<path>` of the id (`vftv.kindle`, `vftv.focus`, `vftv.unmaking`), which the death-message keys under "Lang keys" match. **Rejected:** another id; another `scaling` string; a tag other than those.

### Fixtures

`{"schema": "vftv:foci_fixtures/1", "cost": [...], "quote": [...], "magnitude": [...], "burst": [...], "beam": [...], "push": [...]}`. A `beam` row's `ticks` is the **closed interval** `[first, last]` of ticks on which `onUseTick` runs, one call per tick, both ends included. Each section transcribes one of this spec's vector tables, which an independent reference computed, so the build-phase unit tests check the `model` code against numbers it didn't produce. One entry of each:

```json
{"schema": "vftv:foci_fixtures/1",
 "cost":      [{"effect": {"vftv:aqua": 4, "vftv:ordo": 2}, "medium_pm": 2500,
                "modifiers": [["vftv:breadth", 1500, 2], ["vftv:force", 1300, 1]],
                "expect": {"vftv:aqua": 30, "vftv:ordo": 16}}],
 "quote":     [{"base": {"vftv:aqua": 30, "vftv:ordo": 16}, "cap": 850, "discount": 0, "w": 1250,
                "expect": {"m": 1063, "effective": {"vftv:aqua": 32, "vftv:ordo": 18}}}],
 "magnitude": [{"base": 4000, "force": 3, "step_pm": 250, "w": 250, "void": true,
                "per_warp_pm": 20, "max_bonus_pm": 1000, "cap": 40000,
                "expect": {"power_pm": 1750, "amp_pm": 2000, "out": 14000}}],
 "burst":     [{"centre": [-1, -60, 1], "r2": 4, "expect": {"count": 33, "first": [[-1, -60, 1], [-2, -60, 1]], "last": [0, -59, 2]}}],
 "beam":      [{"start": 103, "pulse": 10, "ticks": [103, 124], "expect": [103, 113, 123]}],
 "push":      [{"kind": "gust_fallback", "yaw": 0.0, "expect": {"arg": [0.0, -1.0], "moves": [0.0, 1.0]}},
               {"kind": "undertow", "velocity": [2.0, 0.0, 2.0], "v": [3.0, 0.0, 4.0], "pull": 1000,
                "expect": [2.041353, 0.0, 2.19838]}]}
```

Decimal expectations compare to within 0.000001. **Rejected:** a section missing; a row of a vector table missing or altered.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content tickets write the final text. The two Void effects' `focus_effect.*` keys live only in `foci_void.json`, in Void-tongue, copied from the lexicon's `names` ([warp spec, The lexicon](warp.md#the-lexicon)), with no provisional English.

| Key | Args | Sense |
|---|---|---|
| `item.vftv.focus`, `item.vftv.focus.blank`, `block.vftv.lathe`, `entity.vftv.focus_bolt` | 0 | "Focus", "Blank Focus", "Lathe", "Bolt" |
| `focus_effect.<ns>.<path>`, `focus_medium.<ns>.<path>`, `focus_modifier.<ns>.<path>` (every entry) | 0 | "Kindle", "Burst", "Force" |
| `container.vftv.lathe`; `gui.vftv.lathe.turn`, `.raise_rank` | 0 | |
| `status.vftv.lathe.<status>` for every `LatheStatus` with its own key | as its table | |
| `tooltip.vftv.focus.parts` | 2 | "%s by %s" (effect, medium) |
| `tooltip.vftv.focus.modifier` | 2 | "%s %s" (modifier, level) |
| `tooltip.vftv.focus.rank` | 2 | "Rank %s of %s" |
| `tooltip.vftv.focus.cooldown`, `.range`, `.radius`, `.pulse`, `.magnitude` | 1 | |
| `tooltip.vftv.focus.amplified` | 1 | "Strengthened ×%s by what hears me" |
| `tooltip.vftv.focus.unlearned` | 1 | "I don't yet know %s." |
| `tooltip.vftv.focus.exchange_warning` | 0 | "If the new block can't be set, the old one is broken all the same." |
| `tooltip.vftv.focus.blank`, `.unreadable`, `.out_of_tune` | 0 | |
| `tooltip.vftv.focus.unknown` | 1 | "It rings in a key I don't know: %s" |
| `tooltip.vftv.lathe.rank_xp` | 2 | "Costs %s levels (you have %s)" |
| `message.vftv.focus.unreadable`, `.blank`, `.out_of_tune`, `.unlearned`, `.needs_block`, `.block_effects_off`, `.nothing_answers`, `.nothing_yields`, `.too_much`, `.slipped` | 0 | the refusals above |
| `message.vftv.focus.unknown` | 1 | |
| `narration.vftv.lathe.choice` | 3 | |
| `narration.vftv.lathe.socket` | 3 | |
| `focus.vftv.kind.ordinary`, `.void`; `focus.vftv.fact.yes`, `.no`, `.no_focus`, `.not_applicable`, `.blank`, `.unreadable`, `.out_of_tune`, `.priced`, `.ready` | 0 | |
| `focus.vftv.fact.unknown` | 1 | "a part I don't know: %s" |
| `death.attack.vftv.kindle`, `.focus`, `.unmaking` | 1 | |
| `death.attack.vftv.kindle.player`, `.focus.player`, `.unmaking.player` | 2 | |
| `subtitles.vftv.<event>` for every sound event | 0 | |

```json
{"schema": "vftv:lang/1", "strings": {
  "item.vftv.focus": "Focus",
  "focus_effect.vftv.kindle": "Kindle",
  "tooltip.vftv.focus.parts": "%s by %s",
  "message.vftv.focus.needs_block": "This working needs something solid. Crouch and touch it.",
  "subtitles.vftv.focus.cast_bolt": "Focus casts"
}}
```

**Rejected:** a key outside the table's patterns; an entry key with no matching entry; a required key missing in `en_us`; a placeholder count that differs; an empty string; a duplicate key; an English `focus_effect.vftv.unmaking` or `.undertow` in `foci.json`.

## Events

NeoForge post-events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server through the platform seam. Their payload records live in `api`. The payload shape, immutability and failure rules are the [API spec's](api.md#events). **`FocusAffectEntityEvent` fails closed:** a throwing listener is logged, the working ends where it was with no refund of the vis already paid, and the cooldown, notifications and guard release still run ("Failure after the spend").

| Event | When | Payload |
|---|---|---|
| `FocusEvent` | after a committed cast (a launch, a Touch, a Burst, or each Beam pulse that paid), compose or rank-up | kind `CAST` \| `COMPOSED` \| `RANKED`; player; rank; modifier ids; and the effect id, medium id and focus kind, each **absent** for a blank focus (a blank can be ranked); for `CAST`: the gesture, the medium, and **`attempted`**, the number of targets or positions resolved before the spend (0 for a Bolt, which is a launch) |
| `FocusAffectEntityEvent` (cancellable) | before each entity is affected (at most 16 per Burst, 1 per Touch, pulse or bolt impact) | caster; target; effect id; harmful |

- **`CAST` reports a paid attempt, not what was affected.** A bolt hits later, if at all, and an Exchange can fail after its break. No sibling needs a post-impact event, so none is posted.
- `VisSpentEvent` comes from `spend`, and [Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26) generates its flux on it ([Hush spec, Sources](hush.md#sources)).
- Nothing fires for a refusal, an attach or detach, a fizzle or a beam that pays nothing.

**Plain `core` services,** implemented in `core`: `FocusService.kind(stack)`, `FocusService.composition(stack)`, `FocusCost.base`, `FocusMagnitude.of`, `FocusRules.valid`, `FocusPreview.of`. `api` cannot depend on `core` or hold implementations ([architecture, Public API](architecture.md#public-api)), so any public facade would be an interface in `api` that `core` implements. **There is none in v1** ([API spec, Services](api.md#services)): all six stay in `core`.

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| a focus's composition and rank | the `vftv:focus` component | as items are |
| a wand's focus | the `vftv:wand_focus` component | as items are |
| registries | the three datapack registries | every player, through their network codecs |
| foci rules | the tuning record and the fixed limits | **`FociRules`**, S2C to every player through `OnDatapackSyncEvent` (join and `/reload`) |
| a beam in progress | the session, in transient per-player memory | **`FocusBeamState(entityId, active)`**, S2C (below) |
| bolts | `vftv:focus_bolt` entities (`noSave`) | trackers, by vanilla entity tracking plus one `INT` tint |
| cooldowns | vanilla `ItemCooldowns` | the owner, as vanilla does |
| experience after a rank-up | the player | the owner, by vanilla's experience packet, sent by the spend's commit ([wands, The spend](wands.md#the-spend)) |
| the operation guard, bolt counts | transient per-player memory | never |
| the Lathe menu | the menu's slots and three data slots | the owner, as menus are |

- **The beam-state signal.** `FocusBeamState(entityId, true)` is sent with `PacketDistributor.sendToPlayersTrackingEntityAndSelf` when a beam session starts. `false` is sent on **every** stop: release, a failed pulse check, logout, death or a dimension change, through the session store's one clear path. A player who starts tracking a beaming player receives `true` from the start-tracking event. The client keeps a set of beaming entity ids, cleared on disconnect and level change. The wands spec's session store exposes its session kind (tap or beam) for exactly this signal; taps send nothing.
- **Payloads:** two S2C (`FociRules`, `FocusBeamState`) and one C2S (`LatheAction`). Every input to a cast arrives through vanilla's use packets, and attaching through its container clicks.
- **Formats:** `vftv:focus` and `vftv:wand_focus` carry `format: 1`. A record with an unknown format, or one out of its format bounds, is kept as its raw tag and written back unchanged with one log line. Decoding checks format bounds, never current tuning or registries.
- **Bounded codecs:** 3 modifier entries; levels 1–3; rank 0–3; ids ≤ 256 characters; one focus stack per wand; `FociRules` a fixed record; `LatheAction` as tabled.

**Pack changes:**

| Change | Effect |
|---|---|
| An entry removed | foci naming it are **inert**: kept verbatim, refused at casting with its id named, and drawn with the `unknown` layer. Restoring the pack restores them. The Lathe can recompose them, keeping the rank. |
| An entry's `media`, `max_level` or applicability narrowed | the focus is **out of tune** until recomposed |
| A cost or param changed | priced and applied by the new data at the next cast; a bolt in flight keeps its snapshot |
| A tuning `/reload` | a new `FociRules`; Lathe revisions bump; previews and facts re-resolve |
| Registries | need the world reopened |
| Resource reload | models, sprites and names rebuild |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/foci.json`**, loaded on reload into an immutable record in `core.focus` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid, and so is a rank `research` that names no entry. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client receives the whole record in `FociRules`.

| Key | Value | Range | Notes |
|---|---|---|---|
| `void.per_warp_pm` | 20 | 0–1000 | amplifier per warp point of `amplifier_warp`, per mille: 2% each, so ×2.00 at 50 warp, just past the warp spec's third band (the scale the warp spec set; not proven balance) |
| `void.max_bonus_pm` | 1000 | 0–4000 | the amplifier's ceiling above 1000 (×2.00 at the default) |
| `ranks.1`, `ranks.2`, `ranks.3` | the "Ranks" table | `xp` 0–100; `vis` 1–8 vis aspects of 1–1000; `research` an existing entry | |

**Other homes:**
- effect, medium and modifier numbers, **including Force's `step_pm`**: their registry entries (`content/foci/effects.json`, `effects_void.json`, `media.json`, `modifiers.json`), fixed when a world opens;
- damage types: `data/vftv/damage_type/*.json` (`content/foci/damage_types.json`);
- the Lathe's block properties (hardness 2.5, axe-mineable): its block registration, fixed;
- `foci.block_effects` (default on): server config, an admin switch ([architecture, Data and code](architecture.md#data-and-code)), checked for `BLOCKS` effects at cast check 7 and at every Beam pulse.

**`FocusLimits` (in `api`)**, fixed limits in code (formats and invariants, not balance): `MAX_RANK` 3; 3 modifier entries; levels 1–3; 64 effects, 16 media, 32 modifiers; costs 1–1000; `cost_pm` 100–4000; ranges ≤ 16 blocks; radius ≤ 6; `block_radius_sq` ≤ 16; `MAX_BURST_ENTITIES` 16; `MAX_BURST_BLOCKS` 27; `MAX_LIVE_BOLTS` 8; bolt speed ≤ 3.0 and lifetime ≤ 100; Beam pulse 5–40; `GUST_MIN_DISTANCE` 0.0001 and `UNDERTOW_MIN_DISTANCE` 0.5 blocks; the Undertow speed cap 3.0; the Beam's block ray ≤ 16 blocks; scaled params ≤ 20,000; magnitude caps 40,000 (health), 3000 (movement), 1200 (ticks); `w` clamped to 0–10,000; ids ≤ 256 characters; at most 1 `LatheAction` per player per tick. **Fixed feedback constants:** 6 Touch and 24 Burst particles; 1 trail particle per bolt per 2 ticks within 32 blocks; the cap of 64 live trail particles; the 40-tick Beam drone; the bolt's 4-frame, 2-tick animation.

## Performance

**Heavy-base workload:**
- one player firing Kindle bolts at the cooldown, with 8 live;
- one player beaming Delve into a stone face that the fixture refills;
- one player casting Burst Rime (Breadth 2, Force 1) every cooldown into a pen of 40 mobs;
- one player at the Lathe sending a `LatheAction` every tick, alternating compose and rank-up, with the fixture restoring experience, vis and rank;
- a fixture keeping 32 bolts from 4 more players alive;
- **Client:** 40 bolts and 2 beams in view, and the Lathe screen (288×234) open with 6 effects, 4 media and 3 filled sockets.

**Share:** server ≤ 0.03 ms mean, ≤ 0.15 ms p99, measured in the combined stress world, including the casts, the entity and block calls, events, packets and the Lathe. Client: 0 with no bolt or beam in view and no Lathe screen; ≤ 0.05 ms p50 with the workload's view, including the renderers and the screen. **Measured separately as load scenarios** (reported):
- **density:** a Burst into a pen of 400 living entities, half of them ineligible (PvP-protected players and the caster's team);
- 100 players each casting a Burst in one tick at one pen;
- a Burst Exchange of 27 positions placing beds (multi-block placement);
- a `LatheAction` flood at the rate cap.

**Hot paths:**
- **per bolt tick:** vanilla movement over ≤ 3 blocks and one move-vector sweep;
- **per Beam pulse:** one view raycast of ≤ 16 blocks (the entity ray, or the block ray capped at 16 even when a modded `block_interaction_range` is larger), then ≤ 1 affected entity, or ≤ 1 break;
- **per Burst:** **the native candidate term.** `getEntities` with its limit visits the `LivingEntity` members of the entity sections overlapping a box of side ≤ 12, calling the predicate on each until 16 are collected (`MC:world/level/Level.java:781–805`). That is **O(living entities in those sections)**, a density-dependent engine term this spec does not bound, disclosed here and measured by the density scenario. After it: ≤ 16 `FocusAffectEntityEvent`s and applications; for blocks, ≤ 729 offsets generated and sorted, ≤ 27 positions × (1 break + 1 placement wrapper call, each of which may run foreign listeners and a multi-block placement);
- **per Touch:** one view raycast with vanilla's entity sweep (`MC:world/entity/projectile/ProjectileUtil.java:143–150`), whose candidates are the entities in the swept box: the same native term, bounded by `range` ≤ 16;
- **per cast:** the cost over ≤ 8 aspects × ≤ 10 multiplications, one quote and one spend;
- **per `LatheAction`:** validation over ≤ 112 registry entries; a rank-up's one spend.
- **Allocation:** **zero** in the validity, cost and magnitude kernels and in the burst-offset sort over a preallocated array (allocation-budget tests). The mutation step is finite, not zero: one component per changed stack, one entity per launch, the engine's own lists in `getEntities` and the raycasts. Zero per frame in our renderers and in the screen's draw code.
- **Packets:** one entity spawn per bolt plus vanilla tracking; one `FocusBeamState` per beam start and stop per tracker; vanilla particle packets at the fixed counts; one `FociRules` per player per join or reload.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **cost:** every vector above; the ceiling; ascending-id modifier order; no overflow at `cost_pm` 4000 and 3 levels;
- **magnitude:** every vector, including both caps and the tuning extremes, whose raw product stays below 2⁶³; `w` below 0 and above 10,000; an ordinary effect ignoring warp;
- **validity:** every rule and its negation, including Force with Delve and with Exchange refused, Reach with Burst refused, **Reach with Delve by Touch or Beam refused**, Breadth with Touch refused, and **two entries of one modifier type refused** (two pack Force entries, each valid alone); socket aggregation (two Force sockets equal Force 2) and the socket cycle never offering a second entry of a held type;
- **burst order:** every vector, with negative coordinates; the 27 cap; R² 0 (the centre only);
- **beam schedule:** the vectors; the start tick never pays twice;
- **push and pull:** the Gust fallback and Undertow vectors, including the whole-vector speed clamp and the minimum-distance cases;
- **facts:** every key of every `focus*` source renders in its frozen format for a representative entry; a key a type doesn't have renders `not_applicable`; a blank, unreadable, unknown and out-of-tune reference focus render their status texts without composition arithmetic; the representative spans under "The authoritative preview" pass the validator;
- **load validation:** each bad entry refused (a compound cost key, a `BLOCKS` effect allowing a Bolt, a non-finite or out-of-range param, an unknown field, too many entries);
- **codecs:** both components, `FociRules`, `FocusBeamState` and `LatheAction` (sizes checked before allocation, duplicate keys, oversized ids), with defaults, ranges, format 1 and the raw-tag fallback;
- **fixtures:** every section of `content/foci/fixtures.json`, checked against the `model` code;
- **every `content/` rejection rule** above.

**GameTest:**
- the registries and tuning load, and each bad entry and file is refused with a clear error;
- **attaching:** attach, swap and detach by click; refused on a crafting result, the Worktable's output and a locked slot; an unreadable `vftv:wand_focus` never detaches; the cursor and the component change together;
- **gestures:** each cast check refuses, consumes the click (no air use follows) and changes nothing; the off hand passes; a sneaking click on a node still taps; a non-sneaking click on a chest opens it; a `BLOCKS` effect on the entity gesture is refused; Kindle Burst from the block gesture needs no harvesting filter; `foci.block_effects` off refuses Delve at the start and stops a running Delve Beam at its next pulse, while Kindle still casts;
- **cooldown:** it covers every wand; the block gesture honours it; **an effect that throws still sets the cooldown and clears the guard**; a `VisSpentEvent` listener that casts again is refused by the guard;
- **spend:** a short cast names its aspects; **a `VisSpentEvent` listener that teleports the caster to another dimension, or a few blocks within the same level, or replaces the main-hand wand, makes the working slip**;
- **rechecks:** a `FocusAffectEntityEvent` listener that teleports the caster abandons the remaining Burst targets; one that moves a target out of the Burst skips it; a damage listener that kills or moves a Kindle target suppresses its fire; a `BreakBlockEvent` listener that teleports the caster within the level, changes their dimension, or swaps either hand stops the rest of a Burst Exchange before the next placement;
- **Beam:** **exactly one debit at a targeted start and none at an empty start**; pulses and spends per interval; an `ENTITIES` Beam started with the block gesture uses the entity ray; stops on a slot change, a swap to an identical wand, a focus swap mid-use, revoked research and running short; no target, no spend; a `FocusEvent` listener that tries to cast during a pulse is refused by the guard; **a pulse whose application throws ends the session, clears the signal and sets the cooldown once**; **a Beam-focused wand tapping a node draws no beam on a second client**, and the beam signal clears on every stop path;
- **Bolt:** the cap discards the oldest; a cancelled `ProjectileImpactEvent` discards it with no effect; it is discarded after a processed hit; the owner logging out or changing dimension makes it fizzle; it never persists across a save;
- **Burst:** 16 of 40 mobs, in id order; PvP-protected players don't use up places; **a mob behind a stone wall inside the radius is affected** (bursts pass through walls, deliberately);
- **PvP:** with PvP off, every harmful effect (Gust and Undertow included) spares players; teams; creative and spectator players skipped; Mend affects anyone;
- **effects:** Kindle on a fire-immune mob neither damages nor ignites it; Rime's slowness only when damage is dealt; Gust and Undertow at a zero vector;
- **block work:** a cancelled `BreakBlockEvent` skips that position while the others proceed and the vis stays spent; spawn protection, adventure mode (through the `blockActionRestricted` pre-filter), air, `vftv:focus_immune`, obsidian and bedrock refused before the spend; double blocks; Delve drops match an iron pickaxe's eligibility, with a `HarvestCheck` listener overriding it; **a `destroyBlock` that returns true without removal places nothing**; a redirected `BlockPlaceContext` places nothing; a cancelled `EntityMultiPlaceEvent` for a bed restores both halves and the off-hand count, and the broken block stays broken; **an exception during a cancelled placement's restoration leaves both `captureBlockSnapshots` and `restoringBlockSnapshots` at their saved values, and an ordinary placement afterwards works and is protected as usual**; the adapter refuses entry while another capture or restoration is active and leaves its snapshots alone; Delve on a crucible spills as the crucible's removal does; Delve on a claimed pedestal follows infusion's removal;
- **the Lathe:** every check; a stale revision; a forged echo; out-of-range and duplicate modifiers; unlearned parts; `UNCHANGED`; a retry after `STALE` and after `SHORT` without another slot change; **rank-up atomicity:** vis, slot and level together, a throwing slot change rolling all three back, a throwing `VisSpentEvent` listener (`COMMITTED_NOTIFY_FAILED`), a cancelled `PlayerXpEvent.LevelChange` (`XP_REFUSED`), a listener that changes the levels (`XP_REFUSED`), **a hook listener that drains 10 levels to 8 for a 4-level rank (`STALE`, nothing charged)**, a hook listener that closes the menu or swaps slot 0 or 1 (`STALE`), a drain between the recheck and the commit (`STALE` at step 5), **a rank of 0 levels (no hook, no experience change)**, infinite materials (no experience change), and **the owner's client showing the debited level, progress and total** after a committed rank-up;
- **the Void tier:** magnitude at `w` 0 and 37 through a stub;
- save and reload of both components and of dormant raw tags;
- every test id in the discovery guard.

**E2E:**
- the Lathe screen in every status at **320×240**, with every slot, strip, socket, button and the hotbar on screen; keyboard-only use with narration and the focus ring on each region; long-text tooltips staying inside the view; and the worst-case mockup (8 cost cells with 4-digit amounts, a long translation);
- `focus*` fact spans in a book page re-resolving live: on a hand change, a wand's pools, a `block_interaction_range` change, a `/reload` and a reconnect;
- tooltips for every effect, a blank, an unreadable and an out-of-tune focus;
- the wand's third layer for every effect, blank, unknown, unreadable and none, beside the focus items' own models;
- bolts, beams, burst particles, sounds and subtitles, seen by a second client;
- a Stage 1 flow: learn `vftv:foci`, craft a Lathe and a blank focus, compose Kindle by Bolt, attach it, and cast at a zombie;
- sync on join, reconnect and `/reload` (`FociRules`, facts, the beam signal);
- the asset, lang and model validators: every key under "Lang keys", every sound event and subtitle, every case of `items/wand.json` and `items/focus.json`.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)'s contracts on this ticket):

| Wands contract | Met by |
|---|---|
| A focus is its own component on `vftv:wand`; #22 adds the third `select` layer to `items/wand.json` and its assets | "On the wand": `vftv:wand_focus` holding the attached stack; the third member, a select on `vftv:focus_effect` with `vftv:none` → `minecraft:empty` and `unknown` as the fallback, appended after #38 and #47; `items/focus.json`; the Foci assets ticket |
| Every cast spends through `VisService.spend` with the focus id as `source` and the focus's `kind` | "The cost": `source` is the **effect's id** (a focus has no id of its own; the wands spec records this reading in the same change), `kind` the effect's, `research` the effect's; the medium's and modifiers' research checked before |
| A cast short of vis does nothing and names the short aspects | "Checks", check 9: `message.vftv.spend.short` in the wands spec's order, before anything is applied |
| A held cast spends per pulse at an interval it states | "Beam": no one-shot spend; the single pulse procedure pays, at the start tick and then every `pulse` (default 10) ticks, recording the pulse tick before any foreign code, so the start tick never pays twice; no target, no spend |
| Foci never draw from nodes or charge wands | No such path: tapping stays first on nodes, and vis leaves only by `spend` ("Failure after the spend") |
| Use on air or an entity casts; sneak-use on a non-node block is the block-targeted path; use on a node always taps; a refused cast consumes the click | "Entry points" (verified against `MC:client/Minecraft.java:1736–1806` and `MC:server/level/ServerPlayerGameMode.java:383–418`), with gesture and target domain separated, and every refusal returning `FAIL` |
| Effects that aren't slot changes run after the spend; the consumer checks first; #22 states what its casts do on a later failure | "Checks", "The cast" (the cast pin and caster check after the spend and between our callback-bearing operations, target rechecks after every listener, the operation guard, the cooldown in a `finally`), "Failure after the spend", "Block work" (revalidation before every position) |
| Later tiers append cases to `items/wand.json`, keeping the foci layer | "On the wand": additive cases; the appends are serialised #38 → #47 → Foci assets |

**Met here** (the [Arcane crafting spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)'s contracts on this ticket):

| Arcane contract | Met by |
|---|---|
| Any recipe made at the Worktable is a `vftv:arcane` body with its own `research`; `kind: void` only behind a Stage 4 gate | "Recipes": `vftv:lathe` and `vftv:blank_focus`, both `ordinary`, gated by `vftv:foci` |
| A recipe needing warp on craft proposes an extension | none applies warp |
| Stage 1 arcane recipes meet the Stage 1 affordability test | both are capped at 32 per aspect by a content rejection rule, with pre-iron ingredients from the reviewed list; Stage 1 casts cost at most 4 base per aspect |

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts, which bind Foci through the arcane block for its own recipe types):

| Research contract | Met by |
|---|---|
| **Gate:** `research` on each gated recipe; the server refuses every processing and output path until it is learned; a GameTest with an unlearned player | Foci has no recipe type of its own. Its two recipes carry `research`; composing, ranking and casting are gated per part on the server at the action ("Stage"), each with an unlearned GameTest |
| **Recipe-page validator** | both recipes sit on `vftv:foci`'s page (handed to #34) |
| **One authoritative cost preview**: base and effective vis, spill and every decision cost, capped modifiers flagged; it backs pages and facts | "The authoritative preview": `FocusPreview` with the complete quote (spill and every flag), typed contexts and absence, live dependencies, and the `focus*` fact sources (the research spec amended) |
| **JEI** by the learned gate | the two recipes ride the arcane spec's JEI; composing is not a recipe |
| The Stage 2–4 content tickets are blocked by Foci | "Stage": five entries with their gates; #34 now consumes `vftv:foci` too (the research spec amended) |

**Met here** (the [Crucible alchemy spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)): a sneaking wand click on a crucible reaches Foci's block-targeted path. That is "Entry points", and a Delve or Exchange there breaks the crucible through the player mining path, so the crucible's own removal spill applies.

**Met here** (the [Infusion spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)'s contracts on this ticket):

| Infusion contract | Met by |
|---|---|
| A sneaking wand with a focus on a bowl or pedestal is Foci's block-targeted cast | "Entry points"; anything a Delve breaks follows infusion's removal and claim rules, through the player mining path |
| Its infusion recipes use the infusion body with their own `research`; `kind: void` built-ins need a Stage 4 gate; runic augments and the like are extensions they propose | Foci ships no infusion recipe; any later one is bound by this |

**Met here** (the [progression spec](progression.md)): composable foci (effect + medium + modifiers) on a simple GUI ("Composition", "The Lathe"). Void-tier foci grow stronger with current warp, bounded and in magnitude only, while ordinary magic pays warp in vis ("The Void tier", "The cost"). Every number a decision rests on is exact ("The authoritative preview"). Every Stage's content is opened by that Stage's find ("Stage").

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)** (amended in this change)
- The components table names `vftv:wand_focus`; `items/wand.json` gains the third member, with `vftv:none` mapped to `minecraft:empty` and `unknown` as the fallback; later appends are additive and serialised.
- **The spend's prepared state** gains a typed **experience change** `(player, expectedLevel, after)`, prepared against the level the consumer snapshotted before resolving `PlayerXpEvent.LevelChange`, checked in step 5, written and restored in step 6, posting nothing, omitted for a zero price, and followed on commit by vanilla's experience packet to the owner.
- The wand's use callbacks dispatch to the player's **one session**, a tap or a beam, and the session store exposes its kind for the beam-state signal.
- Casting is main hand only. `source` for a cast is the effect's id.
- The wand's `isCorrectToolForDrops` answers for an attached harvesting focus.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 1](https://github.com/andrewbrogoff/voices-from-the-void/issues/34), [Stage 2](https://github.com/andrewbrogoff/voices-from-the-void/issues/35), [Stage 3](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) and [Stage 4 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/37):
- **Entries:** the five `note` entries under "Stage", with their parents and gates. `vftv:foci` carries `vftv:recipe` pages for `vftv:lathe` and `vftv:blank_focus`.
- **Fact sources:** `focus`, `focus_effect`, `focus_medium`, `focus_modifier`, `focus_rank` and `focus_rules`, as under "The authoritative preview", with their typed absence and live dependencies.
- **Prose** teaches the two gestures (and that only the crouched one changes the world), the free Lathe and its ranks, the experience price, the warp dial on both kinds, that nothing is ever refunded, and that an Exchange can break without placing. Every number goes in a `focus*` or `vis` fact span.
- **The affordability artifact:** `path-stage1.json` learns `vftv:foci` after `vftv:arcane_crafting`.

**[Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)**
- It supplies **`amplifier_warp(player)`**: a pure, finite, non-negative integer, synced to the owner and identical on both sides, that measures the warp counting toward Void power. It decides the unit and which kinds of warp count, and tests its accepted scale against `void.per_warp_pm`. It is 0 until it lands.
- `vis_factor(player, VOID)` is as already contracted.
- It decides whether a carried Void focus is gear warp.
- Casting applies no warp. Warp on a cast would need #25 to propose a previewed, echoed extension.
- *Met by the [warp spec](warp.md#the-dials): `amplifier_warp` is total warp in warp points (temporary, lasting, permanent and gear), plus the Joining bonus for a Joined Listener (amended with the [Eldritch tier](eldritch.md#the-capstones)), with the scale tested at the amended default of 20 per mille (the vectors and fixtures above); `vis_factor` is 1.0 for `VOID`; a carried Void focus is **not** gear warp, a balance choice; no cast applies warp.*

**[Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)**
- Foci add **no direct flux producer** of their own: their flux is the `spill` applied on `VisSpentEvent`.
- **Indirectly,** a Delve or Exchange that breaks a crucible, a jar or a ringing bowl triggers that sibling's own once-only spill event, unchanged.

**[Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24)**
- Cast discounts come only from `vftv:vis_discount`. No gear changes a focus's magnitude in v1.
- *Met by the [armor spec](armor.md#vis-discount): every discount is a `vftv:wearable` modifier on that attribute, and the Void set's warp amplifier scales only its Baffle.*

**[Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- `vftv:void_foci` follows its opener `vftv:choirs_road`. Scaling stays #25's.
- The names of `vftv:unmaking` and `vftv:undertow` (`focus_effect.vftv.unmaking`, `.undertow`) come from the **Void-tongue lexicon** ticket ([warp spec, Production tickets](warp.md#production-tickets)), which delivers them ahead of #28's own completion; #28 builds its eldritch names on the same lexicon.
- *Met by the [Eldritch tier spec](eldritch.md#stage): `vftv:void_foci` hangs off `vftv:choirs_road`, which it ships; scaling stays the warp spec's, whose amplifier now adds the Joining bonus (amended above); every eldritch name of its own uses existing lexicon words.*

**[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)**
- No golem casts or uses the Lathe in v1. Golems may move foci and wands as items.

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- Hand entries (or reviewed exemptions) for `vftv:lathe` and `vftv:focus`; round-trip non-amplification over both arcane recipes, with vis counting as no material input. Ranks and compositions add no aspect value (components are ignored, [aspects, Coverage and supply](aspects.md#coverage-and-supply)).

**Configuration**
- The admin switch `foci.block_effects` (default on), applied to `BLOCKS` effects at the start of a cast and at every Beam pulse.

**API**
- `FocusKeys` (with the `#vftv:focus_immune` tag key), `FocusLimits`, the marker interfaces `FocusEffect`, `FocusMedium` and `FocusModifier`, and the payload records of `FocusEvent` and `FocusAffectEntityEvent` live in `api`. `FocusService.kind` and every other service stay in `core`. The three datapack registries are public data formats; the three behaviour-type registries are internal in v1 ([API spec](api.md#code-extension)).

## Production tickets

This spec settles every decision. Three required child tickets deliver the assets and the authored content, and **the system is not done until all three land**. **None of them writes mod code:** no block, menu, screen, entity, renderer, payload, registry, item-model property, platform adapter, datagen provider or validator. The asset ticket delivers a generator (authorised tooling under `tools/art/`) and assets under the [art style](art-style.md); the content tickets deliver authored inputs under `content/`, in the schemas above. The tags, loot table, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Foci assets** | **Generator:** `tools/art/foci.py`.<br>**Focus items** (16×16): `assets/vftv/textures/item/focus/blank.png`, `unknown.png` and one per built-in effect (`kindle`, `gust`, `rime`, `delve`, `mend`, `exchange`, `unmaking`, `undertow`). Each is a small brass bell-disc carrying an essentia bead tinted by its effect's aspect family ([art style §2](art-style.md#2-palette-locked-project-wide-48-colours) allows aspect families on essentia tints), in the Stage row of its research ([art style §5](art-style.md#5-tone-ramp-by-stage)); the two Void foci in the Stage 4 row, teal inside void ink. With item models `vftv:item/focus/<id>` (parent `minecraft:item/generated`) and **`assets/vftv/items/focus.json`** exactly as under "On the wand".<br>**Wand layers:** `assets/vftv/textures/item/wand/focus/<id>.png` for the same ten ids, the focus seated above the top cap, with layer models `vftv:item/wand/focus/<id>` in the wands spec's layer form, and the **appended** `assets/vftv/items/wand.json` (the third member, every existing case kept).<br>**The Lathe:** `assets/vftv/models/block/lathe.json` with at most 6 pixel-aligned cuboids and no rotations ([art style §6](art-style.md#6-blocks-and-models)): a base [1,0,2]–[15,4,14], a headstock [2,4,5]–[5,12,11], a tailstock [11,4,6]–[14,10,10], a spindle [5,7,7]–[11,9,9], a bell blank on it [7,6,6]–[9,10,10] and a tool rest [6,4,11]–[10,6,13]. Textures `lathe_side.png`, `lathe_top.png`, `lathe_bottom.png`, `lathe_front.png` (Stage 1 row; the teal tick on the bell blank's top); a blockstate with 4 horizontal facings by rotation; the item definition `assets/vftv/items/lathe.json`.<br>**GUI:** `assets/vftv/textures/gui/container/lathe.png` (288×234 used on a 512×256 sheet, every well, strip, socket row and panel at the positions under "The screen"), and every sprite listed there.<br>**Entity:** `assets/vftv/textures/entity/focus_bolt.png` (16×64, 4 frames, cutout, greyscale for tinting) and `focus_beam.png` (16×16, tiling, greyscale).<br>**Sounds:** every file and `sounds.json` entry under "Rendering and sound", with its category, volume, pitch and subtitle rules, ≤ 3 s with faded tails.<br>**Review:** a contact sheet at GUI scale 2 beside vanilla's blaze rod, ender pearl and stonecutter, with every focus item and the 10 focused wand layers on a wooden-and-copper wand; a Blender render of the Lathe; **worst-case Lathe mockups** composed from the sprites and the vanilla font: 8 cost cells with 4-digit amounts and a short cell, the beyond glyph, an inert and a missing wand, an unlearned socket choice, `XP_SHORT` and `STALE`, the keyboard focus ring on each region, a long test translation and a long-text tooltip, all as the full 288×234 screen inside a 320×240 view with every slot, control and the hotbar visible. Provenance rows. | [Wand assets #38](https://github.com/andrewbrogoff/voices-from-the-void/issues/38) and [Infusion assets #47](https://github.com/andrewbrogoff/voices-from-the-void/issues/47): both write `items/wand.json` first, so the three appends are serialised |
| **Foci content** (Stages 1–3) | `content/foci/effects.json` (`vftv:focus_effects/1`, the six ordinary effects exactly as the roster), `content/foci/media.json` (`vftv:focus_media/1`), `content/foci/modifiers.json` (`vftv:focus_modifiers/1`), `content/foci/recipes.json` (`vftv:foci_recipes/1`: the two arcane bodies, with materials from the reviewed pre-iron list and costs ≤ 32 per aspect), `content/foci/damage_types.json` (`vftv:damage_types/1`), `content/foci/fixtures.json` (`vftv:foci_fixtures/1`, the vector tables transcribed), and `content/lang/en_us/foci.json` (`vftv:lang/1`) with every key under "Lang keys" except the Void effects' names. Its review checks that the messages teach the two gestures, the free Lathe, the experience price and the Exchange warning. | [Vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (the non-amplification lint replays its values) |
| **Void foci content** (Stage 4) | `content/foci/effects_void.json` (`vftv:focus_effects/1`, exactly `vftv:unmaking` and `vftv:undertow` as the roster, under the same merge rules) and `content/lang/en_us/foci_void.json` (`vftv:lang/1`: their `focus_effect.*` names in Void-tongue, with no provisional English) | the **Void-tongue lexicon** ticket ([warp spec, Production tickets](warp.md#production-tickets)) |

The research content tickets [#34](https://github.com/andrewbrogoff/voices-from-the-void/issues/34)–[#37](https://github.com/andrewbrogoff/voices-from-the-void/issues/37) consume the entry ids, recipe ids and fact sources frozen here.
