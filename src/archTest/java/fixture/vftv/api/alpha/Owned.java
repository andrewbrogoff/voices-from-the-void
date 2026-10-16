package fixture.vftv.api.alpha;

import com.andrewbrogoff.vftv.api.Internal;

/** A vftv-owned record with its @Internal canonical constructor: allowed. */
public record Owned(int amount) {
    /** The vftv-owned constructor. */
    @Internal
    public Owned {}
}
