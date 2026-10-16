package com.andrewbrogoff.vftv.api.arcane;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The arcane crafting feature's ids: the block tag {@code #vftv:converts_to_worktable}. Stable: no
 * id here is removed or renamed within a major.
 */
@Stable
public final class ArcaneKeys {
    private ArcaneKeys() {}

    /**
     * {@code #vftv:converts_to_worktable}: the blocks a wand turns into a Worktable (default {@code
     * minecraft:crafting_table} only). Packs may extend it; a block with a block entity is never
     * converted.
     */
    public static final TagKey<Block> CONVERTS_TO_WORKTABLE =
            TagKey.create(Registries.BLOCK, id("converts_to_worktable"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
