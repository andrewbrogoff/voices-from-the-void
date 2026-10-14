# NeoForge 26.2 extension points and missing hooks

Research for ticket #6 (map #1). It covers what **NeoForge 26.2.0.88** (Minecraft 26.2, ModDevGradle 2.0.147, JDK 25) offers for each system *Voices From The Void* needs, where hooks are missing, and which APIs are loader-neutral (and so survive a Fabric port).

**Method.** Claims were checked against the 26.2 sources themselves, not against secondary write-ups:
- `neoforge-26.2.0.88-sources.jar` (from maven.neoforged.net).
- The NeoForge-patched vanilla sources `minecraft-patched-26.2.0.88-sources.jar` (MDG output in `mc-agent-harness/harness-mod/build/moddev/artifacts/`).
- Curios `16.0.0+26.2` sources, JEI `30.38.0.230` common-api, neoforge-api and runtime jars, and TerraBlender `26.2-26.2.0.0.2` sources (from their Mavens; same versions as `MC-instances/tc-26.2`).
- Official pages: docs.neoforged.net, neoforged.net blog posts, the NeoForge GitHub `26.2.x` branch, the Fabric docs and the Modrinth API.

No Thaumcraft code was read.

**Caveat on docs.** docs.neoforged.net is versioned **26.1**. There is no 26.2 doc set and no 26.2 blog post. Where the 26.1 docs and the 26.2.0.88 source disagree, the source wins, and the disagreement is flagged below. No official Mojang 26.2 changelog was read: minecraft.net timed out and the web-search budget ran out. The 26.2 community porting primer (`neoforged/.github/primers/26.2`) and the source filled that gap.

**Tags.**
- **[V]** vanilla `net.minecraft.*` / `com.mojang.blaze3d.*`, or datapack/resource-pack JSON. Loader-neutral.
- **[N]** `net.neoforged.*`. NeoForge-only.
- **[3P]** a third-party mod API.
- `neo:` = `net/neoforged/neoforge/…` in the NeoForge sources jar; `mc:` = the patched vanilla sources jar.

---

## TL;DR

1. **Almost everything has a NeoForge hook.** The whole feature set needs **zero mixins**. Only **one AT is worth having** (`GameRenderer.resourcePool`, an optimisation for post effects), plus whatever the Fabric side later needs.
2. **Essentia fits the new transfer API natively.** `Resource` is an open interface, so `EssentiaResource implements Resource` works, combined with your own `BlockCapability<ResourceHandler<EssentiaResource>, Direction>` and the stock `ResourceStacksResourceHandler<R>`. Transactions come free. `IItemHandler`, `IFluidHandler` and `IEnergyStorage` are deprecated for removal.
3. **Built-in attachment sync exists.** `AttachmentType.builder(...).sync(StreamCodec)` covers players, chunks (synced on `ChunkWatchEvent.Sent`), levels, entities and block entities. A custom chunk-sync packet is not needed.
4. **Rendering is extract-then-submit, with a GL or Vulkan backend.** Vulkan ships as an opt-in "Experimental" backend next to the default OpenGL one, and both compile the same GLSL. Custom shaders must follow vanilla's UBO-block and named-sampler conventions so they compile on both.
5. **Gaps are rendering-only:** no custom UBO on a `RenderType`, no mod hook for a screen-wide post effect, and static post-chain uniforms. Each has an AT-free workaround (section 4.9).
6. **Overworld biome injection has no hook in vanilla or NeoForge.** TerraBlender (LGPL-3.0, NeoForge and Fabric, **beta only** for 26.2) is the answer, and can be a soft dependency via its `terrablender:region` datapack registry. Runtime taint-biome spread uses only public vanilla API.
7. **Recipes are not synced to clients by default in 26.x.** The mod must call `OnDatapackSyncEvent#sendRecipes(ourTypes)` so that the client, JEI and our codex can see them. Fabric has `RecipeSynchronization`.
8. **The GameTest model changed.** There is no `@GameTest` annotation any more. Tests are a `TEST_FUNCTION` registry entry plus `test_instance` and `test_environment` datapack JSON, and `mc-agent-harness` already generates the JSON side.
9. **Interface injection is not an alternative to a mixin.** MDG's interface injection is compile-time only. At runtime it still needs a mixin (or ASM) to implement the interface. The project's preference ladder should treat "AT" and "interface injection + mixin" as different rungs.

---

## 1. Registries and data components

**What exists**
- **Static registries.** `DeferredRegister`, `DeferredHolder`, `DeferredItem` and `DeferredBlock` [N] (`neo:registries/`). For a custom root registry, use `new RegistryBuilder<>(key).sync(true).defaultKey(..).create()` registered in `NewRegistryEvent` [N]. Addons then use `DeferredRegister.create(OUR_KEY, "addon")`. `ModifyRegistriesEvent` [N] attaches callbacks to other mods' registries.
- **Datapack registries.** `DataPackRegistryEvent.NewRegistry#dataPackRegistry(key, codec[, networkCodec])` [N]. With a network codec the registry is synced; without one it is server-only.
- **Data maps.** `DataMapType.builder(id, registryKey, codec).synced(...)` plus `RegisterDataMapTypesEvent` [N], with data at `data/<ns>/data_maps/<registry>/<name>.json`. **This is the natural home for "aspects on item/block/entity"** that datapacks and addons extend. `AdvancedDataMapType` adds custom merge and remove.
- **Data components** [V]: `DataComponentType.builder().persistent(Codec).networkSynchronized(StreamCodec).cacheEncoding()`.
  - **New in 26.x:** defaults are bound late through `DataComponentInitializers` (`mc:world/item/Item.java:138`).
  - Use `Item.Properties#delayedComponent` / `delayedHolderComponent` for values that need registries, e.g. an aspect holder.
  - `ModifyDefaultComponentsEvent` [N] edits other mods' defaults.
