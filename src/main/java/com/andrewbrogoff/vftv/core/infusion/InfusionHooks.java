package com.andrewbrogoff.vftv.core.infusion;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * What infusion needs from the platform, installed once from {@code neoforge.infusion}: the {@code
 * InfusionEvent} publisher, NeoForge's crafted event, the transaction lifecycle, and the draw from
 * the box's vessels with its ledger journal (infusion spec, "Drawing essentia"). The defaults do
 * nothing, for unit tests.
 */
public interface InfusionHooks {
    /** The run's ledger, which the platform journals in the draw's root transaction. */
    interface Ledger {
        /** A copy of {@code drawn}, for a snapshot. */
        int[] snapshot();

        /** Restores {@code drawn} from a snapshot. */
        void restore(int[] snapshot);

        /** Credits {@code amount} units of aspect {@code index} drawn. */
        void credit(int index, int amount);
    }

    /**
     * Posts {@code InfusionEvent} with its flux rules; a throwing listener is the caller's to log.
     */
    void post(InfusionPayload payload, @Nullable FluxRules rules);

    /** Posts {@code PlayerEvent.ItemCraftedEvent}. */
    void crafted(Player player, ItemStack result, Container centre);

    /** Whether no transfer transaction is open or closing: {@code Lifecycle.NONE}. */
    boolean idle();

    /**
     * What the box's sources hold now of each aspect: the sum of {@code getAmountAsLong} over the
     * slots holding it, saturated in {@code long}.
     */
    long[] heldNow(ServerLevel level, BlockPos bowl, List<ResourceKey<Aspect>> aspects);

    /**
     * One draw in one root transaction: for each aspect in order with {@code need[i] > 0}, a nested
     * extract from each source in turn, crediting the ledger in the same transaction; the first
     * aspect that yields anything ends the search. May throw, before or after its commit.
     */
    void draw(
            ServerLevel level,
            BlockPos bowl,
            List<ResourceKey<Aspect>> aspects,
            int[] need,
            Ledger ledger);

    /** Invalidates the capabilities at a position. */
    void invalidate(Level level, BlockPos pos);

    /** No platform. */
    InfusionHooks NONE =
            new InfusionHooks() {
                @Override
                public void post(InfusionPayload payload, @Nullable FluxRules rules) {}

                @Override
                public void crafted(Player player, ItemStack result, Container centre) {}

                @Override
                public boolean idle() {
                    return true;
                }

                @Override
                public long[] heldNow(
                        ServerLevel level, BlockPos bowl, List<ResourceKey<Aspect>> aspects) {
                    return new long[aspects.size()];
                }

                @Override
                public void draw(
                        ServerLevel level,
                        BlockPos bowl,
                        List<ResourceKey<Aspect>> aspects,
                        int[] need,
                        Ledger ledger) {}

                @Override
                public void invalidate(Level level, BlockPos pos) {}
            };
}
