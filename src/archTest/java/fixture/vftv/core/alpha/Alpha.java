package fixture.vftv.core.alpha;

import fixture.vftv.api.neoforge.alpha.GoodEvent;
import fixture.vftv.core.alpha.client.AlphaClientConfig;
import fixture.vftv.core.beta.Beta;
import fixture.vftv.core.flux.FluxService;
import fixture.vftv.neoforge.Platformy;
import java.util.ServiceLoader;

/** A core class that breaks most rules at once; each field or method names the rule it breaks. */
public final class Alpha {
    private Alpha() {}

    /** FEATURES_ACYCLIC: alpha and beta import each other. */
    static Beta beta;

    /** CORE_IS_LOADER_FREE. */
    static net.neoforged.bus.api.Event loaderLeak;

    /** LAYERS: core uses the platform. */
    static Platformy platform;

    /** LAYERS: core uses api.neoforge. */
    static GoodEvent apiNeoforge;

    /** CLIENT_SEPARATION. */
    static net.minecraft.client.Minecraft client;

    /** COMPAT_CONFINED: JEI outside neoforge.compat.jei. */
    static mezz.jei.api.IModPlugin jei;

    /** MIXINS_CONFINED. */
    static org.spongepowered.asm.mixin.Mixin mixin;

    /** CONFIG_SIDES: server code uses a client config. */
    static AlphaClientConfig clientConfig;

    /** NO_REFLECTION. */
    static Object reflect() throws ClassNotFoundException {
        return Class.forName("java.lang.String");
    }

    /** NO_REFLECTION: service discovery. */
    static Object services() {
        return ServiceLoader.load(Runnable.class);
    }

    /** FLUX_ENTRY_POINTS: a deposit from outside the Hush. */
    static void deposit() {
        FluxService.deposit(1);
    }
}
