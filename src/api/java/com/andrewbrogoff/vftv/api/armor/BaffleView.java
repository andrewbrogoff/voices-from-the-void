package com.andrewbrogoff.vftv.api.armor;

import com.andrewbrogoff.vftv.api.Stable;

/**
 * A read-only, live view of one server player's Baffle: its charge and its capacity, answered by
 * the same code as the HUD. Obtain it through {@code ArmorCapabilities.BAFFLE_VIEW} for a server
 * player, on the logical server's thread only; don't keep answers across ticks. See the armor spec,
 * "The Baffle".
 *
 * <p>Not for implementation: vftv provides the only implementation. The view exposes no storage, no
 * operation guard and no mutation.
 */
@Stable
public interface BaffleView {
    /**
     * Whether the player's Baffle record is readable. While it is unreadable the Baffle does
     * nothing.
     *
     * @return true if the record is readable
     */
    boolean readable();

    /**
     * The Baffle points left now.
     *
     * @return the charge, 0 to {@link ArmorLimits#BAFFLE_FORMAT_MAX}; 0 when unreadable
     */
    float current();

    /**
     * The Baffle's capacity now ({@code baffle_max}): the {@code vftv:baffle} attribute under its
     * cap, plus the Void armour's warp bonus.
     *
     * @return the capacity in points, 0 to {@link ArmorLimits#BAFFLE_FORMAT_MAX}
     */
    int capacity();
}
