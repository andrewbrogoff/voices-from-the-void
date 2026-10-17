package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.WandCap;
import com.andrewbrogoff.vftv.core.vis.model.VisRegistryRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * A {@code vftv:wand_cap} entry (wands spec, "Parts"): the ingredient item and the cost factor.
 *
 * @param item the ingredient that is this cap
 * @param costMultiplier the cap factor in the quote, 0.25 to 2.00, at most 2 decimal places
 */
public record WandCapEntry(Identifier item, double costMultiplier) implements WandCap {
    /** The entry's codec: {@code {"item", "cost_multiplier"}}, unknown fields refused. */
    public static final Codec<WandCapEntry> CODEC =
            VisCodecs.strict(
                    RecordCodecBuilder.<WandCapEntry>create(
                                    i ->
                                            i.group(
                                                            Identifier.CODEC
                                                                    .fieldOf("item")
                                                                    .forGetter(WandCapEntry::item),
                                                            Codec.DOUBLE
                                                                    .fieldOf("cost_multiplier")
                                                                    .forGetter(
                                                                            WandCapEntry
                                                                                    ::costMultiplier))
                                                    .apply(i, WandCapEntry::new))
                            .validate(
                                    e -> {
                                        String problem =
                                                VisRegistryRules.capValue(e.costMultiplier());
                                        return problem == null
                                                ? DataResult.success(e)
                                                : DataResult.error(() -> problem);
                                    }),
                    Set.of("item", "cost_multiplier"));

    /** The registry's element codec, up-casting to {@link WandCap}. */
    public static final Codec<WandCap> ELEMENT_CODEC =
            CODEC.flatComapMap(
                    e -> e,
                    c ->
                            c instanceof WandCapEntry e
                                    ? DataResult.success(e)
                                    : DataResult.error(() -> "not a vftv cap entry: " + c));
}
