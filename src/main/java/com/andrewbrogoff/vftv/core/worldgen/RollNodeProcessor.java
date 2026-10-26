package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.vis.NodeType;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.vis.NodeTypeEntry;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.worldgen.model.NodeRoll;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * {@code vftv:roll_node} (worldgen spec, "Processors", step 3): a template's node is dropped when
 * its target isn't air; otherwise its clarity and each aspect's max and current are rolled from the
 * target's position, every other field of the template's complete record kept. Pure: it reads one
 * block state and the type, and never touches level state beyond that.
 */
public final class RollNodeProcessor implements StructureProcessor {
    /** The one instance. */
    public static final RollNodeProcessor INSTANCE = new RollNodeProcessor();

    /** Its codec: no fields. */
    public static final MapCodec<RollNodeProcessor> CODEC = MapCodec.unit(INSTANCE);

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean warnedSeed;
    private static volatile boolean warnedType;

    private RollNodeProcessor() {}

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo process(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            StructureTemplate.StructureBlockInfo originalBlockInfo,
            StructureTemplate.StructureBlockInfo processed,
            StructurePlaceSettings settings,
            @Nullable StructureTemplate template) {
        if (!processed.state().is(VisContent.node())) {
            return processed;
        }
        BlockPos target = processed.pos();
        if (!level.getBlockState(target).isAir()) {
            return null; // the wands spec places nodes only into air: the terrain stays
        }
        CompoundTag nbt = processed.nbt();
        if (nbt == null) {
            return processed;
        }
        CompoundTag rolled = roll(level, target, nbt);
        return rolled == null
                ? processed
                : new StructureTemplate.StructureBlockInfo(target, processed.state(), rolled);
    }

    /** The template's block-entity tag with the node rolled, or null to keep it unrolled. */
    static @Nullable CompoundTag roll(LevelReader level, BlockPos target, CompoundTag nbt) {
        if (!(level instanceof WorldGenLevel world)) {
            if (!warnedSeed) {
                warnedSeed = true;
                LOGGER.warn("A Choir node placed outside world generation stays unrolled");
            }
            return null;
        }
        CompoundTag node = nbt.getCompoundOrEmpty("node");
        Optional<String> typeId = node.getString("type");
        Optional<NodeType> type =
                typeId.flatMap(
                        t ->
                                level.registryAccess()
                                        .lookup(VisKeys.NODE_TYPE_REGISTRY)
                                        .flatMap(
                                                r ->
                                                        r.getOptional(
                                                                ResourceKey.create(
                                                                        VisKeys.NODE_TYPE_REGISTRY,
                                                                        Identifier.parse(t)))));
        if (type.isEmpty() || !(type.get() instanceof NodeTypeEntry entry)) {
            if (!warnedType) {
                warnedType = true;
                LOGGER.warn(
                        "A structure node's type {} is missing; it stays unrolled and inert",
                        typeId.orElse("?"));
            }
            return null;
        }
        List<NodeTypeEntry.AspectRange> ranges = new ArrayList<>(entry.aspects());
        ranges.sort(Comparator.comparing(r -> r.aspect().identifier().toString()));
        NodeRoll.Result result =
                NodeRoll.roll(
                        world.getSeed(),
                        target.asLong(),
                        new int[] {
                            entry.clarity().faint(), entry.clarity().steady(), entry.clarity().clear()
                        },
                        ranges.stream()
                                .map(
                                        r ->
                                                new NodeRoll.Range(
                                                        r.aspect().identifier().toString(),
                                                        r.min(),
                                                        r.max()))
                                .toList());
        CompoundTag out = nbt.copy();
        CompoundTag rolled = out.getCompoundOrEmpty("node");
        rolled.putString(
                "clarity", NodeState.Clarity.byIndex(result.clarity()).getSerializedName());
        ListTag aspects = rolled.getListOrEmpty("aspects");
        for (int i = 0; i < aspects.size(); i++) {
            CompoundTag a = aspects.getCompoundOrEmpty(i);
            String id = a.getStringOr("aspect", "");
            for (int r = 0; r < ranges.size(); r++) {
                if (ranges.get(r).aspect().identifier().toString().equals(id)) {
                    a.putInt("max", result.maxes().get(r));
                    a.putInt("current", result.maxes().get(r));
                }
            }
        }
        out.put("node", rolled);
        return out;
    }

    @Override
    public MapCodec<RollNodeProcessor> codec() {
        return CODEC;
    }
}
