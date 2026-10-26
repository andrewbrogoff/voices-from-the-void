package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** The {@code vftv:template/1} converter's rejections, over mutations of the shipped files. */
class TemplateConverterTest {
    @org.junit.jupiter.api.BeforeAll
    static void bind() {
        com.andrewbrogoff.vftv.api.TestItems.bindComponents();
    }

    private static final JsonObject TYPES =
            WorldgenDataTest.content("node_types.json").getAsJsonObject("types");

    static TemplateConverter.Result convert(String name, JsonObject json) {
        return TemplateConverter.convert(
                name, json, TemplateConverter.CHOIR_RUIN, t -> WorldgenData.minima(TYPES, t));
    }

    static JsonObject template(String name) {
        return WorldgenDataTest.content("templates/" + name + ".json");
    }

    private static void rejects(String name, Consumer<JsonObject> mutate, String reason) {
        JsonObject json = template(name);
        mutate.accept(json);
        List<String> found = convert(name, json).problems();
        assertTrue(found.stream().anyMatch(p -> p.contains(reason)), found.toString());
    }

    private static void row(JsonObject json, int y, int z, String row) {
        json.getAsJsonArray("layers").get(y).getAsJsonArray().set(z, new JsonPrimitive(row));
    }

    private static String row(JsonObject json, int y, int z) {
        return json.getAsJsonArray("layers").get(y).getAsJsonArray().get(z).getAsString();
    }

    private static void cell(JsonObject json, int x, int y, int z, char c) {
        char[] r = row(json, y, z).toCharArray();
        r[x] = c;
        row(json, y, z, new String(r));
    }

    @Test
    void theShippedTemplatesConvert() {
        for (String name : TemplateConverter.CHOIR_RUIN.files().keySet()) {
            TemplateConverter.Result r = convert(name, template(name));
            assertEquals(List.of(), r.problems(), name);
            assertNotNull(r.nbt(), name);
        }
        assertTrue(convert("other", template("rubble_a")).problems().getFirst().contains("not a file"));
    }

