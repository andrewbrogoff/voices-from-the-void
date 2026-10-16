package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A committed vis spend. The live handles are valid only during dispatch. See the wands spec,
 * "Events".
 *
 * @param player the player who spent the vis
 * @param level the level the spend happened in
 * @param pos the position the spend happened at
 * @param context what the spend was for
 * @param quote the price paid, with its resolved factors and spill
 */
public record VisSpentPayload(
        ServerPlayer player, ServerLevel level, BlockPos pos, VisContext context, VisQuote quote) {
    /** Constructed by vftv only. */
    @Internal
    public VisSpentPayload {
        pos = pos.immutable();
    }
}
