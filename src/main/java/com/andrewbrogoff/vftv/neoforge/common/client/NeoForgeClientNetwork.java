package com.andrewbrogoff.vftv.neoforge.common.client;

import com.andrewbrogoff.vftv.core.common.client.ClientNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/**
 * The networking seam's client half on NeoForge: handlers are queued during client mod construction
 * and replayed on {@link RegisterClientPayloadHandlersEvent}.
 */
public final class NeoForgeClientNetwork implements ClientNetwork {
    private final List<Consumer<RegisterClientPayloadHandlersEvent>> handlers = new ArrayList<>();

    /** A client network listening on the mod's bus. */
    public NeoForgeClientNetwork(IEventBus modBus) {
        modBus.addListener(
                RegisterClientPayloadHandlersEvent.class,
                event -> handlers.forEach(h -> h.accept(event)));
    }

    @Override
    public <T extends CustomPacketPayload> void handler(
            CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        handlers.add(event -> event.register(type, (payload, context) -> handler.accept(payload)));
    }

    @Override
    public void toServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
