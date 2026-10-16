package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.neoforge.warp.WarpChangedEvent;
import com.andrewbrogoff.vftv.api.neoforge.warp.WarpDataMaps;
import com.andrewbrogoff.vftv.api.warp.WarpChangedPayload;
import com.andrewbrogoff.vftv.api.warp.WarpKeys;
import com.andrewbrogoff.vftv.api.warp.WarpLimits;
import com.andrewbrogoff.vftv.api.warp.WarpState;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the warp API: the warp change event and the gear-warp data map. */
final class WarpAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    WarpKeys.VOICE_EVENT_REGISTRY,
                    WarpKeys.BARGAIN_REGISTRY,
                    WarpKeys.GEAR_WARP,
                    WarpKeys.WARP_ATTACHMENT,
                    WarpLimits.T_MAX,
                    WarpLimits.L_MAX,
                    WarpLimits.P_FORMAT_MAX,
                    WarpLimits.COUNTER_FORMAT_MAX,
                    WarpLimits.MAX_EVENTS,
                    WarpLimits.MAX_BARGAINS,
                    WarpLimits.MAX_GEAR_SLOTS,
                    WarpLimits.MAX_APPARITION_ATTEMPTS,
                    WarpLimits.MAX_CUE_HOLD,
                    WarpLimits.MAX_MILESTONES,
                    WarpLimits.MAX_ID_CHARS,
                    WarpDataMaps.GEAR_WARP);

    private WarpAddon() {}

    /** Subscribes a listener per event. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(WarpChangedEvent.class, e -> read(e.payload()));
    }

    /** Reads every accessor, as an addon would. */
    private static void read(WarpChangedPayload p) {
        VftvAddon.consume(p.player(), p.source().kind(), p.source().id());
        read(p.before());
        read(p.after());
        read(p.delta());
    }

    /** Reads every accessor of one warp state. */
    private static void read(WarpState s) {
        VftvAddon.consume(s.temporary(), s.lasting(), s.permanent(), s.gear(), s.total());
    }
}
