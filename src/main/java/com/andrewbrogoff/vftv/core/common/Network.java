package com.andrewbrogoff.vftv.core.common;

import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * The networking seam's common half (architecture, "Seams"): payload registration and the server's
 * sender. Payload records, their stream codecs and their handlers live in {@code core}; client
 * handlers and {@code toServer} are {@code core.common.client.ClientNetwork}'s.
 *
 * <p>Register during mod construction. Handlers run on the main thread. A server handler must
 * validate everything the client sent (architecture, "Untrusted input").
 */
public interface Network {
    /** Registers a client-to-server payload and its server handler. */
    <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler);

    /** Registers a server-to-client payload; its handler is registered on the client. */
    <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec);

    /** Sends to one player. */
    void toPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Sends to every player tracking a chunk. */
    void toTracking(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload);

    /** Sends to every player tracking an entity, and to the entity if it is a player. */
    void toTracking(Entity entity, CustomPacketPayload payload);
}
