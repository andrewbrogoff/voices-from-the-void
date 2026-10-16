package fixture.vftv.core.hush;

import fixture.vftv.core.flux.FluxService;

/** A permitted deposit, from core.hush. */
public final class HushSpread {
    private HushSpread() {}

    static void spread() {
        FluxService.deposit(2);
    }
}
