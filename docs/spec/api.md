# Public API

What an addon or a pack may build on, and how long it may rely on it. This covers:
- the **artefacts**: the jars, their coordinates and how an addon wires them;
- the **packages**, what may live in them, and the **stability tiers**;
- **versioning**, what each release may change, and deprecation;
- what addons may **extend**: datapack registries, data maps, tags, recipes, tuning, and the few code extension points;
- the **events**, their shape and their failure rules;
- the **capabilities** and the two read-only **views**, and why no service is public in v1;
- the **keys, limits and rules** classes, and the full **inventory**;
- the **gates** that hold all of this: binary and source compatibility, the frozen compatibility corpus, the ArchUnit rules and Javadoc.

Terms are defined in [`CONTEXT.md`](../../CONTEXT.md). This spec builds on:
- the [architecture spec](architecture.md) for the layers, the seams and the data rules;
- the [quality gates](quality-gates.md) for every gate it extends;
- [ADR 0001](../adr/0001-single-gradle-project.md) (one Gradle project), [ADR 0002](../adr/0002-explicit-platform-wiring.md) (no service discovery), [ADR 0003](../adr/0003-aspects-as-datapack-registry.md) (the aspect registry), [ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md) (addon hooks are NeoForge events) and [ADR 0005](../adr/0005-licensing.md) (the API is MIT);
- every system spec, whose events, keys, limits and records it gathers into one surface.

