package com.andrewbrogoff.vftv.api.infusion;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * What one infusion upset did. See the infusion spec, "Upsets".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface UpsetEffect {
    /**
     * A pending component popped off its pedestal as an item entity.
     *
     * @param pedestal the pedestal's position
     * @param item a copy of the jolted item
     */
    record Jolt(BlockPos pedestal, ItemStack item) implements UpsetEffect {
        /** Constructed by vftv only. */
        @Internal
        public Jolt {
            pedestal = pedestal.immutable();
            item = item.copy();
        }

        /**
         * A copy of the jolted item, fresh on every call.
         *
         * @return a copy of the item
         */
        @Override
        public ItemStack item() {
            return item.copy();
        }
    }

    /**
     * Drawn essentia spilled, and is owed again.
     *
     * @param aspect the aspect spilled
     * @param amount the units spilled
     */
    record Spill(ResourceKey<Aspect> aspect, int amount) implements UpsetEffect {
        /** Constructed by vftv only. */
        @Internal
        public Spill {}
    }

    /**
     * The upset counted but had no effect: neither a jolt nor a spill was possible, or a jolt
     * failed its revalidation.
     */
    record None() implements UpsetEffect {
        /** Constructed by vftv only. */
        @Internal
        public None {}
    }
}
