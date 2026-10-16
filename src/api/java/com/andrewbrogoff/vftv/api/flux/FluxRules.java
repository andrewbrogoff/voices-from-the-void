package com.andrewbrogoff.vftv.api.flux;

import com.andrewbrogoff.vftv.api.Experimental;
import com.andrewbrogoff.vftv.api.Internal;

/**
 * The flux and Hush tuning in force, with the three Hush server switches: a fixed, immutable
 * snapshot that every player receives, and that an infusion run freezes. Each tuning value is
 * within the range the Hush spec's "Tuning and limits" gives for its key; the fixed limits are
 * {@code HushLimits}'.
 *
 * <p>Experimental, because {@code InfusionEvent.fluxRules()} exposes it; a minor may reshape it.
 * See the Hush spec, "Sync and state".
 *
 * @param format the record's format, 1
 * @param generation a hash of {@code format}, every value in key order and the switches, so equal
 *     rules share a generation across restarts
 * @param fluxEssentiaPm {@code flux.essentia_pm}: flux per 1000 essentia spilled
 * @param fluxTaxPm {@code flux.tax_pm}: flux per 1000 vis of warp surcharge
 * @param fluxVoidPm {@code flux.void_pm}: flux per 1000 vis of Void work
 * @param hushOnset {@code hush.onset}: the least pooled flux that spreads, with the block cost
 * @param hushBlockCost {@code hush.block_cost}: flux spent per block hushed
 * @param hushNodeCost {@code hush.node_cost}: flux spent per node hushed
 * @param hushInterval {@code hush.interval}: ticks between a chunk's attempts
 * @param hushWalk {@code hush.walk}: steps per attempt
 * @param hushRadius {@code hush.radius}: blocks from the seep
 * @param hushMaxMisses {@code hush.max_misses}: misses before a chunk saturates
 * @param hushPlayerChunks {@code hush.player_chunks}: attendance, in Chebyshev chunks
 * @param hollowEvery {@code hollow.every}: conversions per Hollow turn
 * @param hollowFlux {@code hollow.flux}: flux spent per Hollow risen
 * @param hollowMaxNear {@code hollow.max_near}: the loaded Hollows a seep admits
 * @param hollowMinPlayerDistance {@code hollow.min_player_distance}: blocks
 * @param bellFlux {@code bell.flux}: flux a Clearing Bell ring clears
 * @param bellRestore {@code bell.restore}: hushed blocks a ring restores
 * @param bellRadius {@code bell.radius}: blocks
 * @param bellCooldown {@code bell.cooldown}: ticks
 * @param pealInterval {@code peal.interval}: ticks between a Pealing Bell's peals
 * @param pealFlux {@code peal.flux}: flux a peal clears
 * @param pealRestore {@code peal.restore}: hushed blocks a peal restores
 * @param pealRadius {@code peal.radius}: blocks
 * @param pealScan {@code peal.scan}: cells a peal examines
 * @param pealCapacity {@code peal.capacity}: the Sonus a Pealing Bell holds
 * @param pealSuction {@code peal.suction}: the Pealing Bell's rank on the essentia suction scale
 * @param spread the server switch {@code hush.spread}
 * @param hollows the server switch {@code hush.hollows}
 * @param unattended the server switch {@code hush.unattended}
 */
@Experimental
public record FluxRules(
        int format,
        long generation,
        int fluxEssentiaPm,
        int fluxTaxPm,
        int fluxVoidPm,
        int hushOnset,
        int hushBlockCost,
        int hushNodeCost,
        int hushInterval,
        int hushWalk,
        int hushRadius,
        int hushMaxMisses,
        int hushPlayerChunks,
        int hollowEvery,
        int hollowFlux,
        int hollowMaxNear,
        int hollowMinPlayerDistance,
        int bellFlux,
        int bellRestore,
        int bellRadius,
        int bellCooldown,
        int pealInterval,
        int pealFlux,
        int pealRestore,
        int pealRadius,
        int pealScan,
        int pealCapacity,
        int pealSuction,
        boolean spread,
        boolean hollows,
        boolean unattended) {
    /** Constructed by vftv only. */
    @Internal
    public FluxRules {}
}
