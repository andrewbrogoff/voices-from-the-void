package com.andrewbrogoff.vftv.core.text.model;

import com.andrewbrogoff.vftv.core.common.model.Schema;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The one lang validator (accessibility, "Validation"), run by datagen over every {@code
 * content/lang} input and the code-defined keys. It also performs the merge: one sorted map per
 * language code, with the code-defined keys added to {@code en_us}.
 *
 * <p>It runs the common rules 1 to 9. Owners' own "Rejected" rules stay with them, and the rules
 * only a second locale can exercise arrive with the second-locale ticket.
 */
public final class LangValidator {
    /** The source language: every required key must be here. */
    public static final String EN_US = "en_us";

    /** The lang envelope, {@code {"schema": "vftv:lang/1", "strings": {...}}}. */
    public static final Schema LANG =
            Schema.of(
                    "vftv:lang/1",
                    Schema.required(
                            "strings",
                            new Schema.MapOf(
                                    new Schema.Str(1, 256, null),
                                    new Schema.Str(0, Integer.MAX_VALUE, null),
                                    0,
                                    Integer.MAX_VALUE)));

    private static final String CODE_DEFINED = "(code-defined)";
    private static final Pattern LETTERS = Pattern.compile("\\p{L}+");
    private static final int SUBTITLE_MAX = 32;
    private static final int VOICE_MAX = 80;

    private LangValidator() {}

    /**
     * One authored input file.
     *
     * @param code the language code, the directory name under {@code content/lang}
     * @param file the file name, e.g. {@code accessibility.json}
     * @param text the raw file text
     */
    public record Input(String code, String file, String text) {}

    /**
     * A locale's number-word list for the prose lint.
     *
     * @param words lowercase number words that may not appear in prose
     * @param allowedKeys prose keys exempt from the list
     */
    public record NumberWords(Set<String> words, Set<String> allowedKeys) {
        /** No list: the number-word lint checks nothing. */
        public static final NumberWords NONE = new NumberWords(Set.of(), Set.of());
    }

    /**
     * The outcome: the merged strings per code, and every problem found.
     *
     * @param merged language code to its strings, sorted by key
     * @param problems one line per problem; empty when the inputs are valid
     */
    public record Result(
            SortedMap<String, SortedMap<String, String>> merged, List<String> problems) {}

    private record Source(String file, String value) {}

    /** Validates and merges the inputs against the owners' tables. */
    public static Result validate(
            List<Input> inputs,
            List<LangEntry> table,
            Set<String> catalogue,
            Map<String, NumberWords> numberWords) {
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, Source>> byCode = new TreeMap<>();
        Map<String, Source> english = byCode.computeIfAbsent(EN_US, c -> new TreeMap<>());
        for (LangEntry entry : table) {
            if (entry.english() != null && entry.key() != null) {
                english.put(entry.key(), new Source(CODE_DEFINED, entry.english()));
            }
        }

        for (Input input : inputs) {
            readInput(input, table, catalogue, byCode, problems);
        }

        for (LangEntry entry : table) {
            if (entry.key() != null && !english.containsKey(entry.key())) {
                problems.add(EN_US + ": missing required key " + entry.key());
            }
        }

        SortedMap<String, SortedMap<String, String>> merged = new TreeMap<>();
        byCode.forEach(
                (code, strings) -> {
                    SortedMap<String, String> out = new TreeMap<>();
                    strings.forEach(
                            (key, source) -> {
                                out.put(key, source.value());
                                LangEntry owner = owner(table, key);
                                if (owner != null) {
                                    String where = code + "/" + source.file() + ": " + key;
                                    Source base = english.get(key);
                                    checkValue(
                                            where,
                                            code,
                                            key,
                                            source.value(),
                                            base == null ? null : base.value(),
                                            owner,
                                            numberWords.getOrDefault(code, NumberWords.NONE),
                                            problems);
                                }
                            });
                    merged.put(code, out);
                });
        return new Result(merged, List.copyOf(problems));
    }

    private static void readInput(
            Input input,
            List<LangEntry> table,
            Set<String> catalogue,
            Map<String, Map<String, Source>> byCode,
            List<String> problems) {
        String where = input.code() + "/" + input.file();
        if (!catalogue.contains(input.code())) {
            problems.add(where + ": " + input.code() + " is not a supported language code");
            return;
        }
        JsonElement root;
        try {
            root = StrictJson.parse(input.text());
        } catch (StrictJson.MalformedJsonException e) {
            problems.add(where + ": " + e.getMessage());
            return;
        }
        List<String> envelope = Schema.check(root, Map.of(LANG.id(), LANG));
        if (!envelope.isEmpty()) {
            envelope.forEach(p -> problems.add(where + ": " + p));
            return;
        }
        Map<String, Source> strings = byCode.computeIfAbsent(input.code(), c -> new TreeMap<>());
        root.getAsJsonObject()
                .getAsJsonObject("strings")
                .asMap()
                .forEach(
                        (key, value) -> {
                            String text = value.getAsString();
                            LangEntry owner = owner(table, key);
                            if (owner == null) {
                                problems.add(where + ": " + key + " is not declared by any owner");
                                return;
                            }
                            if (!owner.file().equals(input.file())) {
                                problems.add(where + ": " + key + " belongs in " + owner.file());
                                return;
                            }
                            if (text.isEmpty()) {
                                problems.add(where + ": " + key + " is empty");
                                return;
                            }
                            Source previous =
                                    strings.putIfAbsent(key, new Source(input.file(), text));
                            if (previous != null) {
                                problems.add(
                                        where
                                                + ": "
                                                + key
                                                + " is duplicated, also in "
                                                + previous.file());
                            }
                        });
    }

