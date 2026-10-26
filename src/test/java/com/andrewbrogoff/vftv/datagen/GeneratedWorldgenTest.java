package com.andrewbrogoff.vftv.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * The datagen-phase checks over {@code src/generated/resources} (worldgen spec, "Tests"): the
 * parsed-NBT no-Hush test over every generated template and worldgen, processor and loot file; each
 * template round-tripping its content; the chancels' pinned node record; the entities' feet; and
 * the find advancements' exact JSON.
 */
class GeneratedWorldgenTest {
    private static final Path DATA =
            WorldgenDataTest.project().resolve("src/generated/resources/data");
    private static final Set<String> HUSHED = TemplateConverter.hushedBlocks();
    private static final String PINNED =
            "{aspects:[{aspect:\"vftv:aer\",current:16,max:16},{aspect:\"vftv:ordo\",current:24,max:24},"
                    + "{aspect:\"vftv:sonus\",current:4,max:4},{aspect:\"vftv:vacuos\",current:4,max:4}],"
                    + "clarity:\"steady\",cultivation:{counters:{},sonus:0},format:1,id:0L,origin:\"structure\","
                    + "regen:{divisor:1,interval:1,tick:0L},type:\"vftv:choir\"}";

    private static List<Path> files(String glob) throws IOException {
        var matcher = DATA.getFileSystem().getPathMatcher("glob:" + glob);
        try (Stream<Path> walk = Files.walk(DATA)) {
            return walk.filter(p -> matcher.matches(DATA.relativize(p))).sorted().toList();
        }
    }