- **Ids are mandatory.** `Item.Properties#setId` and `BlockBehaviour.Properties#setId` are required, or the build throws "Item id not set" [V]. `DeferredRegister.Items/Blocks#register*` call `setId` for you [N].
- **26.x renames:** `ResourceLocation` → `net.minecraft.resources.Identifier`. Data files use `ItemStackTemplate`. The game is no longer obfuscated, so Parchment is optional.

**Recommendation.** The aspect set lives in a **synced static registry** (code-defined so addons can extend it), *or* a synced datapack registry if aspects must be data-defined. That is a domain decision for the aspects spec. Per-item aspect values go in a **synced data map**.

**Missing hooks.** None on NeoForge. Vanilla has no public "add a root registry" call, so the Fabric side uses Fabric API (`FabricRegistryBuilder`, `DynamicRegistries`; not verified here). Data maps have no vanilla equivalent, so on Fabric they become a reload listener plus a sync payload.

## 2. Data attachments (player, chunk, level, block entity)

**What exists [N]** (`neo:attachment/`)
- `AttachmentType.builder(...)` with:
  - `.serialize(MapCodec)` or `.serialize(IAttachmentSerializer)`
  - `.copyOnDeath()` (needs a serializer)
  - `.sync(StreamCodec)`, `.sync(BiPredicate<holder, ServerPlayer>, codec)` or `.sync(AttachmentSyncHandler)` (the last allows delta sync)
- Registered in `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`.
- **Holders:** `Entity`, `BlockEntity` and `Level` extend `AttachmentHolder` (patched). `ChunkAccess` implements `IAttachmentHolder`, and data carries from ProtoChunk to LevelChunk. ItemStack attachments are gone; use components.
- **Auto-sync** happens on default-create, `setData` and `removeData`. Call `syncData(type)` after mutating a value in place. The initial sync is handled for chunks (on `ChunkWatchEvent.Sent`), entities, players and levels (`neo:attachment/AttachmentSync.java`).
- **Dirty marking:** `setData` marks block entities and chunks dirty. After an in-place mutation call `BlockEntity#setChanged()` or `ChunkAccess#markUnsaved()`.

**Doc discrepancy.** The 26.1 attachments doc says `setUnsaved(true)`. That method does not exist in 26.2; it is `markUnsaved()` (`mc:world/level/chunk/ChunkAccess.java:264`).

**Use for:** player warp, research and knowledge (player, `copyOnDeath`); per-chunk aura, vis and flux (chunk, synced); world-level state such as taint bookkeeping (level).

**Vanilla alternatives [V].** `SavedData` + `SavedDataType` via `ServerLevel#getDataStorage().computeIfAbsent`, and block-entity `saveAdditional`/`loadAdditional` with `ValueOutput`/`ValueInput`. Vanilla has **no chunk-data hook**: on Fabric, chunk data uses Fabric's attachment API, or a level `SavedData` keyed by `ChunkPos`.

## 3. Networking

**What exists**
- **Payload definitions [V]:** `CustomPacketPayload` with `Type<T>(Identifier)`, `StreamCodec`, `ByteBufCodecs` and `RegistryFriendlyByteBuf`. These stay in common code.
- **Registration [N]:** `RegisterPayloadHandlersEvent#registrar(version)` → `PayloadRegistrar.playToClient/playToServer/playBidirectional/configuration*/common*`, plus `.executesOn(HandlerThread)`, `.optional()` and `.versioned()`.
  - A client handler for a `playToClient` payload registered without one goes in `RegisterClientPayloadHandlersEvent`.
  - `IPayloadContext` provides `player()`, `reply()`, `enqueueWork()` and `disconnect()`.
- **Sending [N]:** `PacketDistributor.sendToPlayer`, `sendToPlayersTrackingChunk`, `sendToPlayersTrackingEntity[AndSelf]`, `sendToPlayersNear` and `sendToAllPlayers`. Client to server: `ClientPacketDistributor.sendToServer`.
- **Configuration-phase tasks [N]:** `RegisterConfigurationTasksEvent`.

**Missing hooks.** None. The 26.1 docs match the source. On Fabric the same payload records go through `PayloadTypeRegistry` and `ServerPlayNetworking` (Fabric API).

## 4. Rendering

### 4.0 Backend

Both `com.mojang.blaze3d.opengl.GlBackend` and `com.mojang.blaze3d.vulkan.VulkanBackend` ship [V].
- `PreferredGraphicsApi` is `DEFAULT | OPENGL | VULKAN`, and the default tries GL first. The UI string is "Prefer Vulkan (Experimental)", and there is a `--graphicsBackend` launch argument (`mc:client/PreferredGraphicsApi.java`, `mc:client/main/Main.java:85`).
- Vulkan compiles the **same GLSL** to SPIR-V with shaderc (`mc:com/mojang/blaze3d/vulkan/glsl/GlslCompiler.java`). Bindings are derived from names.

**Rules for our shaders:**
- `#version 330` like vanilla.
- Uniforms only inside `layout(std140) uniform Block {}` blocks, plus named `uniform sampler2D`.
- Names must match the pipeline's `BindGroupLayout` and `VertexFormat`.
- No loose uniforms and no GL-only built-ins.
- Depth is reversed (`GREATER_THAN_OR_EQUAL`, clear to 0.0).
- **Test on both backends** (the harness can pass `--graphicsBackend vulkan`).

### 4.1 Block entity renderers

- **Shape [V]:** `BlockEntityRenderer<T, S extends BlockEntityRenderState>` with `createRenderState`, `extractRenderState(...)` and `submit(state, poseStack, SubmitNodeCollector, CameraRenderState)`.
- **Submit calls [V]:** `submitModel`, `submitItem` (floating items), `submitBlockModel`, `submitText` and `submitCustomGeometry(poseStack, RenderType, (pose, vc) -> …)` (jar liquid, auras, beams).
- **Beam reference:** `BeaconRenderer.submitBeaconBeam(...)` is public static.
- **Registration:** `EntityRenderersEvent.RegisterRenderers#registerBlockEntityRenderer` [N]. Vanilla `BlockEntityRenderers.register` is public static [V], so a Fabric port is easy.
- **Bounds:** `IBlockEntityRendererExtension#getRenderBoundingBox` [N] for tall beams.
- **Phases:** NeoForge adds `submitSpecial(RenderPhaseKey, SubmitNode)` and `RenderPhaseKeys` [N].

