# Mod compatibility

How the mod works beside other mods. This covers:
- the **integrations** v1 ships, the ones it defers, and the rules every integration follows;
- **JEI**: the one plugin, its lifecycle, the Striking category and the wand assembly in vanilla's crafting category;
- **Curios**, which the armor spec owns, and how its collector is wired;
- what **datapacks and scripting tools** may add, override and remove, and what they can't;
- **aspect values for modded items**: the `c:` tag defaults in play, and the operator's **derivation export**;
- **convention tags**, and data that names another mod;
- **other mods'** capabilities, claim mods, tooltip overlays and rendering mods;
- the **version ranges**, the **integration runs** in CI, and the tests.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md#soft-dependencies) for soft dependencies, the layers and the enforcement rules;
- the [quality gates](quality-gates.md) for the test layers, CI and the performance protocol;
- the [API spec](api.md) for the public data formats, the events and the stability promise;
- the [configuration spec](config.md#commands) for the `/vftv` command tree;
- the [aspects spec](aspects.md#vanilla-values) for the derivation this spec reuses;
- every system spec that hands an integration here. Each owner keeps its mechanics: [armor](armor.md#curios) owns Curios, and [arcane](arcane.md#jei), [crucible](crucible.md#jei) and [infusion](infusion.md#jei) own their JEI categories. This spec gathers them and owns what they handed off.

Resolved in [Mod compatibility spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/77). Inputs:
- the [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md), §§10–11 (Curios, JEI);
- the Modrinth version API, queried on 2026-10-15 for NeoForge builds of each mod on 26.2 ("Integrations").

**Source citations:**
- `JEI:` is `jei-26.2-common-api-30.38.0.230-sources.jar`, with `mezz/jei/api/` omitted.
- `JEIN:` is the matching NeoForge implementation jar, read as bytecode with `javap -p -c`, with `mezz/jei/` omitted.
- `CU:` is the Curios `16.0.0+26.2` sources, with `top/theillusivec4/curios/` omitted.
- `NF:`, `MC:` and `FML:` are as in the [configuration spec](config.md), with their package prefixes omitted.

## Integrations

### The rules

Every integration follows [architecture, Soft dependencies](architecture.md#soft-dependencies):
- **NeoForge is the only hard dependency.** Progression, every Stage find and all core content work with nothing else installed.
- **API jars only, at compile time.** Each integration is compiled against the other mod's API artefact and lives in its own `neoforge.compat.<mod>` package.
- **How each one is entered:**
  - **JEI** through its own `@JeiPlugin` annotation scan, so our code references the plugin nowhere;
  - **Curios** through one named entry class, called from the `@Mod` wiring behind `ModList.get().isLoaded("curios")` ("Curios").
- **No reflection, no mixin and no access transformer.**
- **A promise needs a verified API.** An integration is promised only on an API someone on this project has read, at a version CI pins.

### Supported and deferred

| Mod | 26.2 NeoForge builds (2026-10-15) | v1 |
|---|---|---|
| **JEI** | many, all beta; the API read is `30.38.0.230` | **supported**: `neoforge.compat.jei.client` ("JEI") |
| **Curios** | one release, `16.0.0+26.2` | **supported**: `neoforge.compat.curios`, specified by [armor](armor.md#curios) |
| REI | one beta, `26.2.820+neoforge` | **deferred** |
| EMI | none; the newest NeoForge build is `1.1.24+1.21.1` | **deferred** |
| KubeJS | none; the newest is `26.1.2-8.0.7`, a beta for 26.1.2 | **deferred**: no bindings ("Scripting tools") |
| TerraBlender | betas | **none**: no biome needs it ([worldgen, Options considered](worldgen.md#not-in-v1)) |
| Jade, WTHIT, The One Probe | Jade releases, a WTHIT beta | **no provider** ("Tooltip overlays") |
| Sodium, Iris | releases | **no code**; a nightly smoke ("Rendering mods") |

- **REI, EMI and KubeJS are deferred from v1 by the owner,** to a later minor once stable 26.2 builds exist. This is an owner-approved scope cut, recorded in the map's "Out of scope".
- **A later integration is additive.** It needs a new `compat.<mod>` package and this spec's amendment, and nothing it needs is JEI-specific:
  - the three authoritative previews ([arcane](arcane.md#the-authoritative-preview), [crucible](crucible.md#the-authoritative-preview), [infusion](infusion.md#the-authoritative-preview));
  - the recipe-visibility diff ("Lifecycle");
  - the public data formats.
- **Beta builds are accepted,** as the architecture spec allows.

## JEI

### The plugin

**One plugin class, `VftvJeiPlugin`,** annotated `@JeiPlugin`, in `neoforge.compat.jei.client`. It owns the whole JEI lifecycle. Each category's geometry, preview, status line and lang key stay with its owner.

| Category | Recipe type | Gate | Crafting station | Owner |
|---|---|---|---|---|
| Arcane Crafting | `vftv:arcane` | its `research` | the Worktable | [arcane](arcane.md#jei) |
| Crucible | `vftv:crucible` | its `research` | the crucible | [crucible](crucible.md#jei) |
| Infusion | `vftv:infusion` | its `research` | the Singing Bowl | [infusion](infusion.md#jei) |
| Striking | `vftv:strike` | none | the Tuning Fork | this spec ("Striking") |
| vanilla crafting | `vftv:wand_assembly`, a crafting recipe | none | JEI's own | this spec ("Wand assembly") |

**Registration** (`JEI:` verified):
- **Categories:** `registerCategories` adds the four custom categories through `IRecipeCategoryRegistration.addRecipeCategories`. Each is an `IRecipeCategory` with `getWidth` and `getHeight`, whose recipe type is `IRecipeHolderType.create` of our `RecipeType` (`JEI:recipe/category/IRecipeCategory.java:42–87`; `JEI:recipe/types/`).
- **Stations:** `registerRecipeCatalysts` registers the item each owner calls its category's catalyst as a crafting station, through `IRecipeCatalystRegistration.addCraftingStation` (`JEI:registration/IRecipeCatalystRegistration.java:42–95`). The deprecated recipe-type and catalyst methods are never used.
- **Slots:** `builder.addSlot` with the roles `INPUT`, `OUTPUT` and `CRAFTING_STATION` (`JEI:recipe/RecipeIngredientRole.java:18–24`). There is no catalyst role.
- **Recipes:** `registerRecipes` adds nothing for the custom categories. Their recipes arrive at runtime through the reconcile ("Lifecycle"), because a client's recipe snapshot may arrive before or after JEI's runtime.

**Recipe sync:** `neoforge` sends each of our recipe types through `OnDatapackSyncEvent#sendRecipes`, as the architecture spec requires. It also sends **`RecipeType.CRAFTING`**, because the wand assembly is a crafting recipe and JEI's crafting category draws it from the client's recipes.

### Lifecycle

Two kinds of state, with different owners:

| State | Owner | Cleared |
|---|---|---|
| each type's **client recipe snapshot** (replaced on each `RecipesReceivedEvent`) and the **learned set** | the owning features' client state | at logout only |
| the **`IJeiRuntime` handle** and, per custom category, the **set of recipe objects added to that runtime** | `VftvJeiPlugin` | in `onRuntimeUnavailable`, and at logout |

**Losing a runtime never clears a snapshot or the learned set.** JEI restarts its runtime on a recipe sync and on a resource reload (`JEIN:neoforge/startup/StartEventObserver.onRecipesReceivedEvent`, `.onResourceManagerReload`). Clearing the connection's state there would drop a snapshot that has just arrived, or a gate already learned.

**The diff.** `GatedRecipeDiff<K>` in **`core.research.model`** is Minecraft-free and generic over the recipe object type `K`.
- **Inputs:** the current snapshot (ordered by recipe id), the set added to this runtime, and a gate predicate.
- **Outputs, four lists:**
  - **add:** current objects not yet added;
  - **hide for good:** added objects no longer in the current snapshot. A same-id replacement counts here, because identity is the **object**, never the id;
  - **unhide:** current objects the gate passes;
  - **hide:** current objects the gate refuses.
- **How they relate:**
  - **hide for good** is disjoint from the current snapshot;
  - **hide** and **unhide** partition that snapshot;
  - **add** is a subset of the snapshot and may overlap either. A first addition is added, then hidden or unhidden in the same reconcile.
- **Applying** it is `IRecipeManager.addRecipes`, then `hideRecipes` for both hide lists, then `unhideRecipes` (`JEI:recipe/IRecipeManager.java:82–117`). Afterwards the added set is exactly the current snapshot.
- **Gates:** the gated types pass a recipe iff its `research` is in the learned set. Striking's gate is always true.

**When it runs,** per custom category:
1. **`onRuntimeAvailable`:** keep the runtime; hide every recipe of that category JEI already holds, since JEI may have collected some itself; then reconcile from the **current** snapshots and learned set. No later event is awaited.
2. **Each `RecipesReceivedEvent`:** after the owners replace their snapshots, reconcile if a runtime is held. JEI's own restart on the same event may come before or after ours; either order converges, because step 1 reconciles from whatever is current.
3. **Each research baseline and delta** (learn, grant, revoke): reconcile the three gated categories.
4. **A resource reload** restarts JEI with no new recipe or research event. Steps 6 and 1 restore the same state from the unchanged snapshots.
5. **Logout:** the owners clear their snapshots and the plugin clears its runtime state, so a join to another server starts clean.
6. **`onRuntimeUnavailable`:** drop the runtime and the added sets; nothing else.

**The crafting category is never touched.** The plugin never adds, hides or unhides a crafting recipe. The wand assembly is ungated, and vanilla's crafting recipes aren't ours.

**Bounded:** one reconcile is O(snapshot) for its category.
- The gated snapshots are capped by their owners (`MAX_ARCANE_RECIPES` and its crucible and infusion counterparts).
- **Striking's cap:** JEI shows strike recipes **up to the compatibility cap of 4,096**, the lowest ids first; the rest are omitted from JEI only.
  - Each over-cap snapshot logs **one `WARN`** naming its count. A reload that delivers a new snapshot is a new snapshot.
  - Gameplay still accepts every loaded strike recipe, shown or not.
- **Objects hidden for good** stay inside JEI only until its runtime ends, and JEI ends it on every recipe sync. So no runtime retains more than the snapshots it received.

### Striking

`vftv:strike` ([aspects, The Commonplace Book](aspects.md#the-commonplace-book)) is the one data-defined recipe type with no station of its own. Packs may add instances ([API, Data maps and other data](api.md#data-maps-and-other-data)), so it gets a category.
- **Size:** 82×18.
- **Slots:** the ingredient at (0, 0) as `INPUT`; the Tuning Fork at (32, 0) as `CRAFTING_STATION`, never consumed; the result at (64, 0) as `OUTPUT`.
- **Drawing:** JEI's own arrow, `IGuiHelper.getRecipeArrow()` (`JEI:helpers/IGuiHelper.java:127`), at (46, 1). The category icon is the Tuning Fork's item stack. No new asset.
- **Title:** `jei.vftv.category.strike`, "Striking". Its station is the Tuning Fork.

### Wand assembly

`vftv:wand_assembly` ([wands, Assembly](wands.md#assembly)) is a special crafting recipe in vanilla's `RecipeType.CRAFTING`, and its serializer id is not a recipe type of its own. **JEI's crafting category rejects special recipes** unless an extension handles them: `CraftingCategoryExtension.isHandled` checks `isSpecial()` before it reads the display (`JEIN:library/plugins/vanilla/crafting/CraftingCategoryExtension.isHandled`, bytecode 8–18). JEI's vanilla plugin selects `RecipeType.CRAFTING` holders and asks the category whether each is handled (`JEIN:library/plugins/vanilla/crafting/VanillaRecipes.getCraftingRecipes`).

- **The extension:** `registerVanillaCategoryExtensions` calls `getCraftingCategory().addExtension(WandAssemblyRecipe.class, extension)`.
  - It implements `getIngredients` from the recipe's shaped display, with width 1 and height 3.
  - It keeps the defaults: `isHandled` returns true, and `setRecipe` draws the display's result (`JEI:recipe/category/extensions/vanilla/crafting/ICraftingCategoryExtension.java:31–70`; `JEI:recipe/category/extensions/IRecipeCategoryExtension.java:69–70`).
- **No duplicate:** JEI's vanilla plugin then shows the one assembly recipe in its crafting category. **The plugin never adds it as well.**
- **What it shows:**
  - each slot lists every registered part item of its kind, cap, rod, cap, in ascending part id;
  - the result is the **representative wand** the display carries ([wands, Assembly](wands.md#assembly), amended in this change): rod `vftv:wooden`, cap `vftv:copper`, empty `vftv:vis`.

### Aspects and tooltips

The aspects spec's "JEI shows aspects only once the player knows them" needs **no JEI code**: it is our own item-tooltip listener ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)), and JEI's item tooltips run through it. An E2E screenshot proves it.

**Other viewers** can't draw our gated types, so they show none of them. Recipes synced to a client can be read by any client mod, as [aspects, Sync and state](aspects.md#sync-and-state) already accepts for its maps.

### Not in v1

Each of these is additive in a later minor:
- transfer handlers (the arcane spec has no "+" path);
- essentia as a JEI ingredient type;
- item subtype interpreters, so wands, foci and jars collapse to their base item;
- information pages;
- hiding items.

## Curios

**The [armor spec](armor.md#curios) is authoritative** for everything Curios does:
- the entities file and slot tags;
- the `ICurioItem` registrations and the `vftv:wearable/curios` base id;
- the collector's order, budget and cost;
- the reload refresh, and the off-hand and head-slot fallbacks;
- its tests.

The [warp spec's](warp.md#gear-warp) gear sample is the collector's other server caller. This spec adds only the wiring and the CI runs.

### Wiring the collector

The collector's callers sit outside `neoforge.compat.curios`, so they reach it through a loader-free callback, wired explicitly ([ADR 0002](../adr/0002-explicit-platform-wiring.md)):

- **`WornSlots`**, an interface in `neoforge.armor`, with no Curios type:
  - **`int collect(LivingEntity entity, int budget, WornSink sink)`:** the [collector's contract](armor.md#the-collector) exactly. Entries are `(slot id, index, cosmetic, stack)` through `WornSink`, also in `neoforge.armor`, whose call returns whether to continue.
  - **`void forEachWearableModifier(LivingEntity entity, BiConsumer<Holder<Attribute>, AttributeModifier> sink)`:** the reload refresh's effective query over **every** functional position ([armor, After a reload](armor.md#after-a-reload)), filtered to the `vftv:wearable/` ids and never truncated.
  - **`WornSlots.NONE`:** emits nothing and returns 0.
- **The entry class:** **`static WornSlots init(IEventBus modBus)`** on `CuriosCompat` registers the three `ICurioItem`s and returns a `CuriosWorn` instance, which implements `WornSlots`.
  - The declared return type is the interface, never `CuriosWorn`, so no confined type appears in the wiring's invocation descriptor.
- **The `@Mod` constructor** chooses `ModList.get().isLoaded("curios") ? CuriosCompat.init(modBus) : WornSlots.NONE` once, and passes the result to its three consumers:

| Consumer | Package | Without Curios (`NONE`) |
|---|---|---|
| the gear sample | `neoforge.warp` | the vanilla inventory, armour and off hand only |
| the spectacles' worn check | `neoforge.armor.client` | the vanilla head slot only |
| the reload refresh | `neoforge.armor` | the vanilla slots only |

- **The only reference** into `neoforge.compat.curios` from outside it is that one call (`COMPAT_CONFINED`, "Code layout").

## Datapacks and scripting tools

The [API spec](api.md#what-addons-may-extend) is authoritative for every format, its owner and its lifecycle. This section adds the **mechanics** a pack maker needs.

### What packs may change

| Surface | Add | Override | Remove or disable | Applies |
|---|---|---|---|---|
| the 14 datapack registries ([API, Datapack registries](api.md#datapack-registries)) | yes, except `vftv:chorister` | yes, by id | **never** a required built-in; a pack's own entries leave with the pack | when the world opens |
| the four data-map families, which are six maps: `vftv:aspects` on item, block and entity type, `vftv:aspect_defaults`, `vftv:gear_warp` and `vftv:wearable` | yes | the whole value; the last pack wins | NeoForge's data-map `remove` | `/reload` |
| every tag in a `*Keys` class | yes | `"replace": true` | the tag file's `remove` list (`MC:tags/TagFile.java:7–12`) | `/reload` |
| recipes of `vftv:strike`, `vftv:wand_assembly`, `vftv:arcane`, `vftv:crucible` and `vftv:infusion` | yes. A gated type's recipe names its `research`, as each owner requires; `vftv:wand_assembly` accepts only its one instance ([wands, Recipes](wands.md#recipes)) | the same id | a file at the same path holding `{"neoforge:conditions": [{"type": "neoforge:never"}]}` (`NF:common/NeoForgeMod.java:389–391`; `NF:common/conditions/NeverCondition.java:18–19`) | `/reload` |
| `data/vftv/tuning/*.json` | — | per each owner's validation | — | `/reload`; an invalid file keeps the previous record |
| worldgen, structures, loot | vanilla's rules | vanilla's rules | vanilla's rules | vanilla's |

**A removed or disabled entry** behaves as its owner states, and the API adds no rule of its own ([API, Lifecycle and ownership](api.md#lifecycle-and-ownership)). For example, a book page naming a disabled recipe is the [research spec's](research.md#entries) validator's case.

### What packs cannot change

- **Behaviour types:** no new recipe type or serializer, focus effect, medium or modifier type, Voice event type, research trigger or page type, `SlotDisplay` type or structure processor ([API, Code extension](api.md#code-extension)). Packs use the built-in type ids in data.
- **Golem jobs** need addon code ([API, Golem jobs](api.md#golem-jobs)).
- **Fixed limits:** the `*Limits` constants are code.
- **Config:** server and client config belong to the operator and player ([configuration spec](config.md#config-data-and-resources)).
- **The registries mid-world:** the aspect set and every other datapack registry are fixed when the world opens.
- **The Choristers:** exactly three; a pack overrides them and never adds or removes one.
- **Shared client model files** don't merge: the last resource pack wins ([API, Known limitations](api.md#known-limitations)).

### Scripting tools

**Passive support:** the promise is the **format**, not the tool.
- Anything that emits datapack JSON in our Stable formats works through the table above, with no code of ours and no runtime dependency: plain packs, KubeJS's custom-recipe JSON and data emission once it exists for 26.2, CraftTweaker.
- [API, Public data formats](api.md#public-data-formats) and the compatibility corpus already guard those formats.
- **No `compat.kubejs` package in v1.** Bindings arrive in a minor, as an amendment here, once a 26.2 KubeJS exists.

## Aspect values for modded items

### In play

**Gameplay lookup is unchanged** ([aspects, Data maps](aspects.md#data-maps)): a `vftv:aspects` entry, else `vftv:aspect_defaults` through its `c:` tags, else nothing ("Nothing answers.").
- **No runtime derivation in v1.** Modded items are covered by the `c:` tag defaults, by any pack's entries, and by the export below, whose output is an ordinary pack.
- **Why not at runtime:**
  - data maps can't be extended at runtime without reflection, so derived values would need a second synced table;
  - modded reversible conversions would amplify under minimum-1 rounding, and the crucible turns amplification into free essentia;
  - per-reload work in large packs is unbounded;
  - non-vanilla recipe types are opaque anyway.

### The derivation export

**`/vftv aspects derive`**, a **permission level 4** leaf under the level-2 root ([configuration spec, Commands](config.md#commands)). It writes files, as vanilla's level-4 `/perf` and `/jfr` do (`MC:server/commands/PerfCommand.java:38`; `MC:server/commands/JfrCommand.java:31`). It has no arguments, its paths are fixed, and no loaded-chunk rule applies, because it reads server data and no positions.

It runs the [aspects spec's](aspects.md#vanilla-values) derivation over the live server's data and writes **reviewable datapacks** that the operator installs by hand. Nothing it writes loads until then.

**1. Refusal while installed.** If any **selected** pack's id starts with `file/vftv_derived_`, the command refuses. A world datapack folder's id is `file/` plus its folder name (`MC:server/packs/repository/FolderRepositorySource.java:64–66`). The refusal gives `commands.vftv.aspects.derive.installed`, naming those ids, and writes nothing.
- Otherwise a live export would read its own earlier output as authored entries and leave those items out, so replacing the installed packs would silently drop them.
- The operator disables the derived packs, runs `/reload`, then derives again: **a full rebuild every time**.
- A renamed copy isn't recognised, and counts as authored data. The report says so.

**2. Accepted recipes.** A recipe holder is **accepted** iff its `Recipe` object's class is **exactly** one of vanilla's eight below (all in `MC:world/item/crafting/`), with **two exceptions** that narrow it further: `TransmuteRecipe`'s fixed-count rule, and the remainder rule.
- A subclass, or another class registered under a vanilla type, is rejected, because its semantics aren't vanilla's.
- **`TransmuteRecipe`** is accepted only when its decoded material-count interval is exactly `[1, 1]` and `add_material_count_to_result` is false. Otherwise its inputs or its result count vary with the grid (`MC:world/item/crafting/TransmuteRecipe.java:24–36,89–95,141–185`). The fields are read through the recipe's own codec, without reflection.

| Class | Ingredients | Result | Remainders | Smelting bonus |
|---|---|---|---|---|
| `ShapedRecipe` | each non-empty grid slot | its result item and count | each alternative's crafting remainder | no |
| `ShapelessRecipe` | each ingredient | as above | as above | no |
| `TransmuteRecipe` | its input and material | as above | as above | no |
| `SmeltingRecipe`, `BlastingRecipe`, `SmokingRecipe`, `CampfireCookingRecipe` | its one ingredient | as above | none | yes |
| `StonecutterRecipe` | its one ingredient | as above | none | no |

- **An ingredient** must be a vanilla item or tag ingredient: a NeoForge custom ingredient (`isCustom()`) rejects the recipe. Its **alternatives** are its items in ascending id; an ingredient with none rejects the recipe.
- **A result** must be non-empty with a count of at least 1. Components are ignored, as scans ignore them ([aspects, Coverage and supply](aspects.md#coverage-and-supply)).
- **A remainder** is `stack.getCraftingRemainder()` on a default stack of the alternative, NeoForge's stack-sensitive, nullable `ItemStackTemplate` accessor (`NF:common/extensions/ItemInstanceExtension.java:67–68`).
  - **The remainder rule:** an alternative with a remainder is accepted only when that remainder's item already has an effective **authored or default** value, `{}` included. Otherwise the whole holder is rejected as `unvalued remainder`, before the graph is built. A derived value never feeds a subtraction, so the graph needs no remainder edges.
  - **Subtraction:** rule 1 subtracts the remainder's value multiplied by its template count.
- **The adapter** from an accepted holder to the model's recipe record is one class in `core.aspect`, `DerivationRecipes`, and datagen's capture uses the same one.
- **Every rejected holder is counted** by reason: another type, another class, `variable transmute`, a custom ingredient, no alternatives, an empty result, `unvalued remainder`.
- **Rejected holders are outside the conservation promise** ("Conservation, by exclusion"), and the report lists their counts with that notice.
- **Datagen's coverage rule still applies** to vanilla and our own items. One whose only recipe this adapter rejects needs a hand override or a reviewed exemption ([aspects, Coverage and supply](aspects.md#coverage-and-supply)).

**3. Resolved items and candidates.**
- **Resolved:** an item with a `vftv:aspects` entry (`{}` included), or covered by a `vftv:aspect_defaults` tag.
- **A candidate** has **both** absent, is outside the `minecraft` and `vftv` namespaces, and is the result of at least one accepted recipe.
  - **`{}` is authored suppression,** never a gap: an item whose entry is `{}` is resolved, with an empty value, and is never derived over.
  - Vanilla's and our own coverage stays datagen's ([aspects, Coverage and supply](aspects.md#coverage-and-supply)).

**4. Conservation, by exclusion.**
- **The graph:** every item that appears as an alternative or a result of an accepted recipe is a node. Each accepted recipe adds an edge from each alternative of each ingredient to its result.
- **Excluded:** every candidate in a **non-trivial strongly connected component** (two or more items, or one item with an edge to itself). The components come from an iterative Tarjan pass, with no recursion. That covers every conversion cycle among accepted recipes, whatever its length. `1 A → 2 B → 2 C → 2 A` is excluded although no recipe pair inverts it.
- **Excluded candidates have no value,** so nothing downstream is computed from them.
- **The promise is exactly this:** no emitted value lies on a conversion cycle of accepted recipes. One-way gains from minimum-1 rounding remain, as in the [aspects spec's](aspects.md#vanilla-values) vanilla table. **Rejected recipes are outside the promise,** a modded machine that turns one ore into two dusts among them, so the report says the output **needs a human review** before it is installed.

**5. Values.**
- **Order:** the remaining candidates form an acyclic graph. They are evaluated once each, in topological order, with ties broken by ascending item id (Kahn's algorithm over a priority queue). A value is never revised.
- **Ingredients:** an ingredient is usable iff at least one alternative has a value. It takes the alternative with the lowest total, ties by item id. A recipe is usable iff every ingredient is.
- **Per item:** the aspects spec's derivation rules 1–7 ([aspects, Vanilla values](aspects.md#vanilla-values)) over the item's usable recipes, with `derivation.factor` and `derivation.smelt_bonus` from the loaded `scanning.json`. Rule 8, datagen's resolution order, which fails on a cycle, is replaced by the order above.
- **Unresolved:** a candidate with no usable recipe is listed with the first blocking item and its reason (`excluded`, `unresolved` or `no value`). An empty derived value isn't emitted, and is counted.

**6. Output.**
- **Staging:** everything is written to `<world>/vftv/derived.staging/`. The previous `<world>/vftv/derived/` is then renamed to `derived.previous`, staging is renamed to `derived`, and `derived.previous` is deleted, with `ATOMIC_MOVE` where the file system supports it.
- **On failure,** by phase. A **failure** gives `commands.vftv.aspects.derive.failed`, with the exception's message and the path where the previous complete export now is, and logs one `ERROR`.

| Phase that fails | Outcome |
|---|---|
| writing staging | staging is deleted; the previous export is unchanged at `derived` |
| the first rename (`derived` → `derived.previous`) | staging is deleted; the previous export is unchanged at `derived` |
| the second rename (staging → `derived`) | `derived.previous` is renamed back to `derived`, and staging is deleted. If that rename back also fails, the previous export stays complete at `derived.previous`, and the message names that path |
| deleting `derived.previous` after a successful swap | **a cleanup failure, not a failure:** the new export is complete and usable at `derived`. The feedback is `commands.vftv.aspects.derive.cleanup_failed`, naming the leftover, with one `WARN`. The next run deletes a leftover `derived.previous` before it starts |

- **One pack per source namespace,** `derived/vftv_derived_<namespace>/`, holding:
  - `pack.mcmeta`, encoded through `PackMetadataSection.SERVER_TYPE.codec()` with the running format `SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA)`;
  - `data/vftv/data_maps/item/aspects.json`, holding only that namespace's items.
- **Removing a mod** means deleting its folder. Even if the folder stays, NeoForge logs and skips each entry for a missing item id, and keeps the file's other entries (`NF:registries/DataMapLoader.java:119–128`). A GameTest keeps that as a regression check.
- **`derived/report.txt`**, plain text with these sections in order:
  - the inputs: both tuning values and the mod list, ids ascending with versions;
  - the counts: holders seen, accepted, rejected by reason, candidates, emitted, empty, excluded and unresolved;
  - every excluded id, with its component;
  - every unresolved id, with its blocking item and reason;
  - the review notice;
  - the renamed-copy notice.
- **Deterministic:** entries ascending by item id; aspects in the model's canonical order; fixed JSON formatting (two-space indent, `\n`); no timestamps. **Two runs over the same state are byte-identical.**

**7. Bounds.**
- **Limits:** at most **100,000** accepted recipes and **2,000,000** expanded alternative edges. They are counted during capture, and exceeding either refuses with `commands.vftv.aspects.derive.too_large`, writing nothing.
- **Cost:** O(R + E + V log V) for R recipes, E edges and V items: capture, one Tarjan pass, one topological pass, sorting.
- The export runs **synchronously on the server thread** ([architecture, Threading](architecture.md#threading)), like any operator command.
- These limits bound an operator command, not a data format. So they are fixed in code in `core.aspect`, not in `AspectLimits`, and may change in any minor.

## Convention tags

### Published

A `vftv` item joins a `c:` tag only where this table says. Adding a row is a minor change.

| Item | Tags | Key |
|---|---|---|
| `vftv:resonant_brass_ingot` | `#c:ingots` | NeoForge's `Tags.Items.INGOTS` |
| | `#c:ingots/resonant_brass` | our own Stable **`CrucibleKeys.RESONANT_BRASS_INGOTS`**: NeoForge has no constant for our material (`NF:common/Tags.java:687–691`) |

### Consumed

- **`vftv:aspect_defaults`** is keyed by `c:` tags ([aspects, Data maps](aspects.md#data-maps)).
- **Label cutting** takes `#c:tools/shear` ([essentia, Labels](essentia.md#labels)).
- **Recipe ingredients are widened at datagen,** so modded equivalents work.
  - When datagen writes one of our recipes, an ingredient that names exactly one item from the table below is written as that item's tag instead, unless the recipe id and field are exempt.
  - **Authored `content/` stays as each owner specifies it:** it is validated against the owner's schema **before** widening, and the widening is a generation step, never an edit to the schema.
  - Every owner's generated-output mapping says "its body unchanged except the compatibility spec's ingredient widening".

| Item | Tag (`Tags.Items` constant) |
|---|---|
| `minecraft:copper_ingot`, `iron_ingot`, `gold_ingot`, `netherite_ingot` | `INGOTS_COPPER`, `INGOTS_IRON`, `INGOTS_GOLD`, `INGOTS_NETHERITE` |
| `minecraft:iron_nugget`, `gold_nugget` | `NUGGETS_IRON`, `NUGGETS_GOLD` |
| `minecraft:diamond`, `emerald`, `amethyst_shard`, `quartz`, `lapis_lazuli` | `GEMS_DIAMOND`, `GEMS_EMERALD`, `GEMS_AMETHYST`, `GEMS_QUARTZ`, `GEMS_LAPIS` |
| `minecraft:redstone`, `glowstone_dust` | `DUSTS_REDSTONE`, `DUSTS_GLOWSTONE` |

- **All 13 constants exist** in NeoForge `26.2.0.88`'s `Tags.Items` (`NF:common/Tags.java`), and each shipped tag JSON holds exactly the vanilla item in its row. They are referenced in code, so a removed one fails compilation.
- **Exemptions,** by recipe id and ingredient field, each with a one-line reason in the datagen table, reviewed like code:

| Recipe | Field | Kept exact | Why |
|---|---|---|---|
| `vftv:resonant_brass_ingot` (`vftv:crucible`) | `catalyst` | `minecraft:copper_ingot` | the [crucible spec](crucible.md#resonant-brass) pins Resonant Brass as attuned copper, made from that catalyst |
| `vftv:hollow_bell` (`vftv:infusion`) | `components` | both `minecraft:amethyst_shard` | the [Eldritch tier spec](eldritch.md#void-tier-crafting) pins "exactly 2 `vftv:resonant_brass_ingot` and 2 `minecraft:amethyst_shard`" |

  An exemption ends only when its owner amends that contract to a tag.
- **Wand-part fields are not ingredients** ([wands, Parts](wands.md#parts)): `minecraft:copper_nugget` stays the copper cap's item.
- **What the widening changes, and what it doesn't:** it changes which items an ingredient accepts, and nothing else. Results, counts, gates and costs are unchanged.
- **Conservation over the widened tags:**
  - a GameTest asserts each table tag's contents in a vanilla-only world are exactly its item;
  - the derivation's lowest-total rule picks among actual tag alternatives (aspects' rule 2), so the generated vanilla table is unchanged;
  - and the aspects spec's non-amplification test replays over the **generated** recipes, after widening.
  - That is a guarantee for the shipped vanilla alternatives only, not for alternatives a pack adds to these tags.

### Data that names another mod

v1's data names no other mod's registry object. These rules, each with a datagen test, keep a future one inert when that mod is absent:
- **Recipes, loot tables and advancements**, which evaluate conditions, carry `{"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "<id>"}]}`.
- **Tags** name the object as an optional entry, `{"id": "<id>:<path>", "required": false}`. Tag loading decodes `TagFile.CODEC` directly and evaluates no top-level condition (`MC:tags/TagLoader.java:56–68`; `MC:tags/TagFile.java:7–12`).
- **Built-in data maps** name only `minecraft` and `vftv` registry objects. Their `#c:` tag keys are NeoForge's convention tags, which always exist.
- **The derivation export's** data maps name only other mods' items. Their removal path is the per-namespace folder ("The derivation export").

The Curios entities file and `curios:` tags name only our items, and are inert without Curios ([armor, Slots and eligibility](armor.md#slots-and-eligibility)).

## Other mods

### Capabilities

No new capability, and no bridge. Every owner has already decided its own:
- **Items:**
  - golems TAKE and GIVE through `Capabilities.Item.BLOCK` on **any** block ([golems, Roles](golems.md#roles));
  - the infusion pedestals and bowls expose it, registered explicitly ([infusion, The altar](infusion.md#the-altar));
  - **no item capability** on the Worktable, the crucible, the jars or a golem ([arcane, Multiplayer and automation](arcane.md#multiplayer-and-automation); [crucible](crucible.md#multiplayer-and-automation); [essentia](essentia.md#multiplayer-and-automation); [golems](golems.md#the-carry-ledger)).
- **Fluids:** none. The crucible takes buckets only.
- **Energy:** none. Nothing in v1 stores or consumes it.
- **Essentia:** only the Stable `vftv:essentia` and `vftv:essentia_suction` capabilities ([API, Capabilities and views](api.md#capabilities-and-views)).
  - **No bridge** to other mods' fluids or chemicals: a conversion needs its own conservation design.
  - An addon can build one on the public capability.

### Claim mods

**No integration with a named claim mod in v1,** and no named claim mod is promised compatible. The protection surface is NeoForge's vanilla-path events plus our three cancellable events ([API, Failure rules](api.md#failure-rules)). A claim mod integrates from its side, against the Stable API.

What is authorised, and what deliberately isn't:

| Activity | Authorised by | Not re-authorised |
|---|---|---|
| hand actions in every system | spawn protection, `mayInteract`, `RightClickBlock`, `BreakBlockEvent`, `EntityPlaceEvent`, as each owner lists | — |
| turning a Worktable | [arcane, Turning a table](arcane.md#turning-a-table)'s events | — |
| focus castings | [foci's protection promise](foci.md#multiplayer-and-automation), with `FocusAffectEntityEvent` per affected entity | callbacks inside vanilla's mining and placing keep vanilla's limits |
| nodes and relays | the hand paths, and a `mayInteract` recheck on every tap pulse ([wands, Creation, removal and sharing](wands.md#creation-removal-and-sharing)) | relay flow and cultivation |
| essentia lines | the hand paths ([essentia, Multiplayer and automation](essentia.md#multiplayer-and-automation)) | capability access: a line draws from an exposed jar or crucible |
| golems | Baton edits and placing; `GolemWorkEvent` before each act ([golems, Ownership and protection](golems.md#multiplayer-and-automation)) | running, and capability access |
| the Hush's spread | `HushSpreadEvent` with the seep's chunk and its **spiller** ([Hush, Claims](hush.md#claims)) | flux deposits |
| a Pealing Bell's restore | `HushSpreadEvent(RESTORE)` with origin **the bell**, and **no actor** | — |
| a Clearing Bell | `mayInteract` and `BreakBlockEvent` per cell, as its player | — |
| ruins | vanilla's events ([worldgen](worldgen.md#protection-and-multiplayer)) | — |

### Tooltip overlays

**No Jade, WTHIT or The One Probe provider in v1.**
- Reading what something holds is the instruments' job: the fork's card, the [Tuned Spectacles](armor.md#what-they-show) (a Stage 2 reward), the crucible HUD and jar labels.
- A provider showing aspects, vis or essentia would bypass per-player knowledge.
- **This spec makes no promise** about what those mods' own default providers show.

### Rendering mods

**No code.** Two pinned **nightly, non-gating** E2E configurations, under the [quality gates' Vulkan rule](quality-gates.md#graphics-backends): a red run is triaged within a week by whoever drives the build phase.

| Configuration | Mods |
|---|---|
| Sodium | Sodium `mc26.2-0.9.2-neoforge` |
| Sodium and Iris, shaders off | Sodium as above, and Iris `1.11.4+26.2-neoforge` with no shader pack enabled |

- **What each runs:** every rendering scenario with its state assertions. Screenshots are reviewed by an agent, and there is no `ERROR` from the `vftv` logger.
- **Shader packs are a known limitation:** the fullscreen warp pass and post chains ([architecture, Rendering](architecture.md#rendering)) aren't promised under one.

## Version ranges

Each integration is declared `optional` in `neoforge.mods.toml`. FML enforces an optional range only when that mod is installed (`FML:loading/ModSorter.java:304–306,378–383`), so an out-of-range install fails at load with FML's message instead of a linkage error in play.

```toml
[[dependencies.vftv]]
modId = "jei"
type = "optional"
versionRange = "[30.38.0.230,31)"
ordering = "NONE"
side = "CLIENT"

[[dependencies.vftv]]
modId = "curios"
type = "optional"
versionRange = "[16.0.0,17)"
ordering = "NONE"
side = "BOTH"
```

- **Lower bound:** the version we compile against and CI pins exactly: JEI `30.38.0.230`, Curios `16.0.0+26.2`, which Maven's version ordering places inside `[16.0.0,17)`.
- **Upper bound:** the next major.
- **A bump** is a reviewed PR that passes every integration run ([quality gates, CI](quality-gates.md#ci)).
- **Sodium and Iris** are not declared: no code depends on them.

## Code layout

| Package | Holds |
|---|---|
| `neoforge.compat.jei.client` | `VftvJeiPlugin`; the four custom categories, whose geometry their owners specify; the wand-assembly extension |
| `neoforge.compat.curios` | `CuriosCompat`, the entry class; `CuriosWorn`; the `ICurioItem` registrations ([armor](armor.md#curios)) |
| `neoforge.armor` | `WornSlots`, `WornSink` |
| `core.research.model` | `GatedRecipeDiff` |
| `core.aspect` | the `aspects derive` subtree, built from vanilla Brigadier types; the export service; `DerivationRecipes` |
| `core.aspect.model` | the derivation graph: the components, the exclusion and the topological evaluation |

**`COMPAT_CONFINED`** ([architecture, Enforcement](architecture.md#enforcement), amended in this change):
- `mezz.jei..` is used only in `neoforge.compat.jei..`, and `top.theillusivec4.curios..` only in `neoforge.compat.curios..`.
- No class outside a `neoforge.compat.<mod>` package references a class inside it, except one named entry class per mod, called from the `@Mod` wiring: `CuriosCompat`. JEI has none.
- Review checks that the call sits behind `isLoaded`.

## Performance

**No hot path of its own, and no additional share** of the [whole-mod envelope](quality-gates.md#whole-mod-envelope):
- **JEI drawing** is charged to the owner of each category, with a view open:
  - arcane, crucible and infusion as their specs state;
  - **Striking to the [aspects spec's](aspects.md#performance) client share**, amended in this change.
- **The reconcile** runs on arrivals, never per frame. It is O(snapshot) per category, bounded as under "Lifecycle".
- **Curios's traversal** is the armor spec's disclosed native term.
- **The derivation export** is an operator **load scenario**, reported apart from steady state: a synthetic pack of 50,000 accepted recipes and 500,000 edges, timed on the reference machine, with the tick it runs in disclosed. It is report-only, and the limits under "The derivation export" cap the work.

## Lang keys

This spec owns these keys. Its English is final: datagen writes them as code-defined UI keys into `assets/vftv/lang/en_us.json`, as the [configuration spec's](config.md#lang-keys) are written, so no `content/` file and no production ticket is needed.

| Key | Placeholders | Text |
|---|---|---|
| `jei.vftv.category.strike` | 0 | "Striking" |
| `commands.vftv.aspects.derive.success` | 3 | "Derived %1$s values into %2$s packs at %3$s. Review report.txt before installing any." |
| `commands.vftv.aspects.derive.installed` | 1 | "Derived packs are enabled: %1$s. Disable them and run /reload, then derive again." |
| `commands.vftv.aspects.derive.too_large` | 4 | "Too much to derive: %1$s recipes and %2$s ingredient links, where the limits are %3$s and %4$s." |
| `commands.vftv.aspects.derive.failed` | 2 | "The export could not be written (%1$s). The previous export is at %2$s." |
| `commands.vftv.aspects.derive.cleanup_failed` | 3 | "Derived %1$s values into %2$s packs, but %3$s could not be removed. The next run removes it." |

## Tests

**Unit:**
- **`GatedRecipeDiff`,** as a pure diff:
  - every arrival order of runtime, snapshot and learned set;
  - a same-id replacement, which is hidden for good while the new object is added;
  - a removed recipe;
  - a revoke and a grant;
  - an empty snapshot;
  - **a first addition with each gate result:** with nothing added, a learned recipe is in **add** and **unhide**, and an unlearned one in **add** and **hide**;
  - **a same-id replacement by a distinct object:** the old object is in **hide for good** and not in the snapshot, while the new one is in **add**;
  - on every case: **hide for good** is disjoint from the snapshot, **hide** and **unhide** partition it, and **add** is a subset of it.
- **Striking's cap**, over a 4,097-recipe snapshot with shuffled ids:
  - exactly the lowest 4,096 ids are shown;
  - a reload's replacement snapshot is reselected;
  - **one `WARN` per over-cap snapshot**, none for a snapshot at 4,096.
- **The derivation graph:**
  - a two-recipe inverse pair, the three-step loop `1 A → 2 B → 2 C → 2 A` and a self-edge are each excluded;
  - a candidate fed only by an excluded item is unresolved, with that reason;
  - a diamond-shaped acyclic graph evaluates in id order whatever the input order;
  - an alternative ingredient takes the lowest-total valued alternative;
  - `{}` is resolved and never a candidate; a defaults-covered item is never a candidate;
  - the limits refuse at exactly limit + 1;
  - Tarjan's pass over a 100,000-node chain, with no stack overflow.
- **`DerivationRecipes`:**
  - each accepted class; a subclass of `ShapedRecipe` rejected; a custom ingredient rejected; the cooking classes' smelting bonus;
  - **`TransmuteRecipe`:** material count `[1, 1]` without an added result count accepted; a fixed eight-material recipe, a material range and an added result count each rejected and counted as `variable transmute`;
  - **remainders:**
    - a resolved remainder with template count 2 subtracts twice its value;
    - a `{}` remainder subtracts nothing and is accepted;
    - a remainder that is itself a candidate rejects the holder as `unvalued remainder`, before the graph is built, and the report lists it among recipes outside the promise.
- **The report and pack writer:** byte-identical output for the same input.

**ArchUnit:** `COMPAT_CONFINED`, with a fixture for each forbidden direction:
- a JEI type outside its package;
- a class outside `neoforge.compat.curios` referencing `CuriosWorn`;
- **a wiring call to an `init` declared to return `CuriosWorn`,** which puts the confined type in the invocation descriptor;

and the permitted call, `static WornSlots init(IEventBus)`, from the wiring.

**GameTest (`runGameTestServer`, no soft dependency):**
- **convention tags:**
  - each widened tag's vanilla-only contents is exactly its item;
  - no generated recipe of ours names a table item, except in an exempt recipe id and field;
  - **the exemption fixture:**
    - with a test-only item added to `#c:ingots/copper` and `#c:gems/amethyst`, `vftv:resonant_brass_ingot` refuses it as a catalyst, and `vftv:hollow_bell`'s components refuse it;
    - a non-exempt widened ingredient accepts it;
    - each recipe's result, count, gate and cost are unchanged;
  - `#c:ingots/resonant_brass` and `#c:ingots` hold the ingot;
- **data that names another mod:** the datagen tests above;
- **a disabled recipe:** a `neoforge:never` override removes one of our recipes, and its owner's behaviour follows;
- every test id in the discovery guard.

**GameTest (`runCompatGameTestServer`,** which loads `vftv_addon` and so has its registered items):
- **capabilities:** a golem TAKEs from and GIVEs to a `vftv_addon` block that exposes a plain `ResourceHandler<ItemResource>`.
- **Striking's cap:** with a `vftv_addon` pack of 4,097 strike recipes, the one JEI omits still converts in play.
- **the export,** over `vftv_addon` items and recipes:
  - the files equal a committed golden copy;
  - a level-2 source can neither run nor see the leaf;
  - an enabled `file/vftv_derived_…` pack refuses;
  - the round trip: export → install → `/reload` → the values read back through `AspectDataMaps.ITEM` → disable → `/reload` → export again gives byte-identical files;
  - `{}` and defaults-covered items are never emitted;
  - unresolved, cyclic, `variable transmute` and `unvalued remainder` recipes are listed or counted;
  - **failures, by phase:**
    - writing staging, the first rename, and the second rename (with and without a failed rename back) each leave a complete previous export where the message says, and log one `ERROR`;
    - a failed delete of `derived.previous` is a cleanup failure: the new export is complete and usable, one `WARN`, and the next run removes the leftover;
  - the over-limit refusal writes nothing;
  - an entry for an unregistered item is skipped while the file's other entries load (`NF:registries/DataMapLoader.java:119–128`), a regression check;
- each test id in that run's discovery guard.

**GameTest (`runCuriosGameTestServer`, Curios `16.0.0+26.2` present):**
- the [armor spec's Curios integration tests](armor.md#tests);
- the [warp spec's](warp.md#tests) gear sample counting Curios stacks;
- each test id in its own discovery guard.

**E2E, JEI present** (JEI `30.38.0.230`, plus Curios for the wearable-dependent previews):
- **the owners' JEI scenarios:** [arcane](arcane.md#tests), [crucible](crucible.md#tests), [infusion](infusion.md#tests);
- **the lifecycle**, each with a dump of what JEI shows:
  - a recipe event before and after JEI's own restart;
  - a resource reload (`F3+T`) with no new recipe or research event;
  - a join with research already learned;
  - a same-id recipe replaced by `/reload`;
  - logout, then a join to another server;
- **the categories:**
  - the Striking category with the Commonplace Book conversion; with the 4,097-recipe pack, the lowest 4,096 ids shown;
  - the wand assembly in JEI's crafting category **once**, showing the representative wand;
  - unrelated vanilla crafting recipes still visible after every grant and revoke;
  - aspects in a JEI tooltip only after a scan.

**E2E, Curios present on the client:**
- the spectacles in a functional head slot show the card, and in a cosmetic one they don't;
- after a `/reload` that changes `vftv:wearable`, a ring in a Curios slot keeps the right Baffle row.

**E2E, absent:** every ordinary scenario runs with no soft dependency; the "without JEI" twins are the owners'.

**Nightly, non-gating:** the two rendering configurations under "Rendering mods".

## Contracts on sibling specs

Each spec is amended in this change, as listed. Each amendment is a criterion in that system's build ticket.

- **[Architecture](architecture.md):**
  - "Layers": the example is `(curios, jei)`;
  - "Soft dependencies": the integrations are Curios and JEI, with a pointer here, and the entry rule;
  - "Datagen": no TerraBlender regions, and Curios files written by datagen;
  - "Enforcement": `COMPAT_CONFINED` as under "Code layout".
- **[Quality gates](quality-gates.md):**
  - "Shipping artifact": the integration runs every PR;
  - the `build` job runs `runCuriosGameTestServer`;
  - the `e2e` job runs the JEI-present and Curios-present clients;
  - "Graphics backends": the two nightly rendering configurations.
- **[Aspects](aspects.md):**
  - "Vanilla values": the in-play rule, `{}` as suppression, no runtime derivation, the export, and `DerivationRecipes` shared;
  - "Tuning": the `derivation.*` keys feed datagen and the export;
  - "Performance": Striking's drawing in the client share;
  - "Tests": the non-amplification replay over the widened recipes.
- **[Arcane](arcane.md):**
  - "JEI": the diff in `core.research.model`, and the lifecycle here;
  - "Tests": the diff and the E2E lifecycle cases;
  - "Turning a table": the claim-mod pointer.
  - [Crucible](crucible.md#jei) and [infusion](infusion.md#jei) inherit it unchanged.
- **[Wands](wands.md):**
  - "Assembly": the representative result, and JEI through the crafting-category extension, not a category;
  - "Creation, removal and sharing": the claim-mod pointer.
- **[Golems](golems.md), [Hush](hush.md), [worldgen](worldgen.md), [foci](foci.md):** their claim-mod hand-offs point to "Claim mods". In worldgen, `neoforge.compat.terrablender` doesn't exist.
- **[Armor](armor.md):**
  - "The collector": wired through `WornSlots`;
  - "Layers": the wiring;
  - "Tests": the Curios runs every PR.
- **[Configuration](config.md):** "Commands" gains the level-4 leaf `aspects derive` and its lang ownership.
- **[API](api.md):**
  - "What addons may extend" points here for the mechanics and scripting tools;
  - "Known limitations": v1 ships a JEI integration only;
  - "The inventory": `CrucibleKeys.RESONANT_BRASS_INGOTS`.
- **[Arcane](arcane.md#authored-content), [crucible](crucible.md#authored-content), [infusion](infusion.md#authored-content), [armor](armor.md#authored-content), [essentia](essentia.md#authored-content), [golems](golems.md#authored-content), [Hush](hush.md#authored-content), [Eldritch tier](eldritch.md#authored-content):** each recipe's generated-output mapping reads "body unchanged except the compatibility spec's ingredient widening". The crucible's `vftv:resonant_brass_ingot` catalyst and the Eldritch tier's `vftv:hollow_bell` components are exempt and stay exact ("Consumed").
- **Unchanged:** warp (the gear sample's allowance and order stand; only the collector's wiring is armor's; its glass-bottle draught is outside the widening table), wands' and foci's recipe mappings (they don't promise an unchanged body), research, `CONTEXT.md`, every ADR and `NOTICE.md`.

## Production tickets

**None.** No integration icon is needed: category icons are existing item stacks, and the arrow is JEI's. Every string is final under "Lang keys" and written by datagen. The build ticket for compatibility delivers:
- the JEI plugin and the Striking category;
- the wand-assembly extension;
- `GatedRecipeDiff`;
- the `WornSlots` wiring;
- the export with its graph;
- the tag widening and the data rules;
- `COMPAT_CONFINED`;
- the two optional dependencies;
- the integration runs, and every test above.

The owners' JEI categories and the Curios layer stay in their own build tickets.
