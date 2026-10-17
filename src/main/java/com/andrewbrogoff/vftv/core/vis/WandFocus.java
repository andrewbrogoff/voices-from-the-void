package com.andrewbrogoff.vftv.core.vis;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code vftv:wand_focus} component, {@code {"format": 1, "focus": <ItemStack>}}: the focus
 * attached to a wand, absent on a wand with none (foci spec, "On the wand"; the foci feature uses
 * it).
 *
 * @param focus the attached focus
 */
public record WandFocus(ItemStack focus) {
    /** The only format this build reads and writes. */
    public static final int FORMAT = 1;

    /** The bounded codec. */
    public static final Codec<WandFocus> BOUNDED =
            VisCodecs.strict(
                    RecordCodecBuilder.create(
                            i ->
                                    i.group(
                                                    Codec.INT
                                                            .fieldOf("format")
                                                            .forGetter(f -> FORMAT),
                                                    ItemStack.CODEC
                                                            .fieldOf("focus")
                                                            .forGetter(WandFocus::focus))
                                            .apply(
                                                    i,
                                                    (format, focus) -> {
                                                        if (format != FORMAT) {
                                                            throw new IllegalArgumentException(
                                                                    "unknown format " + format);
                                                        }
                                                        return new WandFocus(focus);
                                                    })),
                    Set.of("format", "focus"));

    @Override
    public boolean equals(Object o) {
        return o instanceof WandFocus f && ItemStack.matches(focus, f.focus);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(focus);
    }
}
