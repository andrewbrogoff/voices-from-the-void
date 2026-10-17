package com.andrewbrogoff.vftv.datagen.client;

import com.andrewbrogoff.vftv.core.essentia.EssentiaContent;
import com.andrewbrogoff.vftv.core.essentia.client.EssentiaTint;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * The essentia feature's client assets (essentia spec, "Production tickets, Essentia assets"):
 * block models of at most six pixel-aligned cuboids with UVs derived from position, the jar's,
 * tube's, valve's and Alembic's block states, and the item definitions. Listed once in {@link
 * VftvClientData#FEATURES}.
 */
final class EssentiaClientData implements FeatureClientData {
    private static final String GLASS_JAR =
            """
            {"from": [3, 0, 3], "to": [13, 9, 13], "faces": {
              "north": {"texture": "#glass"}, "south": {"texture": "#glass"}, "west": {"texture": "#glass"},
              "east": {"texture": "#glass"}, "up": {"texture": "#lid"}, "down": {"texture": "#lid"}}},
            {"from": [4, 9, 4], "to": [12, 10, 12], "faces": {
              "north": {"texture": "#glass"}, "south": {"texture": "#glass"}, "west": {"texture": "#glass"},
              "east": {"texture": "#glass"}, "up": {"texture": "#lid"}}},
            {"from": [3, 10, 3], "to": [13, 12, 13], "faces": {
              "north": {"texture": "#glass"}, "south": {"texture": "#glass"}, "west": {"texture": "#glass"},
              "east": {"texture": "#glass"}, "up": {"texture": "#lid"}, "down": {"texture": "#lid"}}}
            """;

    private static final String JAR_TEXTURES =
            """
            "textures": {"glass": "vftv:block/essentia_jar_glass", "lid": "vftv:block/essentia_jar_lid",
              "liquid": "vftv:block/essentia_jar_liquid", "particle": "vftv:block/essentia_jar_glass"}
            """;

