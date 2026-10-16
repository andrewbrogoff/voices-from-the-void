package com.andrewbrogoff.vftv.api.hush;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.flux.FluxClearSource;
import com.andrewbrogoff.vftv.api.flux.FluxSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * A settled change to one chunk's flux, as {@code FluxEvent} reports it. Every amount is in flux, 0
 * to {@link HushLimits#FLUX_FORMAT_MAX}. See the Hush spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface FluxPayload {
    /**
     * A deposit: {@code generated = retained + pending + overflow}.
     *
     * @param level the level; valid only during dispatch
     * @param chunk the chunk deposited into
     * @param seep the aura's seep after the deposit, or null if it has none
     * @param generated the flux the producer generated, at least 1
     * @param retained the part now in the chunk's readable aura
     * @param pending the part held for an unloaded or unreadable chunk
     * @param overflow the part lost to a store's bound
     * @param source what generated it
     * @param player the player behind it, or null if none
     */
    record Added(
            ServerLevel level,
            ChunkPos chunk,
            @Nullable BlockPos seep,
            int generated,
            int retained,
            int pending,
            int overflow,
            FluxSource source,
            @Nullable ServerPlayer player)
            implements FluxPayload {
        /** Constructed by vftv only. */
        @Internal
        public Added {
            seep = seep == null ? null : seep.immutable();
        }
    }

    /**
     * A clear: {@code fluxBefore - amount = fluxAfter}.
     *
     * @param level the level; valid only during dispatch
     * @param chunk the chunk cleared
     * @param seep the aura's seep, or null if it has none
     * @param amount the flux cleared, at least 1
     * @param source what cleared it
     * @param player the player who cleared it, or null if none (always null for a peal)
     * @param fluxBefore the aura's flux before the clear
     * @param fluxAfter the aura's flux after the clear
     */
    record Cleared(
            ServerLevel level,
            ChunkPos chunk,
            @Nullable BlockPos seep,
            int amount,
            FluxClearSource source,
            @Nullable ServerPlayer player,
            int fluxBefore,
            int fluxAfter)
            implements FluxPayload {
        /** Constructed by vftv only. */
        @Internal
        public Cleared {
            seep = seep == null ? null : seep.immutable();
        }
    }
}
