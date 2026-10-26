package com.andrewbrogoff.vftv.neoforge.warp;

import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.neoforge.warp.WarpChangedEvent;
import com.andrewbrogoff.vftv.api.neoforge.warp.WarpDataMaps;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.warp.WarpRecord;
import com.andrewbrogoff.vftv.core.warp.WarpRulesService;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.andrewbrogoff.vftv.neoforge.armor.WornSlots;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The warp feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 *
 * <p>{@code worn} is the Curios collector, or {@link WornSlots#NONE} without Curios (compatibility,
 * "Wiring the collector"): the gear sample's other slots.
 */
public final class WarpNeoForge {
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", WarpRulesService.TUNING_PATH);

    private WarpNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(
            IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar, WornSlots worn) {
        registrar.dataMap(WarpDataMaps.GEAR_WARP);
        WarpService.installPlatform(
                payload -> NeoForge.EVENT_BUS.post(new WarpChangedEvent(payload)),
                WarpNeoForge::gearValue,
                (player, budget, sink) ->
                        worn.collect(
                                player,
                                budget,
                                (slot, index, cosmetic, stack) -> sink.test(stack)));
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "warp_tuning"),
                                new TuningListener(
                                        ServerLifecycleHooks.getCurrentServer() != null)));
        // On join and after every /reload, once the new tuning is applied.
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> event.getRelevantPlayers().forEach(WarpRulesService::sendTo));
        gameBus.addListener(PlayerTickEvent.Post.class, WarpNeoForge::tick);
        gameBus.addListener(
                PlayerEvent.PlayerLoggedInEvent.class, event -> arrive(event.getEntity()));
        gameBus.addListener(
                PlayerEvent.PlayerRespawnEvent.class, event -> arrive(event.getEntity()));
        gameBus.addListener(
                PlayerEvent.PlayerChangedDimensionEvent.class, event -> arrive(event.getEntity()));
        gameBus.addListener(
                PlayerEvent.PlayerLoggedOutEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        WarpService.onLogout(player);
                    }
                });
        gameBus.addListener(
                LivingDeathEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        WarpService.onDeath(player);
                    }
                });
        gameBus.addListener(
                VisSpentEvent.class,
                event -> {
                    if (event.payload().context().kind() == VisKind.VOID) {
                        WarpService.noteVoidWork(event.payload().player());
                    }
                });
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_server",
                                () ->
                                        Map.entry(
                                                "vftv:warp",
                                                (Function<String, String>) WarpNeoForge::dump)));
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try (var _ = TickBudget.enter("warp")) {
                WarpService.onPlayerTick(player);
            }
        }
    }

    private static void arrive(Player player) {
        if (player instanceof ServerPlayer server) {
            WarpService.onArrive(server);
        }
    }

    /** The {@code vftv:gear_warp} value of a stack's item, 0 without one. */
    private static int gearValue(ItemStack stack) {
        Integer value = stack.typeHolder().getData(WarpDataMaps.GEAR_WARP);
        return value == null ? 0 : value;
    }

    /**
     * The harness's {@code vftv:warp} dump: every online player's record and the rules in force.
     */
    private static String dump(String args) {
        JsonObject data = new JsonObject();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        JsonArray players = new JsonArray();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                players.add(WarpRecord.dump(player.getPlainTextName(), WarpService.record(player)));
            }
        }
        data.add("players", players);
        WarpRules rules = WarpRulesService.current();
        data.add("rules", WarpTuning.toJson(rules, true));
        return data.toString();
    }

    /** Reads {@code warp.json} off-thread and applies it on the reload's own thread. */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<WarpRules>> {
        private final boolean reloading;

        TuningListener(boolean reloading) {
            this.reloading = reloading;
        }

        @Override
        protected DataResult<WarpRules> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(WarpTuning.DEFAULTS);
            }
            try (InputStream in = resource.get().open()) {
                return WarpRulesService.parse(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<WarpRules> read, ResourceManager manager, ProfilerFiller profiler) {
            WarpRulesService.applyTuning(read, reloading);
        }
    }
}