### 4.2 Custom shaders and RenderPipelines

- **Building a pipeline [V]:** `RenderPipeline.builder(snippet…)` with `.withLocation`, `.withVertexShader`, `.withFragmentShader`, `.withBindGroupLayout`, `.withVertexBinding`, `.withColorTargetState(BlendFunction)`, `.withDepthStencilState` and `.withCull`.
- **Snippets** in `RenderPipelines`: `ENTITY_SNIPPET`, `ENTITY_EMISSIVE_SNIPPET`, `BEACON_BEAM_SNIPPET`, `END_PORTAL_SNIPPET`, `PARTICLE_SNIPPET`, `GUI_SNIPPET` and `POST_PROCESSING_SNIPPET`.
- **Files:** `assets/<ns>/shaders/**.vsh/.fsh`, with `#moj_import <ns:file.glsl>`. Shaders are resources and can be overridden by resource packs. Pipelines are Java-only.
- **Registration:** `RegisterRenderPipelinesEvent#registerPipeline` [N]. It preloads the pipeline. **There is no `RegisterShadersEvent` in 26.2.**
- **RenderType from a pipeline [V]:** `RenderType.create(name, RenderSetup.builder(pipeline).withTexture("Sampler0", id).useLightmap()…)`.
- **Gap: a `RenderType` cannot carry a custom UBO.** `PreparedRenderType.drawFromBuffer` binds only the default uniforms, `DynamicTransforms` and textures. Fallbacks, in order of preference:
  1. Encode per-object parameters in vertex colour, UV or normal, and animate with `Globals.GameTime` [V].
  2. Write a custom `FeatureRenderer` via `RegisterFeatureRenderersEvent` [N, game bus] that opens its own `RenderPass` and calls `setUniform`.
  3. Draw it yourself in a `RenderLevelStageEvent` subclass.
- **"Void" look:** copy vanilla's end-portal pipeline and shaders (`rendertype_end_portal`, screen-projected UV plus `GameTime`) into our namespace.

### 4.3 Post-effect chains (warp and insanity screen effects)

- **Format [V]:** `assets/<ns>/post_effect/<id>.json` with `targets`, `passes`, `inputs` (`sampler_name: "In"` → `InSampler`) and `uniforms` (each group becomes a std140 block). See vanilla `invert.json` and `creeper.json`.
- **Gap: JSON uniform values are static.** They are uploaded once at load (`mc:client/renderer/PostPass.java:42`). `GameTime` is bound, so time-based animation works, but a per-frame mod value such as warp level has no hook.
- **Gap: no mod hook to apply a chain.** The only NeoForge hook is `RegisterEntitySpectatorShadersEvent`. `GameRenderer#setPostEffect` [V] is public, but it is one shared slot that vanilla overwrites on camera or F5 changes. Fallbacks:
  1. **Recommended:** in `RenderLevelStageEvent.AfterLevel` [N], call `shaderManager.getPostChain(id, LevelTargetBundle.MAIN_TARGETS).process(mainRenderTarget, GraphicsResourceAllocator.UNPOOLED)`. These are all public [V]; `process` is `@Deprecated`. An **AT on `GameRenderer.resourcePool`** (private) lets us reuse the pool instead of `UNPOOLED`.
  2. For dynamic intensity: our own `POST_PROCESSING_SNIPPET` pipeline with a custom UBO, drawn as a fullscreen pass (copying `PostPass.addToFrame` logic) [V API].
  3. `FrameGraphSetupEvent` [N] exposes the frame graph. Pass ordering is unverified.

### 4.4 World-space rendering

- **`RenderLevelStageEvent` [N] is now a set of subclasses, not a stage enum:** `AfterSky`, `AfterOpaqueBlocks`, `AfterOpaqueFeatures`, `AfterTranslucentFeatures`, `AfterTranslucentBlocks`, `AfterTranslucentParticles`, `AfterWeather` and `AfterLevel`.
- **Extract-then-submit style [N]:** `ExtractLevelRenderStateEvent` (`LevelRenderState#setRenderData`), `SubmitCustomGeometryEvent` and `RegisterRenderStateModifiersEvent` (extra data on entity render states).

### 4.5 Particles

- **Providers:** `RegisterParticleProvidersEvent#registerSpriteSet/registerSpecial` [N], plus particle description JSON (`ParticleDescriptionProvider` [N] for datagen).
- **26.x model [V]:** `ParticleRenderType` is a record, `Particle#getGroup()` selects a `ParticleGroup`, and groups extract a `ParticleGroupRenderState` and submit. Custom groups register with `RegisterParticleGroupsEvent` [N].
- **Glowing wisps:** a `SingleQuadParticle` returning a custom `SingleQuadParticle.Layer(translucent, atlas, ourPipeline)` [V public record]. No custom group needed.
- **Essentia streams:** quad-particle chains, or a custom group using `submitCustomGeometry`.

### 4.6 HUD and GUI layers

- **Layers:** `RegisterGuiLayersEvent#registerAbove/Below/AboveAll/BelowAll`, `replaceLayer` and `wrapLayer`, with `VanillaGuiLayers` ids [N]. The layer interface is `GuiLayer.render(GuiGraphicsExtractor, DeltaTracker)`.
- **Renames [V]:** `GuiGraphics` is now **`GuiGraphicsExtractor`**. Screens override `extractRenderState`, `extractBackground` and `extractLabels`; the old `render`, `renderBg` and `renderLabels` are gone.
- **Custom-shader GUI drawing is vanilla:** `fill(RenderPipeline, …)` and `blitSprite(RenderPipeline, …)`.
- **3D in GUI** (aspect orbs, codex previews): `RegisterPictureInPictureRenderersEvent` [N] plus `PictureInPictureRenderer` [V].
- **Goggles overlay:** read `Minecraft.hitResult` inside a `GuiLayer`. `IClientItemExtensions#renderFirstPersonOverlay` [N] handles head-worn goggles.
- **Other screens and keys [N]:** `RegisterMenuScreensEvent` (vanilla `MenuScreens.register` is private) and `RegisterKeyMappingsEvent`.

