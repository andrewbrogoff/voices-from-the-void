package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The required built-in node type {@code vftv:wellspring}. Stable: no id here is removed or renamed
 * within a major. See the wands spec, "Node types".
 */
@Stable
public final class NodeTypeKeys {
    private NodeTypeKeys() {}

    /**
     * {@code vftv:wellspring}: the starter's type, anchored, holding exactly the six built-in
     * primals at 32 each.
     */
    public static final ResourceKey<NodeType> WELLSPRING =
            ResourceKey.create(VisKeys.NODE_TYPE_REGISTRY, id("wellspring"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
