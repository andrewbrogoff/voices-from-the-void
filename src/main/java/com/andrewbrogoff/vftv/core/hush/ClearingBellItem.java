package com.andrewbrogoff.vftv.core.hush;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * {@code vftv:clearing_bell} (Hush spec, "The Clearing Bell"): used in either hand, on air or on a
 * block whose own use passed, it rings: clearing the chunk underfoot and restoring the hushed cells
 * around the player.
 */
public final class ClearingBellItem extends Item {
    /** A Clearing Bell. */
    public ClearingBellItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            HushBells.ring(sp, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer sp) {
            HushBells.ring(sp, context.getHand());
        }
        return InteractionResult.SUCCESS;
    }
}