### 4.7 Item model definitions (`assets/<ns>/items/*.json`)

- **Built-in types [V]:** `model`, `select`, `condition`, `range_dispatch`, `special` and `composite`.
- **A wand whose model depends on its parts needs no code:** `"type":"minecraft:select","property":"minecraft:component","component":"<ns>:wand_cap"`, plus the `has_component` and `component` conditions.
- **Custom properties [N]:** `RegisterConditionalItemModelPropertyEvent`, `RegisterSelectItemModelPropertyEvent` and `RegisterRangeSelectItemModelPropertyEvent`. The vanilla `ID_MAPPER`s are private, so Fabric needs an AW or Fabric API.
- **Dynamic item rendering** (a jar item showing its contents): `RegisterSpecialModelRendererEvent` [N] with a `SpecialModelRenderer` [V]. The BEWLR is gone.
- **Block models and geometry [N]:** `RegisterItemModelsEvent`, `ModelEvent.RegisterLoaders/RegisterStandalone/ModifyBakingResult`, `RegisterBlockStateModels` and the `MutableQuad` helper.

## 5. Worldgen

- **Features, structures, processors, template pools, structure sets, dimension types and level stems** are all vanilla datapack registries [V]. Templates are `.nbt` files under `data/<ns>/structure/`.
- **Code registries [V]** (registered via `DeferredRegister`): `FEATURE`, `STRUCTURE_TYPE`, `STRUCTURE_PIECE`, `STRUCTURE_PROCESSOR` (now `Registry<MapCodec<…>>`), `TRUNK_PLACER_TYPE`, `FOLIAGE_PLACER_TYPE`, `TREE_DECORATOR_TYPE`, `PLACEMENT_MODIFIER_TYPE`, `BIOME_SOURCE`, and others.
- **Adding features or spawns to existing biomes:** `BiomeModifier` JSON in `data/<ns>/neoforge/biome_modifier/` [N].
  - Types: `add_features`, `remove_features`, `add_spawns`, `remove_spawns`, `add_carvers`, `remove_carvers`, `add_spawn_costs` and `remove_spawn_costs`.
  - `StructureModifier` [N] edits structure spawns. Structure placement itself uses vanilla `has_structure/*` biome tags [V].
  - Modifiers run once at server start and are not a runtime mechanism.
  - **Discrepancy:** the 26.1 docs list a biome `neoforge:none`. 26.2.0.88 registers only `none` for *structure* modifiers (`neo:common/NeoForgeMod.java:233-312`). To disable a modifier, use `neoforge:conditions` instead.
  - Fabric equivalent: `BiomeModifications` (fabric-biome-api-v1). It has no structure-modifier API.
- **Overworld biome injection: no hook (verified).**
  - The overworld parameter list is the Java preset `minecraft:overworld` (`OverworldBiomeBuilder`, behind a private `BY_NAME` map in `mc:world/level/biome/MultiNoiseBiomeSourceParameterList.java`). NeoForge has nothing here.
  - A datapack override of `world_preset/normal.json` with an inline biome list would work on both loaders, but it copies the whole vanilla table, conflicts with every other worldgen mod and goes stale. **Rejected.**
  - **TerraBlender** [3P] (LGPL-3.0, NeoForge and Fabric) does this with its own mixins and AT. API: subclass `Region` (`addBiomes`), `Regions.register`, `SurfaceRuleManager.addSurfaceRules`. It also has a **`terrablender:region` datapack registry** (`RegionDefinition`), so climate slots can ship as JSON with no Java dependency, and the files are ignored when TerraBlender is absent. Surface rules still need the Java API.
  - **The 26.2 builds (0.0.0 to 0.0.2) are all tagged beta on Modrinth.** Biolith [3P] is the alternative; it is 3.7.0-beta.1 on 26.2, license not confirmed.
