# Arcane crafting

Crafting that costs vis as well as materials: the **Worktable** (a crafting table turned with a wand), its one recipe type `vftv:arcane`, the cost it shows, the one path that commits a craft, and the authoritative preview that the book and JEI price from. Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). The code layout, data rules and sync rules come from the [architecture spec](architecture.md), every gate from the [quality gates](quality-gates.md), research gates, recipe pages and fact fields from the [research spec](research.md), the quote, the spend and the wand from the [wands spec](wands.md), and every asset from the [art style](art-style.md).

Resolved in [Arcane crafting spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/18). Inputs: [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. This system has no tuning file: every number is a recipe's own data or a fixed limit, each listed with its home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content ticket sets the real ones.

The wands spec owns the arithmetic. This spec never computes a cost: it asks `VisService.quote` and spends through `VisService.spend` ([wands, Spending vis](wands.md#spending-vis)).

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 1 Resonance | the Worktable, the `vftv:arcane` recipe type, and the tutorial recipe `vftv:bell` | the Stage is opened by the amethyst geode ([research, Stage](research.md#stage)). Inside it, the `note` entry **`vftv:arcane_crafting`** (already in the [research roster](research.md#roster), after `vftv:knots_in_the_air`) gates turning a table, the tutorial recipe and the [foci spec's](foci.md#stage) **Lathe** recipe `vftv:lathe`, so its page shows the Bell and the Lathe side by side and the first arcane craft can be the instrument. |
| 2–4 | nothing of its own | other specs' recipes ride the same type, each behind its own `research` (the wands spec's Stage 2 recipes; Void-tier recipes behind Stage 4 entries) |

- **Gate the craft, at the one place it happens.** Unlike the vanilla grid, the Worktable commits through a payload this spec owns, so the research gate is checked on the server **before any slot changes** (see "The craft payload"). An unlearned recipe is invisible at the station and in JEI: the Worktable never selects, previews or names it, and JEI hides it. The book keeps the research spec's authorised exception: a recipe page marked `preview: true` may show a forward-looking recipe as a locked preview ([research, Page descriptors](research.md#page-descriptors)).
- **Obtaining counts.** A Worktable can be broken and carried, or handed over. Using one needs no research; every craft at it needs that recipe's.

## The Worktable

### Turning a table

**Right-click a crafting table with any wand.** No vis, no cost, no warp: it is a ritual, not a spend.

**One server listener** on `PlayerInteractEvent.RightClickBlock`, at priority `LOWEST`, not receiving cancelled events, so a claim mod's cancel wins. It fires before the wand's `onItemUseFirst` and before the block's own use, after vanilla's spawn-protection and `mayInteract` checks. It **converts iff** all of these hold:
1. the event's hand holds a `vftv:wand`: focused, unfocused or inert, since no vis is involved;
2. neither `getUseBlock()` nor `getUseItem()` is `FALSE` (an earlier listener that denied either use is honoured);
3. the player is not sneaking (`isSecondaryUseActive()`), is not a spectator, and `mayBuild()`;
4. the block is in the block tag **`vftv:converts_to_worktable`** (default: `minecraft:crafting_table` only) **and has no block entity**, so a pack that tags a storage block can never delete its contents;
5. the player has learned `vftv:arcane_crafting`.

Otherwise the listener does nothing. An unlearned player's click opens the crafting table as usual, and a sneaking click falls through to the wands spec's focus path ([wands, Hand-tapping](wands.md#hand-tapping), interaction precedence).

**Commit,** on the server thread, in order:
1. Record the block state `original` at the position.
2. Post `BreakBlockEvent` for `original` with the player. **Cancelled:** cancel the interaction with `FAIL`; nothing has changed, and the claim mod speaks.
3. **Recheck,** since a listener may have changed the world: the state at the position is still `original`, there is still no block entity, the hand still holds a `vftv:wand`, and checks 3 and 5 still hold. Any failure: cancel with `FAIL`, nothing changed.
4. `setBlock(pos, vftv:worktable default state, UPDATE_ALL)`. If it returns `false`: cancel with `FAIL`; no sound.
5. Post the vanilla game event `BLOCK_CHANGE` with the player and the new state, play `vftv:worktable.convert`, and cancel the interaction with `SUCCESS`.

- **The protection promise is exactly these events:** vanilla's spawn protection and `mayInteract`, `RightClickBlock` (a cancel, or a denied use), and `BreakBlockEvent`. v1 has no integration with a named claim mod ([compatibility, Claim mods](compat.md#claim-mods)); a claim mod answers through these events.
- **The client** mirrors checks 1–5 from synced state and cancels with `SUCCESS`, so the arm swings and the table's own use doesn't run. The server decides; the block change arrives as an ordinary block update.

### The block

**`vftv:worktable`**: a plain block with **no block entity**, no ticker, no item capability and no `Container`.

| Property | Value |
|---|---|
| Behaviour properties | `BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE)`: the crafting table's map colour, instrument, strength, sound and lava ignition |
| Shape and render | a full cube; model `cube_bottom_top` |
| Block state | none (one default state); no facing |
| Use | `useWithoutItem` opens `WorktableMenu` through `getMenuProvider`, as the crafting table does |
| Item | the block item `vftv:worktable`, stacking to 64 |
| Comparator, redstone, random ticks | none |
| Piston push | normal (it holds nothing; a move closes open menus through `stillValid`) |

**Build-phase data** (datagen from code, not production deliverables): the block tag `vftv:converts_to_worktable` containing `minecraft:crafting_table`; `vftv:worktable` in `minecraft:mineable/axe`; the loot table `vftv:blocks/worktable` dropping itself (the vanilla `dropSelf` shape, explosion survival included).

### The menu

**`WorktableMenu`**, menu type **`vftv:worktable`**, is transient and per player. It is not a `RecipeBookMenu`, so vanilla's place-recipe packet is ignored for it.

**Containers,** owned by the menu and never by the block:

| Container | Size | Holds |
|---|---|---|
| grid | a 3×3 `TransientCraftingContainer` bound to the menu | any items |
| wand | a 1-slot container, max stack 1 | only `vftv:wand` |
| output | a 1-slot container | committed results |

**Slot indices** (frozen; tests and the screen use them):

| Index | Slot | Logical position (top-left of the 16×16 item) | Rules |
|---|---|---|---|
| 0 | wand | (8, 35) | `mayPlace` iff the stack is a `vftv:wand`; max 1. **Never charges** the wand. |
| 1–9 | grid, row-major | (30 + 18*c*, 17 + 18*r*) for column *c*, row *r* = 0…2 | any item |
| 10 | output | (134, 35) | `mayPlace` **false on every insertion path**; taking is free |
| 11–37 | player inventory, 3 rows | (8 + 18*c*, 136 + 18*r*) | vanilla |
| 38–46 | hotbar | (8 + 18*c*, 194) | vanilla |

**Data slots** (vanilla container data, each carrying 16 bits):

| Index | Holds |
|---|---|
| 0 | the last refusal code (see "Status and refusals"), 0 for none |
| 1 | the preview revision, low 16 bits |
| 2 | the preview revision, high 16 bits |

- **The output is ordinary committed inventory.** Items in it were paid for when they were made. Taking them by click, shift-click, number key, offhand swap, drag or double-click collect only moves them: it never spends, crafts or posts an event, and no path can insert into it.
- **Shift-click** (`quickMoveStack`): from the wand (0) or grid (1–9), to slots 11–46 in forward order; from the output (10), to 11–46 hotbar first (reverse order). From the inventory or hotbar: a `vftv:wand` goes to an empty wand slot; anything else goes to the grid (1–9), and failing that between inventory (11–37) and hotbar (38–46). No path moves anything into the output.
- **`stillValid`** is vanilla's block-and-reach check against `vftv:worktable` at the menu's position. Breaking or moving the table closes every menu on it.
- **Return on close:** `removed` returns the grid, the wand and the output to the player **exactly once**, through vanilla's container clearing: into the inventory while the player is alive and connected, otherwise dropped at the player (death, disconnect). Nothing stays in the world at the table.

### The screen

**`WorktableScreen`**, background **176×220** logical pixels, centred like vanilla container screens. One sprite texel is one logical pixel.

| Element | Size | Position | Notes |
|---|---|---|---|
| title | one line | (8, 6) | `container.vftv.worktable` |
| wand slot, grid, output | 18-px wells | as under "The menu" | the empty wand slot draws the outline sprite `vftv:container/slot/wand` |
| **preview** | 26×26 | (94, 31) | a widget, **not a slot**: it draws the would-be result and crafts on activation |
| **cost panel** | 2 rows × 4 cells, each 40×18 | (8, 74); cell *i* at (8 + 40·(*i* mod 4), 74 + 18·⌊*i*/4⌋) | one cell per base aspect, ascending aspect id |
| **status line** | 160×9 | (8, 112) | one line, clipped with "…" |
| inventory label | one line | (8, 125) | vanilla `container.inventory` |
| inventory, hotbar | | (8, 136), (8, 194) | vanilla spacing |

**Preview states,** first match wins:

| State | When | Sprite (26×26) | Result item |
|---|---|---|---|
| idle | the grid is empty | `vftv:worktable/preview_idle` | none |
| disabled | the **locally predicted** status is anything but ready (a shown server refusal alone never disables it) | `vftv:worktable/preview_disabled` | drawn if a recipe is selected, so the player sees what it would make |
| hover | ready, and hovered or keyboard-focused | `vftv:worktable/preview_hover` | drawn, with its count |
| ready | otherwise | `vftv:worktable/preview_ready` | drawn, with its count |

The result item is drawn at (5, 5) inside the frame. The preview's tooltip is the result's own tooltip plus `tooltip.vftv.worktable.preview`, then, when priced and the quote's spill is above 0, the **batch total** `tooltip.vftv.worktable.batch_flux` (*n*, *n* × spill), where *n* = min(`MAX_BATCH`, the crafts every pool of the live wand can pay at the current effective cost, the smallest occupied grid stack, the results the output slot has room for). It is an upper bound: a batch can stop early ("Batch"), never spill more. With the Bell at total warp 10 above, a full wand and full stacks: "A batch of 64 spills 320 flux."

**Cost cells** (40×18): the aspect's 16×16 icon ([aspects, icons](aspects.md#icons)) at (1, 1); the cost as text in the **number field x = 18…38**, right-aligned to x = 38 at y = 5, drawn at the scale min(1, 20 / its measured width) and centred on the unscaled line, so it never reaches the icon: 4 digits are 24 px in the vanilla font, and a resource-pack font may be wider. Only the number scales; the icon and sprite never do. The sprite is `vftv:worktable/cost_cell`, or `vftv:worktable/cost_cell_short` when the wand's pool is below the effective cost; `cost_cell_short` carries a 3 × 3 `ink1` notch in its top-right corner, so the state never rests on hue ([localisation, Colour and contrast](accessibility.md#colour-and-contrast)). Both sprites' number fields are uniform `brass5` under `ink1` numbers; the title and inventory label strips are uniform `brass4`, the status line's rectangle `brass5`, and the preview's count corner `ink1` under vanilla's white count ([localisation, Text contrast](accessibility.md#text-contrast)). A cost has at most 4 digits; a cost above 9,999 can only be an extreme that exceeds every rod's capacity, so the cell draws the 9×9 glyph `vftv:worktable/beyond` at (29, 4) instead. **The tooltip is always exact and unscaled.** This is the **shared cost-cell rule**: JEI's arcane category and the [crucible spec's](crucible.md#jei) JEI category draw their cells by it. With no wand or an inert wand, cells show the base cost on `cost_cell`.

**Cost tooltip** (on any cell), in order:
1. this aspect: `tooltip.vftv.worktable.cost` (aspect, effective, base) when priced, else `tooltip.vftv.worktable.base` (aspect, base) followed by `arcane.vftv.no_wand` or `arcane.vftv.inert_wand`;
2. when priced, the pool: `tooltip.vftv.worktable.pool` (current, capacity);
3. when priced, the multiplier's parts: `tooltip.vftv.worktable.factors` (cap, gear discount, warp, the result `m`), from the quote's resolved factors;
4. each flag that is set: `tooltip.vftv.worktable.discount_capped`, `tooltip.vftv.worktable.clamped`, and `status.vftv.worktable.exceeds_capacity`;
5. when the quote's spill is above 0: `tooltip.vftv.worktable.flux` (spill).

The tooltip wraps at 170 px ([localisation, R6](accessibility.md#r6-tooltips)) and fits 232 px tall at 320 × 240 under the localisation fixtures. **Each cell is a focus target:** on keyboard focus it shows this tooltip, and its narration reads every line of it, in this order, after its `narration.vftv.worktable.cost`.

**Number formats** (frozen; ASCII digits, `.` as the decimal point, in every language): costs, pools and spill as plain integers; cap, warp and `m` from per-mille *p* as *p*/1000 with 2 decimals, or 3 when the third is non-zero (850 → "0.85", 956 → "0.956", 1000 → "1.00", 10000 → "10.00"); the gear discount from per-mille *d* as *d*/10 with 0 decimals, or 1 when needed, plus "%" (300 → "30%", 125 → "12.5%").

**Status line:** the text of the current status (see "Status and refusals"), clipped to 160 px with a trailing "…" when it doesn't fit. Hovering or focusing the line shows its full text as a tooltip, and narration always reads the full text ([localisation, R2](accessibility.md#r2-one-line-fields)). It is an inspection focus target while it shows text.

**Controls:**

| Input | Effect |
|---|---|
| Left-click the preview | craft one |
| Shift + left-click the preview | craft a batch |
| Tab / Shift+Tab | cycle focus: the preview → each cost cell, in order → the status line (while it shows text) |
| Arrow keys | move between cost cells |
| Enter or Space, preview focused | craft one; with Shift held, a batch |
| Esc, inventory key | close (vanilla) |

The preview acts only when the local prediction is ready (see "Status and refusals": a shown refusal is feedback, not a lock), and sends **at most one `WorktableCraft` per client tick**; further activations in that tick are ignored, so the server's rate limit never drops a click the player made deliberately.

**Narration:** the preview reads `narration.vftv.worktable.result` (count, name), then one `narration.vftv.worktable.cost` per cell (aspect, cost, pool, or the base cost and the availability text when unpriced), then the full status line. A hovered or focused cell reads its own `narration.vftv.worktable.cost`, then its full cost tooltip (above). A disabled preview stays focusable and reads why through the status line. Vanilla's slots stay mouse-and-hotkey, as vanilla's are ([localisation, Screens](accessibility.md#screens)).

### Sound

| Event | Variants | Plays | Playback |
|---|---|---|---|
| `vftv:worktable.convert` | 1 | a successful turn | at the block's centre, to everyone in range; `SoundSource.BLOCKS`, volume 1.0, pitch 1.0 |
| `vftv:worktable.craft` | 3 | **once per craft payload** with at least one committed iteration | likewise |

Files `assets/vftv/sounds/worktable/convert.ogg` and `craft_0.ogg` … `craft_2.ogg`, in the Stage 1 register ([art style §7](art-style.md#7-sound-palette)): bright, warm, clean partials. `sounds.json` entries carry no volume or pitch overrides and name the subtitle keys `subtitles.vftv.worktable.convert` and `subtitles.vftv.worktable.craft`.

## Arcane recipes

### The recipe type

**One type, `vftv:arcane`** (recipe type and serializer share the id), shaped or shapeless, at most 3×3. Its body is also the body of every arcane recipe in `content/`, including the wands spec's Stage 2 recipes.

| Field | Required | Range | Meaning |
|---|---|---|---|
| `type` | yes | `vftv:arcane` | |
| `research` | yes | an id in the `vftv:research` registry | the gate |
| `kind` | no, default `ordinary` | `ordinary` \| `void` | the quote context's `kind`: `ORDINARY` or `VOID` |
| `pattern`, `key` | exactly one of `pattern`+`key` or `ingredients` | vanilla's shaped pattern rules (equal-width rows, every non-space symbol keyed, every key used, space means empty), **1–3 rows of 1–3 symbols** | shaped |
| `ingredients` | | 1–9 vanilla `Ingredient`s | shapeless |
| `result` | yes | a vanilla `ItemStackTemplate` (`id`, `count`, optional `components`) | the output |
| `vis` | yes | 1–8 entries; each key a **vis aspect**; each value 1–1000 | the base cost |

- **Ingredients** are vanilla `Ingredient`s, so NeoForge's custom ingredients (component matching and the like) work unchanged. One item per ingredient slot.
- **The result** is static: no dynamic assembly. At decode it is **created and checked**: the created stack must be non-empty and its count must not exceed that stack's own maximum stack size (resolved with its components). Vanilla's template codec alone allows counts up to 99, and creation can return an empty stack after strict validation.
- **The 3×3 bound is ours,** enforced by our codec even if another mod enlarges vanilla's shaped-pattern limits.
- **Validation at decode, registry-aware.** The recipe loader decodes through registry ops built from the full layered registry access, so datapack registries (ADR 0003) are visible: `research` must name an existing entry, and each `vis` key must be a vis aspect, an exposed primal ([wands, Vis storage](wands.md#vis-storage)). A recipe that fails any rule fails to decode: vanilla skips it with a logged error naming it. Tags are not read at decode.
- **Unknown fields** are ignored at runtime (vanilla `MapCodec`) and rejected in `content/` (see "Authored content").
- **Stream codec:** the serializer has a bounded `StreamCodec` as well as its `MapCodec`, because the full recipe sync encodes through it: `research` and the result id ≤ 256 characters each, `kind` one byte, pattern ≤ 3×3, ingredients ≤ 9, `vis` ≤ 8 entries of 1–1000. Out of bounds is rejected at decode.

**Matching reuses vanilla, and the player is never in it.** The input is the grid only, as `CraftingInput.ofPositioned` builds it (trimmed to its occupied area). A shaped recipe holds a vanilla `ShapedRecipePattern` and calls its `matches`, which includes the mirror rule. A shapeless recipe holds a vanilla `ShapelessRecipe` built from its ingredients and calls its `matches`, so NeoForge's matcher for non-simple ingredients (a 9 × 9 predicate matrix and a bounded assignment search; see "Performance") runs inside vanilla code and `core.arcane` never imports it. Every occupied grid slot is consumed.

**Not vanilla crafting, and no recipe book:**
- the type is not `RecipeType.CRAFTING`, so the crafting table and the Crafter never see it;
- `isSpecial()` is true, so the server recipe book never adds it, even under `/recipe give`; `showNotification()` is false; `placementInfo()` is `NOT_PLACEABLE`;
- `display()` returns vanilla's `ShapedCraftingRecipeDisplay` or `ShapelessCraftingRecipeDisplay`, with the Worktable item as the crafting station, for the book and JEI. Alternatives in a slot are listed in the order vanilla's `Ingredient` display yields (tag order for a tag, list order for a list).

### Selection

For a grid and a player, the **selected recipe** is the `vftv:arcane` recipe that matches the grid and whose `research` the player has learned, with the **lowest id** by `String.compareTo` on the id string (`ns:path`). Client and server run the same function on the same synced data.

- **Unlearned recipes are invisible to the Worktable's selection and preview** (and hidden in JEI; the book's `preview: true` pages are the research spec's exception). An unlearned recipe that matches never hides a learned one that also matches.
- The status line reads the same for "nothing matches" and "only unlearned recipes match", so nothing leaks a recipe the player hasn't learned ([research, States](research.md#states)).

### Recipe sync and reload

- **Sync.** `neoforge` calls `OnDatapackSyncEvent#sendRecipes(vftv:arcane)` on join and on `/reload`. The client keeps the received map from `RecipesReceivedEvent` and clears it at logout.
- **One snapshot, replaced whole.** Each side holds one immutable **`ArcaneRecipeSnapshot`**: every `vftv:arcane` recipe holder, sorted by id. The server builds the **initial** snapshot in `ServerStartingEvent`, which both dedicated and integrated servers post before any player can join (`OnDatapackSyncEvent` fires only on join and reload, so it cannot guard startup). After that, the `OnDatapackSyncEvent` handler rebuilds it, before sending recipes, whenever the server's recipe manager is not the instance the snapshot was built from: after each `/reload`, never on an ordinary join. The client builds it from each `RecipesReceivedEvent`. Nothing holds a recipe object across a replacement: selection always runs against the current snapshot, and a batch pins its recipe only within one payload, during which no reload can be applied.
- **On replacement:** every open `WorktableMenu` bumps its revision and clears its refusal (see "Preview revision"), so any craft echoed against the old snapshot is refused as stale. Menus keep their real grid, wand and output items. The client's previews recompute and JEI reconciles.
- **The cap, `MAX_ARCANE_RECIPES` = 4,096** (`ArcaneLimits` in `api`). A snapshot of more than 4,096 recipes is rejected as a set, never truncated:
  - **at server start** (in `ServerStartingEvent`, with or without players), startup fails with an error naming the count and the cap;
  - **on `/reload`**, the new snapshot is **empty**: no arcane recipe can be selected, previewed or shown in JEI until a reload brings the count within the cap. An error naming the count and the cap is logged. The previous snapshot is not kept, because its recipe objects are no longer the server's.
  - The client applies the same rule to what it receives.

## Crafting

### The cost

The recipe's `vis` is the quote's `base`. The context is **`VisContext(source = recipe id, kind = the recipe's kind, research = the recipe's research)`**. The station's wand is the wand slot's live stack: it is quoted and spent from, and the Worktable never charges it.

- **The warp tax.** An `ordinary` recipe passes `ORDINARY`, so [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)' `vis_factor` ≥ 1.0 raises its cost ("ordinary magic suffers", [progression, Warp](progression.md#warp)). A `void` recipe passes `VOID`, which #25 may price below 1.0 (the amplifier role). The station shows the warp part of the multiplier, so the tax is never hidden ([lore, Listening and warp](lore.md#listening-and-warp)). Until #25 lands, the factor is 1.0.
- **Arcane crafting applies no warp.** Warp is applied only by an explicit commit through #25 ([research, Warp on entries](research.md#warp-on-entries)), and a craft is not one. A recipe that must apply warp on craft needs an extension proposed by its own spec: a #25 preview echoed in the same commit. v1 has none.
- **Void-tier recipes:** built-in recipes with `kind: void` are gated by a Stage 4 entry ([progression, Stages](progression.md#stages)), checked by a content lint. Packs are free.

### The craft payload

Crafting happens **only** in the server handler of one C2S payload, sent by the preview widget:

**`WorktableCraft(containerId, revision, recipeId, echo, batch)`**

| Field | Wire type and bounds |
|---|---|
| `containerId` | VarInt |
| `revision` | `int`, the 32-bit preview revision the client holds |
| `recipeId` | a recipe id, ≤ 256 characters |
| `echo` | the previewed quote's cost: `base` (≤ 8 entries, aspect id → 1–1000) and `effective` (≤ 8 entries, aspect id → 1–10,000), with **the same key set and no duplicate keys**; `m` 0–10,000; `spill` 0–1,000,000 (VarInt); the flags `discount_capped`, `clamped`, `exceeds_capacity` |
| `batch` | boolean |

Each collection's size is read and checked **before anything is allocated**. Out-of-range, oversized or malformed input is rejected at decode.

Vanilla clicks cannot craft: the preview is not a slot, the output refuses insertion, and the menu is not a recipe-book menu.

**Server checks,** on the server thread (the architecture's `enqueueWork` rule), first failure refusing:

| # | Check | On failure |
|---|---|---|
| 1 | **Rate:** at most 1 `WorktableCraft` per player per server tick | dropped, no reply |
| 2 | not a spectator; the open menu is a `WorktableMenu` with this `containerId`; `stillValid` | dropped, no reply (there is no menu to report to) |
| 3 | the menu's change check (see "Preview revision") runs; then `revision` equals the menu's revision | `STALE` |
| 4 | the wand slot holds a `vftv:wand` | `NO_WAND` |
| 5 | the wand is not inert ([wands, Vis storage](wands.md#vis-storage)) | `INERT_WAND` |
| 6 | **selection** on the server's grid (see "Selection") selects a recipe — **this is the research gate, before any slot changes** | `NO_RECIPE` |
| 7 | the selected recipe's id equals `recipeId` | `STALE` |
| 8 | **plan the effect** (see "Planning") | `LEFTOVER` |
| 9 | the quote for the live wand: not `exceeds_capacity`, and affordable | `EXCEEDS_CAPACITY`, `SHORT` |
| 10 | the output slot can take the result | `OUTPUT_FULL` |
| 11 | `VisService.spend(player, wand slot stack, recipe.vis, ctx, echo, effect, menu boundary)` | its refusal, mapped under "Status and refusals" |

The order of checks 4–10 is the status line's precedence, so a refusal names exactly what the client would show for the same state. Step 11 rechecks research, inertness, the echo, affordability and every slot's `expectedBefore`, then commits vis, materials and output together or restores all three, tells the menu's apply boundary, then posts `VisSpentEvent` ([wands, The spend](wands.md#the-spend)).

**Every payload that passes check 2 is an activation:** before check 3 the server clears data slot 0, so the new activation acknowledges any earlier refusal (see "Status and refusals").

**After a refusal:** set the refusal data slot to its code and call `sendAllDataToRemote()`, so the client redraws from the server's state together with the refusal. Nothing changed: grid, wand vis and output are as they were, and no event is posted.

### Preview revision

A server-owned **32-bit counter per open menu**, starting at 0, sent in data slots 1 and 2. The client echoes the value it holds when it sends a craft. It catches what a recipe id and a cost cannot: a `/reload` that changes a recipe's ingredients or result at the same id and price, or a slot the client hadn't yet seen change.

- **It bumps** (+1, wrapping) on:
  - any change to a menu container slot: grid, wand or output, including the wand's vis written by a spend;
  - a change to the player's research (the research attachment's `revision` differs from the one last seen);
  - a replaced `ArcaneRecipeSnapshot`;
  - a replaced `VisRules` record (a tuning `/reload`).
- **Detection:** slot changes through the menu's `slotsChanged` and container listeners, at once. The other three are an **O(1) change check** comparing the stored research revision, snapshot instance and `VisRules` instance (and the price inputs below, which only clear the refusal), run in the menu's `broadcastChanges` (every server tick while open), at check 3 of every payload, and before every later batch iteration.
- **Every bump clears the refusal.** **Price inputs** (the discount attribute's value, and #25's factor for `ORDINARY` and `VOID`) never bump the revision: the quote echo catches a stale price. The same O(1) change check compares them, and a change **clears the refusal without bumping**, so a `SHORT` or `EXCEEDS_CAPACITY` made obsolete by gear or warp never lingers.
- **Suppression during a commit, ended at the apply boundary.** While a spend applies or restores slot changes, the menu only marks itself dirty: no preview recomputation, no bump and no broadcast happens per slot. The menu passes the spend its **apply boundary** ([wands, The spend](wands.md#the-spend)), which runs once the swap or its rollback is complete and **before `VisSpentEvent` is posted**: it ends suppression, bumps the revision **once** if anything was marked, and on a commit records that value as the handler's own. Anything a `VisSpentEvent`, `ItemCraftedEvent` or criterion listener changes afterwards therefore produces its own, external bump. The final state is broadcast when the payload ends. A rollback may cost the client one `STALE`; it never exposes an intermediate output.
- **Torn reads are harmless.** The two halves arrive in separate data updates; a client that echoes a half-updated value is refused as `STALE`, never crafts wrongly.

### Planning

The effect is a list of the wands spec's prepared slot changes `(container, slot, expectedBefore, after)`, at most 10: one per consumed grid slot and one for the output. `expectedBefore` is always the server's live content.

- **A consumed slot** loses one item. If the stack has a **crafting remainder** (the stack-aware remainder vanilla crafting uses), it replaces the slot **only if the slot held exactly one**: `after` is the remainder template's created stack, which must be non-empty and within its maximum stack size. A slot holding more than one, or an invalid remainder, refuses with `LEFTOVER` ("Something would be left over."). This is visible in the status line before the click. Built-in arcane recipes use no remainder-bearing ingredient (a unit test), so this branch exists for packs.
- **The output slot:** empty → the result; the same item and components with room (`count + result count ≤` the stack's maximum) → merged; otherwise `OUTPUT_FULL`.

### Committing

1. Suppression begins (see "Preview revision").
2. `VisService.spend(…)` runs the wands spec's steps 1–6; at the end of step 6 the menu's apply boundary ends suppression, bumps once and, on a commit, records the revision as the handler's own and clears the refusal; then step 7 posts `VisSpentEvent`.
3. **After either committed outcome** (`SPENT` or `COMMITTED_NOTIFY_FAILED`), in order, each exactly once:
   1. `result.onCraftedBy(player, count)` (statistics);
   2. NeoForge's `PlayerEvent.ItemCraftedEvent`, through the platform seam calling `EventHooks.firePlayerCraftingEvent(Player, ItemStack, Container)` with **a copy** of the result and **a defensive `Container` snapshot** of the grid as it was before the commit (slot positions kept);
   3. `CriteriaTriggers.RECIPE_CRAFTED.trigger(player, recipe key, copies of the consumed stacks)`, so advancements, quest mods and the research spec's `vftv:advancement` triggers can see arcane crafts.
4. After the payload, if any iteration committed, `vftv:worktable.craft` plays once.

- None of step 3 runs inside the commit. **A committed iteration is never reported as failed.** An exception from one of step 3's notifications is logged with the recipe id, the remaining notifications of that iteration still run, and the batch stops; it is never a refusal.
- **`COMMITTED_NOTIFY_FAILED`** (a `VisSpentEvent` listener threw): the craft is committed and nothing is rolled back. The handler logs it, runs all of step 3, counts the iteration as committed (so the payload still plays its sound and the refusal stays clear), and stops the batch.
- **A spend that throws** failed in step 6, so it has already been rolled back and logged by the wands spec; the handler catches it and refuses with `ERROR` (on a later batch iteration it just stops).

### Batch

**Shift-activate the preview:** the **same recipe** repeats up to **64 times** (`ArcaneLimits.MAX_BATCH`).

- **Iteration 1** is the whole check table above.
- **Each later iteration** re-reads live state and stops **silently** at the first failure:
  1. check 2 (the menu is still open, the same container, valid): listeners may have closed it;
  2. the O(1) change check, then the menu's revision equals **the revision recorded at the previous commit's apply boundary**. The bump from the batch's own swap is expected; any later bump (a `VisSpentEvent`, `ItemCraftedEvent` or criterion listener changing a slot or the wand, granting or revoking research, a reload) stops the batch;
  3. checks 4–5 on the **live** wand slot (a listener may have replaced the wand);
  4. the **pinned** recipe still matches the live grid (no re-selection: O(1) recipes);
  5. checks 8–10;
  6. the spend, with **the original echoed cost** as `expected`, so any modifier change since the click stops the batch at the next spend. The spend rechecks research.
- Only a failure on iteration 1 reports. Each iteration is its own spend, with its own events.

### Status and refusals

**One enum, `WorktableStatus`, in `core.arcane`.** Its code is the wire value of data slot 0. The client computes the status line from synced inputs in the precedence below; the server's refusals use the same codes.

| Code | Status | Raised by | Lang key | Args |
|---|---|---|---|---|
| 0 | `READY` (as a refusal: none) | client only | `status.vftv.worktable.ready`, or **`status.vftv.worktable.ready_taxed`** when the quote's `warp` is above 1000 or its `spill` above 0 | 1: the result's name; `ready_taxed` 3: the result's name, `warp` and `spill` |
| 1 | `NO_WAND` | client; check 4 | `status.vftv.worktable.no_wand` | 0 |
| 2 | `INERT_WAND` | client; check 5; spend "inert" | `message.vftv.spend.inert` (the wands spec's) | 0 |
| 3 | `NO_RECIPE` | client; check 6; spend "unlearned" | `status.vftv.worktable.no_recipe` | 0 |
| 4 | `LEFTOVER` | client; check 8 | `status.vftv.worktable.leftover` | 0 |
| 5 | `EXCEEDS_CAPACITY` | client; check 9; spend "short" with `exceeds_capacity` | `status.vftv.worktable.exceeds_capacity` | 0 |
| 6 | `SHORT` | client; check 9; spend "short" otherwise | `message.vftv.spend.short` (the wands spec's) | 1: the short aspects, joined |
| 7 | `OUTPUT_FULL` | client; check 10 | `status.vftv.worktable.output_full` | 0 |
| 8 | `STALE` | server only: checks 3 and 7; spend "stale" (a changed cost, or a slot that no longer matches `expectedBefore`) | `message.vftv.spend.stale` (the wands spec's) | 0 |
| 9 | `ERROR` | server only: a spend that threw in step 6 and was rolled back | `status.vftv.worktable.error` | 0 |

- **Client precedence** (first match): `NO_WAND`, `INERT_WAND`; then an empty grid shows no status line; then `NO_RECIPE`, `LEFTOVER`, `EXCEEDS_CAPACITY`, `SHORT`, `OUTPUT_FULL`, else `READY`.
- **The tax is on the face of the screen.** A taxed `READY` names the warp factor and the flux one craft spills on the status line itself, not only in the cost tooltip, so the tax is never hidden ([lore, Listening and warp](lore.md#listening-and-warp)). For example, with the illustrative Bell (Aer 8, Ordo 6, Terra 4), copper caps, no discount and the [warp spec's](warp.md#the-dials) tax of 20‰ per point: at total warp 10, `w` = 1000 + 10 × 20 = 1200, so `m` = 1200, effective Aer ⌈9.6⌉ = 10, Ordo ⌈7.2⌉ = 8, Terra ⌈4.8⌉ = 5 against neutral 8, 6, 4; the surcharge is 2 + 2 + 1 = 5 and the spill at `tax_pm` 1000 is **5**, so the line reads "Ready to make Bell. Warp ×1.20, spills 5 flux." At total warp 25 (`w` 1500, the +50% cap), effective 12, 9, 6, surcharge 4 + 3 + 2 = 9, spill **9**.
- **`SpendResult` mapping** (by the wands spec's outcome): inert → `INERT_WAND`; unlearned → `NO_RECIPE`; stale → `STALE`; short → `EXCEEDS_CAPACITY` if the recomputed quote has `exceeds_capacity`, else `SHORT`; a step-6 throw → `ERROR`. `SPENT` and `COMMITTED_NOTIFY_FAILED` are both committed crafts and set no refusal.
- **Feedback is separate from eligibility.** The refusal is feedback only: while data slot 0 is non-zero and not yet acknowledged, the **status line** shows that refusal instead of the predicted status. Whether the preview can be activated depends **only on the local prediction**: a locally `READY` preview stays ready (and activatable) while a refusal is shown, so a `STALE` refusal set after the change that caused it never blocks a retry with the current revision and echo. Arguments (the short aspects) are computed by the client from synced state, which the refusal's forced resync has just made the server's.
- **Acknowledging:** activating the preview acknowledges the shown refusal on the client at once, and the server clears data slot 0 at the start of every activation. A new refusal is always delivered by `sendAllDataToRemote()`, which re-sends data slot 0 even when its value is unchanged; any received value for it ends the client's acknowledgement.
- **Clearing:** the server also clears data slot 0 on every revision bump, on every price-input change (without bumping) and on every committed craft. The forced resync after a refusal changes no state, so it never clears the refusal it carries.

## The authoritative preview

**`ArcanePreview.of(player, recipe, wandSource)`** in `core.arcane` is the **only** cost source for the station, the book's `vftv:recipe` pages, the fact fields and JEI. It embeds the complete `VisQuote` **unchanged**.

| Field | Value |
|---|---|
| `recipe` | the recipe id |
| `output` | the created result stack |
| `inputs` | the slot displays in order: shaped, 9 entries row-major over the 3×3 with the pattern at the top-left and empty cells empty; shapeless, the ingredients in data order |
| `vis` | the base cost, ascending aspect id |
| `availability` | `PRICED`, `INERT` or `NO_WAND` |
| `quote` | the `VisQuote`, present iff `PRICED` |
| `wand` | where the pricing wand is: `STATION`, `MAIN_HAND`, `OFFHAND` or `INVENTORY` (with its slot), absent iff `NO_WAND` |

- **Inside the station** the wand is the wand slot's stack.
- **Outside it, the reference wand:** the first non-inert `vftv:wand` among the selected hotbar slot, the offhand, then inventory slots 0–35 in order. If every wand found is inert, availability is `INERT` and the first one found is named. With none, `NO_WAND`. **One reference wand prices both the book and JEI.**
- **Absent values are typed, never guessed:** without a priced quote, effective cost and flux render as `arcane.vftv.no_wand` or `arcane.vftv.inert_wand`, never a number from a wand that isn't there.
- **Identity is visible:** the book's recipe page and JEI show `arcane.vftv.priced_with` (the wand's name, and `arcane.vftv.where.main_hand`, `.offhand` or `.inventory`).
- **Live dependencies:** a preview is recomputed when any input changes: the station's slots or the reference wand (hand, offhand or inventory contents, including the wand's pools), the learned set, the recipe snapshot, `VisRules`, the discount attribute, #25's factor inputs, or the resources (names). Caches may keep layout, **never an old cost**.

**Fact fields,** `{fact:recipe:<ns>:<path>:<field>}` ([research, Prose and fact references](research.md#prose-and-fact-references)), resolved through the preview with the reference wand:

| Field | Renders |
|---|---|
| `output` | the result's name, preceded by "*n* × " when its count is above 1 |
| `inputs` | each distinct ingredient in first-appearance order (row-major for shaped), as "*n* × *name*" of the alternative it lists first, joined by ", " |
| `vis` | the base cost, "*Aspect* *n*", ascending aspect id, joined by ", " |
| `vis_effective` | the effective cost in the same form, or the absent text |
| `flux` | the quote's spill as an integer, or the absent text |

Aspect names are the localised names; numbers follow "Number formats" under "The screen".

## JEI

A soft dependency ([architecture, Soft dependencies](architecture.md#soft-dependencies)), never on the progression path. **Every JEI class lives in `…neoforge.compat.jei.client`**, since JEI's drawing uses client classes; the NeoForge crafting hook and the shapeless matcher stay behind their platform seams.

- **One category** for `vftv:arcane`, 160×114: the grid at (0, 0) with 18-px slots; the result at (106, 18); the cost panel at (0, 58), 4 × 2 cells of 40×18 drawn exactly as the station's, priced through `ArcanePreview` with the reference wand (short aspects on `cost_cell_short`); `arcane.vftv.priced_with` or the absent text at (0, 96); and, when priced with `warp` above 1000 or spill above 0, `arcane.vftv.taxed` at (0, 106), the same facts as the station's `ready_taxed` line. Title `jei.vftv.category.arcane`. The Worktable item is its catalyst. **No transfer handler**: there is no "+" placement path.
- **API used** (verified in the JEI 30.38 common API sources): `IRecipeManager.addRecipes`, `hideRecipes` and `unhideRecipes`, and `IModPlugin.onRuntimeAvailable` and `onRuntimeUnavailable`. Ordering of runtime start against recipe arrival was not exercised; it is the build's E2E obligation below.
- **Reconcile, order-independent.** One function, **`GatedRecipeDiff`** in `core.research.model`, computes the desired state from whatever is present now: the runtime, the client's recipe snapshot and the learned set.
  - Recipe objects from the current snapshot not yet added are added.
  - Every added object not in the current snapshot (removed, or replaced by a reload even at the same id, since identity is the object) is hidden for good.
  - Current ones are unhidden iff learned, hidden otherwise.
- **When it runs:** whenever the runtime, recipes or research arrives, in any order. On `onRuntimeAvailable` it first hides every arcane recipe JEI holds, then reconciles at once against the **current** snapshot and learned set, not waiting for a later delta. It also runs on each `RecipesReceivedEvent`, and on each research baseline and delta (learn, grant, revoke).
- **What clears what:**
  - **losing JEI's runtime** (`onRuntimeUnavailable`, which JEI's restart on a recipe sync or resource reload causes) clears only the plugin's runtime state, never the recipe snapshot or the learned set;
  - **logout** clears both.
- **The function is shared:** it is generic over the recipe object and its snapshot, and the [crucible spec](crucible.md#jei) reuses it unchanged for `vftv:crucible`, with no lifecycle of its own. The plugin and this lifecycle are the [compatibility spec's](compat.md#lifecycle).
- **Affordability** is marked per recipe from the reference wand: short cells, and the absent text for no wand or an inert one. Open JEI views refresh on the preview's live dependencies.

## Multiplayer and automation

- **Per player.** Menus are transient and per player, so two players at one Worktable each have their own grid, wand, learned recipes and costs. Research, warp and discount are the crafting player's. Nothing about the block is owned.
- **Breaking the table** under its users closes every menu and returns each player's items.
- **Revocation never confiscates.** Committed output is the player's inventory; a revoked entry stops the next craft, not the taking.
- **No automation in v1:** no block entity, item capability or `Container`, so hoppers, pipes and the Crafter cannot reach it, and [Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23) do not arcane-craft. Every automatic path would be another gate to prove; a future one must keep the research gate and the spend (see the contracts).

## Authored content

The content ticket delivers authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen, which writes the resources into `src/generated/resources`. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails the recipe type's rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/arcane/recipes.json       the arcane recipes this spec owns
content/lang/en_us/arcane.json    every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `recipes.json` | `vftv:arcane_recipes/1` | each recipe `<ns>:<path>` → `data/<ns>/recipe/<path>.json`, its body unchanged except the [compatibility spec's ingredient widening](compat.md#consumed) |
| `lang/<lang>/arcane.json` | `vftv:lang/1` | its `strings` object merged into `assets/vftv/lang/<lang>.json`; a duplicate key across all `content/lang` files fails datagen |

Other specs' arcane recipes live in their own content files (for example `content/wands/recipes.json`) with this body.

### Recipes

`{"schema": "vftv:arcane_recipes/1", "recipes": {<id>: <body>}}`, each body in the recipe type's format, with these content rules on top:
- **the file holds exactly one recipe, `vftv:bell`,** with `research` `vftv:arcane_crafting` and `kind` `ordinary` (or `kind` omitted, which defaults to it); its `result` is `minecraft:bell`;
- its ingredients are pre-iron: copper, amethyst and wood items or tags only;
- `vis` uses only the six built-in primals, each value ≤ the Wellspring's per-aspect amount in `content/nodes/types.json` (32 today; the affordability test reads it, so the bound follows the data).

A complete file (ingredients and costs illustrative; the content ticket chooses them within the rules):

```json
{"schema": "vftv:arcane_recipes/1", "recipes": {
  "vftv:bell": {
    "type": "vftv:arcane",
    "research": "vftv:arcane_crafting",
    "kind": "ordinary",
    "pattern": ["sss", "cac", "c c"],
    "key": {"s": "#minecraft:wooden_slabs", "c": "minecraft:copper_ingot", "a": "minecraft:amethyst_shard"},
    "result": {"id": "minecraft:bell", "count": 1},
    "vis": {"vftv:aer": 8, "vftv:ordo": 6, "vftv:terra": 4}}
}}
```

**Rejected:** any id other than `vftv:bell`, or `vftv:bell` missing; a `research`, `kind` or `result` item other than the above; any field outside the recipe type's table (`group`, `category` and `show_notification` included); both or neither of `pattern`+`key` and `ingredients`; a pattern over 3×3; a `vis` key that isn't a built-in primal or a value over the Wellspring bound; an ingredient outside the pre-iron rule; a created result that is empty or over its maximum stack.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text.

| Key | Args | Sense |
|---|---|---|
| `block.vftv.worktable` | 0 | "Worktable" (also its item's name) |
| `container.vftv.worktable` | 0 | "Worktable" (the screen title) |
| `status.vftv.worktable.ready` | 1 | "Ready to make %s." (the result's name) |
| `status.vftv.worktable.ready_taxed` | 3 | "Ready to make %s. Warp ×%s, spills %s flux." (the result's name, `warp`, spill) |
| `tooltip.vftv.worktable.batch_flux` | 2 | "A batch of %s spills %s flux." (*n*, total spill) |
| `arcane.vftv.taxed` | 2 | "Warp ×%s, spills %s flux." (`warp`, spill) |
| `status.vftv.worktable.no_wand` | 0 | "Place a wand here." |
| `status.vftv.worktable.no_recipe` | 0 | "Nothing I know how to make." |
| `status.vftv.worktable.leftover` | 0 | "Something would be left over." |
| `status.vftv.worktable.exceeds_capacity` | 0 | "My wand can't hold enough for this." |
| `status.vftv.worktable.output_full` | 0 | "There's no room for it." |
| `status.vftv.worktable.error` | 0 | "Something slipped. Nothing was used." |
| `tooltip.vftv.worktable.preview` | 0 | "Click to make one. Shift-click to make as many as I can." |
| `tooltip.vftv.worktable.cost` | 3 | "%s: %s (base %s)" (aspect, effective, base) |
| `tooltip.vftv.worktable.base` | 2 | "%s: base %s" (aspect, base) |
| `tooltip.vftv.worktable.pool` | 2 | "My wand holds %s of %s." (current, capacity) |
| `tooltip.vftv.worktable.factors` | 4 | "Caps ×%s, gear −%s, warp ×%s: ×%s" (cap, discount, warp, `m`) |
| `tooltip.vftv.worktable.discount_capped` | 0 | "My gear can lighten it no further." |
| `tooltip.vftv.worktable.clamped` | 0 | "The cost is held within its bounds." |
| `tooltip.vftv.worktable.flux` | 1 | "Spills %s flux." |
| `arcane.vftv.no_wand` | 0 | "(no wand)" |
| `arcane.vftv.inert_wand` | 0 | "(a wand missing a part)" |
| `arcane.vftv.priced_with` | 2 | "Priced for my %s, %s." (wand name, where) |
| `arcane.vftv.where.main_hand`, `.where.offhand`, `.where.inventory` | 0 | "in hand", "in my other hand", "in my pack" |
| `narration.vftv.worktable.result` | 2 | "Makes %s %s." (count, name) |
| `narration.vftv.worktable.cost` | 3 | "%s: costs %s, my wand holds %s." (aspect, cost, pool) |
| `jei.vftv.category.arcane` | 0 | "Arcane Crafting" |
| `subtitles.vftv.worktable.convert` | 0 | "Worktable tuned" |
| `subtitles.vftv.worktable.craft` | 0 | "Worktable chimes" |

**Used, not declared here:** `message.vftv.spend.inert` (0), `message.vftv.spend.short` (1) and `message.vftv.spend.stale` (0) belong to the wands spec's file; `container.inventory` is vanilla's.

```json
{"schema": "vftv:lang/1", "strings": {
  "block.vftv.worktable": "Worktable",
  "status.vftv.worktable.no_recipe": "Nothing I know how to make.",
  "tooltip.vftv.worktable.factors": "Caps ×%s, gear −%s, warp ×%s: ×%s",
  "arcane.vftv.priced_with": "Priced for my %s, %s.",
  "subtitles.vftv.worktable.craft": "Worktable chimes"
}}
```

**Rejected:** a key outside the table; a required key missing in `en_us`; a placeholder count that differs from the table; an empty string; a duplicate key, including any of the wands spec's keys redeclared here.

## Events

**No new event.** Arcane crafting posts only events that already exist, on the server, **after each committed iteration**, in this order:

| Event | Posted by | Payload |
|---|---|---|
| `VisSpentEvent` | the wands spec's `spend`, step 7 | player; level and position; the `VisContext` (recipe id, kind, research); the `VisQuote` with its resolved factors |
| statistics | `ItemStack.onCraftedBy` | the result and count |
| `PlayerEvent.ItemCraftedEvent` (NeoForge) | `EventHooks.firePlayerCraftingEvent`, through the platform seam | player; a copy of the result; a defensive `Container` snapshot of the grid before the commit |
| `RECIPE_CRAFTED` criterion | `CriteriaTriggers.RECIPE_CRAFTED.trigger` | player; recipe key; copies of the consumed stacks |

Nothing fires for a turned table (beyond the vanilla `BreakBlockEvent` it asks and the game event it posts), a refusal, a batch that stops, or the taking of committed output.

**Plain `core` services:** `ArcanePreview.of`; `ArcaneRecipes.select(player, grid)` over the current snapshot. They are **internal in v1** ([API spec, Services](api.md#services)). The `vftv:arcane` recipe body is a public data format, and the `#vftv:converts_to_worktable` tag key is in `ArcaneKeys` in `api`.

## Sync and state

**Server-authoritative. Nothing persists:** no block entity, attachment, `SavedData` or component of this spec's own.

| State | Storage | Synced to |
|---|---|---|
| grid, wand, output | the menu's transient containers | the menu's player, by vanilla slot sync |
| refusal code, preview revision | menu fields | the menu's player, through data slots 0–2 |
| arcane recipes | the recipe manager; `ArcaneRecipeSnapshot` in memory | every player, `OnDatapackSyncEvent#sendRecipes(vftv:arcane)` on join and `/reload`; the client's snapshot is cleared at logout |
| craft rate counter | transient per-player server memory | never |
| JEI reconcile state | client memory | never; cleared at logout |
| research, wand vis, `VisRules`, the discount attribute, warp | their specs | as they specify |

- **One custom payload,** C2S `WorktableCraft`. **No S2C payload:** refusals and the revision ride vanilla container data.
- **Bounded codecs:** the payload as above; the recipe `MapCodec` and `StreamCodec` as under "The recipe type".

**Reloads:**

| Change | Effect |
|---|---|
| Recipes (`/reload`) | the snapshot is replaced, or emptied if over the cap; open menus bump and clear refusals, keeping their items; recipes re-sent; client previews recompute; JEI reconciles |
| Tuning (`/reload`) | a new `VisRules`; open menus bump; previews and quotes recompute |
| The research or aspect registries | need the world reopened |
| Resource reload | the screen's layout and names rebuild |

## Tuning and limits

**No tuning file.** Nothing here is balance an admin tunes.

**Data homes:**
- base costs, ingredients, results, counts, kinds and gates: each recipe's data (`content/arcane/recipes.json` and the other specs' content files, or packs);
- the Stage 1 bound (32 today): the Wellspring's per-aspect amount in `vftv:node_type` data (`content/nodes/types.json`), read by the test;
- the conversion's source blocks: the block tag `vftv:converts_to_worktable` (packs may extend it; blocks with a block entity are never converted).

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`ArcaneLimits`** in `api`:

| Constant | Value |
|---|---|
| `MAX_ARCANE_RECIPES` | 4,096 |
| `GRID_SIZE` | 3 (the 3×3 bound) |
| `MAX_COST_ENTRIES` | 8 |
| `MIN_BASE_COST`, `MAX_BASE_COST` | 1, 1000 |
| `MAX_BATCH` | 64 |
| `MAX_CRAFTS_PER_TICK` | 1 per player |
| `MAX_ID_CHARS` | 256 |
| `MAX_ECHO_EFFECTIVE`, `MAX_ECHO_MULTIPLIER` | 10,000, 10,000 |
| `MAX_ECHO_SPILL` | 1,000,000 |

Also fixed in code: the `WorktableStatus` codes 0–9; the slot indices and data-slot layout; the GUI layout and number formats; the 4-digit cost cell. **Fixed feedback constants:** one craft sound per payload; playback volume and pitch 1.0.

## Performance

**Heavy-base workload:**
- 4 players, each at an open Worktable with a full 8-aspect cost panel, against **512 loaded arcane recipes** (addon scale);
- each makes **one single craft a second** and **one 64-iteration batch a minute** of a stackable recipe; the fixture replenishes inputs and vis and drains the output between crafts (harness work, not the mod's), so every batch really completes 64 iterations;
- on the client, the screen open with the cost tooltip hovered, and a book recipe page and a JEI category showing arcane recipes with the reference wand.

**Share:** server ≤ 0.01 ms mean, ≤ 0.05 ms p99, measured in the combined stress world; client 0 with no arcane screen, page or JEI view open, ≤ 0.05 ms p50 with one open. **Measured separately as load scenarios** (reported): the **flood at the cap** (4 players each sending one 64-iteration batch every tick for 600 ticks); **worst-case selection** at 4,096 recipes (all the same shape, the match on the highest id, shapeless with non-simple ingredients); a recipe `/reload` with 4,096 recipes and 4 menus open.

**Hot paths:**
- **Server, per tick:** for each open `WorktableMenu`, vanilla's `stillValid` and `broadcastChanges` (47 slots, 3 data slots) plus our O(1) change check. Nothing else.
- **Server, per payload:** one selection over ≤ 4,096 candidates (`MAX_ARCANE_RECIPES`), each a vanilla match over ≤ 9 cells:
  - **shaped:** the pattern compared cell by cell, in both mirrors (≤ 18 ingredient tests);
  - **shapeless, simple ingredients:** vanilla's item-count comparison (≤ 9 items);
  - **shapeless, non-simple ingredients:** vanilla delegates to NeoForge's matcher, which builds a **predicate matrix of ≤ 9 items × 9 ingredients** (≤ 81 ingredient tests) and then searches for an assignment of items to ingredients with **bounded backtracking**: every item claims one ingredient, and the search is finite because both sides have at most 9 members.

  Then ≤ 64 iterations, each O(1) for the pinned match, O(vis aspects) for the quote, ≤ 10 slot changes, one spend and its events.
- **Allocation:** bounded, not zero, and budgeted in two parts that are measured separately:
  - **First-iteration selection and setup** may allocate **per candidate**: vanilla's shapeless match allocates a list for each non-simple candidate, and NeoForge's matcher allocates its arrays, a bit set and its claim queues, as scratch. Its budget is a **candidate-dependent ceiling**, `setup + perCandidate × candidates` for shaped, simple-shapeless and non-simple-shapeless candidates separately, **capped at 4,096 candidates** and including the matcher's scratch. The byte ceilings are calibrated by the build and stored in its allocation-test fixture; the test asserts them at 64, 512 and 4,096 candidates, with a set of non-simple candidates that are **rejected** after the full matrix and search.
  - **Each batch iteration** allocates a constant amount, asserted equal to iteration 2's: the slot-change records, the event snapshot and copies, and the wands spec's one `vftv:vis` component. The pinned match is a single candidate, so its allocation is inside that constant.
- **Client:** selection and the preview are recomputed only on a dependency change (see "The authoritative preview"), never per frame. Our draw code for the cost panel, preview and status line allocates **nothing** per frame (an allocation-budget test); engine draw calls and JEI's own rendering are outside that claim.
- **Packets:** one C2S per activation, at most 1 per player per tick; vanilla slot and data updates; one recipe sync per join and reload.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **the recipe codec:** shaped xor shapeless; the 3×3 bound with a widened vanilla limit; required `research`; `kind` default; vis-aspect keys through registry ops (a compound, Vacuos and an unknown aspect rejected); 0 and 1001 rejected; a created result that is empty or over its component-resolved maximum rejected; the `StreamCodec` round trip and its bounds;
- **selection:** the learned filter; an unlearned overlapping recipe never hiding a learned one; lowest id among several learned matches; the mirror; shapeless with a custom ingredient;
- **planning:** a remainder with count 1 and with more; an invalid remainder; output empty, merge, merge over the maximum, and mismatched components;
- **the payload codec:** every bound, sizes rejected before allocation, duplicate and mismatched aspect keys, spill out of range;
- **status:** the precedence over every combination; `READY` against `ready_taxed` at `warp` 1000 with spill 0, `warp` 1001, and spill 1 at `warp` 1000 (a `VOID` spill), with the taxed Bell examples above (spill 5 at warp 10, 9 at warp 25) through a stub factor; the batch total's *n* limited in turn by `MAX_BATCH`, the wand, the smallest grid stack and the output room; the `SpendResult` mapping for each outcome, `COMMITTED_NOTIFY_FAILED` included; refusal clearing on each bump source, on each price input without a bump, on activation and on a committed craft, and survival through its own resync; a shown refusal never disabling a locally ready preview;
- **allocation:** the selection ceilings at 64, 512 and 4,096 candidates of each kind, including rejected non-simple shapeless candidates; the constant per-iteration amount;
- **the preview:** each availability; the reference-wand order (hand, offhand, inventory, an inert wand skipped, all inert); every fact field's format, including counts and absent values; the number formats at their edges;
- **the quote factors** against the wands spec's vectors;
- **Stage 1 affordability:** every recipe gated by a Stage 1 entry, across all `content/` recipe files, has base ≤ the Wellspring's per-aspect amount for every aspect and is `ordinary`;
- **content lints:** `void` only behind a Stage 4 entry; no built-in remainder-bearing ingredient; every `content/` rejection rule above;
- **the snapshot:** sorting, the 4,096 cap (start refusal, reload emptying);
- **JEI reconcile:** `GatedRecipeDiff` as a pure diff over every arrival order of runtime, recipes and research, and over a same-id replaced recipe ([compatibility, Tests](compat.md#tests)).

**GameTest:**
- **turning a table:** learned; unlearned opens the crafting table; sneaking; spectator; adventure mode; a cancelled `BreakBlockEvent` changes nothing; a listener on `RightClickBlock` denying the item or block use; **a `BreakBlockEvent` listener that replaces the block** (the recheck refuses); a tagged block with a block entity untouched; a focused wand converts; a failed `setBlock` plays no sound;
- **a craft end to end**, and the Bell recipe crafting in-world;
- **hostile `WorktableCraft`:** no menu; a wrong `containerId`; a spectator; out-of-range and oversized fields; the block broken; an unlearned recipe; a stale id; a stale echo; **a stale revision**; a flood past the rate. **Every refusal leaves grid, wand vis and output unchanged and posts no event**, and each in-menu refusal sets its code;
- **same-id, same-price replacement:** `/reload` changes `vftv:bell`'s result at the same id and cost; the old echo is refused as `STALE`;
- **removed recipe on reload:** the menu keeps its items and shows `NO_RECIPE`;
- a slot changed between preview and commit; a full output slot; the wands spec's **throwing commit** through this path (`ERROR`, everything restored, no event, no intermediate output broadcast);
- **batch:** stop conditions; per-iteration events; listeners on **`VisSpentEvent`** and on **`ItemCraftedEvent`** that **close the menu**, **replace the wand with an identical, same-price one**, **revoke the research** and **change a slot so the grid still matches** each stop the next iteration (their changes are external bumps, never absorbed into the batch's own); a modifier change stops the next spend;
- **post-commit notification throwing:** an `ItemCraftedEvent` listener that throws: the craft stands, the criterion still fires, the batch stops, no refusal;
- **a throwing `VisSpentEvent` listener:** the spend returns `COMMITTED_NOTIFY_FAILED`; vis, materials and output stay committed; statistics, `ItemCraftedEvent` and the criterion each run once; the craft sound plays; the batch stops; no `ERROR` and no refusal;
- **retry after a refusal, without touching slots:** a stale echo is refused as `STALE`, and the next activation with the current revision and echo crafts; a `SHORT` refusal clears when a discount attribute or a stubbed warp factor makes the recipe affordable, and the preview crafts;
- **no vanilla path extracts or crafts:** click, shift-click, number key, offhand swap, drag and double-click collect on output and grid; the place-recipe packet; the crafting table and the Crafter with an arcane pattern; taking committed output posts no event and spends nothing;
- hoppers can't reach it; close returns items; **death and disconnect** drop or return grid, wand and output exactly once; breaking the table returns them;
- two players at one table;
- `ORDINARY` against `VOID` through a stub factor; previewed spill = generated flux through #26's stub;
- recipes sent to the client on join and reload; the 4,096 cap on reload; **over-cap startup with zero players** fails in `ServerStartingEvent` on the dedicated GameTest server (the integrated case is in E2E);
- every test id in the discovery guard.

**E2E:**
- every status state, `ready_taxed` included, and the preview tooltip's batch total, the clipped status line with its full-text tooltip, and the cost tooltip (screenshots + a state dump of the menu and quote), including a **long test translation**;
- **a 4-digit cost cell** (9,999) with the vanilla font and with a wide resource-pack font: the scaled number stays inside x = 18…38 and the tooltip is exact;
- craft by mouse and by keyboard, one and batch;
- **keyboard and narration** under the [localisation fixtures](accessibility.md#fixtures): Tab through the preview, each cost cell (its tooltip shown on focus, its narration holding the cost, pool, factors, flags and spill) and the clipped status line (its full text); `cost_cell_short`'s notch visible in the greyscale contact sheet;
- turning a table on a real client (the arm swings, the block updates);
- an integrated (single-player) world whose datapack holds 4,097 arcane recipes fails to start with the count and cap;
- sync on join, reconnect and `/reload` (recipes re-sent, previews recomputed, a stale click refused);
- a book recipe page and fact fields with a wand in hand, in the offhand, only inert, and none;
- **with JEI:** hide and unhide on grant and revoke, on join with research already learned, with recipes arriving before and after the runtime, and after a recipe reload; a resource reload with no new recipe or research event; logout and a join to another server ([compatibility, Tests](compat.md#tests)); **without JEI**, nothing breaks;
- the first-session flow: assemble a wand, learn `vftv:tapping`, fill at the Wellspring, learn `vftv:arcane_crafting`, turn a table, craft the Bell (a state dump at each step);
- the asset, lang and model validators: every key under "Lang keys", both sounds and subtitles, the block's model, blockstate and item definition, every sprite.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)'s contracts on this ticket):

| Wands contract | Met by |
|---|---|
| Every vis charge through `VisService.spend` with the recipe id as `source`, `kind` `ORDINARY` or `VOID`, `research` the recipe's gate, `expected` echoed from the preview, and prepared slot changes for each consumed and output slot; all or nothing; `VisSpentEvent` after. GameTests: a stale quote, a full output slot, and a slot changed between preview and commit each refuse and change nothing | "The cost", "The craft payload" (checks 3–11, with the echoed quote **and** a server-owned preview revision, so a same-id, same-price reload is caught), "Planning", "Committing" (suppression ended at the spend's apply boundary, before `VisSpentEvent`; `COMMITTED_NOTIFY_FAILED` treated as committed, never as `ERROR`). "Batch" re-reads the live wand and menu and reuses the original echo. The wands spec's spend contract gains the boundary and that outcome in the same change. GameTests for each, plus the throwing commit, a throwing `VisSpentEvent` listener, listener-mutated batches from `VisSpentEvent` and `ItemCraftedEvent`, and same-id replacement. |
| The cost preview embeds `VisService.quote` unchanged | "The authoritative preview": the complete `VisQuote`. The wands spec is amended in the same change so the quote also exposes its resolved factors `cap`, `discount` and `warp`, with its vectors, fixtures and event payload updated; equality is unchanged. |
| Stage 1 affordability: every Stage 1 arcane recipe ≤ 32 per aspect (a unit test over recipe data); the first craft's materials and research reachable before it | The Stage 1 affordability unit test, reading the bound from the Wellspring's data and requiring `ordinary`; the Bell's pre-iron ingredients; and a named artifact on [Stage 1 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/34): its `path-stage1.json` learns `vftv:tapping` and then `vftv:arcane_crafting` (see below). The E2E first-session flow proves it in-world. |
| The arcane type carries the Stage 2 recipes at the wands spec's ids and gates; its schema is their body | "The recipe type": the body, now frozen as `"type": "vftv:arcane"` in the wands spec's "Authored content, Recipes" (amended in the same change) |
| Base vis costs validated at load: vis aspects only, 0–1000 | Registry-aware decode: vis aspects only, 1–1000 (a subset of 0–1000: a zero-cost aspect is simply left out), and created results checked too. There is no second validation pass. |
| The wand slot accepts only `vftv:wand` and never charges it; turning a block is its own right-click listener, working with a focused wand; the wand passes on non-node blocks | "The menu" (slot 0), "Turning a table" (before `onItemUseFirst`, any wand, not sneaking, so the focus path is untouched; denied uses honoured; the target rechecked after `BreakBlockEvent`) |

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts on this ticket):

| Research contract | Met by |
|---|---|
| **Gate:** each research-gated recipe carries `research`; the server refuses every processing and output path until it is learned; a GameTest with an unlearned player | `research` is required; "Selection" sees learned recipes only; check 6 runs **before any slot changes**; no other path crafts ("Not vanilla crafting", "Multiplayer and automation"). Taking committed output is moving owned inventory, not a crafting path, so revocation never confiscates it. GameTests: unlearned crafts refused, and every vanilla path tried. |
| **Recipe-page validator:** an entry's recipe page shows a recipe ungated or gated within its closure, else `preview: true` | `research` is a top-level field the build-phase validator reads; `vftv:bell` is gated by `vftv:arcane_crafting` and sits on its own page. The `preview: true` exception stands for forward-looking pages. |
| **One authoritative cost preview** per type: base and effective vis, flux spill and every decision cost, capped modifiers flagged; backs the page and the fact fields | `ArcanePreview`: typed availability, the visible reference wand, frozen fact formats, live dependencies; the flags in the cost tooltip |
| **JEI** filters by the learned gate, updates on grant, revoke, login and logout, marks unaffordable recipes accurately; everything works without JEI | "JEI": an order-independent reconcile against the current learned set at runtime start and on every arrival, stale objects hidden on replacement, caches cleared at logout, reference-wand affordability. E2E with and without JEI. |

**Met here** (the [progression spec](progression.md#the-first-hour)): the first arcane craft within the first session is the Bell or the Lathe, both behind `vftv:arcane_crafting`, at costs the Wellspring alone pays (the Stage 1 affordability test covers both).

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)** (amended in this change)
- `api.VisQuote` carries the resolved per-mille factors `cap`, `discount` and `warp`; echo equality is unchanged; the vectors, fixtures and `VisSpentEvent` payload show them.
- `VisService.spend` takes an optional **apply boundary**, called once after step 6's swap or rollback and before `VisSpentEvent`, and returns **`COMMITTED_NOTIFY_FAILED`** (never a rollback, never a rethrow) when posting `VisSpentEvent` throws after a commit; its tests cover both.
- `content/wands/recipes.json` bodies use the recipe type's format verbatim, with `"type": "vftv:arcane"`. [Wand and node content](https://github.com/andrewbrogoff/voices-from-the-void/issues/40) is unblocked by it.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)**, handed to [Stage 1 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/34) and [Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35):
- `vftv:arcane_crafting` (Stage 1 `note`) gates turning a table, `vftv:bell` and the foci spec's `vftv:lathe`; its entry carries `vftv:recipe` pages for `vftv:bell` and `vftv:lathe`, side by side. No additional arcane-specific entry is needed; `vftv:tapping` and the existing research ancestors remain prerequisites.
- **The affordability artifact:** `content/research/path-stage1.json` learns `vftv:tapping` and then `vftv:arcane_crafting` within its Stage 1 replay, and its reviewed sources include `vftv:starter` (the Wellspring that fills the wand). The Bell's materials (copper, amethyst, wood) are reachable before iron; #34's ticket reviews that list.
- **Fact fields** for `vftv:arcane`: `output`, `inputs`, `vis`, `vis_effective` and `flux`, in the formats above, priced with the reference wand and shown as the absent text when there is none; they re-resolve on the preview's live dependencies.
- **Recipe sync** of `vftv:arcane` is this spec's, so the research spec's recipe-page sync needs nothing more for it.

**[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19), [Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21), [Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22), [Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24), [Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- Any recipe made at the Worktable is a `vftv:arcane` body with its own `research`. `kind: void` only behind a Stage 4 gate. A recipe needing warp on craft must propose an extension (a #25 preview echoed in the same commit); v1 has none.
- Their Stage 1 arcane recipes, if any, meet the Stage 1 affordability test.

**[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)**
- No golem arcane-crafts in v1. Adding it means proposing an automated path that keeps the research gate (whose research, for an unattended craft) and the spend (which wand, outside any menu).

**[Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)**
- `vis_factor` as already contracted in the wands spec; the station displays its resolved per-mille part and the `clamped` flag. Its synced inputs are among the preview's live dependencies.

**[Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)**
- `spill` as already contracted in the wands spec, generated once on `VisSpentEvent` (amended from "applied": what an aura retains is reported separately); no second arcane flux publisher. **A quote's spill is at most 1,000,000**, the craft echo's wire bound.
- *Met by the [Hush spec](hush.md#contracts-on-sibling-specs): one listener generating the event's own `spill`; at most 80,000 by construction, clipped at 1,000,000.*

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- Hand entries (or reviewed exemptions) for `vftv:worktable` and `minecraft:bell`. The **round-trip** non-amplification checks include every built-in `vftv:arcane` recipe, counting vis as no material input, and the crafting table → Worktable conversion.

## Production tickets

This spec settles every decision. Two required child tickets deliver the assets and the authored content, and **the system is not done until both land**. **Neither writes mod code:** no block, menu, screen, payload, recipe type, JEI plugin, datagen provider or validator. The asset ticket delivers a generator and assets under the [art style](art-style.md); the content ticket delivers authored inputs under `content/`, in the schemas above. The block tag, the mineable tag, the loot table, the runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Worktable assets** | The generator `tools/art/worktable.py`. **Block textures**, 16×16, Stage 1 row of [art style §5](art-style.md#5-tone-ramp-by-stage): `assets/vftv/textures/block/worktable_top.png`, `worktable_side.png` and `worktable_bottom.png` (a crafting table turned to sound: acoustic tools, never saws; the teal tick on the top face, outside any object). The **model** `assets/vftv/models/block/worktable.json` (parent `minecraft:block/cube_bottom_top`), the **blockstate** `assets/vftv/blockstates/worktable.json` (one variant) and the **item definition** `assets/vftv/items/worktable.json` (`minecraft:model` of `vftv:block/worktable`). **GUI:** the background `assets/vftv/textures/gui/container/worktable.png` (176×220 used on a 256×256 sheet, every well at the positions under "The menu" and "The screen"); sprites under `assets/vftv/textures/gui/sprites/`: `worktable/preview_idle.png`, `preview_ready.png`, `preview_hover.png` and `preview_disabled.png` (26×26 each), `worktable/cost_cell.png` and `worktable/cost_cell_short.png` (40×18), `worktable/beyond.png` (9×9) and `container/slot/wand.png` (16×16). **Sounds:** `assets/vftv/sounds/worktable/convert.ogg` and `craft_0.ogg` … `craft_2.ogg`, with the `sounds.json` entries `worktable.convert` and `worktable.craft` naming `subtitles.vftv.worktable.convert` and `subtitles.vftv.worktable.craft`, no volume or pitch overrides. A contact sheet at GUI scale 2: the block beside vanilla's crafting table, and the screen (composed from the sheet and sprites, every preview state, a short cell and the beyond glyph) beside vanilla's crafting screen. Provenance rows. | none |
| **Arcane crafting content** | `content/arcane/recipes.json` (`vftv:arcane_recipes/1`, exactly `vftv:bell` under "Authored content, Recipes": its ingredients, count and costs chosen within those rules) and `content/lang/en_us/arcane.json` (`vftv:lang/1`) with every key under "Lang keys" | none |

[Wand and node content](https://github.com/andrewbrogoff/voices-from-the-void/issues/40) is unblocked by the recipe type's format. [Stage 1 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/34) and [Stage 2 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/35) consume `vftv:arcane_crafting`, `vftv:bell`, the fact fields and the affordability artifact above.
