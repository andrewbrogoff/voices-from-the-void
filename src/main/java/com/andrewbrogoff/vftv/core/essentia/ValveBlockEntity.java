package com.andrewbrogoff.vftv.core.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The valve's block entity: no data and no ticker, only the load and removal hooks the topology and
 * refresh queues need (essentia spec, "The blocks").
 */
public final class ValveBlockEntity extends BlockEntity {
    /** A valve at {@code pos}. */
    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(EssentiaContent.valveType(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            EssentiaService.lines(server).loaded(worldPosition, false);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            EssentiaService.lines(server).removed(worldPosition);
        }
    }
}
