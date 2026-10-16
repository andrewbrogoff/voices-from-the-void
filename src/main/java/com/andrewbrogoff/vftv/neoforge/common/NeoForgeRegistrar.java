package com.andrewbrogoff.vftv.neoforge.common;

import com.andrewbrogoff.vftv.core.common.Registrar;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jspecify.annotations.Nullable;

/**
 * The registration seam on NeoForge: one {@link DeferredRegister} per registry, plus the
 * datapack-registry and data-map events. Build it in the mod constructor.
 */
public final class NeoForgeRegistrar implements Registrar {
    private final String namespace;
    private final IEventBus modBus;
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();
    private final List<Consumer<DataPackRegistryEvent.NewRegistry>> dataPackRegistries =
            new ArrayList<>();
    private final List<DataMapType<?, ?>> dataMaps = new ArrayList<>();

    /** A registrar for {@code namespace}, listening on the mod's bus. */
    public NeoForgeRegistrar(String namespace, IEventBus modBus) {
        this.namespace = namespace;
        this.modBus = modBus;
        modBus.addListener(
                DataPackRegistryEvent.NewRegistry.class,
                event -> dataPackRegistries.forEach(r -> r.accept(event)));
        modBus.addListener(
                RegisterDataMapTypesEvent.class, event -> dataMaps.forEach(event::register));
    }

    @Override
    public <T> Supplier<T> register(
            ResourceKey<? extends Registry<T>> registry,
            String name,
            Supplier<? extends T> factory) {
        // Each key maps to a register created for that same key, so the cast is safe.
        @SuppressWarnings("unchecked")
        DeferredRegister<T> register =
                (DeferredRegister<T>)
                        registers.computeIfAbsent(
                                registry,
                                key -> {
                                    DeferredRegister<T> created =
                                            DeferredRegister.create(registry, namespace);
                                    created.register(modBus);
                                    return created;
                                });
        return register.register(name, factory);
    }

    @Override
    public <T> void dataPackRegistry(
            ResourceKey<Registry<T>> registry, Codec<T> codec, @Nullable Codec<T> networkCodec) {
        dataPackRegistries.add(event -> event.dataPackRegistry(registry, codec, networkCodec));
    }

    /** Registers an {@code api.neoforge} data-map type; core never sees these. */
    public void dataMap(DataMapType<?, ?> type) {
        dataMaps.add(type);
    }
}
