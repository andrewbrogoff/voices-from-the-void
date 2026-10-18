package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.core.common.Network;
import com.andrewbrogoff.vftv.core.vis.model.VisTuning;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The vis tuning and switch in force: on the server, {@code data/vftv/tuning/vis.json} and the
 * published {@link VisConfig}; on a client, the last {@link VisRules} received, absent before the
 * first and after disconnect.
 *
 * <p>The rollback rule (wands spec, "Tuning"): an invalid file refuses the world at open; on {@code
 * /reload} the previous record is kept and an error logged.
 */
public final class VisRulesService {
    /** The tuning file, {@code data/vftv/tuning/vis.json}. */
    public static final String TUNING_PATH = "tuning/vis.json";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile VisTuning tuning = VisTuning.DEFAULTS;
    private static volatile VisConfig config = VisConfig.DEFAULT;
    private static volatile @Nullable VisRules client;
    private static int clientRevision; // client thread only
    private static @Nullable Network network;

    private VisRulesService() {}

    static void init(Network seam) {
        network = seam;
        seam.clientbound(VisRulesPayload.TYPE, VisRulesPayload.STREAM_CODEC);
    }

    /** The server's tuning in force. */
    public static VisTuning tuning() {
        return tuning;
    }

    /** The tuning a side reads: the server's, or the client's copy (defaults before it arrives). */
    public static VisTuning tuning(Level level) {
        if (!level.isClientSide()) {
            return tuning;
        }
        VisRules rules = client;
        return rules == null ? VisTuning.DEFAULTS : rules.tuning();
    }

    /** The server's rules in force: the tuning and the live {@code starter} flag. */
    public static VisRules current(MinecraftServer server) {
        return new VisRules(tuning, NodeService.starterPlaced(server));
    }

    /** The server switch in force. */
    public static VisConfig config() {
        return config;
    }

    /** Swaps the server switch, as the config publisher does. */
    public static void installConfig(VisConfig next) {
        config = next;
    }

    /** Sends the rules in force to one player. */
    public static void sendTo(ServerPlayer player) {
        Network seam = network;
        if (seam == null) {
            throw new IllegalStateException("VisFeature.init has not run");
        }
        seam.toPlayer(player, new VisRulesPayload(current(player.level().getServer())));
    }

    /** Sends the rules in force to every player, as when the {@code starter} flag changes. */
    public static void sendToAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(VisRulesService::sendTo);
    }

    /** Reads a tuning file's text, or why it is invalid. */
    public static DataResult<VisTuning> parse(String json) {
        try {
            return DataResult.success(VisTuning.parse(json));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    /**
     * Applies a tuning read. An invalid one throws while the world opens; on {@code /reload} it
     * logs an error and keeps the previous tuning.
     *
     * @param reloading whether a server is already running
     * @throws IllegalStateException for an invalid read while the world opens
     */
    public static synchronized void applyTuning(DataResult<VisTuning> read, boolean reloading) {
        read.ifSuccess(t -> tuning = t)
                .ifError(
                        error -> {
                            String message =
                                    "Invalid data/vftv/" + TUNING_PATH + ": " + error.message();
                            if (!reloading) {
                                throw new IllegalStateException(message);
                            }
                            LOGGER.error("{}; keeping the previous vis tuning", message);
                        });
    }

    /** The client's copy, or null before the first arrives and after disconnect. */
    public static @Nullable VisRules client() {
        return client;
    }

    /** Bumped whenever the client's copy changes, so client caches can rebuild. */
    public static int clientRevision() {
        return clientRevision;
    }

    /** Takes a received copy, on the client. */
    public static void receive(VisRules received) {
        client = received;
        clientRevision++;
    }

    /** Drops the client's copy, at disconnect. */
    public static void clearClient() {
        client = null;
        clientRevision++;
    }
}
