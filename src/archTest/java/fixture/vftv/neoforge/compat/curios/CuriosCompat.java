package fixture.vftv.neoforge.compat.curios;

import top.theillusivec4.curios.api.CuriosApi;

/** The Curios entry class; it may use the Curios API. */
public final class CuriosCompat {
    private CuriosCompat() {}

    static CuriosApi api;

    /** Called from the @Mod wiring. */
    public static void init() {}
}
