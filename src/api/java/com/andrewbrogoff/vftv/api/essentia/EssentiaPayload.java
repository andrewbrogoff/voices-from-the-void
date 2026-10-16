package com.andrewbrogoff.vftv.api.essentia;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import java.util.Collections;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

/**
 * What an essentia event reports: one nested record per kind. Immutable; its live handles are valid
 * only during dispatch. See the essentia logistics spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface EssentiaPayload {
    /**
     * Essentia spilled once, after the settled state: a filled jar item destroyed, or a jar's
     * unsettled excess at removal.
     *
     * @param level the level it spilled in; valid only during dispatch
     * @param pos the destroyed item's position, or the jar's
     * @param aspects the aspects spilled and their amounts, in ascending id, missing ids included;
     *     totalling 1 to {@link EssentiaLimits#MAX_ESSENTIA_SPILL}
     * @param cause {@link SpillCause#DESTROYED} for a destroyed jar item, {@link
     *     SpillCause#REMOVED} for a jar's unsettled excess
     */
    record Spilled(
            ServerLevel level,
            BlockPos pos,
            SequencedMap<ResourceKey<Aspect>, Integer> aspects,
            SpillCause cause)
            implements EssentiaPayload {
        /** Constructed by vftv only. */
        @Internal
        public Spilled {
            pos = pos.immutable();
            aspects = Collections.unmodifiableSequencedMap(new TreeMap<>(aspects));
        }
    }
}
