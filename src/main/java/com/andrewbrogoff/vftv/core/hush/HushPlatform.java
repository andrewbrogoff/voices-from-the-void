package com.andrewbrogoff.vftv.core.hush;

import com.andrewbrogoff.vftv.api.hush.FluxPayload;
import com.andrewbrogoff.vftv.api.hush.HushSpreadPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The Hush's platform seam (architecture, "Seams"): the {@code vftv:aura} attachment and the event
 * posts, implemented by {@code neoforge.hush} and installed once at mod construction.
 */
public interface HushPlatform {
    /** The chunk's aura, or null when it has none; never creates one. */
    @Nullable Aura aura(LevelChunk chunk);

    /** Attaches a newly created aura (which syncs it once). */
    void createAura(LevelChunk chunk, Aura aura);

    /** Sends the chunk's aura, mutated in place, to the players tracking it. */
    void syncAura(LevelChunk chunk);

    /**
     * Posts {@code HushSpreadEvent}: whether the mutation may go ahead. Fails closed: a cancelled
     * post and a throwing listener both answer false.
     */
    boolean spread(HushSpreadPayload payload);

    /** Posts {@code FluxEvent}; a throwing listener is logged once and the state stands. */
    void flux(FluxPayload payload);

    /** Whether {@code player} may break the block at {@code pos}: an uncancelled break event. */
    boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);
}
