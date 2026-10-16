package com.andrewbrogoff.vftv.core.flux;

import com.andrewbrogoff.vftv.api.flux.FluxRules;
import com.andrewbrogoff.vftv.core.flux.model.FluxTuning;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import org.slf4j.Logger;

/**
 * The server's {@link FluxRules}: the tuning loaded from {@code data/vftv/tuning/hush.json} with
 * the published {@link HushConfig}. Written on the server thread (or the world loader, before the
 * server exists) and read anywhere.
 *
 * <p>The rollback rule (Hush spec, "Tuning and limits"): an invalid file refuses the world at open;
 * on {@code /reload} the previous tuning is kept and an error logged.
 */
public final class FluxRulesService {
    /** The tuning file, {@code data/vftv/tuning/hush.json}. */
    public static final String TUNING_PATH = "tuning/hush.json";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile int[] tuning = FluxTuning.defaults();
    private static volatile HushConfig config = HushConfig.DEFAULT;
    private static volatile FluxRules current = build();

    private FluxRulesService() {}

    /** The rules in force. */
    public static FluxRules current() {
        return current;
    }

    /** The server switches in force. */
    public static HushConfig config() {
        return config;
    }

    /** Swaps the server switches, as the config publisher does. */
    public static synchronized void installConfig(HushConfig next) {
        config = next;
        current = build();
    }

    /** Reads a tuning file's text: its values in key order, or why it is invalid. */
    public static DataResult<int[]> parse(String json) {
        try {
            return DataResult.success(FluxTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read. A valid one replaces the tuning. An invalid one throws while the world
     * opens, which refuses it; on {@code /reload} it logs an error and keeps the previous tuning.
     *
     * @param reloading whether a server is already running, so this is a {@code /reload}
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(DataResult<int[]> read, boolean reloading) {
        read.ifSuccess(
                        values -> {
                            tuning = values;
                            current = build();
                        })
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous flux tuning", message);
                        });
    }

    private static FluxRules build() {
        HushConfig c = config;
        return FluxTuning.rules(tuning, c.spread(), c.hollows(), c.unattended());
    }
}
