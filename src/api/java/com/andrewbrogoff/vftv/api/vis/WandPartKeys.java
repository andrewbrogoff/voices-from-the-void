package com.andrewbrogoff.vftv.api.vis;

import com.andrewbrogoff.vftv.api.Stable;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The required built-in wand parts: the rods {@code vftv:wooden}, {@code vftv:chime}, {@code
 * vftv:echo} and {@code vftv:void}, and the caps {@code vftv:copper}, {@code vftv:resonant_brass},
 * {@code vftv:echo} and {@code vftv:void}. Packs may override their values but never delete them.
 * Stable: no id here is removed or renamed within a major. See the wands spec, "Parts".
 */
@Stable
public final class WandPartKeys {
    private WandPartKeys() {}

    /** {@code vftv:wooden}: the Stage 1 rod, a {@code minecraft:stick}. */
    public static final ResourceKey<WandRod> WOODEN_ROD =
            ResourceKey.create(VisKeys.WAND_ROD_REGISTRY, id("wooden"));

    /** {@code vftv:chime}: the Stage 2 rod, {@code vftv:chime_rod}. */
    public static final ResourceKey<WandRod> CHIME_ROD =
            ResourceKey.create(VisKeys.WAND_ROD_REGISTRY, id("chime"));

    /** {@code vftv:echo}: the Stage 3 rod, {@code vftv:echo_rod}, added by the infusion feature. */
    public static final ResourceKey<WandRod> ECHO_ROD =
            ResourceKey.create(VisKeys.WAND_ROD_REGISTRY, id("echo"));

    /** {@code vftv:void}: the Stage 4 rod, {@code vftv:void_rod}, added by the Eldritch tier. */
    public static final ResourceKey<WandRod> VOID_ROD =
            ResourceKey.create(VisKeys.WAND_ROD_REGISTRY, id("void"));

    /** {@code vftv:copper}: the Stage 1 cap, a {@code minecraft:copper_nugget}. */
    public static final ResourceKey<WandCap> COPPER_CAP =
            ResourceKey.create(VisKeys.WAND_CAP_REGISTRY, id("copper"));

    /** {@code vftv:resonant_brass}: the Stage 2 cap, {@code vftv:resonant_brass_cap}. */
    public static final ResourceKey<WandCap> RESONANT_BRASS_CAP =
            ResourceKey.create(VisKeys.WAND_CAP_REGISTRY, id("resonant_brass"));

    /** {@code vftv:echo}: the Stage 3 cap, {@code vftv:echo_cap}, added by the infusion feature. */
    public static final ResourceKey<WandCap> ECHO_CAP =
            ResourceKey.create(VisKeys.WAND_CAP_REGISTRY, id("echo"));

    /** {@code vftv:void}: the Stage 4 cap, {@code vftv:void_cap}, added by the Eldritch tier. */
    public static final ResourceKey<WandCap> VOID_CAP =
            ResourceKey.create(VisKeys.WAND_CAP_REGISTRY, id("void"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }
}
