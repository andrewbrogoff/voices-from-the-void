package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.flux.FluxRulesPayload;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.HushConfig;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.andrewbrogoff.vftv.neoforge.common.NeoForgeNetwork;
import com.mojang.serialization.DataResult;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The flux feature's GameTests. Name each test {@code flux_<what>}, and list its id {@code
 * vftv:flux_<what>} in {@code src/gameTest/expected/flux.txt}.
 */
final class FluxGameTests {
    private FluxGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {
        tests.put("flux_tuning_loaded", FluxGameTests::tuningLoaded);
        tests.put("flux_tuning_reload_rollback", FluxGameTests::tuningReloadRollback);
        // A second sender over the same seam, registering nothing, to drive every send path.
        Network network = new NeoForgeNetwork(modBus);
        tests.put("flux_rules_sync_unnegotiated", helper -> rulesSyncUnnegotiated(helper, network));
    }

    /**
     * The datagen'd {@code hush.json} is in the server's data, parses to every default, and is the
     * tuning in force with the default switches.
     */
    private static void tuningLoaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Optional<Resource> file =
                server.getResourceManager()
                        .getResource(
                                Identifier.fromNamespaceAndPath(
                                        "vftv", FluxRulesService.TUNING_PATH));
        helper.assertTrue(file.isPresent(), "data/vftv/tuning/hush.json is loaded");
        DataResult<int[]> read = FluxRulesService.parse(text(file.orElseThrow()));
        helper.assertTrue(
                read.result().filter(v -> Arrays.equals(v, FluxTuning.defaults())).isPresent(),
                "the shipped file holds every default");
        helper.assertValueEqual(
                FluxRulesService.current(),
                FluxTuning.rules(FluxTuning.defaults(), true, true, false),
                "the rules in force");
        helper.assertValueEqual(FluxRulesService.config(), HushConfig.DEFAULT, "the switches");
        helper.succeed();
    }

    /**
     * On a {@code /reload} (a server is running), an invalid file keeps the previous tuning; a
     * valid one replaces it. Restored before the tick ends, so no other test sees the change.
     */
    private static void tuningReloadRollback(GameTestHelper helper) {
        FluxRules before = FluxRulesService.current();
        try {
            FluxRulesService.applyTuning(FluxRulesService.parse("{\"hush.walk\": 65}"), true);
            helper.assertValueEqual(
                    FluxRulesService.current(), before, "an invalid file is refused");
            FluxRulesService.applyTuning(FluxRulesService.parse("{\"hush.walk\": 64}"), true);
            helper.assertValueEqual(
                    FluxRulesService.current().hushWalk(), 64, "a valid one applies");
        } finally {
            FluxRulesService.applyTuning(DataResult.success(FluxTuning.defaults()), true);
        }
        helper.assertValueEqual(FluxRulesService.current(), before, "restored");
        helper.succeed();
    }

    /**
     * A connection that never negotiated the payload (as a mock player's) would make an unguarded
     * send throw; the shared sender ({@code NeoForgeNetwork}) skips it on every path instead. The
     * player is never joined, so no other mod's join sync runs against it.
     */
    private static void rulesSyncUnnegotiated(GameTestHelper helper, Network network) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(
                server,
                connection,
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        helper.assertFalse(
                player.connection.hasChannel(FluxRulesPayload.TYPE), "no negotiated channel");
        FluxRulesPayload payload = new FluxRulesPayload(FluxRulesService.current());
        helper.assertTrue(
                throwsUnsupported(() -> PacketDistributor.sendToPlayer(player, payload)),
                "an unguarded send throws");
        FluxRulesService.sendTo(player);
        FluxRulesService.sendToAll(server);
        network.toPlayer(player, payload);
        network.toTracking(player, payload);
        network.toTracking(helper.getLevel(), player.chunkPosition(), payload);
        helper.succeed();
    }

    private static boolean throwsUnsupported(Runnable send) {
        try {
            send.run();
            return false;
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }

    private static String text(Resource resource) {
        try (InputStream in = resource.open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
