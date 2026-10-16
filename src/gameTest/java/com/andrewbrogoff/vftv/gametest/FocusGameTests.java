package com.andrewbrogoff.vftv.gametest;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;

/**
 * The focus feature's GameTests. Name each test {@code focus_<what>}, and list its id {@code
 * vftv:focus_<what>} in {@code src/gameTest/expected/focus.txt}.
 */
final class FocusGameTests {
    private FocusGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {}
}
