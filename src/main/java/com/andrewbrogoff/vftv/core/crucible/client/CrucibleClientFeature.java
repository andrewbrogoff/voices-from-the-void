package com.andrewbrogoff.vftv.core.crucible.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.crucible.CrucibleBlockEntity;
import com.andrewbrogoff.vftv.core.crucible.CruciblePreview;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipe;
import com.andrewbrogoff.vftv.core.crucible.CrucibleRecipes;
import com.andrewbrogoff.vftv.core.research.client.RecipePreviewSource;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * The crucible feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during
 * client mod construction. It registers the crucible's recipe preview for book pages and fact
 * spans.
 */
public final class CrucibleClientFeature {
    private CrucibleClientFeature() {}

    /** Registers the {@code vftv:crucible} recipe preview. */
    public static void init(ClientNetwork network) {
        RecipePreviewSource.Registry.register(CrucibleRecipe.TYPE, new Preview());
    }

    /**
     * The preview's context (crucible spec, "The authoritative preview"): the crucible the client's
     * block hit names within the player's interaction range, in a loaded chunk; null for none.
     */
    public static @Nullable CrucibleBlockEntity context() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null
                || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return player.isWithinBlockInteractionRange(pos, 0)
                        && mc.level.isLoaded(pos)
                        && mc.level.getBlockEntity(pos) instanceof CrucibleBlockEntity be
                ? be
                : null;
    }

    /** The preview of one recipe for the local player, or null when the client lacks it. */
    public static @Nullable CruciblePreview preview(Player reader, Identifier recipe) {
        RecipeHolder<CrucibleRecipe> holder = CrucibleRecipes.client().get(recipe);
        return holder == null ? null : CruciblePreview.of(reader, holder, context());
    }

    private static final class Preview implements RecipePreviewSource {
        @Override
        public Set<String> fields() {
            return CruciblePreview.FIELDS;
        }

        @Override
        public @Nullable Component fact(Player reader, Identifier recipe, String field) {
            RecipeHolder<CrucibleRecipe> holder = CrucibleRecipes.client().get(recipe);
            if (holder == null) {
                return null;
            }
            Ingredient catalyst = holder.value().catalyst();
            List<Holder<Item>> items =
                    catalyst.isCustom() ? List.of() : catalyst.getValues().stream().toList();
            return CruciblePreview.of(reader, holder, context()).fact(reader, field, items);
        }
    }
}
