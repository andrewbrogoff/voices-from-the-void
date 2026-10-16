package com.andrewbrogoff.vftv.gametest;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.flux.FluxRulesPayload;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.flux.HushConfig;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.andrewbrogoff.vftv.neoforge.flux.FluxNeoForge;
import com.mojang.serialization.DataResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.bus.api.IEventBus;

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
        tests.put("flux_rules_sync_unnegotiated", FluxGameTests::rulesSyncUnnegotiated);
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
     * A player whose connection never negotiated the payload (a mock player) joins, and the rules
     * are re-sent to everyone, without an exception reaching the join or the publish.
     */
    @SuppressWarnings("removal") // the only helper that joins a player through the PlayerList
    private static void rulesSyncUnnegotiated(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            helper.assertFalse(
                    player.connection.hasChannel(FluxRulesPayload.TYPE), "no negotiated channel");
            FluxNeoForge.sendRules(server);
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static String text(Resource resource) {
        try (InputStream in = resource.open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
