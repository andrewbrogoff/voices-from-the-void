package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.hush.HushChange;
import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.api.hush.HushOrigin;
import com.andrewbrogoff.vftv.api.hush.HushSpreadKind;
import com.andrewbrogoff.vftv.api.hush.HushSpreadPayload;
import com.andrewbrogoff.vftv.api.vis.NodeCause;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.common.OperationGuard;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.common.SavedDataSlot;
import com.andrewbrogoff.vftv.core.common.TickBudget;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.hush.model.HushMath;
import com.andrewbrogoff.vftv.core.hush.model.Walk;
import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.NodeService;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.vis.NodeTypeEntry;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.warp.WarpService;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Hush (Hush spec, "The Hush", "Hollows", "Hushed nodes", "Protection and multiplayer"): the
 * per-level due queue and its budgeted service, one attempt's walk, every Hush mutation as a
 * guarded operation, and the restores the bells and the command share. Server thread only.
 */
public final class HushService {
    /** The Hush's guards: the level's, a node id's, a player's and a bell's. */
    static final OperationGuard GUARD = new OperationGuard("hush");

    /** The overworld's {@code vftv_hushed_nodes}. */
    public static final SavedDataSlot<Retained<HushRecords.Journal>> JOURNAL =
            new SavedDataSlot<>(
                    Identifier.fromNamespaceAndPath("vftv", "hushed_nodes"),
                    RawRecordCodec.wrap(HushRecords.JOURNAL),
                    () -> new Retained.Read<>(HushRecords.Journal.EMPTY));

    /** The swap's flags: {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SKIP_ON_PLACE}. */
    public static final int SWAP_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Cells CELLS = new Cells();
    private static @Nullable Network network;
    private static boolean journalWarned;
    private static boolean missingTypeWarned;

    private HushService() {}

    /**
     * Takes the network seam the chunk statuses are sent through; once, from the feature's init.
     */
    static void install(Network seam) {
        network = seam;
    }

    /**
     * {@code ChunkWatchEvent.Sent}: the chunk's status, for every chunk, one with no aura included.
     */
    public static void onWatch(ServerPlayer player, LevelChunk chunk) {
        Network seam = network;
        if (seam != null) {
            seam.toPlayer(player, new HushChunkStatus(chunk.getPos().pack(), readout(chunk)));
        }
    }

    static FluxRules rules() {
        return FluxRulesService.current();
    }

    // --- scheduling --------------------------------------------------------------------------

    /** Whether a chunk's aura makes it a candidate: readable, seeped, at the threshold, awake. */
    static boolean candidate(HushLevel state, long pack, Aura aura, FluxRules r) {
        return aura.readable()
                && aura.hasSeep
                && aura.flux >= HushMath.threshold(r.hushOnset(), r.hushBlockCost())
                && !state.saturated.contains(pack);
    }

    /**
     * Offers a candidate to the due queue (a queued chunk keeps its due); a chunk that stopped
     * being one leaves.
     */
    static void wake(ServerLevel level, HushLevel state, long pack, Aura aura) {
        FluxRules r = rules();
        if (candidate(state, pack, aura, r)) {
            state.due.offer(pack, due(level.getGameTime(), pack, r));
        } else {
            state.due.remove(pack);
        }
    }

    private static long due(long g, long pack, FluxRules r) {
        return HushMath.nextDue(g, r.hushInterval(), HushMath.chunkPhase(pack, r.hushInterval()));
    }

    /**
     * A tuning {@code /reload}: every due recomputed from the new phases, every saturation cleared,
     * and every loaded candidate queued again; the bells likewise.
     */
    public static void onTuningReload(MinecraftServer server) {
        FluxRules r = rules();
        for (ServerLevel level : server.getAllLevels()) {
            HushLevel state = FluxService.state(level);
            long g = level.getGameTime();
            List<Long> queued = state.due.keys();
            queued.addAll(state.saturated);
            state.saturated.clear();
            state.misses.clear();
            state.due.clear();
            for (long pack : queued) {
                LevelChunk chunk = FluxService.chunkNow(level, pack);
                Aura aura = chunk == null ? null : FluxService.aura(chunk);
                if (aura != null && candidate(state, pack, aura, r)) {
                    state.due.put(pack, due(g, pack, r));
                }
            }
            HushBells.rebuild(level, state, r);
        }
    }

