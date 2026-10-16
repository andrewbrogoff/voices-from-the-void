package com.andrewbrogoff.vftv.core.common;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * The registration seam (architecture, "Seams"). Features call it from their init, during mod
 * construction; the platform backs it with deferred registers and the datapack-registry event.
 *
 * <p>Data-map types are {@code api.neoforge} constants, which {@code core} never sees, so the
 * platform registers them itself.
 */
public interface Registrar {
    /**
     * Registers {@code factory} under {@code vftv:<name>} in a static registry. The returned
     * supplier resolves once registration has run.
     */
    <T> Supplier<T> register(
            ResourceKey<? extends Registry<T>> registry,
            String name,
            Supplier<? extends T> factory);

    /**
     * Declares a datapack registry. A non-null {@code networkCodec} makes it synced to clients; the
     * API spec's registry table says which are.
     */
    <T> void dataPackRegistry(
            ResourceKey<Registry<T>> registry, Codec<T> codec, @Nullable Codec<T> networkCodec);
}
