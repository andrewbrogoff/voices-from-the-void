package com.andrewbrogoff.vftv.core.research.model;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * One fact span of book prose, {@code {fact:<source>:<args>}} (research spec, "Prose and fact
 * references"): the args are split on {@code :}, and each source's arity is fixed. Whether a key or
 * field is one its source accepts is that source's check ({@code FactSource}).
 *
 * @param source the source name, e.g. {@code recipe}
 * @param args the arguments, exactly the source's arity
 */
public record FactSpan(String source, List<String> args) {
    /** Every source and its fixed arity. */
    public static final Map<String, Integer> ARITY =
            Map.ofEntries(
                    Map.entry("recipe", 3),
                    Map.entry("aspects", 3),
                    Map.entry("entry", 3),
                    Map.entry("pool", 2),
                    Map.entry("warp", 1),
                    Map.entry("warp_rules", 1),
                    Map.entry("key", 1),
                    Map.entry("vis", 1),
                    Map.entry("essentia", 1),
                    Map.entry("infusion", 1),
                    Map.entry("focus", 1),
                    Map.entry("focus_effect", 3),
                    Map.entry("focus_medium", 3),
                    Map.entry("focus_modifier", 3),
                    Map.entry("focus_rank", 2),
                    Map.entry("focus_rules", 1),
                    Map.entry("golem", 1),
                    Map.entry("golem_material", 3),
                    Map.entry("wearable", 3),
                    Map.entry("baffle", 1),
                    Map.entry("hush", 1),
                    Map.entry("road", 1));

    private static final String PREFIX = "fact:";

    /** Copies the args. */
    public FactSpan {
        args = List.copyOf(args);
    }

    /**
     * Parses a span's text between its braces, e.g. {@code fact:pool:vftv:aer}.
     *
     * @return the span, or null when it is malformed: no {@code fact:} prefix, an unknown source,
     *     the wrong arity or an empty argument
     */
    public static @Nullable FactSpan parse(String text) {
        if (!text.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = text.substring(PREFIX.length()).split(":", -1);
        Integer arity = ARITY.get(parts[0]);
        if (arity == null || parts.length - 1 != arity) {
            return null;
        }
        List<String> args = List.of(parts).subList(1, parts.length);
        return args.stream().anyMatch(String::isEmpty) ? null : new FactSpan(parts[0], args);
    }
}
