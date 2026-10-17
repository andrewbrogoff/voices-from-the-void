package com.andrewbrogoff.vftv.core.aspect.client;

import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.core.aspect.AspectLang;
import com.andrewbrogoff.vftv.core.aspect.AspectLookup;
import com.andrewbrogoff.vftv.core.aspect.AspectSet;
import com.andrewbrogoff.vftv.core.aspect.KnowledgeService;
import com.andrewbrogoff.vftv.core.aspect.Strikes;
import com.andrewbrogoff.vftv.core.aspect.TuningForkItem;
import com.andrewbrogoff.vftv.core.aspect.model.Knowledge;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The item tooltips (aspects spec, "What a strike reveals"): the fork's instruction, and, while
 * Shift is held, the aspects of anything whose key the player has scanned, fully or partly. JEI's
 * tooltips run through this too, so it shows aspects only once the player knows them.
 */
public final class AspectTooltips {
    private AspectTooltips() {}

    /** Adds the feature's lines after the item's name. */
    public static void append(ItemStack stack, List<Component> tooltip, @Nullable Player player) {
        int at = Math.min(1, tooltip.size());
        if (Strikes.isFork(stack)) {
            tooltip.add(
                    at++,
                    Component.translatable(TuningForkItem.TOOLTIP).withStyle(ChatFormatting.GRAY));
        }
        if (player == null || !Minecraft.getInstance().hasShiftDown()) {
            return;
        }
        Knowledge knowledge = KnowledgeService.get(player);
        if (knowledge == null
                || !knowledge.scans().containsKey(AspectLookup.key(stack.getItem()).toString())) {
            return;
        }
        AspectValues values = AspectLookup.item(stack.getItem());
        if (values == null) {
            return;
        }
        for (var entry : values.aspects().entrySet()) {
            Component name =
                    knowledge.known().contains(AspectSet.text(entry.getKey()))
                            ? AspectLang.name(entry.getKey())
                            : Component.translatable(AspectLang.UNHEARD);
            tooltip.add(
                    at++,
                    Component.translatable(
                                    AspectLang.TOOLTIP_ENTRY,
                                    name,
                                    NumberFormats.integer(entry.getValue()))
                            .withStyle(ChatFormatting.GRAY));
        }
    }
}
