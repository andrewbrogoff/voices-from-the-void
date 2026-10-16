package com.andrewbrogoff.vftv.neoforge.common;

import com.andrewbrogoff.vftv.core.common.Network;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The networking seam on NeoForge: registrations are queued during mod construction and replayed on
 * {@link RegisterPayloadHandlersEvent}; sending is {@link PacketDistributor}.
 */
public final class NeoForgeNetwork implements Network {
    /** The protocol version; bump it when any payload's wire format changes. */
    public static final String VERSION = "1";

    private final List<Consumer<PayloadRegistrar>> registrations = new ArrayList<>();

    /** A network listening on the mod's bus. */
    public NeoForgeNetwork(IEventBus modBus) {
        modBus.addListener(
                RegisterPayloadHandlersEvent.class,
                event -> {
                    PayloadRegistrar registrar =
                            event.registrar(VERSION).executesOn(HandlerThread.MAIN);
                    registrations.forEach(r -> r.accept(registrar));
                });
    }

    @Override
    public <T extends CustomPacketPayload> void serverbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {
        registrations.add(
                r ->
                        r.playToServer(
                                type,
                                codec,
                                (payload, context) -> {
                                    if (context.player() instanceof ServerPlayer player) {
                                        handler.accept(payload, player);
                                    }
                                }));
    }

    @Override
    public <T extends CustomPacketPayload> void clientbound(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        registrations.add(r -> r.playToClient(type, codec));
    }

    /**
     * Sends to one player, skipping a connection that never negotiated the payload's channel (a
     * mock or vanilla connection), where NeoForge's send would throw.
     */
    @Override
    public void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Each tracking player through {@link #toPlayer}, so non-negotiated connections are skipped.
     */
    @Override
    public void toTracking(ServerLevel level, ChunkPos chunk, CustomPacketPayload payload) {
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(chunk, false)) {
            toPlayer(player, payload);
        }
    }

    /** Each tracking player and the entity itself through {@link #toPlayer}. */
    @Override
    public void toTracking(Entity entity, CustomPacketPayload payload) {
        if (entity.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayersWatching(entity)) {
                toPlayer(player, payload);
            }
            if (entity instanceof ServerPlayer self) {
                toPlayer(self, payload);
            }
        }
    }
}
