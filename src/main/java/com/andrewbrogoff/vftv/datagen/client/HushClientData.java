package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import com.andrewbrogoff.vftv.core.hush.HushContent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * The Hush's client assets (Hush spec, "Models"): each hushed form on its vanilla parent, every
 * property state mapping to its one model with vanilla's axis rotations and no tint indices, its
 * item on the block model; the bell and Ash flat items; the Pealing Bell's six cuboids, a bell in a
 * brass yoke, rotated to its four facings.
 */
final class HushClientData implements FeatureClientData {
    private static final Identifier BELL_MODEL =
            Identifier.fromNamespaceAndPath("vftv", "block/pealing_bell");

    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        for (HushedForm f : HushKeys.FORMS) {
            Block block = HushContent.form(f.form());
            Identifier model =
                    switch (f.source().getPath()) {
                        case "grass_block" ->
                                ModelTemplates.CUBE_BOTTOM_TOP.create(
                                        block,
                                        new TextureMapping()
                                                .put(
                                                        TextureSlot.TOP,
                                                        TextureMapping.getBlockTexture(
                                                                block, "_top"))
                                                .put(
                                                        TextureSlot.SIDE,
                                                        TextureMapping.getBlockTexture(
                                                                block, "_side"))
                                                .put(
                                                        TextureSlot.BOTTOM,
                                                        TextureMapping.getBlockTexture(
                                                                HushContent.form(
                                                                        Identifier
                                                                                .fromNamespaceAndPath(
                                                                                        "vftv",
                                                                                        "hushed_dirt")))),
                                        blocks.modelOutput);
                        case "deepslate", "oak_log", "birch_log", "spruce_log" ->
                                TexturedModel.COLUMN.create(block, blocks.modelOutput);
                        case "short_grass" ->
                                ModelTemplates.CROSS.create(
                                        block, TextureMapping.cross(block), blocks.modelOutput);
                        default -> TexturedModel.CUBE.create(block, blocks.modelOutput);
                    };
            if (block.getStateDefinition().getProperties().stream()
                    .anyMatch(p -> p.getName().equals("axis"))) {
                blocks.createAxisAlignedPillarBlockCustomModel(
                        block, BlockModelGenerators.plainVariant(model));
            } else {
                blocks.blockStateOutput.accept(
                        MultiVariantGenerator.dispatch(
                                block, BlockModelGenerators.plainVariant(model)));
            }
            blocks.itemModelOutput.accept(
                    HushContent.formItem(f.form()), ItemModelUtils.plainModel(model));
        }
        items.generateFlatItem(HushContent.clearingBell(), ModelTemplates.FLAT_ITEM);
        items.generateFlatItem(HushContent.hushAsh(), ModelTemplates.FLAT_ITEM);
        blocks.modelOutput.accept(BELL_MODEL, HushClientData::bell);
        blocks.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(
                                HushContent.pealingBell(),
                                BlockModelGenerators.plainVariant(BELL_MODEL))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blocks.itemModelOutput.accept(
                HushContent.pealingBellItem(), ItemModelUtils.plainModel(BELL_MODEL));
    }

    /** A plinth, two posts and a crossbar of brass; the bell's crown and lip hang between. */
    private static JsonObject bell() {
        JsonArray elements = new JsonArray();
        elements.add(cuboid(1, 0, 1, 15, 2, 15, "#yoke"));
        elements.add(cuboid(1, 2, 7, 3, 14, 9, "#yoke"));
        elements.add(cuboid(13, 2, 7, 15, 14, 9, "#yoke"));
        elements.add(cuboid(1, 14, 6, 15, 16, 10, "#yoke"));
        elements.add(cuboid(5, 10, 5, 11, 13, 11, "#bell"));
        elements.add(cuboid(4, 4, 4, 12, 10, 12, "#bell"));
        JsonObject textures = new JsonObject();
        textures.addProperty("yoke", "vftv:block/pealing_bell_yoke");
        textures.addProperty("bell", "vftv:block/pealing_bell_bell");
        textures.addProperty("particle", "vftv:block/pealing_bell_bell");
        JsonObject display = new JsonObject();
        JsonObject gui = new JsonObject();
        gui.add("rotation", array(30, 225, 0));
        gui.add("scale", array(0.85, 0.85, 0.85));
        display.add("gui", gui);
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        model.add("display", display);
        return model;
    }

    private static JsonObject cuboid(
            int x1, int y1, int z1, int x2, int y2, int z2, String texture) {
        JsonObject faces = new JsonObject();
        for (String face : new String[] {"north", "south", "west", "east", "up", "down"}) {
            JsonObject f = new JsonObject();
            f.addProperty("texture", texture);
            faces.add(face, f);
        }
        if (y1 == 0) {
            faces.getAsJsonObject("down").addProperty("cullface", "down");
        }
        JsonObject element = new JsonObject();
        element.add("from", array(x1, y1, z1));
        element.add("to", array(x2, y2, z2));
        element.add("faces", faces);
        return element;
    }

    private static JsonArray array(double... values) {
        JsonArray array = new JsonArray();
        for (double v : values) {
            if (v == Math.rint(v)) {
                array.add((int) v);
            } else {
                array.add(v);
            }
        }
        return array;
    }
}
