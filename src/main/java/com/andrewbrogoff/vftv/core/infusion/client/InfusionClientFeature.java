package com.andrewbrogoff.vftv.core.infusion.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import com.andrewbrogoff.vftv.core.infusion.InfusionAltars;
import com.andrewbrogoff.vftv.core.infusion.InfusionPayloads;
import com.andrewbrogoff.vftv.core.infusion.InfusionPreview;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipe;
import com.andrewbrogoff.vftv.core.infusion.InfusionRecipes;
import com.andrewbrogoff.vftv.core.research.client.RecipePreviewSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
 * The infusion feature's client entry: {@code Vftv.initClient} calls {@link #init} once, during
 * client mod construction. It handles the rules and readout payloads and registers the infusion
 * recipe preview for book pages and fact spans.
 */
public final class InfusionClientFeature {
    private InfusionClientFeature() {}

    /** Registers the feature's client payload handlers and its recipe preview. */
    public static void init(ClientNetwork network) {
        InfusionHud.init(network);
        network.handler(InfusionPayloads.Rules.TYPE, p -> InfusionAltars.setClientRules(p.rules()));
        network.handler(InfusionPayloads.Readout.TYPE, InfusionHud::receive);
        RecipePreviewSource.Registry.register(InfusionRecipe.TYPE, new Preview());
    }

    /**
     * The preview's context (infusion spec, "The authoritative preview"): the player's current
     * readout when its bowl is the client's block hit within reach; null otherwise. Whether it is
     * this recipe's is the preview's to check.
     */
    public static InfusionPreview.@Nullable Context context() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        InfusionPreview.Context c = InfusionHud.current();
        if (c == null
                || player == null
                || mc.level == null
                || !mc.level.dimension().equals(c.dimension())
                || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(c.pos())
                || !player.isWithinBlockInteractionRange(c.pos(), 0)) {
            return null;
        }
        return c;
    }

    private static final class Preview implements RecipePreviewSource {
        @Override
        public Set<String> fields() {
            return InfusionPreview.FIELDS;
        }

        @Override
        // items() is the one loader-free path to a custom ingredient's alternatives; a fact is
        // rendered once per tooltip, not per tick.
        @SuppressWarnings("deprecation")
        public @Nullable Component fact(Player reader, Identifier recipe, String field) {
            RecipeHolder<InfusionRecipe> holder = InfusionRecipes.client().get(recipe);
            if (holder == null) {
                return null;
            }
            List<List<Holder<Item>>> components = new ArrayList<>();
            for (Ingredient c : holder.value().components()) {
                components.add(c.items().toList());
            }
            return InfusionPreview.of(reader, holder, context())
                    .fact(
                            holder.value(),
                            field,
                            holder.value().centre().items().toList(),
                            components);
        }
    }
}
