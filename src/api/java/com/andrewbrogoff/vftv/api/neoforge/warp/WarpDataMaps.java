package com.andrewbrogoff.vftv.api.neoforge.warp;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.warp.WarpKeys;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * The warp feature's data maps. Stable: no data map here is removed or renamed, and its value
 * format only widens, within a major. See the warp spec, "Gear warp".
 */
@Stable
public final class WarpDataMaps {
    private WarpDataMaps() {}

    /**
     * {@code vftv:gear_warp}: the gear warp an item adds while carried, an integer 1–10 per stack
     * (not multiplied by its count). Synced to clients, not mandatory.
     */
    public static final DataMapType<Item, Integer> GEAR_WARP =
            DataMapType.builder(WarpKeys.GEAR_WARP, Registries.ITEM, Codec.intRange(1, 10))
                    .synced(Codec.intRange(1, 10), false)
                    .build();
}
