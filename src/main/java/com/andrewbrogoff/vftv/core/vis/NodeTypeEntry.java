package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import com.andrewbrogoff.vftv.core.vis.model.VisRegistryRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.biome.Biome;

/**
 * A {@code vftv:node_type} entry (wands spec, "Node types"). The cross-entry rules (unknown
 * aspects, the Wellspring) run when a world opens; the codec checks each field's shape and range.
 *
 * @param aspects the aspects and the rolled range of each, 1 to 6
 * @param mainAspect the defining aspect, one of {@code aspects}
 * @param clarity the clarity weights
 * @param natural whether it spreads naturally
 * @param biomes the biome tags it spreads in, iff natural
 * @param weight the spread weight, iff natural
 * @param rewardDivisor the first-strike reward divisor, or empty for the tuning default
 * @param anchored whether it cannot be captured
 */
public record NodeTypeEntry(
        List<AspectRange> aspects,
        ResourceKey<Aspect> mainAspect,
        Clarity clarity,
        boolean natural,
        List<TagKey<Biome>> biomes,
        Optional<Integer> weight,
        Optional<Integer> rewardDivisor,
        boolean anchored)
        implements NodeType {
    /**
     * One aspect and its range.
     *
     * @param aspect the aspect
     * @param min the least rolled max
     * @param max the greatest rolled max
     */
    public record AspectRange(ResourceKey<Aspect> aspect, int min, int max) {
        static final Codec<AspectRange> CODEC =
                VisCodecs.strict(
                        RecordCodecBuilder.create(
                                i ->
                                        i.group(
                                                        ResourceKey.codec(AspectKeys.REGISTRY)
                                                                .fieldOf("aspect")
                                                                .forGetter(AspectRange::aspect),
                                                        ExtraCodecs.intRange(1, 1000)
                                                                .listOf(2, 2)
                                                                .fieldOf("range")
                                                                .forGetter(
                                                                        r ->
                                                                                List.of(
                                                                                        r.min(),
                                                                                        r.max())))
                                                .apply(
                                                        i,
                                                        (a, r) ->
                                                                new AspectRange(
                                                                        a, r.get(0), r.get(1)))),
                        Set.of("aspect", "range"));
    }

    /**
     * The clarity weights, each 0 to 1000.
     *
     * @param faint the weight of faint
     * @param steady the weight of steady
     * @param clear the weight of clear
     */
    public record Clarity(int faint, int steady, int clear) {
        static final Codec<Clarity> CODEC =
                VisCodecs.strict(
                        RecordCodecBuilder.create(
                                i ->
                                        i.group(
                                                        ExtraCodecs.intRange(0, 1000)
                                                                .fieldOf("faint")
                                                                .forGetter(Clarity::faint),
                                                        ExtraCodecs.intRange(0, 1000)
                                                                .fieldOf("steady")
                                                                .forGetter(Clarity::steady),
                                                        ExtraCodecs.intRange(0, 1000)
                                                                .fieldOf("clear")
                                                                .forGetter(Clarity::clear))
                                                .apply(i, Clarity::new)),
                        Set.of("faint", "steady", "clear"));

        /** The weights in the order faint, steady, clear. */
        public List<Integer> list() {
            return List.of(faint, steady, clear);
        }
    }

    /** The entry's codec, unknown fields refused. */
    public static final Codec<NodeTypeEntry> CODEC =
            VisCodecs.strict(
                    RecordCodecBuilder.<NodeTypeEntry>create(
                                    i ->
                                            i.group(
                                                            AspectRange.CODEC
                                                                    .listOf(1, 6)
                                                                    .fieldOf("aspects")
                                                                    .forGetter(
                                                                            NodeTypeEntry::aspects),
                                                            ResourceKey.codec(AspectKeys.REGISTRY)
                                                                    .fieldOf("main_aspect")
                                                                    .forGetter(
                                                                            NodeTypeEntry
                                                                                    ::mainAspect),
                                                            Clarity.CODEC
                                                                    .fieldOf("clarity")
                                                                    .forGetter(
                                                                            NodeTypeEntry::clarity),
                                                            Codec.BOOL
                                                                    .optionalFieldOf(
                                                                            "natural", false)
                                                                    .forGetter(
                                                                            NodeTypeEntry::natural),
                                                            TagKey.codec(Registries.BIOME)
                                                                    .listOf(0, 8)
                                                                    .optionalFieldOf(
                                                                            "biomes", List.of())
                                                                    .forGetter(
                                                                            NodeTypeEntry::biomes),
                                                            ExtraCodecs.intRange(1, 1000)
                                                                    .optionalFieldOf("weight")
                                                                    .forGetter(
                                                                            NodeTypeEntry::weight),
                                                            ExtraCodecs.intRange(1, 64)
                                                                    .optionalFieldOf(
                                                                            "node_reward_divisor")
                                                                    .forGetter(
                                                                            NodeTypeEntry
                                                                                    ::rewardDivisor),
                                                            Codec.BOOL
                                                                    .optionalFieldOf(
                                                                            "anchored", false)
                                                                    .forGetter(
                                                                            NodeTypeEntry
                                                                                    ::anchored))
                                                    .apply(i, NodeTypeEntry::new))
                            .validate(NodeTypeEntry::checkShape),
                    Set.of(
                            "aspects",
                            "main_aspect",
                            "clarity",
                            "natural",
                            "biomes",
                            "weight",
                            "node_reward_divisor",
                            "anchored"));

    /** The registry's element codec, up-casting to {@link NodeType}. */
    public static final Codec<NodeType> ELEMENT_CODEC =
            CODEC.flatComapMap(
                    e -> e,
                    t ->
                            t instanceof NodeTypeEntry e
                                    ? DataResult.success(e)
                                    : DataResult.error(() -> "not a vftv node type: " + t));

    /** Copies the lists. */
    public NodeTypeEntry {
        aspects = List.copyOf(aspects);
        biomes = List.copyOf(biomes);
    }

    /** This entry as the model's rules read it. */
    public VisRegistryRules.Type rules() {
        return new VisRegistryRules.Type(
                aspects.stream()
                        .map(
                                a ->
                                        new VisRegistryRules.TypeAspect(
                                                a.aspect().identifier().toString(),
                                                a.min(),
                                                a.max()))
                        .toList(),
                mainAspect.identifier().toString(),
                clarity.list(),
                natural,
                biomes.stream().map(b -> b.location().toString()).toList(),
                weight.orElse(null),
                anchored);
    }

    private static DataResult<NodeTypeEntry> checkShape(NodeTypeEntry e) {
        List<String> problems =
                VisRegistryRules.type(
                        e.rules(),
                        Set.copyOf(
                                e.aspects.stream()
                                        .map(a -> a.aspect().identifier().toString())
                                        .toList()));
        return problems.isEmpty()
                ? DataResult.success(e)
                : DataResult.error(() -> String.join("; ", problems));
    }
}
