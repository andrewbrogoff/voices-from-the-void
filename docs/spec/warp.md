# Warp and the Voices

How audible the Void finds a Listener, and what the Voices do about it. This covers:
- **warp**: its four kinds, how it is gained, how lasting warp is **stilled**, the small **permanent** share and its named milestones, and **gear warp**;
- **total warp**, the one number that drives the **dials**: the warp tax on ordinary vis, the infusion penalty and the Void-tier amplifier;
- the four **warp bands** that the Commonplace Book's overlay and the Voices read;
- **Voice events**: the counter that rations them, the suppression rules, the seeded draws and every event type;
- **Bargains**: offers of power for warp, decided in the book and always refusable at no cost;
- what one Listener alone perceives: **Voice lines** and their subtitles, phantom sounds, the colour drain, **disguised blocks** and **apparitions**;
- Joining and Silencing as they touch warp, and the Voice line recordings.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the code layout, the data and sync rules and the seams;
- the [quality gates](quality-gates.md) for every gate;
- the [lore](lore.md) and [progression](progression.md) specs for what warp is for and the hard rules it keeps;
- the [aspects spec](aspects.md) for knowledge, points and the phantom contract;
- the [research spec](research.md) for entries, the checked listen commit, the Ledger, fact spans, `mix64` and `fold`;
- the [wands spec](wands.md) for the quote and `VisSpentEvent`;
- the [crucible spec](crucible.md) for the `vftv:crucible` recipe type;
- the [infusion spec](infusion.md) for the stability formula;
- the [foci spec](foci.md) for the Void-tier amplifier;
- the [art style](art-style.md) for every asset.

Resolved in [Warp and the Voices spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/25). Inputs: [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md), [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md).

