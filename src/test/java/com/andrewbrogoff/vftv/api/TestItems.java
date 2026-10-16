package com.andrewbrogoff.vftv.api;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Binds a minimal component map to every item, which a server does at its first data load, so a
 * unit test can make and copy {@code ItemStack}s without starting one. The API tests only copy
 * stacks; no test here depends on an item's real components.
 */
public final class TestItems {
    private static boolean bound;

    private TestItems() {}

    /** Binds the components once per JVM. */
    public static synchronized void bindComponents() {
        if (!bound) {
            DataComponentMap minimal =
                    DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build();
            BuiltInRegistries.ITEM.listElements().forEach(item -> item.bindComponents(minimal));
            bound = true;
        }
    }
}
