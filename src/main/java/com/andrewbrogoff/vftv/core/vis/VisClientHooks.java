package com.andrewbrogoff.vftv.core.vis;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The client-only behaviour common classes reach without loading client classes: the client feature
 * installs it once, and a dedicated server keeps the no-op.
 */
public final class VisClientHooks {
    /** The wand's client use tick (the tapping player's glints). */
    public interface UseTick {
        /** One client use tick. */
        void tick(Level level, LivingEntity entity, ItemStack stack);
    }

    private static volatile UseTick useTick = (l, e, s) -> {};

    private VisClientHooks() {}

    /** Installs the client's use tick. */
    public static void installUseTick(UseTick hook) {
        useTick = hook;
    }

    static void useTick(Level level, LivingEntity entity, ItemStack stack) {
        useTick.tick(level, entity, stack);
    }
}
