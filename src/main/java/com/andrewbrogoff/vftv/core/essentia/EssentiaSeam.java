package com.andrewbrogoff.vftv.core.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The essentia transfer seam (architecture, "Seams"): everything loader-specific a line or an
 * Alembic needs, implemented in {@code neoforge.essentia} and installed once at mod construction.
 * Capabilities, capability caches, transactions and the event post stay behind it, so {@code core}
 * never sees a NeoForge type. Server thread only.
 */
public interface EssentiaSeam {
    /** One endpoint's two side-matched capabilities, read through caches. */
    interface Probe {
        /** The handler's slot count, or -1 when there is no handler on this side. */
        int slots();

        /** Slot {@code i}'s aspect, or null when empty. */
        @Nullable ResourceKey<Aspect> aspect(int i);

        /** Slot {@code i}'s amount. */
        long amount(int i);

        /** The suction for {@code aspect}, raw (the caller clamps); 0 with no suction. */
        int suction(ResourceKey<Aspect> aspect);

        /** The hold for {@code aspect}, raw (the caller clamps); 0 with no suction. */
        int hold(ResourceKey<Aspect> aspect);
    }

    /**
     * Two capability caches at {@code pos} on {@code side}: valid while {@code valid} holds, and
     * calling {@code invalidated} (which must not touch the level) when either invalidates.
     */
    Probe probe(
            ServerLevel level,
            BlockPos pos,
            Direction side,
            BooleanSupplier valid,
            Runnable invalidated);

    /** Whether no transfer transaction is open or closing: {@code Lifecycle.NONE}. */
    boolean idle();

    /** Opens a root transaction. */
    void beginRoot();

    /** Commits and closes the root. */
    void commitRoot();

    /** Opens a transaction nested in the current one. */
    void beginNested();

    /** Closes the innermost nested transaction, committing or aborting it. */
    void endNested(boolean commit);

    /** Aborts and closes any transaction still open after a failure; a no-op otherwise. */
    void closeAll();

    /** Extracts through the probe's handler in the innermost transaction. */
    int extract(Probe probe, Holder<Aspect> aspect, int amount);

    /** Inserts through the probe's handler in the innermost transaction. */
    int insert(Probe probe, Holder<Aspect> aspect, int amount);

    /** Posts {@code EssentiaEvent} with this payload. */
    void post(EssentiaPayload payload);

    /** {@code level.invalidateCapabilities(pos)}. */
    void invalidateCapabilities(ServerLevel level, BlockPos pos);
}
