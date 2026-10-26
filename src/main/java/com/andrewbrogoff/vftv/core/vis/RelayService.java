package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.common.SavedDataSlot;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.andrewbrogoff.vftv.core.vis.model.RelayNetwork;
import com.andrewbrogoff.vftv.core.vis.model.RelayPulse;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The relay networks (wands spec, "Relays"): per dimension, the persisted membership ({@code
 * vftv_relays}), its components in memory, the membership queue processed at the end of each server
 * tick, the active sinks and the pulse. Server thread only; nothing here loads a chunk.
 */
public final class RelayService {
    /** The membership's SavedData, {@code vftv_relays}, in each dimension's storage. */
    public static final SavedDataSlot<Retained<RelayLedger>> SLOT =
            new SavedDataSlot<>(
                    Identifier.fromNamespaceAndPath("vftv", "relays"),
                    RawRecordCodec.wrap(RelayLedger.BOUNDED),
                    () -> new Retained.Read<>(RelayLedger.EMPTY));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SLOTS = RelayNetwork.MAX_MEMBERS * 8;
    private static final Map<ResourceKey<Level>, Net> LEVELS = new HashMap<>();

    /** One dimension's runtime state. */
    private static final class Net {
        final RelayNetwork net;
        final boolean writable;
        final Map<Long, Set<Long>> byChunk = new HashMap<>();
        final TreeSet<Long> chunks = new TreeSet<>();
        final TreeSet<Long> positions = new TreeSet<>();
        final Map<Long, UUID> placers = new HashMap<>();
        final TreeSet<Long> sinks = new TreeSet<>();
        // The pulse's preallocated arrays: sources, rooms, and what moved.
        final int[] sources = new int[SLOTS];
        final int[] room = new int[SLOTS];
        final int[] taken = new int[SLOTS];
        final int[] given = new int[SLOTS];
        int firstSink = -1;
        int aspectCount;
        boolean dirty;

        Net(RelayNetwork net, boolean writable) {
            this.net = net;
            this.writable = writable;
        }

        final RelayPulse.Moves moves =
                (sink, aspect, source, amount) -> {
                    int a = aspectCount;
                    taken[source * a + aspect] += amount;
                    given[sink * a + aspect] += amount;
                    if (firstSink < 0) {
                        firstSink = sink;
                    }
                };
    }

    private RelayService() {}

