package com.andrewbrogoff.vftv.core.text.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.core.text.model.LangEntry.Kind;
import com.andrewbrogoff.vftv.core.text.model.LangValidator.Input;
import com.andrewbrogoff.vftv.core.text.model.LangValidator.NumberWords;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Self-tests: every rule of accessibility, "Validation", rejects its bad case. */
class LangValidatorTest {
    private static final List<LangEntry> TABLE =
            List.of(
                    LangEntry.codeDefined("message.vftv.busy", 0, "accessibility.json", "Not now."),
                    LangEntry.authored("item.vftv.jar", 0, "essentia.json", Kind.TEMPLATE),
                    LangEntry.authored("status.vftv.two", 2, "essentia.json", Kind.TEMPLATE),
                    LangEntry.authored("status.vftv.one", 1, "essentia.json", Kind.TEMPLATE),
                    LangEntry.pattern("subtitles\\.vftv\\..+", 0, "essentia.json", Kind.TEMPLATE),
                    LangEntry.pattern("voice\\..+", 0, "voices.json", Kind.TEMPLATE),
                    LangEntry.pattern("research\\.vftv\\..+", 0, "stage1.json", Kind.PROSE));

    private static final Set<String> CATALOGUE = Set.of("en_us", "fr_fr");

    private static final String VALID =
            """
            {"schema": "vftv:lang/1", "strings": {
              "item.vftv.jar": "Jar", "status.vftv.two": "Drawn %s of %s", "status.vftv.one": "Spills %s flux."}}
            """;

    private static String strings(String body) {
        return "{\"schema\": \"vftv:lang/1\", \"strings\": {" + body + "}}";
    }

    private static LangValidator.Result run(Input... inputs) {
        return LangValidator.validate(List.of(inputs), TABLE, CATALOGUE, Map.of());
    }

    private static Map<String, String> english(LangValidator.Result result) {
        return Objects.requireNonNull(result.merged().get("en_us"));
    }

    private static Input english(String file, String text) {
        return new Input("en_us", file, text);
    }

    /** Runs the inputs beside a valid en_us file and expects a problem containing {@code part}. */
    private static void rejects(String part, Input... extra) {
        Input[] all = new Input[extra.length + 1];
        all[0] = english("essentia.json", VALID);
        System.arraycopy(extra, 0, all, 1, extra.length);
        List<String> problems = run(all).problems();
        assertTrue(
                problems.stream().anyMatch(p -> p.contains(part)),
                () -> "expected a problem containing \"" + part + "\", got " + problems);
    }

    @Test
    void mergesSortedWithCodeDefinedKeysInEnglish() {
        LangValidator.Result result = run(english("essentia.json", VALID));
        assertEquals(List.of(), result.problems());
        assertEquals(
                List.of("item.vftv.jar", "message.vftv.busy", "status.vftv.one", "status.vftv.two"),
                List.copyOf(english(result).keySet()));
        assertEquals("Not now.", english(result).get("message.vftv.busy"));
    }

    @Test
    void rule1RejectsABadEnvelope() {
        rejects(
                "unknown schema",
                english("voices.json", "{\"schema\": \"vftv:lang/2\", \"strings\": {}}"));
        rejects("$.strings: missing", english("voices.json", "{\"schema\": \"vftv:lang/1\"}"));
        rejects(
                "$.strings: not an object",
                english("voices.json", "{\"schema\": \"vftv:lang/1\", \"strings\": []}"));
        rejects(
                "$.extra: unknown field",
                english(
                        "voices.json",
                        "{\"schema\": \"vftv:lang/1\", \"strings\": {}, \"extra\": 1}"));
        rejects("not a string", english("voices.json", strings("\"voice.a\": 3")));
        rejects("not a supported language code", new Input("xx_yy", "voices.json", strings("")));
    }

    @Test
    void rule2RejectsADuplicateKeyInsideOneObject() {
        rejects(
                "duplicate key $.strings.voice.a",
                english("voices.json", strings("\"voice.a\": \"x\", \"voice.a\": \"y\"")));
    }