    @Test
    void theFormat() {
        rejects("rubble_a", j -> row(j, 1, 2, "  #"), "columns, not 9");
        rejects("rubble_a", j -> j.getAsJsonArray("layers").get(1).getAsJsonArray().remove(8), "rows, not 9");
        rejects("rubble_a", j -> j.getAsJsonArray("layers").remove(1), "layers, not 2");
        rejects("rubble_a", j -> cell(j, 1, 1, 1, 'Q'), "'Q' is not in the palette");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "minecraft:chiseled_tuff[x=y]"), "does not parse");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("^", "minecraft:jigsaw"), "missing [orientation]");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "minecraft:not_a_block"), "does not parse");
        rejects("rubble_a", j -> j.getAsJsonArray("size").set(0, new JsonPrimitive(49)), "49");
    }

    @Test
    void blocksAndNamespaces() {
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "minecraft:structure_block[mode=save]"), "never allowed");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "minecraft:spawner"), "never allowed");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "minecraft:vault[facing=north,ominous=false,vault_state=inactive]"), "never allowed");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "minecraft:barrel[facing=up,open=false]"), "the only container allowed is a chest");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "vftv:pedestal"), "is not a vftv block");
        rejects("rubble_a", j -> j.getAsJsonObject("palette").addProperty("c", "vftv:hushed_stone"), "palette.c");
    }

    @Test
    void blockEntities() {
        rejects("rubble_a", j -> j.getAsJsonObject("block_entities").remove("4,1,0"), "needs its block entity");
        rejects("rubble_a",
                j -> j.getAsJsonObject("block_entities").add("1,1,1", JsonParserHelper.obj("{\"chest\": {\"loot_table\": \"vftv:chests/choir_ruin\"}}")),
                "a chest on minecraft:tuff_bricks");
        rejects("rubble_a",
                j -> j.getAsJsonObject("block_entities").add("9,1,1", JsonParserHelper.obj("{\"chest\": {\"loot_table\": \"vftv:chests/choir_ruin\"}}")),
                "outside the size");
        rejects("rubble_a",
                j -> j.getAsJsonObject("block_entities").getAsJsonObject("4,1,0").getAsJsonObject("jigsaw").addProperty("placement_priority", 3),
                "priorities are 0");
        rejects("rubble_a",
                j -> j.getAsJsonObject("block_entities").getAsJsonObject("4,1,0").add("node", JsonParserHelper.obj("{\"type\": \"vftv:choir\"}")),
                "exactly one of");
        rejects("chancel_a",
                j -> j.getAsJsonObject("block_entities").entrySet().stream()
                        .filter(e -> e.getValue().getAsJsonObject().has("node"))
                        .forEach(e -> e.getValue().getAsJsonObject().getAsJsonObject("node").addProperty("type", "vftv:hushed")),
                "a node of type vftv:hushed");
        rejects("chancel_a",
                j -> j.getAsJsonObject("block_entities").entrySet().stream()
                        .filter(e -> e.getValue().getAsJsonObject().has("node"))
                        .forEach(e -> e.getValue().getAsJsonObject().getAsJsonObject("node").addProperty("type", "vftv:nope")),
                "unknown node type");
        rejects("bell_pit",
                j -> j.getAsJsonObject("block_entities").entrySet().stream()
                        .filter(e -> e.getValue().getAsJsonObject().has("chest"))
                        .forEach(e -> e.getValue().getAsJsonObject().getAsJsonObject("chest").addProperty("loot_table", "minecraft:chests/simple_dungeon")),
                "the loot table must be one of");
    }

    @Test
    void entitiesAndStructure() {
        rejects("cloister",
                j -> j.getAsJsonArray("entities").get(0).getAsJsonObject().addProperty("type", "vftv:hollow"),
                "is not one of");
        rejects("cloister",
                j -> j.getAsJsonArray("entities").get(0).getAsJsonObject().add("pos", JsonParserHelper.arr("[8, 9, 8]")),
                "outside the size");
        rejects("cloister",
                j -> j.getAsJsonArray("entities").get(0).getAsJsonObject().add("pos", JsonParserHelper.arr("[0, 1, 0]")),
                "is not a standing cell");
    }

    @Test
    void theNodeRecordIsComplete() {
        List<NodeState.NodeAspect> minima = WorldgenData.minima(TYPES, "vftv:choir");
        assertNotNull(minima);
        assertEquals(
                Arrays.asList(16, 24, 4, 4),
                minima.stream().map(NodeState.NodeAspect::max).toList());
        assertEquals(
                "{aspects:[{aspect:\"vftv:aer\",current:16,max:16},{aspect:\"vftv:ordo\",current:24,max:24},"
                        + "{aspect:\"vftv:sonus\",current:4,max:4},{aspect:\"vftv:vacuos\",current:4,max:4}],"
                        + "clarity:\"steady\",cultivation:{counters:{},sonus:0},format:1,id:0L,origin:\"structure\","
                        + "regen:{divisor:1,interval:1,tick:0L},type:\"vftv:choir\"}",
                sorted(TemplateConverter.nodeRecord(net.minecraft.resources.Identifier.parse("vftv:choir"), minima)));
    }

    /** SNBT with every compound's keys sorted, so the pinned record compares field for field. */
    static String sorted(net.minecraft.nbt.Tag tag) {
        if (tag instanceof net.minecraft.nbt.CompoundTag c) {
            StringBuilder b = new StringBuilder("{");
            c.keySet().stream().sorted().forEach(k -> {
                if (b.length() > 1) {
                    b.append(',');
                }
                b.append(k).append(':').append(sorted(java.util.Objects.requireNonNull(c.get(k))));
            });
            return b.append('}').toString();
        }
        if (tag instanceof net.minecraft.nbt.ListTag l) {
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    b.append(',');
                }
                b.append(sorted(l.get(i)));
            }
            return b.append(']').toString();
        }
        return tag.toString();
    }

    /** Tiny JSON helpers for the mutations. */
    static final class JsonParserHelper {
        private JsonParserHelper() {}

        static JsonObject obj(String s) {
            return com.google.gson.JsonParser.parseString(s).getAsJsonObject();
        }

        static JsonArray arr(String s) {
            return com.google.gson.JsonParser.parseString(s).getAsJsonArray();
        }
    }
}
