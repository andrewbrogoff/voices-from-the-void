package com.andrewbrogoff.vftv.neoforge.aspect;

import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDataMaps;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDiscoveredEvent;
import com.andrewbrogoff.vftv.api.neoforge.aspect.ThingScannedEvent;
import com.andrewbrogoff.vftv.core.aspect.AspectEntry;
import com.andrewbrogoff.vftv.core.aspect.AspectEvents;
import com.andrewbrogoff.vftv.core.aspect.AspectLookup;
import com.andrewbrogoff.vftv.core.aspect.DerivationExport;
import com.andrewbrogoff.vftv.core.aspect.DerivedAspects;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.ScanningService;
import com.andrewbrogoff.vftv.core.aspect.Strikes;
import com.andrewbrogoff.vftv.core.aspect.model.ScanningTuning;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The aspect feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class AspectNeoForge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", ScanningService.TUNING_PATH);

    /** The data load in progress, whose recipes the derivation reads. */
    private static volatile @Nullable ReloadableServerResources loading;

    /** That load's registries, once its item data maps are in. */
    private static volatile @Nullable RegistryAccess mapsLoaded;

    private AspectNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        registrar.dataMap(AspectDataMaps.ITEM);
        registrar.dataMap(AspectDataMaps.BLOCK);
        registrar.dataMap(AspectDataMaps.ENTITY_TYPE);
        registrar.dataMap(AspectDataMaps.DEFAULTS);
        AspectLookup.install(new DataMapLookup());
        AspectEvents.install(
                p -> post(new ThingScannedEvent(p)), p -> post(new AspectDiscoveredEvent(p)));
        // The fork takes priority over the target's own use, on both sides; the server strikes.
        gameBus.addListener(
                PlayerInteractEvent.RightClickBlock.class,
                event -> {
                    // A spectator neither strikes nor loses vanilla's spectating of containers.
                    if (Strikes.isFork(event.getItemStack()) && !event.getEntity().isSpectator()) {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        if (event.getEntity() instanceof ServerPlayer player) {
                            try (var _ = TickBudget.enter("aspect")) {
                                Strikes.strikeBlock(
                                        player,
                                        event.getItemStack(),
                                        event.getPos(),
                                        event.getHitVec().getLocation());
                            }
                        }
                    }
                });
        gameBus.addListener(
                PlayerInteractEvent.EntityInteract.class,
                event -> {
                    if (Strikes.isFork(event.getItemStack())) {
                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        if (event.getEntity() instanceof ServerPlayer player) {
                            try (var _ = TickBudget.enter("aspect")) {
                                Strikes.strikeEntity(
                                        player, event.getItemStack(), event.getTarget());
                            }
                        }
                    }
                });
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event -> {
                    loading = event.getServerResources();
                    event.addListener(
                            Identifier.fromNamespaceAndPath("vftv", "scanning_tuning"),
                            new TuningListener(ServerLifecycleHooks.getCurrentServer() == null));
                });
        // DerivedAspects: a data load's item data maps arrive after its recipes and tags, and its
        // default components are bound just after them (some custom ingredients read stacks), so
        // the table is rebuilt then; it is sent with the other aspect maps on join and /reload.
        gameBus.addListener(
                DataMapsUpdatedEvent.class,
                event -> {
                    if (event.getCause() == DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD
                            && event.getRegistryKey().equals(Registries.ITEM)) {
                        mapsLoaded = event.getRegistries();
                    }
                });
        gameBus.addListener(
                DefaultDataComponentsBoundEvent.class,
                event -> {
                    ReloadableServerResources resources = loading;
                    RegistryAccess registries = mapsLoaded;
                    if (event.getUpdateCause()
                                    == DefaultDataComponentsBoundEvent.UpdateCause.SERVER_DATA_LOAD
                            && resources != null
                            && registries != null) {
                        loading = null;
                        mapsLoaded = null;
                        DerivedAspects.rebuild(
                                resources.getRecipeManager().getRecipes(), registries);
                    }
                });
        gameBus.addListener(ServerStoppedEvent.class, event -> KnowledgeService.onServerStopped());
        gameBus.addListener(ServerStartedEvent.class, event -> DerivedAspects.serverStarted());
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> event.getRelevantPlayers().forEach(DerivedAspects::sendTo));
        DerivationExport.installMods(AspectNeoForge::mods);
        gameBus.addListener(
                TagsUpdatedEvent.class,
                event ->
                        event.getRegistries()
                                .lookupOrThrow(AspectKeys.REGISTRY)
                                .listElements()
                                .forEach(
                                        h -> {
                                            if (h.value() instanceof AspectEntry entry) {
                                                entry.bind(h.key());
                                            }
                                        }));
    }

    /** The loaded mods' ids and versions, for the export's report and conditions. */
    private static SortedMap<String, String> mods() {
        SortedMap<String, String> mods = new TreeMap<>();
        ModList.get().getMods().forEach(m -> mods.put(m.getModId(), m.getVersion().toString()));
        return mods;
    }

    /** Posts an event; a throwing listener is logged and the committed state stands. */
    private static void post(Event event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (RuntimeException e) {
            LOGGER.error("A listener of {} threw; the state stands", event.getClass().getName(), e);
        }
    }

    /** The data maps through NeoForge's holder API. */
    private static final class DataMapLookup implements AspectLookup.Maps {
        @Override
        public @Nullable AspectValues item(Item item) {
            return BuiltInRegistries.ITEM.wrapAsHolder(item).getData(AspectDataMaps.ITEM);
        }

        @Override
        public @Nullable AspectValues block(Block block) {
            return BuiltInRegistries.BLOCK.wrapAsHolder(block).getData(AspectDataMaps.BLOCK);
        }

        @Override
        public @Nullable AspectValues entity(EntityType<?> type) {
            return BuiltInRegistries.ENTITY_TYPE
                    .wrapAsHolder(type)
                    .getData(AspectDataMaps.ENTITY_TYPE);
        }

        @Override
        public @Nullable AspectValues defaults(Item item) {
            return BuiltInRegistries.ITEM.wrapAsHolder(item).getData(AspectDataMaps.DEFAULTS);
        }
    }

    /**
     * Reads {@code scanning.json} off-thread and applies it on the reload's own thread; when a
     * world opens it also checks the aspect graph, whose registry is fixed until the world reopens.
     */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<ScanningTuning>> {
        private final boolean opening;

        TuningListener(boolean opening) {
            this.opening = opening;
        }

        @Override
        protected DataResult<ScanningTuning> prepare(
                ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(ScanningTuning.DEFAULTS);
            }
            try (InputStream in = resource.get().open()) {
                return ScanningService.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<ScanningTuning> read, ResourceManager manager, ProfilerFiller profiler) {
            if (opening) {
                ScanningService.checkGraph(getRegistryLookup().lookupOrThrow(AspectKeys.REGISTRY));
            }
            ScanningService.applyTuning(read, !opening);
        }
    }
}
