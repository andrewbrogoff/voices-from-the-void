package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.crucible.model.CandidateSet;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleMath;
import com.andrewbrogoff.vftv.core.crucible.model.CrucibleTuning;
import com.andrewbrogoff.vftv.core.crucible.model.Pool;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The crucible's block entity (crucible spec, "Sync and state"): the pool record {@code {"format":
 * 1, "pool": {...}}} with raw retention, and the transient step, operation and transaction state.
 * Clients receive the pool, the capacity in force and the {@code unreadable} flag, never the raw
 * tag.
 */
public final class CrucibleBlockEntity extends BlockEntity {
    /** The saved record's key in the block entity's data. */
    static final String KEY = "crucible";

    /** The pool record's format. */
    static final int FORMAT = 1;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SYNC_POOL = "pool";
    private static final String SYNC_CAPACITY = "capacity";
    private static final String SYNC_UNREADABLE = "unreadable";

    /** The pool as saved: amounts 1-10,000, at most 64 well-formed ids, total at most 10,000. */
    static final Codec<SortedMap<String, Integer>> POOL_CODEC =
            Codec.unboundedMap(Identifier.CODEC, Codec.intRange(1, Pool.MAX_TOTAL))
                    .comapFlatMap(
                            CrucibleBlockEntity::checkPool,
                            m -> {
                                Map<Identifier, Integer> out = new TreeMap<>();
                                m.forEach((k, v) -> out.put(Identifier.parse(k), v));
                                return out;
                            });

    /** The bounded record codec: format 1 only. */
    static final Codec<SortedMap<String, Integer>> RECORD =
            RecordCodecBuilder.<SortedMap<String, Integer>>create(
                    i ->
                            i.group(
                                            Codec.INT
                                                    .validate(
                                                            f ->
                                                                    f == FORMAT
                                                                            ? DataResult.success(f)
                                                                            : DataResult.error(
                                                                                    () ->
                                                                                            "unknown format "
                                                                                                    + f))
                                                    .fieldOf("format")
                                                    .forGetter(m -> FORMAT),
                                            POOL_CODEC.fieldOf("pool").forGetter(m -> m))
                                    .apply(i, (format, pool) -> pool));

    /** The retained record codec. */
    static final Codec<Retained<SortedMap<String, Integer>>> RETAINED = RawRecordCodec.wrap(RECORD);

    private final Pool pool = new Pool();
    private Retained.@Nullable Unreadable<SortedMap<String, Integer>> unreadable;
    private boolean warned;
    private int syncedCapacity = CrucibleTuning.DEFAULTS.capacity();
    private boolean syncedUnreadable;

    /** The admission set. */
    final CandidateSet candidates = new CandidateSet();

    /** An emptying operation is open. */
    boolean operation;

    /** A step is running (the step guard). */
    boolean stepping;

    /** Queued for refresh; processes nothing until refreshed. */
    boolean queued;

    /** A removal waits for the open transaction's root to close. */
    boolean pendingRemoval;

    /**
     * The platform adapter's per-instance state (its essentia handler and journal), which lives and
     * dies with this block entity; {@code core} never reads it.
     */
    public @Nullable Object platformHandler;

    private boolean busy;
    private int tint;
    private int tintRevision;
    private boolean tintValid;
    private int phaseInterval;
    private long phase;

