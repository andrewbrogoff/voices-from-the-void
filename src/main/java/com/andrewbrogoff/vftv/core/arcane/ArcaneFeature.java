package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.core.common.Platform;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The arcane feature's entry into {@code core}: {@code Vftv.init} calls {@link #init} once, during
 * mod construction. This is the feature's hook for registration (content holders, datapack
 * registries, data slots) and for its payloads (serverbound handlers and clientbound types, through
 * {@code platform.network()}).
 */
public final class ArcaneFeature {
    private ArcaneFeature() {}

    /**
     * Registers the {@code vftv:arcane} recipe type and serializer, the Worktable, its menu and
     * sounds, and the {@link WorktableCraft} payload.
     */
    public static void init(Platform platform) {
        String name = ArcaneRecipe.ID.getPath();
        platform.registrar()
                .<RecipeType<?>>register(Registries.RECIPE_TYPE, name, () -> ArcaneRecipe.TYPE);
        platform.registrar()
                .<RecipeSerializer<?>>register(
                        Registries.RECIPE_SERIALIZER, name, () -> ArcaneRecipe.SERIALIZER);
        ArcaneContent.register(platform.registrar());
        platform.network()
                .serverbound(WorktableCraft.TYPE, WorktableCraft.STREAM_CODEC, Worktables::onCraft);
    }
}