Resolved in [Public API spec](https://github.com/andrewbrogoff/voices-from-the-void/issues/75). Inputs: [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md) (§7: Curios publishes its API as an `api` classifier on its own coordinates), [dev-tooling research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/dev-tooling/docs/research/dev-tooling.md), the [japicmp CLI documentation](https://siom79.github.io/japicmp/CliTool.html).

**Source citations:** `MC:` is `minecraft-patched-26.2.0.88-sources.jar`, `NF:` the NeoForge 26.2.0.88 sources and `BUS:` its event bus 8.0.5 sources, with package prefixes omitted. Tool behaviour this spec could not run is marked **[build verifies]**.

## The artefacts

### Coordinates and jars

**One Gradle project** ([ADR 0001](../adr/0001-single-gradle-project.md)) publishes three jars under one version, through `maven-publish`:

| Jar | Coordinates | Contents |
|---|---|---|
| `vftv-X.Y.Z.jar` | `com.andrewbrogoff:vftv:X.Y.Z` | the mod: `main` and `api` classes and every resource ([quality gates, Shipping artifact](quality-gates.md#shipping-artifact)) |
| `vftv-X.Y.Z-api.jar` | `com.andrewbrogoff:vftv:X.Y.Z:api` | `sourceSets.api.output` only, plus `LICENSE-API` |
| `vftv-X.Y.Z-api-sources.jar` | `com.andrewbrogoff:vftv:X.Y.Z:api-sources` | the `api` sources with their Javadoc, plus `LICENSE-API` |

- **Every GitHub Release** (tag `vX.Y.Z`) attaches the three jars, the compatibility corpus jar `vftv-X.Y.Z-compat.jar` (for our own replay gate, not for addons; see "The fixtures") and a `SHA256SUMS` file covering all four.
- **No hosted Maven repository in v1.** Adding one later publishes these same coordinates and changes nothing else here.
- **The `api` jar is not a mod.** It has no `neoforge.mods.toml`. An addon compiles against it and runs against the full mod.
- **Manifest:** every jar records `Vftv-Version` and `Vftv-NeoForge-Version` (the NeoForge version it was built against), which the compatibility gate reads.
- **Options considered:** a separate `vftv-api` artefact with its own version; a Gradle subproject. **Why not:** an addon's runtime check is `neoforge.mods.toml`'s `versionRange` against the **mod** version, so a second version line would need a mapping table nobody keeps current; ADR 0001 rules out the subproject.

### For addon authors

The supported wiring, with an example version:

```sh
gh release download v1.2.0 --repo andrewbrogoff/voices-from-the-void \
  --pattern 'vftv-1.2.0.jar' --pattern 'vftv-1.2.0-api*.jar' --pattern SHA256SUMS --dir libs
(cd libs && sha256sum --check --ignore-missing SHA256SUMS)
```

```groovy
dependencies {
    compileOnly files("libs/vftv-1.2.0-api.jar")   // the API, nothing else
    runtimeOnly files("libs/vftv-1.2.0.jar")       // the full mod, for dev runs [build verifies under MDG]
}
```

```toml
[[dependencies.youraddon]]
modId = "vftv"
type = "required"
versionRange = "[1.2,2)"   # the minor you compiled against, up to the next major
ordering = "AFTER"
side = "BOTH"
```

- **Never jar-in-jar** the `api` jar or the mod. The full mod already contains the API classes, so a nested copy would duplicate them.
- **Compile against the `api` jar only.** Classes outside `com.andrewbrogoff.vftv.api` are internal (see "Stability tiers"), whatever the full jar lets a compiler see.
- **Licence:** the `api` jars are MIT ([`LICENSE-API`](../../LICENSE-API), inside each jar). The full mod is MPL-2.0, with its assets and data under CC-BY-4.0 ([`NOTICE.md`](../../NOTICE.md)).

## Packages and shapes

### Packages

Every API package mirrors a `core` feature ([architecture, Layers](architecture.md#layers)):
- **`…api`** (the root) holds only the two tier annotations and its `package-info`.
- **`…api.<f>`** holds the loader-free types of feature `<f>`, and **`…api.neoforge.<f>`** its NeoForge types (events, capabilities, data-map types, `EssentiaResource`).
- `<f>` is an existing `core.<f>` slice: `aspect`, `research`, `vis`, `arcane`, `crucible`, `essentia`, `infusion`, `focus`, `golem`, `warp`, `armor`, `flux`, `hush`, `worldgen`, `road`.
- Every package carries `@NullMarked` ([quality gates, Static gates](quality-gates.md#static-gates)).
- **Gate:** the ArchUnit rule `API_PACKAGES`.

### Shapes

`api` holds keys, constants, immutable records, interfaces and NeoForge event classes, never an implementation of game logic ([architecture, Public API](architecture.md#public-api)). That is enforced **structurally, plus review**: shapes can't prove that a record or a default method holds no logic, so review keeps compact constructors to copying and range checks and default methods to the trivial.

**`API_SHAPES`** (ArchUnit), over every top-level **and nested** type in `..api..`. Each is one of:
- an interface, whose `default` methods are allowed;
- a record;
- an enum;
- an annotation;
- a **constant holder**: a `final` class with a private constructor whose fields are all `public static final`;
- in `api.neoforge` only, a `final` subclass of `net.neoforged.bus.api.Event` that implements `ICancellableEvent` **if and only if** it is one of the three cancellable events below, and never `IModBusEvent` (NeoForge's game bus rejects mod-bus events, `NF:common/NeoForge.java:17–21`).

**Behaviour that `api` does hold**, on purpose:
- `EssentiaResource` implements NeoForge's `Resource`, whose contract requires `isEmpty` and value equality (`NF:transfer/resource/Resource.java:11–22`);
- the data-map value records and `GolemJob` carry a `CODEC` and range checks, because their constructors and codecs are what addons call;
- payload records copy their inputs, and copy mutable values again on access (see "Events").

## Stability tiers

### The three tiers

| Tier | Marked by | Promise |
|---|---|---|
| **Stable** | nothing: the default | everything under "What an addon may rely on" |
| **Experimental** | `@Experimental` | none: may change or vanish in any release, patches included |
| **Internal** | `@Internal` | none: public only for our own wiring; not for addon use |

- **Everything outside `com.andrewbrogoff.vftv.api`** is internal by location, with no annotation: `core`, `neoforge`, `datagen`.
- **The annotations are ours,** in `…api`, with `RUNTIME` retention and the targets type, method, field, constructor and package. We don't use JetBrains' `ApiStatus`: [`API_IS_PURE`](architecture.md#enforcement) would have to widen, and the `api` jar would gain a dependency. Whether every tool reads them as intended is a gate self-test (see "Compatibility gates").
- **The effective tier** of a declaration is that of its own annotation, else its nearest enclosing type's, else its package's `package-info`, else Stable.

### No tier leaks

**`API_TIER_LEAK`** (ArchUnit): no **Stable** public or protected declaration may mention an Experimental or Internal type anywhere in its signature. That covers field types, return and parameter types, **every generic argument and bound** (`List<SomeInternal>` counts), supertypes and implemented interfaces, record components and `throws` clauses. The fix is to give the declaration the same annotation.

### Construction ownership

Every API type has one owner of its construction:

| Owner | Types | Constructors |
|---|---|---|
| **Addons** (constructible) | `EssentiaResource`, `AspectValues`, `Wearable`, `GolemJob` | public and Stable; their record components are frozen within a major like any Stable record's |
| **vftv** | every other record and every event class | public, explicitly declared and `@Internal`. A record's canonical constructor is written as an annotated compact constructor. |
| nobody | interfaces, enums, constant holders | none public |

**`API_CONSTRUCTION`** (ArchUnit) checks the table. A vftv-owned constructor's `@Internal` withdraws construction only: the record's accessors, `equals` and record patterns stay Stable.

## Versioning

### The version

- `mod_version` is **`MAJOR.MINOR.PATCH`**, one number for the mod and the API.
- **MAJOR** changes when any Stable promise below breaks, and for the **first release on a new Minecraft version** (vanilla and NeoForge types sit in our signatures).
- **The promise starts at the first release tagged ≥ `1.0.0`.** Before it, versions promise compatibility with nothing, and the baseline comparison (`apiCompat`, the baseline ArchUnit rules and the binary replay) is skipped. Every other API check always runs: the jar-content tests, the shape and tier rules, `testAddon` compilation, the compatibility corpus's data and Java compilation, and Javadoc.

### What an addon may rely on

Within one MAJOR:
1. **Binary and source compatibility** of every Stable declaration (see "What each release may change").
2. **Each event's contract:** the class name and payload type; the bus and the side; when it is posted relative to the commit; whether it can be cancelled and what cancelling does; its failure rule; every existing payload accessor.
3. **Every id in a Stable `*Keys` class**: registries, built-in instances, data maps, tags, attributes, the enchantment, entity, structure, dimension and advancement keys, and the component and attachment ids. None is removed or renamed. (Forks keep `vftv` ids too, per [`NOTICE.md`](../../NOTICE.md#branding).)
4. **The public datapack formats** under "Public data formats", **including every embedded type id and its parameter format**, with their lifecycle and field meanings.
5. **The capabilities' semantics** ([essentia, Suction](essentia.md#suction): a mover moves X from A to B only if `B.suction(X) > A.hold(X)`) and the views' answers.

**Not promised:**
- `@Experimental` and `@Internal` declarations, and anything outside `api`;
- **Java behaviour types** behind public JSON selectors (focus effect types, recipe serializers, Voice event types and the like): their ids and parameter formats are Stable data, their classes are internal;
- tuning **values** and balance;
- persisted formats (attachments, block entities, data components, `SavedData`), which each owning spec versions with its own `format` field;
- network payloads, lang keys, asset paths and log text.

### What each release may change

For **Stable** declarations:

| Change | Patch | Minor | Major |
|---|---|---|---|
| Javadoc; a fix inside the documented contract | ✓ | ✓ | ✓ |
| Add a type, constant, key, event, event accessor, enum constant or sealed variant | | ✓ | ✓ |
| Add a method to an interface: as a `default` method only | | ✓ | ✓ |
| Add an optional datapack field, a new type id with its parameters, or widen a range or count | | ✓ | ✓ |
| Deprecate (see "Deprecation") | | ✓ | ✓ |
| Remove, rename or change the signature of anything Stable | | | ✓ |
| Add, remove, rename, reorder or retype a **record component** | | | ✓ |
| Add an abstract method to an interface | | | ✓ |
| Withdraw a declaration to Experimental or Internal | | | ✓ |
| Narrow a datapack format: a required field, a rename, a removed type id, a narrowed range or count | | | ✓ |

- **Record components are frozen within a major,** because record patterns must name every component, even in a switch with a `default` arm ([JLS 25 §14.30.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.30.1)). New information arrives as a **new accessor on the event class**, a new sealed variant, or a new record type.
- **Enums and sealed payloads grow in minors.** Their Javadoc says so, and an addon's `switch` over one needs a `default` arm.
- **Config keys** are an operator surface with their own table of the same shape ([configuration spec, Compatibility](config.md#compatibility)); nothing here checks them.
- **"japicmp passed" is evidence, not proof,** of source compatibility. The corpus, the gate self-tests and review cover what it can't see.

### Deprecation

- **A Stable declaration is removed only in a major,** and only after it shipped as `@Deprecated(since = "X.Y", forRemoval = true)`, with `@deprecated` Javadoc naming the replacement, in **at least one released minor** of the previous major.
- **The one exception:** a Minecraft-port major may remove or change a declaration without the window when a vanilla or NeoForge change forces it. The release notes name each one.
- **Our own code migrates first:** `-Xlint:all -Werror` ([quality gates, Static gates](quality-gates.md#static-gates)) fails any use of a deprecated declaration outside the compatibility corpus, whose every historical-use method carries a narrow suppression from the day it is added (see "The fixtures").
- **The window is a release-checklist review item.** No tool reads release history for it.

## What addons may extend

### Datapack registries

All 14 are Stable formats. Each is fixed when a world opens: changing one needs the world reopened.

| Registry | Owner | Synced | Packs may |
|---|---|---|---|
| `vftv:aspect` | [aspects](aspects.md#registry-format) | yes | add entries; override ours; never delete a required built-in |
| `vftv:wand_rod`, `vftv:wand_cap` | [wands](wands.md#parts) | yes | as above |
| `vftv:node_type` | [wands](wands.md#node-types) | yes | as above |
| `vftv:research`, `vftv:research_category` | [research](research.md#registry-format) | yes | as above |
| `vftv:focus_effect`, `vftv:focus_medium`, `vftv:focus_modifier` | [foci](foci.md#composition) | yes | as above, over the built-in behaviour types only |
| `vftv:golem_material` | [golems](golems.md#materials) | yes | as above |
| `vftv:voice_event` | [warp](warp.md#voice-events) | no | as above, over the built-in event types only |
| `vftv:bargain` | [warp](warp.md#bargains) | no | as above |
| `vftv:choir_site` | [worldgen](worldgen.md#site-kinds) | no | as above |
| `vftv:chorister` | [choristers](choristers.md#the-registry) | yes | **override only**: it holds exactly `vftv:wuuren`, `vftv:suhlaa` and `vftv:nihthaa`; a pack can't add or remove one |

The load validation and the missing-entry behaviour of each registry are its owner's.

### Data maps and other data

| Data | Owner | Synced | Lifecycle |
|---|---|---|---|
| **`vftv:aspects`** on item, block and entity type | [aspects](aspects.md#data-maps) | yes | follows `/reload` |
| **`vftv:aspect_defaults`** on item, keyed by tags | [aspects](aspects.md#data-maps) | yes | follows `/reload` |
| **`vftv:gear_warp`** on item | [warp](warp.md#gear-warp) | yes | follows `/reload` |
| **`vftv:wearable`** on item | [armor](armor.md#wearables) | yes | follows `/reload`. The map holds raw entries; which take effect is armor's internal validated view. |
| every tag in a `*Keys` class | its owner | as vanilla tags are | follows `/reload` |
| recipes of `vftv:strike`, `vftv:wand_assembly`, `vftv:arcane`, `vftv:crucible` and `vftv:infusion` | [aspects](aspects.md#the-commonplace-book), [wands](wands.md#assembly), [arcane](arcane.md#the-recipe-type), [crucible](crucible.md#the-recipe-type), [infusion](infusion.md#the-recipe-type) | as each owner states | follows `/reload` |
| `data/vftv/tuning/*.json` | each owner's "Tuning" | no (each owner syncs its own rules record) | follows `/reload`; an invalid file keeps the previous record |

A data-map file with one bad value is discarded whole by NeoForge, logged, while other packs' files for the same map still load ([armor, Wearables](armor.md#wearables)).

### Public data formats

Each format below is Stable under the table in "What each release may change". The authoritative schema is the owner's section; this list names the parts that are **discriminators or nested parameter formats**, because they are where a change is easiest to miss:

| Format | Discriminators and parameter formats |
|---|---|
| research entries | trigger types (`vftv:research_trigger_type` ids) and their fields; page types (`vftv:page_type` ids) and their fields; rewards ([research, Entries](research.md#entries)) |
| focus effects, media, modifiers | each built-in type id and its `params` ([foci, Composition](foci.md#composition)) |
| Voice events, bargains | each event type id and its fields; each bargain's power kind and fields ([warp, Voice events](warp.md#voice-events), [Bargains](warp.md#bargains)) |
| Choristers | each move `type` (`strike`, `knell`, `breath`, `hear`) and its fields ([choristers, The registry](choristers.md#the-registry)) |
| infusion recipes | both result forms, item and enchantment ([infusion, The recipe type](infusion.md#the-recipe-type)) |
| arcane, crucible, strike and wand-assembly recipes | each body as its owner defines it |
| node types, wand parts, golem materials, choir sites, aspects | each field table |
| the four data maps | each value schema |
| tuning files | every key and its range |

**Review:** every change to a listed schema, and to any aggregate bound a pack can hit (for example `MAX_ARCANE_RECIPES`), is checked against the change table at release, item by item. The compatibility corpus is evidence for this, not the schema itself: a minimum and a maximum example can't prove every conditional rule or aggregate cap.

### Code extension

**In code, addons may:**
- **provide the essentia capabilities** on their own blocks (see "Capabilities and views"), and join `#vftv:essentia_endpoints` so tubes connect;
- **add modifiers** to the attributes `vftv:vis_discount` and `vftv:baffle`;
- **register golem job types** and bind their own core items (see "Golem jobs");
- **subscribe to events** and read the two views.

**Not in v1:** every other code registry stays internal: focus effect, medium and modifier types, `vftv:voice_event_type`, `vftv:research_trigger_type`, `vftv:page_type`, recipe types and serializers, structure processors (including `vftv:roll_node`), `SlotDisplay` types. Their behaviour runs inside internal pipelines ([foci, Casting](foci.md#casting); [warp, Event types](warp.md#event-types)), so an SPI frozen before a second implementation exists would be guesswork. **A pack may still use their built-in ids** in data (see "Public data formats"). A later minor may open one as `@Experimental`.

### Golem jobs

The one code registry addons may extend, because a job type is a declaration with **no behaviour** ([golems, Cores and jobs](golems.md#cores-and-jobs)).

- **`GolemJob`** (record, constructible): `carry` (`GolemCarryKind`: `ITEMS` or `ESSENTIA`), `roles` (an ordered, distinct, non-empty list of `GolemRole`), `defaultRole` (one of `roles`). `ESSENTIA` jobs may use only `TAKE` and `GIVE`, the acts the golems spec defines over essentia ([golems, Roles](golems.md#roles)). The compact constructor rejects anything else.
- **The registry:** `GolemKeys.JOB_REGISTRY`, a `ResourceKey<Registry<GolemJob>>` for the code registry `vftv:golem_job`. Addons register through NeoForge's `DeferredRegister` like any registry. Built-in keys: `GolemKeys.CARRY` and `GolemKeys.DECANT`.
- **The binding:** **`GolemCore`**, a loader-free interface an addon's core `Item` implements: `ResourceKey<GolemJob> golemJob()`. Our own core items implement it too. A core item binds to exactly one job, fixed at registration.
- **Not public:** new kinds of act, the planner, execution and transfer. A missing job or core leaves a golem inert, as the golems spec says.

## Lifecycle and ownership

| Handle | Usable from | Notes |
|---|---|---|
| `ResourceKey` and `TagKey` constants | class load | plain values |
| datapack registry entries (`Holder<Aspect>` and the rest) | the server once a world loads; a client once registries sync; server-only registries never on a client | resolve through `RegistryAccess`; never cache a `Holder` across world loads |
| `DataMapType` constants | after NeoForge's mod-bus `RegisterDataMapTypesEvent`, where `neoforge` registers them | read through NeoForge's data-map API; re-read after `DataMapsUpdatedEvent` |
| capabilities | any time on a loaded level | looked up per NeoForge's capability API; caches as NeoForge's `BlockCapabilityCache` |
| the two views | the logical server thread, for a `ServerPlayer` | live answers; don't keep results across ticks |
| event payloads | inside the listener call | live handles valid only during dispatch (see "Events") |

- **Missing ids:** an entry a pack removed, or an addon's id after the addon is removed, behaves as its owner spec states (inert, dormant or refused). The API adds no rule of its own.
- **Unsupported direct storage mutation:** our attachments and data components are discoverable by id at runtime, and NeoForge's `removeData` accepts their wildcard handles (`NF:registries/NeoForgeRegistries.java:43`; `NF:attachment/IAttachmentHolder.java:87–110`). Reading or writing them that way bypasses our operation guards and journals. **It is unsupported**, carries no compatibility promise, and can corrupt state. Publishing ids only is a **support boundary, not a protection**.

## Registry element types

**Each datapack registry's element type is an interface in `api`.** Its record, codec and validation live in `core`, which registers a codec that up-casts and checks the down-cast. So keys are typed (`ResourceKey<Aspect>`), and no internal behaviour parameters leak.

- **`Aspect`** exposes its registry fields ([aspects, Registry format](aspects.md#registry-format)): `int color()`; `List<ResourceKey<Aspect>> parents()` (0 or 2); `Identifier icon()` (the default resolved); `@Nullable HiddenPrimal hiddenPrimal()`, where `HiddenPrimal(List<ResourceKey<Aspect>> taughtParents, Identifier revealedBy)` names a research entry by id; `OptionalInt discoveryBonus()` (empty means the tuning default).
- **The other 13** (`WandRod`, `WandCap`, `NodeType`, `Research`, `ResearchCategory`, `FocusEffect`, `FocusMedium`, `FocusModifier`, `GolemMaterial`, `VoiceEvent`, `Bargain`, `ChoirSite`, `Chorister`) are **marker interfaces** in v1. They give **id and holder access**, not inspection and not datagen: an addon reads them through their JSON. Accessors may arrive in a minor as `default` methods.
- **Not for implementation:** an addon never implements an element interface. Their Javadoc says so.

## Events

### The shape

All 19 events are Stable. Each is:
- a `final` class in `api.neoforge.<f>`, extending `Event`, with an `@Internal` constructor and one Stable accessor, **`payload()`**, returning a Stable type in `api.<f>`. The one exception is `InfusionEvent`, which also has **`@Experimental @Nullable FluxRules fluxRules()`**, passed to its `@Internal` constructor separately from the payload (see "Payload details");
- **posted on `NeoForge.EVENT_BUS`**, by the owning spec's publisher seam, **on the logical server's thread**. The bus posts synchronously on the caller's thread and doesn't reschedule (`BUS:EventBus.java:322–367`), so the publisher asserts the thread;
- **never posted on a client**, and never by an addon. The constructor is `@Internal`; posting a synthetic event is unsupported.

**Payload shapes:**
- **No Stable record component has an Experimental or Internal type**, because record patterns and `API_RECORDS_FROZEN` freeze every component. Experimental data travels on the event class, behind an `@Experimental` accessor.
- **One record** `<Stem>Payload` when every kind carries the same fields, with a kind enum where the owner has kinds.
- **A `sealed interface <Stem>Payload`** with one nested record per kind otherwise. The variants live in the same file, so `sealed` works in the unnamed module.

### Immutability and access

In every payload:
- **collections are unmodifiable copies**, and aspect maps are `SequencedMap<ResourceKey<Aspect>, Integer>` in ascending id;
- **`ItemStack`s are copied at construction and again on every access**, including stacks nested in collections. So no listener can change what a later listener sees;
- **positions** are `BlockPos.immutable()`, and absent fields are JSpecify `@Nullable`;
- **players:** an actor present at the moment is a `ServerPlayer` (`@Nullable` where the owner says "or none"); an owner or starter who may be offline is a `UUID`;
- **live handles** (`ServerPlayer`, `ServerLevel`, `Entity`) are valid only during dispatch. Listeners must not keep them.

**Listeners and the world.** Nothing in a payload has a setter. Cancelling the three cancellable events is the only way a listener changes **our decision**. Listeners may still change the world, and every owner **revalidates** after its events, as its section states.

### Failure rules

NeoForge's bus **stops at a throwing subscriber and rethrows**; the remaining subscribers of that post don't run (`BUS:EventBus.java:322–367`). Cancelled events skip later listeners unless they ask for cancelled events (`BUS:ICancellableEvent.java:13–30`).

- **Post-events:** our publisher catches the throw and logs it once. The state stands, nothing is rolled back or retried, and **our** later posts still run. The remaining subscribers of the failed post are NeoForge's dispatch, not ours to resume.
- **Pre-events fail closed:**
  - **`FocusAffectEntityEvent`:** a throw is logged; the working ends where it was, with no refund of the vis already paid; the cooldown, notifications and guard release still run ([foci, Failure after the spend](foci.md#failure-after-the-spend)).
  - **`GolemWorkEvent`:** a throw makes the act `REFUSED`: nothing moves and nothing is debited ([golems, An act](golems.md#an-act)).
  - **`HushSpreadEvent`:** a throw is a miss: nothing changes and nothing is debited ([Hush, The operation](hush.md#the-operation)).

Each owner's exact ordering, guards and fields are authoritative. The table below summarises them.

### The events

| Event (`api.neoforge.<f>`) | Owner | Payload (`api.<f>`) | Posted | Cancellable |
|---|---|---|---|---|
| `ThingScannedEvent` (aspect) | [aspects](aspects.md#events) | `ThingScannedPayload`: player, `ScanKey`, `ScanTargetKind`, `ScanOutcome`, `complete`, points per aspect | after the commit | no |
| `AspectDiscoveredEvent` (aspect) | [aspects](aspects.md#events) | `AspectDiscoveredPayload`: player, aspect, bonus awarded | after the commit | no |
| `ResearchLearnedEvent` (research) | [research](research.md#events) | `ResearchLearnedPayload`: player, `ResourceKey<Research>`, `ResearchSource`, the warp applied as a `WarpPreview`, points paid per aspect | after the whole transition | no |
| `StageReachedEvent` (research) | [research](research.md#events) | `StageReachedPayload`: player, Stage and previous Stage (`int` 0–4), `ResearchSource` | after the whole transition | no |
| `VisSpentEvent` (vis) | [wands](wands.md#events) | `VisSpentPayload`: player, level and position, `VisContext`, `VisQuote` | after the whole commit | no |
| `NodeEvent` (vis) | [wands](wands.md#events) | `NodePayload`: `NodeEventKind`, `NodeRef`, `NodeCause` | after the whole commit | no |
| `CrucibleEvent` (crucible) | [crucible](crucible.md#events) | sealed `CruciblePayload`: `Dissolved`, `Crafted`, `Spilled` | after the commit | no |
| `EssentiaEvent` (essentia) | [essentia](essentia.md#events) | sealed `EssentiaPayload`: `Spilled` | after the settled state | no |
| `InfusionEvent` (infusion) | [infusion](infusion.md#events) | sealed `InfusionPayload`: `Started` (with the `InfusionPlan` snapshot), `Upset`, `Spilled`, `Completed`, `Abandoned`; plus the event's `@Experimental fluxRules()` | after the state is written | no |
| `FocusEvent` (focus) | [foci](foci.md#events) | `FocusPayload` | after the commit | no |
| `FocusAffectEntityEvent` (focus) | [foci](foci.md#targets) | `FocusAffectEntityPayload`: caster, target `LivingEntity`, effect, harmful | before each entity is affected | **yes** |
| `GolemWorkEvent` (golem) | [golems](golems.md#an-act) | `GolemWorkPayload`: golem and owner UUIDs, level, mark position and **face**, `GolemRole`, `GolemResource`, planned amount | before any capability call or spawn | **yes** |
| `GolemEvent` (golem) | [golems](golems.md#events) | sealed `GolemPayload`: `Placed`, `PickedUp`, `Moved` (with mark **face**), `Spilled`, `Died` | after the state is written | no |
| `WarpChangedEvent` (warp) | [warp](warp.md#events) | `WarpChangedPayload`: player, `WarpState` before, after and delta, `WarpSource` | after all writes, **while the guard is held** | no |
| `HushSpreadEvent` (hush) | [Hush](hush.md#claims) | `HushSpreadPayload`: `HushSpreadKind`, level and position, `HushChange`, `HushOrigin` | before the mutation | **yes** |
| `FluxEvent` (hush) | [Hush](hush.md#events) | sealed `FluxPayload`: `Added`, `Cleared` | after the deposit or clear settles | no |
| `RoadStepPassedEvent` (road) | [Eldritch tier](eldritch.md#events) | `RoadStepPassedPayload`: player, step 1–3, progress after | after **every** credited player's progress is written, before any keepsake | no |
| `RoadEncounterEvent` (road) | [Eldritch tier](eldritch.md#events) | `RoadEncounterPayload`: step, `EncounterOutcome`, participant UUIDs | after the change it reports | no |
| `EndingChosenEvent` (road) | [Eldritch tier](eldritch.md#events) | `EndingChosenPayload`: player, `Ending`, the warp change's actual `WarpPreview` | after the warp change, the award and the cue | no |

The arcane, armor, worldgen and Choristers specs post no event of their own ([arcane](arcane.md#events), [armor](armor.md#events), [worldgen](worldgen.md#events), [Choristers](choristers.md#events)).

**Payload details** where the owner leaves the representation open:
- **`ScanKey`** (sealed): `Item(ResourceKey<Item>)`, `Block(ResourceKey<Block>)`, `Entity(ResourceKey<EntityType<?>>)`, `Node(long id)`. `toString()` is the owner's canonical text (`item:<id>`, …, `node:<id>`). `ScanTargetKind`: `BLOCK`, `ENTITY`, `ITEM`, `NODE`. `ScanOutcome`: `FIRST`, `PARTIAL`, `COMPLETED`, `REPEAT`.
- **`ResearchSource`:** `SOLVED`, `BOUGHT`, `FOUND`, `FREE`, `LISTENED`, `COMMAND`.
- **`VisContext`:** `Identifier source`, `VisKind kind` (`ORDINARY` or `VOID`), `@Nullable ResourceKey<Research> research`.
- **`VisQuote`:** the maps `base` and `effective`; the ints `m`, `spill`, `cap`, `discount`, `warp`; the flags `discountCapped`, `clamped`, `exceedsCapacity`, `inert`, `affordable`; and `List<ResourceKey<Aspect>> shortAspects` in ascending id ([wands, The quote](wands.md#the-quote)).
- **`NodeRef`:** `long id` and `@Nullable GlobalPos where`, which is absent while the node is carried. `NodeEventKind`: `CREATED`, `CAPTURED`, `PLACED`, `REMOVED`, `CULTIVATED`, `CHANGED`. `NodeCause`: `WORLDGEN`, `STRUCTURE`, `STARTER`, `PLAYER`, `COMMAND`, `RECONCILE`, `API`.
- **`CruciblePayload`:**
  - `Dissolved`: level, position, `@Nullable ServerPlayer thrower`, the item copy, aspects added.
  - `Crafted`: level, position, player, the recipe's `ResourceKey<Recipe<?>>`, the catalyst copy, the result copy, aspects spent.
  - `Spilled`: level, position, `@Nullable ServerPlayer`, aspects, `SpillCause`.
- **`SpillCause`:** `EMPTIED`, `REMOVED`, `DESTROYED`, `UPSET`, `ABANDONED`, `DIED`. Which event posts which is the [crucible's](crucible.md#events) table.
- **`InfusionPayload`:**
  - `Started`: level, position, `long bowlId`, `int seq`, starter UUID, recipe key, and an `InfusionPlan`.
  - **`InfusionPlan`** is an API-owned snapshot of the committed `READY` assessment. It keeps every `READY` field and flag of [infusion, The readout](infusion.md#the-readout), never the internal `InfusionReadout` or a network record:
    - **the recipe and output:** `ResourceKey<Recipe<?>> recipe`, `VisKind kind`, and `ItemStack output`, the frozen output copy for **both** result forms. For an item result that includes `transmute`; for enchanting it is the enchanted centre copy at `L + 1` ([infusion, Enchanting through infusion](infusion.md#enchanting-through-infusion)).
    - **the stability breakdown,** each an `int`: `instability` (`R`), `warpPenalty` (`Wp`), `lonePedestals` (`L`), `loneItems` (`I`), `stabiliserPairs` (`P`), `stabiliserBonus` (`B`), `bonusCap`, `stability` (`S`, 0–100) and `upsetChance` (`p`, per mille).
    - **the roll bounds,** each an `int`: `nominal`, `worst`, `ceiling`, `cleanFloor` and `cleanFullSupply` (both per mille), `upsetMax` and `upsetSpill`, plus `long upsetFluxBound`, which is 0 while flux rules are absent.
    - **the costs:** `List<InfusionCost> costs` in ascending aspect id. `InfusionCost` is a record of `ResourceKey<Aspect> aspect`, `int cost` and `int heldNow` (0–99,999, the display-only figure at the commit).
    - **the flags:** `boolean stabilisersCapped` and `boolean clamped`.
    - Its `output` follows the access-copy rule.
  - `Upset`: level, position, `seq`, and the sealed `UpsetEffect`: `Jolt(BlockPos pedestal, ItemStack item)`, `Spill(ResourceKey<Aspect>, int amount)` or `None`.
  - `Spilled`: level, position, aspects, `SpillCause`. It has **no** rules component.
  - **The flux rules** for a `Spilled` post are on the event, not in the payload: **`InfusionEvent.fluxRules()`**, `@Experimental @Nullable FluxRules`. It is the run's frozen rules for `UPSET`, the current rules for `ABANDONED` and `REMOVED`, and `null` for an `UPSET` from a run frozen before the Hush spec landed and for every other kind ([Hush, Sources](hush.md#sources)). The `@Internal` constructor takes it separately from the payload.
  - `Completed`: level, position, `seq`, starter UUID, recipe key, the output copy.
  - `Abandoned`: level, position, `seq`, `AbandonCause` (`PLAYER` or `REMOVED`), `@Nullable ServerPlayer player`, the drawn map.
- **`FocusPayload`:** `FocusEventKind` (`CAST`, `COMPOSED`, `RANKED`); player; `int rank`; modifier keys. Effect, medium and `VisKind` are each **`@Nullable`, absent for a blank focus** (a blank can be ranked). For `CAST` only: `FocusGesture gesture` (`ENTITY` or `BLOCK`) and `int attempted`; both are absent or 0 otherwise.
- **`GolemResource`** (sealed): `Item(ItemStack copy)` or `Essentia(ResourceKey<Aspect>)`. It is never NeoForge's `ItemResource`.
- **`GolemPayload`:**
  - `Placed`: golem, owner, level, position, `ResourceKey<GolemMaterial>` body, `ResourceKey<Item>` core.
  - `PickedUp`: golem, owner, level, position.
  - `Moved`: golem, owner, level, mark position and face, role, resource, amount moved.
  - `Spilled`: golem, level, position, aspect, amount, `SpillCause.DIED`.
  - `Died`: golem, owner, level, position, copies of the drops.
- **`WarpState`:** the ints `temporary`, `lasting`, `permanent`, `gear` and `total`. As a delta its values are signed. **`WarpSource`:** a `WarpSourceKind` (`ENTRY`, `BARGAIN`, `STILLING`, `DECAY`, `GEAR`, `MILESTONE`, `SILENCING`, `COMMAND`) and an `@Nullable Identifier id`, present for `ENTRY`, `BARGAIN` and `MILESTONE`. **`WarpPreview`:** `dT`, `dL`, `dP` and `clipped` ([warp, The commit](warp.md#the-commit)).
- **`HushSpreadPayload`:**
  - `HushSpreadKind`: `CONVERT`, `NODE`, `RISE`, `RESTORE`.
  - `HushChange` (sealed): `Block(BlockState before, BlockState after)` for `CONVERT` and a block `RESTORE`; `Node(long id, ResourceKey<NodeType> before, ResourceKey<NodeType> after)` for `NODE` and a node `RESTORE`; `Rise(EntityType<?> type)` for `RISE`.
  - `HushOrigin` (sealed): `Seep(ChunkPos chunk, @Nullable UUID spiller)` for the spread, or `Bell(BlockPos bell)` for a peal, with no actor.
- **`FluxPayload`:**
  - `Added`: level, chunk, `@Nullable BlockPos seep`, `generated`, `retained`, `pending`, `overflow`, a `FluxSource` (`VIS`, `ESSENTIA` with its `SpillCause`, or `COMMAND`), and `@Nullable ServerPlayer`.
  - `Cleared`: level, chunk, seep, amount, a `FluxClearSource` (`BELL`, `PEAL` or `COMMAND`), `@Nullable ServerPlayer`, and the flux before and after.
- **Road:** `EncounterOutcome` is `STARTED`, `DEFEATED` or `RESET`; `Ending` is `JOINED` or `SILENCED`.

## Capabilities and views

### Essentia

In `api.neoforge.essentia.EssentiaCapabilities`, both Stable:
- **`HANDLER`**: `BlockCapability<ResourceHandler<EssentiaResource>, @Nullable Direction>`, id **`vftv:essentia`** ([architecture, Essentia and capabilities](architecture.md#essentia-and-capabilities));
- **`SUCTION`**: `BlockCapability<EssentiaSuction, @Nullable Direction>`, id `vftv:essentia_suction` ([essentia, Suction](essentia.md#suction)).

**`EssentiaResource`** (`api.neoforge.essentia`, constructible) is a record `EssentiaResource(@Nullable Holder<Aspect> aspect)` implementing NeoForge's `Resource` (`NF:transfer/resource/Resource.java:11–22`):
- **The empty value** is the constant `EssentiaResource.EMPTY`, whose `aspect()` is `null`. `isEmpty()` is true exactly when `aspect()` is `null`. `new EssentiaResource(null)` equals `EMPTY`, and addons use the constant.
- **A non-empty value** needs a holder bound to a key (`unwrapKey()` present, as every `Holder.Reference` from the `vftv:aspect` registry is). The constructor throws `IllegalArgumentException` for an unbound direct holder.
- **Equality and `hashCode`** compare the aspect's `ResourceKey` only, never holder identity, so two holders for one key from different registry snapshots give equal resources. `EMPTY` equals only an empty value.
- A unit test pins each rule.

**`EssentiaSuction`** (`api.essentia`) is loader-free and **implementable by addons**. An addon block exposes both on the faces it chooses, and its routing follows the suction rule.

### ResearchView and BaffleView

The two read-only queries addons need that events can't give: research events carry no login baseline and no revocation, and the `vftv:baffle` attribute is capacity, not current charge.

| View (`api.<f>`) | Methods | Capability (`api.neoforge.<f>`) |
|---|---|---|
| **`ResearchView`** | `boolean isLearned(ResourceKey<Research>)`, `boolean isLatchedOrLearned(ResourceKey<Research>)`, `int stage()` (0–4) | `ResearchCapabilities.VIEW`, an `EntityCapability<ResearchView, Void>` with id `vftv:research_view` |
| **`BaffleView`** | `boolean readable()`, `float current()` (0–40; 0 when unreadable), `int capacity()` (`baffle_max`, 0–40) | `ArmorCapabilities.BAFFLE_VIEW`, an `EntityCapability<BaffleView, Void>` with id `vftv:baffle_view` |

- **Registered by `neoforge`** for `EntityType.PLAYER` in `RegisterCapabilitiesEvent`, with explicit wiring ([ADR 0002](../adr/0002-explicit-platform-wiring.md)). The provider returns a view for a `ServerPlayer` and **null for any client-side player**.
- **Answers are live,** computed at the call by the same `core` queries the owners define: [research, Events](research.md#events) and [armor, The Baffle](armor.md#the-baffle). Unreadable records answer as those owners state. Call them on the server thread only.
- **Not for implementation.** The views expose no storage, no operation guard and no mutation.

### Attachments and data components

**Ids only.** No `AttachmentType` and no typed `DataComponentType` is public. The `*Keys` classes publish their ids as `Identifier` constants for diagnostics and documentation. Direct use is unsupported (see "Lifecycle and ownership").

## Services

**No service facade is public in v1.** Every plain `core` service the system specs list stays internal: the vis quote and spend, `NodeService`, the previews, the warp commit, flux deposits, the golem planner, the Road services and the rest. That holds except for the two views above.

- **What addons use instead:** data, capabilities, attributes, events, the views, and the cancellable events for protection.
- **Why:** each facade is an interface `core` implements ([foci, Events](foci.md#events)), plus a delivery mechanism [ADR 0002](../adr/0002-explicit-platform-wiring.md) constrains, plus a permanent promise over guard, boundary and notify-failure contracts ([wands, Events](wands.md#events)) that callers would then have to honour.
- **Later:** a service arrives in a minor, `@Experimental` first, by amending this spec. It is delivered explicitly, never through `ServiceLoader`. The two views set the pattern: an interface in `api.<f>`, an implementation in `core`, and a named capability registered by `neoforge`.

## Keys, limits and rules

- **`*Keys` classes** are Stable constant holders, one or more per feature. They hold registry keys, built-in instance keys, tag keys, attribute, enchantment, entity, structure, dimension and advancement keys, data-map ids, and component and attachment ids, as listed in "The inventory".
- **`*Limits` classes and `RoadLayout`** are Stable constant holders of `public static final` primitives: the fixed limits each owner lists under its "Tuning and limits".
  - **javac inlines them** ([architecture, Public API](architecture.md#public-api)): an addon keeps the value it compiled against even after a minor changes ours. Javadoc says so on each class.
  - **Which values may change:** a limit that bounds a public data format (a field range, an entry count or an aggregate cap a pack can hit) may only **widen** within a major. A limit on runtime work (per-tick caps and the like) may change in any minor. Each limit's Javadoc says which kind it is.
  - **`RoadLayout`** holds only constants. Its geometry functions are `core.road.RoadGeometry`.
- **`*Rules` records** are tuning snapshots, and none is Stable:
  - **`FluxRules`** is `@Experimental`, because `InfusionEvent.fluxRules()` exposes it. No Stable record has it as a component.
  - **`WarpRules`, `ArmorRules` and `RoadRules`** sit in `api` for cross-feature wiring and are `@Internal`, as are **`WarpCost`** and **`CommitResult`** (the warp commit's argument and result).
  - Every other rules record (`VisRules`, `EssentiaRules`, `InfusionRules`, `GolemRules`, `FociRules`) lives in `core`.

## The inventory

Every API type, by feature. Tier: `S` Stable, `X` `@Experimental`, `I` `@Internal`. `nf` marks `api.neoforge.<f>`; everything else is `api.<f>`.

| Feature (owner) | Types | Tier |
|---|---|---|
| root | `Experimental`, `Internal` | S |
| aspect ([aspects](aspects.md)) | `Aspect`, `HiddenPrimal`; `AspectKeys` (the registry; the 37 built-ins; the data-map ids; the knowledge attachment id `vftv:knowledge`); `AspectLimits` (5 aspects per entry, depth 3, fan-out 5, 2 tier-1 children, sounding radius 256); `AspectValues`; `ScanKey`, `ScanTargetKind`, `ScanOutcome`, `ThingScannedPayload`, `AspectDiscoveredPayload`; nf `AspectDataMaps` (`ITEM`, `BLOCK`, `ENTITY_TYPE`, `DEFAULTS`), `ThingScannedEvent`, `AspectDiscoveredEvent` | S |
| research ([research](research.md)) | `Research`, `ResearchCategory`; `ResearchKeys` (both registries; the required keys under [Registry format](research.md#registry-format); the `vftv:page` component id; the `vftv:research` attachment id); `ResearchLimits` (anchors 2–5, grid radius 7, synthesis depth 3, 8 actions and 1 commit per tick, 16 notes, 9 bookmarks); `ResearchSource`, `ResearchLearnedPayload`, `StageReachedPayload`, `ResearchView`; nf `ResearchLearnedEvent`, `StageReachedEvent`, `ResearchCapabilities` | S |
| vis ([wands](wands.md)) | `WandRod`, `WandCap`, `NodeType`; `VisKeys` (3 registries, the `vftv:vis_discount` attribute, the component ids `vftv:vis`, `vftv:wand_rod`, `vftv:wand_cap`, `vftv:wand_focus`, `vftv:captured_node`); `WandPartKeys` (the rods `vftv:wooden`, `vftv:chime`, `vftv:echo`, `vftv:void`; the caps `vftv:copper`, `vftv:resonant_brass`, `vftv:echo`, `vftv:void`); `NodeTypeKeys` (`vftv:wellspring`); `VisLimits` (the wands spec's fixed limits); `VisKind`, `VisContext`, `VisQuote`, `NodeRef`, `NodeEventKind`, `NodeCause`, `VisSpentPayload`, `NodePayload`; nf `VisSpentEvent`, `NodeEvent` | S |
| arcane ([arcane](arcane.md)) | `ArcaneKeys` (`#vftv:converts_to_worktable`); `ArcaneLimits` | S |
| crucible ([crucible](crucible.md)) | `CrucibleKeys` (`#vftv:crucible_heat`, `#vftv:crucible_refuses`, the attachment ids `vftv:crucible_dissolve`, `vftv:crucible_refused`); `CrucibleLimits`; `CruciblePayload`; nf `CrucibleEvent` | S |
| essentia ([essentia](essentia.md)) | `EssentiaKeys` (`#vftv:essentia_endpoints`, `#vftv:alembic_bases`, the capability ids, the component ids `vftv:jar_contents`, `vftv:label`); `EssentiaLimits`; `EssentiaSuction`; `SpillCause`; `EssentiaPayload`; nf `EssentiaResource`, `EssentiaCapabilities`, `EssentiaEvent` | S |
| infusion ([infusion](infusion.md)) | `InfusionKeys` (`#vftv:infusion_stabilisers`, `#vftv:infusion_unenchantable`); `InfusionLimits`; `InfusionPayload`, `InfusionPlan`, `InfusionCost`, `UpsetEffect`, `AbandonCause`; nf `InfusionEvent` | S (`InfusionEvent.fluxRules()` X) |
| focus ([foci](foci.md)) | `FocusEffect`, `FocusMedium`, `FocusModifier`; `FocusKeys` (3 registries, the 15 required built-ins, `#vftv:focus_immune`, the component id `vftv:focus`); `FocusLimits`; `FocusEventKind`, `FocusGesture`, `FocusPayload`, `FocusAffectEntityPayload`; nf `FocusEvent`, `FocusAffectEntityEvent` | S |
| golem ([golems](golems.md)) | `GolemMaterial`, `GolemJob`, `GolemCarryKind`, `GolemRole`, `GolemCore`; `GolemKeys` (`vftv:golem_material` and its built-ins, `JOB_REGISTRY`, `CARRY`, `DECANT`, the component ids `vftv:golem_body`, `vftv:golem_core`, `vftv:golem_state`, `vftv:baton_attunement`); `GolemLimits`; `GolemResource`, `GolemWorkPayload`, `GolemPayload`; nf `GolemWorkEvent`, `GolemEvent` | S |
| warp ([warp](warp.md)) | `VoiceEvent`, `Bargain`; `WarpKeys` (the `vftv:voice_event` and `vftv:bargain` registries, the `vftv:gear_warp` id, `#vftv:voice_spawnable`, the `vftv:warp` attachment id); `WarpLimits`; `WarpState`, `WarpSource`, `WarpSourceKind`, `WarpPreview`, `WarpChangedPayload`; nf `WarpDataMaps` (`GEAR_WARP`, `DataMapType<Item, Integer>`), `WarpChangedEvent` | S |
| | `WarpCost`, `CommitResult`, `WarpRules` | I |
| armor ([armor](armor.md)) | `ArmorKeys` (the `vftv:baffle` attribute and attachment id, the `vftv:wearable` id, the tags `#vftv:void_armor`, `#vftv:reveals_aspects`, `#vftv:repairs_void_armor`, `#vftv:baffle_ignores`, the `vftv:baffling` enchantment key); `ArmorLimits`; `Wearable`, `WearableSlot`; `BaffleView`; nf `ArmorDataMaps` (`WEARABLE`), `ArmorCapabilities` | S |
| | `ArmorRules` | I |
| flux ([Hush](hush.md)) | `FluxSource`, `FluxClearSource` | S |
| | `FluxRules` | X |
| hush ([Hush](hush.md)) | `HushKeys` (`#vftv:hushed`, `#vftv:hush_immune`, the `vftv:hushed` node type, the `vftv:hollow` entity type, the `vftv:aura` attachment id, `FORMS`); `HushedForm`; `HushLimits`; `HushSpreadKind`, `HushChange`, `HushOrigin`, `HushSpreadPayload`, `FluxPayload`; nf `HushSpreadEvent`, `FluxEvent` | S |
| worldgen ([worldgen](worldgen.md)) | `ChoirSite`; `WorldgenKeys` (as [worldgen, Contracts](worldgen.md#contracts-on-sibling-specs) lists, with the `vftv:choir_claims` attachment id); `WorldgenLimits` | S |
| road ([Eldritch tier](eldritch.md), [Choristers](choristers.md)) | `Chorister`; `EldritchKeys` (as the Eldritch tier lists, with the three Chorister keys and the `vftv:road` attachment id); `RoadLimits`; `ChoristerLimits`; `RoadLayout`; `RoadStepPassedPayload`, `RoadEncounterPayload`, `EncounterOutcome`, `EndingChosenPayload`, `Ending`; nf `RoadStepPassedEvent`, `RoadEncounterEvent`, `EndingChosenEvent` | S |
| | `RoadRules` | I |

- **`HushKeys.FORMS`** is an unmodifiable `List<HushedForm>`, where `HushedForm(Identifier source, Identifier form)` is a pair of ids. It describes the [Hush spec's](hush.md#the-forms) fixed table and never exposes a form implementation.
- **`Wearable`** has the fields of [armor, Wearables](armor.md#wearables): `WearableSlot slot` (`HEAD`, `CHEST`, `LEGS`, `FEET` or `OFFHAND`), `int armor`, `double toughness`, `double knockbackResistance`, `double visDiscount` and `int baffle`. Its codec checks the structure and ranges the owner lists.

**Internal by location:**
- every service not named above;
- every code registry other than `vftv:golem_job`;
- every `AttachmentType` and typed `DataComponentType`;
- network payloads (`ScanResult`, `HeartQuote` and the rest) and persisted records;
- the five `core` rules records;
- `SpendResult`, `InfusionReadout`, the Chorister entry and move records;
- publisher seams, and every `model` class.

## Known limitations

- **Client resource composition.** An addon's wand part needs its own model case in the shared `assets/vftv/items/wand.json`, and so does an addon focus effect's look in `items/focus.json` and `items/wand.json`. Otherwise it draws the `unknown` fallback ([wands, Components](wands.md#components); [foci, On the wand](foci.md#on-the-wand)). Two addons that override one file conflict, and the last resource pack wins. A classifier jar can't solve that, and v1 offers no merge.
- **No client extension hooks.** v1 has no hook for rendering, the HUD or screens. **Reading on a client** is supported where the data is synced: the synced registries, the data maps and vanilla-facing records. Server-only registries, the views and every event are server-side.
- **A Fabric port** is an aim, not a promise: `api` is loader-free so it could carry over, and `api.neoforge` would be mirrored ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)). It would follow the Minecraft-major rule, and no identical platform types are promised.

## Compatibility gates

### The baseline

**The baseline** is the release with the **highest semantic version below `mod_version` that has the same MAJOR**, excluding drafts and prereleases. It is identified by its tag `vX.Y.Z` and the asset `vftv-X.Y.Z-api.jar`, checked against that release's `SHA256SUMS`. The highest version is used rather than the newest date, so a maintenance line can't become the baseline. Comparing with only one baseline is enough because every intervening release passed the same gate.

- **A CI step** lists the releases, picks the baseline, downloads its `api` and `compat` jars and passes their paths as `-PapiBaseline` and `-PcompatBaseline`. If an eligible release exists and any download or checksum fails, **the step fails**.
- **No eligible release** (before `1.0.0`, or in the first release of a new MAJOR): the baseline checks are skipped, and the log says so. A breaking change therefore needs a MAJOR bump of `mod_version` **in the same PR**, visible in its diff.

### `apiCompat`

japicmp's CLI jar, pinned, run as a `JavaExec` task in `check`:
- **`--old`** is the baseline `api` jar, **`--new`** the freshly built one;
- **`--old-classpath` and `--new-classpath`** are the Minecraft and NeoForge artefacts each was built against, read from its `Vftv-NeoForge-Version`;
- **`--error-on-binary-incompatibility`** and **`--error-on-source-incompatibility`**;
- **excluded:** declarations whose effective tier is Experimental or Internal (`@com.andrewbrogoff.vftv.api.Experimental`, `@com.andrewbrogoff.vftv.api.Internal`), so their changes pass.

Exact option spellings, Java 25 class-file support, how exclusions inherit through enclosing types, record handling, missing-class failures and a second configuration-cache run are **[build verifies]**.

### The baseline ArchUnit rules

They run in `archTest` over the baseline `api` jar and the current classes, only when a baseline exists:
- **`API_NO_DEMOTION`:** every declaration that was Stable in the baseline still exists and is still Stable. japicmp's exclusions would otherwise hide a withdrawal.
- **`API_RECORDS_FROZEN`:** every Stable record of the baseline has the same component names, types and order.

### Gate self-tests

`apiGateSelfTest`, in `check`, runs the same japicmp invocation and the same ArchUnit rules over committed pairs of tiny source trees under `src/testApiGate/<case>/{old,new}/`, and asserts each verdict:

| Case | Expected |
|---|---|
| a Stable method removed | fails |
| a Stable type annotated `@Experimental` | fails (`API_NO_DEMOTION`) |
| a component added to a Stable record | fails (`API_RECORDS_FROZEN`) |
| an abstract method added to an interface | fails |
| a `default` method added to an interface | passes |
| an `@Experimental` member changed or removed | passes |
| a member of a type nested in an `@Internal` type changed | passes |
| a Stable method returning `List<` an `@Internal` type `>` | fails (`API_TIER_LEAK`) |
| a vftv-owned record without an `@Internal` canonical constructor | fails (`API_CONSTRUCTION`) |
| a Stable record with a component of an `@Experimental` type | fails (`API_TIER_LEAK`) |
| an event class with a Stable `payload()` and a separate `@Experimental` accessor of an `@Experimental` type, taken by its `@Internal` constructor (the `InfusionEvent` shape) | passes |
| a corpus method seeded with the suppression, whose Stable callee is later deprecated for removal, with the corpus source unchanged | the corpus still compiles under `-Werror` |
| a corpus method that references an `@Experimental` or `@Internal` declaration | fails (`API_CORPUS_STABLE`) |
| end to end: an `@Experimental` accessor is changed and then removed between old and new, while a Stable-only corpus is compiled against new and its old binary replayed against new | `apiCompat`, the corpus build and the binary replay all pass |

## The fixtures

Two source sets, both compiled against the **built `-api.jar` file** (never `sourceSets.api.output`), plus Minecraft and NeoForge, and never `main`. That proves the jar is complete. Their Java is MPL-2.0 under [`NOTICE.md`](../../NOTICE.md) rule 4 (`src/test*/**`); their `resources/data/**` is CC-BY-4.0 under rule 5. No change to the licence map is needed.

### `testAddon`: the example addon

**Editable**, and kept current: it is the worked example addon authors read.
- **Java:**
  - a listener per event that reads every payload accessor of every variant, `InfusionPlan`'s and `InfusionCost`'s included, and the Experimental `InfusionEvent.fluxRules()`. Experimental examples live here, never in the corpus;
  - **all four addon-owned values constructed** through their Stable constructors: an `EssentiaResource` (and `EssentiaResource.EMPTY`), an `AspectValues`, a `Wearable` and a `GolemJob`;
  - an `EssentiaSuction` block, a `GolemCore` item and uses of both views;
  - a reference to every Stable `*Keys`, `*Limits` and `DataMapType` constant.
  - It compiles in `check` against the `api` jar only, so it covers the current surface.
- **Data:** a small example pack under its own namespace `vftv_addon`.

### `testCompat`: the frozen compatibility corpus

**Frozen within a major.** It is a tiny mod `vftv_compat` (its `neoforge.mods.toml` requires `vftv`), built into `vftv-X.Y.Z-compat.jar`.
- **Java:** historical uses of the API, one method per use.
  - **Only effectively Stable public declarations** are referenced: no `@Experimental` or `@Internal` member or type, and nothing outside `api`. The ArchUnit rule **`API_CORPUS_STABLE`** checks the corpus classes and fails otherwise.
  - **Every method carries `@SuppressWarnings({"deprecation", "removal"})` from the day it is added,** before anything it uses is deprecated, with the one-line reason "historical API use; frozen compatibility corpus" ([quality gates, Static gates](quality-gates.md#static-gates)). So a later deprecation never forces an edit to a frozen file.
  - On construction the mod registers a listener for every event class. For each **sealed variant** and each value of each kind enum present when it was written, the listener reads that variant's Stable accessors.
- **Data,** valid per owner:
  - **registry additions** under `data/vftv_compat/…`, for each registry that accepts additions, one minimal and one maximal entry (every optional field, each at its upper bound);
  - **the three Chorister overrides** at `data/vftv/vftv/chorister/{wuuren,suhlaa,nihthaa}.json`;
  - **data-map files** under each map's namespace, `data/vftv/data_maps/<registry>/<map>.json`;
  - **tuning overrides** under `data/vftv/tuning/`, every key of every file;
  - one recipe per recipe type, and **both infusion result forms**;
  - tag additions;
  - and a **sentinel** in each: a value the replay asserts.
- **The freeze check** (in `build`, only with a baseline): `git diff --diff-filter=DMR v<baseline> -- src/testCompat/` is empty. Within a major the corpus only grows. This is an integrity check of the corpus, not the compatibility check itself.

### The replay

- **Binary replay** (FML unit test, only with a baseline): the **baseline's** `vftv-X.Y.Z-compat.jar` is loaded as a mod beside the current build **[build wires]**. The test posts one fixture instance **per sealed payload variant and per kind-enum value** of every event, so every variant-specific path of every corpus listener runs and links against the current API. A `LinkageError` or any throw fails.
- **Data replay** (a separate GameTest run, `runCompatGameTestServer`, always): the current `vftv_compat` and `vftv_addon` load beside `vftv`, isolated from the ordinary GameTests because the corpus overrides tuning **[build wires]**. Its GameTests assert, at startup and **again after `/reload`** for the reloadable data:
  - every registry entry resolves, and every Chorister override took effect;
  - every data-map sentinel reads back through its `DataMapType`, which catches a silently discarded file;
  - every recipe is in the recipe manager, both infusion forms included;
  - every tuning sentinel is the loaded rules record's value;
  - every tag sentinel holds.
  - Each id is in the discovery guard ([quality gates, GameTest](quality-gates.md#gametest-rungametestserver-dedicated-server)).

## Javadoc

- **`javadoc`** on the `api` source set runs with **`-Xdoclint:all -Werror`** over the API, Minecraft and NeoForge classpath, so a missing comment, a broken reference or bad HTML fails the build. That means every public type, member and package has a comment.
- **A comment's existence proves neither completeness nor correctness,** so review checks the content:
  - **an event:** the bus, the side and thread, when it is posted, cancelling and its effect, the failure rule, payload immutability, and a link to the owner's section;
  - **an enum or sealed payload:** that it grows in minors and needs a `default` arm;
  - **a constant:** its unit and range, and for a limit, which kind it is;
  - **an element interface or view:** "not for implementation";
  - **`EssentiaSuction` and `GolemCore`:** the contract an implementer keeps;
  - **a deprecated declaration:** `@deprecated` naming the replacement.

## Licensing

- **All of `src/api/**`, `api.neoforge` included, is MIT** ([`NOTICE.md`](../../NOTICE.md) rule 3; [ADR 0005](../adr/0005-licensing.md)). The `api` and `api-sources` jars carry `LICENSE-API` at their root.
- **The fixtures** are as under "The fixtures". No licence decision is new here.

## Tests

The gates above, all in `check` or the `build` job ([quality gates, CI](quality-gates.md#ci)):

**Static and unit:**
- the ArchUnit rules `API_PACKAGES`, `API_SHAPES`, `API_TIER_LEAK`, `API_CONSTRUCTION` and `API_CORPUS_STABLE`, always; `API_NO_DEMOTION` and `API_RECORDS_FROZEN` with a baseline;
- **`EssentiaResource`:** `EMPTY` and `new EssentiaResource(null)` are equal and empty; an unbound direct holder is rejected; two holders for one key from different registry snapshots are equal with equal hashes;
- `apiCompat` with a baseline; `apiGateSelfTest` always;
- **payload immutability, one unit test per payload type:** mutating the inputs after construction changes nothing; every collection accessor throws on mutation; two calls to an `ItemStack` accessor return distinct copies, and mutating one changes neither the other nor a later call;
- **`GolemJob`:** the constructor's rejections (empty, duplicate or out-of-kind roles, a default outside the list); the codec round trip;
- the data-map value codecs (`AspectValues`, `Wearable`), under [quality gates, Unit](quality-gates.md#unit-test-suite-junit-6-with-fml)'s codec rules;
- the binary replay, with a baseline.

**Shipping-jar checks:**
- the `api` jar holds only `META-INF/MANIFEST.MF`, directory entries, `LICENSE-API` and `com/andrewbrogoff/vftv/api/**/*.class`, and the `api-sources` jar the same with `.java`;
- the full jar holds every API class;
- both manifests carry the two attributes;
- `testAddon` compiles against the `api` jar.

**GameTest:**
- each view's answers for a loaded player after a save and reload; after a command revocation (`isLearned` false at once); after a Baffle absorb and recharge; for an unreadable record; and a null view for a client-side player in the integrated E2E;
- an addon `GolemJob` registered by `vftv_addon` drives a golem through a `GolemCore` item in the compatibility run;
- the data replay.

**Release checklist:** the deprecation window; the schema review under "Public data formats"; `SHA256SUMS` and all four jars attached.

## Contracts on sibling specs

Each system spec is amended in this change, as listed. Each amendment is a criterion in that system's build ticket.

- **[Architecture](architecture.md):** "Public API", "Build layout", "Essentia and capabilities" and "Enforcement" point here, and gain the ArchUnit rules.
- **[Quality gates](quality-gates.md):** "Shipping artifact" sets the baseline, the gates and the fixtures; the `build` job runs them; definition-of-done item 7 asks for an effective, documented tier and compatibility evidence.
- **Every events section** links to "Events" here for the common contract and keeps its own ordering, guards and fields. The [foci](foci.md#events), [golems](golems.md#events) and [Hush](hush.md#events) sections also state their pre-event failure rule.
- **Every "Whether they are exposed in `api` is the API spec's call"** now reads that services are internal in v1, with the [research](research.md#events) and [armor](armor.md#events) views as the exceptions. Each spec's non-service keys and records stay as listed here.
- **[Foci](foci.md#composition):** behaviour types are internal in v1; addons and packs add entries over the built-in types. `FocusService.kind` stays in `core`.
- **[Golems](golems.md#cores-and-jobs):** addon job types and core bindings are public through `GolemJob`, `GolemCore` and the typed `vftv:golem_job` key.
- **[Warp](warp.md#contracts-on-sibling-specs):** the `vftv:voice_event_type` Java key is internal, while its JSON ids stay Stable; `WarpCost`, `CommitResult` and `WarpRules` are `@Internal`.
- **[Armor](armor.md#contracts-on-sibling-specs), [Hush](hush.md#contracts-on-sibling-specs), [worldgen](worldgen.md#contracts-on-sibling-specs), [Eldritch tier](eldritch.md#contracts-on-sibling-specs):** attachment handles become ids. Armor reads through `BaffleView`. `FluxRules` is `@Experimental`, `ArmorRules` and `RoadRules` `@Internal`.
- **[Eldritch tier](eldritch.md#tuning-and-limits) and [Choristers](choristers.md#contracts-on-sibling-specs):** `RoadLayout` holds constants only, with its functions in `core.road.RoadGeometry`. The Chorister entry and move records live in `core`, behind the `Chorister` marker.
- **[Essentia](essentia.md#suction):** `EssentiaResource` and the `vftv:essentia` capability live in `api.neoforge.essentia`.
- **[Aspects](aspects.md#tuning), [research](research.md#tuning), [wands](wands.md#tuning):** their fixed limits move into `AspectLimits`, `ResearchLimits` and `VisLimits`. The wands spec's model limitation is resolved under "Known limitations".
- **No change** to `CONTEXT.md`, `NOTICE.md` or any ADR.

## Build-phase note

This spec has **no production tickets**: it needs no assets and no `content/`. In the build phase, one ticket, **API infrastructure**, comes before the first system's build ticket. It delivers:
- the two annotations;
- the three jars, their manifests and the publication;
- the release workflow's assets and `SHA256SUMS`;
- `apiCompat`, the baseline step and `apiGateSelfTest`;
- the seven ArchUnit rules;
- the `testAddon` and `testCompat` source sets with their runs and replay wiring;
- the Javadoc flags.

Each system's build ticket then adds its own API types, its `testAddon` coverage and its `testCompat` entries, as "The inventory" lists.