    @Test
    void rule3RejectsDuplicatesAcrossFilesAndWithCodeDefinedKeys() {
        rejects(
                "is duplicated, also in (code-defined)",
                english("accessibility.json", strings("\"message.vftv.busy\": \"Busy.\"")));
        rejects(
                "is duplicated, also in",
                new Input("fr_fr", "essentia.json", strings("\"item.vftv.jar\": \"Bocal\"")),
                new Input("fr_fr", "essentia.json", strings("\"item.vftv.jar\": \"Pot\"")));
    }

    @Test
    void rule4RejectsUnownedKeysAndKeysInTheWrongFile() {
        rejects(
                "item.vftv.cog is not declared by any owner",
                english("voices.json", strings("\"item.vftv.cog\": \"Cog\"")));
        rejects(
                "voice.a belongs in voices.json",
                english("stage1.json", strings("\"voice.a\": \"x\"")));
    }

    @Test
    void rule5RejectsAMissingRequiredEnglishKey() {
        List<String> problems = run().problems();
        assertTrue(
                problems.contains("en_us: missing required key item.vftv.jar"), problems::toString);
        assertTrue(
                problems.stream().noneMatch(p -> p.contains("message.vftv.busy")),
                problems::toString);
    }

    @Test
    void rule6RejectsAnEmptyString() {
        rejects("voice.a is empty", english("voices.json", strings("\"voice.a\": \"\"")));
    }

    @Test
    void rule7RejectsMalformedTemplates() {
        rejects("unsupported placeholder", english("voices.json", strings("\"voice.a\": \"%d\"")));
        rejects("unsupported placeholder", english("voices.json", strings("\"voice.a\": \"%f\"")));
        rejects(
                "unsupported placeholder",
                english("voices.json", strings("\"voice.a\": \"50% off\"")));
        rejects(
                "unsupported placeholder",
                english("voices.json", strings("\"voice.a\": \"trailing %\"")));
        rejects(
                "index must be 1 or more",
                english("voices.json", strings("\"voice.a\": \"%0$s\"")));
        rejects("mixes %s and %n$s", english("voices.json", strings("\"voice.a\": \"%s %1$s\"")));
        rejects(
                "unsupported placeholder",
                english("voices.json", strings("\"voice.a\": \"%１$s\"")));
    }

    @Test
    void rule7ChecksEnglishArityExactly() {
        String one = "status.vftv.one";
        assertEquals(List.of(), problemsFor(one, "Spills %1$s flux. 100%%", "en_us"));
        assertTrue(
                problemsFor(one, "Spills flux.", "en_us")
                        .get(0)
                        .contains("argument 1 is never used"));
        assertTrue(
                problemsFor(one, "%s %s", "en_us").get(0).contains("2 placeholders, expected 1"));
        assertTrue(problemsFor(one, "%2$s", "en_us").get(0).contains("above the 1 arguments"));
        String two = "status.vftv.two";
        assertTrue(
                problemsFor(two, "%1$s %1$s", "en_us")
                        .get(0)
                        .contains("argument 1 is used 2 times"));
        assertTrue(
                problemsFor(two, "%1$s %2$s %2$s", "en_us")
                        .get(0)
                        .contains("argument 2 is used 2 times"));
        assertTrue(problemsFor(two, "%1$s", "en_us").get(0).contains("argument 2 is never used"));
    }

    @Test
    void rule7LetsOtherLocalesReorderAndRepeat() {
        assertEquals(List.of(), problemsFor("status.vftv.two", "%2$s ... %1$s %2$s", "fr_fr"));
        assertTrue(
                problemsFor("status.vftv.two", "%2$s", "fr_fr")
                        .get(0)
                        .contains("argument 1 is never used"));
    }

    @Test
    void rule7ChecksProseMarkupAndFactSpans() {
        String key = "research.vftv.entry.page.0";
        assertEquals(
                List.of(),
                problemsFor(key, "It hums {fact:vis} \\* and \\{ and \\\\ softly.", "en_us"));
        assertTrue(problemsFor(key, "bad \\x escape", "en_us").get(0).contains("bad escape"));
        assertTrue(problemsFor(key, "{fact:a", "en_us").get(0).contains("unclosed span"));
        assertTrue(problemsFor(key, "a } b", "en_us").get(0).contains("unmatched }"));
        assertTrue(problemsFor(key, "{fact:{a}}", "en_us").get(0).contains("nested span"));
        // A % in prose is text, not a placeholder: prose is never parsed as a template.
        assertEquals(
                List.of(),
                problemsFor(key, "Half the jar, 50% in spirit.", "en_us").stream()
                        .filter(p -> !p.contains("digit"))
                        .toList());

        List<String> french =
                LangValidator.validate(
                                List.of(
                                        english(
                                                "stage1.json",
                                                strings("\"" + key + "\": \"A {fact:vis} B\"")),
                                        new Input(
                                                "fr_fr",
                                                "stage1.json",
                                                strings("\"" + key + "\": \"A {fact:flux}\""))),
                                TABLE,
                                CATALOGUE,
                                Map.of())
                        .problems();
        assertTrue(
                french.stream().anyMatch(p -> p.contains("fact spans differ")), french::toString);
    }

