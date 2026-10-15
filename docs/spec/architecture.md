# Architecture and loader seams

How the code is laid out, where the loader seams sit, what is data and what is code, how state is kept and synced, and how the code rules are enforced. Every system spec builds on this.

Resolved in [Architecture and loader seams](https://github.com/andrewbrogoff/voices-from-the-void/issues/11). Inputs: [NeoForge 26.2 research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/neoforge-26-2/docs/research/neoforge-26-2.md), [dev-tooling research](https://github.com/andrewbrogoff/voices-from-the-void/blob/research/dev-tooling/docs/research/dev-tooling.md). Hard-to-reverse choices are recorded in [`docs/adr/`](../adr/).

**Cite the source, not the docs.** docs.neoforged.net is versioned 26.1 and lags the code. Where they disagree, the NeoForge `26.2.0.88` sources win.

## Identity

| | |
|---|---|
| Mod id (namespace) | `vftv` |
| Root package | `com.andrewbrogoff.vftv` (shortened to `…` below) |
| Platform | NeoForge 26.2 (Minecraft 26.2, JDK 25, ModDevGradle 2.x) |

## Build layout

There is **one Gradle project** ([ADR 0001](../adr/0001-single-gradle-project.md)), with two source sets:

- **`api`**: compiled on its own, so it cannot see `core`. It builds the `api` and `api-sources` classifier jars that addon authors compile against ([API spec, The artefacts](api.md#the-artefacts)).
- **`main`**: holds `core`, `neoforge` and `datagen`, and depends on `api`. The mod jar packages the `api` classes too.

The test source sets `testAddon` (the example addon) and `testCompat` (the frozen compatibility corpus) compile against the built `api` jar file, never against `main` ([API spec, The fixtures](api.md#the-fixtures)).

Generated resources are committed to `src/generated/resources` (see "Datagen").

## Layers

```
…api            loader-free: vanilla + JSpecify only
…api.neoforge   loader-specific addon surface (capabilities, events)
…core           vanilla Minecraft only; all game logic and content
   .<feature>         e.g. core.aspect, core.warp, core.essentia
   .<feature>.model   Minecraft-free Java: algorithms and rules
   .<feature>.client  client-only classes
…neoforge       the platform layer: NeoForge glue only
   .<feature>         thin listeners and adapters
   .<feature>.client  client registration
   .compat.<mod>      soft-dependency integrations (curios, jei, terrablender)
   .mixin             the only place mixins may live (none planned for v1)
…datagen        data providers
```

- **Allowed access:** `neoforge` may use `core` and `api` (including `api.neoforge`). `core` may use `api` but not `api.neoforge`. Nothing may use `neoforge`.
- **`core` is loader-free.** It never touches `net.neoforged..` or `org.spongepowered..`.
- **Organised by feature:** each feature is one `core.<feature>` package, and features have no import cycles between them.
- **`core.flux` is a leaf feature** ([Hush spec, The pinned functions](hush.md#the-pinned-functions)): it imports only `api` and the JDK, never another `core` feature, so `core.vis`, `core.crucible`, `core.infusion` and `core.hush` can all price flux without a cycle.
- **The `model` tier** holds the heavy logic: the essentia network solver, validation of the research puzzle, the Voice event counter, infusion stability, golem job planning. It may not use `net.minecraft..`, so it gets millisecond unit tests without booting FML, and JMH benchmarks. It is thread-agnostic (immutable inputs, no shared mutable state).
- **Client separation:** nothing outside a `..client..` package may use `net.minecraft.client..` or `com.mojang.blaze3d..`. This prevents dedicated-server class-loading crashes.

## Platform wiring

There is no `ServiceLoader` and no service discovery ([ADR 0002](../adr/0002-explicit-platform-wiring.md)). The NeoForge `@Mod` constructor builds every platform implementation and passes them to `core` once, through `Vftv.init(platform)`. A Fabric entrypoint would do the same.

## Seams

A **seam** is a small interface in `core`, with a NeoForge implementation in `neoforge`. Every NeoForge-only call goes through one of the seams below, or sits in a thin `neoforge` listener.

| Concern | Lives in `core` | Seam (implemented by `neoforge`) |
|---|---|---|
| **Registration** | Each feature's content holders (`XxxBlocks`, `XxxItems`, code registries such as focus effects and golem job types) | `Registrar.register(ResourceKey<Registry<T>>, name, Supplier<T>) → Supplier<T>`, plus a call that declares datapack registries and data maps. Backed by `DeferredRegister`, `DataPackRegistryEvent` and `RegisterDataMapTypesEvent`. |
| **Events** | Plain methods on feature services, e.g. `WarpService.onPlayerTick(ServerPlayer)` | None. One listener class per feature in `neoforge.<feature>` subscribes to NeoForge events and calls `core` in one line. There is **no cross-loader event bus**. A listener that holds logic fails review. |
| **Networking** | Payload records, their `StreamCodec`s, and their handlers (client handlers in `.client`) | A sender (`toPlayer`, `toTracking(chunk / entity)`, `toServer`) and a handler registrar, backed by `PayloadRegistrar` and `PacketDistributor` |
| **Persistent state** | The data records and their codecs | `DataSlot<H, T>` with `get`, `set` and `update`, for player, chunk and level holders (see "State and sync") |
| **Essentia transfer** | `core.essentia.model`: amounts, jar contents and their snapshots, suction ranks, reachability and the pulse's selection. It works over loader-free inputs (positions as `long`, aspects as `int` indices in ascending id, endpoint records) and returns a plan of moves. It never sees a `ResourceKey`, `BlockPos`, capability or transaction. | Adapters in `neoforge.essentia` own everything loader-specific: the capabilities, `BlockCapabilityCache`, `ResourceHandler` calls, `SnapshotJournal`s and `Transaction`s. They execute the plan and report what moved, and each jar's transaction-busy state, back to `core` (see "Essentia and capabilities"). |
| **Config** | Immutable records, e.g. `WarpConfig(boolean whispers, …)` | `neoforge` fills them from `ModConfigSpec` and pushes fresh copies when the config reloads. No `ModConfigSpec` type reaches `core`. |
| **Client registration** | Renderers, pipelines, shaders, particles, screens, GUI layers, item-model properties | **No seam.** One `neoforge.<feature>.client` listener per feature registers them directly, one line each. Fabric's client APIs are shaped too differently for a seam to hold. |

## Data and code

**Types in code, instances in data.**

- **Code registries** hold behaviours: focus effects and media, golem job types, recipe types and serializers, Voice event types, structure processors, Commonplace Book page types, `SlotDisplay` types.
- **Datapack JSON** holds the content that uses those behaviours, referring to them by id:
  - recipes
  - aspect values on items, blocks and entities (a synced data map)
  - research entries and Commonplace Book pages
  - Voice events
  - worldgen, structures and loot
- **The aspect set is a synced datapack registry** ([ADR 0003](../adr/0003-aspects-as-datapack-registry.md)). Code refers to the built-in aspects through `ResourceKey` constants in `api`. Vacuos being a hidden primal is a property in its data, not a special case in code.
- **Balance numbers** (costs, rates, chances, thresholds) live in the data next to their content, so a datapack can override them.
- **Server config** holds only admin switches: research presets and skip mode, a toggle per Voice effect, performance caps. Which options exist is left to the configuration spec.

## State and sync

The server is authoritative. The client predicts nothing except cosmetic effects.

| Scope | Examples | Storage | Synced to |
|---|---|---|---|
| Player | warp, research progress, known aspects, Stage, the Joining or Silencing choice | NeoForge `AttachmentType` with `copyOnDeath` | the owning player only |
| Chunk | aura, vis, flux, Hush bookkeeping | NeoForge `AttachmentType` on the chunk | players tracking the chunk |
| Level-wide | e.g. the Hush's spread frontier | vanilla `SavedData` (loader-neutral) | only as each spec needs |
| Block entity | jars, crucibles, infusion altars | `saveAdditional` / `loadAdditional` | the usual block-entity update |

- **Large or growing collections** (research progress, known aspects) sync as deltas through `AttachmentSyncHandler`, never as a full resend.
- **After an in-place change**, call `syncData`, and mark the holder dirty (`markUnsaved()` / `setChanged()`).
- **Recipes:** clients receive no recipes by default in 26.x. `neoforge` calls `OnDatapackSyncEvent#sendRecipes` for every one of our recipe types. The client gets them from `RecipesReceivedEvent` and clears them on logout.
- **Our datapack registries** each carry a network codec, so clients hold them.
- **Untrusted input:** every client-to-server payload is validated on the server (the player has the right menu or item open, is in range, and owns the target). Each payload has a GameTest that sends it bad input.

## Essentia and capabilities

- `EssentiaResource(Holder<Aspect>)` implements the transfer API's `Resource`, so it lives in `api.neoforge.essentia`. It is exposed as the capability **`vftv:essentia`**, a `BlockCapability<ResourceHandler<EssentiaResource>, @Nullable Direction>` ([API spec, Capabilities and views](api.md#capabilities-and-views)).
- **Essentia Jars** implement that handler through an explicit adapter with its own journal. They don't inherit it unmodified: the stock `ResourceStacksResourceHandler` keeps its journal private and checks indices before arguments ([essentia spec, Contents and the handler](essentia.md#contents-and-the-handler)). **The Alembic** stores nothing and exposes no handler.
- **Transactions:** a multi-hop tube transfer runs inside one `Transaction`, so a failed transfer rolls back.
- **Suction** is a separate, small capability. It never overloads the essentia handler.
- **Items and fluids** use the stock `Capabilities.Item` and `Capabilities.Fluid`. The deprecated `IItemHandler` and `IFluidHandler` are never used.
- **Vis on a wand** is a data component, with no capability, unless the wands spec finds that something outside the mod needs to move it.

## Public API

- **`api`** is loader-free. It holds immutable records, interfaces, `ResourceKey`/`TagKey`/registry/data-map keys, and every shared constant (javac inlines constants, so they must live here, where ArchUnit can see them). It never holds implementations of game logic. That is enforced structurally by `API_SHAPES`, plus review, because a record or a default method can still hold logic.
- **`api.neoforge`** holds the capability constants (essentia, suction and the two read-only views), the data-map types, `EssentiaResource` and the NeoForge events that addons subscribe to ([ADR 0004](../adr/0004-addon-hooks-are-neoforge-events.md)).
- **Packages mirror features:** `api.<f>` and `api.neoforge.<f>` for each `core.<f>` slice.
- **Tiers:** a declaration is Stable unless it, an enclosing type or its package carries `@Experimental` or `@Internal`, both our own annotations in `…api`.
- What is exposed, its tiers, versioning, deprecation and the compatibility gates are the [API spec](api.md)'s.

## Soft dependencies

- NeoForge is the only **hard** dependency.
- Every integration (Curios, JEI, TerraBlender) is compiled against its API jar only, lives in `neoforge.compat.<mod>`, and is entered only behind `ModList.get().isLoaded(...)`.
- JEI plugins are discovered by JEI's own `@JeiPlugin` scan, so our code does no reflection.
- Builds tagged beta are accepted; all of 26.2 is early.
- **Progression never needs a soft dependency.** Nodes, Stage finds and all core content work with nothing but NeoForge installed. Anything that relies on a soft dependency (e.g. a biome placed by TerraBlender) is a bonus.

## Datagen

- Every JSON file that has a provider is generated by `…datagen`, run with MDG's `clientData` and `serverData`.
- The output is committed to `src/generated/resources`. CI regenerates it and fails if the result differs.
- Hand-authored content that datagen turns into resources (research entries and book prose, the vanilla aspect table) lives in the top-level `content/` directory, which is not a resource root.
- Hand-written JSON is allowed only where no provider exists: `post_effect`, shaders, TerraBlender regions, and Curios files if `CuriosDataProvider` doesn't fit. It sits in `src/main/resources`, and a test parses and checks it.

## Rendering

- Renderers, pipelines and GLSL live in `core.<feature>.client`. The shaders follow vanilla's std140 uniform-block and named-sampler conventions, so they compile on both the OpenGL and Vulkan backends.
- **Gaps in NeoForge's rendering hooks,** with the workaround for each:
  - **Per-object parameters** (a render type can't carry its own uniforms): encode them in vertex data and `GameTime`. Failing that, write a custom `FeatureRenderer`.
  - **Screen-wide warp effects** (no hook to apply a post chain): run `PostChain.process` in `RenderLevelStageEvent.AfterLevel` with `GraphicsResourceAllocator.UNPOOLED`.
  - **Warp intensity changing per frame** (post-chain values are fixed at load): our own fullscreen pass with a custom pipeline and uniform block.
- **No access transformer in v1.** The one candidate (`GameRenderer.resourcePool`, which would let the warp effect reuse vanilla's pool) is added only if a measured performance budget fails.

## Threading

- **No off-thread game logic in v1.** All game state is changed on the server thread, and payload handlers use `enqueueWork`.
- The `model` tier stays thread-agnostic, so a hot path (essentia solving, Hush spread) can move off-thread later if its budget demands it.

## Enforcement

The `archTest` suite runs ArchUnit in its own JVM, apart from the FML-booted `test` suite (see the dev-tooling research, §8). Its rules:

| Rule | Checks |
|---|---|
| `NO_REFLECTION` | No use of `java.lang.reflect..`, `java.lang.invoke..`, `sun.misc..` or `jdk.internal..`, no reflective `Class` methods (`forName`, `getMethod`, `setAccessible`…), and no `ObfuscationReflectionHelper`. `java.util.ServiceLoader` is banned too. |
| `LAYERS` | `api` ← `core` ← `neoforge`; nothing uses `neoforge`; `core` never uses `api.neoforge` |
| `CORE_IS_LOADER_FREE` | `api` (outside `api.neoforge`) and `core` never use `net.neoforged..` or `org.spongepowered..` |
| `API_IS_PURE` | `api` (outside `api.neoforge`) uses only `java..`, `net.minecraft..`, `com.mojang..` and `org.jspecify..` |
| `MODEL_IS_MINECRAFT_FREE` | `..model..` packages never use `net.minecraft..` or `com.mojang..` |
| `CLIENT_SEPARATION` | only `..client..` packages use `net.minecraft.client..` or `com.mojang.blaze3d..` |
| `FEATURES_ACYCLIC` | `core.(*)..` slices have no import cycles between them |
| `MIXINS_CONFINED` | only `neoforge.mixin..` uses `org.spongepowered..`, and every class there carries `@WhyMixin("<one line>")` (class retention) |
| `COMPAT_CONFINED` | `curios`, `jei` and `terrablender` types are used only in their own `neoforge.compat.<mod>` package |
| `FLUX_LEAF` | `core.flux..` uses no other `core.(*)..` package |
| `FLUX_ENTRY_POINTS` | a package allowlist: only classes in `neoforge.hush..` and `core.hush..` call `FluxService.deposit` (review keeps them to the Hush spec's named entry points). A static caller check: once-only delivery and conservation are proved by the Hush spec's GameTests |
| `API_PACKAGES` | every `..api..` package is the root `…api` or `api.<f>` / `api.neoforge.<f>` for an existing `core.<f>` slice ([API spec, Packages](api.md#packages)) |
| `API_SHAPES` | every top-level and nested `..api..` type is an interface, record, enum, annotation, constant holder or (in `api.neoforge`) a `final` `Event` subclass; cancellable exactly for the three listed events; never `IModBusEvent` ([API spec, Shapes](api.md#shapes)) |
| `API_TIER_LEAK` | no Stable declaration mentions an Experimental or Internal type anywhere in its signature: generic arguments and bounds, supertypes, record components and `throws` included ([API spec, No tier leaks](api.md#no-tier-leaks)) |
| `API_CONSTRUCTION` | public constructors are Stable only on the addon-constructible types; every other API record and event declares an `@Internal` constructor ([API spec, Construction ownership](api.md#construction-ownership)) |
| `API_CORPUS_STABLE` | the frozen compatibility corpus `testCompat` references only effectively Stable API declarations ([API spec, The fixtures](api.md#the-fixtures)) |
| `API_NO_DEMOTION`, `API_RECORDS_FROZEN` | with a release baseline only: every baseline Stable declaration is still Stable, and every baseline Stable record keeps its components ([API spec, Compatibility gates](api.md#compatibility-gates)) |

**Other checks:**
- A Gradle check fails if any active line (not blank, not a comment) in `META-INF/accesstransformer.cfg` lacks a trailing `# why` comment.
- Interface-injection data files are banned. We only consume NeoForge's own injected interfaces.
- If a rule ever has to bend, freeze it (`FreezingArchRule`); never disable it. The freeze store starts empty, CI never creates or refreezes it, and any change to it is reviewed like code.
- The full set of gates, tests and the definition of done is in the [quality gates spec](quality-gates.md).

**The order of preference stands:** a NeoForge event or API, then an AT, then a mixin, which counts interface injection as a mixin. v1 is planned with no mixins and no ATs.
