package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Alembic (essentia spec, "The Alembic"): a still-head hung on a {@code #vftv:alembic_bases}
 * block's side, pouring one aspect into whatever lies beneath its spout. {@code facing} points
 * toward its base.
 */
public final class AlembicBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** "It needs a crucible to hang on." */
    public static final String NO_BASE = "message.vftv.essentia.no_base";

    private static final MapCodec<AlembicBlock> CODEC = simpleCodec(AlembicBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 14, 13);

    /** An Alembic with these properties. */
    public AlembicBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlembicBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : (l, p, s, be) -> {
                    if (be instanceof AlembicBlockEntity alembic
                            && l instanceof ServerLevel server) {
                        alembic.serverTick(server);
                    }
                };
    }

    /** Whether the block at {@code pos} toward {@code d} is a base. */
    static boolean baseAt(LevelReader level, BlockPos pos, Direction d) {
        return level.getBlockState(pos.relative(d)).is(EssentiaKeys.ALEMBIC_BASES);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        List<Direction> order = new ArrayList<>();
        if (context.getClickedFace().getAxis().isHorizontal()) {
            order.add(context.getClickedFace().getOpposite());
        }
        Direction facing = context.getHorizontalDirection();
        for (int i = 0; i < 4; i++) {
            order.add(facing);
            facing = facing.getClockWise();
        }
        for (Direction d : order) {
            if (baseAt(context.getLevel(), context.getClickedPos(), d)) {
                return defaultBlockState().setValue(FACING, d);
            }
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            StatusLines.send(player, Component.translatable(NO_BASE));
        }
        return null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return baseAt(level, pos, state.getValue(FACING));
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction direction,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random) {
        return direction == state.getValue(FACING) && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : state;
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
        return level.getBlockEntity(pos) instanceof AlembicBlockEntity be
                ? Labels.use(stack, be, level, pos, player, hand)
                : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return EssentiaStatus.use(level, pos, player, hit);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                        instanceof AlembicBlockEntity be
                && be.readable()
                && be.label() != null) {
            params =
                    params.withDynamicDrop(
                            EssentiaTubeBlock.LABEL_DROP,
                            out -> out.accept(Labels.labelStack(be.label())));
        }
        return super.getDrops(state, params);
    }
}
