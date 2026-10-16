package fixture.vftv.neoforge;

import fixture.vftv.neoforge.compat.curios.CuriosCompat;
import fixture.vftv.neoforge.compat.curios.Hidden;

/** COMPAT_CONFINED: reaches into a compat package other than through the @Mod wiring. */
public final class Sneaky {
    private Sneaky() {}

    static CuriosCompat entryFromElsewhere;

    static Hidden inside;
}
