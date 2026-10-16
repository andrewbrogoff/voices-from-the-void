package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * What a golem act moves: items or essentia of one aspect. See the golems spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface GolemResource {
    /**
     * Items, as a copy of a stack with their components.
     *
     * @param stack the items; copied at construction and on every access
     */
    record Item(ItemStack stack) implements GolemResource {
        /** Constructed by vftv only. */
        @Internal
        public Item {
            stack = stack.copy();
        }

        /**
         * The items, as a fresh copy on every call.
         *
         * @return a copy of the stack
         */
        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }

    /**
     * Essentia of one aspect.
     *
     * @param aspect the aspect
     */
    record Essentia(ResourceKey<Aspect> aspect) implements GolemResource {
        /** Constructed by vftv only. */
        @Internal
        public Essentia {}
    }
}
