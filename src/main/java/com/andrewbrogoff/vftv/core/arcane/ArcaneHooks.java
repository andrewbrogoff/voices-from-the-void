package com.andrewbrogoff.vftv.core.arcane;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What arcane crafting needs from the platform, installed once from {@code neoforge.arcane}: the
 * {@code BreakBlockEvent} a turn asks first, and NeoForge's crafted event. The defaults allow every
 * turn and post nothing, for unit tests.
 */
public interface ArcaneHooks {
    /** Posts {@code BreakBlockEvent} for the state with the player; true when it was cancelled. */
    boolean breakCancelled(Level level, BlockPos pos, BlockState state, Player player);

    /** Posts {@code PlayerEvent.ItemCraftedEvent} with a copy of the result and a grid snapshot. */
    void crafted(Player player, ItemStack result, Container grid);

    /** No platform. */
    ArcaneHooks NONE =
            new ArcaneHooks() {
                @Override
                public boolean breakCancelled(
                        Level level, BlockPos pos, BlockState state, Player player) {
                    return false;
                }

                @Override
                public void crafted(Player player, ItemStack result, Container grid) {}
            };
}
