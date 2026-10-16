package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.core.GlobalPos;
import org.jspecify.annotations.Nullable;

/**
 * A node's identity and, while it is placed, its dimension and position. See the wands spec,
 * "Events".
 *
 * @param id the node's stable id, 1 or more
 * @param where the node block's dimension and position, or {@code null} while a Bell Jar carries it
 */
public record NodeRef(long id, @Nullable GlobalPos where) {
    /** Constructed by vftv only. */
    @Internal
    public NodeRef {
        if (where != null) {
            where = GlobalPos.of(where.dimension(), where.pos().immutable());
        }
    }
}