    /** A crucible's block entity. */
    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(CrucibleContent.blockEntity(), pos, state);
    }

    /** The pool; never read or changed while unreadable. */
    public Pool pool() {
        return pool;
    }

    /** Whether the record failed to decode (on a client: the synced flag). */
    public boolean unreadable() {
        return unreadable != null || syncedUnreadable;
    }

    /** The capacity in force: the tuning on a server, the synced value on a client. */
    public int capacity() {
        return level instanceof ServerLevel ? CrucibleService.tuning().capacity() : syncedCapacity;
    }

    /** Whether a transfer transaction's root is open on this crucible. */
    public boolean busy() {
        return busy;
    }

    /**
     * Whether the essentia handler is unavailable: removed or replaced, unreadable, in an
     * operation, or mid-step (crucible spec, "Essentia out").
     */
    public boolean unavailable() {
        return isRemoved()
                || level == null
                || level.getBlockEntity(worldPosition) != this
                || unreadable()
                || operation
                || stepping;
    }

    /** Marks the transaction state; the platform adapter calls it. */
    public void setBusy(boolean busy) {
        boolean closed = this.busy && !busy;
        this.busy = busy;
        if (closed) {
            CrucibleService.transactionClosed(this);
        }
    }

    /** Marks the block entity changed for saving and for the coalesced sync. */
    public void markDirty() {
        CrucibleService.markDirty(this);
    }

    /**
     * The liquid's colour, recomputed by {@code compute} only when the pool changed (client
     * renderer cache, so drawing allocates nothing).
     */
    public int tint(ToIntFunction<CrucibleBlockEntity> compute) {
        if (tintRevision != pool.revision() || !tintValid) {
            tint = compute.applyAsInt(this);
            tintRevision = pool.revision();
            tintValid = true;
        }
        return tint;
    }

    @Override
    public String toString() {
        return "Crucible["
                + worldPosition.toShortString()
                + ", queued="
                + queued
                + ", stepping="
                + stepping
                + ", operation="
                + operation
                + ", busy="
                + busy
                + ", removed="
                + isRemoved()
                + ", "
                + pool
                + "]";
    }

    /** This crucible's phase at an interval, cached. */
    long phase(int interval) {
        if (interval != phaseInterval) {
            phaseInterval = interval;
            phase = CrucibleMath.phase(worldPosition.asLong(), interval);
        }
        return phase;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Retained<SortedMap<String, Integer>> read =
                input.read(KEY, RETAINED).orElse(new Retained.Read<>(new TreeMap<>()));
        switch (read) {
            case Retained.Read<SortedMap<String, Integer>> r -> {
                unreadable = null;
                pool.restore(r.value());
            }
            case Retained.Unreadable<SortedMap<String, Integer>> u -> {
                unreadable = u;
                pool.clear();
                if (!warned) {
                    warned = true;
                    LOGGER.warn(
                            "Crucible at {} has an unreadable record ({}); keeping it as it is",
                            worldPosition,
                            u.reason());
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        Retained.@Nullable Unreadable<SortedMap<String, Integer>> u = unreadable;
        output.store(KEY, RETAINED, u != null ? u : new Retained.Read<>(pool.snapshot()));
    }

    /** The raw record, for the removal's WARN; null when readable. */
    @Nullable Tag raw() {
        Retained.@Nullable Unreadable<SortedMap<String, Integer>> u = unreadable;
        return u == null ? null : u.raw();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(SYNC_CAPACITY, capacity());
        tag.putBoolean(SYNC_UNREADABLE, unreadable());
        if (!unreadable()) {
            tag.put(
                    SYNC_POOL,
                    POOL_CODEC.encodeStart(NbtOps.INSTANCE, pool.snapshot()).getOrThrow());
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        readSync(input);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        readSync(input);
    }

    private void readSync(ValueInput input) {
        syncedCapacity = input.getIntOr(SYNC_CAPACITY, CrucibleTuning.DEFAULTS.capacity());
        syncedUnreadable = input.getBooleanOr(SYNC_UNREADABLE, false);
        pool.restore(input.read(SYNC_POOL, POOL_CODEC).orElse(new TreeMap<>()));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            CrucibleService.onLoad(server, this);
            if (unreadable()) {
                // A cache from a readable predecessor at this position must read the inert handler.
                CrucibleService.hooks().invalidate(server, worldPosition);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            CrucibleService.onUnload(server, this);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            CrucibleService.onRemoved(server, this);
        }
    }

    private static DataResult<SortedMap<String, Integer>> checkPool(Map<Identifier, Integer> map) {
        if (map.size() > Pool.MAX_ENTRIES) {
            return DataResult.error(() -> "more than " + Pool.MAX_ENTRIES + " pool entries");
        }
        long total = 0;
        TreeMap<String, Integer> out = new TreeMap<>();
        for (Map.Entry<Identifier, Integer> e : map.entrySet()) {
            total += e.getValue();
            out.put(e.getKey().toString(), e.getValue());
        }
        if (total > Pool.MAX_TOTAL) {
            long t = total;
            return DataResult.error(() -> "pool total " + t + " over " + Pool.MAX_TOTAL);
        }
        return DataResult.success(out);
    }
}
