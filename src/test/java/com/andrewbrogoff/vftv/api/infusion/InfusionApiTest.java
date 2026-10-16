package com.andrewbrogoff.vftv.api.infusion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.SpillCause;
import com.andrewbrogoff.vftv.api.vis.VisKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// No server exists in a unit test, so live handles (players, levels) are passed as null.
@SuppressWarnings("NullAway")
class InfusionApiTest {
    @BeforeAll
    static void bindItemComponents() {
        TestItems.bindComponents();
    }

    private static final ResourceKey<Registry<Aspect>> ASPECTS =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("vftv", "aspect"));
    private static final ResourceKey<Aspect> AER = aspect("aer");
    private static final ResourceKey<Aspect> TERRA = aspect("terra");
    private static final ResourceKey<Recipe<?>> RECIPE =
            ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("vftv", "test"));
    private static final UUID STARTER = new UUID(1, 2);

    private static ResourceKey<Aspect> aspect(String path) {
        return ResourceKey.create(ASPECTS, Identifier.fromNamespaceAndPath("vftv", path));
    }

    /** Distinct copies per call; mutating one changes neither the other nor a later call. */
    private static void assertCopiedOnAccess(Supplier<ItemStack> accessor, ItemStack expected) {
        ItemStack first = accessor.get();
        ItemStack second = accessor.get();
        assertNotSame(first, second);
        first.setCount(9);
        assertTrue(ItemStack.matches(expected, second));
        assertTrue(ItemStack.matches(expected, accessor.get()));
    }

    private static InfusionPlan plan(ItemStack output, List<InfusionCost> costs) {
        return new InfusionPlan(
                RECIPE,
                VisKind.ORDINARY,
                output,
                20,
                0,
                0,
                0,
                2,
                6,
                8,
                86,
                21,
                19,
                49,
                349,
                353,
                668,
                6,
                50,
                300L,
                costs,
                false,
                false);
    }

    @Test
    void startedCopiesItsInputs() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        InfusionPlan plan = plan(new ItemStack(Items.DIAMOND), List.of());
        var started = new InfusionPayload.Started(null, pos, 7L, 1, STARTER, RECIPE, plan);

        pos.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), started.pos());
        assertEquals(7L, started.bowlId());
        assertEquals(1, started.seq());
        assertEquals(STARTER, started.starter());
        assertEquals(RECIPE, started.recipe());
        assertSame(plan, started.plan());
    }

    @Test
    void planCopiesItsInputs() {
        ItemStack output = new ItemStack(Items.DIAMOND);
        List<InfusionCost> costs = new ArrayList<>();
        costs.add(new InfusionCost(AER, 12, 3));
        costs.add(new InfusionCost(TERRA, 20, 0));
        InfusionPlan plan = plan(output, costs);

        output.setCount(5);
        costs.clear();

        assertEquals(1, plan.output().getCount());
        assertCopiedOnAccess(plan::output, new ItemStack(Items.DIAMOND));
        assertEquals(
                List.of(new InfusionCost(AER, 12, 3), new InfusionCost(TERRA, 20, 0)),
                plan.costs());
        assertThrows(UnsupportedOperationException.class, () -> plan.costs().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> plan.costs().add(new InfusionCost(AER, 1, 0)));
        assertEquals(300L, plan.upsetFluxBound());
    }

    @Test
    void costHoldsItsValues() {
        InfusionCost cost = new InfusionCost(AER, 12, 99_999);
        assertEquals(AER, cost.aspect());
        assertEquals(12, cost.cost());
        assertEquals(99_999, cost.heldNow());
    }

    @Test
    void upsetCopiesItsInputs() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        UpsetEffect effect = new UpsetEffect.None();
        var upset = new InfusionPayload.Upset(null, pos, 4, effect);

        pos.set(9, 9, 9);

        assertEquals(new BlockPos(1, 2, 3), upset.pos());
        assertEquals(4, upset.seq());
        assertSame(effect, upset.effect());
    }

    @Test
    void joltCopiesItsInputs() {
        BlockPos.MutableBlockPos pedestal = new BlockPos.MutableBlockPos(2, 0, 2);
        ItemStack item = new ItemStack(Items.STONE);
        var jolt = new UpsetEffect.Jolt(pedestal, item);

        pedestal.set(9, 9, 9);
        item.setCount(5);

        assertEquals(new BlockPos(2, 0, 2), jolt.pedestal());
        assertCopiedOnAccess(jolt::item, new ItemStack(Items.STONE));
    }

    @Test
    void spillHoldsItsValues() {
        var spill = new UpsetEffect.Spill(TERRA, 50);
        assertEquals(TERRA, spill.aspect());
        assertEquals(50, spill.amount());
    }

    @Test
    void noneHasNoValues() {
        assertEquals(new UpsetEffect.None(), new UpsetEffect.None());
    }

    @Test
    void spilledCopiesItsInputs() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        SequencedMap<ResourceKey<Aspect>, Integer> aspects = new LinkedHashMap<>();
        aspects.put(TERRA, 3);
        aspects.put(AER, 5);
        var spilled = new InfusionPayload.Spilled(null, pos, aspects, SpillCause.UPSET);

        pos.set(9, 9, 9);
        aspects.clear();

        assertEquals(new BlockPos(1, 2, 3), spilled.pos());
        assertEquals(List.of(AER, TERRA), List.copyOf(spilled.aspects().keySet()));
        assertEquals(Map.of(AER, 5, TERRA, 3), spilled.aspects());
        assertThrows(UnsupportedOperationException.class, () -> spilled.aspects().put(AER, 1));
        assertEquals(SpillCause.UPSET, spilled.cause());
    }

    @Test
    void completedCopiesItsInputs() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        ItemStack output = new ItemStack(Items.DIAMOND);
        var completed = new InfusionPayload.Completed(null, pos, 2, STARTER, RECIPE, output);

        pos.set(9, 9, 9);
        output.setCount(5);

        assertEquals(new BlockPos(1, 2, 3), completed.pos());
        assertEquals(2, completed.seq());
        assertEquals(STARTER, completed.starter());
        assertEquals(RECIPE, completed.recipe());
        assertCopiedOnAccess(completed::output, new ItemStack(Items.DIAMOND));
    }

    @Test
    void abandonedCopiesItsInputs() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(1, 2, 3);
        SequencedMap<ResourceKey<Aspect>, Integer> drawn = new LinkedHashMap<>();
        drawn.put(TERRA, 8);
        drawn.put(AER, 4);
        var abandoned =
                new InfusionPayload.Abandoned(null, pos, 3, AbandonCause.REMOVED, null, drawn);

        pos.set(9, 9, 9);
        drawn.clear();

        assertEquals(new BlockPos(1, 2, 3), abandoned.pos());
        assertEquals(3, abandoned.seq());
        assertEquals(AbandonCause.REMOVED, abandoned.cause());
        assertNull(abandoned.player());
        assertEquals(List.of(AER, TERRA), List.copyOf(abandoned.drawn().keySet()));
        assertEquals(Map.of(AER, 4, TERRA, 8), abandoned.drawn());
        assertThrows(UnsupportedOperationException.class, () -> abandoned.drawn().clear());
    }
}