    private static Net state(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> load(level));
    }

    private static Net load(ServerLevel level) {
        double range = VisRulesService.tuning().decimal("relay.range");
        Retained<RelayLedger> stored = SLOT.get(level);
        if (!(stored instanceof Retained.Read<RelayLedger> read)) {
            LOGGER.warn(
                    "vftv_relays in {} is unreadable ({}); kept as it was, and its relays stay"
                            + " idle",
                    level.dimension().identifier(),
                    ((Retained.Unreadable<RelayLedger>) stored).reason());
            return new Net(new RelayNetwork(range), false);
        }
        Net n = new Net(new RelayNetwork(range), true);
        for (RelayLedger.Member m : read.value().members()) {
            BlockPos p = m.pos();
            n.net.load(p.asLong(), p.getX(), p.getY(), p.getZ(), m.kind());
            n.byChunk.computeIfAbsent(ChunkPos.pack(p), c -> new HashSet<>()).add(p.asLong());
        }
        logOversized(level, n.net.rebuild(range), range);
        return n;
    }

    private static void logOversized(ServerLevel level, List<Long> firstUnlinked, double range) {
        for (long key : firstUnlinked) {
            LOGGER.warn(
                    "A relay network in {} has more than {} members at relay.range {}; members"
                            + " from {} on are unlinked",
                    level.dimension().identifier(),
                    RelayNetwork.MAX_MEMBERS,
                    range,
                    BlockPos.of(key).toShortString());
        }
    }

    /** Forgets every dimension, at server stop. */
    public static void clear() {
        LEVELS.clear();
        NodeTaps.clear();
    }

    /** Forgets one dimension, when it unloads. */
    public static void unload(ServerLevel level) {
        LEVELS.remove(level.dimension());
    }

    // --- the membership queue -----------------------------------------------------------------

    /** Queues a position (a member's placement, load, removal or state change). */
    public static void queue(ServerLevel level, BlockPos pos) {
        state(level).positions.add(pos.asLong());
    }

    /** Queues a loaded chunk. */
    public static void queueChunk(ServerLevel level, ChunkPos chunk) {
        state(level).chunks.add(chunk.pack());
    }

    /** A member was placed by {@code placer}: queued, remembering who to tell of a refusal. */
    static void placed(ServerLevel level, BlockPos pos, @Nullable ServerPlayer placer) {
        Net n = state(level);
        if (placer != null) {
            n.placers.put(pos.asLong(), placer.getUUID());
        }
        n.positions.add(pos.asLong());
    }

    /** The member kind of a block state, or null. */
    static RelayNetwork.@Nullable Kind kind(BlockState state) {
        return state.getBlock() instanceof RelayBlock r ? r.kind() : null;
    }

    private static boolean loaded(ServerLevel level, long key) {
        return level.getChunkSource().getChunkNow(BlockPos.getX(key) >> 4, BlockPos.getZ(key) >> 4)
                != null;
    }

    /**
     * The end of a server tick: every level's queue (chunks first, ascending {@code
     * ChunkPos.pack()}, then positions, ascending {@code asLong()}), a range rebuild if the tuning
     * changed, the pulse, and the SavedData written if anything changed.
     */
    public static void tickEnd(MinecraftServer server) {
        if (LEVELS.isEmpty()) {
            return;
        }
        VisTuning t = VisRulesService.tuning();
        double range = t.decimal("relay.range");
        for (ServerLevel level : server.getAllLevels()) {
            Net n = LEVELS.get(level.dimension());
            if (n == null) {
                continue;
            }
            if (n.net.range() != range) {
                logOversized(level, n.net.rebuild(range), range);
                n.dirty = true;
                refreshAllSinks(level, n);
            }
            process(level, n);
            pulse(level, n, t);
            if (n.dirty && n.writable) {
                List<RelayLedger.Member> members = new ArrayList<>();
                for (long key : n.net.keys()) {
                    RelayNetwork.Kind kind = java.util.Objects.requireNonNull(n.net.kind(key));
                    members.add(new RelayLedger.Member(BlockPos.of(key), kind));
                }
                SLOT.set(level, new Retained.Read<>(new RelayLedger(members)));
            }
            n.dirty = false;
        }
    }

    private static void process(ServerLevel level, Net n) {
        while (!n.chunks.isEmpty()) {
            long packed = n.chunks.pollFirst();
            LevelChunk chunk =
                    level.getChunkSource()
                            .getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk == null) {
                continue; // queued again when it loads
            }
            Set<Long> persisted = n.byChunk.get(packed);
            if (persisted != null) {
                n.positions.addAll(persisted);
            }
            for (BlockPos p : chunk.getBlockEntitiesPos()) {
                if (kind(level.getBlockState(p)) != null) {
                    n.positions.add(p.asLong());
                }
            }
        }
        while (!n.positions.isEmpty()) {
            long key = n.positions.pollFirst();
            UUID placer = n.placers.remove(key);
            if (loaded(level, key)) {
                position(level, n, key, placer);
            }
        }
    }

    /** One position of the membership table. */
    private static void position(ServerLevel level, Net n, long key, @Nullable UUID placer) {
        BlockPos pos = BlockPos.of(key);
        RelayNetwork.Kind actual = kind(level.getBlockState(pos));
        RelayNetwork.Kind persisted = n.net.kind(key);
        if (actual != null && persisted == null) {
            int size = n.net.admissionSize(key, pos.getX(), pos.getY(), pos.getZ());
            if (size > RelayNetwork.MAX_MEMBERS) {
                level.destroyBlock(pos, true);
                ServerPlayer p =
                        placer == null ? null : level.getServer().getPlayerList().getPlayer(placer);
                if (p != null) {
                    StatusLines.send(p, tooMany(size));
                }
                return;
            }
            boolean links = n.net.linksAny(key, pos.getX(), pos.getY(), pos.getZ());
            n.net.admit(key, pos.getX(), pos.getY(), pos.getZ(), actual);
            n.byChunk.computeIfAbsent(ChunkPos.pack(pos), c -> new HashSet<>()).add(key);
            n.dirty = true;
            if (links) {
                level.playSound(
                        null,
                        pos,
                        VisContent.sound(VisContent.RELAY_LINK),
                        SoundSource.BLOCKS,
                        1,
                        1);
            }
            refreshSink(level, pos);
        } else if (actual != null && actual != persisted) {
            n.net.setKind(key, actual);
            n.dirty = true;
            refreshSink(level, pos);
        } else if (actual == null && persisted != null) {
            n.net.remove(key);
            Set<Long> in = n.byChunk.get(ChunkPos.pack(pos));
            if (in != null) {
                in.remove(key);
            }
            n.sinks.remove(key);
            n.dirty = true;
        }
    }

    // --- admission and status -----------------------------------------------------------------

    /** The admission size of a new member at {@code pos} (wands spec, "Admission check"). */
    static int admissionSize(ServerLevel level, BlockPos pos) {
        return state(level).net.admissionSize(pos.asLong(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** "Too many voices for one chord: {@code size} of 32." */
    static Component tooMany(int size) {
        return Component.translatable(
                VisLang.RELAY_TOO_MANY,
                NumberFormats.integer(size),
                NumberFormats.integer(RelayNetwork.MAX_MEMBERS));
    }

    /** A member's status line, built from live values. */
    static Component status(ServerLevel level, BlockPos pos) {
        Net n = state(level);
        BlockState state = level.getBlockState(pos);
        if (state.is(VisContent.nodeTap())) {
            return NodeTaps.status(level, pos);
        }
        int component = n.net.component(pos.asLong());
        if (state.is(VisContent.wandCradle())) {
            return cradleStatus(level, n, pos, component);
        }
        if (n.net.unlinked(pos.asLong())) {
            return Component.translatable(VisLang.STATUS_UNLINKED);
        }
        int members = component < 0 ? 1 : n.net.members(component).size();
        int charging = 0;
        if (component >= 0) {
            for (long s : n.sinks) {
                charging += n.net.component(s) == component ? 1 : 0;
            }
        }
        return Component.translatable(
                VisLang.STATUS_NETWORK,
                NumberFormats.integer(members),
                NumberFormats.integer(RelayNetwork.MAX_MEMBERS),
                NumberFormats.integer(component < 0 ? 0 : sources(level, n, component).size()),
                NumberFormats.integer(charging));
    }

    private static Component cradleStatus(ServerLevel level, Net n, BlockPos pos, int component) {
        if (!(level.getBlockEntity(pos) instanceof WandCradleBlockEntity cradle)
                || cradle.unreadable()
                || cradle.wand().isEmpty()) {
            return Component.translatable(VisLang.STATUS_CRADLE_EMPTY);
        }
        WandParts parts = WandParts.of(level.registryAccess());
        if (!(WandState.of(cradle.wand(), parts) instanceof WandState.Ready wand)) {
            return Component.translatable(VisLang.STATUS_CRADLE_IDLE);
        }
        long have = 0;
        for (ResourceKey<Aspect> a : parts.visAspects()) {
            have += wand.pool(a);
        }
        long capacity = (long) wand.rod().capacity() * parts.visAspects().size();
        if (have >= capacity) {
            return Component.translatable(VisLang.STATUS_CRADLE_FULL);
        }
        if (component < 0 || sources(level, n, component).isEmpty()) {
            return Component.translatable(VisLang.STATUS_CRADLE_IDLE);
        }
        return Component.translatable(
                VisLang.STATUS_CRADLE_CHARGING,
                NumberFormats.integer(have),
                NumberFormats.integer(capacity));
    }

    // --- active sinks and the pulse -----------------------------------------------------------

    /**
     * Re-checks one cradle as an active sink: loaded, holding a non-inert wand with room in some
     * pool.
     */
    static void refreshSink(ServerLevel level, BlockPos pos) {
        Net n = state(level);
        if (loaded(level, pos.asLong())
                && level.getBlockEntity(pos) instanceof WandCradleBlockEntity cradle
                && !cradle.unreadable()
                && hasRoom(level, cradle.wand())) {
            n.sinks.add(pos.asLong());
        } else {
            n.sinks.remove(pos.asLong());
        }
    }

    /** Drops a cradle from the active sinks (it unloaded or went). */
    static void dropSink(ServerLevel level, BlockPos pos) {
        Net n = LEVELS.get(level.dimension());
        if (n != null) {
            n.sinks.remove(pos.asLong());
        }
    }

    private static void refreshAllSinks(ServerLevel level, Net n) {
        for (long s : List.copyOf(n.sinks)) {
            refreshSink(level, BlockPos.of(s));
        }
    }

    private static boolean hasRoom(ServerLevel level, ItemStack stack) {
        WandParts parts = WandParts.of(level.registryAccess());
        if (!(WandState.of(stack, parts) instanceof WandState.Ready wand)) {
            return false;
        }
        for (ResourceKey<Aspect> a : parts.visAspects()) {
            if (wand.pool(a) < wand.rod().capacity()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A component's sources: its active taps whose chunk and node chunk are loaded and whose node
     * is {@code PLACED} with its type present, sorted by node id.
     */
    private static List<NodeBlockEntity> sources(ServerLevel level, Net n, int component) {
        List<NodeBlockEntity> out = new ArrayList<>();
        for (long key : n.net.members(component)) {
            if (n.net.kind(key) != RelayNetwork.Kind.TAP || !loaded(level, key)) {
                continue;
            }
            NodeBlockEntity be = NodeTaps.node(level, BlockPos.of(key));
            if (be != null) {
                out.add(be);
            }
        }
        out.sort(
                (a, b) ->
                        Long.compare(
                                java.util.Objects.requireNonNull(a.state()).id(),
                                java.util.Objects.requireNonNull(b.state()).id()));
        return out;
    }

    /** The pulse (wands spec, "Flow"), on the ticks where {@code gameTime % relay.pulse == 0}. */
    private static void pulse(ServerLevel level, Net n, VisTuning t) {
        long now = level.getGameTime();
        int period = t.integer("relay.pulse");
        if (n.sinks.isEmpty() || now % period != 0) {
            return;
        }
        WandParts parts = WandParts.of(level.registryAccess());
        int aspects = parts.visAspects().size();
        if (aspects == 0) {
            return;
        }
        // Each component with an active sink, in ascending component key.
        TreeMap<Long, List<Long>> byComponent = new TreeMap<>();
        Map<Long, Integer> ids = new HashMap<>();
        for (long s : n.sinks) {
            int c = n.net.component(s);
            if (c >= 0) {
                long key = n.net.componentKey(c);
                ids.put(key, c);
                byComponent.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
        }
        List<Long> touched = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> e : byComponent.entrySet()) {
            pulseComponent(
                    level,
                    n,
                    parts,
                    java.util.Objects.requireNonNull(ids.get(e.getKey())),
                    e.getValue(),
                    now,
                    t);
            touched.addAll(e.getValue());
        }
        for (long s : touched) {
            refreshSink(level, BlockPos.of(s));
        }
    }

    private static void pulseComponent(
            ServerLevel level,
            Net n,
            WandParts parts,
            int component,
            List<Long> sinkKeys,
            long now,
            VisTuning t) {
        List<NodeBlockEntity> sources = sources(level, n, component);
        if (sources.isEmpty()) {
            return;
        }
        int aspects = parts.visAspects().size();
        List<ResourceKey<Aspect>> vis = parts.visAspects();
        List<NodeState> settled = new ArrayList<>(sources.size());
        for (int s = 0; s < sources.size(); s++) {
            NodeState st = NodeService.settle(level, sources.get(s));
            settled.add(st);
            for (int a = 0; a < aspects; a++) {
                NodeState.NodeAspect na = st.byAspect().get(vis.get(a).identifier().toString());
                n.sources[s * aspects + a] = na == null ? 0 : na.current();
                n.taken[s * aspects + a] = 0;
            }
        }
        List<WandCradleBlockEntity> cradles = new ArrayList<>(sinkKeys.size());
        List<WandState.Ready> wands = new ArrayList<>(sinkKeys.size());
        for (long key : sinkKeys) {
            if (level.getBlockEntity(BlockPos.of(key)) instanceof WandCradleBlockEntity c
                    && WandState.of(c.wand(), parts) instanceof WandState.Ready w) {
                int k = cradles.size();
                cradles.add(c);
                wands.add(w);
                for (int a = 0; a < aspects; a++) {
                    n.room[k * aspects + a] = w.rod().capacity() - w.pool(vis.get(a));
                    n.given[k * aspects + a] = 0;
                }
            }
        }
        if (cradles.isEmpty()) {
            return;
        }
        n.aspectCount = aspects;
        n.firstSink = -1;
        RelayPulse.run(
                now,
                t.integer("relay.pulse"),
                t.integer("relay.rate"),
                aspects,
                n.sources,
                sources.size(),
                n.room,
                cradles.size(),
                n.moves);
        for (int s = 0; s < sources.size(); s++) {
            NodeState st = settled.get(s);
            boolean changed = false;
            List<NodeState.NodeAspect> next = new ArrayList<>(st.aspects());
            for (int a = 0; a < aspects; a++) {
                int take = n.taken[s * aspects + a];
                if (take == 0) {
                    continue;
                }
                for (int i = 0; i < next.size(); i++) {
                    NodeState.NodeAspect na = next.get(i);
                    if (na.aspect().equals(vis.get(a).identifier())) {
                        next.set(
                                i,
                                new NodeState.NodeAspect(
                                        na.aspect(), na.max(), na.current() - take));
                        changed = true;
                    }
                }
            }
            if (changed) {
                sources.get(s).setState(st.withAspects(next));
                NodeService.markForSync(level, sources.get(s).getBlockPos());
            }
        }
        for (int k = 0; k < cradles.size(); k++) {
            Map<String, Integer> pools = new HashMap<>();
            for (int a = 0; a < aspects; a++) {
                int got = n.given[k * aspects + a];
                if (got > 0) {
                    pools.put(
                            vis.get(a).identifier().toString(),
                            wands.get(k).pool(vis.get(a)) + got);
                }
            }
            if (!pools.isEmpty()) {
                WandState.write(cradles.get(k).wand(), wands.get(k), pools, parts);
                cradles.get(k).setChanged(); // saved, but charging sends no packet
            }
        }
        if (n.firstSink >= 0) {
            level.playSound(
                    null,
                    cradles.get(n.firstSink).getBlockPos(),
                    VisContent.sound(VisContent.CRADLE_CHARGE),
                    SoundSource.BLOCKS,
                    1,
                    1);
        }
    }
}
