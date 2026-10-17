package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.core.vis.model.NaturalSpread;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * The natural-spread feature {@code vftv:node} (wands spec, "Natural spread"): run once per chunk,
 * it places the cell's seeded candidate if its column is in this chunk, its biome has an eligible
 * type and the air search finds room. No level state is touched beyond the chunk being generated.
 */
public final class NodeFeature extends Feature<NodeFeature.Config> {
    /**
     * The configured feature's numbers (content {@code nodes/placement.json}).
     *
     * @param chance the chance a cell spawns
     * @param height the offset range above the surface, {@code [min, max]}
     * @param retries how many blocks above the first the air search tries
     */
    public record Config(double chance, List<Integer> height, int retries)
            implements FeatureConfiguration {
        /** The codec, with the spec's ranges. */
        public static final Codec<Config> CODEC =
                RecordCodecBuilder.<Config>create(
                                i ->
                                        i.group(
                                                        Codec.doubleRange(0, 1)
                                                                .fieldOf("chance")
                                                                .forGetter(Config::chance),
                                                        ExtraCodecs.intRange(0, 64)
                                                                .listOf(2, 2)
                                                                .fieldOf("height")
                                                                .forGetter(Config::height),
                                                        ExtraCodecs.intRange(0, 32)
                                                                .fieldOf("retries")
                                                                .forGetter(Config::retries))
                                                .apply(i, Config::new))
                        .validate(
                                c ->
                                        c.height().get(0) <= c.height().get(1)
                                                ? com.mojang.serialization.DataResult.success(c)
                                                : com.mojang.serialization.DataResult.error(
                                                        () -> "height min above max"));

        /** Copies the list. */
        public Config {
            height = List.copyOf(height);
        }

        NaturalSpread.Placement placement() {
            return new NaturalSpread.Placement(chance, height.get(0), height.get(1), retries);
        }
    }

    /** The feature, with its config codec. */
    public NodeFeature(Codec<Config> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        int cx = SectionPos.blockToSectionCoord(context.origin().getX());
        int cz = SectionPos.blockToSectionCoord(context.origin().getZ());
        NaturalSpread.Candidate c =
                NaturalSpread.candidate(
                        level.getSeed(),
                        Math.floorDiv(cx, 4),
                        Math.floorDiv(cz, 4),
                        context.config().placement());
        if (!c.spawns()
                || SectionPos.blockToSectionCoord(c.x()) != cx
                || SectionPos.blockToSectionCoord(c.z()) != cz) {
            return false;
        }
        int y0 = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, c.x(), c.z()) + c.offset();
        Holder<Biome> biome = level.getBiome(new BlockPos(c.x(), y0, c.z()));
        List<NaturalSpread.Eligible> eligible = new ArrayList<>();
        level.registryAccess()
                .lookupOrThrow(VisKeys.NODE_TYPE_REGISTRY)
                .listElements()
                .forEach(
                        h -> {
                            if (h.value() instanceof NodeTypeEntry t
                                    && t.natural()
                                    && t.weight().isPresent()
                                    && t.biomes().stream().anyMatch(biome::is)) {
                                eligible.add(eligible(h.key().identifier().toString(), t));
                            }
                        });
        if (eligible.isEmpty()) {
            return false;
        }
        for (int y = y0; y <= y0 + context.config().retries(); y++) {
            BlockPos pos = new BlockPos(c.x(), y, c.z());
            if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).isAir()) {
                continue;
            }
            NaturalSpread.Roll roll = NaturalSpread.roll(c.h(), eligible);
            level.setBlock(pos, VisContent.node().defaultBlockState(), Block.UPDATE_CLIENTS);
            if (level.getBlockEntity(pos) instanceof NodeBlockEntity be) {
                be.setState(
                        NodeState.born(
                                0,
                                NodeState.Origin.NATURAL,
                                net.minecraft.resources.Identifier.parse(roll.type()),
                                NodeState.Clarity.byIndex(roll.clarity()),
                                roll.maxes(),
                                0,
                                VisRulesService.tuning()));
            }
            return true;
        }
        return false;
    }

    private static NaturalSpread.Eligible eligible(String id, NodeTypeEntry t) {
        SortedMap<String, int[]> aspects = new TreeMap<>();
        t.aspects()
                .forEach(
                        a ->
                                aspects.put(
                                        a.aspect().identifier().toString(),
                                        new int[] {a.min(), a.max()}));
        return new NaturalSpread.Eligible(
                id, t.weight().orElseThrow(), aspects, t.clarity().list());
    }
}
