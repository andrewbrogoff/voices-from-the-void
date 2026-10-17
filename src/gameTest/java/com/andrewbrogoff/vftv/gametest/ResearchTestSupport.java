package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchRecord;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.function.UnaryOperator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * Research state for other features' GameTests: learn or latch entries and set the Stage directly,
 * through the research service's one mutation path, with no costs, rewards, warp or resolution
 * (those are the full research ticket's). Each call takes the next record revision and syncs the
 * owner.
 */
final class ResearchTestSupport {
    private ResearchTestSupport() {}

    /** Adds {@code entries} (research keys) to the player's learned set. */
    static void learn(Player player, ResourceKey<?>... entries) {
        String[] ids = ids(entries);
        change(player, r -> r.learn(ids));
    }

    /** Latches {@code entries}' (research keys) triggers for the player. */
    static void latch(Player player, ResourceKey<?>... entries) {
        String[] ids = ids(entries);
        change(player, r -> r.latch(ids));
    }

    /**
     * A mock server player with a connection that never negotiated our channels, so a synced
     * attachment change has somewhere to go (and is skipped) instead of meeting a null connection.
     */
    static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(),
                connection,
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    /** Sets the player's Stage, 0-4. */
    static void stage(Player player, int stage) {
        change(player, r -> r.withStage(stage));
    }

    private static void change(Player player, UnaryOperator<ResearchRecord> op) {
        if (!ResearchService.update(player, op)) {
            throw new IllegalStateException("the player's research record is unreadable");
        }
    }

    private static String[] ids(ResourceKey<?>[] entries) {
        return Arrays.stream(entries).map(k -> k.identifier().toString()).toArray(String[]::new);
    }
}
