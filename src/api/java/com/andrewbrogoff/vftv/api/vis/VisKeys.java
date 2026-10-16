package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * The wand feature's ids: the {@code vftv:wand_rod}, {@code vftv:wand_cap} and {@code
 * vftv:node_type} registries, the {@code vftv:vis_discount} attribute, and the ids of the {@code
 * vftv:vis}, {@code vftv:wand_rod}, {@code vftv:wand_cap}, {@code vftv:wand_focus} and {@code
 * vftv:captured_node} data components. Stable: no id here is removed or renamed within a major.
 */
@Stable
public final class VisKeys {
    private VisKeys() {}

    /** The synced datapack registry {@code vftv:wand_rod} of the wand rods. */
    public static final ResourceKey<Registry<WandRod>> WAND_ROD_REGISTRY =
            ResourceKey.createRegistryKey(id("wand_rod"));

    /** The synced datapack registry {@code vftv:wand_cap} of the wand caps. */
    public static final ResourceKey<Registry<WandCap>> WAND_CAP_REGISTRY =
            ResourceKey.createRegistryKey(id("wand_cap"));

    /** The synced datapack registry {@code vftv:node_type} of the node types. */
    public static final ResourceKey<Registry<NodeType>> NODE_TYPE_REGISTRY =
            ResourceKey.createRegistryKey(id("node_type"));

    /**
     * The syncable attribute {@code vftv:vis_discount}: a fraction off every vis cost, default 0,
     * range 0 to 1, capped in the quote by the tuning's {@code quote.max_discount}.
     */
    public static final ResourceKey<Attribute> VIS_DISCOUNT =
            ResourceKey.create(Registries.ATTRIBUTE, id("vis_discount"));

    /**
     * The id of the {@code vftv:vis} item component, a wand's pools (diagnostics only; direct use
     * is unsupported).
     */
    public static final Identifier VIS_COMPONENT = id("vis");

    /**
     * The id of the {@code vftv:wand_rod} item component, a wand's rod key (diagnostics only;
     * direct use is unsupported).
     */
    public static final Identifier WAND_ROD_COMPONENT = id("wand_rod");

    /**
     * The id of the {@code vftv:wand_cap} item component, a wand's cap key (diagnostics only;
     * direct use is unsupported).
     */
    public static final Identifier WAND_CAP_COMPONENT = id("wand_cap");

    /**
     * The id of the {@code vftv:wand_focus} item component, a wand's attached focus (diagnostics
     * only; direct use is unsupported).
     */
    public static final Identifier WAND_FOCUS_COMPONENT = id("wand_focus");

    /**
     * The id of the {@code vftv:captured_node} item component, a filled Bell Jar's node
     * (diagnostics only; direct use is unsupported).
     */
    public static final Identifier CAPTURED_NODE_COMPONENT = id("captured_node");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
