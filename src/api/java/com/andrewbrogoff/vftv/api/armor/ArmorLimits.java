package com.andrewbrogoff.vftv.api.armor;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * The armor feature's fixed limits. javac inlines these constants into addon code, so an addon
 * keeps the value it compiled against even after a minor release changes ours.
 */
@Stable
public final class ArmorLimits {
    private ArmorLimits() {}

    /**
     * The Baffle's format bound in points: the top of the {@code vftv:baffle} attribute and of the
     * stored charge. A data-format bound: may only widen within a major.
     */
    public static final int BAFFLE_FORMAT_MAX = 40;

    /**
     * The largest {@code armor} of a {@code vftv:wearable} entry, in armor points (range 0 to
     * this). A data-format bound: may only widen within a major.
     */
    public static final int WEARABLE_MAX_ARMOR = 20;

    /**
     * The largest {@code baffle} of a {@code vftv:wearable} entry, in Baffle points (range 0 to
     * this). A data-format bound: may only widen within a major.
     */
    public static final int WEARABLE_MAX_BAFFLE = 20;

    /** Baffle HUD pips in one row. A runtime cap: may change in any minor. */
    public static final int HUD_PIPS_PER_ROW = 10;

    /** Rows of Baffle HUD pips at most. A runtime cap: may change in any minor. */
    public static final int HUD_MAX_ROWS = 2;
}
