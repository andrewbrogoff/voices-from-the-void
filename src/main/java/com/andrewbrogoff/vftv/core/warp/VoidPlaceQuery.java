package com.andrewbrogoff.vftv.core.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A Void-touched place (warp spec, "Context", clause 3), registered at init with {@link
 * WarpService#registerPlaceQuery} by the feature that owns the place: the Hush for hushed ground,
 * worldgen for Choir sites, the Eldritch tier for the Road.
 *
 * <p>A query must be pure, read only loaded data, never load a chunk, and do work bounded by what
 * is indexed for that position's chunk. A position whose answer would need an unloaded chunk
 * answers false.
 */
@FunctionalInterface
public interface VoidPlaceQuery {
    /** Whether {@code pos} in {@code level} is Void-touched. */
    boolean test(ServerLevel level, BlockPos pos);
}
