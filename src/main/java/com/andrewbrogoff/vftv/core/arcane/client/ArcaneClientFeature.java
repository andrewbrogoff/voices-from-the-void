package com.andrewbrogoff.vftv.core.arcane.client;

import com.andrewbrogoff.vftv.core.arcane.ArcanePreview;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipe;
import com.andrewbrogoff.vftv.core.arcane.ArcaneRecipes;
import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.research.client.RecipePreviewSource;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/**
 * The arcane feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during
 * client mod construction. It keeps the client network for the Worktable's craft payload and
 * registers the arcane recipe preview for book pages and fact spans.
 */
public final class ArcaneClientFeature {
    private static @Nullable ClientNetwork network;

    private ArcaneClientFeature() {}

    /** Keeps the network and registers the {@code vftv:arcane} recipe preview. */
    public static void init(ClientNetwork seam) {
        network = seam;
        RecipePreviewSource.Registry.register(ArcaneRecipe.TYPE, new Preview());
    }

    /** The client network, for {@code toServer}. */
    static ClientNetwork network() {
        ClientNetwork n = network;
        if (n == null) {
            throw new IllegalStateException("ArcaneClientFeature.init has not run");
        }
        return n;
    }

    /** Fact fields resolved through {@link ArcanePreview}, priced with the reference wand. */
    private static final class Preview implements RecipePreviewSource {
        @Override
        public Set<String> fields() {
            return ArcanePreview.FIELDS;
        }

        @Override
        public @Nullable Component fact(Player reader, Identifier recipe, String field) {
            RecipeHolder<ArcaneRecipe> holder = ArcaneRecipes.client().get(recipe);
            return holder == null
                    ? null
                    : ArcanePreview.of(reader, holder, ArcanePreview.reference(reader))
                            .fact(holder.value(), field);
        }
    }
}
