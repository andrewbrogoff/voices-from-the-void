package com.andrewbrogoff.vftv.core.common.model;

import static com.andrewbrogoff.vftv.core.common.model.Schema.optional;
import static com.andrewbrogoff.vftv.core.common.model.Schema.required;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The common rejection list, one case per rule and type. */
class SchemaTest {
    private static final Schema WIDGETS =
            Schema.of(
                    "vftv:widgets/1",
                    required("name", new Schema.Str(1, 8, Pattern.compile("[a-z]+"))),
                    required("count", new Schema.Int(0, 10)),
                    optional("weight", new Schema.Num(0.0, 1.0)),
                    optional("shiny", new Schema.Bool()),
                    optional("item", new Schema.Id()),
                    optional("kind", new Schema.OneOf(Set.of("small", "large"))),
                    optional("tags", new Schema.ListOf(new Schema.Str(1, 4, null), 1, 2, true)),
                    optional(
                            "costs",
                            new Schema.MapOf(
                                    new Schema.Str(1, 8, Pattern.compile("[a-z]+")),
                                    new Schema.Int(1, 99),
                                    0,
                                    2)),
                    optional("part", new Schema.Obj(List.of(required("id", new Schema.Id())))));

    private static final Map<String, Schema> KNOWN = Map.of(WIDGETS.id(), WIDGETS);

    private static List<String> check(String json) {
        return Schema.check(StrictJson.parse(json), KNOWN);
    }

    private static void rejects(String json, String expected) {
        List<String> problems = check(json);
        assertTrue(
                problems.stream().anyMatch(p -> p.contains(expected)),
                () -> json + " gave " + problems + ", expected one containing " + expected);
    }

    @Test
    void acceptsAValidEnvelope() {
        assertEquals(
                List.of(),
                check(
                        """
                        {"schema": "vftv:widgets/1", "name": "cog", "count": 3, "weight": 0.5,
                         "shiny": false, "item": "minecraft:stick", "kind": "small", "tags": ["a"],
                         "costs": {"iron": 2}, "part": {"id": "vftv:gear"}}
                        """));
        assertEquals(
                List.of(),
                check("{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", \"count\": 0}"));
    }

    @Test
    void rejectsTheEnvelopeItself() {
        assertEquals(List.of("$: not a JSON object"), Schema.check(StrictJson.parse("[]"), KNOWN));
        rejects("{\"name\": \"a\"}", "$.schema: missing");
        rejects("{\"schema\": 1}", "$.schema: missing or not a string");
        rejects("{\"schema\": \"vftv:widgets/2\"}", "unknown schema vftv:widgets/2");
    }

    @Test
    void rejectsUnknownAndMissingFields() {
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\"}",
                "$.count: missing required field");
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", \"count\": 1, \"colour\": 2}",
                "$.colour: unknown field");
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", \"count\": 1, \"part\": {\"id\": \"a:b\", \"x\": 1}}",
                "$.part.x: unknown field");
    }

    @Test
    void rejectsWrongTypes() {
        String base = "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", \"count\": 1, ";
        rejects(base + "\"weight\": \"heavy\"}", "$.weight: not a number");
        rejects(base + "\"shiny\": 1}", "$.shiny: not a boolean");
        rejects(base + "\"item\": \"Stick\"}", "$.item: not a namespaced id");
        rejects(base + "\"item\": 3}", "$.item: not a namespaced id");
        rejects(base + "\"kind\": \"huge\"}", "$.kind: not one of");
        rejects(base + "\"tags\": \"a\"}", "$.tags: not a list");
        rejects(base + "\"costs\": []}", "$.costs: not an object");
        rejects(base + "\"part\": 1}", "$.part: not an object");
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": 5, \"count\": 1}",
                "$.name: not a string");
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", \"count\": 1.5}",
                "$.count: not an integer");
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", \"count\": \"1\"}",
                "$.count: not an integer");
    }

    @Test
    void rejectsValuesOutOfRange() {
        String base = "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", ";
        rejects(base + "\"count\": 11}", "$.count: 11 outside 0..10");
        rejects(base + "\"count\": -1}", "$.count: -1 outside 0..10");
        rejects(base + "\"count\": 1, \"weight\": 1.5}", "$.weight: 1.5 outside");
        rejects(
                base + "\"count\": 1, \"weight\": 1.0000000000000001}",
                "$.weight: 1.0000000000000001 outside");
        rejects(base + "\"count\": 1, \"tags\": []}", "$.tags: 0 entries, outside 1..2");
        rejects(base + "\"count\": 1, \"tags\": [\"a\", \"a\"]}", "$.tags[1]: duplicate");
        rejects(
                base + "\"count\": 1, \"tags\": [\"toolong\"]}",
                "$.tags[0]: length 7 outside 1..4");
        rejects(
                base + "\"count\": 1, \"costs\": {\"a\": 1, \"b\": 1, \"c\": 1}}",
                "$.costs: 3 entries");
        rejects(base + "\"count\": 1, \"costs\": {\"Iron\": 1}}", "$.costs{Iron}: does not match");
        rejects(base + "\"count\": 1, \"costs\": {\"iron\": 0}}", "$.costs.iron: 0 outside 1..99");
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": \"\", \"count\": 1}",
                "$.name: length 0");
        rejects(
                "{\"schema\": \"vftv:widgets/1\", \"name\": \"A\", \"count\": 1}",
                "$.name: does not match");
    }

    @Test
    void runsTheOwnersExtraRulesOnlyOnAValidEnvelope() {
        Schema strict =
                WIDGETS.withExtra(
                        root ->
                                root.get("count").getAsInt() == 7
                                        ? List.of("$.count: seven is unlucky")
                                        : List.of());
        Map<String, Schema> known = Map.of(strict.id(), strict);
        assertEquals(
                List.of("$.count: seven is unlucky"),
                Schema.check(
                        StrictJson.parse(
                                "{\"schema\": \"vftv:widgets/1\", \"name\": \"a\", \"count\": 7}"),
                        known));
        assertEquals(
                1,
                Schema.check(
                                StrictJson.parse("{\"schema\": \"vftv:widgets/1\", \"count\": 7}"),
                                known)
                        .size());
    }

    @Test
    void aCheckedValueRunsTheOwnersCheckUnderItsPath() {
        Schema checked =
                Schema.of(
                        "vftv:checked/1",
                        required(
                                "value",
                                new Schema.Checked(
                                        v -> v.isJsonArray() ? List.of() : List.of("not mine"))));
        Map<String, Schema> known = Map.of(checked.id(), checked);
        assertEquals(
                List.of(),
                Schema.check(
                        StrictJson.parse("{\"schema\": \"vftv:checked/1\", \"value\": []}"),
                        known));
        assertEquals(
                List.of("$.value: not mine"),
                Schema.check(
                        StrictJson.parse("{\"schema\": \"vftv:checked/1\", \"value\": 1}"), known));
    }

    @Test
    void schemaIdsHaveTheVftvShape() {
        assertThrows(IllegalArgumentException.class, () -> Schema.of("widgets"));
        assertThrows(IllegalArgumentException.class, () -> Schema.of("vftv:widgets/0"));
        assertThrows(IllegalArgumentException.class, () -> Schema.of("other:widgets/1"));
    }
}
