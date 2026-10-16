package com.andrewbrogoff.vftv.gametest;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;

/**
 * The armor feature's GameTests. Name each test {@code armor_<what>}, and list its id {@code
 * vftv:armor_<what>} in {@code src/gameTest/expected/armor.txt}.
 */
final class ArmorGameTests {
    private ArmorGameTests() {}

    /**
     * Adds the feature's tests by name; {@code modBus} is for any test-only registration they need.
     */
    static void register(Map<String, Consumer<GameTestHelper>> tests, IEventBus modBus) {}
}