    @Test
    void rule8RejectsDigitsAndNumberWordsInProse() {
        String key = "research.vftv.entry.page.0";
        assertTrue(problemsFor(key, "Wait 3 days.", "en_us").get(0).contains("a digit in prose"));
        assertTrue(problemsFor(key, "Wait ３ days.", "en_us").get(0).contains("a digit in prose"));
        assertTrue(problemsFor(key, "Wait ٣ days.", "en_us").get(0).contains("a digit in prose"));
        assertEquals(List.of(), problemsFor(key, "It costs {fact:12} vis.", "en_us"));

        NumberWords words = new NumberWords(Set.of("three"), Set.of());
        List<String> problems =
                LangValidator.validate(
                                List.of(
                                        english(
                                                "stage1.json",
                                                strings("\"" + key + "\": \"Wait Three days.\""))),
                                TABLE,
                                CATALOGUE,
                                Map.of("en_us", words))
                        .problems();
        assertTrue(
                problems.stream().anyMatch(p -> p.contains("number word \"three\"")),
                problems::toString);

        NumberWords allowed = new NumberWords(Set.of("three"), Set.of(key));
        List<String> exempt =
                LangValidator.validate(
                                List.of(
                                        english(
                                                "stage1.json",
                                                strings("\"" + key + "\": \"Wait three days.\""))),
                                TABLE,
                                CATALOGUE,
                                Map.of("en_us", allowed))
                        .problems();
        assertTrue(exempt.stream().noneMatch(p -> p.contains("number word")), exempt::toString);
    }

    @Test
    void rule9BoundsSubtitlesAndVoiceLines() {
        assertEquals(List.of(), problemsFor("subtitles.vftv.hum", "x".repeat(32), "en_us"));
        assertTrue(
                problemsFor("subtitles.vftv.hum", "x".repeat(33), "en_us")
                        .get(0)
                        .contains("over 32"));
        assertEquals(List.of(), problemsFor("voice.a", "é".repeat(80), "en_us"));
        assertTrue(problemsFor("voice.a", "x".repeat(81), "en_us").get(0).contains("over 80"));
    }

    @Test
    void sharedMessagesValidateClean() {
        LangValidator.Result result =
                LangValidator.validate(List.of(), SharedMessages.TABLE, CATALOGUE, Map.of());
        assertEquals(List.of(), result.problems());
        assertEquals(3, english(result).size());
    }

    @Test
    void langEntriesAreWellFormed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LangEntry(null, null, 0, "a.json", Kind.TEMPLATE, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> LangEntry.authored("a", -1, "a.json", Kind.TEMPLATE));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LangEntry(null, Pattern.compile("a"), 0, "a.json", Kind.TEMPLATE, "A"));
        assertTrue(LangEntry.pattern("a\\..+", 0, "a.json", Kind.TEMPLATE).owns("a.b"));
    }

    /** The problems for one key's value alone, under a copy of its owner's row. */
    private static List<String> problemsFor(String key, String value, String code) {
        LangEntry owner = TABLE.stream().filter(e -> e.owns(key)).findFirst().orElseThrow();
        LangEntry probe =
                new LangEntry(
                        owner.key(),
                        owner.pattern(),
                        owner.args(),
                        "probe.json",
                        owner.kind(),
                        null);
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        Input input =
                new Input(code, "probe.json", strings("\"" + key + "\": \"" + escaped + "\""));
        return LangValidator.validate(List.of(input), List.of(probe), CATALOGUE, Map.of())
                .problems()
                .stream()
                .filter(p -> !p.contains("missing required"))
                .toList();
    }
}
