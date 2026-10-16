package com.andrewbrogoff.vftv.api.research;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.server.level.ServerPlayer;

/**
 * What one Stage step reported. Immutable; the player handle is valid only during dispatch. See the
 * research spec, "Stage gates".
 *
 * @param player the player whose Stage changed
 * @param stage the Stage reached, 0–4
 * @param previousStage the Stage before the step, 0–4
 * @param source how the step came about
 */
public record StageReachedPayload(
        ServerPlayer player, int stage, int previousStage, ResearchSource source) {

    /** Constructed by vftv only. */
    @Internal
    public StageReachedPayload {}
}
