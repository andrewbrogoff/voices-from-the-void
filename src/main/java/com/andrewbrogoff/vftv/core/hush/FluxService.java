package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.flux.FluxClearSource;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import com.andrewbrogoff.vftv.api.hush.FluxPayload;
import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.common.SavedDataSlot;
import com.andrewbrogoff.vftv.core.hush.model.HushMath;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Where flux goes (Hush spec, "Depositing", "Pending and unreadable"): deposits into a chunk's aura
 * or the level's pending table, clears, merges at load, the repair, and the coalesced aura sync.
 * Server thread only. Internal in v1: no addon deposits or clears flux.
 */
public final class FluxService {
    /** The per-level {@code vftv_flux_pending}. */
    public static final SavedDataSlot<Retained<HushRecords.Pending>> PENDING =
            new SavedDataSlot<>(
                    Identifier.fromNamespaceAndPath("vftv", "flux_pending"),
                    RawRecordCodec.wrap(HushRecords.PENDING),
                    () -> new Retained.Read<>(HushRecords.Pending.EMPTY));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceKey<Level>, HushLevel> LEVELS = new HashMap<>();
    private static volatile @Nullable HushPlatform platform;

    private FluxService() {}

    /**
     * What a deposit did with the flux generated: {@code generated = retained + pending +
     * overflow}.
     */
    public record DepositResult(int generated, int retained, int pending, int overflow) {
        static final DepositResult ZERO = new DepositResult(0, 0, 0, 0);
    }

    /** Installs the platform seam, once, from {@code neoforge.hush}. */
    public static void install(HushPlatform seam) {
        platform = seam;
    }

    static HushPlatform platform() {
        HushPlatform p = platform;
        if (p == null) {
            throw new IllegalStateException("the Hush platform is not installed");
        }
        return p;
    }

