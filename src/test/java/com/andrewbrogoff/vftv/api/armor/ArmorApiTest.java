package com.andrewbrogoff.vftv.api.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class ArmorApiTest {
    private static Wearable parse(String json) {
        return Wearable.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static void rejects(String json) {
        assertTrue(
                Wearable.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError(),
                json);
    }

    @Test
    void codecRoundTrips() {
        Wearable full = new Wearable(WearableSlot.CHEST, 8, 2.0, 0.25, 0.9, 20);
        JsonElement json = Wearable.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow();
        assertEquals(full, Wearable.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        Wearable ring = new Wearable(WearableSlot.OFFHAND, 0, 0, 0, 0.05, 2);
        json = Wearable.CODEC.encodeStart(JsonOps.INSTANCE, ring).getOrThrow();
        assertEquals(ring, Wearable.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void codecDefaultsOmittedNumbersToZero() {
        assertEquals(
                new Wearable(WearableSlot.HEAD, 3, 0, 0, 0, 0),
                parse("{\"slot\": \"head\", \"armor\": 3}"));
        assertEquals(
                new Wearable(WearableSlot.FEET, 20, 10.0, 1.0, 0.9, 20),
                parse(
                        "{\"slot\": \"feet\", \"armor\": 20, \"toughness\": 10.0,"
                                + " \"knockback_resistance\": 1.00, \"vis_discount\": 0.90,"
                                + " \"baffle\": 20}"));
    }

    @Test
    void codecRejectsBadStructure() {
        rejects("[]");
        rejects("{\"armor\": 3}");
        rejects("{\"slot\": \"body\", \"armor\": 3}");
        rejects("{\"slot\": \"head\", \"armor\": 3, \"extra\": 1}");
        rejects("{\"slot\": \"head\", \"armor\": \"three\"}");
        rejects("{\"slot\": \"head\"}");
        rejects("{\"slot\": \"head\", \"armor\": 0, \"baffle\": 0}");
    }

    @Test
    void codecRejectsOutOfRange() {
        rejects("{\"slot\": \"head\", \"armor\": -1}");
        rejects("{\"slot\": \"head\", \"armor\": 21}");
        rejects("{\"slot\": \"head\", \"toughness\": 10.1}");
        rejects("{\"slot\": \"head\", \"toughness\": -0.5}");
        rejects("{\"slot\": \"head\", \"knockback_resistance\": 1.01}");
        rejects("{\"slot\": \"head\", \"vis_discount\": 0.91}");
        rejects("{\"slot\": \"head\", \"baffle\": 21}");
        rejects("{\"slot\": \"head\", \"baffle\": -1}");
    }

    @Test
    void codecRejectsExtraDecimalPlaces() {
        rejects("{\"slot\": \"head\", \"toughness\": 1.25}");
        rejects("{\"slot\": \"head\", \"knockback_resistance\": 0.125}");
        rejects("{\"slot\": \"head\", \"vis_discount\": 0.055}");
    }

    @Test
    void constructorRejectsWhatTheCodecRejects() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Wearable(WearableSlot.HEAD, 0, 0, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Wearable(WearableSlot.HEAD, 21, 0, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Wearable(WearableSlot.HEAD, 1, Double.NaN, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Wearable(WearableSlot.HEAD, 1, 0, 0, 0.95, 0));
        assertThrows(NullPointerException.class, () -> new Wearable(null, 1, 0, 0, 0, 0));
    }

    @Test
    void slotsSerializeLowercase() {
        for (WearableSlot slot : WearableSlot.values()) {
            assertEquals(slot.name().toLowerCase(java.util.Locale.ROOT), slot.getSerializedName());
        }
    }
}
