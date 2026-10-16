package com.andrewbrogoff.vftv.api.crucible;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pins the immutability of each crucible payload variant (API spec, "Immutability"). */
// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class CrucibleApiTest {
    @BeforeAll
    static void bindItemComponents() {
        TestItems.bindComponents();
    }

    private static final ResourceKey<Registry<Aspect>> ASPECTS =
            ResourceKey.createRegistryKey(id("aspect"));
    private static final ResourceKey<Aspect> AQUA = ResourceKey.create(ASPECTS, id("aqua"));
    private static final ResourceKey<Aspect> TERRA = ResourceKey.create(ASPECTS, id("terra"));
    private static final ResourceKey<Aspect> IGNIS = ResourceKey.create(ASPECTS, id("ignis"));

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vftv", path);
    }

    private static SequencedMap<ResourceKey<Aspect>, Integer> aspects() {
        return new LinkedHashMap<>(Map.of(TERRA, 4, AQUA, 2));
    }

    /** Asserts the map kept its construction-time contents, in ascending id, and is read-only. */
    private static void assertFrozen(Map<ResourceKey<Aspect>, Integer> map) {
        assertEquals(List.of(AQUA, TERRA), List.copyOf(map.keySet()));
        assertEquals(List.of(2, 4), List.copyOf(map.values()));
        assertThrows(UnsupportedOperationException.class, () -> map.put(IGNIS, 1));
        assertThrows(UnsupportedOperationException.class, () -> map.remove(AQUA));
    }

    /** Asserts each call returns a distinct copy, and mutating one changes nothing else. */
    private static void assertCopiedOnAccess(Supplier<ItemStack> accessor, int count) {
        ItemStack first = accessor.get();
        ItemStack second = accessor.get();
        assertNotSame(first, second);
        first.setCount(count + 7);
        assertEquals(count, second.getCount());
        assertEquals(count, accessor.get().getCount());
    }

    @Test
    void dissolvedIsImmutable() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 64, 1);
        ItemStack item = new ItemStack(Items.CLAY_BALL, 3);
        SequencedMap<ResourceKey<Aspect>, Integer> added = aspects();
        CruciblePayload.Dissolved d = new CruciblePayload.Dissolved(null, pos, null, item, added);

        pos.set(0, 0, 0);
        item.setCount(9);
        added.put(IGNIS, 5);

        assertEquals(new BlockPos(1, 64, 1), d.pos());
        assertEquals(3, d.item().getCount());
        assertCopiedOnAccess(d::item, 3);
        assertFrozen(d.aspects());
    }

    @Test
    void craftedIsImmutable() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(2, 64, 2);
        ItemStack catalyst = new ItemStack(Items.COPPER_INGOT, 1);
        ItemStack result = new ItemStack(Items.GOLD_INGOT, 2);
        SequencedMap<ResourceKey<Aspect>, Integer> spent = aspects();
        CruciblePayload.Crafted c =
                new CruciblePayload.Crafted(
                        null,
                        pos,
                        null,
                        ResourceKey.create(Registries.RECIPE, id("resonant_brass_ingot")),
                        catalyst,
                        result,
                        spent);

        pos.set(0, 0, 0);
        catalyst.setCount(5);
        result.setCount(5);
        spent.clear();

        assertEquals(new BlockPos(2, 64, 2), c.pos());
        assertCopiedOnAccess(c::catalyst, 1);
        assertCopiedOnAccess(c::result, 2);
        assertFrozen(c.aspects());
    }

    @Test
    void spilledIsImmutable() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(3, 64, 3);
        SequencedMap<ResourceKey<Aspect>, Integer> spilled = aspects();
        CruciblePayload.Spilled s =
                new CruciblePayload.Spilled(null, pos, null, spilled, SpillCause.REMOVED);

        pos.set(0, 0, 0);
        spilled.put(IGNIS, 5);

        assertEquals(new BlockPos(3, 64, 3), s.pos());
        assertEquals(SpillCause.REMOVED, s.cause());
        assertFrozen(s.aspects());
    }
}
