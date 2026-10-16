package fixture.vftv.api.neoforge.alpha;

import com.andrewbrogoff.vftv.api.Internal;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.event.IModBusEvent;

/** API_SHAPES: events of the wrong shape, nested in a constant holder. */
public final class BadEvents {
    private BadEvents() {}

    /** Not final. */
    public static class OpenEvent extends Event {
        /** Posted by the owner only. */
        @Internal
        public OpenEvent() {}
    }

    /** A mod-bus event. */
    public static final class ModBusEvent extends Event implements IModBusEvent {
        /** Posted by the owner only. */
        @Internal
        public ModBusEvent() {}
    }

    /** Cancellable, but not one of the three listed. */
    public static final class CancelledEvent extends Event implements ICancellableEvent {
        /** Posted by the owner only. */
        @Internal
        public CancelledEvent() {}
    }
}
