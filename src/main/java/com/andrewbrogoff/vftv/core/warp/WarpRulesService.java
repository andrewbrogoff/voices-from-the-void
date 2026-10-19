package com.andrewbrogoff.vftv.core.warp;

import com.andrewbrogoff.vftv.api.warp.WarpRules;
import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.warp.model.WarpTuning;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The {@link WarpRules} in force: on the server, the tuning loaded from {@code
 * data/vftv/tuning/warp.json} with the published {@link WarpConfig}; on a client, the last copy
 * received, absent before the first and after disconnect (conventions, "Rules records").
 *
 * <p>The rollback rule (warp spec, "Tuning and limits"): an invalid file refuses the world at open;
 * on {@code /reload} the previous record is kept and an error logged.
 */
public final class WarpRulesService {
    /** The tuning file, {@code data/vftv/tuning/warp.json}. */
    public static final String TUNING_PATH = "tuning/warp.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Function<WarpRules, List<String>>> CHECKS =
            new CopyOnWriteArrayList<>();

    private static volatile WarpRules tuning = WarpTuning.DEFAULTS;
    private static volatile WarpConfig config = WarpConfig.DEFAULT;
    private static volatile WarpRules current = build();
    private static volatile @Nullable WarpRules client;
    private static @Nullable Network network;

    private WarpRulesService() {}

    /** Takes the network seam the rules are sent through; called once from the feature's init. */
    static void init(Network seam) {
        network = seam;
    }

    /** Sends the rules in force to one player, as on join and after {@code /reload}. */
    public static void sendTo(ServerPlayer player) {
        Network seam = network;
        if (seam == null) {
            throw new IllegalStateException("WarpFeature.init has not run");
        }
        seam.toPlayer(player, new WarpRulesPayload(current));
    }

    /** Sends the rules in force to every player, as after a changed warp switch. */
    public static void sendToAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(WarpRulesService::sendTo);
    }

    /** The server's rules in force. */
    public static WarpRules current() {
        return current;
    }

    /** The server switches in force. */
    public static WarpConfig config() {
        return config;
    }

    /** Swaps the server switches, as the config publisher does. */
    public static synchronized void installConfig(WarpConfig next) {
        config = next;
        current = build();
    }

    /** Reads a tuning file's text: the rules it gives, or why it is invalid. */
    public static DataResult<WarpRules> parse(String json) {
        try {
            return DataResult.success(WarpTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read. A valid one replaces the tuning. An invalid one throws while the world
     * opens, which refuses it; on {@code /reload} it logs an error and keeps the previous record.
     *
     * @param reloading whether a server is already running, so this is a {@code /reload}
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(DataResult<WarpRules> read, boolean reloading) {
        read.flatMap(WarpRulesService::checked)
                .ifSuccess(
                        rules -> {
                            tuning = rules;
                            current = build();
                        })
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous warp tuning", message);
                        });
    }

    /**
     * Adds a check a new tuning must pass beyond its own file: the research feature's milestone
     * rules (warp spec, "Permanent warp"), registered at init so warp imports no research. Each
     * returns its problems, empty when the tuning is acceptable.
     */
    public static void registerTuningCheck(Function<WarpRules, List<String>> check) {
        CHECKS.add(check);
    }

    private static DataResult<WarpRules> checked(WarpRules rules) {
        List<String> problems = CHECKS.stream().flatMap(c -> c.apply(rules).stream()).toList();
        return problems.isEmpty()
                ? DataResult.success(rules)
                : DataResult.error(() -> String.join("; ", problems));
    }

    /** The client's copy, or null before the first arrives and after disconnect. */
    public static @Nullable WarpRules client() {
        return client;
    }

    /** Takes a received copy, on the client. */
    public static void receive(WarpRules received) {
        client = received;
    }

    /** Drops the client's copy, at disconnect. */
    public static void clearClient() {
        client = null;
    }

    private static WarpRules build() {
        WarpConfig c = config;
        return WarpTuning.withSwitches(tuning, c.voices(), c.tax(), c.amplifier(), c.gear());
    }
}
