# Aspects and scanning

The aspect set, what every thing in the world is made of, and how the Listener finds out: the Tuning Fork, striking, sounding for nodes, knowledge and aspect points. Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). The code layout, data rules and sync rules come from the [architecture spec](architecture.md), and every gate from the [quality gates](quality-gates.md).

Resolved in [Aspects and scanning spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/15). Inputs: [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md), [TC4 alchemy](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-alchemy/docs/research/tc4-alchemy.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. Each tunable one is listed with its data home under "Tuning".

## Stage

Everything here belongs to **Stage 1, Resonance**. The find that opens it is an **amethyst geode**: the Tuning Fork is a plain crafting-table recipe (a copper ingot and an amethyst shard) with no research gate.

- **Obtaining counts as finding.** Stage 1 is recorded the first time a player holds an amethyst shard or a Tuning Fork, however they got it. A newcomer to a server, or a player handed a fork, is never locked out for not mining the geode themselves.
- **One later beat:** the Vacuos revelation is Stage 3, opened by a Choir page. The page and its research entry are [Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s; the ruins that hold it are [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s.

## The aspect set

**37 aspects: 6 primals, the hidden primal Vacuos, and 30 compounds.** The tree is broad and shallow. Depth is what made the old tree a grind, and the community asks that difficulty come from new constraints, not longer paths. Every aspect costs an icon, value coverage and puzzle tuning, so the set is no bigger than the nouns of the world need.

- **Pairs are ours.** A few are kept where the meaning forces them (light, life, death) and Vacuos's taught pair is locked by the lore. The rest are redesigned so that the lore lives in the graph: tone is ordered air (Sonus), the soul is a voice (Spiritus = Victus + Sonus), silence is sound gone into the Void (Silentium), and the Choir are souls gone into the Void (Alienis = Spiritus + Vacuos).
- **Names are Latin; glosses are English** and appear only in tooltips.
- **Tier** is derived from the graph and used only for display. It shifts when Vacuos is revealed, so nothing that matters for balance may depend on it.

### Graph rules

The validator lives in `core.aspect.model` and runs when a world opens, on the **true view** (Vacuos as a primal) and the **taught view** (Vacuos as Aer + Perditio) separately.

| Rule | On failure |
|---|---|
| Every compound has exactly 2 distinct parents, and every parent exists. | world refuses to load, with a clear error |
| The graph is acyclic. | refuses to load |
| Each unordered parent pair is unique, so combining is unambiguous. | refuses to load |
| Every built-in `api` key is present. | refuses to load |
| Depth ≤ 3. | built-ins: test failure; addons: warning |
| Every exposed primal is a parent of ≥ 2 tier-1 aspects. Hidden primals are exempt. | built-ins: test failure; addons: warning |
| No compound is a parent of more than 5 children, so nothing becomes a choke point. Primals are natural hubs and are exempt. | built-ins: test failure; addons: warning |

### Vacuos

The lore's central revelation, carried by data rather than special code ([ADR 0003](../adr/0003-aspects-as-datapack-registry.md)).

- **Data:** `parents: []` (it truly is a primal) and `hidden_primal: {taught_parents: [vftv:aer, vftv:perditio], revealed_by: <research id>}`.
- **Taught view (before the reveal, per player):** the book draws Vacuos as Aer + Perditio, discovery treats Aer and Perditio as its prerequisites, and the puzzle links it to Aer and Perditio.
- **After the reveal:** the book draws it as a primal. The puzzle keeps the taught links and *adds* a link to every exposed primal ("the other primals sit on top of it"). Old notes stay valid.
- **The world is truthful from the start.** Vacuos never decomposes, and the fork never shows parentage. Only the notebook's prose made the claim, which the lore allows. Whether Aer and Perditio essentia combine into Vacuos is the crucible's call (see "Contracts"), but the book never states a recipe the world refuses.

### The aspects

Tiers are true tiers. In the taught view Vacuos is tier 1, which makes Tenebrae, Fames and Silentium tier 2 to the player; Alienis stays tier 3.

| Tier | Aspect | Parents | Gloss | Colour | Icon glyph |
|---|---|---|---|---|---|
| Primal | **Aer** | | air, breath, wind | `gale` | an upright feather, vane to the left |
| Primal | **Terra** | | earth, stone, soil | `loam` | a cairn of three stacked stones |
| Primal | **Ignis** | | fire, heat | `ember` | a candle stub with a split flame |
| Primal | **Aqua** | | water, liquid | `tide` | a wave crest curling to the left |
| Primal | **Ordo** | | order, law, pattern | `lumen` | a plumb bob hanging on its line |
| Primal | **Perditio** | | entropy, decay, undoing | `blight` | a clay pot split by one jagged crack |
| Hidden primal | **Vacuos** | none (taught: Aer + Perditio) | void, emptiness | `ink` | a ring around a hole of void ink, one teal pinprick inside |
| 1 | **Lux** | Aer + Ignis | light | `lumen` | a hanging lantern with a lit pane |
| 1 | **Gelum** | Aqua + Ordo | cold, ice | `tide` | an icicle hanging from a bar |
| 1 | **Motus** | Aer + Aqua | motion | `gale` | a tilted spinning top with one swept stroke behind it |
| 1 | **Potentia** | Ignis + Ordo | power, energy | `ember` | an upright coiled spring |
| 1 | **Victus** | Aqua + Terra | life | `vital` | an egg with a single crack of light |
| 1 | **Vitreus** | Terra + Ordo | crystal, glass | `lumen` | a triangular prism standing on its base |
| 1 | **Venenum** | Aqua + Perditio | poison | `blight` | a spotted toadstool |
| 1 | **Permutatio** | Ordo + Perditio | change, exchange | `blight` | an hourglass |
| 1 | **Sonus** | Aer + Ordo | sound, tone | `gale` | a curled hunting horn, bell to the right |
| 1 | **Metallum** | Terra + Ignis | metal | `brass` | a chime bar with one hanging hole |
| 1 | **Tenebrae** | Terra + Vacuos | darkness | `ink` | a crescent moon, hollow side to the right |
| 2 | **Herba** | Victus + Lux | plants | `verdure` | a three-leaf sprig |
| 2 | **Bestia** | Victus + Motus | beasts | `vital` | a four-toed paw print |
| 2 | **Mortuus** | Victus + Perditio | death | `blight` | a withered flower drooping on its stem |
| 2 | **Spiritus** | Victus + Sonus | soul, spirit | `amethyst` | a rising curl of breath above an open mouth-ring |
| 2 | **Praecantatio** | Potentia + Sonus | magic, incantation | `amethyst` | a scroll half unrolled, closed with a seal |
| 2 | **Fames** | Victus + Vacuos | hunger | `ink` | an empty bowl with a spoon |
| 2 | **Instrumentum** | Metallum + Ordo | tools | `brass` | an upright hammer |
| 2 | **Silentium** | Sonus + Vacuos | silence | `hush` | a hand bell with its clapper bound |
| 3 | **Arbor** | Herba + Terra | wood, trees | `verdure` | a round-crowned tree on a short trunk |
| 3 | **Sano** | Herba + Ordo | healing | `vital` | a mortar with its pestle |
| 3 | **Corpus** | Bestia + Terra | flesh, body | `vital` | a joint of meat on the bone |
| 3 | **Exanimis** | Mortuus + Motus | undeath | `blight` | a cracked headstone |
| 3 | **Cognitio** | Spiritus + Ordo | mind, knowledge | `amethyst` | a pair of round spectacles |
| 3 | **Humanus** | Spiritus + Bestia | humankind | `vital` | an open hand, palm out |
| 3 | **Telum** | Instrumentum + Perditio | weapons | `brass` | an arrowhead pointing up |
| 3 | **Tutamen** | Instrumentum + Vitreus | protection | `brass` | a kite shield |
| 3 | **Pannus** | Bestia + Instrumentum | cloth | `mauve` | a spool of thread with a needle through it |
| 3 | **Machina** | Instrumentum + Potentia | machines | `brass` | a six-toothed cog |
| 3 | **Alienis** | Spiritus + Vacuos | the Choir, the otherworldly | `ink` | an arched doorway filled with void ink, one teal point inside |

**How it passes the rules** (checked by script on this table): 37 aspects; every pair unique; maximum depth 3 in both views; exposed primals parent 2–5 tier-1 aspects (Perditio has the fewest, Venenum and Permutatio; Ordo the most); the largest compound fan-out is Victus with 5, and Vacuos, a compound in the taught view, has 4.

**Folded away:** weather, slime, binding, travel, flight, the senses, aura, greed, harvest, mining, crafting and the deep "human" chain have no aspect of their own. Their things carry Aer, Aqua, Motus, Victus, Instrumentum and so on.

### Colours

Each aspect takes a palette ramp from [art-style §2](art-style.md#2-palette-locked-project-wide-48-colours): the owner-approved **aspect ramp** (ember, tide, loam, gale, verdure, vital, blight, lumen), plus `amethyst` for mind and voice, `mauve` for cloth, `brass` for metal, `ink` for void and `hush` for silence. Teal is resonance only, so no aspect uses it. The registry's `color` is that ramp's base step (step 1 of an aspect-ramp family). For the older ramps, the Aspect icons ticket picks the step.

- Aspects that share a ramp are told apart by their glyph, and by ramp step where the icon needs it.
- **Identity rests on shape, not colour.** Every glyph is distinct in greyscale, and jars and tubes carry labels as well as tint.
- Built-in colours are palette members (a test enforces it). Addon colours may be any RGB.

### Icons

Free-standing pictographic glyphs, one per aspect, plus the **unheard** icon (an open ink ring broken at the top, like a bell mouth with no clapper).

- **Original.** Each glyph is a small picture of a thing a natural philosopher would keep on a desk or meet in the field, never an alchemical or geometric sign, and never drawn from memory of another mod's icons. The column above is the brief.
- **Style:** [art-style §3](art-style.md#3-resolution-and-pixel-rules) rules: 16×16, 1-px `ink1` outline, light from the upper left, no anti-aliasing. A glyph surface painted with an aspect-ramp family may use its two steps only, which §3's 3–4-step shading rule allows for these glyphs. Highlights and specs come from the existing ramps. **No teal tick**: the aspect *is* the resonance. Vacuos and Alienis follow the Stage 4 row (teal inside void ink), and Silentium the Hush (hush greys, no teal).
- **Files:** `assets/vftv/textures/gui/sprites/aspect/<id>.png` in the GUI atlas, from one generator, `tools/art/aspects.py`, with provenance rows.
- **Checks:** a greyscale contact sheet at GUI scale 1 is reviewed by an agent, and every pair of icons must be told apart. A test checks that each icon contains the identity-colour pixels its generator declares and that they match the aspect's JSON colour (not "dominant colour", which a navy outline can win on a small glyph).

### Registry format

The aspect set is the synced datapack registry `vftv:aspect` ([ADR 0003](../adr/0003-aspects-as-datapack-registry.md)). Entries are minimal: types in code, instances in data.

| Field | Required | Meaning |
|---|---|---|
| `color` | yes | RGB used for icons and essentia tints |
| `parents` | yes | 0 or 2 aspect ids |
| `icon` | no | sprite id; defaults to `<ns>:aspect/<path>` |
| `hidden_primal` | no | `taught_parents` (2 ids) and `revealed_by` (a research id, checked once research data loads) |
| `discovery_bonus` | no | points granted on discovery; defaults to the tuning value |

Name and gloss are lang keys `aspect.<ns>.<path>` and `aspect.<ns>.<path>.gloss`. The `api` exposes the element interface `Aspect`, `AspectKeys` (a `ResourceKey` per built-in, and the registry and data-map keys), the data-map value `AspectValues` and, in `api.neoforge`, the `DataMapType`s ([API spec, The inventory](api.md#the-inventory)).

**Overrides:** datapacks may add aspects and override ours (colour, icon, parents). They cannot delete ours, because code holds their keys. The whole graph is re-validated. The set is fixed when a world opens: changing it needs the world reopened, while the value maps below follow `/reload`.

## Aspect values

### Data maps

What a thing is made of is one fact about the world, so scanning and the [crucible](https://github.com/andrewbrogoff/voices-from-the-void/issues/19) read the same maps. The crucible applies its own yield to them; it never forks the data.

- **`vftv:aspects`**, a synced data map on **item**, **block** and **entity_type**. Value: `{"vftv:ignis": 3, …}`, positive integers, at most 5 aspects (one HUD row). `{}` is an explicit "nothing to learn", distinct from no entry.
- **`vftv:aspect_defaults`**, a synced item data map keyed by `c:` convention tags (e.g. `#c:ingots` → Metallum, `#c:gems` → Vitreus). It is consulted only when `vftv:aspects` has no entry, so a broad tag can never override a specific value.
- **Lookup:** a placed block uses its block entry, else its `asItem()` item entry, else the defaults. An item uses its entry, else the defaults. An entity uses its entry; a dropped item entity reads as the item it carries. Anything else has nothing to learn.
- The block map holds only itemless blocks (water, lava, fire, portals) and blocks that genuinely differ from their item.
- **Precedence inside a map is NeoForge's:** the last pack wins per key, with `replace` and `remove`. NeoForge promises no ordering between an id entry and a tag entry, or between overlapping tags, so our built-in data never lets two entries in one map cover the same object (a datagen test checks it). Pack makers get whole-value replacement; nothing sums across packs.

### Vanilla values

A hand **base table** plus **recipe derivation at datagen**, committed to `src/generated` and caught by the datagen diff gate. It is deterministic, reviewable and free at runtime, and a stair stays consistent with its plank.

- **Base table:** raw materials, natural blocks and mobs, keyed by vanilla or `c:` tag wherever one exists.
- **Capture:** a small subclass of vanilla's `VanillaRecipeProvider` feeds recipes into a capturing output inside datagen.
- **Derivation** (in `core.aspect.model`: Minecraft-free, unit-tested, and reused by the derivation export below) is an authoring aid, not the authority:
  1. **Each ingredient contributes** its values minus the values of its returned remainder (the bucket of a milk bucket, the bottle of a potion), clamped at zero per aspect.
  2. For a tag or alternative ingredient, use the alternative with the lowest total (ties by item id).
  3. Sum the contributions, multiply by the derivation factor, and divide by the output count.
  4. Round down, but keep at least 1 of every aspect present in the sum, so planks and nuggets are never erased.
  5. For a smelted result, add the smelting bonus.
  6. *Then* keep 5 aspects: highest amount first, ties by ascending aspect id.
  7. For an item with several recipes, take the recipe whose result has the lowest total (ties by recipe id).
  8. **Resolution order:** an item's value is resolved from hand entries first, then by recipes whose ingredients are all resolved, repeated until nothing changes. An item still unresolved, for example one that sits in a cycle with no hand entry, **fails datagen**, naming the item and the cycle. The fix is a hand override or a reviewed exemption.
- **Semantic overrides are mandatory** where ingredients cannot introduce an aspect: tools gain Instrumentum, weapons Telum, armour Tutamen.
- **Reversible conversions** (nugget ↔ ingot ↔ storage block, and the like) are hand-entered as exact multiples of each other; derivation never applies to them. That is the only way minimum-1 rounding and non-amplification hold together, and the scan-once rule bounds the large values storage blocks get.
- **Value scale:** small integers. A common raw block totals 2–5, a rare material 8–16, a mob 4–12. These are authoring targets, not caps.

**Unknown and modded content** gets the `c:` tag defaults in v1, and otherwise nothing. **There is no runtime derivation in v1**, and the lookup above is the same with or without other mods ([compatibility, In play](compat.md#in-play)).
- **The derivation export:** an operator's `/vftv aspects derive` runs this derivation over the live server's data and writes **reviewable datapacks** that the operator installs by hand ([compatibility, The derivation export](compat.md#the-derivation-export), which owns its rules).
- **What it derives over:** only items with **neither** a `vftv:aspects` entry **nor** a default. An explicit `{}` is authored suppression and is never derived over.
- **Conservation:** it excludes every item on a conversion cycle of the recipes it accepts.
- **Shared adapter:** the adapter from a recipe to the model's input is one class in `core.aspect`, `DerivationRecipes`. Datagen's capture and the export both use it, and it accepts exactly vanilla's recipe classes, as that section lists.

### Coverage and supply

- **Coverage:** every survival-obtainable vanilla item, block and entity has an entry or sits in a reviewed exempt list (air, barrier, command blocks, spawn eggs…).
- **Known limitation:** scans ignore item components and block states in v1, so potions, tipped arrows, enchanted books and suspicious stew each share one value per item. They are listed as such.
- **Supply:** every aspect has ≥ 3 accessible sources, across vanilla *and* VFTV content, obtainable in the Stage where it is first needed. Hush and Choir aspects may come from VFTV content alone.
- **Affordability:** a test checks that the Stage 1 research #16 defines is affordable from Stage 1 supply alone, starting from the bootstrap.
- **Non-amplification:** no round trip through crafting recipes yields more aspects than went in.

## The Tuning Fork

The first instrument: copper and amethyst, no durability, no ink, nothing consumed. Striking is the verb (lore and glossary), and a strike is instant; the ring is a cooldown, never a wait.

### Striking

Right-click with the fork in hand. Reach is vanilla interaction reach.

| Target | How |
|---|---|
| **Block** | right-click it. The fork takes priority over the block's own use, so a chest does not open and a door does not swing (a thin `neoforge` listener on the right-click-block event calls `core`). |
| **Entity** | right-click it (`interactLivingEntity`, and the entity-interact event for non-living entities). The entity's own interaction (trading, mounting) is suppressed. A dropped item reads as the item it carries. |
| **Item in an inventory** | with the fork on the cursor, right-click a slot (`overrideStackedOnOther`, `overrideOtherStackedOnMe`). No dropping items to scan them. |
| **Node** | right-click [the node](https://github.com/andrewbrogoff/voices-from-the-void/issues/17). |
| **Air** | sounding for nodes (below). Using the fork with no block or entity targeted on the client always sounds; it is never silence. |

- **Cooldown:** a vanilla `ItemCooldowns` cooldown, shown by the hotbar overlay and by a "ringing" item model through the native `minecraft:cooldown` property, with no code. It is a gameplay number, independent of the strike sound's 1.1 s tail.
- **A click during cooldown is consumed** and does nothing: no readout, and no chest opens.
- **The inventory path runs on both sides** and bypasses the ordinary use cooldown. The client callback only consumes the click; the server checks the cooldown itself, validates the input (the player has that menu open, the slot is real and accessible, the carried stack is a fork), and is the only side that grants anything.
- **Sounds:** strike ×4 pitch and seed variants, node answer ×3, discovery chime ×1, within [art-style §7](art-style.md#7-sound-palette): the events `vftv:fork.strike`, `vftv:fork.answer` and `vftv:fork.discover`, whose `sounds.json` entries carry no overrides and name `subtitles.vftv.fork.strike`, `.answer` and `.discover` ("Lang keys"). Positional sound plus vanilla subtitles gives a visual cue to players who can't hear.

### What a strike reveals

**Instruments never lie.** A **HUD readout card** near the crosshair shows the target's name and its aspects with exact amounts. It stays readable for the readout hold after the *latest* strike, then fades: on the client, `ceil(readout_hold_ticks × max(1, notificationDisplayTime))` ticks ([localisation, How long transient text stays](accessibility.md#how-long-transient-text-stays)). Chat is never used.

- **Narration:** when a strike's `ScanResult` arrives, the card's text is queued once as system narration ([localisation, HUD narration](accessibility.md#hud-narration)): the name, the state's line, and each aspect as `narration.vftv.strike.aspect` with its exact amount, an unheard one named `aspect.vftv.unheard`. Never per frame.
- **Overflow:** the [localisation spec's R3](accessibility.md#r3-hud-text): the card wraps and never clips, in the [crucible HUD's](crucible.md#the-hud) geometry, caption band and sheets (head: the target's name; no foot). Its layout is rebuilt when a `ScanResult` arrives, the language, resources or scaled screen change, the [caption band's](accessibility.md#the-caption-band) revision changes, or the sheet turns; never per frame.

- **Blocks, items, entities:** their own aspects only (never container contents), in one row.
- **Nodes:** their aspects with current / max, plus whatever type and brightness #17 defines. Rows **wrap**; nothing is silently truncated.
- **Unheard aspects** (see "Knowledge") show the unheard icon with the **exact amount**, and a hint: the icons of their parents the player already knows, and a blank for each one they don't.
- **Feedback states,** each with its own lang key:

| State | Readout |
|---|---|
| nothing to learn (`{}` or no entry) | "Nothing answers." (`hud.vftv.strike.nothing`) |
| first scan | aspects, points gained (`hud.vftv.strike.gained`) |
| already scanned | aspects, "In your notes" (`hud.vftv.strike.known`) |
| partly scanned | known aspects, unheard ones with their hint (`hud.vftv.strike.partial`) |
| silence (a block or entity strike whose target the server does not have: a phantom) | silence: no ring, no card |
| no answer (sounding found no node) | "No answer." (`hud.vftv.sounding.none`) |

- **Tooltips:** once a thing's key is scanned (fully or partly), its item tooltip shows its aspects while Shift is held. JEI shows aspects only once the player knows them.
- **Teaching:** the fork's own tooltip carries the inventory-strike instruction ("Carry the fork and right-click an item to strike it"), and the first Commonplace Book entry repeats it. No external wiki is needed.

**Phantoms.** Warp's phantom blocks and mobs are client-only, so the server answers what is truly there. Struck phantom mob: the server has no such entity → silence. Struck phantom block over air → silence (the client sent a block strike, which the server cannot match; this is not sounding). Struck phantom block over a real block → the card names the real block, and the phantom vanishes when touched. The fork is how a Listener tells what's real; this is a contract on the warp spec (below).

### Sounding for nodes

Strike air to sound. Listening stays a deliberate act; nothing hums on its own.

- The server asks #17's node index for the **nearest node within the sounding radius**, in the player's dimension, by 3-D Euclidean distance from the player's eye position. The radius is inclusive, and ties go to the lowest stable node id. The query loads and generates no chunks.
- **Bands:** near is `d < near`, mid is `near ≤ d < mid`, and far is `mid ≤ d ≤ radius`.
- **Answer:** a positional answer sound always played at the answer offset along the bearing, never at the node, plus a few teal particles drifting that way. Distance is carried by the band alone.
- **Bearing:** a HUD marker showing the direction taken at the strike (including up or down) and the distance band (near, mid, far). The band is printed beneath the marker as its word (`hud.vftv.sounding.near`, `.mid`, `.far`), never carried by colour or pitch alone. It stays until the next strike or until the fork leaves the hand. Only a direction and a band reach the client, never a node position.
- **Narration:** when the answer arrives, `narration.vftv.sounding` is queued once as system narration: the compass direction (one of eight, `hud.vftv.sounding.dir.*`), up, down or level (`hud.vftv.sounding.up`, `.down`, `.level`), and the band; or `hud.vftv.sounding.none`.
- **No answer:** beyond the radius the fork says so explicitly.
- A **starter node** within the sounding radius of the bootstrap location is #17's guarantee (see the contracts), so the first sounding always answers.

### Knowledge

Per player. **A scan always succeeds**: there is no wall of "you don't have the knowledge".

- **Known aspects pay out.** An aspect whose prerequisites are both known is **discovered on the spot**. Prerequisites are its parents, or its taught parents for a hidden primal. Primals have none, so they are discovered on first contact and no starting set is special-cased.
- **Discovery resolves to a fixed point in dependency order** within a strike (lowest tier first, ties by id), so a thing holding Aer, Ignis and Lux discovers all three at once whatever the map order.
- **Any other aspect is unheard.** Its amount is exact, and the thing is recorded as **partly scanned**. Striking it again once the prerequisites are known pays the rest.
- **Scan keys are typed:** `item:<id>` for anything with an item form (a placed log and a log item are one key), `block:<id>` for blocks with their own block-map entry, `entity:<id>`, `node:<stable id>`. Block states and item components are ignored in v1.
- **What is stored per key:** completed, or partly scanned with a **snapshot** of the values at the first strike and the aspects already paid. A partial scan pays the rest from its snapshot, so a `/reload` that changes values never repays, never shrinks a reward already promised, and never depends on which form was struck first.

### Aspect points

**No caps** of any kind (progression spec). Scan-once is the only throttle on supply; [#16](https://github.com/andrewbrogoff/voices-from-the-void/issues/16) owns demand.

| Source | Pays |
|---|---|
| First scan of a thing | exactly its values (known aspects now, unheard ones on completion) |
| Discovering an aspect | its `discovery_bonus` of that aspect |
| First strike on a node, per player per node | ceil(max capacity ÷ node reward divisor) of each of its aspects, using the max capacity at that first strike |
| Choir pages, research rewards | #16's |
| The Voices: insight events and `points` Bargains | [#25](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)'s ([warp spec, Event types](warp.md#event-types), [Bargains](warp.md#bargains)): points of aspects the player **already knows**, credited through the knowledge service |

Points are spent in #16's puzzle and in skip mode.

- **Voice credits never teach.** An insight draws only from the known aspects, and a `points` Bargain is offered and accepted only while every one of its aspects is known. Neither runs discovery, pays a discovery bonus or fires `AspectDiscoveredEvent`, so the Voices can never name an unheard aspect.

### The Commonplace Book

Striking a **blank Book and Quill** in the inventory turns it into the Commonplace Book. This is the one built-in instance of a minimal data-defined recipe type, **`vftv:strike`** (an ingredient and a result): types in code, instances in data.

- It runs through the inventory strike path, before scanning.
- **A Book and Quill with writing is refused**, and the readout tells the player why ("The ink would be lost.", `hud.vftv.strike.ink`). Player text is never destroyed.
- The Commonplace Book item itself is #16's.
- **JEI** shows strike recipes in the ungated Striking category, up to the compatibility cap; gameplay accepts every one ([compatibility, Striking](compat.md#striking)).

## Lang keys

This spec's keys are **code-defined UI keys**: their English below is **final**, and datagen writes them into `assets/vftv/lang/en_us.json`, as the [configuration spec's](config.md#lang-keys) are written, so no `content/` file and no production ticket is needed. Translations go in `content/lang/<lang>/aspects.json`. The **Latin names are protected text**: every locale keeps them exactly; glosses are translated ([localisation, Protected text](accessibility.md#protected-text)).

| Key | Args | Text |
|---|---|---|
| `item.vftv.tuning_fork` | 0 | "Tuning Fork" |
| `aspect.vftv.<path>` | 0 | the Latin name, for each of the 37 aspects below |
| `aspect.vftv.<path>.gloss` | 0 | the gloss, for each of the 37 aspects below |
| `aspect.vftv.unheard` | 0 | "something unheard" (an unheard aspect's name in narration) |
| `tooltip.vftv.tuning_fork` | 0 | "Carry the fork and right-click an item to strike it." |
| `tooltip.vftv.aspect` | 2 | "%s: %s" (name, gloss) |
| `tooltip.vftv.aspects.entry` | 2 | "%s %s" (name, exact amount; the Shift tooltip's rows) |
| `hud.vftv.strike.nothing` | 0 | "Nothing answers." |
| `hud.vftv.strike.gained` | 1 | "Gained: %s" (the points, as "*Aspect* *n*" joined by ", ") |
| `hud.vftv.strike.known` | 0 | "In your notes" |
| `hud.vftv.strike.partial` | 0 | "Some of it I can't yet hear." |
| `hud.vftv.strike.of` | 2 | "%s / %s" (a node aspect's current and max) |
| `hud.vftv.strike.ink` | 0 | "The ink would be lost." |
| `hud.vftv.sounding.none` | 0 | "No answer." |
| `hud.vftv.sounding.near`, `.mid`, `.far` | 0 | "near", "farther", "far" |
| `hud.vftv.sounding.dir.n`, `.ne`, `.e`, `.se`, `.s`, `.sw`, `.w`, `.nw` | 0 | "north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west" |
| `hud.vftv.sounding.up`, `.down`, `.level` | 0 | "above", "below", "level" |
| `narration.vftv.strike.aspect` | 2 | "%s: %s" (name or `aspect.vftv.unheard`, exact amount) |
| `narration.vftv.sounding` | 3 | "An answer: %s, %s, %s." (direction, up or down, band) |
| `subtitles.vftv.fork.strike`, `.answer`, `.discover` | 0 | "Tuning fork rings", "A node answers", "Something is understood" |

**Names and glosses** (the table under "The aspects", in order; the gloss is that table's, verbatim):

| Key path | Name | Gloss |
|---|---|---|
| `aer` | Aer | air, breath, wind |
| `terra` | Terra | earth, stone, soil |
| `ignis` | Ignis | fire, heat |
| `aqua` | Aqua | water, liquid |
| `ordo` | Ordo | order, law, pattern |
| `perditio` | Perditio | entropy, decay, undoing |
| `vacuos` | Vacuos | void, emptiness |
| `lux` | Lux | light |
| `gelum` | Gelum | cold, ice |
| `motus` | Motus | motion |
| `potentia` | Potentia | power, energy |
| `victus` | Victus | life |
| `vitreus` | Vitreus | crystal, glass |
| `venenum` | Venenum | poison |
| `permutatio` | Permutatio | change, exchange |
| `sonus` | Sonus | sound, tone |
| `metallum` | Metallum | metal |
| `tenebrae` | Tenebrae | darkness |
| `herba` | Herba | plants |
| `bestia` | Bestia | beasts |
| `mortuus` | Mortuus | death |
| `spiritus` | Spiritus | soul, spirit |
| `praecantatio` | Praecantatio | magic, incantation |
| `fames` | Fames | hunger |
| `instrumentum` | Instrumentum | tools |
| `silentium` | Silentium | silence |
| `arbor` | Arbor | wood, trees |
| `sano` | Sano | healing |
| `corpus` | Corpus | flesh, body |
| `exanimis` | Exanimis | undeath |
| `cognitio` | Cognitio | mind, knowledge |
| `humanus` | Humanus | humankind |
| `telum` | Telum | weapons |
| `tutamen` | Tutamen | protection |
| `pannus` | Pannus | cloth |
| `machina` | Machina | machines |
| `alienis` | Alienis | the Choir, the otherworldly |

**Used, not declared here:** a node's type and clarity names ([wands, Lang keys](wands.md#lang-keys)), the Striking category's title ([compatibility, Lang keys](compat.md#lang-keys)), and the target's own name component.

**Rejected:** a key outside the table; an aspect key for a path not in the built-in registry; a required key missing in `en_us`; a placeholder count that differs; an empty string; a duplicate key. An addon's aspects carry their own `aspect.<ns>.<path>` keys in their own lang files.

## Events

NeoForge post-events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), fired on the server after state is committed, plus plain service methods in `core`, which are internal in v1. The payload shape, immutability and failure rules are the [API spec's](api.md#events), and the scan key's representation is `ScanKey` there.

| Event | Payload |
|---|---|
| `ThingScannedEvent` | player; scan key; target kind; outcome; `complete` (whether the key is now fully scanned); points actually awarded per aspect (possibly zero) |
| `AspectDiscoveredEvent` | player; aspect; bonus actually awarded |

**Outcomes:**
- `FIRST`: the key had no ledger entry. It may be partial; check `complete`.
- `PARTIAL`: an existing partial entry is still incomplete.
- `COMPLETED`: an existing partial entry became complete.
- `REPEAT`: the entry was already complete.

No `ThingScannedEvent` fires for sounding, silence, "nothing to learn", or a `vftv:strike` conversion. #16 uses these events for hidden entries and Stage gates.

## Sync and state

**Server-authoritative, owner-synced.**

- **Knowledge** is one player `AttachmentType` with `copyOnDeath`: known aspects, scan keys (with partial snapshots) and the point pool. It sends a **full baseline on join**, then deltas through `AttachmentSyncHandler`, **filtered to the owner explicitly** (the handler defaults to every tracking player).
- **No sharing** of knowledge or points in v1; gates are per player.
- **No custom client-to-server payload.** Every strike arrives through vanilla use, interact or container-click packets, and those inputs are validated like any other (range, menu, slot, held item).
- **One server-to-client payload,** `ScanResult`, to the striker only: target name, aspects and amounts, unheard hints, state, and for sounding the bearing and band. It also carries the readout hold, so the tuning file stays server-only. The strike sound is broadcast; the readout is private.
- The registry and both data maps are synced by their network codecs. A client mod can read every value; that is no worse than reading the datapack.

## Tuning

Every tunable number, all ours. Unless noted, the home is the server-only datapack file **`data/vftv/tuning/scanning.json`**, loaded on reload into an immutable record in `core.aspect`, and written by datagen from that record's defaults.

| Key | Value | Notes |
|---|---|---|
| `strike_cooldown_ticks` | 16 | gameplay cooldown |
| `readout_hold_ticks` | 60 | after the latest strike; sent in `ScanResult` |
| `discovery_bonus` | 4 | default; an aspect's own `discovery_bonus` field overrides it |
| `node_reward_divisor` | 8 | default; a node type's own field in #17's data overrides it |
| `sounding.radius` | 192 | 1–256, the largest radius [#17's node index](wands.md#the-index) supports; the performance bound below assumes 192 |
| `sounding.near`, `sounding.mid` | 16, 64 | band edges; far runs to the radius. Valid only if 0 < `near` < `mid` ≤ `radius` |
| `sounding.answer_offset` | 6 | blocks along the bearing |
| `sounding.particles` | 3 | |
| `derivation.factor` | 0.75 | used by datagen, and by the [derivation export](compat.md#the-derivation-export), which reads the loaded value; nothing reads it in play |
| `derivation.smelt_bonus` | `{"vftv:ignis": 1}` | as above |

**Validation:** a `sounding.radius` outside 1–256, or band edges that break 0 < `near` < `mid` ≤ `radius`, makes the file invalid. An invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged.

**Fixed limits, in code** (formats and validator rules, not balance), as constants in **`AspectLimits`** in `api`: at most 5 aspects per material entry; depth ≤ 3; compound fan-out ≤ 5; ≥ 2 tier-1 children per exposed primal; the sounding radius ≤ 256. **Test thresholds:** ≥ 3 sources per aspect. **Authoring targets:** the value-scale totals above.

## Performance

**Heavy-base workload:** one player striking on every cooldown, alternating block, entity, inventory and sounding strikes, with 2,000 known scan keys, the HUD card up, and a 36-slot inventory hovered with Shift. The sounding runs in an area at #17's maximum node density with moved nodes; one strike reads a node with #17's maximum aspect count; one partial completion pays several aspects at once; and one join sends the full baseline.

**Share:** server ≤ 0.02 ms mean, ≤ 0.1 ms p99; client ≤ 0.02 ms p50, which also covers the JEI Striking category's drawing while it is open ([compatibility, Performance](compat.md#performance)). The derivation export is an operator load scenario there, outside this share.

**Hot paths:**
- **Server, per tick:** none.
- **Server, per strike:** O(1) data-map and knowledge lookups; the discovery fixed point is bounded by the aspects on one thing (≤ 5, or #17's node maximum). Sounding examines at most the index cells within the radius, bounded by #17's per-cell cap, and **loads 0 chunks** (a gating GameTest).
- **Server, on join:** one baseline, linear in known keys.
- **Client, per frame while the card is up:** layout is precomputed when `ScanResult` arrives; our draw code allocates **nothing** per frame (an allocation-budget test). Engine draw calls are outside that claim.
- **Client, tooltips:** one O(1) lookup per hovered stack, with no per-frame list building. This answers the old complaint of scanning UIs dropping frames with long aspect lists.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- the graph validator on both views: every rule, the reveal, and the shipped table;
- derivation: alternatives and tie-breaking, remainders, cycles, minimum-1 rounding, top 5, smelting bonus;
- **non-amplification** across every reversible conversion in the generated table, replayed over the recipes as datagen writes them, with their ingredients widened to `c:` tags ([compatibility, Consumed](compat.md#consumed));
- the knowledge ledger: first scan, partial then completed, repeat pays 0, discovery bonus, **same-strike discovery order** (one result for any map iteration order), taught prerequisites for Vacuos;
- **reload reward conservation:** values change between a partial scan and its completion, and the payout follows the snapshot;
- **mixed-map ordering:** block entry over item entry over defaults; `{}` versus no entry;
- nearest-node selection and band and bearing maths, including exact band and radius edges and ties by id;
- tuning validation: a radius of 257, and band edges out of order or beyond the radius, are rejected;
- codecs: the aspect entry, both data-map values, the tuning file, the knowledge attachment with its baseline and delta `StreamCodec`s, `ScanResult`, the strike recipe.

**Datagen and unit gates:** coverage, supply, Stage 1 affordability, no two built-in entries in one map covering the same object, and palette membership of built-in colours.

**GameTest:**
- the registry, both maps and the tuning file load; bad entries are rejected with clear errors (1 parent, a cycle, a duplicate pair, an unknown aspect, more than 5 aspects, zero or negative values, an unknown `revealed_by`);
- a strike on each target kind, including a dropped item;
- **cooldown click suppression:** a struck chest does not open, and a click during cooldown does nothing;
- the inventory strike, including the server's own cooldown and input checks;
- a partial scan, then completion;
- the Book and Quill transform, and **refusal of a written book** (the book is unchanged);
- knowledge survives save and reload, and death;
- **two-player privacy:** one player's strike changes nothing for the other, and no knowledge payload reaches them;
- sounding is bounded and loads no chunks; no answer beyond the radius; neither `ScanResult` nor the answer sound packet carries a node's coordinates;
- **scan events:** first-partial, unchanged-partial, completion and completed-repeat each fire the right outcome and `complete` flag with the real awards (including zero); sounding, silence, nothing-to-learn and conversions fire none;
- **stable node identity:** a node moved by #17's mechanics keeps its key and does not pay its first-strike reward twice;
- a block-targeted strike on a position the server holds as air returns silence, while an ordinary air-use sounds;
- every test id above is in the discovery guard.

**E2E:**
- a strike shows the HUD card (screenshot, plus a state dump of the pool);
- Shift tooltips after a scan;
- the sounding bearing marker;
- **phantom targeting** on a real client: a phantom mob and a phantom block over air give silence, and a phantom block over a real block reads the real one;
- sync on join, reconnect and `/reload` (values update, rewards do not);
- the asset and lang validators cover icons, glosses and every feedback key;
- **narration** from the [localisation capture](accessibility.md#fixtures): a strike's card and a sounding's answer are each attempted once on arrival (unheard aspects as `aspect.vftv.unheard` with exact amounts) and never per frame; the bearing marker shows its band's word;
- the card's hold at `notificationDisplayTime` 2.0 is twice `readout_hold_ticks`; every fork sound plays with its subtitle.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)**
- The puzzle links aspects by the taught view before a player's reveal, and by the taught links plus every exposed primal after it. Notes made before the reveal stay valid.
- It owns the research entry named in Vacuos's `revealed_by`, and the revealing listen shows its warp cost before the player commits.
- **Affordability and recovery:** Stage 1 research is affordable from the supply this spec guarantees, and there is an active way to recover from spending mistakes (a refund or a renewable source), so no player can strand themselves.
- It owns the Commonplace Book item, records Stage 1 on the trigger under "Stage", and teaches the inventory strike in the first entry.
- It consumes `ThingScannedEvent` and `AspectDiscoveredEvent` rather than polling knowledge.

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)**
- It owns node placement and the node index. The index answers `nearest(pos, radius)` for the 192-block radius by examining a bounded set of cells, with a declared per-cell cap, and **never loads or generates a chunk**.
- **Node ids are stable** through moves, and creating or re-placing a node never mints a fresh identity for the same node.
- A **real, usable starter node** is guaranteed in the same dimension within the shipped sounding radius (192 blocks, by the distance rule under "Sounding for nodes") of a bootstrap location that #17 names, such as world spawn. #17 proves the guarantee with a starter-placement test.
- Node-type data supplies each node's aspects and max capacity, and may override `node_reward_divisor`. It declares the maximum aspects a node can hold (the performance bound depends on it).

**[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)**
- It dissolves using `vftv:aspects` and `vftv:aspect_defaults` as they are, with its own yield multiplier if it needs one.
- Vacuos behaves as a primal in decay: it never breaks down into Aer or Perditio.
- It states whether Aer and Perditio essentia combine into Vacuos, and the book never shows a recipe the world refuses.
- Nothing it does lets a round trip yield more essentia than went in.
- Jars and tubes carry labels as well as tint.

**[Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)**
- Phantom mobs have no server identity, and phantom blocks vanish when struck, so the fork's readout always names what is really there. *Met by the [warp spec](warp.md#phantoms): apparitions are client-only entities under negative ids, and a strike at one is cancelled on the client with its swing turned off, so nothing is sent and the fork stays silent rather than sounding; phantom blocks are only ever disguises over real blocks, so every strike reaches the real block, and the disguise vanishes. A phantom over air never arises.*

## Production tickets

This spec settles every decision. Three required child tickets deliver the assets and data, and **the system is not done until all three land**.

| Ticket | Delivers |
|---|---|
| **Aspect icons** | the generator `tools/art/aspects.py`; one 16×16 icon per aspect (37) plus the unheard icon, to the glyph briefs above and the approved palette and colour assignments above; the greyscale contact-sheet review; provenance rows |
| **Vanilla aspect value table** | authored inputs under `content/aspects/`: the hand base table (tag- or id-keyed), semantic overrides, reversible-conversion entries and the reviewed exempt list, in a schema the future datagen reads. No code: the `VanillaRecipeProvider` capture, the derivation output in `src/generated`, and the coverage, supply, affordability and non-amplification checks are build-phase acceptance criteria |
| **Tuning Fork final assets** | the final `tuning_fork` texture and model from the prototype, plus its "ringing" cooldown variant; strike ×4, node answer ×3 and discovery chime ×1 sounds, for the events `vftv:fork.strike`, `.answer` and `.discover`; the HUD readout frame sprite, its interior opaque `ink1` ([localisation, Text contrast](accessibility.md#text-contrast)); provenance rows |
