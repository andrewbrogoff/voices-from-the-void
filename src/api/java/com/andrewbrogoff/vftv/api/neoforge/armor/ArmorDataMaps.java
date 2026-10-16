package com.andrewbrogoff.vftv.api.neoforge.armor;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.armor.ArmorKeys;
import com.andrewbrogoff.vftv.api.armor.Wearable;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * The armor feature's data-map types. vftv registers them in NeoForge's mod-bus {@code
 * RegisterDataMapTypesEvent}; read them through NeoForge's data-map API after that, and re-read
 * after {@code DataMapsUpdatedEvent}.
 */
@Stable
public final class ArmorDataMaps {
    private ArmorDataMaps() {}

    /**
     * {@code vftv:wearable} on items, synced: the raw entries. Which take effect is vftv's
     * validated view (the slot must match the item). See the armor spec, "Wearables".
     */
    public static final DataMapType<Item, Wearable> WEARABLE =
            DataMapType.builder(ArmorKeys.WEARABLE, Registries.ITEM, Wearable.CODEC)
                    .synced(Wearable.CODEC, false)
                    .build();
}
