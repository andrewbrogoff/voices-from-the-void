package com.andrewbrogoff.vftv.api.focus;

import com.andrewbrogoff.vftv.api.Internal;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * An entity a focus working is about to affect. See the foci spec, "Targets".
 *
 * @param caster the casting player; a live handle, valid only during dispatch
 * @param target the entity about to be affected; a live handle, valid only during dispatch
 * @param effect the effect about to be applied
 * @param harmful whether the effect is harmful (every built-in entity effect except Mend)
 */
public record FocusAffectEntityPayload(
        ServerPlayer caster,
        LivingEntity target,
        ResourceKey<FocusEffect> effect,
        boolean harmful) {
    /** Constructed by vftv only. */
    @Internal
    public FocusAffectEntityPayload {}
}
