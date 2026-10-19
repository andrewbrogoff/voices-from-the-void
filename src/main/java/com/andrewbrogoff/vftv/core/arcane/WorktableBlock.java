package com.andrewbrogoff.vftv.core.arcane;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code vftv:worktable} (arcane spec, "The block"): a plain block with no block entity, ticker,
 * item capability or {@code Container}. Using it opens a {@link WorktableMenu}, as the crafting
 * table opens its own.
 */
public final class WorktableBlock extends Block {
    /** The block's codec. */
    public static final MapCodec<WorktableBlock> CODEC = simpleCodec(WorktableBlock::new);

    /** {@code container.vftv.worktable}, the screen title. */
    static final Component TITLE = Component.translatable(ArcaneLang.CONTAINER);

    /** A Worktable with these properties. */
    public WorktableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<WorktableBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(state.getMenuProvider(level, pos));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
                (id, inventory, player) ->
                        new WorktableMenu(id, inventory, ContainerLevelAccess.create(level, pos)),
                TITLE);
    }
}
