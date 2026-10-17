package com.andrewbrogoff.vftv.core.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.research.model.CategoryEntry;
import com.andrewbrogoff.vftv.core.research.model.EntryKind;
import com.andrewbrogoff.vftv.core.research.model.Page;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.research.model.Trigger;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchCodecsTest {
    /** The research spec's secret-reveal example, with every optional field the codec reads. */
    private static final String SILENCE =
            """
            {"stage": 3, "category": "vftv:ruins", "position": [3, -2],
             "icon": {"sprite": "vftv:aspect/vacuos"}, "kind": "listen",
             "parents": ["vftv:ruins", "vftv:being_heard"], "hidden_parents": ["vftv:x"],
             "secret": true,
             "reveal": [{"type": "vftv:choir_page", "page": "vftv:choir/beneath_the_notes"}],
             "hinted_by": "vftv:choir/beneath_the_notes", "warp": {"lasting": 4},
             "rewards": {"points": {"vftv:silentium": 4}},
             "pages": [{"type": "vftv:text"}, {"type": "vftv:text"}, {"type": "vftv:action"}]}
            """;

    private static <T> DataResult<T> parse(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static void rejects(String json, String why) {
        DataResult<ResearchEntry> result = parse(ResearchCodecs.ENTRY, json);
        assertTrue(result.isError(), "accepted: " + json);
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains(why), () -> "expected \"" + why + "\" in " + message);
    }

    private static String entry(String fields) {
        return "{\"stage\": 1, \"category\": \"vftv:resonance\", \"position\": [0, 0],"
                + " \"icon\": {\"item\": \"vftv:commonplace_book\"}, \"kind\": \"free\","
                + " \"pages\": [{\"type\": \"vftv:text\"}]"
                + fields
                + "}";
    }

    @Test
    void theSpecExampleDecodesAndRoundTrips() {
        ResearchEntry e = parse(ResearchCodecs.ENTRY, SILENCE).getOrThrow();
        assertEquals(3, e.stage());
        assertEquals(3, e.x());
        assertEquals(-2, e.y());
        assertEquals(new ResearchEntry.Icon(true, "vftv:aspect/vacuos"), e.icon());
        assertEquals(EntryKind.LISTEN, e.kind());
        assertEquals(List.of("vftv:x"), e.hiddenParents());
        assertEquals(new ResearchEntry.Warp(0, 4, 0), e.warp());
        assertEquals(java.util.Map.of("vftv:silentium", 4), e.rewards());
        assertEquals(List.of(new Trigger.ChoirPage("vftv:choir/beneath_the_notes")), e.reveal());
        assertEquals("vftv:choir/beneath_the_notes", e.hintedBy());
        JsonElement encoded = ResearchCodecs.ENTRY.encodeStart(JsonOps.INSTANCE, e).getOrThrow();
        assertEquals(e, ResearchCodecs.ENTRY.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(e, ResearchCodecs.RESEARCH.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void defaultsForMissingFields() {
        ResearchEntry e = parse(ResearchCodecs.ENTRY, entry("")).getOrThrow();
        assertEquals(List.of(), e.parents());
        assertEquals(List.of(), e.anchors());
        assertEquals(ResearchEntry.Warp.NONE, e.warp());
        assertNull(e.hintedBy());
        assertEquals(0, e.opensStage());
        assertEquals(false, e.secret());
        // Defaults are omitted on encode.
        JsonElement encoded = ResearchCodecs.ENTRY.encodeStart(JsonOps.INSTANCE, e).getOrThrow();
        assertEquals(JsonParser.parseString(entry("")), encoded);
    }

    @Test
    void everyTriggerAndPageType() {
        ResearchEntry e =
                parse(
                                ResearchCodecs.ENTRY,
                                entry(
                                                """
                                        , "triggers": [
                                          {"type": "vftv:advancement", "advancement": "vftv:find/resonance"},
                                          {"type": "vftv:scanned", "key": "item:minecraft:amethyst_shard"},
                                          {"type": "vftv:scanned", "tag": "item:#c:gems"},
                                          {"type": "vftv:scanned", "node": true},
                                          {"type": "vftv:discovered", "aspect": "vftv:aer"},
                                          {"type": "vftv:choir_page", "page": "vftv:choir/x"},
                                          {"type": "vftv:warp_at_least", "kind": "total", "value": 7}],
                                          "opens_stage": 1
                                        """)
                                        .replace(
                                                "\"pages\": [{\"type\": \"vftv:text\"}]",
                                                "\"pages\": [{\"type\": \"vftv:text\"},"
                                                        + " {\"type\": \"vftv:recipe\", \"recipe\": \"vftv:crucible\", \"preview\": true},"
                                                        + " {\"type\": \"vftv:aspects\", \"of\": \"item:minecraft:torch\"},"
                                                        + " {\"type\": \"vftv:structure\", \"multiblock\": \"vftv:infusion_altar\"},"
                                                        + " {\"type\": \"vftv:image\", \"sprite\": \"vftv:research/ruins\"},"
                                                        + " {\"type\": \"vftv:action\"}, {\"type\": \"vftv:choir\"}]"))
                        .getOrThrow();
        assertEquals(
                List.of(
                        new Trigger.Advancement("vftv:find/resonance"),
                        new Trigger.Scanned("item:minecraft:amethyst_shard", null, false),
                        new Trigger.Scanned(null, "item:#c:gems", false),
                        new Trigger.Scanned(null, null, true),
                        new Trigger.Discovered("vftv:aer"),
                        new Trigger.ChoirPage("vftv:choir/x"),
                        new Trigger.WarpAtLeast(Trigger.WarpKind.TOTAL, 7)),
                e.triggers());
        assertEquals(1, e.opensStage());
        assertEquals(
                List.of(
                        new Page.Text(),
                        new Page.Recipe("vftv:crucible", true),
                        new Page.Aspects("item:minecraft:torch"),
                        new Page.Structure("vftv:infusion_altar"),
                        new Page.Image("vftv:research/ruins"),
                        new Page.Action(),
                        new Page.Choir()),
                e.pages());
        JsonElement encoded = ResearchCodecs.ENTRY.encodeStart(JsonOps.INSTANCE, e).getOrThrow();
        assertEquals(e, ResearchCodecs.ENTRY.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void strictAboutFieldsTypesAndRanges() {
        rejects(entry(", \"colour\": 1"), "unknown fields [colour]");
        rejects(entry(", \"parents\": [\"resonance\"]"), "resonance is not a namespaced id");
        rejects(
                entry(", \"triggers\": [{\"type\": \"vftv:nothing\"}]"),
                "unknown trigger type vftv:nothing");
        rejects(
                entry(
                        ", \"triggers\": [{\"type\": \"vftv:discovered\", \"aspect\": \"vftv:aer\", \"page\": \"x:y\"}]"),
                "unknown fields [page]");
        rejects(entry("").replace("vftv:text", "vftv:poem"), "unknown page type vftv:poem");
        rejects(entry("").replace("\"stage\": 1", "\"stage\": 5"), "5");
        rejects(entry("").replace("[0, 0]", "[17, 0]"), "17");
        rejects(entry("").replace("[0, 0]", "[0]"), "");
        rejects(entry(", \"warp\": {\"temporary\": 101}"), "101");
        rejects(entry(", \"warp\": {\"bargain\": 1}"), "unknown fields [bargain]");
        rejects(entry(", \"rewards\": {\"points\": {\"vftv:aer\": 257}}"), "257");
        rejects(entry(", \"rewards\": {\"vftv:aer\": 2}"), "unknown fields [vftv:aer]");
        rejects(entry(", \"skip_cost\": {\"vftv:aer\": 0}"), "0");
        rejects(entry(", \"opens_stage\": 0"), "0");
        rejects(
                entry(
                        ", \"triggers\": [{\"type\": \"vftv:warp_at_least\", \"kind\": \"total\", \"value\": 1001}]"),
                "1001");
        rejects(
                entry(
                        ", \"triggers\": [{\"type\": \"vftv:warp_at_least\", \"kind\": \"some\", \"value\": 1}]"),
                "some");
        rejects(
                entry(", \"triggers\": [{\"type\": \"vftv:scanned\"}]"),
                "exactly one of key, tag or node");
        rejects(
                entry(", \"triggers\": [{\"type\": \"vftv:scanned\", \"key\": \"gem\"}]"),
                "not a scan key");
        rejects(
                entry(", \"triggers\": [{\"type\": \"vftv:scanned\", \"key\": \"node:0001\"}]"),
                "not a node key");
        rejects(
                entry(
                        ", \"triggers\": [{\"type\": \"vftv:scanned\","
                                + " \"key\": \"node:9999999999999999999\"}]"),
                "not a node key");
        assertTrue(
                parse(
                                ResearchCodecs.ENTRY,
                                entry(
                                                ", \"kind\": \"find\", \"triggers\": [{\"type\":"
                                                        + " \"vftv:scanned\", \"key\": \"node:-42\"}]")
                                        .replace("\"kind\": \"free\",", ""))
                        .isSuccess(),
                "a signed node key, as ScanKey.Node prints it");
        rejects(
                entry(", \"triggers\": [{\"type\": \"vftv:scanned\", \"tag\": \"item:c:gems\"}]"),
                "not a scan tag");
        rejects(entry("").replace("\"kind\": \"free\"", "\"kind\": \"quest\""), "quest");
        rejects(
                entry("")
                        .replace(
                                "{\"item\": \"vftv:commonplace_book\"}",
                                "{\"item\": \"a:b\", \"sprite\": \"a:c\"}"),
                "exactly one of item or sprite");
        rejects(
                entry("").replace("{\"item\": \"vftv:commonplace_book\"}", "{}"),
                "exactly one of item or sprite");
        rejects(entry("").replace("[{\"type\": \"vftv:text\"}]", "[]"), "");
        rejects(entry(", \"anchors\": [\"a:a\", \"a:b\", \"a:c\", \"a:d\", \"a:e\", \"a:f\"]"), "");
    }

    @Test
    void categoriesWithANullOrMissingStage() {
        CategoryEntry pages =
                parse(
                                ResearchCodecs.CATEGORY,
                                "{\"stage\": null, \"icon\": {\"item\": \"vftv:choir_page\"}, \"order\": 4}")
                        .getOrThrow();
        assertNull(pages.stage());
        assertEquals(4, pages.order());
        CategoryEntry resonance =
                parse(
                                ResearchCodecs.CATEGORY,
                                "{\"stage\": 1, \"icon\": {\"item\": \"a:b\"}, \"order\": 0}")
                        .getOrThrow();
        assertEquals(1, resonance.stage());
        JsonElement encoded =
                ResearchCodecs.RESEARCH_CATEGORY
                        .encodeStart(JsonOps.INSTANCE, resonance)
                        .getOrThrow();
        assertEquals(
                resonance, ResearchCodecs.CATEGORY.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertTrue(
                parse(
                                ResearchCodecs.CATEGORY,
                                "{\"icon\": {\"item\": \"a:b\"}, \"order\": 0, \"x\": 1}")
                        .isError());
    }
}
