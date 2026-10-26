package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.api.arcane.ArcaneKeys;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.Nullable;

/**
 * Turning a table (arcane spec, "Turning a table"): a right-click on a block in {@code
 * #vftv:converts_to_worktable} with any wand. No vis, no cost, no warp. The platform's one listener
 * on {@code RightClickBlock} (lowest priority, never for a cancelled event) calls {@link
 * #onRightClick} on both sides; the server decides, the client mirrors the checks.
 */
public final class WorktableConversion {
    private WorktableConversion() {}

    /**
     * The turn, if every check holds.
     *
     * @param useDenied whether an earlier listener denied the block's or the item's use
     * @return the interaction's result to cancel with, or null to do nothing (an unlearned or
     *     sneaking click falls through to the table's own use or the wand's)
     */
    public static @Nullable InteractionResult onRightClick(
            Player player, InteractionHand hand, BlockPos pos, boolean useDenied) {
        Level level = player.level();
        if (useDenied || !eligible(player, hand, level, pos)) {
            return null;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS; // the arm swings; the block update arrives
        }
        BlockState original = level.getBlockState(pos);
        if (Worktables.hooks().breakCancelled(level, pos, original, player)) {
            return InteractionResult.FAIL;
        }
        // A BreakBlockEvent listener may have changed the world: recheck before the write.
        if (!level.getBlockState(pos).equals(original) || !eligible(player, hand, level, pos)) {
            return InteractionResult.FAIL;
        }
        BlockState worktable = ArcaneContent.worktable().defaultBlockState();
        if (!level.setBlock(pos, worktable, Block.UPDATE_ALL)) {
            return InteractionResult.FAIL;
        }
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, worktable));
        level.playSound(null, pos, ArcaneContent.convertSound(), SoundSource.BLOCKS, 1f, 1f);
        return InteractionResult.SUCCESS;
    }

    /** Checks 1 and 3-5; check 2 (a denied use) is the listener's. */
    private static boolean eligible(
            Player player, InteractionHand hand, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return player.getItemInHand(hand).is(VisContent.wand())
                && !player.isSecondaryUseActive()
                && !player.isSpectator()
                && player.mayBuild()
                && state.is(ArcaneKeys.CONVERTS_TO_WORKTABLE)
                && !state.hasBlockEntity()
                && level.getBlockEntity(pos) == null
                && ResearchService.isLearned(player, ArcaneContent.ARCANE_CRAFTING);
    }
}
