package com.andrewbrogoff.vftv.api.armor;

import com.andrewbrogoff.vftv.api.Stable;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * Where a {@code vftv:wearable} item counts, among the vanilla slots. Serialized as its lowercase
 * name. Grows in minor releases: a switch over it needs a default arm.
 */
@Stable
public enum WearableSlot implements StringRepresentable {
    /** The head slot, {@code head}. */
    HEAD,
    /** The chest slot, {@code chest}. */
    CHEST,
    /** The legs slot, {@code legs}. */
    LEGS,
    /** The feet slot, {@code feet}. */
    FEET,
    /** The off hand, {@code offhand}: only for an item that isn't {@code Equippable}. */
    OFFHAND;

    /** The codec by lowercase name. */
    public static final Codec<WearableSlot> CODEC =
            StringRepresentable.fromEnum(WearableSlot::values);

    /**
     * The serialized name.
     *
     * @return the lowercase name
     */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
