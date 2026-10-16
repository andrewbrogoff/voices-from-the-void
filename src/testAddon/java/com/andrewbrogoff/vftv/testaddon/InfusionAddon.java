package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.infusion.InfusionCost;
import com.andrewbrogoff.vftv.api.infusion.InfusionKeys;
import com.andrewbrogoff.vftv.api.infusion.InfusionLimits;
import com.andrewbrogoff.vftv.api.infusion.InfusionPayload;
import com.andrewbrogoff.vftv.api.infusion.InfusionPlan;
import com.andrewbrogoff.vftv.api.infusion.UpsetEffect;
import com.andrewbrogoff.vftv.api.neoforge.infusion.InfusionEvent;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the infusion API: a listener reading every infusion payload. */
final class InfusionAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    InfusionKeys.INFUSION_STABILISERS,
                    InfusionKeys.INFUSION_UNENCHANTABLE,
                    InfusionLimits.BOX_RADIUS,
                    InfusionLimits.BOX_BELOW,
                    InfusionLimits.BOX_ABOVE,
                    InfusionLimits.MAX_PEDESTALS,
                    InfusionLimits.MAX_COMPONENTS,
                    InfusionLimits.MAX_SOURCES,
                    InfusionLimits.MAX_INFUSION_RECIPES,
                    InfusionLimits.MAX_COST_ENTRIES,
                    InfusionLimits.MIN_COST,
                    InfusionLimits.MAX_COST,
                    InfusionLimits.MAX_INSTABILITY,
                    InfusionLimits.MAX_WARP_PENALTY,
                    InfusionLimits.MAX_STEPS_PER_TICK,
                    InfusionLimits.MAX_QUEUE_POPS_PER_TICK,
                    InfusionLimits.MAX_ARM_REFRESHES_PER_TICK,
                    InfusionLimits.MAX_ASSESSMENTS_PER_TICK,
                    InfusionLimits.ASSESS_TTL,
                    InfusionLimits.HELD_NOW_MAX,
                    InfusionLimits.MAX_RUNS,
                    InfusionLimits.MAX_ROLLS,
                    InfusionLimits.MAX_ID_CHARS,
                    InfusionLimits.MAX_UPSET_SPILL_TOTAL);

    private InfusionAddon() {}

    /** Subscribes a listener per event. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(InfusionEvent.class, e -> read(e.payload()));
    }

    /** Reads every accessor of every variant, as an addon would. */
    private static void read(InfusionPayload payload) {
        switch (payload) {
            case InfusionPayload.Started s -> {
                VftvAddon.consume(s.level(), s.pos(), s.bowlId(), s.seq(), s.starter(), s.recipe());
                read(s.plan());
            }
            case InfusionPayload.Upset u -> {
                VftvAddon.consume(u.level(), u.pos(), u.seq());
                read(u.effect());
            }
            case InfusionPayload.Spilled s ->
                    VftvAddon.consume(s.level(), s.pos(), s.aspects(), s.cause());
            case InfusionPayload.Completed c ->
                    VftvAddon.consume(
                            c.level(), c.pos(), c.seq(), c.starter(), c.recipe(), c.output());
            case InfusionPayload.Abandoned a ->
                    VftvAddon.consume(
                            a.level(), a.pos(), a.seq(), a.cause(), a.player(), a.drawn());
        }
    }

    private static void read(InfusionPlan p) {
        VftvAddon.consume(
                p.recipe(),
                p.kind(),
                p.output(),
                p.instability(),
                p.warpPenalty(),
                p.lonePedestals(),
                p.loneItems(),
                p.stabiliserPairs(),
                p.stabiliserBonus(),
                p.bonusCap(),
                p.stability(),
                p.upsetChance(),
                p.nominal(),
                p.worst(),
                p.ceiling(),
                p.cleanFloor(),
                p.cleanFullSupply(),
                p.upsetMax(),
                p.upsetSpill(),
                p.upsetFluxBound(),
                p.stabilisersCapped(),
                p.clamped());
        for (InfusionCost cost : p.costs()) {
            VftvAddon.consume(cost.aspect(), cost.cost(), cost.heldNow());
        }
    }

    private static void read(UpsetEffect effect) {
        switch (effect) {
            case UpsetEffect.Jolt j -> VftvAddon.consume(j.pedestal(), j.item());
            case UpsetEffect.Spill s -> VftvAddon.consume(s.aspect(), s.amount());
            case UpsetEffect.None n -> VftvAddon.consume(n);
        }
    }
}
