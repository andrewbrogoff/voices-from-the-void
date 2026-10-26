package com.andrewbrogoff.vftv.neoforge.infusion;

import com.andrewbrogoff.vftv.core.infusion.AltarBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The bowl's and pedestal's one explicit item handler (infusion spec, "Holding items"): one slot
 * under the block entity's rules, with its own snapshot journal. The journal reports the block
 * transaction-busy from its first snapshot until the root closes, and publishes on the root commit.
 */
final class AltarItemHandler implements ResourceHandler<ItemResource> {
    private final AltarBlockEntity altar;
    private final Journal journal = new Journal();

    private AltarItemHandler(AltarBlockEntity altar) {
        this.altar = altar;
    }

    /** The handler of one block entity, bound to that instance for its life. */
    static AltarItemHandler of(AltarBlockEntity altar) {
        if (altar.platformHandler instanceof AltarItemHandler handler) {
            return handler;
        }
        AltarItemHandler handler = new AltarItemHandler(altar);
        altar.platformHandler = handler;
        return handler;
    }

    private boolean mayInsert(int index, ItemResource resource) {
        return index == 0
                && !altar.unavailable()
                && altar.getTheItem().isEmpty()
                && altar.mayInsert(resource.toStack(1));
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public ItemResource getResource(int index) {
        return index == 0 && !altar.unavailable()
                ? ItemResource.of(altar.getTheItem())
                : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index == 0 && !altar.unavailable() ? altar.getTheItem().getCount() : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return !resource.isEmpty() && mayInsert(index, resource) ? 1 : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty() && mayInsert(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!mayInsert(index, resource)) {
            return 0;
        }
        int k = Math.min(amount, 1);
        if (k > 0) {
            journal.updateSnapshots(tx);
            altar.writeSlot(resource.toStack(1));
        }
        return k;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return insert(0, resource, amount, tx);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (index != 0
                || altar.unavailable()
                || !resource.matches(altar.getTheItem())
                || altar.takingLocked()) {
            return 0;
        }
        int k = Math.min(amount, 1);
        if (k > 0) {
            journal.updateSnapshots(tx);
            altar.writeSlot(ItemStack.EMPTY);
        }
        return k;
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext tx) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return extract(0, resource, amount, tx);
    }

    /** Snapshots the slot once per touched depth; tracks busy until the root closes. */
    private final class Journal extends SnapshotJournal<ItemStack> {
        private int live;

        @Override
        protected ItemStack createSnapshot() {
            if (live++ == 0) {
                altar.setBusy(true);
            }
            return altar.getTheItem().copy();
        }

        @Override
        protected void revertToSnapshot(ItemStack snapshot) {
            altar.writeSlot(snapshot);
        }

        @Override
        protected void releaseSnapshot(ItemStack snapshot) {
            if (--live == 0) {
                altar.setBusy(false);
            }
        }

        @Override
        protected void onRootCommit(ItemStack originalState) {
            altar.markDirty();
        }
    }
}
