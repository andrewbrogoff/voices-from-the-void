package com.andrewbrogoff.vftv.api.crucible;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import java.util.Collections;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

/**
 * What a crucible committed: a dissolve, a craft or a spill. Item stacks are copied at construction
 * and again on every access; aspect maps are unmodifiable and in ascending id; the live handles are
 * valid only during dispatch. See the crucible spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface CruciblePayload {
    /**
     * One dissolve action: items melted into the pool.
     *
     * @param level the crucible's level
     * @param pos the crucible's position
     * @param thrower the player who threw the item, or {@code null} for none
     * @param item a copy of the item whose count is the number dissolved
     * @param aspects the aspects added to the pool, unmodifiable and in ascending id
     */
    record Dissolved(
            ServerLevel level,
            BlockPos pos,
            @Nullable ServerPlayer thrower,
            ItemStack item,
            SequencedMap<ResourceKey<Aspect>, Integer> aspects)
            implements CruciblePayload {
        /** Constructed by vftv only. */
        @Internal
        public Dissolved {
            pos = pos.immutable();
            item = item.copy();
            aspects = Collections.unmodifiableSequencedMap(new TreeMap<>(aspects));
        }

        /**
         * A fresh copy of the dissolved item, whose count is the number dissolved.
         *
         * @return a new copy on every call
         */
        @Override
        public ItemStack item() {
            return item.copy();
        }
    }

    /**
     * One crucible craft: a catalyst turned into a result, paid from the pool.
     *
     * @param level the crucible's level
     * @param pos the crucible's position
     * @param player the player whose catalyst was crafted
     * @param recipe the crafted recipe's key
     * @param catalyst a count-one copy of the catalyst
     * @param result a copy of the result
     * @param aspects the aspects spent from the pool, unmodifiable and in ascending id
     */
    record Crafted(
            ServerLevel level,
            BlockPos pos,
            ServerPlayer player,
            ResourceKey<Recipe<?>> recipe,
            ItemStack catalyst,
            ItemStack result,
            SequencedMap<ResourceKey<Aspect>, Integer> aspects)
            implements CruciblePayload {
        /** Constructed by vftv only. */
        @Internal
        public Crafted {
            pos = pos.immutable();
            catalyst = catalyst.copy();
            result = result.copy();
            aspects = Collections.unmodifiableSequencedMap(new TreeMap<>(aspects));
        }

        /**
         * A fresh count-one copy of the catalyst.
         *
         * @return a new copy on every call
         */
        @Override
        public ItemStack catalyst() {
            return catalyst.copy();
        }

        /**
         * A fresh copy of the result.
         *
         * @return a new copy on every call
         */
        @Override
        public ItemStack result() {
            return result.copy();
        }
    }

    /**
     * One non-empty spill: the pool's whole contents lost, by emptying or removal.
     *
     * @param level the crucible's level
     * @param pos the crucible's position
     * @param player the player who emptied it, or {@code null} for none
     * @param aspects the aspects spilled, missing ids included, unmodifiable and in ascending id
     * @param cause {@link SpillCause#EMPTIED} or {@link SpillCause#REMOVED}
     */
    record Spilled(
            ServerLevel level,
            BlockPos pos,
            @Nullable ServerPlayer player,
            SequencedMap<ResourceKey<Aspect>, Integer> aspects,
            SpillCause cause)
            implements CruciblePayload {
        /** Constructed by vftv only. */
        @Internal
        public Spilled {
            pos = pos.immutable();
            aspects = Collections.unmodifiableSequencedMap(new TreeMap<>(aspects));
        }
    }
}
