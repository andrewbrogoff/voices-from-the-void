package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.vis.NodeCause;
import com.andrewbrogoff.vftv.api.vis.NodeEventKind;
import com.andrewbrogoff.vftv.api.vis.NodePayload;
import com.andrewbrogoff.vftv.api.vis.NodeRef;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import com.andrewbrogoff.vftv.api.vis.NodeTypeKeys;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.aspect.NodeSource;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.common.SavedDataSlot;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.vis.model.NodeIndex;
import com.andrewbrogoff.vftv.core.vis.model.Reconcile;
import com.andrewbrogoff.vftv.core.vis.model.StarterRing;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The node registry, index and reconciliation (wands spec, "Identity and reconciliation", "The
 * index", "The starter", "Events"): every change to a node's identity goes through here, on the
 * server thread. Plain {@code core} services, internal in v1.
 */
public final class NodeService {
    /** {@code message.vftv.node.starter_missing}. */
    public static final String STARTER_MISSING = "message.vftv.node.starter_missing";

    /** The registry's SavedData, {@code vftv_nodes}, in the overworld's storage. */
    public static final SavedDataSlot<Retained<NodeLedger>> SLOT =
            new SavedDataSlot<>(
                    Identifier.fromNamespaceAndPath("vftv", "nodes"),
                    RawRecordCodec.wrap(NodeLedger.BOUNDED),
                    () -> new Retained.Read<>(new NodeLedger()));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, NodeIndex> INDEX = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, Set<Long>>> CHUNK_IDS = new HashMap<>();
    private static final Map<ResourceKey<Level>, TreeSet<Long>> PENDING = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> DIRTY = new HashMap<>();
    private static boolean noticeSent;
    private static boolean unreadableWarned;

    private NodeService() {}

    /** The answer of {@link #update}. */
    public enum UpdateResult {
        /** Written, the boundary called and {@code CHANGED} posted. */
        UPDATED,
        /** The node is not loaded; nothing changed. */
        NOT_LOADED,
        /** The change is invalid (an unregistered type, or bad aspects); nothing changed. */
        INVALID,
        /** The written change stands, but posting {@code CHANGED} or the boundary threw. */
        COMMITTED_NOTIFY_FAILED
    }

    /** The answer of {@link #remove}. */
    public enum RemoveResult {
        /** Tombstoned. */
        REMOVED,
        /** No live entry with that id. */
        UNKNOWN,
        /** {@code vftv_nodes} is unreadable; nothing changed. */
        UNREADABLE
    }

    /**
     * A node change for {@link #update}: each part optional.
     *
     * @param type a new type, registered
     * @param aspects new aspects and their maxes, at most 6, each 1 to 1000
     * @param clarity a new clarity
     */
    public record NodeChange(
            Optional<ResourceKey<NodeType>> type,
            Optional<SortedMap<ResourceKey<Aspect>, Integer>> aspects,
            Optional<NodeState.Clarity> clarity) {}

    /**
     * A node's registry state, as {@link #get} answers.
     *
     * @param id its id
     * @param state placed, carried or removed
     * @param where its dimension and position while placed
     */
    public record Info(long id, NodeLedger.State state, @Nullable GlobalPos where) {}

    // --- the registry ----------------------------------------------------------------------

    /** The registry, or null while {@code vftv_nodes} is unreadable. */
    public static @Nullable NodeLedger ledger(MinecraftServer server) {
        Retained<NodeLedger> r = SLOT.get(server.overworld());
        if (r instanceof Retained.Read<NodeLedger> read) {
            return read.value();
        }
        if (!unreadableWarned && r instanceof Retained.Unreadable<NodeLedger> u) {
            unreadableWarned = true;
            LOGGER.warn("vftv_nodes is unreadable ({}); node commands refuse", u.reason());
        }
        return null;
    }

    private static void markDirty(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        SLOT.set(overworld, SLOT.get(overworld));
    }

    /** {@code NodeService.get(id)}: the id's registry state, or null. */
    public static @Nullable Info get(MinecraftServer server, long id) {
        NodeLedger ledger = ledger(server);
        NodeLedger.Entry e = ledger == null ? null : ledger.get(id);
        if (e == null) {
            return null;
        }
        GlobalPos where =
                e.dimension().isPresent() && e.pos().isPresent()
                        ? GlobalPos.of(e.dimension().get(), e.pos().get())
                        : null;
        return new Info(id, e.state(), where);
    }

    /** Whether the starter's entry is {@code PLACED}. */
    public static boolean starterPlaced(MinecraftServer server) {
        NodeLedger ledger = ledger(server);
        return ledger != null && ledger.starterPlaced();
    }

    /**
     * Whether the world has no starter while the starter isn't switched off, so an empty sounding
     * names the missing Wellspring.
     */
    public static boolean starterMissing(MinecraftServer server) {
        NodeLedger ledger = ledger(server);
        return VisRulesService.config().starter() != VisConfig.StarterMode.OFF
                && ledger != null
                && ledger.starterMissing();
    }

    private static NodeIndex index(ResourceKey<Level> dimension) {
        return INDEX.computeIfAbsent(dimension, k -> new NodeIndex());
    }

    /** Rebuilds the index and the chunk lookup from the registry, at server start. */
    public static void rebuild(MinecraftServer server) {
        // Keep PENDING: the chunks loaded while the levels were prepared are queued already.
        INDEX.clear();
        CHUNK_IDS.clear();
        NodeLedger ledger = ledger(server);
        if (ledger == null) {
            return;
        }
        for (NodeLedger.Entry e : ledger.entries()) {
            if (e.state() == NodeLedger.State.PLACED) {
                ResourceKey<Level> dim = e.dimension().orElseThrow();
                BlockPos pos = e.pos().orElseThrow();
                if (index(dim).count(pos.getX(), pos.getZ()) >= VisLimits.MAX_NODES_PER_CELL) {
                    LOGGER.warn("Node {} at {} is past its cell's cap; not indexed", e.id(), pos);
                    continue;
                }
                indexAdd(dim, e.id(), pos);
            }
        }
    }

    /** Forgets every in-memory structure, at server stop. */
    public static void clear() {
        INDEX.clear();
        CHUNK_IDS.clear();
        PENDING.clear();
        DIRTY.clear();
        noticeSent = false;
        unreadableWarned = false;
    }

    private static void indexAdd(ResourceKey<Level> dim, long id, BlockPos pos) {
        index(dim).add(id, pos.getX(), pos.getY(), pos.getZ());
        CHUNK_IDS
                .computeIfAbsent(dim, k -> new HashMap<>())
                .computeIfAbsent(ChunkPos.pack(pos), k -> new HashSet<>())
                .add(id);
    }

    private static void indexRemove(ResourceKey<Level> dim, long id, BlockPos pos) {
        index(dim).remove(id, pos.getX(), pos.getZ());
        Set<Long> ids = CHUNK_IDS.getOrDefault(dim, Map.of()).get(ChunkPos.pack(pos));
        if (ids != null) {
            ids.remove(id);
        }
    }

    private static Reconcile.Cell cell(NodeLedger ledger, ResourceKey<Level> dim, BlockPos pos) {
        long[] ids = index(dim).idsIn(pos.getX(), pos.getZ());
        int natural = 0;
        int structure = 0;
        for (long id : ids) {
            NodeLedger.Entry e = ledger.get(id);
            if (e != null && e.origin() == NodeState.Origin.NATURAL) {
                natural++;
            } else if (e != null && e.origin() == NodeState.Origin.STRUCTURE) {
                structure++;
            }
        }
        return new Reconcile.Cell(ids.length, natural, structure);
    }

    private static void post(
            NodeEventKind kind, long id, @Nullable GlobalPos where, NodeCause cause) {
        VisEvents.node(new NodePayload(kind, new NodeRef(id, where), cause));
    }

    // --- reconciliation -----------------------------------------------------------------------

    /** Queues a chunk for reconciliation at the start of the next server tick. */
    public static void queue(ServerLevel level, ChunkPos chunk) {
        PENDING.computeIfAbsent(level.dimension(), k -> new TreeSet<>()).add(chunk.pack());
    }

    /** A node block entity loaded: its chunk is queued. */
    static void onBlockEntityLoad(ServerLevel level, BlockPos pos) {
        queue(level, ChunkPos.containing(pos));
    }

    /** Processes every pending chunk, in ascending {@code ChunkPos.pack()} order per level. */
    public static void tickStart(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        NodeLedger ledger = ledger(server);
        if (ledger == null) {
            PENDING.clear();
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            TreeSet<Long> pending = PENDING.remove(level.dimension());
            if (pending == null) {
                continue;
            }
            for (long packed : pending) {
                LevelChunk chunk =
                        level.getChunkSource()
                                .getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
                if (chunk != null) {
                    reconcile(level, ledger, chunk);
                }
            }
        }
    }

    private static void reconcile(ServerLevel level, NodeLedger ledger, LevelChunk chunk) {
        ResourceKey<Level> dim = level.dimension();
        Block nodeBlock = VisContent.node();
        List<BlockPos> positions = new ArrayList<>(chunk.getBlockEntitiesPos());
        positions.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        // Each node block's id after this pass; -1 for an unreadable one, which is never a ghost.
        Map<BlockPos, Long> present = new HashMap<>();
        boolean changed = false;
        boolean starterChanged = false;
        for (BlockPos pos : positions) {
            if (!level.getBlockState(pos).is(nodeBlock)
                    || !(level.getBlockEntity(pos) instanceof NodeBlockEntity be)) {
                continue;
            }
            NodeState state = be.state();
            if (state == null) {
                present.put(pos.immutable(), -1L);
                continue; // unreadable or empty: left alone, neither minted nor a ghost
            }
            present.put(pos.immutable(), state.id());
            NodeLedger.Entry entry = state.id() == 0 ? null : ledger.get(state.id());
            Reconcile.Entry relation =
                    entry == null
                            ? Reconcile.Entry.NONE
                            : switch (entry.state()) {
                                case PLACED ->
                                        entry.placedAt(dim, pos)
                                                ? Reconcile.Entry.PLACED_HERE
                                                : Reconcile.Entry.PLACED_ELSEWHERE;
                                case CARRIED -> Reconcile.Entry.CARRIED;
                                case REMOVED -> Reconcile.Entry.REMOVED;
                            };
            Reconcile.Action action =
                    Reconcile.decide(
                            state.id() != 0,
                            state.origin().model(),
                            relation,
                            cell(ledger, dim, pos));
            GlobalPos where = GlobalPos.of(dim, pos.immutable());
            switch (action) {
                case NOTHING -> {}
                case MINT -> {
                    long id = ledger.mint();
                    be.setState(
                            state.withId(id)
                                    .stamped(level.getGameTime(), VisRulesService.tuning()));
                    ledger.put(NodeLedger.Entry.placed(id, state.origin(), dim, pos));
                    indexAdd(dim, id, pos);
                    present.put(pos.immutable(), id);
                    changed = true;
                    post(
                            NodeEventKind.CREATED,
                            id,
                            where,
                            state.origin() == NodeState.Origin.STRUCTURE
                                    ? NodeCause.STRUCTURE
                                    : state.origin() == NodeState.Origin.STARTER
                                            ? NodeCause.STARTER
                                            : NodeCause.WORLDGEN);
                }
                case ADOPT -> {
                    ledger.put(NodeLedger.Entry.placed(state.id(), state.origin(), dim, pos));
                    indexAdd(dim, state.id(), pos);
                    changed = true;
                }
                case RECOVER -> {
                    ledger.put(NodeLedger.Entry.placed(state.id(), state.origin(), dim, pos));
                    ledger.advancePast(state.id());
                    indexAdd(dim, state.id(), pos);
                    changed = true;
                    post(NodeEventKind.CREATED, state.id(), where, NodeCause.RECONCILE);
                }
                case REMOVE -> {
                    LOGGER.warn(
                            "Removing node block at {} in {} (id {}, {})",
                            pos,
                            dim.identifier(),
                            state.id(),
                            relation);
                    present.remove(pos);
                    level.removeBlock(pos, false);
                }
            }
        }
        Set<Long> ids = CHUNK_IDS.getOrDefault(dim, Map.of()).get(chunk.getPos().pack());
        if (ids != null) {
            for (long id : List.copyOf(ids)) {
                NodeLedger.Entry e = ledger.get(id);
                if (e == null || e.state() != NodeLedger.State.PLACED) {
                    continue;
                }
                BlockPos at = e.pos().orElseThrow();
                Long held = present.get(at);
                if (held == null || (held != -1L && held != id)) {
                    LOGGER.warn("Node {} at {} is a ghost: tombstoned", id, at);
                    starterChanged |= isStarter(ledger, id);
                    ledger.put(e.removed());
                    indexRemove(dim, id, at);
                    changed = true;
                    post(NodeEventKind.REMOVED, id, GlobalPos.of(dim, at), NodeCause.RECONCILE);
                }
            }
        }
        if (changed) {
            markDirty(level.getServer());
        }
        if (starterChanged) {
            VisRulesService.sendToAll(level.getServer());
        }
    }

    /**
     * The removal hook: tombstones the id only if its entry is {@code PLACED} at exactly this
     * position, then posts {@code REMOVED}.
     */
    static void onBlockRemoved(ServerLevel level, BlockPos pos, NodeBlockEntity be) {
        NodeState state = be.state();
        NodeLedger ledger = ledger(level.getServer());
        if (state == null || state.id() == 0 || ledger == null) {
            return;
        }
        NodeLedger.Entry e = ledger.get(state.id());
        if (e == null || !e.placedAt(level.dimension(), pos)) {
            return;
        }
        boolean starter = isStarter(ledger, e.id());
        ledger.put(e.removed());
        indexRemove(level.dimension(), e.id(), pos);
        markDirty(level.getServer());
        post(
                NodeEventKind.REMOVED,
                e.id(),
                GlobalPos.of(level.dimension(), pos.immutable()),
                NodeCause.PLAYER);
        if (starter) {
            VisRulesService.sendToAll(level.getServer());
        }
    }

    /** Whether a node block holds this id; an unreadable one is taken to be the entry's own. */
    private static boolean heldBy(NodeBlockEntity be, long id) {
        NodeState held = be.state();
        return held == null || held.id() == id;
    }

    private static boolean isStarter(NodeLedger ledger, long id) {
        NodeLedger.Starter s = ledger.starter();
        return s != null && s.id() == id;
    }

    /**
     * The node block entity at a position whose entry is {@code PLACED} there; anything else is
     * refused (null), and a mismatched block's chunk is queued.
     */
    public static @Nullable NodeBlockEntity placedNode(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof NodeBlockEntity be)) {
            return null;
        }
        NodeState state = be.state();
        NodeLedger ledger = ledger(level.getServer());
        if (state == null || ledger == null) {
            return null;
        }
        NodeLedger.Entry e = state.id() == 0 ? null : ledger.get(state.id());
        if (e == null || !e.placedAt(level.dimension(), pos)) {
            queue(level, ChunkPos.containing(pos));
            return null;
        }
        return be;
    }

    // --- reads and writes of one node -------------------------------------------------------

    /** Settles a node in place under the server's tuning and returns its state. */
    public static NodeState settle(ServerLevel level, NodeBlockEntity be) {
        NodeState state = java.util.Objects.requireNonNull(be.state(), "a readable node");
        NodeState settled = state.settle(level.getGameTime(), VisRulesService.tuning());
        if (!settled.equals(state)) {
            be.setState(settled);
        }
        return settled;
    }

    /** Marks a node for the coalesced sync: at most one update packet per node per tick. */
    public static void markForSync(ServerLevel level, BlockPos pos) {
        DIRTY.computeIfAbsent(level.dimension(), k -> new LinkedHashSet<>()).add(pos.immutable());
    }

    /** Sends one update for each node marked this tick; called once per level, after it ticks. */
    public static void flush(ServerLevel level) {
        Set<BlockPos> dirty = DIRTY.remove(level.dimension());
        if (dirty == null) {
            return;
        }
        for (BlockPos pos : dirty) {
            if (level.isLoaded(pos)) {
                BlockState state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** A node type's entry, or null when the type is missing. */
    public static @Nullable NodeTypeEntry type(Level level, Identifier type) {
        return WandParts.of(level.registryAccess())
                .types()
                .get(ResourceKey.create(VisKeys.NODE_TYPE_REGISTRY, type));
    }

    /**
     * {@code readout(level, pos)}: a loaded node whose entry is {@code PLACED} there, settled, as
     * the fork reads it; an inert node reads its aspects and the type "Unknown".
     */
    public static NodeSource.@Nullable Readout readout(ServerLevel level, BlockPos pos) {
        NodeBlockEntity be = placedNode(level, pos);
        if (be == null) {
            return null;
        }
        NodeState state = settle(level, be);
        NodeTypeEntry type = type(level, state.type());
        List<NodeSource.NodeAspect> aspects = new ArrayList<>();
        var registry =
                level.registryAccess()
                        .lookupOrThrow(com.andrewbrogoff.vftv.api.aspect.AspectKeys.REGISTRY);
        for (NodeState.NodeAspect a : state.aspects()) {
            var key =
                    ResourceKey.create(
                            com.andrewbrogoff.vftv.api.aspect.AspectKeys.REGISTRY, a.aspect());
            // An aspect a pack removed is kept in the node's state but ignored (Pack changes).
            if (registry.get(key).isPresent()) {
                aspects.add(new NodeSource.NodeAspect(key, a.current(), a.max()));
            }
        }
        return new NodeSource.Readout(
                state.id(),
                type == null
                        ? Component.translatable(VisLang.UNKNOWN_TYPE)
                        : typeName(state.type()),
                Component.translatable(state.clarity().langKey()),
                aspects,
                type == null || type.rewardDivisor().isEmpty()
                        ? OptionalInt.empty()
                        : OptionalInt.of(type.rewardDivisor().get()));
    }

    /** A type's name, {@code node_type.<ns>.<path>}. */
    public static Component typeName(Identifier type) {
        return Component.translatable("node_type." + type.getNamespace() + "." + type.getPath());
    }

    /**
     * {@code nearest(dimension, point, r)}: the nearest placed node within {@code r}; memory only.
     */
    public static NodeSource.@Nullable Nearest nearest(ServerLevel level, Vec3 point, int r) {
        NodeIndex index = INDEX.get(level.dimension());
        if (index == null) {
            return null;
        }
        long id = index.nearest(point.x, point.y, point.z, r);
        if (id < 0) {
            return null;
        }
        int[] at = index.position(id);
        return new NodeSource.Nearest(id, new Vec3(at[0] + 0.5, at[1] + 0.5, at[2] + 0.5));
    }

    /** The fork's view of the nodes. */
    public static final NodeSource SOURCE =
            new NodeSource() {
                @Override
                public NodeSource.@Nullable Readout readout(ServerLevel level, BlockPos pos) {
                    return NodeService.readout(level, pos);
                }

                @Override
                public NodeSource.@Nullable Nearest nearest(
                        ServerLevel level, Vec3 point, int radius) {
                    return NodeService.nearest(level, point, radius);
                }

                @Override
                public boolean starterMissing(MinecraftServer server) {
                    return NodeService.starterMissing(server);
                }
            };

    /**
     * {@code remove(id, cause)}: tombstones an id and posts {@code REMOVED} once. A loaded node's
     * block goes at once; an unloaded one's at its chunk's next reconciliation.
     */
    public static RemoveResult remove(MinecraftServer server, long id, NodeCause cause) {
        NodeLedger ledger = ledger(server);
        if (ledger == null) {
            return RemoveResult.UNREADABLE;
        }
        NodeLedger.Entry e = ledger.get(id);
        if (e == null || e.state() == NodeLedger.State.REMOVED) {
            return RemoveResult.UNKNOWN;
        }
        boolean starter = isStarter(ledger, id);
        ledger.put(e.removed());
        GlobalPos where = null;
        if (e.state() == NodeLedger.State.PLACED) {
            ResourceKey<Level> dim = e.dimension().orElseThrow();
            BlockPos pos = e.pos().orElseThrow();
            where = GlobalPos.of(dim, pos);
            indexRemove(dim, id, pos);
            ServerLevel level = server.getLevel(dim);
            if (level != null
                    && level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof NodeBlockEntity be
                    && heldBy(be, id)) {
                level.removeBlock(pos, false);
            }
        }
        markDirty(server);
        post(NodeEventKind.REMOVED, id, where, cause);
        if (starter) {
            VisRulesService.sendToAll(server);
        }
        return RemoveResult.REMOVED;
    }

    /**
     * {@code update(id, change, cause, boundary?)}: loaded nodes only. Validates, settles, writes
     * and marks for sync, calls the boundary once, then posts {@code CHANGED}.
     */
    public static UpdateResult update(
            MinecraftServer server,
            long id,
            NodeChange change,
            NodeCause cause,
            @Nullable BiConsumer<NodeState, NodeState> boundary) {
        NodeLedger ledger = ledger(server);
        NodeLedger.Entry e = ledger == null ? null : ledger.get(id);
        if (e == null || e.state() != NodeLedger.State.PLACED) {
            return UpdateResult.NOT_LOADED;
        }
        ServerLevel level = server.getLevel(e.dimension().orElseThrow());
        BlockPos pos = e.pos().orElseThrow();
        if (level == null || !level.isLoaded(pos)) {
            return UpdateResult.NOT_LOADED;
        }
        NodeBlockEntity be = placedNode(level, pos);
        if (be == null || be.state() == null) {
            return UpdateResult.NOT_LOADED;
        }
        if (change.type().isPresent() && type(level, change.type().get().identifier()) == null) {
            return UpdateResult.INVALID;
        }
        if (change.aspects().isPresent()) {
            var aspects = change.aspects().get();
            if (aspects.isEmpty()
                    || aspects.size() > VisLimits.MAX_NODE_ASPECTS
                    || aspects.values().stream().anyMatch(m -> m < 1 || m > VisLimits.MAX_AMOUNT)) {
                return UpdateResult.INVALID;
            }
        }
        NodeState before = settle(level, be);
        Map<String, NodeState.NodeAspect> old = before.byAspect();
        List<NodeState.NodeAspect> aspects = before.aspects();
        NodeState.Cultivation cultivation = before.cultivation();
        if (change.aspects().isPresent()) {
            aspects = new ArrayList<>();
            SortedMap<Identifier, Integer> counters = new TreeMap<>();
            for (var a : change.aspects().get().entrySet()) {
                Identifier aid = a.getKey().identifier();
                NodeState.NodeAspect prior = old.get(aid.toString());
                int max = a.getValue();
                aspects.add(
                        new NodeState.NodeAspect(
                                aid, max, prior == null ? 0 : Math.min(prior.current(), max)));
                Integer counter = before.cultivation().counters().get(aid);
                if (counter != null) {
                    counters.put(aid, counter);
                }
            }
            aspects.sort((x, y) -> x.aspect().toString().compareTo(y.aspect().toString()));
            cultivation = new NodeState.Cultivation(counters, before.cultivation().sonus());
        }
        NodeState after =
                new NodeState(
                                before.id(),
                                before.origin(),
                                change.type().map(ResourceKey::identifier).orElse(before.type()),
                                change.clarity().orElse(before.clarity()),
                                aspects,
                                before.regen(),
                                cultivation)
                        .settle(level.getGameTime(), VisRulesService.tuning());
        be.setState(after);
        markForSync(level, pos);
        boolean ok = true;
        if (boundary != null) {
            try {
                boundary.accept(before, after);
            } catch (RuntimeException ex) {
                LOGGER.error("A node update boundary threw for node {}; the write stands", id, ex);
                ok = false;
            }
        }
        ok &=
                VisEvents.node(
                        new NodePayload(
                                NodeEventKind.CHANGED,
                                new NodeRef(id, GlobalPos.of(level.dimension(), pos)),
                                cause));
        return ok ? UpdateResult.UPDATED : UpdateResult.COMMITTED_NOTIFY_FAILED;
    }

    // --- the starter --------------------------------------------------------------------------

    /** Whether the cell of {@code pos} admits a starter and no node is within the spacing. */
    static boolean admitsStarter(ServerLevel level, BlockPos pos) {
        NodeIndex index = index(level.dimension());
        double spacing = VisRulesService.tuning().minSpacing();
        return index.count(pos.getX(), pos.getZ()) < VisLimits.MAX_NODES_PER_CELL
                && index.closest(
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5,
                                (int) Math.ceil(spacing))
                        > spacing;
    }

    /** The world as the ring search reads it; placement may load and generate chunks. */
    static StarterRing.Terrain terrain(ServerLevel level) {
        return new StarterRing.Terrain() {
            @Override
            public boolean insideBorder(int x, int z) {
                return level.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z));
            }

            @Override
            public int surface(int x, int z) {
                return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            }

            @Override
            public int minY() {
                return level.getMinY();
            }

            @Override
            public int maxY() {
                return level.getMaxY();
            }

            @Override
            public boolean isAir(int x, int y, int z) {
                return level.getBlockState(new BlockPos(x, y, z)).isAir();
            }

            @Override
            public boolean sturdyDry(int x, int y, int z) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                return s.isFaceSturdy(level, p, Direction.UP) && s.getFluidState().isEmpty();
            }

            @Override
            public boolean openDry(int x, int y, int z) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(p);
                return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
            }

            @Override
            public boolean admits(int x, int y, int z) {
                return admitsStarter(level, new BlockPos(x, y, z));
            }
        };
    }

    /** The effective spawn-protection radius for a non-op player (wands spec, "The starter"). */
    static int effectiveProtection(MinecraftServer server) {
        int radius = server instanceof DedicatedServer d ? d.spawnProtectionRadius() : 0;
        return StarterRing.effectiveRadius(
                server.isDedicatedServer(),
                Level.OVERWORLD.equals(server.getRespawnData().dimension()),
                !server.getPlayerList().getOps().isEmpty(),
                radius);
    }

    /**
     * The starter at server start (wands spec, "The starter"): whenever {@code starter} is absent
     * or {@code REMOVED}, the ring search places the Wellspring; with no candidate, {@code
     * best_effort} warns, {@code required} throws, {@code off} does nothing.
     *
     * @throws IllegalStateException in {@code required} mode with no candidate
     */
    public static void onServerStarted(MinecraftServer server) {
        rebuild(server);
        placeStarter(server, effectiveProtection(server));
    }

    /** The starter search with a given effective protection radius (GameTests set it). */
    public static boolean placeStarter(MinecraftServer server, int protection) {
        VisConfig.StarterMode mode = VisRulesService.config().starter();
        NodeLedger ledger = ledger(server);
        if (mode == VisConfig.StarterMode.OFF || ledger == null || !ledger.starterMissing()) {
            return false;
        }
        ServerLevel overworld = server.overworld();
        BlockPos boot = server.getRespawnData().pos();
        VisTuning t = VisRulesService.tuning();
        int first = StarterRing.firstRing(t.integer("starter.min_ring"), protection);
        int[] found =
                StarterRing.search(
                        terrain(overworld),
                        overworld.getSeed(),
                        boot.getX(),
                        boot.getY(),
                        boot.getZ(),
                        first,
                        t.integer("starter.ring"),
                        t.integer("starter.height"));
        if (found != null) {
            birthStarter(overworld, new BlockPos(found[0], found[1], found[2]), NodeCause.STARTER);
            return true;
        }
        int configured = server instanceof DedicatedServer d ? d.spawnProtectionRadius() : 0;
        String radii =
                "configured spawn-protection "
                        + configured
                        + ", effective "
                        + protection
                        + ", bootstrap "
                        + boot.toShortString();
        if (mode == VisConfig.StarterMode.REQUIRED) {
            throw new IllegalStateException(
                    "No Wellspring could be placed near spawn ("
                            + radii
                            + "). Remedies: lower spawn-protection, move the world spawn, or set"
                            + " nodes.starter to best_effort or off in "
                            + configFile(server)
                            + ".");
        }
        LOGGER.warn(
                "No Wellspring could be placed near spawn ({}); retrying at the next start", radii);
        return false;
    }

    /** The active server config file's path. */
    static String configFile(MinecraftServer server) {
        Path world =
                server.getWorldPath(LevelResource.ROOT).resolve("serverconfig/vftv-server.toml");
        return Files.exists(world) ? world.normalize().toString() : "config/vftv-server.toml";
    }

    /**
     * One birth of the Wellspring at {@code pos}: the id minted, the block and state set, the entry
     * recorded {@code PLACED}, the starter and the bootstrap recorded, indexed, marked dirty and
     * the block update sent; only then {@code NodeEvent(CREATED)}, and the rules re-sent.
     */
    static long birthStarter(ServerLevel overworld, BlockPos pos, NodeCause cause) {
        MinecraftServer server = overworld.getServer();
        NodeLedger ledger = java.util.Objects.requireNonNull(ledger(server), "a readable registry");
        NodeTypeEntry wellspring =
                java.util.Objects.requireNonNull(
                        type(overworld, NodeTypeKeys.WELLSPRING.identifier()), "the Wellspring");
        SortedMap<String, Integer> maxes = new TreeMap<>();
        wellspring.aspects().forEach(a -> maxes.put(a.aspect().identifier().toString(), a.min()));
        long id = ledger.mint();
        NodeState state =
                NodeState.born(
                        id,
                        NodeState.Origin.STARTER,
                        NodeTypeKeys.WELLSPRING.identifier(),
                        NodeState.Clarity.CLEAR,
                        maxes,
                        overworld.getGameTime(),
                        VisRulesService.tuning());
        overworld.setBlock(pos, VisContent.node().defaultBlockState(), Block.UPDATE_CLIENTS);
        if (overworld.getBlockEntity(pos) instanceof NodeBlockEntity be) {
            be.setState(state);
        }
        ledger.put(
                NodeLedger.Entry.placed(id, NodeState.Origin.STARTER, overworld.dimension(), pos));
        ledger.starter(new NodeLedger.Starter(id, server.getRespawnData().pos()));
        indexAdd(overworld.dimension(), id, pos);
        markDirty(server);
        BlockState placed = overworld.getBlockState(pos);
        overworld.sendBlockUpdated(pos, placed, placed, Block.UPDATE_CLIENTS);
        post(
                NodeEventKind.CREATED,
                id,
                GlobalPos.of(overworld.dimension(), pos.immutable()),
                cause);
        VisRulesService.sendToAll(server);
        return id;
    }

    /**
     * The join notice: the first player with permission level 2 or more to join while the starter
     * is missing, in {@code best_effort}, gets one status line, once per server run.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        if (noticeSent
                || VisRulesService.config().starter() != VisConfig.StarterMode.BEST_EFFORT
                || !Commands.LEVEL_GAMEMASTERS.check(player.permissions())
                || !starterMissing(player.level().getServer())) {
            return;
        }
        noticeSent = true;
        StatusLines.send(player, Component.translatable(STARTER_MISSING));
    }
}
