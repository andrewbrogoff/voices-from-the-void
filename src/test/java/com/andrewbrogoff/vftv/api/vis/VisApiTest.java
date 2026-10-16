package com.andrewbrogoff.vftv.api.vis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Pins the immutability of the vis payloads (API spec, "Immutability and access"). */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class VisApiTest {
    private static final ResourceKey<Registry<Aspect>> ASPECTS =
            ResourceKey.createRegistryKey(id("aspect"));
    private static final ResourceKey<Aspect> AER = ResourceKey.create(ASPECTS, id("aer"));
    private static final ResourceKey<Aspect> IGNIS = ResourceKey.create(ASPECTS, id("ignis"));
    private static final ResourceKey<Aspect> ORDO = ResourceKey.create(ASPECTS, id("ordo"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    private static VisQuote quote(
            SequencedMap<ResourceKey<Aspect>, Integer> base,
            SequencedMap<ResourceKey<Aspect>, Integer> effective,
            List<ResourceKey<Aspect>> shortAspects) {
        return new VisQuote(
                base,
                effective,
                850,
                0,
                850,
                0,
                1000,
                false,
                false,
                false,
                false,
                false,
                shortAspects);
    }

    @Test
    void visQuoteIsImmutableAndOrdered() {
        SequencedMap<ResourceKey<Aspect>, Integer> base =
                new LinkedHashMap<>(Map.of(ORDO, 1, AER, 10));
        SequencedMap<ResourceKey<Aspect>, Integer> effective =
                new LinkedHashMap<>(Map.of(ORDO, 1, AER, 9));
        List<ResourceKey<Aspect>> shortAspects = new ArrayList<>(List.of(ORDO, AER));
        VisQuote q = quote(base, effective, shortAspects);

        base.put(IGNIS, 3);
        effective.clear();
        shortAspects.add(IGNIS);

        assertEquals(List.of(AER, ORDO), List.copyOf(q.base().keySet()));
        assertEquals(List.of(10, 1), List.copyOf(q.base().values()));
        assertEquals(List.of(AER, ORDO), List.copyOf(q.effective().keySet()));
        assertEquals(List.of(AER, ORDO), q.shortAspects());
        assertThrows(UnsupportedOperationException.class, () -> q.base().put(IGNIS, 1));
        assertThrows(UnsupportedOperationException.class, () -> q.effective().remove(AER));
        assertThrows(UnsupportedOperationException.class, () -> q.shortAspects().add(IGNIS));
    }

    @Test
    void visSpentPayloadIsImmutable() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 64, -2);
        SequencedMap<ResourceKey<Aspect>, Integer> base = new LinkedHashMap<>(Map.of(AER, 10));
        VisContext context = new VisContext(id("arcane/test"), VisKind.ORDINARY, null);
        VisSpentPayload p =
                new VisSpentPayload(null, null, pos, context, quote(base, base, new ArrayList<>()));

        pos.set(0, 0, 0);
        base.put(IGNIS, 3);

        assertEquals(new BlockPos(1, 64, -2), p.pos());
        assertEquals(Map.of(AER, 10), p.quote().base());
        assertNull(p.context().research());
        assertThrows(UnsupportedOperationException.class, () -> p.quote().base().put(IGNIS, 1));
        assertThrows(UnsupportedOperationException.class, () -> p.quote().shortAspects().add(AER));
    }

    @Test
    void nodePayloadIsImmutable() {
        ResourceKey<Level> overworld = Level.OVERWORLD;
        GlobalPos where = GlobalPos.of(overworld, new BlockPos(5, 70, 5));
        NodePayload placed =
                new NodePayload(NodeEventKind.PLACED, new NodeRef(7L, where), NodeCause.PLAYER);
        NodePayload carried =
                new NodePayload(NodeEventKind.CAPTURED, new NodeRef(7L, null), NodeCause.PLAYER);

        assertEquals(7L, placed.node().id());
        assertEquals(where, placed.node().where());
        assertNull(carried.node().where());
    }
}
