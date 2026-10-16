package com.andrewbrogoff.vftv.api.armor;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The armor feature's ids: the {@code vftv:baffle} attribute and attachment id, the {@code
 * vftv:wearable} data-map id, the tags {@code #vftv:void_armor}, {@code #vftv:reveals_aspects},
 * {@code #vftv:repairs_void_armor} and {@code #vftv:baffle_ignores}, and the {@code vftv:baffling}
 * enchantment. Stable: no id here is removed or renamed within a major.
 */
@Stable
public final class ArmorKeys {
    private ArmorKeys() {}

    /**
     * The attribute {@code vftv:baffle}: the Baffle's capacity input, default 0, range 0 to {@link
     * ArmorLimits#BAFFLE_FORMAT_MAX}. Addons may add modifiers to it.
     */
    public static final ResourceKey<Attribute> BAFFLE =
            ResourceKey.create(Registries.ATTRIBUTE, id("baffle"));

    /**
     * The id of the {@code vftv:baffle} player attachment, the Baffle's charge (diagnostics only;
     * direct use is unsupported).
     */
    public static final Identifier BAFFLE_ATTACHMENT = id("baffle");

    /** The id of the synced item data map {@code vftv:wearable}. */
    public static final Identifier WEARABLE = id("wearable");

    /** {@code #vftv:void_armor}: the Void pieces, counted by the Baffle's warp bonus. */
    public static final TagKey<Item> VOID_ARMOR = TagKey.create(Registries.ITEM, id("void_armor"));

    /** {@code #vftv:reveals_aspects}: worn on the head, shows the Tuned Spectacles card. */
    public static final TagKey<Item> REVEALS_ASPECTS =
            TagKey.create(Registries.ITEM, id("reveals_aspects"));

    /** {@code #vftv:repairs_void_armor}: the repair material of the Void pieces. */
    public static final TagKey<Item> REPAIRS_VOID_ARMOR =
            TagKey.create(Registries.ITEM, id("repairs_void_armor"));

    /** {@code #vftv:baffle_ignores}: damage types the Baffle never absorbs. */
    public static final TagKey<DamageType> BAFFLE_IGNORES =
            TagKey.create(Registries.DAMAGE_TYPE, id("baffle_ignores"));

    /** The enchantment {@code vftv:baffling}, adding to {@code vftv:baffle}. */
    public static final ResourceKey<Enchantment> BAFFLING =
            ResourceKey.create(Registries.ENCHANTMENT, id("baffling"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
