# Community pain points with Thaumcraft 4 and its successors

Research for ticket #5 (map #1). Question: what did players and modpack makers love and hate about Thaumcraft 4 (TC4), how were TC5, TC6 and comparable magic mods received, and which design moves were widely praised or widely hated?

All findings are paraphrased from public sources. Nothing was decompiled. URLs marked *(snippet)* were seen only in search-result summaries and not opened, so treat them as weaker evidence.

## Evidence base and gaps

- **Reddit is missing.** reddit.com was blocked for fetching and searching, so there are no direct r/feedthebeast or r/thaumcraft threads. Two Reddit mirror links appear as snippets only. Reddit is where most post-2016 sentiment lives, so the TC6-era and modern-mod findings are under-sampled.
- **What the evidence is made of:** the archived Feed The Beast (FTB) forum (2013–2019), GT New Horizons (GTNH) and Azanor GitHub issues, ModDex user reviews, CurseForge and Modrinth pages, server forums (MineYourMind, Aternos), and TV Tropes (snippet only, because the site returned 403).
- **Thin or absent:** YouTube reviews, CurseForge comment threads, the Minecraft Forum (apart from Azanor's farewell post), and sentiment about Witchery, Ars Magica 2, Embers, Occultism and Mahou Tsukai. The search budget ran out before these were covered.
- **Azanor gave almost no public design rationale.** I found no stated reason for the TC6 research redesign, the removal of wands, or the TC5→TC6 rewrite. The one exception is a 2016 remark that node recharge particles "were bandwidth hogs" ([suggestions#14](https://github.com/Azanor/thaumcraft-suggestions/issues/14)).
- **How to read "prevalence":** it is relative to the sources found, and is not a survey.

Ranking criterion: how often a complaint (or praise) recurs across independent sources × how badly it hurts (it makes people disable the system, drop the mod, or write a fix mod).

---

## Ranked pain points

### 1. Warp: permanent, and it interrupts play that has nothing to do with Thaumcraft
- GTNH players: "We all know and hate warp... an annoyance and unavoidable." Proposals to rebalance it were closed as not planned. [GTNH#5643](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/5643)
- Squads of Eldritch Guardians spawn on top of the player every 15 minutes or so, sometimes while a GUI is open. [FTB post](https://forum.feed-the-beast.com/goto/post?id=848892), [FTB post](https://forum.feed-the-beast.com/goto/post?id=1557692)
- Warp's mob spawns and debuffs punish people who are mostly doing something else. Several players disable warp in the config or threaten to drop the mod. [FTB "Thaumcraft discovery"](https://forum.feed-the-beast.com/threads/thaumcraft-discovery.52767/), [FTB post](https://forum.feed-the-beast.com/goto/post?id=814119)
- TV Tropes lists permanent warp as a "Scrappy Mechanic". [TV Tropes YMMV](https://tvtropes.org/pmwiki/pmwiki.php/YMMV/Thaumcraft) *(snippet)*
- Addons with their own forbidden research push warp totals higher still. Vanilla TC4 warp on its own was called "very manageable". [FTB post](https://forum.feed-the-beast.com/goto/post?id=848892)
- Counter-evidence: some players just ignore it (it decays in later 4.2 versions). GTNH chose to add more varied and less destructive warp events (Warp Theory) rather than remove warp. [FTB post](https://forum.feed-the-beast.com/goto/post?id=1557692), [GTNH#8611](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/8611)

**Implication for our redesign:** this is the biggest tension in the project, because the most-hated TC4 mechanic is the one we are making central. The redesign needs four properties:
- Warp should be earned by choices the player can see and understand, not as a hidden tax.
- It should be readable: the player can always see where they stand.
- Reducing it should have a real cost but always be possible. A permanent tier is fine if it is small and deliberate.
- Its effects should be the Voices (atmosphere, information, temptation, opt-in bargains) rather than mob squads that ignore context.

Hard rules: no hostile spawns while a GUI is open or right on top of the player, and per-effect server configs. GTNH's "more varied, less destructive" direction is the evidence-backed path.

### 2. The research grind: fun at first, tedious at scale, worse in TC6
- 4.1's hex puzzle gets repetitive after the first few notes. [ModDex review](https://moddex.gg/mod/thaumcraft/reviews/3649), [ModDex review](https://moddex.gg/mod/thaumcraft/reviews/427)
- In big packs, research takes hours and "Nobody likes it". The GTNH lead dev refused a cheat-sheet quest because research protects recipe gating. [GTNH#5907](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/5907)
- Max-difficulty maps are "not difficult. They are tedious": players spam Instrumentum and Ordo to bridge long gaps. [GTNH#22692](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/22692)
- The community wrote auto-solvers, and GTNH ships its own quality-of-life mod with drag-and-drop combining that combines 10 at a time. [research bot](https://www.mintlify.com/leumasme/thaumcraft4-research-bot) *(snippet)*, [research helper](https://www.modpackindex.com/mod/247/thaumcraft-research-helper) *(snippet)*, [thaumcraft-research-tweaks](https://github.com/GTNewHorizons/thaumcraft-research-tweaks)
- Collecting the aspects is harder than solving the puzzle. Running out of ink "ruins the flow". The per-aspect point cap was "a bit painful". [MineYourMind](https://mineyourmind.net/forum/threads/thaumcraft-research-sucks.32111/), [FTB post](https://forum.feed-the-beast.com/goto/post?id=559246)
- In 4.0, some notes could not be solved and the game gave no warning ("rage-inducing"). [FTB thread](https://forum.feed-the-beast.com/threads/thaumcraft-4-research-not-completing.39240/page-2)
- A server poll on disabling the minigame split 18 for and 25 against, so people want to keep it, just in a less grindy form. [MineYourMind poll](https://mineyourmind.net/forum/threads/disable-hardcore-minigame-driven-thaumcraft-research.18924/)
- TC6's theorycrafting is the most criticised TC6 system. Players say it feels like "making Quests", that later worlds "feel always the same", and that it "lost its fun in combining stuff". Fix mods exist to put TC4 research back. [suggestions#464](https://github.com/Azanor/thaumcraft-suggestions/issues/464), [suggestions#399](https://github.com/Azanor/thaumcraft-suggestions/issues/399), [Thaumic Origins](https://www.curseforge.com/minecraft/customization/thaumic-origins), [TC4 Research Port: Reborn](https://www.curseforge.com/minecraft/mc-mods/oldresearchreborn)

**Implication for our redesign:** keep a puzzle, because the puzzle is what people defend; TC6 shows that replacing it is worse. Then remove the friction around it:
- No consumable ink attrition.
- No puzzles that cannot be solved.
- Difficulty should come from new constraints, not longer paths.
- Combining aspects in bulk is built in.
- Each puzzle should teach something new (a new aspect relation, or a Void twist) so that it does not repeat.

Ship difficulty presets and a pack-maker "purchase/skip" mode as config (TC4's easy mode was praised), with the default set at "medium".

### 3. Taint: wrecks servers, the config is unreliable, the cure comes late; and to others it is toothless
- Taint overran servers ("the CPU was heavily strained"). Some servers removed Thaumcraft entirely, and chunkloaders made it worse. [FTB post](https://forum.feed-the-beast.com/goto/post?id=567155), [FTB post](https://forum.feed-the-beast.com/goto/post?id=448829)
- Fibrous taint kept spreading even with spread set to 0 in the config. [FTB thread](https://forum.feed-the-beast.com/threads/thaumcraft-4-taint-spread-config.131245/), [FTB post](https://forum.feed-the-beast.com/goto/post?id=630591)
- The cures (silverwood, Ethereal Bloom) sit deep in the tree and are often far from retro-generated taint. [FTB post](https://forum.feed-the-beast.com/goto/post?id=460827)
- The opposite complaint also exists: taint "doesn't really seem to do anything", and "it's supposed to be a threat". [FTB 4.1 thread](https://forum.feed-the-beast.com/threads/the-thaumcraft-4-1-experience.41800/), [GTNH#25803](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/25803)
- In TC6, flux was felt as harsh by some players and defended as risk/reward by others. Flux rifts became a meme. [thaumcraft-beta#136](https://github.com/Azanor/thaumcraft-beta/issues/136), [TV Tropes](https://www.tvtropes.org/pmwiki/pmwiki.php/VideoGame/ThaumCraft) *(snippet)*

**Implication for our redesign:**
- Taint must be caused by the player and bounded: no ambient worldgen spread that eats servers.
- It must run on a deterministic per-chunk budget and never be processed in chunks that are only loaded by chunkloaders unless the server enables that.
- Counterplay must be available in the early game.
- Configs must be verified by GameTests (a test that "spread=0 means zero spread").
- It must stay threatening locally, so the "toothless" camp is also satisfied: intense where you caused it, absent where you didn't.

### 4. Performance and TPS across many systems
- Lag sources fixed by community mods include the Arcane Workbench when crafting 64 items, wand recharge, relay checks on energized nodes, the alchemical furnace, and infusion-altar rendering. [TC4Tweaks](https://modrinth.com/mod/tc4tweaks), [CurseForge](https://www.curseforge.com/projects/431297), [altar cull](https://www.modpackindex.com/mod/132378/thaumcraftaltarcull) *(snippet)*
- Essentia tube networks of 100+ pipes caused lag, and someone proposed a 32-pipe cap. [FTB post](https://forum.feed-the-beast.com/goto/post?id=675175)
- Scanning causes FPS drops in packs with large aspect lists. [GTNH wiki tips](https://gtnh.miraheze.org/wiki/Thaumcraft_Tips) *(snippet)*
- TC5's vis drain across loaded chunks could freeze the game. Nodes and TC5's elemental vis are remembered as laggy. [FTB post](https://forum.feed-the-beast.com/goto/post?id=1572035), [thaumcraft-beta#1020](https://github.com/Azanor/thaumcraft-beta/issues/1020)
- TC6 servers report lag from the research table and focal manipulator, and some ban foci. [Aternos](https://board.aternos.org/thread/41947-thaumcraft-6-is-a-laggy-hell-on-1-12-2-help/) *(snippet)*, [Sponge focus ban](https://forums.spongepowered.org/t/thaumcraft-focus-ban-allows-specified-thaumcraft-6-spells-to-be-disabled/33700) *(snippet)*
- Malum shows the cost of rich visuals: performance regressions from shaders and particles. [Malum#420](https://github.com/SammySemicolon/Malum-Mod/issues/420)

**Implication for our redesign:** this confirms the map's "performance budgets per system" item:
- Essentia networks are a cached graph, not per-tile ticking.
- Node and taint ticking is budgeted and time-sliced.
- Particles are client-side with an LOD budget and a config cap.
- Bulk crafting is batched.
- Every system gets a TPS GameTest. Perf is a feature that players notice.

### 5. Golems: much loved, but their AI is unreliable
- Golem AI is "rather stupid". Golems get stuck on the shortest path, and they vanish or die when chunks load ("press my rage buttons"). [FTB post](https://forum.feed-the-beast.com/goto/post?id=675175), [FTB golems disappearing](https://forum.feed-the-beast.com/threads/thaumcraft-4-golems-disappearing.45643/latest)
- TC6 seal golems miscount items and have buggy pathing (2025). [Reddit mirror](https://redlib.ssps.io/r/Thaumcraft/comments/1ka92iu/the_golem_doesnt_supply_stuff) *(snippet)*

**Implication for our redesign:** persistence is non-negotiable (a GameTest that unloads and reloads the chunk). Pathing must be deterministic, golem state must be inspectable (the golem shows what it is trying to do and why it is stuck), and item counting must be exact.

### 6. Hunting nodes and recharging wands wastes time
- Finding nodes is "quite a time waster". One player's nearest Fire node was about 1 km away, inside taint. [FTB "dichotomy"](https://forum.feed-the-beast.com/threads/thaum-4-a-dichotomy.37945/page-3), [ModDex](https://moddex.gg/mod/thaumcraft/reviews/427)
- Recharge workarounds are fiddly (64 nodes around a pedestal, an amulet with very short range). [FTB post](https://forum.feed-the-beast.com/goto/post?id=1099294)
- Server players ask for better ways to share nodes. [GTNH#14766](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/14766)

**Implication for our redesign:** keep nodes (see Loved #2) but make them findable. Options:
- A locator or attunement mechanic.
- A guaranteed spread of primal aspects within a reasonable radius.
- Nodes that can be relocated or shared in multiplayer.

Recharging should be "set up once, then convenient". Exploring to find better nodes is the reward, not a toll.

### 7. Essentia logistics: nerfed for performance, then resented
- The 4.1 tube nerf: "I really hate the new system", "nerfing them to utter uselessness". Others accepted it as a fix for lag. [FTB tubes thread](https://forum.feed-the-beast.com/threads/thaumcraft-4-1-essentia-tubes.41554/)
- In TC6, essentia tubes gate half of the alchemy tree. [suggestions#435](https://github.com/Azanor/thaumcraft-suggestions/issues/435)

**Implication for our redesign:** don't create a performance problem and then fix it with a gameplay nerf. Design the network to be cheap first (see #4) so that range and throughput limits are deliberate gameplay choices, explained in the game.

### 8. The Thaumonomicon and guidebook: hidden content, spoilers, vagueness
- Hidden or eldritch research is hard to discover, and the Eldritch tab needs warp without clearly saying so. [GTNH#554](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/554), [FTB Eldritch Eye thread](https://forum.feed-the-beast.com/threads/thaumcraft-4-2-3-0-eldritch-eye-research-requirements.59877/latest) *(snippet)*
- The book is vague about infusion stabilizers. [FTB post](https://forum.feed-the-beast.com/goto/post?id=598921)
- The TC6 book references taint and vitium everywhere but gives "zero guidance" on finding them, and spoils entries before they are discovered. [suggestions#435](https://github.com/Azanor/thaumcraft-suggestions/issues/435)
- The GUI is small and needs scrolling fixes (TC4Tweaks). [TC4Tweaks](https://modrinth.com/mod/tc4tweaks)
- Blood Magic is criticised for relying on external wikis. [FTB "Best magic mod?"](https://forum.feed-the-beast.com/threads/best-magic-mod.56035/)

**Implication for our redesign:**
- Secrets are fine, but each locked entry gives a diegetic hint about how to unlock it.
- Never mention content the player cannot yet find.
- Give exact numbers wherever the player has to make a decision (stability, cost).
- Use a scalable GUI with search and bookmarks (what Patchouli learned from Lexica Botania, see Loved #7).

### 9. Infusion instability is opaque
- Fans love the chaos ("Explosions and taint and goo = fun!"). Others hit flux spirals that destroy items, and the book is vague. [FTB post](https://forum.feed-the-beast.com/goto/post?id=598921)

**Implication for our redesign:** keep the danger, but show stability before the player commits, make the outcome of a failure predictable, and make most failures cost something other than the main item.

### 10. Abandonment, an unfinished endgame, closed IP
- Azanor stopped in January 2019 for lack of time, with Eldritch and Aurmancy "still heavily WIP". TC6 never left beta, and requests to release the source got no reply. [Minecraft Forum farewell](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1292130-thaumcraft-6-1-beta26-no-longer-being-developed), [thaumcraft-beta#1624](https://github.com/Azanor/thaumcraft-beta/issues/1624)
- The missing TC6 endgame is a common complaint. Thaumic Augmentation (about 8.5M downloads) exists largely to fill it. [ModDex](https://moddex.gg/mod/thaumcraft), [Thaumic Augmentation](https://www.curseforge.com/minecraft/mc-mods/thaumic-augmentation)
- Eidolon (dev "lost interest") and Astral Sorcery (frozen on 1.16.5, with poor communication) show the same pattern. Permissive licences let forks rescue Eidolon, while Azanor's strict IP terms discourage revivals. [Eidolon Repraised](https://modrinth.com/mod/eidolonrepraised), [AstralSorcery#1993](https://github.com/HellFirePvP/AstralSorcery/issues/1993), [suggestions#506](https://github.com/Azanor/thaumcraft-suggestions/issues/506)

**Implication for our redesign:** v1 must ship the endgame, not leave it as "WIP tier". The spec's Eldritch/Outer-Lands item is load-bearing, not optional. The licence and the public API decide whether the mod outlives its author (open question below).

### 11. Gating before any magic, and AFK waiting
- The top "what I want in a magic mod" complaint is having no magic before iron, coal and heavy recipes. [FTB thread](https://forum.feed-the-beast.com/threads/what-would-you-like-to-see-in-a-magic-mod.44115/page-3)
- Repeating the early-game grind in every world wears players out. [FTB "dichotomy"](https://forum.feed-the-beast.com/threads/thaum-4-a-dichotomy.37945/page-3)
- AFK generation is called a "tedious timewaste" (Forbidden & Arcanus). [ModDex](https://moddex.gg/mod/forbidden-arcanus/reviews/1080)

**Implication for our redesign:** the first real magic should come within the first session from vanilla materials (TC4.1's cheaper Thaumometer and starter aspects were praised for this). No progress should depend on passive waiting.

### 12. TC6 removed wands and nodes, and with them the mod's identity
- TC6 is "a step backwards": no wands, no nodes to capture, a gauntlet that can't be upgraded. Some players won't play it for that reason alone. [suggestions#464](https://github.com/Azanor/thaumcraft-suggestions/issues/464), [TV Tropes YMMV](https://tvtropes.org/pmwiki/pmwiki.php/YMMV/Thaumcraft) *(snippet)*
- In 2025 players still ask for TC4's research and nodes back. A 2022 comment claims TC7 brings back nodes and wands (unverified). [suggestions#506](https://github.com/Azanor/thaumcraft-suggestions/issues/506)

**Implication for our redesign:** see Loved #2.

---

## Ranked loved features

### 1. Atmosphere, visual identity and lore
- This is the most consistent praise: TC4 "feels like its own world". Nostalgia packs still advertise it in 2026. [ModDex](https://moddex.gg/mod/thaumcraft), [Thaumcraft Revival pack](https://www.curseforge.com/minecraft/modpacks/thaumcraft-revival)
- TC4 won an FTB "best magic mod" poll (16 of 36 votes). [FTB poll](https://forum.feed-the-beast.com/threads/best-magic-mod.56035/)
- Malum is "the only magic mod that doesn't feel tacky" and has "some of the best visual effects". [ModDex Malum](https://moddex.gg/mod/malum/reviews/3629), [CraftDownUnder](https://craftdownunder.co/guides/mods/malum)
- Players say a magic mod's value is "complex mechanics and awesome aesthetics", not stats. [FTB magic vs tech](https://forum.feed-the-beast.com/threads/magic-vs-tech.263527/)

**Implication for our redesign:** art direction and the Void's tone are the product. The art-pipeline prototype is a critical-path item, not polish.

### 2. Wands and nodes as exploration and identity
- TC4 felt "more open to exploration". Losing wands and nodes is the second most cited TC6 complaint. [suggestions#464](https://github.com/Azanor/thaumcraft-suggestions/issues/464)
- Node bullying and growing super-nodes became a deep player hobby. [FTB post](https://forum.feed-the-beast.com/goto/post?id=1099294)
- TC5's "vis comes to you" setup was liked for speed, but its chunk-vis was laggy. [FTB TC5 thread](https://forum.feed-the-beast.com/threads/thoughts-on-thaumcraft-5-1-8.142845/)

**Implication for our redesign:** keep wands (renamed) with crafted cores and caps, and keep nodes as world objects that can be found, tapped and cultivated. Fix how hard they are to find (Pain #6) instead of abstracting them away. The Void could add a node type or a twist on cultivation.

### 3. Research as discovery, when it is a real puzzle
- The 4.1 hex aspect-linking puzzle is "an actual puzzle" and less frustrating than 4.0. Choosing your next research and the easy (purchase) mode were praised. [FTB post](https://forum.feed-the-beast.com/goto/post?id=559246), [FTB 4.1 thread](https://forum.feed-the-beast.com/threads/the-thaumcraft-4-1-experience.41800/)
- Scanning teaches aspects and makes exploring flavourful ("I don't mind scan craft"). [ModDex](https://moddex.gg/mod/thaumcraft/reviews/3649)

**Implication for our redesign:** keep scanning and a spatial aspect puzzle (see Pain #2 for what to strip out). Make scanning frictionless: scan from the inventory and hotbar, and never require dropping items.

### 4. Golems and logistics that feel magical
- Golems are a highlight of the automation path. [ModDex](https://moddex.gg/mod/thaumcraft)
- TC5's seal system (introduced in TC5, not TC6) let one golem run a farm. [FTB TC5 thread](https://forum.feed-the-beast.com/threads/thoughts-on-thaumcraft-5-1-8.142845/)
- Azanor intended infusion to be automatable ("if I considered it cheating I wouldn't have made them accessible by hoppers"). [FTB post](https://forum.feed-the-beast.com/threads/redundant-items-in-infusion.41754/post-569985)

**Implication for our redesign:** golems are the automation story. Seal-style job markers are a proven UX, and they need the reliability fixes from Pain #5. Allow hopper or pipe automation of altars, curated rather than total (like Ars Nouveau below).

### 5. Danger and risk the player chose
- Players ask for magic to be dangerous ("Not enough danger is implented"), yet hate danger that interrupts unrelated play. [FTB wishlist thread](https://forum.feed-the-beast.com/threads/what-would-you-like-to-see-in-a-magic-mod.44115/page-3), [FTB discovery](https://forum.feed-the-beast.com/threads/thaumcraft-discovery.52767/)
- Infusion chaos is "fun". Warp effects used for PvP pranks are remembered fondly. [FTB post](https://forum.feed-the-beast.com/goto/post?id=598921), [FTB post](https://forum.feed-the-beast.com/goto/post?id=1557692)
- Blightfall turned taint into the premise and is highly rated. [MMC reviews](https://mmcreviews.com/all/modpacks/blightfall) *(snippet)*

**Implication for our redesign:** this is the guiding principle for warp, taint and infusion: danger should be proportional to the player's ambition, local to the player's actions, and legible. The Void is the bargain: power in exchange for exposure.

### 6. Interacting in the world, with a philosophy you can state
- Botania's stated rules are widely cited and loved: no GUIs where possible, in-world interaction, no visible min-max numbers, renewable resources. [Botania (Modrinth)](https://modrinth.com/mod/botania)
- Players accept magic that works like tech if it is in-world and has personality ("Thaumcraft is about 40% magic and 60% tech"). [FTB poll thread](https://forum.feed-the-beast.com/threads/best-magic-mod.56035/)
- Botania's start from stone, wood and flowers makes it the first magic mod people pick up. [FTB poll thread](https://forum.feed-the-beast.com/threads/best-magic-mod.56035/)
- Caveat: Botania's hidden numbers and slow pace put some players off. [ModDex Botania](https://moddex.gg/mod/botania/reviews/466)

**Implication for our redesign:** write down our own short list of design pillars (a candidate ADR). Prefer in-world crafting (crucible, altars) to GUIs. Keep numbers where the player has to make a decision (Pain #8).

### 7. A good in-game guidebook
- Patchouli (about 36M downloads) was built from "what features people liked" in the Lexica Botania: multiblock previews, bookmarks, nested categories. [Patchouli](https://modrinth.com/mod/patchouli)

**Implication for our redesign:** the codex should at least match Patchouli: multiblock previews, search, bookmarks, recipe links (TC4's visual recipe linking was praised, [GTNH#554](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/554)), plus progressive reveal that avoids spoilers.

### 8. Expressive spell crafting
- TC6's focus or casting crafting is "a way nicer one", one of the few TC6 systems that was clearly praised. [ModDex](https://moddex.gg/mod/thaumcraft)
- Hex Casting: "this mod lets me be a wizard", with power that scales with cleverness. [ModDex Hexcasting](https://moddex.gg/mod/hexcasting/reviews/931)
- Ars Nouveau's glyph spellcrafting is praised. [ModDex Ars](https://moddex.gg/mod/ars-nouveau/reviews/4185)
- Caveats: Hex Casting's learning curve is steep, and a crop of addons exists to soften it. Psi's programmer GUI was called "a big mess". [Petra-k FAQ](https://forum.petra-k.at/viewtopic.php?p=74), [HexResearch](https://github.com/dashkal16/HexResearch), [FTB Psi thread](https://forum.feed-the-beast.com/threads/psi-discussion-thread.144282/page-2)

**Implication for our redesign:** foci should be composable (effect + medium + modifiers, in the spirit of TC6), on a GUI that stays simple. Add depth through combinations, not syntax.

### 9. Progression gated by exploration, not grind
- Hex Casting's great spells come from loot-chest scrolls. Astral Sorcery's temple start and "whole experience" progression are praised. [Petra-k FAQ](https://forum.petra-k.at/viewtopic.php?p=74), [FTB magic vs tech](https://forum.feed-the-beast.com/threads/magic-vs-tech.263527/)
- Malum's spirit harvesting gives a reason to explore and ties the mod to the vanilla world. [ModDex Malum](https://moddex.gg/mod/malum/reviews/3629)

**Implication for our redesign:** gate tiers with things found in the world (Void-touched ruins, eldritch fragments, node types) rather than point counts.

### 10. Configurability for pack makers
- Easy research, warp toggles and research skips ("Wuss Mode") are used heavily. GTNH maintains a whole suite of tweaks for TC4. [ThaumicTweaker](https://www.curseforge.com/minecraft/mc-mods/thaumictweaker), [GTNH wiki](https://wiki.gtnewhorizons.com/wiki/Thaumcraft), [GTNH repos](https://github.com/orgs/GTNewHorizons/repositories?q=thaum)

**Implication for our redesign:** expose first-class, tested configs and datapack hooks for research difficulty, warp effects, taint spread and recipe gating. Pack makers will otherwise write a fix mod.

---

## Successor scorecard (summary)

| Mod | Widely praised | Widely disliked |
|---|---|---|
| TC5 | Vis comes to the player and charges the hotbar; seals introduced | Chunk-vis lag. Little attention overall; *not* evidently hated ([FTB](https://forum.feed-the-beast.com/threads/thoughts-on-thaumcraft-5-1-8.142845/)) |
| TC6 | Focus/casting crafting | Theorycrafting research, losing wands and nodes, the gauntlet, unfinished endgame, beta forever ([suggestions#464](https://github.com/Azanor/thaumcraft-suggestions/issues/464)) |
| Thaumic Augmentation | Restores the eldritch endgame and gauntlet upgrades ([CF](https://www.curseforge.com/minecraft/mc-mods/thaumic-augmentation)) | (no strong negatives found) |
| Botania | Design philosophy, in-world play, Lexica, Garden of Glass | Slow, hidden numbers, "tech in disguise" |
| Ars Nouveau | Glyphs, curated automation by familiars | Hard to connect to other mods' automation ([ModDex](https://moddex.gg/mod/ars-nouveau/reviews/121)) |
| Hex Casting | "Be a wizard", gated by exploration | Steep curve, punishing mishaps |
| Malum | Visuals, feels part of the world | Performance cost of shaders and particles |
| Eidolon / Astral | Atmosphere, progression by experience | Abandoned; poor communication from devs |
| Blood Magic | Balance philosophy (counters for everything) | Unintuitive, depends on external wikis |
| Embers, Occultism, Witchery, AM2 | Embers' graphics; Witchery's natural-ingredient magic | Embers unfinished; Witchery abandoned. **Under-sampled.** |

## Surprises

- **TC5 was not the villain.** I found little evidence that it was widely hated: it got little attention, and what it got was mixed to positive. Seal golems date from TC5. The hate is concentrated on TC6.
- **Complaints about taint point both ways:** "wrecks servers" and "toothless". The fix is a bounded threat that bites locally, not a different dial setting.
- **The TC4 audience is still active in 2026.** GTNH maintains forks of TC4 addons, and TC4-research-back-ported-to-TC6 mods were still being updated in October 2026.
- **There is no public design rationale from Azanor** for the TC6 redesigns. We cannot learn "why" from him, only from how players reacted.
- **Clean-room hazard:** existing TC4 "ports" (Thaumcraft 4 Unbound, which says its code matches the decompiled source and whose CurseForge page now 404s; Thaumic Reborn for 1.20.1) must not be used as references. [TC4 Unbound](https://www.curseforge.com/minecraft/mc-mods/thaumcraft-4-unbound) *(snippet)*, [Thaumic Reborn](https://www.modpackindex.com/mod/172362/thaumic-reborn) *(snippet)*

## Open questions for the map

1. **Warp as the spine versus warp as the most-hated mechanic.** This needs a grilling ticket: how much is permanent, what reduces it, and what the Voices actually do.
2. **The licence** (permissive versus all-rights-reserved). The evidence shows closed IP blocking revivals of abandoned mods.
3. **The default research difficulty, and whether a skip mode ships in v1.** Pack makers want one, while GTNH's lead wants research to protect gating, so both should be configurable.
4. **A follow-up pass on Reddit and YouTube** if access becomes available, especially for TC6-era and modern-mod sentiment, and for Occultism, Embers and Witchery.
