package com.andrewbrogoff.vftv.api.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.neoforge.warp.WarpDataMaps;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/** The warp API: payload immutability and the {@code vftv:gear_warp} value codec. */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class WarpApiTest {
    private static final Codec<Integer> GEAR = WarpDataMaps.GEAR_WARP.codec();

    private static boolean rejects(String text) {
        return GEAR.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).error().isPresent();
    }

    @Test
    void warpChangedPayloadIsImmutable() {
        WarpState before = new WarpState(0, 0, 0, 0, 0);
        WarpState after = new WarpState(0, 4, 0, 0, 4);
        WarpState delta = new WarpState(0, 4, 0, 0, 4);
        WarpSource source =
                new WarpSource(
                        WarpSourceKind.ENTRY,
                        Identifier.fromNamespaceAndPath("vftv", "silence_beneath"));
        WarpChangedPayload payload = new WarpChangedPayload(null, before, after, delta, source);
        assertEquals(before, payload.before());
        assertEquals(after, payload.after());
        assertEquals(delta, payload.delta());
        assertEquals(source, payload.source());
        assertEquals(4, payload.after().lasting());
        assertEquals(4, payload.delta().total());
    }

    @Test
    void gearWarpCodecRoundTrips() {
        for (int value : new int[] {1, 10}) {
            JsonElement encoded = GEAR.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
            assertEquals(value, GEAR.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
    }

    @Test
    void gearWarpCodecRejectsInvalidValues() {
        assertTrue(rejects("0"), "zero");
        assertTrue(rejects("11"), "above 10");
        assertTrue(rejects("-1"), "negative");
        assertTrue(rejects("\"three\""), "not a number");
        assertTrue(rejects("{}"), "an object");
        assertTrue(rejects("2.5"), "a fraction");
        assertTrue(rejects("4294967297"), "an int overflow");
    }
}
