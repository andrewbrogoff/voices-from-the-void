# TC4 behaviour catalogue: world and endgame

Resolves [#4](https://github.com/andrewbrogoff/voices-from-the-void/issues/4) (map: #1).

This catalogue covers what Thaumcraft 4 (4.2.x, MC 1.7.10) **observably does** in these areas:

- golems
- armor and baubles
- warp
- taint and flux
- worldgen
- mobs
- Eldritch content and the Outer Lands

It describes behaviour, numbers and rules, not implementation.

## Method and caveats

- **Clean room.**
  - Public sources only: the Thaumcraft 4 Wiki (thaumcraft-4.fandom.com, abbreviated **TC4W**), FTB Wiki (ftbwiki.org / ftb.fandom.com, abbreviated **FTBW**), FTB forums and CurseForge.
  - No jar was opened or decompiled, and no Thaumcraft source repo was read.
  - The reference instance was not run. Wiki coverage was enough, and in-game checks are listed as open questions instead.
  - TC4W blocks HTML fetching, so its wikitext was read through the public MediaWiki API. Citations use the normal page URLs.
- **Tags.**
  - **[S]** means sourced. The URL is on the line or in the section header.
  - **[I]** means inference, a source conflict, or unverified.
- **Exact numbers.**
  - Some of the precise figures in wiki tables were probably worked out from code by wiki editors: tick rates, warp formulas, taint spread chances.
  - We treat them as documented observable behaviour. Our design should use its own numbers rather than copy them.
- **Gaps.**
  - No source gives numeric golem stats; they are only rated relative to each other.
  - No source gives a TC4 runic-shield recharge rate or vis cost, the Sanitizing Soap percentage, or a Warp Ward duration formula.

---

## 1. Golems

### Making one
- [S] **Golem body.** You make the body at the crucible: one block of the material plus Humanus, Spiritus and Motus, 4 of each. Flesh, Tallow and Thaumium bodies take 8 of each. https://thaumcraft-4.fandom.com/wiki/Golemancy
- [S] **Placing and activating.**
  - A placed body does nothing until you right-click it with an animation core.
  - The block face you click when placing it becomes its **home**. Place it against an inventory to make that inventory its home inventory.
  - https://ftb.fandom.com/wiki/Straw_Golem
- [I] "Animation powder" does not exist in TC4.

### Body materials
[S] https://thaumcraft-4.fandom.com/wiki/Golemancy. All ratings are relative.

| Body | Block | Durability | Strength | Self-heal | Carry (items) | Speed | Upgrade slots |
|---|---|---|---|---|---|---|---|
| Straw | hay bale | very low | very low | average | 1 | fast | 1 |
| Wood | greatwood log | below avg | low | average | 4 | above avg | 1 |
| Flesh (forbidden) | flesh block | low | low | very fast | 4 | above avg | 2 |
| Clay | bricks | average | average | slow | 8 | average | 1 |
| Tallow | tallow block | average | average | average | 8 | average | 2 |
| Stone | stone bricks | above avg | above avg | slow | 16 | slow | 1 |
| Iron | iron block | high | high | very slow | 32 | very slow | 1 |
| Thaumium | thaumium block | very high | high | slow | 32 | slow | 2 |

- [S] Clay, Stone, Iron and Thaumium golems are fireproof and sink and walk underwater; the others float.
- [S] A new golem starts at no more than 10 HP and heals up to its maximum.
- [S] Thaumium golems resist magic damage.
- [S] **Advanced Golem** (forbidden, 5 warp) is an infusion onto any golem. It gives:
  - one extra upgrade slot
  - slightly more speed
  - longer sight range
  - a brain-in-a-jar on its head
  - https://thaumcraft-4.fandom.com/wiki/Advanced_Golem

### Cores
[S] https://thaumcraft-4.fandom.com/wiki/Golem_Cores, https://ftbwiki.org/Golem_Animation_Core_(Guard)

How cores are made:
- A **blank core** is made at the arcane worktable.
- **Basic cores** are made at the crucible: the blank core plus 2 aspects.
- **Advanced cores** are made by infusion.

| Core | Tier | Behaviour |
|---|---|---|
| Gather | basic | Picks up loose items within range and puts them in home. Its item slots act as a filter. |
| Empty | basic | Carries items from home to marked spots. It puts them in a marked inventory or drops them on a marked block. With Order, it routes items by colour. |
| Fill | basic | Fetches the items set in its slots from marked inventories to home. "Precise" mode keeps a set amount; "Any" mode fetches until the source is empty or home is full. |
| Harvest | basic | Harvests mature crops and leaves the drops on the ground. Base range is 4. With Order, it replants using nearby seeds. |
| Guard | basic | Attacks hostile mobs except creepers. With Order, you choose targets, including animals and players. |
| Butcher | basic, forbidden in 4.2 | Kills adult farm animals. Never touches babies, and always leaves 2 adults of each type. |
| Decanting (liquid) | basic | Moves liquid from a marked pool or tank to home. Carries 1 bucket per source block, or 100 mB per point of carry from a tank. |
| Chop (lumber) | advanced | Fells trees from the top down. Its axe never wears out. It does not collect drops or replant. |
| Fishing | advanced | Fishes in nearby water. Its rod never wears out and catches land on the ground. |
| Use | advanced | Clicks marked locations like a player, using an item from home if it has one. Options: left or right click, sneak, or click empty space. It cannot mine blocks or hit mobs. |
| Sorting | advanced | Moves an item from home only to a marked inventory that already holds that item. |
| Alchemy (essentia) | advanced | Moves essentia, one aspect at a time, from home to marked jar banks. A marked jar counts together with all jars touching it. Fill order: labelled jars before unlabelled, warded before void, partly full before empty. |

**Shared rules**
- [S] **Range.** Default range is 16 around home. Harvest and Chop start at 4.
- [S] **Home.** Home stays fixed until you pick the golem up and place it again. Idle golems walk back home.
- [S] **Sensing.** Golems sense targets through walls.
- [S] **Movement.** Golems open doors. They cannot walk onto a Paving Stone of Warding. A redstone-powered **Golem Fetter** freezes any golem standing on it.
- [S] **Item slots.** Cores have 6 filter slots by default. Fire upgrades add more.
- [S] **Double chests.** Each half counts as a separate inventory.

### Elemental upgrades
[S] https://thaumcraft-4.fandom.com/wiki/Golemancy
- Each is made at the arcane worktable from 8 gold nuggets around the matching shard.
- A golem can have at most 2 of the same element.

| Upgrade | General effect | Per-core effect |
|---|---|---|
| Air | Faster movement and faster ranged attacks | Use: clicks faster. Fishing: chance of a double catch. |
| Earth | More strength (damage and work speed); carry +4 to +16 depending on body | n/a |
| Fire | More item slots; melee sets targets on fire | Fishing: cooks the catch. |
| Water | +4 range each, plus better ranged attacks | Harvest/Chop: +1 range each. |
| Order | Coloured marks | Empty: routes by colour. Harvest: replants. Guard: target choice. Fishing: more treasure. |
| Entropy | Thorns; looser item matching (ignores damage and NBT, uses ore dictionary) | Decanting: drains from the far edge of a pool. Fishing: less junk. |

### Accessories
[S] https://thaumcraft-4.fandom.com/wiki/Golemancy and the item pages linked there
- Accessories don't use upgrade slots. A golem can wear one of each, but these pairs can't be combined:
  - Hat and Fez
  - Plating and Bowtie
  - Visor and Spectacles
- The accessories:
  - **Top Hat**: +6 HP.
  - **Tiny Spectacles**: more sight range.
  - **Bowtie**: about 1.5× speed.
  - **Fez**: much faster healing.
  - **Dart Launcher** (Guard only): ranged attack for 3 to 5 damage, which lets the golem hit creepers.
  - **Visor** (Guard or Butcher): kills drop XP and player-only loot.
  - **Iron Plating**: much more armor, much less speed.
  - **Mace** (Guard or Butcher): +1 heart of damage.

### Golemancer's Bell and marks
[S] https://thaumcraft-4.fandom.com/wiki/Golemancer's_Bell
- **Picking up a golem.**
  - Left-click: it drops as an item.
  - Shift-left-click: it drops as separate parts (body, core, upgrades). Some upgrades and accessories are destroyed.
- **Tuning.** Right-clicking a golem tunes the bell to it. You can then see its home ring and its marks, drawn as rune disks on block faces with a floating chain linking them to the golem.
- **Editing marks** while tuned:
  - Right-click a block face to add a mark. Right-click a mark to remove it, or with Order, to cycle its colour (16 colours, uncoloured, or none).
  - Shift-right-click always removes a mark.
  - The change-focus key (F) clears all of that golem's marks.
- **Persistence.** Marks stay with the golem when you pick it up.

### Traveling Trunk (golem-like construct)
[S] https://thaumcraft-4.fandom.com/wiki/Traveling_Trunk
- An infusion made from a hungry chest and a wood golem.
- It hops after its owner like a pet, can be told to stay, and teleports to you, even across dimensions.
- It heals when fed and dumps its contents when it dies.
- It takes one elemental upgrade:
  - Air: faster.
  - Earth: more slots.
  - Fire: attacks mobs.
  - Water: near-invulnerable, and owner-only on servers.
  - Order: can be picked up with its contents inside.
  - Entropy: vacuums up nearby items.

---

## 2. Armor and baubles

### Armor sets
[S] Sources:
- Thaumium: https://ftb.fandom.com/wiki/Thaumium_Chestplate_(Thaumcraft_4) and the sibling pages
- Fortress: https://thaumcraft-4.fandom.com/wiki/Thaumium_Fortress_Armor
- Void: https://thaumcraft-4.fandom.com/wiki/Voidmetal
- Robes: https://thaumcraft-4.fandom.com/wiki/Thaumaturge's_Robes
- Void robes: https://thaumcraft-4.fandom.com/wiki/Void_Thaumaturge_Robes
- Cult: https://thaumcraft-4.fandom.com/wiki/Crimson_Cult

| Set | Armor (head/chest/legs/feet) | Durability (h/c/l/f) | Vis discount | Warping | Notes |
|---|---|---|---|---|---|
| Thaumium | 2/6/5/2 | 275/400/375/325 | none | none | Enchantability like gold |
| Thaumium Fortress | 3/7/6/no boots | 440/641/600 | none | none | Each piece protects like diamond, plus extra against magic and fire. Each extra piece worn strengthens the others. Made by infusing the Thaumium piece. |
| Void metal | 3/7/6/3 | 110/160/150/130 | none | 1 per piece | Repairs itself fast. Hits weaken the target. |
| Thaumaturge's Robes | 3 chest/2 legs/1 boots | 400 each | 2%/2%/1% | none | Dyeable. Full set plus goggles = 10% |
| Void Thaumaturge | hood 3/robe 7/legs 6 | 110/160/150 | 5% each | 2 per piece | Hood counts as goggles. Self-repairs. Unlocked late (warp-gated). |
| Crimson Knight / Cleric | 2/6/5/2 | 165/240/225/195 | Cleric 1% per piece | 1 per piece | Drops from mobs only |

- [S] **Fortress masks** are infused onto the Fortress helm (https://thaumcraft-4.fandom.com/wiki/Thaumium_Fortress_Masks):
  - **Grinning Devil**: makes warp events less severe, but not less frequent.
  - **Angry Ghost** (forbidden): attackers may get Wither.
  - **Sipping Fiend** (forbidden): chance to steal life when you hit something.
  - There is no "Witherer" mask.
- [I] **Goggles on the Fortress helm.** No source confirms an infusion that adds goggles to it. Addons do it. Verify in game.
- [S] **Goggles of Revealing** (https://thaumcraft-4.fandom.com/wiki/Goggles_of_Revealing). They give a 5% vis discount and show:
  - nodes through walls, with their aspects
  - essentia in jars, alembics and crucibles
  - essentia still owed during infusion

  They do not scan. Durability is 276 on FTBW and 350 on TC4W.
- [S] **Boots of the Traveller** (https://thaumcraft-4.fandom.com/wiki/Boots_of_the_Traveller):
  - faster movement, and swimming as fast as walking
  - step up 1-block heights
  - jump about 3 blocks
  - less fall damage
- [S] **Thaumostatic Harness** (chest slot) lets you fly using Potentia from a jar. It gives a 2% vis discount, or 5% for Aer. The **Thaumostatic Girdle** (belt slot) speeds up flight and blocks fall damage. https://thaumcraft-4.fandom.com/wiki/Thaumostatic_Harness

### Baubles
[S] https://thaumcraft-4.fandom.com/wiki/Baubles
- Slots: 1 amulet, 2 rings, 1 belt.
- [S] **Amulet of Vis Storage** (https://thaumcraft-4.fandom.com/wiki/Amulet_of_Vis_Storage):
  - Holds 250 of each primal.
  - Recharges the held wand at 1 centivis per tick.
  - Powers the Repair enchantment and runic shields.
  - Refills only on a recharge pedestal or, slowly, through vis relays.
- [S] **Vis Stone**: a loot-only version holding 25 of each primal.
- [I] These items do not exist in TC4: vis-discount rings or belts, a "Belt of Vis", and lesser/greater vis amulets.

### Runic shielding
[S] https://thaumcraft-4.fandom.com/wiki/Runic_Shielding and https://thaumcraft-4.fandom.com/wiki/Runic_Shielding_Upgrades

**How it works**
- Every runic item adds to **one shared shield pool**.
- Damage drains the shield before health. The shield does not stop drowning or starvation.
- It recharges using **Aer and Terra vis** from carried wands or the vis amulet, and stops recharging when either runs out.
- It shows as an overlay on the health bar.

**Items**

| Item | Shield | Special effect |
|---|---|---|
| Ring of Protection (loot only) | +1 | n/a |
| Ring of Runic Shielding | +5 | n/a |
| Amulet | +8 | n/a |
| Girdle | +10 | n/a |
| Charged Ring | +4 | Each one recharges the shield 25% faster |
| Revitalizing Ring | +4 | When the shield breaks: Regeneration II for 12 s; 20 s cooldown |
| Amulet of Emergency Shielding | +7 | When the shield breaks: restores 8 shield; 60 s cooldown |
| Kinetic Girdle | +9 | When the shield breaks: a blast knocks back and damages nearby mobs without breaking blocks; 20 s cooldown |

**Runic augmentation**
- [S] Each infusion adds +1 shield to any armor piece or bauble.
- [S] Each one costs a diamond plus Salis Mundus equal to the new level.
- [S] The essentia cost doubles with each level the item already has, and instability goes up by 1 per level.
- [I] There is no hard cap; the rising cost works as the limit.

**History and config**
- [S] TC4.0 and 4.1 used a full runic armor set instead (head 8, chest 24, legs 16, feet 8). Version 4.2 replaced it with baubles. https://thaumcraft-4.fandom.com/wiki/Runic_Armor
- [S] Some runic values can be configured since 4.2.2.0.
- [I] No source gives the recharge rate or vis cost.

---

## 3. Warp

[S] https://thaumcraft-4.fandom.com/wiki/Warp unless noted.

### Kinds
- **Temporary.** Loses 1 point every 100 s.
- **Normal ("sticky").** Never decays on its own.
- **Permanent.** Cannot be removed in normal play.
  - [I] FTBW claims soap removes it after about 10 hours of use. TC4W and the forums disagree; trust "not removable".
- **Item warp.** "Warping N" gear adds N warp only while it is carried. It does not wake up quiet warp (see the counter below).

### Sources
- **Forbidden research.** Its warp value is split: half rounded up goes to permanent, half rounded down to normal.
  - 1: Research Mastery, bone wand/staff cores, Bottled Taint, the Angry Ghost and Sipping Fiend masks, Butcher core, Primal Node Manipulation, void caps.
  - 2: Nine Hells focus, Sinister Lodestone, Infernal Furnace, Primal focus.
  - 3: The Crimson Cult, Liquid Death, Brain in a Jar, Flesh Golems, Primal staff core.
  - 5: Advanced Golems.
  - 6: Opening the Eye.
- **Hand-crafting certain forbidden items.** +1 normal warp each time (Bottled Taint, Primal focus, Flesh Golem and others).
- **Temporary warp sources:**
  - Eldritch Guardian screech: 1–3, always.
  - Eating a zombie brain: 1–3 at 90%, otherwise 1 normal warp at 10%.
  - Hit by runed stone: 1–2 at 50%.
  - Infusion instability: 1–5, 75% temporary, 25% normal.
- **Gear (item warp).**
  - 1 per piece: Crimson Cult gear, void metal armor and tools.
  - 2 per piece: void robes, Crimson Blade, Primal Crusher.
- [S] Players deliberately eat brains to reach the warp gates. https://forum.feed-the-beast.com/threads/thaumcraft-4-2-how-does-flux-work.144102/

### Event timing
- **Check.** Every 100 s, for each player with warp.
- **Counter.** A hidden counter jumps to your total warp whenever you gain any. Each event that fires lowers it by the larger of 5 or 2√counter. So events fade out once you stop gaining warp. This was added in 4.2.3.1.
- **Chance.** An event fires if a d100 roll ≤ √counter. The maximum is about 10% per check, roughly one event every 11 minutes.
- **Severity.** Severity W′ = min(100, (2 × total warp + counter) / 3), with the Grinning Devil mask taking off 2–6. A roll from 0 to W′ picks from the table below, so more warp unlocks the nastier events.
- **Presentation.** Every event has a "headache": the screen edges pulse dark and a heartbeat plays. Most events also post a chat line.

### Event table (roll → effect)

| Roll | Effect |
|---|---|
| 0–8 | whispers: +1 random research point |
| 9–12 | fake sound: "something behind you" |
| 13–16 | Flux Flu |
| 17–20 | Thaumarhia: you leak flux goo |
| 21–24 | Unnatural Hunger |
| 25–28 | fake: "something is following you" |
| 29–32 | fog, then 1 Eldritch Guardian |
| 33–36 | blurred vision |
| 37–40 | Sun Scorned |
| 41–44 | Mining Fatigue |
| 45–48 | Flux Phage |
| 49–52 | Night Vision |
| 53–56 | Deadly Gaze |
| 57–60 | fake Mind Spiders |
| 61–64 | message: "maybe it is time to stop" |
| 65–68 | Eldritch Guardians (W′/30 of them, max 8) |
| 69–72 | blindness |
| 73–76 | "moment of clarity": −1 normal warp |
| 77–80 | Unnatural Hunger IV |
| 81–88 | whispers: several research points |
| 89–92 | real Mind Spiders, up to 50 |
| 93–100 | Eldritch Guardians, up to 8 |

### Effects
[S] https://thaumcraft-4.fandom.com/wiki/Effect
- **Flux Flu.** −10% vis discount per level.
- **Flux Phage.** Like Flux Flu, but it spreads to others one level lower.
- **Sun Scorned.** You burn in sunlight but heal in darkness. Since 4.2.1.0 it applies only in sunlight.
- **Unnatural Hunger.** Fast hunger drain. Each rotten flesh or brain you eat removes one level.
- **Thaumarhia.** You leave flux goo behind you.
- **Deadly Gaze.** Mobs you look at get Wither.
- **Milk.** Cures none of these.

### Warp-gated research
- Granted on the next event that fires. Only normal and permanent warp count.

| Warp | Research | What you get |
|---|---|---|
| 11 | Purifying Bath Salts | "how do I stop the headaches" message |
| 26 | Eldritch Epiphany | opens the Eldritch tab, +10 research points |
| 51 | Eldritch Revelation | +20 research points |

- [I] FTBW instead says the Eldritch tab opens after "half the warp research". The forums agree with the warp threshold. Verify in game.
- [S] **Wuss mode config.** `wuss_mode` turns warp off entirely, and warp-gated research then stays locked. https://thaumcraft-4.fandom.com/wiki/Configuration
- [S] **Sanity Checker.** A held item that shows a gauge for permanent (dark purple), normal (purple) and temporary (pink) warp, with gold marks at 26 and 51. https://thaumcraft-4.fandom.com/wiki/Sanity_Checker

### Cures
- [S] **Sanitizing Soap** (https://thaumcraft-4.fandom.com/wiki/Sanitizing_Soap):
  - You hold right-click to use it.
  - It removes all temporary warp and has a small chance to remove normal warp.
  - The chance is "much higher" under Warp Ward or while standing in purifying fluid.
  - Made at the crucible from a tallow block with Ordo, Cognitio, Sano and Alienis, 16 of each.
  - [I] No source gives the exact percentage.
- [S] **Purifying Bath Salts** (https://thaumcraft-4.fandom.com/wiki/Purifying_Bath_Salts):
  - Thrown into water, or fed to the Arcane Spa, they make a **Purifying Fluid** block.
  - Touching the fluid uses it up and gives **Warp Ward** for about 20 minutes. The ward is shorter the more warp you have.
- [S] **What Warp Ward does.** It skips the 100 s checks entirely, so events are delayed rather than cancelled. While it lasts, temporary warp stops decaying and the counter stops dropping.

---

## 4. Flux and taint

### Flux goo and gas
[S] https://thaumcraft-4.fandom.com/wiki/Flux_Goo, https://thaumcraft-4.fandom.com/wiki/Flux_Gas, https://thaumcraft-4.fandom.com/wiki/Crucible

- **Sources:**
  - A crucible holding more than 100 essentia.
  - Essentia left in a crucible: compound aspects decay into their parts, and primal aspects decay into flux.
  - Emptying a crucible with a wand.
  - Unstable infusions.
  - Thaumarhia.
  - Node explosions.
  - Breaking a full essentia reservoir.
  - [I] No source names the alchemical furnace or broken jars.
- **Both forms:**
  - Finite fluids of 1–16 units per block, spreading at lava speed.
  - Gas rises and clings to ceilings.
  - Neither can be picked up in a bucket; you remove them by placing a block over them.
- **Goo:**
  - Contact gives Flux Flu for 30 s; its level depends on how many units the block holds.
  - Each random tick a goo block may:
    - become a Thaumic Slime (4%, if it has 3 or more units)
    - become fibrous taint and taint its column's biome (≈1.9%, if it has 7 or more units)
    - lose a unit (3.2%), sometimes releasing gas
- **Gas:** contact gives Flux Flu or Nausea, and the gas loses a unit each time.
- **Flux Scrubber:** a powered device that removes flux in a 17³ cube around it.
- **Config:** `biome_taint_from_flux` stops flux from turning into taint.

### Tainted Land spread
[S] https://thaumcraft-4.fandom.com/wiki/Tainted_Land and https://thaumcraft-4.fandom.com/wiki/Fibrous_Taint

- **The biome doesn't spread itself.** The spreading is done by the taint *blocks*: fibrous taint, crusted taint and tainted soil.
- **Each random tick, a taint block may:**
  - (a) Turn a random column within 3×3 into Tainted Land. It must already be in Tainted Land and touch at least 2 other taint blocks.
  - (b) Place fibrous taint within ±2 blocks vertically, in an already-tainted column. Fibrous taint can replace air, grass, plants, leaves, snow, vines or fire.
  - (c) Convert nearby blocks:
    - Logs, pumpkins, melons and cactus become crusted taint.
    - Dirt, grass, sand, gravel and clay become tainted soil.
- **Barriers:**
  - It reaches 3 blocks sideways and 5 vertically.
  - A 1-block wall stops it sideways, but thin floors and ceilings don't.
  - Water and non-solid blocks stop it.
  - It travels through caves.
  - It freezes in unloaded chunks.
- **Decay:** when a taint block finds it is no longer in Tainted Land:
  - Fibrous taint vanishes.
  - Crusted taint may turn into flux goo (5%).
  - Tainted soil may turn back to dirt (10%).
- **Other ways taint starts:**
  - Tainted sheep "grazing".
  - Tainted creeper explosions.
  - Thrown Bottled Taint.
  - Tainted nodes.
  - Rare primal-focus explosions.
- **Immune:** the Outer Lands.
- **Worldgen and config:**
  - Biome weight is 2, against 10 for vanilla biomes. Tainted Land often generates next to Magical Forests.
  - Config switches: `generate_taint`, `biome_taint_spread` (speed, or off) and `hard_mode_nodes`.
- **Tainted nodes** (https://thaumcraft-4.fandom.com/wiki/Nodes):
  - Every 50 ticks, they taint a column within 15×15 and place fibrous taint within 11³.
  - A non-pure node standing in Tainted Land becomes tainted after about 40 minutes on average.
  - There is no in-mod cure. Your only options are to wall it in, jar it, or destroy it.
  - Pure nodes (from silverwood) convert their area to Magical Forest faster than taint spreads.

### Taint Poison and items
[S] https://thaumcraft-4.fandom.com/wiki/Taint and https://thaumcraft-4.fandom.com/wiki/Effect

- **Taint Poison** is damage over time.
  - Animals, villagers and creepers that die while poisoned come back as tainted versions. Other mobs become Thaumic Slimes.
  - Zombies, skeletons and slimes are immune.
- **Catching it from blocks:**
  - Touching fibrous taint: 0.1% chance per tick for players, 0.2% for mobs.
  - Standing on crusted taint or tainted soil: 1% per tick for players, 5% for mobs.
- **Tainted goo and taint tendrils** dissolve in your inventory and give you taint. They are safe in chests and the trunk. They are the renewable source of Vitium essentia.
- **Silk Touch** collects crusted taint and tainted soil.

### Taint mobs
[S] https://thaumcraft-4.fandom.com/wiki/Taint and the individual mob pages

- **Tainted cow, pig, chicken, sheep, villager and creeper.**
  - All hostile, and their hits inflict taint.
  - Tainted sheep spread taint as they graze.
  - Tainted creepers taint the area around them when they explode, but don't break blocks.
- **Thaumic Slime.** Merges with other slimes, up to 100 HP. Anything it kills becomes a new slime.
- **Taintacle.**
  - 44 HP and doesn't move, but has a long reach.
  - Loses health outside Tainted Land.
  - The only mob that spawns naturally in the taint biome.
- **Taint Spore.** Grows on spore stalks and bursts into 1–8 Tainted Crawlers when a player comes within 3–4 blocks. Crawlers have 5 HP.
- **Taint Spore Swarmer.**
  - 75 HP. Forms from crusted taint.
  - Releases a Taint Swarm every 25 s while a player is within 16 blocks. Invisibility stops it.
  - Loses health outside Tainted Land.
- **Taint Swarm.** 46 HP; it flies.
- [I] There is no "taint spider" in TC4.

### Cleanup
- [S] **Ethereal Bloom** (https://thaumcraft-4.fandom.com/wiki/Tainted_Land):
  - Turns the biome back to what it was before the taint, or to Plains if it generated tainted, over roughly a 9×9 area.
  - The taint blocks then die off on their own.
  - It can't clean the ground next to a tainted node.
  - It needs Vitium essentia.
- [S] **Manual removal.** Breaking taint blocks or washing them away stops the damage, but the biome stays tainted.
- [I] No source says Salis Mundus cures taint.

---

## 5. Worldgen

### Ores
[S] https://ftbwiki.org/Cinnabar_Ore_(Thaumcraft_4), https://ftbwiki.org/Amber_Bearing_Stone_(Thaumcraft_4), https://ftbwiki.org/Air_Infused_Stone_(Thaumcraft_4) and sibling pages

- **Cinnabar.** Up to 18 veins per chunk, 1–2 blocks each. Smelts to Quicksilver.
- **Amber-bearing stone.** Up to 20 veins per chunk, 1–2 blocks each. Drops amber, more with Fortune.
- **Infused stones**, one per primal.
  - Up to 8 veins per chunk, 4–8 blocks each. Drop 1–2 shards (1–5 with Fortune III). Found in any biome, but each is most common in one:

    | Stone | Most common in |
    |---|---|
    | Air | Plains |
    | Fire | Desert |
    | Earth | Forest |
    | Water | Ocean |
    | Order | Magical Forest, Mushroom Island, Extreme Hills |
    | Entropy | Swamp, Tainted Land |

  - [I] The wiki says "all heights"; the real Y distribution needs an in-game check.
- **Balanced shards** are not an ore. They are crafted, or dropped by Outer Lands crystals.

### Trees and plants
[S] https://ftbwiki.org/Greatwood_Sapling_(Thaumcraft_4), https://ftbwiki.org/Silverwood_Sapling_(Thaumcraft_4), https://ftbwiki.org/Magical_Forest

- **Greatwood.**
  - About 32 tall and about 20 wide, with a 2×2 trunk, yet it grows from a single sapling.
  - Bone meal doesn't work on it.
  - Found in forests and plains, most often in the Magical Forest.
- **Infested Greatwood.** A variant with cobwebs, a cave spider spawner and a loot chest.
- **Silverwood.**
  - About 16 tall, with a 5×5 diamond-shaped trunk.
  - It usually holds one node, almost always **Pure**, in its core logs. Breaking that log destroys the node.
- **Plants:**
  - **Shimmerleaf**: around silverwood.
  - **Cinderpearl**: deserts.
  - **Vishroom**: Magical Forest only; stepping on it causes Nausea.
  - **Mana beans**: on oak logs in the Magical Forest.

### Biomes
- [S] **Magical Forest** (https://ftbwiki.org/Magical_Forest):
  - Pale green, with silverwood and greatwood 6× as common as in normal forests.
  - Has big mushrooms and moss-stone spheres.
  - The only biome where Pech spawn.
- [S] **Eerie** (https://ftbwiki.org/Eerie):
  - Never generates; it only spreads around Dark nodes over time.
  - Very dark grass, sky and water.
- [S] **Tainted Land:** see section 4.

### Structures
- [S] **Totem** (https://ftbwiki.org/Totem):
  - An obsidian pillar 3–8 tall with a Dark node inside.
  - Spawns Furious Zombies and turns the area around it Eerie.
- [S] **Hollow Hill**, TC4's "barrow" (https://ftbwiki.org/Hollow_Hill_(Thaumcraft_4)):
  - Extremely rare. Three levels, with a Dark node, zombie and skeleton spawners, a trapped chest wired to TNT, and 2 Old Urns.
- [S] **Wizard's Tower** (https://ftbwiki.org/Wizard%27s_Tower): appears in villages and has a chest of early TC items.
- [S] **Eldritch Portal (obelisk) and its Crimson variant:** see section 7.
- [I] "Hilltop stones" were a TC3 structure and are not in TC4.

### Aura nodes
[S] https://ftbwiki.org/Aura_Node, https://thaumcraft-4.fandom.com/wiki/Nodes

- **Node types:**

  | Type | Frequency | Behaviour |
  |---|---|---|
  | Normal | most common | n/a |
  | Pure | ≈1.7% | Converts the area to Magical Forest and blocks taint. Contains Victus. |
  | Dark | ≈1.7% | Makes the area Eerie and spawns Furious Zombies at night. Contains Mortuus, Exanimis and Tenebrae. |
  | Tainted | Tainted Land only | Spreads taint. |
  | Unstable | ≈1.7% | Produces no vis and eventually vanishes. |
  | Hungry | ≈0.6% | Eats nearby blocks except obsidian and pulls in entities. |

- **Modifiers:**
  - Bright: 2× regeneration.
  - Pale: ½ regeneration.
  - Fading: no regeneration; it drains away.
- **Aspects** depend on location:
  - Aer: plains and high altitude.
  - Aqua: near water.
  - Terra: forests and underground.
  - Ordo: snow.
  - Ignis: desert, nether and near lava.
  - Perditio: swamp and taint.
- **Neighbouring nodes.** Nodes within 5 blocks drain each other. The bigger node gains only the square root of what it drains.
- **Breaking a node** drops Ethereal Essence equal to its power ÷ 10.

---

## 6. Mobs

[S] FTBW pages: https://ftbwiki.org/Wisp_(Thaumcraft_4), https://ftbwiki.org/Pech, https://ftbwiki.org/Angry_Zombie_(Thaumcraft_4), https://ftbwiki.org/Furious_Zombie, https://ftbwiki.org/Firebat_(Thaumcraft_4), https://ftbwiki.org/Eldritch_Guardian, https://ftbwiki.org/Eldritch_Crab, https://ftbwiki.org/Shambling_Husk, https://ftbwiki.org/Mind_Spider, https://ftbwiki.org/Crimson_Knight, https://ftbwiki.org/Crimson_Cleric

| Mob | HP / armor | Damage | Where | Notes / drops |
|---|---|---|---|---|
| Wisp | 22 | 4 (lightning bolt) | dark areas | Has a random aspect. Drops essence of that aspect. |
| Pech (Forager, Mage, Stalker) | 30 / 2 | 6 + weapon | Magical Forest only | Neutral, but the whole group turns on you if one is hit. Trade by giving them Lucrum items until friendly, then roll a die for a reward. |
| Angry Zombie | 25 / 5 | 5 | dark areas | Can drop a zombie brain. |
| Furious Zombie | 50 / 5 | 7 | near Dark nodes | Grows up to 3.5 blocks tall when hit. Rare thaumium drop. |
| Firebat | 5 | 1 + fire | Nether | Explodes. |
| Eldritch Guardian | 50 / 4 | 7 | Outer Lands, Dark-node portals, warp events | Causes fog and dims the screen. Gets 10 runic shield in the Outer Lands. Rare Eldritch Eye drop. |
| Eldritch Crab | 20 | 4 | Outer Lands | Latches onto your head; look up to kill it. |
| Shambling Husk | 30 / 15 | 5 | Outer Lands | Releases a crab when it dies. |
| Mind Spider | 1 | 1 | Outer Lands nests, warp events | Nearly invisible. |
| Crimson Knight | 36 / 15 | 10 | cult portals | Four ranks, from iron sword up to void sword. Drops void seeds, knowledge fragments, rarely cult gear or Crimson Rites. |
| Crimson Cleric | 30 / 13 | 4 + fire | cult portals | Shoots fireballs. Same drops as the Knight. |

[I] These mobs do not exist in TC4: "Watcher", "Primal Orb" and "Brainy Zombie" (that name is the TC3 ancestor of the Angry/Furious Zombie).

---

## 7. Eldritch content and the Outer Lands

### The overworld portal (obelisk)
[S] https://ftbwiki.org/Eldritch_Portal, https://ftbwiki.org/Crimson_Eldritch_Portal

**Structure**
- A floating obelisk 6 blocks tall stands over a Dark node, with an Eldritch Altar below the node.
- 8 capstones form a ring around it.
- Everything sits on a 7×7 platform of obsidian tiles.

**Behaviour**
- Mining any part makes the rest explode, with no drops.
- The node spawns Guardians and Furious Zombies.

**Opening it**
1. Get the research **Opening the Eye** (6 warp).
2. Put 4 **Eldritch Eyes** into the obelisk.
3. Use a wand holding **100 of each primal** on the node.
4. The node becomes a portal.

**Separate mazes**
- Every portal leads to its own maze, so each new obelisk you find means a new maze and another chance at a pearl.

**Crimson variant**
- The same structure with banners on it.
- 4 Clerics at the corners channel into the node, which spawns Knights.
- If you hit a Knight, the Clerics attack you.
- [S] Players complain these portals are hard to find, and that angry Clerics despawn, taking the Crimson Rites source with them. https://forum.feed-the-beast.com/goto/post?id=1544076

**Eldritch Eye**
- A rare Guardian drop.
- It can also be made by infusion from a void seed, gold and an ender eye. The recipe needs Crimson Rites research.

**Crimson Rites**
- A book that drops from Knights and Clerics 2.5% of the time.
- Reading it grants "The Crimson Cult" research.
- [I] Forum users say it also leads into Opening the Eye.

**Void seed and void metal**
- [S] Void seeds are made at the crucible from seeds plus Tenebrae, Vacuos and Alienis. Cultists also drop them.
- [S] Void seeds are the base for void metal and the Eldritch Eye. https://ftbwiki.org/Void_Seed

### The Outer Lands
[S] https://ftbwiki.org/Exploring_Eldritch_Dimension

**Layout**
- A maze of corridors with 5×5 walkable space.
- It is sealed by "Portal to Nothing" blocks and bedrock.
- **Flight is disabled.**
- The outer shell can't be mined.
- Players can place and break ordinary blocks inside, for example to mark dead ends.
- The boss room does not allow block placement.

**Room types**
- portal room
- crossings
- ancient library
- runed corridor (lightning that partly ignores armor)
- crusted nest (about 1 crab per second)
- crusted blockade
- mind-spider nest
- tainted corridor
- **capstone room**: holds the **Runed Tablet**, guarded by 4 Guardians
- near-boss room

**Boss access**
- Inserting the tablet into the Ancient Doorway spawns that maze's single boss.

**Loot**
- **Old Urns**, in three tiers (https://ftbwiki.org/Old_Urn):
  - common: coins
  - uncommon: thaumium and void gear
  - rare: nether stars, and a pearl about 0.1% of the time
- Abandoned crates.
- Strange crystals, which drop balanced shards.
- Glyphed stone, which drops knowledge fragments.

**Pacing**
- Rushing straight to the boss takes under 5 minutes. Fully looting a maze takes 4–6 hours.

**Bugs (4.2.3.5)**
- Players can fall into the void on arrival.
- Mazes sometimes fail to generate in multiplayer.
- [S] https://board.aternos.org/thread/86146-thaumcraft-s-outer-lands-in-blightfall-do-not-generate-properly-when-entered/

### Bosses
Each maze has exactly one of these four bosses. [S] https://ftbwiki.org/Champion_Eldritch_Guardian, https://ftbwiki.org/Eldritch_Construct, https://ftbwiki.org/Crimson_Portal, https://ftbwiki.org/Crimson_Praetor, https://ftbwiki.org/Giant_Taintacle

| Boss | HP | Damage | Behaviour |
|---|---|---|---|
| Champion Eldritch Guardian | 200 + 150 shield, armor 4 | 10 | Regenerates 1 HP/s and 2 shield/s. Blocks it walks on are "charged" and give Weakness. In a frenzy it teleports home, becomes invulnerable and charges everything within 23 blocks. It gets a random Lovecraftian name and a random champion modifier. |
| Eldritch Construct (Golem) | 250, armor 6 | 7, or 17 when raging | Rages after several hits. Near death its head blows off, it heals fully, and it pulses electric damage that ignores runic shielding. The room has 3 helper portals. |
| Crimson Portal | 500, armor 5 | none | Spawns waves of cultists (Knights, Clerics, then the **Praetor** on wave 5). It loses 8 HP per mob spawned and regenerates when hit until the Praetor appears. Dies by itself after about 30 minutes. |
| Giant Taintacle ×4 | 142–155 each | 27 | Two start giant and two grow later. Only the last one killed drops the pearl. |

- **Crimson Praetor** (a mini-boss, not an armor set):
  - 125 HP, armor 18, 12 damage (24 when enraged).
  - Gives Regeneration to cultists within 23 blocks.
  - Drops the Crimson Blade and Praetor armor.

### Primordial Pearl
[S] https://ftbwiki.org/Primordial_Pearl, https://ftbwiki.org/Primal_Crusher

**Sources**
- Every boss drops one guaranteed.
- Urns and crates very rarely contain one.
- It does not stack.

**Uses**
- **On a node:** adds primal aspects and brightness or upgrades the node, but explodes and releases flux. The research Primordial Node Manipulation reduces this.
- **Primal Crusher:** an infusion that combines void and elemental tools around the pearl. It mines 3×3, sometimes gives native clusters, repairs itself, and carries 2 warp.

---

## Design notes for the reimagining [I]

These are inferences from community feedback found during research. They are not part of the spec.

- **Warp.**
  - Much content is gated behind warp, which some players resent. The 4.2.3.1 fade-out counter is widely seen as the fix.
  - Players liked the early "flavour" events (whispers, fake sounds) and treated the late ones (Guardians, spiders) as the real cost.
  - This maps naturally onto "the voices" as the Void spine. Sources: https://forum.feed-the-beast.com/threads/thaumcraft-4-2-how-does-flux-work.144102/ and https://forum.feed-the-beast.com/goto/post?id=1544076
- **Taint.**
  - Complaints:
    - It spreads invisibly through caves and under water.
    - Tainted nodes can't be cured.
    - Ethereal Blooms are expensive, and you need lots of them.
    - Taint items decay in your inventory.
    - The `biome_taint_spread` config is confusing.
    - It keeps spreading in spawn chunks on empty servers.
  - Players do like that its growth is easy to see and that it can be countered (walls, water, blooms, pure nodes).
  - Source: https://forum.feed-the-beast.com/goto/post?id=1474098 and https://forum.feed-the-beast.com/goto/post?id=751381
- **Outer Lands.**
  - Complaints are about bugs (void-fall, failed generation), getting lost, and rare Crimson portals.
  - Nobody calls the dimension itself a bad idea, and later ports (TC4 Unbound, Thaumic Reborn) recreated it.
- **Golems.**
  - The bell and mark interface is powerful but hard to discover, and the stats are opaque because they are only rated relative to each other.

## Open questions (verify in the reference instance if needed)
- Does an infusion exist that adds goggles to the Fortress helm?
- Does the Eldritch tab unlock at 26 warp, or after "half the warp research"?
- What are the actual Y ranges for ores and infused stones?
- What is the Sanitizing Soap percentage, and how does Warp Ward duration scale with warp?
- What is the runic shield recharge rate and vis cost?
- What are the absolute golem HP, speed and damage numbers?
