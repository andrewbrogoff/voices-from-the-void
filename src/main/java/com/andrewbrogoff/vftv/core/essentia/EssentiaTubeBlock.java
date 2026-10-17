package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.essentia.EssentiaKeys;
import com.andrewbrogoff.vftv.core.essentia.model.EssentiaMath;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Essentia Tube (essentia spec, "Connections"): six faces, each {@code none}, {@code connected}
 * or {@code blocked}, computed from block states and the tube's own label alone, so client and
 * server agree.
 */
public final class EssentiaTubeBlock extends Block implements EntityBlock {
    /** A tube face's state. */
    public enum Face implements StringRepresentable {
        /** Joins nothing. */
        NONE,
        /** Joined to a neighbour. */
        CONNECTED,
        /** Closed by hand. */
        BLOCKED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The model's code: {@link EssentiaMath#NONE}, {@code CONNECTED} or {@code BLOCKED}. */
        int code() {
            return switch (this) {
                case NONE -> EssentiaMath.NONE;
                case CONNECTED -> EssentiaMath.CONNECTED;
                case BLOCKED -> EssentiaMath.BLOCKED;
            };
        }

        /** The face of a model code. */
        static Face of(int code) {
            return code == EssentiaMath.CONNECTED
                    ? CONNECTED
                    : code == EssentiaMath.BLOCKED ? BLOCKED : NONE;
        }
    }

    /** The six face properties, in Direction order D, U, N, S, W, E. */
    public static final List<EnumProperty<Face>> FACES =
            List.of(
                    EnumProperty.create("down", Face.class),
                    EnumProperty.create("up", Face.class),
                    EnumProperty.create("north", Face.class),
                    EnumProperty.create("south", Face.class),
                    EnumProperty.create("west", Face.class),
                    EnumProperty.create("east", Face.class));

    /** The dynamic drop that carries a cut-off label. */
    public static final net.minecraft.resources.Identifier LABEL_DROP = EssentiaContent.id("label");

    private static final MapCodec<EssentiaTubeBlock> CODEC = simpleCodec(EssentiaTubeBlock::new);
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final double EPSILON = 1e-4;

    private final java.util.function.Function<BlockState, VoxelShape> shapes;

