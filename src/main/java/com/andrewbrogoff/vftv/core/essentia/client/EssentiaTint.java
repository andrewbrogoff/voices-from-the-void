package com.andrewbrogoff.vftv.core.essentia.client;

import com.andrewbrogoff.vftv.core.common.RawRecordCodec.Retained;
import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.EssentiaRecords.JarStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The item tint source {@code vftv:essentia}: a filled jar item's liquid takes its aspect's colour.
 * The jar item shows no glyph; its tooltip carries the aspect.
 */
public record EssentiaTint() implements ItemTintSource {
    /** The tint source's id. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("vftv", "essentia");

    /** The codec: no fields. */
    public static final MapCodec<EssentiaTint> CODEC = MapCodec.unit(new EssentiaTint());

    @Override
    public int calculate(
            ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Retained<JarStack> contents = stack.get(EssentiaContent.contents());
        return contents instanceof Retained.Read<JarStack> r
                ? ClientEssentia.color(r.value().aspect())
                : 0xFFFFFFFF;
    }

    @Override
    public MapCodec<EssentiaTint> type() {
        return CODEC;
    }
}
