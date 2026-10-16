package com.andrewbrogoff.vftv.gametest;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;

/**
 * The golem feature's GameTests. Name each test {@code golem_<what>}, and list its id {@code
 * vftv:golem_<what>} in {@code src/gameTest/expected/golem.txt}.
 */
final class GolemGameTests {
    private GolemGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {}
}
