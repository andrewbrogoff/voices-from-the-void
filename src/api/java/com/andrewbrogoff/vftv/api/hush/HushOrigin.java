package com.andrewbrogoff.vftv.api.hush;

import com.andrewbrogoff.vftv.api.Internal;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * What a Hush mutation comes from: the spread from a chunk's seep, or a Pealing Bell. A claim mod
 * reads it to decide whether the change may happen. See the Hush spec, "Claims".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface HushOrigin {
    /**
     * The Hush's own spread from a chunk's pooled flux.
     *
     * @param chunk the chunk whose aura pays for the change
     * @param spiller the last known player behind that chunk's flux, or null if none is known
     */
    record Seep(ChunkPos chunk, @Nullable UUID spiller) implements HushOrigin {
        /** Constructed by vftv only. */
        @Internal
        public Seep {}
    }

    /**
     * A Pealing Bell's restore, with no actor.
     *
     * @param bell the bell's position
     */
    record Bell(BlockPos bell) implements HushOrigin {
        /** Constructed by vftv only. */
        @Internal
        public Bell {
            bell = bell.immutable();
        }
    }
}
