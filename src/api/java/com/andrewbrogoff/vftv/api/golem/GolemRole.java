package com.andrewbrogoff.vftv.api.golem;

import com.andrewbrogoff.vftv.api.Stable;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * What a golem does at a mark. See the golems spec, "Roles".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
@Stable
public enum GolemRole implements StringRepresentable {
    /** Extracts items or essentia from the marked face's handler. */
    TAKE,
    /** Inserts the carry into the marked face's handler. */
    GIVE,
    /** Drops one item of the carry at the mark, thrown by the golem's owner (items only). */
    FEED,
    /** Drops one item of the carry at the mark, with no thrower (items only). */
    TIP,
    /** Picks up one loose item entity near the mark (items only). */
    COLLECT;

    /**
     * The codec of the lowercase names {@code take}, {@code give}, {@code feed}, {@code tip} and
     * {@code collect}.
     */
    public static final Codec<GolemRole> CODEC = StringRepresentable.fromEnum(GolemRole::values);

    /**
     * The lowercase name used in data.
     *
     * @return the constant's name in lowercase
     */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
