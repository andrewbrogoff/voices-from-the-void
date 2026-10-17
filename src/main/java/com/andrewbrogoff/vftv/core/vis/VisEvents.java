package com.andrewbrogoff.vftv.core.vis;

import com.andrewbrogoff.vftv.api.vis.NodePayload;
import com.andrewbrogoff.vftv.api.vis.VisSpentPayload;
import com.mojang.logging.LogUtils;
import java.util.function.Consumer;
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

    private VisEvents() {}

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
