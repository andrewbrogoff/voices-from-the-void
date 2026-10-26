package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.vis.model.RelayNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * {@code vftv:node_tap} (wands spec, "Members", "Cultivation"): placed against a node, it faces the
 * clicked block, which must be a node; at most one tap per node. Its {@code facing} points at its
 * node. The source of a relay network and the cultivation input.
 */
public final class NodeTapBlock extends RelayBlock {
    /** The direction of its node. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private static final VoxelShape ALONG_Z = Block.box(3, 3, 0, 13, 13, 16);
    private static final VoxelShape ALONG_X = Block.box(0, 3, 3, 16, 13, 13);
    private static final VoxelShape ALONG_Y = Block.box(3, 0, 3, 13, 16, 13);

    /** A tap with the given properties. */
    public NodeTapBlock(BlockBehaviour.Properties properties) {
        super(properties, RelayNetwork.Kind.TAP, ALONG_Z);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING).getAxis()) {
            case X -> ALONG_X;
            case Y -> ALONG_Y;
            case Z -> ALONG_Z;
        };
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        BlockPos pos = context.getClickedPos();
        BlockPos node = pos.relative(facing);
        String refusal = null;
        if (!context.getLevel().getBlockState(node).is(VisContent.node())) {
            refusal = VisLang.RELAY_NO_NODE;
        } else if (NodeTaps.tappedBy(context.getLevel(), node, pos) != null) {
            refusal = VisLang.RELAY_TAKEN;
        }
        if (refusal != null) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                StatusLines.send(player, Component.translatable(refusal));
            }
            return null;
        }
        return admits(context) ? defaultBlockState().setValue(FACING, facing) : null;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
