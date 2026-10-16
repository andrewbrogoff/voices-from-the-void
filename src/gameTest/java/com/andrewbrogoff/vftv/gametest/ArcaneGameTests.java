package com.andrewbrogoff.vftv.gametest;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;

/**
 * The arcane feature's GameTests. Name each test {@code arcane_<what>}, and list its id {@code
 * vftv:arcane_<what>} in {@code src/gameTest/expected/arcane.txt}.
 */
final class ArcaneGameTests {
    private ArcaneGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {}
}
