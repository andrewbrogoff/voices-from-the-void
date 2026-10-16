package fixture.vftv.api.alpha;

import com.andrewbrogoff.vftv.api.Stable;
import java.util.List;

/** API_TIER_LEAK: a Stable method returning a list of an Internal type. */
@Stable
public interface Leaky {
    /** The leak, inside a generic argument. */
    List<Secret> secrets();
}
