package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.worldgen.WorldgenContent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.resources.Identifier;

/**
 * The worldgen feature's client assets (worldgen spec, "Models", "Reliquary assets"): the
 * Reliquary's four cuboids (a mauve-stone plinth and neck, a brass-bound page box and its lid),
 * pixel-aligned with default UVs and the sides painted as one front elevation, rotated to its four
 * facings; its item on the block model. The Answerers are drawn by their renderer.
 */
final class WorldgenClientData implements FeatureClientData {
    private static final Identifier MODEL =
            Identifier.fromNamespaceAndPath("vftv", "block/reliquary");

    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        blocks.modelOutput.accept(MODEL, WorldgenClientData::reliquary);
        blocks.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(
                                WorldgenContent.reliquary(), BlockModelGenerators.plainVariant(MODEL))
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING));
        blocks.itemModelOutput.accept(
                WorldgenContent.reliquaryItem(), ItemModelUtils.plainModel(MODEL));
    }

    /** The plinth, its neck, the page box and the lid; the box's front faces north. */
    private static JsonObject reliquary() {
        JsonArray elements = new JsonArray();
        elements.add(cuboid(1, 0, 1, 15, 3, 15, "#side"));
        elements.add(cuboid(3, 3, 3, 13, 6, 13, "#side"));
        elements.add(cuboid(2, 6, 3, 14, 12, 13, "#front"));
        elements.add(cuboid(1, 12, 2, 15, 14, 14, "#side"));
        JsonObject textures = new JsonObject();
        for (String t : new String[] {"side", "front", "top", "bottom"}) {
            textures.addProperty(t, "vftv:block/reliquary_" + t);
        }
        textures.addProperty("particle", "vftv:block/reliquary_side");
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static JsonObject cuboid(
            int x1, int y1, int z1, int x2, int y2, int z2, String north) {
        JsonObject faces = new JsonObject();
        face(faces, "north", north);
        face(faces, "south", "#side");
        face(faces, "west", "#side");
        face(faces, "east", "#side");
        face(faces, "up", "#top");
        face(faces, "down", "#bottom");
        if (y1 == 0) {
            faces.getAsJsonObject("down").addProperty("cullface", "down");
        }
        JsonObject element = new JsonObject();
        element.add("from", array(x1, y1, z1));
        element.add("to", array(x2, y2, z2));
        element.add("faces", faces);
        return element;
    }

    private static void face(JsonObject faces, String name, String texture) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", texture);
        faces.add(name, face);
    }

    private static JsonArray array(int... values) {
        JsonArray array = new JsonArray();
        for (int v : values) {
            array.add(v);
        }
        return array;
    }
}
