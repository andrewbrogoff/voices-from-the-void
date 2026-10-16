package com.andrewbrogoff.vftv.neoforge.armor;

import net.minecraft.world.entity.LivingEntity;

/**
 * The worn-slot collector beyond the vanilla slots, with no Curios type (compatibility, "Wiring the
 * collector"; armor, "The collector"). {@code CuriosCompat.init} returns Curios's; without Curios
 * the {@code @Mod} constructor passes {@link #NONE}.
 */
public interface WornSlots {
    /** Emits nothing and returns 0: only the vanilla slots are read. */
    WornSlots NONE = (entity, budget, sink) -> 0;

    /**
     * Gives each worn entry to {@code sink}, slot ids ascending, then functional before cosmetic
     * stacks by index, skipping empty stacks; stops after {@code budget} entries or when the sink
     * says so, and returns how many it gave.
     */
    int collect(LivingEntity entity, int budget, WornSink sink);
}
