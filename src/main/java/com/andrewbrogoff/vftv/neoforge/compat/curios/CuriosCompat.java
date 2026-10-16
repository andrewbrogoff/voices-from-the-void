package com.andrewbrogoff.vftv.neoforge.compat.curios;

import com.andrewbrogoff.vftv.neoforge.armor.WornSlots;
import net.neoforged.bus.api.IEventBus;

/**
 * The one entry into the Curios integration (compatibility, "Wiring the collector"). The
 * {@code @Mod} constructor calls {@link #init} only when Curios is loaded; nothing else references
 * this package ({@code COMPAT_CONFINED}).
 */
public final class CuriosCompat {
    private CuriosCompat() {}

    /**
     * Registers the Curios items and returns the collector over Curios slots, typed as the
     * interface.
     */
    public static WornSlots init(IEventBus modBus) {
        // ponytail: no Curios slot is read until the armor ticket returns its CuriosWorn here.
        return WornSlots.NONE;
    }
}
