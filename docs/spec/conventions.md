# Shared conventions

The machinery several system specs share, stated once. A system spec cites the section here and states only what is its own: its holder, its keys, its order of checks, and any per-site difference this file lists. Where a spec and this file disagree, this file wins, and the spec is amended.

Raised by the [holistic review, finding 2](../../build/review/holistic.md). The code lives in `core.common` and `core.common.model`, the shared leaf ([architecture, Layers](architecture.md#layers)); `COMMON_LEAF` keeps it from importing any other `core` feature ([architecture, Enforcement](architecture.md#enforcement)).

| Section | Code in `core.common` |
|---|---|
| [Raw-record retention](#raw-record-retention) | `RawRecordCodec` |
| [Operation guards](#operation-guards) | `OperationGuard` |
| [Checked commits](#checked-commits) | `CheckedCommit` (in `.model`) |
| [Hashing and seeded phases](#hashing-and-seeded-phases) | `Hashes`, `Salts` (in `.model`) |
| [Rules records](#rules-records) | none: each owner's record |
| [Authored content](#authored-content) | none: build-phase datagen |
| [Feedback routing](#feedback-routing) | none: `core.text` owns the status line |
| [Save-format evolution](#save-format-evolution) | `vftv_data_version` |

## Raw-record retention

Applies to every persisted record that carries `format`: player and chunk attachments, block entities, data components and `SavedData` ([architecture, State and sync](architecture.md#state-and-sync)).

### Readable and unreadable

Decoding gives one of two results:
- **Readable:** the format is one this build reads, and every format bound and invariant holds. **Decoding checks format bounds only,** never current tuning or registries. A tuning change is reconciled after decoding, so a lowered threshold never invalidates a saved record.
- **Unreadable:** anything else. That includes an unknown or future `format`, a missing `format`, a wrong type, a value out of bounds, a duplicate or unknown key, a failed invariant, and an id that doesn't parse.

**The vocabulary is "unreadable".** "Dormant" is retired. "Lost" is used only for a record a removal destroys ("Removal" below).

### What an unreadable record does

1. **The raw tag is kept exactly** and written back byte-identical on every save, unload, copy and reload. Nothing writes over it.
2. **One `WARN`,** the first time each holder's record fails to decode in a server session, naming the holder, the `format` and the reason. It is not repeated until the record decodes or the session ends.
3. **The thing does nothing its record would drive.** It runs no tick, step, spill, check or capability (its handler is unavailable), and its contents are never synced.
4. **Every use that would read or write the record is refused** with `message.vftv.unreadable` on the status line, and nothing changes. Descriptive surfaces (a tooltip, a HUD line, a status row, a command reply) show the owner's `<prefix>.unreadable` key with the text "I can't read …", naming the thing.
5. **Synced views carry an `unreadable` flag, never the raw tag.** The one exception is an item's data component: it travels whole, as vanilla sends components.
6. **Only two things replace it:** a build that reads the format (the record decodes on the next load), or an operator's repair command where the owner provides one (`/vftv flux repair`, [Hush, Pending and dormant](hush.md#pending-and-dormant)). A repair logs the raw tag before replacing it. No build ever discards a newer build's record unasked.

### Removal

- A block that drops itself carries the raw tag in its item's component, so the record survives the move ([essentia, Sync and state](essentia.md#sync-and-state)).
- Otherwise, removal **loses** the record, with one `WARN` that logs the raw tag.
- An entity that dies drops nothing it can't read, and logs one `WARN`.

### Missing ids are inert, not unreadable

A readable record that names a registry entry a pack removed is **inert**. It is kept and written back unchanged, drawn with the missing sprite or a "missing" status, and does nothing with the missing part. Restoring the pack restores it. Its refusal is the owner's own key, because it names what is missing (`message.vftv.golem.inert`, `message.vftv.essentia.label_unreadable`).

### The codec

`RawRecordCodec.wrap(Codec<T> bounded)` gives a `Codec<Retained<T>>`, where `Retained<T>` is `Readable(T value)` or `Unreadable(Tag raw, String reason)`. Encoding an `Unreadable` writes `raw` unchanged. Owners supply the bounded codec for `T` and never catch decode errors themselves.

### Retention per site

| Owner | What it adds |
|---|---|
| a `SavedData` store ([Hush](hush.md#pending-and-dormant), [wands](wands.md#sync-and-state)) | the degraded behaviour while the whole store is unreadable: the Hush counts overflow in memory, and node commands refuse with `commands.vftv.node.unreadable` |
| [warp](warp.md#kinds-and-the-record) | an unreadable player record leaves every dial neutral and runs no check; `commit` and `apply` return `UNREADABLE` |
| [wands](wands.md#vis-storage) | an unreadable `vftv:vis` makes the wand **inert** as a whole, and the spend refuses it with `message.vftv.spend.inert`, because the spend reports one outcome per wand |
| [eldritch](eldritch.md#sync-and-state) | its list of what every Road path does with an unreadable `vftv:road` |

## Operation guards

### The guard

A **guard** is a transient flag, keyed by **(owner, holder)**. The holder is a player UUID, an entity UUID, a block-entity instance, a level or a node id. A guard is never persisted, and it is dropped at logout or unload.

`OperationGuard.tryEnter(key)` returns a scope, or nothing if the guard is already held. The scope is closed in `finally` on every path, including a thrown exception.

**Guards are per owner.** The warp guard, the road guard and the Reliquary guard are separate guards on the same player. The Heart can therefore hold the road guard and still call `WarpService.commit`, which takes the warp guard.

### The operation

Every guarded operation runs in this order:

1. **Guard first,** before anything that can run foreign code: an event post, a capability call, an `Ingredient` test, a spawn.
2. **Authorise:** post the owner's cancellable event, if it has one. This is the one point where foreign code runs before the write.
3. **Revalidate everything read before the callback:** the holder is the same instance and not removed, the chunk is still loaded, and the state the decision used is unchanged.
4. **Write,** with no foreign code in between.
5. **Notify:** post events after the write, **while the guard is held**. A throwing listener is logged once. The write stands, and it is never retried or rolled back.
6. **Release** in `finally`.

**Compound operations** take the guard once and make every write through one internal mutation helper, which assumes the guard is held (**trusted nesting**). Only code reached through a foreign callback is refused.

### Re-entry

A call that finds its guard held changes nothing, and:
- **if it acts for a player** (a payload, a use, or a listener running inside that player's action), it is refused with **`message.vftv.busy`** ("Not now.") on that player's status line, **at most once per player per tick**. An API with a status returns `BUSY`. **It is never dropped silently.**
- **if it has no player** (a scheduled step, a think, a pulse), it returns at once. It is rescheduled at its next phase tick or stays queued, so no work is lost.

**Silent by design, because these are not re-entry:**
- a **rate cap** (at most *n* payloads per player per tick): the excess is dropped with no reply;
- a **same-tick duplicate** of one gesture, such as the second hand's use or "already travelled this tick";
- a **context failure with nothing to report to:** a wrong `containerId`, a failed `stillValid`, or out of reach.

A table of first-match rows puts these in their own rows, never in the same row as "the guard is held".

### Transaction-busy

A block whose transfer-transaction root is open holds a per-block guard until the root closes. While it is held, hand uses refuse with `message.vftv.busy`, automation skips the block, and removal is deferred to the root's close, as each owner states ([essentia, Transactions](essentia.md#transactions), [crucible, Essentia out](crucible.md#essentia-out), [infusion, Holding items](infusion.md#holding-items-the-item-handler-and-the-container)). A descriptive status line for a busy block (`status.vftv.infusion.busy`) stays the owner's.

## Checked commits

Every change a player saw quoted is made through one protocol: **the client echoes the `revision` and the quote it saw, and the server recomputes both.**

### The revision

- **Owned by the server,** one per surface instance: an open menu, an open book, or a readout arm. A 32-bit `int` that starts at 0 and adds 1 on each bump, wrapping.
- **It bumps** on any change to an input **the echo doesn't carry**: a menu slot, the player's research revision, a replaced recipe snapshot, or a replaced Rules record.
- **Price inputs never bump it.** These are the discount attribute and the warp factor and amplifier inputs. The echo catches them. A change to one only clears any refusal on show.
- **Detection:** slot listeners fire at once. Everything else is caught by an O(1) **change check** (comparing instances and revisions), run every tick while the surface is open and again at the start of every commit.

### The shape

- **S2C quote:** `(revision, quote)`. The quote holds exactly the values the UI shows. It is re-sent with the new revision after every bump.
- **C2S commit:** `(context, revision, echo)`, where the echo is the quote values the confirm showed, bounded and checked at decode.
- **The server's checks,** in order, with the first failure refusing and nothing changed:

| # | Check | On failure |
|---|---|---|
| 1 | rate cap and same-tick duplicate | dropped, no reply |
| 2 | context: the menu or book is open, `containerId` matches, `stillValid`, reach | dropped, no reply |
| 3 | the owner's guard is free | `BUSY` → `message.vftv.busy` |
| 4 | change check, then `revision` = current | `STALE` |
| 5 | the owner's eligibility, in the owner's order | the owner's refusals |
| 6 | recompute the quote from live state; it equals the echo | `STALE` |
| 7 | write, with no foreign code between check 6 and the write; then notify; release in `finally` | |

- **`STALE`** shows **`message.vftv.cost_changed`** ("The cost has changed.") and sends a fresh quote with the current revision. A retry needs no other change from the player.
- **`CheckedCommit`** (in `core.common.model`) holds checks 4 and 6 as a pure compare. Owners supply the recompute.
- **Unchecked form:** a change no quote was shown for uses the owner's unchecked call: warp's `apply` (decay, stilling, commands) and a spend with no `expected` (a cast).

### Commits per site

| Site | Revision | Echo | What differs, and why |
|---|---|---|---|
| [`WarpService.commit`](warp.md#commit-and-apply) | none: a server API, and the calling surface owns the revision | `expected` `WarpPreview` | Returns `STALE` with the current preview, so each caller can re-quote. It is the inner primitive the research book, the Bargains, the Listen rite and the Heart call. |
| [research](research.md#sync-and-state) `learn`, `buy`, `listen` | `quotes.revision`, one per open book | price and warp preview | Also the monotonic `requestId`, and `noteRevision` for note actions. |
| [Bargain acceptance](warp.md#accepting), [the Listen rite](warp.md#the-listen-rite) | `quotes.revision` (through research's actions) | `echoedWarp` | An offer also carries `offerSeq`, its identity, so a stale action never touches a newer offer. |
| [arcane craft](arcane.md#the-craft-payload), [Lathe action](foci.md#the-lathe-payload) | menu data slots 1 and 2 | `VisQuote` | Data slots are how vanilla menus sync ints, so a torn read gives a harmless `STALE`. `recipeId` is a second identity check. The spend also rechecks every slot's `expectedBefore`. The refusal goes in data slot 0 and shows on the menu's status line. |
| [the Heart](eldritch.md#the-choice) | menu revision | full `WarpPreview` | None. This is the reference shape. |
| [infusion start](infusion.md#the-commit) | the arm's revision, plus the client's acknowledged **seen** revision | none on the wire: the server re-assesses, then compares the arm's inputs and the live fingerprint | **There is no C2S commit.** The commit must happen inside the second strike's own vanilla use, and binding a payload to that use needs its private packet sequence (two access transformers). The seen revision gives an honest client the same guarantee. |

## Hashing and seeded phases

All in `core.common.model`. All arithmetic is on 64-bit `long`s and wraps; `>>>` is an unsigned shift.

### mix64 and fold

The SplitMix64 finaliser, folded over 64-bit words:

```
mix64(z): z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L
          z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL
          return z ^ (z >>> 31)
fold(words): h = 0; for w in words: h = mix64((h + 0x9E3779B97F4A7C15L) ^ w); return h
idWords(id): the UTF-8 bytes of id.toString() ("ns:path"), cut into 8-byte big-endian words,
             the last zero-padded on the right, followed by one word holding the byte length
```

**Words:**

| Value | Word or words |
|---|---|
| a salt | its 4 ASCII bytes, big-endian, as a non-negative `int` widened to `long` |
| `UUID` | `getMostSignificantBits()`, then `getLeastSignificantBits()` |
| `BlockPos` | `asLong()` (26 bits of x shifted by 38, 26 bits of z shifted by 12, 12 bits of y) |
| `ChunkPos` | `ChunkPos.pack()` |
| `int` | sign-extended |
| boolean | 0 or 1 |
| id | `idWords(id)` |
| world seed | `server.overworld().getSeed()`, or `WorldGenLevel#getSeed()` on worldgen threads |

### Streams

```
stream(h):  s = h;  next() = { s = s + 0x9E3779B97F4A7C15L; return mix64(s) }
at(h, n)  = mix64(h + n · 0x9E3779B97F4A7C15L)      the n-th next() of stream(h), n ≥ 1
bounded(d, lo, hi)   = lo + (d >>> 1) % (hi − lo + 1)
weighted(d, weights) = t = (d >>> 1) % Σweights; the first i with t < weights[0] + … + weights[i]
```

Where an owner seeds a `java.util.Random` with a `fold` instead (research's notes and marginalia, warp's checks), the owner pins its draw order.

### Phases

```
phase(salt, words, interval) = Long.remainderUnsigned(fold([salt] ++ words), interval)
```

- **Due** on the ticks where `gameTime mod interval = phase`, with `interval` ≥ 1. The owner names the clock: overworld game time for per-player work, and the holder's level for blocks and chunks.
- **Recomputed** when a tuning `/reload` changes the interval. Entries already queued keep their due tick, and each reschedules at its next phase tick.
- **Why salted:** neighbouring holders of different kinds never share a schedule by accident, and each salt's vectors stay independent.

### Salts

**Every `fold` in the mod starts with a salt from this table.** Salts are fixed in code, never tuned. A new salt amends this table, and two uses never share a salt unless the table lists them together.

| Salt | Hex | Owner | Use | Words after the salt |
|---|---|---|---|---|
| `NOTE` | `0x4E4F5445` | [research](research.md#generating-a-note) | note seed (`Random`) | world seed, UUID, `attempt`, `idWords(entry)` |
| `MARG` | `0x4D415247` | [research](research.md#prose-that-degrades-with-warp) | marginalia (`Random`) | UUID, `idWords(entry)`, page index, band |
| `WARP` | `0x57415250` | [warp](warp.md#schedule) | per-player phase | UUID |
| `VOIC` | `0x564F4943` | [warp](warp.md#selection-and-draws) | Voice check (`Random`) | UUID, `checks` |
| `HUSH` | `0x48555348` | [Hush](hush.md#scheduling) | chunk phase; **and** the attempt stream (which adds the world seed, so the word counts differ) | `pack`; world seed, `pack`, `steps` |
| `PEAL` | `0x5045414C` | [Hush](hush.md#pealing) | Pealing Bell phase | `asLong` |
| `FLUX` | `0x464C5558` | [Hush](hush.md#sync-and-state) | `FluxRules` generation | `format`, every value in key order, switches as 0 or 1 |
| `CRUC` | `0x43525543` | [crucible](crucible.md#boiling-and-the-step) | block phase, **shared by** the crucible step, the Alembic pour, the essentia line pulse and the infusion bowl's run | `asLong` (a line: its key position) |
| `INFU` | `0x494E4655` | [infusion](infusion.md#upsets) | upset seed; draws are `at(seed, 3k + j + 1)` | the nonce |
| `INFR` | `0x494E4652` | [infusion](infusion.md#sync-and-state) | `InfusionRules` generation (new here) | as `FLUX` |
| `GOLE` | `0x474F4C45` | [golems](golems.md#scheduling) | think phase | UUID |
| `NODE` | `0x4E4F4445` | [wands](wands.md#natural-spread) | natural spread, per cell (stream) | world seed, `rx`, `rz` |
| `STAR` | `0x53544152` | [wands](wands.md#the-starter) | starter ring start | world seed, `k` |
| `CHOR` | `0x43484F52` | [worldgen](worldgen.md#processors) | structure node roll (stream) | world seed, `asLong(target)` |

**Not `fold` salts:** the `random_spread` structure-set salts `1128812370` (`CHOR`, [worldgen, Placement](worldgen.md#placement)) and `1414025811` (`THRS`, [eldritch, The structure](eldritch.md#the-structure)) go into vanilla's own placement algorithm. They follow the same ASCII naming only so that they are easy to read.

### Vectors

These were computed by the owners' reference implementations, and the owners' fixtures replay them. A `core.common.model` unit test replays these rows as well.

| Input | Result | From |
|---|---|---|
| `fold([HUSH, ChunkPos.pack(0, 0)])` | `0xC21D48E4BBC2BCC8`; phase 2 at 10 | [Hush fixtures](hush.md#fixtures) |
| `fold([CRUC, asLong(0, 64, 0)])` | `0xE0F53741245D77A5`; phase 2 at 5, 37 at 40 | [crucible](crucible.md#boiling-and-the-step) |
| `fold([CRUC, asLong(−37, 70, 512)])` | `0x28DE383757949BE2`; phase 4 at 5, 34 at 40 | [crucible](crucible.md#boiling-and-the-step) |
| `phase(WARP, UUID 00112233-4455-6677-8899-aabbccddeeff, i)` | 805, 5 and 5 for `i` = 1200, 400 and 20 | [warp](warp.md#schedule) |
| `NOTE` seed, world seed `0x0123456789ABCDEF`, the UUID above, `vftv:taking_notes`, attempt 0 | `0xF206817A7B0403F2` | [research](research.md#generating-a-note) |

## Rules records

A **Rules record** is the client's copy of one owner's tuning, and of the few server-config values the client needs. Each one is:
- **immutable and fixed-shape,** with `format`, a `Codec` and a bounded `StreamCodec`. It is built from `data/vftv/tuning/<feature>.json` and the owner's config record. An invalid tuning file keeps the previous record ([API, Data maps and other data](api.md#data-maps-and-other-data));
- **sent S2C to every player:**
  1. **on join,** before any screen can open;
  2. **after every `/reload`.** Both of these go through `OnDatapackSyncEvent`;
  3. **after a config publish that changed a value it carries** ([configuration, Publishing a change](config.md#publishing-a-change)), and only then;
- **dropped by the client at disconnect.** Until the first one arrives, fact spans that read it render `[?]` without logging, and client previews price at the typed zero. A server echo then refuses the stale quote as `STALE` ("Checked commits"). Client caches that read it are invalidated when it arrives;
- **given a `generation`** only if a long-running operation freezes it: `fold([salt, format, every value in key order, switches as 0 or 1])`, so equal rules share a generation across restarts.

| Record | Owner | Where it lives ([API](api.md#keys-limits-and-rules)) | Server config it carries | Generation |
|---|---|---|---|---|
| `VisRules` | [wands](wands.md#sync-and-state) | `core` | none | no |
| `EssentiaRules` | [essentia](essentia.md#sync-and-state) | `core` | none | no |
| `InfusionRules` | [infusion](infusion.md#sync-and-state) | `core` | none | `INFR` |
| `FluxRules` | [Hush](hush.md#sync-and-state) | `api`, `@Experimental` | `hush.spread`, `.hollows`, `.unattended` | `FLUX` |
| `WarpRules` | [warp](warp.md#sync-and-state) | `api`, `@Internal` | `warp.voices.enabled`, `warp.tax`, `.amplifier`, `.gear` | no |
| `GolemRules` | [golems](golems.md#sync-and-state) | `core` | `golems.max_per_chunk` | no |
| `FociRules` | [foci](foci.md#sync-and-state) | `core` | none | no |
| `ArmorRules` | [armor](armor.md#sync-and-state) | `api`, `@Internal` | none | no |
| `RoadRules` | [eldritch](eldritch.md#sync-and-state) | `api`, `@Internal` | none | no |

**Not Rules records,** with their own rules:
- research's **`quotes`**: per player, sent to an open book only, when it opens and after every change its owner lists, including a changed `research.mode` ([research, Sync and state](research.md#sync-and-state));
- the **recipe snapshots** (arcane, crucible, infusion): `sendRecipes` on join and `/reload`, and cleared at logout ([crucible, Recipe sync and reload](crucible.md#recipe-sync-and-reload));
- **`DerivedAspects`**: on join and after every `/reload`, with the aspect maps ([aspects](aspects.md));
- **`FocusRules`**: the composition validator in `core.focus.model`, not a record.

## Authored content

### The envelope

- **Where:** production tickets deliver authored inputs under the top-level `content/` directory, one directory per owner: `content/<dir>/*.json`. Lang inputs go in `content/lang/<lang>/<owner>.json`.
- **Licence:** the content is CC-BY-4.0 data, per [`NOTICE.md`](../../NOTICE.md). Generators and tools follow the [art style's pipeline](art-style.md#pipeline).
- **Read by datagen,** which writes registry instances, assets and lang into `src/generated/resources`, so the [architecture's generated-resource rule](architecture.md#datagen) stands. **Validators and datagen are build-phase acceptance criteria, not production deliverables.**
- **One JSON object per file,** with a `"schema"` string `vftv:<family>/<n>`. The version `<n>` goes up only for an incompatible change. Content is build input, never saved data, so datagen reads only the current version.
- **Each owner lists** its files in a code block (path, then purpose), and gives one table: file, schema, generated output. An output of "nothing" names the reader instead (the review, a lint, the unit tests).

### Rejection

**The common list.** Datagen rejects:
- an unknown schema;
- an unknown field, or a missing required field;
- a wrong type;
- a value out of range;
- an entry that fails its type's load validation.

**The "Rejected:" convention.** Each schema's table is followed by one sentence, starting **Rejected:**, that lists only what that schema refuses beyond the common list. A spec never restates the common list.

### Fixtures

- `content/<dir>/fixtures.json`, schema `vftv:<owner>_fixtures/1`. A second owner sharing a directory uses `<owner>_fixtures.json` (as `content/eldritch/chorister_fixtures.json` does).
- One section per vector table in the spec. **Every section is required,** and together the sections transcribe every vector table exactly, one row per vector. The `model` unit tests replay them. That consumer is build work.
- **Rejected,** for every fixtures file: a section missing; an unknown field; a conditional field present when its condition is false, or absent when it is true; a vector of the wrong length; a value outside its type or range; a row of a vector table missing or altered. **The shipped file passes every rule.**

### Bootstrap

- `content/<dir>/bootstrap.json`, schema `vftv:<owner>_bootstrap/1`. It lists the reviewed **physical** sources that make the owner's recipes reachable at their Stage: items, or item tags the review resolves, each with a one-line `why`. Research points never count.
- It generates nothing. It is read by the review, and by the affordability lint where the owner says so, beside `content/research/path-stage<N>.json`.
- **Rejected:** a missing item or aspect; an unknown item or tag; an empty `why` or `sources`.

### Lang

`content/lang/<lang>/<owner>.json`, schema `vftv:lang/1`, `{"strings": {<key>: <text>}}`. The merge, the validator and the duplicate-key rule are the [localisation spec's lang pipeline](accessibility.md#the-lang-pipeline).

### Schema families in use

| Owner | Families |
|---|---|
| [accessibility](accessibility.md) | `lang`, `number_words` |
| [research](research.md#authored-content) | `research_categories`, `research_entries`, `affordability_path`, `overlay`, `number_words` |
| [warp](warp.md#authored-content) | `lexicon`, `voice_events`, `bargains`, `phantoms`, `voice_lines`, `warp_recipes`, `warp_bootstrap`, `warp_fixtures` |
| [wands](wands.md#authored-content) | `wand_parts`, `wand_recipes`, `node_types`, `node_placement`, `node_guarantees`, `node_client`, `node_fixtures` |
| [arcane](arcane.md#authored-content) | `arcane_recipes` |
| [crucible](crucible.md#authored-content) | `crucible_recipes`, `crucible_dose_plan` |
| [essentia](essentia.md#authored-content) | `essentia_recipes`, `essentia_client` |
| [infusion](infusion.md#authored-content) | `infusion_recipes`, `wand_parts`, `multiblocks`, `echo_bootstrap` (renamed `infusion_bootstrap`, under "Divergences resolved") |
| [foci](foci.md#authored-content) | `focus_effects`, `focus_media`, `focus_modifiers`, `foci_recipes`, `damage_types`, `foci_fixtures` |
| [golems](golems.md#authored-content) | `golem_materials`, `golem_recipes`, `golem_bootstrap`, `golem_fixtures` |
| [armor](armor.md#authored-content) | `wearables`, `armor_recipes`, `enchantments`, `armor_tags`, `curios_slots`, `void_armor`, `armor_bootstrap`, `armor_fixtures` |
| [Hush](hush.md#authored-content) | `hush_recipes`, `node_types`, `hush_fixtures` |
| [worldgen](worldgen.md#authored-content) | `template`, `choir_ruin`, `choir_sites`, `node_types`, `worldgen_loot`, `worldgen_fixtures` |
| [eldritch](eldritch.md#authored-content) | `template`, `threshold`, `eldritch_sites`, `vuulas`, `eldritch_recipes`, `wand_parts`, `eldritch_bootstrap`, `eldritch_tags`, `eldritch_fixtures` |
| [choristers](choristers.md#authored-content) | `choristers`, `chorister_fixtures` |
| every owner with prose | `lang` |

A family used by several owners (`wand_parts`, `node_types`, `template`) has one shape, defined by its first owner (wands, wands and worldgen respectively). Later owners add files, never fields.

## Feedback routing

The [localisation spec's R5](accessibility.md#r5-the-status-line) owns the status line: its payload, its layer, its lifetime and its narration. This section only says where each kind of feedback goes:

| Feedback | Goes to |
|---|---|
| a refusal or confirmation about a block, item or entity the player used, `message.vftv.busy`, `.unreadable` and `.cost_changed` included | `StatusLines.send` (R5) |
| a refusal in a menu that has a refusal data slot (the Worktable, the Lathe) | the data slot, shown on the menu's own status line, and cleared by the next activation |
| a command reply | vanilla command feedback, `commands.vftv.*` |
| a readout (crucible, infusion, Baffle, golem card) | its own HUD layer, never the status line |
| chat | never used |

## Save-format evolution

Format bumps, old readers and one-shot world migrations (`vftv_data_version`) are owned by [architecture, State and sync](architecture.md#state-and-sync). A format this build doesn't know is handled by "Raw-record retention" above.

## Message keys

Code-defined keys, declared here, and validated by the [lang pipeline](accessibility.md#the-lang-pipeline) like every other code-defined key:

| Key | Args | `en_us` |
|---|---|---|
| `message.vftv.busy` | 0 | "Not now." |
| `message.vftv.unreadable` | 0 | "I can't read it, so I won't touch it." |
| `message.vftv.cost_changed` | 0 | "The cost has changed." |

## Tests

Each owner runs these templates for each of its holders, and cites this section instead of restating them:
- **raw retention:** an unknown format, a future format and each out-of-bounds field decode as unreadable, round-trip byte-identical through save and reload, log one `WARN`, and refuse every use with `message.vftv.unreadable`. A missing id decodes as inert, and restoring the pack restores it;
- **re-entry:** a listener on the owner's event that calls the guarded operation again is refused `BUSY` with `message.vftv.busy` and changes nothing; a scheduled re-entry is rescheduled and runs later;
- **checked commit:** a stale revision and a stale echo each give `STALE`, "The cost has changed." and a fresh quote, with nothing changed. A retry with the fresh quote commits. A price-input change clears a refusal without bumping the revision;
- **phases:** every row of "Vectors" and of the owner's own phase table, negative coordinates included;
- **Rules:** sent on join and after `/reload`; re-sent after a changed config publish and not after an unchanged one; `[?]` before the first record arrives and after disconnect.

## Divergences resolved

The later phase's work list. Each spec replaces its copy with a citation and makes the change shown.

| Spec | Current behaviour | New rule |
|---|---|---|
| [eldritch](eldritch.md#ringing-the-hollow-bell), Hollow Bell row 0 | road guard held: no message | `message.vftv.busy`. "Already travelled this tick" stays its own silent row (row 1). |
| [eldritch](eldritch.md#the-step-bells), Step Bell row 0 | "road guard held, **or** already travelled or rang this tick": no message | split: guard held → `message.vftv.busy`; same-tick duplicate → silent |
| [eldritch](eldritch.md#the-block-and-the-screen), Heart use row 0 | "road guard held, or a second attempt this tick": no message | split, as for the Step Bell |
| [eldritch](eldritch.md#the-choice), `HeartChoose` | `heart.vftv.busy`, `heart.vftv.cost_changed` | `message.vftv.busy`, `message.vftv.cost_changed`; drop both owner keys |
| [eldritch](eldritch.md#ringing-the-hollow-bell), rows 6 and 1 | `road.vftv.lost` ("I can't find my way.") for an unreadable `vftv:road` | `message.vftv.unreadable`; drop `.lost` |
| [worldgen](worldgen.md#using-it), Reliquary row 0 | guard held: `busy`, no message | `message.vftv.busy` |
| [worldgen](worldgen.md#using-it), Reliquary rows 2 and 3 | an unreadable block record shares `.silent` with an unresolved site; an unreadable claims record uses `reliquary.vftv.unreadable` | unreadable records → `message.vftv.unreadable`; an unresolved site stays `.silent` (inert) |
| [foci](foci.md#checks), cast check 2 and the Beam pulse | re-entrant cast refused silently | `message.vftv.busy`, at most once per player per tick |
| [warp](warp.md#lang-keys) | `message.vftv.warp.busy`, `.unreadable`; reuses research's `cost_changed` | `message.vftv.busy`, `.unreadable`, `.cost_changed` |
| [research](research.md#book-ui-keys) | `message.vftv.research.cost_changed`; `.stale` covers "a stale request, revision or echo" | commit echo mismatch → `message.vftv.cost_changed`; `.stale` only for non-commit note actions (`place`, `remove`, `abandon`) and `requestId` |
| [research](research.md#sync-and-state), `learn`, `buy`, `listen`, `accept_offer` | echo only, no surface revision | add `quotes.revision`, bumped on every re-send, and echoed by every commit action |
| [wands](wands.md#the-spend), [arcane](arcane.md#status-and-refusals), [foci](foci.md#revision-and-status) | `message.vftv.spend.stale` | `message.vftv.cost_changed` |
| [infusion](infusion.md#the-commit) | `message.vftv.infusion.stale`, `.busy` | `message.vftv.cost_changed`, `message.vftv.busy`; the HUD's `.changed` line stays |
| [essentia](essentia.md#labels) | `message.vftv.essentia.busy` ("Not while it's being drawn on."), `.unreadable` | `message.vftv.busy`, `message.vftv.unreadable` |
| [golems](golems.md#the-operation-guard) | `message.vftv.golem.busy`, `.unreadable`; the item called "dormant" | `message.vftv.busy`, `message.vftv.unreadable`; vocabulary "unreadable"; `.inert` stays (missing ids) |
| [crucible](crucible.md#sync-and-state) | `message.vftv.crucible.dormant`, `hud.vftv.crucible.dormant`; "dormant" | `message.vftv.unreadable`, `hud.vftv.crucible.unreadable`; "unreadable" |
| [Hush](hush.md#the-record) | "dormant" aura and bell; `hud.vftv.hush.dormant`, `status.vftv.pealing_bell.dormant`, `commands.vftv.flux.status.dormant`, `.not_dormant`; heading "Pending and dormant" | "unreadable"; `.unreadable` and `.not_unreadable` suffixes; heading "Pending and unreadable" |
| [wands](wands.md#sync-and-state) | "dormant" node and jar; `commands.vftv.node.dormant`; `message.vftv.jar.unreadable` | "unreadable"; `commands.vftv.node.unreadable`; `message.vftv.unreadable` |
| [essentia](essentia.md#sync-and-state), [infusion](infusion.md#sync-and-state) | "dormant" jar, tube, Alembic, bowl and pedestal | "unreadable" |
| [armor](armor.md#state) | an unreadable Baffle record is called "inert" | "unreadable" ("inert" is for missing ids) |
| [foci](foci.md#the-item) | `message.vftv.focus.unreadable` | `message.vftv.unreadable`; the tooltip and fact keys stay |
| [infusion](infusion.md#sync-and-state) | `InfusionRules` generation used but never defined | `fold([INFR, format, values…])`, as `FluxRules` |
| [infusion](infusion.md#the-echo-bootstrap) | `echo_bootstrap.json`, `vftv:echo_bootstrap/1` | `bootstrap.json`, `vftv:infusion_bootstrap/1` |
| [research](research.md#prose-and-fact-references) fact sections | "receives `X` on join" stated per record, with different trigger lists (WarpRules: join; RoadRules: join and `/reload`; FluxRules: join, `/reload` and config) | cite "Rules records" |
| [config](config.md#sync-and-trust), "Sync and trust" | re-lists every config-carrying record with a "Sent" column | keep the record-to-keys table (config's to own); replace "Sent" with a citation |
| [crucible](crucible.md#boiling-and-the-step) | salted with `CRUC` (already fixed since the review); essentia and infusion reuse it as "the crucible's phase" | cite "Phases" and the salt table; the shared `CRUC` stays, so no vectors change |
| [Hush](hush.md#an-attempt), [worldgen](worldgen.md#processors), [infusion](infusion.md#upsets) | cite "the wands spec's stream", `bounded`, `weighted`, and a hand-written counter form | cite "Streams" (`stream`, `at`, `bounded`, `weighted`) |
| every spec citing [research, Generating a note](research.md#generating-a-note) for `mix64` and `fold` | `core.research.model` as the hash's home | cite "mix64 and fold"; research keeps only its `NOTE` seed |
