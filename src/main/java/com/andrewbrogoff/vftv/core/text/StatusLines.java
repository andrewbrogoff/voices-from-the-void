package com.andrewbrogoff.vftv.core.text;

import com.andrewbrogoff.vftv.core.common.Network;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The one destination for world-use feedback (accessibility, R5; conventions, "Feedback routing"):
 * every status, refusal and confirmation the server sends one player about a block, item or entity
 * they used. Never the action bar, never chat.
 */
public final class StatusLines {
    private static @Nullable Network network;
    private static BiConsumer<ServerPlayer, StatusLine> sender =
            (player, line) ->
                    Objects.requireNonNull(network, "StatusLines.init").toPlayer(player, line);

    private StatusLines() {}

    /** Registers the payload; called once from {@code Vftv.init}. */
    public static void init(Network platformNetwork) {
        network = platformNetwork;
        platformNetwork.clientbound(StatusLine.TYPE, StatusLine.STREAM_CODEC);
    }

    /**
     * Sends {@code text} to {@code player}'s status line. The text is a {@code
     * Component.translatable} with its arguments, names as components and numbers pre-formatted
     * (accessibility, "Text built on the server").
     */
    public static void send(ServerPlayer player, Component text) {
        sender.accept(player, new StatusLine(text));
    }

    /**
     * Replaces the sender and returns the previous one: GameTests record what an owner sends to a
     * mock player, which has no connection.
     */
    public static BiConsumer<ServerPlayer, StatusLine> swapSender(
            BiConsumer<ServerPlayer, StatusLine> replacement) {
        BiConsumer<ServerPlayer, StatusLine> previous = sender;
        sender = replacement;
        return previous;
    }
}
