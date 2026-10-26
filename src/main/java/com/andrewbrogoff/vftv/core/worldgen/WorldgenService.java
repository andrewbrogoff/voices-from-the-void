package com.andrewbrogoff.vftv.core.worldgen;

import com.andrewbrogoff.vftv.api.worldgen.ChoirSite;
import com.andrewbrogoff.vftv.api.worldgen.WorldgenKeys;
import com.andrewbrogoff.vftv.core.research.ResearchService;
import com.andrewbrogoff.vftv.core.research.model.ResearchEntry;
import com.andrewbrogoff.vftv.core.worldgen.model.Offers;
import com.andrewbrogoff.vftv.core.worldgen.model.SiteRules;
import com.andrewbrogoff.vftv.core.worldgen.model.WorldgenTuning;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import org.slf4j.Logger;

/**
 * The worldgen feature's server state: the tuning record and the site kinds' load validation when a
 * world opens (worldgen spec, "Site kinds" and "Tuning and limits").
 */
public final class WorldgenService {
    /** The tuning file, under {@code data/vftv/}. */
    public static final String TUNING_PATH = "tuning/worldgen.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile WorldgenTuning tuning = WorldgenTuning.DEFAULTS;

    private WorldgenService() {}

    /** The tuning in force. */
    public static WorldgenTuning tuning() {
        return tuning;
    }

    /** Parses a tuning file. */
    public static DataResult<WorldgenTuning> parse(String json) {
        try {
            return DataResult.success(WorldgenTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read: an invalid one refuses the world at open and keeps the previous record
     * on {@code /reload}.
     *
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(
            DataResult<WorldgenTuning> read, boolean reloading) {
        read.ifSuccess(t -> tuning = t)
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous worldgen tuning", message);
                        });
    }

    /** Resets the tuning when the server stops. */
    public static void onServerStopped() {
        tuning = WorldgenTuning.DEFAULTS;
    }

    /** Every site kind, by id. */
    public static Map<String, Offers.Site> sites(HolderLookup.Provider access) {
        Map<String, Offers.Site> out = new TreeMap<>();
        access.lookupOrThrow(WorldgenKeys.CHOIR_SITE)
                .listElements()
                .forEach(
                        h -> {
                            if (h.value() instanceof WorldgenCodecs.SiteEntry e) {
                                out.put(
                                        h.key().identifier().toString(),
                                        new Offers.Site(e.pageIds(), e.pagesPerClaim()));
                            }
                        });
        return out;
    }

    /** Every research entry as the site rules read it. */
    public static Map<String, SiteRules.Entry> entries(HolderLookup.Provider access) {
        Map<String, SiteRules.Entry> out = new TreeMap<>();
        for (Map.Entry<String, ResearchEntry> e : ResearchService.entries(access).entrySet()) {
            ResearchEntry r = e.getValue();
            out.put(e.getKey(), new SiteRules.Entry(r.category(), r.stage(), r.kind().id()));
        }
        return out;
    }

    /**
     * The load validation when a world opens: refuses it, naming every entry, when a rule breaks;
     * the built-in kind's own rules only warn here (the tests assert them).
     *
     * @throws IllegalStateException naming every problem
     */
    public static void validateAtOpen(HolderLookup.Provider access) {
        Map<String, Offers.Site> sites = sites(access);
        Map<String, SiteRules.Entry> entries = entries(access);
        List<String> problems = SiteRules.check(sites, entries);
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid vftv choir site data:\n" + String.join("\n", problems));
        }
        SiteRules.builtIn(
                        java.util.Objects.requireNonNull(sites.get(SiteRules.CHOIR_RUIN)), entries)
                .forEach(p -> LOGGER.warn("Choir site data: {}", p));
    }

    /** A site kind, or null when the id names none (inert). */
    static Offers.@org.jspecify.annotations.Nullable Site site(
            HolderLookup.Provider access, net.minecraft.resources.ResourceKey<ChoirSite> key) {
        return access.lookupOrThrow(WorldgenKeys.CHOIR_SITE)
                .get(key)
                .map(h -> h.value() instanceof WorldgenCodecs.SiteEntry e ? e : null)
                .map(e -> new Offers.Site(e.pageIds(), e.pagesPerClaim()))
                .orElse(null);
    }
}
