package com.andrewbrogoff.vftv.core.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.core.aspect.client.DerivedAspectsClient;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DerivedAspectsSyncTest {
    private static RegistryFriendlyByteBuf buf() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.NEOFORGE);
    }

    private static AspectValues values(int n) {
        return new AspectValues(new TreeMap<>(Map.of(AspectKeys.TERRA, n, AspectKeys.ORDO, 1)));
    }

    @AfterEach
    void clear() {
        DerivedAspectsClient.clear();
    }

    @Test
    void roundTrips() {
        var payload =
                new DerivedAspectsSync(
                        List.of(
                                new DerivedAspectsSync.Entry(Items.STONE, values(2)),
                                new DerivedAspectsSync.Entry(Items.DIRT, values(3))),
                        true);
        RegistryFriendlyByteBuf b = buf();
        DerivedAspectsSync.STREAM_CODEC.encode(b, payload);
        assertEquals(payload, DerivedAspectsSync.STREAM_CODEC.decode(b));
        assertEquals(0, b.readableBytes());
    }

    @Test
    void tenThousandEntriesSplitIntoThreePayloadsAndTheClientSwapsOnTheLast() {
        DerivedAspectsClient.clear();
        List<DerivedAspectsSync> payloads = DerivedAspects.payloads(Map.of());
        assertEquals(1, payloads.size(), "an empty table is one empty payload");
        assertTrue(payloads.getFirst().last());

        // Fewer than 10,000 items exist, so the entries repeat items; the split counts entries.
        List<Item> items = BuiltInRegistries.ITEM.stream().toList();
        List<DerivedAspectsSync.Entry> many = new java.util.ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            many.add(new DerivedAspectsSync.Entry(items.get(i % items.size()), values(1)));
        }
        List<DerivedAspectsSync> split = DerivedAspects.split(many);
        assertEquals(
                List.of(4096, 4096, 1808), split.stream().map(p -> p.entries().size()).toList());
        assertEquals(
                List.of(false, false, true), split.stream().map(DerivedAspectsSync::last).toList());
        DerivedAspectsClient.arrive(split.get(0));
        DerivedAspectsClient.arrive(split.get(1));
        assertTrue(AspectLookup.derived().isEmpty(), "nothing swaps before the last slice");
        DerivedAspectsClient.arrive(split.get(2));
        assertFalse(AspectLookup.derived().isEmpty());
        DerivedAspectsClient.clear();
        assertTrue(AspectLookup.derived().isEmpty(), "logout clears the copy");
    }

    @Test
    void payloadsAreInAscendingItemIdWithTheLastFlagged() {
        Map<Item, AspectValues> table = new LinkedHashMap<>();
        table.put(Items.STONE, values(2));
        table.put(Items.ACACIA_LOG, values(3));
        List<DerivedAspectsSync> out = DerivedAspects.payloads(table);
        assertEquals(1, out.size());
        assertEquals(Items.ACACIA_LOG, out.getFirst().entries().getFirst().item());
        assertTrue(out.getFirst().last());
    }

    @Test
    void anEntryOverFiveAspectsIsRejectedAtDecode() {
        RegistryFriendlyByteBuf b = buf();
        b.writeVarInt(1);
        ByteBufCodecs.registry(Registries.ITEM).encode(b, Items.STONE);
        b.writeVarInt(6);
        List<ResourceKey<Aspect>> six =
                List.of(
                        AspectKeys.AER,
                        AspectKeys.TERRA,
                        AspectKeys.IGNIS,
                        AspectKeys.AQUA,
                        AspectKeys.ORDO,
                        AspectKeys.PERDITIO);
        for (ResourceKey<Aspect> a : six) {
            ResourceKey.streamCodec(AspectKeys.REGISTRY).encode(b, a);
            b.writeVarInt(1);
        }
        b.writeBoolean(true);
        assertThrows(DecoderException.class, () -> DerivedAspectsSync.STREAM_CODEC.decode(b));
    }

    @Test
    void aZeroAmountOrTooManyEntriesIsRejected() {
        RegistryFriendlyByteBuf zero = buf();
        zero.writeVarInt(1);
        ByteBufCodecs.registry(Registries.ITEM).encode(zero, Items.STONE);
        zero.writeVarInt(1);
        ResourceKey.streamCodec(AspectKeys.REGISTRY).encode(zero, AspectKeys.AER);
        zero.writeVarInt(0);
        zero.writeBoolean(true);
        assertThrows(DecoderException.class, () -> DerivedAspectsSync.STREAM_CODEC.decode(zero));

        RegistryFriendlyByteBuf many = buf();
        many.writeVarInt(DerivedAspectsSync.MAX + 1);
        assertThrows(DecoderException.class, () -> DerivedAspectsSync.STREAM_CODEC.decode(many));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DerivedAspectsSync(
                                java.util.Collections.nCopies(
                                        DerivedAspectsSync.MAX + 1,
                                        new DerivedAspectsSync.Entry(Items.STONE, values(1))),
                                true));
    }

    @Test
    void modelValuesConvertBothWays() {
        AspectValues v = values(4);
        assertEquals(v, DerivedAspects.values(DerivedAspects.text(v)));
        assertEquals(
                Map.of(Items.STONE, v),
                DerivedAspects.table(
                        new TreeMap<>(
                                Map.of(
                                        "minecraft:stone",
                                        DerivedAspects.text(v),
                                        "nosuchmod:thing",
                                        DerivedAspects.text(v)))));
        assertTrue(DerivedAspects.MODDED.test("somemod:x"));
        assertFalse(DerivedAspects.MODDED.test("minecraft:x"));
        assertFalse(DerivedAspects.MODDED.test("vftv:x"));
    }
}
