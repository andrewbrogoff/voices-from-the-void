package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * {@code vftv:wand} (wands spec, "The wand", "Hand-tapping"): one item whose parts are data
 * components. Use on a node taps (or refuses, consuming the click); any other block passes to its
 * own use. The use ticks, release and stop dispatch to the player's session slot.
 */
public final class WandItem extends Item {
    /** The item bar's colour, {@code teal2}. */
    private static final int BAR = 0x82c9bc;

    /** A wand with the given properties. */
    public WandItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * The tap's start: runs before the block's own use, outside NeoForge's placement snapshot,
     * after spawn protection and {@code RightClickBlock} have applied.
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.getBlockState(context.getClickedPos()).is(VisContent.node())
                || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            return Tapping.start(player, context.getHand(), stack, context.getClickedPos());
        }
        if (Tapping.clientMayStart(context.getPlayer(), stack, context.getClickedPos())) {
            context.getPlayer().startUsingItem(context.getHand());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return VisLimits.USE_DURATION_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (entity instanceof ServerPlayer player) {
            Tapping.Session session = Tapping.session(player);
            if (session == null) {
                player.stopUsingItem();
            } else {
                session.useTick(player, stack);
            }
        } else if (level.isClientSide()) {
            VisClientHooks.useTick(level, entity, stack);
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer player) {
            Tapping.close(player);
        }
        return false;
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof ServerPlayer player) {
            Tapping.close(player);
        }
    }

    /**
     * True whenever both stacks are wands, so a client-side slot resync never breaks the use
     * visually; the server's pulse compares stack identity itself.
     */
    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.is(this) && newStack.is(this);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return WandState.of(stack, WandParts.last()) instanceof WandState.Ready;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        WandParts parts = WandParts.last();
        if (!(WandState.of(stack, parts) instanceof WandState.Ready wand)
                || parts.visAspects().isEmpty()) {
            return 0;
        }
        long total = 0;
        for (var aspect : parts.visAspects()) {
            total += wand.pool(aspect);
        }
        long capacity = (long) wand.rod().capacity() * parts.visAspects().size();
        return (int) Math.round(13.0 * total / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR;
    }
}
