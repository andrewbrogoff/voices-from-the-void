package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.infusion.InfusionContent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.resources.Identifier;

/**
 * The infusion feature's client assets (infusion spec, "Infusion assets"): the Singing Bowl's six
 * cuboids and the Pedestal's three, pixel-aligned with default UVs, one blockstate variant each,
 * the block items on their block models, and the Echo rod's and cap's flat items. The wand layers
 * and {@code items/wand.json} come from the wands generator, which reads every parts file.
 */
final class InfusionClientData implements FeatureClientData {
    private static final Identifier BOWL = Identifier.fromNamespaceAndPath("vftv", "block/singing_bowl");
    private static final Identifier PEDESTAL = Identifier.fromNamespaceAndPath("vftv", "block/pedestal");

    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        blocks.modelOutput.accept(BOWL, InfusionClientData::bowl);
        blocks.modelOutput.accept(PEDESTAL, InfusionClientData::pedestal);
        blocks.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(
                        InfusionContent.bowl(), BlockModelGenerators.plainVariant(BOWL)));
        blocks.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(
                        InfusionContent.pedestal(), BlockModelGenerators.plainVariant(PEDESTAL)));
        blocks.itemModelOutput.accept(InfusionContent.bowlItem(), ItemModelUtils.plainModel(BOWL));
        blocks.itemModelOutput.accept(
                InfusionContent.pedestalItem(), ItemModelUtils.plainModel(PEDESTAL));
        items.generateFlatItem(InfusionContent.echoRod(), ModelTemplates.FLAT_ITEM);
        items.generateFlatItem(InfusionContent.echoCap(), ModelTemplates.FLAT_ITEM);
    }

    /** The plinth, the floor and the four walls (infusion spec, "The blocks"). */
    private static JsonObject bowl() {
        JsonArray elements = new JsonArray();
        elements.add(cuboid(3, 0, 3, 13, 8, 13, "#side", "#side", "#side", "#side", "#bottom"));
        elements.add(cuboid(2, 8, 2, 14, 10, 14, "#side", "#side", "#side", "#side", "#inner"));
        elements.add(cuboid(2, 10, 2, 14, 14, 3, "#side", "#inner", "#side", "#side", "#top"));
        elements.add(cuboid(2, 10, 13, 14, 14, 14, "#inner", "#side", "#side", "#side", "#top"));
        elements.add(cuboid(2, 10, 3, 3, 14, 13, "#side", "#side", "#side", "#inner", "#top"));
        elements.add(cuboid(13, 10, 3, 14, 14, 13, "#side", "#side", "#inner", "#side", "#top"));
        return model("singing_bowl", elements, "side", "top", "inner", "bottom");
    }

    /** The base, the shaft and the top plate. */
    private static JsonObject pedestal() {
        JsonArray elements = new JsonArray();
        elements.add(cuboid(2, 0, 2, 14, 2, 14, "#side", "#side", "#side", "#side", "#top"));
        elements.add(cuboid(4, 2, 4, 12, 10, 12, "#side", "#side", "#side", "#side", "#top"));
        elements.add(cuboid(3, 10, 3, 13, 12, 13, "#side", "#side", "#side", "#side", "#top"));
        return model("pedestal", elements, "side", "top", "bottom");
    }

    private static JsonObject model(String name, JsonArray elements, String... textures) {
        JsonObject map = new JsonObject();
        for (String t : textures) {
            map.addProperty(t, "vftv:block/" + name + "_" + t);
        }
        map.addProperty("particle", "vftv:block/" + name + "_side");
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", map);
        model.add("elements", elements);
        return model;
    }

    /** One cuboid with default UVs: north, south, west, east, up as given, down on the bottom. */
    private static JsonObject cuboid(
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2,
            String north,
            String south,
            String west,
            String east,
            String up) {
        JsonObject faces = new JsonObject();
        face(faces, "north", north);
        face(faces, "south", south);
        face(faces, "west", west);
        face(faces, "east", east);
        face(faces, "up", up);
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
