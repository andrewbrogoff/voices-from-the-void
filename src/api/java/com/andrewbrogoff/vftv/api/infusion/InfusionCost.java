package com.andrewbrogoff.vftv.api.infusion;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.aspect.Aspect;
import net.minecraft.resources.ResourceKey;

/**
 * One aspect cost of a committed infusion plan.
 *
 * @param aspect the aspect
 * @param cost the effective units the run owes, {@link InfusionLimits#MIN_COST} to {@link
 *     InfusionLimits#MAX_COST}
 * @param heldNow what the box's sources held of it at the commit, 0 to {@link
 *     InfusionLimits#HELD_NOW_MAX}; display only
 */
public record InfusionCost(ResourceKey<Aspect> aspect, int cost, int heldNow) {
    /** Constructed by vftv only. */
    @Internal
    public InfusionCost {}
}