    static CompoundTag read(Path nbt) throws IOException {
        try (InputStream in = Files.newInputStream(nbt)) {
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }

    /** Every no-Hush problem in one tag, recursively. */
    static void noHush(Tag tag, String where, List<String> problems) {
        if (tag instanceof CompoundTag c) {
            for (String key : c.keySet()) {
                if (key.equals("neoforge:attachments")) {
                    problems.add(where + ": neoforge:attachments");
                }
                Tag child = c.get(key);
                if (child != null) {
                    noHush(child, where + "." + key, problems);
                }
            }
        } else if (tag instanceof ListTag l) {
            for (int i = 0; i < l.size(); i++) {
                noHush(l.get(i), where + "[" + i + "]", problems);
            }
        } else {
            String s = tag.asString().orElse("");
            if (HUSHED.contains(s) || s.equals("vftv:hollow") || s.equals("vftv:hush_ash")) {
                problems.add(where + ": " + s);
            }
        }
    }

    @Test
    void noHushInAnyTemplate() throws IOException {
        List<Path> templates = files("*/structure/**.nbt");
        assertFalse(templates.isEmpty());
        List<String> problems = new ArrayList<>();
        for (Path p : templates) {
            CompoundTag t = read(p);
            noHush(t, p.toString(), problems);
            for (Tag b : t.getListOrEmpty("blocks")) {
                CompoundTag node =
                        ((CompoundTag) b).getCompoundOrEmpty("nbt").getCompoundOrEmpty("node");
                if (node.getStringOr("type", "").equals("vftv:hushed")) {
                    problems.add(p + ": a vftv:hushed node");
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void noHushInAnyWorldgenOrLootFile() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String glob :
                List.of(
                        "*/worldgen/**.json",
                        "*/structure/**.json",
                        "*/loot_table/**.json",
                        "vftv/vftv/**.json")) {
            for (Path p : files(glob)) {
                JsonElement json = StrictJson.parse(Files.readString(p, StandardCharsets.UTF_8));
                scan(json, p.toString(), problems);
            }
        }
        assertEquals(List.of(), problems);
    }

    private static void scan(JsonElement e, String where, List<String> problems) {
        if (e.isJsonObject()) {
            e.getAsJsonObject()
                    .entrySet()
                    .forEach(
                            m -> {
                                if (m.getKey().equals("neoforge:attachments")) {
                                    problems.add(where + ": neoforge:attachments");
                                }
                                scan(m.getValue(), where, problems);
                            });
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(x -> scan(x, where, problems));
        } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            if (HUSHED.contains(s)
                    || s.equals("vftv:hollow")
                    || s.equals("vftv:hush_ash")
                    || s.equals("vftv:hushed")) {
                problems.add(where + ": " + s);
            }
        }
    }

    @Test
    void templatesRoundTripTheirContent() throws IOException {
        for (String name : TemplateConverter.CHOIR_RUIN.files().keySet()) {
            CompoundTag generated =
                    read(DATA.resolve("vftv/structure/choir_ruin/" + name + ".nbt"));
            TemplateConverter.Result r =
                    TemplateConverterTest.convert(name, TemplateConverterTest.template(name));
            assertEquals(r.nbt(), generated, name + ": the committed NBT is the content's");
            int nodes = 0;
            for (Tag b : generated.getListOrEmpty("blocks")) {
                CompoundTag nbt = ((CompoundTag) b).getCompoundOrEmpty("nbt");
                if (nbt.getStringOr("id", "").equals("vftv:node")) {
                    nodes++;
                    assertEquals(
                            PINNED,
                            TemplateConverterTest.sorted(nbt.getCompoundOrEmpty("node")),
                            name);
                }
            }
            assertEquals(name.startsWith("chancel") ? 1 : 0, nodes, name + ": nodes");
            for (Tag e : generated.getListOrEmpty("entities")) {
                CompoundTag entity = (CompoundTag) e;
                ListTag pos = entity.getListOrEmpty("pos");
                ListTag block = entity.getListOrEmpty("blockPos");
                assertEquals(block.getIntOr(0, -1) + 0.5, pos.getDoubleOr(0, 0), name);
                assertEquals(block.getIntOr(1, -1), pos.getDoubleOr(1, 0), name);
                assertEquals(block.getIntOr(2, -1) + 0.5, pos.getDoubleOr(2, 0), name);
                assertEquals(
                        Set.of("id"),
                        entity.getCompoundOrEmpty("nbt").keySet(),
                        name + ": only {id}");
            }
            int height = generated.getListOrEmpty("size").getIntOr(1, 0);
            if (name.startsWith("chancel")) {
                assertTrue(
                        highest(generated) - 1 >= 20,
                        name + ": the tower rises 20 above the floor");
                assertTrue(height <= 30, name);
            }
        }
    }

    private static int highest(CompoundTag template) {
        ListTag palette = template.getListOrEmpty("palette");
        int top = 0;
        for (Tag b : template.getListOrEmpty("blocks")) {
            CompoundTag block = (CompoundTag) b;
            String name =
                    palette.getCompoundOrEmpty(block.getIntOr("state", 0)).getStringOr("Name", "");
            if (!name.equals("minecraft:air")) {
                top = Math.max(top, block.getListOrEmpty("pos").getIntOr(1, 0));
            }
        }
        return top;
    }

    @Test
    void theFindAdvancements() throws IOException {
        JsonObject answerers = advancement("answerers");
        assertEquals(
                StrictJson.parse(
                        "{\"criteria\": {\"killed\": {\"trigger\": \"minecraft:player_killed_entity\","
                                + " \"conditions\": {\"entity\": [{\"condition\":"
                                + " \"minecraft:entity_properties\", \"entity\": \"this\","
                                + " \"predicate\": {\"minecraft:entity_type\":"
                                + " \"#vftv:answerers\"}}]}}}, \"requirements\": [[\"killed\"]],"
                                + " \"sends_telemetry_event\": true}"),
                answerers);
        assertEquals(
                StrictJson.parse(
                        "{\"criteria\": {\"dripstone\": {\"trigger\": \"minecraft:inventory_changed\","
                                + " \"conditions\": {\"items\": [{\"items\":"
                                + " [\"minecraft:pointed_dripstone\", \"minecraft:dripstone_block\"]}]}}},"
                                + " \"requirements\": [[\"dripstone\"]], \"sends_telemetry_event\": true}"),
                advancement("distillation"));
        assertEquals(
                StrictJson.parse(
                        "{\"criteria\": {\"page\": {\"trigger\": \"minecraft:inventory_changed\","
                                + " \"conditions\": {\"items\": [{\"items\": \"vftv:choir_page\"}]}}},"
                                + " \"requirements\": [[\"page\"]], \"sends_telemetry_event\": true}"),
                advancement("ruins"));
        Set<String> triggers = new TreeSet<>();
        for (String find : List.of("answerers", "distillation", "ruins")) {
            JsonObject a = advancement(find);
            assertFalse(a.has("parent"), find);
            assertFalse(a.has("display"), find);
            assertFalse(a.has("rewards"), find);
            a.getAsJsonObject("criteria")
                    .entrySet()
                    .forEach(
                            c ->
                                    triggers.add(
                                            c.getValue()
                                                    .getAsJsonObject()
                                                    .get("trigger")
                                                    .getAsString()));
        }
        assertEquals(
                Set.of("minecraft:inventory_changed", "minecraft:player_killed_entity"), triggers);
    }

    private static JsonObject advancement(String find) throws IOException {
        return StrictJson.parse(
                        Files.readString(
                                DATA.resolve("vftv/advancement/find/" + find + ".json"),
                                StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
