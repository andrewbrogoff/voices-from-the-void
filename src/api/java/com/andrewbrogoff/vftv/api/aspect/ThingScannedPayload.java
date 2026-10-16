package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Internal;
import java.util.Collections;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a committed scan reported. See the aspects spec, "Events".
 *
 * @param player the player who struck; a live handle, valid only during dispatch
 * @param key the scan key the strike recorded
 * @param kind what the strike was aimed at
 * @param outcome how the strike changed the ledger entry
 * @param complete whether the key is now fully scanned
 * @param points the points actually awarded per aspect, possibly zero; unmodifiable and in
 *     ascending aspect id
 */
public record ThingScannedPayload(
        ServerPlayer player,
        ScanKey key,
        ScanTargetKind kind,
        ScanOutcome outcome,
        boolean complete,
        SequencedMap<ResourceKey<Aspect>, Integer> points) {
    /** Constructed by vftv only. */
    @Internal
    public ThingScannedPayload {
        points = Collections.unmodifiableSequencedMap(new TreeMap<>(points));
    }
}
