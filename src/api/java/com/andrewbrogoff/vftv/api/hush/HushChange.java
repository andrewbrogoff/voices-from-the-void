package com.andrewbrogoff.vftv.api.hush;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a Hush mutation would change. See the Hush spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface HushChange {
    /**
     * A block swap, for {@link HushSpreadKind#CONVERT} and a block {@link HushSpreadKind#RESTORE}.
     *
     * @param before the state in the cell now
     * @param after the state the cell would hold
     */
    record Block(BlockState before, BlockState after) implements HushChange {
        /** Constructed by vftv only. */
        @Internal
        public Block {}
    }

    /**
     * A node's type change, for {@link HushSpreadKind#NODE} and a node {@link
     * HushSpreadKind#RESTORE}.
     *
     * @param id the node's id
     * @param before the node's type now
     * @param after the type it would take
     */
    record Node(long id, ResourceKey<NodeType> before, ResourceKey<NodeType> after)
            implements HushChange {
        /** Constructed by vftv only. */
        @Internal
        public Node {}
    }

    /**
     * A creature rising, for {@link HushSpreadKind#RISE}.
     *
     * @param type the entity type that would rise
     */
    record Rise(EntityType<?> type) implements HushChange {
        /** Constructed by vftv only. */
        @Internal
        public Rise {}
    }
}
