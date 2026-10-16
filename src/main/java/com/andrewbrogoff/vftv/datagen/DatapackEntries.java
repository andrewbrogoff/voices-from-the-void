package com.andrewbrogoff.vftv.datagen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.resources.ResourceKey;

/**
 * Collects every feature's datapack registry bootstraps, so two features adding to one registry
 * (e.g. configured features) share one {@link RegistrySetBuilder} entry, which allows only one per
 * registry.
 */
public final class DatapackEntries {
    private final Map<ResourceKey<?>, Stub<?>> stubs = new LinkedHashMap<>();

    private record Stub<T>(
            ResourceKey<? extends Registry<T>> registry,
            List<RegistrySetBuilder.RegistryBootstrap<T>> parts) {
        void addTo(RegistrySetBuilder builder) {
            builder.add(registry, context -> parts.forEach(part -> part.run(context)));
        }
    }

    /** Adds a bootstrap for {@code registry}, run after any added before it. */
    public <T> void add(
            ResourceKey<? extends Registry<T>> registry,
            RegistrySetBuilder.RegistryBootstrap<T> bootstrap) {
        // The map holds, for each registry key, the stub created for that same key.
        @SuppressWarnings("unchecked")
        Stub<T> stub =
                (Stub<T>)
                        stubs.computeIfAbsent(
                                registry, k -> new Stub<>(registry, new ArrayList<>()));
        stub.parts().add(bootstrap);
    }

    /** The builder holding every registry added, in the order first added. */
    RegistrySetBuilder build() {
        RegistrySetBuilder builder = new RegistrySetBuilder();
        stubs.values().forEach(stub -> stub.addTo(builder));
        return builder;
    }

    /** Whether nothing was added. */
    boolean isEmpty() {
        return stubs.isEmpty();
    }
}
