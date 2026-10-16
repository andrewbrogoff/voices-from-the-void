package com.andrewbrogoff.vftv.api.hush;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * One Hush mutation about to happen, as {@code HushSpreadEvent} reports it. See the Hush spec,
 * "Claims" and "Events".
 *
 * @param kind the kind of mutation
 * @param level the level it happens in; valid only during dispatch
 * @param pos the cell it changes
 * @param change what it would change
 * @param origin what it comes from
 */
public record HushSpreadPayload(
        HushSpreadKind kind,
        ServerLevel level,
        BlockPos pos,
        HushChange change,
        HushOrigin origin) {
    /** Constructed by vftv only. */
    @Internal
    public HushSpreadPayload {
        pos = pos.immutable();
    }
}
