package com.andrewbrogoff.vftv.core.vis;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * {@code vftv:node} (wands spec, "The node block"): no collision, a centred selection shape half a
 * block across, an invisible render shape (a renderer draws it), survival-unbreakable,
 * explosion-proof, {@code PushReaction.BLOCK}, not replaceable and not waterloggable.
 */
public final class NodeBlock extends Block implements EntityBlock {
    private static final VoxelShape SELECTION = Block.box(4, 4, 4, 12, 12, 12);

    /** A node with the given properties. */
    public NodeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** The node's properties. */
    static BlockBehaviour.Properties nodeProperties() {
        return BlockBehaviour.Properties.of()
                .setId(VisContent.NODE_KEY)
                .noCollision()
                .noOcclusion()
                .noLootTable()
                .strength(-1.0f, 3_600_000.0f)
                .pushReaction(PushReaction.BLOCK);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NodeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        // Client only: ambient glints and the hum; the server never ticks a node.
        return level.isClientSide() && type.equals(VisContent.nodeEntity())
                ? (l, p, s, be) -> NodeBlockEntity.clientTick(l, p, (NodeBlockEntity) be)
                : null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SELECTION;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
