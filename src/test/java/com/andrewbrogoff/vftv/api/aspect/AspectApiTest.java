package com.andrewbrogoff.vftv.api.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;

/** The aspect API: payload immutability, the scan keys' text and the {@link AspectValues} codec. */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class AspectApiTest {
    private static final Identifier OAK_LOG = Identifier.withDefaultNamespace("oak_log");
    private static final ResourceKey<Item> ITEM = ResourceKey.create(Registries.ITEM, OAK_LOG);
    private static final ResourceKey<Block> BLOCK = ResourceKey.create(Registries.BLOCK, OAK_LOG);
    private static final ResourceKey<EntityType<?>> COW =
            ResourceKey.create(Registries.ENTITY_TYPE, Identifier.withDefaultNamespace("cow"));

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static boolean rejects(String text) {
        return AspectValues.CODEC.parse(JsonOps.INSTANCE, json(text)).error().isPresent();
    }

    private static AspectValues values(Map<ResourceKey<Aspect>, Integer> aspects) {
        return new AspectValues(new TreeMap<>(aspects));
    }

    @Test
    void aspectValuesCodecRoundTrips() {
        SequencedMap<ResourceKey<Aspect>, Integer> in = new LinkedHashMap<>();
        in.put(AspectKeys.METALLUM, 2);
        in.put(AspectKeys.IGNIS, 3);
        AspectValues value = new AspectValues(in);
        JsonElement encoded = AspectValues.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, AspectValues.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());

        AspectValues parsed =
                AspectValues.CODEC
                        .parse(JsonOps.INSTANCE, json("{\"vftv:metallum\": 2, \"vftv:ignis\": 3}"))
                        .getOrThrow();
        assertEquals(value, parsed);
        assertEquals(
                List.of(AspectKeys.IGNIS, AspectKeys.METALLUM),
                List.copyOf(parsed.aspects().keySet()));

        AspectValues empty = AspectValues.CODEC.parse(JsonOps.INSTANCE, json("{}")).getOrThrow();
        assertTrue(empty.aspects().isEmpty());
    }

    @Test
    void aspectValuesCodecRejectsInvalidValues() {
        assertTrue(rejects("{\"vftv:ignis\": 0}"), "zero");
        assertTrue(rejects("{\"vftv:ignis\": -1}"), "negative");
        assertTrue(
                rejects(
                        "{\"vftv:aer\": 1, \"vftv:terra\": 1, \"vftv:ignis\": 1, \"vftv:aqua\": 1,"
                                + " \"vftv:ordo\": 1, \"vftv:perditio\": 1}"),
                "six aspects");
        assertTrue(rejects("{\"vftv:ignis\": \"three\"}"), "not a number");
        assertTrue(rejects("{\"Not An Id\": 1}"), "not an id");
        assertTrue(rejects("[\"vftv:ignis\"]"), "an array");
        assertTrue(rejects("3"), "a number");
    }

    @Test
    void aspectValuesConstructorRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> values(Map.of(AspectKeys.IGNIS, 0)));
        assertThrows(IllegalArgumentException.class, () -> values(Map.of(AspectKeys.IGNIS, -2)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        values(
                                Map.of(
                                        AspectKeys.AER, 1,
                                        AspectKeys.TERRA, 1,
                                        AspectKeys.IGNIS, 1,
                                        AspectKeys.AQUA, 1,
                                        AspectKeys.ORDO, 1,
                                        AspectKeys.PERDITIO, 1)));
    }

    @Test
    void aspectValuesIsImmutable() {
        SequencedMap<ResourceKey<Aspect>, Integer> in = new TreeMap<>(Map.of(AspectKeys.IGNIS, 3));
        AspectValues value = new AspectValues(in);
        in.put(AspectKeys.AER, 1);
        assertEquals(Map.of(AspectKeys.IGNIS, 3), value.aspects());
        assertThrows(
                UnsupportedOperationException.class, () -> value.aspects().put(AspectKeys.AER, 1));
    }

    @Test
    void hiddenPrimalIsImmutable() {
        List<ResourceKey<Aspect>> in =
                new ArrayList<>(List.of(AspectKeys.AER, AspectKeys.PERDITIO));
        HiddenPrimal primal =
                new HiddenPrimal(in, Identifier.fromNamespaceAndPath("vftv", "reveal"));
        in.clear();
        assertEquals(List.of(AspectKeys.AER, AspectKeys.PERDITIO), primal.taughtParents());
        assertThrows(UnsupportedOperationException.class, () -> primal.taughtParents().clear());
    }

    @Test
    void scanKeysHaveTheirCanonicalText() {
        assertEquals("item:minecraft:oak_log", new ScanKey.Item(ITEM).toString());
        assertEquals("block:minecraft:oak_log", new ScanKey.Block(BLOCK).toString());
        assertEquals("entity:minecraft:cow", new ScanKey.Entity(COW).toString());
        assertEquals("node:42", new ScanKey.Node(42L).toString());
    }

    @Test
    void scanKeyItemIsImmutable() {
        assertEquals(ITEM, new ScanKey.Item(ITEM).item());
    }

    @Test
    void scanKeyBlockIsImmutable() {
        assertEquals(BLOCK, new ScanKey.Block(BLOCK).block());
    }

    @Test
    void scanKeyEntityIsImmutable() {
        assertEquals(COW, new ScanKey.Entity(COW).entity());
    }

    @Test
    void scanKeyNodeIsImmutable() {
        assertEquals(7L, new ScanKey.Node(7L).id());
    }

    @Test
    void thingScannedPayloadIsImmutable() {
        SequencedMap<ResourceKey<Aspect>, Integer> points = new LinkedHashMap<>();
        points.put(AspectKeys.LUX, 0);
        points.put(AspectKeys.AER, 2);
        ThingScannedPayload payload =
                new ThingScannedPayload(
                        null,
                        new ScanKey.Item(ITEM),
                        ScanTargetKind.BLOCK,
                        ScanOutcome.FIRST,
                        false,
                        points);
        points.put(AspectKeys.IGNIS, 5);
        points.put(AspectKeys.AER, 9);
        assertEquals(
                List.of(AspectKeys.AER, AspectKeys.LUX), List.copyOf(payload.points().keySet()));
        assertEquals(2, payload.points().get(AspectKeys.AER));
        assertEquals(0, payload.points().get(AspectKeys.LUX));
        assertThrows(
                UnsupportedOperationException.class,
                () -> payload.points().put(AspectKeys.IGNIS, 1));
        assertThrows(UnsupportedOperationException.class, () -> payload.points().clear());
    }

    @Test
    void aspectDiscoveredPayloadIsImmutable() {
        AspectDiscoveredPayload payload = new AspectDiscoveredPayload(null, AspectKeys.VACUOS, 4);
        assertEquals(AspectKeys.VACUOS, payload.aspect());
        assertEquals(4, payload.bonus());
    }
}
