package com.andrewbrogoff.vftv.core.warp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The Voices' authored content (warp spec, "Authored content"): the shipped files pass every rule,
 * and each "Rejected:" rule refuses its defect.
 */
class VoiceContentTest {
    private static final Path ROOT = Path.of(System.getProperty("vftv.projectDir", "."), "content");

    private static JsonObject read(String path) {
        try {
            return StrictJson.parse(Files.readString(ROOT.resolve(path), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> voices() {
        Map<String, String> out = new TreeMap<>();
        read("lang/en_us/voices.json")
                .getAsJsonObject("strings")
                .asMap()
                .forEach((k, v) -> out.put(k, v.getAsString()));
        return out;
    }

    private static List<String> check(Schema schema, JsonObject json) {
        return Schema.check(json, Map.of(schema.id(), schema));
    }

    /** {@code json} after {@code edit}, checked against {@code schema}: some problem expected. */
    private static void rejects(Schema schema, String file, Consumer<JsonObject> edit) {
        JsonObject json = read(file);
        edit.accept(json);
        assertFalse(check(schema, json).isEmpty(), json.toString());
    }

    @Test
    void theShippedContentPasses() {
        assertEquals(List.of(), check(VoiceContent.EVENTS, read("warp/events.json")));
        assertEquals(List.of(), check(VoiceContent.BARGAINS, read("warp/bargains.json")));
        assertEquals(List.of(), check(VoiceContent.PHANTOMS, read("warp/phantoms.json")));
        assertEquals(List.of(), check(VoiceContent.RECIPES, read("warp/recipes.json")));
        assertEquals(List.of(), check(VoiceContent.BOOTSTRAP, read("warp/bootstrap.json")));
        assertEquals(List.of(), check(VoiceContent.FIXTURES, read("warp/fixtures.json")));
        assertEquals(List.of(), check(VoiceContent.LEXICON, read("lexicon/void_tongue.json")));
        JsonObject script = read("warp/voice_lines.json");
        assertEquals(List.of(), check(VoiceContent.VOICE_LINES, script));
        assertEquals(
                List.of(),
                VoiceContent.scriptRules(script, read("lexicon/void_tongue.json"), voices()));
    }

    @Test
    void eventRulesRefuse() {
        String f = "warp/events.json";
        rejects(VoiceContent.EVENTS, f, j -> ev(j, "vftv:whisper").addProperty("colour", 1));
        rejects(VoiceContent.EVENTS, f, j -> ev(j, "vftv:whisper").addProperty("min_band", 3));
        rejects(VoiceContent.EVENTS, f, j -> ev(j, "vftv:drained").addProperty("max_band", 0));
        rejects(VoiceContent.EVENTS, f, j -> ev(j, "vftv:pressing_dark").remove("context"));
        rejects(
                VoiceContent.EVENTS,
                f,
                j -> ev(j, "vftv:watcher").addProperty("type", "vftv:spawn"));
        rejects(
                VoiceContent.EVENTS,
                f,
                j -> ev(j, "vftv:watcher").addProperty("type", "vftv:phantom_block"));
        rejects(VoiceContent.EVENTS, f, j -> ev(j, "vftv:whisper").addProperty("weight", 41));
        rejects(VoiceContent.EVENTS, f, j -> ev(j, "vftv:whisper").addProperty("points", 3));
        rejects(VoiceContent.EVENTS, f, j -> ev(j, "vftv:insight").remove("points"));
        rejects(VoiceContent.EVENTS, f, j -> j.getAsJsonObject("events").remove("vftv:offer"));
    }

    @Test
    void bargainRulesRefuse() {
        String f = "warp/bargains.json";
        rejects(VoiceContent.BARGAINS, f, j -> cost(j).addProperty("permanent", 1));
        rejects(
                VoiceContent.BARGAINS,
                f,
                j ->
                        j.getAsJsonObject("bargains")
                                .getAsJsonObject("vftv:keen_eyes")
                                .add("cost", new JsonObject()));
        rejects(
                VoiceContent.BARGAINS,
                f,
                j ->
                        j.getAsJsonObject("bargains")
                                .getAsJsonObject("vftv:keen_eyes")
                                .addProperty("type", "vftv:points"));
        rejects(
                VoiceContent.BARGAINS,
                f,
                j ->
                        j.getAsJsonObject("bargains")
                                .getAsJsonObject("vftv:keen_eyes")
                                .addProperty("points", 4));
        rejects(
                VoiceContent.BARGAINS,
                f,
                j ->
                        j.getAsJsonObject("bargains")
                                .getAsJsonObject("vftv:keen_eyes")
                                .addProperty("amplifier", 3));
        rejects(VoiceContent.BARGAINS, f, j -> cost(j).addProperty("temporary", 4));
    }

    @Test
    void entriesParseAndRefuse() {
        JsonObject events = StrictJson.parse(VoiceContent.SHIPPED_EVENTS).getAsJsonObject();
        events.asMap().forEach((id, e) -> VoiceContent.event(e.getAsJsonObject()));
        StrictJson.parse(VoiceContent.SHIPPED_BARGAINS)
                .getAsJsonObject()
                .asMap()
                .forEach((id, b) -> VoiceContent.bargain(b.getAsJsonObject()));
        VoiceContent.Event status =
                VoiceContent.event(events.getAsJsonObject("vftv:pressing_dark"));
        assertEquals(
                List.of(new VoiceContent.Effect("minecraft:slowness", 0, 100)), status.effects());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VoiceContent.event(
                                StrictJson.parse("{\"type\": \"vftv:spawn\", \"weight\": 1}")
                                        .getAsJsonObject()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VoiceContent.event(
                                StrictJson.parse(
                                                "{\"schema\": \"x\", \"type\": \"vftv:whisper\", \"weight\": 1}")
                                        .getAsJsonObject()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        VoiceContent.bargain(
                                StrictJson.parse(
                                                "{\"type\": \"vftv:effect\", \"weight\": 1, \"cost\": {},"
                                                        + " \"effect\": \"minecraft:haste\", \"amplifier\": 0,"
                                                        + " \"duration\": 200}")
                                        .getAsJsonObject()));
    }

    @Test
    void lexiconRulesRefuse() {
        String f = "lexicon/void_tongue.json";
        rejects(VoiceContent.LEXICON, f, j -> word(j, "kadis", 1));
        rejects(VoiceContent.LEXICON, f, j -> word(j, "uushen", 2));
        rejects(
                VoiceContent.LEXICON,
                f,
                j -> j.getAsJsonObject("names").addProperty("focus_effect.vftv.unmaking", "Kadis"));
        rejects(
                VoiceContent.LEXICON,
                f,
                j ->
                        j.getAsJsonObject("names")
                                .addProperty("focus_effect.vftv.undertow", "lirvuuthen"));
        rejects(
                VoiceContent.LEXICON,
                f,
                j ->
                        segments(j.getAsJsonObject("ending_lines"), "vftv:joining_0")
                                .get(0)
                                .getAsJsonObject()
                                .addProperty("text", "Vuuthel."));
        rejects(
                VoiceContent.LEXICON,
                f,
                j ->
                        j.getAsJsonObject("ending_lines")
                                .getAsJsonObject("vftv:joining_0")
                                .addProperty("pool", "silencing"));
        rejects(
                VoiceContent.LEXICON,
                f,
                j ->
                        j.getAsJsonObject("ending_lines")
                                .getAsJsonObject("vftv:joining_0")
                                .remove("gloss"));
    }

    @Test
    void scriptRulesRefuse() {
        JsonObject lexicon = read("lexicon/void_tongue.json");
        Map<String, String> voices = voices();
        List<Consumer<JsonObject>> defects =
                List.of(
                        j -> line(j, "vftv:band0_0").addProperty("pool", "band1"),
                        j ->
                                segments(j.getAsJsonObject("lines"), "vftv:band0_0")
                                        .get(0)
                                        .getAsJsonObject()
                                        .addProperty("lang", "void"),
                        j -> segments(j.getAsJsonObject("lines"), "vftv:band2_7").remove(1),
                        j ->
                                segments(j.getAsJsonObject("lines"), "vftv:band3_0")
                                        .get(0)
                                        .getAsJsonObject()
                                        .addProperty("lang", "en"),
                        j ->
                                segments(j.getAsJsonObject("lines"), "vftv:band2_7")
                                        .get(1)
                                        .getAsJsonObject()
                                        .addProperty("lang", "en"),
                        j ->
                                segments(j.getAsJsonObject("lines"), "vftv:band3_1")
                                        .get(0)
                                        .getAsJsonObject()
                                        .addProperty("text", "vuuthel kadis."),
                        j ->
                                segments(j.getAsJsonObject("lines"), "vftv:band0_1")
                                        .get(0)
                                        .getAsJsonObject()
                                        .addProperty("text", "x".repeat(81)),
                        j -> line(j, "vftv:band3_0").remove("gloss"),
                        j ->
                                segments(j.getAsJsonObject("lines"), "vftv:band1_0")
                                        .get(0)
                                        .getAsJsonObject()
                                        .addProperty("text", "We hear you."),
                        j ->
                                segments(j.getAsJsonObject("lines"), "vftv:joining_1")
                                        .get(0)
                                        .getAsJsonObject()
                                        .addProperty("text", "ilheh wohlis."),
                        j -> j.getAsJsonObject("lines").remove("vftv:offer_3"));
        for (Consumer<JsonObject> defect : defects) {
            JsonObject script = read("warp/voice_lines.json");
            defect.accept(script);
            List<String> problems =
                    new java.util.ArrayList<>(check(VoiceContent.VOICE_LINES, script));
            if (problems.isEmpty()) {
                problems.addAll(VoiceContent.scriptRules(script, lexicon, voices));
            }
            assertFalse(problems.isEmpty(), script.toString());
        }
    }

    @Test
    void recipeAndBootstrapRulesRefuse() {
        rejects(
                VoiceContent.RECIPES,
                "warp/recipes.json",
                j ->
                        j.getAsJsonObject("recipes")
                                .getAsJsonObject(VoiceContent.STILLING)
                                .addProperty("catalyst", "minecraft:bucket"));
        rejects(
                VoiceContent.BOOTSTRAP,
                "warp/bootstrap.json",
                j -> j.getAsJsonArray("aspects").remove(0));
        rejects(
                VoiceContent.BOOTSTRAP,
                "warp/bootstrap.json",
                j ->
                        j.getAsJsonArray("items")
                                .get(0)
                                .getAsJsonObject()
                                .addProperty("item", "minecraft:stick"));
        rejects(
                VoiceContent.BOOTSTRAP,
                "warp/bootstrap.json",
                j ->
                        j.getAsJsonArray("aspects")
                                .get(0)
                                .getAsJsonObject()
                                .add("sources", StrictJson.parse("[\"minecraft:white_wool\"]")));
        rejects(VoiceContent.FIXTURES, "warp/fixtures.json", j -> j.remove("magnitude"));
        rejects(
                VoiceContent.PHANTOMS,
                "warp/phantoms.json",
                j -> j.add("disguises", new JsonArray()));
    }

    @Test
    void theLangKeyOfALine() {
        assertEquals("voice.vftv.band0_0", VoiceContent.langKey("vftv:band0_0"));
        assertTrue(VoiceContent.POOLS.values().stream().mapToInt(Integer::intValue).sum() == 46);
    }

    private static JsonObject ev(JsonObject root, String id) {
        return root.getAsJsonObject("events").getAsJsonObject(id);
    }

    private static JsonObject cost(JsonObject root) {
        return root.getAsJsonObject("bargains")
                .getAsJsonObject("vftv:keen_eyes")
                .getAsJsonObject("cost");
    }

    private static JsonObject line(JsonObject root, String id) {
        return root.getAsJsonObject("lines").getAsJsonObject(id);
    }

    private static JsonArray segments(JsonObject lines, String id) {
        return lines.getAsJsonObject(id).getAsJsonArray("segments");
    }

    private static void word(JsonObject root, String word, int stress) {
        JsonObject words = root.getAsJsonObject("words");
        JsonObject entry =
                words.has(word) ? words.getAsJsonObject(word).deepCopy() : new JsonObject();
        entry.addProperty("gloss", "a test word");
        entry.addProperty("stress", stress);
        entry.add("uses", StrictJson.parse("[\"voice\"]"));
        words.add(word, entry);
    }
}
