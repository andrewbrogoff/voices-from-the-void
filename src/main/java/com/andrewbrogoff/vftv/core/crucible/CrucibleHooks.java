package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.api.crucible.CruciblePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What the crucible needs from the platform, installed once from {@code neoforge.crucible}: the
 * {@code CrucibleEvent} publisher, NeoForge's crafted event, the two entity marks (attachments
 * {@code core} never sees) and capability invalidation. The defaults do nothing, for unit tests.
 */
public interface CrucibleHooks {
    /** Posts {@code CrucibleEvent}; a throwing listener is the platform's to log. */
    void post(CruciblePayload payload);

    /** Posts {@code PlayerEvent.ItemCraftedEvent}. */
    void crafted(Player player, ItemStack result, Container catalyst);

    /** Whether the entity carries {@code vftv:crucible_refused}. */
    boolean refused(ItemEntity entity);

    /** Sets {@code vftv:crucible_refused}. */
    void markRefused(ItemEntity entity);

    /** Whether the entity carries {@code vftv:crucible_dissolve}. */
    boolean dissolve(ItemEntity entity);

    /** Sets {@code vftv:crucible_dissolve}. */
    void markDissolve(ItemEntity entity);

    /** Invalidates the capabilities at a position. */
    void invalidate(Level level, BlockPos pos);

    /** No platform. */
    CrucibleHooks NONE =
            new CrucibleHooks() {
                @Override
                public void post(CruciblePayload payload) {}

                @Override
                public void crafted(Player player, ItemStack result, Container catalyst) {}

                @Override
                public boolean refused(ItemEntity entity) {
                    return false;
                }

                @Override
                public void markRefused(ItemEntity entity) {}

                @Override
                public boolean dissolve(ItemEntity entity) {
                    return false;
                }

                @Override
                public void markDissolve(ItemEntity entity) {}

                @Override
                public void invalidate(Level level, BlockPos pos) {}
            };
}
