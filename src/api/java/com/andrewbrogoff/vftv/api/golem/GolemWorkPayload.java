package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Internal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * A golem act about to run at a mark, before any capability call or spawn. See the golems spec, "An
 * act".
 *
 * @param golem the golem's UUID
 * @param owner the owner's UUID; the owner may be offline
 * @param level the golem's level; a live handle, valid only during dispatch
 * @param pos the marked block's position
 * @param face the marked face
 * @param role the mark's role
 * @param resource what the act plans to move
 * @param amount the planned amount, in items or essentia units: a ceiling, never the move
 */
public record GolemWorkPayload(
        UUID golem,
        UUID owner,
        ServerLevel level,
        BlockPos pos,
        Direction face,
        GolemRole role,
        GolemResource resource,
        int amount) {
    /** Constructed by vftv only. */
    @Internal
    public GolemWorkPayload {
        pos = pos.immutable();
    }
}
