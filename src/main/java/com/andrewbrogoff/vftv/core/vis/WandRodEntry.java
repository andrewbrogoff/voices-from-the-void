package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.WandRod;
import com.andrewbrogoff.vftv.core.vis.model.VisRegistryRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * A {@code vftv:wand_rod} entry (wands spec, "Parts"): the ingredient item and the capacity per
 * pool. The item is checked against the item registry when a world opens, so an unknown one is
 * named there.
 *
 * @param item the ingredient that is this rod
 * @param capacity vis per pool, 1 to 1000
 */
public record WandRodEntry(Identifier item, int capacity) implements WandRod {
    /** The entry's codec: {@code {"item", "capacity"}}, unknown fields refused. */
    public static final Codec<WandRodEntry> CODEC =
            VisCodecs.strict(
                    RecordCodecBuilder.<WandRodEntry>create(
                                    i ->
                                            i.group(
                                                            Identifier.CODEC
                                                                    .fieldOf("item")
                                                                    .forGetter(WandRodEntry::item),
                                                            Codec.INT
                                                                    .fieldOf("capacity")
                                                                    .forGetter(
                                                                            WandRodEntry::capacity))
                                                    .apply(i, WandRodEntry::new))
                            .validate(
                                    e -> {
                                        String problem = VisRegistryRules.rodValue(e.capacity());
                                        return problem == null
                                                ? DataResult.success(e)
                                                : DataResult.error(() -> problem);
                                    }),
                    Set.of("item", "capacity"));

    /** The registry's element codec, up-casting to {@link WandRod}. */
    public static final Codec<WandRod> ELEMENT_CODEC =
            CODEC.flatComapMap(
                    e -> e,
                    r ->
                            r instanceof WandRodEntry e
                                    ? DataResult.success(e)
                                    : DataResult.error(() -> "not a vftv rod entry: " + r));
}
