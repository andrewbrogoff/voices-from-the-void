package com.andrewbrogoff.vftv.testaddon;

import com.andrewbrogoff.vftv.api.aspect.AspectDiscoveredPayload;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.aspect.AspectLimits;
import com.andrewbrogoff.vftv.api.aspect.AspectValues;
import com.andrewbrogoff.vftv.api.aspect.ScanKey;
import com.andrewbrogoff.vftv.api.aspect.ThingScannedPayload;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDataMaps;
import com.andrewbrogoff.vftv.api.neoforge.aspect.AspectDiscoveredEvent;
import com.andrewbrogoff.vftv.api.neoforge.aspect.ThingScannedEvent;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.bus.api.IEventBus;

/**
 * The example addon's use of the aspect API: the scan and discovery events, and an aspect value.
 */
final class AspectAddon {
    /** Every Stable constant of the feature, referenced so a rename breaks this build. */
    static final List<Object> CONSTANTS =
            List.of(
                    AspectKeys.REGISTRY,
                    AspectKeys.AER,
                    AspectKeys.TERRA,
                    AspectKeys.IGNIS,
                    AspectKeys.AQUA,
                    AspectKeys.ORDO,
                    AspectKeys.PERDITIO,
                    AspectKeys.VACUOS,
                    AspectKeys.LUX,
                    AspectKeys.GELUM,
                    AspectKeys.MOTUS,
                    AspectKeys.POTENTIA,
                    AspectKeys.VICTUS,
                    AspectKeys.VITREUS,
                    AspectKeys.VENENUM,
                    AspectKeys.PERMUTATIO,
                    AspectKeys.SONUS,
                    AspectKeys.METALLUM,
                    AspectKeys.TENEBRAE,
                    AspectKeys.HERBA,
                    AspectKeys.BESTIA,
                    AspectKeys.MORTUUS,
                    AspectKeys.SPIRITUS,
                    AspectKeys.PRAECANTATIO,
                    AspectKeys.FAMES,
                    AspectKeys.INSTRUMENTUM,
                    AspectKeys.SILENTIUM,
                    AspectKeys.ARBOR,
                    AspectKeys.SANO,
                    AspectKeys.CORPUS,
                    AspectKeys.EXANIMIS,
                    AspectKeys.COGNITIO,
                    AspectKeys.HUMANUS,
                    AspectKeys.TELUM,
                    AspectKeys.TUTAMEN,
                    AspectKeys.PANNUS,
                    AspectKeys.MACHINA,
                    AspectKeys.ALIENIS,
                    AspectKeys.ASPECTS,
                    AspectKeys.ASPECT_DEFAULTS,
                    AspectKeys.KNOWLEDGE_ATTACHMENT,
                    AspectLimits.MAX_ASPECTS_PER_ENTRY,
                    AspectLimits.MAX_DEPTH,
                    AspectLimits.MAX_FAN_OUT,
                    AspectLimits.MIN_TIER_ONE_CHILDREN,
                    AspectLimits.MAX_SOUNDING_RADIUS,
                    AspectDataMaps.ITEM,
                    AspectDataMaps.BLOCK,
                    AspectDataMaps.ENTITY_TYPE,
                    AspectDataMaps.DEFAULTS);

    private AspectAddon() {}

    /** Subscribes a listener per event and constructs the feature's addon-owned value. */
    static void register(IEventBus modBus, IEventBus gameBus) {
        gameBus.addListener(ThingScannedEvent.class, e -> read(e.payload()));
        gameBus.addListener(AspectDiscoveredEvent.class, e -> read(e.payload()));
        VftvAddon.consume(values());
    }

    /** An addon's own aspect value, as a data-map entry would hold it. */
    private static AspectValues values() {
        return new AspectValues(new TreeMap<>(Map.of(AspectKeys.IGNIS, 3, AspectKeys.METALLUM, 2)));
    }

    /** Reads every accessor, as an addon would. */
    private static void read(ThingScannedPayload p) {
        VftvAddon.consume(
                p.player(), p.kind(), p.outcome(), p.complete(), p.points(), p.key().toString());
        switch (p.key()) {
            case ScanKey.Item k -> VftvAddon.consume(k.item());
            case ScanKey.Block k -> VftvAddon.consume(k.block());
            case ScanKey.Entity k -> VftvAddon.consume(k.entity());
            case ScanKey.Node k -> VftvAddon.consume(k.id());
        }
    }

    /** Reads every accessor, as an addon would. */
    private static void read(AspectDiscoveredPayload p) {
        VftvAddon.consume(p.player(), p.aspect(), p.bonus());
    }
}
