package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.arcane.ArcaneContent;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.resources.Identifier;

/**
 * The arcane feature's client assets (arcane spec, "Worktable assets"): the Worktable's {@code
 * cube_bottom_top} model, its one-variant blockstate and its item definition.
 */
final class ArcaneClientData implements FeatureClientData {
    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        Identifier model =
                TexturedModel.CUBE_TOP_BOTTOM.create(ArcaneContent.worktable(), blocks.modelOutput);
        blocks.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(
                        ArcaneContent.worktable(), BlockModelGenerators.plainVariant(model)));
        blocks.itemModelOutput.accept(
                ArcaneContent.worktableItem(), ItemModelUtils.plainModel(model));
    }
}
