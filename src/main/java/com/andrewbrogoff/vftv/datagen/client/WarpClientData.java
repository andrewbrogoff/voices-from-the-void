package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.warp.WarpContent;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;

/**
 * The warp feature's client asset generation: override the {@link FeatureClientData} hooks it
 * needs. Listed once in {@link VftvClientData#FEATURES}.
 */
final class WarpClientData implements FeatureClientData {
    /** The Stilling Draught: a flat item over its generated texture. */
    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        items.generateFlatItem(WarpContent.stillingDraught(), ModelTemplates.FLAT_ITEM);
    }
}
