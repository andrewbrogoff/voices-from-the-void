package com.andrewbrogoff.vftv.core.text.model;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One row of an owner's lang table: a key or key pattern, its argument count, the {@code
 * content/lang/<code>/} file it belongs in, and how its value is parsed (accessibility, "The lang
 * pipeline").
 *
 * @param key the exact key, or null for a pattern row
 * @param pattern the key pattern, or null for an exact row
 * @param args the number of placeholders, its owner's Args
 * @param file the owner's file name, e.g. {@code accessibility.json}
 * @param kind how the value is parsed
 * @param english the final English of a code-defined key, which datagen writes into {@code en_us};
 *     null for an authored key
 */
public record LangEntry(
        @Nullable String key,
        @Nullable Pattern pattern,
        int args,
        String file,
        Kind kind,
        @Nullable String english) {
    /** How a value is parsed: once, one way or the other. */
    public enum Kind {
        /** A translatable template: {@code %s}, {@code %n$s} and {@code %%} only. */
        TEMPLATE,
        /** Book prose: markup with {@code \} escapes and {@code {fact:...}} spans; no digits. */
        PROSE
    }

    /** Checks that exactly one of key and pattern is set and the count is sane. */
    public LangEntry {
        if ((key == null) == (pattern == null)) {
            throw new IllegalArgumentException("exactly one of key and pattern");
        }
        if (args < 0) {
            throw new IllegalArgumentException("args < 0");
        }
        if (english != null && key == null) {
            throw new IllegalArgumentException("a code-defined key is exact");
        }
    }

    /** A code-defined template key with final English, written by datagen. */
    public static LangEntry codeDefined(String key, int args, String file, String english) {
        return new LangEntry(key, null, args, file, Kind.TEMPLATE, english);
    }

    /** An authored exact key, required in {@code en_us}. */
    public static LangEntry authored(String key, int args, String file, Kind kind) {
        return new LangEntry(key, null, args, file, kind, null);
    }

    /** An authored key pattern; keys matching it are owned but not individually required. */
    public static LangEntry pattern(String regex, int args, String file, Kind kind) {
        return new LangEntry(null, Pattern.compile(regex), args, file, kind, null);
    }

    /** Whether this row owns {@code candidate}. */
    public boolean owns(String candidate) {
        return key != null
                ? key.equals(candidate)
                : pattern != null && pattern.matcher(candidate).matches();
    }
}
