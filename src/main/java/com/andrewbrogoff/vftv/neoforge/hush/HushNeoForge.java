package com.andrewbrogoff.vftv.neoforge.hush;

import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import com.andrewbrogoff.vftv.api.golem.GolemPayload;
import com.andrewbrogoff.vftv.api.hush.FluxPayload;
import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushSpreadPayload;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import com.andrewbrogoff.vftv.api.neoforge.crucible.CrucibleEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaEvent;
import com.andrewbrogoff.vftv.api.neoforge.golem.GolemEvent;
import com.andrewbrogoff.vftv.api.neoforge.hush.FluxEvent;
import com.andrewbrogoff.vftv.api.neoforge.hush.HushSpreadEvent;
import com.andrewbrogoff.vftv.api.neoforge.infusion.InfusionEvent;
import com.andrewbrogoff.vftv.api.neoforge.vis.NodeEvent;
import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.vis.NodeEventKind;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.model.FluxMath;
import com.andrewbrogoff.vftv.core.hush.Aura;
import com.andrewbrogoff.vftv.core.hush.FluxService;
import com.andrewbrogoff.vftv.core.hush.Hollow;
import com.andrewbrogoff.vftv.core.hush.HushBells;
import com.andrewbrogoff.vftv.core.hush.HushChunkStatus;
import com.andrewbrogoff.vftv.core.hush.HushContent;
import com.andrewbrogoff.vftv.core.hush.HushPlatform;
import com.andrewbrogoff.vftv.core.hush.HushService;
import com.andrewbrogoff.vftv.core.hush.HushStatuses;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeRegistrar;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The hush feature's NeoForge wiring, called once from the {@code @Mod} constructor: the {@code
 * vftv:aura} attachment and its sync, the platform seam, one {@code HIGHEST} listener per sibling
 * spill event (the only deposits besides the bell's removal and the command), the level ticks and
 * chunk hooks, the Hollow's attributes, the Pealing Bell's capabilities and the harness dump. Each
 * listener calls {@code core} in one line.
 */
public final class HushNeoForge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> LOGGED = new HashSet<>();

    private HushNeoForge() {}

    /** Subscribes the feature's listeners and registers its NeoForge-only types. */
    public static void register(IEventBus modBus, IEventBus gameBus, NeoForgeRegistrar registrar) {
        Supplier<AttachmentType<?>> aura =
                registrar.register(
                        NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                        HushKeys.AURA_ATTACHMENT.getPath(),
                        () ->
                                AttachmentType.builder(Aura::new)
                                        .serialize(Aura.CODEC.fieldOf("data"))
                                        .sync(new AuraSync())
                                        .build());
        FluxService.install(new Platform(aura));
        modBus.addListener(
                EntityAttributeCreationEvent.class,
                event -> event.put(HushContent.hollow(), Hollow.createAttributes().build()));
        modBus.addListener(
                RegisterCapabilitiesEvent.class,
                event -> {
                    event.registerBlockEntity(
                            EssentiaCapabilities.HANDLER,
                            HushContent.pealingBellType(),
                            (be, side) -> PealingBellEssentia.of(be));
                    event.registerBlockEntity(
                            EssentiaCapabilities.SUCTION,
                            HushContent.pealingBellType(),
                            (be, side) -> PealingBellEssentia.of(be));
                });
        listen(gameBus, VisSpentEvent.class, e -> vis(e));
        listen(gameBus, CrucibleEvent.class, e -> crucible(e));
        listen(gameBus, EssentiaEvent.class, e -> essentia(e));
        listen(gameBus, InfusionEvent.class, e -> infusion(e));
        listen(gameBus, GolemEvent.class, e -> golem(e));
        gameBus.addListener(
                NodeEvent.class,
                event -> {
                    if (event.payload().kind() == NodeEventKind.REMOVED) {
                        var server =
                                net.neoforged.neoforge.server.ServerLifecycleHooks
                                        .getCurrentServer();
                        if (server != null) {
                            HushService.onNodeRemoved(server, event.payload().node().id());
                        }
                    }
                });
        gameBus.addListener(
                LevelTickEvent.Pre.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        FluxService.tickStart(level);
                    }
                });
        gameBus.addListener(
                LevelTickEvent.Post.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        HushService.tick(level);
                    }
                });
        gameBus.addListener(
                ChunkEvent.Load.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        FluxService.onLoad(level, event.getChunk());
                    }
                });
        gameBus.addListener(
                ChunkEvent.Unload.class,
                event -> {
                    if (event.getLevel() instanceof ServerLevel level) {
                        FluxService.onUnload(level, event.getChunk().getPos());
                    }
                });
        gameBus.addListener(
                ChunkWatchEvent.Sent.class,
                event -> HushService.onWatch(event.getPlayer(), event.getChunk()));
        gameBus.addListener(
                OnDatapackSyncEvent.class,
                event -> {
                    if (event.getPlayer() == null) {
                        HushService.onTuningReload(event.getPlayerList().getServer());
                    }
                });
        gameBus.addListener(
                ServerStoppedEvent.class,
                event -> {
                    FluxService.clear();
                    HushBells.clear();
                });
        modBus.addListener(
                InterModEnqueueEvent.class,
                event ->
                        InterModComms.sendTo(
                                "mc_agent_harness",
                                "dump_server",
                                () ->
                                        Map.entry(
                                                "vftv:hush",
                                                (Function<String, String>) HushNeoForge::dump)));
    }

    private static String dump(String args) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? "{}" : com.andrewbrogoff.vftv.core.hush.HushDump.dump(server, args);
    }

    /** One {@code HIGHEST} listener that never throws: an exception is logged once per source. */
    private static <E extends Event> void listen(
            IEventBus bus, Class<E> type, Consumer<E> deposit) {
        bus.addListener(
                EventPriority.HIGHEST,
                type,
                event -> {
                    try {
                        deposit.accept(event);
                    } catch (RuntimeException e) {
                        if (LOGGED.add(type.getSimpleName())) {
                            LOGGER.error("A flux deposit from {} failed", type.getSimpleName(), e);
                        }
                    }
                });
    }

    private static int[] amounts(SequencedMap<?, Integer> aspects) {
        return aspects.values().stream().mapToInt(Integer::intValue).toArray();
    }

    /** {@code VisSpentEvent}: that event's {@code quote.spill}, never recomputed. */
    private static void vis(VisSpentEvent event) {
        var p = event.payload();
        FluxService.deposit(
                p.level(), p.pos(), p.quote().spill(), new FluxSource.Vis(), p.player());
    }

    /** {@code CrucibleEvent(SPILLED)}, cause {@code EMPTIED} or {@code REMOVED}. */
    private static void crucible(CrucibleEvent event) {
        if (event.payload() instanceof CruciblePayload.Spilled s
                && (s.cause() == SpillCause.EMPTIED || s.cause() == SpillCause.REMOVED)) {
            int flux =
                    FluxMath.essentiaFlux(
                            amounts(s.aspects()), s.cause(), FluxRulesService.current());
            FluxService.deposit(
                    s.level(), s.pos(), flux, new FluxSource.Essentia(s.cause()), s.player());
        }
    }

    /** {@code EssentiaEvent(SPILLED)}, cause {@code DESTROYED} or {@code REMOVED}. */
    private static void essentia(EssentiaEvent event) {
        if (event.payload() instanceof EssentiaPayload.Spilled s
                && (s.cause() == SpillCause.DESTROYED || s.cause() == SpillCause.REMOVED)) {
            int flux =
                    FluxMath.essentiaFlux(
                            amounts(s.aspects()), s.cause(), FluxRulesService.current());
            FluxService.deposit(s.level(), s.pos(), flux, new FluxSource.Essentia(s.cause()), null);
        }
    }

    /**
     * {@code InfusionEvent(SPILLED)}, cause {@code UPSET}, {@code ABANDONED} or {@code REMOVED},
     * priced with the rules the event carries (absent: 0).
     */
    private static void infusion(InfusionEvent event) {
        if (event.payload() instanceof InfusionPayload.Spilled s
                && (s.cause() == SpillCause.UPSET
                        || s.cause() == SpillCause.ABANDONED
                        || s.cause() == SpillCause.REMOVED)) {
            FluxRules rules = event.fluxRules();
            int flux = FluxMath.essentiaFlux(amounts(s.aspects()), s.cause(), rules);
            FluxService.deposit(s.level(), s.pos(), flux, new FluxSource.Essentia(s.cause()), null);
        }
    }

    /** {@code GolemEvent(SPILLED)}, cause {@code DIED}, at its death position. */
    private static void golem(GolemEvent event) {
        if (event.payload() instanceof GolemPayload.Spilled s && s.cause() == SpillCause.DIED) {
            int flux =
                    FluxMath.essentiaFlux(
                            new int[] {s.amount()}, SpillCause.DIED, FluxRulesService.current());
            FluxService.deposit(
                    s.level(), s.pos(), flux, new FluxSource.Essentia(SpillCause.DIED), null);
        }
    }

    /** The seam: the attachment, the event posts and the break check. */
    private record Platform(Supplier<AttachmentType<?>> type) implements HushPlatform {
        private AttachmentType<Aura> aura() {
            // Registered from an AttachmentType<Aura> builder above.
            @SuppressWarnings("unchecked")
            AttachmentType<Aura> t = (AttachmentType<Aura>) type.get();
            return t;
        }

        @Override
        public @Nullable Aura aura(LevelChunk chunk) {
            return chunk.hasData(aura()) ? chunk.getData(aura()) : null;
        }

        @Override
        public void createAura(LevelChunk chunk, Aura value) {
            chunk.setData(aura(), value);
        }

        @Override
        public void syncAura(LevelChunk chunk) {
            chunk.syncData(aura());
        }

        @Override
        public boolean spread(HushSpreadPayload payload) {
            try {
                return !NeoForge.EVENT_BUS.post(new HushSpreadEvent(payload)).isCanceled();
            } catch (RuntimeException e) {
                LOGGER.error("A HushSpreadEvent listener threw; the mutation is a miss", e);
                return false;
            }
        }

        @Override
        public void flux(FluxPayload payload) {
            try {
                NeoForge.EVENT_BUS.post(new FluxEvent(payload));
            } catch (RuntimeException e) {
                LOGGER.error("A FluxEvent listener threw; the change stands", e);
            }
        }

        @Override
        public boolean mayBreak(
                ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
            return !NeoForge.EVENT_BUS
                    .post(new BreakBlockEvent(level, pos, state, player))
                    .isCanceled();
        }
    }

    /**
     * The aura's sync: {@code {status, flux, seep?}} only, never the raw tag; the client keeps it
     * in its per-chunk statuses.
     */
    private static final class AuraSync implements AttachmentSyncHandler<Aura> {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Aura attachment, boolean initialSync) {
            HushChunkStatus.View.of(attachment).write(buf);
        }

        @Override
        public Aura read(
                IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable Aura previous) {
            HushChunkStatus.View view = HushChunkStatus.View.read(buf);
            if (holder instanceof LevelChunk chunk) {
                HushStatuses.put(chunk.getPos().pack(), view);
            }
            return previous != null ? previous : new Aura();
        }
    }
}
