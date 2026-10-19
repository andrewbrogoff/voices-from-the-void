package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.LabelRecord;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A tube's block entity, and the Alembic's base: the optional label record {@code {"format": 1,
 * "label"?: <id>}}, and the load and removal hooks the topology and refresh queues need (essentia
 * spec, "The blocks").
 */
public class LabelledBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity
        implements Labels.Target {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RECORD = "vftv";
    private static final String UNREADABLE = "unreadable";

    private Retained<LabelRecord> record = new Retained.Read<>(LabelRecord.EMPTY);
    private boolean clientUnreadable;
    private boolean warned;

    /** A tube at {@code pos}. */
    public LabelledBlockEntity(BlockPos pos, BlockState state) {
        this(EssentiaContent.tubeType(), pos, state);
    }

    /** A labelled block entity of {@code type}. */
    protected LabelledBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean readable() {
        return record instanceof Retained.Read<LabelRecord> && !clientUnreadable;
    }

    @Override
    public boolean busy() {
        return false;
    }

    @Override
    public @Nullable ResourceKey<Aspect> label() {
        LabelRecord r = EssentiaRecords.read(record);
        return r == null ? null : r.label().orElse(null);
    }

    @Override
    public @Nullable ResourceKey<Aspect> held() {
        return null;
    }

    @Override
    public boolean isJar() {
        return false;
    }

    @Override
    public void setLabel(@Nullable ResourceKey<Aspect> label) {
        record =
                new Retained.Read<>(
                        new LabelRecord(EssentiaRecords.FORMAT, Optional.ofNullable(label)));
        setChanged();
        if (level instanceof ServerLevel server) {
            EssentiaService.markDirty(server, this);
            onLabelChanged(server);
        }
    }

    /** A label applied or cut: a tube retires its line and normalises its faces. */
    protected void onLabelChanged(ServerLevel server) {
        EssentiaService.lines(server).labelChanged(worldPosition);
        EssentiaTubeBlock.refresh(server, worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            EssentiaService.lines(server).loaded(worldPosition, this instanceof AlembicBlockEntity);
        }
    }

    /** A real removal (not an unload) loses an unreadable record: logged with its raw tag. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (record instanceof Retained.Unreadable<LabelRecord> u) {
            LOGGER.warn("An unreadable essentia label record at {} was removed: {}", pos, u.raw());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            EssentiaService.lines(server).removed(worldPosition);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        clientUnreadable = input.getBooleanOr(UNREADABLE, false);
        Retained<LabelRecord> before = record;
        record =
                input.read(RECORD, LabelRecord.RETAINED)
                        .orElse(new Retained.Read<>(LabelRecord.EMPTY));
        // A label replaced in place (/data merge) retires the line like a label applied by hand.
        if (level instanceof ServerLevel server && !before.equals(record)) {
            onLabelChanged(server);
        }
        if (!warned && record instanceof Retained.Unreadable<LabelRecord> u) {
            warned = true;
            LOGGER.warn("Unreadable essentia label record at {}: {}", worldPosition, u.reason());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store(RECORD, LabelRecord.RETAINED, record);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (readable()) {
            tag.store(RECORD, LabelRecord.RETAINED, record);
        } else {
            tag.putBoolean(UNREADABLE, true);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