    private static @Nullable LangEntry owner(List<LangEntry> table, String key) {
        for (LangEntry entry : table) {
            if (entry.owns(key)) {
                return entry;
            }
        }
        return null;
    }

    private static void checkValue(
            String where,
            String code,
            String key,
            String value,
            @Nullable String englishValue,
            LangEntry owner,
            NumberWords words,
            List<String> problems) {
        boolean source = code.equals(EN_US);
        if (owner.kind() == LangEntry.Kind.TEMPLATE) {
            Template template = Template.parse(value);
            template.errors().forEach(e -> problems.add(where + ": " + e));
            if (template.errors().isEmpty()) {
                String arity = template.arityProblem(owner.args(), source);
                if (arity != null) {
                    problems.add(where + ": " + arity);
                }
            }
        } else {
            Prose prose = Prose.parse(value);
            prose.errors().forEach(e -> problems.add(where + ": " + e));
            if (prose.text().codePoints().anyMatch(Character::isDigit)) {
                problems.add(where + ": a digit in prose");
            }
            if (!words.allowedKeys().contains(key)) {
                Matcher run = LETTERS.matcher(prose.text().toLowerCase(Locale.ROOT));
                while (run.find()) {
                    if (words.words().contains(run.group())) {
                        problems.add(where + ": the number word \"" + run.group() + "\" in prose");
                    }
                }
            }
            if (!source && englishValue != null) {
                Prose base = Prose.parse(englishValue);
                if (!counts(base.spans()).equals(counts(prose.spans()))) {
                    problems.add(where + ": fact spans differ from en_us");
                }
            }
        }
        int length = value.codePointCount(0, value.length());
        if (key.startsWith("subtitles.vftv.") && length > SUBTITLE_MAX) {
            problems.add(where + ": " + length + " code points, over " + SUBTITLE_MAX);
        }
        if (key.startsWith("voice.") && length > VOICE_MAX) {
            problems.add(where + ": " + length + " code points, over " + VOICE_MAX);
        }
    }

    private static Map<String, Integer> counts(List<String> spans) {
        Map<String, Integer> out = new HashMap<>();
        spans.forEach(s -> out.merge(s, 1, Integer::sum));
        return out;
    }

    /**
     * A parsed template: {@code %s} occurrences, {@code %n$s} occurrences by index, and grammar
     * errors.
     */
    record Template(int plain, Map<Integer, Integer> indexed, List<String> errors) {
        static Template parse(String value) {
            int plain = 0;
            Map<Integer, Integer> indexed = new TreeMap<>();
            List<String> errors = new ArrayList<>();
            int i = 0;
            while (i < value.length()) {
                if (value.charAt(i) != '%') {
                    i++;
                    continue;
                }
                int j = i + 1;
                while (j < value.length() && Character.isDigit(value.charAt(j))) {
                    j++;
                }
                if (j == i + 1 && j < value.length() && value.charAt(j) == '%') {
                    i = j + 1;
                } else if (j == i + 1 && j < value.length() && value.charAt(j) == 's') {
                    plain++;
                    i = j + 1;
                } else if (j > i + 1
                        && j + 1 < value.length()
                        && value.charAt(j) == '$'
                        && value.charAt(j + 1) == 's') {
                    int index = parseIndex(value.substring(i + 1, j));
                    if (index < 1) {
                        errors.add("placeholder index must be 1 or more at " + i);
                    } else {
                        indexed.merge(index, 1, Integer::sum);
                    }
                    i = j + 2;
                } else {
                    errors.add("unsupported placeholder at " + i + " (only %s, %n$s and %%)");
                    i = j;
                }
            }
            if (plain > 0 && !indexed.isEmpty()) {
                errors.add("mixes %s and %n$s");
            }
            return new Template(plain, indexed, errors);
        }

        private static int parseIndex(String digits) {
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        @Nullable String arityProblem(int args, boolean source) {
            if (plain > 0) {
                return plain == args ? null : plain + " placeholders, expected " + args;
            }
            for (int index : indexed.keySet()) {
                if (index > args) {
                    return "placeholder %" + index + "$s above the " + args + " arguments";
                }
            }
            for (int index = 1; index <= args; index++) {
                int count = indexed.getOrDefault(index, 0);
                if (count == 0) {
                    return "argument " + index + " is never used";
                }
                if (source && count > 1) {
                    return "argument " + index + " is used " + count + " times";
                }
            }
            return null;
        }
    }

    /** Parsed book prose: its plain text outside spans, its spans and markup errors. */
    record Prose(String text, List<String> spans, List<String> errors) {
        static Prose parse(String value) {
            StringBuilder text = new StringBuilder();
            StringBuilder span = null;
            List<String> spans = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            int i = 0;
            while (i < value.length()) {
                char c = value.charAt(i);
                if (c == '\\') {
                    char next = i + 1 < value.length() ? value.charAt(i + 1) : 0;
                    if (next != '*' && next != '{' && next != '\\') {
                        errors.add("bad escape at " + i);
                    }
                    (span == null ? text : span).append(next);
                    i += 2;
                    continue;
                }
                if (c == '{') {
                    if (span != null) {
                        errors.add("nested span at " + i);
                    }
                    span = new StringBuilder();
                } else if (c == '}') {
                    if (span == null) {
                        errors.add("unmatched } at " + i);
                    } else {
                        spans.add(span.toString());
                        span = null;
                    }
                } else if (c != '*') {
                    (span == null ? text : span).append(c);
                }
                i++;
            }
            if (span != null) {
                errors.add("unclosed span");
            }
            return new Prose(text.toString(), spans, errors);
        }
    }
}
