package com.andrewbrogoff.vftv.core.infusion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.ticks.ContainerSingleItem;
import org.jspecify.annotations.Nullable;

/**
 * What the Singing Bowl and the Pedestal share (infusion spec, "Holding items"): one item, held
 * through vanilla's {@link ContainerSingleItem.BlockContainerSingleItem} with a maximum stack of 1,
 * the container path's locks, the transaction-busy state the platform's item handler reports, and
 * the deferred removal. A hopper sees exactly the item handler's rules.
 */
public abstract sealed class AltarBlockEntity extends BlockEntity
        implements ContainerSingleItem.BlockContainerSingleItem
        permits BowlBlockEntity, PedestalBlockEntity {
    static final String SYNC_ITEM = "item";
    static final String SYNC_UNREADABLE = "unreadable";

    /** The held item; empty while unreadable. */
    ItemStack item = ItemStack.EMPTY;

    /** A removal waits for the open transaction's root, or the run operation, to close. */
    boolean pendingRemoval;

    /** The synced unreadable flag (client). */
    boolean syncedUnreadable;

    /** One WARN per session for an unreadable record. */
    boolean warned;

    /**
     * The platform adapter's per-instance state (its item handler and journal), which lives and
     * dies with this block entity; {@code core} never reads it.
     */
    public @Nullable Object platformHandler;

    private boolean busy;

    AltarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Whether the record failed to decode (on a client: the synced flag). */
    public abstract boolean unreadable();

    /**
     * Whether {@code stack} may be inserted now, the slot being empty (infusion spec, "May
     * insert"). The caller checks availability and busy first.
     */
    public abstract boolean mayInsert(ItemStack stack);

    /** Whether taking is locked (a ringing bowl; a pedestal with a live or unknown claim). */
    public abstract boolean takingLocked();

    /**
     * Whether the item handler is unavailable: removed or replaced, or unreadable; a bowl also
     * while a run operation is open on it.
     */
    public boolean unavailable() {
        return isRemoved()
                || level == null
                || level.getBlockEntity(worldPosition) != this
                || unreadable();
    }

    /** Whether a transfer transaction's root is open on this block. */
    public boolean busy() {
        return busy;
    }

    /**
     * Marks the transaction state; the platform adapter calls it. A close runs a deferred removal.
     */
    public void setBusy(boolean busy) {
        boolean closed = this.busy && !busy;
        this.busy = busy;
        if (closed) {
            InfusionAltars.settle(this);
        }
    }

    /** Marks the block entity changed for saving and for the coalesced sync, unless removed. */
    public void markDirty() {
        if (!isRemoved()) {
            InfusionAltars.markDirty(this);
        }
    }

    /**
     * Writes the slot with no publication, for the platform's item handler inside a transaction:
     * its journal publishes on the root commit.
     */
    public void writeSlot(ItemStack stack) {
        item = stack;
    }

    /**
     * Marks the chunk unsaved without vanilla's neighbour signal update: neither block has a
     * comparator output, and a commit's or a step's writes must run no foreign code.
     */
    @Override
    public void setChanged() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    @Override
    public ItemStack getTheItem() {
        return item;
    }

    /** A direct write, as commands and other mods make: it bypasses the locks, as on a chest. */
    @Override
    public void setTheItem(ItemStack stack) {
        item = stack;
        markDirty();
    }

    @Override
    public ItemStack splitTheItem(int count) {
        ItemStack taken = item.split(count);
        if (item.isEmpty()) {
            item = ItemStack.EMPTY;
        }
        markDirty();
        return taken;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    /** The hopper's insert: exactly the handler's "may insert". */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && !unavailable() && !busy && item.isEmpty() && mayInsert(stack);
    }

    /** The hopper's extract: exactly "taking is not locked". */
    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return slot == 0 && !unavailable() && !busy && !takingLocked();
    }

    // --- Sync -----------------------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SYNC_UNREADABLE, unreadable());
        if (!unreadable() && !item.isEmpty()) {
            ItemStack.CODEC
                    .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), item)
                    .ifSuccess(t -> tag.put(SYNC_ITEM, t));
        }
        writeSync(tag, registries);
        return tag;
    }

    /** The owner's extra synced fields. */
    abstract void writeSync(CompoundTag tag, HolderLookup.Provider registries);

    /** Reads the owner's extra synced fields. */
    abstract void readSync(ValueInput input);

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        read(input);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        read(input);
    }

    private void read(ValueInput input) {
        syncedUnreadable = input.getBooleanOr(SYNC_UNREADABLE, false);
        item = input.read(SYNC_ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        readSync(input);
    }

    // --- Removal --------------------------------------------------------------------------

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        boolean deferred = false;
        try {
            // Deferred: the operation's or the root's close drops what it leaves. Otherwise a run
            // is spilled first, and the default drop follows whatever a listener throws.
            deferred = level instanceof ServerLevel server && InfusionAltars.removing(server, this);
        } finally {
            if (!deferred) {
                super.preRemoveSideEffects(pos, state);
            }
        }
    }

    /** Checks a stack is one item, for the records. */
    static DataResult<ItemStack> one(ItemStack stack) {
        return stack.getCount() == 1
                ? DataResult.success(stack)
                : DataResult.error(() -> "a stack of " + stack.getCount() + ", not 1");
    }

    /** One item, as the records store it. */
    static final Codec<ItemStack> ONE = ItemStack.CODEC.validate(AltarBlockEntity::one);
}
