package com.andrewbrogoff.vftv.core.common.client;

import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The networking seam's client half: handlers for server-to-client payloads, and the client's
 * sender. Register handlers during client mod construction; they run on the client thread.
 */
public interface ClientNetwork {
    /** Registers the client handler of a payload already registered as clientbound. */
    <T extends CustomPacketPayload> void handler(
            CustomPacketPayload.Type<T> type, Consumer<T> handler);

    /** Sends to the server. */
    void toServer(CustomPacketPayload payload);
}
