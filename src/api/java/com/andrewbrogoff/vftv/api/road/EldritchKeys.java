package com.andrewbrogoff.vftv.api.road;

import com.andrewbrogoff.vftv.api.Stable;
import com.andrewbrogoff.vftv.api.worldgen.ChoirSite;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The Eldritch tier's ids: the dimension {@code vftv:vuulas} with its dimension type and biome, the
 * {@code vftv:threshold} structure, the {@code vftv:threshold} and {@code vftv:road} site kinds,
 * the {@code vftv:chorister} registry and its three instances, the {@code vftv:chorister} entity
 * type, the Road's item and block tags, the find and ending advancements, and the {@code vftv:road}
 * attachment id. Stable: no id here is removed or renamed within a major. See the Eldritch tier
 * spec and the Choristers spec.
 */
@Stable
public final class EldritchKeys {
    private EldritchKeys() {}

    /** {@code vftv:vuulas}: the dimension where the Road lies. */
    public static final ResourceKey<Level> VUULAS =
            ResourceKey.create(Registries.DIMENSION, id("vuulas"));

    /** {@code vftv:vuulas}: the dimension type of {@link #VUULAS}. */
    public static final ResourceKey<DimensionType> VUULAS_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, id("vuulas"));

    /** {@code vftv:vuulas}: the one biome of {@link #VUULAS}. */
    public static final ResourceKey<Biome> VUULAS_BIOME =
            ResourceKey.create(Registries.BIOME, id("vuulas"));

    /** {@code vftv:threshold}: the overworld structure where the Choir went in. */
    public static final ResourceKey<Structure> THRESHOLD =
            ResourceKey.create(Registries.STRUCTURE, id("threshold"));

    /** {@code vftv:threshold}: the site kind of a Threshold's Reliquary. */
    public static final ResourceKey<ChoirSite> THRESHOLD_SITE =
            ResourceKey.create(WorldgenKeys.CHOIR_SITE, id("threshold"));

    /** {@code vftv:road}: the site kind of the Reliquaries at Steps 1 to 3. */
    public static final ResourceKey<ChoirSite> ROAD_SITE =
            ResourceKey.create(WorldgenKeys.CHOIR_SITE, id("road"));

    /** The synced datapack registry {@code vftv:chorister} of the Choristers' instances. */
    public static final ResourceKey<Registry<Chorister>> CHORISTER =
            ResourceKey.createRegistryKey(id("chorister"));

    /** {@code vftv:wuuren}: the Chorister of Step 1. */
    public static final ResourceKey<Chorister> WUUREN = ResourceKey.create(CHORISTER, id("wuuren"));

    /** {@code vftv:suhlaa}: the Chorister of Step 2. */
    public static final ResourceKey<Chorister> SUHLAA = ResourceKey.create(CHORISTER, id("suhlaa"));

    /** {@code vftv:nihthaa}: the Chorister of Step 3. */
    public static final ResourceKey<Chorister> NIHTHAA =
            ResourceKey.create(CHORISTER, id("nihthaa"));

    /** {@code vftv:chorister}: the entity type every Chorister instance uses. */
    public static final ResourceKey<EntityType<?>> CHORISTER_ENTITY =
            ResourceKey.create(Registries.ENTITY_TYPE, id("chorister"));

    /** {@code #vftv:usable_on_the_road}: the items whose use on a block Vuulas allows. */
    public static final TagKey<Item> USABLE_ON_THE_ROAD =
            TagKey.create(Registries.ITEM, id("usable_on_the_road"));

    /** {@code #vftv:forbidden_on_the_road}: the items whose use Vuulas refuses outright. */
    public static final TagKey<Item> FORBIDDEN_ON_THE_ROAD =
            TagKey.create(Registries.ITEM, id("forbidden_on_the_road"));

    /** {@code #vftv:unsafe_return}: the blocks a safe standing point may not hold or stand on. */
    public static final TagKey<Block> UNSAFE_RETURN =
            TagKey.create(Registries.BLOCK, id("unsafe_return"));

    /** The advancement {@code vftv:find/choirs_road}, the find that opens Stage 4. */
    public static final Identifier FIND_CHOIRS_ROAD = id("find/choirs_road");

    /** The advancement {@code vftv:end/joined}, awarded only by code to a Joined Listener. */
    public static final Identifier END_JOINED = id("end/joined");

    /** The advancement {@code vftv:end/silenced}, awarded only by code to a Silenced Listener. */
    public static final Identifier END_SILENCED = id("end/silenced");

    /**
     * The id of the {@code vftv:road} player attachment (diagnostics only; direct use is
     * unsupported).
     */
    public static final Identifier ROAD_ATTACHMENT = id("road");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