    /** A tube with these properties. */
    public EssentiaTubeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (EnumProperty<Face> face : FACES) {
            state = state.setValue(face, Face.NONE);
        }
        registerDefaultState(state);
        shapes = getShapeForEachState(EssentiaTubeBlock::shapeOf);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        FACES.forEach(builder::add);
    }

    /** The property of face {@code d}. */
    public static EnumProperty<Face> face(Direction d) {
        return FACES.get(d.get3DDataValue());
    }

    /** The arm box of face {@code d}, in pixels: from the core out to that face. */
    static AABB arm(Direction d) {
        double lo = 5;
        double hi = 11;
        return switch (d) {
            case DOWN -> new AABB(lo, 0, lo, hi, lo, hi);
            case UP -> new AABB(lo, hi, lo, hi, 16, hi);
            case NORTH -> new AABB(lo, lo, 0, hi, hi, lo);
            case SOUTH -> new AABB(lo, lo, hi, hi, hi, 16);
            case WEST -> new AABB(0, lo, lo, lo, hi, hi);
            case EAST -> new AABB(hi, lo, lo, 16, hi, hi);
        };
    }

    private static VoxelShape shapeOf(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction d : Direction.values()) {
            if (state.getValue(face(d)) != Face.NONE) {
                AABB a = arm(d);
                shape = Shapes.or(shape, Block.box(a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ));
            }
        }
        return shape;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LabelledBlockEntity(pos, state);
    }

    // --- connections ------------------------------------------------------------------------

    /**
     * Whether a tube face toward {@code d} connects, from block states alone: a tube whose face
     * toward us isn't blocked, a valve on {@code d}'s axis, or an endpoint-tag block.
     */
    static boolean joins(LevelReader level, BlockPos pos, Direction d) {
        BlockState n = level.getBlockState(pos.relative(d));
        if (n.getBlock() instanceof EssentiaTubeBlock) {
            return n.getValue(face(d.getOpposite())) != Face.BLOCKED;
        }
        if (n.getBlock() instanceof TubeValveBlock) {
            return n.getValue(TubeValveBlock.AXIS) == d.getAxis();
        }
        return n.is(EssentiaKeys.ESSENTIA_ENDPOINTS);
    }

    private static boolean labelled(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LabelledBlockEntity be
                && (!be.readable() || be.label() != null);
    }

    /** The faces of {@code state} as model ints, in Direction order. */
    static int[] faces(BlockState state) {
        int[] out = new int[6];
        for (Direction d : Direction.values()) {
            out[d.get3DDataValue()] = state.getValue(face(d)).code();
        }
        return out;
    }

    private static BlockState withFaces(BlockState state, int[] faces) {
        for (Direction d : Direction.values()) {
            state = state.setValue(face(d), Face.of(faces[d.get3DDataValue()]));
        }
        return state;
    }

    /** Recomputes every unblocked face and normalises, keeping blocked faces. */
    static BlockState computed(
            LevelReader level, BlockPos pos, BlockState state, boolean labelled) {
        int[] f = faces(state);
        for (Direction d : Direction.values()) {
            if (f[d.get3DDataValue()] != EssentiaMath.BLOCKED) {
                f[d.get3DDataValue()] =
                        joins(level, pos, d) ? EssentiaMath.CONNECTED : EssentiaMath.NONE;
            }
        }
        return withFaces(state, EssentiaMath.normalise(f, labelled));
    }

    /** A face refresh: recomputes and sets the state if it differs. */
    static void refresh(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof EssentiaTubeBlock) {
            BlockState next = computed(level, pos, state, labelled(level, pos));
            if (!next.equals(state)) {
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state =
                computed(context.getLevel(), context.getClickedPos(), defaultBlockState(), false);
        boolean[] proposed = new boolean[6];
        for (Direction d : Direction.values()) {
            proposed[d.get3DDataValue()] = state.getValue(face(d)) == Face.CONNECTED;
        }
        if (EssentiaLines.refuseAdmission(
                context.getLevel(), context.getClickedPos(), proposed, context.getPlayer())) {
            return null;
        }
        return state;
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
        return computed(level, pos, state, labelled(level, pos));
    }

    @Override
    protected void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel server) {
            EssentiaService.lines(server).changed(pos);
        }
    }

    // --- use --------------------------------------------------------------------------------

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return level.getBlockEntity(pos) instanceof LabelledBlockEntity be
                ? Labels.use(stack, be, level, pos, player, hand)
                : InteractionResult.TRY_WITH_EMPTY_HAND;
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
        Direction d = toggledFace(state, pos, hit);
        if (!level.isClientSide()) {
            boolean lab = labelled(level, pos);
            int computed = joins(level, pos, d) ? EssentiaMath.CONNECTED : EssentiaMath.NONE;
            int[] next = EssentiaMath.toggle(faces(state), d.get3DDataValue(), computed, lab);
            level.setBlock(pos, withFaces(state, next), Block.UPDATE_ALL);
        }
        return InteractionResult.SUCCESS;
    }

    /** The face a hand toggles: the arm the hit lies in, else the clicked face. */
    static Direction toggledFace(BlockState state, BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)).scale(16);
        for (Direction d : Direction.values()) {
            if (state.getValue(face(d)) != Face.NONE
                    && arm(d).inflate(EPSILON * 16).contains(local)) {
                return d;
            }
        }
        return hit.getDirection();
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                        instanceof LabelledBlockEntity be
                && be.readable()
                && be.label() != null) {
            params =
                    params.withDynamicDrop(
                            LABEL_DROP, out -> out.accept(Labels.labelStack(be.label())));
        }
        return super.getDrops(state, params);
    }
}
