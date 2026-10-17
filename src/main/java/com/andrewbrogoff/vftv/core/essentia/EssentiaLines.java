package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaLimits;
import com.andrewbrogoff.vftv.core.common.model.Hashes;
import com.andrewbrogoff.vftv.core.common.model.Salts;
import com.andrewbrogoff.vftv.core.essentia.EssentiaSeam.Probe;
import com.andrewbrogoff.vftv.core.essentia.EssentiaService.AspectIndex;
import com.andrewbrogoff.vftv.core.essentia.EssentiaTubeBlock.Face;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaTuning;
import com.andrewbrogoff.vftv.core.essentia.model.LineModel;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * One level's essentia lines (essentia spec, "Lines", "The pulse", "Scheduling"): the topology
 * derived from block states and never persisted, the budgeted rebuild and refresh queues, and the
 * due-time scheduler that runs each line's pulses. Server thread only; nothing here loads a chunk.
 */
public final class EssentiaLines {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BlockEntityType<?>> CLAMP_LOGGED = new HashSet<>();

    /** A line's state. */
    public enum State {
        /** A rebuild is queued or in progress. */
        BUILDING,
        /** Built, and every chunk in its set loaded and block-ticking. */
        READY,
        /** A chunk it touches isn't loaded or isn't block-ticking. */
        INCOMPLETE,
        /** Over the member or endpoint cap. */
        OVERGROWN
    }

    /** One endpoint: a neighbour position and the side its arm touches. */
    record End(long pos, Direction side, long member) {}

    /** One built line, or an overgrown record. */
    static final class Line {
        final long id;
        final long key;
        final int members;
        final int endpointCount;
        final long[] memberPos;
        final List<End> ends;
        final Set<Long> chunks;
        final Set<Long> boundary;
        @Nullable LineModel model;
        Probe @Nullable [] probes;
        State state;
        boolean retired;
        boolean sleeping;
        int epoch;
        int skipped;
        int cleaned;
        @Nullable ResourceKey<Aspect> lastAspect;
        int lastAmount;
        long lastTick = -1;

        Line(
                long id,
                long[] memberPos,
                int endpointCount,
                List<End> ends,
                Set<Long> chunks,
                Set<Long> boundary,
                State state) {
            this.id = id;
            this.key = Arrays.stream(memberPos).min().orElseThrow();
            this.members = memberPos.length;
            this.endpointCount = endpointCount;
            this.memberPos = memberPos;
            this.ends = ends;
            this.chunks = chunks;
            this.boundary = boundary;
            this.state = state;
        }
    }

    /** A scheduler entry, ordered by (due tick, key). */
    private record Entry(long due, long key, Line line, int epoch, boolean recheck) {}

    /** A BFS or finalisation in progress, resumed under the per-tick budget. */
    private static final class Build {
        final long start;
        final LinkedHashSet<Long> visited = new LinkedHashSet<>();
        final ArrayDeque<Long> frontier = new ArrayDeque<>();
        final LinkedHashMap<Long, End> ends = new LinkedHashMap<>();
        final List<long[]> joins = new ArrayList<>();
        final Set<Long> boundary = new HashSet<>();
        boolean bfsDone;
        @Nullable Line overgrown;
        int finalised;

        Build(long start) {
            this.start = start;
            visited.add(start);
            frontier.add(start);
        }
    }

    private final ServerLevel level;
    private final Map<Long, Line> membership = new HashMap<>();
    private final Map<Long, List<Line>> byEndpoint = new HashMap<>();
    private final ArrayDeque<Long> rebuild = new ArrayDeque<>();
    private final TreeSet<Long> refresh = new TreeSet<>();
    private final Set<Long> loaded = new HashSet<>();
    private final ArrayDeque<Line> cleanup = new ArrayDeque<>();
    private final PriorityQueue<Entry> schedule =
            new PriorityQueue<>(Comparator.comparingLong(Entry::due).thenComparingLong(Entry::key));
    private final List<JarBlockEntity> releases = new ArrayList<>();
    private final List<Line> live = new ArrayList<>();
    private @Nullable Build build;
    private long nextId;
    private static boolean running;

    EssentiaLines(ServerLevel level) {
        this.level = level;
    }

    // --- change hooks -----------------------------------------------------------------------

