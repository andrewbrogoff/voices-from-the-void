package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.aspect.ScanKey;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The one lookup of what a thing is made of (aspects spec, "Data maps"; compatibility, "In play"):
 * a placed block reads its block entry, else its item's; an item reads its entry, else the {@code
 * c:} tag defaults, else the derived values; an entity reads its entry, and a dropped item reads as
 * the item it carries. Crucible, golems and armor read the same answer.
 *
 * <p>A null result is "no entry"; an empty value is the authored "nothing to learn". Both answer
 * nothing to a strike, but only no entry falls through to the next source.
 */
public final class AspectLookup {
    /** The data maps, as the platform reads them; {@code core} never sees the map types. */
    public interface Maps {
        /** {@code vftv:aspects} on an item. */
        @Nullable AspectValues item(Item item);

        /** {@code vftv:aspects} on a block. */
        @Nullable AspectValues block(Block block);

        /** {@code vftv:aspects} on an entity type. */
        @Nullable AspectValues entity(EntityType<?> type);

        /** {@code vftv:aspect_defaults} on an item, through its tags. */
        @Nullable AspectValues defaults(Item item);
    }

    /** No maps: before the platform installs them, and in unit tests. */
    public static final Maps NONE =
            new Maps() {
                @Override
                public @Nullable AspectValues item(Item item) {
                    return null;
                }

                @Override
                public @Nullable AspectValues block(Block block) {
                    return null;
                }

                @Override
                public @Nullable AspectValues entity(EntityType<?> type) {
                    return null;
                }

                @Override
                public @Nullable AspectValues defaults(Item item) {
                    return null;
                }
            };

    private static volatile Maps maps = NONE;
    private static volatile Map<Item, AspectValues> derived = Map.of();

    private AspectLookup() {}

    /** Installs the platform's maps; called once from the platform's wiring. */
    public static void install(Maps platformMaps) {
        maps = platformMaps;
    }

    /**
     * Replaces the derived values, consulted after the defaults (the {@code DerivedAspects} table;
     * empty until the derivation at load fills it).
     */
    public static void installDerived(Map<Item, AspectValues> table) {
        derived = Map.copyOf(table);
    }

    /** The derived values in force: the server's table, or a client's synced copy. */
    public static Map<Item, AspectValues> derived() {
        return derived;
    }

    /** An item's values: its entry, else its defaults, else its derived values; null for none. */
    public static @Nullable AspectValues item(Item item) {
        AspectValues v = authored(item);
        return v != null ? v : derived.get(item);
    }

    /**
     * An item's authored value: its entry, else its defaults; null when neither covers it. An item
     * with one is resolved, and the derivation never derives over it.
     */
    public static @Nullable AspectValues authored(Item item) {
        Maps m = maps;
        AspectValues v = m.item(item);
        return v != null ? v : m.defaults(item);
    }

    /** A placed block's values: its block entry, else its item form's values; null for none. */
    public static @Nullable AspectValues block(BlockState state) {
        AspectValues v = maps.block(state.getBlock());
        if (v != null) {
            return v;
        }
        Item item = state.getBlock().asItem();
        return item.equals(Items.AIR) ? null : item(item);
    }

    /** An entity's values: a dropped item's item, else its type's entry; null for none. */
    public static @Nullable AspectValues entity(Entity entity) {
        return entity instanceof ItemEntity drop
                ? item(drop.getItem().getItem())
                : maps.entity(entity.getType());
    }

    /** The scan key of an item. */
    public static ScanKey key(Item item) {
        return new ScanKey.Item(BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow());
    }

    /** The scan key of a placed block: its own only when it has a block entry or no item form. */
    public static ScanKey key(BlockState state) {
        Block block = state.getBlock();
        Item item = block.asItem();
        if (maps.block(block) == null && !item.equals(Items.AIR)) {
            return key(item);
        }
        return new ScanKey.Block(BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow());
    }

    /** The scan key of an entity: a dropped item's item, else its type. */
    public static ScanKey key(Entity entity) {
        if (entity instanceof ItemEntity drop) {
            return key(drop.getItem().getItem());
        }
        return new ScanKey.Entity(
                BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.getType()).orElseThrow());
    }
}