    /** Whether a non-spectator player stands within {@code hush.player_chunks} of the chunk. */
    public static boolean attended(
            ServerLevel level, long pack, FluxRules r, boolean silencedCount) {
        if (r.unattended()) {
            return true;
        }
        int cx = ChunkPos.getX(pack);
        int cz = ChunkPos.getZ(pack);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || (!silencedCount && WarpService.isSilenced(p))) {
                continue;
            }
            ChunkPos at = p.chunkPosition();
            if (Math.max(Math.abs(at.x() - cx), Math.abs(at.z() - cz)) <= r.hushPlayerChunks()) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code LevelTickEvent.Post} on a server level: the spread's service, the bells' and the
     * coalesced aura sync.
     */
    public static void tick(ServerLevel level) {
        try (var _ = TickBudget.enter("hush")) {
            serve(level);
            HushBells.serve(level);
            FluxService.flush(level);
        }
    }

    /**
     * One attempt from a loaded chunk's seep now, outside the queue (the harness and tests): what
     * the service's visit does.
     *
     * @return the spread mutations made, or -1 when the chunk has no readable seeped aura
     */
    public static int attempt(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = FluxService.chunkNow(level, chunkPos.pack());
        Aura aura = chunk == null ? null : FluxService.aura(chunk);
        if (chunk == null || aura == null || !aura.readable() || !aura.hasSeep) {
            return -1;
        }
        FluxRules r = rules();
        return visit(
                level,
                FluxService.state(level),
                chunk,
                aura,
                r,
                HushMath.hollowTurn(aura.taken, r.hollowEvery()));
    }

    /**
     * One spread operation at a chosen target from the chunk's seep, skipping the walk (tests): a
     * block conversion, followed by a rise on a Hollow turn, or a node hushed.
     *
     * @return the spread mutations made
     */
    public static int spreadTo(ServerLevel level, ChunkPos chunkPos, BlockPos target) {
        LevelChunk chunk = FluxService.chunkNow(level, chunkPos.pack());
        Aura aura = chunk == null ? null : FluxService.aura(chunk);
        if (chunk == null || aura == null || !aura.readable() || !aura.hasSeep) {
            return 0;
        }
        FluxRules r = rules();
        HushOrigin origin = new HushOrigin.Seep(chunkPos, aura.spiller);
        if (level.getBlockState(target).is(VisContent.node())) {
            return hushNode(level, chunk, aura, target, r, origin) ? 1 : 0;
        }
        boolean turn = HushMath.hollowTurn(aura.taken, r.hollowEvery());
        if (!convert(level, chunk, aura, target, r, origin)) {
            return 0;
        }
        return turn && rise(level, chunk, aura, target, r, origin) ? 2 : 1;
    }

    /** Whether a chunk is in the due queue. */
    public static boolean queued(ServerLevel level, ChunkPos chunk) {
        return FluxService.state(level).due.contains(chunk.pack());
    }

    /** The spread's service, in {@code (due, pack)} order under the per-tick caps. */
    public static void serve(ServerLevel level) {
        FluxRules r = rules();
        if (!r.spread()) {
            return;
        }
        HushLevel state = FluxService.state(level);
        long g = level.getGameTime();
        int pops = 0;
        int visits = 0;
        int slots = HushLimits.MAX_SPREAD_MUTATIONS_PER_TICK;
        while (pops < HushLimits.MAX_POPS_PER_TICK && visits < HushLimits.MAX_VISITS_PER_TICK) {
            var e = state.due.first();
            if (e == null || e.due() > g) {
                return;
            }
            long pack = e.key();
            LevelChunk chunk = FluxService.chunkNow(level, pack);
            Aura aura = chunk == null ? null : FluxService.aura(chunk);
            if (chunk == null || aura == null || !candidate(state, pack, aura, r)) {
                state.due.remove(pack);
                pops++;
                continue;
            }
            if (!attended(level, pack, r, false)) {
                state.due.put(pack, due(g, pack, r));
                pops++;
                continue;
            }
            boolean turn = HushMath.hollowTurn(aura.taken, r.hollowEvery());
            if (slots < (turn ? 2 : 1)) {
                return; // fair carry: it stays due and is served first next tick
            }
            pops++;
            visits++;
            slots -= visit(level, state, chunk, aura, r, turn);
            if (candidate(state, pack, aura, r)) {
                state.due.put(pack, due(g, pack, r));
            } else {
                state.due.remove(pack);
            }
        }
    }

    // --- an attempt --------------------------------------------------------------------------

    /** The walk's reads: {@code getChunkNow} and the chunk's own block read, never loading. */
    private static final class Cells implements Walk.Cells {
        private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        private @Nullable ServerLevel level;

        @Override
        public int classify(int x, int y, int z) {
            ServerLevel l = java.util.Objects.requireNonNull(level);
            LevelChunk chunk = ticking(l, x >> 4, z >> 4);
            if (chunk == null) {
                return Walk.UNLOADED;
            }
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (HushedForms.isSource(state) && !state.is(HushKeys.HUSH_IMMUNE)) {
                return Walk.SOURCE;
            }
            if (state.is(VisContent.node()) && hushableNode(l, pos) != null) {
                return Walk.NODE;
            }
            return Walk.PASS;
        }
    }

    /** The chunk at chunk coordinates if loaded and at least block-ticking, else null. */
    static @Nullable LevelChunk ticking(ServerLevel level, int cx, int cz) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        return chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                ? null
                : chunk;
    }

    static @Nullable LevelChunk ticking(ServerLevel level, BlockPos pos) {
        return ticking(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * One attempt from the chunk's seep: {@code steps + 1} whatever the outcome, then the target's
     * operation, or a miss.
     *
     * @return the spread mutations it made, 0 to 2
     */
    static int visit(
            ServerLevel level,
            HushLevel state,
            LevelChunk chunk,
            Aura aura,
            FluxRules r,
            boolean turn) {
        long pack = chunk.getPos().pack();
        BlockPos seep = java.util.Objects.requireNonNull(aura.seep());
        long seed = HushMath.attemptSeed(level.getServer().overworld().getSeed(), pack, aura.steps);
        aura.steps++;
        FluxService.dirty(level, chunk);
        CELLS.level = level;
        long out;
        try {
            out =
                    Walk.attempt(
                            CELLS,
                            seed,
                            seep.getX(),
                            seep.getY(),
                            seep.getZ(),
                            r.hushRadius(),
                            r.hushWalk(),
                            level.getMinY(),
                            level.getMaxY() + 1,
                            aura.flux >= r.hushBlockCost(),
                            aura.flux >= r.hushNodeCost());
        } finally {
            CELLS.level = null;
        }
        BlockPos target = seep.offset(Walk.dx(out), Walk.dy(out), Walk.dz(out));
        HushOrigin origin = new HushOrigin.Seep(chunk.getPos(), aura.spiller);
        int made = 0;
        switch (Walk.kind(out)) {
            case Walk.BLOCK -> {
                if (convert(level, chunk, aura, target, r, origin)) {
                    made = 1;
                    if (turn && rise(level, chunk, aura, target, r, origin)) {
                        made = 2;
                    }
                }
            }
            case Walk.NODE_TARGET -> {
                if (hushNode(level, chunk, aura, target, r, origin)) {
                    made = 1;
                }
            }
            default -> {}
        }
        if (made == 0) {
            int misses = state.misses.merge(pack, 1, Integer::sum);
            if (misses >= r.hushMaxMisses()) {
                state.saturated.add(pack);
                state.misses.remove(pack);
                state.due.remove(pack); // saturated: it leaves the queue until an exit
            }
        } else {
            state.misses.remove(pack);
        }
        return made;
    }

    /** The seep's aura is the same record, still readable, and holds {@code cost}. */
    @SuppressWarnings("ReferenceEquality") // instance identity is the revalidation rule
    private static boolean pays(ServerLevel level, LevelChunk seepChunk, Aura aura, int cost) {
        return FluxService.chunkNow(level, seepChunk.getPos().pack()) == seepChunk
                && FluxService.aura(seepChunk) == aura
                && aura.readable()
                && aura.flux >= cost;
    }

    /** A block conversion, as one guarded operation. */
    @SuppressWarnings("ReferenceEquality") // instance identity is the revalidation rule
    static boolean convert(
            ServerLevel level,
            LevelChunk seepChunk,
            Aura aura,
            BlockPos pos,
            FluxRules r,
            HushOrigin origin) {
        try (var scope = GUARD.tryEnter(level.dimension())) {
            if (scope == null) {
                return false; // re-entry with no player: a miss
            }
            LevelChunk chunk = ticking(level, pos);
            if (chunk == null) {
                return false;
            }
            BlockState before = chunk.getBlockState(pos);
            BlockState after = HushedForms.hushed(before);
            if (after == null || before.is(HushKeys.HUSH_IMMUNE)) {
                return false;
            }
            if (!FluxService.platform()
                    .spread(
                            new HushSpreadPayload(
                                    HushSpreadKind.CONVERT,
                                    level,
                                    pos,
                                    new HushChange.Block(before, after),
                                    origin))) {
                return false;
            }
            chunk = ticking(level, pos);
            if (chunk == null
                    || chunk.getBlockState(pos) != before
                    || !pays(level, seepChunk, aura, r.hushBlockCost())) {
                return false;
            }
            if (!swap(level, pos, after)) {
                return false;
            }
            aura.flux -= r.hushBlockCost();
            aura.taken++;
            FluxService.dirty(level, seepChunk);
            return true;
        }
    }

    /** The swap, checked: {@code setBlock} returned true and the cell holds exactly the state. */
    @SuppressWarnings("ReferenceEquality") // instance identity is the revalidation rule
    public static boolean swap(ServerLevel level, BlockPos pos, BlockState state) {
        return level.setBlock(pos, state, SWAP_FLAGS) && level.getBlockState(pos) == state;
    }

    // --- Hollows -----------------------------------------------------------------------------

    /** Rise rules 1 to 4, read before and again after the event. */
    private static boolean mayRise(
            ServerLevel level, BlockPos seep, Aura aura, BlockPos c, FluxRules r) {
        if (!r.hollows() || level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (hollowsNear(level, seep, r) >= r.hollowMaxNear()) {
            return false;
        }
        if (aura.flux < r.hollowFlux()) {
            return false;
        }
        LevelChunk ground = ticking(level, c);
        if (ground == null || !ground.getBlockState(c).isFaceSturdy(level, c, Direction.UP)) {
            return false;
        }
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos p = c.above(dy);
            LevelChunk chunk = ticking(level, p);
            if (chunk == null
                    || level.isOutsideBuildHeight(p)
                    || !level.getWorldBorder().isWithinBounds(p)
                    || !chunk.getBlockState(p).isAir()) {
                return false;
            }
        }
        double x = c.getX() + 0.5;
        double y = c.getY() + 1;
        double z = c.getZ() + 0.5;
        return level.noCollision(HushContent.hollow().getSpawnAABB(x, y, z))
                && !playerNear(level, x, y, z, r.hollowMinPlayerDistance());
    }

    /** Loaded, living Hollows within {@code hush.radius + 8} of the seep: one AABB query. */
    static int hollowsNear(ServerLevel level, BlockPos seep, FluxRules r) {
        double reach = r.hushRadius() + 8;
        return level.getEntitiesOfClass(
                        Hollow.class,
                        new AABB(seep).inflate(reach),
                        h ->
                                h.isAlive()
                                        && h.distanceToSqr(
                                                        net.minecraft.world.phys.Vec3.atCenterOf(
                                                                seep))
                                                <= reach * reach)
                .size();
    }

    private static boolean playerNear(ServerLevel level, double x, double y, double z, int d) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) < (double) d * d) {
                return true;
            }
        }
        return false;
    }

    /** One rise at the converted cell {@code c}; first failure ends it with nothing spent. */
    @SuppressWarnings("ReferenceEquality") // instance identity is the revalidation rule
    static boolean rise(
            ServerLevel level,
            LevelChunk seepChunk,
            Aura aura,
            BlockPos c,
            FluxRules r,
            HushOrigin origin) {
        BlockPos seep = java.util.Objects.requireNonNull(aura.seep());
        try (var scope = GUARD.tryEnter(level.dimension())) {
            if (scope == null || !mayRise(level, seep, aura, c, r)) {
                return false;
            }
            if (!FluxService.platform()
                            .spread(
                                    new HushSpreadPayload(
                                            HushSpreadKind.RISE,
                                            level,
                                            c.above(),
                                            new HushChange.Rise(HushContent.hollow()),
                                            origin))
                    || !pays(level, seepChunk, aura, r.hollowFlux())
                    || !mayRise(level, seep, aura, c, r)) {
                return false;
            }
            Hollow hollow = HushContent.hollow().create(level, EntitySpawnReason.EVENT);
            if (hollow == null) {
                return false;
            }
            double x = c.getX() + 0.5;
            double y = c.getY() + 1;
            double z = c.getZ() + 0.5;
            hollow.snapTo(x, y, z, level.getRandom().nextFloat() * 360F, 0F);
            hollow.setHomeTo(seep, r.hushRadius() + 4);
            hollow.setPersistenceRequired();
            hollow.startRise();
            boolean added = level.addFreshEntity(hollow);
            boolean counts =
                    added
                            && hollow.isAlive()
                            && hollow.level() == level
                            && hollow.blockPosition().equals(c.above())
                            && level.noCollision(hollow, hollow.getBoundingBox())
                            && !playerNear(
                                    level,
                                    hollow.getX(),
                                    hollow.getY(),
                                    hollow.getZ(),
                                    r.hollowMinPlayerDistance())
                            && hollowsNear(level, seep, r) <= r.hollowMaxNear()
                            && pays(level, seepChunk, aura, r.hollowFlux());
            if (!counts) {
                hollow.discard();
                return false;
            }
            aura.flux -= r.hollowFlux();
            FluxService.dirty(level, seepChunk);
            level.playSound(
                    null,
                    hollow,
                    HushContent.sound(HushContent.HOLLOW_RISE),
                    SoundSource.HOSTILE,
                    0.8F,
                    1.0F);
            return true;
        }
    }

    // --- hushed nodes ------------------------------------------------------------------------

    /**
     * The node at {@code pos} if it is hushable: placed there, readable, its type present, not
     * anchored and not already {@code vftv:hushed}.
     */
    static @Nullable NodeBlockEntity hushableNode(ServerLevel level, BlockPos pos) {
        NodeBlockEntity be = NodeService.placedNode(level, pos);
        NodeState state = be == null ? null : be.state();
        if (state == null || state.type().equals(HushKeys.HUSHED_NODE.identifier())) {
            return null;
        }
        NodeTypeEntry type = NodeService.type(level, state.type());
        return type == null || type.anchored() ? null : be;
    }

    static HushRecords.@Nullable Journal journal(MinecraftServer server) {
        if (JOURNAL.get(server.overworld()) instanceof Retained.Read<HushRecords.Journal> r) {
            return r.value();
        }
        if (!journalWarned && JOURNAL.get(server.overworld()) instanceof Retained.Unreadable<?> u) {
            journalWarned = true;
            LOGGER.warn(
                    "vftv_hushed_nodes is unreadable ({}); no node is hushed or restored",
                    u.reason());
        }
        return null;
    }

    private static void putJournal(MinecraftServer server, Map<Long, HushRecords.Original> nodes) {
        JOURNAL.set(
                server.overworld(),
                new Retained.Read<>(new HushRecords.Journal(HushRecords.FORMAT, nodes)));
    }

    private static ResourceKey<NodeType> typeKey(Identifier id) {
        return ResourceKey.create(VisKeys.NODE_TYPE_REGISTRY, id);
    }

    /**
     * Hushing a node, under the level's and the node's guards; the journal written at the boundary.
     */
    @SuppressWarnings("ReferenceEquality") // instance identity is the revalidation rule
    static boolean hushNode(
            ServerLevel level,
            LevelChunk seepChunk,
            Aura aura,
            BlockPos pos,
            FluxRules r,
            HushOrigin origin) {
        MinecraftServer server = level.getServer();
        HushRecords.Journal journal = journal(server);
        if (journal == null || journal.nodes().size() >= HushLimits.MAX_HUSHED_NODES) {
            return false;
        }
        NodeBlockEntity be = hushableNode(level, pos);
        NodeState state = be == null ? null : be.state();
        if (be == null || state == null) {
            return false;
        }
        long id = state.id();
        try (var scope = GUARD.tryEnter(level.dimension());
                var node = GUARD.tryEnter(id)) {
            if (scope == null || node == null) {
                return false;
            }
            if (!FluxService.platform()
                    .spread(
                            new HushSpreadPayload(
                                    HushSpreadKind.NODE,
                                    level,
                                    pos,
                                    new HushChange.Node(
                                            id, typeKey(state.type()), HushKeys.HUSHED_NODE),
                                    origin))) {
                return false;
            }
            NodeBlockEntity again = hushableNode(level, pos);
            if (again != be
                    || !state.equals(be.state())
                    || !pays(level, seepChunk, aura, r.hushNodeCost())) {
                return false;
            }
            HushRecords.Original original =
                    new HushRecords.Original(state.type(), state.clarity().getSerializedName());
            boolean[] settled = new boolean[1];
            NodeService.UpdateResult result =
                    NodeService.update(
                            server,
                            id,
                            new NodeService.NodeChange(
                                    Optional.of(HushKeys.HUSHED_NODE),
                                    Optional.empty(),
                                    Optional.of(NodeState.Clarity.FAINT)),
                            NodeCause.API,
                            (b, a) -> {
                                // the live store: a listener may have changed it during the event
                                Map<Long, HushRecords.Original> nodes =
                                        new TreeMap<>(
                                                java.util.Objects.requireNonNullElse(
                                                                journal(server), journal)
                                                        .nodes());
                                nodes.put(id, original);
                                putJournal(server, nodes);
                                aura.flux -= r.hushNodeCost();
                                FluxService.dirty(level, seepChunk);
                                settled[0] = true;
                            });
            return settled[0]
                    && (result == NodeService.UpdateResult.UPDATED
                            || result == NodeService.UpdateResult.COMMITTED_NOTIFY_FAILED);
        }
    }

    /** {@code NodeEvent(REMOVED)}: the node's journal entry goes. */
    public static void onNodeRemoved(MinecraftServer server, long id) {
        HushRecords.Journal journal = journal(server);
        if (journal != null && journal.nodes().containsKey(id)) {
            Map<Long, HushRecords.Original> nodes = new TreeMap<>(journal.nodes());
            nodes.remove(id);
            putJournal(server, nodes);
        }
    }

    // --- restores ----------------------------------------------------------------------------

    /**
     * Who restores: a Clearing Bell's player (claims through {@code mayInteract} and a break
     * event), a Pealing Bell (its {@code HushSpreadEvent(RESTORE)}, no actor) or an operator (no
     * claim events).
     *
     * @param player the ringing player, or null
     * @param bell the pealing bell's position, or null
     */
    record Restorer(@Nullable ServerPlayer player, @Nullable BlockPos bell) {
        static final Restorer COMMAND = new Restorer(null, null);

        boolean authorise(ServerLevel level, BlockPos pos, BlockState state, HushChange change) {
            if (player != null) {
                return level.mayInteract(player, pos)
                        && FluxService.platform().mayBreak(player, level, pos, state);
            }
            if (bell != null) {
                return FluxService.platform()
                        .spread(
                                new HushSpreadPayload(
                                        HushSpreadKind.RESTORE,
                                        level,
                                        pos,
                                        change,
                                        new HushOrigin.Bell(bell)));
            }
            return true;
        }
    }

    /**
     * Restores a hushed block at {@code pos}, in a loaded block-ticking chunk, under the caller's
     * level guard (trusted nesting).
     */
    @SuppressWarnings("ReferenceEquality") // instance identity is the revalidation rule
    static boolean restoreBlock(ServerLevel level, BlockPos pos, Restorer who) {
        LevelChunk chunk = ticking(level, pos);
        if (chunk == null) {
            return false;
        }
        BlockState before = chunk.getBlockState(pos);
        BlockState after = HushedForms.restored(before);
        if (after == null
                || !who.authorise(level, pos, before, new HushChange.Block(before, after))) {
            return false;
        }
        chunk = ticking(level, pos);
        return chunk != null && chunk.getBlockState(pos) == before && swap(level, pos, after);
    }

    /** Whether the cell holds a node with a journal entry (a restore candidate). */
    static boolean journaled(ServerLevel level, BlockPos pos) {
        HushRecords.Journal journal = journal(level.getServer());
        if (journal == null || !level.getBlockState(pos).is(VisContent.node())) {
            return false;
        }
        NodeBlockEntity be = NodeService.placedNode(level, pos);
        NodeState state = be == null ? null : be.state();
        return state != null && journal.nodes().containsKey(state.id());
    }

    /**
     * Restores a journaled node at {@code pos}, under the caller's level guard: a stale entry is
     * deleted without overwriting the later type; a missing original type leaves it hushed.
     */
    @SuppressWarnings("ReferenceEquality") // instance identity is the revalidation rule
    static boolean restoreNode(ServerLevel level, BlockPos pos, Restorer who) {
        MinecraftServer server = level.getServer();
        HushRecords.Journal journal = journal(server);
        NodeBlockEntity be = NodeService.placedNode(level, pos);
        NodeState state = be == null ? null : be.state();
        if (journal == null || state == null) {
            return false;
        }
        long id = state.id();
        HushRecords.Original original = journal.nodes().get(id);
        if (original == null) {
            return false;
        }
        if (!state.type().equals(HushKeys.HUSHED_NODE.identifier())) {
            onNodeRemoved(server, id); // stale: someone retyped it since
            return false;
        }
        if (NodeService.type(level, original.type()) == null) {
            if (!missingTypeWarned) {
                missingTypeWarned = true;
                LOGGER.warn(
                        "Hushed node {} stays hushed: its original type {} is missing",
                        id,
                        original.type());
            }
            return false;
        }
        try (var node = GUARD.tryEnter(id)) {
            if (node == null
                    || !who.authorise(
                            level,
                            pos,
                            level.getBlockState(pos),
                            new HushChange.Node(
                                    id, HushKeys.HUSHED_NODE, typeKey(original.type())))) {
                return false;
            }
            NodeBlockEntity again = NodeService.placedNode(level, pos);
            if (again != be || again == null || !state.equals(again.state())) {
                return false;
            }
            boolean[] settled = new boolean[1];
            NodeService.update(
                    server,
                    id,
                    new NodeService.NodeChange(
                            Optional.of(typeKey(original.type())),
                            Optional.empty(),
                            Optional.of(
                                    NodeState.Clarity.valueOf(
                                            original.clarity().toUpperCase(Locale.ROOT)))),
                    NodeCause.API,
                    (b, a) -> {
                        onNodeRemoved(server, id);
                        settled[0] = true;
                    });
            return settled[0];
        }
    }

    // --- readings ----------------------------------------------------------------------------

    /** {@code HushService.readout(level, chunk)}: the synced view of a loaded chunk's aura. */
    public static HushChunkStatus.View readout(LevelChunk chunk) {
        return HushChunkStatus.View.of(FluxService.aura(chunk));
    }

    /** Whether a chunk is saturated (transient server state; instruments never claim it). */
    public static boolean saturated(ServerLevel level, ChunkPos chunk) {
        return FluxService.state(level).saturated.contains(chunk.pack());
    }
}