    /** The live line holding {@code pos}, or null. */
    @Nullable Line lineAt(long pos) {
        Line l = membership.get(pos);
        return l == null || l.retired ? null : l;
    }

    private void retire(@Nullable Line line) {
        if (line == null || line.retired) {
            return;
        }
        line.retired = true;
        live.remove(line);
        cleanup.add(line);
    }

    private void retireAround(long pos, boolean self) {
        if (self) {
            retire(lineAt(pos));
        }
        for (Direction d : Direction.values()) {
            retire(lineAt(BlockPos.offset(pos, d)));
        }
    }

    private void restartIfTouched(long pos) {
        Build b = build;
        if (b == null) {
            return;
        }
        boolean touched = b.visited.contains(pos);
        for (Direction d : Direction.values()) {
            touched |= b.visited.contains(BlockPos.offset(pos, d));
        }
        if (touched) {
            build = null;
            rebuild.addFirst(b.start);
        }
    }

    /** A tube or valve placed, removed or changing state at {@code pos}. */
    void changed(BlockPos pos) {
        long p = pos.asLong();
        retireAround(p, true);
        restartIfTouched(p);
        rebuild.add(p);
        for (Direction d : Direction.values()) {
            rebuild.add(BlockPos.offset(p, d));
        }
    }

    /** A member's (or an Alembic's) block entity loaded at {@code pos}. */
    void loaded(BlockPos pos, boolean alembic) {
        long p = pos.asLong();
        loaded.add(p);
        if (!alembic) {
            retireAround(p, false);
            restartIfTouched(p);
            rebuild.add(p);
        }
    }

    /** A member's (or an Alembic's) block entity removed or unloaded at {@code pos}. */
    void removed(BlockPos pos) {
        long p = pos.asLong();
        loaded.remove(p);
        retire(lineAt(p));
        restartIfTouched(p);
        for (Direction d : Direction.values()) {
            rebuild.add(BlockPos.offset(p, d));
        }
    }

    /** A tube's label applied, cut or turned unreadable. */
    void labelChanged(BlockPos pos) {
        long p = pos.asLong();
        retire(lineAt(p));
        restartIfTouched(p);
        rebuild.add(p);
    }

    /** A tag reload: every line freezes, and every loaded tube, valve and Alembic refreshes. */
    void tagsReloaded() {
        List.copyOf(live).forEach(this::retire);
        build = null;
        refresh.addAll(loaded);
    }

    /** A tuning reload: every live line wakes under the new pulse. */
    void wakeAll() {
        for (Line l : live) {
            if (l.state == State.OVERGROWN) {
                continue;
            }
            l.epoch++;
            l.sleeping = false;
            schedule(
                    l,
                    l.state == State.INCOMPLETE ? level.getGameTime() : nextPhase(l),
                    l.state == State.INCOMPLETE);
        }
    }

    /** One of our jars at {@code pos} published a commit: wakes lines that touch it. */
    void wakeAt(BlockPos pos) {
        List<Line> lines = byEndpoint.get(pos.asLong());
        if (lines != null) {
            List.copyOf(lines).forEach(this::wake);
        }
    }

    private void wake(Line line) {
        if (line.retired || line.state != State.READY || !line.sleeping) {
            return;
        }
        line.sleeping = false;
        line.epoch++;
        schedule(line, nextPhase(line), false);
    }

    /** A drop reservation to release at the end of this tick if the jar survived. */
    void releaseAtTickEnd(JarBlockEntity jar) {
        releases.add(jar);
    }

    // --- admission --------------------------------------------------------------------------

