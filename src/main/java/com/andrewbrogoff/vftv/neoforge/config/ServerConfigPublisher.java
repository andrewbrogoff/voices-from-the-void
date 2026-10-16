package com.andrewbrogoff.vftv.neoforge.config;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;

/**
 * Publishes server-config records to their features (configuration, "Publishing a change"). No
 * config type reaches {@code core}: each feature's record is built here from corrected values.
 *
 * <ol>
 *   <li>Only a live local server publishes; with none, the event is ignored before any value is
 *       read (a remote client's synced copy).
 *   <li>Every record is snapshotted at once, on the event's thread.
 *   <li>The snapshot is queued with {@code server.execute}, and does nothing unless that server is
 *       still current, not stopped, and running it on its own thread.
 *   <li>Each feature swaps its record; an owner re-sends only for a record that changed.
 * </ol>
 *
 * When the server stops, every record goes back to its default.
 */
public final class ServerConfigPublisher {
    private final IConfigSpec spec;
    private final List<ServerRecord<?>> records;

    /**
     * One feature's server record.
     *
     * @param read builds the record from the spec's corrected values
     * @param current the record the feature's service holds now
     * @param install swaps the service's record
     * @param defaultValue the record's {@code DEFAULT}
     * @param resend the owner's re-send after a change, e.g. a rules record to every player
     */
    public record ServerRecord<R>(
            Supplier<R> read,
            Supplier<R> current,
            Consumer<R> install,
            R defaultValue,
            Consumer<MinecraftServer> resend) {}

    /** A publisher for the records filled from {@code spec}. */
    public ServerConfigPublisher(IConfigSpec spec, List<ServerRecord<?>> records) {
        this.spec = spec;
        this.records = List.copyOf(records);
    }

    /** Listens for the spec's loading and reloading, and for the server stopping. */
    public void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(ModConfigEvent.Loading.class, this::onConfig);
        modBus.addListener(ModConfigEvent.Reloading.class, this::onConfig);
        gameBus.addListener(ServerStoppedEvent.class, event -> reset());
    }

    private void onConfig(ModConfigEvent event) {
        if (spec.equals(event.getConfig().getSpec())) {
            publish(ServerLifecycleHooks.getCurrentServer());
        }
    }

    /** Publishes to {@code server}; a null server (no live local server) publishes nothing. */
    public void publish(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<Consumer<MinecraftServer>> snapshot = records.stream().map(r -> snapshot(r)).toList();
        server.execute(
                () -> {
                    if (server.equals(ServerLifecycleHooks.getCurrentServer())
                            && !server.isStopped()
                            && server.isSameThread()) {
                        snapshot.forEach(apply -> apply.accept(server));
                    }
                });
    }

    /** Puts every record back to its default. */
    public void reset() {
        records.forEach(ServerConfigPublisher::reset);
    }

    private static <R> Consumer<MinecraftServer> snapshot(ServerRecord<R> record) {
        R value = record.read().get();
        return server -> {
            R old = record.current().get();
            record.install().accept(value);
            if (!old.equals(value)) {
                record.resend().accept(server);
            }
        };
    }

    private static <R> void reset(ServerRecord<R> record) {
        record.install().accept(record.defaultValue());
    }
}
