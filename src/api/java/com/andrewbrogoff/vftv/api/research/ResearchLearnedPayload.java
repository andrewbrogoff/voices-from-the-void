package com.andrewbrogoff.vftv.api.research;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.warp.WarpPreview;
import java.util.Collections;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the learning of one research entry reported. Immutable; the player handle is valid only
 * during dispatch. See the research spec, "Events".
 *
 * @param player the player who learned the entry
 * @param research the entry learned
 * @param source how it was learned
 * @param warp the warp actually applied by the commit, as the warp spec's actual deltas; all zero
 *     when none was applied
 * @param points the points actually paid per aspect by the Learn or purchase (placements are free),
 *     in ascending aspect id; empty when nothing was paid
 */
public record ResearchLearnedPayload(
        ServerPlayer player,
        ResourceKey<Research> research,
        ResearchSource source,
        WarpPreview warp,
        SequencedMap<ResourceKey<Aspect>, Integer> points) {

    /** Constructed by vftv only. */
    @Internal
    public ResearchLearnedPayload {
        points = Collections.unmodifiableSequencedMap(new TreeMap<>(points));
    }
}
