package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * The {@code vftv:vis} component (wands spec, "Vis storage"): {@code {"format": 1, "pools":
 * {<aspect id>: 0–1000}}}, at most 16 entries. A missing entry is 0; entries for aspects that
 * aren't vis aspects are kept and ignored.
 *
 * @param pools the stored amounts by aspect id text
 */
public record VisPools(SortedMap<String, Integer> pools) {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    /** No vis. */
    public static final VisPools EMPTY = new VisPools(new TreeMap<>());

    private static final Codec<String> ASPECT =
            Identifier.CODEC.xmap(Identifier::toString, Identifier::parse);

    /** The bounded codec. */
    public static final Codec<VisPools> BOUNDED =
            VisCodecs.strict(
                    RecordCodecBuilder.<VisPools>create(
                                    i ->
                                            i.group(
                                                            Codec.INT
                                                                    .fieldOf("format")
                                                                    .forGetter(p -> FORMAT),
                                                            Codec.unboundedMap(
                                                                            ASPECT,
                                                                            ExtraCodecs.intRange(
                                                                                    0,
                                                                                    VisLimits
                                                                                            .MAX_AMOUNT))
                                                                    .fieldOf("pools")
                                                                    .forGetter(VisPools::pools))
                                                    .apply(
                                                            i,
                                                            (format, pools) -> {
                                                                if (format != FORMAT) {
                                                                    throw new IllegalArgumentException(
                                                                            "unknown format "
                                                                                    + format);
                                                                }
                                                                return new VisPools(
                                                                        new TreeMap<>(pools));
                                                            }))
                            .validate(
                                    p ->
                                            p.pools().size() <= VisLimits.MAX_VIS_ENTRIES
                                                    ? DataResult.success(p)
                                                    : DataResult.error(
                                                            () -> "more than 16 entries")),
                    Set.of("format", "pools"));

    /** Copies the map. */
    public VisPools {
        pools = java.util.Collections.unmodifiableSortedMap(new TreeMap<>(pools));
    }
}
