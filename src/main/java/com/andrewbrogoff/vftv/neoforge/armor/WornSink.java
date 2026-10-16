package com.andrewbrogoff.vftv.neoforge.armor;

import net.minecraft.world.item.ItemStack;

/** Receives one {@link WornSlots} entry at a time (compatibility, "Wiring the collector"). */
@FunctionalInterface
public interface WornSink {
    /** Takes one entry and returns whether the collector should continue. */
    boolean accept(String slotId, int index, boolean cosmetic, ItemStack stack);
}
