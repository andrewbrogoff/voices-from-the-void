package com.andrewbrogoff.vftv.gametest;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;

/**
 * The worldgen feature's GameTests. Name each test {@code worldgen_<what>}, and list its id {@code
 * vftv:worldgen_<what>} in {@code src/gameTest/expected/worldgen.txt}.
 */
final class WorldgenGameTests {
    private WorldgenGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {}
}
