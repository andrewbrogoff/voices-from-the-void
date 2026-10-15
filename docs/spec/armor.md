# Armor and trinkets

What a Listener wears. This covers:
- the **Tuned Spectacles**, which show what the wearer has already struck about whatever they look at;
- **wearables**: the one data map that gives every worn item its protection, vis discount and Baffle;
- **vis-discount gear**: the Felted garments, the Chime Pendant and the Void set, through the wands spec's one attribute;
- the **Baffle**, the renamed runic shield: a layer of resonance that soaks up blows before health and comes back after a quiet spell, and **Baffling**, the infusion-only enchantment that adds to it;
- **trinkets** and Curios, and how trinkets work without Curios;
- **Brass armour** (Stage 2) and the **Void set** (Stage 4), with its gear warp and its warp-scaled Baffle.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the soft-dependency rules, the data and sync rules and the seams;
- the [quality gates](quality-gates.md) for every gate;
- the [lore](lore.md) and [progression](progression.md) specs for naming, the kept systems and "instruments never lie";
- the [aspects spec](aspects.md) for scan keys, knowledge, the unheard rule and the strike card;
- the [research spec](research.md) for entries, gates, fact spans and authored content;
- the [wands spec](wands.md) for the quote, `vftv:vis_discount`, node state and `VisRules`;
- the [arcane](arcane.md) and [infusion](infusion.md) specs for the recipe types this spec's recipes use, and enchanting through infusion;
- the [warp spec](warp.md) for gear warp, `amplifier_warp` and the Void-tongue lexicon;
- the [art style](art-style.md) for every asset.

Resolved in [Armor and trinkets spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/24). Inputs: [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md) (§1.2, §6), [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md) (§2), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md) (§10 Curios).

**Every number in this spec is ours.** None is taken from TC4: its set protections, durabilities, per-piece discounts, goggle discount, shield amounts and per-piece warping were not reused, and nothing here is a TC4 ratio rescaled. Each tunable number is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content tickets set the real ones.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port that uses Java `float`, `double` and `long` arithmetic as the mod will. Both print the same 60 lines (3,701 bytes, SHA-256 `7eb9a5b257dbdae293a4a4d71efda75fec983db7a47b2ccb99559640cc4dd2a7`). Baffle vectors use halves, which `float` and `double` hold exactly. The references cover arithmetic only; synchronisation, ordering against other listeners and Curios behaviour are proved by the GameTests (see "Tests").

