package com.andrewbrogoff.vftv.api.essentia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class EssentiaApiTest {
    private static final ResourceKey<Registry<Aspect>> ASPECTS =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("vftv", "aspect"));
    private static final ResourceKey<Aspect> AER = aspect("aer");
    private static final ResourceKey<Aspect> TERRA = aspect("terra");

    private static ResourceKey<Aspect> aspect(String path) {
        return ResourceKey.create(ASPECTS, Identifier.fromNamespaceAndPath("vftv", path));
    }

    private static Holder<Aspect> standAlone(ResourceKey<Aspect> key) {
        return Holder.Reference.createStandAlone(new HolderOwner<>() {}, key);
    }

    @Test
    void spilledCopiesItsInputs() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        SequencedMap<ResourceKey<Aspect>, Integer> aspects = new LinkedHashMap<>();
        aspects.put(TERRA, 3);
        aspects.put(AER, 5);
        var spilled = new EssentiaPayload.Spilled(null, pos, aspects, SpillCause.DESTROYED);

        pos.set(9, 9, 9);
        aspects.put(TERRA, 99);
        aspects.clear();

        assertEquals(new BlockPos(1, 2, 3), spilled.pos());
        assertEquals(List.of(AER, TERRA), List.copyOf(spilled.aspects().keySet()));
        assertEquals(Map.of(AER, 5, TERRA, 3), spilled.aspects());
        assertThrows(UnsupportedOperationException.class, () -> spilled.aspects().put(AER, 1));
        assertThrows(UnsupportedOperationException.class, () -> spilled.aspects().clear());
        assertEquals(SpillCause.DESTROYED, spilled.cause());
    }

    @Test
    void emptyResourceIsTheConstant() {
        EssentiaResource fresh = new EssentiaResource(null);
        assertTrue(EssentiaResource.EMPTY.isEmpty());
        assertTrue(fresh.isEmpty());
        assertEquals(EssentiaResource.EMPTY, fresh);
        assertEquals(EssentiaResource.EMPTY.hashCode(), fresh.hashCode());
        assertNotEquals(EssentiaResource.EMPTY, new EssentiaResource(standAlone(AER)));
    }

    @Test
    void unboundDirectHolderIsRejected() {
        Holder<Aspect> unbound = Holder.direct((Aspect) null);
        assertThrows(IllegalArgumentException.class, () -> new EssentiaResource(unbound));
    }

    @Test
    void equalityComparesTheKeyOnly() {
        EssentiaResource first = new EssentiaResource(standAlone(AER));
        EssentiaResource second = new EssentiaResource(standAlone(AER));
        assertFalse(first.isEmpty());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new EssentiaResource(standAlone(TERRA)));
    }
}
