package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.focus.FocusAffectEntityPayload;
import com.andrewbrogoff.vftv.api.focus.FocusKeys;
import com.andrewbrogoff.vftv.api.focus.FocusLimits;
import com.andrewbrogoff.vftv.api.focus.FocusPayload;
import com.andrewbrogoff.vftv.api.neoforge.focus.FocusAffectEntityEvent;
import com.andrewbrogoff.vftv.api.neoforge.focus.FocusEvent;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the focus API: its keys and limits, and a listener per event. */
final class FocusAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    FocusKeys.EFFECT_REGISTRY,
                    FocusKeys.MEDIUM_REGISTRY,
                    FocusKeys.MODIFIER_REGISTRY,
                    FocusKeys.KINDLE,
                    FocusKeys.GUST,
                    FocusKeys.RIME,
                    FocusKeys.DELVE,
                    FocusKeys.MEND,
                    FocusKeys.EXCHANGE,
                    FocusKeys.UNMAKING,
                    FocusKeys.UNDERTOW,
                    FocusKeys.BOLT,
                    FocusKeys.TOUCH,
                    FocusKeys.BURST,
                    FocusKeys.BEAM,
                    FocusKeys.FORCE,
                    FocusKeys.REACH,
                    FocusKeys.BREADTH,
                    FocusKeys.FOCUS_IMMUNE,
                    FocusKeys.FOCUS_COMPONENT,
                    FocusLimits.MAX_RANK,
                    FocusLimits.MAX_MODIFIER_ENTRIES,
                    FocusLimits.MIN_MODIFIER_LEVEL,
                    FocusLimits.MAX_MODIFIER_LEVEL,
                    FocusLimits.MAX_EFFECTS,
                    FocusLimits.MAX_MEDIA,
                    FocusLimits.MAX_MODIFIERS,
                    FocusLimits.MIN_COST,
                    FocusLimits.MAX_COST,
                    FocusLimits.MIN_COST_PM,
                    FocusLimits.MAX_COST_PM,
                    FocusLimits.MAX_RANGE,
                    FocusLimits.MAX_BURST_RADIUS,
                    FocusLimits.MAX_BLOCK_RADIUS_SQ,
                    FocusLimits.MAX_BURST_ENTITIES,
                    FocusLimits.MAX_BURST_BLOCKS,
                    FocusLimits.MAX_LIVE_BOLTS,
                    FocusLimits.MAX_BOLT_SPEED,
                    FocusLimits.MAX_BOLT_LIFETIME,
                    FocusLimits.MIN_BEAM_PULSE,
                    FocusLimits.MAX_BEAM_PULSE,
                    FocusLimits.GUST_MIN_DISTANCE,
                    FocusLimits.UNDERTOW_MIN_DISTANCE,
                    FocusLimits.UNDERTOW_MAX_SPEED,
                    FocusLimits.MAX_BEAM_BLOCK_RAY,
                    FocusLimits.MAX_SCALED_PARAM,
                    FocusLimits.MAX_HEALTH_MAGNITUDE,
                    FocusLimits.MAX_MOVEMENT_MAGNITUDE,
                    FocusLimits.MAX_TICKS_MAGNITUDE,
                    FocusLimits.MAX_AMPLIFIER_WARP,
                    FocusLimits.MAX_ID_CHARS,
                    FocusLimits.MAX_LATHE_ACTIONS_PER_TICK);

    private FocusAddon() {}

    /** Subscribes a listener per event. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(FocusEvent.class, e -> read(e.payload()));
        gameBus.addListener(FocusAffectEntityEvent.class, e -> read(e.payload()));
    }

    /** Reads every accessor, as an addon would. */
    private static void read(FocusPayload p) {
        VftvAddon.consume(
                p.kind(),
                p.player(),
                p.rank(),
                p.modifiers(),
                p.effect(),
                p.medium(),
                p.visKind(),
                p.gesture(),
                p.attempted());
    }

    /** Reads every accessor, as an addon would. */
    private static void read(FocusAffectEntityPayload p) {
        VftvAddon.consume(p.caster(), p.target(), p.effect(), p.harmful());
    }
}
