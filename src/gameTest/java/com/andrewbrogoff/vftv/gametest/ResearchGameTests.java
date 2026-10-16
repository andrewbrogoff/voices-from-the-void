package com.andrewbrogoff.vftv.gametest;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;

/**
 * The research feature's GameTests. Name each test {@code research_<what>}, and list its id {@code
 * vftv:research_<what>} in {@code src/gameTest/expected/research.txt}.
 */
final class ResearchGameTests {
    private ResearchGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {}
}
