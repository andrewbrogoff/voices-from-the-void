package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The node block entity (wands spec, "Node state", "Sync and state"): it holds the node's state
 * under {@code node}, kept raw when unreadable. Trackers get the full state in the chunk's initial
 * data and then in block-entity update packets; an unreadable record syncs only a flag.
 */
public final class NodeBlockEntity extends BlockEntity {
    /** The state's key in the block entity's data. */
    static final String KEY = "node";

    /** The state's codec, kept raw when unreadable. */
    public static final Codec<Retained<NodeState>> CODEC = RawRecordCodec.wrap(NodeState.BOUNDED);

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The client's per-tick hook (ambient glints and the hum), installed by the client feature. */
    public interface ClientTicker {
        /** One client tick of one node. */
        void tick(Level level, BlockPos pos, NodeBlockEntity node);
    }

    private static volatile ClientTicker clientTicker = (l, p, n) -> {};

    private @Nullable Retained<NodeState> record;
    private boolean warned;

    /** A node block entity with no state yet. */
    public NodeBlockEntity(BlockPos pos, BlockState state) {
        super(VisContent.nodeEntity(), pos, state);
    }

    /** Installs the client tick; called once from the client feature. */
    public static void installClientTicker(ClientTicker ticker) {
        clientTicker = ticker;
    }

    static void clientTick(Level level, BlockPos pos, NodeBlockEntity node) {
        clientTicker.tick(level, pos, node);
    }

    /** The stored record, or null when the block holds none. */
    public @Nullable Retained<NodeState> record() {
        return record;
    }

    /** The readable state, or null when there is none or it is unreadable. */
    public @Nullable NodeState state() {
        return record instanceof Retained.Read<NodeState> r ? r.value() : null;
    }

    /** Whether the record is unreadable (or, on a client, synced as unreadable). */
    public boolean unreadable() {
        return record instanceof Retained.Unreadable<NodeState>;
    }

    /** Replaces the state and marks the block entity changed (for saving, not yet for sync). */
    public void setState(NodeState state) {
        record = new Retained.Read<>(state);
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        record = input.read(KEY, CODEC).orElse(null);
        if (input.getBooleanOr("unreadable", false)) {
            record = new Retained.Unreadable<>(new CompoundTag(), "unreadable on the server");
        }
        if (record instanceof Retained.Unreadable<NodeState> u
                && level instanceof ServerLevel
                && !warned) {
            warned = true;
            LOGGER.warn("Node block entity at {} is unreadable: {}", getBlockPos(), u.reason());
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (record != null) {
            output.store(KEY, CODEC, record);
        }
    }

    @Override
    public void onLoad() {
        if (level instanceof ServerLevel server) {
            if (record instanceof Retained.Unreadable<NodeState> u && !warned) {
                warned = true;
                LOGGER.warn("Node block entity at {} is unreadable: {}", getBlockPos(), u.reason());
            }
            NodeService.onBlockEntityLoad(server, getBlockPos());
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            NodeService.onBlockRemoved(server, pos, this);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (level instanceof ServerLevel server && state() != null) {
            NodeService.settle(server, this);
        }
        switch (record) {
            case Retained.Read<NodeState> r ->
                    tag.put(
                            KEY,
                            NodeState.BOUNDED
                                    .encodeStart(
                                            registries.createSerializationContext(NbtOps.INSTANCE),
                                            r.value())
                                    .getOrThrow());
            case Retained.Unreadable<NodeState> u -> tag.putBoolean("unreadable", true);
            case null -> {}
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
