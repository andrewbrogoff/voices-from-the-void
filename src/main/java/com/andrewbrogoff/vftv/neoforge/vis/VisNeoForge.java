package com.andrewbrogoff.vftv.neoforge.vis;

import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.vis.NodeEvent;
import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.vis.NodeService;
import com.andrewbrogoff.vftv.core.vis.NodeTapBlock;
import com.andrewbrogoff.vftv.core.vis.RelayBlockEntity;
import com.andrewbrogoff.vftv.core.vis.RelayService;
import com.andrewbrogoff.vftv.core.vis.Tapping;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.VisEvents;
import com.andrewbrogoff.vftv.core.vis.VisFeature;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.andrewbrogoff.vftv.core.vis.WandParts;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

/**
 * The vis feature's NeoForge wiring, called once from the {@code @Mod} constructor: game-bus and
 * mod-bus listeners, capabilities and data-map types. Each listener calls {@code core} in one line.
 */
public final class VisNeoForge {
    private static final Identifier TUNING =
            Identifier.fromNamespaceAndPath("vftv", VisRulesService.TUNING_PATH);

    private VisNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        // vftv:vis_discount: a syncable percentage attribute, default 0, range 0 to 1.
        Supplier<Attribute> discount =
                registrar.register(
                        Registries.ATTRIBUTE,
                        VisKeys.VIS_DISCOUNT.identifier().getPath(),
                        () ->
                                new PercentageAttribute("attribute.name.vftv.vis_discount", 0, 0, 1)
                                        .setSyncable(true));
        modBus.addListener(
                EntityAttributeModificationEvent.class,
                event -> event.add(EntityTypes.PLAYER, holder(discount)));
        VisEvents.install(
                p -> NeoForge.EVENT_BUS.post(new VisSpentEvent(p)),
                p -> NeoForge.EVENT_BUS.post(new NodeEvent(p)));
        VisEvents.installProtection(new Protection());
        modBus.addListener(RegisterCapabilitiesEvent.class, VisNeoForge::capabilities);
        gameBus.addListener(
                AddServerReloadListenersEvent.class,
                event ->
                        event.addListener(
                                Identifier.fromNamespaceAndPath("vftv", "vis_tuning"),
                                new TuningListener(
                                        ServerLifecycleHooks.getCurrentServer() != null)));
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> event.getRelevantPlayers().forEach(VisRulesService::sendTo));
        gameBus.addListener(TagsUpdatedEvent.class, event -> WandParts.of(event.getRegistries()));
        gameBus.addListener(
                ServerStartedEvent.class, event -> NodeService.onServerStarted(event.getServer()));
        gameBus.addListener(
                ServerStoppedEvent.class,
                event -> {
                    NodeService.clear();
                    Tapping.clear();
                    RelayService.clear();
                });
        gameBus.addListener(
                LevelEvent.Unload.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        RelayService.unload(level);
                    }
                });
        gameBus.addListener(
                ServerTickEvent.Post.class,
                event -> {
                    try (var _ = TickBudget.enter("vis")) {
                        RelayService.tickEnd(event.getServer());
                    }
                });
        gameBus.addListener(
                ChunkEvent.Load.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        NodeService.queue(level, event.getChunk().getPos());
                        RelayService.queueChunk(level, event.getChunk().getPos());
                    }
                });
        gameBus.addListener(
                ServerTickEvent.Pre.class,
                event -> {
                    try (var _ = TickBudget.enter("vis")) {
                        NodeService.tickStart(event.getServer());
                    }
                });
        gameBus.addListener(
                LevelTickEvent.Post.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        try (var _ = TickBudget.enter("vis")) {
                            NodeService.flush(level);
                        }
                    }
                });
        gameBus.addListener(
                PlayerEvent.PlayerLoggedInEvent.class,
                event -> {
                    if (event.getEntity() instanceof ServerPlayer player) {
                        NodeService.onPlayerJoin(player);
                    }
                });
        for (var type :
                List.of(
                        PlayerEvent.PlayerLoggedOutEvent.class,
                        PlayerEvent.PlayerRespawnEvent.class,
                        PlayerEvent.PlayerChangedDimensionEvent.class)) {
            gameBus.addListener(
                    type,
                    event -> {
                        if (event.getEntity() instanceof ServerPlayer player) {
                            Tapping.onLogout(player);
                        }
                    });
        }
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_server",
                                () ->
                                        Map.entry(
                                                "vftv:vis",
                                                (Function<String, String>) VisNeoForge::dump)));
    }

    /** The Node Tap's handler and suction on every face but its node's, and the null side. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                EssentiaCapabilities.HANDLER,
                VisContent.nodeTapEntity(),
                (tap, side) -> faces(tap, side) ? NodeTapEssentia.of(tap) : null);
        event.registerBlockEntity(
                EssentiaCapabilities.SUCTION,
                VisContent.nodeTapEntity(),
                (tap, side) -> faces(tap, side) ? NodeTapEssentia.of(tap) : null);
    }

    private static boolean faces(RelayBlockEntity tap, @Nullable Direction side) {
        return side == null || side != tap.getBlockState().getValue(NodeTapBlock.FACING);
    }

    /** The capture's break event and the release's placement event, which claim mods answer. */
    private static final class Protection implements VisEvents.Protection {
        @Override
        public boolean mayBreak(
                ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
            return !NeoForge.EVENT_BUS
                    .post(new BreakBlockEvent(level, pos, state, player))
                    .isCanceled();
        }

        @Override
        public boolean mayPlace(
                ServerPlayer player,
                ServerLevel level,
                BlockPos pos,
                BlockState against,
                Runnable install) {
            BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            install.run();
            return !NeoForge.EVENT_BUS
                    .post(new BlockEvent.EntityPlaceEvent(snapshot, against, player))
                    .isCanceled();
        }
    }

    // The registrar's supplier is NeoForge's DeferredHolder, which is the registry holder.
    @SuppressWarnings("unchecked")
    private static Holder<Attribute> holder(Supplier<Attribute> registered) {
        return (Holder<Attribute>) registered;
    }

    /** The harness's {@code vftv:vis} dump: the registry's counters, the starter and the rules. */
    private static String dump(String args) {
        JsonObject data = new JsonObject();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            var ledger = NodeService.ledger(server);
            data.addProperty("readable", ledger != null);
            if (ledger != null) {
                data.addProperty("nextId", ledger.nextId());
                data.addProperty("starterPlaced", ledger.starterPlaced());
                var starter = ledger.starter();
                if (starter != null) {
                    var info = NodeService.get(server, starter.id());
                    data.addProperty("starterId", starter.id());
                    if (info != null && info.where() != null) {
                        data.addProperty("starterPos", info.where().pos().toShortString());
                    }
                }
            }
            data.add("rules", VisRulesService.current(server).toJson());
        }
        return data.toString();
    }

    /**
     * Reads {@code vis.json} off-thread and applies it on the reload's own thread; when a world
     * opens it also checks the part and node-type registries, which are fixed until it reopens.
     */
    private static final class TuningListener
            extends SimplePreparableReloadListener<DataResult<VisTuning>> {
        private final boolean reloading;

        TuningListener(boolean reloading) {
            this.reloading = reloading;
        }

        @Override
        protected DataResult<VisTuning> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Optional<Resource> resource = manager.getResource(TUNING);
            if (resource.isEmpty()) {
                return DataResult.success(VisTuning.DEFAULTS);
            }
            try (InputStream in = resource.get().open()) {
                return VisRulesService.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                return DataResult.error(e::toString);
            }
        }

        @Override
        protected void apply(
                DataResult<VisTuning> read, ResourceManager manager, ProfilerFiller profiler) {
            if (!reloading) {
                List<String> problems = VisFeature.validate(getRegistryLookup());
                if (!problems.isEmpty()) {
                    throw new IllegalStateException(
                            "The vis registries are invalid: " + String.join("; ", problems));
                }
            }
            VisRulesService.applyTuning(read, reloading);
        }
    }
}