    static HushLevel state(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new HushLevel());
    }

    /** Drops every level's transient state, when the server stops. */
    public static void clear() {
        LEVELS.clear();
    }

    /** The loaded, block-ticking-or-not chunk at {@code pack}, or null; never loads. */
    static @Nullable LevelChunk chunkNow(ServerLevel level, long pack) {
        return level.getChunkSource().getChunkNow(ChunkPos.getX(pack), ChunkPos.getZ(pack));
    }

    /** The aura of a loaded chunk, or null. */
    public static @Nullable Aura aura(LevelChunk chunk) {
        return platform().aura(chunk);
    }

    /**
     * {@code deposit(level, pos, generated, source, player?)}: retains what the chunk's aura can
     * hold, holds the rest for an unloaded or unreadable chunk as far as the pending table allows,
     * and reports the remainder as overflow. Zero is a strict no-op. No chunk is ever loaded.
     *
     * @throws IllegalArgumentException if {@code generated} is negative (a caller's data error)
     */
    public static DepositResult deposit(
            ServerLevel level,
            BlockPos pos,
            int generated,
            FluxSource source,
            @Nullable ServerPlayer player) {
        if (generated < 0) {
            throw new IllegalArgumentException("negative flux " + generated);
        }
        if (generated == 0) {
            return DepositResult.ZERO;
        }
        HushLevel state = state(level);
        ChunkPos chunkPos = ChunkPos.containing(pos);
        long pack = chunkPos.pack();
        LevelChunk chunk = chunkNow(level, pack);
        Aura aura = chunk == null ? null : platform().aura(chunk);
        int retained = 0;
        int pending = 0;
        BlockPos seep;
        if (chunk != null && (aura == null || aura.readable())) {
            retained = HushMath.retain(aura == null ? 0 : aura.flux, generated);
            if (retained > 0) {
                if (aura == null) {
                    aura = new Aura();
                    aura.flux = retained;
                    aura.hasSeep = true;
                    aura.seep = pos.asLong();
                    aura.spiller = player == null ? null : player.getUUID();
                    platform().createAura(chunk, aura);
                } else {
                    aura.flux += retained;
                    aura.hasSeep = true;
                    aura.seep = pos.asLong();
                    if (player != null) {
                        aura.spiller = player.getUUID();
                    }
                    state.dirty.add(pack);
                }
                state.unsaturate(pack);
                HushService.wake(level, state, pack, aura);
            }
            seep = aura == null ? null : aura.seep();
        } else {
            pending = addPending(level, state, pack, pos, generated, player);
            seep = pos;
        }
        int overflow = generated - retained - pending;
        if (chunk != null) {
            chunk.markUnsaved();
        }
        addOverflow(level, state, overflow);
        platform()
                .flux(
                        new FluxPayload.Added(
                                level, chunkPos, seep, generated, retained, pending, overflow,
                                source, player));
        return new DepositResult(generated, retained, pending, overflow);
    }

    /** Adds to the chunk's pending entry as far as the bound and the table allow. */
    private static int addPending(
            ServerLevel level,
            HushLevel state,
            long pack,
            BlockPos pos,
            int generated,
            @Nullable ServerPlayer player) {
        if (!(PENDING.get(level) instanceof Retained.Read<HushRecords.Pending> read)) {
            warnPending(level, state);
            return 0;
        }
        HushRecords.Pending p = read.value();
        List<HushRecords.PendingEntry> entries = new ArrayList<>(p.entries());
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).chunk() == pack) {
                index = i;
            }
        }
        int held = index < 0 ? 0 : entries.get(index).flux();
        if (index < 0 && entries.size() >= HushLimits.MAX_PENDING) {
            return 0;
        }
        int kept = HushMath.retain(held, generated);
        if (kept == 0) {
            return 0;
        }
        Optional<java.util.UUID> spiller =
                player != null
                        ? Optional.of(player.getUUID())
                        : index < 0 ? Optional.empty() : entries.get(index).spiller();
        HushRecords.PendingEntry next =
                new HushRecords.PendingEntry(pack, held + kept, pos.asLong(), spiller);
        if (index < 0) {
            entries.add(next);
        } else {
            entries.set(index, next);
        }
        PENDING.set(
                level,
                new Retained.Read<>(new HushRecords.Pending(p.format(), p.overflow(), entries)));
        return kept;
    }

    private static void addOverflow(ServerLevel level, HushLevel state, int overflow) {
        if (overflow <= 0) {
            return;
        }
        if (PENDING.get(level) instanceof Retained.Read<HushRecords.Pending> read) {
            HushRecords.Pending p = read.value();
            long total =
                    p.overflow() > Long.MAX_VALUE - overflow
                            ? Long.MAX_VALUE
                            : p.overflow() + overflow;
            PENDING.set(
                    level,
                    new Retained.Read<>(new HushRecords.Pending(p.format(), total, p.entries())));
        } else {
            warnPending(level, state);
            state.sessionOverflow =
                    state.sessionOverflow > Long.MAX_VALUE - overflow
                            ? Long.MAX_VALUE
                            : state.sessionOverflow + overflow;
        }
    }

    private static void warnPending(ServerLevel level, HushLevel state) {
        if (!state.pendingWarned && PENDING.get(level) instanceof Retained.Unreadable<?> u) {
            state.pendingWarned = true;
            LOGGER.warn(
                    "vftv_flux_pending in {} is unreadable ({}); deposits that need it overflow",
                    level.dimension().identifier(),
                    u.reason());
        }
    }

    /** The level's pending count and overflow total (with this session's, while unreadable). */
    public static long[] pendingAndOverflow(ServerLevel level) {
        HushLevel state = state(level);
        if (PENDING.get(level) instanceof Retained.Read<HushRecords.Pending> read) {
            return new long[] {read.value().entries().size(), read.value().overflow()};
        }
        return new long[] {0, state.sessionOverflow};
    }

    /**
     * Clears up to {@code max} from a loaded chunk's readable aura, clearing its saturation and
     * offering it to the due queue; posts {@code FluxEvent(CLEARED)} when anything was cleared. An
     * absent aura is never created and an unreadable one never changed.
     *
     * @return the flux cleared
     */
    public static int clear(
            ServerLevel level,
            LevelChunk chunk,
            int max,
            FluxClearSource source,
            @Nullable ServerPlayer player) {
        Aura aura = platform().aura(chunk);
        if (aura == null || !aura.readable()) {
            return 0;
        }
        HushLevel state = state(level);
        long pack = chunk.getPos().pack();
        int before = aura.flux;
        int cleared = Math.min(before, Math.max(0, max));
        state.unsaturate(pack);
        if (cleared > 0) {
            aura.flux -= cleared;
            state.dirty.add(pack);
            chunk.markUnsaved();
        }
        HushService.wake(level, state, pack, aura);
        if (cleared > 0) {
            platform()
                    .flux(
                            new FluxPayload.Cleared(
                                    level,
                                    chunk.getPos(),
                                    aura.seep(),
                                    cleared,
                                    source,
                                    player,
                                    before,
                                    aura.flux));
        }
        return cleared;
    }

    /**
     * {@code /vftv flux repair}: an unreadable aura becomes an empty readable one (its raw tag
     * logged first), then the chunk's pending entry merges.
     *
     * @return whether the aura was unreadable
     */
    public static boolean repair(ServerLevel level, LevelChunk chunk) {
        Aura aura = platform().aura(chunk);
        if (aura == null || aura.readable()) {
            return false;
        }
        LOGGER.warn(
                "Repairing the unreadable aura of chunk {} in {}; its raw record was {}",
                chunk.getPos(),
                level.dimension().identifier(),
                aura.raw());
        aura.repair();
        HushLevel state = state(level);
        long pack = chunk.getPos().pack();
        merge(level, state, chunk);
        state.dirty.add(pack);
        chunk.markUnsaved();
        HushService.wake(level, state, pack, aura);
        return true;
    }

    /** {@code ChunkEvent.Load} on a server level: checks the seep and queues the chunk. */
    public static void onLoad(ServerLevel level, LevelChunk chunk) {
        Aura aura = platform().aura(chunk);
        if (aura != null && aura.checkSeep(chunk.getPos())) {
            LOGGER.warn(
                    "The aura of chunk {} in {} is unreadable: {}",
                    chunk.getPos(),
                    level.dimension().identifier(),
                    aura.reason());
        } else if (aura != null && !aura.readable()) {
            LOGGER.warn(
                    "The aura of chunk {} in {} is unreadable: {}",
                    chunk.getPos(),
                    level.dimension().identifier(),
                    aura.reason());
        }
        state(level).loaded.add(chunk.getPos().pack());
    }

    /** {@code ChunkEvent.Unload}: the chunk leaves every queue. */
    public static void onUnload(ServerLevel level, ChunkPos chunk) {
        HushLevel state = state(level);
        long pack = chunk.pack();
        state.due.remove(pack);
        state.unsaturate(pack);
        state.loaded.remove(pack);
        state.dirty.remove(pack);
    }

    /**
     * The start of a level's tick: each chunk loaded last tick and still loaded, in ascending
     * {@code pack()}, merges its pending entry and is offered to the due queue.
     */
    public static void tickStart(ServerLevel level) {
        HushLevel state = state(level);
        if (state.loaded.isEmpty()) {
            return;
        }
        List<Long> loaded = new ArrayList<>(state.loaded);
        state.loaded.clear();
        for (long pack : loaded) {
            LevelChunk chunk = chunkNow(level, pack);
            if (chunk == null) {
                continue;
            }
            merge(level, state, chunk);
            Aura aura = platform().aura(chunk);
            if (aura != null) {
                HushService.wake(level, state, pack, aura);
            }
        }
    }

    /** Merges the chunk's pending entry into a readable or absent aura, once. */
    private static void merge(ServerLevel level, HushLevel state, LevelChunk chunk) {
        if (!(PENDING.get(level) instanceof Retained.Read<HushRecords.Pending> read)) {
            return; // merging is off while the store is unreadable
        }
        long pack = chunk.getPos().pack();
        HushRecords.Pending p = read.value();
        HushRecords.PendingEntry entry = null;
        for (HushRecords.PendingEntry e : p.entries()) {
            if (e.chunk() == pack) {
                entry = e;
            }
        }
        Aura aura = platform().aura(chunk);
        if (entry == null || (aura != null && !aura.readable())) {
            return;
        }
        int kept = HushMath.retain(aura == null ? 0 : aura.flux, entry.flux());
        long overflow = (long) entry.flux() - kept;
        if (kept > 0) {
            if (aura == null) {
                aura = new Aura();
                aura.flux = kept;
                aura.hasSeep = true;
                aura.seep = entry.seep();
                aura.spiller = entry.spiller().orElse(null);
                platform().createAura(chunk, aura);
            } else {
                aura.flux += kept;
                aura.hasSeep = true;
                aura.seep = entry.seep();
                if (entry.spiller().isPresent()) {
                    aura.spiller = entry.spiller().get();
                }
                state.dirty.add(pack);
            }
            state.unsaturate(pack);
            chunk.markUnsaved();
        }
        List<HushRecords.PendingEntry> rest = new ArrayList<>(p.entries());
        rest.remove(entry);
        long total =
                p.overflow() > Long.MAX_VALUE - overflow ? Long.MAX_VALUE : p.overflow() + overflow;
        PENDING.set(level, new Retained.Read<>(new HushRecords.Pending(p.format(), total, rest)));
    }

    /** Marks a chunk's aura for the coalesced sync and the chunk unsaved. */
    static void dirty(ServerLevel level, LevelChunk chunk) {
        state(level).dirty.add(chunk.getPos().pack());
        chunk.markUnsaved();
    }

    /** After the level ticks: one aura sync per chunk written this tick. */
    public static void flush(ServerLevel level) {
        HushLevel state = state(level);
        if (state.dirty.isEmpty()) {
            return;
        }
        for (long pack : state.dirty) {
            LevelChunk chunk = chunkNow(level, pack);
            if (chunk != null && platform().aura(chunk) != null) {
                platform().syncAura(chunk);
            }
        }
        state.dirty.clear();
    }
}