- **Runtime biome change** (taint spread) uses only public vanilla API, with no AT [V]. This is the `/fillbiome` path:
  1. `chunk.fillBiomesFromNoise(resolver, randomState.sampler())`
  2. `chunk.markUnsaved()`
  3. `level.getChunkSource().chunkMap.resendBiomesForChunks(chunks)`

  The resolver is about 10 lines (vanilla's `makeResolver` is private). Biomes are 4×4×4 quarts, and changing them does not convert blocks. A spread-only taint biome needs no climate slot, so only the magical forest needs TerraBlender.
- **Custom dimension and portal [V]:**
  - `data/<ns>/dimension/*.json` and `dimension_type/*.json`. 26.x dimension types carry `attributes`, `skybox` and `timelines`.
  - The portal block implements `net.minecraft.world.level.block.Portal`, returning a `TeleportTransition`. `Entity.teleport(TeleportTransition)`.
  - NeoForge extras [N]: `EntityTravelToDimensionEvent`, `RegisterDimensionTransitionScreenEvent`, and `RegisterCustomEnvironmentEffectRendererEvent` (custom sky, clouds and weather selected via environment attributes).
  - Open: whether a datapack dimension appears in already-existing worlds.

## 6. Datagen

- **Events [N]:** `GatherDataEvent.Client` and `GatherDataEvent.Server`. The patched client data main fires only `Client`, which may hold every provider; the server main fires only `Server`. The NeoForge MDK registers everything on `Client`.
- **MDG 2.0.x:** run types `clientData()` and `serverData()` (the MDG README still says `data`; the `RunModel` source is authoritative).
- **Vanilla providers [V]:** `ModelProvider` (`registerModels(BlockModelGenerators, ItemModelGenerators)`, which emits both models and `items/*.json`), `RecipeProvider` with `.Runner`, `LootTableProvider`, `AdvancementProvider`, `TagsProvider`.
- **NeoForge providers [N]:** `LanguageProvider`, `BlockTagsProvider`, `ItemTagsProvider`, `DataMapProvider`, `DatapackBuiltinEntriesProvider` (via `event.createDatapackRegistryObjects(RegistrySetBuilder)`), `SoundDefinitionsProvider`, `ParticleDescriptionProvider`, `GlobalLootModifierProvider`, `JsonCodecProvider`.
- **Third-party:** Curios ships `CuriosDataProvider` [3P].
- **Portability:** generated JSON is loader-neutral. Only the entry point differs (Fabric uses `DataGeneratorEntrypoint`).

## 7. GameTest

- **26.x is registry- and data-driven [V].** There is **no `@GameTest` annotation**.
  - `Registries.TEST_FUNCTION` (`Consumer<GameTestHelper>`) is a built-in registry.
  - `TEST_INSTANCE` and `TEST_ENVIRONMENT` are datapack registries. The JSON is `"type":"minecraft:function","function":"<id>"` plus `TestData` (`max_ticks`, `setup_ticks`, `required`, `max_attempts`, `required_successes`, `sky_access`, `rotation`…).
  - `/test run|runall|runclosest|runfailed` is always registered. The test server accepts `--tests <glob> --report <junit.xml>`.
- **NeoForge [N]:**
  - `RegisterGameTestsEvent#registerTest/registerEnvironment`. It fires only when GameTest is enabled: dev, the test server, or `-Dneoforge.enableGameTest=true`.
  - `GameTestHelperExtension` adds `getCapability` and `requireCapability`.
  - MDG run `type = "gameTestServer"`. The exit code is the number of failed required tests.
- **mc-agent-harness:** `harness-gametest` writes the `test_instance` and `test_environment` JSON plus an empty structure. **The consumer must still register each `TEST_FUNCTION`**; otherwise the run fails with "missing test function".

## 8. Capabilities and the transfer API (essentia, fluids, vis)

**Capabilities [N]** (`neo:capabilities/`)
- `BlockCapability.createSided(id, Class)`, `EntityCapability` and `ItemCapability.create(id, Class, ItemAccess.class)`.
- Registered in `RegisterCapabilitiesEvent`, looked up with `level.getCapability(cap, pos, side)`. Cache with `BlockCapabilityCache` and invalidate with `level.invalidateCapabilities(pos)`.
- **Stock constants:**
  - `Capabilities.Item.BLOCK`, `ENTITY`, `ENTITY_AUTOMATION` and `ITEM` (`ResourceHandler<ItemResource>`)
  - `Capabilities.Fluid.BLOCK`, `ENTITY` and `ITEM` (`ResourceHandler<FluidResource>`)
  - `Capabilities.Energy.BLOCK`, `ENTITY` and `ITEM` (`EnergyHandler`)

**Transfer API [N]** (`neo:transfer/`, introduced in 21.9; see the [Transfer Rework post](https://neoforged.net/news/21.9-transfer-rework/))
- `ResourceHandler<T extends Resource>`: `size`, `getResource(i)`, `getAmountAsLong`, `getCapacityAsLong`, `isValid`, `insert` and `extract` (with `TransactionContext`; amounts are `int`, getters also come in `long`).
- **`Resource` is an open, one-method interface** (`isEmpty()`). Instances must be immutable and implement `equals`/`hashCode`. `RegisteredResource<T>` is the variant for registry-backed resources.
- **Ready-made bases:** `ResourceStacksResourceHandler<R>(size, emptyResource, Codec<ResourceStack<R>>)` and `StacksResourceHandler<S, T>`. Both are generic and `ValueIOSerializable`.
- **Transactions:** `Transaction.openRoot()` / `open(parent)` in try-with-resources. Custom state uses `SnapshotJournal<T>`.
- **Item context:** `ItemAccess` for item capabilities, with `forPlayerInteraction`, `forStack` and `forHandlerIndex`.
- **Deprecations:** `IItemHandler`, `IFluidHandler` and `IEnergyStorage` are `@Deprecated(since = "1.21.9", forRemoval = true)`. Only one-way adapters exist (`IItemHandler.of(...)`). **Write new code against `ResourceHandler` only.**

**Essentia mapping.** Nothing in the API is sealed, so essentia is first-class:
- `record EssentiaResource(Holder<Aspect> aspect) implements RegisteredResource<Aspect>`, with `EMPTY`.
- `BlockCapability<ResourceHandler<EssentiaResource>, @Nullable Direction> ESSENTIA = BlockCapability.createSided(id("essentia_handler"), ResourceHandler.asClass())`.
- Jars and alembics extend `ResourceStacksResourceHandler<EssentiaResource>`.
- Tube networks move essentia inside a `Transaction`, so a failed multi-hop transfer rolls back.
- Suction and priority are **our** concept on top. Expose them as a separate small interface or capability, not by overloading the handler.
- Vis charge on a wand: either a data component plus a custom `ItemCapability<ResourceHandler<EssentiaResource>, ItemAccess>` (per-aspect), or just components if nothing external needs to move vis. That is a design call for the wands spec.

**Fluids [N].** `FluidType` goes in `NeoForgeRegistries.FLUID_TYPES` (synced). `BaseFlowingFluid` is the base class. Client look comes from `IClientFluidTypeExtensions` via `RegisterClientExtensionsEvent#registerFluidType`. Recipe and ingredient support: `FluidIngredient`, `SizedFluidIngredient`, and `FluidSlotDisplay` for recipe displays.

**Portability.** The transfer API is NeoForge-only, but it is modelled on the **Fabric Transfer API** (`Storage<T extends TransferVariant>` plus `Transaction`). The rework post credits Fabric's design. Keep the essentia *model* (aspect amounts, network solving) in common code, and put one thin adapter per loader between it and the capability or `Storage` lookup.

## 9. Custom recipe types, recipe book and display

- **Recipe types [V]:**
  - `Recipe<T extends RecipeInput>` with `matches`, `assemble`, `placementInfo()`, `recipeBookCategory()` and `display()`.
  - **`RecipeSerializer` is now a record `(MapCodec, @Deprecated StreamCodec)`.**
  - Registration: `RecipeType.simple(id)`, plus `RECIPE_TYPE`, `RECIPE_SERIALIZER`, `RECIPE_BOOK_CATEGORY`, `RECIPE_DISPLAY` and `SLOT_DISPLAY`, all via `DeferredRegister`.
  - **Custom `SlotDisplay` and `RecipeDisplay` types are registrable.** NeoForge registers fluid slot displays the same way, so an aspect or essentia `SlotDisplay` is feasible.
  - Each station gets its own `RecipeInput`: arcane grid plus the player (for the research gate), crucible catalyst plus contents, infusion centre plus pedestals. Vis and essentia costs are codec fields.
- **Sync (important).** Vanilla 26.x sends clients only property sets, stonecutter recipes and *unlocked* recipe-book `RecipeDisplayEntry`s (`mc:network/protocol/game/ClientboundUpdateRecipesPacket.java`). **There is no full recipe list on the client.** The fix:
  - NeoForge [N]: `OnDatapackSyncEvent#sendRecipes(RecipeType<?>...)` on the server, then `RecipesReceivedEvent#getRecipeMap()` on the client. Clear the stored map on logout.
  - Fabric: `RecipeSynchronization.synchronizeRecipeSerializer`.
- **NeoForge extras [N]:** `neoforge:conditions` on recipe JSON (`ICondition`), `ICustomIngredient`, `SizedIngredient`, `RegisterRecipeBookSearchCategoriesEvent`, `RecipePrioritiesProvider`. `RecipeBookType` is an `IExtensibleEnum`, extended through enum-extension config, not runtime reflection.
- **Vanilla recipe book for a custom station:** possible without mixins (`RecipeBookMenu` plus a `RecipeBookComponent` subclass), but it is a lot of UI code for grids that don't fit vanilla's model. Recommended: a codex-driven recipe view fed by `RecipesReceivedEvent`, plus JEI.

## 10. Curios (optional soft dependency)

Curios `16.0.0+26.2` [3P] is LGPL-3.0-or-later. Maven: `https://maven.theillusivec4.top/`. The API artifact is `curios-neoforge:16.0.0+26.2:api` (the `-api.jar` exists).

**API surface** (`top.theillusivec4.curios.api`)
- **Slots are data:**
  - Curios ships presets under `data/curios/curios/slots/`: `ring`, `necklace`, `belt`, `head`, `charm`, `back`, `body`, `bracelet`, `hands`, `feet` and `curio`. Slots exist only once an `entities` file grants them, so we add `data/<ns>/curios/entities/*.json`.
  - Item eligibility comes from item tags (`curios:ring`, …).
  - `CuriosDataProvider` generates both slots and tags.
- **Behaviour:** `ICurioItem`, either implemented on the `Item` or attached via `CuriosApi.registerCurio(item, ICurioItem)`. Hooks: `curioTick`, `onEquip`, `onUnequip`, `canEquip`, `canUnequip`, `getAttributeModifiers`, `canSync` and `writeSyncData`.
- **Capabilities [3P on N]:** `CuriosCapability.INVENTORY` (`EntityCapability<ICuriosItemHandler, Void>`), `ITEM_HANDLER` (`ResourceHandler<ItemResource>`, i.e. the new transfer API) and `ITEM`. `CuriosApi.getCuriosInventory(entity)`.
- **Events:** `CurioChangeEvent`, `CurioCanEquipEvent`, `CurioCanUnequipEvent`, `CurioDropsEvent`, `DropRulesEvent` and `CurioAttributeModifierEvent`.
- **Rendering:** `ICurioRenderer` registered with `CuriosRendererRegistry.register(item, supplier)`. In 16.0 the `MultiBufferSource` overloads were removed; it is **`SubmitNodeCollector` only** (CHANGELOG 16.0.0).

**Soft dependency with no reflection:**
- Declare Curios `compileOnly` on the `:api` artifact, with the full jar as a runtime dependency in dev runs.
- Keep every Curios type reference inside one `compat.curios` package. Enter it only behind `ModList.get().isLoaded("curios")`; JVM lazy class loading keeps it safe.
- Curios JSON and tags are inert without Curios.
- Without Curios, trinkets fall back to the hotbar or armour. That is a design decision for the armor and trinkets spec.

**Fabric.** Curios is NeoForge-only. The Fabric counterparts are Trinkets or Accessories, so this whole layer sits behind a small "worn trinkets" seam.

## 11. JEI

JEI `30.38.0.230` for 26.2 [3P]. All 26.2 builds (up to 30.42) are tagged **beta** on Modrinth.

**API** (`mezz.jei.api`, in **common-api, which is loader-neutral across JEI's NeoForge and Fabric builds**)
- **Entry point:** `@JeiPlugin` on an `IModPlugin` with a no-arg constructor. JEI discovers it (via NeoForge annotation scan data) and instantiates it. **Our code does no reflection**, and the class is never loaded without JEI, so the dependency is soft by construction. Fabric JEI uses a `fabric.mod.json` entrypoint instead (not verified here).
- **Registration callbacks:**
  - `registerCategories` (`IRecipeCategory<T>`)
  - `registerRecipes` (`IRecipeRegistration#addRecipes(IRecipeType, list)`)
  - `registerRecipeCatalysts`
  - `registerGuiHandlers`
  - `registerRecipeTransferHandlers`
  - `registerIngredients`
  - `registerSlotDisplayInterpreters` (since 30.20, so JEI understands custom `SlotDisplay`s)
  - `registerItemSubtypes`, `registerExtraIngredients`, `onRuntimeAvailable`
- **Recipe type ids:** `IRecipeType` / `IRecipeHolderType.create(vanillaRecipeType)`. The old `mezz.jei.api.recipe.RecipeType` class is deprecated for removal.
- **Essentia as a JEI ingredient:** `IModIngredientRegistration#register(IIngredientType<V>, Collection<V>, IIngredientHelper<V>, IIngredientRenderer<V>, Codec<V>)`. Essentia stacks then show in the JEI list and can be bookmarked and searched. An aspects-of-item panel can be a category or an `IRecipeCategory` info view.

**Recipe source.** The JEI NeoForge runtime listens for `RecipesReceivedEvent` and calls `OnDatapackSyncEvent#sendRecipes` itself (the strings are present in `JustEnoughItems.class` and `StartEventObserver.class`). Which types JEI requests was not verified. **We call `sendRecipes` for our own three types regardless**, so JEI and our codex both see them on a JEI-less server too.

## 12. Access transformers, interface injection, mixins

- **AT [N format]:**
  - `META-INF/accesstransformer.cfg` is picked up by default with no `mods.toml` entry. Other paths need `[[accessTransformers]]`.
  - MDG applies the default file automatically (`neoForge { accessTransformers.from … }` for others).
  - Fabric's equivalent is an access widener. **Keep the list tiny, so both files stay mechanically mirrorable.**
- **Interface injection:**
  - MDG `interfaceInjectionData.from "interfaces.json"`. NeoForge's own is `META-INF/injected-interfaces.json`.
  - **It is compile-time only.** The runtime class still needs a mixin (`@Mixin(Target.class) … implements Iface`) or an existing NeoForge patch.
  - In practice it is only useful to *consume* NeoForge's injected extension interfaces (e.g. `IItemExtension`, `IBlockExtension`).
- **Mixin:**
  - FML-native via `[[mixins]] config="<modid>.mixins.json"` in `neoforge.mods.toml`.
  - The 26.2.x branch bundles Fabric's sponge-mixin `0.17.4+mixin.0.8.7` and **MixinExtras 0.5.5**. Use `compatibilityLevel` `JAVA_25` (as Curios does).
  - NeoForge itself now ships a few mixins (`neoforge.mixins.json`).
  - Mixins are the most portable code layer, since Fabric runs the same mixin.

---

## Table A: loader-neutral vs NeoForge-only, by area

| Area | Loader-neutral [V] | NeoForge-only [N] | Fabric counterpart |
|---|---|---|---|
| Registries | `Registry`, `Holder`, `TagKey`, `Registries` keys, `RegistrySetBuilder` | `DeferredRegister`, `RegistryBuilder`/`NewRegistryEvent`, `DataPackRegistryEvent`, data maps | `FabricRegistryBuilder`, `DynamicRegistries`; data maps → own reload listener |
| Data components | `DataComponentType`, codecs, `DataComponentInitializers`, `Item.Properties#delayedComponent` | `ModifyDefaultComponentsEvent`, `DeferredRegister.createDataComponents` | `DefaultItemComponentEvents` |
| Attachments | `SavedData`, BE `saveAdditional`/`loadAdditional` | `AttachmentType` (+ built-in sync) on player, chunk, level, entity, BE | Fabric data attachment API |
| Networking | `CustomPacketPayload`, `StreamCodec` | `PayloadRegistrar`, `PacketDistributor`, config tasks | `PayloadTypeRegistry`, `ServerPlayNetworking` |
| BERs and entity renderers | renderer, render-state and submit API; `BlockEntityRenderers.register` | registration events, `getRenderBoundingBox`, `submitSpecial` | `BlockEntityRendererFactories` |
| Shaders and pipelines | `RenderPipeline`, `BindGroupLayout`, GLSL, `RenderType.create` | `RegisterRenderPipelinesEvent`, `RegisterFeatureRenderersEvent` | mixin or Fabric rendering API |
| Post effects | `post_effect` JSON, `PostChain`, `ShaderManager` | `RenderLevelStageEvent.AfterLevel`, `FrameGraphSetupEvent` | world-render events or mixin |
| Particles | `ParticleType`, `SingleQuadParticle`, `ParticleGroup` | provider and group registration events | `ParticleFactoryRegistry` |
| HUD and GUI | `GuiGraphicsExtractor`, screens, `PictureInPictureRenderer` | `RegisterGuiLayersEvent`, `RegisterMenuScreensEvent`, key mappings, PiP registration | `HudElementRegistry`, `HandledScreens` |
| Item models | `items/*.json`, built-in `select`/`condition`/`range_dispatch`/`special` | custom property and special-renderer registration events | AW on `ID_MAPPER` or Fabric API |
| Worldgen | all worldgen datapack registries and code registries; `fillBiomesFromNoise` + `resendBiomesForChunks`; `Portal` | Biome and structure modifiers, travel events, custom sky renderers | `BiomeModifications`; TerraBlender (both loaders) |
| Datagen | vanilla providers and all output JSON | `GatherDataEvent`, Neo providers (lang, tags, data maps, datapack entries) | `DataGeneratorEntrypoint` |
| GameTest | `TEST_FUNCTION`, `test_instance`/`test_environment` JSON, `/test` | `RegisterGameTestsEvent`, `GameTestHelperExtension`, MDG run | Fabric gametest API |
| Capabilities and transfer | — | `*Capability`, `ResourceHandler`, `Transaction`, `ItemAccess`, `FluidType` | Fabric Transfer API (same design lineage) |
| Recipes | `Recipe`, `RecipeSerializer` record, display types, recipe book | `sendRecipes`/`RecipesReceivedEvent`, conditions, `RecipeBookType` extension | `RecipeSynchronization` |
| Curios | — | Curios [3P] | Trinkets or Accessories [3P] |
| JEI | JEI common-api [3P] | JEI NeoForge plugin discovery | JEI Fabric entrypoint |

## Table B: missing hooks, with fallback in order of preference

Preference ladder: **NeoForge API → AT → interface injection + mixin → plain mixin**.

| Need | Hook in 26.2? | Fallback (first that works) |
|---|---|---|
| Add a biome to the overworld climate list | **None** (vanilla or Neo) | TerraBlender [3P], ideally its `terrablender:region` JSON as a soft dependency. Do not use a datapack preset override or our own mixin |
| Per-object custom uniforms on a `RenderType` | **None** | 1. encode in vertex data and `GameTime` [V]; 2. custom `FeatureRenderer` via `RegisterFeatureRenderersEvent` [N]; 3. own `RenderPass` in a `RenderLevelStageEvent` |
| Apply a screen-wide post chain from mod logic | **None** (spectator only; `setPostEffect` is a shared slot) | `RenderLevelStageEvent.AfterLevel` + `PostChain.process` [public V]; **AT** `GameRenderer.resourcePool` to reuse the pool (optional) |
| Per-frame post-effect uniforms (warp intensity) | **None** (JSON uniforms are static) | Own fullscreen pass with a custom pipeline and UBO [V API] |
| Runtime biome change (taint) | No *event*, but public vanilla API | `fillBiomesFromNoise` + `resendBiomesForChunks`; no AT |
| Inject pieces into vanilla jigsaw pools | None | Not needed (own structures only). If ever needed: mixin or a pool override |
| Chunk-scoped data | Yes [N attachments] | Fabric: Fabric attachment API |
| Custom item-model property ids | Yes [N events] | Fabric: AW on `*ItemModelProperties.ID_MAPPER` |
| Full recipe list on the client | Yes [N `sendRecipes`] | Fabric: `RecipeSynchronization` |

**Net result:** v1 needs **no mixins** and **at most one AT**. Every NeoForge-only call sits in a thin listener that delegates to common code.

## Suggested seams for the Fabric-port rule

- **Common module** (vanilla only): domain logic, payload records and codecs, recipe classes and serializers, render-state, renderer and pipeline definitions, shaders, all JSON, the essentia network model, and the runtime biome-spread code.
- **Per-loader layer:** registration (`DeferredRegister` vs `Registry.register`), event listeners, capability exposure (`ResourceHandler` vs `Storage`), attachments, the payload registrar, GUI-layer, pipeline and particle registration, biome modifiers vs `BiomeModifications`, Curios vs Trinkets, and the datagen entry point.
- **Third-party:** JEI common-api code is shareable. TerraBlender exists on both loaders. Curios does not.

## Open questions (for the map)

1. **TerraBlender and JEI are beta-only on 26.2.** Do we accept beta soft dependencies, or gate the magical-forest biome until TerraBlender goes stable?
2. **Aspect registry shape:** a static synced registry (addon-friendly, code-defined) or a datapack registry (data-defined, reloadable)? This decision belongs to the aspects spec and the API ticket.
3. **Vulkan:** is "renders correctly on the experimental Vulkan backend" part of the quality bar? It costs a second harness run per visual test.
4. **Does a datapack-added dimension appear in existing worlds?** Not verified; it matters for the Outer-Lands equivalent.
5. **When to call `Regions.register` in TerraBlender, and the exact JSON path for `terrablender:region`.** Not verified.
6. **The docs lag the code.** 26.1 docs vs 26.2 source mismatches found: biome `neoforge:none`, `setUnsaved` vs `markUnsaved`, MDG `data` vs `clientData`/`serverData`. Spec writers should cite the source, not the docs.

## Sources

| id | Source |
|---|---|
| NEO-SRC | `net.neoforged:neoforge:26.2.0.88:sources`, https://maven.neoforged.net/releases/net/neoforged/neoforge/26.2.0.88/ |
| MC-SRC | `minecraft-patched-26.2.0.88-sources.jar` (MDG artifact, `mc-agent-harness/harness-mod/build/moddev/artifacts/`) |
| NEO-GH | https://github.com/neoforged/NeoForge/tree/26.2.x (`gradle.properties`: mixin and MixinExtras versions) |
| NEO-DOCS | https://docs.neoforged.net/ (version 26.1): attachments, networking/payload, datacomponents, accesstransformers, gametest, biomemodifier, recipes, particles |
| NEO-BLOG | https://neoforged.net/news/26.1release/, https://neoforged.net/news/21.9-transfer-rework/ |
| PRIMER | https://raw.githubusercontent.com/neoforged/.github/main/primers/26.2/index.md |
| MDG | https://github.com/neoforged/ModDevGradle (`RunModel.java`, README) |
| CURIOS | `top.theillusivec4.curios:curios-neoforge:16.0.0+26.2:sources` (README, CHANGELOG, `api/`), https://maven.theillusivec4.top/ |
| JEI | `mezz.jei:jei-26.2-common-api` / `-neoforge-api` / `-neoforge` `30.38.0.230`, https://maven.blamejared.com/ ; https://api.modrinth.com/v2/project/jei/version?game_versions=[%2226.2%22]&loaders=[%22neoforge%22] |
| TB | `com.github.glitchfiend:TerraBlender-neoforge:26.2-26.2.0.0.2:sources`; https://github.com/Glitchfiend/TerraBlender ; https://api.modrinth.com/v2/project/terrablender/version?game_versions=[%2226.2%22] |
| BIOLITH | https://github.com/TerraformersMC/Biolith ; https://api.modrinth.com/v2/project/biolith/version?game_versions=[%2226.2%22] |
| FABRIC | https://github.com/FabricMC/fabric-api/tree/HEAD/fabric-biome-api-v1 ; https://docs.fabricmc.net/develop/data-generation/features ; https://docs.fabricmc.net/develop/recipes/custom-recipe-types |
| HARNESS | `mc-agent-harness` README, `harness-gametest/.../HarnessGameTests.java`, `gradle-plugin/.../McAgentHarnessPlugin.java` |
