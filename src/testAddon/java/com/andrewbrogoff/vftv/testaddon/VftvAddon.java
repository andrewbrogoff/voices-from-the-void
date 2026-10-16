package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.Experimental;
import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.Stable;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The example addon (API spec, "testAddon"): compiled against the built {@code vftv-api} jar only,
 * and run beside vftv by {@code runAddonGameTestServer}. Each system ticket extends it to cover its
 * API types. Its test ids are listed in {@code src/testAddon/expected-tests.txt}.
 */
@Mod(VftvAddon.MOD_ID)
public final class VftvAddon {
    /** The addon's namespace. */
    public static final String MOD_ID = "vftv_addon";

    /** Every API declaration this addon uses; it grows with the API. */
    static final List<Class<?>> API_USED =
            List.of(Stable.class, Experimental.class, Internal.class);

    private static final DeferredRegister<Consumer<GameTestHelper>> TESTS =
            DeferredRegister.create(Registries.TEST_FUNCTION, MOD_ID);

    /** Registers the addon's test function and its instance. */
    public VftvAddon(IEventBus modBus) {
        TESTS.register("api_present", () -> VftvAddon::apiPresent);
        TESTS.register(modBus);
        modBus.addListener(RegisterGameTestsEvent.class, VftvAddon::registerInstances);
    }

    private static void registerInstances(RegisterGameTestsEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(MOD_ID, "api_present");
        Holder<TestEnvironmentDefinition<?>> environment =
                event.registerEnvironment(Identifier.fromNamespaceAndPath(MOD_ID, "default"));
        event.registerTest(
                id,
                new FunctionGameTestInstance(
                        ResourceKey.create(Registries.TEST_FUNCTION, id),
                        new TestData<>(
                                environment,
                                Identifier.withDefaultNamespace("empty"),
                                20,
                                0,
                                true)));
    }

    /** vftv is loaded beside the addon, and the API the addon compiled against links at runtime. */
    private static void apiPresent(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("vftv"), "vftv is loaded");
        helper.assertValueEqual(API_USED.size(), 3, "the API types the addon links against");
        helper.succeed();
    }
}
