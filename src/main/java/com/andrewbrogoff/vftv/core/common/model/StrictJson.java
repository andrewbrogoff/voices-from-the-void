package com.andrewbrogoff.vftv.core.common.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;

/**
 * A strict JSON reader for hand-written and authored files: standard JSON only, one top-level
 * value, and a duplicate key inside one object is an error (a plain map would keep the last).
 */
public final class StrictJson {
    private StrictJson() {}

    /** Thrown for any input that is not strict, duplicate-free JSON. */
    public static final class MalformedJsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        MalformedJsonException(String message) {
            super(message);
        }
    }

    /** Parses {@code text}, or throws {@link MalformedJsonException} naming the problem. */
    public static JsonElement parse(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement value = read(reader, "$");
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new MalformedJsonException("more than one top-level value");
            }
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException e) {
            throw new MalformedJsonException(String.valueOf(e.getMessage()));
        }
    }

    private static JsonElement read(JsonReader reader, String path) throws IOException {
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readObject(reader, path);
            case BEGIN_ARRAY -> readArray(reader, path);
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default ->
                    throw new MalformedJsonException("unexpected " + reader.peek() + " at " + path);
        };
    }

    private static JsonObject readObject(JsonReader reader, String path) throws IOException {
        JsonObject object = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            String child = path + "." + key;
            if (object.has(key)) {
                throw new MalformedJsonException("duplicate key " + child);
            }
            object.add(key, read(reader, child));
        }
        reader.endObject();
        return object;
    }

    private static JsonArray readArray(JsonReader reader, String path) throws IOException {
        JsonArray array = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
            array.add(read(reader, path + "[" + array.size() + "]"));
        }
        reader.endArray();
        return array;
    }
}
