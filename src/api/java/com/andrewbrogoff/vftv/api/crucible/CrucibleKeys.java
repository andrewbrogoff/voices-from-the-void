package com.andrewbrogoff.vftv.api.crucible;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The crucible feature's ids: the tags {@code #vftv:crucible_heat}, {@code #vftv:crucible_refuses}
 * and {@code #c:ingots/resonant_brass}, and the ids of the {@code vftv:crucible_dissolve} and
 * {@code vftv:crucible_refused} entity attachments. Stable: no id here is removed or renamed within
 * a major.
 */
@Stable
public final class CrucibleKeys {
    private CrucibleKeys() {}

    /**
     * {@code #vftv:crucible_heat}: the blocks that heat a crucible directly above them (when they
     * have no {@code lit} property or are lit).
     */
    public static final TagKey<Block> CRUCIBLE_HEAT =
            TagKey.create(Registries.BLOCK, id("crucible_heat"));

    /** {@code #vftv:crucible_refuses}: the protected items a crucible never dissolves. */
    public static final TagKey<Item> CRUCIBLE_REFUSES =
            TagKey.create(Registries.ITEM, id("crucible_refuses"));

    /**
     * {@code #c:ingots/resonant_brass}: the common tag holding {@code vftv:resonant_brass_ingot}
     * (NeoForge has no constant for this material).
     */
    public static final TagKey<Item> RESONANT_BRASS_INGOTS =
            TagKey.create(
                    Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/resonant_brass"));

    /**
     * The id of the {@code vftv:crucible_dissolve} entity attachment, the sneak-drop mark
     * (diagnostics only; direct use is unsupported).
     */
    public static final Identifier CRUCIBLE_DISSOLVE_ATTACHMENT = id("crucible_dissolve");

    /**
     * The id of the {@code vftv:crucible_refused} entity attachment, the refusal mark (diagnostics
     * only; direct use is unsupported).
     */
    public static final Identifier CRUCIBLE_REFUSED_ATTACHMENT = id("crucible_refused");

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
