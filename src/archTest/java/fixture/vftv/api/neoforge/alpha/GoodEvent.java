package fixture.vftv.api.neoforge.alpha;

import com.andrewbrogoff.vftv.api.Internal;
import fixture.vftv.api.alpha.Owned;
import net.neoforged.bus.api.Event;

/** A well-shaped event: final, game bus, @Internal constructor, one payload accessor. */
public final class GoodEvent extends Event {
    private final Owned payload;

    /** Posted by the owner only. */
    @Internal
    public GoodEvent(Owned payload) {
        this.payload = payload;
    }

    /** The payload. */
    public Owned payload() {
        return payload;
    }
}