    @Override
    public void models(BlockModelGenerators blocks, ItemModelGenerators items) {
        model(
                blocks,
                "block/essentia_jar",
                "{\"parent\": \"minecraft:block/block\", \"render_type\": \"minecraft:translucent\", "
                        + JAR_TEXTURES
                        + ", \"elements\": ["
                        + GLASS_JAR
                        + "]}");
        model(
                blocks,
                "item/essentia_jar_filled",
                "{\"parent\": \"minecraft:block/block\", \"render_type\": \"minecraft:translucent\", "
                        + JAR_TEXTURES
                        + ", \"elements\": [{\"from\": [4, 1, 4], \"to\": [12, 6, 12], \"faces\": {"
                        + allFaces("#liquid", ", \"tintindex\": 0")
                        + "}}, "
                        + GLASS_JAR
                        + "]}");
        model(blocks, "block/essentia_tube_core", box("core", 5, 5, 5, 11, 11, 11, "#core", null));
        model(blocks, "block/essentia_tube_arm", box("arm", 5, 5, 0, 11, 11, 11, "#arm", null));
        model(blocks, "block/essentia_tube_cap", box("cap", 5, 5, 0, 11, 11, 11, "#arm", "#cap"));
        model(
                blocks,
                "item/essentia_tube",
                "{\"parent\": \"minecraft:block/block\", \"textures\": {\"arm\": \"vftv:block/essentia_tube_arm\","
                        + " \"particle\": \"vftv:block/essentia_tube_arm\"}, \"elements\": [{\"from\": [5, 5, 0],"
                        + " \"to\": [11, 11, 16], \"faces\": {"
                        + allFaces("#arm", "")
                        + "}}]}");
        for (boolean open : new boolean[] {true, false}) {
            String wheel = "vftv:block/tube_valve_wheel_" + (open ? "open" : "closed");
            model(
                    blocks,
                    "block/tube_valve_" + (open ? "open" : "closed"),
                    "{\"parent\": \"minecraft:block/block\", \"textures\": {\"body\": \"vftv:block/tube_valve\","
                            + " \"arm\": \"vftv:block/essentia_tube_arm\", \"wheel\": \""
                            + wheel
                            + "\", \"particle\": \"vftv:block/tube_valve\"}, \"elements\": ["
                            + element(4, 4, 4, 12, 12, 12, "#body", "")
                            + ", "
                            + element(5, 5, 0, 11, 11, 4, "#arm", "")
                            + ", "
                            + element(5, 5, 12, 11, 11, 16, "#arm", "")
                            + ", "
                            + element(3, 12, 3, 13, 13, 13, "#wheel", "")
                            + "]}");
        }
        model(
                blocks,
                "block/alembic",
                "{\"parent\": \"minecraft:block/block\", \"render_type\": \"minecraft:translucent\","
                        + " \"textures\": {\"glass\": \"vftv:block/alembic_glass\", \"brass\": \"vftv:block/alembic_brass\","
                        + " \"particle\": \"vftv:block/alembic_brass\"}, \"elements\": ["
                        + element(4, 2, 4, 12, 10, 12, "#glass", "")
                        + ", "
                        + element(5, 10, 5, 11, 13, 11, "#brass", "")
                        + ", "
                        + element(7, 11, 0, 9, 13, 5, "#brass", "")
                        + ", "
                        + element(7, 0, 7, 9, 2, 9, "#brass", "")
                        + "]}");

        blocks.blockStateOutput.accept(
                definition(EssentiaContent.jar(), facing("vftv:block/essentia_jar")));
        blocks.blockStateOutput.accept(
                definition(EssentiaContent.alembic(), facing("vftv:block/alembic")));
        blocks.blockStateOutput.accept(definition(EssentiaContent.valve(), valve()));
        blocks.blockStateOutput.accept(definition(EssentiaContent.tube(), tube()));

        items.itemModelOutput.accept(
                EssentiaContent.jarItem(),
                ItemModelUtils.conditional(
                        ItemModelUtils.hasComponent(EssentiaContent.contents()),
                        ItemModelUtils.tintedModel(
                                id("item/essentia_jar_filled"), new EssentiaTint()),
                        ItemModelUtils.plainModel(id("block/essentia_jar"))));
        items.itemModelOutput.accept(
                EssentiaContent.label(),
                ItemModelUtils.conditional(
                        ItemModelUtils.hasComponent(EssentiaContent.labelComponent()),
                        ItemModelUtils.plainModel(
                                items.createFlatItemModel(
                                        EssentiaContent.label(),
                                        "_written",
                                        net.minecraft.client.data.models.model.ModelTemplates
                                                .FLAT_ITEM)),
                        ItemModelUtils.plainModel(
                                items.createFlatItemModel(
                                        EssentiaContent.label(),
                                        net.minecraft.client.data.models.model.ModelTemplates
                                                .FLAT_ITEM))));
        items.itemModelOutput.accept(
                EssentiaContent.tubeItem(), ItemModelUtils.plainModel(id("item/essentia_tube")));
        items.itemModelOutput.accept(
                EssentiaContent.valveItem(),
                ItemModelUtils.plainModel(id("block/tube_valve_open")));
        items.itemModelOutput.accept(
                EssentiaContent.alembicItem(), ItemModelUtils.plainModel(id("block/alembic")));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    private static void model(BlockModelGenerators blocks, String path, String json) {
        JsonElement parsed = JsonParser.parseString(json);
        blocks.modelOutput.accept(id(path), () -> parsed);
    }

    private static String allFaces(String texture, String extra) {
        List<String> faces = new ArrayList<>();
        for (Direction d : Direction.values()) {
            faces.add(
                    "\""
                            + d.getSerializedName()
                            + "\": {\"texture\": \""
                            + texture
                            + "\""
                            + extra
                            + "}");
        }
        return String.join(", ", faces);
    }

    private static String element(
            int x0, int y0, int z0, int x1, int y1, int z1, String texture, String extra) {
        return String.format(
                Locale.ROOT,
                "{\"from\": [%d, %d, %d], \"to\": [%d, %d, %d], \"faces\": {%s}}",
                x0,
                y0,
                z0,
                x1,
                y1,
                z1,
                allFaces(texture, extra));
    }

    /** A one-cuboid tube part; a cap's outer (north) face takes the cap texture. */
    private static String box(
            String name,
            int x0,
            int y0,
            int z0,
            int x1,
            int y1,
            int z1,
            String texture,
            @Nullable String northTexture) {
        String faces = allFaces(texture, "");
        if (northTexture != null) {
            faces =
                    faces.replace(
                            "\"north\": {\"texture\": \"" + texture + "\"}",
                            "\"north\": {\"texture\": \"" + northTexture + "\"}");
        }
        return "{\"parent\": \"minecraft:block/block\", \"textures\": {\"core\": \"vftv:block/essentia_tube_core\","
                + " \"arm\": \"vftv:block/essentia_tube_arm\", \"cap\": \"vftv:block/essentia_tube_cap\","
                + " \"particle\": \"vftv:block/essentia_tube_"
                + name
                + "\"}, \"elements\": [{\"from\": ["
                + x0
                + ", "
                + y0
                + ", "
                + z0
                + "], \"to\": ["
                + x1
                + ", "
                + y1
                + ", "
                + z1
                + "], \"faces\": {"
                + faces
                + "}}]}";
    }

    /** Four {@code facing} variants rotated by {@code y}, the model facing north. */
    private static String facing(String model) {
        List<String> variants = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int y = (int) d.toYRot() + 180;
            variants.add(
                    "\"facing="
                            + d.getSerializedName()
                            + "\": {\"model\": \""
                            + model
                            + "\", \"y\": "
                            + (y % 360)
                            + "}");
        }
        return "{\"variants\": {" + String.join(", ", variants) + "}}";
    }

