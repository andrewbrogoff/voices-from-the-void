package com.andrewbrogoff.vftv.gametest;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;

/**
 * The vis feature's GameTests. Name each test {@code vis_<what>}, and list its id {@code
 * vftv:vis_<what>} in {@code src/gameTest/expected/vis.txt}.
 */
final class VisGameTests {
    private VisGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {}
}
