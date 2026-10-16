package fixture.vftv.api.alpha;

import com.andrewbrogoff.vftv.api.Internal;
import com.andrewbrogoff.vftv.api.Stable;

/** API_TIER_LEAK: a Stable record with a component of an Experimental type. */
@Stable
public record StableRecord(Draft draft) {
    /** The vftv-owned constructor. */
    @Internal
    public StableRecord {}
}
