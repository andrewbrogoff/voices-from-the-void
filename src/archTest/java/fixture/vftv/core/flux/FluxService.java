package fixture.vftv.core.flux;

import fixture.vftv.core.alpha.Alpha;
import fixture.vftv.core.common.Common;

/** FLUX_LEAF: flux imports another feature (alpha); importing core.common is allowed. */
public final class FluxService {
    private FluxService() {}

    static Alpha leak;

    static Common shared;

    /** The guarded entry point. */
    public static void deposit(int amount) {}
}
