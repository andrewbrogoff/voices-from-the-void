package com.andrewbrogoff.vftv.api.hush;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/**
 * The Hush feature's ids: the tags {@code #vftv:hushed} and {@code #vftv:hush_immune}, the {@code
 * vftv:hushed} node type, the {@code vftv:hollow} entity type, the {@code vftv:aura} attachment id
 * and the fixed hushed-form table. Stable: no id here is removed or renamed within a major.
 */
@Stable
public final class HushKeys {
    private HushKeys() {}

    /** {@code #vftv:hushed}: every hushed form block. */
    public static final TagKey<Block> HUSHED = TagKey.create(Registries.BLOCK, id("hushed"));

    /** {@code #vftv:hush_immune}: blocks the Hush passes through (data, empty by default). */
    public static final TagKey<Block> HUSH_IMMUNE =
            TagKey.create(Registries.BLOCK, id("hush_immune"));

    /** The node type {@code vftv:hushed}, in the {@code vftv:node_type} registry. */
    public static final ResourceKey<NodeType> HUSHED_NODE =
            ResourceKey.create(ResourceKey.createRegistryKey(id("node_type")), id("hushed"));

    /** The entity type {@code vftv:hollow}, which rises from hushed ground and never spawns. */
    public static final ResourceKey<EntityType<?>> HOLLOW =
            ResourceKey.create(Registries.ENTITY_TYPE, id("hollow"));

    /**
     * The id of the {@code vftv:aura} chunk attachment, a chunk's pooled flux (diagnostics only;
     * direct use is unsupported).
     */
    public static final Identifier AURA_ATTACHMENT = id("aura");

    /**
     * The fixed hushed-form table, unmodifiable, in the spec's order: 13 source blocks and their
     * hushed forms. Fixed in code; no data extends it.
     */
    public static final List<HushedForm> FORMS =
            List.of(
                    form("grass_block"),
                    form("dirt"),
                    form("stone"),
                    form("sand"),
                    form("gravel"),
                    form("deepslate"),
                    form("short_grass"),
                    form("oak_log"),
                    form("birch_log"),
                    form("spruce_log"),
                    form("oak_leaves"),
                    form("birch_leaves"),
                    form("spruce_leaves"));

    private static HushedForm form(String vanillaPath) {
        return new HushedForm(
                Identifier.withDefaultNamespace(vanillaPath), id("hushed_" + vanillaPath));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
