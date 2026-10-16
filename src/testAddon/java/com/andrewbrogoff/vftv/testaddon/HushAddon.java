package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.hush.FluxPayload;
import com.andrewbrogoff.vftv.api.hush.HushChange;
import com.andrewbrogoff.vftv.api.hush.HushKeys;
import com.andrewbrogoff.vftv.api.hush.HushLimits;
import com.andrewbrogoff.vftv.api.hush.HushOrigin;
import com.andrewbrogoff.vftv.api.hush.HushSpreadPayload;
import com.andrewbrogoff.vftv.api.hush.HushedForm;
import com.andrewbrogoff.vftv.api.neoforge.hush.FluxEvent;
import com.andrewbrogoff.vftv.api.neoforge.hush.HushSpreadEvent;
import java.util.List;
import net.neoforged.bus.api.IEventBus;

/** The example addon's use of the Hush API: a listener per event reading every accessor. */
final class HushAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    HushKeys.HUSHED,
                    HushKeys.HUSH_IMMUNE,
                    HushKeys.HUSHED_NODE,
                    HushKeys.HOLLOW,
                    HushKeys.AURA_ATTACHMENT,
                    HushKeys.FORMS,
                    HushLimits.FLUX_FORMAT_MAX,
                    HushLimits.MAX_SPILL_FLUX,
                    HushLimits.MAX_ESSENTIA_SPILL,
                    HushLimits.MAX_PENDING,
                    HushLimits.MAX_HUSHED_NODES,
                    HushLimits.MAX_POPS_PER_TICK,
                    HushLimits.MAX_VISITS_PER_TICK,
                    HushLimits.MAX_SPREAD_MUTATIONS_PER_TICK,
                    HushLimits.MAX_PEAL_POPS_PER_TICK,
                    HushLimits.MAX_PEALS_PER_TICK,
                    HushLimits.MAX_RING_ATTEMPTS_PER_PLAYER_PER_TICK,
                    HushLimits.MAX_WALK,
                    HushLimits.MAX_RADIUS,
                    HushLimits.MAX_BELL_RADIUS,
                    HushLimits.MAX_PEAL_SCAN,
                    HushLimits.MAX_COMMAND_CELLS,
                    HushLimits.MAX_COMMAND_MUTATIONS);

    private HushAddon() {}

    /** Subscribes a listener per event and reads the fixed form table. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        for (HushedForm form : HushKeys.FORMS) {
            VftvAddon.consume(form.source(), form.form());
        }
        gameBus.addListener(HushSpreadEvent.class, e -> read(e.payload()));
        gameBus.addListener(FluxEvent.class, e -> read(e.payload()));
    }

    /** Reads every accessor of a Hush mutation, every change and origin variant included. */
    private static void read(HushSpreadPayload p) {
        VftvAddon.consume(p.kind(), p.level(), p.pos());
        switch (p.change()) {
            case HushChange.Block block -> VftvAddon.consume(block.before(), block.after());
            case HushChange.Node node -> VftvAddon.consume(node.id(), node.before(), node.after());
            case HushChange.Rise rise -> VftvAddon.consume(rise.type());
        }
        switch (p.origin()) {
            case HushOrigin.Seep seep -> VftvAddon.consume(seep.chunk(), seep.spiller());
            case HushOrigin.Bell bell -> VftvAddon.consume(bell.bell());
        }
    }

    /** Reads every accessor of each flux variant. */
    private static void read(FluxPayload p) {
        switch (p) {
            case FluxPayload.Added added -> {
                VftvAddon.consume(
                        added.level(),
                        added.chunk(),
                        added.seep(),
                        added.generated(),
                        added.retained(),
                        added.pending(),
                        added.overflow(),
                        added.player());
                FluxAddon.read(added.source());
            }
            case FluxPayload.Cleared cleared -> {
                VftvAddon.consume(
                        cleared.level(),
                        cleared.chunk(),
                        cleared.seep(),
                        cleared.amount(),
                        cleared.source(),
                        cleared.player(),
                        cleared.fluxBefore(),
                        cleared.fluxAfter());
            }
        }
    }
}
