package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.common.model.StrictJson;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

/**
 * S2C: the {@link WarpRules} in force, sent to every player on join, after every {@code /reload}
 * and after a changed warp switch (conventions, "Rules records").
 *
 * @param rules the rules in force
 */
public record WarpRulesPayload(WarpRules rules) implements CustomPacketPayload {
    /** The payload's type, {@code vftv:warp_rules}. */
    public static final Type<WarpRulesPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("vftv", "warp_rules"));

    /**
     * The longest encoded record accepted, in characters: the defaults take about 800, and the
     * largest valid record (16 dimensions and 16 milestones of 256 characters) under 10,000.
     */
    static final int MAX_CHARS = 16_384;

    /**
     * The rules' codec: the record's JSON form (every tuning key, {@code format} and the switches),
     * each key required and checked by the one tuning reader.
     */
    public static final Codec<WarpRules> RULES_CODEC =
            ExtraCodecs.JSON.comapFlatMap(
                    WarpRulesPayload::fromJson, rules -> WarpTuning.toJson(rules, true));

    /** The wire form: the record's JSON, bounded, and rejected at decode unless it is valid. */
    public static final StreamCodec<ByteBuf, WarpRulesPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(MAX_CHARS)
                    .map(
                            WarpRulesPayload::decode,
                            p -> WarpTuning.toJson(p.rules, true).toString());

    @Override
    public Type<WarpRulesPayload> type() {
        return TYPE;
    }

    private static DataResult<WarpRules> fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            return DataResult.error(() -> "warp rules: not an object");
        }
        try {
            return DataResult.success(WarpTuning.fromJson(json.getAsJsonObject(), true));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "warp rules: " + e.getMessage());
        }
    }

    private static WarpRulesPayload decode(String text) {
        try {
            JsonElement json = StrictJson.parse(text);
            return new WarpRulesPayload(fromJson(json).getOrThrow(DecoderException::new));
        } catch (StrictJson.MalformedJsonException e) {
            throw new DecoderException("warp rules: " + e.getMessage(), e);
        }
    }
}
