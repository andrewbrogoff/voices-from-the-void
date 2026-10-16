package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What a golem event reports: one variant per kind. Golems and owners are UUIDs; an owner may be
 * offline. See the golems spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface GolemPayload {
    /**
     * A golem was placed from its item.
     *
     * @param golem the golem's UUID
     * @param owner the owner's UUID (the placer)
     * @param level the level; a live handle, valid only during dispatch
     * @param pos where it was placed
     * @param body its material
     * @param core its core item
     */
    record Placed(
            UUID golem,
            UUID owner,
            ServerLevel level,
            BlockPos pos,
            ResourceKey<GolemMaterial> body,
            ResourceKey<Item> core)
            implements GolemPayload {
        /** Constructed by vftv only. */
        @Internal
        public Placed {
            pos = pos.immutable();
        }
    }

    /**
     * A golem was picked up into its item.
     *
     * @param golem the golem's UUID
     * @param owner the owner's UUID
     * @param level the level; a live handle, valid only during dispatch
     * @param pos where it stood
     */
    record PickedUp(UUID golem, UUID owner, ServerLevel level, BlockPos pos)
            implements GolemPayload {
        /** Constructed by vftv only. */
        @Internal
        public PickedUp {
            pos = pos.immutable();
        }
    }

    /**
     * A golem act moved something.
     *
     * @param golem the golem's UUID
     * @param owner the owner's UUID
     * @param level the level; a live handle, valid only during dispatch
     * @param pos the marked block's position
     * @param face the marked face
     * @param role the mark's role
     * @param resource what moved
     * @param amount the amount moved, in items or essentia units (1 for FEED and TIP; the picked-up
     *     count for COLLECT)
     */
    record Moved(
            UUID golem,
            UUID owner,
            ServerLevel level,
            BlockPos pos,
            Direction face,
            GolemRole role,
            GolemResource resource,
            int amount)
            implements GolemPayload {
        /** Constructed by vftv only. */
        @Internal
        public Moved {
            pos = pos.immutable();
        }
    }

    /**
     * A dying golem's carried essentia spilled, after its drops and before {@link Died}.
     *
     * @param golem the golem's UUID
     * @param level the level; a live handle, valid only during dispatch
     * @param pos the death position
     * @param aspect the spilled aspect
     * @param amount the spilled essentia units
     * @param cause always {@link SpillCause#DIED}
     */
    record Spilled(
            UUID golem,
            ServerLevel level,
            BlockPos pos,
            ResourceKey<Aspect> aspect,
            int amount,
            SpillCause cause)
            implements GolemPayload {
        /** Constructed by vftv only. */
        @Internal
        public Spilled {
            pos = pos.immutable();
        }
    }

    /**
     * A golem died, after its drops spawned.
     *
     * @param golem the golem's UUID
     * @param owner the owner's UUID
     * @param level the level; a live handle, valid only during dispatch
     * @param pos the death position
     * @param drops what dropped; copied at construction and on every access
     */
    record Died(UUID golem, UUID owner, ServerLevel level, BlockPos pos, List<ItemStack> drops)
            implements GolemPayload {
        /** Constructed by vftv only. */
        @Internal
        public Died {
            pos = pos.immutable();
            drops = drops.stream().map(ItemStack::copy).toList();
        }

        /**
         * What dropped, as fresh copies on every call.
         *
         * @return an unmodifiable list of stack copies
         */
        @Override
        public List<ItemStack> drops() {
            return drops.stream().map(ItemStack::copy).toList();
        }
    }
}
