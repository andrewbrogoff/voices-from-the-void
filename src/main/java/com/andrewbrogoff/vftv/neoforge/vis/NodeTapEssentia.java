package com.andrewbrogoff.vftv.neoforge.vis;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.core.vis.NodeBlockEntity;
import com.andrewbrogoff.vftv.core.vis.NodeState;
import com.andrewbrogoff.vftv.core.vis.NodeTaps;
import com.andrewbrogoff.vftv.core.vis.RelayBlockEntity;
import com.andrewbrogoff.vftv.core.vis.VisRulesService;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The Node Tap's essentia handler and suction (wands spec, "Cultivation"): insert-only, holding
 * nothing extractable (no slots, hold 0). The first insert in a transaction snapshots the node's
 * state through this journal, then settles; an abort restores the snapshot exactly; the root
 * commit publishes once. One instance per tap block entity.
 */
final class NodeTapEssentia implements ResourceHandler<EssentiaResource>, EssentiaSuction {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A snapshot: the node and its state, with identity so two depths stay two snapshots. */
    private static final class Snap {
        final NodeBlockEntity node;
        final NodeState state;

        Snap(NodeBlockEntity node, NodeState state) {
            this.node = node;
            this.state = state;
        }
    }

    private final RelayBlockEntity tap;
    private @Nullable NodeBlockEntity target;
    private final SnapshotJournal<Snap> journal =
            new SnapshotJournal<>() {
                @Override
                protected Snap createSnapshot() {
                    NodeBlockEntity node = java.util.Objects.requireNonNull(target);
                    return new Snap(node, java.util.Objects.requireNonNull(node.state()));
                }

                @Override
                protected void revertToSnapshot(Snap snapshot) {
                    NodeTaps.restore(snapshot.node, snapshot.state);
                }

                @Override
                protected void onRootCommit(Snap original) {
                    try {
                        if (tap.getLevel() instanceof ServerLevel level) {
                            NodeTaps.committed(level, original.node, original.state);
                        }
                    } catch (RuntimeException e) {
                        // Publication is never part of correctness.
                        LOGGER.error("A Node Tap's publication failed at {}", tap.getBlockPos(), e);
                    }
                }
            };

    private NodeTapEssentia(RelayBlockEntity tap) {
        this.tap = tap;
    }

    /** The handler of one tap block entity, bound to that instance for its life. */
    static NodeTapEssentia of(RelayBlockEntity tap) {
        if (tap.platformHandler instanceof NodeTapEssentia handler) {
            return handler;
        }
        NodeTapEssentia handler = new NodeTapEssentia(tap);
        tap.platformHandler = handler;
        return handler;
    }

    private @Nullable NodeBlockEntity node() {
        return tap.getLevel() instanceof ServerLevel level && !tap.isRemoved()
                ? NodeTaps.node(level, tap.getBlockPos())
                : null;
    }

    private static @Nullable ResourceKey<Aspect> key(EssentiaResource resource) {
        Holder<Aspect> a = resource.aspect();
        return a == null ? null : a.unwrapKey().orElse(null);
    }

    // --- suction ------------------------------------------------------------------------------

    @Override
    public int suction(ResourceKey<Aspect> aspect) {
        NodeBlockEntity node = node();
        if (node == null || !(tap.getLevel() instanceof ServerLevel level)) {
            return 0;
        }
        return NodeTaps.room(level, node, aspect.identifier()) > 0
                ? VisRulesService.tuning().integer("cultivate.suction")
                : 0;
    }

    // --- the handler: no slots, insert only ---------------------------------------------------

    @Override
    public int size() {
        return 0;
    }

    @Override
    public EssentiaResource getResource(int index) {
        return EssentiaResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return 0;
    }

    @Override
    public long getCapacityAsLong(int index, EssentiaResource resource) {
        return 0;
    }

    @Override
    public boolean isValid(int index, EssentiaResource resource) {
        return false;
    }

    @Override
    public int insert(int index, EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }

    @Override
    public int insert(EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ResourceKey<Aspect> aspect = key(resource);
        NodeBlockEntity node = node();
        if (aspect == null
                || node == null
                || amount == 0
                || !(tap.getLevel() instanceof ServerLevel level)
                || NodeTaps.room(level, node, aspect.identifier()) == 0) {
            return 0;
        }
        target = node;
        journal.updateSnapshots(tx); // snapshots the exact state before settling
        return NodeTaps.insert(level, node, aspect.identifier(), amount);
    }

    @Override
    public int extract(int index, EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }

    @Override
    public int extract(EssentiaResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }
}
