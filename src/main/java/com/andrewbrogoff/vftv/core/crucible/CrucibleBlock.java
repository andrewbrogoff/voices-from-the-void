package com.andrewbrogoff.vftv.core.crucible;

import com.andrewbrogoff.vftv.api.crucible.CrucibleKeys;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The crucible (crucible spec, "The block"): a cauldron-like vessel with {@code water} and {@code
 * boiling} states. Buckets fill and empty it; item entities touching its interior offer themselves
 * to its block entity on its phase ticks; a server ticker exists only while it boils.
 */
public final class CrucibleBlock extends Block implements EntityBlock {
    /** {@code water}: whether it holds water. */
    public static final BooleanProperty WATER = BooleanProperty.create("water");

    /** {@code boiling}: {@code water ∧ heated}. */
    public static final BooleanProperty BOILING = BooleanProperty.create("boiling");

    /** The interior box, [2,4,2]-[14,16,14] px. */
    public static final VoxelShape INTERIOR = Block.box(2, 4, 2, 14, 16, 14);

    private static final VoxelShape SHAPE =
            Shapes.join(Shapes.block(), INTERIOR, BooleanOp.ONLY_FIRST);
    private static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);

    /** A crucible with these properties. */
    public CrucibleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATER, false).setValue(BOILING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATER, BOILING);
    }

    /** Whether the state below heats a crucible: in the heat tag, and unlit-free or lit. */
    public static boolean heats(BlockState below) {
        return below.is(CrucibleKeys.CRUCIBLE_HEAT)
                && (!below.hasProperty(BlockStateProperties.LIT)
                        || below.getValue(BlockStateProperties.LIT));
    }

    /** The state with {@code boiling} recomputed against the state below. */
    public static BlockState withHeat(BlockState state, BlockState below) {
        return state.setValue(BOILING, state.getValue(WATER) && heats(below));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withHeat(
                defaultBlockState(),
                context.getLevel().getBlockState(context.getClickedPos().below()));
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
        return direction == Direction.DOWN ? withHeat(state, neighbourState) : state;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return INTERIOR;
    }

    @Override
    protected VoxelShape getEntityInsideCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return INTERIOR;
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
        boolean water = stack.is(Items.WATER_BUCKET);
        if (!water && !stack.is(Items.BUCKET)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return CrucibleService.useBucket(level, pos, state, player, hand, stack, water);
    }

    @Override
    protected void entityInside(
            BlockState state,
            Level level,
            BlockPos pos,
            Entity entity,
            InsideBlockEffectApplier effects,
            boolean precise) {
        if (level instanceof ServerLevel server) {
            CrucibleService.contact(server, pos, state, entity);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BOILING)) {
            return;
        }
        int bubbles = 1 + random.nextInt(2);
        for (int i = 0; i < bubbles; i++) {
            level.addParticle(
                    ParticleTypes.BUBBLE_POP,
                    pos.getX() + 0.125 + random.nextDouble() * 0.75,
                    pos.getY() + 15.0 / 16.0,
                    pos.getZ() + 0.125 + random.nextDouble() * 0.75,
                    0,
                    0,
                    0);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(BOILING) || !type.equals(CrucibleContent.blockEntity())) {
            return null;
        }
        BlockEntityTicker<CrucibleBlockEntity> ticker =
                level.isClientSide() ? CrucibleBlock::clientTick : CrucibleService::tick;
        @SuppressWarnings("unchecked") // type is the crucible's own, checked above
        BlockEntityTicker<T> cast = (BlockEntityTicker<T>) ticker;
        return cast;
    }

    /** The ambient boil: client-only, at most once per 40 ticks per crucible. */
    private static void clientTick(
            Level level, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 40L) == 0) {
            level.playLocalSound(
                    pos, CrucibleContent.boilSound(), SoundSource.BLOCKS, 0.6f, 1.0f, false);
        }
    }
}
