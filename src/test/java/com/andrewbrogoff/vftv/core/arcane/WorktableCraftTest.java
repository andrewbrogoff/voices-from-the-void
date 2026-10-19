package com.andrewbrogoff.vftv.core.arcane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.core.vis.VisService;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** The {@link WorktableCraft} payload codec: the round trip, every bound and the key rules. */
class WorktableCraftTest {
    private static final Identifier BELL = Identifier.fromNamespaceAndPath("vftv", "bell");

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.NEOFORGE);
    }

    private static SortedMap<ResourceKey<Aspect>, Integer> cost(int aer, int ordo) {
        return new TreeMap<>(Map.of(AspectKeys.AER, aer, AspectKeys.ORDO, ordo));
    }

    private static WorktableCraft payload() {
        return new WorktableCraft(
                3,
                0x12345678,
                BELL,
                new WorktableCraft.Echo(cost(8, 6), cost(10, 8), 1200, 5, false, true, false),
                true);
    }

    @Test
    void roundTrips() {
        RegistryFriendlyByteBuf buf = buffer();
        WorktableCraft.STREAM_CODEC.encode(buf, payload());
        assertEquals(payload(), WorktableCraft.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void theEchoIsTheQuotesCost() {
        WorktableCraft.Echo echo = payload().echo();
        assertTrue(VisService.sameCost(echo.quote(), WorktableCraft.Echo.of(echo.quote()).quote()));
    }

    /** A wire with the given fields; the defaults are a valid payload. */
    private static RegistryFriendlyByteBuf wire(
            int containerId,
            String recipe,
            Consumer<RegistryFriendlyByteBuf> base,
            Consumer<RegistryFriendlyByteBuf> effective,
            int m,
            int spill) {
        RegistryFriendlyByteBuf buf = buffer();
        buf.writeVarInt(containerId);
        buf.writeInt(7);
        buf.writeUtf(recipe);
        base.accept(buf);
        effective.accept(buf);
        buf.writeVarInt(m);
        buf.writeVarInt(spill);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        return buf;
    }

    private static Consumer<RegistryFriendlyByteBuf> entries(Map<String, Integer> m) {
        return buf -> {
            buf.writeVarInt(m.size());
            m.forEach(
                    (k, v) -> {
                        buf.writeUtf(k);
                        buf.writeVarInt(v);
                    });
        };
    }

    private static Consumer<RegistryFriendlyByteBuf> raw(int size, String key, int value) {
        return buf -> {
            buf.writeVarInt(size);
            for (int i = 0; i < size && i < 2; i++) {
                buf.writeUtf(key);
                buf.writeVarInt(value);
            }
        };
    }

    private static final Consumer<RegistryFriendlyByteBuf> AER = entries(Map.of("vftv:aer", 8));

    private static void rejects(RegistryFriendlyByteBuf buf, String why) {
        DecoderException e =
                assertThrows(DecoderException.class, () -> WorktableCraft.STREAM_CODEC.decode(buf));
        assertTrue(String.valueOf(e.getMessage()).contains(why), e.getMessage());
    }

    @Test
    void validWirePasses() {
        WorktableCraft p =
                WorktableCraft.STREAM_CODEC.decode(wire(0, "vftv:bell", AER, AER, 0, 1_000_000));
        assertEquals(1_000_000, p.echo().spill());
        assertEquals(0, p.echo().m());
    }

    @Test
    void everyBound() {
        rejects(wire(-1, "vftv:bell", AER, AER, 1000, 0), "container id");
        rejects(wire(0, "Not An Id", AER, AER, 1000, 0), "not an id");
        rejects(wire(0, "vftv:" + "a".repeat(300), AER, AER, 1000, 0), "");
        rejects(wire(0, "vftv:bell", entries(Map.of()), AER, 1000, 0), "base entries");
        rejects(wire(0, "vftv:bell", entries(Map.of("vftv:aer", 1001)), AER, 1000, 0), "base cost");
        rejects(wire(0, "vftv:bell", entries(Map.of("vftv:aer", 0)), AER, 1000, 0), "base cost");
        rejects(
                wire(0, "vftv:bell", AER, entries(Map.of("vftv:aer", 10_001)), 1000, 0),
                "effective cost");
        rejects(wire(0, "vftv:bell", AER, AER, 10_001, 0), "multiplier");
        rejects(wire(0, "vftv:bell", AER, AER, -1, 0), "multiplier");
        rejects(wire(0, "vftv:bell", AER, AER, 1000, 1_000_001), "spill");
        rejects(wire(0, "vftv:bell", AER, AER, 1000, -1), "spill");
    }

    @Test
    void sizesAreCheckedBeforeAllocation() {
        // A huge declared size is refused at once, before any entry is read or allocated.
        rejects(
                wire(0, "vftv:bell", raw(Integer.MAX_VALUE, "vftv:aer", 1), AER, 1000, 0),
                "entries");
        rejects(wire(0, "vftv:bell", raw(9, "vftv:aer", 1), AER, 1000, 0), "entries");
    }

    @Test
    void duplicateAndMismatchedKeys() {
        rejects(wire(0, "vftv:bell", raw(2, "vftv:aer", 1), AER, 1000, 0), "duplicate");
        rejects(
                wire(0, "vftv:bell", AER, entries(Map.of("vftv:ordo", 8)), 1000, 0),
                "differ from the base");
        rejects(
                wire(0, "vftv:bell", AER, entries(Map.of("vftv:aer", 8, "vftv:ordo", 8)), 1000, 0),
                "differ from the base");
    }
}
