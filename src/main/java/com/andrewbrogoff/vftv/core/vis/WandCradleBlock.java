package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.core.vis.model.RelayNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code vftv:wand_cradle} (wands spec, "Members"): a sink that holds one wand. A wand inserts into
 * an empty cradle; an empty hand, not sneaking, takes it; anything else passes. Breaking drops it.
 */
public final class WandCradleBlock extends RelayBlock {
    /** A cradle with the given properties. */
    public WandCradleBlock(BlockBehaviour.Properties properties) {
        super(properties, RelayNetwork.Kind.CRADLE, Block.box(2, 0, 2, 14, 8, 14));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WandCradleBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!stack.is(VisContent.wand())
                || !(level.getBlockEntity(pos) instanceof WandCradleBlockEntity cradle)
                || cradle.unreadable()
                || !cradle.wand().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            cradle.setWand(stack.consumeAndReturn(1, player));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!(level.getBlockEntity(pos) instanceof WandCradleBlockEntity cradle)
                || cradle.wand().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, cradle.wand());
            cradle.setWand(ItemStack.EMPTY);
        }
        return InteractionResult.SUCCESS;
    }
}
