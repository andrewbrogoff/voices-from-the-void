package com.andrewbrogoff.vftv.core.arcane;

import com.andrewbrogoff.vftv.core.vis.VisService.SlotChange;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Planning a craft's effect (arcane spec, "Planning"): the wands spec's prepared slot changes, one
 * per consumed grid slot and one for the output, each {@code expectedBefore} the live content.
 */
public final class WorktablePlan {
    private WorktablePlan() {}

    /**
     * Every occupied grid slot loses one item. A slot whose stack has a crafting remainder takes
     * the remainder's created stack only if it held exactly one.
     *
     * @return the changes, or null for {@code LEFTOVER}: a remainder in a slot holding more than
     *     one, or a remainder that creates an empty stack or one over its maximum
     */
    public static @Nullable List<SlotChange> grid(Container grid) {
        List<SlotChange> changes = new ArrayList<>(grid.getContainerSize());
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack stack = grid.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStackTemplate remainder = stack.getCraftingRemainder();
            ItemStack after;
            if (remainder == null) {
                after = stack.copyWithCount(stack.getCount() - 1);
            } else {
                after = remainder.create();
                if (stack.getCount() != 1
                        || after.isEmpty()
                        || after.getCount() > after.getMaxStackSize()) {
                    return null;
                }
            }
            changes.add(new SlotChange(grid, i, stack, after));
        }
        return changes;
    }

    /**
     * The output slot: empty takes the result; the same item and components with room merge.
     *
     * @return the change, or null for {@code OUTPUT_FULL}
     */
    public static @Nullable SlotChange output(Container output, ItemStack result) {
        ItemStack held = output.getItem(0);
        if (held.isEmpty()) {
            return new SlotChange(output, 0, ItemStack.EMPTY, result);
        }
        int sum = held.getCount() + result.getCount();
        return ItemStack.isSameItemSameComponents(held, result) && sum <= held.getMaxStackSize()
                ? new SlotChange(output, 0, held, held.copyWithCount(sum))
                : null;
    }
}