**Source citations:** `MC:` is the `minecraft-patched-26.2.0.88` sources (with NeoForge's patches to vanilla classes), `NF:` the NeoForge `26.2.0.88` sources, and `CU:` the Curios `16.0.0+26.2` sources, each path relative to `net/minecraft/`, `net/neoforged/neoforge/` or `top/theillusivec4/curios/`. `NFP:` cites the matching NeoForge userdev jar's `patches/net/minecraft/client/gui/Hud.java.patch`, by patch-file line.

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 1 Resonance | the Tuned Spectacles | the Stage is opened by the amethyst geode ([research, Stage](research.md#stage)); `vftv:tuned_spectacles` follows `vftv:arcane_crafting` |
| 2 Distillation | Brass armour, the Felted garments, the Chime Pendant | the opener `vftv:distillation` ([Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s find), through `vftv:resonant_brass` |
| 3 Ruins | the Baffle Ring, Baffling | the opener `vftv:ruins` (#27's find), through `vftv:infused_enchanting` |
| 4 The Choir's Road | the Void set | the opener `vftv:choirs_road` ([Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)'s find) |

Five entries, authored by the research content tickets:

| Entry | Stage | Kind | Parents | Gates |
|---|---|---|---|---|
| **`vftv:tuned_spectacles`** | 1 | `note` | `vftv:arcane_crafting` | the arcane recipe `vftv:tuned_spectacles` |
| **`vftv:brass_armor`** | 2 | `free` | `vftv:resonant_brass` | nothing: it teaches the four **ungated** crafting recipes `vftv:resonant_brass_helmet`, `_chestplate`, `_leggings`, `_boots` |
| **`vftv:felted_garments`** | 2 | `note` | `vftv:resonant_brass`, `vftv:tuned_spectacles` | the arcane recipes `vftv:felted_coat`, `vftv:felted_breeches`, `vftv:felted_boots`, `vftv:chime_pendant` |
| **`vftv:baffling`** | 3 | `note` | `vftv:infused_enchanting`, `vftv:brass_armor` | the arcane recipe `vftv:baffle_ring`; the infusion recipe `vftv:armor/baffling` |
| **`vftv:void_armor`** | 4 | `note`, **secret**: `reveal` `[{"type": "vftv:warp_at_least", "kind": "lasting_plus_permanent", "value": 4}]`, `hinted_by` `vftv:baffling` | `vftv:choirs_road`, `vftv:baffling` | the infusion recipes `vftv:void_helmet`, `vftv:void_chestplate`, `vftv:void_leggings`, `vftv:void_boots` |

- **Brass armour is ungated vanilla crafting.** No hook gates the vanilla crafting grid ([wands, Stage](wands.md#stage)), and the ingot already is: its crucible recipe needs `vftv:resonant_brass` ([crucible, Resonant Brass](crucible.md#resonant-brass)). A Worktable recipe would add a vis cost with no decision behind it.
- **The Void reveal is proved by Stage 3.** `vftv:silence_beneath` commits lasting 4 ([warp, Stage](warp.md#stage)), so the reveal latches during the Stage 3 replay, before Stage 4 opens, and stays latched whatever is stilled later ([research, Triggers](research.md#triggers)). It is the research roster's rule for Void-tier crafting, and it meets the [warp spec's](warp.md#permanent-warp) ordered proof: warp committed before the gate pays for it, with no permanent warp and no Bargain.
- **Obtaining counts.** Wearing needs no research. Void armour handed to a Stage 1 Listener gives gear warp, which the [warp spec](warp.md#stage) already accepts.
- **Stage 1 affordability:** the spectacles' arcane cost is at most 32 per aspect, from pre-iron ingredients (a content rule), so they pass the [arcane](arcane.md#contracts-on-sibling-specs) affordability test. They are optional and on no affordability path.
- **Options considered:** the spectacles at Stage 2, where TC4's goggles sat. **Why Stage 1:** they grant no points and no knowledge ("Tuned Spectacles"), so they cannot distort the early economy, and they reward the strike habit the first hour teaches.

## The roster

Fourteen items. **Attribute numbers** (armour, toughness, discount, Baffle) live in the data map `vftv:wearable` ("Wearables"); **registration properties** are fixed when items register, as vanilla's are.

| Item | Slot | Armour | Toughness | Discount | Baffle | Gear warp | Stack | Durability | Ench. | Repair |
|---|---|---|---|---|---|---|---|---|---|---|
| `vftv:tuned_spectacles` | head | 0 | 0 | 0.03 | 0 | — | 1 | none | — | — |
| `vftv:felted_coat` | chest | 2 | 0 | 0.04 | 0 | — | 1 | 144 | 18 | `#minecraft:wool` |
| `vftv:felted_breeches` | legs | 1 | 0 | 0.03 | 0 | — | 1 | 135 | 18 | `#minecraft:wool` |
| `vftv:felted_boots` | feet | 1 | 0 | 0.02 | 0 | — | 1 | 117 | 18 | `#minecraft:wool` |
| `vftv:chime_pendant` (trinket) | off hand; Curios `necklace` | 0 | 0 | 0.02 | 0 | — | 1 | none | — | — |
| `vftv:resonant_brass_helmet` | head | 2 | 1.0 | 0 | 0 | — | 1 | 220 | 16 | `vftv:resonant_brass_ingot` |
| `vftv:resonant_brass_chestplate` | chest | 7 | 1.0 | 0 | 0 | — | 1 | 320 | 16 | `vftv:resonant_brass_ingot` |
| `vftv:resonant_brass_leggings` | legs | 5 | 1.0 | 0 | 0 | — | 1 | 300 | 16 | `vftv:resonant_brass_ingot` |
| `vftv:resonant_brass_boots` | feet | 2 | 1.0 | 0 | 0 | — | 1 | 260 | 16 | `vftv:resonant_brass_ingot` |
| `vftv:baffle_ring` (trinket) | off hand; Curios `ring` | 0 | 0 | 0 | 3 | — | 1 | none | — | — |
| `vftv:void_helmet` | head | 3 | 2.0 | 0.04 | 1 | 2 | 1 | 286 | 12 | `#vftv:repairs_void_armor` |
| `vftv:void_chestplate` | chest | 8 | 2.0 | 0.06 | 1 | 3 | 1 | 416 | 12 | `#vftv:repairs_void_armor` |
| `vftv:void_leggings` | legs | 6 | 2.0 | 0.04 | 1 | 2 | 1 | 390 | 12 | `#vftv:repairs_void_armor` |
| `vftv:void_boots` | feet | 2 | 2.0 | 0.02 | 1 | 2 | 1 | 338 | 12 | `#vftv:repairs_void_armor` |

- **Durability** is vanilla's unit per piece (helmet 11, chestplate 16, leggings 15, boots 13; `MC:world/item/equipment/ArmorType.java`) times a multiplier: Felted 9, Brass 20, Void 26 (vectors: Felted 144/135/117; Brass 220/320/300/260; Void 286/416/390/338). Every Void piece outlasts its Brass centre, so damage carried by the transmute stays valid ("Void armour").
- **The Stage 2 choice:** Brass protects (16 armour in all, between iron's 15 and diamond's 20, `MC:world/item/equipment/ArmorMaterials.java`); Felted makes magic cheaper (0.09) with little protection; without Curios the spectacles compete with a helmet for the head. The Void set joins both at a warp price.
- **Registration properties** (fixed in code, asserted by a unit test against this table): durability, enchantment value, the `Equippable` slot, equip sound and equipment asset (`MC:world/item/Item.java:583–591` shows vanilla's `humanoidArmor` wiring the same parts), the `REPAIRABLE` tag, stack size and allowed entities. **Our items carry no code `ATTRIBUTE_MODIFIERS`**: `Properties.attributes` would be a second home for the same numbers (`Item.java:715–717`).
- **The spectacles** are `Equippable` in the head slot with `damage_on_hurt` false (`MC:world/item/equipment/Equippable.java:230`) and no durability: lenses don't wear out. Trinkets are not equippable at all.
- **Allowed entities:** every equippable here sets `allowed_entities` to `minecraft:player` and `minecraft:armor_stand` (`Equippable.java:175–176`). Mob pickup checks it (`MC:world/entity/Mob.java:560–563` through `LivingEntity#isEquippableInSlot`, `MC:world/entity/LivingEntity.java:4050–4054`), so zombies don't pick these up. That is ordinary pickup only: a command or an addon can still equip a mob, and then any of our attributes the mob lacks are skipped (`LivingEntity.java:3078–3084`; `MC:world/entity/ai/attributes/AttributeMap.java:73–87`). The Baffle stays players-only ("The Baffle").
- **Equip sounds** are vanilla's: `item.armor.equip_leather` (Felted), `item.armor.equip_copper` (Brass), `item.armor.equip_netherite` (Void), `item.armor.equip_generic` (spectacles).
- **Equipment assets** (`MC:client/resources/model/EquipmentAssetManager.java`; layers resolve to `textures/entity/equipment/<layer>/<path>.png`, `MC:client/resources/model/EquipmentClientInfo.java:106–107`): `vftv:felted`, `vftv:resonant_brass` and `vftv:void` with `humanoid`, `humanoid_leggings` and `humanoid_baby` layers, and `vftv:tuned_spectacles` with `humanoid` and `humanoid_baby`. Vanilla's own layers are 64×32 (`humanoid`, `humanoid_leggings`) and 64×64 (`humanoid_baby`), and ours match.
- **Tags:** the armour pieces join `#minecraft:head_armor`, `#minecraft:chest_armor`, `#minecraft:leg_armor` or `#minecraft:foot_armor`, the matching `#minecraft:enchantable/*_armor`, and `#minecraft:trimmable_armor` (vanilla trims, no asset of ours); the Void pieces `#vftv:void_armor`; the spectacles and `vftv:void_helmet` `#vftv:reveals_aspects`. The spectacles are in no armour tag, so lenses take no Protection. Every membership is authored in `content/armor/tags.json` ("Authored content").
- **Options considered:** dyeable Felted garments (TC4's robes were). **Why not:** an overlay layer per piece and a dye tag buy no decision.

## Wearables

**Data map `vftv:wearable`** on items, synced, so every attribute number has a data home ([architecture, Data and code](architecture.md#data-and-code)):

| Field | Required | Range | Meaning |
|---|---|---|---|
| `slot` | yes | `head`, `chest`, `legs`, `feet`, `offhand` | where the item counts in vanilla slots |
| `armor` | no, default 0 | 0–20, integer | `minecraft:armor` |
| `toughness` | no, default 0 | 0.0–10.0, at most 1 decimal place | `minecraft:armor_toughness` |
| `knockback_resistance` | no, default 0 | 0.00–1.00, at most 2 decimal places | `minecraft:knockback_resistance` |
| `vis_discount` | no, default 0 | 0.00–0.90, at most 2 decimal places | `vftv:vis_discount` ([wands](wands.md#the-quote)) |
| `baffle` | no, default 0 | 0–20, integer | `vftv:baffle` ("The Baffle") |

**Validation runs in two places**, because NeoForge passes only the value to a data map's codec, never the item that owns it (`NF:registries/datamaps/DataMapEntry.java:21–26`; `DataMapType.java:79–86`):
1. **The value codec** checks structure and ranges: an unknown field, a value out of range or with more places, every numeric field 0. A data-map file decodes through a strict map (`NF:registries/datamaps/DataMapFile.java:47–50`) whose result is taken with `getOrThrow()` inside a per-resource catch (`DataMapLoader.java:166–177`), so **one bad value discards that whole resource file**, logged; the other pack layers' files for the same map still load. It is not a rollback of the reload.
2. **The validated view** checks the item: `slot` must equal the item's `Equippable` slot, and `offhand` is allowed only on an item that isn't `Equippable`. After each load, `core.armor` post-processes the resolved item → entry map into an immutable **validated view** that **excludes** each incompatible entry with one error naming the item, and keeps the rest. The view is rebuilt on `DataMapsUpdatedEvent` for the item registry, with cause `SERVER_RELOAD` on the server, `CLIENT_SYNC` on a remote client, and in an integrated world on the server's `SERVER_RELOAD` through the client flag ("After a reload"). **Every consumer reads the view, never the raw map:** the modifier listener, the Curios hook, the reload refresh, tooltips, the fact source and the content lint.

Datagen rejects a slot mismatch outright, so built-in content never reaches step 2.

### Applying

- **A thin `neoforge.armor` listener on `ItemAttributeModifierEvent`** (`NF:event/ItemAttributeModifierEvent.java:27–34,81–83`, fired from `NF:common/extensions/IItemStackExtension.java:359–375` for `MC:world/item/ItemStack.java:1096–1117`, and reached by tooltips through `NF:common/util/AttributeUtil.java:298–306`) adds, for a stack whose item has an entry in the validated view, one `add_value` modifier per non-zero field, in the entry's slot group, with id **`vftv:wearable/<slot>`**. It works without any stored attribute-modifier component. The conversion from entry to modifiers is `core.armor`'s; the listener only extracts and applies.
- **One id per slot** means swapping pieces replaces, never stacks, as vanilla's `minecraft:armor.<type>` ids do (`MC:world/item/equipment/ArmorMaterial.java`).
- **Vanilla applies** these on every equipment change. **Curios applies** them in its slots ("Curios").

### After a reload

Vanilla re-applies modifiers only on an equipment change, so a `/reload` that changes `vftv:wearable` would leave worn values stale, and a field the reload removed would never be taken off.

1. **Queue:** `DataMapsUpdatedEvent` (`NF:registries/datamaps/DataMapsUpdatedEvent.java:80–93`) for the item registry with cause `SERVER_RELOAD` sets a pending flag. It fires **during** reload application, before Curios reconciles its slots (`CU:common/CuriosCommonEvents.java:193–210`), so nothing is rebuilt inside it.
2. **Refresh** at the next server `ServerTickEvent.Post`, once, for each online player:
   - remove, from `minecraft:armor`, `minecraft:armor_toughness`, `minecraft:knockback_resistance`, `vftv:vis_discount` and `vftv:baffle`, **every modifier whose id is in namespace `vftv` with a path starting `wearable/`**: ours in vanilla slots and the ids Curios derived from our base id alike;
   - re-add through the **effective** query paths, so other mods' modifier events still apply: for each vanilla slot that our map can name (head, chest, legs, feet, off hand), the stack's `forEachModifier` filtered to our ids; for **every** active functional Curios position, Curios's own attribute query for that position's stack, filtered the same way ("Curios"). This is a **full traversal**, never cut at `MAX_GEAR_SLOTS`: everything the refresh removed must come back, and Curios applies modifiers in every position however many there are (`CU:common/CuriosCommonEvents.java:491–580`; slot sizes have no such ceiling, `CU:common/slot/SlotType.java:210–218`);
   - a stack whose query throws is logged once and skipped; the rest of the refresh runs.
3. **Clients** rebuild the validated view, tooltips, the spectacles card and the Baffle HUD on `DataMapsUpdatedEvent` with cause `CLIENT_SYNC` (a remote client) or, in an integrated world whose connection is in memory and receives no `CLIENT_SYNC`, on the same `SERVER_RELOAD` event, through a flag the client tick reads.

**Cost, per player, once per reload:** removing touches the modifiers present on 5 attributes; re-adding queries ≤ 5 vanilla slots plus every functional Curios position, an **O(S + T log T)** traversal for *S* slot positions over *T* slot types (the collector's order, "The collector"). That term is Curios's and the pack's, not ours to cap: **disclosed** as a reload term and measured as a load scenario.

## Vis discount

- **Discounts exist only as `vftv:wearable.vis_discount`,** turned into modifiers on `vftv:vis_discount`, which the [wands quote](wands.md#the-quote) caps at `quote.max_discount` (0.30) and flags. Nothing else discounts, and no gear changes a focus's magnitude ([foci, Contracts](foci.md#contracts-on-sibling-specs)).
- **The flag compares per mille** (amended in the wands spec in this change): `discount_capped` iff `Math.round(value × 1000) > Math.round(quote.max_discount × 1000)`. The attribute sums its modifiers in `double` (`MC:world/entity/ai/attributes/AttributeInstance.java:148–165`), so 0.10 + 0.20 is 0.30000000000000004, which the old `value > max` flagged although `d` is 300 either way.
- **Display:** `vftv:vis_discount` is NeoForge's `PercentageAttribute` (`NF:common/PercentageAttribute.java:34–54`, `add_value` shown at scale 100; amended in the wands spec), so a tooltip reads "+3% Vis Discount".
- **The best built-in loadout** (four armour slots, the off hand, and one Curios `head`, `necklace` and `ring` slot, Curios's default size, `CU:common/slot/SlotType.java:204–212`) is the Void set, spectacles in the Curios head slot and two pendants: **0.23**, below the cap (a content lint). `discount_capped` is reached only by packs; the wands contract's GameTest uses a test-pack value of 0.40.

**Vectors** (cap 0.85, Aer 10, Ignis 3, Ordo 1; modifiers summed in order as `double`):

| Worn | Sum | `d` | Flag (old / amended) | `W` | `m` | Effective |
|---|---|---|---|---|---|---|
| nothing | 0.0 | 0 | no / no | 1.00 | 850 | Aer 9, Ignis 3, Ordo 1 |
| spectacles, Felted set, a pendant in the off hand | 0.14 | 140 | no / no | 1.00 | 731 | Aer 8, Ignis 3, Ordo 1 |
| the Void set, at its own gear warp 9 | 0.16 | 160 | no / no | 1.09 | 778 | Aer 8, Ignis 3, Ordo 1 |
| the best built-in loadout, **tax switch off** (discount isolation; with the tax on, the Void set's warp 9 makes `W` 1.09) | 0.22999999999999998 | 230 | no / no | 1.00 | 655 | Aer 7, Ignis 2, Ordo 1 |
| two modifiers, 0.10 and 0.20 | 0.30000000000000004 | 300 | **yes** / no | 1.00 | 595 | Aer 6, Ignis 2, Ordo 1 |
| a test-pack 0.40 | 0.4 | 300 | yes / yes | 1.00 | 595 | Aer 6, Ignis 2, Ordo 1 |

## Tuned Spectacles

The renamed Goggles of Revealing: amethyst lenses ground to the fork's pitch. Acoustic and a scholar's kit, with no telescope imagery ([lore, Tone](lore.md#tone)).

### What they show

While the player **wears a revealing item** (an item in `#vftv:reveals_aspects` in the vanilla head slot, or in a functional Curios slot when Curios is loaded), a card at the crosshair describes the vanilla hit result (`Minecraft.hitResult`, the fork's reach). The card is a **read-only projection** of what the client already holds: the synced knowledge (known aspects and the scan ledger with its partial snapshots, [aspects, Sync and state](aspects.md#sync-and-state)), the synced aspect data maps, node block-entity state and `VisRules`. It is **not** a simulated strike: a strike can discover or pay, and this never does.

**The card,** first match:

| # | Target | Card |
|---|---|---|
| 1 | an entity of type `vftv:apparition` | **none**: the fork's silence ([warp, Phantoms](warp.md#phantoms)) |
| 2 | a node whose block entity is dormant or whose id is unset | the type "Unknown" (`node.vftv.unknown_type`), as a strike shows it ([wands, Inspection](wands.md#inspection-and-teaching)), no aspects |
| 3 | a node whose `node:<id>` key isn't in the viewer's ledger | its name and `hud.vftv.spectacles.unstruck` |
| 4 | a node in the ledger | type, clarity and each aspect as current / max, from the synced state **settled to the client's game time** through the wands spec's pure regeneration ([wands, Regeneration](wands.md#regeneration)) and `VisRules` |
| 5 | a block, entity or dropped item whose scan key is **partly scanned** | the ledger's **stored snapshot** values, whatever its current values are now, even if a reload has emptied them or removed its entry |
| 6 | no current aspect entry, or `{}` | its name and `hud.vftv.spectacles.nothing` ("Nothing answers.") |
| 7 | its scan key isn't in the ledger | its name and `hud.vftv.spectacles.unstruck` ("I haven't struck this.") |
| 8 | its key is **completed** | its **current** values from the synced data maps |

- **Masking** (rows 4, 5, 8): an aspect in the viewer's **current known set** shows its icon; any other shows the **unheard** icon with its exact amount and the parent hint ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals)). Prerequisites that have since become known **don't** reveal an undiscovered aspect: only a strike discovers ([aspects, Knowledge](aspects.md#knowledge)), so the card can never name an unheard aspect.
- **Own aspects only:** a jar, crucible or bowl reads as its block, never its contents, as the fork does; a dropped item reads as the item it carries.
- **Disguises** are client drawings over real blocks, and the client level holds the real block, so the card names the real block. Instruments never lie ([progression, Reality slipping](progression.md#reality-slipping)).
- **Never:** a strike, points, discovery, a ledger write or a packet.

### Drawing

- **HUD layer `vftv:spectacles`** (`RegisterGuiLayersEvent`), drawn with the strike card's frame, anchor, wrapping and fallbacks ([aspects, What a strike reveals](aspects.md#what-a-strike-reveals); the [crucible HUD](crucible.md#the-hud)'s geometry), and **only while** the strike card's hold, `vftv:crucible` and `vftv:infusion` would all draw nothing: each owns its block, and the strike card wins at the crosshair.
- **Hidden** with F1, a screen or the debug overlay open, in spectator mode, and with the client option `spectacles.card` off.
- **The layout is rebuilt only** when one of these changes: the target identity (block position and state; entity id; for a dropped item, its carried item; node id); the viewer's knowledge; the aspect data maps; a node's synced state; the node's **settled values**, recomputed once per client tick for a node target (≤ 6 aspects) so a stationary node's regeneration shows; `VisRules`; resources; the scaled screen size.
- **Cleared** at a level change, a `LocalPlayer` replacement and logout.
- **Allocation:** our draw code allocates nothing per frame (an allocation-budget test).
- **Options considered:** jar contents, as TC4's goggles showed. **Why not:** the fork reads a jar's own aspects, never its contents, and the essentia spec keeps one contents path, its status lines ([essentia, Inspection and teaching](essentia.md#inspection-and-teaching)). Aspects of unstruck things. **Why not:** knowledge comes from striking ([aspects, Knowledge](aspects.md#knowledge)). Shift-free aspect tooltips while worn. **Why not:** unasked, and the Shift tooltip already exists.

## The Baffle

The renamed runic shield, after the acoustic baffle that soaks up sound.

### Capacity

**Attribute `vftv:baffle`**, registered here: a plain ranged attribute, default 0, range 0–`BAFFLE_FORMAT_MAX` (40), syncable, added to the player type through `EntityAttributeModificationEvent` (`NF:event/entity/EntityAttributeModificationEvent.java:25–50`). Its modifiers come from `vftv:wearable.baffle` and Baffling ("Baffling").

```
baffle_max = min(baffle.max, floor(attr)) + min(void.max_bonus, floorDiv(p × amplifier_warp × void.per_warp_pm, 1000))
```

`p` is the number of `#vftv:void_armor` items in the four armour slots (0–4); `amplifier_warp` is the [warp spec's](warp.md#the-dials) pure synced dial (0 with its switch off or the record unreadable). Integer arithmetic, a pure function in `core.armor.model`, identical on both sides. Tuning keeps `baffle.max + void.max_bonus ≤ 40`, so the result never passes the format bound.

**Vectors** (`baffle.max` 20, `void.max_bonus` 8, `void.per_warp_pm` 40; `attr`, `p`, warp → max): 0, 0, 0 → 0; 3, 0, 50 → 3; 20, 4, 0 → 20; 20, 4, 9 → 21; 20, 4, 25 → 24; 20, 4, 50 → 28; 20, 4, 200 → 28; 8, 2, 50 → 12; 26, 4, 50 → 28; 4, 4, 9 → 5. At the widest allowed tuning (`baffle.max` 32, `void.max_bonus` 8): 40, 4, 1000 → 40.

### State

Player attachment **`vftv:baffle`**, persisted with a serializer, **not** `copyOnDeath`, synced to the owner only:

| Field | Format bounds | Persisted | Meaning |
|---|---|---|---|
| `format` | 1 | yes | |
| `current` | `float`, finite, 0–40 | yes | Baffle points left |
| `next_recharge` | game time | no | transient; set to `g + baffle.delay` at login, respawn and dimension change |

- **Decoding checks format bounds only.** A record with an unknown `format` or out of bounds keeps its raw tag and is written back unchanged with one warning. That player's Baffle is **inert**: nothing clamps, recharges or absorbs, so no write ever replaces the raw tag, and the HUD shows nothing. The [warp](warp.md#kinds-and-the-record) and [golems](golems.md#sync-and-state) specs keep records the same way.
- **Respawn** starts from the attachment's default, 0. **Logout** keeps the value. A **dimension change** keeps the same `ServerPlayer` and its value.

### Absorbing

A `neoforge.armor` listener on **`LivingDamageEvent.Pre`** at `EventPriority.LOWEST`. Pre runs after the armour, enchantment and Resistance reductions and before absorption hearts and health (`MC:world/entity/player/Player.java:779–810`, Pre at `:787`; `NF:event/entity/living/LivingDamageEvent.java:35–44`); killing inside it throws (`Player.java:789`). The listener reads the container and passes plain numbers to `core.armor`; no `DamageContainer` type reaches `core`. For a server player with a readable record, first match:

1. the source is in the damage-type tag **`#vftv:baffle_ignores`** (default `#minecraft:bypasses_invulnerability`, `minecraft:drown`, `minecraft:starve`, `minecraft:in_wall`, `minecraft:outside_border`): nothing; the recharge clock is untouched;
2. `d = getNewDamage()` ≤ 0: nothing;
3. otherwise `next_recharge = g + baffle.delay`; `a = min(current, d)`; if `a > 0`: `setNewDamage(d − a)`, `current −= a`, the record synced; **then** the sound.

- **Write first.** The sound can call foreign code (`NF:event/EventHooks.java:532–535` from `MC:server/level/ServerLevel.java:1096–1104`), so it plays after our writes. Nothing of ours calls `hurt`; a listener that hurts the player again makes a nested damage container, which this listener handles on its own terms.
- **Invulnerability frames** already reduce the container before this point (`MC:world/entity/LivingEntity.java:1270–1288`), so only the excess arrives.
- **LOWEST is not final.** A later-registered LOWEST listener can still change the damage. If it raises it, the excess falls to absorption hearts and health. If it lowers it below what we took, that Baffle is wasted. The Baffle never credits health. Both are disclosed and tested.
- **Golden hearts** survive while the Baffle holds, because it comes first. **Food exhaustion** applies only to damage left after absorption (`Player.java:803–804`), so a fully baffled blow tires no one.
- **All sources alike**, PvP included. The absorb sound is positional and public, so an opponent hears a baffled hit. **Players only:** no other entity holds the attachment.

**Vectors** (`current`, damage → absorbed, `current`, through; clock reset; broke): 6.0, 4.5 → 4.5, 1.5, 0.0; yes; no. 1.5, 7.0 → 1.5, 0.0, 5.5; yes; **yes**. 6.0, drowning 2.0 → 0.0, 6.0, 2.0; no; no. 0.0, 3.0 → 0.0, 0.0, 3.0; yes; no. 4.0, 0.0 → nothing; no; no. 20.0, 20.0 → 20.0, 0.0, 0.0; yes; yes.

### Recharge

Each online server player, once per tick in the server-side `PlayerTickEvent.Post` (as the [warp spec](warp.md#schedule) uses it), when the record is readable:
1. **Clamp:** `current = min(current, baffle_max)`, written and synced only if it changed.
2. **Recharge:** if `g ≥ next_recharge` and `current < baffle_max`: `current = min(baffle_max, current + baffle.step)`; `next_recharge = g + baffle.interval`.

**Free: no vis.** A hurt sets the clock to `g + baffle.delay`, so at any delay above 0 the hurt tick itself never recharges, whether the hurt came before or after that player's tick. The vectors place each hurt before the player's tick.

**Vectors** (`baffle.delay` 100, `baffle.interval` 20, `baffle.step` 1):
- Max 12, from tick 1000 with `current` 6.0 and the clock open; hurts of 4.5 at 1000 and 0.5 at 1110: 1000 absorb → 1.5; 1100 recharge → 2.5; 1110 absorb → 2.0; 1210 → 3.0; then +1 every 20 ticks, reaching 12.0 at 1390.
- `current` 6.0, max falling from 12 to 3 at tick 50 (a ring taken off): clamp to 3.0 at tick 50.

- **Options considered:** recharging from wand vis, as TC4 drew Aer and Terra. **Why not:** vis leaves only through `VisService.spend` ([wands, Spending vis](wands.md#spending-vis)). A recharge would have to pick a wand on its own and debit the one about to cast, which is a hidden drain, and add a flux source on every pulse ([progression](progression.md#warp): never a hidden tax). Effects when the Baffle breaks (TC4's charged and revitalising variants). **Why not:** unasked; each would be one more callback in the damage pipeline.

### The HUD

**HUD layer `vftv:baffle`**, registered with `registerAbove(VanillaGuiLayers.ARMOR_LEVEL, …)` (`NF:client/gui/VanillaGuiLayers.java:23`; `NF:client/event/RegisterGuiLayersEvent.java:71–72`), so it draws after vanilla's armour row. The left status rows share NeoForge's public counter **`Hud.leftHeight`** (`NFP:18–22,286–317`); vanilla's air row uses `rightHeight` (`NFP:342–366`) and stays on the right. Nothing of vanilla's moves.

**Geometry** (GUI-scaled pixels, `guiWidth` × `guiHeight` the scaled screen, all integer):
- the bottom-left pip's top-left corner at `x = guiWidth / 2 − 91`, `y = guiHeight − hud.leftHeight`, read when the layer draws;
- pip *i* at `(x + 8 × (i mod 10), y − 10 × ⌊i / 10⌋)`;
- the text's top-left at `(x + 83, y − 10 × (rows − 1))`, beside the top row;
- after drawing, `hud.leftHeight += 10 × rows`, so layers drawn later on the left stack above it. A hidden row draws nothing and **doesn't** raise the counter.

- **Inputs, all client-side:** the synced `current`; `baffle_max` from the synced `vftv:baffle` attribute (vanilla syncs syncable attributes to tracking players and self, `MC:server/level/ServerEntity.java:287–290,352–355`), the player's own armour slots, the synced warp record and `WarpRules`, and `ArmorRules`. It draws `min(current, baffle_max)` (the server clamps at the next tick). **Until `ArmorRules` arrives,** it draws nothing.
- **Pips:** `n = ⌈baffle_max / 2⌉` pips of 9×9, placed as above: 10 to a row, rows growing **upward** like vanilla's hearts; `n` ≤ 20 at the format bound, so at most 2 rows. With `v = ⌊current⌋`: pip *i* (0-based, left to right, bottom row first) is **full** if `v ≥ 2i + 2`, **half** if `v = 2i + 1`, else **empty**. Flooring never shows Baffle the player doesn't have.
- **The exact value:** `hud.vftv.baffle` ("%s/%s") at the text position, `current` floored to one decimal with a trailing ".0" trimmed, then the integer max, in `hush3` with a shadow. Instruments never lie.
- **Hidden** when `baffle_max` and `current` are both 0, the record is unreadable, F1 is on, a screen or the debug overlay is open, or the player is in spectator or creative mode (as vanilla hides health), and with `baffle.hud` off.
- **Sprites:** `vftv:hud/baffle_full`, `baffle_half`, `baffle_empty`. The layout is rebuilt only when an input changes; our draw code allocates nothing per frame.

**Geometry vectors** (`guiWidth`, `guiHeight`, `hud.leftHeight` on entry, max → the first pip, the last pip, the text, `hud.leftHeight` after): 320, 240, 49, 12 → (69, 191), (109, 191), (152, 191), 59; 320, 240, 49, 40 → (69, 191), (141, 181), (152, 181), 69; 427, 240, 59, 28 → (122, 181), (146, 171), (205, 171), 79.

**Vectors** (`current`, max → pips, full, half, empty, rows, text): 1.5, 12 → 6, 0, 1, 5, 1, "1.5/12"; 20.0, 20 → 10, 10, 0, 0, 1, "20/20"; 27.5, 28 → 14, 13, 1, 0, 2, "27.5/28"; 40.0, 40 → 20, 20, 0, 0, 2, "40/40"; 3.0, 21 → 11, 1, 1, 9, 2, "3/21"; 0.5, 3 → 2, 0, 0, 2, 1, "0.5/3"; 0.0, 0 → hidden.

### Sound

| Event | Variants | Plays |
|---|---|---|
| `baffle.absorb` | 3, equal weights, picked by vanilla's sound-event selection | a hit with `a > 0` that leaves `current > 0`: at the player, `SoundSource.PLAYERS`, volume 0.8, pitch 1.0 |
| `baffle.break` | 1 | a hit that takes `current` to 0: the same, volume 1.0 |

At most one per hit, broadcast to nearby players as positional sound, with vanilla subtitles.

## Baffling

The runic-augmentation equivalent, with no new recipe form.

**Enchantment `vftv:baffling`** (`data/vftv/enchantment/baffling.json`, the 26.2 format of vanilla's `data/minecraft/enchantment/swift_sneak.json`): one `minecraft:attributes` effect on `vftv:baffle` with id `vftv:enchantment.baffling`, `add_value`, amount `{"type": "minecraft:linear", "base": 2, "per_level_above_first": 2}`; `max_level` 2; `slots: ["armor"]`; `supported_items` `#minecraft:enchantable/armor`. Vectors: level 1 → 2, level 2 → 4.

- **In no enchantment tag** (not `#minecraft:non_treasure`, so not `#minecraft:in_enchanting_table`; not `treasure`, `tradeable` or `on_random_loot`), so it is never in the table, trades or loot.
- **Applied only through infusion,** by the recipe `vftv:armor/baffling`: an ordinary `vftv:infusion` enchantment body ([infusion, Enchanting through infusion](infusion.md#enchanting-through-infusion)): one level per run, the cost × (L + 1), and instability rising with the levels already on the item. Infusion refuses books (`#vftv:infusion_unenchantable`), so no book carries it; an anvil still merges two baffled pieces, as vanilla does.
- **The infusion spec's built-in lint** (every built-in enchanting recipe's enchantment in `#minecraft:in_enchanting_table`) **exempts exactly `vftv:baffling`**, amended there in this change. No other treasure or curse is allowed.
- **Reach:** four pieces at level 2 give 16; with the Void pieces' 4 and a ring in the off hand and the Curios ring slot, `floor(attr)` reaches 26, capped at `baffle.max` 20. Only the warp bonus sits above the cap.

## Void armour

- **Made by infusion transmute** from Brass: four `vftv:infusion` bodies, `kind: void`, `research: vftv:void_armor` (a Stage 4 gate, as the [infusion](infusion.md#contracts-on-sibling-specs) and [arcane](arcane.md#contracts-on-sibling-specs) contracts require), centre the matching Brass piece, result `{"item": {"id": "vftv:void_<piece>", "count": 1}, "transmute": true}` ([infusion, The recipe type](infusion.md#the-recipe-type)), so enchantments, trims and damage carry over. A content lint checks each Void piece's durability against its Brass centre's. No #28 material is needed.
- **The amplifier** scales the Baffle bonus only ("Capacity"), bounded by `void.max_bonus`. Protection and discount stay flat, so the one warp-scaled number is visible and capped, and no focus magnitude is touched.
- **Gear warp** `vftv:gear_warp` 2 / 3 / 2 / 2 = **9**, against the warp spec's shared `gear.max` 12 ([warp, Gear warp](warp.md#gear-warp)): carried, sampled every 20 ticks. **The set alone** (vectors): `total` 9 → band 1, ORDINARY vis factor 1090 ‰, infusion penalty 2, amplifier 9 → Baffle bonus 1. With its 0.16 discount and a Brass cap, a quote's `m` is 778 against a bare 850, and every further warp point erodes it.
- **Names** are Void-tongue ([lore, Naming register](lore.md#vocabulary-policy)): each 1–2 words of the [lexicon](warp.md#the-lexicon), capitalised as proper item names, linted for lexicon membership and phonology. Item ids stay `vftv:void_<piece>`.

## Repair and enchanting

- **Repair:** vanilla's anvil through the `REPAIRABLE` tag of each item, and Mending. `#vftv:repairs_void_armor` ships with `minecraft:echo_shard`; #28 may add its Void material.
- **No self-repair** (TC4's Void metal did). Mending covers it, and a per-tick worn-item hook buys nothing.
- **Enchanting:** the table and anvil work by the enchantment values and armour tags above; [enchanting through infusion](infusion.md#enchanting-through-infusion) works by the same tags (its `supportsEnchantment` condition). The spectacles and trinkets take no enchantment.
- **Trims:** vanilla's, by `#minecraft:trimmable_armor`.

## Curios

Curios is an optional soft dependency ([architecture, Soft dependencies](architecture.md#soft-dependencies)): compiled against its API jar only, every Curios type in `neoforge.compat.curios`, entered only behind `ModList.get().isLoaded("curios")` from the `@Mod` wiring. **No progression, recipe or research needs a trinket or Curios.**

### Slots and eligibility

- **Entities file** `data/vftv/curios/entities/vftv.json`: `{"entities": ["minecraft:player"], "slots": ["head", "necklace", "ring"]}` (`CU:impl/CuriosCodecs.java:166–181`). Slot sizes are Curios's (default one, `CU:common/slot/SlotType.java:204–212`).
- **Item tags:** `curios:head` ← the spectacles; `curios:necklace` ← the Chime Pendant; `curios:ring` ← the Baffle Ring.
- **Both are written by datagen as plain JSON from `content/armor/curios.json`** ("Authored content"). No Curios type is used to write them, so `COMPAT_CONFINED` holds without `CuriosDataProvider` ([architecture, Enforcement](architecture.md#enforcement)). Without Curios they are inert.

### Modifiers in Curios slots

`neoforge.compat.curios` registers an `ICurioItem` for each of the three items (`CuriosApi.registerCurio`) that overrides **`getDefaultCurioAttributeModifiers(ItemStack)`** (`CU:api/type/capability/ICurioItem.java:57–70,180–197`) to return that item's `vftv:wearable` values with the **base id `vftv:wearable/curios`**. Curios appends the slot and index itself (`CU:api/CurioAttributeModifiers.java:110–122`), so a slot's modifier never collides with a vanilla slot's or another Curios slot's; supplying an already-suffixed id would double the suffix. The registered-item default path is the one Curios uses (`CU:common/capability/ItemizedCurioCapability.java:122–130`). Cosmetic slots apply no modifiers, as Curios decides.

### The collector

One bounded enumerator in `neoforge.compat.curios`, **`CuriosWorn.collect(entity, budget, sink)`**, gives each entry as `(slot id, index, cosmetic, stack)`:
- **Order:** slot ids ascending (`String.compareTo`); within a slot, functional stacks by index, then cosmetic stacks by index. Empty stacks are skipped and don't count.
- **Budget:** it stops after `budget` entries and returns how many it gave. A sink may also stop it early.
- **Cost:** the budget bounds what it **emits**, not what it **inspects**. Empty positions are walked and skipped, and the slot types are ordered first, so a full walk is **O(S + T log T)** for *S* slot positions over *T* slot types. *S* is set by Curios and the pack, with no ceiling of ours (`CU:common/slot/SlotType.java:210–218`), so this is **a native term, disclosed** wherever a caller may walk it all.

Its three callers, all in `neoforge`:

| Caller | Takes | Budget | Walk |
|---|---|---|---|
| the [warp spec's](warp.md#gear-warp) gear sample (**carried**: functional and cosmetic), every 20 ticks | every entry | what remains of `MAX_GEAR_SLOTS` (128) after the player's vanilla inventory, armour and off hand, read first in container index order: **one shared allowance**, never 128 each (amended in the warp spec) | up to the full O(S + T log T), disclosed |
| the spectacles' worn check (client, each client tick) | functional entries whose stack is in `#vftv:reveals_aspects`; **skipped** when the vanilla head slot already reveals, and stopped at the first revealing entry | unlimited | up to the full O(S + T log T), disclosed |
| the reload refresh (server, once per reload) | **every** functional entry, through Curios's attribute query ("After a reload") | unlimited: reconstruction is never truncated | the full O(S + T log T), disclosed |

### Without Curios

- **Trinkets count in the off hand** (`slot: offhand`, plain vanilla modifiers), so a trinket is never useless. With Curios loaded the off hand still counts: one stack in each both count, and the cap bounds the discount.
- **The spectacles** use the vanilla head slot. `vftv:void_helmet` reveals too, so the Stage 4 head needs no Curios.
- **No vis-storing trinket in v1** ([wands, Contracts](wands.md#contracts-on-sibling-specs)).
- **No Curios rendering:** spectacles in a Curios head slot work but aren't drawn on the model. An `ICurioRenderer` is code with no decision behind it.
- **Fabric:** the Curios layer is NeoForge-only; the off-hand fallback is vanilla, so a port loses only the bonus slots.

## Layers

- **`core.armor.model`** (Minecraft-free): `baffle_max`, absorbing, the recharge step, the wearable-to-modifier conversion (as plain records), the spectacles' card selection and masking over loader-free inputs, the HUD pip layout. Every vector above is a unit test against it.
- **`core.armor`**: the attachment record and codec, `ArmorRules`, the wearables' validated view, the services the listeners call. It imports `core.warp` (for `amplifier_warp`) and `core.aspect` (for the readout); neither imports it.
- **`core.armor.client`**: the card and the Baffle HUD renderers.
- **`neoforge.armor`**: thin listeners: `ItemAttributeModifierEvent`, `LivingDamageEvent.Pre`, `PlayerTickEvent.Post`, `DataMapsUpdatedEvent` and the queued refresh, attribute registration (`PercentageAttribute` construction stays here). **`neoforge.armor.client`**: layer registration. **`neoforge.compat.curios`**: as above.
- **No `DamageContainer`, Curios or `PercentageAttribute` type reaches `core`.** No seam is needed: discount and Baffle ride attributes, which vanilla and Curios apply, and every other Curios caller is already in `neoforge`.

## In the Commonplace Book

The [research spec](research.md#prose-and-fact-references) owns the book. This spec adds two fact sources (amended there in this change):
- **`wearable : <ns> ":" <path> ":" <field>`**: an item's `vftv:wearable` entry and registration properties: `slot`, `armor`, `toughness`, `knockback_resistance`, `vis_discount`, `baffle`, `gear_warp`, `durability`. `vis_discount` is the value as per mille, `Math.round(v × 1000)`, in the arcane percentage format: 0.03 → 30 ‰ → "3%".
- **`baffle : <key>`**: the `ArmorRules` keys, the derived seconds, Baffling's per-level grant and level cap from its enchantment, and the reader's live `current` and `capacity`.

The formats, absence rules and live dependencies are the research spec's tables. Prose (handed to the research content tickets) teaches that the spectacles show only what I've struck, that trinkets count in the off hand, that the Baffle soaks blows before golden hearts and comes back after quiet, what it ignores, that Baffling comes only from the altar, and that Void armour is carried warp whose Baffle grows with warp. Every number is in a fact span.

## Authored content

The content tickets deliver authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails its type's rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/armor/wearables.json      the Stage 1–3 items' wearable values
content/armor/recipes.json        the eleven Stage 1–3 recipes
content/armor/enchantments.json   Baffling
content/armor/tags.json           every tag this spec ships, with its members
content/armor/curios.json         the Curios entities file and slot tags
content/armor/bootstrap.json      the reviewed sources for the Stage 1–3 recipes
content/armor/fixtures.json       the vector tables, transcribed
content/armor/void.json           the Void set: wearables, gear warp, recipes and their bootstrap
content/lang/en_us/armor.json     every key under "Lang keys" except the Void names
content/lang/en_us/armor_void.json the four Void names
```

| Input | Schema | Generated output |
|---|---|---|
| `wearables.json` | `vftv:wearables/1` | `data/vftv/data_maps/item/wearable.json`, merged with `void.json`'s; an item in both fails datagen |
| `recipes.json` | `vftv:armor_recipes/1` | each recipe `<ns>:<path>` → `data/<ns>/recipe/<path>.json`, its body unchanged |
| `enchantments.json` | `vftv:enchantments/1` | `data/vftv/enchantment/baffling.json` |
| `tags.json` | `vftv:armor_tags/1` | each tag's file under `data/<ns>/tags/{item,damage_type}/` |
| `curios.json` | `vftv:curios_slots/1` | `data/vftv/curios/entities/vftv.json` and `data/curios/tags/item/{head,necklace,ring}.json` |
| `bootstrap.json` | `vftv:armor_bootstrap/1` | nothing: read by the review |
| `fixtures.json` | `vftv:armor_fixtures/1` | nothing: read by the unit tests |
| `void.json` | `vftv:void_armor/1` | its wearables into the data map above; `data/vftv/data_maps/item/gear_warp.json`; its four recipes |
| `lang/<lang>/armor.json`, `armor_void.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json`; a duplicate key across `content/lang` fails datagen |

A recipe id declared in two `content/` files, of any type, fails datagen ([infusion, Authored content](infusion.md#authored-content)).

### Wearables

`{"schema": "vftv:wearables/1", "wearables": {<item id>: <entry>}}`, each entry the data-map record under "Wearables". **Exactly** the ten Stage 1–3 items of "The roster", each with its shipped values. **Rejected:** another item; a missing one; a value differing from the roster; any data-map rule broken; the best built-in loadout (with `void.json`) over the `quote.max_discount` default.

### Recipes

`{"schema": "vftv:armor_recipes/1", "recipes": {<id>: <body>}}`. The ids, and the type each must have:

| Id | Body | Rules |
|---|---|---|
| `vftv:tuned_spectacles` | [`vftv:arcane`](arcane.md#the-recipe-type) | `research` `vftv:tuned_spectacles`; `kind` `ordinary` (or omitted); every `vis` value ≤ 32; ingredients from the reviewed pre-iron list; result the item, count 1 |
| `vftv:felted_coat`, `vftv:felted_breeches`, `vftv:felted_boots`, `vftv:chime_pendant` | `vftv:arcane` | `research` `vftv:felted_garments`; `ordinary`; every `vis` value ≤ the `vftv:wooden` rod's capacity in `content/wands/parts.json` (the lint reads it); ingredients reachable in Stages 1–2 |
| `vftv:resonant_brass_helmet`, `_chestplate`, `_leggings`, `_boots` | `minecraft:crafting_shaped` | **no `research`** (vanilla crafting carries none; exempt from the gate rule); vanilla's armour shapes with `vftv:resonant_brass_ingot`; result the item, count 1 |
| `vftv:baffle_ring` | `vftv:arcane` | `research` `vftv:baffling`; `ordinary`; every `vis` value ≤ the `vftv:chime` rod's capacity; ingredients reachable in Stages 1–3 |
| `vftv:armor/baffling` | [`vftv:infusion`](infusion.md#the-recipe-type) | `research` `vftv:baffling`; `kind` `ordinary`; centre `#minecraft:enchantable/armor`; result `{"enchantment": "vftv:baffling", "max_level": 2}`; `instability` 5–25; `essentia[a] × 2 ≤ 1000` |

```json
{"schema": "vftv:armor_recipes/1", "recipes": {
  "vftv:tuned_spectacles": {
    "type": "vftv:arcane", "research": "vftv:tuned_spectacles", "kind": "ordinary",
    "ingredients": ["minecraft:copper_ingot", "minecraft:amethyst_shard", "minecraft:amethyst_shard",
                    "minecraft:glass_pane", "minecraft:leather"],
    "result": {"id": "vftv:tuned_spectacles", "count": 1},
    "vis": {"vftv:aer": 12, "vftv:ordo": 8}},
  "vftv:armor/baffling": {
    "type": "vftv:infusion", "research": "vftv:baffling",
    "centre": "#minecraft:enchantable/armor",
    "components": ["minecraft:white_wool", "minecraft:white_wool", "vftv:resonant_brass_ingot", "minecraft:amethyst_shard"],
    "essentia": {"vftv:tutamen": 8, "vftv:silentium": 4},
    "instability": 12,
    "result": {"enchantment": "vftv:baffling", "max_level": 2}}
}}
```

**Rejected:** an id outside the table or a required id missing; a body of the wrong type; any field outside its type's table; any rule above broken; a created result that is empty or over its maximum stack.

### Enchantments, tags and Curios

- **`enchantments.json`:** `{"schema": "vftv:enchantments/1", "enchantments": {"vftv:baffling": <vanilla enchantment JSON>}}`, exactly that one, with exactly the effect, `max_level`, `slots` and `supported_items` under "Baffling" (`anvil_cost`, `weight`, `min_cost` and `max_cost` within vanilla's ranges, chosen by the ticket; `description` `{"translate": "enchantment.vftv.baffling"}`). **Rejected:** another id; the effect differing; the enchantment named in any tag.
- **`tags.json`:** `{"schema": "vftv:armor_tags/1", "item": {<tag>: [<item id or #tag>]}, "damage_type": {<tag>: [<damage type id or #tag>]}}`, exactly the memberships under "The roster" plus `#vftv:repairs_void_armor` [`minecraft:echo_shard`] and `#vftv:baffle_ignores` with its five defaults. **Rejected:** a tag or member outside these; one missing.
- **`curios.json`:** `{"schema": "vftv:curios_slots/1", "entities": ["minecraft:player"], "slots": ["head", "necklace", "ring"], "tags": {"curios:head": ["vftv:tuned_spectacles"], "curios:necklace": ["vftv:chime_pendant"], "curios:ring": ["vftv:baffle_ring"]}}`, exactly this.

### The bootstrap

`{"schema": "vftv:armor_bootstrap/1", "recipes": {<id>: {"items": [{"item": <item id or #tag>, "why": <one line>}, …], "aspects": [{"aspect": <id>, "why": <one line>}, …]}}}`, for exactly the eleven recipes of `recipes.json`. **Coverage:** every ingredient, centre and component (a tag names the alternative the review resolves) and every essentia aspect. **Review:** each `why` says how the thing is obtained in its recipe's Stage without the thing it makes, and for an aspect, which matter dissolves into it ([aspects, Coverage and supply](aspects.md#coverage-and-supply)); the sequence is non-circular. **Rejected:** a missing recipe, item or aspect; an empty `why`.

### The Void set

`{"schema": "vftv:void_armor/1", "wearables": {…}, "gear_warp": {<item id>: 1–10}, "recipes": {…}, "bootstrap": {…}}`:
- `wearables`: exactly the four Void pieces with their shipped values;
- `gear_warp`: exactly 2, 3, 2, 2, summing ≤ the warp spec's `gear.max` default;
- `recipes`: exactly `vftv:void_helmet`, `vftv:void_chestplate`, `vftv:void_leggings`, `vftv:void_boots`, each a `vftv:infusion` body with `research` `vftv:void_armor`, `kind` `void`, centre the matching `vftv:resonant_brass_<piece>`, result `{"item": {"id": "vftv:void_<piece>", "count": 1}, "transmute": true}`, `instability` 15–35, and `vftv:vacuos` among its essentia;
- `bootstrap`: the bootstrap record above for those four, every source obtainable in Stage 4. **This ticket owns it;** the Stages 1–3 bootstrap never covers the Void bodies.

**Rejected:** as for the other files; a Void piece whose durability is below its centre's.

### Fixtures

`{"schema": "vftv:armor_fixtures/1", "baffle_max": [...], "absorb": [...], "schedule": [...], "quote": [...], "dials": [...], "durability": [...], "baffling": [...], "pips": [...], "hud": [...], "pct": [...]}`. **All ten sections are required**, and together they transcribe every vector in this spec exactly. Every field is required unless a default is given.

| Record | Fields |
|---|---|
| `baffle_max` | `attr` 0–40; `pieces` 0–4; `warp` 0–2,200; `rules` `{max, max_bonus, per_warp_pm}` (default 20, 8, 40); `expect` |
| `absorb` | `current`, `damage` (halves, 0–40 and 0–1,000); `ignored` (default false); `expect` `{absorbed, current, through, reset, broke}` |
| `schedule` | `start` (tick); `until` (tick); `current`; `next` (tick); `max` (a list of `{from, max}`, ascending, the first `from` = `start`); `hurts` (a list of `{tick, damage}`); `rules` `{delay, interval, step}` (default 100, 20, 1); `expect`: the ordered changes, each `{tick, op: "absorb" | "clamp" | "recharge", current}` |
| `quote` | `cap`; `modifiers` (the discounts, summed in order as `double`); `max_discount`; `w`; `base`; `expect` `{sum, raw_gt, d, discount_capped, m, effective}` (`sum` as the shortest decimal that round-trips the `double`) |
| `dials` | `gear` (a list of gear warp values); `expect` `{total, band, factor, penalty, bonus}` |
| `durability` | `set`; `multiplier`; `expect` `{<piece>: n}` |
| `baffling` | `level`; `expect` |
| `pips` | `current`; `max`; `expect` `{pips, full, half, empty, rows, text}` |
| `hud` | `gui_width`, `gui_height` (scaled, ≥ 1); `left_height` (on entry, ≥ 0); `max` 0–40; `expect` `{first: [x, y], last: [x, y], text: [x, y], left_height}` |
| `pct` | `value`; `expect` `{per_mille, text}` |

```json
{"schema": "vftv:armor_fixtures/1",
 "baffle_max": [{"attr": 20, "pieces": 4, "warp": 25, "expect": 24}],
 "absorb":     [{"current": 1.5, "damage": 7.0, "expect": {"absorbed": 1.5, "current": 0.0, "through": 5.5, "reset": true, "broke": true}}],
 "schedule":   [{"start": 40, "until": 60, "current": 6.0, "next": 10000, "max": [{"from": 40, "max": 12}, {"from": 50, "max": 3}],
                 "hurts": [], "expect": [{"tick": 50, "op": "clamp", "current": 3.0}]}],
 "quote":      [{"cap": 0.85, "modifiers": [0.10, 0.20], "max_discount": 0.30, "w": 1.00,
                 "base": {"vftv:aer": 10, "vftv:ignis": 3, "vftv:ordo": 1},
                 "expect": {"sum": "0.30000000000000004", "raw_gt": true, "d": 300, "discount_capped": false, "m": 595,
                            "effective": {"vftv:aer": 6, "vftv:ignis": 2, "vftv:ordo": 1}}}],
 "dials":      [{"gear": [2, 3, 2, 2], "expect": {"total": 9, "band": 1, "factor": 1090, "penalty": 2, "bonus": 1}}],
 "durability": [{"set": "void", "multiplier": 26, "expect": {"helmet": 286, "chestplate": 416, "leggings": 390, "boots": 338}}],
 "baffling":   [{"level": 2, "expect": 4}],
 "pips":       [{"current": 27.5, "max": 28, "expect": {"pips": 14, "full": 13, "half": 1, "empty": 0, "rows": 2, "text": "27.5/28"}}],
 "hud":        [{"gui_width": 320, "gui_height": 240, "left_height": 49, "max": 40,
                 "expect": {"first": [69, 191], "last": [141, 181], "text": [152, 181], "left_height": 69}}],
 "pct":        [{"value": 0.03, "expect": {"per_mille": 30, "text": "3%"}}]}
```

**Rejected:** a section missing; an unknown field; a row of a vector table missing or altered.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text, and its review checks that the messages teach what "In the Commonplace Book" lists.

| Key | Args | Sense |
|---|---|---|
| `item.vftv.tuned_spectacles`, `.felted_coat`, `.felted_breeches`, `.felted_boots`, `.chime_pendant`, `.resonant_brass_helmet`, `.resonant_brass_chestplate`, `.resonant_brass_leggings`, `.resonant_brass_boots`, `.baffle_ring` | 0 | names |
| `item.vftv.void_helmet`, `.void_chestplate`, `.void_leggings`, `.void_boots` (in `armor_void.json` only) | 0 | Void-tongue names: 1–2 lexicon words each, capitalised, passing the lexicon and phonology lints, with no provisional English |
| `attribute.name.vftv.baffle` | 0 | "Baffle" |
| `enchantment.vftv.baffling` | 0 | "Baffling" |
| `tooltip.vftv.spectacles` | 0 | "What I have struck, I see." |
| `tooltip.vftv.trinket.offhand` | 0 | "Counts in my off hand." |
| `hud.vftv.spectacles.unstruck`, `.nothing` | 0 | "I haven't struck this.", "Nothing answers." |
| `hud.vftv.baffle` | 2 | "%s/%s" |
| `wearable.vftv.slot.head`, `.chest`, `.legs`, `.feet`, `.offhand` | 0 | "Head", "Body", "Legs", "Feet", "Off hand" |
| `wearable.vftv.fact.none` | 0 | "none" |
| `options.vftv.spectacles.card`, `.baffle.hud` | 0 | "Spectacles card", "Baffle row" |
| `subtitles.vftv.baffle.absorb`, `.break` | 0 | "Baffle soaks a blow", "Baffle gives way" |

**Used, not declared here:** `node.vftv.unknown_type`, the clarity keys and aspect names (the wands and aspects specs'). **Rejected:** a key outside the table; a required key missing in `en_us`; a placeholder count that differs; an empty string; a duplicate key, including any other spec's key redeclared here.

## Events

**None new** ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)). Addons read:
- the `vftv:baffle` attribute (capacity's input) and the `vftv:wearable` data map, through the keys in `api`. The map gives the raw entries; which take effect is the internal validated view ("Wearables").
- the Baffle's live charge and capacity through the read-only **`BaffleView`**, delivered for a `ServerPlayer` by the entity capability `vftv:baffle_view` ([API spec, ResearchView and BaffleView](api.md#researchview-and-baffleview)): `readable`, `current` and `capacity`, answered by the same `core` code as the HUD.

The `vftv:baffle` attachment itself is internal; only its id is public. The damage hooks are NeoForge's own.
- **Options considered:** a `BaffleAbsorbedEvent`. **Why not:** no sibling consumes it, and a listener inside the damage pipeline would be one more callback to order.

**Plain `core` services:** `BaffleService.capacity`, `.absorb`, `.tick`; `Wearables.modifiers`; `SpectaclesCard.of`. They are **internal in v1**, except that `BaffleView` exposes the reads ([API spec, Services](api.md#services)).

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| the Baffle record | the `vftv:baffle` player attachment (persisted; not copied on death) | the owner, through `AttachmentSyncHandler` filtered to the owner (`NF:attachment/AttachmentSyncHandler.java:28–40`); one send per change: ≤ 1 per hit, ≤ 1 per `baffle.interval`, ≤ 1 per clamp |
| `next_recharge` | transient per-player memory | never |
| `vftv:baffle`, `vftv:vis_discount` and vanilla armour attributes | vanilla attribute modifiers, re-applied on equip, unequip and the reload refresh | **tracking players and self**, as vanilla syncs syncable attributes (`MC:server/level/ServerEntity.java:287–290,352–355`) |
| `vftv:wearable`, `vftv:gear_warp` | data maps | every player, as synced data maps are |
| armour rules | the tuning record | **`ArmorRules`**, S2C to every player through `OnDatapackSyncEvent` (join and `/reload`) |
| the card and HUD layouts | client caches | never |

- **No client-to-server payload.** The card derives only from data the client already holds (a client can read every value anyway, [aspects, Sync and state](aspects.md#sync-and-state)); the Baffle is server arithmetic.
- **Baselines are NeoForge's:** login and respawn send initial player attachments, and a dimension change resends them ([warp, Sync and state](warp.md#sync-and-state)).
- **Bounded codecs:** the record as above; `ArmorRules` a fixed record. Out of bounds is rejected at decode.

**Reloads:**

| Change | Effect |
|---|---|
| Tuning (`/reload`) | a new record, validated; `ArmorRules` re-sent; capacity and the HUD follow at once; the clamp applies at the next tick |
| `vftv:wearable` (`/reload`) | a file with a bad value is dropped whole; the validated view rebuilt on each side ("Wearables"); the queued refresh ("After a reload"); client caches rebuilt |
| Tags (`/reload`) | `#vftv:baffle_ignores`, `#vftv:void_armor` and `#vftv:reveals_aspects` take effect at the next hit, tick or client tick |
| The enchantment registry | needs the world reopened |
| Resource reload | sprites, layouts and the card rebuild |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/armor.json`**, loaded on reload into an immutable record in `core.armor` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type, a value out of range or a broken constraint is invalid. **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client receives the whole record in `ArmorRules`.

| Key | Value | Range | Notes |
|---|---|---|---|
| `baffle.max` | 20 | 0–40 | the cap on `floor(attr)` |
| `baffle.delay` | 100 ticks | 0–1,200 | after a hurt, before the first recharge |
| `baffle.interval` | 20 ticks | 1–200 | between recharges |
| `baffle.step` | 1 | 1–40 | points per recharge |
| `void.per_warp_pm` | 40 | 0–1,000 | per piece, per warp point |
| `void.max_bonus` | 8 | 0–20 | **constraint:** `baffle.max + void.max_bonus ≤ 40`, for every allowed tuning |

**Other homes:**
- attribute numbers: the `vftv:wearable` data map;
- gear warp: the `vftv:gear_warp` data map ([warp, Gear warp](warp.md#gear-warp));
- Baffling's grant and level cap: its enchantment JSON;
- ignored damage: `#vftv:baffle_ignores`;
- recipe costs: the recipe bodies;
- **client config:** `spectacles.card` (on), `baffle.hud` (on).

**Fixed at registration** (unit-tested against "The roster"): durability, enchantment value, slot, equip sound, equipment asset, repair tag, stack size and allowed entities. A pack changes them only with code.

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`ArmorLimits`** in `api`:

| Constant | Value |
|---|---|
| `BAFFLE_FORMAT_MAX` | 40 |
| `WEARABLE_MAX_ARMOR`, `WEARABLE_MAX_BAFFLE` | 20, 20 |
| `HUD_PIPS_PER_ROW`, `HUD_MAX_ROWS` | 10, 2 |

**Also fixed in code:** the modifier ids `vftv:wearable/<slot>` and the Curios base `vftv:wearable/curios`; the card's first-match table and the layer priority; the absorb and recharge order; the pip rule and geometry; the sound volumes. `MAX_GEAR_SLOTS` (128) is the warp spec's.

## Performance

**Heavy-base workload:** 8 players online with Curios loaded, each in a full Void or Brass-and-Felted set at Baffling II, with the spectacles and both trinkets (one in the off hand, one in a Curios slot):
- 2 in **cycled combat**: 10 s taking a hit every 5 ticks, then 30 s quiet, so absorbing, breaking, the delay and recharging to full all recur after warm-up;
- 1 swapping a chestplate every 40 ticks;
- every player's warp sampled by the warp spec's collector over the same Curios slots.

On the client: one player wearing the spectacles whose target changes every tick (a block, an entity, a dropped item, a regenerating node, an unstruck thing), with the Baffle row up at capacity 40 (20 pips).

**Share:** server ≤ **0.01 ms mean, ≤ 0.04 ms p99**, measured in the combined stress world, including the modifier events, sync and sounds. Client ≤ **0.01 ms p50**. Shares are acceptance targets, not measurements.

**The envelope's running totals** (planning arithmetic, never a combined percentile): the finished specs' server shares total **0.49 ms mean / 1.98 ms p99** with warp, and **0.50 / 2.02** with armour, leaving **0.50 / 0.98** for Flux and the Hush, Worldgen and mobs, and the Eldritch tier. Client shares total **0.81 ms p50**, **0.82** with armour, leaving **0.18**.

**Measured separately as load scenarios** (reported): a `/reload` that changes `vftv:wearable` with 64 players online; the same with one player wearing more than 128 Curios items; a pack with thousands of empty Curios positions, for the client worn check and the gear sample; 1,000 hits in one tick across 8 players.

**Hot paths:**

| When | Work |
|---|---|
| Per player per tick | the clamp and recharge compare, O(1), **zero allocation** (an allocation-budget test) |
| Per hit | one tag test, O(1) arithmetic; ≤ 1 sync, ≤ 1 sound and its callbacks: **a native term**, disclosed |
| Per equipment change | the modifier event per changed stack, ≤ 5 modifiers, at vanilla's cadence |
| Per reload | the refresh: per player, the modifiers present on 5 attributes, ≤ 5 vanilla slots, and every functional Curios position: **O(S + T log T), a native term**, disclosed |
| Per client tick | one hit-result read; O(1) ledger and map lookups; the worn check: the head slot, then, only if it doesn't reveal, the Curios walk to the first revealing entry, **up to O(S + T log T), a native term**, disclosed; for a node target, ≤ 6 settled values |
| Per frame (client) | the cached card and ≤ 20 pips with their text; **zero allocation in our code** |
| Packets | ≤ 1 record sync per hit, recharge or clamp; vanilla's attribute sync; one `ArmorRules` per join or reload |

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **every vector** in this spec, replayed from `content/armor/fixtures.json`: capacity at default and widest tuning, absorbing, both schedules, the quote rows including 0.10 + 0.20, the Void dials, durabilities, Baffling, pips, HUD geometry and percentages;
- **the card:** every first-match row; a partial scan whose missing prerequisite has since become known shows that aspect **still unheard**, from the **snapshot**; a value-changing reload changes a completed key's card and leaves a partial key's snapshot; a partial key whose current entry a reload **removed**, and one whose current values became **`{}`**, both still show the snapshot, not "Nothing answers."; masking against the current known set; a stationary node's settled values advancing while the target is unchanged; a dropped item's carried item changing;
- the wearable-to-modifier conversion; a `vftv:wearable` entry with a field removed gives no modifier for it;
- the validated view: a slot-mismatched entry and an `offhand` entry on an equippable item are excluded with an error naming the item, and the rest kept;
- codecs: the attachment (bounds, the raw round trip of an unreadable record), `ArmorRules`, the data-map entry;
- the registration table against "The roster";
- every `content/` rejection rule and lint: the loadout bound, the Void durability, the gear-warp sum, the lexicon names, the recipe rules, the enchantment's shape and tags.

**GameTest:**
- the tuning file, the data map, the enchantment and the tags load, and each bad entry and file is refused; a tuning reload breaking the constraint keeps the previous record;
- **the data map's two failure modes, separately:** a pack file with one out-of-range value is discarded **whole** (its other entries absent, another pack's file for the map still loaded); a pack file with one slot-mismatched entry keeps every other entry, and only that item is excluded from the validated view, on the server and on the client;
- **wearables:** each piece and trinket applies and removes its modifiers in its slot, without Curios; a trinket in the off hand counts; a zombie doesn't pick up a Brass helmet; an armour stand wears one;
- **discount:** a test-pack `vftv:wearable` of 0.40 shows `discount_capped` and the capped cost; two modifiers 0.10 and 0.20 do **not** flag;
- **absorbing:** order with exact numbers (armour, then the Baffle, then golden hearts, then health); each ignored type leaves the clock alone; the invulnerability-frame case; no exhaustion on a fully baffled hit; a later LOWEST listener that raises damage and one that lowers it; a sound listener that hurts the player again (a nested container, the record consistent); a mob with our armour forced on by command takes no Baffle;
- **recharge:** the delay, the interval, the clamp when a ring is taken off, the Void bonus under `/vftv warp set` and with `warp.amplifier` off;
- **persistence:** save and reload keep `current`; death resets it; a dimension change keeps it; an unreadable record is never written, clamped, recharged or used to absorb;
- **reload:** a `/reload` that changes, and one that removes, a worn item's `vftv:wearable` values refreshes the worn modifiers at the next tick, not during the reload;
- Baffling by altar to level 2, refused on a book; a Void transmute keeps enchantments, trims and damage; anvil repair with each tag;
- the packaged jar without Curios;
- every test id in the discovery guard.

**Curios integration** (run when `neoforge.compat.curios` changes): a ring and a pendant in their slots add modifiers with Curios's derived ids and remove them; a cosmetic slot adds none; the spectacles in a functional head slot enable the card, in a cosmetic one they don't; the collector's order and shared budget; the gear sample counts Curios stacks; the reload refresh removes and rebuilds Curios-derived ids after Curios's own reconciliation; **a player wearing more than 128 Curios items, a Baffle Ring last in the order, keeps its Baffle through a `/reload`**; a pack with thousands of empty Curios positions, its worn-check and sample work measured.

**E2E:**
- **the card:** struck, partial (an unheard icon with its amount), unstruck, nothing-to-learn, a node regenerating in place, a disguise (the real block), an apparition (no card), with dumps; yielding to the strike card, the crucible HUD and the infusion HUD; `spectacles.card` off; cleared on a dimension change;
- **the client over-cap quote:** wearing the test-pack 0.40 item, the Worktable's cost tooltip shows `discount_capped` and the capped cost, and the dump equals the server's quote;
- **the Baffle row** equals the dump through a hit, a break and a recharge, at capacities 12, 28 and 40, hidden at 0 and in creative; at the pinned coordinates, with absorption hearts and full armour below it, nothing overlaps, and vanilla's air row stays on the right; `hud.leftHeight` raised by 10 × rows only while drawn; `baffle.hud` off;
- "+3% Vis Discount" in a tooltip; each set rendered on the player and a small armour stand (screenshots);
- sync on join, reconnect, dimension change and `/reload`;
- the asset, lang, equipment-asset and sound validators.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)'s contracts on this ticket):

| Wands contract | Met by |
|---|---|
| Vis discounts **only** as modifiers on `vftv:vis_discount` (default 0, range 0–1, syncable), capped at `quote.max_discount` and flagged; GameTest: gear past the cap shows `discount_capped` and the capped cost, identically on client and server | "Wearables", "Vis discount": data-map modifiers only; the flag fixed to compare per mille (amended there); the test-pack 0.40 GameTest and the E2E client over-cap quote equal to the server's |
| No vis-storing trinket in v1; everything works without Curios | "Without Curios": none; trinkets count in the off hand; the packaged jar boots without Curios |

**Met here** (the [Foci spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/22)'s contracts):

| Foci contract | Met by |
|---|---|
| Cast discounts come only from `vftv:vis_discount`; no gear changes a focus's magnitude in v1 | "Vis discount"; "Void armour": the amplifier scales the Baffle only |

**Met here** (the [Warp and the Voices spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)'s contracts):

| Warp contract | Met by |
|---|---|
| Void-tier gear carries warp only through the `vftv:gear_warp` data map; its Curios compat passes carried Curios stacks to the gear sample | "Void armour": 2/3/2/2 in `void.json`; "The collector": ordered `(slot, index, cosmetic, stack)` entries within one shared `MAX_GEAR_SLOTS` allowance, functional and cosmetic both (amended in the warp spec) |

**Met here** (the [Infusion spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)'s contracts):

| Infusion contract | Met by |
|---|---|
| Infusion recipes use the body with their own `research`, in their own content files; `kind: void` built-ins need a Stage 4 gate; #24 may use `transmute`; runic augments and the like are extensions they propose | "Baffling": the existing enchantment result form, no new recipe form, with exactly one lint exemption (amended there); "Void armour": `transmute` inside `result`, `kind: void` behind `vftv:void_armor`; "Authored content": `recipes.json` and `void.json` |

**Met here** (the [Arcane crafting spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)'s contracts):

| Arcane contract | Met by |
|---|---|
| Any Worktable recipe is a `vftv:arcane` body with its own `research`; `kind: void` only behind a Stage 4 gate; a recipe needing warp on craft proposes an extension; Stage 1 arcane recipes meet the affordability test | "Recipes": every arcane body `ordinary` with its gate; none applies warp; the spectacles ≤ 32 per aspect from pre-iron ingredients |

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts, which bind #24 through the arcane block for its own recipe types, and its production note):

| Research contract | Met by |
|---|---|
| The recipe-gate, recipe-page, authoritative-preview and JEI contracts for its own recipe types | no recipe type of its own: the arcane and infusion types carry the gate, preview and JEI; the ungated Brass recipes sit on `vftv:brass_armor`'s page |
| The Stage 2–4 content tickets are blocked by #24 | "Stage" and "In the Commonplace Book": five entries and two fact sources (the research spec amended); the Stage 1 ticket is now blocked by #24 too |

**Met here** (the [architecture spec](architecture.md#soft-dependencies)): Curios is optional, compiled against its API, confined to `neoforge.compat.curios` and entered behind `isLoaded`; progression never needs it ("Curios"); no loader type reaches `core` ("Layers").

**Met here** (the [progression spec](progression.md)): armour and trinkets kept, trinkets on Curios, runic shielding renamed ("The Baffle"); Void-tier gear grows stronger with warp, bounded ("Void armour"); instruments never lie: the card is a read-only projection that names real blocks and never an unheard aspect, and the Baffle row prints its exact value ("Tuned Spectacles", "The HUD").

**Met here** (the [lore spec](lore.md#vocabulary-policy)): Thaumium → Resonant Brass armour; Goggles of Revealing → Tuned Spectacles; Runic Shielding → Baffle; acoustic imagery; Void-tongue only for the Void set.

**Met here** (the [aspects spec](aspects.md#what-a-strike-reveals) and the [warp spec's](warp.md#phantoms) phantom rules): unheard amounts exact and identities withheld, against the current known set and the stored snapshot; apparitions silent; disguises read as the real block.

**Met here** (the [quality gates](quality-gates.md#what-each-system-spec-must-declare)): a sustained workload, a share, hot paths with bounded work at every allowed tuning, allocation budgets, and disclosed native terms, among them the Curios walk, O(S + T log T), which the reload refresh always takes in full so it never truncates reconstruction, and which the client worn check and the gear sample may take ("The collector", "Performance").

**[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)**, **[Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)** and **[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)** place no contract on this ticket.

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)** (amended in this change)
- `discount_capped` iff `Math.round(value × 1000) > Math.round(quote.max_discount × 1000)`, with a new vector row and fixture form for a modifier sum; `vftv:vis_discount` is registered as NeoForge's `PercentageAttribute`; its sync row says tracking players and self.

**[Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)** (amended in this change)
- The built-in enchanting lint exempts exactly `vftv:baffling`; "no enchantment of our own in v1" now names Baffling as the one.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 1](https://github.com/andrewbrogoff/voices-from-the-void/issues/34), [Stage 2](https://github.com/andrewbrogoff/voices-from-the-void/issues/35), [Stage 3](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) and [Stage 4 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/37):
- **Entries:** the five under "Stage", with their kinds, parents, the Void reveal and `hinted_by`; each carries `vftv:recipe` pages for the recipes it gates, and `vftv:brass_armor` pages the four ungated Brass recipes.
- **Fact sources** `wearable` and `baffle`, with their formats, absence rules and live dependencies.
- **The affordability artifact:** `path-stage4.json` shows the Void reveal latched by `vftv:silence_beneath`'s lasting 4 during the Stage 3 replay. No path learns an armour entry; the Void set's material bootstrap is the Void armour content ticket's, not #37's.
- **Prose** as under "In the Commonplace Book".

**[Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)** (amended in this change)
- The gear sample reads the player's vanilla inventory first, in container index order, then Curios entries from `CuriosWorn.collect` with what remains of the one `MAX_GEAR_SLOTS` allowance.

**[Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- Its Void weapons share `gear.max` 12 with the Void set's 9; the sample clips beyond it.
- It may add its Void material to `#vftv:repairs_void_armor`.
- Any Void-tongue word the Void set's names need beyond the lexicon is added through the lexicon.

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- Hand entries (or reviewed exemptions) for the **14** items, armour carrying Tutamen by the aspects spec's mandatory override.
- **Round-trip non-amplification** over the fifteen recipes' actual outputs, ingredients, remainders and essentia, with vis counting as no material input; transmuting and enchanting add no value, so preserved enchantments and components count for nothing ([infusion, Contracts](infusion.md#contracts-on-sibling-specs)).

**Configuration**
- The client options `spectacles.card` and `baffle.hud`.

**API**
- `ArmorKeys` (the `vftv:baffle` attribute key and the attachment's id, the `vftv:wearable` data-map key, the tags `vftv:void_armor`, `vftv:reveals_aspects`, `vftv:repairs_void_armor`, `vftv:baffle_ignores`, and the `vftv:baffling` enchantment key), `ArmorLimits`, the data-map value `Wearable` (with `WearableSlot`) and `BaffleView` live in `api`, loader-free, as Stable, beside the `ArmorRules` record as `@Internal`. The `vftv:wearable` `DataMapType` and the `vftv:baffle_view` capability live in `api.neoforge`; `PercentageAttribute` construction stays in `neoforge`. The `vftv:baffle` attachment is public by id only, and every service is internal ([API spec](api.md#researchview-and-baffleview)).

## Production tickets

This spec settles every decision. Three required child tickets deliver the assets and the authored content, and **the system is not done until all three land**. **None of them writes mod code:** no item, attribute, attachment, listener, HUD layer, renderer, payload, Curios adapter, platform adapter, datagen provider or validator. The asset ticket delivers a generator (authorised tooling under `tools/art/`) and assets under the [art style](art-style.md); the content tickets deliver authored inputs under `content/`, in the schemas above. The runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Armor assets** | **Generator:** `tools/art/armor.py`.<br>**Items** (16×16, the [art style §5](art-style.md#5-tone-ramp-by-stage) row of each item's Stage): `tuned_spectacles` (1: copper frame, amethyst lenses, the tick outside); `felted_coat`, `felted_breeches`, `felted_boots`, `chime_pendant`, `resonant_brass_helmet`, `_chestplate`, `_leggings`, `_boots` (2); `baffle_ring` (3: mauve felt in a brass band, `teal1` in a groove); `void_helmet`, `void_chestplate`, `void_leggings`, `void_boots` (4: brass draining to Hush grey, teal inside void ink). Item models (parent `minecraft:item/generated`) and `assets/vftv/items/<id>.json` for each.<br>**Equipment:** `assets/vftv/equipment/felted.json`, `resonant_brass.json`, `void.json` (layers `humanoid`, `humanoid_leggings`, `humanoid_baby`) and `tuned_spectacles.json` (`humanoid`, `humanoid_baby`), with textures `assets/vftv/textures/entity/equipment/humanoid/<id>.png` (64×32), `humanoid_leggings/<id>.png` (64×32; not the spectacles) and `humanoid_baby/<id>.png` (64×64): palette-locked, lit from the upper left on each face, ink seams on plate edges, no outline around UV islands.<br>**HUD sprites:** `assets/vftv/textures/gui/sprites/hud/baffle_full.png`, `baffle_half.png`, `baffle_empty.png` (9×9). The card reuses the strike card's frame.<br>**Sounds** (Stage 3 register, [art style §7](art-style.md#7-sound-palette); felt-damped, never a clang): `baffle/absorb_0…2.ogg`, `baffle/break.ogg`, with the `sounds.json` entries `baffle.absorb` (3 equal-weight variants) and `baffle.break` naming their subtitle keys, no overrides.<br>**Review:**<br>• a contact sheet at GUI scale 2 beside vanilla's iron armour, leather tunic and carved pumpkin;<br>• Blender renders of every set on a player-shaped figure (front, back, ¾) and on a small armour stand;<br>• **the HUD contract mockup at 320 × 240** (scaled), composed from the sprites and the vanilla font at the pinned coordinates under "The HUD" (the bottom-left pip at `(guiWidth / 2 − 91, guiHeight − hud.leftHeight)`, pips 8 px apart, rows 10 px up, the text at `x + 83`): the Baffle row above full armour and four absorption hearts at capacities 12 (with a half pip and text "1.5/12"), 28 (two rows, "27.5/28") and 40 ("40/40"), with vanilla's air row on the right, unmoved, and the spectacles card beside the strike card's position with an unheard aspect and a long test translation.<br>Provenance rows. | none |
| **Armor content** (Stages 1–3) | `content/armor/wearables.json`, `recipes.json`, `enchantments.json`, `tags.json`, `curios.json`, `bootstrap.json` and `fixtures.json` (every vector table transcribed), and `content/lang/en_us/armor.json` with every key under "Lang keys" except the Void names, all in the schemas above. Its review checks the bootstrap's sources and what "Lang keys" lists. | [Vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (the non-amplification lint replays its values) |
| **Void armour content** (Stage 4) | `content/armor/void.json` (`vftv:void_armor/1`: the four wearables, gear warp 2/3/2/2, the four transmute bodies and **their Stage 4 bootstrap**, every `why` reviewed) and `content/lang/en_us/armor_void.json` (the four names in Void-tongue, from lexicon words, no provisional English). | the **Void-tongue lexicon** ticket [#54](https://github.com/andrewbrogoff/voices-from-the-void/issues/54); #30 |

The research content tickets [#34](https://github.com/andrewbrogoff/voices-from-the-void/issues/34)–[#37](https://github.com/andrewbrogoff/voices-from-the-void/issues/37) consume the entry ids, recipe ids and fact sources frozen here.
