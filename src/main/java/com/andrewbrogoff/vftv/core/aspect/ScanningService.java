package com.andrewbrogoff.vftv.core.aspect;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.core.aspect.model.AspectGraph;
import com.andrewbrogoff.vftv.core.aspect.model.ScanningTuning;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.core.HolderLookup;
import org.slf4j.Logger;

/**
 * The server's scanning tuning, {@code data/vftv/tuning/scanning.json}, and the world-open check of
 * the aspect graph. Server-only: the readout hold reaches the client inside each {@code
 * ScanResult}.
 *
 * <p>The rollback rule (aspects spec, "Tuning"): an invalid file refuses the world at open; on
 * {@code /reload} the previous record is kept and an error logged.
 */
public final class ScanningService {
    /** The tuning file, under {@code data/vftv/}. */
    public static final String TUNING_PATH = "tuning/scanning.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ScanningTuning tuning = ScanningTuning.DEFAULTS;

    private ScanningService() {}

    /** The tuning in force. */
    public static ScanningTuning tuning() {
        return tuning;
    }

    /** Reads a tuning file's text, or why it is invalid. */
    public static DataResult<ScanningTuning> parse(String json) {
        try {
            return DataResult.success(ScanningTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read. An invalid one throws while the world opens, which refuses it; on
     * {@code /reload} it logs an error and keeps the previous tuning.
     *
     * @param reloading whether a server is already running, so this is a {@code /reload}
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(
            DataResult<ScanningTuning> read, boolean reloading) {
        read.ifSuccess(t -> tuning = t)
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous scanning tuning", message);
                        });
    }

    /**
     * Checks the aspect graph when a world opens (aspects spec, "Graph rules"): a fatal problem
     * refuses the world with every fatal problem named; a soft one logs a warning.
     *
     * @throws IllegalStateException listing the fatal problems
     */
    public static void checkGraph(HolderLookup.RegistryLookup<Aspect> registry) {
        List<AspectGraph.Problem> problems = AspectSet.of(registry).validate();
        List<String> fatal =
                problems.stream()
                        .filter(AspectGraph.Problem::fatal)
                        .map(AspectGraph.Problem::message)
                        .toList();
        if (!fatal.isEmpty()) {
            throw new IllegalStateException(
                    "The vftv:aspect registry is invalid: " + String.join("; ", fatal));
        }
        problems.forEach(p -> LOGGER.warn("vftv:aspect: {}", p.message()));
        registry.listElements()
                .forEach(
                        holder -> {
                            if (holder.value() instanceof AspectEntry entry) {
                                entry.bind(holder.key());
                            }
                        });
    }
}
