package com.andrewbrogoff.vftv.core.hush.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** A Hollow's render state: whether it has a target, and the rise's scale. */
public final class HollowRenderState extends LivingEntityRenderState {
    /** Whether it hunts (its synced aggressive flag): the head droops without one. */
    public boolean hunting;

    /** The rise's scale, 0.5 to 1.0. */
    public float rise = 1.0F;
}
