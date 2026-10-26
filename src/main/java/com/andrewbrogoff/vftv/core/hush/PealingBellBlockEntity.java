package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Pealing Bell's block entity (Hush spec, "The Pealing Bell"): {@code {format 1, sonus,
 * cursor}}, kept raw when unreadable. While a peal owns it or a transfer transaction is open it is
 * scoped: its handler is unavailable, and a removal waits for both scopes to settle.
 */
public final class PealingBellBlockEntity extends BlockEntity {
    private static final String KEY = "bell";
    private static final Codec<Retained<HushRecords.Bell>> RETAINED =
            RawRecordCodec.wrap(HushRecords.BELL);
    private static final Logger LOGGER = LogUtils.getLogger();

    private Retained.@Nullable Unreadable<HushRecords.Bell> unreadable;
    private boolean warned;

    /** Stored Sonus. */
    int sonus;

    /** The scan's continuation index. */
    int cursor;

    /** Whether a peal owns the bell. */
    boolean pealing;

    /** Whether a transfer transaction is open on it. */
    private boolean busy;

    /** Whether a removal waits for the scopes to settle. */
    boolean removalPending;

    /** The platform's handler, bound to this instance for its life. */
    public @Nullable Object platformHandler;

    /** A bell. */
    public PealingBellBlockEntity(BlockPos pos, BlockState state) {
        super(HushContent.pealingBellType(), pos, state);
    }

    /** Whether its record is unreadable. */
    public boolean unreadable() {
        return unreadable != null;
    }

    /** Stored Sonus; 0 while unreadable. */
    public int sonus() {
        return unreadable == null ? 0 : sonus;
    }

    /** The scan's cursor. */
    public int cursor() {
        return cursor;
    }

    /**
     * Whether the essentia handler is unavailable: removed, unreadable or owned by a peal. It
     * accepts nothing and its suction reads 0.
     */
    public boolean unavailable() {
        return isRemoved() || unreadable != null || pealing;
    }

    /** Sonus it accepts now, within {@code peal.capacity}. */
    public int room() {
        return unavailable() ? 0 : Math.max(0, HushService.rules().pealCapacity() - sonus);
    }

    /** Adds Sonus inside an open transaction (the journal has snapshotted it). */
    public void add(int amount) {
        sonus += amount;
    }

    /** Restores a snapshot, on a transaction's abort. */
    public void restore(int snapshot) {
        sonus = snapshot;
    }

    /** Marks the bell transaction-busy or not; closing settles a pending removal. */
    public void setBusy(boolean next) {
        boolean closed = busy && !next;
        busy = next;
        if (closed && level instanceof ServerLevel server) {
            HushBells.settle(server, this);
        }
    }

    /** Whether a transfer transaction is open. */
    public boolean busy() {
        return busy;
    }

    /** On a committed root transaction: saved and synced. */
    public void committed() {
        if (!isRemoved()) {
            changed();
        }
    }

    /** Marks it for saving and sends its Sonus to trackers. */
    void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        switch (input.read(KEY, RETAINED).orElse(new Retained.Read<>(HushRecords.Bell.EMPTY))) {
            case Retained.Read<HushRecords.Bell> r -> {
                unreadable = null;
                sonus = r.value().sonus();
                cursor = r.value().cursor();
            }
            case Retained.Unreadable<HushRecords.Bell> u -> {
                unreadable = u;
                sonus = 0;
                cursor = 0;
                if (!warned) {
                    warned = true;
                    LOGGER.warn("Pealing Bell at {} is unreadable: {}", worldPosition, u.reason());
                }
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        Retained.@Nullable Unreadable<HushRecords.Bell> u = unreadable;
        output.store(
                KEY,
                RETAINED,
                u != null
                        ? u
                        : new Retained.Read<>(
                                new HushRecords.Bell(HushRecords.FORMAT, sonus, cursor)));
    }

    /** The raw tag of an unreadable record, for the removal's WARN. */
    @Nullable Object raw() {
        Retained.@Nullable Unreadable<HushRecords.Bell> u = unreadable;
        return u == null ? null : u.raw();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("sonus", sonus());
        tag.putBoolean("unreadable", unreadable());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        sonus = input.getIntOr("sonus", 0);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        sonus = input.getIntOr("sonus", 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            HushBells.index(server, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            HushBells.unindex(server, this);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            HushBells.onRemoved(server, this);
        }
    }
}