    /** Twelve variants: the open model iff open and not powered, rotated per axis. */
    private static String valve() {
        List<String> variants = new ArrayList<>();
        for (String axis : List.of("x", "y", "z")) {
            for (boolean open : new boolean[] {true, false}) {
                for (boolean powered : new boolean[] {true, false}) {
                    String model =
                            "vftv:block/tube_valve_" + (open && !powered ? "open" : "closed");
                    String rot =
                            switch (axis) {
                                case "x" -> ", \"y\": 90";
                                case "y" -> ", \"x\": 90";
                                default -> "";
                            };
                    variants.add(
                            "\"axis="
                                    + axis
                                    + ",open="
                                    + open
                                    + ",powered="
                                    + powered
                                    + "\": {\"model\": \""
                                    + model
                                    + "\""
                                    + rot
                                    + "}");
                }
            }
        }
        return "{\"variants\": {" + String.join(", ", variants) + "}}";
    }

    /** Multipart: the core only when every face is none; per face an arm or a cap, rotated. */
    private static String tube() {
        List<String> parts = new ArrayList<>();
        List<String> none = new ArrayList<>();
        for (Direction d : Direction.values()) {
            none.add("\"" + d.getSerializedName() + "\": \"none\"");
        }
        parts.add(
                "{\"when\": {"
                        + String.join(", ", none)
                        + "}, \"apply\": {\"model\": \"vftv:block/essentia_tube_core\"}}");
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
            parts.add(
                    "{\"when\": {\""
                            + d.getSerializedName()
                            + "\": \"connected\"}, \"apply\": {\"model\": \"vftv:block/essentia_tube_arm\""
                            + rot
                            + "}}");
            parts.add(
                    "{\"when\": {\""
                            + d.getSerializedName()
                            + "\": \"blocked\"}, \"apply\": {\"model\": \"vftv:block/essentia_tube_cap\""
                            + rot
                            + "}}");
        }
        return "{\"multipart\": [" + String.join(", ", parts) + "]}";
    }

    private static BlockModelDefinitionGenerator definition(Block block, String json) {
        BlockStateModelDispatcher dispatcher =
                BlockStateModelDispatcher.CODEC
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                        .getOrThrow();
        return new BlockModelDefinitionGenerator() {
            @Override
            public Block block() {
                return block;
            }

            @Override
            public BlockStateModelDispatcher create() {
                return dispatcher;
            }
        };
    }
}