**Every number in this spec is ours.** None is taken from TC4: our counter is linear and in per mille, and our check period, decay, bands, cap, costs and chances were chosen fresh. Each tunable number is listed with its data home under "Tuning and limits". Numbers inside the JSON examples are illustrative unless the text says they are the shipped values; the content tickets set the real ones.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port that uses `java.util.Random` itself. Both print the same 91 lines (4,631 bytes, SHA-256 `9a2d654f99280c9db71fcaa14765324ee6f72f6a1f700859c4d9084c40a48210`). The references cover the arithmetic and the draws only: their agreement is no evidence for suppression, guards, callbacks, `SKIPPED` or `FAILED` outcomes, or the service's source-kind and negative-permanent rejections, which the build's GameTests prove (see "Tests").

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 1 Resonance, 2 Distillation | nothing: no built-in entry carries warp | |
| 3 Ruins | warp, the Voices, Bargains, stilling, the first permanent milestone | the opener `vftv:ruins`, whose find `vftv:find/ruins` is [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)'s ([research, Stage](research.md#stage)); then the entries below |
| 4 The Choir's Road | the second milestone; Joining's milestone | the opener `vftv:choirs_road` ([Eldritch tier](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)'s find) |

| Entry | Stage | Kind | Parents | Warp | Role |
|---|---|---|---|---|---|
| **`vftv:being_heard`** | 3 | `listen` | `vftv:ruins` | temporary 3 | gates the crucible recipe `vftv:stilling_draught`; teaches the kinds, the bands, the dials, the counter, Bargains and stilling |
| **`vftv:silence_beneath`** ([research](research.md#the-vacuos-reveal)'s) | 3 | `listen`, secret | `vftv:ruins`, `vftv:being_heard` | **lasting 4** | Vacuos's `revealed_by` |
| **`vftv:under_the_silence`** | 3 | `listen`, optional | `vftv:silence_beneath` | **permanent 2** | a named milestone; rewards Silentium 6 and Alienis 4 points; parent of nothing |
| **`vftv:learning_the_tongue`** | 4 | `listen`, optional | `vftv:choirs_road`, `vftv:silence_beneath` | **permanent 3** | a named milestone; parent of nothing |

- **The first warp a Listener takes is temporary.** They watch it fade before choosing lasting warp, so the dial is taught by doing.
- **No warp before Stage 3** is a rule over built-in research, not a runtime promise: transferable gear, commands and a research `reset` can give a Stage 1 player warp. The [wands](wands.md#contracts-on-sibling-specs) and [arcane](arcane.md#contracts-on-sibling-specs) specs prove Stage 1 affordable at zero warp from the Wellspring's 32 per aspect, and even 3 warp makes a 32 cost 33 (see "The dials").
- `vftv:under_the_silence` is not `secret`: [research's reveal triggers](research.md#triggers) have no "learned" type, so it is LOCKED until its parent is learned.
- **Options considered:** Stage 2 whispers as foreshadowing. **Why not:** no Stage 2 instrument or rite makes them a choice ([lore, Listening and warp](lore.md#listening-and-warp)), and the progression spec permits early doses without requiring them.

## Warp

### Kinds and the record

Player attachment **`vftv:warp`**, `copyOnDeath` with a serializer (`NF:attachment/AttachmentType.java:234–241`), synced to the owner only ([architecture, State and sync](architecture.md#state-and-sync)):

| Field | Format bounds | Persisted | Meaning |
|---|---|---|---|
| `format` | 1 | yes | |
| `temporary`, `lasting` | 0–1,000 each | yes | `T`, `L` |
| `permanent` | 0–100 | yes | `P` |
| `gear` | 0–100 | yes | `G`: the latest gear sample ("Gear warp") |
| `counter` | 0–10,000 | yes | the Voice counter ("The counter and the chance") |
| `checks` | `long` ≥ 0 | yes | performed checks: the draw index |
| `milestones_paid` | ≤ 16 distinct ids, ≤ 256 characters each | yes | named milestones already paid |
| `silenced` | boolean | yes | set only by Silencing |
| `revision` | `long` ≥ 0, monotonic | yes | every change takes the next value |
| `offer` | at most one (see "Bargains") | **no** | transient: gone at logout |
| `quiet`, `void_work_until`, `grace_until`, the operation guard | | no | transient |

- **Decoding checks format bounds only, never tuning.** A record with an unknown `format`, or one out of these bounds, keeps its raw tag and is written back unchanged with one warning. That player's warp is **unreadable**: every dial is neutral, no check runs, and every commit and apply refuses with `UNREADABLE` (`message.vftv.warp.unreadable`). The [golems](golems.md#sync-and-state) and [foci](foci.md#sync-and-state) specs keep records the same way.
- **Reconciling a lowered tuning** never rewrites a record. A `permanent` above a lowered `permanent.cap` is kept (it can never be removed) and further permanent gains preview as 0. A `counter` above a lowered `counter.max` is **read** as `min(counter, counter.max)` and written clamped at its next change. A `gear` above a lowered `gear.max` is replaced at the next sample. Vector (`counter.max` 1000 → 150 with 900 stored): the chance reads the counter as 150 (165 ‰ at `total` 9); a fired event leaves 120; a gain of 2 leaves 150.
- **Saving:** player attachments are written with the player's data at every save, so a change to `counter` or `checks` alone persists without a dirty flag.

### Sources

The complete list for v1:

| Kind | Gained from | Removed by |
|---|---|---|
| Temporary | an entry's `warp.temporary`, on its checked commit ([research, Warp on entries](research.md#warp-on-entries)); a Bargain's cost; commands | fading (see "Schedule"); Silencing; commands |
| Lasting | an entry's `warp.lasting`; a Bargain's cost; commands | stilling; Silencing; commands |
| Permanent | **only** the named milestones ("Permanent warp"), each at most once per player; commands | commands only |
| Gear | carried items with a `vftv:gear_warp` value ("Gear warp") | putting them down |

- **Listening** is a `listen` or `note` entry's commit. **Void-tier work** reaches warp through the research that teaches it (Stage 4 notes carry warp, authored by [Stage 4 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/37)) and through gear warp.
- **Crafting, casting and infusing apply no warp in v1,** as the [arcane](arcane.md#the-cost), [foci](foci.md#the-void-tier) and [infusion](infusion.md#stability) specs already state, and no previewed extension is proposed here.
- **Options considered:** a temporary charge per `VOID` craft, as TC4 charged per forbidden craft. **Why not:** it needs a previewed, echoed extension in three specs for a dose the research already charges, and the [pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md) ask for fewer, visible sources.

### Total warp

**`total = T + L + P + G`**, an `int` of at most 2,200 by format bounds. Every continuous reader reads `total`; only latched research gates read a narrower sum.

| Reader | Reads | Why |
|---|---|---|
| the vis factor and the infusion penalty | `total` | "the more you sound like the Void, the more your resonant magic costs" ([progression, Warp](progression.md#warp)): carrying Void gear or a fresh Bargain dose is sounding like it |
| the amplifier | `total`, plus the **Joining bonus** for a Joined Listener ("The dials") | the same dial from the other side; a Bargain's temporary dose buys Void power, which is what a Bargain is. The bonus is Joining's "lasting Void power" ([progression, Endgame](progression.md#endgame)), added, never a floor |
| the bands, Voice event eligibility, the chance, the book overlay | `total` | one number, which the Ledger shows |
| research `warp_at_least` with `kind: total` | `total` | [research, Triggers](research.md#triggers) |
| research `warp_at_least` with `kind: lasting_plus_permanent` | `L + P` | **every built-in gate uses this kind** (validated): a key is turned only by warp that stays, never by a spike or a carried item. Once latched, a gate stays turned, so stilling later never locks content again. |

- **Gear never feeds the counter,** so equipping and unequipping can neither farm nor force events.
- **Options considered:** a separate composition per dial (TC4's gates counted only normal and permanent warp). **Why one:** every sibling contract asks "which warp", and one answer lets the Ledger show one number that every dial reads.

### Bands

**`bands` = [8, 20, 40]**, exactly three strictly ascending thresholds on `total`. `band = |{t ∈ bands : total ≥ t}|`, from 0 to 3. This is the research spec's book band ([research, Prose that degrades with warp](research.md#prose-that-degrades-with-warp)), and it also gates and scales Voice events.

Vectors: `total` 7 → 0; 8 → 1; 19 → 1; 20 → 2; 39 → 2; 40 → 3; 2,200 → 3.

### Schedule

Each online player is handled once per server tick, in the server-side `PlayerTickEvent.Post` (`NF:event/tick/PlayerTickEvent.java:46–55` posts on both logical sides; the listener takes server players only). With `g` = `server.overworld().getGameTime()` and **`phase(i)` = `Long.remainderUnsigned(fold([0x57415250 ("WARP"), uuidMsb, uuidLsb]), i)`**, in this order:

1. **Decay:** if `g mod temporary.decay_interval = phase(temporary.decay_interval)` and `T > 0`: `T − 1`, source `DECAY`.
2. **Gear sample:** if `g mod 20 = phase(20)`: sample gear ("Gear warp").
3. **Check:** if `g mod check.interval = phase(check.interval)`: run one check ("Voice events").

Because 20 divides 400 divides 1200 and the phases come from one hash, all three coincide at every default check; this order makes a check read the state after that tick's decay and sample. Phases are recomputed when a `/reload` changes an interval. Online ticks only: an offline player's temporary warp waits for them.

**Vectors** (UUID `00112233-4455-6677-8899-aabbccddeeff`): phases 805, 5 and 5 for 1200, 400 and 20; for `0…0`/`0…1`, 872, 72 and 12; for all ones, 92, 92 and 12. **Coincident schedule:** from `T` 3, `L` 4, counter 70, with an item worth gear 2 carried from tick 790 to 1499: tick 5 decay → `T` 2 (`total` 6); tick 405 → `T` 1 (5); tick 805 decay → `T` 0 (4), **then** the sample → gear 2 (6), **then** check 0 at `total` 6, band 0, which rolls 188 ≥ 85 (quiet); tick 1505 sample → gear 0 (4); tick 2005 check 1 at `total` 4 rolls 859 (quiet).

## The dials

Three pure functions of the owner's synced `(T, L, P, G)` and `milestones_paid`, the synced `WarpRules` and the context kind, so client and server compute the same value. All arithmetic is integer.

```
vis_factor(player, ctx)        = ORDINARY and tax on ? (1000 + min(tax.max_pm, total × tax.per_warp_pm)) / 1000.0 : 1.0
infusion_penalty(player, kind) = ORDINARY and tax on ? min(infusion.max_penalty, floorDiv(total × infusion.per_warp_pm, 1000)) : 0
amplifier_warp(player)         = amplifier on ? total + (joined ? joining.amplifier_bonus : 0) : 0
```

`joined` ⇔ `vftv:joining ∈ milestones_paid`, which is written even when the cap clipped the milestone to 0, because the choice was made ("The commit").

- **The warp tax:** +1% per warp, at most +50% (`tax.per_warp_pm` 10, `tax.max_pm` 500). The [wands quote](wands.md#the-quote) converts with `Math.round(clamp(W, 0, 10) × 1000)`. Each value here is meant as the rational *k*/1000; a `double` division doesn't always hold it exactly, but the rounding recovers *k* (a unit test over every `total` from 0 to 2,200), so the station shows the per mille exactly.
- **The infusion penalty:** 1 stability point per 4 warp, at most 20 (`infusion.per_warp_pm` 250, `infusion.max_penalty` 20), within the [infusion spec's](infusion.md#stability) 0–100.
- **The amplifier:** `total` in warp points, plus the Joining bonus for a Joined Listener. The [foci spec's](foci.md#the-void-tier) `void.per_warp_pm` default is **20** (amended in this change), so a Void working doubles at 50 warp, just past band 3. That is this spec's scale decision, not proven balance.
- **The Joining bonus:** a Joined Listener's amplifier adds `joining.amplifier_bonus` (**10**, 0–50) warp points to `total`, so `amplifier_warp` is an `int` of at most 2,250. It is **additive, never a floor**: current warp still scales Void power, so stilling still costs power ("Stilling"). It changes no band, chance, tax or penalty, and `warp.amplifier` off still zeroes the whole amplifier. Owned by the [Eldritch tier spec](eldritch.md#the-capstones), whose references give the vectors: `total` 37 → 37 unjoined, 47 Joined; 5 Joined → 15; 0 Joined → 10; 40 Joined at bonus 0 → 40; 2,200 Joined at bonus 50 → 2,250; 37 Joined with the switch off → 0.
- **`VOID` is neutral:** factor 1.0, penalty 0. Void power has one lever, the amplifier's magnitude; Void costs stay predictable.
- **Unreadable warp** reads as `total` 0 for every dial, with no Joining bonus.

**Vectors** (`total` → band, factor `ORDINARY`/`VOID` in per mille, penalty `ORDINARY`/`VOID`): 0 → 0, 1000/1000, 0/0; 3 → 0, 1030/1000, 0/0; 7 → 0, 1070/1000, 1/0; 8 → 1, 1080/1000, 2/0; 19 → 1, 1190/1000, 4/0; 20 → 2, 1200/1000, 5/0; 37 → 2, 1370/1000, 9/0; 40 → 3, 1400/1000, 10/0; 49 → 3, 1490/1000, 12/0; 50 → 3, 1500/1000, 12/0; 80 → 3, 1500/1000, 20/0; 2,200 → 3, 1500/1000, 20/0. With the tax off, `total` 40 → 1000, 0.

**Amplifier vectors** at the amended default ([foci, Magnitude](foci.md#magnitude): Force step 250, `max_bonus_pm` 1000; base, Force, `w`, Void → `power_pm`, `amp_pm`, out): 4000, 0, 37, yes → 1000, 1740, **6960**; 4000, 0, 50, yes → 1000, 2000, 8000; 4000, 3, 250, yes → 1750, 2000, 14,000. The old 10-per-mille figure survives only as an explicitly parameterised regression: 4000, 0, 37, yes → 1000, 1370, 5480.

## The commit

One service in `core.warp`, whose records live in `api`:

| Record | Fields |
|---|---|
| `WarpCost` | `temporary`, `lasting`, `permanent`: each −1,000–1,000 |
| `WarpSource` | a kind `ENTRY` \| `BARGAIN` \| `STILLING` \| `DECAY` \| `GEAR` \| `MILESTONE` \| `SILENCING` \| `COMMAND`, and an id for `ENTRY`, `BARGAIN` and `MILESTONE` |
| `WarpPreview` | the actual deltas `dT`, `dL`, `dP`, and `clipped` (the actual deltas differ from the cost) |
| `CommitResult` | a status `OK` \| `STALE` \| `BUSY` \| `INVALID` \| `UNREADABLE`; the actual `WarpPreview` (`OK`) or the current one (`STALE`); the new `revision` (`OK`) |

### Preview

`preview(player, cost, source)` is pure over the record and `WarpRules`. First match:
1. The record is unreadable: `UNREADABLE`.
2. `cost.permanent < 0` and the source isn't `COMMAND`: `INVALID`.
3. `cost.permanent > 0` and the source isn't `COMMAND`: the source must be `ENTRY` or `MILESTONE` with an id listed in `permanent.milestones` **and** `cost.permanent` equal to that milestone's amount, else `INVALID`. An id already in `milestones_paid` previews `dP` = 0.
4. Otherwise clamp: `T' = clamp(T + dT, 0, 1000)`, `L' = clamp(L + dL, 0, 1000)`, and for `dP ≥ 0`, `P' = max(P, min(permanent.cap, P + dP))`, so a lowered cap blocks gains and removes nothing; for a `COMMAND`'s `dP < 0`, `P' = max(0, P + dP)`.

**Vectors** (state `(T, L, P)`, cost, source → actual, clipped): (0, 0, 0), (0, 4, 0), entry `silence_beneath` → (0, 4, 0), no; (3, 4, 9), (0, 0, 2), milestone `under_the_silence`, cap 10 → (0, 0, 1), yes; (0, 998, 0), (0, 5, 0), a bargain → (0, 2, 0), yes; (0, 7, 0), (0, −4, 0), stilling → (0, −4, 0), no; (0, 2, 0), (0, −4, 0) → (0, −2, 0), yes; (0, 0, 4), (0, 0, 2), `under_the_silence` already paid → (0, 0, 0), yes; (995, 0, 0), (9, 0, 0) → (5, 0, 0), yes; `joining` with 4 instead of its 5 → `INVALID`; an unlisted entry with permanent 2 → `INVALID`; (0, 0, 9), (0, 0, 2), command, cap 10 → (0, 0, 1), yes; (0, 0, 9), `joining` 5 under a cap lowered to 6 → (0, 0, 0), yes; (0, 0, 9), (0, 0, −2), command, cap 6 → (0, 0, −2), no.

### Commit and apply

- **`commit(player, cost, source, expected)`**, for every change a player saw previewed (research's listen, learn and buy; a Bargain's acceptance; [#28](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)'s Joining):
  1. the player's **operation guard** is held: `BUSY`;
  2. take the guard; `preview` (`INVALID` or `UNREADABLE` end here);
  3. the preview ≠ `expected`: `STALE`, carrying the current preview;
  4. **write**, with no foreign code between: `T`, `L`, `P`; the milestone id into `milestones_paid` when the source is a listed milestone (even if the cap clipped it to 0, because the choice was made); `counter = min(counter.max, min(counter, counter.max) + gains × counter.per_gain)`, where `gains` is the sum of the positive actual deltas; the next `revision`; sync;
  5. post `WarpChangedEvent` if any delta is non-zero, **while the guard is held**;
  6. release the guard in `finally`; return `OK` with the actual deltas.
- **`apply(player, cost, source)`** is the same without step 3, for server-originated changes that show no echoed preview: decay, stilling and commands.
- **Compound operations** (a Bargain's acceptance, Silencing, a gear sample) take the guard once, make every write through one internal mutation helper, publish one event at the end, and release in `finally`.
- **Re-entry:** a `commit` or `apply` from a listener, or from any callback inside a guarded operation, is refused `BUSY`. Nothing can come between a preview and its charge ([research, Contracts](research.md#contracts-on-sibling-specs)).
- **Commands** are the one exception to the milestone rule: they change any kind, clamped to the format bounds and, for permanent gains, the cap.
- **A throwing `WarpChangedEvent` listener** is logged once; the write stands and is never retried or rolled back, and the remaining cleanup and sync still run. NeoForge's own dispatch to its other subscribers is NeoForge's.

## Stilling

Treating lasting warp. "Silencing" stays the name of the ending.

- **`vftv:stilling_draught`**, stacking to 16, drunk over 32 ticks (`ItemUseAnimation.DRINK`).
- **Starting to drink** is refused on both sides, consuming nothing, when the synced `L` is 0: `message.vftv.warp.nothing_to_still`.
- **On finishing** (server): `L` is read again; if it is 0 now, the same refusal and nothing is consumed. Otherwise `apply((0, −min(still.amount, L), 0), STILLING)`; `BUSY` refuses (`message.vftv.warp.busy`) and consumes nothing; `OK` consumes one draught (none for a player with infinite materials) and returns a glass bottle the way vanilla's potions do.
- **Always works:** no chance. `still.amount` is **4**, and the tooltip states it beside the player's current lasting warp.
- **Always costs:** the recipe (below), and every point stilled also lowers the amplifier, so going quiet weakens Void tools ([progression, Warp](progression.md#warp)). A Joined Listener's Joining bonus is never stilled, but everything else is: stilling from 37 to 5 takes their amplifier from 47 to 15.
- **The recipe** `vftv:stilling_draught` (the shipped body): `{"type": "vftv:crucible", "research": "vftv:being_heard", "catalyst": "minecraft:glass_bottle", "aspects": {"vftv:silentium": 6, "vftv:ordo": 4, "vftv:aqua": 4}, "result": {"id": "vftv:stilling_draught", "count": 1}}`. Silentium is a compound, and the [crucible never combines](crucible.md#the-recipe-type), so it must come from matter: wool (see the contract on [#30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)), named in the content's reviewed bootstrap.
- **Options considered:** a soap with a hidden chance; a ward that only delays events (both TC4's). **Why not:** the lore says treatment "always works but always costs something", and the pain points ask for a visible price.

## Permanent warp

**`permanent.cap` = 10.** Permanent warp comes **only** from the named milestones in tuning `permanent.milestones`, each paid at most once per player, each showing its exact cost before commit:

| Milestone | Where | Permanent | Authored by |
|---|---|---|---|
| `vftv:under_the_silence` | the optional Stage 3 listen | 2 | [Stage 3 research content #36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) |
| `vftv:learning_the_tongue` | the optional Stage 4 listen | 3 | [Stage 4 research content #37](https://github.com/andrewbrogoff/voices-from-the-void/issues/37) |
| `vftv:joining` | the Joining choice | 5 | [Eldritch tier #28](https://github.com/andrewbrogoff/voices-from-the-void/issues/28), through `commit` with the preview it shows |

**Validated** when the world opens **and** when tuning reloads (a failure refuses the world at open, and on `/reload` keeps the previous record with an error):
- the listed amounts sum to ≤ `permanent.cap`, so built-ins never clip;
- every research entry with `permanent` > 0 is a listed milestone with exactly that amount, carries no `temporary` or `lasting`, and is nobody's parent;
- every listed milestone except `vftv:joining` names an existing `listen` entry;
- **`vftv:joining` is listed** (any amount in range): the [Eldritch tier's Heart](eldritch.md#the-choice) reads its current amount for every quote and commit, so a retuning changes what Joining costs and never disables it.

**No built-in gate needs permanent warp, and none waits on a Bargain.** The proof is ordered, through the research spec's affordability paths ([research, Affordability and recovery](research.md#affordability-and-recovery), amended): a path never commits a milestone entry, Bargains are not steps, and every built-in `warp_at_least` gate must latch during its Stage's replay from the `lasting` committed **before** it, so warp behind the very gate being tested can't pay for it.

- **Options considered:** no permanent warp at all. **Why not:** the lore makes the permanent share "what you can never un-hear", a deliberate milestone. 10 against bands of 8, 20 and 40 means permanent warp alone reaches band 1 at most.

## Gear warp

- **Data map `vftv:gear_warp`** on items: an integer 1–10 per carried **stack** (not multiplied by its count), synced, with the tooltip `tooltip.vftv.gear_warp`.
- **Carried** means the player's inventory, armour and off hand, plus their Curios slots when Curios is loaded, functional and cosmetic alike: `neoforge.compat.curios` collects those stacks and passes them in, so progression never needs Curios ([architecture, Soft dependencies](architecture.md#soft-dependencies)).
- **A sample** (see "Schedule") reads at most `MAX_GEAR_SLOTS` (128) stacks **in all**, in a fixed order: the player's vanilla inventory first, in container index order (main, armour, off hand), then Curios entries from the [armor spec's collector](armor.md#the-collector) `CuriosWorn.collect` (slot ids ascending; functional, then cosmetic, by index), whose budget is what remains of the one allowance. Empty stacks don't count. It sums their values, capped at `gear.max` (**12**); with the server switch `warp.gear` off it is 0. A changed sum is written under the guard (a held guard skips that sample; the next comes 20 ticks later), synced, and posts `WarpChangedEvent(GEAR)`. A sample also runs in the same tick as a login, a respawn and a dimension change.
- **The dials read the latest sample,** at most 20 ticks old. An inventory change not yet sampled is not yet in any quote; the next sample changes the price inputs, which the [arcane](arcane.md#preview-revision) and [infusion](infusion.md#the-authoritative-preview) previews already watch.
- **Void foci are not gear warp.** A Void focus already scales with warp; adding warp for carrying it would compound. That is a balance choice: should it change, the platform collector could read `FocusService.kind` and pass a contribution in, with no import of `core.focus` here.
- This spec ships no `vftv:gear_warp` values. Void-tier armour and weapons ([#24](https://github.com/andrewbrogoff/voices-from-the-void/issues/24), [#28](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)) add them.

## Voice events

### The counter and the chance

- **Gains raise the counter:** on every commit or apply, `counter = min(counter.max, min(counter, counter.max) + gains × counter.per_gain)`, `gains` being the positive actual deltas of `T`, `L` and `P`. Decay, stilling and gear never raise it.
- **Fired events lower it:** `counter = max(0, min(counter, counter.max) − counter.per_event)`.
- **The chance, per check, in per mille:** `p = total == 0 ? 0 : min(chance.max_pm, chance.base_pm + P × chance.per_permanent_pm + min(counter, counter.max) × chance.per_counter_pm)`. This one formula is what both the check and the Ledger's `chance` fact use, so a Listener with no warp reads 0%.
- **The background:** a Listener who stops gaining settles to `15 + 5P` per mille at each check (1.5% to 6.5%): faint, set by permanent warp, never zero while any warp remains ([progression, What the Voices do](progression.md#what-the-voices-do)). A Listener with no warp hears nothing: `p` is 0, and the check is skipped as well (suppression row 4).
- **Options considered:** TC4's counter, which jumps to the total and fires on a square-root roll. **Why ours:** a linear per-mille chance is integer, identical on every runtime, and the Ledger can print it exactly with its parts.

### Suppression

A check is **skipped** (no draw; `checks`, the counter and everything else unchanged) when, first match:

| # | Condition |
|---|---|
| 1 | the warp record is unreadable |
| 2 | `silenced` |
| 3 | the server switch `warp.voices.enabled` is off |
| 4 | `total` is 0 |
| 5 | the player is dead or a spectator |
| 6 | `g < grace_until`, set to `g + grace` (600 ticks) at login, respawn and every dimension change |
| 7 | the player has a menu open: `Player.hasContainerOpen()` (`MC:world/entity/player/Player.java:1386–1387`), which covers the Commonplace Book's `CommonplaceMenu`, the Worktable and the Lathe |
| 8 | the player's `quiet` flag is set |

**The `quiet` flag** covers screens the server can't see: the inventory, chat, pause, sleep and loading screens.
- The client sends C2S **`VoiceQuiet(boolean open)`** whenever `Minecraft.gui.screen()` (`MC:client/gui/Gui.java:221`; `MC:client/Minecraft.java:293`) changes between null and non-null, polled in `ClientTickEvent.Post` (`NF:client/event/ClientTickEvent.java:30–36`), and **reasserts** the current value whenever its `LocalPlayer` or `ClientLevel` instance changes (join, respawn, dimension change), because a replaced player sees no new transition.
- The server stores each packet's value as it arrives: one O(1) write, no reply, so the **last** value of a tick wins. The flag resets to false at login; the grace period covers the gap before the client's reassertion.
- **Trust:** it can only quiet its sender. A modified client that holds it forever opts out of the Voices, **including in-context danger**, which muting audio would not do. The progression spec requires client-only screens to suppress checks, and this is the only way the server can learn of them; the limitation is accepted and stated.

### Selection and draws

A check that isn't skipped draws from **one `java.util.Random(fold([0x564F4943 ("VOIC"), uuidMsb, uuidLsb, checks]))`**, and `checks` then increments, whatever the outcome. Every draw is `nextInt(bound)` with `bound` ≥ 1, or `nextLong()`. In order:

1. **Roll:** `roll = nextInt(1000)`; the check goes on only if `roll < p`.
2. **Eligibility,** over the `vftv:voice_event` registry in ascending id: the event's type is on in server config; `min_band ≤ band ≤ max_band`; if `context`, the player is in context ("Context"); a `vftv:bargain` event needs no live offer and at least one eligible bargain ("Bargains"); a `vftv:insight` event needs at least one known aspect. **None eligible:** the check ends with no further draw.
3. **Pick:** `nextInt(Σ weight)` over the eligible events' cumulative weights.
4. **Cue seed:** `cueSeed = nextLong()`.
5. **Server parameters,** by type: `vftv:insight` draws `known[nextInt(n)]`, the known aspects in ascending id; `vftv:bargain` draws `nextInt(Σ weight)` over the eligible bargains in ascending id; `vftv:spawn` draws its placement (see "Event types"). Other types draw nothing here.
6. **Run** the server part, then settle the outcome.

**Outcomes:**

| Outcome | When | `checks` | Counter | Cue |
|---|---|---|---|---|
| `SKIPPED` | a suppression row | unchanged | unchanged | none |
| `QUIET` | `roll ≥ p` | +1 | unchanged | none |
| `NONE` | nothing eligible | +1 | unchanged | none |
| `FAILED` | the server part did nothing: a `status` event applied no effect, or a `spawn` event spawned nothing | +1 | unchanged | none |
| `FIRED` | otherwise | +1 | − `counter.per_event` | sent, **except** after a danger callback made the Listener ineligible (see "Danger") |

**The one exception** is a danger event whose callbacks silence the Listener, open a menu, set `quiet` or otherwise make a suppression row hold: the counter settles from what actually succeeded (`FIRED` or `FAILED` as above), but no cue is sent.

A client-side type always settles `FIRED`: the server can't see whether the client placed anything, and a client that finds no place shows nothing (stated).

**Vectors** (UUID above):
- After `being_heard` (`T` 3), `silence_beneath` (`L` 4) and `under_the_silence` (`P` 2): counter 90, `total` 9, band 1, `p` 115. Checks 0–3 are quiet (rolls 188, 859, 766, 549); check 4 rolls 26, picks 12 of 88 → `vftv:footsteps`, cue `0DF0AA695B97CC14`, counter 60; checks 5–11 are quiet at `p` 85 (rolls 584, 671, 532, 604, 677, 595, 388).
- `total` 52, band 3, `P` 10, counter 400, in context, an offer open, 20 known: checks 100 and 101 quiet (528, 659); check 102 rolls 71 < 250, picks 11 of 104 → `vftv:false_stone`, cue `611DA5A7DEEFDC87`, counter 370; checks 103–105 quiet (486, 862, 704).
- `total` 25, band 2, counter held at 1000, no context, no offer, 7 known, the first of each type: check 203 → `vftv:whisper` (pick 78 of 102, cue `339C9C5D43AE62AC`); 204 → `vftv:false_stone` (10, `0D874E6671D1C117`); 207 → `vftv:offer` (55, `210DB43E85A0C2EC`), bargain `vftv:quick_hands`; 211 → `vftv:watcher` (60, `2A5CEE7D8F68C7E8`); 212 → `vftv:insight` (43, `FDBDCB55FA6CDBB7`), aspect 3 of 7; 228 → `vftv:drained` (7, `D6C2A8E3AADAB044`); 233 → `vftv:footsteps` (18, `76CAA311F155588D`).
- The same check 207 with only `vftv:keen_eyes` eligible draws bargain `vftv:keen_eyes`.
- **Empty eligibility:** `total` 5, band 0, no known aspect, whisper switched off, counter 1000: check 509 rolls 168 < 250, `NONE`, counter still 1000.
- The background: `total` 4, `P` 0, counter 0 → 15 ‰; `total` 0 → 0 ‰; `P` 10 → 65 ‰.

### Event types

A code registry, **`vftv:voice_event_type`**: types in code, instances in data. Every type plays its cue only on the owner's client ("What a Listener perceives").

| Type | Server part | Client part |
|---|---|---|
| `vftv:whisper` | none | a Voice line from the band's pool |
| `vftv:insight` | credits `points` points of the drawn aspect through the [aspects](aspects.md#aspect-points) knowledge service: a known aspect only, never a discovery | a line from the `insight` pool; the HUD line `voice.vftv.insight_gain` with the aspect's name and the points |
| `vftv:bargain` | creates the offer ("Bargains") | a line from the `offer` pool |
| `vftv:sound` | none | a phantom sound |
| `vftv:colour` | none | the colour drain |
| `vftv:phantom_block` | none | disguised blocks |
| `vftv:phantom_mob` | none | an apparition |
| `vftv:status` (context only) | each listed effect through **one** `addEffect` call ([below](#danger)) | none of its own |
| `vftv:spawn` (context only) | the placement below | none of its own |

#### Danger

**Physical danger only in context** ([progression, What the Voices do](progression.md#what-the-voices-do)): the two server-side danger types are valid only on events with `context: true`.

**Danger eligibility,** rechecked **before every danger operation after the first** (each later `addEffect`, each later spawn attempt): the Listener is online, alive and in the same level; no suppression row holds (so not silenced, no menu open, `quiet` unset, past grace); and they are still in context ("Context"), read afresh. Foreign callbacks can teleport, silence or open a menu for the Listener, so nothing from before a callback is trusted after it. When the recheck fails, the remaining operations of that event are skipped, and the outcome follows the exception under "Outcomes".

**`vftv:status`:** the listed effects (1–3), in list order, each through **one** vanilla `addEffect(new MobEffectInstance(effect, duration, amplifier))`. That call runs NeoForge's applicability callback and the `Added` callbacks inside itself (`MC:world/entity/LivingEntity.java:1047–1066`; `NF:common/CommonHooks.java:1588–1590`); its return value is the result, and nothing probes applicability separately. `FIRED` if any call returned true, else `FAILED`. A throw is logged once and counts as false. Whatever the callbacks did stays: native and addon side effects are disclosed, never rolled back.

The built-in `vftv:pressing_dark`: Darkness 0 and Slowness 0 for 100 ticks each. Darkness honours the client's own `darknessEffectScale` (`MC:client/Options.java:1427`).

**`vftv:spawn`:** up to `count` entities (1–3), from **one budget of `MAX_SPAWN_ATTEMPTS` (16) attempts shared by all the entities in the event**, drawn from the check's `Random`, in order until `count` have counted or the budget is spent. Each attempt:
1. `dx = nextInt(2·max + 1) − max`, `dz` likewise. The attempt goes on only if `min² ≤ dx² + dz² ≤ max²` (`min_distance` and `max_distance`, default 8 and 24).
2. **Column:** from the player's block y + 4 down to y − 8 (at most 13 cells), the first y where the cell is entity-ticking (`ServerLevel#isPositionEntityTicking`, `MC:server/level/ServerLevel.java:1823`), the block below has a sturdy top face, the entity type's box at `(x + 0.5, y, z + 0.5)` has no collision, and every player in the level is at least `min_distance` away in three dimensions. No chunk is loaded. No such y ends the attempt.
3. **Spawn:** create it with spawn reason `EVENT`, place it, and `addFreshEntity`. Join listeners may cancel or move it.
4. **Check the insertion:** it counts only if `addFreshEntity` returned `true` and, read afterwards, the entity is alive in this level, still in the chosen cell, its box has no collision, and every player in the level is still at least `min_distance` away. Otherwise our entity is discarded (if it was added) and doesn't count.
5. **Recheck danger eligibility** before the next attempt; when it fails, stop.

`FIRED` if at least one counted, else `FAILED`. Entities that counted stay. An entity type outside the tag **`vftv:voice_spawnable`** is rejected at load. **v1 ships no spawn instance:** [#27](https://github.com/andrewbrogoff/voices-from-the-void/issues/27) and [#28](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) may add instances for their own Void-touched mobs under these rules. The Answerers are never Voice spawns.

### Context

A player is **in context** iff any of:
1. **Void work:** `g < void_work_until`, which is set to `g + context.window` (600 ticks) when that player's `VisSpentEvent` carries `kind` `VOID` ([wands, Events](wands.md#events)). The `neoforge.warp` listener calls `WarpService.noteVoidWork(player)`; `core.warp` never imports `core.vis`.
2. **A Void-touched dimension:** the player's level is in tuning `context.dimensions` (by default the [Eldritch tier's](eldritch.md#vuulas) `vftv:vuulas`).
3. **A Void-touched place:** any **place query** registered with `WarpService.registerPlaceQuery(VoidPlaceQuery)` answers true for the player's level and block position. A query is a plain interface in `core.warp`, registered at init by the feature that owns the place ([Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26) for the Hush, [Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27) for Choir sites, [Eldritch tier](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) for the Road: its `RoadPlaceQuery`, true iff the level is Vuulas and the position is in the Road's column, O(1) over `RoadLayout` with no chunk read; its Thresholds are Choir sites). It must be pure, read only loaded data, never load a chunk, and do work bounded by what is indexed for that position's chunk.

**`WarpService.inVoidTouchedPlace(level, pos)`** is clause 3 alone, as a pure read: any registered query answers true. The [Eldritch tier's Hollow Bell](eldritch.md#ringing-the-hollow-bell) descends only where it holds, so recent Void work or a listed dimension never counts there.

- **Why a query and not a structure lookup:** `StructureManager.getStructureWithPieceAt` follows structure references through other chunks with loading `getChunk` calls, then scans pieces (`MC:world/level/StructureManager.java:50–80,109–148`), so it can't promise bounded, loaded-only work.

## Bargains

### What they offer

A datapack registry **`vftv:bargain`** (server-only, fixed when a world opens), with types in code:

| Type | Fields | Power |
|---|---|---|
| `vftv:effect` | `effect` (a mob effect id), `amplifier` 0–2, `duration` 200–24,000 ticks | that effect, through one `addEffect` call |
| `vftv:points` | `points`: 1–4 aspects, each 1–64 | those points, through the knowledge service, to known aspects only |

Every bargain also has `cost` `{"temporary": 0–20, "lasting": 0–20}`, at least one > 0 (**permanent is rejected**: permanent warp stays the milestones' alone), and `weight` 1–1000. At most `MAX_BARGAINS` (64).

**The shipped roster:**

| Id | Power | Cost | Weight |
|---|---|---|---|
| `vftv:keen_eyes` | Night Vision (amplifier 0) for 6,000 ticks | temporary 3 | 30 |
| `vftv:quick_hands` | Haste (amplifier 1) for 3,600 ticks | temporary 2, lasting 1 | 20 |
| `vftv:unheard_insight` | 6 Vacuos and 4 Silentium points | lasting 2 | 10 |

**A bargain is eligible** at a check iff its cost's preview is not clipped (the Voices never offer power for nothing at a cap), and for `vftv:points`, every one of its aspects is known.

### The offer

- A fired `vftv:bargain` event creates the **offer**: `{seq, bargain id, the resolved power, cost, expires = g + bargain.ttl}` (`ttl` 12,000 ticks). `seq` is a per-login counter starting at 1. At most one offer exists. It is synced with the record.
- **The offer is transient.** It is deleted when it is accepted or refused, when it expires, at death, at logout and by Silencing. Expiry is lazy: an expired offer counts as absent everywhere, and the next check, quote or action deletes it. Because offers never outlive a session, they never outlive a pack reopen either.
- **Deciding** happens only in the Commonplace Book's Ledger ("In the Commonplace Book"), through the research spec's `accept_offer` and `refuse_offer` actions.
- **Refusing costs nothing, ever:** pressing Refuse, letting it expire, dying or logging out removes the offer and nothing else, and no Voice line rebukes it.

### Accepting

`ResearchAction.accept_offer(offerSeq, echoedWarp)`, after the research spec's rate, request-id and menu checks and with its refusal feedback. **This spec owns every offer check after those**, so the research spec runs no offer eligibility of its own. Inside one guarded compound operation, first match refusing with no warp, research or knowledge change:
1. The guard is held: `BUSY` (`message.vftv.warp.busy`).
2. **Offer validation and lazy cleanup:**
   - no offer → `message.vftv.warp.offer_gone`;
   - the offer has expired → it is **deleted**, `offer_gone`;
   - `seq` ≠ `offerSeq` → `offer_gone`, and **the live offer is kept** (a stale action never touches a newer offer);
   - the bargain id is missing from the registry, or a `points` aspect is missing or no longer known → the offer is **deleted**, `offer_gone`.

   These deletions of an expired or invalid offer are the only state a refusal may change.
3. `preview(cost, BARGAIN(id))` is clipped → `message.vftv.warp.offer_full`; the offer stays.
4. The preview ≠ `echoedWarp` → the research spec's "The cost has changed." with fresh quotes.
5. **Grant** the power: one `addEffect` call, or the points credit. `addEffect` returning false (an applicability callback said no, or a stronger effect is already active) → `message.vftv.warp.offer_cannot`; nothing is charged and the offer stays. A throw is logged once and counts as false; whatever native or addon side effects happened stay, and no general rollback is promised.
6. On a grant, with no foreign code between: write the warp (as a commit's step 4), delete the offer; then post `WarpChangedEvent(BARGAIN(id))` and play `bargain.accept`; release in `finally`.

Nothing is rechecked after the grant: the guard kept warp from changing, and once the power is given the price is paid.

`refuse_offer(offerSeq)`, after the same research checks, under the guard: no offer → `offer_gone`; an expired offer → deleted, `offer_gone`; `seq` ≠ `offerSeq` → `offer_gone`, the live offer kept; otherwise the offer is deleted. It changes nothing else, ever.

## What a Listener perceives

Everything in this section runs on the owner's client and never touches server state. Other players never see or hear it; only danger effects are real and visible to them.

### Cues

- **S2C `VoiceCue`**, to the owner only: the event id, the type id, the band (0–3), `cueSeed`, and for an insight the aspect id and points.
- **Held while a screen is open:** at most one cue (a newer one replaces it), for at most `MAX_CUE_HOLD` (200 ticks). It plays when the screen closes, or is dropped. A screen opened within one round trip of a check can therefore coincide with a status effect starting: a latency-bounded race, stated.
- **Cleared** (the playing line stopped, the colour drain ended, phantoms removed, any held cue dropped) on S2C **`VoiceClear`** (sent by Silencing), on a level change, on a `LocalPlayer` replacement, and at logout.
- **Client draws** come from `java.util.Random(cueSeed)` in the orders below. A pool is sorted by id (`ResourceLocation.toString()` with `String.compareTo`). An empty pool or list draws nothing and shows nothing, so `nextInt(0)` is never called.

### Voice lines

- **Pools:** `band0`, `band1`, `band2`, `band3` (8 lines each), `insight` (4), `offer` (4), and the ending pools `joining` and `silencing` (3 each), which #28 plays at the choice. That is 46 lines in all.
- **The language ramp** ([lore, How the Voices sound](lore.md#how-the-voices-sound)): `band0` and `band1` are whispered English fragments, almost intelligible; `band2` mixes English with Void-tongue words; `band3` is Void-tongue only.
- **Which cue plays a line:** a whisper plays from `band<b>` with *b* the cue's band; an insight plays from `insight`; an offer plays from `offer`. No other type plays a line.
- **Draws:** the line `pool[nextInt(n)]`, then a whisper's side `nextInt(2)` (0 left, 1 right).
- **Playback:** on `SoundSource.VOICE` (`MC:sounds/SoundSource.java:13`), so vanilla's Voice slider sets its volume; relative to the listener, offset one block to the drawn side, at volume 1.0 and pitch 1.0. One line at a time: a line cue arriving while a line plays is dropped.
- **The Voice text:** our own HUD layer (`RegisterGuiLayersEvent`), centred above the hotbar, at most 240 logical pixels wide and wrapped to at most 3 lines, in italic: `amethyst` for a line with any `void` segment, `hush3` otherwise. It shows for the clip's length plus 20 ticks. It is **on by default, independent of vanilla's subtitle option**, because the lore requires text with every Voice line; the client option `voices.text` is its opt-out. Never chat.
- **Lang:** line `<ns>:<path>` is the sound event `<ns>:voice.<path>` and the lang key `voice.<ns>.<path>`.

### Perception effects

| Type | Draws, in order | What it does |
|---|---|---|
| `vftv:sound` | `sounds[nextInt(n)]` over the `phantoms` resource's list; `yaw = nextInt(360)`; `dist = 4 + nextInt(7)` | `ClientLevel.playLocalSound` (`MC:client/multiplayer/ClientLevel.java:738`) at the player's feet + (−sin yaw · dist, 1, cos yaw · dist) on `SoundSource.AMBIENT`, volume 1.0, pitch 1.0, no distance delay |
| `vftv:colour` | none | the colour drain (below) |
| `vftv:phantom_block` | per probe: `dx = nextInt(33) − 16`, `dy = nextInt(9) − 4`, `dz = nextInt(33) − 16` | disguises (see "Phantoms") |
| `vftv:phantom_mob` | per attempt: `yaw = nextInt(360)`, `dist = 12 + nextInt(9)` | an apparition (see "Phantoms") |

**The colour drain** is a fullscreen desaturation (the Hush's subtraction, [lore, The Hush](lore.md#the-hush-replaces-taint)), drawn by our own pass with a per-frame uniform, as the [architecture spec](architecture.md#rendering) plans for warp intensity:
- **Peak:** 25%, 45% and 65% at bands 1, 2 and 3; nothing at band 0. It ramps in linearly over 50 ticks, holds for 200 and ramps out over 50.
- **Photosensitivity:** desaturation only, with luminance preserved (Rec. 709 weights), no flash, no oscillation, and at most 1.3% change per tick.
- **Scaled** by vanilla's `screenEffectScale` (`MC:client/Options.java:1419`), and off entirely with `voices.colour_shift` off.
- **A new colour cue** while one is active ramps from the current intensity to the new peak.

### Phantoms

**The fork never lies** ([aspects, Phantoms](aspects.md#what-a-strike-reveals)): phantoms have no server identity, so whatever the fork strikes is what is really there.

**Apparitions** (phantom mobs):
- **The entity type `vftv:apparition`:** a plain `Entity` with no health and no AI, built with `noSummon()` and `noSave()` (`MC:world/entity/EntityType.java:596,601`); `isPushable` false, `canBeCollidedWith` false, `isPickable` true (`MC:world/entity/Entity.java:2017,2025,2384`). The server never creates one; one that exists server-side is discarded on its first tick.
- **Client-only,** added with `ClientLevel.addEntity` (`MC:client/multiplayer/ClientLevel.java:546–550`) under a **negative id** that `getEntity` shows is free. Server ids come from an incrementing counter (`MC:server/level/ServerLevel.java:323–330`), so negative ids are a practical namespace, not a proof. If the server ever sends a real entity with that id, `addEntity` removes ours first (`:548`), and the apparition simply vanishes. A cancelled client `EntityJoinLevelEvent` (`:547`) means no apparition.
- **Placement:** up to 16 attempts with the draws above. Each scans a column from the player's eye y + 4 down to y − 8 for the first cell in a loaded chunk whose block below is a full opaque cube and whose cell and the one above are air. It stands at the cell's bottom centre and turns to face the player. An existing apparition vanishes first.
- **It vanishes** when touched, in the first of: an attack or use input whose crosshair hit is the apparition; the player within 4 blocks; 400 ticks; a level change. It plays `apparition.vanish`.
- **No packet:** our `InputEvent.InteractionKeyMappingTriggered` listener cancels that input **and calls `setSwingHand(false)`** (`NF:client/event/InputEvent.java:314–325`). The call sites return before any attack or use processing, and swing only when asked to (`MC:client/Minecraft.java:1689–1693,1744–1748`); a permitted swing would send `ServerboundSwingPacket` (`MC:client/player/LocalPlayer.java:327–329`). So a strike at an apparition sends nothing, shows silence, and **never falls through to sounding at air**. Even a sent attack or interact would find no target (`MC:server/network/ServerGamePacketListenerImpl.java:1849–1851,1878–1880`).
- **Lore:** an apparition is a glimpse of a Choir figure, robed and faceless, never a vanilla mob, so nothing hostile ever seems to spawn on the Listener and nothing is confused with vanilla's Phantom.

**Disguises** (phantom blocks):
- **A disguise** is a real block drawn as another. The `phantoms` resource's `disguises` list pairs a block tag (`real`) with a block (`shown`).
- **Placement:** count = `max(1, 2 · (band − 1))`, so 2 at band 2 and 4 at band 3. Up to `MAX_PHANTOM_PROBES` (64) probes with the draws above, relative to the player's block position. A probe is accepted iff 36 ≤ dx² + dy² + dz² ≤ 256; the chunk is loaded on the client; the cell isn't already disguised; its block is a full opaque cube; the first disguise in list order whose `real` tag holds that block shows a different block; and at least one of the six neighbours isn't a full opaque cube, so it can be seen. At most `MAX_PHANTOM_BLOCKS` (4) at once: the oldest goes first.
- **Drawn** as the shown block's model scaled 1.002 about the cell's centre, with the cell's light, so it covers the real block; no chunk is rebuilt and the level is unchanged.
- **Why disguises only:** collision, targeting and the server all stay real. Walking, mining and the fork all reach the real block, so the [aspects](aspects.md#what-a-strike-reveals) row "a phantom over a real block reads the real block" holds by construction, and a phantom over air never arises. A phantom over air would need collision (and moving onto it would desync the player from the server) or a client picking override.
- **It vanishes** when touched, in the first of: an attack, use or strike whose crosshair hit is its cell (the input goes on to the real block); the player's box, grown by 0.05, touching the cell; a change of the cell's block state (checked each tick, at most 4 reads); 600 ticks; a level change.

### Accessibility

**Client config:** `voices.text` (on), `voices.colour_shift` (on), `voices.phantoms` (on; disguises and apparitions), `voices.phantom_sounds` (on). The research spec's `book.clear_prose` is unchanged. Turning one off changes nothing on the server: the counter and every outcome stay as they were. The volume of Voice lines is vanilla's Voice slider; the colour drain also follows `screenEffectScale`, and Darkness follows `darknessEffectScale`.

## In the Commonplace Book

The [research spec](research.md#structure) owns the book. This spec supplies its numbers (amended there in this change):
- **The Ledger's warp spread** ([research, GUI contract](research.md#gui-contract)): on the left page, the kinds, gear, `total` (and, for a Joined Listener, the Joining bonus the amplifier adds, `ledger.vftv.warp.joined`), the band and the next threshold, the counter, and the **chance per eligible check** with the check's interval in seconds and every part of the formula; on the right page, the offer card with **Accept** and **Refuse**.
- **Fact spans:** the existing `warp:<kind|total>` grows `band`, `counter` and `chance`; `entry:<ns>:<path>:warp` shows the reader's own preview; a new `warp_rules` source states every tuning number.
- **Two actions,** `accept_offer` and `refuse_offer`, each counted as a commit under the research spec's one-commit-per-tick rule; the offer's preview travels in its quotes.

## Joining and Silencing

[Eldritch tier #28](https://github.com/andrewbrogoff/voices-from-the-void/issues/28) owns the choice and keeps it in the world. This spec never imports #28; #28 calls `core.warp`:
- **Silencing:** `WarpService.silence(player, expected)`, one guarded compound operation, checked like a commit. First match: the guard is held → `BUSY`; the record is unreadable → `UNREADABLE`; already silenced, or `expected` isn't the current `(−T, −L, 0)` preview → `STALE` with the current one. Otherwise `T` and `L` to 0, `silenced` set, the offer deleted and the counter to 0, all written before one `WarpChangedEvent(SILENCING)` is posted with the deltas, while the guard is held; then `VoiceClear`; the guard released in `finally`; `OK`. A throwing listener is logged once and the silencing stands, `VoiceClear` included. A silenced Listener is never checked again, so the Voices stop for them. Permanent and gear warp stay, the dials still read `total`, and later gains still apply, taxed and visible, so nothing silently stops working.
- **Joining:** #28 shows `preview((0, 0, J), MILESTONE(vftv:joining))`, with J the milestone's current amount in `permanent.milestones` (5 as shipped), and calls `commit` with it, at most once (`milestones_paid`). The Voices continue for a Joined Listener, and their amplifier gains the Joining bonus ("The dials"). No event field distinguishes Joined Listeners in v1.
- **The record is the ending's only authority:** Joined ⇔ `vftv:joining ∈ milestones_paid`; Silenced ⇔ `silenced`. #28 writes no ending of its own and derives everything from these ([Eldritch tier, The ending authority](eldritch.md#the-ending-authority)). **`WarpService.isSilenced(player)`** is a pure read of the flag (false for an unreadable record), for the [Hush spec's](hush.md#where-it-begins) attendance and Hollows without a dependency on #28.

## Commands

`/vftv warp …`, permission level 2. Every change goes through `apply` with source `COMMAND`.

| Command | Effect |
|---|---|
| `get <player>` | prints `T`, `L`, `P`, gear, `total`, band, counter, `p` and the offer |
| `set <player> <temporary\|lasting\|permanent\|counter> <n>` | sets that value, clamped to its format bounds; a permanent gain also to the cap |
| `add <player> <temporary\|lasting\|permanent> <n>` | adds `n` (−1,000–1,000), clamped the same way |
| `clear <player>` | `T`, `L`, `P` and the counter to 0; `milestones_paid` is kept |
| `fire <player> <event>` | runs that event's server part and cue, skipping the roll and eligibility but **not** the suppression rows, and settles its outcome |

## Authored content

The content tickets deliver authored inputs under `content/`, following the research spec's [authored-content conventions](research.md#authored-content): CC-BY-4.0 data, read by future datagen. Every file is one JSON object with a `"schema"` string. Datagen rejects an unknown schema, an unknown or missing required field, a wrong type, a value out of range, or an entry that fails its type's rules. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**

```
content/lexicon/void_tongue.json   the Void-tongue lexicon and the ending lines' text
content/warp/events.json           the Voice events
content/warp/bargains.json         the bargains
content/warp/phantoms.json         disguises and phantom sounds
content/warp/voice_lines.json      the Voice line script
content/warp/recipes.json          the Stilling Draught
content/warp/bootstrap.json        the reviewed physical sources for that recipe
content/warp/fixtures.json         the vector tables, transcribed
content/lang/en_us/warp.json       every key under "Lang keys"
content/lang/en_us/voices.json     every Voice line's text
```

| Input | Schema | Generated output |
|---|---|---|
| `void_tongue.json` | `vftv:lexicon/1` | nothing: read by the script, the foci content and the phonology lint |
| `events.json` | `vftv:voice_events/1` | each event `<ns>:<path>` → `data/<ns>/vftv/voice_event/<path>.json` |
| `bargains.json` | `vftv:bargains/1` | each bargain → `data/<ns>/vftv/bargain/<path>.json` |
| `phantoms.json` | `vftv:phantoms/1` | `assets/vftv/voices/phantoms.json` |
| `voice_lines.json` | `vftv:voice_lines/1` | `assets/vftv/voices/lines.json`, and a `sounds.json` entry `<ns>:voice.<path>` per line |
| `recipes.json` | `vftv:warp_recipes/1` | `data/vftv/recipe/stilling_draught.json`, its body unchanged |
| `bootstrap.json` | `vftv:warp_bootstrap/1` | nothing: read by the review |
| `fixtures.json` | `vftv:warp_fixtures/1` | nothing: read by the unit tests |
| `lang/<lang>/warp.json`, `voices.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json`; a duplicate key across `content/lang` fails datagen |

### The lexicon

`{"schema": "vftv:lexicon/1", "words": {<word>: <word entry>}, "names": {<lang key>: <text>}, "ending_lines": {<line id>: <ending line>}}`.

| Record | Fields |
|---|---|
| `<word>` | the romanised word, lowercase |
| `<word entry>` | `gloss` (English, 1–64 characters); `stress` (the stressed syllable, 1-based); `uses` (≥ 1 of `"voice"`, `"name"`, `"ending"`) |
| `names` | exactly `focus_effect.vftv.unmaking` and `focus_effect.vftv.undertow`, each 1–3 words of the lexicon, capitalised as proper item names |
| `<ending line>` | `pool` `"joining"` or `"silencing"`; `segments` (see "Language segments"; the concatenated text ≤ 80 characters); `gloss` (English, required iff a segment is `void`) |

- **Exactly** 18–24 words, and 6 ending lines, 3 in each pool.
- **The phonology lint** ([lore, Void-tongue phonology](lore.md#void-tongue-phonology)) checks every word, every name word and every word of a `void` segment: consonants only from s, sh, h, l, r, m, n, v, th, w, y; syllables (C)V(V)(C), closed only by s, l, r, n or h; vowels a, e, i, o, u with doubles for length; `stress` on the first long vowel, else the first syllable.
- **Case:** Void-tongue is **lowercase in prose**, so a `void` segment's words are lowercase; only `names` capitalise their first letter, as proper item names ([lore](lore.md#void-tongue-phonology)). The lint compares a name's words with the lexicon after lowercasing that first letter.
- **Rejected:** a word failing the lint; a `stress` that disagrees with the rule; a name or `void` segment using a word that isn't in `words`; an uppercase letter in a `void` segment; a missing name or a pool count other than 3.

#### Language segments

A line's text is a list of **`segments`**, each `{"lang": "en" | "void", "text": <string>}`, whose concatenation, in order, must equal the line's lang value exactly.
- **Tokenising a `void` segment:** split on spaces; strip each token's leading and trailing punctuation from `. , ; : ! ? … — ' "`; every remaining token must be non-empty, all lowercase letters a–z, a word in the lexicon's `words`, and pass the phonology lint. Punctuation and spaces may sit in either kind of segment.
- **`en` segments** aren't linted for phonology.
- **Rules by pool:** `band0` and `band1`: `en` only; `band2`: **at least one segment of each** language; `band3`: `void` only; `insight`, `offer`, `joining`, `silencing`: any mix.
- Adjacent segments of the same language are rejected, so each line has exactly one segmentation.

```json
{"schema": "vftv:lexicon/1",
 "words": {"saaren": {"gloss": "hear", "stress": 1, "uses": ["voice", "ending"]}},
 "names": {"focus_effect.vftv.unmaking": "Saaren"},
 "ending_lines": {"vftv:joining_0": {"pool": "joining", "segments": [{"lang": "void", "text": "saaren."}],
                                     "gloss": "Hear."}}}
```

### Voice events

`{"schema": "vftv:voice_events/1", "events": {<id>: <event>}}`, at most `MAX_EVENTS` (64).

| Field | Required | Range | Meaning |
|---|---|---|---|
| `type` | yes | a `vftv:voice_event_type` id | |
| `weight` | yes | 1–1000 | |
| `min_band` | no, default 0 | 0–3 | |
| `max_band` | no, default 3 | `min_band`–3 | |
| `context` | no, default false | boolean | eligible only in context; **required true** for `status` and `spawn` |
| `points` | `insight` only | 1–64 | |
| `effects` | `status` only | 1–3 of `{"effect": <mob effect id>, "amplifier": 0–4, "duration": 20–600}` | |
| `entity` | `spawn` only | an entity type in `vftv:voice_spawnable` | |
| `count` | `spawn` only | 1–3 | |
| `min_distance`, `max_distance` | `spawn` only, defaults 8, 24 | 8–24, `min_distance`–32 | |

**The shipped roster** (the content ticket ships exactly these):

```json
{"schema": "vftv:voice_events/1", "events": {
  "vftv:whisper":       {"type": "vftv:whisper",       "weight": 40},
  "vftv:insight":       {"type": "vftv:insight",       "weight": 15, "points": 3},
  "vftv:footsteps":     {"type": "vftv:sound",         "weight": 15, "min_band": 1},
  "vftv:drained":       {"type": "vftv:colour",        "weight": 10, "min_band": 1},
  "vftv:offer":         {"type": "vftv:bargain",       "weight": 8,  "min_band": 1},
  "vftv:false_stone":   {"type": "vftv:phantom_block", "weight": 8,  "min_band": 2},
  "vftv:watcher":       {"type": "vftv:phantom_mob",   "weight": 6,  "min_band": 2},
  "vftv:pressing_dark": {"type": "vftv:status",        "weight": 10, "min_band": 2, "context": true,
                         "effects": [{"effect": "minecraft:darkness", "amplifier": 0, "duration": 100},
                                     {"effect": "minecraft:slowness", "amplifier": 0, "duration": 100}]}}}
```

Band 0 (`total` below 8, which covers early Stage 3) hears only whispers and insights. **Rejected:** a field outside the table or the type's own; `min_band > max_band`; `status` or `spawn` without `context: true`; an `entity` outside the tag; any value differing from the shipped roster.

### Bargains

`{"schema": "vftv:bargains/1", "bargains": {<id>: {"type", "weight", "cost", …type fields}}}`, with **exactly** the shipped roster under "Bargains":

```json
{"schema": "vftv:bargains/1", "bargains": {
  "vftv:keen_eyes":        {"type": "vftv:effect", "weight": 30, "cost": {"temporary": 3},
                            "effect": "minecraft:night_vision", "amplifier": 0, "duration": 6000},
  "vftv:quick_hands":      {"type": "vftv:effect", "weight": 20, "cost": {"temporary": 2, "lasting": 1},
                            "effect": "minecraft:haste", "amplifier": 1, "duration": 3600},
  "vftv:unheard_insight":  {"type": "vftv:points", "weight": 10, "cost": {"lasting": 2},
                            "points": {"vftv:vacuos": 6, "vftv:silentium": 4}}}}
```

**Rejected:** a `permanent` cost; a cost with both kinds 0 or absent; a field outside its type; a value out of range or differing from the roster.

### Phantoms

`{"schema": "vftv:phantoms/1", "disguises": [{"real": <block tag>, "shown": <block id>}, …], "sounds": [<sound event id>, …]}`, 1–32 of each. **Shipped:**

```json
{"schema": "vftv:phantoms/1",
 "disguises": [{"real": "#minecraft:base_stone_overworld", "shown": "minecraft:chiseled_deepslate"},
               {"real": "#minecraft:base_stone_overworld", "shown": "minecraft:deepslate_tiles"},
               {"real": "#minecraft:dirt",                  "shown": "minecraft:coarse_dirt"}],
 "sounds": ["minecraft:block.wool.step", "minecraft:block.wooden_door.open",
            "minecraft:entity.creeper.primed", "vftv:apparition.vanish"]}
```

**Rejected:** a `shown` block that isn't a full opaque cube, or is air, glass, a fluid or a container; an unknown sound or tag. **At runtime** a resource pack's override is validated before use: an invalid file is ignored with one logged error and the built-in file is used. An empty list makes its type show nothing.

### Voice lines

`{"schema": "vftv:voice_lines/1", "session": {"lines": 46, "perform_seconds_max": 3.5, "estimated_minutes": 90}, "lines": {<id>: {"pool", "segments", "gloss"?, "perform_seconds", "note"}}}`.

| Field | Range | Meaning |
|---|---|---|
| `pool` | `band0`–`band3`, `insight`, `offer`, `joining`, `silencing` | |
| `segments` | 1–8 [language segments](#language-segments) | the text, tagged by language; concatenated, it equals `voice.<ns>.<path>` |
| `gloss` | required iff a segment is `void` | the English sense, for review only, never shown |
| `perform_seconds` | 0.5–3.5 | the spoken length the owner aims for |
| `note` | 1–120 characters | one line of performance direction |

- **Exactly 46 lines:** 8 in each band pool, 4 in `insight`, 4 in `offer`, and the 6 ending lines copied verbatim from the lexicon's `ending_lines` (same ids, pools and segments).
- Every line's text is `voice.<ns>.<path>` in `voices.json`, at most 80 characters, equal to its segments' concatenation. The pool rules and the `void` segment lint are under "Language segments".
- **Rejected:** another count; a pool count other than above; a mismatch with the lexicon's ending lines; a text over 80 characters, or differing from its segments; a segment breaking "Language segments".

```json
{"schema": "vftv:voice_lines/1",
 "session": {"lines": 46, "perform_seconds_max": 3.5, "estimated_minutes": 90},
 "lines": {"vftv:band0_0": {"pool": "band0", "segments": [{"lang": "en", "text": "Are you listening?"}],
                            "perform_seconds": 2.0, "note": "barely voiced, as if remembering a word"},
           "vftv:band2_0": {"pool": "band2", "segments": [{"lang": "en", "text": "I hear "},
                                                          {"lang": "void", "text": "saaren."}],
                            "gloss": "I hear (hear).", "perform_seconds": 2.5,
                            "note": "the English breaks off; the last word is breathed"}}}
```

with `"voice.vftv.band0_0": "Are you listening?"` and `"voice.vftv.band2_0": "I hear saaren."` in `voices.json`. The generated `assets/vftv/voices/lines.json` keeps each line's pool and whether it has a `void` segment.

### Recipes and bootstrap

`{"schema": "vftv:warp_recipes/1", "recipes": {"vftv:stilling_draught": <body>}}`, with **exactly** the body under "Stilling". **Rejected:** another id; any difference from that body.

`{"schema": "vftv:warp_bootstrap/1", "items": [{"item": <id>, "why": <one line>}], "aspects": [{"aspect": <id>, "sources": [<item id or #item tag>, …], "why": <one line>}]}`, covering the catalyst and each of the recipe's three aspects, with the physical items a Stage 3 Listener dissolves for each. A source is an item id **or an item tag**; the review resolves each tag to physical items obtainable by Stage 3. `vftv:silentium`'s sources include `#minecraft:wool`. Research points never count: they can't fuel a crucible. **Rejected:** a missing item or aspect; an unknown item or tag; an empty `why` or `sources`.

### Fixtures

`{"schema": "vftv:warp_fixtures/1", "phase": [...], "dials": [...], "magnitude": [...], "preview": [...], "checks": [...], "background": [...], "schedule": [...], "reconcile": [...], "amplifier": [...]}`. **All nine sections are required**, and together they transcribe every vector table in this spec exactly; `amplifier` transcribes the [Eldritch tier spec's](eldritch.md#fixtures) seven rows of the same name, whose references computed them. The unit tests replay them against the `model` code; that consumer is build work. Every field is required unless a default is given.

| Record | Fields |
|---|---|
| `phase` | `msb`, `lsb` (16 uppercase hex digits each); `expect` `[p1200, p400, p20]` |
| `dials` | `total` 0–2,200; `tax` boolean (default true); `expect` `{band, factor_ordinary, factor_void, penalty_ordinary, penalty_void}` |
| `magnitude` | the [foci fixture's](foci.md#fixtures) row: `base`, `force`, `step_pm`, `w`, `void`, `per_warp_pm`, `max_bonus_pm`, `cap`; `expect` `{power_pm, amp_pm, out}` |
| `preview` | `state` `[T, L, P]`; `cost` `[T, L, P]`; `source` `{kind, id?}` (`id` required for `entry`, `bargain` and `milestone`); `cap` (default 10); `paid` (ids, default `[]`); `expect` `{"actual": [T, L, P], "clipped"}` or `"invalid"` |
| `checks` | `uuid`; `index`; `total`; `permanent`; `counter`; `counter_max` (default 1000); `context` (default false); `offer` (default false); `known` (default 7); `disabled` (event ids, default `[]`); `bargains` (eligible ids, default all three); `expect` `{roll, pm, outcome: "quiet"\|"none"\|"fired", counter}`, plus `pick`, `of`, `event` and `cue` when fired, `aspect` `[index, of]` for an insight and `bargain` for an offer |
| `background` | `total`; `permanent`; `counter`; `expect_pm` |
| `schedule` | `uuid`; `start` `{T, L, P, gear, counter}`; `gear_held` `{from, to, value}`; `until` (the last tick); `expect`: the ordered operations, each `{tick, op}` and by `op`: **`decay`:** `T`, `total` after it; **`gear`:** `gear`, `total` after it; **`check`:** `total`, `band`, `index`, and the check's `expect` as in `checks` |
| `reconcile` | `counter`; `counter_max`; `total`; `permanent`; `expect` `{pm, after_fire, after_gain_2}` |
| `amplifier` | `total` 0–2,200; `joined` boolean; `bonus` 0–50 (default 10); `on` boolean (default true); `expect` `{w, amp_pm, out}` for a base-4000 Void working at the foci defaults |

```json
{"schema": "vftv:warp_fixtures/1",
 "checks": [{"uuid": "00112233-4455-6677-8899-aabbccddeeff", "index": 4, "total": 9, "permanent": 2,
             "counter": 90, "context": false, "offer": false, "known": 7, "disabled": [],
             "bargains": ["vftv:keen_eyes", "vftv:quick_hands", "vftv:unheard_insight"],
             "expect": {"roll": 26, "pm": 115, "outcome": "fired", "pick": 12, "of": 88,
                        "event": "vftv:footsteps", "cue": "0DF0AA695B97CC14", "counter": 60}}]}
```

**Rejected:** a section missing; an unknown field; a row of a vector table missing or altered. Fixtures model arithmetic and draws only; suppression, guards, callbacks, transactions and the service's source-kind and negative-permanent rejections are proved by the GameTests.

### Lang keys

`{"schema": "vftv:lang/1", "strings": {<key>: <text>}}`. Every key below is required in `en_us`, with exactly the listed number of `%s` placeholders. The English is the intended sense; the content ticket writes the final text, and its review checks that the messages teach that refusing an offer costs nothing, that stilling always works, and what each kind of warp does.

| Key | Args | Sense |
|---|---|---|
| `item.vftv.stilling_draught`, `entity.vftv.apparition` | 0 | "Stilling Draught", "Apparition" |
| `tooltip.vftv.stilling_draught` | 2 | "Stills %s lasting warp (I carry %s)." |
| `tooltip.vftv.gear_warp` | 1 | "Gear warp +%s" |
| `message.vftv.warp.nothing_to_still`, `.busy`, `.unreadable`, `.offer_gone`, `.offer_full`, `.offer_cannot` | 0 | "Nothing in me to still.", "Not now.", "I can't read my own warp.", "The offer is gone.", "The Voices can't take more of me.", "The Voices can't give me that now." |
| `voice.vftv.insight_gain` | 2 | "Something of %s comes to me (+%s)." |
| `warp.vftv.kind.temporary`, `.lasting`, `.permanent` | 1 | "%s temporary", "%s lasting", "%s permanent" |
| `warp.vftv.fact.none`, `.clipped`, `.on`, `.off` | 0 | "no warp", "(less, at the limit)", "on", "off" |
| `ledger.vftv.warp.title`, `.silenced` | 0 | "Warp", "I have silenced them." |
| `ledger.vftv.warp.temporary`, `.lasting`, `.gear`, `.total`, `.next_band`, `.still`, `.joined` | 1 | "Temporary: %s", "Lasting: %s", "Gear: %s", "In all: %s", "The next band begins at %s.", "A Stilling Draught stills %s lasting.", "Joined: my Void workings hear %s more." |
| `ledger.vftv.warp.permanent`, `.band`, `.counter` | 2 | "Permanent: %s of %s", "Band %s of %s", "Counter: %s of %s" |
| `ledger.vftv.warp.chance` | 2 | "Chance at each check, every %s seconds: %s" |
| `ledger.vftv.warp.chance_parts` | 4 | "%s, plus %s for each permanent, plus %s for each count, at most %s" |
| `ledger.vftv.offer.title`, `.none`, `.expired`, `.accept`, `.refuse` | 0 | "An Offer", "No offer stands.", "The offer has faded.", "Accept", "Refuse" |
| `ledger.vftv.offer.effect` | 3 | "%s %s for %s seconds" |
| `ledger.vftv.offer.points`, `.cost`, `.expires` | 1 | "%s", "It costs %s.", "It fades in %s seconds." |
| `narration.vftv.offer` | 2 | "Offer: %s, for %s" |
| `options.vftv.voices.text`, `.colour_shift`, `.phantoms`, `.phantom_sounds` | 0 | "Voice text", "Colour drain", "Phantoms", "Phantom sounds" |
| `commands.vftv.warp.get` | 9 | "%s: temporary %s, lasting %s, permanent %s, gear %s, in all %s, band %s, counter %s, chance %s" |
| `commands.vftv.warp.set`, `.fired` | 2 | "%s: %s.", "%s: %s." |
| `subtitles.vftv.apparition.vanish`, `.voice.insight`, `.bargain.accept`, `.stilling.drink_end` | 0 | "Something fades", "A voice gives", "A bargain is struck", "A draught settles" |
| `voice.<ns>.<path>` (in `voices.json`) | 0 | every line's text |

**Used, not declared here:** effect, aspect and player names; the research spec's confirm dialog and "The cost has changed."

**Rejected:** a key outside the table; a required key missing in `en_us`; a placeholder count that differs; an empty string; a duplicate key, including any other spec's key redeclared here.

## Events

NeoForge events in `api.neoforge` ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)), posted on the server through a publisher seam that `neoforge` implements. Payload records live in `api` and are **immutable**, captured before any listener runs.

| Event | When | Payload |
|---|---|---|
| `WarpChangedEvent` | after a commit, apply or compound operation changes `T`, `L`, `P` or gear, once all its writes are complete and **while the guard is held** | player; before and after `(T, L, P, G, total)`; the actual deltas; the source |

- **One post per change,** never per check or cue. The [research spec's](research.md#triggers) `warp_at_least` latches on it.
- **A throwing listener** is logged once; the change stands.

**Plain `core` services:** `WarpService.preview`, `.commit`, `.apply`, `.silence`, `.isSilenced`, `.noteVoidWork`, `.registerPlaceQuery`, `.inVoidTouchedPlace`, `WarpDials.visFactor`, `.infusionPenalty`, `.amplifierWarp`, `WarpPhase.of`. Whether they are exposed in `api` is the API spec's call.

## Sync and state

**Server-authoritative.**

| State | Storage | Synced to |
|---|---|---|
| the warp record and the offer | the `vftv:warp` attachment (transient fields marked above) | the owner, through `AttachmentSyncHandler` filtered to the owner (`NF:attachment/AttachmentSyncHandler.java:28–40`) |
| warp rules | the tuning record and the dial switches of server config | **`WarpRules`**, S2C to every player through `OnDatapackSyncEvent` (join and `/reload`), and again when server config reloads **[build verifies the config reload event]** |
| the gear map | the `vftv:gear_warp` data map | every player, as synced data maps are |
| cues | none | **`VoiceCue`** and **`VoiceClear`**, S2C, to the owner only |
| screen state | the transient `quiet` flag | **`VoiceQuiet`**, C2S |
| guard, `void_work_until`, `grace_until` | transient per-player memory | never |

- **Baselines are NeoForge's:** login and respawn send initial player attachments (`MC:server/players/PlayerList.java:228,446`), and a dimension change resends them because the client loses them (`MC:server/level/ServerPlayer.java:1186–1188`). No duplicate send is added. The gear sample of that tick follows as an ordinary change.
- **Every change** sends the whole record: about 70 bytes, plus an offer of at most 4 aspect ids, or 1 effect id, of at most 256 characters each.
- **Death:** the record is copied (`copyOnDeath`); the transient offer is deleted. Death neither cures nor charges.
- **Dimension change:** the same server player continues (`MC:server/level/ServerPlayer.java:1129–1150`); `grace_until` is set; the client clears its presentation and reasserts `quiet`.
- **Bounded codecs:** the record as above; `VoiceCue` (ids ≤ 256 characters, band 0–3); `VoiceQuiet` one boolean; `WarpRules` a fixed record; `echoedWarp` as the research spec's (three values, 0–100); `offerSeq` a positive `long`. Out of bounds is rejected at decode.

**Reloads:**

| Change | Effect |
|---|---|
| Tuning (`/reload`) | a new record, validated with the milestone rules; `WarpRules` re-sent; phases recomputed; records reconciled on read (see "Kinds and the record") |
| Server config | the switches apply at the next check, quote and sample; `WarpRules` re-sent |
| The event, bargain and research registries | need the world reopened, so no live offer survives |
| Resource reload | the `phantoms` and `lines` resources, revalidated |

## Tuning and limits

Every tunable number, all ours. The home is the server-only datapack file **`data/vftv/tuning/warp.json`**, loaded on reload into an immutable record in `core.warp` and written by datagen from that record's defaults. Every key is optional and defaults to the value below; an unknown key, a wrong type or a value out of range is invalid, and so is a broken milestone rule (see "Permanent warp"). **Rollback rule:** an invalid file refuses the world at open; on `/reload` the previous record is kept and an error logged. The client receives the whole record in `WarpRules`.

| Key | Value | Range | Notes |
|---|---|---|---|
| `bands` | [8, 20, 40] | exactly 3, strictly ascending, 1–1000 | |
| `temporary.decay_interval` | 400 ticks | 20–24,000 | |
| `tax.per_warp_pm`, `tax.max_pm` | 10, 500 | 0–100, 0–3,000 | the ordinary vis factor |
| `infusion.per_warp_pm`, `infusion.max_penalty` | 250, 20 | 0–1,000, 0–100 | |
| `check.interval` | 1,200 ticks | 200–24,000 | |
| `counter.per_gain`, `counter.per_event`, `counter.max` | 10, 30, 1,000 | 0–100, 1–1,000, 1–10,000 | |
| `chance.base_pm`, `chance.per_permanent_pm`, `chance.per_counter_pm`, `chance.max_pm` | 15, 5, 1, 250 | 0–1,000 each | |
| `grace` | 600 ticks | 0–6,000 | |
| `context.window` | 600 ticks | 0–6,000 | |
| `context.dimensions` | [`vftv:vuulas`] | ≤ 16 level keys | the [Eldritch tier's](eldritch.md#vuulas) dimension; a pack may replace the list |
| `joining.amplifier_bonus` | 10 | 0–50 | warp points a Joined Listener's amplifier adds ("The dials") |
| `gear.max` | 12 | 0–100 | |
| `bargain.ttl` | 12,000 ticks | 1,200–168,000 | |
| `still.amount` | 4 | 1–100 | |
| `permanent.cap` | 10 | 0–100 | |
| `permanent.milestones` | the "Permanent warp" table | ≤ 16 ids, amounts 1–100 | |

**Other homes:**
- event weights and parameters: the `vftv:voice_event` registry (`content/warp/events.json`);
- bargains: the `vftv:bargain` registry (`content/warp/bargains.json`);
- each entry's warp: research data;
- gear values: the `vftv:gear_warp` data map, added by #24 and #28;
- disguises and phantom sounds: the `phantoms` client resource;
- the overlay counts: the research spec's `overlay.json`;
- **server config** (admin switches, [architecture, Data and code](architecture.md#data-and-code)), each default on and **each proven by a GameTest**: `warp.voices.enabled` (every check); one per event type, `warp.voices.whisper`, `.insight`, `.sound`, `.colour`, `.phantom_block`, `.phantom_mob`, `.bargain`, `.status`, `.spawn` (a type that is off is never eligible, so it never fires and the counter is untouched); `warp.tax` (the vis factor and the infusion penalty go neutral); `warp.amplifier` (`amplifier_warp` is 0); `warp.gear` (the gear sample is 0);
- **client config:** `voices.text`, `voices.colour_shift`, `voices.phantoms`, `voices.phantom_sounds` (see "Accessibility").

**Fixed limits, in code** (formats and invariants, not balance), as constants in **`WarpLimits`** in `api`:

| Constant | Value |
|---|---|
| `T_MAX`, `L_MAX`, `P_FORMAT_MAX`, `COUNTER_FORMAT_MAX` | 1,000, 1,000, 100, 10,000 |
| `MAX_EVENTS`, `MAX_BARGAINS` | 64, 64 |
| `MAX_GEAR_SLOTS` | 128 |
| `MAX_SPAWN_ATTEMPTS`, `MAX_SPAWN_COLUMN` | 16, 13 cells |
| `MAX_PHANTOM_BLOCKS`, `MAX_PHANTOM_PROBES` | 4, 64 |
| `MAX_APPARITION_ATTEMPTS` | 16 |
| `MAX_CUE_HOLD` | 200 ticks |
| `MAX_MILESTONES`, `MAX_ID_CHARS` | 16, 256 |

**Also fixed in code:** the phase function and its salt; the schedule order; the check salt and draw order; the suppression order; the outcome table; the gear sample cadence (20 ticks). **Fixed feedback constants:** the colour drain's peaks, ramps and hold; the disguise scale 1.002; the apparition's 4-block touch radius and 400-tick lifetime; the disguise's 600-tick lifetime; the Voice text's width, line count and extra 20 ticks; playback volumes as stated.

## Performance

**Heavy-base workload:** 8 players online with warp:
- 2 at band 3 with the counter refilled to 1,000 by the fixture, so `p` stays at its cap;
- 1 of them in context, spending `VOID` vis every 100 ticks, with a registered place query answering true and a test-pack `spawn` event that spawns 3;
- 2 holding live offers, accepted and refused by the fixture each minute;
- every player carrying 41 stacks plus 8 Curios stacks, one of them swapping a gear item every 40 ticks;
- every event type on.

On the client: one band-3 player with the colour drain, 4 disguises, an apparition and a Voice line active at once, with the Voice text shown.

**Share:** server ≤ **0.02 ms mean, ≤ 0.08 ms p99**, measured in the combined stress world, including the spawns' placement and callbacks, the effect callbacks, events and sync. Client: ≤ **0.005 ms p50** idle (the screen poll); ≤ **0.04 ms p50** with every effect active, GPU pass included. Shares are acceptance targets, not measurements.

**The envelope's running totals** (planning arithmetic, never a combined percentile): the finished specs' server shares total **0.47 ms mean / 1.90 ms p99** with golems; with warp, **0.49 / 1.98**, leaving **0.51 / 1.02** for Armor and trinkets, Flux and the Hush, Worldgen and mobs, and the Eldritch tier. Client shares total **0.77 ms p50**, **0.81** with warp, leaving **0.19**.

**Measured separately as load scenarios** (reported): 64 players all checking in one tick (shared phases); a `spawn` event whose 16 attempts all fail; a place query over a dense index; a `VoiceQuiet` flood.

**Hot paths:**

| When | Work |
|---|---|
| Per player per tick | three phase compares, O(1), zero allocation |
| Per gear sample (every 20 ticks) | ≤ 128 stack reads and data-map lookups |
| Per check (every 1,200 ticks by default) | one `Random`; ≤ 64 eligibility tests; the context test: O(1) for Void work and dimensions, plus each registered query's bounded loaded-only work; for an insight, O(known aspects); for a bargain, ≤ 64 |
| Per status event | ≤ 3 `addEffect` calls and their callbacks: **a native term**, disclosed |
| Per spawn event | one shared budget of ≤ 16 attempts × ≤ 13 cells, each a loaded-cell read and a collision query, plus a scan of the level's players: **an O(players) native term**, disclosed; ≤ 3 `addFreshEntity` calls and their callbacks, each followed by one insertion check and one eligibility recheck |
| Per cue (client) | ≤ 64 block probes or ≤ 16 columns of ≤ 13 cells, once |
| Per frame (client) | ≤ 4 disguise models, 1 entity, one fullscreen pass, the Voice text; **zero allocation in our code** (an allocation-budget test) |
| Per client tick | one screen poll; ≤ 4 disguise state reads |
| Packets | one record sync per change (≤ 1 per check, decay, sample or commit); ≤ 1 cue per check; `WarpRules` per join, reload or config reload |

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **every vector** in this spec, replayed from `content/warp/fixtures.json`: phases, dials, bands, previews (milestone authorisation, already paid, clipping, a lowered cap), checks (the arithmetic and draw outcomes `QUIET`, `NONE` and `FIRED`, per-type parameter draws, empty eligibility, zero known aspects, a single eligible bargain), the background chance, the 20‰ amplifier rows, the Joining bonus rows (the `amplifier` section), the coincident schedule, and reconciling a lowered `counter.max`. `SKIPPED`, `FAILED`, native failures and the service's source-kind and negative-permanent rejections are not in the references; they are the GameTests below;
- the per-mille recovery of the vis factor over every `total` from 0 to 2,200;
- the counter under gains, fires and a lowered maximum; the background floor; `total` 0 skipping;
- the gear sum, its cap and the slot bound;
- the client draws: pool sorting, empty pools and lists (no `nextInt(0)`), probe and column rules, the colour ramp's per-tick bound;
- codecs: the record (bounds, the raw round trip of an unreadable record, defaults), `VoiceCue`, `VoiceClear`, `VoiceQuiet`, `WarpRules`, the tuning file;
- every `content/` rejection rule and lint: the lexicon phonology, the line counts, the ending-line copy, the event and bargain rules, the bootstrap, the milestone sum, the no-early-warp, ancestry and milestone-entry rules.

**GameTest:**
- the registries and the tuning file load, and each bad entry and file is refused; a tuning reload that breaks a milestone rule keeps the previous record;
- **commits:** `silence_beneath`'s listen applies lasting 4 and raises the counter by 40; a stale echo, an unreadable record and an `INVALID` milestone each refuse with nothing changed; **a `WarpChangedEvent` listener that calls `commit` gets `BUSY`**, and the event's snapshot shows the completed change; a throwing listener leaves the change in place;
- **milestones:** `under_the_silence` pays once, and again after `revoke` and a re-listen pays 0; a lowered cap keeps permanent warp and blocks gains;
- **schedule:** decay, sample and check in one tick, in order;
- **gear:** equip and unequip post `GEAR` events within 20 ticks, with Curios absent; `warp.gear` off;
- **stilling:** exact removal; refused at 0 at start; lasting stilled to 0 during the drink (nothing consumed); 2 left gives a partial; `BUSY`;
- **Bargains:** accept and refuse; a stale `seq` (the newer live offer kept); an expired offer on accept and on refuse (deleted, `offer_gone`, nothing else changed); a clipped cost refused (`offer_full`); **an applicability callback that refuses** (nothing charged, the offer kept); **a callback that changes its answer between calls** (one `addEffect`, so nothing is double-probed); a throwing callback; an `Added` listener that commits warp (`BUSY` for it, our charge stands); the menu closed or mutated before the action; death and logout delete the offer; a `points` aspect no longer known;
- **suppression:** no check with each menu open (book, Worktable, Lathe), with `quiet` set, during grace, dead or spectating; **the last `VoiceQuiet` of a tick wins**; a flood; reassertion after a dimension change;
- **every server switch,** off and on;
- **context and danger:** status only in context; a status whose every effect is refused settles `FAILED` with the counter unchanged; a test-pack `spawn` lands ≥ 8 blocks from every player; all attempts failing settles `FAILED`; a cancelled join; partial success (1 of 3); the 16-attempt budget shared across all 3 entities; unloaded cells never loaded; a place query that would need an unloaded chunk answers false;
- **danger callbacks:** a first effect's callback that teleports the Listener out of context in the same level, opens a menu, sets `quiet` or silences them: the second effect never runs; a join listener that moves our entity onto a player, into a wall or out of its cell: discarded, not counted; a callback that silences the Listener after one success: the counter falls, but **no cue** is sent (the outcome exception);
- **Silencing** writes everything before its one event and stops checks; it returns `STALE` on a stale or repeated echo, `BUSY` under the guard and `UNREADABLE` on an unreadable record, changing nothing; **Joining** through `commit` once, after which the amplifier carries the bonus, and `warp.amplifier` off zeroes it; `isSilenced` and `inVoidTouchedPlace` against each registered query, and recent Void work alone answering false;
- persistence through save and reload, death, dimension change and the End credits respawn; the offer gone after logout;
- two-player privacy: cues reach only the owner;
- every command; every test id in the discovery guard.

**E2E:**
- **the first listen, driven deterministically:** `/vftv warp fire` with `vftv:whisper` after `being_heard` plays a line with its text (screenshot and dump);
- the Ledger's warp spread equals the state dump (kinds, gear, total, band, counter, chance and its parts), with an offer accepted by mouse and refused **by keyboard only**, a long test translation scrolling, and labels fitting;
- the colour drain at bands 1–3 (screenshots, plus a dump of per-frame intensity proving the ramp and peak bounds); with `voices.colour_shift` off and with `screenEffectScale` 0;
- a disguise struck by the fork reads the real block;
- an apparition vanishing when approached and when struck: **no attack, interact or swing packet** in the protocol dump, the fork silent, no sounding;
- client options off; `VoiceClear` on Silencing; cleanup on a level change;
- sync on join, reconnect, dimension change and `/reload`;
- the asset, lang and sound validators.

## Contracts on sibling specs

Acceptance requirements those specs must meet. Each is a criterion in that ticket.

**Met here** (the [Research and the Commonplace Book spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)'s contracts on this ticket):

| Research contract | Met by |
|---|---|
| `book_bands`: three ascending thresholds in tuning; sync the owner's band 0–3 and exact warp per kind | "Bands": `bands` [8, 20, 40], exactly three (the research spec now names it); "Sync and state": the owner-filtered record with every kind, gear, total and band |
| Defines `total`, including gear if gear counts; a post-commit warp-change event with values and source, including gear equip and unequip | "Total warp"; `WarpChangedEvent` after the complete change, under the guard, from commits, applies, compound operations and gear samples |
| One checked commit `commit(player, cost, source, expectedPreview)`: exactly the previewed deltas, cap clipping included, refused on stale state, returning the actual deltas; nothing between preview and charge | "The commit": the preview with clipping and milestone authorisation; `STALE`; the guard held through the event, re-entry `BUSY`; Joining on the checked path |
| A concrete lasting-only amount for `silence_beneath`; the optional deeper listen decided | "Stage": lasting 4; `vftv:under_the_silence`, optional, permanent 2, parent of nothing |
| The book counts as an open GUI | "Suppression" row 7: `hasContainerOpen()` covers `CommonplaceMenu` |

**Met here** (the [Wands, vis and nodes spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/17)'s contracts):

| Wands contract | Met by |
|---|---|
| `vis_factor(player, ctx)`: finite, pure over synced warp and `ctx.kind`, ≥ 1.0 for `ORDINARY`, any value for `VOID`, identical on both sides | "The dials": (1000 + min(500, 10 · `total`)) / 1000 for `ORDINARY`, 1.0 for `VOID`, from the synced record and `WarpRules`; the per-mille recovery test |

**Met here** (the [Arcane crafting spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/18)'s contracts):

| Arcane contract | Met by |
|---|---|
| `vis_factor` as contracted; the station shows the resolved per-mille part and `clamped`; synced inputs are live dependencies | the factor is *k*/1000, which the quote's `w` recovers; the record and `WarpRules` are synced and change the price inputs the station already watches |
| A recipe needing warp on craft proposes an extension | none: "Sources" |

**Met here** (the [Infusion spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/21)'s contracts):

| Infusion contract | Met by |
|---|---|
| `infusion_penalty(player, kind)`: pure, 0–100, ≥ 0 for `ORDINARY`, a function of synced warp and the kind, identical on both sides, the typed zero until it lands; re-sends nothing itself | "The dials": min(20, ⌊250 · `total` / 1000⌋) for `ORDINARY`, 0 for `VOID`; this spec sends nothing for it |
| Infusion applies no warp; warp on an upset needs a proposed extension | none proposed |

**Met here** (the [Foci spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/22)'s contracts):

| Foci contract | Met by |
|---|---|
| `amplifier_warp(player)`: pure, finite, non-negative, synced, identical on both sides; the unit and which kinds count decided; the scale tested against `void.per_warp_pm` | "Total warp" and "The dials": `total` in warp points; the foci default amended to 20 with its vectors and fixtures, so a Void working doubles at 50 |
| Whether a carried Void focus is gear warp | "Gear warp": no, as a balance choice; the foci spec drops its kind-read wording |
| Casting applies no warp | "Sources" |

**Met here** (the [Aspects and scanning spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/15)'s contracts):

| Aspects contract | Met by |
|---|---|
| Phantom mobs have no server identity; phantom blocks vanish when struck; the fork's readout names what is really there | "Phantoms": client-only apparitions with negative ids, the input cancelled **with the swing off**, so no packet is sent and nothing falls through to sounding; disguises over real blocks only, which vanish on any hit or strike while the input reaches the real block |
| The revealing listen shows its warp cost before commit | the research spec's flow over "Preview" |

**Met here** (the [Crucible alchemy spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)): a craft that should spill flux needs #26 or #25 to propose it. None is proposed; the Stilling Draught is an ordinary crucible craft.

**Met here** (the [progression spec](progression.md#warp)): warp as a dial of key, amplifier and price ("Total warp", "The dials"); the four kinds ("Kinds and the record", "Sources"); a small cap from named milestones that show their cost ("Permanent warp"); events rationed by a counter that settles to a faint background set by permanent warp ("The counter and the chance"); nothing while a GUI is open, including client-only screens ("Suppression"); nothing hostile on top of the Listener, and physical danger only in context, with eligibility and every inserted entity rechecked after each foreign callback ("Danger", "Context"); a server switch per effect, each with a GameTest ("Tuning and limits"); senses lie and instruments never: the Ledger is exact and the fork reads the real thing ("In the Commonplace Book", "Phantoms"); phantoms client-only, unseen by others and harmless ("Phantoms").

**Met here** (the [lore spec](lore.md#listening-and-warp)): warp is never a hidden tax, since every source is a previewed commit or a carried item with its tooltip; the counter and the chance are printed with their parts; a Bargain is never free and always refusable at no cost; listening is deliberate; the Voices are the owner's recordings, delivered as audio plus text and never chat, sliding into Void-tongue with the bands ("Voice lines").

**Met here** (the [quality gates](quality-gates.md#what-each-system-spec-must-declare)): a workload, a share, hot paths with bounded work and disclosed native terms, and an allocation budget ("Performance").

**[Essentia logistics](https://github.com/andrewbrogoff/voices-from-the-void/issues/20)** and **[Golems](https://github.com/andrewbrogoff/voices-from-the-void/issues/23)** place no contract on this ticket.

**[Research and the Commonplace Book](https://github.com/andrewbrogoff/voices-from-the-void/issues/16)** (amended in this change), handed to [Stage 3 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) and [Stage 4 research content](https://github.com/andrewbrogoff/voices-from-the-void/issues/37):
- **Entries:** the four under "Stage", with their warp and parents; `vftv:being_heard` carries the `vftv:recipe` page for `vftv:stilling_draught`.
- **Load validation:** no warp in Stage 1–2 entries; permanent only on listed milestones with their amounts; every entry with `lasting` or `permanent` > 0 has `vftv:being_heard` in its ancestry; built-in `warp_at_least` gates use `lasting_plus_permanent`.
- **Affordability paths** never commit a milestone entry; milestone entries are exempt from "every entry committed"; every built-in warp gate latches during its replay from earlier `lasting`.
- **The Ledger's warp spread, the fact sources and the offer actions,** as specified there.
- **Prose** teaches the kinds, the bands, the dials, the counter and the chance, that a Bargain can always be refused at no cost, and that stilling always works and always costs, with every number in a `warp`, `warp_rules`, `entry` or `recipe` fact span.

**[Aspects and scanning](https://github.com/andrewbrogoff/voices-from-the-void/issues/15)** (amended in this change)
- Its points sources gain insight events and `points` Bargains: credits to known aspects only, never a discovery.

**[Foci](https://github.com/andrewbrogoff/voices-from-the-void/issues/22)** (amended in this change)
- `void.per_warp_pm` defaults to 20, with every default vector, fixture and note updated; a Void focus is not gear warp; the Void effects' names come from the Void-tongue lexicon ticket.

**[Art style](art-style.md)** (amended in this change)
- §7 gains the Voice clip exception: a raw performance ≤ 3.5 s, a final clip ≤ 4.5 s, peak −3 dBFS, every other convention kept.

**[Crucible alchemy](https://github.com/andrewbrogoff/voices-from-the-void/issues/19)**
- `vftv:stilling_draught` is an ordinary crucible body with `research: vftv:being_heard`; no flux or vis is added.

**[Armor and trinkets](https://github.com/andrewbrogoff/voices-from-the-void/issues/24)**
- Void-tier gear carries warp only through the `vftv:gear_warp` data map. Its Curios compat passes carried Curios stacks to the gear sample.
- *Met by the [armor spec](armor.md#contracts-on-sibling-specs): the Void set's 2/3/2/2 in `content/armor/void.json`, and `CuriosWorn.collect`'s ordered entries within the one shared allowance under "Gear warp" (amended here).*

**[Flux and the Hush](https://github.com/andrewbrogoff/voices-from-the-void/issues/26)**
- It registers a place query for hushed ground under "Context": pure, loaded-only, bounded. It adds no warp.

**[Worldgen and mobs](https://github.com/andrewbrogoff/voices-from-the-void/issues/27)**
- It registers a place query for Choir sites, backed by site metadata it indexes per chunk at generation, so the query never follows structure references. Any Void-touched mob it adds to `vftv:voice_spawnable` meets "Danger"'s rules. The Answerers are never Voice spawns.
- *Met by the [worldgen spec](worldgen.md#the-choir-site-place-query): `ChoirSitePlaceQuery` reads whether a loaded chunk's vanilla structure-reference set for `#vftv:choir_sites` is non-empty (written per chunk at generation, never followed), at most 8 lookups with zero allocation; it adds no mob to `vftv:voice_spawnable`, and `#vftv:answerers` is disjoint from it (tested).*

**[Eldritch tier and the Choir's Road](https://github.com/andrewbrogoff/voices-from-the-void/issues/28)**
- **Silencing** calls the checked `silence(player, expected)`; **Joining** commits the `vftv:joining` milestone, at its current amount, through `commit` with the preview it shows, once.
- Its dimension goes in `context.dimensions`, and its road registers a place query; Void gear adds `vftv:gear_warp` values.
- It plays the `joining` and `silencing` pools at the choice. Its eldritch names use the lexicon's words, adding any it needs in its own spec.
- *Met by the [Eldritch tier spec](eldritch.md#contracts-on-sibling-specs): the Heart's checked Joining commit and `silence(player, expected)` (amended here to be checked and to report its status), with this record as the ending's only authority; `vftv:vuulas` in `context.dimensions` (amended here) and `RoadPlaceQuery` registered; no gear warp on its items, with the reason; all six ending lines played in id order at the choice; names from existing lexicon words. Amended here too: the Joining bonus on the amplifier, `inVoidTouchedPlace` and `isSilenced`.*

**[Vanilla aspect value table](https://github.com/andrewbrogoff/voices-from-the-void/issues/30)**
- Every `#minecraft:wool` item carries **Silentium 2**, so a Stage 3 Listener can dissolve the draught's Silentium from matter.
- A hand entry (or a reviewed exemption) for `vftv:stilling_draught`, and round-trip non-amplification over its recipe, counting the returned glass bottle.

**Configuration**
- The server switches and client options under "Tuning and limits", as named.

**API**
- `WarpCost`, `WarpSource`, `WarpPreview`, `CommitResult`, `WarpLimits`, the `WarpRules` record, the `vftv:voice_event`, `vftv:voice_event_type` and `vftv:bargain` registry keys, the `vftv:gear_warp` data-map key and the `vftv:voice_spawnable` tag key live in `api`, loader-free. The `WarpChangedEvent` class and the data-map registration live in `api.neoforge`. The commit's guard and publication contract is as under "The commit". Which services are public is the API spec's call.

## Production tickets

This spec settles every decision. Five required child tickets deliver the lexicon, the assets, the authored content and the Voice recordings, and **the system is not done until all five land**. **None of them writes mod code:** no entity, renderer, shader, HUD layer, item, payload, registry, attachment, config, platform adapter, datagen provider or validator; the colour drain's shader is code under [`NOTICE.md`](../../NOTICE.md) rule 4 and stays in the build. The asset and recording tickets deliver generators and processing tools (authorised tooling under `tools/art/`), an editable model and assets under the [art style](art-style.md); the content tickets deliver authored inputs under `content/`, in the schemas above. The tags, runtime validators, datagen output and every test above are build-phase acceptance criteria.

| Ticket | Delivers | Blocked by |
|---|---|---|
| **Void-tongue lexicon** | `content/lexicon/void_tongue.json` (`vftv:lexicon/1`, "The lexicon"): 18–24 words with glosses, stress and uses, passing the phonology rules; the two foci names (`focus_effect.vftv.unmaking`, `.undertow`); and the **6 ending lines' text** (3 Joining, 3 Silencing) with glosses, the handoff #28 would otherwise have to finish first. Its review checks every word against the [lore spec's phonology](lore.md#void-tongue-phonology), that no word echoes another work's invented language, and that the ending lines leave "doom or release?" open. | none |
| **Warp assets** | **Generator:** `tools/art/warp.py`.<br>**Item:** `assets/vftv/textures/item/stilling_draught.png` (16×16, Stage 3 row of [art style §5](art-style.md#5-tone-ramp-by-stage): a stoppered phial with a felt-bound neck and a brass cap, the teal dimmed into a groove), its item model (parent `minecraft:item/generated`) and `assets/vftv/items/stilling_draught.json`.<br>**The apparition:** `tools/art/warp/apparition.bbmodel`, an editable model with its parts, pivots, cuboids, UV origins and mirroring (a robed, faceless Choir figure, 8 × 30 × 6 px within a 64 × 64 sheet), a UV guide `tools/art/warp/apparition_uv.png`, and `assets/vftv/textures/entity/apparition.png` (mauve cloth, an ink hollow for the face, no teal).<br>**GUI:** `assets/vftv/textures/gui/sprites/ledger/offer_panel.png` (130×64, the card's frame; the translated layout itself is the research spec's) and `assets/vftv/textures/gui/sprites/hud/voice_text.png` (16×16, nine-slice border 4, in its `.mcmeta`).<br>**Sounds** (Stage 3 register, [art style §7](art-style.md#7-sound-palette); the Hush rule, subtraction and never a growl): `apparition/vanish_0…2.ogg`, `voice/insight.ogg`, `bargain/accept.ogg`, `stilling/drink_end.ogg`, with the `sounds.json` entries `apparition.vanish`, `voice.insight`, `bargain.accept`, `stilling.drink_end` naming their subtitle keys, no overrides.<br>**Review:** a contact sheet at GUI scale 2 beside a vanilla potion and the research spec's 9×9 warp glyph; Blender renders of the apparition from the front, side and at 16 blocks; a mockup of the offer card in the Ledger with a long translation. Provenance rows. | none |
| **Warp content** | `content/warp/events.json`, `bargains.json`, `phantoms.json` and `recipes.json`, each exactly as shipped above; `content/warp/bootstrap.json` (the catalyst and the three aspects' **physical** Stage 3 sources, Silentium from `#minecraft:wool`, every `why` reviewed); `content/warp/fixtures.json` (every vector table transcribed); and `content/lang/en_us/warp.json` with every key under "Lang keys" except the lines. Its review checks what "Lang keys" lists. | [Vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) (wool's Silentium and the non-amplification lint) |
| **Voice line script** | `content/warp/voice_lines.json` and `content/lang/en_us/voices.json` ("Voice lines"): **exactly 46 lines** (8 per band pool, 4 insight, 4 offer, and the lexicon's 6 ending lines verbatim), each with its text, gloss, a one-line performance note and a spoken length of **at most 3.5 s**, and the session header stating **46 lines and about 90 minutes** (a 10-minute audition, then about 5 takes of each line with breaks). Every line is written as [language segments](#language-segments), so a mixed `band2` line tags its Void-tongue words, which alone are checked against the lexicon and the phonology, in lowercase. Its review checks the language ramp and that the Voices sound kind rather than menacing. | Void-tongue lexicon |
| **Voice recordings** (performed by the owner) | **1. Audition:** the owner records 3 named lines (one each from `band0`, `band2` and `band3`); an agent processes them with `tools/art/voices.py` (the chain below) and the owner approves the choir sound. They may retune it once, within the listed ranges, and the chosen values are recorded in the provenance rows. **2. Session:** the owner records all 46 lines close-mic in a near-whisper. **3. Processing:** `tools/art/voices.py` turns the takes into `assets/vftv/sounds/voice/<path>.ogg` with a `sounds.json` entry `voice.<path>` for each line. **The raw takes are never committed** ([`NOTICE.md`, Voice recordings](../../NOTICE.md#voice-recordings)). Provenance rows carry the credit "Voice recordings by Andrew Rogoff" and the permission notice. | Voice line script |

**The processing chain** (ours, in `tools/art/voices.py`, with [art style §7](art-style.md#7-sound-palette)'s conventions as amended for Voice clips):
- highpass 100 Hz;
- three layers: dry; pitched −300 cents and delayed 23 ms at −4 dB; pitched +200 cents and delayed 41 ms at −6 dB;
- reverb with reverberance 75, HF damping 60, room 100, pre-delay 60 ms, wet −2 dB; `band3` and ending lines take a 90 ms pre-delay ("the room answers late");
- lowpass 7 kHz; a 0.3 s quarter-sine fade; normalise to −3 dBFS; mono, 44.1 kHz, Vorbis q5; `sox -R`, so a re-run from the same takes is byte-identical;
- **audition ranges:** pitch layers ±100–400 cents, delays 15–60 ms, reverberance 50–90, pre-delay 40–120 ms.

**The recording decision** (answered by the owner on this ticket): the owner records the ~40 base lines plus #28's ending lines in a close-mic near-whisper, after the audition above. A text-to-speech fallback may be used only after a specific model **and** its output licence are individually cleared and recorded in provenance ([art style, Pipeline](art-style.md#pipeline)); none is cleared today, and none is planned.

[Stage 3 research content #36](https://github.com/andrewbrogoff/voices-from-the-void/issues/36) and [Stage 4 research content #37](https://github.com/andrewbrogoff/voices-from-the-void/issues/37) consume the entry ids, amounts, recipe id and the `warp` and `warp_rules` fact keys frozen here. [Void foci content #51](https://github.com/andrewbrogoff/voices-from-the-void/issues/51) consumes the lexicon's two names.
