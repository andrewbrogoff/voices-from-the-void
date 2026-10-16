package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.neoforge.road.EndingChosenEvent;
import com.andrewbrogoff.vftv.api.neoforge.road.RoadEncounterEvent;
import com.andrewbrogoff.vftv.api.neoforge.road.RoadStepPassedEvent;
import com.andrewbrogoff.vftv.api.road.ChoristerLimits;
import com.andrewbrogoff.vftv.api.road.EldritchKeys;
import com.andrewbrogoff.vftv.api.road.EndingChosenPayload;
import com.andrewbrogoff.vftv.api.road.RoadEncounterPayload;
import com.andrewbrogoff.vftv.api.road.RoadLayout;
import com.andrewbrogoff.vftv.api.road.RoadLimits;
import com.andrewbrogoff.vftv.api.road.RoadStepPassedPayload;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/**
 * The example addon's use of the road API: a listener per Road event, and every Stable key, limit
 * and layout constant.
 */
final class RoadAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    EldritchKeys.VUULAS,
                    EldritchKeys.VUULAS_TYPE,
                    EldritchKeys.VUULAS_BIOME,
                    EldritchKeys.THRESHOLD,
                    EldritchKeys.THRESHOLD_SITE,
                    EldritchKeys.ROAD_SITE,
                    EldritchKeys.CHORISTER,
                    EldritchKeys.WUUREN,
                    EldritchKeys.SUHLAA,
                    EldritchKeys.NIHTHAA,
                    EldritchKeys.CHORISTER_ENTITY,
                    EldritchKeys.USABLE_ON_THE_ROAD,
                    EldritchKeys.FORBIDDEN_ON_THE_ROAD,
                    EldritchKeys.UNSAFE_RETURN,
                    EldritchKeys.FIND_CHOIRS_ROAD,
                    EldritchKeys.END_JOINED,
                    EldritchKeys.END_SILENCED,
                    EldritchKeys.ROAD_ATTACHMENT,
                    RoadLimits.MAX_PARTICIPANTS,
                    RoadLimits.MAX_ARENA_SCAN,
                    RoadLimits.MAX_ENTRY_HEALTH,
                    RoadLimits.THREAT_MAX,
                    RoadLimits.MAX_ATTEMPTS_PER_PLAYER_PER_TICK,
                    RoadLimits.ENDING_LINE_GAP_TICKS,
                    RoadLimits.ENDING_LINE_FALLBACK_TICKS,
                    RoadLimits.ENDING_MISSING_LINE_TICKS,
                    ChoristerLimits.MIN_WINDUP,
                    ChoristerLimits.MIN_LOCK,
                    ChoristerLimits.MIN_RECOVERY,
                    ChoristerLimits.MAX_MOVE_DAMAGE,
                    ChoristerLimits.ARENA_REACH,
                    ChoristerLimits.MAX_WAVES,
                    ChoristerLimits.MAX_LIFETIME,
                    ChoristerLimits.MAX_HELD_TICKS,
                    ChoristerLimits.MAX_MOVES,
                    ChoristerLimits.MAX_CYCLE,
                    ChoristerLimits.MAX_CYCLE_TICKS,
                    ChoristerLimits.WALK_RADIUS,
                    ChoristerLimits.BASE_WIDTH,
                    ChoristerLimits.BASE_HEIGHT,
                    ChoristerLimits.EYE_HEIGHT,
                    ChoristerLimits.MAX_SCALE,
                    ChoristerLimits.ZONE_MIN_DY,
                    ChoristerLimits.ZONE_MAX_DY,
                    ChoristerLimits.DODGE_LATENCY_TICKS,
                    ChoristerLimits.DODGE_WALK_SPEED,
                    ChoristerLimits.MAX_MARK_QUADS,
                    RoadLayout.STEP_1_FLOOR_Y,
                    RoadLayout.STEP_2_FLOOR_Y,
                    RoadLayout.STEP_3_FLOOR_Y,
                    RoadLayout.HEART_FLOOR_Y,
                    RoadLayout.TEMPLATE_SIZE_X,
                    RoadLayout.TEMPLATE_SIZE_Y,
                    RoadLayout.TEMPLATE_SIZE_Z,
                    RoadLayout.TEMPLATE_ORIGIN_X,
                    RoadLayout.TEMPLATE_ORIGIN_Y,
                    RoadLayout.TEMPLATE_ORIGIN_Z,
                    RoadLayout.WELL_MAX_D2,
                    RoadLayout.RING_MIN_D2,
                    RoadLayout.RING_MAX_D2,
                    RoadLayout.LANDING_X,
                    RoadLayout.LANDING_Y,
                    RoadLayout.LANDING_Z,
                    RoadLayout.LANDING_YAW,
                    RoadLayout.STEP_BELL_X,
                    RoadLayout.STEP_BELL_Y,
                    RoadLayout.STEP_BELL_Z,
                    RoadLayout.HEART_STEP_BELL_X,
                    RoadLayout.HEART_STEP_BELL_Z,
                    RoadLayout.RISE_X,
                    RoadLayout.RISE_Y,
                    RoadLayout.RISE_Z,
                    RoadLayout.RISE_BOX_MIN_X,
                    RoadLayout.RISE_BOX_MAX_X,
                    RoadLayout.RISE_BOX_MIN_Y,
                    RoadLayout.RISE_BOX_MAX_Y,
                    RoadLayout.RISE_BOX_MIN_Z,
                    RoadLayout.RISE_BOX_MAX_Z,
                    RoadLayout.CHOIR_WALK_MIN_D2,
                    RoadLayout.CHOIR_WALK_MAX_D2,
                    RoadLayout.CHOIR_WALK_MIN_Y,
                    RoadLayout.CHOIR_WALK_MAX_Y,
                    RoadLayout.RELIQUARY_X,
                    RoadLayout.RELIQUARY_Y,
                    RoadLayout.RELIQUARY_Z,
                    RoadLayout.HEART_X,
                    RoadLayout.HEART_Y,
                    RoadLayout.HEART_Z,
                    RoadLayout.ARENA_RADIUS,
                    RoadLayout.ARENA_MIN_Y,
                    RoadLayout.ARENA_MAX_Y,
                    RoadLayout.COLUMN_HALF_WIDTH,
                    RoadLayout.COLUMN_MIN_Y,
                    RoadLayout.COLUMN_MAX_Y);

    private RoadAddon() {}

    /** Subscribes a listener per Road event. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(RoadStepPassedEvent.class, e -> read(e.payload()));
        gameBus.addListener(RoadEncounterEvent.class, e -> read(e.payload()));
        gameBus.addListener(EndingChosenEvent.class, e -> read(e.payload()));
        VftvAddon.consume(CONSTANTS);
    }

    /** Reads every accessor, as an addon would. */
    private static void read(RoadStepPassedPayload p) {
        VftvAddon.consume(p.player(), p.step(), p.progress());
    }

    /** Reads every accessor, as an addon would. */
    private static void read(RoadEncounterPayload p) {
        VftvAddon.consume(p.step(), p.outcome(), p.participants());
    }

    /** Reads every accessor, as an addon would. */
    private static void read(EndingChosenPayload p) {
        VftvAddon.consume(p.player(), p.ending(), p.warpChange());
    }
}
