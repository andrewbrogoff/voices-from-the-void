package com.andrewbrogoff.vftv.api.worldgen;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.vis.NodeType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The worldgen feature's ids: the {@code vftv:choir_site} registry, the tags {@code
 * #vftv:choir_sites}, {@code #vftv:answerers} and {@code #vftv:choir_weathering}, the {@code
 * vftv:choir_ruin} structure and site, the {@code vftv:choir} node type and the {@code
 * vftv:choir_claims} attachment id. Stable: no id here is removed or renamed within a major. See
 * the worldgen spec, "Events".
 */
@Stable
public final class WorldgenKeys {
    private WorldgenKeys() {}

    /** The server-only datapack registry {@code vftv:choir_site} of the Reliquary site kinds. */
    public static final ResourceKey<Registry<ChoirSite>> CHOIR_SITE =
            ResourceKey.createRegistryKey(id("choir_site"));

    /** {@code vftv:choir_ruin}: the required built-in site kind, giving the Stage 3 Choir pages. */
    public static final ResourceKey<ChoirSite> CHOIR_RUIN_SITE =
            ResourceKey.create(CHOIR_SITE, id("choir_ruin"));

    /** {@code vftv:choir_ruin}: the Choir ruin jigsaw structure. */
    public static final ResourceKey<Structure> CHOIR_RUIN =
            ResourceKey.create(Registries.STRUCTURE, id("choir_ruin"));

    /**
     * {@code #vftv:choir_sites}: the structures whose chunks are Choir sites, and so Void-touched
     * places. At most {@link WorldgenLimits#MAX_SITE_STRUCTURES} of them are read.
     */
    public static final TagKey<Structure> CHOIR_SITES =
            TagKey.create(Registries.STRUCTURE, id("choir_sites"));

    /** {@code #vftv:answerers}: the entity types that answer an alert and receive a chant. */
    public static final TagKey<EntityType<?>> ANSWERERS =
            TagKey.create(Registries.ENTITY_TYPE, id("answerers"));

    /**
     * {@code #vftv:choir_weathering}: the masonry a ruin's weathering rules may match or produce.
     */
    public static final TagKey<Block> CHOIR_WEATHERING =
            TagKey.create(Registries.BLOCK, id("choir_weathering"));

    /**
     * {@code vftv:choir}: the node type a ruin's chancel holds, in the {@code vftv:node_type}
     * registry.
     */
    public static final ResourceKey<NodeType> CHOIR_NODE_TYPE =
            ResourceKey.create(
                    ResourceKey.<NodeType>createRegistryKey(id("node_type")), id("choir"));

    /**
     * The id of the {@code vftv:choir_claims} player attachment (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier CHOIR_CLAIMS_ATTACHMENT = id("choir_claims");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
