package com.andrewbrogoff.vftv.neoforge.flux;

import com.andrewbrogoff.vftv.core.flux.FluxRulesPayload;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The flux feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class FluxNeoForge {
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", FluxRulesService.TUNING_PATH);

    private FluxNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "hush_tuning"),
                                new TuningListener(
                                        ServerLifecycleHooks.getCurrentServer() != null)));
        // On join and after every /reload, once the new tuning is applied.
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> event.getRelevantPlayers().forEach(FluxNeoForge::sendRulesTo));
    }

    /** Sends the rules in force to every player, as after a Hush config change. */
    public static void sendRules(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(FluxNeoForge::sendRulesTo);
    }

    /**
     * Sends the rules in force to one player whose connection has negotiated the payload; a
     * connection without it (a mock player) gets nothing rather than an exception.
     */
    static void sendRulesTo(ServerPlayer player) {
        if (player.connection.hasChannel(FluxRulesPayload.TYPE)) {
            PacketDistributor.sendToPlayer(
                    player, new FluxRulesPayload(FluxRulesService.current()));
        }
    }

    /** Reads {@code hush.json} off-thread and applies it on the reload's own thread. */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<int[]>> {
        private final boolean reloading;

        TuningListener(boolean reloading) {
            this.reloading = reloading;
        }

        @Override
        protected DataResult<int[]> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(FluxTuning.defaults());
            }
            try (InputStream in = resource.get().open()) {
                return FluxRulesService.parse(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<int[]> read, ResourceManager manager, ProfilerFiller profiler) {
            FluxRulesService.applyTuning(read, reloading);
        }
    }
}
