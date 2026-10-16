package fixture.vftv.api.alpha;

import com.andrewbrogoff.vftv.api.Stable;

/** A Stable constant holder: an allowed shape. */
@Stable
public final class AlphaKeys {
    private AlphaKeys() {}

    /** An id. */
    public static final String ID = "fixture:alpha";

    /** A nested holder inherits Stable from its enclosing type. */
    public static final class Nested {
        private Nested() {}
    }
}
