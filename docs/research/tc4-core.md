# TC4 behaviour catalogue: core loop

Research ticket: [#2](https://github.com/andrewbrogoff/voices-from-the-void/issues/2) (map [#1](https://github.com/andrewbrogoff/voices-from-the-void/issues/1)).
Scope: Thaumcraft 4.2.x on Minecraft 1.7.10. Covers aspects and scanning, research, wands, aura nodes and arcane crafting.

**This is clean-room material.** It describes behaviour that players can observe, taken only from public wikis, forums and modder documentation. Nobody decompiled or unpacked any jar or asset. All text is our own summary.

**Treat the numbers as a sense of scale only.** Each system spec will set its own values. TC4's figures are listed so we know roughly what "cheap", "expensive", "slow" and "fast" meant in the original.

## Evidence tags

Every claim carries one of these tags. The bracketed key after a tag points to the source list at the end.

| Tag | Meaning |
|---|---|
| **F** | Sourced fact. We read the page directly (ftbwiki.org, FTB forums, ModTweaker docs, GitHub). |
| **Fs** | Sourced fact, but we only saw a search-engine snippet. The fandom wikis (thaumcraft-4.fandom.com, ftb.fandom.com) return HTTP 402, and web.archive.org was blocked. These are slightly less trustworthy. |
| **Fg** | From the GT New Horizons wiki or notes. GTNH is a heavily modified modpack, so the value may not be stock TC4. |
| **I** | Our inference. |
| **?** | The sources conflict. Both values are given. |

Reddit could not be fetched. YouTube was not used. We did not observe the in-game reference instance.

---

## 1. Aspects

### 1.1 The aspect tree (4.2.x, 48 aspects)

There are 6 primals and 42 compounds. Every compound is made of exactly two lower aspects. F[aspects]
A second, independent source gives the same 42 pairs. F[solver-aspects]

| Tier | Aspect | Meaning | = A + B |
|---|---|---|---|
| P | Aer | air | — |
| P | Terra | earth | — |
| P | Ignis | fire | — |
| P | Aqua | water | — |
| P | Ordo | order | — |
| P | Perditio | entropy | — |
| 1 | Gelum | cold | Ignis + Perditio |
| 1 | Lux | light | Aer + Ignis |
| 1 | Motus | motion | Aer + Ordo |
| 1 | Permutatio | exchange | Ordo + Perditio |
| 1 | Potentia | energy | Ignis + Ordo |
| 1 | Tempestas | weather | Aer + Aqua |
| 1 | **Vacuos** | **void** | Aer + Perditio |
| 1 | Venenum | poison | Aqua + Perditio |
| 1 | Victus | life | Aqua + Terra |
| 1 | Vitreus | crystal | Ordo + Terra |
| 2 | Bestia | beast | Motus + Victus |
| 2 | Fames | hunger | Vacuos + Victus |
| 2 | Herba | plant | Terra + Victus |
| 2 | Iter | travel | Motus + Terra |
| 2 | Limus | slime | Aqua + Victus |
| 2 | Metallum | metal | Terra + Vitreus |
| 2 | Mortuus | death | Perditio + Victus |
| 2 | Praecantatio | magic | Potentia + Vacuos |
| 2 | Sano | healing | Ordo + Victus |
| 2 | Tenebrae | darkness | Lux + Vacuos |
| 2 | Vinculum | binding | Motus + Perditio |
| 2 | Volatus | flight | Aer + Motus |
| 3 | Alienis | eldritch | Tenebrae + Vacuos |
| 3 | Arbor | wood | Aer + Herba |
| 3 | Auram | aura | Aer + Praecantatio |
| 3 | Corpus | flesh | Bestia + Mortuus |
| 3 | Exanimis | undead | Mortuus + Motus |
| 3 | Spiritus | soul | Mortuus + Victus |
| 3 | Vitium | taint | Perditio + Praecantatio |
| 4 | Cognitio | mind | Ignis + Spiritus |
| 4 | Sensus | senses | Aer + Spiritus |
| 5 | Humanus | humankind | Bestia + Cognitio |
| 6 | Instrumentum | tool | Humanus + Ordo |
| 6 | Lucrum | greed | Fames + Humanus |
| 6 | Messis | crop | Herba + Humanus |
| 6 | Perfodio | mining | Humanus + Terra |
| 7 | Fabrico | craft | Humanus + Instrumentum |
| 7 | Machina | machine | Instrumentum + Motus |
| 7 | Meto | harvest | Instrumentum + Messis |
| 7 | Pannus | cloth | Bestia + Instrumentum |
| 7 | Telum | weapon | Instrumentum + Ignis |
| 7 | Tutamen | protection | Instrumentum + Terra |

**Observations**

- The tree is deep and narrow. Everything from tier 5 to tier 7 runs through Humanus, which itself depends on Cognitio and then Spiritus. One player worked out that a single point of Meto costs about 24 primal points to build at the table. F[ftbforum-894289] **I:** the top tiers are very expensive to synthesise, and this is part of the well-known "ran out of aspects" grind.
- **Void is already in the tree.** Vacuos is a tier-1 aspect, and it feeds Fames, Praecantatio, Tenebrae and Alienis. **I:** that makes it a natural spine for our Void theme. Magic itself is built partly from void (Praecantatio = Potentia + Vacuos).
- **What changed between versions:**
  - 4.2 removed Saxum (stone) and Granum (seed), which existed in 4.1. F[aspects] Fs[fandom-aspects]
  - 4.2.0 changed the recipes for Cognitio and Telum. Fs[changelog]
  - Tempestas changed its recipe several times between 4.0 and 4.2.2. Fs[fandom-tempestas]
  - **I:** the recipe pairs were tuned over time. Expect to tune our own.
- No item may have more than 6 aspects (from 4.2.0). Fs[changelog]
- Addons such as Forbidden Magic added extra aspects (Ira, Gula, and others). F[solver-aspects] **I:** aspect registration should be an open API.

### 1.2 Seeing aspects

- Holding Shift while hovering over an item shows its aspects and amounts, but only after the player has scanned that item. Before that they show as unknown. F[aspects] F[thaumometer]
- **I:** what reveals tooltips is scan knowledge, not equipment. The goggles do not unlock tooltips.
- Undiscovered aspects show as "?" at the research table. F[research]
- **Goggles of Revealing:**
  - In the world, they show essentia in crucibles and alembics, vis in nodes and in wands on the recharge pedestal, and what an infusion needs.
  - They make nodes visible and give a 5% vis discount.
  - They cannot scan.

  F[goggles]

## 2. Scanning (Thaumometer)

- **How it works:** hold right-click on a block, a dropped item, a mob or a node for a few seconds. A shutter click plays when the scan completes. F[thaumometer] F[ftbforum-483234] No source gives the exact duration.
- **Reward:** points in the player's aspect pool equal to the target's aspect amounts. Fs[fandom-research] Fg[gtnh]
  - A node gives about 1/10 of its strength in each of its aspects. Fs[fandom-research]
  - Each node can be analysed once. F[ftbforum-453511]
- **Each thing is scanned once.** After that, looking at it shows its aspects but gives no new points. Fs[fandom-research]
  - **I:** scan memory is per player. No source states this directly.
- **Discovery gate:** you cannot scan something with an unknown aspect unless you already know both of that aspect's components. Otherwise you get the "You don't have the knowledge required" message. Fs[fandom-iterm] F[ftbforum-483234]
  - Some compounds have to be discovered by combining them at the research table instead, for example Victus. Fs[fandom-table] F[ftbforum-461337]
- **? Bonus for discovering a new aspect:** sources give triple points Fs[fandom-research], +2 points Fs[ftbfandom-research] F[ftbforum-643799], or double the amount F[ftbforum-35955]. **I:** a flat small bonus is the most likely for 4.2.
- **Scan results:** a message scrolls in the bottom-right corner. There are five outcomes. F[thaumometer]
  1. Nothing can be learned.
  2. Research needed first.
  3. Aspects revealed.
  4. Aspect discovered.
  5. Hidden research unlocked.
- **Caps on gains:**
  - In 4.2.0 the most one scan can add to a single aspect is 64, and this cannot be changed in the config. Fs[changelog]
  - Once a pool for an aspect passes roughly 100, scans usually add only 1 point. Fs[ftbfandom-research]
  - Earlier 4.1 builds used caps of 8 or 10 and could be configured. Fs[changelog]
  - **I:** the aim is to stop players farming one aspect.

### Other ways to get aspect points

| Source | Behaviour | Tag |
|---|---|---|
| Research-table combining | 1 of each component makes 1 compound. Lossy. It is also how compounds get discovered. | Fs[fandom-table] |
| Knowledge Fragment | Right-click it for 1–10 of each primal (GTNH says 1–2). Nine fragments craft a Research Note for lost knowledge (see §3.3). | F[fragment] Fg[gtnh] |
| Deconstruction Table | Breaks items down for a chance at 1 primal point. | F[decon] |
| Table "ghost points" | Nearby blocks top up a depleted aspect to 1 free point (see §3.5). | F[ftbforum-32725] |
| Warp "whispers" event | Sometimes gives 1 primal point. | F[warp] |

## 3. Research

### 3.1 The book (Thaumonomicon)

- **Getting it:** right-click a bookshelf placed in the world with any wand. F[tome]
- **Tabs:** there are 5 visible tabs: Basic Information, Thaumaturgy, Alchemy, Artifice and Golemancy. A hidden 6th tab, Eldritch, opens through Warp. Addons usually add a tab of their own. F[tome]
- **Layout:** each tab is a pannable canvas, like the vanilla achievements screen. Entries are placed by hand, and lines connect them to their prerequisites. F[tome]
  - Some required parents are deliberately not drawn ("hidden parents"). F[mt-research]
  - Links between tabs are not drawn either. F[gotnukes]
- **Icon shape says what an entry is:** F[tome] F[mt-research]
  - Circle: a concept or auto-unlocked entry.
  - Square: unlocks recipes.
  - Hexagon: "secondary" research, bought directly with points.
  - Spiked border: a key entry.
- **Icon state:** F[tome] F[warp]
  - Bright: done.
  - Blinking: can be researched now.
  - Dark: locked.
  - Yellowish: hidden, needs several conditions.
  - Purple swirl: gives Warp. A severity word goes with it: minor, moderate, dangerous or taboo.
- **Secret entries** show as "???". Everything downstream of a secret entry is also secret, which is why the whole Eldritch tab reads ??? at first. F[gotnukes]
- **Auto-unlocked at the start:** at least the "Research" and "Thaumonomicon" entries. Fs[fandom-research-entry] We did not find a full list. **I:** the concept pages on Basic Information are probably also auto-unlocked.
- **Modding flags** (useful vocabulary for our API): round, spiky, stub (only unlocked indirectly), secondary, virtual (parent only, never researchable), autoUnlock, concealed. Concealed means hidden until its parents are done, and it is the default. F[mt-research]

### 3.2 Points and difficulty

- **The pool:** aspect points are kept per player and per aspect, and they are shared across every research table. F[ftbforum-32725]
- **Spending:**
  - 1 point for each aspect placed in the minigame.
  - The listed price of a secondary research.
  - Duplicating notes.

  F[notes]
- **Each research has an aspect list.** That list does two jobs: it sets the price for buying the research outright, and it sets which aspects are pre-placed on the minigame grid. Each research also has a *complexity* value that makes the minigame harder. F[mt-research]
- **Difficulty config:** Fs[fandom-config]
  - Easy: no minigame. Everything is bought with points, at a much higher price.
  - Normal: primary research uses the minigame, secondary research is bought.
  - Hard: even secondary research needs the minigame.

### 3.3 Prerequisites and hidden research

- **Normal parents** are drawn as lines. **Hidden parents** are required but not drawn. F[mt-research]
- **Scan triggers:** scanning anything that contains a particular aspect, item or entity can reveal hidden research. Fs[gtnh-cheatsheet] F[gotnukes] F[thaumometer]
  - Example: scanning something with Lux reveals Nitor or Alumentum.
  - One trigger reveals only one research at a time.
  - Killing things is also named as a trigger type.
- **Warp-granted research:** six entries have no parents at all and are handed out by Warp events. These are Eldritch Epiphany, Eldritch Revelation, Crimson Cult, Purifying Bath Salts, Creation and Crimson Philosophy. F[gotnukes] F[warp]
  - Eldritch Epiphany needs about 26 Warp, gives 10 random points and opens the Eldritch tab.
  - Eldritch Revelation needs about 51 Warp and gives 20 points.
  - Only sticky and permanent Warp count toward these thresholds. Fs[fandom-warp]
  - ? Other sources give 30 and 50 Fg[gtnh], or "half the warping research done" F[tome].
- **Lost knowledge:** nine Knowledge Fragments craft a Research Note. Right-clicking it binds it to a random hidden research you could not get any other way; the golem upgrades are the usual example. If nothing is left, it breaks back into fewer fragments. F[fragment] Players complain that it is unreliable. F[ftbforum-33246]
- **Wuss mode** in the config turns off Warp, and with it any research that is gated by Warp. Fs[fandom-warp]

### 3.4 The research-table minigame

1. **Build the table:** place two plain tables side by side and right-click one with Scribing Tools. F[table]
   - Scribing Tools are a glass bottle, a feather and ink. They hold 101 uses and are refilled with an ink sac. F[scribing]
2. **Make a note:** with Scribing Tools and Paper in your inventory, click a blinking entry in the book. This uses 1 paper and 1 ink and gives you Research Notes. F[notes]
   - Until you have learned the research, you can make notes for it again and again. F[ftbforum-77872]
3. **Solve the note:** put the Scribing Tools in the top-left slot and the note in the top-right. F[research]
   - The note shows a hex grid with the research's aspects already placed around the edge. Fs[fandom-research]
   - You place aspects from your pool on empty hexes, and right-click to remove one. F[research]
   - Two neighbouring hexes **link only if one aspect is a direct component of the other**. Linked hexes get a white line; an aspect that links to nothing goes black. F[research]
   - The goal is to join all the pre-placed aspects into one connected network. Branching is allowed. F[notes]
   - Every placement costs 1 point, and removing an aspect does not refund it. Placing or removing also uses ink. F[notes]
   - **I:** 1 ink per action.
4. **Finish:** once everything is joined, the grid clears and the note becomes a "Discovery", and its icon changes to a tied scroll. Right-click it to learn the research permanently. Each research can only be learned once. F[research]

- **Grid size:** there is no public figure for how complexity maps to grid radius or to the number of starting aspects. A GTNH solver tool sees anywhere from 2–4 up to 9 or more starting aspects. Fg[solver-usage] **I:** higher complexity means a bigger grid and more anchors.
- **Research Duplication** lets you copy a completed note before you learn it. A copy costs paper, ink and research points, and each further copy costs more. We did not find the formula. Fs[fandom-research] Fg[gtnh] Fs[namu]

### 3.5 Table bonuses and research upgrades

- **"Ghost points" from nearby blocks:** F[ftbforum-32725] Fs[fandom-research]
  - The table scans about 8 blocks around itself every 30 seconds.
  - Most blocks feed one particular primal: crystal clusters, infused stone, grass, fire, water, lava and redstone. Bookshelves and brains in jars feed random aspects.
  - A table holds at most 1 bonus point per aspect. Bonus points belong to the table, not the player.
  - A bonus point is spent only after the real points run out, and it shows as a sparkle with no number.
  - **I:** the purpose is to make the table's surroundings matter and to keep a player who has run dry from being stuck completely.
- **Research Expertise:** removing an aspect has a 25% chance to refund it, and hovering over a compound shows what it is made of. Fs[fandom-expertise]
- **Research Mastery:** Fs[fandom-mastery]
  - The refund chance rises to 50%.
  - Each placement has a 10% chance to cost nothing.
  - Shift-clicking a compound crafts it automatically from its components.
  - It leads on to Research Duplication.

## 4. Wands

### 4.1 Construction and vis storage

- **Vis is stored per primal.** A wand holds six separate pools, one for each primal, and every pool has the same capacity. F[wandcore]
- **The three forms:** F[wandcore] Fs[fandom-wands] F[tc4220]

  | Form | Parts | Capacity | Notes |
  |---|---|---|---|
  | Wand | 2 caps + rod | ×1 | Takes a focus and can craft. |
  | Sceptre | 3 caps + Primal Charm + rod | ×1.5 | No focus. Built-in discount of 10% (5% before 4.2.2.0). |
  | Staff | 2 caps + staff core | about ×2.5 | Melee weapon. Cannot craft. |

- **Assembly:** the finished wand is put together at the Arcane Worktable and costs vis that scales with its capacity. F[wandcraft] Example totals are 10 for a gold-capped wood wand and 60 for a gold-capped greatwood sceptre. Fg[gtnh-notes]
- **Primal Charm:** a crafting ingredient made from 6 elemental shards, a balanced shard and gold. It costs 25 of every primal. F[charm]
- **Reading a wand:** holding Shift shows exact vis amounts and the discount. Fs[fandom-basicwand]

### 4.2 Rods (capacity per primal, as a wand)

| Rod | Cap | Special |
|---|---|---|
| Wood | 25 | — (Fg[gtnh-wands]; I from "greatwood = double wood") |
| Greatwood | 50 | — F[greatwood] |
| Reed / Blaze / Obsidian / Ice / Quartz / Bone | 75 | Each slowly regenerates one primal (Aer / Ignis / Terra / Aqua / Ordo / Perditio) while it is below about 10% of capacity. F[rods] |
| Silverwood | 100 | No regeneration. F[silverwood] |
| Primal staff core | 250 | Staff only. Regenerates all six primals, and its focus gets +1 potency. F[primalcore] |

- **I:** none of the rods' regeneration depends on location. No source mentions regeneration tied to the Nether or to altitude. It is a trickle that only stops a wand going completely dry.
- Elemental rods and silverwood rods are made by infusion. F[rods]

### 4.3 Caps (multiplier on vis cost)

| Cap | Cost multiplier |
|---|---|
| Iron | 110% (the starter cap) |
| Copper | ? 110% on the four elements; Ordo and Perditio given as 100% |
| Gold | 100% |
| Silver | 95% on the four elements, 100% on Ordo and Perditio |
| Thaumium | 90% |
| Void metal | 80% |

Sources: F[gtnh-wands] F[copper] F[silvercap] F[thaumiumcap] F[voidcap]

- Thaumium, silver and void caps are "charged" by infusion from an inert cap. F[thaumiumcap]
- **I:** caps work as a per-primal multiplier, so some caps can favour certain elements.
- Whether copper and silver caps are part of vanilla 4.2 is unresolved.

### 4.4 Recharging

- **From a node by hand:** see §5.3.
- **Wand Recharge Pedestal:** F[pedestal] Fs[fandom-pedestal]
  - The wand sits on it and slowly pulls vis from nodes within roughly 8 blocks.
  - It won't fully drain a node unless the wand has a wood rod or iron caps.
  - A Compound Recharge Focus placed on top converts compound aspects into primal vis 1:1.
- **Amulet of Vis Storage:** a bauble holding 250 of each primal. It slowly tops up the wand in your hand, and it recharges from the pedestal or the relay network. F[amulet]
- **Vis relays (4.2):** F[transducer] F[tc4220]
  - Each relay has an 8-block range and they chain into a network.
  - A relay can be tuned to a single primal.
  - Since 4.2.2.0 relays need line of sight.
  - A relay mounted on the worktable charges the wand sitting in it. F[chargerelay]
- **Foci:** Fire, Frost, Shock, Excavation, Equal Trade, Portable Hole, Warding, Pech's Curse, Nine Hells, Primal, plus focus upgrades (reworked in 4.2.2.0). F[wandcraft] F[tc4220] Foci are out of scope here; a separate ticket covers them.

## 5. Aura nodes

### 5.1 What a node is

- **Basics:** a node is a floating object in the world that holds one or more aspects. These are mostly primals, and compounds are rare.
- **Two values per aspect:** each aspect has a *maximum* and a *current* value, and the current value slowly regenerates toward the maximum.
- **Looks:** a node's size and colour follow its current contents. F[node]
- **What decides the contents:** biome, height and nearby blocks. For example, high or plains locations give Aer, deserts, lava or the Nether give Ignis, and swamps give Perditio. F[node]
- **Natural amounts:** we found no public spawn ranges. **I:** the low hundreds at most, judging by the thresholds players talk about (feeding past 500, 70 vis to jar a node).

### 5.2 Types and modifiers

| Type | Natural chance | Behaviour |
|---|---|---|
| Normal | most | — |
| Pure | ~1.7% (always in silverwood trees and Magical Forest) | Turns the biome into Magical Forest and resists or cures taint. Contains Victus. |
| Dark | ~1.7% | Turns a ~23×23 area into an eerie biome and spawns angry zombies in the dark. Contains death and darkness aspects. Since 4.2.2.0 it no longer affects the Nether or End. |
| Unstable | ~1.7% | Every ~5 s it has a 50% chance to throw off an aspect orb, which permanently lowers that aspect's maximum. Eventually it vanishes. |
| Hungry | ~0.6% | Breaks blocks (not obsidian) and pulls in items, mobs and players, then adds whatever it eats to its own aspects. Can grow past 500. |
| Taint | only in tainted land | Spreads taint. |

Sources: F[node] Fs[fandom-node] F[tc4220] F[ftbforum-hungry]

| Brightness | Regeneration |
|---|---|
| Bright | faster: ? 1 per 400 ticks Fs[fandom-node] vs "double" F[node]. Wider effect range. |
| Normal | 1 point per 600 ticks (30 s) Fs[fandom-node] |
| Pale | half the normal rate, and a smaller effect range F[node] |
| Fading | none. It disappears when drained. F[node] |

### 5.3 Tapping (wand drain)

- **How:** hold right-click on a node with a wand. **Only primal aspects can be drawn** by hand. F[node]
- **Rate:** 1 vis every 5 ticks to start. Advanced Node Tapping raises it to 2 per 5 ticks, and Master Node Tapping to 3 per 5 ticks; each replaces the rate before it. Fs[fandom-tapping]
  - **I:** there is no research that lets a hand-held wand convert compounds into primals. Only the pedestal's Compound Recharge Focus does that.
- **Node Preserver research:** draining stops at 1 of each aspect unless you're sneaking. It needs a better wand (greatwood rod or better, gold caps or better); a basic wand still drains completely. Fs[fandom-preserver]
- **Draining an aspect to 0** can delete that aspect, damage the node, or make the node disappear. F[node] **I:** this, plus regeneration of only about 2 points per minute, is TC4's big "careless players lose their node" risk.

### 5.4 Manipulation and destruction

- **Node in a Jar:** F[node] Fs[fandom-jar]
  - Surround the node with a 3×3×3 shell of glass plus a wooden-slab lid, then use a wand that holds 70 of every primal. That vis is used up.
  - The jar can be picked up and placed elsewhere.
  - Jarring tends to drop the node one brightness level and fixes the aspects at whatever they were when jarred.
  - Moving a node that isn't full lowers its maximums.
- **Node Stabilizer** (a block placed under the node): F[stabilizer]
  - It stops biome conversion, zombie spawning, draining between nodes, and the losses of unstable nodes.
  - It has tiny chances to cure Unstable and to upgrade Fading to Pale.
  - It halves regeneration, and redstone switches it off.
  - An Advanced Stabilizer exists, but the wiki doesn't say how it differs.
- **Energising:** F[transducer] F[ftbforum-1099294]
  - Put a Stabilizer below the node and a powered Node Transducer above it.
  - The node stops storing vis and can't be tapped any more. Instead it streams centivis every tick into relays within 8 blocks.
  - Output per primal is reportedly floor(√amount) cV per tick, with compounds counting through their primal parts.
  - Turning it off leaves the node drained and possibly damaged.
  - Breaking the transducer while the node is charged makes the node **explode**.
  - **I:** this splits nodes into two roles. A node is either a personal battery that refills slowly, or base infrastructure feeding the relay network.
- **Breaking a node** destroys it permanently. It drops Ethereal Essence equal to ceil(power/10) for each aspect, and aspects at 5 or below give nothing. F[node] Fs[fandom-node]
- **Nodes draining each other:** nodes within 5 blocks of each other whose sizes differ by 4 or more drain each other. The bigger node wins, but it gains only the square root of what it takes. Hungry nodes drain much faster. Players build "bully" farms around this. F[node] F[ftbforum-1099294]

## 6. Arcane crafting

- **Making the worktable:** wand a placed plain table. F[worktable]
- **Layout:** a 3×3 grid plus a wand slot, with six primal cost symbols arranged around the grid. F[worktable]
- **Recipes** need the research to be known and also cost specific primal vis. The vis is taken from the wand in the slot at craft time. F[worktable] F[vis]
  - Typical costs run from small amounts up to 25 of each primal, as for the Primal Charm. F[charm]
- **Discounts add together** and are applied when you craft. Fs[ftbfandom-worktable] Fs[wand-adept]

  | Source | Discount |
  |---|---|
  | Caps | −20% to +10% (see §4.3) |
  | Sceptre | 10% |
  | Goggles of Revealing | 5% F[goggles] |
  | Thaumaturge's Robe | 2% F[robe] |
  | Thaumaturge's Leggings | 2% Fs[leggings] |
  | Thaumaturge's Boots | 1% F[boots] |
  | Void Thaumaturge Robes | 5% each, but they give Warp F[voidrobe] |

- **Combined formula:** GTNH gives (cap multiplier) × (recipe cost) − sceptre − armour, with a floor of 10% of the base cost. Fg[gtnh-wands] Fg[gtnh-notes] **I:** the floor may be GTNH-only.
- **Discounts beyond the worktable:** **I:** the discount also seems to apply to focus casting costs. That means the cap and gear choices matter for every use of vis, not just crafting. Pages about older versions describe the discount as general. Fs[wand-adept]

---

## Design-relevant takeaways (all inference)

1. **The core loop:** scan things to gain aspect points, spend the points on minigame notes, unlock recipes, then pay vis from your wand to craft. Nodes refill the wand.
   - Aspects are both the *currency* of research and the *vocabulary* of crafting.
   - The scan-once rule plus the per-aspect caps force players to explore widely rather than farm one thing.
2. **Known pain points, from forum evidence:** running out of high-tier aspects, unreliable lost-knowledge fragments, nodes accidentally drained to death, and an opaque grid-to-complexity scaling. These are the candidates for redesign (see the community-pain-points ticket).
3. **The hex adjacency rule (A is next to B only if one is a component of the other) is the elegant heart of the minigame.** It turns the aspect tree into a puzzle graph. **I:** keep that idea and change the presentation.
4. **There are two vis economies:** per-wand pools refilled from nodes, and centivis streamed from energised nodes through relays. Our spec should decide early whether to keep both or merge them.
5. **Void is native to the aspect tree.** Vacuos feeds magic, darkness, hunger and the eldritch, which gives us a lore-consistent hook for Void-centric aspects.
6. **The API needs:** aspect registration (addons added aspects), research flags (concealed, secondary, stub, virtual, autoUnlock, hidden parents), scan triggers, and node types and modifiers.

## Gaps and open questions

- How complexity maps to grid size and anchor count; the duplication cost formula.
- Natural node aspect amounts; how the Advanced Node Stabilizer differs.
- The scan duration in ticks; the size of the discovery bonus (sources conflict).
- Whether copper and silver caps are vanilla 4.2; whether the 10% discount floor is vanilla.
- The Eldritch Warp thresholds (26/51 vs 30/50).
- **I:** all of these could be settled cheaply by watching the game in `MC-instances/tc-1.7-instance`, if any spec needs them.

## Sources

[aspects]: https://ftbwiki.org/List_of_Aspects_(Thaumcraft_4)
[solver-aspects]: https://mintlify.wiki/leumasme/thaumcraft4-research-bot/concepts/aspect-system
[solver-usage]: https://mintlify.wiki/leumasme/thaumcraft4-research-bot/guides/usage
[fandom-aspects]: https://thaumcraft-4.fandom.com/wiki/Aspects
[fandom-tempestas]: https://thaumcraft4.fandom.com/wiki/Tempestas
[fandom-iterm]: https://thaumcraft4.fandom.com/wiki/File:Iterm.png
[changelog]: https://thaumcraft4.fandom.com/wiki/Changelog
[thaumometer]: https://ftbwiki.org/Thaumometer_(Thaumcraft_4)
[goggles]: https://ftbwiki.org/Goggles_of_Revealing_(Thaumcraft_4)
[research]: https://ftbwiki.org/Research_(Thaumcraft_4)
[table]: https://ftbwiki.org/Research_Table_(Thaumcraft_4)
[notes]: https://ftbwiki.org/Research_Notes
[scribing]: https://ftbwiki.org/Scribing_Tools_(Thaumcraft_4)
[tome]: https://ftbwiki.org/Thaumonomicon_(Thaumcraft_4)
[fragment]: https://ftbwiki.org/Knowledge_Fragment_(Thaumcraft_4)
[decon]: https://ftbwiki.org/Deconstruction_Table
[warp]: https://ftbwiki.org/Warp
[mt-research]: https://docs.blamejared.com/MineTweaker/en/modtweaker/thaumcraft_4_support/research/
[gotnukes]: https://github.com/GotNukesGit/Thaumcraft---New-Thaumonomicon-Interface/blob/main/README.md
[gtnh]: https://wiki.gtnewhorizons.com/wiki/Thaumcraft
[gtnh-cheatsheet]: https://wiki.gtnewhorizons.com/wiki/Thaumcraft_Research_Cheatsheet
[gtnh-wands]: https://wiki.gtnewhorizons.com/wiki/Thaumcraft_Wands
[gtnh-notes]: https://crazedd0ge.github.io/gtnhnotes/tc-wands.html
[fandom-research]: https://thaumcraft-4.fandom.com/wiki/Research
[ftbfandom-research]: https://ftb.fandom.com/wiki/Research_(Thaumcraft_4)
[fandom-table]: https://thaumcraft-4.fandom.com/wiki/Research_table
[fandom-research-entry]: https://thaumcraft-4.fandom.com/wiki/Research_(research)
[fandom-config]: https://thaumcraft-4.fandom.com/wiki/Configuration
[fandom-warp]: https://thaumcraft-4.fandom.com/wiki/Warp
[fandom-expertise]: https://thaumcraft-4.fandom.com/wiki/Research_Expertise
[fandom-mastery]: https://thaumcraft-4.fandom.com/wiki/Research_Mastery
[namu]: https://en.namu.wiki/w/Thaumcraft%204/%EC%97%B0%EA%B5%AC%20%EC%9D%BC%EB%9E%8C
[ftbforum-32725]: https://forum.feed-the-beast.com/threads/thaumcraft-4-what-to-do-if-you-run-out-of-aspects.32725/page-2
[ftbforum-35955]: https://forum.feed-the-beast.com/threads/thaumcraft-4-research-halted-no-aspects-left.35955/latest
[ftbforum-33246]: https://forum.feed-the-beast.com/threads/thaumcraft-4-knowledge-fragments-and-lost-knowledge-why-dont-they-work-for-me.33246/
[ftbforum-894289]: https://forum.feed-the-beast.com/threads/thaumcraft-4-2-help.61388/post-894289
[ftbforum-643799]: https://forum.feed-the-beast.com/goto/post?id=643799
[ftbforum-483234]: https://forum.feed-the-beast.com/goto/post?id=483234
[ftbforum-461337]: https://forum.feed-the-beast.com/goto/post?id=461337
[ftbforum-453511]: https://forum.feed-the-beast.com/goto/post?id=453511
[ftbforum-77872]: https://forum.feed-the-beast.com/goto/post?id=77872
[ftbforum-1099294]: https://forum.feed-the-beast.com/goto/post?id=1099294
[ftbforum-hungry]: https://forum.feed-the-beast.com/threads/thaumcraft-4-hungry-node.37585/page-2
[tc4220]: https://forum.feed-the-beast.com/threads/mod-update-thaumcraft-4-2-2-0.58333/latest
[wandcore]: https://ftbwiki.org/Wand_Core
[wandcraft]: https://ftbwiki.org/Wandcraft
[fandom-wands]: https://thaumcraft-4.fandom.com/wiki/Wands
[fandom-basicwand]: https://thaumcraft-4.fandom.com/wiki/Basic_Wand_Craft
[charm]: https://ftbwiki.org/Primal_Charm
[greatwood]: https://ftbwiki.org/Greatwood_Rod
[rods]: https://ftbwiki.org/Reed_Rod
[silverwood]: https://ftbwiki.org/Silverwood_Rod
[primalcore]: https://ftbwiki.org/Staff_Core_of_the_Primal
[copper]: https://ftbwiki.org/Copper_Cap
[silvercap]: https://ftbwiki.org/Charged_Silver_Cap
[thaumiumcap]: https://ftbwiki.org/Charged_Thaumium_Cap
[voidcap]: https://ftbwiki.org/Charged_Void_metal_Cap
[pedestal]: https://ftbwiki.org/Wand_Recharge_Pedestal
[fandom-pedestal]: https://thaumcraft4.fandom.com/wiki/Recharging_Pedestal
[amulet]: https://ftbwiki.org/Amulet_of_Vis_Storage
[transducer]: https://ftbwiki.org/Node_Transducer
[chargerelay]: https://ftbwiki.org/Vis_Charge_Relay
[node]: https://ftbwiki.org/Aura_Node
[fandom-node]: https://thaumcraft-4.fandom.com/wiki/Aura_Node
[fandom-tapping]: https://thaumcraft-4.fandom.com/wiki/Master_Node_Tapping
[fandom-preserver]: https://thaumcraft-4.fandom.com/wiki/Node_Preserver
[fandom-jar]: https://thaumcraft-4.fandom.com/wiki/Node_in_a_Jar
[stabilizer]: https://ftbwiki.org/Node_Stabilizer
[worktable]: https://ftbwiki.org/Arcane_Worktable_(Thaumcraft_4)
[vis]: https://ftbwiki.org/Vis_(Thaumcraft_4)
[ftbfandom-worktable]: https://ftb.fandom.com/wiki/Arcane_Worktable_(Thaumcraft_4)
[wand-adept]: https://ftb.fandom.com/wiki/Wand_of_the_Adept
[robe]: https://ftbwiki.org/Thaumaturge%27s_Robe_(Thaumcraft_4)
[leggings]: https://ftb.fandom.com/wiki/Thaumaturge's_Leggings_(Thaumcraft_4)
[boots]: https://ftbwiki.org/Thaumaturge%27s_Boots_(Thaumcraft_4)
[voidrobe]: https://ftbwiki.org/Void_Thaumaturge_Robe
