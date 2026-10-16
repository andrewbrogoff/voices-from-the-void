# Choristers of the Road

The three guardians of the Choir's Road. This covers:
- **the creature** `vftv:chorister`: one entity type, one model and three skins, its attributes, and what creates, owns and reports it;
- **the moves**: four move types (**strike**, **knell**, **breath** and **hear**), each instance's windups, locks, recoveries, reach, area and damage, and how they are dodged;
- **the schedule** that runs them, the **choir walk** it moves along, and whom it hits;
- **damage**: four damage types, difficulty and peaceful, armour, the Baffle and shields, and who can hurt it;
- the instances **Wuuren**, **Suhlaa** and **Nihthaa** of the synced registry `vftv:chorister`, their schema, content and fixtures;
- rendering, the floor **marks** that telegraph every move, particles and sound, with their budgets.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [Eldritch tier spec](eldritch.md), whose section ["The split"](eldritch.md#the-split) freezes the frame this spec plugs into: the Road, each Step's rise cell and arena, admission, threat and target selection, entry health, the rise, defeat and credit, reset and unload, the boss bar and the completion interface;
- the [architecture spec](architecture.md) for the code layout, the data and sync rules and the seams;
- the [quality gates](quality-gates.md) for every gate;
- the [lore](lore.md) and [progression](progression.md) specs for the Choir and what the Voices may do;
- the [armor spec](armor.md) for the Baffle, the [foci spec](foci.md) for target rechecks around callbacks, and the [warp spec](warp.md) for context danger and phantoms;
- the [worldgen spec](worldgen.md) for the creature-spec conventions this spec follows (frozen geometry, one predicate everywhere);
- the [shared conventions](conventions.md) for the authored-content envelope and fixtures;
- the [art style](art-style.md) for every asset.

Resolved in [Choristers of the Road spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/69). Inputs: [TC4 world and endgame](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/tc4-endgame/docs/research/tc4-endgame.md) (§7, the bosses and the boss room), [community pain points](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/community-pain-points/docs/research/community-pain-points.md) (§1, hostile things on top of the player; §10, the unfinished endgame).

**Every number in this spec is ours.** None is taken from TC4. There is no regenerating health or shield pool, no frenzy that teleports home and turns invulnerable, no rage that multiplies damage, no head-loss full heal, no helper portals or spawned waves, no armour- or shield-ignoring pulse, no multi-body boss and no random name or modifier, and nothing here is a TC4 figure rescaled. Each tunable number is listed with its data home under "Tuning and limits". The JSON under "Authored content" holds the shipped values.

**Renamed coinages** ([lore, Vocabulary policy](lore.md#vocabulary-policy)): the Champion Eldritch Guardian, Eldritch Construct, Crimson Portal and Giant Taintacle → three **Choristers**, named from the lexicon: **Wuuren** ("step; footfall"), **Suhlaa** ("breath") and **Nihthaa** ("brow; the head that listens"). Every id stays English.

**Vectors** throughout come from one reference implementation, cross-checked against an independent JDK port. Both print the same 21 lines (2,203 bytes, SHA-256 `e34a6750661807bfb030383db2e03a9eec1202f70b0f774f8f1578193b6c7b68`). The references cover arithmetic only: the schedule, the four zones, the knell, facing, the walk, difficulty scaling, the box fit, the dodge margins, held knell waves with their record slots and serials, and the sync anchor. Their agreement says nothing about callbacks, sync, rendering or performance, which the build's tests prove (see "Tests"). **Nothing in this spec draws randomness.**

**Source citations:** `MC:` is `minecraft-patched-26.2.0.88-sources.jar` and `NF:` the NeoForge 26.2.0.88 sources, with package prefixes omitted.

## Stage

| Stage | What this spec puts there | Find |
|---|---|---|
| 4 The Choir's Road | the three Choristers, one at each Step | the opener `vftv:choirs_road` ([Eldritch tier](eldritch.md#stage)) |

- **No entry of its own.** The Choristers are met on the Road, which `vftv:hollow_bell` opens, and `vftv:the_road` teaches them ("Contracts on sibling specs").
- **No warp, no find, no reward of its own:** a defeat's credit and keepsake are the [Eldritch tier's](eldritch.md#defeat-and-credit).

## The Choristers

| | **Wuuren** (Step 1) | **Suhlaa** (Step 2) | **Nihthaa** (Step 3) |
|---|---|---|---|
| What remains | the scholar who still keeps the step | who still keeps the breath | who still listens |
| What it teaches | read a lock; jump a knell | spacing: out of the inner ring, then in or beyond the outer | turns and lanes, everything at once |
| Moves | strike, knell | strike, inhale, exhale, knell | hear, strike, inhale, exhale, double knell |
| Base health (×1 / ×2.5 at four players) | 200 / 500 | 260 / 650 | 320 / 800 |
| Armour | 4 | 6 | 8 |

Each later Chorister **adds a move and shortens its windups**. Each keeps **one fixed cycle**: no phase, no threshold and no condition changes what it does.

- **Options considered:**

  | Option | Why not |
  |---|---|
  | a separate creature per Step | the [Eldritch tier](eldritch.md#the-split) freezes one type with three registry instances; a second model buys no gameplay |
  | moves drawn at random, or a seeded RNG with a pinned draw order | nothing random can be learned ([Eldritch tier, Encounters](eldritch.md#options-considered-1)) |
  | health-threshold phases | TC4's rage and frenzy (TE §7) |
  | moves that wait for a condition (strike only when someone is near) | a player who never comes near stalls the cycle and never meets the knell |

## The creature

### The entity type

**`vftv:chorister`**, a `Mob` subclass that implements the `Enemy` marker, category `MONSTER`:
- **Builder:** `sized(1.2, 3.4)`, `eyeHeight(3.0)`, `noSave()`, `noSummon()`, `noLootTable()`, `clientTrackingRange(4)` (64 blocks horizontally, all of the 47-block Road), `updateInterval(2)` (`MC:world/entity/EntityType.java:596,601,621,626,636`). **Not** `notInPeaceful`, so it is allowed in peaceful (`MC:world/entity/Mob.java:715–718`); not `fireImmune`.
- **Overrides,** all vanilla and in `core`:

  | Method | Behaviour | Why |
  |---|---|---|
  | `requiresCustomPersistence` | true | persistence-required while it lives ([Eldritch tier, The split](eldritch.md#the-split)) |
  | `isPushable`, `canUsePortal` | false | nothing shoves it into the well or takes it elsewhere |
  | `heal` | does nothing | it never heals |
  | `canBeAffected` | false for every `BENEFICIAL` effect; absorption is held at 0 | no Regeneration, Instant Health, Health Boost or Absorption |
  | `isInvulnerableTo` | vanilla's answer, or true for damage a player causes who fails `isEligibleParticipant` ("Who can hurt it") | no out-of-arena sniping |
  | `blockedByItem` | does nothing | no shield recoil ("Damage, Shields") |
  | `getSecondsToDisableBlocking` | the current move's `shield_seconds` during one of its hits, else 0 | strikes and hears disable shields |
  | `die` | the confirmed-death report ("Created, owned and reported") | the frame's only signal |
  | `getBaseExperienceReward` | 0 | re-fighting pays a keepsake and nothing else ([Eldritch tier](eldritch.md#defeat-and-credit)) |
  | `getAmbientSound`, `getVoicePitch` | none; 1.0 | no ambient voice; fixed pitch |
  | `getTypeName` | the instance's name, `chorister.<ns>.<path>` | death text and the boss bar name it |

- **`Enemy`** makes it unleashable (`MC:Mob.java:1322–1323`).
- **No goals.** `registerGoals` adds none, so no goal flags (MOVE, LOOK, JUMP, TARGET) compete, and navigation is never asked to move. Everything it does is one step in `customServerAiStep`, which vanilla calls **every tick** after the selectors and navigation (`MC:Mob.java:743–783`). Vanilla's `setTarget` is never called, so no target-change callback runs.
- **Never:** breaks, places or tramples a block; picks up items; rides; creates an entity; draws on a random source for any decision. Vanilla's ambient-sound roll still draws from the entity's own `random` every tick (`MC:Mob.java:296`) and finds no sound; disclosed.
- **Code placement:** the creature lives with the frame in **`core.road`**: `Chorister`, `ChoristerFactory`, `Encounter` and `RoadService` there, the Minecraft-free schedule, zones, walk and wave validation in `core.road.model`, the renderer, model layer and marks in `core.road.client`, listeners in `neoforge.road`. One feature, so `FEATURES_ACYCLIC` holds ([architecture, Enforcement](architecture.md#enforcement); amended in the Eldritch tier spec).

### Attributes

| Attribute | Value | Home |
|---|---|---|
| max health | the entry health the factory is given, 1–1024; health set to it | the [Eldritch tier's](eldritch.md#the-rise) formula; `MC:world/entity/ai/attributes/Attributes.java:68–69` accepts it |
| armour | the instance's `armor` | registry |
| scale | the instance's `scale` | registry |
| knockback resistance, explosion knockback resistance | 1.0, 1.0 (`Attributes.java:61–62,38–39`) | code |
| gravity | off (`setNoGravity`): its position is set every tick ("The choir walk") | code |
| armour toughness, attack damage, follow range, movement speed | 0: moves carry their own numbers and nothing pathfinds | code |

**The box** is 1.2·`scale` × 3.4·`scale`, centred on the rise point (0.5, floor + 1, 12.5). With `scale` ≤ `MAX_SCALE` (1.4) it is at most 1.68 × 4.76, inside the rise box (x −1 to 1, y 1 to 5, z 11 to 13, so 3 × 5 × 3 blocks; [Eldritch tier, The layout](eldritch.md#the-layout)), with at least 0.66 blocks to spare across and 0.24 above. **Vectors** (`scale` → width × height, fits): 1.0 → 1.2 × 3.4, yes; 1.1 → 1.32 × 3.74, yes; 1.2 → 1.44 × 4.08, yes; 1.4 → 1.68 × 4.76, yes; 1.5 → 1.8 × 5.1, no (rejected at load).

### Created, owned and reported

- **`ChoristerFactory.create(level, instance, risePos, maxHealth)`** gives none if `instance` isn't one of the three keys or `maxHealth` is outside 1–1024. Otherwise a configured entity, **not added**: at the rise point, yaw 180 (facing the well), walk angle 0, its own tick count `t` 0, attributes as above, health = max, `setPersistenceRequired()` (`MC:Mob.java:1131`), no gravity, and its Step recorded. The [Eldritch tier](eldritch.md#the-rise) adds it and checks the insertion. **The factory never calls `finalizeSpawn`**, which would draw a random follow-range bonus and handedness (`MC:Mob.java:1115–1126`).
- **Ownership,** first in every tick: if its Step's encounter slot isn't `ACTIVE` with this entity, it **discards itself**, with no death and no report. A copy another mod makes, a cross-level copy and a Chorister left behind by a slot reset never fight.
- **The confirmed-death report**, exactly as the frame requires: `die(source)` records `dead`, calls `super.die(source)`, and calls `Encounter.onConfirmedDeath(this)` **only if** `dead` went from false to true across that call. A `LivingDeathEvent` a listener cancels makes `super.die` return first (`MC:world/entity/LivingEntity.java:1526–1529`; `NF:event/entity/living/LivingDeathEvent.java:27–30`), so nothing is reported. **Nothing else of ours reports a death.**
- **Never saved:** `noSave`, so an unload removes it and the frame resets the slot ([Eldritch tier, Reset, unload and peaceful](eldritch.md#reset-unload-and-peaceful)).

## The moves

### The four move types

Move types are a **closed set in code**; every instance and every number is data ([architecture, Data and code](architecture.md#data-and-code)). Lengths are in **tenths of a block**, angles in whole degrees, damage in **tenths of a health point**, durations in ticks.

Every zone is **horizontal geometry from the Chorister's position C** at the tick it is tested, with the facing unit vector **f = (−sin yaw, cos yaw)**, vanilla's convention (yaw 0 faces +Z). For a participant at P, v = P − C and d = |v|. A strike, breath or hear also needs **−2.0 ≤ py − Cy ≤ 4.0**.

| Type | A participant is in the zone iff |
|---|---|
| **strike** | a sector: `d ≤ radius`, and `d < 0.5` or `v·f ≥ d·cos(half_angle)` |
| **hear** | a lane: `0 ≤ v·f ≤ length` and `|f × v| ≤ half_width` |
| **breath** | an annulus: `inner ≤ d ≤ outer`. An **inhale** has `inner` 0; an **exhale** has `inner` above 0 |
| **knell** | a ring along everything standing ("The knell") |

- **Directional moves** (strike and hear) **lock**: their aim is the facing at the first locked tick ("The schedule").
- **Vectors** (C = (0.5, 12.5), yaw 180, so facing the axis):
  - strike, radius 45, half-angle 45, at (0.5, 9.0), (3.5, 11.0), (0.5, 8.0), (0.5, 7.9), (0.5, 13.0), (0.6, 12.5), (3.0, 10.5) → in, out, in, out, out, in, out;
  - hear, length 340, half-width 12, at (0.5, −20.0), (1.6, 0.0), (1.8, 0.0), (0.5, −22.0), (0.5, 13.5) → in, in, out, out, out;
  - breath 0–50 at (0.5, 7.6), (0.5, 7.4) → in, out; breath 50–110 at (0.5, 7.5), (0.5, 1.5), (0.5, 1.4), (0.5, 7.6) → in, in, out, out.

### The knell

At its ACTIVE tick a knell **releases** `waves` waves (1 or 2) from C's position then, its **origin**; wave *j* **starts** `j × wave_gap` ticks after the release. A wave started *s* has its **front** at `speed × k` tenths at tick `s + k`, for k = 0 to ⌊`max_radius` / `speed`⌋, so it **lives** ⌊`max_radius` / `speed`⌋ + 1 ticks.

**Each tick a wave lives,** each eligible participant (A1, "Targets and hits") is **attempted** iff they are **on the ground** (`onGround()`, on any surface) and `|10·d − speed·k| ≤ band`, with d their horizontal distance from the origin in blocks, and **their token for that wave's record slot isn't that wave's serial**.

- **Ground, not floor.** A participant on a raised block inside the arena would stand above a floor-bound wave, so the knell reads the ground: it runs through everything standing. **A jump clears it:** vanilla's jump keeps a player off the ground for **11** ticks on flat ground (vector: initial speed 0.42, gravity 0.08, drag 0.98), and the front covers a standing player for 2 or 3 ticks. That is an ideal-ground figure, not a promise for every surface. `onGround` is the flag the client reports and vanilla also trusts for fall damage; disclosed.
- **Tokens: one attempt per player per wave.** Each wave is written into one of the Chorister's **two wave records** at its release ("Sync and state") and gets a **fresh serial** there, from a counter kept in its Step's encounter slot (`long`, starting at 1). The counter survives resets and rematches and never repeats while the server runs, so a token left from an earlier encounter can never match. Each player holds **two tokens per Step, indexed by record slot**, in the [Road service's](eldritch.md#sync-and-state) per-player memory beside `last_station`, each 0 until written.
  - A player is attempted only if `tokens[step][slot] ≠ record.serial`. **That slot's token is written before `hurtServer` is called**, so **every** attempt consumes it, whatever its outcome: blocked, cancelled, refused by invulnerability frames, or zero in peaceful.
  - Reusing a record replaces only its own slot's token. A wave that is still held keeps its slot, so its token is never evicted by another wave. Ordering by serial age could evict it: in the long/short/short vector a player hit by waves 1 and 2 would lose wave 1's token to wave 3, and wave 1 could hit them again.
  - So a seventeenth player who walks in after sixteen others were hit is still attempted once, and nobody is attempted twice.
  - Tokens are memory: a player who logs out and back in during a wave may be attempted once more by it; disclosed.
  - **Vectors** (wave → record slot and serial): the long/short/short cycle (all W 20, R 8; gaps 0, 11 and 200; one long wave at speed 3, band 6, max 400, then two short ones at speed 10, band 7, max 340; cycle 298) releases at 60, 89 and 129 into slots 0, 1 and 1 with serials 1, 2 and 3. The first wave, held until 194, keeps slot 0 throughout. Nihthaa's double knells by `t` 630: starts 280 and 296 into slots 0 and 1 (serials 1 and 2), then 614 and 630 into slots 0 and 1 (serials 3 and 4).
- **Vectors** (band 6, max 350; first tick a standing player at d is attempted): d 0.0, speed 5 → 0; 3.2, 5 → 6; 16.85, 5 → 33; 34.0, 5 → 67; 35.7, 5 → never; 34.0, 6 → 56; 35.7, 6 → never.

### The move sets

`W` windup, `L` lock (directional moves only), `R` recovery, `G` gap; damage raw and, in brackets, on hard. Every Chorister's `opening` is 40.

| Instance | Move | Type | W / L / R / G | Zone | Damage (hard) | Shield |
|---|---|---|---|---|---|---|
| Wuuren | `strike` | strike | 30 / 20 / 24 / 20 | radius 45, half-angle 45 | 80 (12.0) | 0 s |
| | `knell` | knell | 40 / – / 30 / 20 | speed 5, band 6, max 350, 1 wave | 50 (7.5) | – |
| Suhlaa | `strike` | strike | 28 / 19 / 22 / 16 | radius 45, half-angle 45 | 90 (13.5) | 3 s |
| | `inhale` | breath | 30 / – / 10 / 0 | 0–50 | 90 (13.5) | – |
| | `exhale` | breath | 20 / – / 24 / 16 | 50–110 | 90 (13.5) | – |
| | `knell` | knell | 36 / – / 26 / 16 | speed 6, band 6, max 350, 1 wave | 60 (9.0) | – |
| Nihthaa | `hear` | hear | 30 / 18 / 20 / 12 | length 340, half-width 12 | 110 (16.5) | 3 s |
| | `strike` | strike | 24 / 18 / 20 / 12 | radius 50, half-angle 40 | 100 (15.0) | 3 s |
| | `inhale` | breath | 26 / – / 8 / 0 | 0–55 | 100 (15.0) | – |
| | `exhale` | breath | 20 / – / 20 / 12 | 55–120 | 100 (15.0) | – |
| | `knell` | knell | 32 / – / 24 / 12 | speed 6, band 6, max 350, 2 waves 16 apart | 70 (10.5) | – |

| Instance | `cycle` | Cycle length | `turn` (degrees a tick) | `walk` (tenths of a degree a tick; blocks a tick) |
|---|---|---|---|---|
| Wuuren | strike, strike, knell | 241 | 10 | 5 (0.105) |
| Suhlaa | strike, inhale, exhale, knell | 248 | 12 | 7 (0.147) |
| Nihthaa | hear, strike, inhale, exhale, knell, strike | 334 | 15 | 9 (0.188) |

- **Inhale then exhale** is Suhlaa's lesson: leave the inner disc, then, within 20 ticks, step back inside it or get beyond the outer ring. Every walk is slower than a walking player.
- **A knell wave lives** 71 ticks at speed 5 and 59 at speed 6; Nihthaa's two waves together hold the field for 75 ticks after the release.

### Dodging

A directional move's lock must give its target time to leave the zone **at a walk, slowed by the [warp spec's](warp.md#danger) `pressing_dark`** (Slowness, amplifier 0, −15 %), after **6 ticks** of reaction and network allowance: about 250 ms to perceive the lock cue and react (5 ticks) plus one tick of round trip on a typical server. The lock is a sub-window of the windup, so no cycle length changes. Strafing can't sprint, so walking speed is the bar: **0.2158 blocks a tick** (about 4.32 m/s), so 0.18343 slowed. Two distances must fit:
- **a strafe from 2 blocks out:** `min(2·tan(half_angle), √(radius² − 4))`, sideways along the line that the target's position and the facing make;
- **the sector's inscribed radius** `radius·sin(h) / (1 + sin(h))`: from anywhere inside, the shortest way out is never longer.

For a lane, the strafe is `half_width`.

**Vectors** (radius, half-angle, lock → strafe, inscribed, available, fits): Wuuren's strike 45, 45, 20 → 2.0, 1.863961, 2.56802, yes; Suhlaa's 45, 45, 19 → 2.0, 1.863961, 2.38459, yes; Nihthaa's 50, 40, 18 → 1.678199, 1.956393, 2.20116, yes. Counter-examples: 45, 60, 20 → 3.464102, 2.088457, 2.56802, **no**; 50, 70, 16 → 4.582576, 2.422272, 1.8343, **no**; 50, 40, 16 → 1.678199, 1.956393, 1.8343, **no** (so `MIN_LOCK` alone doesn't fit Nihthaa's strike). Nihthaa's hear (half-width 12, lock 18) → needed 1.2, available 2.20116, yes; with lock 8 → 0.36686, no.

- **The walking speed is the reference's assumption,** measured by the slowed-strafe GameTest ("Tests"). The inequality is checked during authored-content validation of the shipped file ("Authored content, Rejected"); world-load validation does not enforce it, so a datapack may tune past it.

## The schedule

The Chorister keeps its own tick count `t`: 0 on its first tick, +1 at the end of each of its ticks. A pause in entity ticking pauses the fight; it never skips a move. With C the cycle's total length and each entry's length `W + 1 + R + G`:

1. `t < opening`: **OPENING**.
2. Otherwise `u = (t − opening) mod C`; walk the cycle to the entry holding `u`; with `k` the tick inside it, first match: `k < W − L` **WINDUP**; `k < W` **LOCKED** for a directional move, WINDUP for any other; `k = W` **ACTIVE**; `k ≤ W + R` **RECOVERY**; otherwise **GAP**.

- **Facing.** While OPENING, WINDUP or GAP, the facing turns toward the target (the yaw from C to the target's position) by at most `turn` degrees a tick; the delta is wrapped into (−180, 180], and an exact 180 turns positive. LOCKED, ACTIVE and RECOVERY hold it. **The aim is the facing at the first LOCKED tick**, held through ACTIVE. With no target, the facing holds.
- **What the players see is the aim.** The facing is synced as its own value ("Sync and state"); the server sets the yaw, body yaw and head yaw to it every tick, and the client renders **body and head from the synced facing**, not from vanilla's body and head smoothing, which lags and is separate from the yaw (`MC:world/entity/ai/control/LookControl.java:47–60`; `LivingEntity.java:3125–3132`; `client/renderer/entity/LivingEntityRenderer.java:247–251`). The facing the client draws is the one a strike or hear lands along.
- **Moves fire on time** whether anyone is in reach or not. The timeline is a pure function of `t` and the instance. Only the facing and the walk read the target.
- **The opening** (40 ticks): the bowed pose straightens; no move, no walk; fully damageable. Nothing strikes on the tick it appears ([progression, What the Voices do](progression.md#what-the-voices-do)).
- **Vectors** (`t` → entry, phase, `k`, loop):
  - Wuuren (cycle 241): 0 → opening 0; 39 → opening 39; 40 → 0, WINDUP, 0; 49 → 0, WINDUP, 9; 50 → 0, LOCKED, 10; 70 → 0, ACTIVE, 30; 71 → 0, RECOVERY, 31; 95 → 0, GAP, 55; 115 → 1, WINDUP, 0; 190 → 2, WINDUP, 0; 230 → 2, ACTIVE, 40; 231 → 2, RECOVERY, 41; 281 → 0, WINDUP, 0, loop 1.
  - Suhlaa (248): 40 → 0, WINDUP, 0; 48 → 0, WINDUP, 8; 49 → 0, LOCKED, 9; 68 → 0, ACTIVE, 28; 107 → 1, WINDUP, 0; 108 → 1, WINDUP, 1; 138 → 1, RECOVERY, 31; 148 → 2, WINDUP, 0; 169 → 2, RECOVERY, 21; 205 → 2, GAP, 57; 288 → 0, WINDUP, 0, loop 1.
  - Nihthaa (334): 40 → 0, WINDUP, 0; 51 → 0, WINDUP, 11; 52 → 0, LOCKED, 12; 70 → 0, ACTIVE, 30; 103 → 1, WINDUP, 0; 108 → 1, WINDUP, 5; 109 → 1, LOCKED, 6; 186 → 2, ACTIVE, 26; 214 → 3, WINDUP, 19; 255 → 4, WINDUP, 7; 287 → 4, RECOVERY, 39; 374 → 0, WINDUP, 0, loop 1.
  - Facing (from, toward, rate → after): 180, −170, 12 → −170; 180, 90, 10 → 170; 0, 180, 15 → 15; −175, 175, 15 → 175; 30, 32.5, 10 → 32.5. The yaw from (0.5, 12.5) to (0.5, 0.5), (−11.5, 12.5), (12.5, 12.5), (6.5, 18.5) is 180, 90, −90, −45.

### Each tick, in order

1. **Ownership** ("Created, owned and reported").
2. **The leash** ("The choir walk").
3. **The anchor:** if `origin_time` isn't the game time minus the current, **unincremented** `t` (the first tick, or a skipped tick), it is written as that. So the client's `game time − origin_time` is always the `t` the server is executing.
4. **The phase** for `t`. On entering a phase: its sound and the synced values it changes.
5. **The target:** `Encounter.targetFor(this)`, **every tick**.
6. **The facing**, as above.
7. **The walk**, in GAP only.
8. **ACTIVE:** a strike, breath or hear applies its hits ("Targets and hits"); a knell releases its waves into the wave records ("Sync and state").
9. **Live waves:** each record whose wave lives now runs its attempts; a record whose wave has ended is cleared.
10. `t` += 1, and nothing else.

**Anchor vectors** (the game times at which it ticks → `t`, `origin_time`, the client's `t`): 1000 → 0, 1000, 0; 1001 → 1, 1000, 1; 1002 → 2, 1000, 2; 1005 (two ticks skipped) → 3, 1002, 3; 1006 → 4, 1002, 4.

## The choir walk

**The Chorister moves only along the choir walk:** the circle of radius 12 (`WALK_RADIUS`) about the axis centre (0.5, 0.5), through its rise point. At walk angle φ it stands at **(0.5 − 12 sin φ, floor + 1, 0.5 + 12 cos φ)**; φ = 0 is the rise point.

- **It walks only in GAP,** toward the angle of its target about the axis, by at most `walk` tenths of a degree a tick, with the same wrap and tie rule as facing; then `setPos`. Its velocity is zeroed every tick, so nothing pushes or carries it.
- **Collision guard:** a step whose box at the new φ would collide (`level.noCollision`) isn't taken; it holds that tick.
- **The walk is clear by construction.** The Road templates keep **every cell with 81 ≤ d² ≤ 225 at y 1–5** air at Steps 1–3 (amended in the [Eldritch tier spec](eldritch.md#the-layout)). At `scale` 1.4 the box touches only cells with 106 ≤ d² ≤ 185 anywhere on the circle (vector; 113–181 at 1.0). The walk never meets the well (d² ≤ 36) or the Step Bell, the Reliquary or the landing (d² 289).
- **The leash,** second in every tick: if its y is below the floor − 8, or its position isn't in the arena, φ becomes 0 and it is set on the rise point; otherwise, if anything moved it off the circle, it is set back on the circle at φ.
- **Vectors:** φ 0, 90, 180, −45 → (0.5, 12.5), (−11.5, 0.5), (0.5, −11.5), (8.985281, 8.985281). A step (φ, target's angle, `walk` → φ after): 0, 90, 5 → 0.5; 0, 180, 5 → 0.5; 0, 0.3, 5 → 0.3; 179.8, −179, 9 → −179.3.
- **Options considered:**

  | Option | Why not |
  |---|---|
  | vanilla pathfinding inside the ring | arbitrary masonry and a 1.7-block-wide box make the stuck boss; paths allocate |
  | standing on the rise cell | "stand behind it" becomes the whole fight |

## Targets and hits

**Targeting:** `Encounter.targetFor(this)`, read every tick, steers only the facing and the walk. The creature never chooses its own target.

**Whom a move hits:** only **eligible participants**: those admitted to this Step's encounter **and** eligible now, **`Encounter.isEligibleParticipant(player)`**, the one predicate the frame uses for its target, its credit, every hit and every incoming player damage (amendment A1).

**At each ACTIVE tick and each live wave's tick:** `Encounter.eligibleParticipants(out)` fills a preallocated array of 16 with their entity ids, **ascending**. Then, per id, first failure skips that participant:
1. the Chorister is alive and its slot is still `ACTIVE` with it, **otherwise the whole move stops**;
2. the player resolves in this level and `isEligibleParticipant` still holds, read now;
3. still in the zone, both positions read now (for a knell, also on the ground, and their token for the record's slot isn't the wave's serial; the token is written now);
4. `hurtServer` with the move's damage source and `damage / 10`, with `getSecondsToDisableBlocking()` answering the move's `shield_seconds` for that call only (cleared in `finally`).

- **Rechecks around callbacks.** Damage listeners run inside each `hurtServer` and can kill, move or remove anyone, the Chorister included, so nothing read before a call is trusted after it ([foci, Targets](foci.md#targets)).
- **The damage source:** a strike, breath or hear uses `damageSources().source(type, this)` (`MC:world/damagesource/DamageSources.java:79`): direct and causing entity both the Chorister, at C. A knell uses `new DamageSource(type, this, this, origin)` (`MC:DamageSource.java:34`), so its source position is the wave's origin, and a shield is held toward the wave, not toward wherever the Chorister has walked since.
- **Never anyone else:** a non-participant inside a zone is never touched ([Eldritch tier, The split](eldritch.md#the-split)).

## Damage

### Damage types and difficulty

**Four damage types,** `vftv:chorister_strike`, `vftv:chorister_knell`, `vftv:chorister_breath` and `vftv:chorister_hear`, each with `message_id` `vftv.<path>`, **`scaling` `always`**, the content's `exhaustion` (0.1 as shipped), default effects and death message type, and the one tag **`minecraft:no_knockback`** (`MC:world/damagesource/DamageType.java:12–22`).

- **Difficulty:** `always` makes `scalesWithDifficulty` true whoever caused it (`MC:DamageSource.java:94–100`). A player's hurt applies the type's scaling function (`MC:world/entity/player/Player.java:723`; `NF:common/damagesource/IScalingFunction.java:25–35`): peaceful 0, easy `min(d/2 + 1, d)`, normal `d`, hard `1.5·d`. **Vectors** (raw → peaceful / easy / normal / hard): 1.0 → 0 / 1.0 / 1.0 / 1.5; 5.0 → 0 / 3.5 / 5.0 / 7.5; 8.0 → 0 / 5.0 / 8.0 / 12.0; 13.0 → 0 / 7.5 / 13.0 / 19.5.
- **Peaceful:** a hurt scaled to 0 returns before anything happens (`Player.java:739`): no damage, no flinch, no blocking, no knockback. The fight runs and can be won ([Eldritch tier, Reset, unload and peaceful](eldritch.md#reset-unload-and-peaceful)).
- **Death text:** with a causing entity the key is `death.attack.<message_id>` with two arguments, the victim and the Chorister (`MC:DamageSource.java:69–83`).

**Never a bypass, never a push.** Every damage type an instance names must, as resolved now:
- have `scaling` `always` or `when_caused_by_living_non_player` (**checked at world open**; the world is refused otherwise);
- be **in** `#minecraft:no_knockback` and **outside** every `#minecraft:bypasses_*` tag (armour, shield, invulnerability, cooldown, effects, resistance, enchantments; `MC:tags/DamageTypeTags.java:9–15,34`) and outside the [armor spec's](armor.md#absorbing) `#vftv:baffle_ignores` (**checked at every tag bind**: at server start and on `TagsUpdatedEvent` with server data, `NF:event/TagsUpdatedEvent.java:53–66`).

A move whose type fails the tag check is **harmless** until a later bind passes: it still animates and telegraphs, so the fight stays readable, and one error is logged. A datapack can tune exhaustion, never make a Chorister bypass a defence.

### Shields

- **Blocking is vanilla's,** from the source position (`MC:LivingEntity.java:1375–1416`).
- **A blocked strike or hear disables the shield** for its `shield_seconds` (3 at Steps 2 and 3, 0 at Step 1), as an axe does (`Player.java:743–750`). A knell or breath never disables one.
- **No recoil.** Blocking a non-projectile hit calls the attacker's `blockedByItem`, which knocks the **defender** back by 0.5 whatever the damage type's tags (`MC:LivingEntity.java:1411–1412,1453–1458`). The Chorister's `blockedByItem` does nothing, so a blocking player beside the well is never pushed. `Player.blockUsingItem`'s shield disabling still runs after it.

### Invulnerability frames

- **The player's are vanilla's:** a second hit within 10 ticks deals only its excess over the first (`MC:LivingEntity.java:1270–1292`). On every shipped instance, two ACTIVE ticks on one player are at least 16 ticks apart, except where a knell wave crosses another move.
- **The Chorister has none.** A thin `neoforge.road` listener on **`LivingIncomingDamageEvent`**, at `EventPriority.LOWEST`, sets the damage container's post-attack invulnerability ticks to 0 when the target is a Chorister (`NF:event/entity/living/LivingIncomingDamageEvent.java:74–85`); vanilla then stores 0 (`MC:LivingEntity.java:1290`), **before** the hit's `LivingDamageEvent.Post` callbacks run (`:1351–1353`). So a callback's nested hit from another participant counts in full, and four participants' hits all count. Without this, entry health ×2.5 for four players would meet about one player's throughput. A later LOWEST listener could restore ticks; disclosed. **A solo player gains too:** rapid sources (several projectiles at once, a Beam's pulses, fire ticks) all count in full; disclosed and intended.

### Fairness limits

Fixed in code in **`ChoristerLimits`** (`api`):

| Limit | Value | Guarantees |
|---|---|---|
| `MIN_WINDUP` | 20 ticks | every move telegraphs for at least 1 s: pose, mark and sound |
| `MIN_LOCK` | 16 ticks | a directional aim is fixed and shown at least 0.8 s before it lands: the 6-tick dodge latency plus a 10-tick walk |
| `MIN_RECOVERY` | 8 ticks | every move leaves a punish window |
| `MAX_MOVE_DAMAGE` | 130 tenths | on hard, **an unarmoured vanilla player at full health survives any one hit** (19.5 < 20); one hit, not a combination, and not under another mod's amplification |
| `ARENA_REACH` | 340 tenths | every cycle holds a knell whose last front plus band reaches 34 blocks, the farthest arena point from anywhere on the walk (12 + 22): **no safe cell** |
| `MAX_WAVES` | 2 | knell waves held at once, each from its release until its end |
| `MAX_LIFETIME` | 134 ticks | the longest legal wave (⌊400 / 3⌋ + 1) |
| `MAX_HELD_TICKS` | 174 ticks | the longest a record is held: from the release, the largest `wave_gap` (40) plus `MAX_LIFETIME` |
| `MAX_MOVES`, `MAX_CYCLE`, `MAX_CYCLE_TICKS` | 8, 12, 2,400 | bounded data |
| `WALK_RADIUS`; base box; eye | 12; 1.2 × 3.4; 3.0 | the walk and the fit |
| `MAX_SCALE` | 1.4 | the rise-box fit |
| `MAX_MARK_QUADS` | 384 | "Rendering" |

And always: **no invulnerable phase** (it can be hurt from its first tick, in the opening and in every recovery), **no healing**, **no adds**, **no block change**, **no knockback**, **no randomness**, **no armour, shield or Baffle bypass**.

## Who can hurt it

**Damage a player causes is refused unless that player is an eligible participant at that moment.** `isInvulnerableTo(level, source)` is vanilla's answer (`MC:LivingEntity.java:4162–4163`), or true iff `source.getEntity()` is a player who isn't in creative mode and for whom `Encounter.isEligibleParticipant` is false, read **at the moment of damage**. A projectile resolves to its owner. Damage that no player causes (fire, a fall, a command, another mod) is never refused by this rule.

- **Why "eligible now", not "admitted":** admission refreshes every 10 ticks ([Eldritch tier, Admission](eldritch.md#admission)), so a player who steps out of the arena stays admitted for up to 10 ticks. Corner-shooting from just outside would slip through a membership test.
- **Why at all:** the Road's templates are 47 blocks wide and the arena is 22 in radius, so there are places outside it from which a bow reaches the Chorister and no move reaches back. Participants are exactly those the moves can reach, so risk and reward stay together. This is a rule about **sources**, not a phase: every participant can hurt it at every tick (amendment A2). A template rule against standing outside the arena couldn't stop a shot fired while airborne.
- **Creative is exempt** so operators can test; `/kill` and commands always work.
- **Refused damage posts nothing of ours** and gives no threat: the frame's threat already counts only participants.

### No cheese

| Exploit | Answer |
|---|---|
| pillaring, walls, lava, water, beds, boats | [Eldritch tier, No building](eldritch.md#no-building) |
| standing out of reach and shooting | the knell reaches every arena cell and reads the ground, so a perch inside the arena is hit too |
| shooting from outside the arena | refused ("Who can hurt it") |
| holding a shield up | strikes and hears disable it at Steps 2 and 3; nothing recoils the blocker |
| a group's hits lost to frames | the Chorister has none |
| kiting it round the ring | it walks slower than a walking player; the knell, breath and hear don't care where it stands |
| hiding in the well | below the floor − 2 isn't the arena: no target, no credit; the catch returns them ([Eldritch tier, The catch](eldritch.md#the-catch)) |
| pushing it into the well, leading it, riding it | not pushable, kinematic, `Enemy`, no vehicle can be placed |
| healing it to stall a reset | `heal` does nothing; beneficial effects are refused |

## Interactions

| With | Rule |
|---|---|
| armour, enchantments, Resistance | apply: the types bypass nothing |
| the Baffle | absorbs after armour, in `LivingDamageEvent.Pre` ([armor, Absorbing](armor.md#absorbing)) |
| totems | vanilla |
| warp | the Chorister applies no warp, and v1 has no Voice spawn type ([warp, Danger](warp.md#danger)). Vuulas is in context, so `pressing_dark`'s Slowness may fire in a fight; it carries no Darkness, so the floor marks stay readable. Every telegraph also has a sound and a subtitle, and every lock is sized for the slowed walk ("Dodging") |
| Joining | a Joined Listener's Void foci hit harder through the amplifier ([foci, The Void tier](foci.md#the-void-tier)); nothing special here |
| Silencing | changes nothing: `targetFor` decides, and Silencing exempts only the Hush ([Eldritch tier, The capstones](eldritch.md#the-capstones)) |
| phantoms | client-only and harmless ([warp, Phantoms](warp.md#phantoms)); a Chorister is never mistaken for an apparition ("Rendering") |
| PvP | not involved: a Chorister's damage isn't PvP, and players' own fire is vanilla |
| a screen opened mid-fight | doesn't pause it: it isn't a Voice event ([Eldritch tier](eldritch.md#reset-unload-and-peaceful)) |

## Group scaling

**Health only:** the [Eldritch tier's](eldritch.md#the-rise) entry health, ×1, ×1.5, ×2 and ×2.5 for 1, 2, 3 and 4 or more admitted, capped at 1024. No damage, cadence or move changes with the group. The frame rejects damage scaling ("punishes bringing friends"); area moves already reach everyone; threat lets a group choose who faces the strike. The Chorister having no invulnerability frames is what makes the health scaling honest.

## Rendering and sound

### The model

**One model, frozen geometry,** in model pixels (1/16 block). The origin is at the feet's centre, y up, −z the front and +x the entity's left, as for the [Answerers](worldgen.md#models). Box UVs in vanilla's layout on a **128 × 128** texture, with no overlap; the left arm mirrors the right arm's net. It is 54 pixels tall at `scale` 1.0 (3.375 blocks, inside the 3.4 box).

| Part | Parent | Pivot | Cuboid from | Size (w × h × d) | UV origin | Mirror |
|---|---|---|---|---|---|---|
| `robe` | root | (0, 0, 0) | (−8, 0, −6) | 16 × 30 × 12 | (0, 0) | |
| `chest` | `robe` | (0, 30, 0) | (−7, 30, −5) | 14 × 14 × 10 | (0, 42) | |
| `bell` | `chest` | (0, 37, −5) | (−4, 32, −7) | 8 × 10 × 2 | (48, 42) | |
| `hood` | `chest` | (0, 44, 0) | (−5, 44, −5) | 10 × 10 × 10 | (56, 0) | |
| `brow` | `hood` | (0, 50, −5) | (−5, 50, −7) | 10 × 2 × 2 | (56, 20) | |
| `right_arm` | `chest` | (−8, 42, 0) | (−11, 16, −2) | 4 × 28 × 4 | (96, 0) | |
| `left_arm` | `chest` | (8, 42, 0) | (7, 16, −2) | 4 × 28 × 4 | (96, 0) | yes |

- **Look** ([art style §5](art-style.md#5-tone-ramp-by-stage), Stage 4 row): a tall, faceless, robed scholar with **a bell for a heart**. The `bell` plate holds a void-ink mouth with the only teal inside it. Mauve robes drain to `hush` from the lower right through the 1-px checker; the ink outline; no face; no emissive layer; shadow radius 0.9.
- **Three skins,** `assets/vftv/textures/entity/chorister/<instance>.png`, the drain deepening by Step: **Wuuren's** bell is still mostly brass, with a weighted hem band; **Suhlaa's** is half drained, with rib grooves down the chest; **Nihthaa's** is wholly `hush`, with a lit brow band and a teal slit above it.
- **Not an apparition:** an apparition is human-sized, seen by one player and gone within 4 blocks ([warp, Phantoms](warp.md#phantoms)); a Chorister stands 3.4 to 4.1 blocks, solid, casting a shadow, with the bell. The asset review sets them side by side.
- **Body and head** are drawn from the synced facing ("The schedule").

### Poses

Client time, in degrees: x pitch, y yaw, z roll. Linear between keys, fixed in code.

| State | Keys |
|---|---|
| opening | `hood` x +60 → 0 and the arms crossed (x −40, y ±30) → hanging, over `opening` |
| idle | the arms z ±3 on a 40-tick sine |
| walking | `robe` x +4; the arms x ±8 on a 20-tick sine, in opposite phase |
| strike | windup to the lock: `right_arm` x 0 → −160; locked: held; active: → −20 in 2 ticks |
| knell | windup: both arms x −170, z ±10 (hands together); active: → 0 in 2 ticks |
| inhale | windup: the arms y ±70, `hood` x −25; active: the arms y ∓30 in 2 ticks |
| exhale | windup: the arms crossed, `hood` x +15; active: the arms y ±80, `hood` x −20 |
| hear | windup: `hood` x +25, the arms x +20; locked: held; active: `hood` x −15 in 2 ticks |
| recovery | `hood` x +20, easing to 0 by the recovery's end: the punish window reads as a bow |
| death | vanilla's death roll |

### Marks

**Every move is drawn on the ground before it lands.** The renderer draws flat quads from the atlas `assets/vftv/textures/entity/chorister/mark.png` (16 × 32: frame 0 at v 0–16, a dashed strip, **tracking**; frame 1 at v 16–32, a solid strip, **locked** or an area), translucent, at **floor + 1.02**, just above the top face of the ring's floor layer. Shape, not colour alone, carries the state; the colours are `teal2` (locked, areas) and `hush3` (tracking).

| Move | Drawn from | Shape |
|---|---|---|
| strike | windup start, following the facing; solid from the lock | the sector: its arc in 12 segments and both radii |
| hear | likewise | the lane: both sides in 1-block dashes, and the far end |
| breath | windup start | the inner and outer circles, 32 segments each (no inner circle at 0) |
| knell | windup: a circle of radius 1.5 at its feet; from each wave's start until its end: its front, 48 segments, 0.4 blocks wide | |

- **Raised surfaces.** When the viewer stands inside the arena with their feet at least 0.5 above floor + 1, every mark is drawn a second time **at the viewer's feet + 0.02**, so a knell is seen coming on a block, a frame or a stair.
- **Culling** follows the marks, not only the creature: the renderer's culling box (`MC:client/renderer/entity/EntityRenderer.java:64–97`) is the union of the entity's box, the directional and breath marks' extent around C, and for each live wave a box around **its own origin**, its current front + band wide, from floor + 1 to floor + 13.
- **Budget:** at most **`MAX_MARK_QUADS` (384)** a Chorister a frame: two waves (96), the longest legal lane (70) and their raised copies. **Zero allocation per frame** in our renderer (an allocation-budget test).

### Particles

No particle type of our own, so no particle JSON. The server sends vanilla `DustParticleOptions` in `hush2` with `ServerLevel#sendParticles`: **24** evenly spaced along the zone's outline at each ACTIVE, **8** at each knell release, **2** every 20 ticks of walking (fixed). At most 24 a Chorister a tick.

### Sound

Stage 4 register ([art style §7](art-style.md#7-sound-palette)): breath through glass and a late room; no clang; never words, so neither the Voice nor the lexicon. All play at the Chorister on `SoundSource.HOSTILE`, pitch 1.0, to everyone in range. Files are `assets/vftv/sounds/chorister/<event path>_<n>.ogg`, `sounds.json` entries carry no overrides and name `subtitles.vftv.chorister.<event path>`; every file is at most 3 s with the standard faded tail.

| Event | Variants | Plays | Volume |
|---|---|---|---|
| `vftv:chorister.rise` | 1 | the rise | 2.5 |
| `vftv:chorister.strike_windup`, `.knell_windup`, `.breath_windup`, `.hear_windup` | 3 each | the move's windup start | 2.5 (heard 40 blocks: the whole arena) |
| `vftv:chorister.lock` | 3 | the first LOCKED tick | 2.0 |
| `vftv:chorister.strike`, `.knell`, `.breath`, `.hear` | 3 each | ACTIVE; `.knell` at each wave's start | 2.0 |
| `vftv:chorister.footfall` | 3 | every 20 ticks while walking | 0.8 |
| `vftv:chorister.hurt` | 3 | a hurt, at most once every 10 ticks | 1.0 |
| `vftv:chorister.death` | 1 | death | 2.5 |

13 events, 35 files. Sounds may overlap: a double knell's releases are 16 ticks apart. At most 3 plays a Chorister a tick.

## Authored content

Authored inputs follow the [authored-content envelope](conventions.md#the-envelope).

```
content/eldritch/choristers.json          the three instances and the four damage types
content/eldritch/chorister_fixtures.json  the vector tables, transcribed
content/lang/en_us/choristers.json        every key under "Lang keys"
```

| Input | Schema | Generated output |
|---|---|---|
| `choristers.json` | `vftv:choristers/1` | `data/vftv/vftv/chorister/<path>.json` for each instance; `data/vftv/damage_type/<path>.json` for each type, with `message_id` `vftv.<path>` and `scaling` `always` written by datagen; the four types in `data/minecraft/tags/damage_type/no_knockback.json` (`"replace": false`) |
| `chorister_fixtures.json` | `vftv:chorister_fixtures/1` | nothing: read by the unit tests |
| `lang/<lang>/choristers.json` | `vftv:lang/1` | merged into `assets/vftv/lang/<lang>.json` ([conventions, Lang](conventions.md#lang)) |

### The registry

**The synced datapack registry `vftv:chorister`** (`data/<ns>/vftv/chorister/<path>.json`), with a network codec, as the [aspect registry](../adr/0003-aspects-as-datapack-registry.md) is. It holds **exactly** `vftv:wuuren` (Step 1), `vftv:suhlaa` (Step 2) and `vftv:nihthaa` (Step 3), the keys in `EldritchKeys`. A pack may change their values; it can't add or remove one.

| Field | Range |
|---|---|
| `health` | 1–1000 (the frame's field) |
| `armor` | 0–20 |
| `scale` | 0.5–1.4 |
| `skin` | a resource id; the texture `assets/<ns>/textures/entity/chorister/<path>.png` |
| `opening` | 20–100 ticks |
| `turn` | 1–30 degrees a tick |
| `walk` | 0–20 tenths of a degree a tick (0 stands) |
| `moves` | 1–8 entries, keys matching `[a-z_]{1,32}` |
| `cycle` | 1–12 keys of `moves`; every move used at least once |
| every move | `type` (`strike`, `knell`, `breath` or `hear`); `windup` 20–200; `recovery` 8–200; `gap` 0–200; `damage` 1–130; `damage_type` a damage type id |
| `strike` | `lock` 16 to `windup`; `radius` 10–80; `half_angle` 15–90; `shield_seconds` 0–5 |
| `hear` | `lock` 16 to `windup`; `length` 40–340; `half_width` 4–20; `shield_seconds` 0–5 |
| `breath` | `inner` 0 to `outer` − 10; `outer` 10–150 |
| `knell` | `speed` 3–10; `band` 3–10, with `2·band ≥ speed + 4` (a player sprinting into the front is still sampled); `max_radius` 340–400, with `speed·⌊max_radius / speed⌋ + band ≥ 340`; `waves` 1–2; `wave_gap` 0 with one wave, else 10–40 |

No other field is allowed on a type: `lock` and `shield_seconds` only on `strike` and `hear`.

**Load validation** runs when the world opens, on the full registries through registry ops, so every id resolves. It **refuses the world** with an error naming the entry ([foci, Load validation](foci.md#load-validation)'s pattern):
- a field missing, unknown or out of range;
- a key other than the three, or one missing;
- a cycle longer than 2,400 ticks;
- a cycle without a knell meeting `ARENA_REACH`;
- **more than `MAX_WAVES` waves held at once**, by the rule below;
- a `damage_type` that doesn't exist or isn't difficulty-scaled.

**Waves held at once.** A wave is **held** from its knell's release until its end (release + `j × wave_gap` + its lifetime), because its record is reserved at the release ("Sync and state"). A record is held at most `MAX_HELD_TICKS` (174) ticks after its release, and releases repeat with period C from the opening, so from `t = opening + 174` on the count is periodic. **The count is checked at every `t` from 0 to `opening + MAX_HELD_TICKS + C − 1`** with every release up to then, which covers the transient start and one full steady-state period, whatever the lifetimes and wave gaps. Two simulated loops would not, and neither would a horizon built on the lifetime alone (134), because a second wave is held from the release, up to 40 ticks before it starts. **Vectors** (most held; first `t` above 2): Wuuren 1, never; Suhlaa 1, never; Nihthaa 2, never. A legal-looking single knell (W 20, R 8, G 0; speed 3, band 6, max 350), cycle 29 and lifetime 117: 5, first at `t` 118, in its third cycle, so rejected. Two knells (W 20, R 8, G 20 and G 180; the same wave), cycle 258: 2, never; at `t` 110 both are held, started 60 (entry 0) and 109 (entry 1), from different origins if it walked in between. Nihthaa at `t` 281 and 300 holds both its waves (starts 280 and 296), at 340 only the second. **A delayed double knell** (W 135, R 8, G 6; speed 3, band 6, max 400; 2 waves 40 apart), cycle 150: 3, first at `t` 325, when the records of the waves started 215, 325 and 365 are all held, so rejected; a 134-tick horizon would stop at 323 and miss it. The long/short/short cycle ("The knell"): 2, never.

**Datapack registries load when the world opens** and `/reload` doesn't rebuild them, so an instance change needs the world reopened. Damage-type **tags** change at `/reload` and are rechecked at each bind ("Damage").

### `choristers.json`

`{"schema": "vftv:choristers/1", "damage_types": {<id>: {"exhaustion"}}, "instances": {<id>: <registry entry>}}`. The shipped file:

```json
{"schema": "vftv:choristers/1",
 "damage_types": {
  "vftv:chorister_strike": {"exhaustion": 0.1},
  "vftv:chorister_knell": {"exhaustion": 0.1},
  "vftv:chorister_breath": {"exhaustion": 0.1},
  "vftv:chorister_hear": {"exhaustion": 0.1}},
 "instances": {
  "vftv:wuuren": {"health": 200, "armor": 4, "scale": 1.0, "skin": "vftv:wuuren",
   "opening": 40, "turn": 10, "walk": 5,
   "moves": {
    "strike": {"type": "strike", "windup": 30, "lock": 20, "recovery": 24, "gap": 20, "damage": 80,
               "damage_type": "vftv:chorister_strike", "radius": 45, "half_angle": 45, "shield_seconds": 0},
    "knell": {"type": "knell", "windup": 40, "recovery": 30, "gap": 20, "damage": 50,
              "damage_type": "vftv:chorister_knell", "speed": 5, "band": 6, "max_radius": 350,
              "waves": 1, "wave_gap": 0}},
   "cycle": ["strike", "strike", "knell"]},
  "vftv:suhlaa": {"health": 260, "armor": 6, "scale": 1.1, "skin": "vftv:suhlaa",
   "opening": 40, "turn": 12, "walk": 7,
   "moves": {
    "strike": {"type": "strike", "windup": 28, "lock": 19, "recovery": 22, "gap": 16, "damage": 90,
               "damage_type": "vftv:chorister_strike", "radius": 45, "half_angle": 45, "shield_seconds": 3},
    "inhale": {"type": "breath", "windup": 30, "recovery": 10, "gap": 0, "damage": 90,
               "damage_type": "vftv:chorister_breath", "inner": 0, "outer": 50},
    "exhale": {"type": "breath", "windup": 20, "recovery": 24, "gap": 16, "damage": 90,
               "damage_type": "vftv:chorister_breath", "inner": 50, "outer": 110},
    "knell": {"type": "knell", "windup": 36, "recovery": 26, "gap": 16, "damage": 60,
              "damage_type": "vftv:chorister_knell", "speed": 6, "band": 6, "max_radius": 350,
              "waves": 1, "wave_gap": 0}},
   "cycle": ["strike", "inhale", "exhale", "knell"]},
  "vftv:nihthaa": {"health": 320, "armor": 8, "scale": 1.2, "skin": "vftv:nihthaa",
   "opening": 40, "turn": 15, "walk": 9,
   "moves": {
    "hear": {"type": "hear", "windup": 30, "lock": 18, "recovery": 20, "gap": 12, "damage": 110,
             "damage_type": "vftv:chorister_hear", "length": 340, "half_width": 12, "shield_seconds": 3},
    "strike": {"type": "strike", "windup": 24, "lock": 18, "recovery": 20, "gap": 12, "damage": 100,
               "damage_type": "vftv:chorister_strike", "radius": 50, "half_angle": 40, "shield_seconds": 3},
    "inhale": {"type": "breath", "windup": 26, "recovery": 8, "gap": 0, "damage": 100,
               "damage_type": "vftv:chorister_breath", "inner": 0, "outer": 55},
    "exhale": {"type": "breath", "windup": 20, "recovery": 20, "gap": 12, "damage": 100,
               "damage_type": "vftv:chorister_breath", "inner": 55, "outer": 120},
    "knell": {"type": "knell", "windup": 32, "recovery": 24, "gap": 12, "damage": 70,
              "damage_type": "vftv:chorister_knell", "speed": 6, "band": 6, "max_radius": 350,
              "waves": 2, "wave_gap": 16}},
   "cycle": ["hear", "strike", "inhale", "exhale", "knell", "strike"]}}}
```

**Rejected,** besides every registry rule: `damage_types` other than exactly the four, or an `exhaustion` outside 0.0–1.0; `instances` other than exactly the three; a `damage_type` not among the four; and, as a **content lint on the shipped file**, any directional move whose lock fails "Dodging" or any value differing from the file above.

### Fixtures

`{"schema": "vftv:chorister_fixtures/1", …}`, under the [fixtures convention](conventions.md#fixtures); the reference prints it (`python3 -I choristers_ref.py --fixtures`).

| Section | Record |
|---|---|
| `schedule` | `instance` (an id in `choristers.json`); `t` ≥ 0; `expect` `{entry` (−1 in the opening), `phase` (`OPENING`, `WINDUP`, `LOCKED`, `ACTIVE`, `RECOVERY` or `GAP`), `k`, `loop}` |
| `sector` | `c` and `p` (`[x, z]`, blocks); `yaw`; `radius`, `half_angle` (registry units); `expect` boolean |
| `lane` | `c`, `p`, `yaw`, `length`, `half_width`; `expect` boolean |
| `annulus` | `c`, `p`, `inner`, `outer`; `expect` boolean |
| `knell` | `d` (blocks); `speed`, `band`, `max_radius`; `expect` the first tick, or null for never |
| `jump` | `expect`: airborne ticks |
| `turn` | `yaw`, `target`, `rate` (degrees); `expect` the yaw after |
| `yaw_to` | `c`, `p`; `expect` the yaw |
| `circle` | `phi`; `expect` `[x, z]` |
| `walk` | `phi`, `target_phi`, `walk` (registry units); `expect` φ after |
| `scaling` | `damage` (raw); `expect` `{peaceful, easy, normal, hard}` |
| `box` | `scale`; `expect` `{width, height, fits}` |
| `walk_cells` | `scale`; `expect` `[min d², max d²]` |
| `strafe` | `radius`, `half_angle`, `lock`; `expect` `{at_2, inscribed, available, ok}` |
| `lane_dodge` | `half_width`, `lock`; `expect` `{needed, available, ok}` |
| `waves` | `name`; either `instance`, or `opening` and `cycle` (knell moves: `windup`, `recovery`, `gap`, `speed`, `band`, `max_radius`, `waves`, `wave_gap`); `expect` `{max_live, first_overflow` (null for never)`}` |
| `live_at` | `waves` (a `waves` record's `name`); `t`; `expect` the held waves, `[{start, entry}]` ascending |
| `slots` | `waves` (a `waves` record's `name`); `until`; `expect` every wave released by then, in release order, `[{release, start, entry, slot` (−1 if none was free)`, serial}]`, serials counted from 1 |
| `anchor` | `game_time` (the game times at which the Chorister ticks, in order, from its first tick); `expect` `{t, origin_time, client_t}` |

Decimals compare to 6 places. **The complete file:**

```json
{"schema": "vftv:chorister_fixtures/1",
 "schedule": [
  {"instance": "vftv:wuuren", "t": 0, "expect": {"entry": -1, "phase": "OPENING", "k": 0, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 39, "expect": {"entry": -1, "phase": "OPENING", "k": 39, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 40, "expect": {"entry": 0, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 49, "expect": {"entry": 0, "phase": "WINDUP", "k": 9, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 50, "expect": {"entry": 0, "phase": "LOCKED", "k": 10, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 70, "expect": {"entry": 0, "phase": "ACTIVE", "k": 30, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 71, "expect": {"entry": 0, "phase": "RECOVERY", "k": 31, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 95, "expect": {"entry": 0, "phase": "GAP", "k": 55, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 115, "expect": {"entry": 1, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 190, "expect": {"entry": 2, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 230, "expect": {"entry": 2, "phase": "ACTIVE", "k": 40, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 231, "expect": {"entry": 2, "phase": "RECOVERY", "k": 41, "loop": 0}},
  {"instance": "vftv:wuuren", "t": 281, "expect": {"entry": 0, "phase": "WINDUP", "k": 0, "loop": 1}},
  {"instance": "vftv:suhlaa", "t": 40, "expect": {"entry": 0, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 48, "expect": {"entry": 0, "phase": "WINDUP", "k": 8, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 49, "expect": {"entry": 0, "phase": "LOCKED", "k": 9, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 68, "expect": {"entry": 0, "phase": "ACTIVE", "k": 28, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 107, "expect": {"entry": 1, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 108, "expect": {"entry": 1, "phase": "WINDUP", "k": 1, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 138, "expect": {"entry": 1, "phase": "RECOVERY", "k": 31, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 148, "expect": {"entry": 2, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 169, "expect": {"entry": 2, "phase": "RECOVERY", "k": 21, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 205, "expect": {"entry": 2, "phase": "GAP", "k": 57, "loop": 0}},
  {"instance": "vftv:suhlaa", "t": 288, "expect": {"entry": 0, "phase": "WINDUP", "k": 0, "loop": 1}},
  {"instance": "vftv:nihthaa", "t": 40, "expect": {"entry": 0, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 51, "expect": {"entry": 0, "phase": "WINDUP", "k": 11, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 52, "expect": {"entry": 0, "phase": "LOCKED", "k": 12, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 70, "expect": {"entry": 0, "phase": "ACTIVE", "k": 30, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 103, "expect": {"entry": 1, "phase": "WINDUP", "k": 0, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 108, "expect": {"entry": 1, "phase": "WINDUP", "k": 5, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 109, "expect": {"entry": 1, "phase": "LOCKED", "k": 6, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 186, "expect": {"entry": 2, "phase": "ACTIVE", "k": 26, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 214, "expect": {"entry": 3, "phase": "WINDUP", "k": 19, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 255, "expect": {"entry": 4, "phase": "WINDUP", "k": 7, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 287, "expect": {"entry": 4, "phase": "RECOVERY", "k": 39, "loop": 0}},
  {"instance": "vftv:nihthaa", "t": 374, "expect": {"entry": 0, "phase": "WINDUP", "k": 0, "loop": 1}}],
 "sector": [
  {"c": [0.5, 12.5], "yaw": 180.0, "radius": 45, "half_angle": 45, "p": [0.5, 9.0], "expect": true},
  {"c": [0.5, 12.5], "yaw": 180.0, "radius": 45, "half_angle": 45, "p": [3.5, 11.0], "expect": false},
  {"c": [0.5, 12.5], "yaw": 180.0, "radius": 45, "half_angle": 45, "p": [0.5, 8.0], "expect": true},
  {"c": [0.5, 12.5], "yaw": 180.0, "radius": 45, "half_angle": 45, "p": [0.5, 7.9], "expect": false},
  {"c": [0.5, 12.5], "yaw": 180.0, "radius": 45, "half_angle": 45, "p": [0.5, 13.0], "expect": false},
  {"c": [0.5, 12.5], "yaw": 180.0, "radius": 45, "half_angle": 45, "p": [0.6, 12.5], "expect": true},
  {"c": [0.5, 12.5], "yaw": 180.0, "radius": 45, "half_angle": 45, "p": [3.0, 10.5], "expect": false}],
 "lane": [
  {"c": [0.5, 12.5], "yaw": 180.0, "length": 340, "half_width": 12, "p": [0.5, -20.0], "expect": true},
  {"c": [0.5, 12.5], "yaw": 180.0, "length": 340, "half_width": 12, "p": [1.6, 0.0], "expect": true},
  {"c": [0.5, 12.5], "yaw": 180.0, "length": 340, "half_width": 12, "p": [1.8, 0.0], "expect": false},
  {"c": [0.5, 12.5], "yaw": 180.0, "length": 340, "half_width": 12, "p": [0.5, -22.0], "expect": false},
  {"c": [0.5, 12.5], "yaw": 180.0, "length": 340, "half_width": 12, "p": [0.5, 13.5], "expect": false}],
 "annulus": [
  {"c": [0.5, 12.5], "inner": 0, "outer": 50, "p": [0.5, 7.6], "expect": true},
  {"c": [0.5, 12.5], "inner": 0, "outer": 50, "p": [0.5, 7.4], "expect": false},
  {"c": [0.5, 12.5], "inner": 50, "outer": 110, "p": [0.5, 7.5], "expect": true},
  {"c": [0.5, 12.5], "inner": 50, "outer": 110, "p": [0.5, 1.5], "expect": true},
  {"c": [0.5, 12.5], "inner": 50, "outer": 110, "p": [0.5, 1.4], "expect": false},
  {"c": [0.5, 12.5], "inner": 50, "outer": 110, "p": [0.5, 7.6], "expect": false}],
 "knell": [
  {"d": 0.0, "speed": 5, "band": 6, "max_radius": 350, "expect": 0},
  {"d": 3.2, "speed": 5, "band": 6, "max_radius": 350, "expect": 6},
  {"d": 16.85, "speed": 5, "band": 6, "max_radius": 350, "expect": 33},
  {"d": 34.0, "speed": 5, "band": 6, "max_radius": 350, "expect": 67},
  {"d": 35.7, "speed": 5, "band": 6, "max_radius": 350, "expect": null},
  {"d": 34.0, "speed": 6, "band": 6, "max_radius": 350, "expect": 56},
  {"d": 35.7, "speed": 6, "band": 6, "max_radius": 350, "expect": null}],
 "jump": {"expect": 11},
 "turn": [
  {"yaw": 180.0, "target": -170.0, "rate": 12.0, "expect": -170.0},
  {"yaw": 180.0, "target": 90.0, "rate": 10.0, "expect": 170.0},
  {"yaw": 0.0, "target": 180.0, "rate": 15.0, "expect": 15.0},
  {"yaw": -175.0, "target": 175.0, "rate": 15.0, "expect": 175.0},
  {"yaw": 30.0, "target": 32.5, "rate": 10.0, "expect": 32.5}],
 "yaw_to": [
  {"c": [0.5, 12.5], "p": [0.5, 0.5], "expect": 180.0},
  {"c": [0.5, 12.5], "p": [-11.5, 12.5], "expect": 90.0},
  {"c": [0.5, 12.5], "p": [12.5, 12.5], "expect": -90.0},
  {"c": [0.5, 12.5], "p": [6.5, 18.5], "expect": -45.0}],
 "circle": [
  {"phi": 0.0, "expect": [0.5, 12.5]},
  {"phi": 90.0, "expect": [-11.5, 0.5]},
  {"phi": 180.0, "expect": [0.5, -11.5]},
  {"phi": -45.0, "expect": [8.985281, 8.985281]}],
 "walk": [
  {"phi": 0.0, "target_phi": 90.0, "walk": 5, "expect": 0.5},
  {"phi": 0.0, "target_phi": 180.0, "walk": 5, "expect": 0.5},
  {"phi": 0.0, "target_phi": 0.3, "walk": 5, "expect": 0.3},
  {"phi": 179.8, "target_phi": -179.0, "walk": 9, "expect": -179.3}],
 "scaling": [
  {"damage": 1.0, "expect": {"peaceful": 0.0, "easy": 1.0, "normal": 1.0, "hard": 1.5}},
  {"damage": 5.0, "expect": {"peaceful": 0.0, "easy": 3.5, "normal": 5.0, "hard": 7.5}},
  {"damage": 8.0, "expect": {"peaceful": 0.0, "easy": 5.0, "normal": 8.0, "hard": 12.0}},
  {"damage": 13.0, "expect": {"peaceful": 0.0, "easy": 7.5, "normal": 13.0, "hard": 19.5}}],
 "box": [
  {"scale": 1.0, "expect": {"width": 1.2, "height": 3.4, "fits": true}},
  {"scale": 1.1, "expect": {"width": 1.32, "height": 3.74, "fits": true}},
  {"scale": 1.2, "expect": {"width": 1.44, "height": 4.08, "fits": true}},
  {"scale": 1.4, "expect": {"width": 1.68, "height": 4.76, "fits": true}},
  {"scale": 1.5, "expect": {"width": 1.8, "height": 5.1, "fits": false}}],
 "walk_cells": [
  {"scale": 1.0, "expect": [113, 181]},
  {"scale": 1.4, "expect": [106, 185]}],
 "strafe": [
  {"radius": 45, "half_angle": 45, "lock": 20, "expect": {"at_2": 2.0, "inscribed": 1.863961, "available": 2.56802, "ok": true}},
  {"radius": 45, "half_angle": 45, "lock": 19, "expect": {"at_2": 2.0, "inscribed": 1.863961, "available": 2.38459, "ok": true}},
  {"radius": 50, "half_angle": 40, "lock": 18, "expect": {"at_2": 1.678199, "inscribed": 1.956393, "available": 2.20116, "ok": true}},
  {"radius": 45, "half_angle": 60, "lock": 20, "expect": {"at_2": 3.464102, "inscribed": 2.088457, "available": 2.56802, "ok": false}},
  {"radius": 50, "half_angle": 70, "lock": 16, "expect": {"at_2": 4.582576, "inscribed": 2.422272, "available": 1.8343, "ok": false}},
  {"radius": 50, "half_angle": 40, "lock": 16, "expect": {"at_2": 1.678199, "inscribed": 1.956393, "available": 1.8343, "ok": false}}],
 "lane_dodge": [
  {"half_width": 12, "lock": 18, "expect": {"needed": 1.2, "available": 2.20116, "ok": true}},
  {"half_width": 12, "lock": 8, "expect": {"needed": 1.2, "available": 0.36686, "ok": false}}],
 "waves": [
  {"name": "wuuren", "instance": "vftv:wuuren", "expect": {"max_live": 1, "first_overflow": null}},
  {"name": "suhlaa", "instance": "vftv:suhlaa", "expect": {"max_live": 1, "first_overflow": null}},
  {"name": "nihthaa", "instance": "vftv:nihthaa", "expect": {"max_live": 2, "first_overflow": null}},
  {"name": "fast_knell", "opening": 40, "cycle": [{"windup": 20, "recovery": 8, "gap": 0, "speed": 3, "band": 6, "max_radius": 350, "waves": 1, "wave_gap": 0}], "expect": {"max_live": 5, "first_overflow": 118}},
  {"name": "two_knells", "opening": 40, "cycle": [{"windup": 20, "recovery": 8, "gap": 20, "speed": 3, "band": 6, "max_radius": 350, "waves": 1, "wave_gap": 0}, {"windup": 20, "recovery": 8, "gap": 180, "speed": 3, "band": 6, "max_radius": 350, "waves": 1, "wave_gap": 0}], "expect": {"max_live": 2, "first_overflow": null}},
  {"name": "delayed", "opening": 40, "cycle": [{"windup": 135, "recovery": 8, "gap": 6, "speed": 3, "band": 6, "max_radius": 400, "waves": 2, "wave_gap": 40}], "expect": {"max_live": 3, "first_overflow": 325}},
  {"name": "long_short_short", "opening": 40, "cycle": [{"windup": 20, "recovery": 8, "gap": 0, "speed": 3, "band": 6, "max_radius": 400, "waves": 1, "wave_gap": 0}, {"windup": 20, "recovery": 8, "gap": 11, "speed": 10, "band": 7, "max_radius": 340, "waves": 1, "wave_gap": 0}, {"windup": 20, "recovery": 8, "gap": 200, "speed": 10, "band": 7, "max_radius": 340, "waves": 1, "wave_gap": 0}], "expect": {"max_live": 2, "first_overflow": null}}],
 "live_at": [
  {"waves": "two_knells", "t": 110, "expect": [{"start": 60, "entry": 0}, {"start": 109, "entry": 1}]},
  {"waves": "nihthaa", "t": 281, "expect": [{"start": 280, "entry": 4}, {"start": 296, "entry": 4}]},
  {"waves": "nihthaa", "t": 300, "expect": [{"start": 280, "entry": 4}, {"start": 296, "entry": 4}]},
  {"waves": "nihthaa", "t": 340, "expect": [{"start": 296, "entry": 4}]}],
 "slots": [
  {"waves": "long_short_short", "until": 160, "expect": [{"release": 60, "start": 60, "entry": 0, "slot": 0, "serial": 1}, {"release": 89, "start": 89, "entry": 1, "slot": 1, "serial": 2}, {"release": 129, "start": 129, "entry": 2, "slot": 1, "serial": 3}]},
  {"waves": "nihthaa", "until": 630, "expect": [{"release": 280, "start": 280, "entry": 4, "slot": 0, "serial": 1}, {"release": 280, "start": 296, "entry": 4, "slot": 1, "serial": 2}, {"release": 614, "start": 614, "entry": 4, "slot": 0, "serial": 3}, {"release": 614, "start": 630, "entry": 4, "slot": 1, "serial": 4}]}],
 "anchor": [
  {"game_time": 1000, "expect": {"t": 0, "origin_time": 1000, "client_t": 0}},
  {"game_time": 1001, "expect": {"t": 1, "origin_time": 1000, "client_t": 1}},
  {"game_time": 1002, "expect": {"t": 2, "origin_time": 1000, "client_t": 2}},
  {"game_time": 1005, "expect": {"t": 3, "origin_time": 1002, "client_t": 3}},
  {"game_time": 1006, "expect": {"t": 4, "origin_time": 1002, "client_t": 4}}]}
```

**Rejected,** beyond the convention: a `schedule` or `waves` instance not in `choristers.json`; a `live_at` or `slots` naming no `waves` record.

### Lang keys

[Lang files](conventions.md#lang); every key below is required in `en_us`, and **the text shown is the shipped text**.

| Key | Args | Text |
|---|---|---|
| `entity.vftv.chorister` | 0 | "Chorister" |
| `chorister.vftv.wuuren`, `.suhlaa`, `.nihthaa` | 0 | "Wuuren", "Suhlaa", "Nihthaa" |
| `death.attack.vftv.chorister_strike` | 2 | "%s was struck down by %s" |
| `death.attack.vftv.chorister_knell` | 2 | "%s was rung under by %s" |
| `death.attack.vftv.chorister_breath` | 2 | "%s lost their breath to %s" |
| `death.attack.vftv.chorister_hear` | 2 | "%s was heard by %s" |
| `subtitles.vftv.chorister.rise` | 0 | "Chorister rises" |
| `subtitles.vftv.chorister.strike_windup` | 0 | "Chorister raises an arm" |
| `subtitles.vftv.chorister.knell_windup` | 0 | "Chorister gathers a knell" |
| `subtitles.vftv.chorister.breath_windup` | 0 | "Chorister draws breath" |
| `subtitles.vftv.chorister.hear_windup` | 0 | "Chorister listens" |
| `subtitles.vftv.chorister.lock` | 0 | "Chorister holds still" |
| `subtitles.vftv.chorister.strike` | 0 | "Chorister strikes" |
| `subtitles.vftv.chorister.knell` | 0 | "Knell rolls out" |
| `subtitles.vftv.chorister.breath` | 0 | "Chorister breathes out" |
| `subtitles.vftv.chorister.hear` | 0 | "Chorister answers" |
| `subtitles.vftv.chorister.footfall` | 0 | "Chorister steps" |
| `subtitles.vftv.chorister.hurt` | 0 | "Chorister hurts" |
| `subtitles.vftv.chorister.death` | 0 | "Chorister falls silent" |

- **The names** are the lexicon's words, each with `"name"` among its `uses` already, capitalised as proper names and linted for membership and phonology ([warp, The lexicon](warp.md#the-lexicon)). The lexicon is unchanged.
- **No `.player` variants:** every hit has a causing entity, so vanilla never asks for them.

**Rejected:** a Void-tongue name failing the lint.

## Events

**None of our own.** The [Eldritch tier](eldritch.md#events) posts `RoadEncounterEvent`; each hit posts vanilla's and NeoForge's damage events on the player; the confirmed death goes through the frame's interface. **No command.**

## Sync and state

**Server-authoritative.** The client predicts nothing and derives every phase, pose and mark from the synced values below, the synced registry and the schedule's pure function, so a player who starts tracking mid-fight sees the right marks at once.

| State | Storage | Synced to |
|---|---|---|
| the instances | the registry `vftv:chorister` | every client, by its network codec |
| `step` (byte, 0–2) | entity data | trackers |
| `origin_time` (long): the game time minus the `t` being executed, written on the first tick and after a skipped tick, before the phase is derived | entity data | trackers |
| `facing` (float): written whenever it changes | entity data | trackers |
| **two wave records**, `wave0` and `wave1`, each `start` (int, the wave's start in `t`, −1 empty), `entry` (byte, the cycle entry) and `origin` (`Vector3fc`) | entity data | trackers |
| `t`, φ, the participant snapshot, the wave serials | memory, per Chorister | never |
| knell tokens, two per Step, indexed by wave-record slot | memory, per player (the Road service) | never |
| the wave serial counter (`long`) | memory, per Step's encounter slot: survives resets and rematches | never |

- **Entity data** uses vanilla's serializers (`MC:network/syncher/EntityDataSerializers.java:53–56,143`) and sends only changes.
- **The two records are independent.** A knell writes each of its waves into a record at its **release**, the first free one (lowest index first; a record is free once its wave has ended): its start (`t` at release, + `wave_gap` for a second wave), entry and origin. Load validation guarantees a free record. A client draws a record's front from `t − start`, its entry's `speed`, `band` and `max_radius`, and its origin. One "last release" field couldn't recover an older wave from another move or origin.
- **No payload of our own,** so no client-to-server surface. Positions travel by vanilla tracking.
- **Persistence:** none. Choristers are `noSave` and encounters are memory ([Eldritch tier, Reset, unload and peaceful](eldritch.md#reset-unload-and-peaceful)).
- **Reloads:** the registry is fixed while a world is open; a damage-type tag change applies at the next bind ("Damage").

## Tuning and limits

Every balance number is a field of the **`vftv:chorister` registry** (written by datagen from `content/eldritch/choristers.json`), overridable by a datapack within the ranges under "Authored content". There is no tuning file of our own.

| Number | Home |
|---|---|
| health, armour, scale, skin, opening, turn, walk; every move's timings, zone, damage, damage type and shield seconds; every cycle | the registry |
| each damage type's exhaustion | `choristers.json`, written into `data/vftv/damage_type/` |
| the damage types' `scaling` (`always`) and their `no_knockback` tag | code (datagen) |
| the fairness limits and fixed sizes | `ChoristerLimits` in `api` ("Damage, Fairness limits") |
| the zones' vertical band (−2.0 to 4.0), the dodge assumption (latency 6, walking 0.2158) | `ChoristerLimits` |
| poses, mark segments and widths, particle counts, the hurt-sound gap (10), footfall interval (20) and sound volumes; tracking range 4 and update interval 2 | code (presentation) |
| entry health, admission, threat, reset and every encounter number | the [Eldritch tier's tuning](eldritch.md#tuning-and-limits) |

## Performance

**Heavy-base workload:** the [Eldritch tier's](eldritch.md#performance) (4 players at Step 3 against Nihthaa with the full ×2.5, 2 at Step 1 against Wuuren, each fight restarted on defeat). On the client: one player in Step 3's ring with the Chorister, its marks and a double knell in view, standing on a raised block for part of the run, and the two lower rings visible down the well.

**Share** (the frame's): server **0.04 ms mean, 0.13 ms p99**; client **0.04 ms p50**. Targets, not measurements.

| When | Work |
|---|---|
| Per Chorister per tick (≤ 3) | ownership and leash O(1); the phase O(cycle ≤ 12) from precomputed offsets; `targetFor` (the frame's, O(16)); facing O(1); in GAP one `noCollision` on a box of at most 2 × 2 × 6 cells (**native**); entity data on change. **Zero allocation** (an allocation-budget test) |
| Per ACTIVE | one snapshot of ≤ 16 ids; ≤ 16 O(1) zone tests; ≤ 16 `hurtServer` calls (**native**, with callbacks); ≤ 24 particles; ≤ 2 sounds |
| Per live wave per tick (≤ 2 records, each held ≤ 174 ticks and live ≤ 134) | one snapshot; ≤ 16 O(1) tests and token reads; each player hurt at most once a wave. **Zero allocation** |
| Per client frame per Chorister | one 7-cuboid model; ≤ 384 mark quads; **zero allocation in our code** |
| Packets | entity data: ≤ 1 facing change a tick, ≤ 3 other changes a move; positions every 2 ticks while walking; sounds and particles as above |

**Measured separately as load scenarios** (reported):
- all three Choristers at once with 16 participants each inside every zone;
- a pathological legal instance: 12 moves at minimum durations and two **134-tick** waves held at once, one of them a second wave reserved 40 ticks before its start (**174 ticks held**);
- raised-surface marks: a viewer on a block with both waves and a lane drawn twice;
- 70 players crowding an arena (the frame's scan cap bounds our snapshot).

## Tests

All three layers ([quality gates](quality-gates.md#test-layers)).

**Unit (`model` tier and codecs):**
- **every fixture row** through `core.road.model`: the schedule, the zones, the knell, the jump, facing, the walk, scaling, the box, the walk's cells, the strafe and lane margins, waves held and records at a tick;
- the registry codec and **every** load-validation rule, including **the third-cycle overflow** (`fast_knell` rejected), **the delayed second wave** (`delayed` rejected at 325), and `two_knells` and `long_short_short` accepted;
- record slots and serials (`slots`) and the anchor (`anchor`) through the same code the entity uses;
- the `content/` schemas and their rejection rules; the dodge lint on the shipped file;
- the damage-type checks: `scaling` `never` refuses the world; a type outside `no_knockback` or inside any `bypasses_*` tag or `#vftv:baffle_ignores` is harmless after a bind;
- allocation budgets: the tick without a hit, the ACTIVE loop, a wave tick, the renderer's frame.

**Datagen-phase:**
- the generated registry entries, damage types and `no_knockback` entries match the content;
- **the Road templates keep the choir walk clear** (81 ≤ d² ≤ 225, y 1–5, air at Steps 1–3).

**GameTest:**
- **the creature:** the factory's Chorister at each instance's scale fits the rise box; max health 1024 accepted; both resistances 1.0; `/summon` refused; it discards itself when not owned; `die` reports once, and a cancelling `LivingDeathEvent` listener reports nothing; `heal` and each beneficial effect refused;
- **the schedule:** every phase boundary at fixed `t`; **the body and head face the synced facing at the lock**;
- **the anchor:** **on a normal first tick**, and **after a skipped tick** (the ring's chunk held below entity ticking for two ticks), a tracking client's derived `t`, phase and every wave's age equal the server's on every following tick;
- **dodging:** **a target with Slowness I strafing at a walk, starting 6 ticks after the lock, leaves each shipped strike from 2 blocks out and the hear's lane**; a target who leaves the sector after the lock isn't hit, one who enters is;
- **the knell:** a standing player attempted once a wave; a mid-jump player not hit; a player on a raised block hit; **admission churn**: sixteen hit by one wave, then a seventeenth walking in before it ends is attempted once and nobody twice; **a blocked, a cancelled and a peaceful attempt each consume the token**; two independent waves from different origins (`two_knells`) hit by their own origins; **the long/short/short regression**: a player hit by waves 1 and 2, teleported by a test callback to 29.7 blocks from the shared origin at `t` 159, is not attempted again by wave 1 at 160 after wave 3 has taken slot 1; **serial uniqueness across resets**: a second encounter at the same Step after a reset issues serials above the first's, and a token left from the first never matches;
- **zones:** inhale then exhale; the hear's lane; a non-participant inside any zone never touched;
- **damage per difficulty** for an unarmoured player against the scaling table, and **peaceful: no damage, no knockback, the fight won**; armour and the Baffle reduce it;
- **shields:** a blocked strike disables the shield for its seconds; **blocked strike, hear, breath and knell beside the well leave the player's velocity unchanged**;
- **frames:** four participants hitting in one tick all count; **a `LivingDamageEvent.Post` listener whose nested hit from a second participant counts in full**;
- **who can hurt it:** a participant's melee and arrow count; **an admitted player who has stepped outside the arena, before the next refresh, is refused**; a never-admitted player is refused; a creative player is not;
- **the walk and leash:** confined to the circle; holding at an obstruction a test places; the leash from outside the arena and from below the floor − 8;
- **callbacks:** a damage listener that kills the Chorister mid-move stops the move; one that teleports a later participant out skips them;
- **a datapack type moved into `#minecraft:bypasses_armor`** makes its move harmless after `/reload`;
- the discovery guard over every test id.

**E2E:**
- a full fight per instance on one client, with screenshots at each mark, from a raised block too, and the subtitles shown;
- **the body's facing matches the locked sector and lane** in screenshots;
- two clients see the same marks and timings;
- **a client that starts tracking at `t` 110 of `two_knells` (a test instance) draws both live waves at their own origins**, and one joining mid-Nihthaa draws its double knell;
- reconnect mid-fight;
- the asset, lang, model and sound validators; the three skins and every pose reviewed;
- the client profile on the reference machine.

## Contracts on sibling specs

| Needs from | What | Met at |
|---|---|---|
| eldritch | the completion interface: `ChoristerFactory.create`, `targetFor`, `isParticipant`, `arena`, `isEligibleParticipant`, `eligibleParticipants` and `onConfirmedDeath`; "has no invulnerable phase" as worded here | [eldritch, The split](eldritch.md#the-split) |
| eldritch | the rise cell and box, the arena and the **choir walk** row (81 ≤ d² ≤ 225, y 1–5, air at Steps 1–3), with the templates rejecting a blocked walk | [eldritch, The layout](eldritch.md#the-layout), [Templates](eldritch.md#templates) |
| eldritch | entry health, admission, threat, reset and every other encounter number; `RoadEncounterEvent` | [eldritch, Encounters](eldritch.md#encounters), [Tuning and limits](eldritch.md#tuning-and-limits), [Events](eldritch.md#events) |
| eldritch | the frame and the creature in the one feature `core.road` | [eldritch, Events](eldritch.md#events) |
| research (#37) | `vftv:the_road`'s prose teaches that a knell is jumped, an inhale fled and an exhale met close or far, that a lock fixes the aim and a strike can break a shield, and that only Listeners in the ring can hurt a Chorister, **stating no Chorister number** (there is no `chorister` fact source) | [research, Production tickets](research.md#production-tickets) |
| warp | context danger stays Slowness only, which the dodge margins assume | [warp, Danger](warp.md#danger) |
| armor | the Baffle absorbs Chorister damage after armour, outside `#vftv:baffle_ignores` | [armor, Absorbing](armor.md#absorbing) |
| API | `ChoristerLimits` and the marker `Chorister` in `api`; the registry and instance keys in `EldritchKeys` | [API, Registry element types](api.md#registry-element-types), [The inventory](api.md#the-inventory) |
| [`CONTEXT.md`](../../CONTEXT.md) | the terms **knell** and **choir walk** | `CONTEXT.md` |
| [vanilla aspect value table #30](https://github.com/andrewbrogoff/voices-from-the-void/issues/30) | the `vftv:chorister` entity's values or a reviewed exemption | not yet stated in any spec |

## Production tickets

This spec settles every decision for its own content. Two required child tickets, which already exist, deliver the assets and the authored content, and **the system is not done until both land**. **Neither writes mod code:** no entity, renderer, model layer, animation, particle-spawning code, listener, datagen provider, validator or test. The asset ticket delivers a generator and assets under the [art style](art-style.md); the content ticket delivers authored inputs under `content/`, in the schemas above.

| Ticket | Delivers | Blocked by |
|---|---|---|
| [**Chorister assets #73**](https://github.com/andrewbrogoff/voices-from-the-void/issues/73) | **Generator:** `tools/art/choristers.py`, seeded and byte-identical on re-run. **Model:** `art/chorister.bbmodel`, written by the generator from "The model"'s table exactly, for review and touch-up. **Skins:** `assets/vftv/textures/entity/chorister/wuuren.png`, `suhlaa.png`, `nihthaa.png`, each 128 × 128 on the model's UV layout, the Stage 4 drain deepening by Step as described. **Mark atlas:** `assets/vftv/textures/entity/chorister/mark.png`, 16 × 32: frame 0 (v 0–16) a dashed strip in `hush3`, frame 1 (v 16–32) a solid strip in `teal2`, both with alpha. **Sounds:** the 13 events and 35 files under "Sound" (`chorister/rise_0`, `strike_windup_0…2`, `knell_windup_0…2`, `breath_windup_0…2`, `hear_windup_0…2`, `lock_0…2`, `strike_0…2`, `knell_0…2`, `breath_0…2`, `hear_0…2`, `footfall_0…2`, `hurt_0…2`, `death_0`), with `sounds.json` entries naming each `subtitles.vftv.chorister.<event>`, whose shipped text is under "Lang keys". **Review:** Blender renders of every pose under "Poses" for each skin; a line-up of the three beside a player, an Answerer and a warp apparition; a top-down preview of every mark at its shipped size, on the floor and on a raised block, with a double knell. Provenance rows. | this spec |
| [**Chorister content #74**](https://github.com/andrewbrogoff/voices-from-the-void/issues/74) | `content/eldritch/choristers.json` and `content/eldritch/chorister_fixtures.json`, **exactly the files printed above**; `content/lang/en_us/choristers.json` with every key and text under "Lang keys". Its review checks the names against the lexicon and the dodge lint. | this spec |
