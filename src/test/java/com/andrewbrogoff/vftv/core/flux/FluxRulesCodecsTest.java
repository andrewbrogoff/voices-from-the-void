package com.andrewbrogoff.vftv.core.flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.VarInt;
import org.junit.jupiter.api.Test;

class FluxRulesCodecsTest {
    private static final FluxRules RULES = sample();

    /** Distinct values everywhere, so a swapped field can't round-trip by accident. */
    private static FluxRules sample() {
        int[] tuning = new int[FluxTuning.KEYS.size()];
        for (int i = 0; i < tuning.length; i++) {
            FluxTuning.Field key = FluxTuning.KEYS.get(i);
            tuning[i] = key.max() - (i % (key.max() - key.min() + 1));
        }
        return FluxTuning.rules(tuning, false, true, true);
    }

    private static JsonObject json(FluxRules rules) {
        return FluxRulesCodecs.CODEC
                .encodeStart(JsonOps.INSTANCE, rules)
                .getOrThrow()
                .getAsJsonObject();
    }

    @Test
    void theCodecRoundTripsThroughJsonAndNbt() {
        JsonElement json = json(RULES);
        assertEquals(RULES, FluxRulesCodecs.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        Tag nbt = FluxRulesCodecs.CODEC.encodeStart(NbtOps.INSTANCE, RULES).getOrThrow();
        assertEquals(RULES, FluxRulesCodecs.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
    }

    @Test
    void thePersistedFormNamesEveryFieldAndNoGeneration() {
        JsonObject json = json(RULES);
        assertEquals(FluxTuning.FIELDS.size(), json.size());
        assertEquals(1, json.get("format").getAsInt());
        assertEquals(RULES.hushWalk(), json.get("hush.walk").getAsInt());
        assertEquals(0, json.get("hush.spread").getAsInt());
        assertEquals(1, json.get("hush.unattended").getAsInt());
        assertTrue(!json.has("generation"));
    }

    @Test
    void theCodecRejectsAnythingOutOfBounds() {
        assertRejected(j -> j.remove("hush.walk"));
        assertRejected(j -> j.addProperty("extra", 1));
        assertRejected(j -> j.addProperty("format", 2));
        assertRejected(j -> j.addProperty("hush.walk", 65));
        assertRejected(j -> j.addProperty("peal.interval", 19));
        assertRejected(j -> j.addProperty("hush.hollows", 2));
        assertRejected(j -> j.addProperty("hush.walk", "many"));
    }

    private static void assertRejected(java.util.function.Consumer<JsonObject> change) {
        JsonObject json = json(RULES);
        change.accept(json);
        assertTrue(FluxRulesCodecs.CODEC.parse(JsonOps.INSTANCE, json).isError(), json.toString());
    }

    @Test
    void theStreamCodecRoundTrips() {
        ByteBuf buf = Unpooled.buffer();
        FluxRulesPayload.STREAM_CODEC.encode(buf, new FluxRulesPayload(RULES));
        FluxRulesPayload read = FluxRulesPayload.STREAM_CODEC.decode(buf);
        assertEquals(RULES, read.rules());
        assertEquals(RULES.generation(), read.rules().generation(), "folded again on arrival");
        assertEquals(0, buf.readableBytes());
    }

    /** A hostile or broken payload: every field out of range, a future format, a short buffer. */
    @Test
    void theStreamCodecRejectsHostilePayloads() {
        int[] values = FluxTuning.values(RULES);
        for (int i = 0; i < values.length; i++) {
            FluxTuning.Field field = FluxTuning.FIELDS.get(i);
            for (int bad : new int[] {field.min() - 1, field.max() + 1, Integer.MIN_VALUE}) {
                int[] hostile = values.clone();
                hostile[i] = bad;
                ByteBuf buf = Unpooled.buffer();
                for (int v : hostile) {
                    VarInt.write(buf, v);
                }
                assertThrows(
                        DecoderException.class,
                        () -> FluxRulesCodecs.STREAM_CODEC.decode(buf),
                        field.name() + " = " + bad);
            }
        }
        ByteBuf truncated = Unpooled.buffer();
        FluxRulesCodecs.STREAM_CODEC.encode(truncated, RULES);
        ByteBuf shortBuf = truncated.slice(0, truncated.readableBytes() - 1);
        assertThrows(RuntimeException.class, () -> FluxRulesCodecs.STREAM_CODEC.decode(shortBuf));
        ByteBuf endless = Unpooled.buffer();
        for (int i = 0; i < 6; i++) {
            endless.writeByte(0xFF);
        }
        assertThrows(RuntimeException.class, () -> FluxRulesCodecs.STREAM_CODEC.decode(endless));
    }
}
