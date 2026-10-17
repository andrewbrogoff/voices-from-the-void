package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The client's copy of the vis tuning, the fixed limits the book states, and the {@code starter}
 * flag (wands spec, "Sync and state"; conventions, "Rules records"). Fixed-shape: its wire form is
 * the JSON of every key, each required.
 *
 * @param tuning the tuning in force
 * @param starter whether the starter's entry is {@code PLACED}
 */
public record VisRules(VisTuning tuning, boolean starter) {
    /** The record's format. */
    public static final int FORMAT = 1;

    /** The fixed limits the record carries for the book, with their values. */
    static final String NETWORK_MEMBERS = "limits.network_members";

    static final String NODES_PER_CELL = "limits.nodes_per_cell";
    static final String ASPECTS_PER_NODE = "limits.aspects_per_node";

    /** The longest encoded record accepted, in characters (the defaults take under 1,000). */
    static final int MAX_CHARS = 4096;

    /** The wire form: the record's JSON, bounded, rejected at decode unless valid. */
    public static final StreamCodec<ByteBuf, VisRules> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(MAX_CHARS).map(VisRules::decode, r -> r.toJson().toString());

    /** The record as JSON: {@code format}, every tuning key, the limits and {@code starter}. */
    public JsonObject toJson() {
        JsonObject json = tuning.toJson();
        json.addProperty("format", FORMAT);
        json.addProperty(NETWORK_MEMBERS, VisLimits.MAX_NETWORK_MEMBERS);
        json.addProperty(NODES_PER_CELL, VisLimits.MAX_NODES_PER_CELL);
        json.addProperty(ASPECTS_PER_NODE, VisLimits.MAX_NODE_ASPECTS);
        json.addProperty("starter", starter);
        return json;
    }

    /**
     * Reads the JSON form, every key required.
     *
     * @throws IllegalArgumentException naming the problem
     */
    public static VisRules fromJson(JsonObject json) {
        if (!(json.get("format") instanceof JsonPrimitive f)
                || !f.isNumber()
                || f.getAsInt() != FORMAT) {
            throw new IllegalArgumentException("format is not " + FORMAT);
        }
        for (String key : new String[] {NETWORK_MEMBERS, NODES_PER_CELL, ASPECTS_PER_NODE}) {
            if (!(json.get(key) instanceof JsonPrimitive p) || !p.isNumber()) {
                throw new IllegalArgumentException(key + ": missing");
            }
        }
        if (!(json.get("starter") instanceof JsonPrimitive s) || !s.isBoolean()) {
            throw new IllegalArgumentException("starter: not a boolean");
        }
        VisTuning tuning =
                VisTuning.fromJson(
                        json,
                        true,
                        "format",
                        NETWORK_MEMBERS,
                        NODES_PER_CELL,
                        ASPECTS_PER_NODE,
                        "starter");
        return new VisRules(tuning, s.getAsBoolean());
    }

    private static VisRules decode(String text) {
        try {
            JsonElement json = StrictJson.parse(text);
            if (!json.isJsonObject()) {
                throw new DecoderException("vis rules: not an object");
            }
            return fromJson(json.getAsJsonObject());
        } catch (StrictJson.MalformedJsonException | IllegalArgumentException e) {
            throw new DecoderException("vis rules: " + e.getMessage(), e);
        }
    }
}
