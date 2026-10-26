package com.andrewbrogoff.vftv.core.vis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * {@code vftv:bell_jar} (wands spec, "Moving nodes: the Bell Jar"), stacking to 1: empty, it takes
 * up the node it is used on; filled ({@code vftv:captured_node}), it sets its node down at the
 * clicked face, and the click is consumed even when refused. Both run in {@code onItemUseFirst},
 * before the block's own use and outside NeoForge's placement snapshot.
 */
public final class BellJarItem extends Item {
    /** A Bell Jar with the given properties. */
    public BellJarItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        boolean filled = stack.has(VisContent.captured());
        boolean onNode = context.getLevel().getBlockState(context.getClickedPos()).is(VisContent.node());
        if (!filled && !onNode) {
            return InteractionResult.PASS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.CONSUME;
        }
        return filled
                ? BellJar.release(
                        player,
                        stack,
                        context.getClickedPos().relative(context.getClickedFace()),
                        context.getLevel().getBlockState(context.getClickedPos()))
                : BellJar.capture(player, stack, context.getClickedPos());
    }
}
