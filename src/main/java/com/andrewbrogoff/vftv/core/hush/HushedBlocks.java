package com.andrewbrogoff.vftv.core.hush;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The hushed forms' block classes (Hush spec, "Behaviour and drops"): each declares its source's
 * property objects and shapes, and is inert: no random ticks, no falling, no spreading, no decay.
 * Their only display is a {@code vftv:hush_mote} at 1 in 64 per displayed tick.
 */
public final class HushedBlocks {
    /** The mote chance's denominator: 1 in 64 per displayed tick. */
    public static final int MOTE_CHANCE = 64;

    private HushedBlocks() {}

    static void mote(Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(MOTE_CHANCE) == 0) {
            level.addParticle(
                    HushContent.mote(),
                    pos.getX() + random.nextDouble(),
                    pos.getY() + 1.05,
                    pos.getZ() + random.nextDouble(),
                    0,
                    0.01,
                    0);
        }
    }

    /** Dirt, stone, sand and gravel: plain cubes (sand and gravel don't fall). */
    public static final class Plain extends Block {
        private static final MapCodec<Plain> CODEC = simpleCodec(Plain::new);

        /** A plain hushed cube. */
        public Plain(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<Plain> codec() {
            return CODEC;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return false;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            mote(level, pos, random);
        }
    }

    /** Hushed grass: {@code snowy}, as grass, but it never spreads. */
    public static final class Grass extends SnowyBlock {
        private static final MapCodec<Grass> CODEC = simpleCodec(Grass::new);

        /** Hushed grass. */
        public Grass(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<Grass> codec() {
            return CODEC;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return false;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            mote(level, pos, random);
        }
    }

    /** Hushed deepslate and logs: {@code axis}. */
    public static final class Pillar extends RotatedPillarBlock {
        private static final MapCodec<Pillar> CODEC = simpleCodec(Pillar::new);

        /** A hushed pillar. */
        public Pillar(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public MapCodec<Pillar> codec() {
            return CODEC;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return false;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            mote(level, pos, random);
        }
    }

    /** Hushed short grass: short grass's shape, not bonemealable. */
    public static final class Plant extends VegetationBlock {
        private static final MapCodec<Plant> CODEC = simpleCodec(Plant::new);
        private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 13.0);

        /** Hushed short grass. */
        public Plant(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<Plant> codec() {
            return CODEC;
        }

        @Override
        protected VoxelShape getShape(
                BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return false;
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            mote(level, pos, random);
        }
    }

    /**
     * Hushed leaves: {@code distance}, {@code persistent} and {@code waterlogged}, a water source
     * while waterlogged, and never decaying.
     */
    public static final class Leaves extends LeavesBlock {
        private static final MapCodec<Leaves> CODEC = simpleCodec(Leaves::new);

        /** Hushed leaves. */
        public Leaves(BlockBehaviour.Properties properties) {
            super(0.0F, properties);
        }

        @Override
        public MapCodec<Leaves> codec() {
            return CODEC;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return false;
        }

        @Override
        protected void randomTick(
                BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {}

        @Override
        protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {}

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
            mote(level, pos, random);
        }
    }
}
