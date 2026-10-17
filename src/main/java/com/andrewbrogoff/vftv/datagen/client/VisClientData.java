package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.WandCap;
import com.andrewbrogoff.vftv.api.vis.WandRod;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.client.NodeRenderer;
import com.andrewbrogoff.vftv.datagen.ContentProvider;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The vis feature's client asset generation (wands spec, "Components"; plan: the {@code wand.json}
 * generator): {@code items/wand.json} as a composite of two {@code minecraft:select} models on the
 * rod and cap components, one case per part in every {@code content/} parts file and a fallback to
 * each {@code unknown} layer; the layer models; the Stage 2 part items; and the node's
 * particle-only block model. Listed once in {@link VftvClientData#FEATURES}.
 */
final class VisClientData implements FeatureClientData {
    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        blocks.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(
                        VisContent.node(),
                        BlockModelGenerators.plainVariant(
                                ModelTemplates.PARTICLE_ONLY.create(
                                        VisContent.node(),
                                        TextureMapping.particle(new Material(NodeRenderer.CORE)),
                                        blocks.modelOutput))));
        items.generateFlatItem(VisContent.chimeRod(), ModelTemplates.FLAT_ITEM);
        items.generateFlatItem(VisContent.resonantBrassCap(), ModelTemplates.FLAT_ITEM);
        Set<String> rods = new TreeSet<>();
        Set<String> caps = new TreeSet<>();
        readParts(rods, caps);
        List<SelectItemModel.SwitchCase<ResourceKey<WandRod>>> rodCases = new ArrayList<>();
        for (String id : rods) {
            rodCases.add(
                    ItemModelUtils.when(
                            ResourceKey.create(VisKeys.WAND_ROD_REGISTRY, Identifier.parse(id)),
                            layer(items, "rod", Identifier.parse(id).getPath())));
        }
        List<SelectItemModel.SwitchCase<ResourceKey<WandCap>>> capCases = new ArrayList<>();
        for (String id : caps) {
            capCases.add(
                    ItemModelUtils.when(
                            ResourceKey.create(VisKeys.WAND_CAP_REGISTRY, Identifier.parse(id)),
                            layer(items, "cap", Identifier.parse(id).getPath())));
        }
        items.itemModelOutput.accept(
                VisContent.wand(),
                ItemModelUtils.composite(
                        ItemModelUtils.select(
                                new ComponentContents<>(VisContent.rod()),
                                layer(items, "rod", "unknown"),
                                rodCases),
                        ItemModelUtils.select(
                                new ComponentContents<>(VisContent.cap()),
                                layer(items, "cap", "unknown"),
                                capCases)));
    }

    /** A layer model {@code vftv:item/wand/<kind>/<path>}, flat over its own texture. */
    private static ItemModel.Unbaked layer(ItemModelGenerators items, String kind, String path) {
        Identifier id = Identifier.fromNamespaceAndPath("vftv", "item/wand/" + kind + "/" + path);
        return ItemModelUtils.plainModel(
                ModelTemplates.FLAT_ITEM.create(
                        id, TextureMapping.layer0(new Material(id)), items.modelOutput));
    }

    /** Every part id in every {@code content/} parts file ({@code vftv:wand_parts/1}). */
    private static void readParts(Set<String> rods, Set<String> caps) {
        String root = System.getProperty(ContentProvider.CONTENT_DIR);
        if (root == null) {
            throw new IllegalStateException(
                    "the system property " + ContentProvider.CONTENT_DIR + " is not set");
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(Path.of(root))) {
            files = walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path file : files) {
            try {
                if (!(StrictJson.parse(Files.readString(file, StandardCharsets.UTF_8))
                                instanceof JsonObject json)
                        || !json.has("schema")
                        || !json.get("schema").getAsString().equals("vftv:wand_parts/1")) {
                    continue;
                }
                json.getAsJsonObject("rods").keySet().forEach(rods::add);
                json.getAsJsonObject("caps").keySet().forEach(caps::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
