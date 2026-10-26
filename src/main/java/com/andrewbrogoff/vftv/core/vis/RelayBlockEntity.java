package com.andrewbrogoff.vftv.core.vis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Node Tap's or Vis Relay's block entity (wands spec, "Members"): it holds no data. It exists so
 * that {@code getBlockEntitiesPos()} and {@code onLoad} find every member, as they find nodes; its
 * load and removal queue the position for the membership queue.
 */
public class RelayBlockEntity extends BlockEntity {
    /** The platform's per-instance handler (the tap's essentia adapter), owned by the platform. */
    public @org.jspecify.annotations.Nullable Object platformHandler;

    /** A member block entity of the block's own type. */
    public RelayBlockEntity(BlockPos pos, BlockState state) {
        this(VisContent.relayEntity(state), pos, state);
    }

    /** A member block entity of {@code type}. */
    protected RelayBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        if (level instanceof ServerLevel server) {
            RelayService.queue(server, getBlockPos());
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            RelayService.queue(server, pos);
        }
    }
}
