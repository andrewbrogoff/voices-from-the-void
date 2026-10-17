package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.research.ResearchItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;

/**
 * The research feature's client asset generation: override the {@link FeatureClientData} hooks it
 * needs. Listed once in {@link VftvClientData#FEATURES}.
 */
final class ResearchClientData implements FeatureClientData {
    /** The Commonplace Book and the Choir page: flat items over their generated textures. */
    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        items.generateFlatItem(ResearchItems.commonplaceBook(), ModelTemplates.FLAT_ITEM);
        items.generateFlatItem(ResearchItems.choirPage(), ModelTemplates.FLAT_ITEM);
    }
}
