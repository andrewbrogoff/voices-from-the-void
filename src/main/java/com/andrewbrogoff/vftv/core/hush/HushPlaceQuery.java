package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.core.warp.VoidPlaceQuery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;

/**
 * {@code HushPlaceQuery} (Hush spec, "Warp context"): a place is Void-touched iff its chunk is
 * loaded and the block at it or the one below is in {@code #vftv:hushed}. Two reads of one loaded
 * chunk, pure, never loading. The client's muffling asks the same question.
 */
public final class HushPlaceQuery implements VoidPlaceQuery {
    /** The one instance. */
    public static final HushPlaceQuery INSTANCE = new HushPlaceQuery();

    private HushPlaceQuery() {}

    @Override
    public boolean test(ServerLevel level, BlockPos pos) {
        return inHush(level, pos);
    }

    /** The query over any level, client or server: two reads of a loaded chunk. */
    public static boolean inHush(Level level, BlockPos pos) {
        ChunkAccess chunk =
                level.getChunkSource()
                        .getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        return hushed(chunk, pos) || hushed(chunk, pos.below());
    }

    private static boolean hushed(@Nullable ChunkAccess chunk, BlockPos pos) {
        return chunk != null && chunk.getBlockState(pos).is(HushKeys.HUSHED);
    }
}
