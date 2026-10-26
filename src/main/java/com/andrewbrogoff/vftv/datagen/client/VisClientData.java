package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.WandCap;
import com.andrewbrogoff.vftv.api.vis.WandRod;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.vis.VisContent;
import com.andrewbrogoff.vftv.core.vis.client.NodeRenderer;
import com.andrewbrogoff.vftv.datagen.ContentProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.SelectItemModel;
import net.minecraft.client.renderer.item.properties.select.ComponentContents;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

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
        relayMembers(blocks, items);
        items.itemModelOutput.accept(
                VisContent.bellJar(),
                ItemModelUtils.conditional(
                        ItemModelUtils.hasComponent(VisContent.captured()),
                        ItemModelUtils.plainModel(
                                items.createFlatItemModel(
                                        VisContent.bellJar(), "_filled", ModelTemplates.FLAT_ITEM)),
                        ItemModelUtils.plainModel(
                                items.createFlatItemModel(
                                        VisContent.bellJar(), ModelTemplates.FLAT_ITEM))));
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

    /**
     * The relay members' block models (wands spec, "Production tickets": at most six cuboids each,
     * UVs from position), block states and item forms: the Node Tap's ear-trumpet facing its node
     * (modelled facing north, six facings by rotation), the Vis Relay's fork on a stand and the
     * Wand Cradle's two rests.
     */
    private static void relayMembers(BlockModelGenerators blocks, ItemModelGenerators items) {
        model(
                blocks,
                "block/node_tap",
                "{\"parent\": \"minecraft:block/block\", \"textures\": {\"brass\": \"vftv:block/node_tap\","
                        + " \"mouth\": \"vftv:block/node_tap_mouth\", \"particle\": \"vftv:block/node_tap\"},"
                        + " \"elements\": ["
                        + String.join(
                                ", ",
                                element(3, 3, 0, 13, 13, 1, "#brass", "#mouth"),
                                element(4, 4, 1, 12, 12, 4, "#brass", null),
                                element(5, 5, 4, 11, 11, 7, "#brass", null),
                                element(6, 6, 7, 10, 10, 13, "#brass", null),
                                element(5, 5, 13, 11, 11, 16, "#brass", null))
                        + "]}");
        model(
                blocks,
                "block/vis_relay",
                "{\"parent\": \"minecraft:block/block\", \"textures\": {\"stand\": \"vftv:block/vis_relay_stand\","
                        + " \"fork\": \"vftv:block/vis_relay_fork\", \"particle\": \"vftv:block/vis_relay_fork\"},"
                        + " \"elements\": ["
                        + String.join(
                                ", ",
                                element(3, 0, 3, 13, 2, 13, "#stand", null),
                                element(7, 2, 7, 9, 8, 9, "#fork", null),
                                element(4, 8, 7, 12, 10, 9, "#fork", null),
                                element(4, 10, 7, 6, 15, 9, "#fork", null),
                                element(10, 10, 7, 12, 15, 9, "#fork", null))
                        + "]}");
        model(
                blocks,
                "block/wand_cradle",
                "{\"parent\": \"minecraft:block/block\", \"textures\": {\"base\": \"vftv:block/wand_cradle_base\","
                        + " \"rest\": \"vftv:block/wand_cradle_rest\", \"particle\": \"vftv:block/wand_cradle_base\"},"
                        + " \"elements\": ["
                        + String.join(
                                ", ",
                                element(2, 0, 2, 14, 3, 14, "#base", null),
                                element(2, 3, 6, 4, 7, 10, "#rest", null),
                                element(12, 3, 6, 14, 7, 10, "#rest", null))
                        + "]}");
        blocks.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(
                        VisContent.visRelay(),
                        BlockModelGenerators.plainVariant(id("block/vis_relay"))));
        blocks.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(
                        VisContent.wandCradle(),
                        BlockModelGenerators.plainVariant(id("block/wand_cradle"))));
        List<String> variants = new ArrayList<>();
        for (Direction d : Direction.values()) {
            String rot =
                    switch (d) {
                        case NORTH -> "";
                        case SOUTH -> ", \"y\": 180";
                        case WEST -> ", \"y\": 270";
                        case EAST -> ", \"y\": 90";
                        case UP -> ", \"x\": 270";
                        case DOWN -> ", \"x\": 90";
                    };
            variants.add(
                    "\"facing="
                            + d.getSerializedName()
                            + "\": {\"model\": \"vftv:block/node_tap\""
                            + rot
                            + "}");
        }
        BlockStateModelDispatcher tap =
                BlockStateModelDispatcher.CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                JsonParser.parseString(
                                        "{\"variants\": {" + String.join(", ", variants) + "}}"))
                        .getOrThrow();
        blocks.blockStateOutput.accept(
                new BlockModelDefinitionGenerator() {
                    @Override
                    public Block block() {
                        return VisContent.nodeTap();
                    }

                    @Override
                    public BlockStateModelDispatcher create() {
                        return tap;
                    }
                });
        for (Block block :
                List.of(VisContent.nodeTap(), VisContent.visRelay(), VisContent.wandCradle())) {
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            items.itemModelOutput.accept(
                    block.asItem(), ItemModelUtils.plainModel(id("block/" + path)));
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    private static void model(BlockModelGenerators blocks, String path, String json) {
        JsonElement parsed = JsonParser.parseString(json);
        blocks.modelOutput.accept(id(path), () -> parsed);
    }

    /** One cuboid, every face on {@code texture}, its north face on {@code north} if given. */
    private static String element(
            int x0,
            int y0,
            int z0,
            int x1,
            int y1,
            int z1,
            String texture,
            @Nullable String north) {
        List<String> faces = new ArrayList<>();
        for (Direction d : Direction.values()) {
            String t = d == Direction.NORTH && north != null ? north : texture;
            faces.add("\"" + d.getSerializedName() + "\": {\"texture\": \"" + t + "\"}");
        }
        return String.format(
                Locale.ROOT,
                "{\"from\": [%d, %d, %d], \"to\": [%d, %d, %d], \"faces\": {%s}}",
                x0,
                y0,
                z0,
                x1,
                y1,
                z1,
                String.join(", ", faces));
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
