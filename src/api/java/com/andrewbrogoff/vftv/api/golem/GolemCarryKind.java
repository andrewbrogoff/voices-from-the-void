package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Stable;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * What a golem job carries. See the golems spec, "Cores and jobs".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
@Stable
public enum GolemCarryKind implements StringRepresentable {
    /** Items, moved through the block item capability. */
    ITEMS,
    /** Essentia, moved through the essentia capability; such a job uses only TAKE and GIVE. */
    ESSENTIA;

    /** The codec of the lowercase names {@code items} and {@code essentia}. */
    public static final Codec<GolemCarryKind> CODEC =
            StringRepresentable.fromEnum(GolemCarryKind::values);

    /**
     * The lowercase name used in data.
     *
     * @return {@code items} or {@code essentia}
     */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
