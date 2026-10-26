package com.andrewbrogoff.vftv.core.arcane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.andrewbrogoff.vftv.api.TestItems;
import com.andrewbrogoff.vftv.core.vis.VisService.SlotChange;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Planning (arcane spec, "Planning"): consumed slots, remainders and the output. */
class WorktablePlanTest {
    @BeforeAll
    static void bind() {
        TestItems.bindComponents();
    }

    @Test
    void everyOccupiedSlotLosesOne() {
        SimpleContainer grid = new SimpleContainer(9);
        grid.setItem(0, new ItemStack(Items.STICK, 3));
        grid.setItem(4, new ItemStack(Items.COPPER_INGOT));
        List<SlotChange> plan = WorktablePlan.grid(grid);
        assertNotNull(plan);
        assertEquals(2, plan.size());
        assertEquals(0, plan.get(0).slot());
        assertEquals(2, plan.get(0).after().getCount());
        assertTrue(plan.get(1).after().isEmpty());
        assertTrue(ItemStack.matches(grid.getItem(4), plan.get(1).expectedBefore()));
    }

    @Test
    void aRemainderReplacesASlotHoldingOne() {
        SimpleContainer grid = new SimpleContainer(9);
        grid.setItem(2, new ItemStack(Items.WATER_BUCKET));
        List<SlotChange> plan = WorktablePlan.grid(grid);
        assertNotNull(plan);
        assertTrue(plan.getFirst().after().is(Items.BUCKET));
    }

    @Test
    void aRemainderInASlotHoldingMoreIsLeftOver() {
        SimpleContainer grid = new SimpleContainer(9);
        grid.setItem(2, new ItemStack(Items.WATER_BUCKET, 2));
        assertNull(WorktablePlan.grid(grid));
    }

    @Test
    void theOutputSlot() {
        SimpleContainer output = new SimpleContainer(1);
        ItemStack bell = new ItemStack(Items.BELL);
        SlotChange empty = WorktablePlan.output(output, bell);
        assertNotNull(empty);
        assertTrue(empty.expectedBefore().isEmpty() && empty.after().is(Items.BELL));

        output.setItem(0, new ItemStack(Items.BELL, 10));
        SlotChange merged = WorktablePlan.output(output, bell);
        assertNotNull(merged);
        assertEquals(11, merged.after().getCount());

        output.setItem(0, new ItemStack(Items.BELL, 64));
        assertNull(WorktablePlan.output(output, bell), "a merge over the maximum");

        output.setItem(0, new ItemStack(Items.STICK));
        assertNull(WorktablePlan.output(output, bell), "another item");

        ItemStack named = new ItemStack(Items.BELL);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Gong"));
        output.setItem(0, named);
        assertNull(WorktablePlan.output(output, bell), "mismatched components");
    }
}
