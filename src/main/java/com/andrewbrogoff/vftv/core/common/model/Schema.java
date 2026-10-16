package com.andrewbrogoff.vftv.core.common.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One authored-content schema, {@code vftv:<family>/<n>}, and the validator every owner's schema
 * table plugs into (conventions, "Authored content").
 *
 * <p>An envelope is one JSON object with a {@code "schema"} string. {@link #check} rejects the
 * common list: an unknown schema, an unknown field, a missing required field, a wrong type and a
 * value out of range. An owner adds its own "Rejected:" rules as {@code extra}. Load validation of
 * registry entries stays with the owner's codec.
 *
 * @param id the schema id, {@code vftv:<family>/<n>}
 * @param fields the top-level fields, besides {@code "schema"}
 * @param extra the owner's further rules, returning problems; empty when there are none
 */
public record Schema(String id, List<Field> fields, Function<JsonObject, List<String>> extra) {
    private static final Pattern ID = Pattern.compile("vftv:[a-z0-9_]+/[1-9][0-9]*");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** Builds a schema, checking that the id has the {@code vftv:<family>/<n>} shape. */
    public Schema {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("bad schema id " + id);
        }
        fields = List.copyOf(fields);
    }

    /** A schema with no extra rules. */
    public static Schema of(String id, Field... fields) {
        return new Schema(id, List.of(fields), root -> List.of());
    }

    /** This schema with the owner's extra rules. */
    public Schema withExtra(Function<JsonObject, List<String>> rules) {
        return new Schema(id, fields, rules);
    }

    /**
     * Checks one envelope against the known schemas. Returns the problems, each prefixed with its
     * JSON path; empty when the envelope is valid.
     */
    public static List<String> check(JsonElement root, Map<String, Schema> known) {
        if (!root.isJsonObject()) {
            return List.of("$: not a JSON object");
        }
        JsonObject object = root.getAsJsonObject();
        JsonElement schemaField = object.get("schema");
        if (schemaField == null || !isString(schemaField)) {
            return List.of("$.schema: missing or not a string");
        }
        Schema schema = known.get(schemaField.getAsString());
        if (schema == null) {
            return List.of("$.schema: unknown schema " + schemaField.getAsString());
        }
        return schema.validate(object);
    }

    /** Validates an object that already names this schema. */
    public List<String> validate(JsonObject object) {
        List<String> problems = new ArrayList<>();
        List<Field> all = new ArrayList<>(fields);
        all.add(new Field("schema", new OneOf(Set.of(id)), true));
        checkObject(object, all, "$", problems);
        if (problems.isEmpty()) {
            problems.addAll(extra.apply(object));
        }
        return problems;
    }

    /** One field of an object: its name, its type and whether it must be present. */
    public record Field(String name, Type type, boolean required) {}

    /** A required field. */
    public static Field required(String name, Type type) {
        return new Field(name, type, true);
    }

    /** An optional field. */
    public static Field optional(String name, Type type) {
        return new Field(name, type, false);
    }

    /** A field type. */
    public sealed interface Type permits Str, Int, Num, Bool, Id, OneOf, ListOf, MapOf, Obj {}

    /** A string of {@code min}..{@code max} code points, optionally matching a pattern. */
    public record Str(int min, int max, @Nullable Pattern pattern) implements Type {}

    /** An integer in {@code [min, max]}. */
    public record Int(long min, long max) implements Type {}

    /** A number in {@code [min, max]}. */
    public record Num(double min, double max) implements Type {}

    /** A boolean. */
    public record Bool() implements Type {}

    /** A namespaced id, {@code ns:path}. */
    public record Id() implements Type {}

    /** One of a fixed set of strings. */
    public record OneOf(Set<String> values) implements Type {}

    /** A list of {@code min}..{@code max} elements, optionally all distinct. */
    public record ListOf(Type element, int min, int max, boolean unique) implements Type {}

    /** An object used as a map: keys of type {@code key}, values of type {@code value}. */
    public record MapOf(Str key, Type value, int min, int max) implements Type {}

    /** A nested object with fixed fields; unknown fields are rejected. */
    public record Obj(List<Field> fields) implements Type {}

    private static void check(JsonElement value, Type type, String path, List<String> problems) {
        switch (type) {
            case Str s -> {
                if (!isString(value)) {
                    problems.add(path + ": not a string");
                    return;
                }
                checkString(value.getAsString(), s, path, problems);
            }
            case Int i -> {
                BigDecimal n = number(value);
                if (n == null || n.stripTrailingZeros().scale() > 0) {
                    problems.add(path + ": not an integer");
                } else if (n.compareTo(BigDecimal.valueOf(i.min())) < 0
                        || n.compareTo(BigDecimal.valueOf(i.max())) > 0) {
                    problems.add(path + ": " + n + " outside " + i.min() + ".." + i.max());
                }
            }
            case Num d -> {
                BigDecimal n = number(value);
                if (n == null) {
                    problems.add(path + ": not a number");
                } else if (n.compareTo(BigDecimal.valueOf(d.min())) < 0
                        || n.compareTo(BigDecimal.valueOf(d.max())) > 0) {
                    problems.add(path + ": " + n + " outside " + d.min() + ".." + d.max());
                }
            }
            case Bool b -> {
                if (!(value instanceof JsonPrimitive p && p.isBoolean())) {
                    problems.add(path + ": not a boolean");
                }
            }
            case Id id -> {
                if (!isString(value) || !IDENTIFIER.matcher(value.getAsString()).matches()) {
                    problems.add(path + ": not a namespaced id");
                }
            }
            case OneOf o -> {
                if (!isString(value) || !o.values().contains(value.getAsString())) {
                    problems.add(path + ": not one of " + new TreeSet<>(o.values()));
                }
            }
            case ListOf l -> checkList(value, l, path, problems);
            case MapOf m -> checkMap(value, m, path, problems);
            case Obj o -> {
                if (!value.isJsonObject()) {
                    problems.add(path + ": not an object");
                    return;
                }
                checkObject(value.getAsJsonObject(), o.fields(), path, problems);
            }
        }
    }

    private static void checkString(String text, Str s, String path, List<String> problems) {
        int length = text.codePointCount(0, text.length());
        if (length < s.min() || length > s.max()) {
            problems.add(path + ": length " + length + " outside " + s.min() + ".." + s.max());
        } else if (s.pattern() != null && !s.pattern().matcher(text).matches()) {
            problems.add(path + ": does not match " + s.pattern());
        }
    }

    private static void checkList(JsonElement value, ListOf l, String path, List<String> problems) {
        if (!value.isJsonArray()) {
            problems.add(path + ": not a list");
            return;
        }
        JsonArray array = value.getAsJsonArray();
        if (array.size() < l.min() || array.size() > l.max()) {
            problems.add(
                    path + ": " + array.size() + " entries, outside " + l.min() + ".." + l.max());
        }
        Set<JsonElement> seen = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            check(array.get(i), l.element(), path + "[" + i + "]", problems);
            if (l.unique() && !seen.add(array.get(i))) {
                problems.add(path + "[" + i + "]: duplicate");
            }
        }
    }

    private static void checkMap(JsonElement value, MapOf m, String path, List<String> problems) {
        if (!value.isJsonObject()) {
            problems.add(path + ": not an object");
            return;
        }
        Map<String, JsonElement> entries = new LinkedHashMap<>(value.getAsJsonObject().asMap());
        if (entries.size() < m.min() || entries.size() > m.max()) {
            problems.add(
                    path + ": " + entries.size() + " entries, outside " + m.min() + ".." + m.max());
        }
        entries.forEach(
                (key, child) -> {
                    checkString(key, m.key(), path + "{" + key + "}", problems);
                    check(child, m.value(), path + "." + key, problems);
                });
    }

    private static void checkObject(
            JsonObject object, List<Field> fields, String path, List<String> problems) {
        Map<String, Field> byName = new LinkedHashMap<>();
        for (Field field : fields) {
            byName.put(field.name(), field);
        }
        for (String key : object.keySet()) {
            if (!byName.containsKey(key)) {
                problems.add(path + "." + key + ": unknown field");
            }
        }
        for (Field field : fields) {
            JsonElement child = object.get(field.name());
            if (child == null) {
                if (field.required()) {
                    problems.add(path + "." + field.name() + ": missing required field");
                }
            } else {
                check(child, field.type(), path + "." + field.name(), problems);
            }
        }
    }

    private static boolean isString(JsonElement value) {
        return value instanceof JsonPrimitive p && p.isString();
    }

    private static @Nullable BigDecimal number(JsonElement value) {
        return value instanceof JsonPrimitive p && p.isNumber() ? p.getAsBigDecimal() : null;
    }
}
