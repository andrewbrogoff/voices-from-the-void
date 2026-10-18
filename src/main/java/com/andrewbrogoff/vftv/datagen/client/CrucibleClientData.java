package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.crucible.CrucibleBlock;
import com.andrewbrogoff.vftv.core.crucible.CrucibleContent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The crucible feature's client assets (crucible spec, "Crucible assets"): the five-cuboid vessel
 * model, its blockstate (the four {@code water} × {@code boiling} variants on that one model, the
 * liquid being the renderer's), and the item definitions of the crucible and Resonant Brass.
 */
final class CrucibleClientData implements FeatureClientData {
    private static final Identifier MODEL =
            Identifier.fromNamespaceAndPath("vftv", "block/crucible");

    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        blocks.modelOutput.accept(MODEL, CrucibleClientData::model);
        MultiVariant variant = BlockModelGenerators.plainVariant(MODEL);
        blocks.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(CrucibleContent.crucible())
                        .with(
                                PropertyDispatch.initial(CrucibleBlock.WATER, CrucibleBlock.BOILING)
                                        .generate((water, boiling) -> variant)));
        blocks.itemModelOutput.accept(
                CrucibleContent.crucibleItem(), ItemModelUtils.plainModel(MODEL));
        items.generateFlatItem(CrucibleContent.ingot(), ModelTemplates.FLAT_ITEM);
    }

    /** Floor [0,0,0]-[16,4,16] and four walls, textured side, top, inner and bottom. */
    private static JsonObject model() {
        JsonObject textures = new JsonObject();
        for (String face : new String[] {"side", "top", "inner", "bottom"}) {
            textures.addProperty(face, "vftv:block/crucible_" + face);
        }
        textures.addProperty("particle", "vftv:block/crucible_side");
        JsonArray elements = new JsonArray();
        elements.add(cuboid(0, 0, 0, 16, 4, 16, "#inner", "#bottom"));
        elements.add(cuboid(0, 4, 0, 2, 16, 16, "#top", null));
        elements.add(cuboid(14, 4, 0, 16, 16, 16, "#top", null));
        elements.add(cuboid(2, 4, 0, 14, 16, 2, "#top", null));
        elements.add(cuboid(2, 4, 14, 14, 16, 16, "#top", null));
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    /**
     * One cuboid: outward faces on the block's boundary use the side texture, faces looking into
     * the vessel use the inner texture; {@code up} and {@code down} as given.
     */
    private static JsonObject cuboid(
            int x1, int y1, int z1, int x2, int y2, int z2, String up, @Nullable String down) {
        JsonObject faces = new JsonObject();
        face(faces, "north", z1 == 0 ? "#side" : "#inner", z1 == 0 ? "north" : null);
        face(faces, "south", z2 == 16 ? "#side" : "#inner", z2 == 16 ? "south" : null);
        face(faces, "west", x1 == 0 ? "#side" : "#inner", x1 == 0 ? "west" : null);
        face(faces, "east", x2 == 16 ? "#side" : "#inner", x2 == 16 ? "east" : null);
        face(faces, "up", up, null);
        if (down != null) {
            face(faces, "down", down, "down");
        }
        JsonObject element = new JsonObject();
        element.add("from", array(x1, y1, z1));
        element.add("to", array(x2, y2, z2));
        element.add("faces", faces);
        return element;
    }

    private static void face(
            JsonObject faces, String name, String texture, @Nullable String cullface) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", texture);
        if (cullface != null) {
            face.addProperty("cullface", cullface);
        }
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
