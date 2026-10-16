package fixture.vftv.neoforge;

import fixture.vftv.neoforge.compat.curios.CuriosCompat;
import net.neoforged.fml.common.Mod;

/** The @Mod wiring: the one place allowed to call the Curios entry class. */
@Mod("fixture")
public final class FixtureMod {
    FixtureMod() {
        CuriosCompat.init();
    }
}