    /**
     * Whether placing or opening at {@code pos} with these proposed connections would join lines
     * beyond the cap; refuses with {@code message.vftv.essentia.line_too_long} on the server.
     */
    static boolean refuseAdmission(
            Level level, BlockPos pos, boolean[] proposed, @Nullable Player player) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        EssentiaLines lines = EssentiaService.lines(server);
        boolean[] back = new boolean[6];
        long[] ids = new long[6];
        int[] counts = new int[6];
        boolean[] built = new boolean[6];
        for (Direction d : Direction.values()) {
            int i = d.get3DDataValue();
            BlockPos n = pos.relative(d);
            BlockState s = level.getBlockState(n);
            back[i] =
                    (s.getBlock() instanceof EssentiaTubeBlock
                                    && s.getValue(EssentiaTubeBlock.face(d.getOpposite()))
                                            != Face.BLOCKED)
                            || (s.getBlock() instanceof TubeValveBlock
                                    && TubeValveBlock.passes(s)
                                    && s.getValue(TubeValveBlock.AXIS) == d.getAxis());
            Line l = lines.lineAt(n.asLong());
            built[i] = l != null && l.state != State.BUILDING;
            ids[i] = l == null ? -1 : l.id;
            counts[i] = l == null ? 0 : l.members;
        }
        if (!EssentiaMath.admissionRefused(
                proposed, back, ids, counts, built, EssentiaLimits.MAX_MEMBERS)) {
            return false;
        }
        if (player instanceof ServerPlayer sp) {
            StatusLines.send(
                    sp,
                    Component.translatable(
                            TubeValveBlock.LINE_TOO_LONG,
                            NumberFormats.integer(EssentiaLimits.MAX_MEMBERS)));
        }
        return true;
    }

    // --- the tick -----------------------------------------------------------------------------

    /** End of a server tick: refresh, topology, scheduler, then reservations. */
    void tick() {
        int work = EssentiaLimits.MAX_TOPOLOGY_WORK_PER_TICK;
        work = cleanupStale(work);
        processRefresh();
        if (refresh.isEmpty()) {
            processRebuilds(work);
        }
        runSchedule();
        releases.forEach(JarBlockEntity::releaseReservation);
        releases.clear();
    }

    private int cleanupStale(int work) {
        while (work > 0 && !cleanup.isEmpty()) {
            Line l = cleanup.peek();
            while (l.cleaned < l.memberPos.length && work > 0) {
                long p = l.memberPos[l.cleaned++];
                if (membership.get(p) == l) {
                    membership.remove(p);
                }
                work--;
            }
            if (l.cleaned < l.memberPos.length) {
                return work;
            }
            cleanup.poll();
            for (End e : l.ends) {
                List<Line> at = byEndpoint.get(e.pos());
                if (at != null) {
                    at.remove(l);
                    if (at.isEmpty()) {
                        byEndpoint.remove(e.pos());
                    }
                }
            }
        }
        return work;
    }

    private boolean chunkReady(int cx, int cz) {
        return level.getChunkSource().getChunkNow(cx, cz) != null
                && level.shouldTickBlocksAt(ChunkPos.pack(cx, cz));
    }

    private boolean chunkReady(long chunk) {
        return chunkReady(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
    }

    private boolean loadedAt(long pos) {
        return level.getChunkSource().getChunkNow(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4)
                != null;
    }

    private void processRefresh() {
        for (int n = 0; n < EssentiaLimits.MAX_FACE_REFRESHES_PER_TICK && !refresh.isEmpty(); n++) {
            long p = refresh.pollFirst();
            if (!loadedAt(p)) {
                continue;
            }
            BlockPos pos = BlockPos.of(p);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AlembicBlock) {
                if (!AlembicBlock.baseAt(level, pos, state.getValue(AlembicBlock.FACING))) {
                    level.destroyBlock(pos, true);
                }
                continue;
            }
            EssentiaTubeBlock.refresh(level, pos);
            rebuild.add(p);
        }
    }

    private static boolean isTube(BlockState s) {
        return s.getBlock() instanceof EssentiaTubeBlock;
    }

    private static boolean isMember(BlockState s) {
        return isTube(s) || (s.getBlock() instanceof TubeValveBlock && TubeValveBlock.passes(s));
    }

    /** Whether member {@code s} at {@code pos} connects toward {@code d}. */
    private boolean connects(BlockPos pos, BlockState s, Direction d) {
        if (isTube(s)) {
            return s.getValue(EssentiaTubeBlock.face(d)) == Face.CONNECTED;
        }
        return s.getValue(TubeValveBlock.AXIS) == d.getAxis()
                && EssentiaTubeBlock.joins(level, pos, d);
    }

    private void processRebuilds(int work) {
        while (work > 0) {
            Build b = build;
            if (b == null) {
                Long next = rebuild.poll();
                if (next == null) {
                    return;
                }
                work--;
                long p = next;
                if (lineAt(p) != null
                        || !loadedAt(p)
                        || !isMember(level.getBlockState(BlockPos.of(p)))) {
                    continue;
                }
                b = new Build(p);
                build = b;
            }
            work = step(b, work);
            if (b.overgrown == null && b.bfsDone && b.finalised < 0) {
                build = null;
            } else if (b.overgrown != null && b.finalised >= b.visited.size()) {
                build = null;
            }
        }
    }

    /** Advances a build by up to {@code work} units; returns what is left. */
    private int step(Build b, int work) {
        while (!b.bfsDone && work > 0) {
            Long next = b.frontier.poll();
            if (next == null) {
                b.bfsDone = true;
                break;
            }
            work--;
            BlockPos pos = BlockPos.of(next);
            BlockState s = level.getBlockState(pos);
            for (Direction d : Direction.values()) {
                if (!connects(pos, s, d)) {
                    continue;
                }
                BlockPos n = pos.relative(d);
                if (!chunkReady(n.getX() >> 4, n.getZ() >> 4)) {
                    b.boundary.add(ChunkPos.pack(n.getX() >> 4, n.getZ() >> 4));
                    continue;
                }
                BlockState ns = level.getBlockState(n);
                if (isMember(ns)) {
                    if (connects(n, ns, d.getOpposite())) {
                        b.joins.add(new long[] {next, n.asLong()});
                        if (b.visited.add(n.asLong())) {
                            b.frontier.add(n.asLong());
                        }
                    }
                } else if (!isTube(ns) && !(ns.getBlock() instanceof TubeValveBlock)) {
                    long key = n.asLong() * 8 + d.getOpposite().get3DDataValue();
                    b.ends.putIfAbsent(key, new End(n.asLong(), d.getOpposite(), next));
                }
            }
        }
        if (!b.bfsDone) {
            return work;
        }
        boolean over =
                b.visited.size() > EssentiaLimits.MAX_MEMBERS
                        || b.ends.size() > EssentiaLimits.MAX_ENDPOINTS;
        if (over) {
            return finaliseOvergrown(b, work);
        }
        int cost = b.visited.size() + 2 * b.ends.size();
        if (work < cost && work < EssentiaLimits.MAX_TOPOLOGY_WORK_PER_TICK) {
            return 0; // finish next tick, with a full allowance
        }
        finalise(b);
        b.finalised = -1;
        return Math.max(0, work - cost);
    }

    private int finaliseOvergrown(Build b, int work) {
        Line l = b.overgrown;
        if (l == null) {
            long[] members = b.visited.stream().mapToLong(Long::longValue).toArray();
            l =
                    new Line(
                            nextId++,
                            members,
                            b.ends.size(),
                            List.of(),
                            Set.of(),
                            Set.of(),
                            State.OVERGROWN);
            b.overgrown = l;
            live.add(l);
        }
        while (b.finalised < l.memberPos.length && work > 0) {
            membership.put(l.memberPos[b.finalised++], l);
            work--;
        }
        return work;
    }

    private void finalise(Build b) {
        AspectIndex index = EssentiaService.index(level.registryAccess());
        long[] members = b.visited.stream().mapToLong(Long::longValue).toArray();
        Map<Long, Integer> memberIndex = new HashMap<>();
        for (int i = 0; i < members.length; i++) {
            memberIndex.put(members[i], i);
        }
        // Nodes: unlabelled tubes and valves joined into segments; each labelled tube a gate.
        int[] node = new int[members.length];
        int[] admit = new int[members.length];
        boolean[] gate = new boolean[members.length];
        for (int i = 0; i < members.length; i++) {
            node[i] = i;
            admit[i] = LineModel.ADMIT_ALL;
            if (level.getBlockEntity(BlockPos.of(members[i])) instanceof LabelledBlockEntity be
                    && isTube(level.getBlockState(BlockPos.of(members[i])))) {
                if (!be.readable()) {
                    gate[i] = true;
                    admit[i] = LineModel.ADMIT_NONE;
                } else if (be.label() != null) {
                    gate[i] = true;
                    int x = index.index(be.label());
                    admit[i] = x < 0 ? LineModel.ADMIT_NONE : x;
                }
            }
        }
        for (long[] j : b.joins) {
            int a = memberIndex.getOrDefault(j[0], 0);
            int c = memberIndex.getOrDefault(j[1], 0);
            if (!gate[a] && !gate[c]) {
                int ra = root(node, a);
                int rc = root(node, c);
                node[Math.max(ra, rc)] = Math.min(ra, rc);
            }
        }
        Map<Integer, Integer> compact = new HashMap<>();
        List<Integer> admits = new ArrayList<>();
        int[] nodeOf = new int[members.length];
        for (int i = 0; i < members.length; i++) {
            int r = root(node, i);
            Integer id = compact.get(r);
            if (id == null) {
                id = admits.size();
                compact.put(r, id);
                admits.add(admit[r]);
            }
            nodeOf[i] = id;
        }
        Set<Long> edges = new LinkedHashSet<>();
        for (long[] j : b.joins) {
            int a = nodeOf[memberIndex.getOrDefault(j[0], 0)];
            int c = nodeOf[memberIndex.getOrDefault(j[1], 0)];
            if (a != c) {
                edges.add((long) Math.min(a, c) << 32 | Math.max(a, c));
            }
        }
        List<End> ends = new ArrayList<>(b.ends.values());
        ends.sort(
                Comparator.comparingLong(End::pos)
                        .thenComparingInt(e -> e.side().get3DDataValue()));
        Set<Long> chunks = new HashSet<>();
        for (long m : members) {
            chunks.add(ChunkPos.pack(BlockPos.getX(m) >> 4, BlockPos.getZ(m) >> 4));
        }
        long[] pos = new long[ends.size()];
        int[] endNode = new int[ends.size()];
        for (int e = 0; e < ends.size(); e++) {
            End end = ends.get(e);
            pos[e] = end.pos();
            endNode[e] = nodeOf[memberIndex.getOrDefault(end.member(), 0)];
            chunks.add(ChunkPos.pack(BlockPos.getX(end.pos()) >> 4, BlockPos.getZ(end.pos()) >> 4));
        }
        boolean ready = b.boundary.isEmpty() && chunks.stream().allMatch(this::chunkReady);
        Line line =
                new Line(
                        nextId++,
                        members,
                        ends.size(),
                        List.copyOf(ends),
                        chunks,
                        Set.copyOf(b.boundary),
                        ready ? State.READY : State.INCOMPLETE);
        int[] ea = new int[edges.size()];
        int[] eb = new int[edges.size()];
        int k = 0;
        for (long edge : edges) {
            ea[k] = (int) (edge >>> 32);
            eb[k++] = (int) edge;
        }
        line.model =
                new LineModel(
                        index.size(),
                        pos,
                        endNode,
                        admits.stream().mapToInt(Integer::intValue).toArray(),
                        ea,
                        eb);
        Probe[] probes = new Probe[ends.size()];
        for (int e = 0; e < ends.size(); e++) {
            End end = ends.get(e);
            int endpoint = e;
            probes[e] =
                    EssentiaService.seam()
                            .probe(
                                    level,
                                    BlockPos.of(end.pos()),
                                    end.side(),
                                    () -> !line.retired,
                                    () -> invalidated(line, endpoint));
            byEndpoint.computeIfAbsent(end.pos(), p -> new ArrayList<>()).add(line);
        }
        line.probes = probes;
        for (long m : members) {
            membership.put(m, line);
        }
        live.add(line);
        if (ready) {
            schedule(line, nextPhase(line), false);
        } else {
            schedule(line, level.getGameTime(), true);
        }
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private void invalidated(Line line, int endpoint) {
        LineModel m = line.model;
        if (line.retired || m == null) {
            return;
        }
        m.unstallSink(endpoint);
        wake(line);
    }

    // --- scheduling -------------------------------------------------------------------------

    private long nextPhase(Line line) {
        int pulse = EssentiaService.rules().tuning().tubePulse();
        long phase = Hashes.phase(Salts.CRUC, new long[] {line.key}, pulse);
        long now = level.getGameTime();
        long t = now - Long.remainderUnsigned(now, pulse) + phase;
        return t <= now ? t + pulse : t;
    }

    private void schedule(Line line, long due, boolean recheck) {
        schedule.add(new Entry(due, line.key, line, line.epoch, recheck));
    }

    private void runSchedule() {
        long now = level.getGameTime();
        int pops = 0;
        int pulses = 0;
        List<Entry> later = new ArrayList<>();
        while (pops < EssentiaLimits.MAX_SCHEDULER_POPS_PER_TICK
                && pulses < EssentiaLimits.MAX_PULSES_PER_TICK) {
            Entry e = schedule.peek();
            if (e == null || e.due() > now) {
                break;
            }
            schedule.poll();
            pops++;
            Line l = e.line();
            if (l.retired || e.epoch() != l.epoch) {
                continue;
            }
            if (e.recheck()) {
                recheck(l, later);
                continue;
            }
            if (l.state != State.READY) {
                continue;
            }
            if (!l.chunks.stream().allMatch(this::chunkReady)) {
                l.state = State.INCOMPLETE;
                later.add(new Entry(now, l.key, l, l.epoch, true));
                continue;
            }
            if (running || !EssentiaService.seam().idle()) {
                later.add(e); // deferred with its original due time
                continue;
            }
            pulses++;
            pulse(l);
        }
        schedule.addAll(later);
    }

    private void recheck(Line l, List<Entry> later) {
        boolean ready =
                l.boundary.stream().allMatch(this::chunkReady)
                        && l.chunks.stream().allMatch(this::chunkReady);
        if (ready) {
            retire(l);
            rebuild.add(l.key);
        } else {
            later.add(
                    new Entry(
                            level.getGameTime() + EssentiaService.rules().tubeIdleTicks(),
                            l.key,
                            l,
                            l.epoch,
                            true));
        }
    }

    /** Runs one pulse of a ready line (essentia spec, "The pulse"). */
    private void pulse(Line line) {
        LineModel model = line.model;
        Probe[] probes = line.probes;
        if (model == null || probes == null) {
            return;
        }
        AspectIndex index = EssentiaService.index(level.registryAccess());
        EssentiaTuning t = EssentiaService.rules().tuning();
        running = true;
        try {
            model.beginReads();
            int skipped = 0;
            for (int e = 0; e < probes.length; e++) {
                Probe p = probes[e];
                int slots = p.slots();
                if (slots < 0 || slots > EssentiaLimits.MAX_HANDLER_SLOTS) {
                    model.skip(e);
                    skipped += slots > EssentiaLimits.MAX_HANDLER_SLOTS ? 1 : 0;
                    continue;
                }
                for (int s = 0; s < slots; s++) {
                    ResourceKey<Aspect> a = p.aspect(s);
                    int x = index.index(a);
                    long amount = p.amount(s);
                    if (a != null && x >= 0 && amount > 0) {
                        model.read(
                                e,
                                x,
                                (int) Math.min(Integer.MAX_VALUE, amount),
                                clamp(p.hold(a), BlockPos.of(line.ends.get(e).pos())));
                    }
                }
            }
            line.skipped = skipped;
            long[] at = line.ends.stream().mapToLong(End::pos).toArray();
            LineModel.Outcome o =
                    model.pulse(
                            port(probes, index, at),
                            line.sleeping,
                            t.tubeIdleEvery(),
                            t.tubeRate());
            if (o.moved() > 0) {
                ResourceKey<Aspect> aspect =
                        index.holders().get(o.aspect()).unwrapKey().orElseThrow();
                line.lastAspect = aspect;
                line.lastAmount = o.moved();
                line.lastTick = level.getGameTime();
                BlockPos sink = BlockPos.of(line.ends.get(o.sink()).pos());
                if (o.source() >= 0) {
                    EssentiaService.sendTracking(
                            level,
                            sink,
                            new EssentiaPayloads.Flow(
                                    line.key,
                                    BlockPos.of(line.ends.get(o.source()).pos()),
                                    sink,
                                    aspect,
                                    Math.min(1000, o.sourceAmount())));
                }
            }
            line.sleeping = !o.awake();
        } catch (RuntimeException e) {
            LOGGER.error("An essentia line at {} failed a pulse", BlockPos.of(line.key), e);
        } finally {
            EssentiaService.seam().closeAll();
            running = false;
        }
        if (!line.retired) {
            long next = nextPhase(line);
            if (line.sleeping) {
                next += (long) (t.tubeIdleEvery() - 1) * t.tubePulse();
            }
            schedule(line, next, false);
        }
    }

    /** Whether a pulse or pour of ours is running now: no other one starts meanwhile. */
    static boolean running() {
        return running;
    }

    static void setRunning(boolean value) {
        running = value;
    }

    /**
     * Clamps a rank to 0–128, logging an out-of-range one once per block entity type (essentia
     * spec, "Suction").
     */
    int clamp(int rank, BlockPos at) {
        if (rank >= 0 && rank <= EssentiaLimits.MAX_SUCTION) {
            return rank;
        }
        var be = level.getBlockEntity(at);
        if (be != null && CLAMP_LOGGED.add(be.getType())) {
            LOGGER.warn("Essentia rank {} at {} is outside 0..128; clamped", rank, at);
        }
        return Math.max(0, Math.min(EssentiaLimits.MAX_SUCTION, rank));
    }

    /** The model's port over these probes at these positions, through the seam's transactions. */
    LineModel.Port port(Probe[] probes, AspectIndex index, long[] at) {
        EssentiaSeam seam = EssentiaService.seam();
        return new LineModel.Port() {
            @Override
            public int suction(int e, int x) {
                return clamp(
                        probes[e].suction(index.holders().get(x).unwrapKey().orElseThrow()),
                        BlockPos.of(at[e]));
            }

            @Override
            public void beginRoot() {
                seam.beginRoot();
            }

            @Override
            public void commitRoot() {
                seam.commitRoot();
            }

            @Override
            public void beginNested() {
                seam.beginNested();
            }

            @Override
            public void endNested(boolean commit) {
                seam.endNested(commit);
            }

            @Override
            public int extract(int e, int x, int amount) {
                return seam.extract(probes[e], index.holders().get(x), amount);
            }

            @Override
            public int insert(int e, int x, int amount) {
                return seam.insert(probes[e], index.holders().get(x), amount);
            }
        };
    }

    // --- inspection ---------------------------------------------------------------------------

    /**
     * A read-only view of a live line (essentia spec, "Events": internal in v1).
     *
     * @param state the state; {@code BUILDING} for none
     * @param asleep whether a ready line sleeps
     * @param members its member count
     * @param endpoints its endpoint count
     * @param lastAspect the last move's aspect, or null
     * @param lastAmount the last move's amount
     * @param lastTick the last move's game time, or -1
     * @param deafArms connected faces whose endpoint exposes no handler on that side
     * @param stalled stalled (sink, aspect) pairs
     * @param skipped endpoints skipped for too many slots
     */
    public record View(
            State state,
            boolean asleep,
            int members,
            int endpoints,
            @Nullable ResourceKey<Aspect> lastAspect,
            int lastAmount,
            long lastTick,
            int deafArms,
            int stalled,
            int skipped) {
        /** No line: building, with zero counts. */
        public static final View NONE = new View(State.BUILDING, false, 0, 0, null, 0, -1, 0, 0, 0);
    }

    /** The view of the line holding {@code pos}, or {@link View#NONE}. */
    public View of(BlockPos pos) {
        Line l = lineAt(pos.asLong());
        if (l == null) {
            return View.NONE;
        }
        int deaf = 0;
        Probe[] probes = l.probes;
        if (probes != null && l.state == State.READY) {
            for (int e = 0; e < probes.length; e++) {
                BlockPos at = BlockPos.of(l.ends.get(e).pos());
                if (level.getBlockState(at).is(EssentiaKeys.ESSENTIA_ENDPOINTS)
                        && probes[e].slots() < 0) {
                    deaf++;
                }
            }
        }
        LineModel m = l.model;
        return new View(
                l.state,
                l.sleeping,
                l.members,
                l.endpointCount,
                l.lastAspect,
                l.lastAmount,
                l.lastTick,
                deaf,
                m == null ? 0 : m.stalledCount(),
                l.skipped);
    }

    /** How many live lines this level holds (diagnostics and tests). */
    public int liveLines() {
        return live.size();
    }

    /** Whether the rebuild and refresh queues are empty and no build is in progress. */
    public boolean settled() {
        return rebuild.isEmpty() && refresh.isEmpty() && build == null;
    }
}
