# Research and the Commonplace Book

How a Listener turns what they have heard into knowledge: the hex puzzle (a **note**), the presets and skip mode, research entries and their triggers, the four Stage gates, Choir pages, and the Commonplace Book itself, whose prose degrades with warp while its numbers stay exact. Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). The code layout, data rules and sync rules come from the [architecture spec](architecture.md), every gate from the [quality gates](quality-gates.md), the aspects, knowledge and points from the [aspects spec](aspects.md), and every asset from the [art style](art-style.md).

Resolved in [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16). Inputs: [TC4 core loop](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-core/docs/research/tc4-core.md), [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4. Each tunable one is listed with its data home under "Tuning". Numbers inside the JSON examples are illustrative; the content tickets set the real ones.

## Stage

The Commonplace Book and the puzzle belong to **Stage 1, Resonance**, and research entries exist in **every Stage**. Each Stage is opened by exactly one **Stage opener**: a `find` entry with `opens_stage`, whose trigger is a hidden advancement satisfiable with only NeoForge installed.

| Stage | Opener entry | Find (advancement) | Advancement owned by |
|---|---|---|---|
| 1 Resonance | `vftv:resonance` | `vftv:find/resonance`: `inventory_changed` with an amethyst shard **or** a Tuning Fork | this spec |
| 2 Distillation | `vftv:distillation` | `vftv:find/distillation` | [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27) |
| 3 Ruins | `vftv:ruins` | `vftv:find/ruins` | [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27) |
| 4 The Choir's Road | `vftv:choirs_road` | `vftv:find/choirs_road` | [Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) |

- **Obtaining counts as finding.** Stage 1 is recorded the first time a player holds a shard or a fork, however they got it.
- **Openers latch before visibility.** An opener's find is recorded the moment it fires, even though the opener's own entry is hidden until its Stage opens. Stages then advance strictly in order (see "Stage gates").
- **A reachable next-find hint** is always in the current Stage: each opener of Stage 2–4 names, in `hinted_by`, a non-secret entry of the Stage before it, whose prose points at the find.

## The puzzle

### Notes

A **note** is a puzzle in progress, kept as per-player state inside the book. There is no table, paper or ink. Ink attrition was among the top complaints about the old puzzle, and the first arcane craft must fit in the first session.

- A note is a hexagonal grid of **cells**. Some cells hold **anchors** (aspects printed before work begins), and on hard some are **silent** (no aspect can be placed there).
- The Listener **places** known aspects in empty cells and **removes** their own placements.
- **Solved** iff every anchor lies in one connected component of the link graph over occupied cells (anchors plus placements). Branching is allowed. A placement with no linked occupied neighbour is allowed and drawn dimmed, as **unanswered**.
- The server re-runs the solve check after every placement and removal: a BFS over at most 169 cells.
- **Solving never learns by itself.** A solved note shows **Learn**, so the Listener can first remove strays they don't want to pay for.

### Links

Two neighbouring occupied cells **link** iff, in the player's view, one aspect is a parent of the other. A cell never links to the same aspect.

- **Taught view** (before the player's reveal): every compound links to its two parents, and Vacuos links to its taught parents, Aer and Perditio.
- **After the reveal:** the taught links all stay, and each hidden primal whose `revealed_by` entry the player has learned also links to **every exposed primal**. An exposed primal is an aspect with `parents: []` and no `hidden_primal`.
- **A note's links** are its snapshot (below) **united with** the player's current view, so links only ever grow. A note begun before the reveal stays valid after it.
- **No physical decomposition is implied.** Puzzle adjacency is a notebook convention; it never substitutes for the true graph, which the crucible and the world follow ([aspects spec, Vacuos](aspects.md#vacuos)).

### Grid geometry

- **Coordinates:** axial `(q, r)`, pointy-top. Hex distance is `(|Δq| + |Δr| + |Δq + Δr|) / 2`. The grid of radius *R* is every cell within distance *R* of `(0, 0)`: `3R(R + 1) + 1` cells (19 at 2, 37 at 3, 61 at 4, 169 at 7).
- **Neighbour order** `D0…D5`: `(+1, 0)` E, `(+1, −1)` NE, `(0, −1)` NW, `(−1, 0)` W, `(−1, +1)` SW, `(0, +1)` SE. Every scan over neighbours uses this order.
- **Spiral order** (index 0 to `3R(R+1)`): index 0 is `(0, 0)`. Ring *k* = 1…*R* starts at `(−k, 1)`, steps `D5` (*k* − 1) times to the corner `(−k, k)`, then steps `D0`, `D1`, `D2`, `D3`, `D4` *k* times each, ending at `(−k, 0)`. The next ring starts one `D4` step away, so **every two consecutive spiral cells are neighbours**. For *R* = 2 the order is `(0,0) (−1,1) (0,1) (1,0) (1,−1) (0,−1) (−1,0) (−2,1) (−2,2) (−1,2) (0,2) (1,1) (2,0) (2,−1) (2,−2) (1,−2) (0,−2) (−1,−1) (−2,0)`.
- **Symmetries** `t` = 0…11: if `t ≥ 6`, reflect `(q, r) → (r, q)`; then rotate `(t mod 6)` times by `(q, r) → (−r, q + r)`. All twelve preserve the grid, adjacency and spiral contiguity.
- **Screen position** of a cell: `x = 18q + 9r`, `y = 15r` logical pixels from the grid centre (see "GUI contract").

### Presets and modes

Server config **`research.mode`** = `gentle | standard | hard | skip` (one enum, default `standard`). Difficulty comes from constraints, not from longer chains.

| Per preset *(tuning)* | gentle | standard | hard |
|---|---|---|---|
| `grid.base_radius` | 2 | 3 | 3 |
| `anchors.max_used` (the entry's first *n* anchors) | 3 | 5 | 5 |
| `anchors.min_distance` (hex steps between any two anchors) | 2 | 3 | 4 |
| `silent_cells` | 0 | 0 | 5 |
| `palette_help` | `neighbours`: hovering or focusing a cell highlights palette aspects that would link to an occupied neighbour | `parents`: hovering a palette aspect shows its parents in the player's view | `parents` |

- **A note snapshots its preset** as resolved numbers (below). Changing the mode never alters an open note; new notes use the new mode.
- **In `skip` mode** no new note can be begun. Open notes keep their preset and can still be solved, learned or abandoned, and every eligible note entry shows **Buy** (see "Skip mode").

### Generating a note

Generation is a pure function in `core.research.model`: (seed, anchors, known aspects, link set, resolved preset) → note. It is constructive: it plants a solution first and shows only the anchors, so **a note can never be unsolvable**, with a bounded number of steps.

**Precondition to begin** (otherwise the entry is LOCKED and shows the mechanical hint): the entry's first *n* anchors, with *n* = min(anchor count, `anchors.max_used`), are all known, and each consecutive pair is connected in the player's **known-link graph** (known aspects only, links by the player's current view).

**Seed.** One `long`, from the SplitMix64 finaliser folded over 64-bit words, all arithmetic wrapping:

```
mix64(z): z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L
          z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL
          return z ^ (z >>> 31)
fold(words): h = 0; for w in words: h = mix64((h + 0x9E3779B97F4A7C15L) ^ w); return h
idWords(id): the UTF-8 bytes of id.toString() ("ns:path"), cut into 8-byte big-endian words,
             the last zero-padded on the right, followed by one word holding the byte length
seed = fold([0x4E4F5445 ("NOTE"), server.overworld().getSeed(), uuid.getMostSignificantBits(),
             uuid.getLeastSignificantBits(), attempt] ++ idWords(entry id))
```

- **`attempt`** is the player's attempt counter for that entry: 0 at first, **+1 on every abandon** (by the player, by a refund at login, or by a skip purchase that closed the note). A refused `begin` leaves it unchanged; `reset` clears it.
- **RNG:** one `java.util.Random(seed)` per `begin`, whose algorithm its Javadoc fixes. Every draw is `nextInt(bound)`, **even when `bound` is 1**. Generation attempts continue the same stream.
- **Sorting:** aspect ids sort by `ResourceLocation.toString()` with `String.compareTo`.

**Vectors** (computed by a reference implementation and cross-checked against the JDK; world seed `0x0123456789ABCDEF`, UUID `00112233-4455-6677-8899-aabbccddeeff`):

| Entry, attempt | Seed | First four `nextInt(37)` |
|---|---|---|
| `vftv:taking_notes`, 0 | `0xF206817A7B0403F2` | 23, 4, 22, 32 |
| `vftv:taking_notes`, 1 | `0x9782C35A1BA5AE07` | 30, 31, 17, 6 |
| `vftv:first_wand`, 0 | `0x234A7342996DEC4D` | 18, 16, 28, 15 |

Full-note vectors (three notes per preset, plus one forced fallback per preset) are **build-time fixtures**: the build writes them from the `model` implementation, and an independent test re-derives each one by following the steps below.

**Steps:**
1. **Chain.** For each consecutive anchor pair, find the shortest path in the known-link graph by BFS from the first anchor. Each node's neighbours are visited in ascending id order, a node's predecessor is set when it is first discovered, and the path is read back from the second anchor. Joining the paths (sharing the anchors) gives the **unpadded chain**, of length *L*.
2. **Padding.** A segment `[x0, x1, …, xm]` with `m < anchors.min_distance` has the pair `[x1, x0]` inserted right after `x0`, repeatedly, until `m ≥ anchors.min_distance` (so `A–B` becomes `A–B–A–B`). The **padded chain** has length *Lp*.
3. **Radius.** `R_base = min(7, grid.base_radius + (n ≥ grid.anchor_bonus_at ? 1 : 0))`. `R_walk` = the smallest *R* ≥ `R_base` with `cells(R) ≥ Lp + silent_cells`. If `R_walk` > 7, skip straight to the fallback.
4. **Walk**, up to `generate.max_attempts` times:
   1. Start: `spiral[nextInt(cells(R_walk))]` holds anchor 0.
   2. For each padded segment, for each step *j* = 1…*m*: the candidates are the neighbours of the current cell, in `D0…D5` order, that are inside the grid, unused, and satisfy `distance(candidate, segment start) + (m − j) ≥ anchors.min_distance`. On the last step (*j* = *m*, which places the next anchor) a candidate must also be at least `anchors.min_distance` from **every** anchor already placed. If there are no candidates, the attempt fails. Otherwise take `candidates[nextInt(size)]` and place `x_j` there.
   3. On success, draw the silent cells. List the grid's unused cells in spiral order, then `silent_cells` times take `free.remove(nextInt(free.size()))`.
5. **Fallback** (every walk attempt failed, or *Lp* didn't fit): `R_fb` = the smallest *R* ≥ `R_base` with `cells(R) ≥ L + silent_cells`. If `R_fb` > 7, **refuse** (below). Otherwise draw `t = nextInt(12)`, lay the **unpadded** chain on the first *L* cells of the spiral transformed by symmetry *t*, and draw the silent cells as in step 4.3. Consecutive spiral cells are neighbours, so every consecutive chain pair links. Extra adjacencies can only add links. The fallback may break `anchors.min_distance`, which is accepted and logged at debug.
6. **Verify.** The plant (anchors plus witness placements) must pass the solve check, with silent cells disjoint from it, before the note is issued. A walk plant that fails is logged as an error and goes to the fallback. A fallback that fails is refused.
7. **Issue.** The note stores its anchors (cell and aspect), its silent cells and its radius. The witness is discarded.

**Refusal:** `begin` changes nothing, the player sees "This note will not lie flat in my book.", and an error naming the entry is logged.

**Built-ins can never be refused.** The proof uses the player's **known-link graph**, not the full graph. With all aspects known, the longest shortest path is only 5 steps in both views (checked by script), but a partly known graph can force longer detours. For example, with the six exposed primals plus Metallum, Instrumentum, Sonus and Vacuos known, the taught path from Ignis to Perditio runs Ignis–Metallum–Instrumentum–Ordo–Sonus–Aer–Vacuos–Perditio, 7 steps. Anchors `[Ignis, Perditio, Metallum, Vacuos, Instrumentum]` then give segments of 7, 6, 5 and 4 steps: an unpadded chain of **23** cells. The valid bound comes from the vertex count instead. The built-in registry has 37 aspects, so a shortest path has at most 36 steps per segment, and a 5-anchor chain is at most 1 + 4 × 36 = **145** cells. Even at the maximum of 12 silent cells that is 157 ≤ 169, so the fallback always fits at radius ≤ 7. A unit test re-proves this bound on the shipped data and keeps the 23-cell case as a regression. Only an addon registry or note too large for 169 cells can be refused, and world open logs a warning for any note whose full-knowledge chain would already be too large.

**Expected behaviour** (reference run at **full knowledge and default tuning**, 3,000 random 2–5-anchor notes per preset on the built-in graph; partly known graphs give longer chains): gentle and standard never fell back, and hard fell back about 1% of the time. Walks needed about 1.2, 2 and 4 attempts on average. The grids had radius 2 (gentle) or 3–4 (standard and hard).

### Paying for placements

Placements **reserve** points; only learning spends them. No in-puzzle action is irreversible.

- **Place** costs the note's snapshot `place_cost` of that aspect.
- **Debit plan,** worked out in full before anything is applied: `plan(a, k)` takes min(*k*, the pool's balance of *a* minus what this plan has already taken) from the pool. For any remainder *r*, it calls `plan(p, r × combine.parent_cost)` for each **synthesis parent** *p* of *a*, in ascending id order, depth first. Partial compound balances are therefore used before anything is synthesised.
- **Synthesis parents** are the taught parents, **even after the reveal**, so Vacuos always synthesises from Aer + Perditio. Exposed primals have none. A note's debit plans read the **synthesis-parent map in its snapshot**, never the current registry; a skip purchase reads the current taught view. Synthesis recurses at most 3 levels (fixed in code, matching the aspect depth limit); anything deeper counts as short.
- **Failure debits nothing.** "Not enough X" names the first short leaf in plan order.
- **Receipt:** each placement stores its **actual debit multiset** (`{aspect: n}`, at most 15 entries). Removing a placement refunds its own receipt exactly. Abandoning refunds every receipt, including on a solved note. **Learning destroys all receipts**, strays included; the Learn button shows the exact total first.
- **Invariant** (tested): pool + held is constant through every place, remove and abandon; only learn and buy reduce it.
- Points move between the knowledge and research attachments on the server thread within one action, so a save cannot split them.

### Snapshots and reopening

A note snapshots everything that could change under it:
- the preset numbers (`min_distance`, `silent_cells`, `palette_help`);
- `place_cost` and `combine.parent_cost`;
- its radius, anchors and silent cells;
- the **link pairs** of the player's view at `begin` (all pairs between registry aspects, at most 4,096);
- the **taught synthesis-parent map** (each registry aspect's taught parents, at most 4,096 entries).

Data, config or tuning changes therefore never invalidate an open note.

**Existing notes are not re-gated.** Access to an open note is separate from eligibility to begin one. While the note exists, `place`, `remove` and `abandon` work from its snapshot alone, and `learn` needs only the note to be solved under the snapshot ∪ current-view links. None of them rechecks the begin precondition, the entry's current parents or its kind requirement. An entry with an open note opens to its note even if new data or knowledge has made it LOCKED. If the entry is HIDDEN (its Stage was lowered, or a secret's data changed), its note stays reachable from its row in the Ledger.

**One note per entry.** A `begin` for an entry that already has an open note is refused ("That note is already open in my book."). Whenever an entry with an open note becomes learned **by any other path** (an admin `grant`, or anything other than that note's own `learn` or `buy`), the note is abandoned with a full refund in the same action, and the attempt counter rises.

**Reconciliation** runs at login after a world is reopened with changed data:

| Change | Effect |
|---|---|
| A note's entry is gone, is no longer a `note`, or is already learned | The note is abandoned with a full refund and the attempt counter rises. |
| A placement or anchor names an aspect that is gone | The placement is removed and its receipt refunded, minus any removed aspect, which is logged. A note with a missing anchor is abandoned. |
| An entry's anchors, parents, category or the aspect graph changed | The open note keeps its snapshot, stays usable (see "Existing notes are not re-gated" above), and learning it learns the entry. The entry's state is recomputed from the new data. |
| A learned, latched or revealed id names an entry that is gone | It is kept dormant, so re-adding the pack restores it. |
| A bookmark names an entry that is gone | It is dropped. |
| Any change | Rewards are never paid twice (see "Rewards"). |

### Skip mode

Research bought with aspect points. Skip mode skips the puzzle, never the exploration: finds, Stage gates, secrets and parents still apply.

- **Price:** the entry's `skip_cost` if authored, otherwise per aspect `ceil(skip.multiplier × reference[a])`.
- **Reference vector:** the unpadded chain over **all authored anchors**, in data order, in the **taught view with every aspect known**, laid on the canonical spiral (no RNG, no routing). It is `place_cost` × the count of each aspect in the non-anchor cells. It is identical for every player, recomputed when tuning reloads, and sent to the client as a quote. A note entry whose reference vector is empty is rejected at load.
- **Eligibility,** separate from puzzle connectivity: mode is `skip`, the entry is a `note` that is not HIDDEN, its `parents` and `hidden_parents` are learned, and every aspect in its price is known.
- **Payment** runs the debit plan for the whole price against the pool **plus that note's own receipts**, if it is open. All or nothing: the receipts are refunded, the price debited and the entry learned in one action.
- **Commit:** `buy` echoes the price, and the warp if the entry carries any. The server rechecks the mode, eligibility, price and #25's warp preview at commit. A mismatch refuses with "The cost has changed." and sends fresh quotes. Cancelling the confirm spends nothing.

### Affordability and recovery

**Recovery has three layers, so no player can strand themselves:**
1. **Refunds:** no in-puzzle action is irreversible ("Paying for placements").
2. **Renewable primals in Stage 1:** the node types that generate in Stage 1 together carry all 6 exposed primals, and a node's first strike pays per player per node, so exploring always yields more (contract on #17).
3. **Synthesis** turns primals into any compound, including Vacuos from Aer + Perditio, with no circular dependency.

The one remaining "mistake" is a skip purchase, which shows its exact price first.

**Affordability is a prefix proof, not an end-of-Stage total.** Each Stage content ticket authors an **affordability path**, `content/research/path-stage<N>.json` (schema under "Affordability paths"). It has two parts:
- **reviewed sources**: what the world guarantees in that Stage, each with a quantity and its prerequisites;
- **ordered steps**: evidence and commits.

A build-phase unit test replays every path with the shipped data, **separately for each preset and for skip**. Stage 1 starts from an empty ledger, and each later Stage starts from the previous Stage's end state **in the same mode**.

- **Every step runs the real rules:** normal eligibility, the aspects spec's payouts and discovery, and the full resolution. Nothing is credited by fiat.
- **A `use` step** spends one unit of a reviewed source, and only once the source's `requires` (research learned and aspects known) already hold. What happens depends on the source:
  - `scan`: the aspects payout for that key;
  - `node`: a first strike on a **fresh node identity** (`<source id>#<n>`), paying the aspects spec's real node reward formula on #17's declared composition and capacity;
  - `advancement`: grants that advancement, so its triggers fire;
  - `choir_page`: a tip-in under the ordinary tip-in rules, with the page claimed from its named site.
- **A `learn` step** may only name a `note` entry that is eligible at that point. **A find can never be learned by a step**; it is learned only through its evidence.
  - In a preset replay the note needs, before it is debited, the padded chain's placements for that preset over the **current simulated known-link graph**, × `affordability.margin` and rounded up per aspect, payable through the debit plan. Then it debits ×1.0.
  - In the skip replay it is bought at its price ×1.0.
- **A `listen` step** is an explicit listen commit on an eligible `listen` entry, applying its warp to a simulated warp ledger, so `warp_at_least` triggers fire as in play.
- Only steps **before** a spend count toward it. Every `note` and `listen` entry of the Stage must be committed in the path, and the replay must end with the next Stage's opener latched (Stages 1–3).

## Entries

### Registry format

Two **synced datapack registries**, fixed when a world opens, like the aspect set ([ADR 0003](../adr/0003-aspects-as-datapack-registry.md)): **`vftv:research`** (`data/<ns>/vftv/research/<path>.json`) and **`vftv:research_category`**. Content authors reopen the world to change them; the tuning file follows `/reload`. Datagen writes the instances from authored inputs (see "Authored content").

| Field | Required | Meaning |
|---|---|---|
| `stage` | yes | 1–4 |
| `category` | yes | a `vftv:research_category` id |
| `position` | yes | `[x, y]`, integers −16…16, unique within the category |
| `icon` | yes | exactly one of `{"item": <item id>}` or `{"sprite": <GUI sprite id>}` |
| `kind` | yes | `free` (learned as soon as it is OPEN), `find` (learned by a trigger), `note` (the puzzle, or bought in skip mode), `listen` (an explicit commit; see "Warp on entries") |
| `parents` | no, default `[]` | entries that must be learned; drawn as lines when in the same category |
| `hidden_parents` | no, default `[]` | must be learned; never drawn |
| `anchors` | `note` only | 2–5 **distinct** aspect ids |
| `skip_cost` | no, `note` only | `{aspect: 1…4096}` |
| `triggers` | `find` only, 1–16 | typed triggers; **any one** suffices |
| `secret` | no, default `false` | HIDDEN until revealed |
| `reveal` | iff `secret`, 1–16 | typed triggers; any one reveals |
| `hinted_by` | if `secret`, or an opener of Stage 2–4 | a **non-secret** entry of the same or an earlier Stage, whose prose hints at this one |
| `opens_stage` | no, `find` only | 1–4 |
| `warp` | no, `note`/`listen` only | `{"temporary", "lasting", "permanent"}`, each 0–100, default 0; applied on commit |
| `rewards` | no | `{"points": {aspect: 1…256}}`, paid once, on first learning |
| `pages` | yes, 1–32 | typed page descriptors (see "Page descriptors") |

**Categories** hold `stage` (1–4, or `null` for the Choir pages, which use each page's own Stage), `icon` (as above) and `order` (an integer that sets tab order). Built-ins: `vftv:resonance`, `vftv:distillation`, `vftv:ruins`, `vftv:choirs_road` and `vftv:choir_pages`. The **Ledger** tab is code, not data.

**Required built-in keys** (`ResearchKeys` in `api`): the four openers; `vftv:commonplace_book`; `vftv:silence_beneath` (Vacuos's `revealed_by`); `vftv:choir/beneath_the_notes`; and the five categories.

**Load validation** runs when the world opens, on the full registry, and **refuses the world with a clear error** naming the entry. A GameTest covers each rule, but a pack user's world is protected by the runtime check itself.

| Rule | |
|---|---|
| Every id in `parents`, `hidden_parents`, `hinted_by`, triggers and `revealed_by` exists | refuse |
| No cycle across `parents` ∪ `hidden_parents`, and no parent in a later Stage than its child | refuse |
| `anchors` present only on notes, 2–5 distinct known registry aspects; reference vector non-empty | refuse |
| `warp` only on `note`/`listen`; `triggers` (≥ 1) only on and required on `find`; `opens_stage` only on `find` | refuse |
| `secret` ⇔ `reveal`; no entry reveals itself; the reveal dependencies (a `choir_page` reveal's page, its parents) are acyclic | refuse |
| `hinted_by` is non-secret and in the same or an earlier Stage | refuse |
| Exactly one opener per Stage 1–4; an opener's parents are all in earlier Stages; each Stage 2–4 opener has `hinted_by` in the Stage before | refuse |
| No `position` collision within a category; an entry's Stage equals its category's (except `choir_pages`) | refuse |
| Exactly one `action` page on `note`/`listen` entries, none elsewhere | refuse |
| Unknown trigger or page type; a numeric field out of range; a required built-in key missing | refuse |
| Each aspect's `revealed_by` is a `listen` entry with `lasting` > 0 | refuse |
| The `revealed_by` entry carries `permanent` 0 | built-ins: test failure; addons: warning |
| A note's full-knowledge chain + silent cells > 169 cells | warning |

Recipe and fact references point at reloadable data, so they are checked by the build-phase validator, and at runtime only logged once and drawn as a visible "missing" marker, never as a wrong number.

### States

One total function from (data, player state) to state, computed identically on both sides from synced state.

**Stored learning and render state are separate.** The stored `learned` set is what counts for every gate: parents, recipe gates and skip eligibility. The render state below decides only what the book draws. For display, **HIDDEN wins over LEARNED**: after a `stage` command lowers the Stage, or after changed data makes a learned entry secret and unrevealed, the entry is not drawn, but it still counts as learned. LEARNED wins over OPEN and LOCKED.

| Render state | When (first match wins) |
|---|---|
| **HIDDEN** | the entry's Stage is above the player's, or it is `secret` and not revealed. Never drawn, never searchable, never named. |
| **LEARNED** | in the stored `learned` set |
| **OPEN** | `parents` and `hidden_parents` learned, and by kind: `free` always; `find` its trigger has latched; `note` the player has an open note for it, or the begin precondition holds (in skip mode: skip eligibility); `listen` nothing more |
| **LOCKED** | otherwise. Drawn with its title and hints; an open note is still reachable (see "Snapshots and reopening"). |

- **`free`** entries are learned the moment they are OPEN.
- **Categories** of an unreached Stage don't render at all, so the book never mentions content the player can't yet find.
- **Cross-Stage parents** can't be drawn as lines across canvases; the entry page lists them by title, and each is a link that opens that entry.

### Triggers

A code registry, **`vftv:research_trigger_type`**: types in code, instances in data. A `triggers` or `reveal` list is an **OR**; the fields within one trigger are an **AND**.

| Type | Fields | Fires on | Login catch-up checks |
|---|---|---|---|
| `vftv:advancement` | `advancement` | NeoForge `AdvancementEarnEvent` | the advancement is done |
| `vftv:scanned` | exactly one of `key` (a scan key, e.g. `item:minecraft:amethyst_shard`), `tag` (`item:#c:gems`, `block:#…`, `entity:#…`) or `node: true` (any node key) | `ThingScannedEvent`, any outcome | the scan ledger holds a matching key, with tags read **as they are now** |
| `vftv:discovered` | `aspect` | `AspectDiscoveredEvent` | the aspect is known |
| `vftv:choir_page` | `page` (the page entry's id) | that page being tipped in | that page entry is latched or learned |
| `vftv:warp_at_least` | `kind` (`total` or `lasting_plus_permanent`), `value` 1–1000 | #25's post-commit warp event | current warp meets it |

- **No aspect-content matcher.** `ThingScannedEvent` carries no observed aspect set, so triggers match keys; aspect knowledge is matched with `discovered`. The aspects spec's event payload is used as it stands.
- **Index:** at world open, triggers are indexed by type and key, so an event costs O(matching triggers).
- **Latch:** a trigger that fires before its entry is visible is recorded (`latched` for finds, `reveal_latched` for secrets) and acts once the entry can use it, so the order of finds can never strand anyone.
- **Catch-up runs once per login**, never by polling. It lets entries added by a reopened pack work in old worlds. **Limitation:** history the game no longer holds can't be reconstructed. The ledger keeps no record of past tag contents, and a warp level crossed and then left before the entry existed is gone.

**Resolution** after any state change, in rounds until nothing changes. Each round runs these steps in order, and within each step entries go in ascending id order:
1. **Stage:** if the opener of Stage `stage + 1` is latched and its parents are learned, advance the Stage and learn the opener.
2. **Reveals:** a secret with a latched reveal becomes revealed.
3. **Free:** each OPEN `free` entry is learned.
4. **Finds:** each OPEN `find` with a latched trigger is learned.

Every round learns or reveals at least one entry, or the resolution stops, so it is bounded by the registry size. Events are posted **after** the whole resolution, in learning order. A change caused by an event listener is queued and resolved after the current resolution finishes.

### Stage gates

- The attachment holds `stage` (0–4). Stages advance strictly in order. A Stage *n* + 2 find made early stays latched and cascades as soon as *n* + 1 opens.
- **An opener may not require research in the Stage it opens** (validated), so opening never waits on content the opener itself hides.
- Skip mode never skips a Stage gate.
- Each step fires `StageReachedEvent`.

### Warp on entries

Warp is never a hidden tax. It is only ever applied by an explicit commit the player sees.

- **Only `note` and `listen` entries** may carry `warp` (validated), and **no trigger ever applies warp**.
- **Commit flow:** the action page shows the exact cost with #25's preview (actual deltas, including any clipping at the permanent cap), taken from the quote. The commit button opens a confirm dialog repeating it, and the payload echoes the previewed cost. The server makes **one checked commit** through #25 with that expected preview: if the current preview differs, it refuses with "The cost has changed." and changes nothing. Cancelling spends nothing.
- **Applies to:** `listen` (the **Listen** button), `learn` on a solved note with warp, and `buy` of a warp-bearing note. A solved note waiting for confirmation can still be abandoned for a full refund.

### The Vacuos reveal

**`vftv:silence_beneath`, "The Silence Beneath":** a Stage 3 `listen` entry that is `secret`, revealed by the Choir page `vftv:choir/beneath_the_notes` (the first page to contradict the Listener's notes), and hinted by that page's prose. Its parents are the Stage 3 opener `vftv:ruins` and the warp entry `vftv:being_heard`. It is the `revealed_by` of Vacuos, and it is never auto-learned.

- **Cost: lasting warp only** (`warp.lasting` > 0, `temporary` and `permanent` 0). The exact amount is set by [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25) and written into the entry data by the Stage 3 content ticket. Lasting warp is treatable, so the central revelation on the critical path never forces the most-hated mechanic on anyone.
- **Flow:** the page shows the cost, Listen opens the confirm, and the server commits. Then #25 applies the warp, the entry is learned, the player's view switches to the true view (new links appear in every note), and the listen beat plays to that player only.
- **Available in every mode**, skip included, since it is a commit and not a puzzle.
- **Optional deeper listen:** an optional `listen` entry may carry **permanent** warp. Whether it exists, its name and its amount are #25's call; the schema already supports it, and it must never be anyone's parent.

### Rewards

- `rewards.points` are paid **once per entry, ever**, on first learning. A **`rewarded`** ledger in the attachment records it and survives `revoke` and `reset`, so neither can farm rewards.
- **Rewards teach.** A reward naming an aspect the player doesn't know first discovers it, together with its missing prerequisites, through the aspects knowledge service. That runs lowest tier first, then by id, pays the usual discovery bonuses and fires `AspectDiscoveredEvent`. Hidden primals use their taught prerequisites. A Silentium reward therefore also teaches Sonus and Vacuos if they are missing.
- Rewards never apply warp.

### Choir pages

Stage 3 and 4 `find` entries in the `vftv:choir_pages` category, each carried by one item, **`vftv:choir_page`**, whose component **`vftv:page`** holds the entry id. They are lucid, they carry the backstory, they sometimes contradict the player's notes, they never degrade, and they may grant rewards. They are the guaranteed supply of Alienis, Spiritus and Silentium, which the aspects spec allows to come from VFTV content alone.

**Tipping in:** use the item, in either hand.

| Case | Message | Item | State |
|---|---|---|---|
| the component is missing or names no page entry | "The page is blank to me." | kept | none |
| the page's Stage is above the player's | "The hand is too old for me yet." | kept | none |
| already latched or learned | "Already in my book." | kept, so it can be handed on | none |
| otherwise | "Tipped in." | **consumed**, after the latch is committed | the `choir_page` trigger latches; resolution runs; the page entry is learned once its parents allow |

- **Physical pages are transferable; learning is not.** Handing a page on lets another player tip it in for themselves.
- **Where found:** in Choir ruins (#27) and along the Choir's Road (#28), in a **per-player reward block**. Its claims are persistent, once **per player per page per site**, with no eviction. A player who claimed a page but has neither latched nor learned it may claim it again (a free reissue for a lost page).
- **Count:** about 12 in Stage 3 and 6 in Stage 4. The content tickets freeze the list.

### Admin commands

`/vftv research …`, permission level 2. Each change runs resolution and fires events with source `COMMAND`.

| Command | Effect |
|---|---|
| `grant <player> <entry \| all>` | Learns the entry and its whole `parents` ∪ `hidden_parents` closure, raising the Stage and learning openers if needed. **Bypasses** costs, rewards and warp: nothing is paid, applied or added to `rewarded`. Any open note for a granted entry is refunded and closed. |
| `revoke <player> <entry \| all>` | Removes the learned flag only: no cascade, no refund, no warp change, and the Stage is never lowered. Fires **no** learned event. Revoking a reveal entry returns the taught view; open notes keep their snapshot links. |
| `stage <player> <0–4>` | Sets the Stage. Learns nothing and clears no latch. |
| `reset <player>` | Abandons every note with a full refund, then clears the Stage, learned, revealed, latches, attempts and bookmarks, and revokes the player's `vftv:find/*` advancements. **Keeps** `rewarded`, warp and the Joining or Silencing choice. Finds whose evidence lives in knowledge (scans, discoveries) or in current warp re-latch at the next login catch-up, by design. |

## Authored content

**Production tickets deliver authored inputs under the top-level `content/` directory** (CC-BY-4.0 data, per [`NOTICE.md`](../../NOTICE.md)). Future datagen reads them and writes the registry instances and lang files into `src/generated/resources`, so the architecture's generated-resource rule stands unchanged. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/research/categories.json          categories
content/research/stage1.json … stage4.json entries and page descriptors (Choir pages in stage3/stage4)
content/research/path-stage1.json … 4     affordability paths
content/research/overlay.json             warp-overlay numbers (client)
content/research/number-words-allowed.json reviewed exceptions to the number-word lint
content/lang/en_us/stage1.json … 4        titles, hints and pages
content/lang/en_us/marginalia.json        marginalia pools
```

| Input | Generated output |
|---|---|
| `categories.json`, `stage<N>.json` | `data/<ns>/vftv/research_category/<path>.json`, `data/<ns>/vftv/research/<path>.json` (an id `vftv:choir/x` becomes `choir/x.json`) |
| `overlay.json` | `assets/vftv/commonplace/overlay.json` |
| `lang/<lang>/*.json` | only each file's `strings` object is merged into `assets/vftv/lang/<lang>.json`, together with the code-defined UI keys; a duplicate key fails datagen |
| `path-stage<N>.json`, `number-words-allowed.json` | nothing: read by the affordability test and the lint only |

Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown field, a wrong type, a value out of range, or an entry that fails load validation.

| File | Schema | Shape |
|---|---|---|
| `categories.json` | `vftv:research_categories/1` | `{"categories": {<id>: <category>}}` |
| `stage<N>.json` | `vftv:research_entries/1` | `{"entries": {<id>: <entry>}}` |
| `path-stage<N>.json` | `vftv:affordability_path/1` | see "Affordability paths" |
| `overlay.json` | `vftv:overlay/1` | see "Overlay numbers" |
| `number-words-allowed.json` | `vftv:number_words_allowed/1` | `{"allowed": {<lang key>: <one-line reason>}}` |
| `lang/<lang>/*.json` (prose and marginalia) | `vftv:lang/1` | `{"strings": {<key>: <text>}}` |

### Entry files

`{"schema": "vftv:research_entries/1", "entries": {<id>: <entry>, …}}`, where each entry uses the registry format above. Four representative complete entries (comments are not part of the files):

**A cross-Stage prerequisite.** A Stage 2 note whose parents include a Stage 1 entry:

```jsonc
"vftv:crucible": {
  "stage": 2,
  "category": "vftv:distillation",
  "position": [0, 1],
  "icon": {"item": "vftv:crucible"},
  "kind": "note",
  "parents": ["vftv:distillation", "vftv:arcane_crafting"],   // arcane_crafting is Stage 1
  "anchors": ["vftv:aqua", "vftv:ignis", "vftv:permutatio"],
  "rewards": {"points": {"vftv:aqua": 4}},
  "pages": [
    {"type": "vftv:text"},
    {"type": "vftv:recipe", "recipe": "vftv:crucible"},        // id frozen once #19 lands
    {"type": "vftv:action"}
  ]
}
```

**A secret reveal.** The Vacuos listen:

```jsonc
"vftv:silence_beneath": {
  "stage": 3,
  "category": "vftv:ruins",
  "position": [3, -2],
  "icon": {"sprite": "vftv:aspect/vacuos"},
  "kind": "listen",
  "parents": ["vftv:ruins", "vftv:being_heard"],
  "secret": true,
  "reveal": [{"type": "vftv:choir_page", "page": "vftv:choir/beneath_the_notes"}],
  "hinted_by": "vftv:choir/beneath_the_notes",
  "warp": {"lasting": 3},                                      // #25 sets the amount
  "pages": [{"type": "vftv:text"}, {"type": "vftv:text"}, {"type": "vftv:action"}]
}
```

**A warp-bearing skip purchase.** In skip mode, Buy opens the same warp confirm; `skip_cost` overrides the derived price:

```jsonc
"vftv:hollow_bell": {
  "stage": 4,
  "category": "vftv:choirs_road",
  "position": [-1, 2],
  "icon": {"item": "vftv:hollow_bell"},
  "kind": "note",
  "parents": ["vftv:choirs_road"],
  "anchors": ["vftv:silentium", "vftv:metallum", "vftv:alienis"],
  "skip_cost": {"vftv:silentium": 6, "vftv:metallum": 4, "vftv:alienis": 4},
  "warp": {"temporary": 2, "lasting": 1},                      // #25 / #28 set the amounts
  "pages": [
    {"type": "vftv:text"},
    {"type": "vftv:recipe", "recipe": "vftv:infusion/hollow_bell"},   // id frozen once #28 lands
    {"type": "vftv:action"}
  ]
}
```

**A Choir reward.** It teaches Silentium (and Sonus and Vacuos if they are missing):

```jsonc
"vftv:choir/beneath_the_notes": {
  "stage": 3,
  "category": "vftv:choir_pages",
  "position": [0, 0],
  "icon": {"item": "vftv:choir_page"},
  "kind": "find",
  "triggers": [{"type": "vftv:choir_page", "page": "vftv:choir/beneath_the_notes"}],
  "rewards": {"points": {"vftv:silentium": 4, "vftv:spiritus": 2}},
  "pages": [{"type": "vftv:choir"}, {"type": "vftv:choir"}]
}
```

**Categories** (complete; icons illustrative until their items exist):

```json
{"schema": "vftv:research_categories/1", "categories": {
  "vftv:resonance":    {"stage": 1,    "icon": {"item": "vftv:tuning_fork"},      "order": 0},
  "vftv:distillation": {"stage": 2,    "icon": {"item": "vftv:crucible"},         "order": 1},
  "vftv:ruins":        {"stage": 3,    "icon": {"sprite": "vftv:research/ruins"}, "order": 2},
  "vftv:choirs_road":  {"stage": 4,    "icon": {"item": "vftv:hollow_bell"},      "order": 3},
  "vftv:choir_pages":  {"stage": null, "icon": {"item": "vftv:choir_page"},       "order": 4}
}}
```

### Page descriptors

A code registry, **`vftv:page_type`**. Page *i* (0-based) of entry `<ns>:<path>` takes its prose from the lang key `research.<ns>.<path>.page.<i>`.

| Type | Fields | Prose | Draws |
|---|---|---|---|
| `vftv:text` | none | required | the prose |
| `vftv:recipe` | `recipe` (any synced recipe id, ours or vanilla); `preview` (default `false`) | optional caption | the recipe from synced recipe data through `SlotDisplay`, with costs from the owning spec's preview. With `preview: true` it is a **locked preview** ("Not yet within my reach") that implies no craftability. |
| `vftv:aspects` | `of` (a scan key) | optional caption | the thing's aspects: known ones exact, unheard ones as the unheard icon with the exact amount |
| `vftv:structure` | `multiblock` (an id in the owning spec's data) | optional caption | a rotatable preview through `PictureInPictureRenderer` |
| `vftv:image` | `sprite` (a GUI sprite id, at most 130×120) | optional caption | the sprite |
| `vftv:action` | none | none | per kind: the note grid button and Learn/Abandon, Buy in skip mode, or Listen |
| `vftv:choir` | none | required | the prose on Choir paper, never degraded |

**Recipe sync:** clients receive no recipes by default in 26.x, so `neoforge` sends every recipe type that **any** `recipe` page references, vanilla ones included, through `OnDatapackSyncEvent#sendRecipes`.

### Prose and fact references

**Lang keys,** all in `content/lang/<lang>/`. In a key, a `/` in an id's path becomes `.` (`vftv:choir/beneath_the_notes` → `research.vftv.choir.beneath_the_notes.title`).

| Key | Required |
|---|---|
| `research.<ns>.<path>.title` | every entry |
| `research.<ns>.<path>.hint` | every non-`free` entry: a first-person diegetic hint |
| `research.<ns>.<path>.page.<i>` | `text` and `choir` pages; optional captions elsewhere |
| `research.<ns>.<path>.margin.<band>` | optional authored marginal note for band 1–3 |
| `category.<ns>.<path>` | every category |
| `marginalia.vftv.own.<n>`, `.foreign.<n>`, `.interlinear.<n>` | the overlay pools, *n* contiguous from 0 |

**Lang files** (complete examples):

```json
{"schema": "vftv:lang/1", "strings": {
  "research.vftv.silence_beneath.title": "The Silence Beneath",
  "research.vftv.silence_beneath.hint": "The page says there is something under the notes. I would have to listen for it.",
  "research.vftv.silence_beneath.page.0": "I was taught that the void is air and undoing, mixed.\n\nThe page says otherwise.",
  "research.vftv.silence_beneath.page.1": "To hear it I must let it hear me. The cost is written plainly: {fact:entry:vftv:silence_beneath:warp}."
}}
```

```json
{"schema": "vftv:lang/1", "strings": {
  "marginalia.vftv.own.0": "Did I write this?",
  "marginalia.vftv.foreign.0": "*it is quieter below*",
  "marginalia.vftv.interlinear.0": "listen listen listen"
}}
```

**Rejected** (by the build-phase validator): a key outside the patterns above; a `research.*` key for an entry or page index that doesn't exist; a `.margin.<band>` outside 1–3; a required key missing in `en_us`; an empty string; a pool with a gap in *n*; a duplicate key across files; a malformed fact span; and either lint failing.

**Markup:** paragraphs are separated by a blank line (`\n\n`); `*…*` is italic; `\` escapes `*`, `{` and `\`; and **fact spans** insert live data.

```
span   = "{fact:" source ":" args "}"        args are split on ":"; the arity is fixed per source
recipe : <ns> ":" <path> ":" <field>         field from the owning spec's preview: at least
                                             output, inputs, and every decision cost it returns
                                             (e.g. vis, vis_effective, flux; stability for infusion)
aspects: <kind> ":" <ns> ":" <path>          a scan key of kind item, block or entity
entry  : <ns> ":" <path> ":" ("warp" | "skip_cost" | "rewards" | "held")
pool   : <ns> ":" <path>                     the player's points of that aspect
warp   : "temporary" | "lasting" | "permanent" | "gear" | "total"
key    : <key mapping name>                  e.g. key.use
vis    : <VisRules key>                      a tuning key or limits.* key of the wands spec's synced
                                             VisRules, e.g. tap.amount, relay.range, limits.network_members
```

- Fact spans render as exact values in plain text (aspect amounts as name and number, never inline icons), are byte-identical at every warp band, and are never struck or covered.
- **Live values:** a fact span is re-resolved whenever one of its dependencies changes: the pool, a note's receipts, warp, quotes, a recipe or data reload, or, for `vis` spans, the arrival of a new `VisRules` ([wands spec, Sync and state](wands.md#sync-and-state): on join and after `/reload`). Layout caching may keep the surrounding text, but **never an old decision value**.
- **Guardrail lints** (build phase):
  - **Digits:** shipped prose contains **no ASCII digits** outside fact spans.
  - **Number words (`en_us`):** these case-insensitive whole-word tokens are rejected: `zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty fifty sixty seventy eighty ninety hundred hundreds thousand thousands dozen dozens score half halves quarter quarters twice thrice double triple single pair pairs couple`. This list is exhaustive. Lists for other languages come with the localisation spec, and until then other languages get the digit lint only.
  - **Exceptions:** a key may use a listed word only if it appears, with a one-line reason, in `content/research/number-words-allowed.json`, e.g. `{"schema": "vftv:number_words_allowed/1", "allowed": {"research.vftv.choir.beneath_the_notes.page.0": "'one' as a pronoun, not a quantity"}}`.
  - A review checklist in each content ticket covers the rest: prose never states a quantity a decision rests on.
- An unresolvable span (data removed by a pack) renders as `[?]` and is logged once. It never shows a stale number.

### Affordability paths

`{"schema": "vftv:affordability_path/1", "stage": 1–4, "sources": {<source id>: <source>}, "steps": [<step>, …]}`.

- **A source** has exactly one of:
  - `scan` (a scan key);
  - `node` (a node guarantee id #17 declares, written in full: `"vftv:starter"`);
  - `advancement` (an advancement id);
  - `choir_page` (a page entry id), with `site` (a site id #27 or #28 publishes).

  Every source also has `count` (1–64, how many uses the world guarantees), `requires` (`{"research": [ids], "aspects": [ids]}`, default empty) and `why` (a one-line review note on why the source is guaranteed in this Stage).
- **A step** is exactly one of `{"use": <source id>}`, `{"learn": <note entry id>}` or `{"listen": <listen entry id>}`.
- **Rejected:** an unknown source id; using a source beyond its `count` or before its `requires` hold; a `learn` on a non-note or an ineligible note; a `listen` on an ineligible entry; an advancement or page that no reviewed source names; a Stage that does not end as required.

```jsonc
{"schema": "vftv:affordability_path/1", "stage": 1,
 "sources": {
   "geode":   {"advancement": "vftv:find/resonance", "count": 1, "why": "geodes generate in every overworld; holding a shard grants it"},
   "shard":   {"scan": "item:minecraft:amethyst_shard", "count": 1, "why": "mined from the same geode"},
   "torch":   {"scan": "item:minecraft:torch", "count": 1, "why": "craftable from starting wood and coal"},
   "starter": {"node": "vftv:starter", "count": 1, "requires": {"research": ["vftv:sounding"]}, "why": "#17's starter-node guarantee"}
 },
 "steps": [
   {"use": "geode"},                  // opens Stage 1; free entries learn themselves
   {"use": "shard"},
   {"use": "torch"},                  // Lux, so the tutorial's anchors connect
   {"use": "starter"},
   {"learn": "vftv:taking_notes"}
   // … the rest of Stage 1, ending once vftv:find/distillation is latched
 ]}
```

### Overlay numbers

```jsonc
{"schema": "vftv:overlay/1", "min_word_letters": 4,
 "bands": [
   {"own": 1, "struck_percent": 0,  "foreign": 0, "interlinear": 0, "blots": 0},   // band 1
   {"own": 1, "struck_percent": 6,  "foreign": 1, "interlinear": 0, "blots": 0},   // band 2
   {"own": 1, "struck_percent": 12, "foreign": 2, "interlinear": 1, "blots": 3}    // band 3
 ]}
```

**Ranges:**
- `min_word_letters` 1–64;
- `bands` has **exactly three** objects (bands 1, 2 and 3; band 0 is always empty);
- `own` 0–1, `foreign` 0–2, `interlinear` 0–1, `blots` 0–6, `struck_percent` 0–100.

Anything else is rejected: a missing or unknown field, a wrong type, a value out of range, or a different band count. A resource pack's invalid override is ignored with a logged error, and the built-in file is used.

### Roster

Each content ticket freezes its Stage's roster: ids, kinds, dependency edges, anchors, rewards, pages and prose. Provisional outline:

| Stage | Entries |
|---|---|
| 1 Resonance | `resonance` (opener) → `commonplace_book` (free; teaches the inventory strike), `notes_of_things` (free; aspects and the unheard), `sounding` (free) → `taking_notes` (the tutorial note: Aer and Ignis, joined through **Lux**, which the path makes known first) → `knots_in_the_air` (find: `scanned` with `node: true`) → `first_wand` (`free` or `note`: teaches the **ungated** wand recipe `vftv:wand_assembly` and gates nothing), `tapping` (note: no recipe; gates the server-checked tap action), `arcane_crafting` (note) → [Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)'s Stage 1 recipe entries |
| 2 Distillation | `distillation` (opener), then the crucible, Resonant Brass, essentia and jars, tubes, cultivating nodes, relays and first golems |
| 3 Ruins | `ruins` (opener), the Choir pages, the Answerers (find: an advancement for killing one), infusion, `being_heard` (warp and its treatment), the Hush, `silence_beneath` |
| 4 The Choir's Road | `choirs_road` (opener), Void-tier crafting (warp-gated through `warp_at_least` reveals), the road, and pages on Joining and Silencing. The choice itself is made in the world (#28), never in the book. |

## The Commonplace Book

### The item

**`vftv:commonplace_book`**, stacking to 1, bound to no one. It is a viewer of its holder's own research.

- **Made** by striking a **blank** Book and Quill with the Tuning Fork, through the aspects spec's `vftv:strike` recipe, which owns the conversion and the refusal of written books. A lost book is simply struck again.
- **Using it** (in either hand) opens a server menu, `CommonplaceMenu`, which stays valid while the book is in a hand and the player is alive. A book given to another player opens *their* notebook, so copies need no prevention.
- **Before Stage 1** it opens to one page: "The pages are blank. I have heard nothing yet."
- All state lives in the player's research attachment (`copyOnDeath`).
- The first entry, `vftv:commonplace_book` (`free`, child of `vftv:resonance`), teaches the inventory strike.

### Structure

- **Tabs:** Resonance, Distillation, Ruins, The Choir's Road and Choir Pages (each shown once its Stage is reached, or once a page is tipped in), then the **Ledger**. Each Stage tab is one chapter in one voice, following the authored decline.
- **Canvas:** each Stage tab is a pannable canvas of entry frames with parent lines. The frame's shape shows the kind and marks the opener; its finish shows LOCKED, OPEN or LEARNED.
- **Search:** a case-insensitive substring match over the titles of non-HIDDEN entries in the current language. Results replace the canvas, and Enter opens the first.
- **Bookmarks:** up to 9, stored in the attachment and toggled on an entry page. Keys 1–9 jump to them.
- **Recipe links:** clicking an item on a recipe page opens the non-HIDDEN entry with a recipe page whose recipe outputs that item, by lowest Stage and then lowest id. With none, it shows the tooltip only.
- **Locked entries** open to one page: title, authored hint and mechanical blockers. No content pages.
- **The Ledger** holds exact numbers only: the pool per known aspect (by tier, then id), held points per open note (each row opens that note, even when its entry is HIDDEN or LOCKED), the known aspects drawn in the player's view, and warp by kind with its total (#25's values). It replaces TC4's Sanity Checker.

### GUI contract

All sizes are **logical pixels**, and one sprite texel is one logical pixel.

- **Book screen:** 320×240, centred.
- **Scale:** the screen draws at the integer pixel scale `s = max(1, min(guiScale, ⌊framebufferWidth / 320⌋, ⌊framebufferHeight / 240⌋))`, applying a pose scale of `s / guiScale`, so it always fits the window and stays pixel-crisp.

| Element | Size | Position (screen origin top-left) |
|---|---|---|
| Spread (book frame, both pages) | 312×208 | (4, 16) |
| Page interiors | 142×188 each, 8-px gutter | left (14, 26), right (164, 26) |
| Text column | 130 wide, 9-px lines, inside the prose viewport (below) | 6-px inset in each page |
| Tabs (6) | 24×20, 4 px tucked under the spread | x = 14 + 28*i*, y = 0 |
| Tab icons | 16×16 | centred on each tab |
| Search field | 96×14 | (210, 1) |
| Bookmark ribbons (9) | 12×16, hanging below the spread | x = 196 + 12*i*, y = 224 |
| Back arrow | 18×10 | (20, 200) |
| Page arrows | 18×10 | previous (132, 200), next (282, 200) |
| Feedback line | 130×9 | (170, 188); text columns stop at y = 186 |
| Confirm dialog | 160×80 with two 54×18 buttons | centred on the spread |

- **Canvas view:** one 292×188 viewport across both pages, with the gutter drawn over it. **Entry frames** are 26×26 with a 16×16 icon, and position `[x, y]` puts the frame's centre at `(32x, 32y)` in canvas space. Parent lines are elbow connectors (horizontal, then vertical), 2 px wide, drawn in code: `ink2` while locked, `brass2` once learned. Panning is clamped to the entries' extent plus one grid unit.
- **Note view** (the whole spread):
  - **Palette column** at (14, 26), 58 wide: 3 columns of 18×18 slots at x = 16, 34, 52 and 8 visible rows from y = 28, with a 4×144 scrollbar at (70, 28).
  - Two **buttons**, 54×18 each, at (16, 176) and (16, 196): Learn or Buy, and Abandon.
  - **Grid area** at (76, 26), 230×188. The grid is centred.
  - **Hex cells** are 18×20, pointy-top, with an 18-px horizontal and 15-px vertical pitch, so the grid measures `(2R + 1)·18` × `30R + 20`. **Radius ≤ 5 fits without panning**; radius 6–7 pans (drag in the grid's empty space, or follow the keyboard focus). The initial view is centred.
  - Aspects in cells are drawn as their 16×16 icons. **Links** are teal bars across the shared edge, in three orientations. **Unanswered** placements are drawn dimmed.
- **Recipe pages** use 18-px slots; **structure** pages a 130×110 viewport.
- **Prose viewport:** on every prose-bearing page, prose is laid out in the text column (130 wide) and drawn in a **clipped, vertically scrollable viewport**.
  - The viewport runs from y = 32, or from y = 44 on a page that carries the entry title, down to y = 186, so it ends above the feedback line. Any length of prose, in any translation, plus every overlay line fits by scrolling; nothing is cut off or paged automatically.
  - When the content is taller than the viewport, a scrollbar (track 4 wide, tiled from a 4×16 sprite, with a 4×12 thumb) runs the viewport's height at the page's inner edge (x = 152 on the left page, 302 on the right).
  - The scroll position resets on every page turn.
  - **Overlays are part of the same layout:** inserted notes, the interlinear line and struck words scroll with the text. Blot slots are spaced evenly over the laid-out content height and centred on the page's outer interior edge, so a blot covers only the border and inset, never the text column. **Fact spans are never obscured.**
- **The palette** uses the same scrollbar sprites.

**Controls:**

| Input | Effect |
|---|---|
| Left-click a palette slot | select it as the brush |
| Left-click an empty cell | place the brush |
| Right-click a placement | remove it |
| Left-drag on empty canvas or grid space | pan |
| Mouse wheel | pan the canvas vertically (Shift: horizontally), scroll the palette, or scroll the hovered prose viewport by 3 lines (27 px) per notch |
| Tab / Shift+Tab | cycle focus: tabs → search → bookmarks → main region (canvas entries, or page widgets → palette → grid → buttons) |
| Arrow keys | move within a region. On the canvas: the nearest entry within a 90° cone, ties by id. On the grid: Left/Right = W/E; Up = NW if *r* is even, else NE; Down = SW if *r* is even, else SE (so the focus keeps its screen column). On a focused prose viewport: Up/Down scroll one line (9 px). |
| Home / End | scroll the focused prose viewport to its top or bottom |
| Enter / Space | activate: open an entry, select a palette aspect, place the brush on the focused empty cell |
| Delete / Backspace | remove the focused placement |
| PageUp / PageDown | turn pages |
| 1–9 | open a bookmark (outside the search field) |
| Ctrl+F | focus search |
| Esc | close the dialog, then return to the canvas, then close the book |

- **Narration:** every focusable widget narrates.
  - Entry: title, kind, state, and the clean hint.
  - Cell: its axial position, its content (empty, silent, an anchor, or a placement with its aspect name), and its linked neighbours.
  - Palette slot: aspect name, gloss and exact pool count.
  - The note announces when it becomes solved.

  The narrator always reads the **clean** prose and the fact values, never the overlay.
- **Feedback:** each action carries a request id, and the affected control shows a 9×9 pending glyph and ignores input until its result arrives. Inputs re-enable after 40 ticks if no result comes. A refusal shows its message on the feedback line until the next action; success plays its sound. The drawn state always comes from the attachment, never from prediction.

### Hints on locked entries

Two lines, never naming a later Stage's content:
1. **The authored hint** (`.hint`), required for every non-`free` entry and written in the first person ("The amethyst hums when struck. I should find where it grows.").
2. **A mechanical line built from state,** naming only things the player can see:
   - missing visible parents, by title;
   - "Something else must come first." for unmet `hidden_parents`;
   - for a note whose anchors don't connect, "I can't yet hear how [A] meets [B].", drawing unknown aspects as the unheard icon;
   - in skip mode, unknown price aspects, drawn the same way.

A secret is hinted only through its `hinted_by` entry's prose, and validation proves that entry is reachable (non-secret, same or earlier Stage).

### Prose that degrades with warp

1. **Authored decline:** each Stage's prose is written in a more unravelled voice. This is content.
2. **Live overlay,** client-only, from the owner's **book band** 0–3. #25 owns the thresholds (`book_bands`) and syncs the band. The overlay recedes as warp falls, because the band follows current warp.

| Band | Overlay (cumulative counts, not increments) |
|---|---|
| 0 | none |
| 1 | 1 own-hand note |
| 2 | 1 own-hand note, 6% of eligible words struck, 1 foreign note |
| 3 | 1 own-hand note, **12% of eligible words struck (in total)**, 2 foreign notes, 1 interlinear foreign line, 3 blots |

- **Applies to `text` pages only.** Titles, hints, fact spans, recipe, aspect, structure, image and action pages, Choir pages and the Ledger are never touched. Words are struck through and stay legible; nothing is replaced.
- **Eligible words** are maximal runs of Unicode letters at least `min_word_letters` long, outside fact spans. The struck count is *k* = ⌊(`struck_percent` × eligible + 50) / 100⌋.
- **Determinism:** one `java.util.Random` seeded with `fold([0x4D415247 ("MARG"), uuid msb, uuid lsb] ++ idWords(entry) ++ [page index, band])`. Draws, in order:
  1. *k* struck words, each `nextInt(remaining)` over the remaining eligible words in text order;
  2. for each own note, `nextInt(remaining own pool)`, then a paragraph `nextInt(paragraphs)`, after which the note is inserted as an indented italic line;
  3. foreign notes, the same way (in `amethyst` ink);
  4. the interlinear line: pool index, paragraph, then line `nextInt(lines in it)`;
  5. each blot: a margin slot `nextInt(remaining of 6)` down the page's outer edge.

  **Draws are without replacement and capped by the pool.** A step draws min(configured count, items still available) times, and a pool that runs out ends that step. For example, a foreign pool of one line yields one foreign note even when the band asks for two. `nextInt(0)` is never called. An empty pool, or a page with no eligible words or no paragraphs, consumes no draws for that step. An authored `.margin.<band>` key replaces the first own-hand note on page 0. Layouts (text, positions and overlay choices) are cached per (entry, page, band, language) and rebuilt when one of them changes. Fact span values are never cached. They are re-resolved when a dependency changes (see "Prose and fact references"), and the layout is rebuilt if a value's rendered text changes.
- **Accessibility:** the client option `book.clear_prose` (default off) hides the whole overlay. The Ledger shows exact warp either way.
- **No custom font in v1:** the foreign hand is the vanilla font in italic plus colour.

## Events

NeoForge post-events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server after the whole state transition is committed, plus plain `core` queries `isLearned(player, id)` and `stage(player)`. Whether they are exposed in `api` is the API spec's call.

| Event | Payload |
|---|---|
| `ResearchLearnedEvent` | player; entry; source `SOLVED \| BOUGHT \| FOUND \| FREE \| LISTENED \| COMMAND`; warp actually applied (#25's deltas); points actually paid |
| `StageReachedEvent` | player; new Stage; previous Stage; source |

No event fires for a reveal, a placement, an abandon or a revocation, since no sibling needs them. Discoveries caused by rewards fire the aspects spec's `AspectDiscoveredEvent`.

## Sync and state

**Server-authoritative, owner-synced.**

- **Attachment `vftv:research`** (player, `copyOnDeath`):
  - `stage`, `learned`, `revealed`, `latched` and `reveal_latched`;
  - `notes` (at most 16 open);
  - `bookmarks` (at most 9);
  - `revision`: a persisted, monotonic `long`. Every change takes the next value;
  - **server-only:** `rewarded`, `attempts`.

  A **note** holds its entry, its snapshot (preset numbers, `place_cost`, `parent_cost`, link pairs, synthesis-parent map), radius, anchors, silent cells, placements (cell → aspect and receipt) and its own `revision`.
  - **Note revisions are never reused.** A note's revision is the attachment `revision` taken when it was created or last changed. The counter only rises, so a replacement note for the same entry (after an abandon, a refund or a reset) never repeats an earlier note's revision, within a connection or across connections.
  - The attachment sends a full baseline on join, then deltas (at most one note per delta), through `AttachmentSyncHandler` **filtered to the owner explicitly**.
- **No sharing** in v1: research, notes and held points are per player.
- **C2S `ResearchAction`** `(containerId, requestId, variant)`, where `containerId` is the open `CommonplaceMenu`'s container id:
  - `begin(entry)`
  - `place(entry, noteRevision, q, r, aspect)`
  - `remove(entry, noteRevision, q, r)`
  - `abandon(entry, noteRevision)`
  - `learn(entry, noteRevision, echoedWarp)`
  - `buy(entry, echoedPrice, echoedWarp)`
  - `listen(entry, echoedWarp)`
  - `bookmark(entry, on)`
- **S2C `ResearchFeedback`,** to the owner only:
  - `quotes`: the skip price and #25's warp preview for every non-HIDDEN `note`/`listen` entry. Sent when the menu opens. While it is open, re-sent **after any resolution** (whether from an action, an event or a command) and after a tuning `/reload`, whenever the set of non-HIDDEN `note`/`listen` entries or any price or preview has changed. For example, a reward that reveals a secret listen sends its preview at once.
  - `result(requestId, OK | REFUSED, reason key, up to 4 args)`. Attachments alone can't report a refusal that changes no state.
- **Server checks, in this order,** with the first failure refusing:
  1. **Rate:** at most 8 actions per player per tick, of which at most 1 is a commit (`begin`, `abandon`, `learn`, `buy`, `listen`), checked **before** any generation or resolution. Excess is dropped with no reply.
  2. `requestId` is greater than the last one processed for this player in this connection (stale or replayed requests are refused).
  3. `CommonplaceMenu` is open, its container id equals `containerId`, and the book is in a hand.
  4. The variant's eligibility (table below), including `noteRevision` where the variant carries one.
  5. The debit plan, where the variant needs one, succeeds.
  6. Echoes match the current quote and preview.
- **Variant eligibility:**

  | Variant | Requires |
  |---|---|
  | `begin` | the entry is a `note`, not learned, with no open note for it; mode is not `skip`; the begin precondition holds; fewer than 16 notes are open ("My book has no room for another open note.") |
  | `place` | an open note for the entry with that revision; the cell is inside the grid, not an anchor, not silent and empty; the aspect is known. Checked against the snapshot only (see "Existing notes are not re-gated"). |
  | `remove` | an open note with that revision; the cell holds a placement |
  | `abandon` | an open note with that revision (solved or not) |
  | `learn` | an open note with that revision, **solved** under snapshot ∪ current-view links; the entry not yet learned; the warp echo matches if the entry carries warp |
  | `buy` | skip eligibility; the entry not yet learned; the price echo and warp echo match |
  | `listen` | a `listen` entry that is OPEN and not yet learned; the warp echo matches |
  | `bookmark` | on: the entry is not HIDDEN, and fewer than 9 are set (re-setting an existing bookmark is a no-op); off: always |

- **Refusals change nothing.** Every refusal path, and every failed plan or commit, leaves the research attachment, the knowledge attachment and warp exactly as they were.
- **Bounded codecs:**
  - ids ≤ 4,096 per set, notes ≤ 16, cells ≤ 169, receipt entries ≤ 15, link pairs ≤ 4,096, synthesis-parent map ≤ 4,096, anchors ≤ 5, bookmarks ≤ 9, triggers ≤ 16, pages ≤ 32;
  - **echoed price** ≤ 64 entries, each 1–4,096; **echoed warp** three values, each 0–100;
  - reason args ≤ 4 strings of ≤ 256 chars.

  Oversized or out-of-range input is rejected at decode, before any planning.
- **Reload behaviour:**

  | Reload | Effect |
  |---|---|
  | Tuning (`/reload`) | Prices rebuild and fresh quotes are pushed. Open notes are untouched. |
  | Recipes or data (`/reload`) | Recipe pages and every fact span re-resolve. |
  | Resource reload | Prose, overlay and layout caches rebuild. |
  | Registries | Need the world reopened; reconciliation runs at login. |

## Tuning

Every tunable number, all ours. Unless noted, the home is the server-only datapack file **`data/vftv/tuning/research.json`**, loaded on reload into an immutable record in `core.research` and written by datagen from that record's defaults. **Rollback rule:** an invalid file (malformed, or a value out of range) refuses the world at open. On `/reload` it keeps the previous record and logs an error. The client never needs this file: everything client-side arrives in the note snapshot or in quotes.

| Key | Value | Range | Notes |
|---|---|---|---|
| `place_cost` | 1 | 1–4 | points reserved per placement |
| `combine.parent_cost` | 1 | 1–4 | of each synthesis parent, per unit synthesised |
| `grid.base_radius` | gentle 2, standard 3, hard 3 | 1–7 | |
| `grid.anchor_bonus_at` | 4 | 2–6 | +1 radius at this many anchors used |
| `anchors.max_used` | 3 / 5 / 5 | 2–5 | |
| `anchors.min_distance` | 2 / 3 / 4 | 1–6 | |
| `silent_cells` | 0 / 0 / 5 | 0–12 | |
| `palette_help` | `neighbours` / `parents` / `parents` | enum | |
| `generate.max_attempts` | 32 | 1–64 | walk attempts before the fallback |
| `skip.multiplier` | 1.5 | 1.0–10.0 | on the reference vector |

**Other homes:**
- the overlay numbers (the band table, `min_word_letters` 4): `content/research/overlay.json` → the client asset `assets/vftv/commonplace/overlay.json`, overridable by resource packs;
- `book_bands`: #25's tuning;
- each entry's `warp`, `skip_cost`, `rewards` and anchors: entry data;
- `research.mode`: server config;
- `book.clear_prose`: client config.

**Fixed limits, in code** (formats and validator rules, not balance): anchors 2–5; grid radius ≤ 7 (169 cells); synthesis depth ≤ 3; 8 actions and 1 commit per player per tick; 16 open notes; 9 bookmarks; the codec bounds above; the 40-tick pending timeout; the GUI layout. **Test threshold:** `affordability.margin` 1.5.

## Performance

**Heavy-base workload:** one player with 150 learned entries and 16 open notes, 3 of them at radius 7 (addon-sized; built-in notes stay within radius 4 at default tuning) with about 100 placements each, **with the book open on a note**. Every tick they send 7 placements or removals and 1 `begin` or `abandon` (the action cap). The aspects workload strikes on every cooldown through the trigger index. One join sends the full baseline, and one find cascades through a 20-entry resolution. On the client the book is open on the largest canvas (60 entries) at band 3 with bookmarks, then on a structure page.

**Share:** server ≤ 0.03 ms mean, ≤ 0.15 ms p99, with the action flood included. Client: **0** with the book closed; with it open, our draw code ≤ 0.3 ms p50 at 1080p. The reference stress world includes one client with the book open. **Measured separately as load scenarios** (reported): join baseline and catch-up, a 20-entry cascade, a `begin` flood at the cap, and a tuning `/reload` rebuilding quotes.

**Hot paths:**
- **Server, per tick:** none.
- **Server, per placement or removal:** a solve BFS over ≤ 169 cells × 6 neighbours, and a debit plan of ≤ 15 nodes.
- **Server, per `begin`:** BFS chains over the known-link graph (O(aspects + links) per segment, ≤ 4 segments), ≤ `generate.max_attempts` walk attempts (32 by default, hard maximum 64) of ≤ 169 steps × 6 candidates, one fallback lay, one verification BFS. At most 1 per player per tick.
- **Server, per event:** O(matching triggers), then resolution bounded by the registry size.
- **Server, on join:** baseline O(state), plus catch-up O(entries × triggers).
- **Server, on tuning reload:** reference vectors O(note entries × chain BFS), once per reload, not per player.
- **Client, per frame:** the canvas, page layout and overlay are cached; our draw code allocates **nothing** per frame (an allocation-budget test). Engine draw calls and the structure preview's renderer are outside that claim; the structure preview stays within the share.
- **Packets:** a delta carries at most one note (≤ 169 cells); a baseline at most 16 notes and the id sets.

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- links in both views, the reveal adding links, and the snapshot ∪ current union;
- the solve check, including strays and branching;
- **grid geometry:** spiral contiguity and completeness for radius 0–7, all 12 symmetries, the neighbour order;
- **seed vectors** (the table above) and the full-note fixtures;
- **generation:** BFS tie-breaking, padding, the radius rule, walk candidates and the anchor distance, silent-cell draws, a forced fallback and a forced refusal at every preset;
- **solvability:** every issued note solved by an **independent exhaustive solver** on small boards (radius ≤ 2, ≤ 8 aspects, every seed in a range), plus **independent witness validation** at full scale (100,000 notes over random known sets at every preset);
- the built-in bound (≤ 145 chain cells + ≤ 12 silent = 157 ≤ 169), plus the **partial-knowledge regression**: known set = the six exposed primals + Metallum, Instrumentum, Sonus and Vacuos, taught view, anchors `[Ignis, Perditio, Metallum, Vacuos, Instrumentum]` → segments 7, 6, 5 and 4 steps, unpadded chain 23 cells; the note is issued at every preset (in the reference: gentle walks at radius 2, standard walks at radius 4, and hard falls back at radius 4);
- **debit plans:** conservation, partial compound balances before synthesis, mixed-depth synthesis, Vacuos synthesis after the reveal, all-or-nothing rollback, exact receipt refunds, the depth limit;
- the reference vector and skip price, including a recompute on tuning change;
- **affordability:** each Stage path replayed per preset and for skip, with prefix ordering (a reward after a spend never funds it);
- states over generated player states; trigger latching, the resolution order, opener-before-visibility and the Stage cascade;
- reconciliation for every row of its table; **existing-note access** after changed parents, aspect graph and knowledge (place, remove, abandon and learn still work from the snapshot, including its synthesis-parent map); note revisions never reused across replacement notes;
- **affordability path rules:** a `learn` on a find is rejected; a source used beyond its `count` or before its `requires` is rejected; node uses get fresh identities and the real reward formula; each Stage replay consumes the previous Stage's state in the same mode;
- **overlay pool caps:** a one-line foreign pool at band 3 yields one note and never calls `nextInt(0)`; empty pools and pages with no words consume no draws;
- every `content/` rejection rule (lang, overlay, number-word allow-list, path, entries, categories);
- fact-span parsing; spans byte-identical at every band; overlay determinism and the draw order; the digit and number-word lints;
- codecs: entry, category, every trigger and page type, the attachment (baseline and delta), the note snapshot, `ResearchAction`, `ResearchFeedback`, the tuning file and every `content/` schema, with ranges and defaults.

**GameTest:**
- both registries and the tuning file load, and every load-validation rule rejects its bad entry with a clear error;
- the book opens its menu; a blank Book and Quill becomes the Commonplace Book; the Stage 0 blank page;
- Stage 1 from picking up a shard, and from being handed a fork; the **opener bootstrap** (a later opener latched early cascades in order);
- a full note at **each preset**: begin, place, remove, solve, learn, with the pool checked at every step; a skip purchase, **including a note already open with reservations** and a **warp-bearing** one;
- an abandon refund, a refund after solving, a synthesis shortfall refused with nothing debited;
- a mode switch mid-note keeps the snapshot; **graph and tuning changes across a reopen** keep open notes valid;
- `listen` confirm, a mismatched echo refused, a warp note waiting for confirmation;
- each trigger type, latching before visibility, login catch-up;
- every Choir page case; **lost-page reissue**; two players each claim `beneath_the_notes` from one ruin without consuming the other's claim; the **129th claimant** still gets their page (with #27's block);
- an entry removed by a reopened pack (refund);
- **hostile input** for every `ResearchAction` variant: no menu, a **wrong container id**, no book, a wrong cell, an anchor or silent cell, an unknown aspect, a short pool, a stale revision or echo, an **oversized echoed map**, a **replayed request id**, and a flood beyond the cap. Each one leaves both attachments and warp unchanged;
- a second `begin` on an entry with an open note is refused; `grant` of an entry with an open note refunds and closes it;
- **quote refresh:** a note reward that reveals a secret `listen` while the menu is open delivers its preview without reopening the menu;
- **command replay:** grant → revoke → grant pays no reward twice; reset keeps warp and `rewarded`;
- persistence through save, reload and death; **two-player privacy**;
- every test id in the discovery guard.

**E2E:**
- open the book: tabs per Stage; pan, search, bookmarks and recipe links;
- a locked entry's hints (screenshot);
- solve a note by mouse, and one by **keyboard only**; a radius-7 note pans;
- band 0–3 screenshots plus a dump showing fact text unchanged; `clear_prose`;
- **prose overflow:** a long test translation at band 3 scrolls by wheel and by keyboard to its last line, with the marginalia scrolling with the text and every fact span visible and unobscured (screenshots at top and bottom);
- a Choir page tip-in; the Vacuos listen (state dump: lasting warp applied, true-view links present);
- the Ledger's warp matching #25's state;
- a refusal shown on the feedback line;
- sync on join, reconnect, `/reload` (fresh quotes, notes unchanged) and a reopen;
- the asset, lang and book-text validators: every entry has its `en_us` title, hint and pages, and every UI key exists.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Aspects and scanning spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/15)'s contracts on this ticket):

| Aspects contract | Met by |
|---|---|
| Puzzle links: the taught view before the reveal, taught links plus every exposed primal after it; notes made before the reveal stay valid | "Links", plus the snapshot union in "Snapshots and reopening" |
| Owns the `revealed_by` entry, and the revealing listen shows its warp cost before commit | "The Vacuos reveal" and "Warp on entries" |
| Stage 1 affordable from guaranteed supply; an active recovery path, so no player can strand themselves | "Affordability and recovery" (prefix proof and three layers), "Paying for placements" (refunds) |
| Owns the Commonplace Book item, records Stage 1 on the shard-or-fork trigger, teaches the inventory strike in the first entry | "The item", "Stage", "Stage gates" |
| Consumes `ThingScannedEvent` and `AspectDiscoveredEvent` rather than polling | "Triggers" (event-driven, plus one catch-up per login) |

**[Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)**
- The node types that generate in **Stage 1** terrain together carry all 6 exposed primals (a unit test over node-type data), and first-strike rewards are per player per node. This is recovery layer 2.
- It declares **node guarantee ids** (at least `vftv:starter`), each with its guaranteed composition, capacity and how many distinct nodes it guarantees in which Stage, which the affordability paths replay.
- It names a **stable, ungated recipe id for the wand** and a **server-checked action gate for tapping**. *Reconciled with the wands spec:* no hook proves a research gate on the vanilla crafting grid, so the wand recipe `vftv:wand_assembly` carries no `research`; `vftv:first_wand` teaches it and gates nothing, and `vftv:tapping` has no recipe but gates the tap action on the server at its start and at every pulse ([wands spec, Stage](wands.md#stage)).
- Node scan keys stay `node:<stable id>` through moves, so `scanned` triggers with `node: true` work.

**[Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)** (the recipe-gate contracts also bind [Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19), [Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20), [Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21), [Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22), [Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23), [Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24), [Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26) and [Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) for their own recipe types)
- **Gate:** each research-gated recipe carries `research: <entry id>`. The server refuses every processing and output path until that entry is learned, proved by a GameTest with an unlearned player.
- **Recipe-page validator:** a `recipe` page in entry E shows a recipe that is ungated, or gated by E or by anything in E's transitive `parents` ∪ `hidden_parents` closure. Any other (forward-looking) recipe must be marked `preview: true`.
- **One authoritative cost preview** per recipe type: base and effective vis, flux spill and every other decision cost, with capped modifiers flagged. It backs both the recipe page and the fact fields (`output`, `inputs` and each cost).
- **JEI** filters by the **learned gate**, updates on grant, revoke, login and logout, and marks recipes the player currently can't afford accurately. Everything works with JEI absent.

**[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)**
- The gate applies to crucible matching and consumption.
- Book pages follow the real essentia rules, including the Vacuos statement it owns. Point synthesis in the puzzle is a separate, explicitly defined economy and implies nothing about essentia.

**[Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)**
- The preview includes **stability** as a fact field, and the altar is a `multiblock` id that structure pages can show.

**[Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25)**
- `book_bands`: three ascending warp thresholds in its tuning. It syncs the owner's current band (0–3) and their exact warp per kind.
- It defines **`total`**, including gear warp if gear counts, and posts a post-commit warp-change event with values and source, **including gear equip and unequip** if they change the total.
- **One checked commit:** `commit(player, cost, source, expectedPreview)` applies exactly the previewed deltas (cap clipping included), or refuses on stale state, and returns the actual deltas. A listener or state change can never come between the preview and the charge.
- It sets a concrete **lasting-only** amount for `vftv:silence_beneath`, written into that entry's data before the Stage 3 content completes. It decides whether an optional deeper listen with permanent warp exists, and if so its name and amount.
- The book counts as an open GUI, so no Voice event fires while it is open.

**[Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)**
- Ships the hidden advancements `vftv:find/distillation` and `vftv:find/ruins` at those exact ids, attainable with only NeoForge installed. A test proves no opener prerequisite cycle.
- **Per-player Choir page blocks:** persistent claims once per player per page per site, **no eviction** (unlike a vanilla vault's capped history), and a free reissue for a claimed page that is neither latched nor learned. One player's claim never consumes another's.
- `vftv:choir/beneath_the_notes` is obtainable in Stage 3 with no Stage 3 research learned. A GameTest has two players obtain it independently from one ruin.
- It publishes source identities (site ids for Choir pages, advancement ids for finds), their aspect discovery and research prerequisites, quantities and pre-spend reachability for the Stage 2 and 3 affordability paths.

**[Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- Ships `vftv:find/choirs_road`, places the Stage 4 Choir pages in the same kind of per-player block, and keeps the Joining and Silencing choice in the world; the book only documents it. It publishes its Stage 4 sources the same way as #27.

## Production tickets

This spec settles every decision. Six required child tickets deliver the assets and the authored content, and **the system is not done until all six land**. **None of them writes mod code.** The content tickets deliver authored inputs under `content/`, in the schemas above. The asset tickets deliver generators and assets under the [art style](art-style.md). The runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Commonplace Book GUI sprites** | the generator `tools/art/book.py`, with every sprite at the sizes in "GUI contract": spread 312×208; Choir paper 142×188; tabs 24×20 (selected and unselected) with 6 tab icons at 16×16; search field 96×14 (idle and focused); bookmark ribbon 12×16 (empty and set); page and back arrows 18×10 (two states); entry frames 26×26 for free (circle), find (diamond), note (hexagon) and listen (arch) × locked/open/learned, plus an opener frame (a diamond with an outer ring) × 3; focus ring 30×30; hex cell 18×20 (empty, anchor ring, silent, hover/focus, unanswered); link bars in 3 orientations; palette slot 18×18 (normal and selected); scrollbar track 4×16 (tiled vertically) and thumb 4×12, shared by the palette and the prose viewports; buttons 54×18 (normal, hover, disabled); 9×9 warp and pending glyphs; confirm panel 160×80; blots ×4 at 12×12; a GUI-scale-2 contact sheet; provenance rows | none |
| **Commonplace Book items and sounds** | `commonplace_book` (Stage 1 row of art-style §5) and `choir_page` (Stage 3 row) textures, models and item definitions. Sounds in **Stage-appropriate registers** (art-style §7): page turn ×3, place ×3, link, solve and learn (Stage 1); Stage reached ×4 (one per Stage, each in its own Stage's register); tip-in (Stage 3); the listen beat (Stage 3, lower and wetter). Provenance rows. | none |
| **Stage 1 research content** | `content/research/stage1.json` (frozen roster, edges, anchors, rewards, pages), its categories in `categories.json`, `content/lang/en_us/stage1.json` (titles, hints, pages), and `path-stage1.json` with its reviewed sources; the tutorial's Lux made known in the path | [Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17), [Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18), and [Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (the path replays its values) |
| **Stage 2 research content** | the same for Stage 2, with the recipe ids frozen | [Wands, vis and nodes](https://github.com/andrewbrogoff/voices-from-the-void/issues/17), [Arcane crafting](https://github.com/andrewbrogoff/voices-from-the-void/issues/18), [Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19), [Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20), [Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23) |
| **Stage 3 research content** | the same for Stage 3, plus its Choir pages, `silence_beneath` with #25's lasting amount, `being_heard`, the marginalia pools and `overlay.json` | [Infusion](https://github.com/andrewbrogoff/voices-from-the-void/issues/21), [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25), [Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26), [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27) |
| **Stage 4 research content** | the same for Stage 4, plus its Choir pages and the Joining and Silencing pages (documentation only) | [Warp and the Voices](https://github.com/andrewbrogoff/voices-from-the-void/issues/25), [Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) |

The Stage 2, 3 and 4 content tickets are also blocked by [Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22) and [Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24), whose recipes can land in any of those Stages.
