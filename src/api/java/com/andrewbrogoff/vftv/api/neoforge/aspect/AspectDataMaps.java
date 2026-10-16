package com.andrewbrogoff.vftv.api.neoforge.aspect;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * The aspect feature's data-map types, all synced to clients and following {@code /reload}. vftv
 * registers them in NeoForge's {@code RegisterDataMapTypesEvent}; read them through NeoForge's
 * data-map API after that, and again after {@code DataMapsUpdatedEvent}. See the aspects spec,
 * "Data maps".
 */
@Stable
public final class AspectDataMaps {
    private AspectDataMaps() {}

    /** {@code vftv:aspects} on items: an item's aspects. */
    public static final DataMapType<Item, AspectValues> ITEM =
            DataMapType.builder(AspectKeys.ASPECTS, Registries.ITEM, AspectValues.CODEC)
                    .synced(AspectValues.CODEC, false)
                    .build();

    /**
     * {@code vftv:aspects} on blocks: only blocks with no item form, and blocks that differ from
     * their item.
     */
    public static final DataMapType<Block, AspectValues> BLOCK =
            DataMapType.builder(AspectKeys.ASPECTS, Registries.BLOCK, AspectValues.CODEC)
                    .synced(AspectValues.CODEC, false)
                    .build();

    /** {@code vftv:aspects} on entity types: an entity's aspects. */
    public static final DataMapType<EntityType<?>, AspectValues> ENTITY_TYPE =
            DataMapType.builder(AspectKeys.ASPECTS, Registries.ENTITY_TYPE, AspectValues.CODEC)
                    .synced(AspectValues.CODEC, false)
                    .build();

    /**
     * {@code vftv:aspect_defaults} on items, keyed by convention tags: consulted only where {@link
     * #ITEM} has no entry.
     */
    public static final DataMapType<Item, AspectValues> DEFAULTS =
            DataMapType.builder(AspectKeys.ASPECT_DEFAULTS, Registries.ITEM, AspectValues.CODEC)
                    .synced(AspectValues.CODEC, false)
                    .build();
}
