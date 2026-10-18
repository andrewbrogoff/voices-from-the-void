package com.andrewbrogoff.vftv.neoforge.config.client;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Publishes client-config records to their features (configuration, "Client config"): every record
 * is built at once on the event's thread, and installed on the client thread.
 */
public final class ClientConfigPublisher {
    private final IConfigSpec spec;
    private final List<ClientRecord<?>> records;

    /**
     * One feature's client record.
     *
     * @param read builds the record from the spec's corrected values
     * @param install swaps the feature's record
     */
    public record ClientRecord<R>(Supplier<R> read, Consumer<R> install) {}

    /** A publisher for the records filled from {@code spec}. */
    public ClientConfigPublisher(IConfigSpec spec, List<ClientRecord<?>> records) {
        this.spec = spec;
        this.records = List.copyOf(records);
    }

    /** Listens for the spec's loading and reloading. */
    public void register(IEventBus modBus) {
        modBus.addListener(ModConfigEvent.Loading.class, this::onConfig);
        modBus.addListener(ModConfigEvent.Reloading.class, this::onConfig);
    }

    private void onConfig(ModConfigEvent event) {
        if (!spec.equals(event.getConfig().getSpec())) {
            return;
        }
        List<Runnable> snapshot = records.stream().map(r -> snapshot(r)).toList();
        Minecraft minecraft = Minecraft.getInstance();
        // The first load fires during mod loading, before the client exists: install it there.
        if (castToNullable(minecraft) == null) {
            snapshot.forEach(Runnable::run);
        } else {
            minecraft.execute(() -> snapshot.forEach(Runnable::run));
        }
    }

    // Minecraft.getInstance() is null until the client is constructed, despite its annotation.
    private static @org.jspecify.annotations.Nullable Minecraft castToNullable(
            Minecraft minecraft) {
        return minecraft;
    }

    private static <R> Runnable snapshot(ClientRecord<R> record) {
        R value = record.read().get();
        return () -> record.install().accept(value);
    }
}
