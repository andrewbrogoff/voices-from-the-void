package com.andrewbrogoff.vftv.core.essentia;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Valve (essentia spec, "Valves"): it passes iff {@code open} and not {@code powered}, and then
 * is a member of a line with arms only along its axis; otherwise it is a wall.
 */
public final class TubeValveBlock extends Block implements EntityBlock {
    /** The valve's axis. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** Opened by hand. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** A redstone signal holds it closed. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** "Too long a line to carry (%s)." */
    public static final String LINE_TOO_LONG = "message.vftv.essentia.line_too_long";

    private static final MapCodec<TubeValveBlock> CODEC = simpleCodec(TubeValveBlock::new);
    private static final VoxelShape BODY = Block.box(4, 4, 4, 12, 12, 12);
    private static final VoxelShape X = Shapes.or(BODY, Block.box(0, 5, 5, 16, 11, 11));
    private static final VoxelShape Y = Shapes.or(BODY, Block.box(5, 0, 5, 11, 16, 11));
    private static final VoxelShape Z = Shapes.or(BODY, Block.box(5, 5, 0, 11, 11, 16));

    /** A valve with these properties. */
    public TubeValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(AXIS, Direction.Axis.Z)
                        .setValue(OPEN, true)
                        .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN, POWERED);
    }

    /** Whether a valve in this state passes. */
    public static boolean passes(BlockState state) {
        return state.getValue(OPEN) && !state.getValue(POWERED);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }

    /** Which faces a valve in {@code state} would join, if it passes: its two axis ends. */
    static boolean[] proposed(LevelReader level, BlockPos pos, BlockState state) {
        boolean[] out = new boolean[6];
        if (passes(state)) {
            for (Direction d : Direction.values()) {
                out[d.get3DDataValue()] =
                        d.getAxis() == state.getValue(AXIS)
                                && EssentiaTubeBlock.joins(level, pos, d);
            }
        }
        return out;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state =
                defaultBlockState()
                        .setValue(AXIS, context.getClickedFace().getAxis())
                        .setValue(OPEN, true)
                        .setValue(
                                POWERED,
                                context.getLevel().hasNeighborSignal(context.getClickedPos()));
        if (EssentiaLines.refuseAdmission(
                context.getLevel(),
                context.getClickedPos(),
                proposed(context.getLevel(), context.getClickedPos(), state),
                context.getPlayer())) {
            return null;
        }
        return state;
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            @Nullable Orientation orientation,
            boolean moved) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            BlockState next = state.setValue(POWERED, powered);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            passingSound(level, pos, state, next);
        }
    }

    @Override
    protected void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel server) {
            EssentiaService.lines(server).changed(pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            return EssentiaStatus.use(level, pos, player, hit);
        }
        BlockState next = state.setValue(OPEN, !state.getValue(OPEN));
        if (passes(next)
                && EssentiaLines.refuseAdmission(level, pos, proposed(level, pos, next), player)) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, next, Block.UPDATE_ALL);
            passingSound(level, pos, state, next);
        }
        return InteractionResult.SUCCESS;
    }

    private static void passingSound(
            Level level, BlockPos pos, BlockState before, BlockState after) {
        if (passes(before) != passes(after)) {
            level.playSound(
                    null,
                    pos,
                    passes(after) ? EssentiaContent.valveOpen() : EssentiaContent.valveClose(),
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f);
        }
    }
}
