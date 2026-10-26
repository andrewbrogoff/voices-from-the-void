package com.andrewbrogoff.vftv.core.infusion;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Singing Bowl and the Pedestal (infusion spec, "The blocks"): stateless blocks with a one-item
 * block entity, shaped as the spec's cuboids, used by hand through {@link InfusionAltars}. Neither
 * ticks on the server; the bowl's client ticker only plays the ambient ring.
 */
public final class AltarBlock extends Block implements EntityBlock {
    private static final VoxelShape BOWL =
            Shapes.or(
                    Block.box(3, 0, 3, 13, 8, 13),
                    Block.box(2, 8, 2, 14, 10, 14),
                    Block.box(2, 10, 2, 14, 14, 3),
                    Block.box(2, 10, 13, 14, 14, 14),
                    Block.box(2, 10, 3, 3, 14, 13),
                    Block.box(13, 10, 3, 14, 14, 13));
    private static final VoxelShape PEDESTAL =
            Shapes.or(
                    Block.box(2, 0, 2, 14, 2, 14),
                    Block.box(4, 2, 4, 12, 10, 12),
                    Block.box(3, 10, 3, 13, 12, 13));
    private static final MapCodec<AltarBlock> BOWL_CODEC =
            simpleCodec(p -> new AltarBlock(p, true));
    private static final MapCodec<AltarBlock> PEDESTAL_CODEC =
            simpleCodec(p -> new AltarBlock(p, false));
    private static final int RING_PERIOD = 40;
    private static final int MAX_AMBIENT = 16;

    private final boolean bowl;

    /** A bowl ({@code bowl}) or a pedestal with these properties. */
    public AltarBlock(Properties properties, boolean bowl) {
        super(properties);
        this.bowl = bowl;
    }

    /** Whether this is the Singing Bowl. */
    public boolean isBowl() {
        return bowl;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return bowl ? BOWL_CODEC : PEDESTAL_CODEC;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return bowl ? BOWL : PEDESTAL;
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
        return InfusionAltars.useItemOn(level, pos, player, hand, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InfusionAltars.useWithoutItem(level, pos, player);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!bowl || !(level.getBlockEntity(pos) instanceof BowlBlockEntity be) || !be.ringing()) {
            return;
        }
        java.util.List<Integer> pending = be.view().pending();
        for (int i = 0; i < pending.size() && i < MAX_AMBIENT; i++) {
            int dx = BowlView.dx(pending.get(i));
            int dz = BowlView.dz(pending.get(i));
            if (BowlView.present(pending.get(i))) {
                level.addParticle(
                        ParticleTypes.ENCHANT,
                        pos.getX() + 0.5,
                        pos.getY() + 1.0,
                        pos.getZ() + 0.5,
                        dx + random.nextDouble() - 0.5,
                        0.5,
                        dz + random.nextDouble() - 0.5);
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return bowl ? new BowlBlockEntity(pos, state) : new PedestalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (!bowl || !level.isClientSide() || !type.equals(InfusionContent.bowlType())) {
            return null;
        }
        BlockEntityTicker<BowlBlockEntity> ticker = AltarBlock::ring;
        @SuppressWarnings("unchecked") // type is the bowl's own, checked above
        BlockEntityTicker<T> cast = (BlockEntityTicker<T>) ticker;
        return cast;
    }

    /** The ambient ring: client-only, at most once per bowl per 40 ticks, while ringing. */
    private static void ring(Level level, BlockPos pos, BlockState state, BowlBlockEntity be) {
        if (be.ringing() && Math.floorMod(level.getGameTime() + pos.asLong(), RING_PERIOD) == 0) {
            level.playLocalSound(
                    pos,
                    InfusionContent.sound(InfusionContent.RING),
                    SoundSource.BLOCKS,
                    0.6f,
                    1.0f,
                    false);
        }
    }
}
