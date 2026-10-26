package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.core.text.StatusLines;
import com.andrewbrogoff.vftv.core.vis.model.RelayNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A relay member (wands spec, "Relays, Members"): the Vis Relay itself, and the base of the Node
 * Tap and the Wand Cradle. {@code PushReaction.BLOCK}, a block entity, no ticker. Placement asks
 * the network for admission; the membership queue settles the rest. Sneaking with an empty hand
 * shows the member's status line.
 */
public class RelayBlock extends Block implements EntityBlock {
    private final RelayNetwork.Kind kind;
    private final VoxelShape shape;

    /** A member of {@code kind} with a selection and collision shape. */
    public RelayBlock(BlockBehaviour.Properties properties, RelayNetwork.Kind kind, VoxelShape shape) {
        super(properties);
        this.kind = kind;
        this.shape = shape;
    }

    /** The members' shared properties. */
    static BlockBehaviour.Properties properties(net.minecraft.resources.ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of()
                .setId(key)
                .strength(1.5f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK);
    }

    /** The member's kind. */
    public RelayNetwork.Kind kind() {
        return kind;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    /** The admission check (wands spec, "Topology"): a placement past 32 members is refused. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state != null && admits(context) ? state : null;
    }

    /** Whether the network admits a member at the context's position; tells the placer if not. */
    protected boolean admits(BlockPlaceContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return true; // the client can't see the network: the server decides
        }
        int size = RelayService.admissionSize(level, context.getClickedPos());
        if (size <= RelayNetwork.MAX_MEMBERS) {
            return true;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            StatusLines.send(player, RelayService.tooMany(size));
        }
        return false;
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity by,
            ItemStack itemStack) {
        if (level instanceof ServerLevel server) {
            RelayService.placed(server, pos, by instanceof ServerPlayer p ? p : null);
        }
    }

    @Override
    protected void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (level instanceof ServerLevel server) {
            RelayService.queue(server, pos); // a placement, or any state change of a member
        }
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer p) {
            StatusLines.send(p, RelayService.status(server, pos));
        }
        return InteractionResult.SUCCESS;
    }
}
