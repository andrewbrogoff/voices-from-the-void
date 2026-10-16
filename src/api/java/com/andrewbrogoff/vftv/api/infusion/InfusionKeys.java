package com.andrewbrogoff.vftv.api.infusion;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The infusion feature's ids: the block tag {@code #vftv:infusion_stabilisers} and the item tag
 * {@code #vftv:infusion_unenchantable}. Stable: no id here is removed or renamed within a major.
 */
@Stable
public final class InfusionKeys {
    private InfusionKeys() {}

    /**
     * The block tag {@code #vftv:infusion_stabilisers}: blocks that count as stabilisers around an
     * altar, when they have no {@code lit} property or are lit. Packs may extend it.
     */
    public static final TagKey<Block> INFUSION_STABILISERS =
            TagKey.create(Registries.BLOCK, id("infusion_stabilisers"));

    /**
     * The item tag {@code #vftv:infusion_unenchantable}: items infusion never enchants. Packs may
     * extend it.
     */
    public static final TagKey<Item> INFUSION_UNENCHANTABLE =
            TagKey.create(Registries.ITEM, id("infusion_unenchantable"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
