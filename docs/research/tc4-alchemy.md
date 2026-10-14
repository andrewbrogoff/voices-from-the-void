# TC4 behaviour catalogue: alchemy and infusion

Research for ticket #3 (map #1). This is a clean-room catalogue of how Thaumcraft 4 *behaves* from a player's point of view. It covers the crucible, essentia logistics, infusion and wand foci. Everything here comes from public wikis. No mod code or assets were read, decompiled or unpacked. Nothing was observed in-game either: the wikis were enough, and the few numbers worth checking are listed under [Open questions](#open-questions).

**Baseline version.** Most sources describe TC 4.2.x, mostly 4.2.3.5 (the last 1.7.10 release). Where a source notes that something changed between 4.0, 4.1 and 4.2, the 4.2 behaviour is the one recorded here.

**Tags.**
- **[S: id]** means a sourced fact. The id points to the [Sources](#sources) table.
- **[I]** means my own inference. It is not stated by any source.
- **[C]** means the sources disagree. Both values are given.
- **[1src]** means only one source states it, so treat it as lower confidence.

**Units.** Wand vis is shown in whole "vis" with two decimals. Machines speak in centivis (1 vis = 100 centivis). The focus costs below are in vis per use, as the wikis give them.

---

## 1. Crucible

### Setup
- You make it by right-clicking a vanilla cauldron in the world with any wand. This costs no vis. [S: F-Cru, B-Cru]
- It needs a constant heat source directly underneath: a fire block (for example burning netherrack), lava, or Nitor (the magical flame). [S: F-Cru, B-Alc]
- Fill it with water (a bucket, or any pipe). Once the water visibly boils, it is ready. [S: F-Cru, B-Alc]
- Arcane Bellows attached to the crucible shorten the time the water takes to boil. [S: B-Bel]
- A heated crucible burns entities standing on top of it. [S: F-Cru]
- Mining it with a pickaxe dumps its contents, which behaves like an overflow (see below). [S: F-Cru]

### Melting
- Any item thrown in while the water is boiling dissolves into its component aspects. These are added to an internal pool of mixed essentia. [S: F-Cru, B-Cru]
- Players use filled phials (8 points of a single aspect each, and nothing else) or crystallised essence (1 point each) to dose exact amounts without leftovers. [S: B-Alc]
- Hoppers and droppers can feed ingredients in. [S: B-Alc]

### Recipes and the catalyst
- A recipe is an aspect bill plus a **catalyst** item. If the pool holds enough of each aspect, throwing in the catalyst consumes those aspects and ejects the result just above the crucible. The result does not fall back in. [S: F-Cru, B-Cru]
- **You must have researched the recipe.** Otherwise the catalyst just melts like any other item. [S: F-Cru]
- [1src] The catalyst must be thrown by a player. Ingredients can arrive automatically, but an automatically inserted catalyst will not trigger the recipe. [S: B-Alc]
- Several catalysts can be thrown at once. Each one converts while enough essentia remains, and the rest melt. Example: of 20 coal thrown into 3 phials of essentia, 12 melt and 8 transform. [S: F-Rec]
- Recipes can collide. A cheap catalyst can match its own recipe and "steal" essentia meant for another one. Example: iron nuggets duplicate themselves instead of melting. [S: F-Cru, F-Rec]
- Each craft uses up some water. With no water left the crucible stops absorbing items, but it keeps its essentia, and that essentia does not decay until the crucible is refilled. [S: F-Cru]

### Decay, overflow and flux
- Essentia left in the pool for more than a few seconds starts to break down one point at a time. A compound aspect decays into one of its components. A primal aspect simply disappears, and that can produce flux. [S: F-Cru, B-Cru]
- [C/1src] Overflow happens above **100** points of total essentia. [S: F-Cru] This is the only source for the threshold. For contrast, a TC6 wiki gives 500 for TC6's crucible. [S: B6-Cru] Verify the TC4 number in-game.
- Shift-right-clicking the crucible with a wand empties it, and all the essentia goes "into the atmosphere" as flux. [S: F-Cru, B-Cru]
- Overflow, decay and dumping all release liquid and/or gaseous flux. Each overflow also uses up water. [S: F-Cru]
- Some players built "flux farms" on purpose: they overload a crucible inside a pit to spawn Thaumic Slimes for experience and for tainted goo, the main source of Vitium. [S: F-Cru]

### Flux (as released by alchemy)
- **Flux Goo** (liquid) only flows 1–2 blocks sideways. Every goo block behaves like a source and creeps downhill, so a spill on a slope slides to the bottom instead of flooding it. Goo can spawn Thaumic Slimes, which merge into bigger slimes. [S: B-Flux, F-Cru]
- **Flux Gas** behaves like goo turned upside down. It floats up until it reaches a block, spreads along the underside of ceilings, and breaks torches on its way up. Outdoors it rises out of sight and dissipates. [S: B-Flux, F-Cru]
- Contact effects: goo gives Flux Flu for 30 s. Gas gives Flux Flu III for 60 s plus Nausea for 10 s. Flux Flu raises the player's vis costs. [S: B-Flux]
- Neither form can be picked up with a bucket. You remove flux by placing any block in its space; dirt or a water source both work. Flowing water only hides it. The Flux Scrubber clears nearby flux automatically and turns it into Praecantatio essentia. [S: F-Cru, B-Flux]
- Flux left lying around has a faint chance of tainting the area. [S: F-Cru]

### Recipe families (representative, not exhaustive)
- **Basic:** Thaumium (iron ingot + Praecantatio), Magic Tallow (rotten flesh + Praecantatio), Nitor, Alumentum, Ethereal Bloom. [S: F-Rec]
- **Transmutation:** a metal nugget plus a small essentia bill turns 1 nugget into 3. Ore purification turns 1 ore into a "native cluster" that smelts into 2 ingots. [S: F-Rec]
- **Duplication:** 1 becomes 2 for gunpowder, slime, clay, glowstone and ink. **Entropic processing:** cracked bricks, extra bone meal. **Manufacture:** cobweb, moss stone, ice. [S: F-Rec]
- **Golem bodies and cores:** a blank core plus 5 + 5 of two aspects gives a specific core, and some cores are the base for infusion upgrades. **Void seed and void metal** need the "Eldritch" epiphany before they appear. [S: F-Rec]
- Some recipes are "forbidden" (flesh golem, butcher core) and give warp when researched and when crafted. [S: F-Rec]

---

## 2. Essentia logistics

### The suction model (the core rule)
These rules come from the fandom Essentia page and agree with the in-game book text quoted on several pages. [S: F-Ess, B-Tube]
- Each tube block holds **1** point of essentia. Breaking a tube loses that point. [S: F-Ess]
- Every connected device exerts **suction**. Suction is either untyped or typed to one aspect. A plain tube carries the strongest suction it touches, minus 1 per block, so suction decays with distance. A weaker suction arriving on a side branch is effectively blocked. [S: F-Ess]
- A tube moves its point one block towards the strongest suction, but only if there is room and the suction is untyped or matches the point's aspect. A tube at 0 suction does not move anything. [S: F-Ess]
- **One aspect per line at a time.** The highest suction wins: with 20 Ordo against 19 Perditio, Ordo flows and Perditio does not. A stray point of the wrong aspect can block a line, for example a primal point sitting at a centrifuge input. [S: F-Ess, B-Tube]
- When suctions conflict, a tube visibly **vents coloured gas**. The colour hints at the competing aspects. [S: B-Tube]
- [1src] Throughput is at most 1 point per tick (20/s) and more typically about 8/s. A route has to stay stable for about 1 s before flow starts. [S: B-Tube]
- [C] B-Tube also describes a "route and destination" model in which a jammed tube eventually makes flux. The book text and the fandom article describe plain suction instead. I treat the suction model as authoritative and the flux-from-jams claim as unconfirmed.
- Right-clicking a tube with a wand toggles individual connections. Right-clicking the tube's centre rotates oriented tubes such as the valve. [S: B-Tube]

### Suction and capacity table [S: F-Ess, F-Res, B-Void]
"/x" is the value when the device is full.

| Component | Capacity | Suction |
|---|---|---|
| Tube | 1 | strongest neighbour − 1 |
| Warded jar, unlabelled | 64, one aspect | 32 / 0 when full |
| Warded jar, labelled | 64, label aspect only | 64 / 0 when full |
| Void jar, unlabelled | 64 | 32 (never drops) |
| Void jar, labelled | 64 | 48 / 32 when full |
| Essentia buffer | 8, mixed aspects | 1, +32 per active bellows |
| Essentia reservoir | 256, mixed aspects | 24 / 0 when full |
| Consuming device (lamps, centrifuge, crystalliser, infernal furnace, bore) | small | 128 while working / most drop to 0 when their buffer is full |

### Special tubes [S: F-Ess, B-Filt, B-Valve, F-AdvT]
- **Filtered tube:** you apply a jar label to it. It passes only its own aspect's suction, blocks other typed suction, and converts untyped suction on one side into typed suction on the other. Shift-clicking with an empty hand removes the label.
- **Restricted tube:** halves any suction passing through it, in both directions.
- **Directional tube:** accepts suction only from the end with the blue marking. If whatever is attached to the output cannot take the point, the tube blocks.
- **Valve:** a knob you toggle by right-clicking. A redstone rising edge closes it and a falling edge opens it. When closed it blocks suction both ways and holds nothing.
- **Essentia Resonator** (a tool): clicked on any essentia block, it reports the contents and the suction. On a buffer it reports the suction of the clicked side. [S: F-Rsn]

### Jars and labels [S: B-Jar, F-Jar, F-Ess, B-Void]
- A jar holds 64 points of a single aspect and connects to tubes **only on the top**. An empty unlabelled jar accepts its first point of any aspect and then only that aspect until it empties again.
- A broken jar **keeps its contents as an item**. No other component does this.
- Shift-right-clicking with an empty hand empties the jar. Shift-clicking the label face instead removes the label.
- A label can only go on a jar that holds something, and it locks the jar to that aspect. Crafting a label together with a phial pre-assigns the aspect; the phial's essentia is not used up. A pre-assigned label locks an empty jar. Putting a label on a filled jar re-types the label to match the contents. Crafting a label alone blanks it.
- Jars fill from an alembic when you click the alembic with the jar, or automatically when the jar sits under the alembic's spout. Phials fill from and empty into jars by hand.
- **Void jar:** when full it keeps accepting essentia and silently deletes the excess. A labelled void jar has less suction than a labelled warded jar, so it only gets essentia once the warded jars are full. That makes it a natural overflow sink.
- The book jokes that jars are great for jam. [S: B-Jar]

### Alchemical furnace and alembics [S: B-Furn, F-Dist, B-Alem]
- It works like a vanilla furnace with a fuel slot and an item slot. Instead of smelting, it breaks items down into a raw, mixed-aspect "slurry". The GUI shows the slurry as a purple bar. [1src] The bar holds 32. The furnace will not start an item that would overfill it.
- Arcane alembics stacked on top (up to 5) pull slurry upwards and separate it into pure aspects, each alembic holding 32 points of one aspect. The furnace fills the lowest alembic that already holds that aspect and still has room. Failing that, it fills the lowest empty alembic.
- Distillation is faster when several alembics have room for the aspect being drained. With all 5 alembics accepting, distillation outpaces breakdown.
- Bellows on the furnace's sides (1–4) speed up **breakdown** but not distillation. Alumentum as fuel speeds up **distillation**.
- Alembics feed tubes and phials. A golem with the right core can also empty them. Shift-clicking with an empty hand empties an alembic.

### Alchemical centrifuge and essentia buffer [S: B-Cent, F-Cent, B-Buf, F-Buf]
- **Centrifuge:** compound essentia goes in from below. Each point is slowly split into **one** of its two components, which comes out of the top. The centrifuge never drops its suction, even when it is backed up. Chaining two centrifuges ("double spinning") works only when the input is known to be a compound of compounds.
- **Buffer:** holds 8 points of any mix of aspects. It has 6 faces and accepts tubes or bellows on any of them. Its base suction is 1, and each attached Arcane Bellows adds 32. Bellows switch off under redstone. With bellows attached, shift-right-clicking a tube connection with a wand cycles that face through full suction, choked to 1 (blue band) and choked to 0 (red band). The buffer passes essentia on without passing its suction through. It is the standard way to collect a centrifuge's mixed output. Breaking a buffer loses its contents.

### Advanced storage and machines
- **Essentia Reservoir** [S: F-Res]: holds 256 points of mixed aspects. It has a single connection face, which a wand can rotate to any side. It cannot be filled or emptied by hand. Its low suction (24) means almost anything drains it. **Breaking a non-empty reservoir causes an explosion and releases flux, and both scale with the contents.** The infusion altar and essentia mirrors can draw from it. Golems treat it as a device, so it gets priority over jars.
- **Essentia Crystalliser** [S: F-Cry, F-Ess]: piped essentia comes out as crystals, 1 crystal per point. Crystals stack to 64 per aspect and turn back into essentia when smelted. It outputs into an adjacent inventory or drops crystals on the floor; it never backs up. It is slow unless fed Terra centivis.
- **Thaumatorium** (automated crucible) [S: F-Thm]: two alchemical constructs on top of a crucible, activated with a wand for 30 Ordo, 30 Aqua and 15 Ignis. It still needs heat underneath but no water. You put the catalyst in a GUI slot (a golem can supply it) and pick the recipe. It pulls the exact essentia it needs through tubes and ejects results out of the front. It produces **no flux, no decay and no water use**. Each Mnemonic Matrix attached to a side remembers 2 more recipes but takes up a pipe face. Gotcha: switching recipes in the middle of a run can lose the partial essentia already drawn.
- **Essentia Mirror** [S: F-Mir]: a pair of linked mirrors. Only the **infusion altar** can draw through them; earlier versions also fed lamps, the bore and the infernal furnace. The local mirror must be within 12 blocks of the matrix, and the remote mirror sees jars and reservoirs within 8 blocks of itself. It cannot connect to tubes. It occasionally eats an extra point of essentia as upkeep.
- **Advanced Alchemical Furnace** [S: B-AAF, F-AAF]: a 3×3×3 multiblock (alchemical furnace in the centre, 8 advanced constructs, 4 constructs, 4 alembics), activated with a wand for 50 Aqua, 50 Ignis and 50 Ordo. It burns no fuel. It needs a **continuous** supply of Ignis centivis to stay hot, even when idle, plus Aqua and Perditio centivis while it is smelting. Players throw items in from above. Its heat shows as fire filling three vents. All four bottom ports draw from one shared store and the furnace decides which aspect leaves which port, so filtered outputs can clog it. It produces essentia very fast, and players report storage being overwhelmed.

---

## 3. Infusion

### Building the altar [S: B-Alt, F-Alt]
- The structure is a central Arcane Pedestal with a Runic Matrix floating above it, plus 4 Arcane Stone Blocks and 4 Arcane Stone Bricks. [I] They are arranged as two-high pillars on the diagonals; the wiki layout grid could not be read as text. The central 3×3×3 must be empty; snow, carpet or water there blocks it.
- You activate the structure by right-clicking the matrix with a wand. **It costs 25 of each primal.** The starter wand can hold 25 but its cost penalty stops it from delivering 25. The Infusion research must be done, even in creative mode.
- The wiki pages do not say what the matrix looks like. That is covered by the core-loop catalogue (#2). [I]

### Running an infusion [S: F-InfM, B-Inf, F-Alt]
1. Put the **target** item on the central pedestal and the ingredients on the surrounding pedestals, in any order. Supply the essentia in jars, reservoirs, alembics or through mirrors within **12 blocks** of the matrix. Containers do not need to be placed symmetrically.
2. Right-click the matrix with any wand. This uses no vis, so an empty wand works. If the recipe is wrong (a missing item, an extra item, or an unresearched recipe) nothing happens.
3. If the recipe matches, the matrix "opens" and glows. With Goggles of Revealing you can see the outstanding essentia debt floating above it. Any **experience levels** the recipe costs are taken from a nearby player. If nobody nearby has enough, the altar stays lit and stalls.
4. **Essentia is drained first**, one aspect at a time and in a fixed order (the reverse of the order displayed). Floating essentia "blobs" are cosmetic only. If an aspect runs out, the altar **adds a few points to each remaining requirement every few seconds**, so stalls get more expensive the longer they last.
5. **Items are drained next**, one pedestal at a time, each shown as a particle beam. A missing ingredient also stalls the altar and grows the debt.
6. When everything is absorbed, the target turns into the result. A multi-item result shows as one item but drops its full count.
- **Abort:** take the target off the central pedestal. The infusion stops immediately and anything already consumed is lost. Accumulated instability may fire one last burst of events.
- **Item perfection:** ingredients other than the target must not be damaged, enchanted or renamed. An imperfect ingredient lets the infusion **start** but stalls it when that item's turn comes, and instability keeps building meanwhile.
- **Pedestals:** up to about 12 surrounding pedestals. They must be within 8 blocks horizontally, below the matrix (B-Alt: down to 10 below) and in line of sight. Any item on a pedestal within range is treated as an ingredient, so stray display pedestals spoil the recipe. Blocks and golems have to be placed *into* the pedestal (shift-right-click), not on top of it. Hoppers can feed pedestals.

### Symmetry, stabilisers and instability
- **Symmetry rule:** for every pedestal, ingredient and stabiliser there should be a partner at the same Y level, mirrored through the matrix in both X and Z (180° rotational symmetry). [S: F-Alt, F-InfM, B-Alt]
- **Recipe instability** is a hidden number shown as a category: Negligible 0–1, Minor 2–3, Moderate 4–5, High 6–7, Very High 8–9, Dangerous 10+. [S: F-InfM]
- **Penalties:** an unmatched *ingredient* adds +1, and an unmatched *pedestal* adds +2. The two stack. Odd-count recipes take the +1 unavoidably; leave the odd item facing an empty pedestal so the pedestals still pair. [S: F-InfM]
- **Stabilisers:** tallow candles (any colour), crystal clusters (any aspect, including mixed) and mob or player skulls. Any stabiliser pairs with any other. **10 matched stabilisers cancel 1 point**, which B-Alt states as 0.2 per pair; [1src] B-Alt adds that the first pair is not counted because of a rounding bug. An unmatched stabiliser *adds* instability. Stabilisers can push the start value below zero, which buys headroom. [S: F-InfM, B-Alt, F-Alt]
- **Stabiliser scan volume:** 12 blocks horizontally from the matrix (a 25×25 footprint). [C] Most sources give **5 above and 10 below**; F-InfM's "4.2" note says the reverse. Players build "candle rooms" under the altar, and late recipes use 100–150 or more candles. [S: F-InfM, F-Alt, F-Inf, B-Alt]
- 4.1 stopped counting nodes, Nitor and Arcane Lamp light motes as stabilisers. [S: F-InfM]
- More stabilisers make the matrix bob and spin more. This is purely cosmetic. [S: F-Inf]
- **Growth during the run:** each time a point of essentia, a level or an item is consumed, there is a 1 in (50 − 2 × recipeInstability) chance that live instability rises by 1, capped at **25**. The chance depends only on the recipe's base value, so stabilisers lower the level but not the rate. Instability never goes down during a run. Expensive recipes and long stalls make it worse. [S: F-InfM]
- **Event chance:** each tick, there is an instability/500 chance of an event, which is about 1 event per second at 25. [S: F-InfM, B-Inf]

### Instability events [S: B-Inf, F-InfM, F-Alt]
Weights below are from B-Inf.

| Weight | Event |
|---|---|
| 12/21 | An ingredient is knocked off its pedestal and drops nearby, where fire, lava or cactus may destroy it. The choice of pedestal is weighted towards the first ingredient slots. |
| 4/21 | Flux lightning strikes the player for 4–6 damage. |
| 3/21 | A random ingredient melts into 1 flux goo block plus 1 flux gas block on its pedestal. The ingredient is destroyed. |
| 1/21 | A small explosion that does not break blocks but can knock items off pedestals and hurt the player. |
| 1/21 | The player gains warp. F-InfM also lists "tainting the player" as a possible event. |

- The **target item is never affected** by events. [S: F-Inf]
- Lightning and the explosion only hurt players who are nearby. [S: B-Inf]

### Infusion enchanting [S: F-Ench, F-Enc2, F-Inf]
- Infusion enchanting is a later research. The altar applies vanilla and Thaumcraft enchantments, one level per infusion. Each recipe needs Salis Mundus, one or two themed items, essentia and a few XP levels. Other mods' enchantments only work if that mod adds a recipe.
- **Cost scaling:** the essentia and XP costs are multiplied by the *target* level (level II costs twice level I). Item ingredients do not scale. Each level of *other* enchantments already on the item adds about 10% to the cost.
- Example costs: Sharpness costs 1 level plus 4 Praecantatio and 8 Telum, with an iron sword as the item. Haste (up to III) costs 5 levels plus 8 Iter, 4 Praecantatio and 8 Volatus, with Nitor as the item.
- Base instability runs from Negligible to Moderate and rises with each level of enchantment the item already has. Vanilla's exclusivity rules still apply, so Fire Protection and Blast Protection cannot be combined.
- If no player nearby has enough levels, the altar lights up, stalls and keeps building instability.
- **Thaumcraft's own enchantments:** *Repair* (I–II) slowly fixes the item by drawing vis from wands in your inventory, priced by the item's value, and only works on some Thaumcraft items. *Haste* (I–III) goes on boots and gives land speed; it is also how players make the Thaumostatic Harness usable.
- **Runic augmentation** (shielding) adds +1 instability for each runic level the item already has, and needs Salis Mundus plus steeply rising essentia. [S: F-InfM, F-Ench]
- In 4.2.2.0, the focus enchantments Potency, Frugal and Treasure were **removed** from enchanting and replaced by the Focal Manipulator (section 4). [S: F-Ench, F-Enc2]

---

## 4. Wand foci

### Equipping [S: F-Foci, F-Pouch, B-Fire]
- Holding the focus key (default F) while holding a wand or staff opens a radial menu of every focus you carry, including those in a focus pouch. Shift+F unequips the current focus. **Sceptres cannot take foci.**
- **Focus pouch:** holds foci in one slot, can sit in the Baubles belt slot, is checked first when swapping, and receives the focus you unequip.
- Foci are made on the Arcane Worktable (shards, quartz and a themed core item) or, for stronger ones, by infusion. Each focus needs its own research, and some research only appears after a specific scan. [S: F-Foci]

### Focus catalogue
Base vis costs are in vis per use. [S: F-Foci table, per-focus pages]

| Focus | Behaviour | Base cost | Obtained by |
|---|---|---|---|
| **Fire** | Hold to spray a cone of flame that sets mobs alight. It does not ignite blocks unless upgraded. Low direct damage, but it hits everything in the cone. Fire-immune and Nether mobs take no damage. | Ignis **0.1/tick** [C: F-Fire says "1 per tick", B-Fire says "0.1 per second"] | Worktable; the first focus researched |
| **Shock** | A lightning bolt that seeks the nearest mob within about 10 blocks. It deals about 2.5 hearts and has a short cooldown (about 0.3 s). Since 4.2.3.5 it no longer transforms mobs (no charged creepers, no zombie pigmen). | Aer 0.25/cast | Worktable |
| **Frost** | Throws a bouncing ice ball that does good damage and applies Slowness. It can ricochet back into the caster. [C] Sources disagree on whether it freezes water and lava: F-Frost says it does, F-Foci says it doesn't. | Aqua 0.05, Ignis 0.02, Perditio 0.02 per cast | Worktable |
| **Excavation** | A green beam with about 10 blocks of reach that mines like a pickaxe and gives normal drops. Harder blocks take longer. It costs nothing when aimed at air. It is also the part that powers the Arcane Bore, and the bore inherits its upgrades. | Terra 0.15/tick [C: the fandom Excavation page says 0.1/tick] | Worktable |
| **Equal Trade** | Shift-right-click a block to choose the replacement. Left-click swaps one block from your inventory; right-click swaps a whole patch. Only blocks with an exposed face are affected. Blocks that don't drop themselves give their normal drops, and glass breaks. Slabs are placed as bottom slabs and logs upright. | Ordo, Perditio, Terra 0.05 each per block | Worktable |
| **Portable Hole** | Opens a temporary 3×3 tunnel into a surface, about 32 deep by default, lasting about 5 s. Anyone still inside when it closes is buried. A config blacklist decides which blocks it cannot pass through. | Aer 0.1, Perditio 0.1 per block of depth | Infusion |
| **Warding** | Makes a solid opaque block unbreakable and immune to pistons and redstone; warded falling blocks stay put. Only the player who warded it can undo it. Glass breaks instead of being warded. Warded blocks give off light. Crafting it needs a Nether Star. | Aqua 0.1, Ordo 0.25, Terra 0.25 per block | Infusion |
| **Nine Hells** | Summons a hellbat that chases the mob under your crosshair, attacks several times, then dies or explodes. It needs a target, has a 1 s cooldown and costs nothing if it fails. Hitting the bat makes it target you. | Aer 1, Ignis 2, Perditio 1 per cast | Research or knowledge fragments; needs a firebat scan |
| **Pech's Curse** | An arcing projectile affected by gravity. It deals 1 heart and applies a random choice of Poison, Weakness or Slowness. It **cannot be crafted**: it is traded from or dropped by Pech thaumaturges, who also use it against you. | Aqua, Perditio, Terra 0.1 each per cast | Pech only |
| **Primal** | An erratic orb that explodes on impact, breaking blocks and doing random damage. It can rarely create fibrous taint or a small aura node. | Every primal, random 0.5–2.0 each per cast [C: F-Primal says 0.5–2.5] | Eldritch tab (warp-gated) plus a primal charm scan |

Blink is **not** a TC4 focus; it comes from the Forbidden Magic addon. [S: F-Blink]

### Focus upgrades: Focal Manipulator (4.2.2.0+) [S: F-FM, F-Foci]
- A block GUI with the focus in the centre and the upgrades currently available shown below it. Each focus gets **5 upgrade tiers**. **Tier N costs 8×N XP levels** (8, 16, 24, 32, 40), paid at the start, plus a set of primal **centivis** costs that grow with each tier, drawn from nearby energised nodes or relays.
- Upgrades are **permanent** and cannot be removed.
- Gotchas: the manipulator does not check that the centivis is available before starting; it just pauses until it is. Taking the focus out mid-upgrade **loses all progress, XP and centivis**.
- **Generic upgrades,** repeatable per tier where the focus allows them:
  - *Potency:* +20% effectiveness (damage or mining speed); on Pech's Curse it also raises the debuff level.
  - *Frugal:* −10% vis cost.
  - *Enlarge:* more area or more targets.
  - *Extend:* longer duration.
  - *Treasure:* Fortune-like.
- **Unique branch upgrades** sit at tier 3 or 5, and some are mutually exclusive:
  - Fire: *Alchemist's Fire* (longer burning); at tier 3, a choice between *Fireball* (explosive) and *Fire Beam* (long, narrow, high damage). Alchemist's Fire also makes those two set blocks alight.
  - Shock: tier 3 *Chain Lightning* (each bolt hits up to 2 more targets; each extra hit does slightly less damage, roughly −1) or *Earth Shock* (an electrified ground patch that lasts a long time, gives light and destroys item drops). Tier 4 *Enlarge* boosts whichever one you took.
  - Frost: *Alchemist's Frost* (longer Slowness); tier 3 *Scattershot* or *Ice Boulder*.
  - Excavation: *Treasure*; tier 3 *Dowsing* (chance to mine native clusters, which smelt to 2× metal); tier 5 *Silk Touch*.
  - Equal Trade: tier 3 *Treasure* or *Architect* (a preview of affected blocks, resized with the G key); tier 5 *Silk Touch*. Warding: tier 2 *Architect*.
  - Nine Hells: tier 3 *Bat Bombs* (instant, bigger explosions) or *Devil Bats* (tougher melee bats that set targets on fire); tier 5 *Vampire Bats* (separate research; drains health to the caster).
  - Pech's Curse: tier 5 *Nightshade* (applies all debuffs at once).
  - Primal: tier 3 *Seeker* (homes in on living targets and never makes nodes or taint).
  - Portable Hole: *Enlarge* (+8 depth per level) and *Extend* (+3 s per level).

---

## Observed friction (feeds #5, community pain points)
These are inferences from how the sources describe workarounds. [I]
- **Tube jamming** is the defining frustration. Single-aspect lines, invisible suction maths and a dedicated diagnostic tool (the Resonator) suggest the model is hard to read. The wiki's own setup diagrams say the basic layout "suffers from jamming".
- **The crucible punishes slowness.** Decay, accidental recipe collisions and the manual catalyst all push players to phials and, eventually, the Thaumatorium.
- **Candle rooms** of hundreds of stabilisers are the expected endgame for infusion: a chore built on symmetry.
- **Stalls snowball.** Debt grows, instability grows, and an imperfect ingredient is only discovered halfway through.
- **The AAF over-produces** and pushes players into storage sprawl or Applied Energistics add-ons.
- **Flux cleanup** means placing blocks by hand.
- **The Focal Manipulator** gives no upfront check and wipes progress if the focus is removed.

## Open questions
Verify these in the reference instance if the spec depends on them:
1. Crucible overflow threshold: is it 100 points? This rests on a single source.
2. Fire focus cost: is it 0.1 vis per tick, or something else?
3. Stabiliser vertical range: is it 5 above and 10 below, or the reverse?
4. Alchemical furnace slurry buffer: is it 32?
5. Tube throughput: about 8 points per second?
6. Do jammed tubes ever make flux?
7. Can the crucible's catalyst really not be automated?

For the map, as design decisions rather than research:
- Keep 180° symmetry plus stabilisers, or replace them?
- Keep the "one aspect per tube" suction model, or move to a network model?
- Do foci keep XP-plus-centivis tiered upgrades?

---

## Sources
All pages were accessed 2026-10-14 through the public MediaWiki API (`action=parse&prop=wikitext`) for exact numbers. They are paraphrased here, not copied.

| id | URL |
|---|---|
| F-Cru | https://thaumcraft-4.fandom.com/wiki/Crucible |
| F-Rec | https://thaumcraft-4.fandom.com/wiki/Crucible_recipes |
| B-Cru | https://ftbwiki.org/Crucible_(Thaumcraft_4) |
| B-Alc | https://ftbwiki.org/Alchemy_(Thaumcraft_4) |
| B-Flux | https://ftbwiki.org/Flux_(Thaumcraft_4) |
| B6-Cru | https://ftb.fandom.com/wiki/Crucible_(Thaumcraft_6) (TC6, contrast only) |
| F-Ess | https://thaumcraft-4.fandom.com/wiki/Essentia |
| B-Tube | https://ftbwiki.org/Essentia_Tube |
| B-Valve | https://ftbwiki.org/Essentia_Valve |
| B-Filt | https://ftbwiki.org/Filtered_Essentia_Tube |
| F-AdvT | https://thaumcraft-4.fandom.com/wiki/Advanced_Essentia_Tube |
| B-Buf | https://ftbwiki.org/Essentia_Buffer |
| F-Buf | https://thaumcraft-4.fandom.com/wiki/Essentia_buffer |
| B-Jar | https://ftbwiki.org/Warded_Jar_(Thaumcraft_4) |
| F-Jar | https://thaumcraft-4.fandom.com/wiki/Warded_Jar |
| B-Void | https://ftbwiki.org/Void_Jar |
| B-Alem | https://ftbwiki.org/Arcane_Alembic_(Thaumcraft_4) |
| B-Furn | https://ftbwiki.org/Alchemical_Furnace |
| F-Dist | https://thaumcraft-4.fandom.com/wiki/Essentia_Distillation |
| B-Cent | https://ftbwiki.org/Alchemical_Centrifuge |
| F-Cent | https://thaumcraft-4.fandom.com/wiki/Alchemical_Centrifuge |
| B-Bel | https://ftbwiki.org/Arcane_Bellows_(Thaumcraft_4) |
| B-AAF | https://ftbwiki.org/Advanced_Alchemical_Furnace_(Thaumcraft_4) |
| F-AAF | https://thaumcraft-4.fandom.com/wiki/Advanced_Alchemical_Furnace |
| F-Res | https://thaumcraft-4.fandom.com/wiki/Advanced_Essentia_Storage |
| F-Thm | https://thaumcraft-4.fandom.com/wiki/Thaumatorium |
| F-Cry | https://thaumcraft-4.fandom.com/wiki/Essentia_Crystallizer |
| F-Mir | https://thaumcraft-4.fandom.com/wiki/Essentia_Mirror |
| F-Rsn | https://thaumcraft-4.fandom.com/wiki/Essentia_Resonator |
| F-Inf | https://thaumcraft-4.fandom.com/wiki/Infusion |
| F-InfM | https://thaumcraft-4.fandom.com/wiki/Infusion_mechanics (cites a r/feedthebeast analysis thread, which was not reachable to verify) |
| F-Alt | https://thaumcraft-4.fandom.com/wiki/Infusion_Altar |
| B-Inf | https://ftbwiki.org/Infusion_(Thaumcraft_4) |
| B-Alt | https://ftbwiki.org/Infusion_Altar_(Thaumcraft_4) |
| B-RM | https://ftbwiki.org/Runic_Matrix |
| F-Ench | https://thaumcraft-4.fandom.com/wiki/Infusion_Enchanting |
| F-Enc2 | https://thaumcraft-4.fandom.com/wiki/Enchantments |
| F-Foci | https://thaumcraft-4.fandom.com/wiki/Wand_Foci |
| F-FM | https://thaumcraft-4.fandom.com/wiki/Focal_Manipulator |
| F-Pouch | https://thaumcraft-4.fandom.com/wiki/Focus_Pouch |
| F-Fire / F-Frost / F-Primal / F-Blink etc. | https://thaumcraft-4.fandom.com/wiki/Wand_Focus:_Fire (and the sibling `Wand_Focus:_*` pages) |
| B-Fire / B-Shock | https://ftbwiki.org/Wand_Focus:_Fire, https://ftbwiki.org/Wand_Focus:_Shock |
