package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.NodePayload;
import com.andrewbrogoff.vftv.api.vis.VisSpentPayload;
import com.mojang.logging.LogUtils;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Where the vis feature reports committed spends and node changes: the platform installs publishers
 * that post {@code VisSpentEvent} and {@code NodeEvent}, which {@code core} never sees (wands spec,
 * "Events"). A publisher lets a listener's exception through, so the caller can log it once and
 * report {@code COMMITTED_NOTIFY_FAILED}.
 */
public final class VisEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Consumer<VisSpentPayload> spent = p -> {};
    private static volatile Consumer<NodePayload> node = p -> {};

    private static volatile Protection protection =
            new Protection() {
                @Override
                public boolean mayBreak(
                        ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
                    return true;
                }

                @Override
                public boolean mayPlace(
                        ServerPlayer player,
                        ServerLevel level,
                        BlockPos pos,
                        BlockState against,
                        Runnable install) {
                    install.run();
                    return true;
                }
            };

    /**
     * The platform's protection events, which claim mods answer (wands spec, "Moving nodes: the
     * Bell Jar"). Each posts its event on the server thread and reports whether it went through.
     */
    public interface Protection {
        /** Posts the break event for a capture; false when it was cancelled. */
        boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);

        /**
         * Snapshots {@code pos}, runs {@code install} (the provisional install), then posts the
         * placement event with that snapshot; false when it was cancelled.
         */
        boolean mayPlace(
                ServerPlayer player,
                ServerLevel level,
                BlockPos pos,
                BlockState against,
                Runnable install);
    }

    private VisEvents() {}

    /** Installs the protection events; called once from the platform's wiring. */
    public static void installProtection(Protection next) {
        protection = next;
    }

    /** The protection events in force. */
    static Protection protection() {
        return protection;
    }

    /** Installs the publishers; called once from the platform's wiring. */
    public static void install(Consumer<VisSpentPayload> onSpent, Consumer<NodePayload> onNode) {
        spent = onSpent;
        node = onNode;
    }

    /** Posts a spend; returns false (after logging once) when a listener threw. */
    static boolean spent(VisSpentPayload payload) {
        try {
            spent.accept(payload);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error(
                    "A VisSpentEvent listener threw for {}; the spend stands",
                    payload.context().source(),
                    e);
            return false;
        }
    }

    /** Posts a node change; returns false (after logging once) when a listener threw. */
    static boolean node(NodePayload payload) {
        try {
            node.accept(payload);
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("A NodeEvent listener threw for {}; the change stands", payload, e);
            return false;
        }
    }
}
