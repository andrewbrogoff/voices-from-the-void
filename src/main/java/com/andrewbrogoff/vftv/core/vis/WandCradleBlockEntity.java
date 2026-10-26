package com.andrewbrogoff.vftv.core.vis;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Wand Cradle's block entity (wands spec, "Members", "Sync and state"): it holds one wand,
 * persisted as {@code {format: 1, wand}}. Trackers get the wand in the initial data and on insert
 * and take only; charging writes the wand in place and sends no packet. A record this build can't
 * read is kept raw and the cradle refuses use.
 */
public final class WandCradleBlockEntity extends RelayBlockEntity {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    private static final Logger LOGGER = LogUtils.getLogger();

    private ItemStack wand = ItemStack.EMPTY;
    private int rawFormat = FORMAT;
    private @Nullable CompoundTag rawWand;
    private boolean unreadable;

    /** A cradle block entity. */
    public WandCradleBlockEntity(BlockPos pos, BlockState state) {
        super(VisContent.cradleEntity(), pos, state);
    }

    /** The cradled wand, or empty; the stack object itself, written in place when charged. */
    public ItemStack wand() {
        return wand;
    }

    /** Whether the stored record is unreadable (kept raw; the cradle is unusable). */
    public boolean unreadable() {
        return unreadable;
    }

    /** Sets the cradled wand (an insert or a take) and sends it to trackers. */
    public void setWand(ItemStack next) {
        wand = next;
        setChanged();
        if (level instanceof ServerLevel server) {
            BlockState state = getBlockState();
            server.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_CLIENTS);
            RelayService.refreshSink(server, getBlockPos());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        rawFormat = input.getIntOr("format", FORMAT);
        Optional<CompoundTag> raw = input.read("wand", CompoundTag.CODEC);
        Optional<ItemStack> read = input.read("wand", ItemStack.CODEC);
        unreadable = rawFormat != FORMAT || (raw.isPresent() && read.isEmpty());
        rawWand = unreadable ? raw.orElse(null) : null;
        wand = unreadable ? ItemStack.EMPTY : read.orElse(ItemStack.EMPTY);
        if (unreadable && level instanceof ServerLevel) {
            LOGGER.warn("Wand cradle at {} is unreadable; kept as it was", getBlockPos());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("format", unreadable ? rawFormat : FORMAT);
        if (unreadable) {
            if (rawWand != null) {
                output.store("wand", CompoundTag.CODEC, rawWand);
            }
        } else if (!wand.isEmpty()) {
            output.store("wand", ItemStack.CODEC, wand);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            RelayService.refreshSink(server, getBlockPos());
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) {
            RelayService.dropSink(server, getBlockPos());
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            RelayService.dropSink(server, pos);
            if (!wand.isEmpty()) {
                Containers.dropItemStack(server, pos.getX(), pos.getY(), pos.getZ(), wand);
                wand = ItemStack.EMPTY;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
