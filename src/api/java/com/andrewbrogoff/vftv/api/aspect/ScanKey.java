package com.andrewbrogoff.vftv.api.aspect;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

/**
 * What a player's knowledge records a scan under. Anything with an item form is scanned as its item
 * (a placed log and a log item are one key); a block is keyed by itself only when it has its own
 * block-map entry. Block states and item components are ignored. Each variant's {@code toString()}
 * is the canonical text: {@code item:<id>}, {@code block:<id>}, {@code entity:<id>} or {@code
 * node:<stable id>}. See the aspects spec, "Knowledge".
 *
 * <p>Grows in minor releases: a switch over it needs a default arm.
 */
public sealed interface ScanKey {
    /**
     * An item's key, or a placed block's that has an item form.
     *
     * @param item the item
     */
    record Item(ResourceKey<net.minecraft.world.item.Item> item) implements ScanKey {
        /** Constructed by vftv only. */
        @Internal
        public Item {}

        /**
         * The canonical text.
         *
         * @return {@code item:<id>}
         */
        @Override
        public String toString() {
            return "item:" + item.identifier();
        }
    }

    /**
     * A block's key, for a block with its own block-map entry.
     *
     * @param block the block
     */
    record Block(ResourceKey<net.minecraft.world.level.block.Block> block) implements ScanKey {
        /** Constructed by vftv only. */
        @Internal
        public Block {}

        /**
         * The canonical text.
         *
         * @return {@code block:<id>}
         */
        @Override
        public String toString() {
            return "block:" + block.identifier();
        }
    }

    /**
     * An entity type's key.
     *
     * @param entity the entity type
     */
    record Entity(ResourceKey<EntityType<?>> entity) implements ScanKey {
        /** Constructed by vftv only. */
        @Internal
        public Entity {}

        /**
         * The canonical text.
         *
         * @return {@code entity:<id>}
         */
        @Override
        public String toString() {
            return "entity:" + entity.identifier();
        }
    }

    /**
     * A node's key, by its stable id, which survives moves.
     *
     * @param id the node's stable id
     */
    record Node(long id) implements ScanKey {
        /** Constructed by vftv only. */
        @Internal
        public Node {}

        /**
         * The canonical text.
         *
         * @return {@code node:<stable id>}
         */
        @Override
        public String toString() {
            return "node:" + id;
        }
    }
}
