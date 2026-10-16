package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.neoforge.vis.NodeEvent;
import com.andrewbrogoff.vftv.api.neoforge.vis.VisSpentEvent;
import com.andrewbrogoff.vftv.api.vis.NodePayload;
import com.andrewbrogoff.vftv.api.vis.NodeTypeKeys;
import com.andrewbrogoff.vftv.api.vis.VisContext;
import com.andrewbrogoff.vftv.api.vis.VisKeys;
import com.andrewbrogoff.vftv.api.vis.VisLimits;
import com.andrewbrogoff.vftv.api.vis.VisQuote;
import com.andrewbrogoff.vftv.api.vis.VisSpentPayload;
import com.andrewbrogoff.vftv.api.vis.WandPartKeys;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the vis API: the wand and node keys, the limits and both events. */
final class VisAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    VisKeys.WAND_ROD_REGISTRY,
                    VisKeys.WAND_CAP_REGISTRY,
                    VisKeys.NODE_TYPE_REGISTRY,
                    VisKeys.VIS_DISCOUNT,
                    VisKeys.VIS_COMPONENT,
                    VisKeys.WAND_ROD_COMPONENT,
                    VisKeys.WAND_CAP_COMPONENT,
                    VisKeys.WAND_FOCUS_COMPONENT,
                    VisKeys.CAPTURED_NODE_COMPONENT,
                    WandPartKeys.WOODEN_ROD,
                    WandPartKeys.CHIME_ROD,
                    WandPartKeys.ECHO_ROD,
                    WandPartKeys.VOID_ROD,
                    WandPartKeys.COPPER_CAP,
                    WandPartKeys.RESONANT_BRASS_CAP,
                    WandPartKeys.ECHO_CAP,
                    WandPartKeys.VOID_CAP,
                    NodeTypeKeys.WELLSPRING,
                    VisLimits.CELL_SIZE,
                    VisLimits.MAX_NODES_PER_CELL,
                    VisLimits.MAX_NATURAL_NODES_PER_CELL,
                    VisLimits.MAX_STRUCTURE_NODES_PER_CELL,
                    VisLimits.MAX_NODE_ASPECTS,
                    VisLimits.MAX_VIS_ASPECTS,
                    VisLimits.MAX_NETWORK_MEMBERS,
                    VisLimits.STARTER_MAX_DISTANCE,
                    VisLimits.MAX_NEAREST_RADIUS,
                    VisLimits.USE_DURATION_TICKS,
                    VisLimits.MAX_SLOT_CHANGES,
                    VisLimits.MIN_BASE_COST,
                    VisLimits.MAX_BASE_COST,
                    VisLimits.MIN_ROD_CAPACITY,
                    VisLimits.MAX_ROD_CAPACITY,
                    VisLimits.MIN_COST_MULTIPLIER,
                    VisLimits.MAX_COST_MULTIPLIER,
                    VisLimits.MAX_VIS_ENTRIES,
                    VisLimits.MAX_AMOUNT,
                    VisLimits.MAX_CULTIVATION_COUNTER,
                    VisLimits.MAX_CULTIVATION_SONUS,
                    VisLimits.MAX_RELAY_RECORD_MEMBERS,
                    VisLimits.HUD_FRAME_BORDER,
                    VisLimits.HUD_PADDING,
                    VisLimits.HUD_ROW_HEIGHT,
                    VisLimits.HUD_ROW_GAP,
                    VisLimits.HUD_ICON_X,
                    VisLimits.HUD_NUMBER_X,
                    VisLimits.HUD_FRAME_WIDTH,
                    VisLimits.HUD_MAX_FRAME_WIDTH,
                    VisLimits.HUD_WRAP_WIDTH);

    private VisAddon() {}

    /** Subscribes a listener per event. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(VisSpentEvent.class, e -> read(e.payload()));
        gameBus.addListener(NodeEvent.class, e -> read(e.payload()));
    }

    /** Reads every accessor, as an addon would. */
    private static void read(VisSpentPayload p) {
        VisContext c = p.context();
        VisQuote q = p.quote();
        VftvAddon.consume(p.player(), p.level(), p.pos(), c.source(), c.kind(), c.research());
        VftvAddon.consume(
                q.base(),
                q.effective(),
                q.m(),
                q.spill(),
                q.cap(),
                q.discount(),
                q.warp(),
                q.discountCapped(),
                q.clamped(),
                q.exceedsCapacity(),
                q.inert(),
                q.affordable(),
                q.shortAspects());
    }

    /** Reads every accessor, as an addon would. */
    private static void read(NodePayload p) {
        VftvAddon.consume(p.kind(), p.node().id(), p.node().where(), p.cause());
    }
}
