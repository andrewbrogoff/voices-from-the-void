package com.andrewbrogoff.vftv.datagen.client;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;

/**
 * One feature's contribution to the client assets that need client classes to generate: block
 * states, models and item definitions. Each feature has one implementation, {@code
 * datagen.client.<F>ClientData}, listed once in {@link VftvClientData#FEATURES}; it overrides only
 * the hooks it needs.
 */
public interface FeatureClientData {
    /** Block states, block and item models, and item definitions. */
    default void models(BlockModelGenerators blocks, ItemModelGenerators items) {}
}
