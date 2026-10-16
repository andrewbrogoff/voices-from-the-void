# Core fantasy and progression arc

The backbone that every system spec hangs off. Terms are defined in [`CONTEXT.md`](../../CONTEXT.md), and the fiction behind them is in the [lore spec](lore.md).

Resolved in [Core fantasy and progression arc](https://github.com/andrewbrogoff/voices-from-the-void/issues/10).

## Core fantasy

> A natural philosopher masters the aspects of a resonant world, and in doing so learns to hear the silence beneath it, and what lives there.

You start with mastery, and the Void is where mastery leads. Every system spec should be checkable against this sentence.

## Stages

Progression runs through four **Stages**. You enter each one by **finding something in the world** (a ruin, a page, a node type), never by reaching a point total. Each Stage follows the lore's tone ramp.

| Stage | Tone | What opens |
|---|---|---|
| 1. **Resonance** | occult natural philosophy | Tuning Fork, aspects and scanning, Commonplace Book, first wand, hand-tapping nodes, arcane crafting |
| 2. **Distillation** | occult natural philosophy, turning to wonder | Crucible, essentia and its logistics, Resonant Brass, cultivating nodes, vis relays, first golems |
| 3. **Ruins** | melancholy wonder | The Vacuos revelation, Choir ruins and pages, the Answerers, infusion, warp beyond small temporary doses |
| 4. **The Choir's Road** | cosmic horror | Void-tier crafting and gear, the journey to where the Choir went, Joining or Silencing |

Each system spec decides which Stage its content belongs to, and what find opens that Stage. Being research, the gates are per player.

### The first hour

The first real magic comes from vanilla materials, before iron, within the first session of a new world.

- **Tuning Fork:** a copper ingot and an amethyst shard. Vanilla amethyst already chimes.
- **Commonplace Book:** strike a plain vanilla book with the Tuning Fork (a blank Book and Quill works too). Leather and paper, so no squid hunt.
- **First node:** the Tuning Fork hums toward the nearest node, so finding one is a short walk.
- **First arcane craft:** within the first session.

The gate into Stage 1 is the amethyst geode, so it is also a find. No step depends on passive waiting.

## Warp

Warp is how audible the Void finds you. It has three roles at once:

1. **Key:** warp thresholds unlock Void-tier research and crafting.
2. **Amplifier:** Void-tier gear and foci grow stronger the more current warp you have.
3. **Price:** it always costs something, scaling with warp.
   - **Reality slips:** perception effects grow stronger as warp rises (below).
   - **Ordinary magic suffers:** the more you sound like the Void, the more your resonant (non-Void) magic costs in vis and spills in flux.

So warp is a **dial**: Void power on one side, ordinary craft and a reliable world on the other. Reducing warp is a real trade, because going quiet also weakens your Void tools.

The dial turns both ways. From Stage 3 (after the Vacuos revelation), the **Listen rite** is a deliberate, repeatable listen at a Void-touched place: it costs lasting warp, shown before you commit, has a cooldown, and draws the Voices' attention at once. A Listener who has stilled to nothing can always pay to be heard again. Silenced Listeners can't perform it. The warp spec owns its surface and numbers.

### Kinds

| Kind | Behaviour |
|---|---|
| Temporary | Fades on its own within minutes. |
| Lasting | Stays until treated. Treatment always works and always costs something. |
| Permanent | Gained only from a few named milestone choices, each showing its exact cost before you commit. The total has a small designed cap. Never removable. |
| Gear | Counts only while the item is carried. |

### What the Voices do

- **Anywhere:** whispers, perception effects (see "Reality slipping"), and **Bargains**: offers of power for more warp, which you can always refuse at no cost.
- **Physical danger only in context:** it happens only while you're doing Void-tier work or standing in Void-touched places.
- **Hard rules:**
  - Nothing fires while a GUI is open, and nothing hostile spawns on top of you.
  - Events are rationed by a counter that rises when you gain warp, perform the Listen rite or do Void-tier work, and falls as events fire. Void-tier work feeds only the counter, never warp itself. A player who stops gaining warp settles to a faint background set by their permanent warp, never to zero.
  - Every effect has its own server config, verified by GameTests.

### Reality slipping

**Your senses lie; instruments never do.**

- **May lie:** phantom sounds, apparitions that vanish when touched, colour shifts, notebook prose and marginalia.
- **Never lie:** Tuning Fork readouts, costs, infusion stability, health, warp itself, and every other number a decision rests on. This extends the Commonplace Book guardrail in the lore spec.
- **Phantoms** are client-side and seen only by the player who has the warp. They never deal damage.

The numbers (thresholds, rates, scaling, cures, the permanent cap) belong to the warp spec.

## Endgame

Stage 4 leads to **where the Choir went**, which is a dimension. There are fights along the way (the Answerers and guardians), but the end is a **choice**, not a boss:

- **Joining:** answer the Choir and go in after them. You gain lasting Void power and permanent warp.
- **Silencing:** end the Choir's voice for yourself. Temporary and lasting warp are removed, permanent warp stays, and a different capstone opens.

Both endings change how you play afterwards. The choice is **per player and final**: one player's choice never decides it for another, and it can't be undone in that world. This is how the lore's deliberately open "doom or release?" gets answered: by each player.

**Existing worlds:** a mod's datapack dimension appears in worlds created before the mod was added (1.19.3 onward, unchanged through 26.x). Players have to reopen the world after installing the mod. Removing the mod leaves stale dimension entries behind; the [Eldritch tier spec](eldritch.md#existing-worlds-and-removing-the-mod) handles them. Sources: [Minecraft wiki: adding a dimension](https://minecraft.wiki/w/Tutorial:Adding_a_new_dimension), [MC-227131](https://bugs.mojang.com/browse/MC-227131), [fabric-api#2856](https://github.com/FabricMC/fabric-api/pull/2856).

## Research difficulty

- The hex puzzle stays. It never uses ink and never generates an unsolvable note.
- **Presets:** gentle, standard and hard (the default is standard), plus a **skip mode** where research is bought with aspect points. All of them are server config with GameTest coverage, and all ship in v1.
- **World-find gates are separate from the puzzle.** Skip mode skips the puzzle, never the exploration, so research still guards progression in curated packs.

## Vis

**One economy, one unit (vis).**

- **Stage 1:** wands are filled by tapping nodes by hand.
- **Stage 2:** relays carry vis from your nodes to charging points. This is the same economy made convenient ("set up once"), not a second currency.
- Nodes stay world objects to find, tap and cultivate. They can be located (the Tuning Fork), moved and shared.

## TC4 systems: kept, redesigned, cut

| System | Verdict |
|---|---|
| Scanning, research puzzle, Commonplace Book | **Keep.** No ink attrition, no per-aspect point caps, no unsolvable notes. |
| Lost-knowledge fragments | **Cut.** Choir pages replace them. |
| Wands (rods and caps), nodes | **Keep.** Nodes become findable, movable and shareable. |
| Two vis economies | **Merged** (see "Vis"). |
| Arcane crafting, crucible, essentia and tubes, infusion | **Keep.** Tube networks are cheap by design, so any limits are gameplay choices. Infusion stability is shown before you commit. |
| Foci | **Redesign** as composable (effect + medium + modifiers) on a simple GUI. |
| Golems | **Keep**, with mark-based jobs, survival across chunk reloads, and inspectable state. |
| Armor and trinkets, runic shielding | **Keep.** Trinkets use Curios when it is installed, and the off hand otherwise ([armor spec, Curios](armor.md#curios)). Runic shielding is renamed the Baffle. |
| Warp | **Redesign** (see "Warp"). |
| Flux and taint → the Hush | **Redesign:** caused only by the player, bounded, with counterplay available early ([Hush spec](hush.md)). |
| TC4's Sanity Checker (warp gauge) | **Cut.** The Commonplace Book shows exact warp. |
| Traveling Trunk | **Cut** from v1. |
| Eldritch dimension and bosses | **Keep**, reshaped as in "Endgame". |

## Answers to the top community pain points

From [Community pain points with TC4 and its successors](https://github.com/andrewbrogoff/voices-from-the-void/issues/5):

| Pain point | Answer |
|---|---|
| 1. Permanent, context-blind warp | Warp is a chosen dial. Permanent warp is small, capped and announced. Physical danger only comes in context, and nothing ever happens while a GUI is open. |
| 2. Research grind | No ink, no unsolvable notes, presets plus a skip mode, gates come from finds. |
| 3. Taint wrecks servers, or is toothless | The Hush is caused by the player and bounded, intense locally, with early counterplay. |
| 4. Performance | Per-system budgets inside one whole-mod envelope ([quality gates, Performance](quality-gates.md#performance)). Networks are designed to be cheap from the start. |
| 5. Unreliable golems | Survival across chunk reloads, deterministic pathing, inspectable state. |
| 6. Node hunting and recharging | The Tuning Fork locates nodes. Relays make recharging "set up once". |
| 7. Essentia nerfed for performance | Cheap by design, so limits are deliberate. |
| 8. Opaque guidebook | Locked entries hint at how to unlock them, nothing is spoiled, decision numbers are exact. |
| 9. Opaque infusion | Stability is shown before you commit. |
| 10. Unfinished endgame | Stage 4 and Joining/Silencing ship in v1. |
| 11. No magic before iron, AFK waiting | The first hour runs on copper and amethyst. No passive waiting. |
| 12. TC6 lost wands and nodes | Both kept, with nodes as an exploration reward. |
