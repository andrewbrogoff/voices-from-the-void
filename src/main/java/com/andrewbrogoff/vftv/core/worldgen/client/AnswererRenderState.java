package com.andrewbrogoff.vftv.core.worldgen.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** An Answerer's render state: which type, the attack swing and the Cantor's chant flag. */
public final class AnswererRenderState extends LivingEntityRenderState {
    /** Whether it is a Cantor. */
    public boolean cantor;

    /** Whether the Cantor is showing its chant. */
    public boolean chanting;

    /** Vanilla's attack animation, 0 to 1. */
    public float attackAnim;
}
