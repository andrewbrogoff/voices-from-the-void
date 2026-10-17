package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.aspect.AspectContent;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.renderer.item.properties.numeric.Cooldown;
import net.minecraft.world.item.Item;

/**
 * The aspect feature's client asset generation: the Tuning Fork's model and its "ringing" variant,
 * picked by the native {@code minecraft:cooldown} property while the strike cooldown runs (aspects
 * spec, "Striking"). Listed once in {@link VftvClientData#FEATURES}.
 */
final class AspectClientData implements FeatureClientData {
    /** Any cooldown left reads as ringing. */
    private static final float RINGING = 0.01f;

    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        Item fork = AspectContent.tuningFork();
        items.itemModelOutput.accept(
                fork,
                ItemModelUtils.rangeSelect(
                        new Cooldown(),
                        ItemModelUtils.plainModel(
                                items.createFlatItemModel(fork, ModelTemplates.FLAT_ITEM)),
                        ItemModelUtils.override(
                                ItemModelUtils.plainModel(
                                        items.createFlatItemModel(
                                                fork, "_ringing", ModelTemplates.FLAT_ITEM)),
                                RINGING)));
    }
}
