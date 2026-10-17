package com.andrewbrogoff.vftv.neoforge.essentia;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.essentia.EssentiaPayload;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaCapabilities;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaEvent;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.core.essentia.EssentiaSeam;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The essentia seam on NeoForge: probes over two {@link BlockCapabilityCache}s each, a stack of
 * {@link Transaction}s for one root at a time, the {@link EssentiaEvent} post and capability
 * invalidation. Server thread only.
 */
final class NeoForgeEssentiaSeam implements EssentiaSeam {
    private final Deque<Transaction> open = new ArrayDeque<>();

    /** One endpoint's caches. */
    private static final class CachedProbe implements Probe {
        final BlockCapabilityCache<ResourceHandler<EssentiaResource>, @Nullable Direction> handler;
        final BlockCapabilityCache<EssentiaSuction, @Nullable Direction> suction;

        CachedProbe(
                ServerLevel level,
                BlockPos pos,
                Direction side,
                BooleanSupplier valid,
                Runnable invalidated) {
            handler =
                    BlockCapabilityCache.create(
                            EssentiaCapabilities.HANDLER, level, pos, side, valid, invalidated);
            suction =
                    BlockCapabilityCache.create(
                            EssentiaCapabilities.SUCTION, level, pos, side, valid, invalidated);
        }

        @Override
        public int slots() {
            ResourceHandler<EssentiaResource> h = handler.getCapability();
            return h == null ? -1 : h.size();
        }

        @Override
        public @Nullable ResourceKey<Aspect> aspect(int i) {
            ResourceHandler<EssentiaResource> h = handler.getCapability();
            if (h == null) {
                return null;
            }
            Holder<Aspect> a = h.getResource(i).aspect();
            return a == null ? null : a.unwrapKey().orElse(null);
        }

        @Override
        public long amount(int i) {
            ResourceHandler<EssentiaResource> h = handler.getCapability();
            return h == null ? 0 : h.getAmountAsLong(i);
        }

        @Override
        public int suction(ResourceKey<Aspect> aspect) {
            EssentiaSuction s = suction.getCapability();
            return s == null ? 0 : s.suction(aspect);
        }

        @Override
        public int hold(ResourceKey<Aspect> aspect) {
            EssentiaSuction s = suction.getCapability();
            return s == null ? 0 : s.hold(aspect);
        }

        @Override
        public boolean hasSuction() {
            return suction.getCapability() != null;
        }
    }

    @Override
    public Probe probe(
            ServerLevel level,
            BlockPos pos,
            Direction side,
            BooleanSupplier valid,
            Runnable invalidated) {
        return new CachedProbe(level, pos, side, valid, invalidated);
    }

    @Override
    public boolean idle() {
        return Transaction.getLifecycle() == Transaction.Lifecycle.NONE;
    }

    @Override
    public void beginRoot() {
        open.push(Transaction.openRoot());
    }

    @Override
    public void commitRoot() {
        endNested(true);
    }

    @Override
    public void beginNested() {
        open.push(Transaction.open(open.peek()));
    }

    @Override
    public void endNested(boolean commit) {
        try (Transaction t = open.pop()) {
            if (commit) {
                t.commit();
            }
        }
    }

    @Override
    public void closeAll() {
        while (!open.isEmpty()) {
            open.pop().close();
        }
    }

    @Override
    public int extract(Probe probe, Holder<Aspect> aspect, int amount) {
        ResourceHandler<EssentiaResource> h = ((CachedProbe) probe).handler.getCapability();
        return h == null ? 0 : h.extract(new EssentiaResource(aspect), amount, open.element());
    }

    @Override
    public int insert(Probe probe, Holder<Aspect> aspect, int amount) {
        ResourceHandler<EssentiaResource> h = ((CachedProbe) probe).handler.getCapability();
        return h == null ? 0 : h.insert(new EssentiaResource(aspect), amount, open.element());
    }

    @Override
    public void post(EssentiaPayload payload) {
        NeoForge.EVENT_BUS.post(new EssentiaEvent(payload));
    }

    @Override
    public void invalidateCapabilities(ServerLevel level, BlockPos pos) {
        level.invalidateCapabilities(pos);
    }
}
