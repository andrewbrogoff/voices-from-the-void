package com.andrewbrogoff.vftv.core.essentia.client;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarStack;
import com.andrewbrogoff.vftv.core.text.model.NumberFormats;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * The jar's and label's tooltips (essentia spec, "The jar as an item"), built on the client with
 * masked names; the amount is always exact.
 */
public final class EssentiaTooltips {
    private EssentiaTooltips() {}

    /** Adds the feature's lines after the item's name. */
    public static void append(ItemStack stack, List<Component> tooltip) {
        int at = Math.min(1, tooltip.size());
        ResourceKey<Aspect> label = stack.get(EssentiaContent.labelComponent());
        if (stack.is(EssentiaContent.label())) {
            tooltip.add(
                    at,
                    (label == null
                                    ? Component.translatable("tooltip.vftv.label.blank")
                                    : Component.translatable(
                                            "tooltip.vftv.label.written",
                                            ClientEssentia.name(label)))
                            .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!stack.is(EssentiaContent.jarItem())) {
            return;
        }
        Retained<JarStack> contents = stack.get(EssentiaContent.contents());
        if (contents instanceof Retained.Unreadable<JarStack>) {
            tooltip.add(
                    at++,
                    Component.translatable("tooltip.vftv.essentia_jar.unreadable")
                            .withStyle(ChatFormatting.GRAY));
        } else if (contents instanceof Retained.Read<JarStack> r) {
            tooltip.add(
                    at++,
                    Component.translatable(
                                    "tooltip.vftv.essentia_jar.holds",
                                    NumberFormats.integer(r.value().amount()),
                                    ClientEssentia.name(r.value().aspect()))
                            .withStyle(ChatFormatting.GRAY));
        }
        if (label != null) {
            tooltip.add(
                    at,
                    Component.translatable(
                                    "tooltip.vftv.essentia_jar.labelled",
                                    ClientEssentia.name(label))
                            .withStyle(ChatFormatting.GRAY));
        }
    }
}
