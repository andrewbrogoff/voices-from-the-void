package com.andrewbrogoff.vftv.api.infusion;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import java.util.Collections;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.jspecify.annotations.Nullable;

/**
 * What an infusion event reports: one nested record per kind. Immutable, captured before any
 * listener runs; its live handles are valid only during dispatch. See the infusion spec, "Events".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface InfusionPayload {
    /**
     * A run started: the commit wrote its plan.
     *
     * @param level the bowl's level; valid only during dispatch
     * @param pos the bowl's position
     * @param bowlId the bowl's id
     * @param seq the run's sequence number on this bowl, from 1
     * @param starter the starting player's UUID
     * @param recipe the recipe's key
     * @param plan the committed assessment
     */
    record Started(
            ServerLevel level,
            BlockPos pos,
            long bowlId,
            int seq,
            UUID starter,
            ResourceKey<Recipe<?>> recipe,
            InfusionPlan plan)
            implements InfusionPayload {
        /** Constructed by vftv only. */
        @Internal
        public Started {
            pos = pos.immutable();
        }
    }

    /**
     * A run upset.
     *
     * @param level the bowl's level; valid only during dispatch
     * @param pos the bowl's position
     * @param seq the run's sequence number
     * @param effect what the upset did
     */
    record Upset(ServerLevel level, BlockPos pos, int seq, UpsetEffect effect)
            implements InfusionPayload {
        /** Constructed by vftv only. */
        @Internal
        public Upset {
            pos = pos.immutable();
        }
    }

    /**
     * Drawn essentia spilled from a run. The flux rules that apply are on the event, not here.
     *
     * @param level the bowl's level; valid only during dispatch
     * @param pos the bowl's position
     * @param aspects the aspects spilled and their amounts, in ascending id: at most 8 aspects
     *     totalling at most 8,000
     * @param cause {@link SpillCause#UPSET}, {@link SpillCause#ABANDONED} or {@link
     *     SpillCause#REMOVED}
     */
    record Spilled(
            ServerLevel level,
            BlockPos pos,
            SequencedMap<ResourceKey<Aspect>, Integer> aspects,
            SpillCause cause)
            implements InfusionPayload {
        /** Constructed by vftv only. */
        @Internal
        public Spilled {
            pos = pos.immutable();
            aspects = Collections.unmodifiableSequencedMap(new TreeMap<>(aspects));
        }
    }

    /**
     * A run finished: the bowl holds the output.
     *
     * @param level the bowl's level; valid only during dispatch
     * @param pos the bowl's position
     * @param seq the run's sequence number
     * @param starter the starting player's UUID; the player may be offline
     * @param recipe the recipe's key
     * @param output a copy of the output; copied on every access
     */
    record Completed(
            ServerLevel level,
            BlockPos pos,
            int seq,
            UUID starter,
            ResourceKey<Recipe<?>> recipe,
            ItemStack output)
            implements InfusionPayload {
        /** Constructed by vftv only. */
        @Internal
        public Completed {
            pos = pos.immutable();
            output = output.copy();
        }

        /**
         * A copy of the output, fresh on every call.
         *
         * @return a copy of the output
         */
        @Override
        public ItemStack output() {
            return output.copy();
        }
    }

    /**
     * A run was abandoned; the centre item stays in the bowl.
     *
     * @param level the bowl's level; valid only during dispatch
     * @param pos the bowl's position
     * @param seq the run's sequence number
     * @param cause why it was abandoned
     * @param player the abandoning player for {@link AbandonCause#PLAYER}, or {@code null}
     * @param drawn the essentia the run had drawn, in ascending id
     */
    record Abandoned(
            ServerLevel level,
            BlockPos pos,
            int seq,
            AbandonCause cause,
            @Nullable ServerPlayer player,
            SequencedMap<ResourceKey<Aspect>, Integer> drawn)
            implements InfusionPayload {
        /** Constructed by vftv only. */
        @Internal
        public Abandoned {
            pos = pos.immutable();
            drawn = Collections.unmodifiableSequencedMap(new TreeMap<>(drawn));
        }
    }
}
