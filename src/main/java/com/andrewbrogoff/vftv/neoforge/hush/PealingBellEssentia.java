package com.andrewbrogoff.vftv.neoforge.hush;

import com.andrewbrogoff.vftv.api.aspect.Aspect;
import com.andrewbrogoff.vftv.api.aspect.AspectKeys;
import com.andrewbrogoff.vftv.api.essentia.EssentiaSuction;
import com.andrewbrogoff.vftv.api.neoforge.essentia.EssentiaResource;
import com.andrewbrogoff.vftv.core.flux.FluxRulesService;
import com.andrewbrogoff.vftv.core.hush.PealingBellBlockEntity;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Pealing Bell's insert-only, Sonus-only essentia handler and its suction (Hush spec,
 * "Feeding"): it accepts {@code min(amount, peal.capacity − sonus)} inside the transfer
 * transaction, with a snapshot journal that holds the bell transaction-busy until the root closes.
 * While a peal owns the bell it accepts 0 and its suction reads 0.
 */
final class PealingBellEssentia implements ResourceHandler<EssentiaResource>, EssentiaSuction {
    private final PealingBellBlockEntity bell;
    private final Journal journal = new Journal();

    private PealingBellEssentia(PealingBellBlockEntity bell) {
        this.bell = bell;
    }

    /** The handler of one bell, bound to that instance for its life. */
    static PealingBellEssentia of(PealingBellBlockEntity bell) {
        if (bell.platformHandler instanceof PealingBellEssentia handler) {
            return handler;
        }
        PealingBellEssentia handler = new PealingBellEssentia(bell);
        bell.platformHandler = handler;
        return handler;
    }

    private static boolean sonus(EssentiaResource resource) {
        Holder<Aspect> aspect = resource.aspect();
        return aspect != null && aspect.is(AspectKeys.SONUS);
    }

    @Override
    public int suction(ResourceKey<Aspect> aspect) {
        return aspect.equals(AspectKeys.SONUS) && bell.room() > 0
                ? FluxRulesService.current().pealSuction()
                : 0;
    }

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
        int n = sonus(resource) ? Math.min(amount, bell.room()) : 0;
        if (n > 0) {
            journal.updateSnapshots(tx);
            bell.add(n);
        }
        return n;
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

    /** Snapshots the Sonus once per touched depth; tracks busy until the root closes. */
    private final class Journal extends SnapshotJournal<int[]> {
        private int live;

        @Override
        protected int[] createSnapshot() {
            if (live++ == 0) {
                bell.setBusy(true);
            }
            return new int[] {bell.sonus()};
        }

        @Override
        protected void revertToSnapshot(int[] snapshot) {
            bell.restore(snapshot[0]);
        }

        @Override
        protected void releaseSnapshot(int[] snapshot) {
            if (--live == 0) {
                bell.setBusy(false);
            }
        }

        @Override
        protected void onRootCommit(int[] originalState) {
            bell.committed();
        }
    }
}
