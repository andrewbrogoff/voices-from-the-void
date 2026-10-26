package com.andrewbrogoff.vftv.core.vis;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
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
 * persisted under {@code cradle} as {@code {format: 1, wand}}. Trackers get the wand in the initial
 * data and on insert and take only; charging writes the wand in place and sends no packet. A record
 * this build can't read is kept raw and the cradle refuses use.
 */
public final class WandCradleBlockEntity extends RelayBlockEntity {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The record's key in the block entity's data. */
    static final String KEY = "cradle";

    /** The bounded record: {@code {"format": 1, "wand"?}}, no unknown key. */
    private static final Codec<ItemStack> RECORD =
            VisCodecs.strict(
                    RecordCodecBuilder.<ItemStack>create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(w -> FORMAT),
                                                    ItemStack.CODEC
                                                            .optionalFieldOf(
                                                                    "wand", ItemStack.EMPTY)
                                                            .forGetter(w -> w))
                                            .apply(
                                                    i,
                                                    (format, wand) -> {
                                                        if (format != FORMAT) {
                                                            throw new IllegalArgumentException(
                                                                    "unknown format " + format);
                                                        }
                                                        return wand;
                                                    })),
                    Set.of("format", "wand"));

    private ItemStack wand = ItemStack.EMPTY;
    private @Nullable CompoundTag raw; // the whole record while unreadable, written back unchanged

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
        return raw != null;
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
        Optional<CompoundTag> stored = input.read(KEY, CompoundTag.CODEC);
        Optional<ItemStack> read;
        try {
            read = input.read(KEY, RECORD);
        } catch (RuntimeException e) {
            read = Optional.empty(); // a record constructor's failed invariant
        }
        raw = stored.isPresent() && read.isEmpty() ? stored.get() : null;
        wand = read.orElse(ItemStack.EMPTY);
        if (raw != null && level instanceof ServerLevel) {
            LOGGER.warn("Wand cradle at {} is unreadable; kept as it was: {}", getBlockPos(), raw);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (raw != null) {
            output.store(KEY, CompoundTag.CODEC, raw);
        } else {
            output.store(KEY, RECORD, wand);
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
            if (raw != null) {
                LOGGER.warn("An unreadable wand cradle at {} was removed; lost: {}", pos, raw);
            }
            if (!wand.isEmpty()) {
                Containers.dropItemStack(server, pos.getX(), pos.getY(), pos.getZ(), wand);
                wand = ItemStack.EMPTY;
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        if (raw != null) {
            CompoundTag flag = new CompoundTag();
            flag.putBoolean("unreadable", true); // synced views never carry the raw tag
            return flag;
        }
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
