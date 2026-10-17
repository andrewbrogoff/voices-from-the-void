package com.andrewbrogoff.vftv.core.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The warp rules in force, the rollback rule and the {@code WarpRules} codecs. */
class WarpRulesServiceTest {
    @AfterEach
    void restore() {
        WarpRulesService.applyTuning(DataResult.success(WarpTuning.DEFAULTS), true);
        WarpRulesService.installConfig(WarpConfig.DEFAULT);
        WarpRulesService.clearClient();
    }

    @Test
    void theDefaultsAreInForceBeforeAnyLoad() {
        assertEquals(WarpTuning.DEFAULTS, WarpRulesService.current());
        assertEquals(WarpConfig.DEFAULT, WarpRulesService.config());
        assertEquals(new WarpConfig(true, true, true, true), WarpConfig.DEFAULT);
    }

    @Test
    void anInvalidFileRefusesTheWorldAtOpen() {
        WarpRules before = WarpRulesService.current();
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                WarpRulesService.applyTuning(
                                        WarpRulesService.parse("{\"permanent.cap\": 9}"), false));
        String message = String.valueOf(e.getMessage());
        assertTrue(message.contains("data/vftv/tuning/warp.json"), message);
        assertTrue(message.contains("permanent.cap"), message);
        assertSame(before, WarpRulesService.current());
    }

    @Test
    void aReloadThatBreaksAMilestoneRuleKeepsThePreviousRecord() {
        WarpRulesService.applyTuning(WarpRulesService.parse("{\"grace\": 7}"), true);
        WarpRules before = WarpRulesService.current();
        assertEquals(7, before.grace());
        WarpRulesService.applyTuning(
                WarpRulesService.parse("{\"permanent.milestones\": {\"vftv:x\": 1}}"), true);
        assertSame(before, WarpRulesService.current());
        WarpRulesService.applyTuning(DataResult.error(() -> "unreadable"), true);
        assertSame(before, WarpRulesService.current());
    }

    @Test
    void theSwitchesReachTheRules() {
        WarpRulesService.installConfig(new WarpConfig(false, false, true, false));
        WarpRules r = WarpRulesService.current();
        assertFalse(r.voicesEnabled());
        assertFalse(r.tax());
        assertTrue(r.amplifier());
        assertFalse(r.gear());
        assertEquals(WarpTuning.DEFAULTS.grace(), r.grace(), "tuning kept");
    }

    @Test
    void theClientCopyIsAbsentUntilReceivedAndAfterDisconnect() {
        assertNull(WarpRulesService.client());
        WarpRulesService.receive(WarpTuning.DEFAULTS);
        assertSame(WarpTuning.DEFAULTS, WarpRulesService.client());
        WarpRulesService.clearClient();
        assertNull(WarpRulesService.client());
    }

    @Test
    void theCodecsRoundTrip() {
        WarpRules off = WarpTuning.withSwitches(WarpTuning.DEFAULTS, true, false, true, false);
        assertEquals(
                off,
                WarpRulesPayload.RULES_CODEC
                        .parse(
                                JsonOps.INSTANCE,
                                WarpRulesPayload.RULES_CODEC
                                        .encodeStart(JsonOps.INSTANCE, off)
                                        .getOrThrow())
                        .getOrThrow());
        ByteBuf buf = Unpooled.buffer();
        WarpRulesPayload.STREAM_CODEC.encode(buf, new WarpRulesPayload(off));
        assertEquals(off, WarpRulesPayload.STREAM_CODEC.decode(buf).rules());
        assertTrue(
                WarpRulesPayload.RULES_CODEC
                        .parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("[]"))
                        .isError());
    }

    /** 16 dimensions and 16 milestones, each id 256 characters long, still fit the wire bound. */
    @Test
    void theLargestRecordFitsTheWire() {
        var json = WarpTuning.toJson(WarpTuning.DEFAULTS, true);
        var dimensions = new com.google.gson.JsonArray();
        var milestones = new com.google.gson.JsonObject();
        for (int i = 0; i < 16; i++) {
            String id = "vftv:" + (char) ('a' + i) + "x".repeat(250);
            dimensions.add(id);
            milestones.addProperty(i == 0 ? WarpTuning.JOINING : id, 1);
        }
        json.add("context.dimensions", dimensions);
        json.add("permanent.milestones", milestones);
        json.addProperty("permanent.cap", 100);
        WarpRules big = WarpTuning.fromJson(json, true);
        ByteBuf buf = Unpooled.buffer();
        WarpRulesPayload.STREAM_CODEC.encode(buf, new WarpRulesPayload(big));
        assertEquals(big, WarpRulesPayload.STREAM_CODEC.decode(buf).rules());
    }

    @Test
    void aHostilePayloadIsRejectedAtDecode() {
        for (String text :
                new String[] {"{}", "not json", "[]", "{\"format\": 2}", tamperedGrace()}) {
            ByteBuf buf = Unpooled.buffer();
            ByteBufCodecs.stringUtf8(WarpRulesPayload.MAX_CHARS).encode(buf, text);
            assertThrows(
                    DecoderException.class, () -> WarpRulesPayload.STREAM_CODEC.decode(buf), text);
        }
        ByteBuf huge = Unpooled.buffer();
        ByteBufCodecs.stringUtf8(1 << 16).encode(huge, "x".repeat(WarpRulesPayload.MAX_CHARS + 1));
        assertThrows(RuntimeException.class, () -> WarpRulesPayload.STREAM_CODEC.decode(huge));
    }

    private static String tamperedGrace() {
        var json = WarpTuning.toJson(WarpTuning.DEFAULTS, true);
        json.addProperty("grace", 99_999);
        return json.toString();
    }
}
