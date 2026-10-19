package com.andrewbrogoff.vftv.neoforge.essentia;

import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import com.andrewbrogoff.vftv.api.neoforge.crucible.CrucibleEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.EssentiaService;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The essentia feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus
 * and mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one
 * line.
 */
public final class EssentiaNeoForge {
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", EssentiaService.TUNING_PATH);

    private EssentiaNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        EssentiaService.install(new NeoForgeEssentiaSeam());
        modBus.addListener(RegisterCapabilitiesEvent.class, EssentiaNeoForge::capabilities);
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "essentia_tuning"),
                                new TuningListener(
                                        ServerLifecycleHooks.getCurrentServer() != null)));
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> event.getRelevantPlayers().forEach(EssentiaService::sendTo));
        gameBus.addListener(
                ServerTickEvent.Post.class,
                event -> EssentiaService.onServerTickEnd(event.getServer()));
        gameBus.addListener(
                ServerStartedEvent.class,
                event -> EssentiaService.onServerStarted(event.getServer()));
        gameBus.addListener(ServerStoppedEvent.class, event -> EssentiaService.onServerStopped());
        gameBus.addListener(
                LevelEvent.Unload.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        EssentiaService.onLevelUnload(level);
                    }
                });
        // A crucible that dissolved or crafted at an endpoint wakes the lines touching it.
        gameBus.addListener(
                CrucibleEvent.class,
                event -> {
                    switch (event.payload()) {
                        case CruciblePayload.Dissolved d ->
                                EssentiaService.wakeAt(d.level(), d.pos());
                        case CruciblePayload.Crafted c ->
                                EssentiaService.wakeAt(c.level(), c.pos());
                        default -> {}
                    }
                });
        gameBus.addListener(
                TagsUpdatedEvent.ServerDataLoad.class, event -> EssentiaService.onTagsReloaded());
    }

    /** The jar's handler and suction on every face and the null side. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                EssentiaCapabilities.HANDLER,
                EssentiaContent.jarType(),
                (jar, side) -> (JarHandler) jar.journal(() -> new JarHandler(jar)));
        event.registerBlockEntity(
                EssentiaCapabilities.SUCTION, EssentiaContent.jarType(), (jar, side) -> jar);
    }

    /** Reads {@code essentia.json} off-thread and applies it on the reload's own thread. */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<EssentiaTuning>> {
        private final boolean reloading;

        TuningListener(boolean reloading) {
            this.reloading = reloading;
        }

        @Override
        protected DataResult<EssentiaTuning> prepare(
                ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(EssentiaTuning.DEFAULTS);
            }
            try (InputStream in = resource.get().open()) {
                return EssentiaService.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<EssentiaTuning> read, ResourceManager manager, ProfilerFiller profiler) {
            EssentiaService.applyTuning(read, reloading);
        }
    }
}
